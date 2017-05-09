package com.contextsmith.api.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.mail.internet.InternetAddress;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.contextsmith.utils.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.data.Conversation;
import com.contextsmith.api.data.EmailMessage;
import com.contextsmith.api.data.Messageable;
import com.contextsmith.api.data.Project;
import com.contextsmith.api.data.ProjectFactory;
import com.contextsmith.email.cluster.EmailClusterer;
import com.contextsmith.email.provider.GmailQueryBuilder;
import com.contextsmith.email.provider.UserEventCrawler;
import com.contextsmith.email.provider.UserInboxCrawler;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;

@Path("newsfeed")
public class NewsFeeder {
  public static enum MessageType { EMAIL, EVENT }

  static final Logger log = LoggerFactory.getLogger(NewsFeeder.class);;
  public static final int DEFAULT_HTTP_ERROR_CODE = HttpStatus.BAD_REQUEST_400;
  public static final String JSON_RESPONSE_DIR = "json-responses";
  public static final String JSON_EXT = ".json";
  public static final int MIN_QUERY_TOKEN_LENGTH = 3;
  static final UrlValidator urlValidator = Environment.mode == Mode.production ? UrlValidator.getInstance() : new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.ALLOW_ALL_SCHEMES);
  // For callback.
  private static ExecutorService executor = Executors.newCachedThreadPool();

  @GET
  @Path("cluster")
  @Produces(MediaType.APPLICATION_JSON)
  public static String clusterEmails(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("preview") Boolean showContent,  // Transient
      @QueryParam("time") Boolean parseTime,  // Transient
      @QueryParam("request") Boolean parseRequest,  // Transient
      @QueryParam("pos_sentiment") Double posSentimentThreshold,
      @QueryParam("neg_sentiment") Double negSentimentThreshold,
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("callback") String callbackUrl) {
    // Set thread name at entry point.
    Thread.currentThread().setName(Long.toString(System.currentTimeMillis()));

    // Must have callback URL.
    if (StringUtils.isBlank(callbackUrl)) {
      return makeJsonError("Missing 'callback' parameter.");
    } else if (!urlValidator.isValid(callbackUrl)) {
      return makeJsonError("Invalid 'callback' parameter: " + callbackUrl);
    }

    final MessageType MSG_TYPE = MessageType.EMAIL;
    return createResponse(MSG_TYPE, tokenEmailJson, null, null, null,
                          startTimeInSec, endTimeInSec, internalDomain,
                          maxMessages, showContent, parseTime, parseRequest,
                          posSentimentThreshold, negSentimentThreshold,
                          callbackUrl);
  }

  @GET
  @Path("create")
  @Produces(MediaType.APPLICATION_JSON)
  public static String createResponse(
      @QueryParam("type") MessageType messageType,
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("query") String userQuery,  // Optional
      @QueryParam("subject") String subjectToRetain,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson,  // Optional
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("max") Integer maxMessages,  // Transient
      @QueryParam("preview") Boolean showContent,  // Transient
      @QueryParam("time") Boolean parseTime,  // Transient
      @QueryParam("request") Boolean parseRequest,  // Transient
//      @QueryParam("project_name") Boolean resolveProjectName,  // Transient
      @QueryParam("pos_sentiment") Double posSentimentThreshold,
      @QueryParam("neg_sentiment") Double negSentimentThreshold,
      @QueryParam("callback") String callbackUrl) {  // Optional
    // Set thread name at entry point.
    Thread.currentThread().setName(Long.toString(System.currentTimeMillis()));

    // Verify callback URL (if exist).
    if (StringUtils.isNotBlank(callbackUrl) &&
        !urlValidator.isValid(callbackUrl)) {
      return makeJsonError("Invalid callback URL: " + callbackUrl);
    }

    final NewsFeederRequest request = new NewsFeederRequest();
    request.setMessageType(messageType);
    request.setCallbackUrl(callbackUrl);
    request.setStartTimeInSec(startTimeInSec);
    request.setEndTimeInSec(endTimeInSec);
    request.setUserQuery(userQuery);
    request.setMaxMessages(maxMessages);
    request.setShowContent(showContent);
    request.setParseTime(parseTime);
    request.setParseRequest(parseRequest);
//    request.setResolveProjectName(resolveProjectName);
    request.setPosSentimentThreshold(posSentimentThreshold);
    request.setNegSentimentThreshold(negSentimentThreshold);
    request.setSubjectToRetain(subjectToRetain);

    // Parsing jsons.
    request.setTokenEmailPairsFromJson(tokenEmailJson);
    request.setExternalClustersFromJson(externalClusterJson);

    // Verify user email and access token.
    if (request.getTokenEmailPairs() == null ||
        request.getTokenEmailPairs().isEmpty()) {
      return makeJsonError("Missing 'token_emails' parameter.");
    }
    String tokenEmailDomain = null;
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      String emailStr = tokenEmailPair.getEmailStr();
      if (!EmailValidator.getInstance().isValid(emailStr)) {
        return makeJsonError("Invalid email address: " + emailStr);
      }
      if (StringUtils.isBlank(tokenEmailPair.getAccessToken())) {
        return makeJsonError("Missing access token for email: " + emailStr);
      }
      String domain = InternetAddressUtil.getAddressDomain(emailStr);
      if (tokenEmailDomain == null) {
        tokenEmailDomain = domain;
      } else if (!tokenEmailDomain.equals(domain)) {
        return makeJsonError(String.format(
            "Token emails do not have identical domains: %s vs %s",
            tokenEmailDomain, domain));
      }
    }

    // Verify external member email address (if specified).
    if (request.getExternalClusters() != null) {
      for (Set<InternetAddress> addresses : request.getExternalClusters()) {
        for (InternetAddress address : addresses) {
          String emailStr = address.getAddress();
          if (!EmailValidator.getInstance().isValid(emailStr)) {
            return makeJsonError("Invalid email address: " + address);
          }
        }
      }
    }

    // Retrieve internal domain from user parameter first.
    // If not found, then retrieve from user token's email address.
    if (StringUtils.isNotBlank(internalDomain)) {
      request.setInternalDomain(internalDomain);
    } else if (StringUtils.isNotBlank(tokenEmailDomain)) {
      request.setInternalDomain(tokenEmailDomain);
    } else {
      return makeJsonError("Missing company's internal domain.");
    }
    log.info("[{}] sent a request: {}",
             request.getInternalDomain(),
             request.toString());

    if (StringUtils.isBlank(callbackUrl)) {  // No callback. Blocking
      String jsonOutput = processRequest(request);
      return jsonOutput;
    } else {  // Non-blocking
      executor.submit(new ProcessRequestCallable(
          request, Thread.currentThread().getName()));
      return "";
    }
  }

  @GET
  @Path("event")
  @Produces(MediaType.APPLICATION_JSON)
  public static String gatherEvents(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("ex_clusters") String externalClusterJson,
      @QueryParam("after") Long startTimeInSec,
      @QueryParam("before") Long endTimeInSec,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("in_domain") String internalDomain) {
    // Set thread name at entry point.
    Thread.currentThread().setName(Long.toString(System.currentTimeMillis()));

    // Must have 'after' and 'before'.
    if (startTimeInSec == null) {
      return makeJsonError("Missing 'after' parameter.");
    } else if (endTimeInSec == null) {
      return makeJsonError("Missing 'before' paramter.");
    }
    String error = checkExternalCluster(externalClusterJson);
    if (error != null) return error;

    final MessageType MSG_TYPE = MessageType.EVENT;
    return createResponse(MSG_TYPE, tokenEmailJson, null, null, externalClusterJson,
                          startTimeInSec, endTimeInSec, internalDomain,
                          maxMessages, false, false, false, null, null, null);
  }

  @GET
  @Path("thread")
  @Produces(MediaType.APPLICATION_JSON)
  public static String retrieveThread(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("subject") String subjectToRetain,
      @QueryParam("ex_clusters") String externalClusterJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("time") Boolean parseTime,   // Optional
      @QueryParam("request") Boolean parseRequest,   // Optional
      @QueryParam("pos_sentiment") Double posSentimentThreshold,
      @QueryParam("neg_sentiment") Double negSentimentThreshold,
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain) {  // Optional
    // Set thread name at entry point.
    Thread.currentThread().setName(Long.toString(System.currentTimeMillis()));

    // Must have external members.
    String error = checkExternalCluster(externalClusterJson);
    if (error != null) return error;

    // Must have subject.
    if (StringUtils.isBlank(subjectToRetain)) {
      return makeJsonError("Missing 'subject' parameter.");
    }

    final MessageType MSG_TYPE = MessageType.EMAIL;
    final boolean SHOW_CONTENT = true;
    return createResponse(MSG_TYPE, tokenEmailJson, null, subjectToRetain,
                          externalClusterJson, startTimeInSec, endTimeInSec,
                          internalDomain, maxMessages, SHOW_CONTENT, parseTime,
                          parseRequest, posSentimentThreshold,
                          negSentimentThreshold, null);
  }

  @GET
  @Path("search")
  @Produces(MediaType.APPLICATION_JSON)
  public static String searchEmails(
      @QueryParam("token_emails") String tokenEmailJson,
      @QueryParam("max") Integer maxMessages,  // Optional
      @QueryParam("query") String searchQuery,  // Optional
      @QueryParam("time") Boolean parseTime,   // Optional
      @QueryParam("request") Boolean parseRequest,   // Optional
      @QueryParam("pos_sentiment") Double posSentimentThreshold,
      @QueryParam("neg_sentiment") Double negSentimentThreshold,
      @QueryParam("after") Long startTimeInSec,  // Optional
      @QueryParam("before") Long endTimeInSec,  // Optional
      @QueryParam("in_domain") String internalDomain,  // Optional
      @QueryParam("ex_clusters") String externalClusterJson) {  // Optional
    // Set thread name at entry point.
    Thread.currentThread().setName(Long.toString(System.currentTimeMillis()));

    final MessageType MSG_TYPE = MessageType.EMAIL;
    final boolean SHOW_CONTENT = true;
    return createResponse(MSG_TYPE, tokenEmailJson, searchQuery, null,
                          externalClusterJson, startTimeInSec, endTimeInSec,
                          internalDomain, maxMessages, SHOW_CONTENT, parseTime,
                          parseRequest, posSentimentThreshold,
                          negSentimentThreshold, null);
  }

  protected static String processRequest(NewsFeederRequest request) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    Set<Project> projects = null;
    try {
      switch (request.getMessageType()) {
      case EMAIL: projects = makeEmailProjects(request); break;
      case EVENT: projects = makeEventProjects(request); break;
      default: return makeJsonError(
          "Unknown message type: " + request.getMessageType().toString(),
          DEFAULT_HTTP_ERROR_CODE);
      }
   } catch (GoogleJsonResponseException e) {
      GoogleJsonError error = e.getDetails();
      if (error != null) return StringUtil.toJson(error);
      Integer code = StringUtil.parseLeadingIntegers(e.getMessage());
      return makeJsonError(e.getMessage(), code);
    }
    // No errors occurred at this point.
    String jsonOutput = StringUtil.toJson(projects);
    log.info("Total elapsed time: {}", stopwatch);

    try {
      // Log json output to another file.
      storeResponse(jsonOutput);
    } catch (IOException e) {
      log.error(e.toString());
      e.printStackTrace();
    }
    return jsonOutput;
  }

  private static String checkExternalCluster(String externalClusterJson) {
    // Must have external members.
    if (StringUtils.isBlank(externalClusterJson)) {
      return makeJsonError("Missing 'ex_clusters' parameter.");
    } else {
      List<Set<InternetAddress>> clusters =
          NewsFeederRequest.parseExternalClusterJson(externalClusterJson);
      if (clusters == null || clusters.isEmpty()) {
        return makeJsonError(
            "Invalid 'ex_clusters' parameter: " + externalClusterJson);
      }
    }
    return null;
  }

  // Should not return 'null'.
  private static Set<Project> makeEmailProjects(NewsFeederRequest request)
      throws GoogleJsonResponseException {
    // Generate regex to retain a particular subject, if needed.
    Pattern subjectRetainPattern = null;
    String subjectQuery = null;
    if (StringUtils.isNotBlank(request.getSubjectToRetain())) {
      String subjectToRetain =
          MimeMessageUtil.normalizeSubject(request.getSubjectToRetain());
      subjectRetainPattern = Pattern.compile("^\\Q" + subjectToRetain + "\\E$");
      subjectQuery = String.format("subject:\"%s\"", subjectToRetain);
    }

    String gmailQuery = new GmailQueryBuilder()
        .addQuery(request.getUserQuery())
        .addQuery(subjectQuery)
        .addBeforeDate(request.getEndTimeInSec())
        .addAfterDate(request.getStartTimeInSec())
        .addClusters(request.getExternalClusters())
        .build();

    UserInboxCrawler inboxCrawler = new UserInboxCrawler();
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      inboxCrawler.addTask(gmailQuery,
                           tokenEmailPair.getAccessToken(),
                           tokenEmailPair.getEmailStr(),
                           request.getMaxMessages());
    }

    Set<Project> projects = new TreeSet<>();
    // Throws GoogleJsonResponseException
    inboxCrawler.setSubjectRetainPattern(subjectRetainPattern).startCrawl();
    if (inboxCrawler.getMessages() == null ||
        inboxCrawler.getMessages().isEmpty()) {
      log.warn("No messages retrieved.");
      return projects;
    }

    List<Set<InternetAddress>> externalClusters = request.getExternalClusters();
    if (externalClusters == null) {  // Construct external clusters.
      externalClusters = EmailClusterer.findExternalClusters(
          inboxCrawler.getUnfilteredMimeMessages(),
          request.getTokenEmailPairs(),
          request.getInternalDomain());
    }
    if (externalClusters == null || externalClusters.isEmpty()) {
      log.warn("No external clusters found.");
      return projects;
    }
    for (Set<InternetAddress> addresses : externalClusters) {
      inboxCrawler.getEnResolver().resolve(addresses);
    }
    EmailClustererUtil.printClusters(externalClusters);

    ProjectFactory projectFactory = new ProjectFactory();
    projectFactory.loadMessages(inboxCrawler.getMessages());

    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
      log.debug("Creating project #{}...", i + 1);
      Project project = projectFactory.createProject(
          request.getInternalDomain(),
          externalCluster);
      if (project != null) projects.add(project);
    }

    // Post-processing.
    if (request.isShowContent()) {
      Stopwatch processWatch = Stopwatch.createStarted();
      processContent(projects, request);
      log.info("E-mail content processing time: {}", processWatch);
    }
    return projects;
  }

  // Should not return 'null'.
  private static Set<Project> makeEventProjects(NewsFeederRequest request)
      throws GoogleJsonResponseException {
    Set<Project> projects = new TreeSet<>();

    List<Set<InternetAddress>> externalClusters = request.getExternalClusters();
    if (externalClusters == null || externalClusters.isEmpty()) {
      log.warn("No external clusters found.");
      return projects;
    }

    if (request.getStartTimeInSec() == null ||
        request.getEndTimeInSec() == null) {
      log.warn("No event start tiem and end time defined.");
      return projects;
    }

    UserEventCrawler eventCrawler = new UserEventCrawler();
    for (TokenEmailPair tokenEmailPair : request.getTokenEmailPairs()) {
      eventCrawler.addTask(tokenEmailPair.getAccessToken(),
                           tokenEmailPair.getEmailStr(),
                           request.getStartTimeInSec(),
                           request.getEndTimeInSec(),
                           request.getMaxMessages());
    }

    // Throws GoogleJsonResponseException
    eventCrawler.startCrawl();
    if (eventCrawler.getMessages() == null ||
        eventCrawler.getMessages().isEmpty()) {
      log.warn("No events retrieved.");
      return projects;
    }

    for (Set<InternetAddress> addresses : externalClusters) {
      eventCrawler.getEnResolver().resolve(addresses);
    }
    EmailClustererUtil.printClusters(externalClusters);

    ProjectFactory projectFactory = new ProjectFactory();
    projectFactory.loadMessages(eventCrawler.getMessages());

    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
      log.debug("Creating project #{}...", i + 1);

//      Project project = projectFactory.createProject(
//          request.getInternalDomain(),
//          externalCluster,
//          request.isShowContent(),
//          request.isParseTime(),
//          request.isParseRequest(),
//          request.isResolveProjectName(),
//          request.getPosSentimentThreshold(),
//          request.getNegSentimentThreshold(),
//          searchPattern);
      Project project = projectFactory.createProject(
          request.getInternalDomain(),
          externalCluster);
      if (project != null) projects.add(project);
    }
    return projects;
  }

  private static String makeJsonError(String message) {
    return makeJsonError(message, null);
  }

  private static String makeJsonError(String message, Integer code) {
    if (code == null || HttpStatus.getCode(code) == null) {
      code = DEFAULT_HTTP_ERROR_CODE;
    }
    log.error(String.format("%d Error: %s", code, message));

    GoogleJsonError error = new GoogleJsonError();
    error.setCode(code);
    error.setMessage(message);
    return StringUtil.toJson(error);
  }

  private static void processContent(Collection<Project> projects,
                                     NewsFeederRequest request) {
    Pattern searchPattern = null;
    if (StringUtils.isNotBlank(request.getUserQuery())) {
      List<String> tokens = StringUtil.tokenizeQuery(request.getUserQuery(),
          MIN_QUERY_TOKEN_LENGTH);
      if (!tokens.isEmpty()) {
        searchPattern = StringUtil.makeLookupPattern(tokens, true);
      }
    }

    int numKeywords = Integer.MAX_VALUE;
    if (searchPattern != null) {
      numKeywords = StringUtils.countMatches(searchPattern.pattern(), '|') + 1;
    }

    for (Iterator<Project> i = projects.iterator(); i.hasNext();) {
      Project project = i.next();
      if (searchPattern != null) {
        project.setSearchPattern(searchPattern.pattern());
      }
      for (Iterator<Conversation> j = project.getConversations().iterator();
          j.hasNext();) {
        Conversation conversation = j.next();

        for (Iterator<Messageable> k = conversation.getMessages().iterator();
            k.hasNext();) {
          EmailMessage email = (EmailMessage) k.next();
          email.processContent(
              request.isParseTime(),
              request.isParseRequest(),
              request.getPosSentimentThreshold(),
              request.getNegSentimentThreshold(),
              searchPattern);

          if (searchPattern != null) {
            // Must have search results here or discard this context message.
            if (email.getSearchAnnotations() == null ||
                AnnotationUtil.uniqueText(email.getSearchAnnotations()).size() < numKeywords) {
              k.remove();
              continue;
            }
          }
        }
        if (conversation.getMessages().isEmpty()) {
          j.remove();
          continue;
        }
      }
      if (project.getConversations().isEmpty()) {
        i.remove();
        continue;
      }
    }
  }

  private static void storeResponse(String content) throws IOException {
    File responseDir = new File(JSON_RESPONSE_DIR);
    if (!responseDir.isDirectory()) {
      log.info("Creating directory: {}", responseDir.getAbsolutePath());
      FileUtils.forceMkdir(responseDir);
    }
    File responseFile = new File(responseDir,
                                 Thread.currentThread().getName() + JSON_EXT);
    log.debug("Storing json output to: {}", responseFile.getAbsolutePath());
    FileUtils.writeStringToFile(responseFile, content, StandardCharsets.UTF_8);
  }
}