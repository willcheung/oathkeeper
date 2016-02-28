package com.contextsmith.email.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.utils.MimeMessageUtil;
import com.contextsmith.utils.StringUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;

public class EmailFetcher {

  public enum EmailSource { GMAIL, ENRON }

  static final Logger log = LogManager.getLogger(EmailFetcher.class);

  // Directory to store user credentials for this application.
  public static final String DEFAULT_DATA_STORE_DIR = "indifferenzetester@gmail.com";
//  public static final String DEFAULT_DATA_STORE_DIR = "rcwang@gmail.com";
  public static final File DEFAULT_DATA_STORE_FILE = new File(
      System.getProperty("user.home"), ".credentials/" + DEFAULT_DATA_STORE_DIR);
  public static final String DEFAULT_ACCESS_TOKEN = "test";
  public static final String DEFAULT_GMAIL_USER = "me";

  public static List<MimeMessage> fetchEmails(String query, String accessToken,
                                              String email, long maxMessages,
                                              EmailSource source)
    throws IOException {
    List<MimeMessage> messages = null;
    switch (source) {
    case GMAIL: messages = fetchGmails(query, accessToken, maxMessages); break;
    case ENRON: messages = fetchEnron(query, email, maxMessages); break;
    default: return null;
    }

    // Insert user's email address into MimeMessage.
    if (!StringUtils.isBlank(email)) {
      for (MimeMessage message : messages) {
        try {
          message.addHeader(MimeMessageUtil.SOURCE_INBOX_HEADER, email);
        } catch (MessagingException e) {
          log.error(e);
          e.printStackTrace();
        }
      }
    }
    return messages;
  }

  // Note: Input query not yet supported.
  public static List<MimeMessage> fetchEnron(String query, String email,
                                             long maxMessages) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String user = email.replaceAll("@.+", "");
    log.info("Fetching max. {} Enron e-mails from user: {}", maxMessages, user);

    List<MimeMessage> messages = LocalFileProvider.provide(user, maxMessages);
    log.info("Fetching Enron e-mails took: {}", stopwatch);
    return messages;
  }

  public static List<MimeMessage> fetchGmails(String query,
                                              String accessToken,
                                              long maxMessages)
    throws IOException {
    checkNotNull(query);
    checkNotNull(accessToken);

    Stopwatch stopwatch = Stopwatch.createStarted();
    log.info("[{}] Fetching max {} gmails using query: \"{}\"",
             StringUtil.substringFromLast(accessToken, 5), maxMessages, query);

    GmailServiceProvider provider = null;
    if (accessToken.equals(DEFAULT_ACCESS_TOKEN)) {
      provider = new GmailServiceProvider(DEFAULT_DATA_STORE_FILE);
    } else {
      provider = new GmailServiceProvider(accessToken);
    }
    checkNotNull(provider);

    List<MimeMessage> messages = provider.provide(
        DEFAULT_GMAIL_USER, query, maxMessages);

    log.info("[{}] Fetching gmails took: {}",
             StringUtil.substringFromLast(accessToken, 5), stopwatch);
    return messages;
  }

  private EmailSource source;
  private ExecutorService executor;
  private Set<Callable<List<MimeMessage>>> callables;

  public EmailFetcher(EmailSource source) {
    this.source = source;
    this.callables = new HashSet<>();
    this.executor = Executors.newCachedThreadPool();
  }

  public void addTask(final String query, final String accessToken,
                      final String email, final int maxMessages) {
    final EmailSource source = this.source;
    this.callables.add(new Callable<List<MimeMessage>>() {
      @Override
      public List<MimeMessage> call() throws Exception {
        return fetchEmails(query, accessToken, email, maxMessages, source);
      }
    });
  }

  public void shutdown(long waitTimeInMillis) {
    try {
      this.executor.shutdown();
      this.executor.awaitTermination(waitTimeInMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      log.error(e);
    } finally {
      if (!this.executor.isTerminated()) {
        log.warn("Killing non-finished tasks.");
      }
      this.executor.shutdownNow();
    }
  }

  public Collection<MimeMessage> startFetch()
      throws InterruptedException, GoogleJsonResponseException {
    return startFetch(null);
  }

  public Collection<MimeMessage> startFetch(Long timeOutInMillis)
      throws InterruptedException, GoogleJsonResponseException {
    // Blocking
    List<Future<List<MimeMessage>>> futures = null;
    if (timeOutInMillis == null) {
      futures = this.executor.invokeAll(this.callables);
    } else {
      futures = this.executor.invokeAll(this.callables, timeOutInMillis,
                                        TimeUnit.MILLISECONDS);
    }
    this.callables.clear();

    // Collect messages.
    Map<String, MimeMessage> idToMessageMap = new HashMap<>();
    for (Future<List<MimeMessage>> future : futures) {
      List<MimeMessage> messages = null;
      try {
        messages = future.get();
      } catch (ExecutionException e) {
        // If exception is related to gmail api, then throw; otherwise skip.
        if (e.getCause() instanceof GoogleJsonResponseException) {
          log.error(e.getCause());
          throw (GoogleJsonResponseException) e.getCause();
        } else {
          log.error(e);
          e.printStackTrace();
        }
      }
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
        log.error(e);
      }
    }
    return idToMessageMap.values();
  }
}