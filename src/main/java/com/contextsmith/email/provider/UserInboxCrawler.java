package com.contextsmith.email.provider;

import com.contextsmith.api.data.EmailMessage;
import com.contextsmith.api.data.Messageable;
import com.contextsmith.api.service.Source;
import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.email.provider.exchange.ExchangeServiceProvider;
import com.contextsmith.email.provider.exchange.MimeMessageProducer;
import com.contextsmith.email.provider.office365.MSGraphMimeMessageProducer;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;
import microsoft.exchange.webservices.data.core.ExchangeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO(rcwang): Make executor service static.
public class UserInboxCrawler {
    public static final String DEFAULT_GMAIL_USER = "me";
    private static final Logger log = LoggerFactory.getLogger(UserInboxCrawler.class);
    public static int SHUTDOWN_WAIT_TIME_IN_MILLIS = 1000;
    private Supplier<ExchangeServiceProvider> exchangeServiceProvider;
    private EmailFilterer emailFilterer;
    private EmailNameResolver enResolver;
    private ExecutorService executor;
    private Collection<Messageable> messages;
    private Collection<MimeMessage> unfilteredMimeMessages;
    private Set<Callable<List<MimeMessage>>> callables;
    private List<Future<List<MimeMessage>>> futures;
    private Pattern subjectRetainPattern;

    public UserInboxCrawler() {
        this.executor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat(
                        Thread.currentThread().getName() + ".%d").build());
        this.callables = new HashSet<>();
        this.emailFilterer = new EmailFilterer();
        this.enResolver = new EmailNameResolver();
        this.messages = new ArrayList<>();
    }

    public void addGmailTask(Function<String, GoogleServiceProvider> googleServiceProvider, final String query, final String accessToken,
                             final String email, final int maxMessages) {
        this.callables.add(new Callable<List<MimeMessage>>() {
            @Override
            public List<MimeMessage> call() throws Exception {
                List<MimeMessage> messages = fetchGmails(query, googleServiceProvider.apply(accessToken), maxMessages);
                if (messages == null) return null;

                // Insert user's email address into MimeMessage.
                if (!StringUtils.isBlank(email)) {
                    for (MimeMessage message : messages) {
                        try {
                            message.addHeader(MimeMessageUtil.SOURCE_INBOX_HEADER, email);
                        } catch (MessagingException e) {
                            log.error(e.toString());
                            e.printStackTrace();
                        }
                    }
                }
                return messages;
            }
        });
    }

    public void addExchangeTask(Supplier<ExchangeServiceProvider> exchangeServiceProvider, String exchangeQuery, Source source, List<Set<InternetAddress>> externalClusters, int maxMessages) {
        this.callables.add(() -> {
            ExchangeService exchangeService = exchangeServiceProvider.get().connectAsUser(source.email, source.password.toCharArray(), source.url);
            MimeMessageProducer producer = new MimeMessageProducer(exchangeService).query(exchangeQuery).maxMessages(maxMessages);
            return produceMessages(source, externalClusters, producer.asFlux());
        });
    }

    public void addOffice365Task(Function<String, IGraphServiceClient> office365Provider, String exchangeQuery, Source source, List<Set<InternetAddress>> externalClusters, int maxMessages) {
        this.callables.add(() -> {
            IGraphServiceClient client = office365Provider.apply(source.token);
            MSGraphMimeMessageProducer producer = new MSGraphMimeMessageProducer(client).query(exchangeQuery).maxMessages(maxMessages);
            return produceMessages(source, externalClusters, producer.asFlux());
        });
    }

    /** Measure flux and time for producing messages from Exchange or Outlook. */
    private List<MimeMessage> produceMessages(Source source, List<Set<InternetAddress>> externalClusters, Flux<MimeMessage> mimeMessageFlux) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Used Memory before: " + usedMemoryBefore / 1_000_000);
        long startTime = System.currentTimeMillis();

        List<MimeMessage> mimeMessages = filterMessages(source, externalClusters, mimeMessageFlux);

        System.out.println("Received " + mimeMessages.size() + " messages");
        long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Memory increased: " + (usedMemoryAfter - usedMemoryBefore) / 1_000_000);
        System.out.println("Duration (s): " + (System.currentTimeMillis() - startTime) / 1000.0);
        return mimeMessages;
    }

    List<MimeMessage> filterMessages(Source source, List<Set<InternetAddress>> externalClusters, Flux<MimeMessage> mimeMessageFlux) {
        if (externalClusters != null) { // filter for known addresses
            HashSet<InternetAddress> allAddresses = externalClusters.stream()
                    .flatMap(Collection::stream).collect(Collectors.toCollection(HashSet::new));
            mimeMessageFlux = mimeMessageFlux.filter(msg -> {
                try {
                    Address[] allRecipients = msg.getAllRecipients();
                    Address[] from = msg.getFrom();
                    return allRecipients != null && (findAny(from, allAddresses) || findAny(allRecipients, allAddresses));
                } catch (MessagingException e) {
                    log.error("Unable to parse address", e);
                }
                return false;
            });
        }
        return mimeMessageFlux.map(msg -> {
            try {
                msg.addHeader(MimeMessageUtil.SOURCE_INBOX_HEADER, source.email);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
            return msg;
        }).collectList().block();
    }

    private boolean findAny(Address[] addresses, HashSet<InternetAddress> allAddresses) {
        if (addresses == null) {
            return false;
        }
        for (Address a : addresses) {
            if (allAddresses.contains(a))
                return true;
        }
        return false;
    }

    public EmailNameResolver getEnResolver() {
        return this.enResolver;
    }

    public Collection<Messageable> getMessages() {
        return this.messages;
    }

    public Pattern getSubjectRetainPattern() {
        return this.subjectRetainPattern;
    }

    public UserInboxCrawler setSubjectRetainPattern(Pattern subjectRetainPattern) {
        this.subjectRetainPattern = subjectRetainPattern;
        return this;
    }

    public Collection<MimeMessage> getUnfilteredMimeMessages() {
        return this.unfilteredMimeMessages;
    }

    public void shutdown(long waitTimeInMillis) {
        try {
            this.executor.shutdown();
            this.executor.awaitTermination(waitTimeInMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.toString());
        } finally {
            if (!this.executor.isTerminated()) {
                log.warn("Force killing non-finished tasks.");
            }
            this.executor.shutdownNow();
        }
    }

    public void startCrawl() throws GoogleJsonResponseException {
        this.messages.clear();
        this.enResolver.reset();
        this.unfilteredMimeMessages = null;

        List<Future<List<MimeMessage>>> inboxFutures = null;
        try {
            inboxFutures = this.executor.invokeAll(this.callables);
        } catch (InterruptedException e) {
            log.error(e.toString());
        } finally {
            this.callables.clear();
            this.shutdown(SHUTDOWN_WAIT_TIME_IN_MILLIS);
        }
        this.unfilteredMimeMessages = mergeMessageWithSameId(inboxFutures);

        Collection<MimeMessage> filteredMimeMessages = this.emailFilterer
                .setSubjectRetainPattern(this.subjectRetainPattern)
                .setRemoveMailListMessages(false)
                .setRemovePrivateMessages(false)
                .filter(this.unfilteredMimeMessages);

        this.enResolver.loadMimeMessages(filteredMimeMessages);
        for (MimeMessage mimeMessage : filteredMimeMessages) {
            Messageable msg = null;
            try {
                msg = new EmailMessage().loadFrom(mimeMessage, this.enResolver);
            } catch (IOException | MessagingException e) {
                log.error("Unable to extract message data", e);
                e.printStackTrace();
            }
            if (msg != null) this.messages.add(msg);
        }
    }

    public static List<MimeMessage> fetchGmails(String query,
                                                GoogleServiceProvider service,
                                                long maxMessages)
            throws IOException {
        checkNotNull(query);
        checkNotNull(service);

        Stopwatch stopwatch = Stopwatch.createStarted();
        log.info("Fetching max {} gmails using query: \"{}\"", maxMessages, query);

        BatchEmailFetcher fetcher = new BatchEmailFetcher(service.getGmailService());
        List<MimeMessage> messages = fetcher.fetchMimeMessages(
                DEFAULT_GMAIL_USER, query, maxMessages);

        log.info("Fetching gmails took: {}", stopwatch);
        return messages;
    }

    /**
     * If the same message occurs in inboxes of several users, pick the first one and add users to it for any duplicates encountered
     */
    private static Collection<MimeMessage> mergeMessageWithSameId(
            List<Future<List<MimeMessage>>> inboxFutures)
            throws GoogleJsonResponseException {
        Map<String, MimeMessage> idToMessageMap = new HashMap<>();
        GoogleJsonResponseException googleJsonException = null;

        // Collect messages.
        for (Future<List<MimeMessage>> inbox : inboxFutures) {
            List<MimeMessage> messages = null;
            try {
                messages = inbox.get();  // Blocking call.
            } catch (ExecutionException | InterruptedException e) {
                // If exception is related to Gmail api, then cache;
                // otherwise output and skip.
                if (e.getCause() instanceof GoogleJsonResponseException) {
                    log.error(e.getCause().getMessage().replaceAll("\\s+", " "));
                    googleJsonException = (GoogleJsonResponseException) e.getCause();
                } else {
                    log.error("Unable to get the mail data for:" + inbox, e);
                    e.printStackTrace();
                }
            }
            // TODO(rcwang): When messages is null, needs to output json error message.
            if (messages == null || messages.isEmpty()) continue;

            try {
                for (MimeMessage message : messages) {
                    String messageId = message.getMessageID();
                    MimeMessage msg = idToMessageMap.get(messageId);
                    if (msg == null) {
                        idToMessageMap.put(messageId, message);
                    } else {
                        String[] sourceInboxes = MimeMessageUtil.getSourceInboxes(message);
                        for (String sourceInbox : sourceInboxes) {
                            msg.addHeader(MimeMessageUtil.SOURCE_INBOX_HEADER, sourceInbox);
                        }
                    }
                }
            } catch (MessagingException e) {
                log.error(e.toString());
            }
        }
        // Throw gmail api error if it had occurred that may have caused
        // no messages to be fetched.
        if (idToMessageMap.isEmpty() && googleJsonException != null) {
            throw googleJsonException;
        }
        return idToMessageMap.values();
    }
}