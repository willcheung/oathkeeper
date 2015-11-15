package com.contextsmith.api.service;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
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

import org.apache.commons.lang3.StringUtils;
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
public class NewsFeeder implements Runnable {

  static final Logger log = LogManager.getLogger(NewsFeeder.class);

  // False to query Enron data.
  public static final boolean QUERY_GMAIL_DATA = true;
  public static final String EMPTY_JSON_ARRAY = "[]";
  public static final String START_DATE_PARAM = "startDate";
  public static final String END_DATE_PARAM = "endDate";

  // Gmail configurations.
  // Directory to store user credentials for this application.
//  public static final String DATA_STORE_DIR = "rcwang@gmail.com";
  public static final String DEFAULT_DATA_STORE_DIR = "indifferenzetester@gmail.com";
  public static final String DEFAULT_ACCESS_TOKEN = "test";
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
  public static void createResponse(
      @QueryParam("after") Long startTimeInSec,
      @QueryParam("before") Long endTimeInSec,
      @QueryParam("max") Integer maxMessages,
      @QueryParam("token") String accessToken,
      @QueryParam("email") String userEmailAddr,
      @QueryParam("callback") String callbackUrl) {

    NewsFeeder runnable = new NewsFeeder();
    runnable.setAccessToken(accessToken);
    runnable.setCallbackUrl(callbackUrl);
    runnable.setStartTimeInSec(startTimeInSec);
    runnable.setEndTimeInSec(endTimeInSec);
    runnable.setUserEmailAddr(userEmailAddr);
    runnable.setMaxMessages(maxMessages);
    new Thread(runnable).start();
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
    checkNotNull(beforeThisDate);
    checkNotNull(accessToken);

    Stopwatch stopwatch = Stopwatch.createStarted();
    SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
    String query = "before:" + df.format(beforeThisDate);
    log.info("Fetching at most {} gmails using query: \"{}\"", maxMessages, query);

    GmailServiceProvider provider = null;
    if (accessToken.equals(DEFAULT_ACCESS_TOKEN)) {
      File dataStoreFile = new File(
          System.getProperty("user.home"), ".credentials/" + DEFAULT_DATA_STORE_DIR);
      provider = new GmailServiceProvider(dataStoreFile);
    } else {
      provider = new GmailServiceProvider(accessToken);
    }

    List<MimeMessage> messages = provider.provide(
        DEFAULT_GMAIL_USER, query, maxMessages);
    log.info("Fetching gmails took: {}\n", stopwatch);
    return messages;
  }

  public static void main(String[] args) {
    /*createResponse(
        DEFAULT_GMAIL_AFTER_DATE.getTime() / 1000,
        DEFAULT_GMAIL_BEFORE_DATE.getTime() / 1000,
        QUERY_GMAIL_DATA ? DEFAULT_GMAIL_MAX_MESSAGES : DEFAULT_ENRON_MAX_MESSAGES,
        null,
        null,
        null);*/
  }

  public static Date parseDate(String dateStr, String dateFormat) {
    try {
      return new SimpleDateFormat(dateFormat).parse(dateStr);
    } catch (ParseException e) {
      return null;
    }
  }

  public static int sendHttpPost(URL url, String json) throws IOException {
    log.debug("Posting json ({} bytes) to URL: {}",
              json.getBytes().length, url);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    conn.setDoOutput(true);

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

  private Long startTimeInSec;
  private Long endTimeInSec;
  private Integer maxMessages;
  private String accessToken;
  private String userEmailAddr;
  private String callbackUrl;

  public String getAccessToken() {
    return this.accessToken;
  }

  public String getCallbackUrl() {
    return this.callbackUrl;
  }

  public Long getEndTimeInSec() {
    return this.endTimeInSec;
  }

  public Integer getMaxMessages() {
    return this.maxMessages;
  }

  public Long getStartTimeInSec() {
    return this.startTimeInSec;
  }

  public String getUserEmailAddr() {
    return this.userEmailAddr;
  }

  @Override
  public void run() {
    if (this.startTimeInSec == null ||
        this.endTimeInSec == null ||
        StringUtils.isBlank(this.accessToken) ||
        StringUtils.isBlank(this.callbackUrl)) {
      return;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();
    Date afterThisDate = new Date(this.startTimeInSec * 1000);
    Date beforeThisDate = new Date(this.endTimeInSec * 1000);

    if (this.maxMessages == null) this.maxMessages = DEFAULT_GMAIL_MAX_MESSAGES;
    List<MimeMessage> messages = null;
    if (QUERY_GMAIL_DATA) {
      try {
        messages = fetchGmails(beforeThisDate, this.accessToken, this.maxMessages);
      } catch (IOException e) {
        if (e instanceof GoogleJsonResponseException) {
          log.error(((GoogleJsonResponseException) e).getDetails().toString());
        } else {
          log.error(e);
          e.printStackTrace();
        }
      }
    } else {
      messages = fetchEnronMails(DEFAULT_ENRON_USER, this.maxMessages);
    }
    if (messages == null) return;

    // Predict user's e-mail addresses.
    Set<InternetAddress> sortedAddresses =
        UserAddressPredictor.predict(messages).keySet();

    if (!Strings.isBlank(this.userEmailAddr)) {
      InternetAddress userAddress = null;
      try { userAddress = new InternetAddress(this.userEmailAddr); }
      catch (AddressException e) {}
      if (userAddress != null && !sortedAddresses.contains(userAddress)) {
        log.warn("Predicted user addresses are missing: {}", this.userEmailAddr);
        sortedAddresses.add(userAddress);
      }
    }
    // We use the user's e-mail address to obtain company's email domain;
    // so it must *not* be empty here.
    if (sortedAddresses.isEmpty()) return;

    // Extract company mail address domain.
    String internalDomain =
        EmailClustererUtil.findInternalDomain(sortedAddresses);

    // Construct external clusters.
    List<Set<InternetAddress>> externalClusters =
        EmailClusterer.findExternalClusters(
            messages, internalDomain, sortedAddresses);
    if (externalClusters == null) return;
    EmailClustererUtil.printClusters(externalClusters);

    // Construct internal clusters.
    List<Set<InternetAddress>> internalClusters =
        EmailClusterer.findInternalClusters(
            messages, internalDomain, externalClusters);
    if (internalClusters == null) return;
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
      URL url = new URIBuilder(this.callbackUrl)
          .addParameter(START_DATE_PARAM, Long.toString(this.startTimeInSec))
          .addParameter(END_DATE_PARAM, Long.toString(this.endTimeInSec))
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
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public void setEndTimeInSec(Long endTimeInSec) {
    this.endTimeInSec = endTimeInSec;
  }

  public void setMaxMessages(Integer maxMessages) {
    this.maxMessages = maxMessages;
  }

  public void setStartTimeInSec(Long startTimeInSec) {
    this.startTimeInSec = startTimeInSec;
  }

  public void setUserEmailAddr(String userEmailAddr) {
    this.userEmailAddr = userEmailAddr;
  }
}
