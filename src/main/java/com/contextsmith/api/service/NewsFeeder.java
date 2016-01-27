package com.contextsmith.api.service;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpStatus;

import com.contextsmith.api.data.Project;
import com.contextsmith.api.data.ProjectFactory;
import com.contextsmith.email.cluster.EmailClusterer;
import com.contextsmith.email.cluster.UserEmailAliasFinder;
import com.contextsmith.email.provider.EmailFetcher;
import com.contextsmith.email.provider.EmailFetcher.EmailSource;
import com.contextsmith.email.provider.EmailFilterer;
import com.contextsmith.email.provider.GmailQueryBuilder;
import com.contextsmith.utils.EmailClustererUtil;
import com.contextsmith.utils.InternetAddressUtil;
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

  public static final String EMPTY_JSON_ARRAY = "[]";
  public static final String START_DATE_PARAM = "startDate";
  public static final String END_DATE_PARAM = "endDate";

  public static final Date DEFAULT_GMAIL_AFTER_DATE =
      parseDate("2014/08/01", "yyyy/MM/dd");
  public static final Date DEFAULT_GMAIL_BEFORE_DATE =
      parseDate("2014/08/31", "yyyy/MM/dd");

  @GET
  @Path("cluster")
  @Produces(MediaType.APPLICATION_JSON)
  public static String clusterEmails(
//      @QueryParam("token") String accessToken,
//      @QueryParam("email") String userEmail,
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("preview") Boolean showPreview,  // Transient
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("callback") String callbackUrl) {

    // Must have callback URL.
    if (StringUtils.isBlank(callbackUrl) ||
        !UrlValidator.getInstance().isValid(callbackUrl)) {
      return "";
    }

    EmailFilterer filterer = new EmailFilterer();
    filterer.setRemoveMailListMessages(true);
    filterer.setRemovePrivateMessages(true);

    final boolean RESOLVE_PROJECT_NAME = false;
    return createResponse(tokenEmailJson, null, null, startTimeInSec,
                          endTimeInSec, internalDomain, maxMessages,
                          showPreview, RESOLVE_PROJECT_NAME, filterer,
                          callbackUrl);
  }

  @GET
  @Path("create")
  @Produces(MediaType.APPLICATION_JSON)
  public static String createResponse(
//      @QueryParam("token") String accessToken,
//      @QueryParam("email") String userEmail,
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("query") String searchQuery,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson,  // Optional
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("max") Integer maxMessages,  // Transient
      @QueryParam("preview") Boolean showPreview,  // Transient
      @QueryParam("project_name") Boolean resolveProjectName,  // Transient
      EmailFilterer messageFilterer,
      @QueryParam("callback") String callbackUrl) {  // Optional
    // TODO(rcwang): add Message IDs input.

    // Verify callback URL (if exist).
    if (StringUtils.isNotBlank(callbackUrl) &&
        !UrlValidator.getInstance().isValid(callbackUrl)) {
      return "";
    }

    NewsFeederRequest request = new NewsFeederRequest();
//    request.setAccessToken(accessToken);
    request.setCallbackUrl(callbackUrl);
    request.setStartTimeInSec(startTimeInSec);
    request.setEndTimeInSec(endTimeInSec);
//    request.setUserEmail(userEmail);
    request.parseTokenEmailJson(tokenEmailJson);
    request.parseExternalClusterJson(externalClusterJson);
    request.setSearchQuery(searchQuery);
    request.setMaxMessages(maxMessages);
    request.setInternalDomain(internalDomain);
    request.setShowPreview(showPreview);
    request.setResolveProjectName(resolveProjectName);
    request.setMessageFilterer(messageFilterer);

    // Verify user email and access token.
    if (request.getTokenEmailPairs() == null ||
        request.getTokenEmailPairs().isEmpty()) {
      log.error("Missing 'token_emails' parameter.");
      return "";
    }
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      String emailStr = tokenEmailPair.getEmailStr();
      if (!EmailValidator.getInstance().isValid(emailStr)) {
        log.error("Invalid email address: {}", emailStr);
      }
      if (StringUtils.isBlank(tokenEmailPair.getAccessToken())) {
        log.error("Missing access token for email: {}", emailStr);
        return "";
      }
    }
    log.info(request);

    NewsFeeder runnable = new NewsFeeder();
    runnable.setRequest(request);

    // TODO(rcwang): Use ExecutorService
//    ExecutorService executor = Executors.newCachedThreadPool();
//    executor.submit(runnable);

    if (StringUtils.isBlank(callbackUrl)) {
      runnable.run();  // Blocking
      return runnable.getJsonOutput();
    } else {
      new Thread(runnable).start();  // Non-blocking
      return "";
    }
  }

  /*public static void main(String[] args) {
    createResponse(
        DEFAULT_ACCESS_TOKEN,
        DEFAULT_TEST_EMAIL,
        DEFAULT_GMAIL_AFTER_DATE.getTime() / 1000,
        DEFAULT_GMAIL_BEFORE_DATE.getTime() / 1000,
        QUERY_GMAIL_DATA ? DEFAULT_GMAIL_MAX_MESSAGES : DEFAULT_ENRON_MAX_MESSAGES,
        null,
        true,
        false,
        null,
        null);
  }*/

  public static Date parseDate(String dateStr, String dateFormat) {
    try {
      return new SimpleDateFormat(dateFormat).parse(dateStr);
    } catch (ParseException e) {
      return null;
    }
  }

  @GET
  @Path("search")
  @Produces(MediaType.APPLICATION_JSON)
  public static String searchEmails(
//      @QueryParam("token") String accessToken,
//      @QueryParam("email") String userEmail,
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("query") String searchQuery,  // Optional
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson) {  // Optional

    EmailFilterer filterer = new EmailFilterer();
    filterer.setRemoveMailListMessages(false);
    filterer.setRemovePrivateMessages(true);

    final boolean SHOW_PREVIEW = true;
    final boolean RESOLVE_PROJECT_NAME = false;
    return createResponse(tokenEmailJson, searchQuery, externalClusterJson,
                          startTimeInSec, endTimeInSec, internalDomain,
                          maxMessages, SHOW_PREVIEW, RESOLVE_PROJECT_NAME,
                          filterer, null);
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

  private String jsonOutput;
  private NewsFeederRequest request;
  private EmailFetcher emailFetcher;

  public NewsFeeder() {
    this.emailFetcher = new EmailFetcher(EmailSource.GMAIL);
  }

  public NewsFeeder(NewsFeederRequest request) {
    this.setRequest(request);
    this.jsonOutput = null;
  }

  public boolean execCallback(String jsonOutput) {
    if (StringUtils.isBlank(this.request.callbackUrl)) return false;
    URL url = null;
    try {
      URIBuilder builder = new URIBuilder(this.request.callbackUrl);
      if (this.request.startTimeInSec != null && this.request.endTimeInSec != null) {
        builder.addParameter(START_DATE_PARAM, Long.toString(this.request.startTimeInSec))
        .addParameter(END_DATE_PARAM, Long.toString(this.request.endTimeInSec));
      }
      url = builder.build().toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      log.error(e + " (" + this.request.callbackUrl + ")");
    }
    if (url == null) return false;
    try {
      if (sendHttpPost(url, this.jsonOutput) == HttpStatus.OK_200) {
        return true;
      }
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    }
    return false;
  }

  public String getJsonOutput() {
    return this.jsonOutput;
  }

  @Override
  public void run() {
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.jsonOutput = null;  // Reset the output.

    // Date constructor take unix time in milliseconds.
    Date afterThisDate = null;
    if (this.request.startTimeInSec != null) {
      afterThisDate = new Date(this.request.startTimeInSec * 1000);
    }
    Date beforeThisDate = null;
    if (this.request.endTimeInSec != null) {
      beforeThisDate = new Date(this.request.endTimeInSec * 1000);
    }

    String query = new GmailQueryBuilder()
        .addQuery(this.request.searchQuery)
        .addBeforeDate(beforeThisDate)
        .addAfterDate(afterThisDate)
        .addClusters(this.request.externalClusters)
        .build();

    for (TokenEmailPair tokenEmailPair : this.request.getTokenEmailPairs()) {
      this.emailFetcher.addTask(query, tokenEmailPair.getAccessToken(),
                                tokenEmailPair.getEmailStr(),
                                this.request.maxMessages);
    }
    Collection<MimeMessage> messages = null;
    try {
//      messages = EmailFetcher.fetchGmails(
//          query, this.request.accessToken, this.request.maxMessages);
      messages = this.emailFetcher.startFetch();
    } catch (InterruptedException e) {
      log.error(e);
      e.printStackTrace();
    }
    /*catch (IOException e) {
      if (e instanceof GoogleJsonResponseException) {
        this.jsonOutput = ((GoogleJsonResponseException) e).getDetails().toString();
        log.error(this.jsonOutput);
        execCallback(this.jsonOutput);
        return;
      } else {
        log.error(e);
        e.printStackTrace();
        return;
      }
    }*/
    if (messages == null || messages.isEmpty()) {
      log.warn("No messages retrieved.");
      return;
    }

    // Filter out irrelevant emails.
    this.request.getMessageFilterer().filterUselessMessages(messages);

    // Extract company's mail address domain.
    String companyDomain = this.request.internalDomain;
    if (StringUtils.isBlank(companyDomain)) {
      // Get the first email, assuming all emails have the same domain.
      companyDomain = InternetAddressUtil.getAddressDomain(
          this.request.getTokenEmailPairs().get(0).getEmailStr());
    }
    log.debug("Company domain: {}", companyDomain);

    // Construct external clusters.
    List<Set<InternetAddress>> externalClusters = this.request.externalClusters;
    if (externalClusters == null) {
      // Find all user's alias e-mail addresses.
      Set<InternetAddress> sortedAliasEmails = new LinkedHashSet<>(
          UserEmailAliasFinder.find(messages).keySet());
      // Insert all internal email addresses into the alias set.
      for (TokenEmailPair tokenEmailPairs : this.request.getTokenEmailPairs()) {
        sortedAliasEmails.add(tokenEmailPairs.getEmailAddress());
      }
      externalClusters = EmailClusterer.findExternalClusters(
          messages, companyDomain, sortedAliasEmails);
    }
    if (externalClusters == null) {
      log.warn("No external clusters found.");
      return;
    }
    EmailClustererUtil.printClusters(externalClusters);

    // Use external clusters to find internal clusters.
    /*List<Set<InternetAddress>> internalClusters =
        EmailClusterer.findInternalClusters(
            messages, internalDomain, externalClusters);
    if (internalClusters == null) {
      log.warn("No internal clusters found.");
      return;
    }
    EmailClustererUtil.printClusters(internalClusters); */

    // The list index must match between external and internal.
//    checkState(externalClusters.size() == internalClusters.size());

    /*if (beforeThisDate != null && afterThisDate != null) {
      MimeMessageUtil.filterByDateRange(messages, afterThisDate, beforeThisDate);
      SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
      log.debug("{} messages found between {} and {}", messages.size(),
                df.format(afterThisDate), df.format(beforeThisDate));
    }*/

    ProjectFactory projectFactory = ProjectFactory.load(messages);
    Set<Project> projects = new TreeSet<>();

    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
//      Set<InternetAddress> internalCluster = internalClusters.get(i);

      log.debug("Creating project #{}...", i + 1);
      Project project = projectFactory.createProject(
          String.valueOf(i), companyDomain, externalCluster,
          this.request.showPreview, this.request.resolveProjectName);

      if (project != null) projects.add(project);
    }
    this.jsonOutput = convertProjectsToJson(projects);
    execCallback(this.jsonOutput);
    log.debug(this.jsonOutput);
    log.info("Total elapsed time: {}", stopwatch);
  }

  public void setRequest(NewsFeederRequest request) {
    this.request = request;
  }
}