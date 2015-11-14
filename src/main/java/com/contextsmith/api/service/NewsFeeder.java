package com.contextsmith.api.service;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpStatus;

import com.contextsmith.api.data.Project;
import com.contextsmith.api.data.ProjectFactory;
import com.contextsmith.email.cluster.EmailClusterer;
import com.contextsmith.email.cluster.UserAddressPredictor;
import com.contextsmith.email.provider.GmailServiceProvider;
import com.contextsmith.email.provider.LocalFileProvider;
import com.contextsmith.utils.EmailClustererUtil;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@Path("newsfeed")
public class NewsFeeder {

  static final Logger log = LogManager.getLogger(NewsFeeder.class);

  // False to query Enron data.
  public static final boolean QUERY_GMAIL_DATA = true;
  public static final String EMPTY_JSON_ARRAY = "[]";
  public static final String START_DATE_PARAM = "startDate";
  public static final String END_DATE_PARAM = "endDate";

  // Gmail settings.
  public static final String DEFAULT_GMAIL_ACCESS_TOKEN =
      "ya29.JwLnVJ2E9fRzIAN3-3UJwNlhjCb_amfkzDNDQwnMf721zm1paNiOX0Otg1xyo9XXbv7ZPw";
  public static final String DEFAULT_GMAIL_USER = "me";
  public static final Date DEFAULT_GMAIL_AFTER_DATE =
      parseDate("2014/08/01", "yyyy/MM/dd");
  public static final Date DEFAULT_GMAIL_BEFORE_DATE =
      parseDate("2014/08/31", "yyyy/MM/dd");
  public static final int DEFAULT_GMAIL_MAX_MESSAGES = 1_000;

  // Enron mail settings.
  public static final String DEFAULT_ENRON_USER = "kean-s";  // "kean-s, smith-m"
  public static final int DEFAULT_ENRON_MAX_MESSAGES = -1;  // -1 = unlimited

  @GET
  @Path("create")
  @Produces(MediaType.APPLICATION_JSON)
  public static String createResponse(
      @QueryParam("after") Long afterUnixTimeInSec,
      @QueryParam("before") Long beforeUnixTimeInSec,
      @QueryParam("max") Integer maxMessages,
      @QueryParam("token") String accessToken,
      @QueryParam("email") String userAddressStr,
      @QueryParam("callback") String callbackUrl) {

    if (afterUnixTimeInSec == null ||
        beforeUnixTimeInSec == null ||
        Strings.isBlank(accessToken) ||
        Strings.isBlank(callbackUrl)) {
      return EMPTY_JSON_ARRAY;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();
    Date afterThisDate = new Date(afterUnixTimeInSec * 1000);
    Date beforeThisDate = new Date(beforeUnixTimeInSec * 1000);

    if (maxMessages == null) maxMessages = DEFAULT_GMAIL_MAX_MESSAGES;
    List<MimeMessage> messages = null;
    if (QUERY_GMAIL_DATA) {
      try {
        messages = fetchGmails(beforeThisDate, accessToken, maxMessages);
      } catch (IOException e) {
        if (e instanceof GoogleJsonResponseException) {
          return ((GoogleJsonResponseException) e).getDetails().toString();
        } else {
          log.error(e);
          e.printStackTrace();
        }
      }
    } else {
      messages = fetchEnronMails(DEFAULT_ENRON_USER, maxMessages);
    }
    if (messages == null) return EMPTY_JSON_ARRAY;

    // Predict user's e-mail addresses.
    Set<InternetAddress> sortedAddresses =
        UserAddressPredictor.predict(messages).keySet();

    if (!Strings.isBlank(userAddressStr)) {
      InternetAddress userAddress = null;
      try { userAddress = new InternetAddress(userAddressStr); }
      catch (AddressException e) {}
      if (userAddress != null && !sortedAddresses.contains(userAddress)) {
        log.warn("Predicted user addresses are missing: {}", userAddressStr);
        sortedAddresses.add(userAddress);
      }
    }
    // We use the user's e-mail address to obtain company's email domain;
    // so it must *not* be empty here.
    if (sortedAddresses.isEmpty()) return EMPTY_JSON_ARRAY;

    // Extract company mail address domain.
    String internalDomain =
        EmailClustererUtil.findInternalDomain(sortedAddresses);

    // Construct external clusters.
    List<Set<InternetAddress>> externalClusters =
        EmailClusterer.findExternalClusters(
            messages, internalDomain, sortedAddresses);
    if (externalClusters == null) return EMPTY_JSON_ARRAY;
    EmailClustererUtil.printClusters(externalClusters);

    // Construct internal clusters.
    List<Set<InternetAddress>> internalClusters =
        EmailClusterer.findInternalClusters(
            messages, internalDomain, externalClusters);
    if (internalClusters == null) return EMPTY_JSON_ARRAY;
    EmailClustererUtil.printClusters(internalClusters);

    checkState(externalClusters.size() == internalClusters.size());

    if (QUERY_GMAIL_DATA) {
      MimeMessageUtil.filterByDateRange(messages, afterThisDate, beforeThisDate);
    }

    Set<Project> projects = new TreeSet<>();
    ProjectFactory projectFactory = new ProjectFactory().loadMessages(messages);
    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
      Set<InternetAddress> internalCluster = internalClusters.get(i);

      log.debug("Creating project #{}...", i + 1);
      Project project = projectFactory.createProject(
          String.valueOf(i), externalCluster, internalCluster);
      if (project != null) projects.add(project);
    }
    String json = convertProjectsToJson(projects);

    try {
      URL url = new URIBuilder(callbackUrl)
          .addParameter(START_DATE_PARAM, Long.toString(afterUnixTimeInSec))
          .addParameter(END_DATE_PARAM, Long.toString(beforeUnixTimeInSec))
          .build().toURL();
      sendHttpPost(url, json);
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    } catch (URISyntaxException e) {
      log.error(e);
      e.printStackTrace();
    }

    log.debug(json);
    log.info("Total elapsed time: {}", stopwatch);
    return json;
  }

  public static List<MimeMessage> fetchEnronMails(String user,
                                                  int maxMessages) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    log.info("Fetching max. {} Enron e-mails from user: {}", maxMessages, user);

    List<MimeMessage> messages = LocalFileProvider.provide(user, maxMessages);
    log.info("Fetching Enron e-mails took: {}\n", stopwatch);
    return messages;
  }

  public static List<MimeMessage> fetchGmails(Date beforeThisDate,
                                              String accessToken,
                                              int maxMessages)
  throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
    String query = "before:" + df.format(beforeThisDate);

    log.info("Fetching max. {} gmails using query: \"{}\"", maxMessages, query);
    GmailServiceProvider provider = new GmailServiceProvider(accessToken);
    List<MimeMessage> messages = provider.provide(
        DEFAULT_GMAIL_USER, query, maxMessages);

    log.info("Fetching gmails took: {}\n", stopwatch);
    return messages;
  }

  public static void main(String[] args) {
    String json = createResponse(
        DEFAULT_GMAIL_AFTER_DATE.getTime() / 1000,
        DEFAULT_GMAIL_BEFORE_DATE.getTime() / 1000,
        QUERY_GMAIL_DATA ? DEFAULT_GMAIL_MAX_MESSAGES : DEFAULT_ENRON_MAX_MESSAGES,
        null,
        null,
        null);
    log.debug(json);
  }

  public static int sendHttpPost(URL url, String json) throws IOException {
    log.debug("Posting json ({} bytes) to URL: {}",
              json.getBytes().length, url);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
//    conn.setRequestProperty("Accept", "*/*");
//    conn.setRequestProperty("Content-Length", String.valueOf(json.getBytes().length));
    conn.setDoOutput(true);

    /*DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
    wr.writeBytes(json);
    wr.flush();
    wr.close();*/

    OutputStream os = conn.getOutputStream();
    os.write(json.getBytes(StandardCharsets.UTF_8));
    os.close();

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpStatus.OK_200) {
      log.info("POST successful! Response: {}, {}", responseCode,
               conn.getResponseMessage());
    } else {
      log.error("POST failed! Response: {}, {}", responseCode,
                conn.getResponseMessage());
    }
    return conn.getResponseCode();
  }

  private static String convertProjectsToJson(Set<Project> projects) {
    JsonSerializer<Date> jsonSerializer = new JsonSerializer<Date>() {
      @Override
      public JsonElement serialize(
          Date src, Type typeOfSrc, JsonSerializationContext context) {
        return (src == null) ? null : new JsonPrimitive(src.getTime() / 1000);
      }
    };

    JsonDeserializer<Date> jsonDeserializer = new JsonDeserializer<Date>() {
      @Override
      public Date deserialize(
          JsonElement json, Type typeOfT, JsonDeserializationContext context)
              throws JsonParseException {
        return (json == null) ? null : new Date(json.getAsLong() * 1000);
      }
    };

    Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(Date.class, jsonSerializer)
        .registerTypeAdapter(Date.class, jsonDeserializer)
        .create();

    return gson.toJson(projects);
  }

  private static Date parseDate(String dateStr, String dateFormat) {
    try {
      return new SimpleDateFormat(dateFormat).parse(dateStr);
    } catch (ParseException e) {
      return null;
    }
  }
}
