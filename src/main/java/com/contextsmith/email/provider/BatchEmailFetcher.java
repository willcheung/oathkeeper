package com.contextsmith.email.provider;

import com.contextsmith.api.service.NewsFeederRequest;
import com.contextsmith.utils.MimeMessageUtil;
import com.contextsmith.utils.ProcessUtil;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class BatchEmailFetcher {
    public static final int MAX_REQUEST_PER_BATCH = 100;
    public static final int MAX_GMAIL_ID_FETCHING_RETRIES = 2;
    public static final int MAX_GMAIL_MSG_FETCHING_RETRIES = 2;
    public static final int MAX_CONCURRENT_THREADS = 4;
    public static final long MIN_MILLIS_BETWEEN_BATCH_REQUEST = 1_000;  // 1 sec. - to prevent getting rejected by Google API
    public static final Properties PROPS = new Properties();
    private static final Logger log = LoggerFactory.getLogger(BatchEmailFetcher.class);
    private Gmail gmailService;

    public BatchEmailFetcher(Gmail service) {
        checkNotNull(service);
        this.gmailService = service;
    }

    public List<MimeMessage> fetchMimeMessages(String userId, String query,
                                               long maxMessages) throws IOException {
        List<Message> gmailMessages = fetchGmailMessages(userId, query, maxMessages);
        if (gmailMessages == null) return null;
        List<MimeMessage> mimeMessages = new ArrayList<>();

        for (int retriesLeft = MAX_GMAIL_MSG_FETCHING_RETRIES; retriesLeft >= 0;
             --retriesLeft) {
            log.info("Fetching remaining {} emails ({} retry left)...",
                    gmailMessages.size(), retriesLeft);
            mimeMessages.addAll(fetchMimeMessages(userId, gmailMessages));
            gmailMessages = findUnfetchedGmailMessages(gmailMessages, mimeMessages);
            if (gmailMessages.isEmpty()) break;
        }
        return mimeMessages;
    }

    /**
     * List all Messages of the user's mailbox matching the query.
     *
     * @param userId User's email address. The special value "me"
     *               can be used to indicate the authenticated user.
     * @param query  String used to filter the Messages listed.
     * @throws IOException
     */
    private List<Message> fetchGmailMessages(String userId, String query,
                                             long maxMessages)
            throws GoogleJsonResponseException {
        if (this.gmailService == null) return null;

        Stopwatch stopWatch = Stopwatch.createStarted();
        List<Message> messages = new ArrayList<Message>();
        String pageToken = null;

        do {
            ListMessagesResponse response = null;
            for (int retriesLeft = MAX_GMAIL_ID_FETCHING_RETRIES; retriesLeft >= 0;
                 --retriesLeft) {
                try {
                    response = this.gmailService.users()
                            .messages()
                            .list(userId)
                            .setQ(query)
                            .setPageToken(pageToken)
                            .execute();
                    //throw new SocketTimeoutException("read time out");
                } catch (IOException e) {
                    if (e instanceof GoogleJsonResponseException) {
                        throw (GoogleJsonResponseException) e;
                    }
                    log.error(e.toString());
                }
                if (response != null) break;  // Exit if successful.
                log.debug("Currently fetched {} Gmail IDs, retrying ({} retry left)...",
                        messages.size(), retriesLeft);
            }
            if (response == null) {
                log.error("Failed to fetch more Gmail IDs, currently fetched {} IDs.",
                        messages.size());
                return null;
            }
            pageToken = response.getNextPageToken();
            if (response.getMessages() != null) {
                messages.addAll(response.getMessages());
                if (pageToken != null && messages.size() % 1e3 == 0) {
                    log.debug("{} Gmail IDs fetched.", messages.size());
                }
            }
            // Continue only if we have a next page to fetch and
            // # of messages fetched have not reached its maximum.
        } while (pageToken != null && messages.size() < maxMessages);

        log.debug("Successfully fetched {} Gmail IDs in {}.",
                messages.size(), stopWatch);
        return messages;
    }

    private List<MimeMessage> fetchMimeMessages(String userId,
                                                List<Message> messages) {
        // Build a new authorized API client service.
        if (this.gmailService == null) return null;

        // Create callback.
        final List<MimeMessage> mimeMessages = new ArrayList<MimeMessage>();
        // callback received all Messages, converts it to MimeMessage and adds it to mimeMessages
        JsonBatchCallback<Message> callback = makeJsonBatchCallback(
                mimeMessages, messages.size(), Stopwatch.createStarted());

        int numThreads = Math.min(Runtime.getRuntime().availableProcessors(),
                MAX_CONCURRENT_THREADS);
        //BatchRequestRunnable[] runnables = new BatchRequestRunnable[numThreads];
        BlockingQueue<BatchRequestRunnable> bq = new ArrayBlockingQueue<>(numThreads);
        for (int i = 0; i < numThreads; i++) {
            BatchRequest batchRequest = this.gmailService.batch();
            bq.add(new BatchRequestRunnable(batchRequest) {
                @Override
                public void run() {
                    super.run();
                    try {
                        bq.put(this);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        ExecutorService executorService = Executors.newFixedThreadPool(
                numThreads,
                new ThreadFactoryBuilder().setNameFormat(
                        Thread.currentThread().getName() + ".%d").build());

        try {
            BatchRequestRunnable currRunnable = bq.take(); //findAvailableBatchRequestRunnable(runnables);
            // Batch all mail requests.
            for (int i = 0; i < messages.size(); ++i) {
                this.gmailService.users()
                        .messages()
                        .get(userId, messages.get(i).getId())
                        .setFormat("raw")
                        .queue(currRunnable.getBatchRequest(), callback);

                if ((i + 1) % MAX_REQUEST_PER_BATCH == 0 || i == messages.size() - 1) {
                    // Start fetching mails in the background.
                    executorService.execute(currRunnable);
                    Thread.sleep(MIN_MILLIS_BETWEEN_BATCH_REQUEST);
                    currRunnable = bq.take();//findAvailableBatchRequestRunnable(runnables);
                }
            }
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
            //waitForAllRunnables(runnables);  // Make sure all runnables are done.
        } catch (IOException | InterruptedException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
        return mimeMessages;
    }

    private BatchRequestRunnable findAvailableBatchRequestRunnable(
            BatchRequestRunnable[] runnables) {
        while (true) {
            for (int i = 0; i < runnables.length; ++i) {
                if (runnables[i] == null) {
                    BatchRequest batchRequest = this.gmailService.batch();
                    runnables[i] = new BatchRequestRunnable(batchRequest);
                }
                if (runnables[i].isAvailable()) {
                    return runnables[i];
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
    }

    private static List<Message> findUnfetchedGmailMessages(
            List<Message> emailsToFetch,
            List<MimeMessage> emailsFetched) {
        Set<String> fetchedIds = new HashSet<>();
        for (MimeMessage message : emailsFetched) {
            String id = MimeMessageUtil.getGmailMessageId(message);  // gmail id.
            if (id == null) continue;
            fetchedIds.add(id);
        }
        List<Message> missingMessages = new ArrayList<>();
        for (Message message : emailsToFetch) {
            String id = message.getId();  // gmail id.
            if (!fetchedIds.contains(id)) {
                missingMessages.add(message);
            }
        }
        return missingMessages;
    }

    private static JsonBatchCallback<Message> makeJsonBatchCallback(
            final List<MimeMessage> mimeMessages,
            final int maxMessages,
            final Stopwatch stopwatch) {
        return new JsonBatchCallback<Message>() {
            @Override
            public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
                    throws IOException {
            }

            @Override
            public void onSuccess(Message message, HttpHeaders responseHeaders)
                    throws IOException {
                byte[] emailBytes = Base64.decodeBase64(message.getRaw());
                InputStream is = new ByteArrayInputStream(emailBytes);
                Session session = Session.getDefaultInstance(PROPS, null);
                MimeMessage mimeMessage = null;

                try {
                    mimeMessage = new MimeMessage(session, is);
                    // We store gmail's message id to later determine those messages that
                    // are missing, and need to be re-fetched.
                    mimeMessage.addHeader(MimeMessageUtil.GMAIL_MESSAGE_ID_HEADER,
                            message.getId());
                    mimeMessage.addHeader(MimeMessageUtil.GMAIL_THREAD_ID_HEADER,
                            message.getThreadId());
                    // we'll use the next two headers for URNs pointing to e-mails
                    mimeMessage.addHeader(MimeMessageUtil.X_PRIVATE_ID, message.getId());
                    mimeMessage.addHeader(MimeMessageUtil.X_SOURCE, NewsFeederRequest.Provider.gmail.toString());
                } catch (MessagingException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                }
                if (mimeMessage == null) return;

                // Important: Ensures thread-safe!
                synchronized (mimeMessages) {
                    mimeMessages.add(mimeMessage);

                    if (mimeMessages.size() % MAX_REQUEST_PER_BATCH == 0 ||  // moved it into synchronized block
                            mimeMessages.size() == maxMessages) {
                        log.debug(String.format(
                                "[%d%%] %d/%d fetched @ %.1f emails/sec. %s",
                                Math.round(100.0 * mimeMessages.size() / maxMessages),
                                mimeMessages.size(), maxMessages,
                                1000.0 * mimeMessages.size() / stopwatch.elapsed(TimeUnit.MILLISECONDS),
                                ProcessUtil.getHeapConsumption()));
                    }
                }
            }
        };
    }

    private static void waitForAllRunnables(BatchRequestRunnable[] runnables)
            throws InterruptedException {
        while (true) {
            boolean allAvailable = true;
            for (BatchRequestRunnable runnable : runnables) {
                if (runnable != null && !runnable.isAvailable()) {
                    allAvailable = false;
                }
            }
            if (allAvailable) break;
            Thread.sleep(100);
        }
    }

}
