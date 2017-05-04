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
import java.util.function.Function;
import java.util.function.Supplier;

import javax.mail.MessagingException;

import com.contextsmith.api.service.Credential;
import com.contextsmith.email.provider.exchange.EventProducer;
import com.contextsmith.email.provider.exchange.ExchangeServiceProvider;
import microsoft.exchange.webservices.data.core.ExchangeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.data.EventMessage;
import com.contextsmith.api.data.Messageable;
import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

// TODO(rcwang): Make executor service static.
public class UserEventCrawler {
  private static final Logger log = LoggerFactory.getLogger(UserEventCrawler.class);

  public static int SHUTDOWN_WAIT_TIME_IN_MILLIS = 1000;
  public static final String DEFAULT_CALENDAR = "primary";
  public static final int MAX_CALENDAR_FETCHING_RETRIES = 2;

  public static List<Event> fetchEvents(Calendar calendar, long startTime,
                                        long endTime, int maxEvents)
          throws GoogleJsonResponseException {
    if (calendar == null) return null;

    Stopwatch stopWatch = Stopwatch.createStarted();
    List<Event> events = new ArrayList<Event>();
    String pageToken = null;

    do {
      Events response = null;
      for (int retriesLeft = MAX_CALENDAR_FETCHING_RETRIES; retriesLeft >= 0;
           --retriesLeft) {
        try {
          response = calendar.events().list(DEFAULT_CALENDAR)
              .setMaxResults(maxEvents)
              .setTimeMin(new DateTime(startTime * 1000))
              .setTimeMax(new DateTime(endTime * 1000))
              .setPageToken(pageToken)
              .execute();
        } catch (IOException e) {
          if (e instanceof GoogleJsonResponseException) {
            throw (GoogleJsonResponseException) e;
          }
          log.error(e.toString());
        }
        if (response != null) break;  // Exit if successful.
        log.debug("Currently fetched {} events, retrying ({} retry left)...",
                  events.size(), retriesLeft);
      }
      if (response == null) {
        log.error("Failed to fetch more events, currently fetched {} events.",
                  events.size());
        return null;
      }
      pageToken = response.getNextPageToken();
      if (response.getItems() != null) {
        events.addAll(response.getItems());
        if (pageToken != null && events.size() % 1e3 == 0) {
          log.debug("{} events fetched.", events.size());
        }
      }
      // Continue only if we have a next page to fetch and
      // # of messages fetched have not reached its maximum.
    } while (pageToken != null && events.size() < maxEvents);

    log.debug("Successfully fetched {} events in {}.",
              events.size(), stopWatch);
    return events;
  }

  public static List<Event> fetchEvents(final GoogleServiceProvider service, long startTime,
                                        long endTime, int maxEvents)
      throws IOException {

    Stopwatch stopwatch = Stopwatch.createStarted();
    log.info("Fetching max {} events", maxEvents);

    List<Event> events = fetchEvents(service.getCalendarService(), startTime,
                                     endTime, maxEvents);
    log.info("Fetching events took: {}", stopwatch);
    return events;
  }

  @SuppressWarnings("unchecked")
  public static Collection<Event> mergeEventWithSameId(
      List<Future<List<Event>>> eventFutures)
          throws GoogleJsonResponseException {
    Map<String, Event> idToEventMap = new HashMap<>();
    GoogleJsonResponseException googleJsonException = null;

    for (Future<List<Event>> futureEvents : eventFutures) {
      List<Event> events = null;
      try {
        events = futureEvents.get();  // Blocking call.
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
      // TODO(rcwang): When events is null, needs to output json error message.
      if (events == null || events.isEmpty()) continue;

      for (Event newEvent : events) {
        String eventId = newEvent.getId();
        Event existing = idToEventMap.get(eventId);
        if (existing == null) {
          idToEventMap.put(eventId, newEvent);
        } else {
          List<String> existSources = (List<String>) existing.get(
              MimeMessageUtil.SOURCE_INBOX_HEADER);
          List<String> newSources = (List<String>) newEvent.get(
              MimeMessageUtil.SOURCE_INBOX_HEADER);
          if (existSources != null && newSources != null) {
            existSources.addAll(newSources);
          }
        }
      }
    }
    // Throw calendar api error if it had occurred that may have caused
    // no events to be fetched.
    if (idToEventMap.isEmpty() && googleJsonException != null) {
      throw googleJsonException;
    }
    return idToEventMap.values();
  }

  private Function<String, GoogleServiceProvider> googleServiceProvider;
  private Supplier<ExchangeServiceProvider> exchangeServiceProvider;
  private Collection<Messageable> messages;
  private EmailNameResolver enResolver;
  private ExecutorService executor;
  private Set<Callable<List<Event>>> callables;

  public UserEventCrawler(Function<String, GoogleServiceProvider> googleServiceProvider, Supplier<ExchangeServiceProvider> exchangeServiceProvider) {
      this.googleServiceProvider = googleServiceProvider;
      this.exchangeServiceProvider = exchangeServiceProvider;
      this.executor = Executors.newCachedThreadPool(
              new ThreadFactoryBuilder().setNameFormat(
                      Thread.currentThread().getName() + ".%d").build());
      this.callables = new HashSet<>();
      this.enResolver = new EmailNameResolver();
      this.messages = new ArrayList<>();
  }

  public void addGmailTask(final String accessToken, final String email,
                           final long startTime, final long endTime,
                           final int maxEvents) {
    this.callables.add(new Callable<List<Event>>() {
      @Override
      public List<Event> call() throws Exception {
        List<Event> events = fetchEvents(googleServiceProvider.apply(accessToken), startTime, endTime, maxEvents);
        if (events == null) return null;

        // Insert user's email address into Event.
        if (!StringUtils.isBlank(email)) {
          for (Event event : events) {
            @SuppressWarnings("unchecked")
            List<String> sources =
                (List<String>) event.get(MimeMessageUtil.SOURCE_INBOX_HEADER);
            if (sources == null) {
              sources = new ArrayList<>();
              event.set(MimeMessageUtil.SOURCE_INBOX_HEADER, sources);
            }
            sources.add(email);
          }
        }
        return events;
      }
    });
  }

  public void addExchangeTask(Credential cred, final long startDate, final long endDate, int maxEvents) {
      this.callables.add(() -> {
          ExchangeService exchangeService = exchangeServiceProvider.get().connectAsUser(cred.getUsername(), cred.getPassword(), cred.getUrl());
          EventProducer producer = new EventProducer(exchangeService).maxMessages(maxEvents).startDate(startDate).endDate(endDate);
          Runtime runtime = Runtime.getRuntime();
          long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
          System.out.println("Used Memory before: " + usedMemoryBefore / 1_000_000);
          long startTime = System.currentTimeMillis();

          List<Event> events = producer.asFlux().map(event -> {
              @SuppressWarnings("unchecked")
              List<String> sources = (List<String>) event.get(MimeMessageUtil.SOURCE_INBOX_HEADER);
              if (sources == null) {
                  sources = new ArrayList<>();
                  event.set(MimeMessageUtil.SOURCE_INBOX_HEADER, sources);
              }
              sources.add(cred.getUsername());
              return event;
          }).collectList().block();

          System.out.println("Received " + events.size() + " events");
          long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
          System.out.println("Memory increased: " + (usedMemoryAfter - usedMemoryBefore) / 1_000_000);
          System.out.println("Duration (s): " + (System.currentTimeMillis() - startTime) / 1000.0);
          return events;
      });
  }

  public EmailNameResolver getEnResolver() {
    return this.enResolver;
  }

  public Collection<Messageable> getMessages() {
    return this.messages;
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

    List<Future<List<Event>>> eventFutures = null;
    try {
      eventFutures = this.executor.invokeAll(this.callables);  // Blocking.
    } catch (InterruptedException e) {
      log.error(e.toString());
    } finally {
      this.callables.clear();
      this.shutdown(SHUTDOWN_WAIT_TIME_IN_MILLIS);
    }
    Collection<Event> events = mergeEventWithSameId(eventFutures);

    this.enResolver.loadEvents(events);

    for (Event event : events) {
      Messageable msg = null;
      try {
        msg = new EventMessage().loadFrom(event, this.enResolver);
      } catch (MessagingException e) {
        log.error(e.toString());
        e.printStackTrace();
      }
      if (msg != null) {
//        log.debug(StringUtil.toJson(msg));
        this.messages.add(msg);
      }
    }
  }
}
