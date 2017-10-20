package com.contextsmith.email.provider.exchange;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.property.complex.Attendee;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Note: maximum of 2 years for start/end date
 * Created by beders on 5/1/17.
 */
public class EventProducer {
    private CalendarView view;
    private ExchangeService service;
    private CalendarFolder calendar;
    private Queue<Event> currentBatch;
    private boolean moreResults;
    private int itemsRetrieved;
    private int maxCount = 10000;
    private int pageSize = 50;
    private Date startDate, endDate;

    public EventProducer(ExchangeService service) {
        this.service = service;
        this.startDate = new Date(Instant.now().minus(600, ChronoUnit.DAYS).toEpochMilli());
        this.endDate = new Date();
    }

    public void prepare() throws Exception {
        calendar = CalendarFolder.bind(service, WellKnownFolderName.Calendar, new PropertySet());

        // Set the start and end time and number of appointments to retrieve.
        view = new CalendarView(startDate, endDate, maxCount);

        // Limit the properties returned
        view.setPropertySet(PropertySet.FirstClassProperties);
    }

    Event produceNext() throws Exception {
        if (view == null) {
            prepare();
        }
        if (currentBatch == null) {
            moreResults = getNextBatch();
        }
        Event event = currentBatch.poll(); // there's only one batch since CalendarView is not pageable
        /**if (event == null && moreResults) {
            moreResults = getNextBatch();
            event = currentBatch.poll();
        }*/
        return event;
    }

    private boolean getNextBatch() throws Exception {
        currentBatch = new ArrayDeque<>(maxCount);

        // Retrieve a collection of appointments by using the calendar view.
        FindItemsResults<Appointment> findResults = calendar.findAppointments(view);

        //findResults = service.findItems(WellKnownFolderName.Inbox, query, view);
        if (findResults.getItems().size() == 0) return false;

        service.loadPropertiesForItems(findResults, PropertySet.FirstClassProperties);

        for (Appointment appt : findResults.getItems()) {
            currentBatch.add(buildEvent(appt));

            // Do something with the item.
            itemsRetrieved++;
            if (itemsRetrieved % pageSize == 0) {
                System.out.format("Received " + itemsRetrieved + " appointments\n");
            }
        }

        return itemsRetrieved < maxCount && findResults.isMoreAvailable(); // false == no more items
    }

    private Event buildEvent(Appointment appt) throws Exception {
        Event e = new Event();
        // EmailAddress.routingType == "EX" <-- need active directory lookup
        EmailAddress organizer = appt.getOrganizer();
        e.setId(appt.getId().toString());
        e.setOrganizer(new Event.Organizer().setEmail(organizer.getAddress()).setDisplayName(organizer.getName()));
        e.setCreator(new Event.Creator().setEmail(organizer.getAddress()));

        List<EventAttendee> attendees = Flux.fromIterable(appt.getRequiredAttendees())
                .concatWith(Flux.fromIterable(appt.getOptionalAttendees()))
                .map(this::toEventAttendee).doOnNext(att -> System.out.println(att.getEmail())).collectList().block();
        e.setAttendees(attendees);
        DateTime createDate = new DateTime(appt.getDateTimeCreated(), TimeZone.getTimeZone("UTC"));
        e.setCreated(createDate);
        e.setUpdated(new DateTime(appt.getLastModifiedTime(), TimeZone.getTimeZone("UTC")));
        EventDateTime startDate = new EventDateTime().setDateTime(new DateTime(appt.getStart()));
        EventDateTime endDate = new EventDateTime().setDateTime(new DateTime(appt.getEnd()));
        e.setStart(startDate).setEnd(endDate);

        e.setLocation(appt.getLocation());
        e.setSummary(appt.getSubject());
        // e.setDescription(appt.getBody().toString()); // currently ignored. Lands in EventMessage.plainText which is marked transient. Also needs to be converted to plain
        return e; // more values later: MimeMessageUtil.SOURCE_INBOX_HEADER
    }

    private EventAttendee toEventAttendee(Attendee a) {
        EventAttendee ea = new EventAttendee();
        ea.setEmail(a.getAddress());
        ea.setDisplayName(a.getName());
        return ea;
    }


    private void finish() {
        service.close();
    }

    static EventProducer produce(EventProducer producer, reactor.core.publisher.SynchronousSink<Event> sink) {
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
        return Flux.generate(() -> this, EventProducer::produce, EventProducer::finish);
    }

    public EventProducer maxMessages(int max) {
        this.maxCount = max;
        return this;
    }

    public EventProducer startDate(long millis) {
        startDate = new Date(millis);
        return this;
    }

    public EventProducer endDate(long millis) {
        endDate = new Date(millis);
        return this;
    }


}
