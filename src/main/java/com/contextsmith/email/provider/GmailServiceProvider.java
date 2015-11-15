package com.contextsmith.email.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.base.Stopwatch;

public class GmailServiceProvider {

  static final Logger log = LogManager.getLogger(GmailServiceProvider.class);

  public static final String CLIENT_SECRET_FILE = "client_secret.json";
  public static final String APPLICATION_NAME = "Context-Smith Gmail Service Provider";

  public static final String GMAIL_THREAD_ID = "Gmail-Thread-Id";
  public static final String GMAIL_MESSAGE_ID = "Gmail-Message-Id";

  public static final int MAX_CONCURRENT_THREADS = 4;
  public static final int MAX_FETCHING_RETRIES = 3;
  public static final int MAX_REQUEST_PER_BATCH = 100;
  public static final int MAX_TERMINATION_IN_MILLIS = 10_000;  // 10 secs.
  public static final long MIN_MILLIS_BETWEEN_BATCH_REQUEST = 1_000;  // 1 sec.

  private static HttpTransport HTTP_TRANSPORT;
  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      log.error(e);
      e.printStackTrace();
    }
  }

  public static Credential authorizeAccessToken(String accessToken) {
    checkNotNull(accessToken);

    GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
    tokenResponse.setAccessToken(accessToken);
    return new GoogleCredential().setFromTokenResponse(tokenResponse);
  }

  /**
   * Creates an authorized Credential object.
   * @return an authorized Credential object.
   * @throws IOException
   */
  public static Credential authorizeStoredCredential(File storedCredential)
      throws IOException {
    checkNotNull(storedCredential);

    // Load client secrets.
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    InputStream in = classLoader.getResourceAsStream(CLIENT_SECRET_FILE);
    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(),
                                 new InputStreamReader(in));

    List<String> scopes = Arrays.asList(GmailScopes.GMAIL_READONLY);
    FileDataStoreFactory dataStoreFactory =
        new FileDataStoreFactory(storedCredential);

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(),
            clientSecrets, scopes)
        .setDataStoreFactory(dataStoreFactory)
        .setAccessType("offline")
        .build();

    Credential credential = new AuthorizationCodeInstalledApp(
        flow, new LocalServerReceiver()).authorize("user");
    log.info("Credentials saved to: {}",
             dataStoreFactory.getDataDirectory().getAbsolutePath());
    return credential;
  }

  public static String getGmailMessageId(MimeMessage message) {
    return MimeMessageUtil.getHeader(message, GMAIL_MESSAGE_ID);
  }

  public static String getGmailThreadId(MimeMessage message) {
    return MimeMessageUtil.getHeader(message, GMAIL_THREAD_ID);
  }

  private static void filterUselessMimeMessages(List<MimeMessage> mimeMessages) {
    int numBeforeFilter = mimeMessages.size();
    for (Iterator<MimeMessage> iter = mimeMessages.iterator(); iter.hasNext();) {
      if (!MimeMessageUtil.isUsefulMessage(iter.next())) {
        iter.remove();
      }
    }
    int numFiltered = numBeforeFilter - mimeMessages.size();
    int numAfterFilter = mimeMessages.size();
    log.debug(String.format(
        "Filtered %d%% (%d out of %d) invalid emails, %d left.",
        Math.round(100.0 * numFiltered / numBeforeFilter),
        numFiltered, numBeforeFilter, numAfterFilter));
  }

  private static List<Message> findMissingMessages(
      List<Message> messages,
      List<MimeMessage> mimeMessages) {
    Set<String> mailIds = new HashSet<>();
    for (MimeMessage mimeMessage : mimeMessages) {
      String mailId = null;
      try {
        mailId = mimeMessage.getHeader(GMAIL_MESSAGE_ID, null);
      } catch (MessagingException e) {
        e.printStackTrace();
      }
      if (mailId == null) continue;
      mailIds.add(mailId);
    }
    List<Message> missingMessages = new ArrayList<>();
    for (Message message : messages) {
      if (!mailIds.contains(message.getId())) {
        missingMessages.add(message);
      }
    }
    return missingMessages;
  }

  private static JsonBatchCallback<Message> makeJsonBatchCallback(
      final List<MimeMessage> mimeMessages) {
    return new JsonBatchCallback<Message>() {
      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
          throws IOException {
      }

      @Override
      public void onSuccess(Message message, HttpHeaders responseHeaders)
          throws IOException {
        byte[] emailBytes = Base64.decodeBase64(message.getRaw());
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        try {
          MimeMessage mimeMessage = new MimeMessage(
              session, new ByteArrayInputStream(emailBytes));
          mimeMessage.addHeader(GMAIL_MESSAGE_ID, message.getId());
          mimeMessage.addHeader(GMAIL_THREAD_ID, message.getThreadId());
          synchronized (mimeMessages) {
            mimeMessages.add(mimeMessage);
          }
        } catch (MessagingException e) {
          e.printStackTrace();
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
  private Gmail service;
  private String accessToken;

  private File storedCredential;

  public GmailServiceProvider(File storedCredential) {
    this.service = null;
    this.accessToken = null;
    this.storedCredential = storedCredential;
  }

  public GmailServiceProvider(String accessToken) {
    this.service = null;
    this.accessToken = accessToken;
    this.storedCredential = null;
  }

  public List<MimeMessage> fetchMimeMessages(String userId,
                                             List<Message> messages) {
    // Build a new authorized API client service.
    Gmail gmail = getGmailService();
    if (gmail == null) return null;

    // Create callback.
    final List<MimeMessage> mimeMessages = new ArrayList<MimeMessage>();
    JsonBatchCallback<Message> callback = makeJsonBatchCallback(mimeMessages);

    int numThreads = Math.min(Runtime.getRuntime().availableProcessors(),
                              MAX_CONCURRENT_THREADS);
    BatchRequestRunnable[] runnables = new BatchRequestRunnable[numThreads];
    BatchRequestRunnable currRunnable = findAvailableBatchRequestRunnable(runnables);
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      // Batch all mail requests.
      for (int i = 0; i < messages.size(); ++i) {
        Message message = messages.get(i);
        gmail.users().messages()
             .get(userId, message.getId())
             .setFormat("raw")
             .queue(currRunnable.getBatchRequest(), callback);

        if ((i + 1) % MAX_REQUEST_PER_BATCH == 0 || i == messages.size() - 1) {
          log.debug(String.format(
              "[%d%%] %d out of %d fetched! (%.1f emails/sec.)",
              Math.round(100.0 * mimeMessages.size() / messages.size()),
              mimeMessages.size(), messages.size(),
              1000.0 * mimeMessages.size() / stopwatch.elapsed(TimeUnit.MILLISECONDS)));

          // Start fetching mails in the background.
          executorService.execute(currRunnable);
          Thread.sleep(MIN_MILLIS_BETWEEN_BATCH_REQUEST);
          currRunnable = findAvailableBatchRequestRunnable(runnables);
        }
      }
      log.debug("Waiting for {} threads to finish...", runnables.length);
      executorService.shutdown();
      executorService.awaitTermination(MAX_TERMINATION_IN_MILLIS,
                                       TimeUnit.MILLISECONDS);
      waitForAllRunnables(runnables);  // Make sure all runnables are done.
    } catch (IOException | InterruptedException e) {
      log.error(e);
      e.printStackTrace();
    }

    log.debug(String.format(
        "[%d%%] %d out of %d fetched! (%.1f emails/sec.)",
        Math.round(100.0 * mimeMessages.size() / messages.size()),
        mimeMessages.size(), messages.size(),
        1000.0 * mimeMessages.size() / stopwatch.elapsed(TimeUnit.MILLISECONDS)));
    return mimeMessages;
  }

  public String getAccessToken() {
    return this.accessToken;
  }

  /**
   * Build and return an authorized Gmail client service.
   * @return an authorized Gmail client service
   * @throws IOException
   */
  public Gmail getGmailService() {
    if (this.service != null) return this.service;

    Credential credential = null;
    if (!Strings.isBlank(this.accessToken)) {
      credential = authorizeAccessToken(this.accessToken);
    } else if (this.storedCredential != null) {
      try {
        credential = authorizeStoredCredential(this.storedCredential);
      } catch (IOException e) {
        log.error(e);
        e.printStackTrace();
      }
    }
    if (credential == null) return null;

    this.service = new Gmail
        .Builder(HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(), credential)
        .setApplicationName(APPLICATION_NAME)
        .build();
    return this.service;
  }

  public File getStoredCredential() {
    return this.storedCredential;
  }

  public List<MimeMessage> provide(String userId, String query, long maxMessages)
      throws IOException {
    List<Message> messages = searchForMessages(userId, query, maxMessages);
    if (messages == null) return null;

    int retriesLeft = MAX_FETCHING_RETRIES;
    List<Message> tempMessages = messages;
    List<MimeMessage> mimeMessages = new ArrayList<>();
    do {
      retriesLeft--;
      log.info("[{} retry left] Still need to fetch {} emails.",
               retriesLeft, tempMessages.size());
      mimeMessages.addAll(fetchMimeMessages(userId, tempMessages));
      tempMessages = findMissingMessages(tempMessages, mimeMessages);
    } while (!tempMessages.isEmpty() && retriesLeft > 0);

    filterUselessMimeMessages(mimeMessages);
    return mimeMessages;
  }

  /**
   * List all Messages of the user's mailbox matching the query.
   * @param userId User's email address. The special value "me"
   * can be used to indicate the authenticated user.
   * @param query String used to filter the Messages listed.
   * @param service Authorized Gmail API instance.
   *
   * @throws IOException
   */
  public List<Message> searchForMessages(String userId, String query,
                                         long maxMessages)
    throws IOException {
    Gmail service = getGmailService();
    if (service == null) return null;

    List<Message> messages = new ArrayList<Message>();
    ListMessagesResponse response = service.users().messages()
        .list(userId)
        .setMaxResults(maxMessages)
        .setQ(query).execute();
    if (response == null) return null;

    while (response.getMessages() != null) {
      messages.addAll(response.getMessages());
      if (messages.size() >= maxMessages) break;
      if (response.getNextPageToken() == null) break;
      String pageToken = response.getNextPageToken();
      response = service.users().messages()
          .list(userId).setQ(query)
          .setPageToken(pageToken).execute();
    }
    return messages;
  }

  private BatchRequestRunnable findAvailableBatchRequestRunnable(
      BatchRequestRunnable[] runnables) {
    while (true) {
      for (int i = 0; i < runnables.length; ++i) {
        if (runnables[i] == null) {
          Gmail gmail = getGmailService();
          BatchRequest batchRequest = gmail.batch();
          runnables[i] = new BatchRequestRunnable(batchRequest);
        }
        if (runnables[i].isAvailable()) {
          return runnables[i];
        }
      }
      try { Thread.sleep(100); }
      catch (InterruptedException e) {}
    }
  }
}