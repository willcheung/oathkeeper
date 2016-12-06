package com.contextsmith.email.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
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
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.data.EmailMessage;
import com.contextsmith.api.data.Messageable;
import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

// TODO(rcwang): Make executor service static.
public class UserInboxCrawler {
  private static final Logger log = LoggerFactory.getLogger(UserInboxCrawler.class);

  public static int SHUTDOWN_WAIT_TIME_IN_MILLIS = 1000;
  public static final String DEFAULT_GMAIL_USER = "me";

  public static List<MimeMessage> fetchGmails(String query,
                                              String accessToken,
                                              long maxMessages)
    throws IOException {
    checkNotNull(query);
    checkNotNull(accessToken);

    Stopwatch stopwatch = Stopwatch.createStarted();
    log.info("Fetching max {} gmails using query: \"{}\"", maxMessages, query);

    GoogleServiceProvider service = new GoogleServiceProvider(accessToken);
    BatchEmailFetcher fetcher = new BatchEmailFetcher(service.getGmailService());
    List<MimeMessage> messages = fetcher.fetchMimeMessages(
        DEFAULT_GMAIL_USER, query, maxMessages);

    log.info("Fetching gmails took: {}", stopwatch);
    return messages;
  }

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
          log.error(e.toString());
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

  private EmailFilterer emailFilterer;
  private EmailNameResolver enResolver;
  private ExecutorService executor;
  private Collection<Messageable> messages;
  private Collection<MimeMessage> unfilteredMimeMessages;
  private Set<Callable<List<MimeMessage>>> callables;
  private Pattern subjectRetainPattern;

  public UserInboxCrawler() {
    this.executor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat(
            Thread.currentThread().getName() + ".%d").build());
    this.callables = new HashSet<>();
    this.emailFilterer = new EmailFilterer();
    this.enResolver = new EmailNameResolver();
    this.messages = new ArrayList<>();

    this.unfilteredMimeMessages = null;
    this.subjectRetainPattern = null;
  }

  public void addTask(final String query, final String accessToken,
                      final String email, final int maxMessages) {
    this.callables.add(new Callable<List<MimeMessage>>() {
      @Override
      public List<MimeMessage> call() throws Exception {
        List<MimeMessage> messages = fetchGmails(query, accessToken, maxMessages);
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

  public EmailNameResolver getEnResolver() {
    return this.enResolver;
  }

  public Collection<Messageable> getMessages() {
    return this.messages;
  }

  public Pattern getSubjectRetainPattern() {
    return this.subjectRetainPattern;
  }

  public Collection<MimeMessage> getUnfilteredMimeMessages() {
    return this.unfilteredMimeMessages;
  }

  public UserInboxCrawler setSubjectRetainPattern(Pattern subjectRetainPattern) {
    this.subjectRetainPattern = subjectRetainPattern;
    return this;
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
        log.error(e.toString());
        e.printStackTrace();
      }
      if (msg != null) this.messages.add(msg);
    }
  }
}