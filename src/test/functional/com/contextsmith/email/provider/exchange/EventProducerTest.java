package com.contextsmith.email.provider.exchange;


import com.google.api.services.calendar.model.Event;
import microsoft.exchange.webservices.data.core.ExchangeService;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Created by beders on 5/1/17.
 */
public class EventProducerTest {

    String username = System.getenv("USERNAME");
    String password = System.getenv("PASSWORD");
    String url = System.getenv("URL");

    @Test
    public void testProducer() throws Exception {

        ExchangeService service = new ExchangeServiceProvider().connectAsUser(username, password.toCharArray(), url);
        EventProducer eventProducer = new EventProducer(service);
        eventProducer.maxMessages(10);
        List<Event> events = eventProducer.asFlux().collectList().block();
        System.out.println(events);
        assertTrue(events.size() > 0);
    }


}