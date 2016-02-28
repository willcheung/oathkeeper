package com.contextsmith.api.service;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import com.contextsmith.email.provider.EmailFetcher;
import com.contextsmith.email.provider.EmailFetcher.EmailSource;
import com.contextsmith.email.provider.EmailFilterer;
import com.contextsmith.email.provider.GmailQueryBuilder;
import com.contextsmith.utils.EmailClustererUtil;
import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.StringUtil;
import com.google.api.client.googleapis.json.GoogleJsonError;
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

//  public static final String EMPTY_JSON_ARRAY = "[]";
  public static final String START_DATE_PARAM = "startDate";
  public static final String END_DATE_PARAM = "endDate";

  public static final String CALLBACK_REQUEST_METHOD = "POST";
  public static final String CALLBACK_CONTENT_TYPE_HEADER = "Content-Type";
  public static final String CALLBACK_CONTENT_TYPE_VALUE = "application/json; charset=UTF-8";

  public static final int DEFAULT_ERROR_HTTP_STATUS_CODE = 400;

  /*public static final Date DEFAULT_GMAIL_AFTER_DATE =
      parseDate("2014/08/01", "yyyy/MM/dd");
  public static final Date DEFAULT_GMAIL_BEFORE_DATE =
      parseDate("2014/08/31", "yyyy/MM/dd");*/

  private static ExecutorService executor = Executors.newCachedThreadPool();

  @GET
  @Path("cluster")
  @Produces(MediaType.APPLICATION_JSON)
  public static String clusterEmails(
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

    /*EmailFilterer filterer = new EmailFilterer();
    filterer.setRemoveMailListMessages(true);
    filterer.setRemovePrivateMessages(false);*/

    final boolean RESOLVE_PROJECT_NAME = false;
    return createResponse(tokenEmailJson, null, null, startTimeInSec,
                          endTimeInSec, internalDomain, maxMessages,
                          showPreview, RESOLVE_PROJECT_NAME, callbackUrl);
  }

  @GET
  @Path("create")
  @Produces(MediaType.APPLICATION_JSON)
  public static String createResponse(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("query") String searchQuery,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson,  // Optional
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("max") Integer maxMessages,  // Transient
      @QueryParam("preview") Boolean showPreview,  // Transient
      @QueryParam("project_name") Boolean resolveProjectName,  // Transient
      @QueryParam("callback") String callbackUrl) {  // Optional

    // Verify callback URL (if exist).
    if (StringUtils.isNotBlank(callbackUrl) &&
        !UrlValidator.getInstance().isValid(callbackUrl)) {
      return "";
    }

    final NewsFeederRequest request = new NewsFeederRequest();
    request.setCallbackUrl(callbackUrl);
    request.setStartTimeInSec(startTimeInSec);
    request.setEndTimeInSec(endTimeInSec);
    request.setSearchQuery(searchQuery);
    request.setMaxMessages(maxMessages);
    request.setShowPreview(showPreview);
    request.setResolveProjectName(resolveProjectName);
//    request.setMessageFilterer(messageFilterer);

    // Parsing jsons.
    request.parseTokenEmailJson(tokenEmailJson);
    request.parseExternalClusterJson(externalClusterJson);

    // Verify user email and access token.
    if (request.getTokenEmailPairs() == null ||
        request.getTokenEmailPairs().isEmpty()) {
      log.error("Missing 'token_emails' parameter.");
      return "";
    }
    String tokenEmailDomain = null;
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      String emailStr = tokenEmailPair.getEmailStr();
      if (!EmailValidator.getInstance().isValid(emailStr)) {
        log.error("Invalid email address: {}", emailStr);
      }
      if (StringUtils.isBlank(tokenEmailPair.getAccessToken())) {
        log.error("Missing access token for email: {}", emailStr);
        return "";
      }
      String domain = InternetAddressUtil.getAddressDomain(emailStr);
      if (tokenEmailDomain == null) {
        tokenEmailDomain = domain;
      } else if (!tokenEmailDomain.equals(domain)) {
        log.error("Email addresses do not have identical domains.");
        return "";
      }
    }

    // Retrieve internal domain from user parameter first.
    // If not found, then retrieve from user token's email address.
    if (StringUtils.isNotBlank(internalDomain)) {
      request.setInternalDomain(internalDomain);
    } else if (StringUtils.isNotBlank(tokenEmailDomain)) {
      request.setInternalDomain(tokenEmailDomain);
    } else {
      log.error("Missing company's internal domain.");
      return "";
    }
    log.debug("Company internal domain: {}", request.getInternalDomain());
    log.info(request);

//    NewsFeeder runnable = new NewsFeeder();
//    runnable.setRequest(request);

    if (StringUtils.isBlank(callbackUrl)) {  // No callback. Blocking
//      runnable.run();
//      return runnable.getJsonOutput();
      return processToJson(request);
    } else {  // Non-blocking
//      new Thread(runnable).start();
//      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(new Callable<String>() {
        @Override
        public String call() throws Exception {
          return processToJson(request);
        }
      });
      return "";
    }
  }

  /*public static Date parseDate(String dateStr, String dateFormat) {
    try {
      return new SimpleDateFormat(dateFormat).parse(dateStr);
    } catch (ParseException e) {
      return null;
    }
  }*/

  public static String processToJson(NewsFeederRequest request) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    Set<Project> projects;
    try {
      projects = makeProjects(request);
    } catch (IllegalStateException e) {
      return e.getMessage();
    } catch (GoogleJsonResponseException e) {
      GoogleJsonError error = e.getDetails();
      if (error == null) {
        Integer code = StringUtil.parseLeadingIntegers(e.getMessage());
        if (code == null) code = DEFAULT_ERROR_HTTP_STATUS_CODE;
        error = makeGoogleJsonError(HttpStatus.getCode(code).getCode(),
                                    e.getMessage());
      }
      return new Gson().toJson(error);
    }
    String jsonOutput = convertProjectsToJson(projects);
    if (StringUtils.isNotBlank(request.getCallbackUrl())) {
      execCallback(jsonOutput, request.getCallbackUrl(),
                   request.getStartTimeInSec(), request.getEndTimeInSec());
    }
    log.debug(jsonOutput);
    log.info("Total elapsed time: {}", stopwatch);
    return jsonOutput;
  }

//  private String jsonOutput;
//  private NewsFeederRequest request;

  @GET
  @Path("search")
  @Produces(MediaType.APPLICATION_JSON)
  public static String searchEmails(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("query") String searchQuery,  // Optional
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson) {  // Optional

    /*EmailFilterer filterer = new EmailFilterer();
    filterer.setRemoveMailListMessages(false);
    filterer.setRemovePrivateMessages(true);*/

    final boolean SHOW_PREVIEW = true;
    final boolean RESOLVE_PROJECT_NAME = false;
    return createResponse(tokenEmailJson, searchQuery, externalClusterJson,
                          startTimeInSec, endTimeInSec, internalDomain,
                          maxMessages, SHOW_PREVIEW, RESOLVE_PROJECT_NAME,
                          null);
  }

  public static int sendHttpPost(URL url, String json) throws IOException {
    log.debug("Posting json ({} bytes) to URL: {}",
              json.getBytes().length, url);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod(CALLBACK_REQUEST_METHOD);
    conn.setRequestProperty(CALLBACK_CONTENT_TYPE_HEADER,
                            CALLBACK_CONTENT_TYPE_VALUE);
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

  /*public NewsFeeder() {
    this.executor = Executors.newCachedThreadPool();
    this.emailFetcher = new EmailFetcher(EmailSource.GMAIL);
  }*/

  /*public NewsFeeder(NewsFeederRequest request) {
    this.setRequest(request);
    this.jsonOutput = null;
  }*/

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

  /*public String getJsonOutput() {
    return this.jsonOutput;
  }*/

  private static boolean execCallback(String jsonOutput,
                                      String callbackUrl,
                                      Long startTimeInSec,
                                      Long endTimeInSec) {
    if (StringUtils.isBlank(callbackUrl)) return false;
    URL url = null;
    try {
      URIBuilder builder = new URIBuilder(callbackUrl);
      if (startTimeInSec != null && endTimeInSec != null) {
        builder.addParameter(START_DATE_PARAM, Long.toString(startTimeInSec))
               .addParameter(END_DATE_PARAM, Long.toString(endTimeInSec));
      }
      url = builder.build().toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      log.error(String.format("%s (%s)", e, callbackUrl));
    }
    if (url == null) return false;
    try {
      if (sendHttpPost(url, jsonOutput) == HttpStatus.OK_200) {
        return true;
      }
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    }
    return false;
  }

  private static GoogleJsonError makeGoogleJsonError(int code, String message) {
    GoogleJsonError error = new GoogleJsonError();
    error.setCode(code);
    error.setMessage(message);
    return error;
  }

  // Should not return 'null'.
  private static Set<Project> makeProjects(NewsFeederRequest request)
      throws IllegalStateException, GoogleJsonResponseException {
    Set<Project> projects = new TreeSet<>();

    String gmailQuery = new GmailQueryBuilder()
        .addQuery(request.getSearchQuery())
        .addBeforeDate(request.getEndTimeInSec())
        .addAfterDate(request.getStartTimeInSec())
        .addClusters(request.getExternalClusters())
        .build();

    EmailFetcher emailFetcher = new EmailFetcher(EmailSource.GMAIL);
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      emailFetcher.addTask(gmailQuery, tokenEmailPair.getAccessToken(),
                           tokenEmailPair.getEmailStr(),
                           request.getMaxMessages());
    }
    Collection<MimeMessage> messages = null;
    try {
      messages = emailFetcher.startFetch();
    } catch (InterruptedException e) {
      log.error(e);
      e.printStackTrace();
    } finally {
      emailFetcher.shutdown(1000);  // Wait for max. 1 second.
    }
    if (messages == null || messages.isEmpty()) {
      GoogleJsonError error = makeGoogleJsonError(
          HttpStatus.NOT_FOUND_404, "No messages retrieved.");
      log.warn(error.getMessage());
      throw new IllegalStateException(new Gson().toJson(error));
    }

    /*for (MimeMessage message : messages) {
      if (MimeMessageUtil.isSentGmail(message)) {
        try {
          System.out.println(MimeMessageUtil.getMessageId(message));
          System.out.println(message.getSubject());
          System.out.println(new Gson().toJson(MimeMessageUtil.getValidRecipients(message)));
        } catch (MessagingException e) {
          e.printStackTrace();
        }
      }
    }*/

    List<Set<InternetAddress>> externalClusters = request.getExternalClusters();
    if (externalClusters == null) {  // Construct external clusters.
      externalClusters = EmailClusterer.findExternalClusters(
          messages, request.getTokenEmailPairs(), request.getInternalDomain());
    }
    if (externalClusters == null || externalClusters.isEmpty()) {
      GoogleJsonError error = makeGoogleJsonError(
          HttpStatus.NOT_FOUND_404, "No external clusters found.");
      log.warn(error.getMessage());
      throw new IllegalStateException(new Gson().toJson(error));
    }
    EmailClustererUtil.printClusters(externalClusters);

    /*if (beforeThisDate != null && afterThisDate != null) {
      MimeMessageUtil.filterByDateRange(messages, afterThisDate, beforeThisDate);
      SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
      log.debug("{} messages found between {} and {}", messages.size(),
                df.format(afterThisDate), df.format(beforeThisDate));
    }*/

    // Retain emails sent via mailing-list but remove private ones before
    // returning results.
    Collection<MimeMessage> filtered = new EmailFilterer()
        .setRemoveMailListMessages(false)
        .setRemovePrivateMessages(true)
        .filter(messages);

    ProjectFactory projectFactory = new ProjectFactory();
    projectFactory.loadMessages(filtered);

    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
      log.debug("Creating project #{}...", i + 1);

      Project project = projectFactory.createProject(
          request.getInternalDomain(), externalCluster,
          request.isShowPreview(), request.isResolveProjectName());

      if (project != null) projects.add(project);
    }
    return projects;
  }

  /*public void setRequest(NewsFeederRequest request) {
    this.request = request;
  }*/
}