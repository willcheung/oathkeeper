package com.contextsmith.api.service;

import com.contextsmith.api.data.*;
import com.contextsmith.email.cluster.EmailClusterer;
import com.contextsmith.email.cluster.EmailClusterer.ClusteringMethod;
import com.contextsmith.email.provider.GmailQueryBuilder;
import com.contextsmith.email.provider.GoogleServiceProvider;
import com.contextsmith.email.provider.UserEventCrawler;
import com.contextsmith.email.provider.UserInboxCrawler;
import com.contextsmith.email.provider.exchange.AQSBuilder;
import com.contextsmith.email.provider.exchange.ExchangeServiceProvider;
import com.contextsmith.utils.*;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.common.base.Stopwatch;
import com.martiansoftware.validation.Hope;
import com.martiansoftware.validation.UncheckedValidationException;
import microsoft.exchange.webservices.data.autodiscover.exception.AutodiscoverLocalException;
import microsoft.exchange.webservices.data.autodiscover.exception.AutodiscoverUnauthorizedException;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.search.ResolveNameSearchLocation;
import microsoft.exchange.webservices.data.core.service.schema.EmailMessageSchema;
import microsoft.exchange.webservices.data.misc.NameResolutionCollection;
import microsoft.exchange.webservices.data.property.complex.Attachment;
import microsoft.exchange.webservices.data.property.complex.FileAttachment;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Path("newsfeed")
public class NewsFeeder {
    public static final int DEFAULT_HTTP_ERROR_CODE = HttpStatus.BAD_REQUEST_400;
    public static final String JSON_RESPONSE_DIR = "json-responses";
    public static final String JSON_EXT = ".json";
    public static final int MIN_QUERY_TOKEN_LENGTH = 3;
    static final UrlValidator urlValidator = Environment.mode == Mode.production ? UrlValidator.getInstance() : new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.ALLOW_ALL_SCHEMES);
    private static final Logger log = LoggerFactory.getLogger(NewsFeeder.class);
    // For callback.
    private static ExecutorService executor = Executors.newCachedThreadPool();

    public static enum MessageType {EMAIL, EVENT}


    @POST
    @Path("download")
    @Produces(MediaType.WILDCARD)
    @Consumes(MediaType.APPLICATION_JSON)
    public static Response locate(String body) {
        Location location = StringUtil.getGsonInstance().fromJson(body, Location.class);

        MimePartConverter converter = (inputStream, mediaType, name) -> Response.ok((StreamingOutput) outputStream -> {
            IOUtils.copy(inputStream, outputStream);
        }, mediaType).header("Content-Disposition", "attachment=" + name).build();

        switch (location.source.kind) {
            case gmail:
                GoogleServiceProvider googleServiceProvider = new GoogleServiceProvider(location.source.token);
                return locateGmail(location.urn, googleServiceProvider.getGmailService(), converter);
            case exchange:
                try (ExchangeService service = new ExchangeServiceProvider().connectAsUser(location.source.email, location.source.password.toCharArray(), location.source.url)) {
                    return locateExchangeMail(location.urn, service, converter);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

        }
        return Response.status(404).build();
    }

    interface MimePartConverter {
        Response apply(InputStream content, String mimeType, String attachmentName);
    }

    private static Response locateGmail(String urn, Gmail gmailService, MimePartConverter converter) {
        return MimeMessageUtil.fromURN(urn, (provider, email, internalID, fragment) -> {
            try {
                Message message = gmailService.users().messages().get(email, internalID).setFormat("raw").execute();
                byte[] emailBytes = org.apache.commons.codec.binary.Base64.decodeBase64(message.getRaw());
                InputStream rawMessage = new ByteArrayInputStream(emailBytes);
                if (fragment == null) {
                    return converter.apply(rawMessage, "message/rfc822", "e-mail.msg");
                } else { // parse the message, get the attachment TODO support for attachment IDs;
                    MimeMessage msg = new MimeMessage(Session.getDefaultInstance(new Properties()), rawMessage);
                    Part p = MimeMessageUtil.getAttachmentByPath(msg, fragment);
                    return converter.apply(p.getInputStream(), p.getContentType(), p.getFileName());
                }
            } catch (MessagingException | IOException e) {
                e.printStackTrace();
                log.error("Unable to parse message", e);
            }
            return null;
        });
    }

    private static Response locateExchangeMail(String urn, ExchangeService service, MimePartConverter converter) {
        return MimeMessageUtil.fromURN(urn, (provider, email, internalID, fragment) -> {
            try {
                ItemId itemId = new ItemId(internalID);
                microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                        microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(service, itemId, new PropertySet(BasePropertySet.FirstClassProperties, EmailMessageSchema.Attachments));
                for (Attachment att : msg.getAttachments()) {
                    if (att.getId().equals(fragment)) { // found the right attachment
                        att.load();
                        if (att instanceof FileAttachment) {
                            FileAttachment file = (FileAttachment) att;
                            // unfortunately, we have to materialze the whole attachment TODO: change it
                            return converter.apply(new ByteArrayInputStream(file.getContent()), file.getContentType(), file.getFileName());
                        } else {
                            log.warn("Unable to receive attachment " + att);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /** Test curl:
     * curl -X POST -H 'Content-Type:application/json' -d '{"email": "beders@contextsmith.onmicrosoft.com", "password": "askjochen:)" }' http://localhost:8888/newsfeed/auth
     * @param body
     * @return
     */
    @POST
    @Path("auth")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static String authenticate(String body) {
        Source source = StringUtil.getGsonInstance().fromJson(body, Source.class);
        Hope.that(source.email).named("email").isNotNullOrEmpty();
        Hope.that(source.password).named("password").isNotNullOrEmpty();
        Hope.that(source.kind).named("kind").isNotNull();

        switch (source.kind) {
            case exchange: return authenticateExchange(source);
            default: return makeJsonError("Unsupported source:" + source.kind);
        }
    }

    private static String authenticateExchange(Source source) {
        try {
            String url = source.url;

            ExchangeService service = new ExchangeServiceProvider().connectAsUser(source.email, source.password.toCharArray(), url, 10_000);
            url = source.url == null ? service.getUrl().toString() : null;
            // get user information
            NameResolutionCollection resolutions = service.resolveName(source.email, ResolveNameSearchLocation.DirectoryOnly, true);
            com.contextsmith.api.data.Contact contact = null;
            if (resolutions.getCount() > 0) {
                microsoft.exchange.webservices.data.core.service.item.Contact msContact = resolutions.iterator().next().getContact();
                if (msContact != null) {
                    contact = new com.contextsmith.api.data.Contact();
                    contact.givenName = msContact.getGivenName();
                    contact.surName = msContact.getSurname();
                    contact.jobTitle = msContact.getJobTitle();
                }
            }

            return StringUtil.toJson(new AuthResult(true, null, url, contact));
        } catch (AutodiscoverUnauthorizedException aue) {
            log.error("Invalid credentials", aue);
            return StringUtil.toJson(new AuthResult(false, "Invalid credentials", null));
        } catch (AutodiscoverLocalException e) {
            log.error("Unable to auto-discover the URL");
            return StringUtil.toJson(new AuthResult(false, "Unable to auto-discover the URL", "<unknown>"));
        } catch (Exception e) {
            log.error("Can't authenticate ", e);
            return StringUtil.toJson(new AuthResult(false, e.getMessage(), null));
            //return makeJsonError(e.getMessage());
        }

    }

    @POST
    @Path("cluster")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static String clusterEmailsWithCredentials(
            @QueryParam("max") Integer maxMessages,  // Optional
            @QueryParam("preview") Boolean showContent,  // Transient
            @QueryParam("time") Boolean parseTime,  // Transient
            @QueryParam("request") Boolean parseRequest,  // Transient
            @QueryParam("pos_sentiment") Double posSentimentThreshold,
            @QueryParam("neg_sentiment") Double negSentimentThreshold,
            @QueryParam("after") Long startTimeInSec,  // Optional
            @QueryParam("before") Long endTimeInSec,  // Optional
            @QueryParam("in_domain") String internalDomain,  // Optional
            @QueryParam("callback") String callbackUrl, String body) {


        SourceConfiguration config = StringUtil.getGsonInstance().fromJson(body, SourceConfiguration.class);

        // Set thread name at entry point.
        Thread.currentThread().setName("cluster_" + Long.toString(System.currentTimeMillis()));

        // Must have callback URL.
        if (StringUtils.isBlank(callbackUrl)) {
            return makeJsonError("Missing 'callback' parameter.");
        } else if (!urlValidator.isValid(callbackUrl)) {
            return makeJsonError("Invalid 'callback' parameter: " + callbackUrl);
        }

        final MessageType MSG_TYPE = MessageType.EMAIL;

        return createResponse(MSG_TYPE, null, null,
                startTimeInSec, endTimeInSec, internalDomain,
                maxMessages, showContent, parseTime, parseRequest,
                posSentimentThreshold, negSentimentThreshold, null,
                callbackUrl, config);
    }

    static String createResponse(
            MessageType messageType,
            String userQuery,  // Optional
            String subjectToRetain,  // Optional
            Long startTimeInSec,  // Optional
            Long endTimeInSec,  // Optional
            String internalDomain,  // Optional
            Integer maxMessages,  // Transient
            Boolean showContent,  // Transient
            Boolean parseTime,  // Transient
            Boolean parseRequest,  // Transient
            Double posSentimentThreshold,
            Double negSentimentThreshold,
            ClusteringMethod clusteringMethod,  // Optional
            String callbackUrl, SourceConfiguration config) {  // Optional

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
        request.setPosSentimentThreshold(posSentimentThreshold);
        request.setNegSentimentThreshold(negSentimentThreshold);
        request.setClusteringMethod(clusteringMethod);
        request.setSubjectToRetain(subjectToRetain);

        Optional<UncheckedValidationException> validationResult = Arrays.stream(config.sources).map(source -> {
            try {
                Hope.that(source.kind).named("kind").isNotNull();
                switch (source.kind) {
                    case exchange:
                        Hope.that(source.email).named("email").isNotNullOrEmpty();
                        Hope.that(source.password).named("password").isNotNullOrEmpty();
                        break;
                    case gmail:
                        Hope.that(source.email).isNotNullOrEmpty();
                        Hope.that(source.token).isNotNullOrEmpty();
                        break;
                    default:
                        throw new UncheckedValidationException("Unknown kind");
                }
            } catch (UncheckedValidationException ue) {
                return ue;
            }
            return null;
        }).filter(Objects::nonNull).findAny();
        if (validationResult.isPresent()) {
            return makeJsonError(validationResult.get().getMessage());
        }
        // Parsing jsons.
        request.setSourceConfiguration(config); // also sets external members
        //request.setExternalClustersFromJson(externalClusterJson);

        // Verify user email and access token.
    /*if (request.getTokenEmailPairs() == null ||
        request.getTokenEmailPairs().isEmpty()) {
      return makeJsonError("Missing 'token_emails' parameter.");
    }*/
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
        } else  {
            // extract internal domain from first source in request
            String intDomain = InternetAddressUtil.getAddressDomain(request.getSourceConfiguration().sources[0].email);
            request.setInternalDomain(intDomain);
            //return makeJsonError("Missing company's internal domain.");
        }
        log.info("[{}] sent a request: {}", request.getInternalDomain(), request.toString());

        if (StringUtils.isBlank(callbackUrl)) {  // No callback. Blocking
            String jsonOutput = processRequest(request);
            return jsonOutput;
        } else {  // Non-blocking
            executor.submit(new ProcessRequestCallable(
                    request, Thread.currentThread().getName()));
            return "";
        }
    }

    @POST
    @Path("event")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static String gatherEventsWithCredentials(
            @QueryParam("after") Long startTimeInSec,
            @QueryParam("before") Long endTimeInSec,
            @QueryParam("max") Integer maxMessages,  // Optional
            @QueryParam("in_domain") String internalDomain, String body) {
        // Set thread name at entry point.
        Thread.currentThread().setName("event_" + Long.toString(System.currentTimeMillis()));
        SourceConfiguration config = StringUtil.getGsonInstance().fromJson(body, SourceConfiguration.class);
        // must have external clusters set
        Hope.that(config.rawExternalClusters).named("external_clusters").isNotNull();

        // Must have 'after' and 'before'.
        if (startTimeInSec == null) {
            return makeJsonError("Missing 'after' parameter.");
        } else if (endTimeInSec == null) {
            return makeJsonError("Missing 'before' parameter.");
        }

        final MessageType MSG_TYPE = MessageType.EVENT;
        return createResponse(MSG_TYPE, null, null,
                startTimeInSec, endTimeInSec, internalDomain,
                maxMessages, false, false, false, null, null, null, null, config);
    }


    @GET
    @Path("thread")  // unused?
    @Produces(MediaType.APPLICATION_JSON)
    public static String retrieveThread(
            @QueryParam("subject") String subjectToRetain,
            @QueryParam("max") Integer maxMessages,  // Optional
            @QueryParam("time") Boolean parseTime,   // Optional
            @QueryParam("request") Boolean parseRequest,   // Optional
            @QueryParam("pos_sentiment") Double posSentimentThreshold,
            @QueryParam("neg_sentiment") Double negSentimentThreshold,
            @QueryParam("after") Long startTimeInSec,  // Optional
            @QueryParam("before") Long endTimeInSec,  // Optional
            @QueryParam("in_domain") String internalDomain,
            @QueryParam("provider") String provider) {  // Optional
        // Set thread name at entry point.
        Thread.currentThread().setName("thread_" + Long.toString(System.currentTimeMillis()));

        // Must have subject.
        if (StringUtils.isBlank(subjectToRetain)) {
            return makeJsonError("Missing 'subject' parameter.");
        }

        final MessageType MSG_TYPE = MessageType.EMAIL;
        final boolean SHOW_CONTENT = true;
        return createResponse(MSG_TYPE, null, subjectToRetain,
                startTimeInSec, endTimeInSec,
                internalDomain, maxMessages, SHOW_CONTENT, parseTime,
                parseRequest, posSentimentThreshold,
                negSentimentThreshold, null, null, null);
    }

    @POST
    @Path("search")
    @Produces(MediaType.APPLICATION_JSON)
    public static String searchEmails(
            @QueryParam("max") Integer maxMessages,  // Optional
            @QueryParam("query") String searchQuery,  // Optional
            @QueryParam("time") Boolean parseTime,   // Optional
            @QueryParam("request") Boolean parseRequest,   // Optional
            @QueryParam("pos_sentiment") Double posSentimentThreshold,
            @QueryParam("neg_sentiment") Double negSentimentThreshold,
            @QueryParam("after") Long startTimeInSec,  // Optional
            @QueryParam("before") Long endTimeInSec,  // Optional
            @QueryParam("in_domain") String internalDomain,  // Optional
            String body) {  // Optional
        // Set thread name at entry point.
        Thread.currentThread().setName("search_" + Long.toString(System.currentTimeMillis()));

        SourceConfiguration config = StringUtil.getGsonInstance().fromJson(body, SourceConfiguration.class);

        final MessageType MSG_TYPE = MessageType.EMAIL;
        final boolean SHOW_CONTENT = true;
        return createResponse(MSG_TYPE, searchQuery, null,
                startTimeInSec, endTimeInSec,
                internalDomain, maxMessages, SHOW_CONTENT, parseTime,
                parseRequest, posSentimentThreshold,
                negSentimentThreshold, null, null, config);
    }

    protected static String processRequest(NewsFeederRequest request) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Set<Project> projects = null;
        try {
            switch (request.getMessageType()) {
                case EMAIL:
                    projects = makeEmailProjects(request);
                    break;
                case EVENT:
                    projects = makeEventProjects(request);
                    break;
                default:
                    return makeJsonError(
                            "Unknown message type: " + request.getMessageType().toString(),
                            DEFAULT_HTTP_ERROR_CODE);
            }
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError error = e.getDetails();
            if (error != null) return StringUtil.toJson(error);
            Integer code = StringUtil.parseLeadingIntegers(e.getMessage());
            return makeJsonError(e.getMessage(), code);
        } catch (Exception e) {
            e.printStackTrace();
            return makeJsonError(e.getMessage(), 500);
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
        if (StringUtils.isNotBlank(request.getSubjectToRetain())) {  // not set when refreshing the inbox. Search feature not being surfaced
            String subjectToRetain =
                    MimeMessageUtil.normalizeSubject(request.getSubjectToRetain());
            subjectRetainPattern = Pattern.compile("^\\Q" + subjectToRetain + "\\E$");
            subjectQuery = String.format("subject:\"%s\"", subjectToRetain);
        }
        UserInboxCrawler inboxCrawler = new UserInboxCrawler(accessToken -> new GoogleServiceProvider(accessToken), () -> new ExchangeServiceProvider());

        String finalSubjectQuery = subjectQuery; // needed for lambda
        List<InternetAddress> aliases = Arrays.stream(request.getSourceConfiguration().sources).map(source -> {
            switch (source.kind) {
                case gmail: {
                    String gmailQuery = new GmailQueryBuilder()
                            .addQuery(request.getUserQuery())
                            .addQuery(finalSubjectQuery)
                            .addBeforeDate(request.getEndTimeInSec())
                            .addAfterDate(request.getStartTimeInSec())
                            .addClusters(request.getExternalClusters())
                            .build();
                    inboxCrawler.addGmailTask(gmailQuery, source.token, source.email, request.getMaxMessages());
                    return source.getEmailAddress(); // needed for clustering later
                }
                case exchange: {
                    String exchangeQuery = request.hasTimeFilter() ? new AQSBuilder().sentBetween(new Date(request.getStartTimeInSec() * 1000), new Date(request.getEndTimeInSec() * 1000)).toQuery()
                            : "";
                    if (request.getUserQuery() != null) {
                        // TODO translate user query
                        throw new RuntimeException("No user query supported");
                    }
                    inboxCrawler.addExchangeTask(exchangeQuery, source, request.getExternalClusters(), request.getMaxMessages());
                    return source.getEmailAddress();
                }
            }
            return null;
        }).collect(Collectors.toList());

        Set<Project> projects = new TreeSet<>();
        // Throws GoogleJsonResponseException
        inboxCrawler.setSubjectRetainPattern(subjectRetainPattern).startCrawl();
        if (inboxCrawler.getMessages() == null ||
                inboxCrawler.getMessages().isEmpty()) {
            log.warn("No messages retrieved for " + request);
            return projects;
        }

        List<Set<InternetAddress>> externalClusters = request.getExternalClusters();
        if (externalClusters == null) {  // Construct external clusters.
            externalClusters = EmailClusterer.findExternalClusters(
                    inboxCrawler.getUnfilteredMimeMessages(),
                    aliases,
                    request.getInternalDomain(),
                    request.getClusteringMethod());
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

        createProjects(request, projects, externalClusters, projectFactory);

        // Post-processing.
        if (request.isShowContent()) {
            Stopwatch processWatch = Stopwatch.createStarted();
            processContent(projects, request);
            log.info("E-mail content processing time: {}", processWatch);
        }
        return projects;
    }

    private static void createProjects(NewsFeederRequest request, Set<Project> projects, List<Set<InternetAddress>> externalClusters, ProjectFactory projectFactory) {
        for (int i = 0; i < externalClusters.size(); ++i) {
            Set<InternetAddress> externalCluster = externalClusters.get(i);
            log.debug("Creating project #{}...", i + 1);
            Project project = projectFactory.createProject(
                    request.getInternalDomain(),
                    externalCluster);
            if (project != null) projects.add(project);
        }
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
            log.warn("No event start time and end time defined.");
            return projects;
        }

        UserEventCrawler eventCrawler = new UserEventCrawler(accessToken -> new GoogleServiceProvider(accessToken), () -> new ExchangeServiceProvider());
        List<InternetAddress> aliases = Arrays.stream(request.getSourceConfiguration().sources).map(source -> {
            switch (source.kind) {
                case gmail: {
                    eventCrawler.addGmailTask(source.token,
                            source.email,
                            request.getStartTimeInSec(),
                            request.getEndTimeInSec(),
                            request.getMaxMessages());
                    return source.getEmailAddress(); // needed for clustering later
                }
                case exchange: {
                    eventCrawler.addExchangeTask(source, request.getStartTimeInSec() * 1000L, request.getEndTimeInSec() * 1000, request.getMaxMessages());
                    return source.getEmailAddress();
                }
            }
            return null;
        }).collect(Collectors.toList());
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

        createProjects(request, projects, externalClusters, projectFactory);
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

        for (Iterator<Project> i = projects.iterator(); i.hasNext(); ) {
            Project project = i.next();
            if (searchPattern != null) {
                project.setSearchPattern(searchPattern.pattern());
            }
            for (Iterator<Conversation> j = project.getConversations().iterator();
                 j.hasNext(); ) {
                Conversation conversation = j.next();

                for (Iterator<Messageable> k = conversation.getMessages().iterator();
                     k.hasNext(); ) {
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