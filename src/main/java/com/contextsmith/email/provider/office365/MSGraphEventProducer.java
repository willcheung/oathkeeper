package com.contextsmith.email.provider.office365;

import com.contextsmith.utils.Lambda;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.martiansoftware.validation.Hope;
import com.martiansoftware.validation.UncheckedValidationException;
import com.microsoft.graph.extensions.*;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

/**
 * Note: maximum of 2 years for start/end date
 * Created by beders on 5/1/17.
 */
public class MSGraphEventProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MSGraphEventProducer.class);

    private IGraphServiceClient service;
    private Queue<Event> currentBatch;
    private boolean moreResults;
    private int itemsRetrieved;
    private int maxCount = 10000;
    private int pageSize = 50;
    private Date startDate, endDate;
    private static DateTimeFormatter instantFormatter = DateTimeFormatter.ISO_INSTANT;
    private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    private IEventCollectionRequest page;

    public MSGraphEventProducer(IGraphServiceClient service) {
        this.service = service;
        this.startDate = new Date(Instant.now().minus(600, ChronoUnit.DAYS).toEpochMilli());
        this.endDate = new Date();
    }

    public void prepare() throws Exception {
         //List<Option> ops =  // either query or orderBy. Can't have both
//                options("$orderBy","receivedDateTime desc")

        page = service.getMe().getCalendarView().buildRequest(options(
                "StartDateTime", instantFormatter.format(startDate.toInstant()),
                "EndDateTime", instantFormatter.format(endDate.toInstant()))).top(pageSize);
    }

    Event produceNext() throws Exception {
        if (page == null) {
            prepare();
        }
        if (currentBatch == null) {
            moreResults = getNextBatch();
        }
        Event event = currentBatch.poll();
        if (event == null && moreResults) {
            moreResults = getNextBatch();
            event = currentBatch.poll();
        }
        return event;
    }

    private boolean getNextBatch() throws Exception {
        currentBatch = new ArrayDeque<>(maxCount);

        IEventCollectionPage collection = page.get();
        List<com.microsoft.graph.extensions.Event> results = collection.getCurrentPage();
        if (results.isEmpty()) return false;

        results.stream().map(this::buildEvent).filter(Objects::nonNull).forEach(ev -> {
            if (itemsRetrieved < maxCount) {
                currentBatch.add(ev);
            }
            // Do something with the item.
            itemsRetrieved++;
            if (itemsRetrieved % pageSize == 0) {
                System.out.format("Received " + itemsRetrieved + " appointments\n");
            }
        });
        IEventCollectionRequestBuilder nextPage = collection.getNextPage();
        page = nextPage != null ? nextPage.buildRequest() : null;

        return itemsRetrieved < maxCount && page != null; // false == no more items
    }

    private Event buildEvent(com.microsoft.graph.extensions.Event event) {
        Event e = null;
        try {
            e = new Event();
            // EmailAddress.routingType == "EX" <-- need active directory lookup
            Recipient organizer = event.organizer;
            Hope.that(organizer.emailAddress).isNotNull();

            e.setId(event.id);
            e.setOrganizer(new Event.Organizer().setEmail(organizer.emailAddress.address).setDisplayName(organizer.emailAddress.name));
            e.setCreator(new Event.Creator().setEmail(organizer.emailAddress.address));

            List<EventAttendee> attendees = Flux.fromIterable(event.attendees)
                    .map(this::toEventAttendee).doOnNext(att -> LOG.debug("Attendee " + att.getEmail())).collectList().block();
            e.setAttendees(attendees);
            DateTime createDate = new DateTime(event.createdDateTime.getTime(), event.createdDateTime.getTimeZone());
            e.setCreated(createDate);
            e.setUpdated(new DateTime(event.lastModifiedDateTime.getTime(), event.lastModifiedDateTime.getTimeZone()));

            EventDateTime startDate = new EventDateTime().setDateTime(toDateTime(event.start));
            EventDateTime endDate = new EventDateTime().setDateTime(toDateTime(event.end));
            e.setStart(startDate).setEnd(endDate);

            e.setLocation(event.location.displayName);
            e.setSummary(event.subject);
        } catch (UncheckedValidationException u) {
            LOG.warn("Unable to verify event:", u);
        }
        // e.setDescription(event.getBody().toString()); // currently ignored. Lands in EventMessage.plainText which is marked transient. Also needs to be converted to plain
        return e; // more values later: MimeMessageUtil.SOURCE_INBOX_HEADER
    }

    private DateTime toDateTime(DateTimeTimeZone dttZ) {
        LocalDateTime dt = dateTimeFormatter.parse(dttZ.dateTime, LocalDateTime::from);
        ZonedDateTime zonedDateTime = dt.atZone(ZoneId.of(dttZ.timeZone));
        return new DateTime(zonedDateTime.toInstant().toEpochMilli());
    }

    private EventAttendee toEventAttendee(Attendee a) {
        EventAttendee ea = new EventAttendee();
        ea.setEmail(a.emailAddress != null ? a.emailAddress.address : "");
        ea.setDisplayName(a.emailAddress != null ? a.emailAddress.name : "");
        return ea;
    }


    private void finish() {

    }

    static MSGraphEventProducer produce(MSGraphEventProducer producer, reactor.core.publisher.SynchronousSink<Event> sink) {
        try {
            Event event = producer.produceNext();
            if (event == null) {
                sink.complete();
            } else {
                sink.next(event);
            }
        } catch (Exception e) {

            sink.error(e);
        }
        return producer;
    }

    public Flux<Event> asFlux() {
        return Flux.generate(() -> this, MSGraphEventProducer::produce, MSGraphEventProducer::finish);
    }

    public MSGraphEventProducer maxMessages(int max) {
        this.maxCount = max;
        return this;
    }

    public MSGraphEventProducer startDate(long millis) {
        startDate = new Date(millis);
        return this;
    }

    public MSGraphEventProducer endDate(long millis) {
        endDate = new Date(millis);
        return this;
    }

    private static List<Option> options(String... pairs) {
        return Lambda.listFromTuples(QueryOption::new, pairs);
    }

}
