package com.contextsmith.email.provider;

import com.contextsmith.api.service.Source;
import org.junit.Test;
import reactor.core.publisher.Flux;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by beders on 5/22/17.
 */
public class UserInboxCrawlerTest {
    Source source = new Source();
    private List<MimeMessage> testData = new ArrayList<>();
    private List<Set<InternetAddress>> cluster = new ArrayList<>();

    public void setupMessages()  {
        try {
            source = new Source();
            source.email = "test@example.com";

            testData = new ArrayList<>();
            MimeMessage msg = new MimeMessage(Session.getDefaultInstance(new Properties()));
            msg.setFrom("customer@example.com");

            msg.setRecipients(Message.RecipientType.TO, "info@contextsmith.com");

            msg.setSubject("Test");
            msg.setText("Test");
            testData.add(msg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Set<InternetAddress> cluster(String... addresses) {
        return Arrays.stream(addresses).map(s -> {
            try {
                return new InternetAddress(s);
            } catch (AddressException e) {}
            return null;
        }).collect(Collectors.toSet());
    }

    @Test
    public void filterMessages() throws Exception {
        UserInboxCrawler crawler = new UserInboxCrawler();
        // test null-hypothesis

        Flux<MimeMessage> msgs = Flux.fromIterable(testData);
        List<MimeMessage> filtered = crawler.filterMessages(source, cluster, msgs);

        assertTrue(filtered.size() == 0);

        setupMessages();
        // test without cluster

        msgs = Flux.fromIterable(testData);
        filtered = crawler.filterMessages(source, null, msgs);
        assertEquals(testData.size(), filtered.size());

        // test with cluster data
        cluster.add(cluster("customer@example.com"));
        msgs = Flux.fromIterable(testData);
        filtered = crawler.filterMessages(source, cluster, msgs);
        assertEquals(testData.size(), filtered.size());

        // test TO recipients
        cluster.clear();
        cluster.add(cluster("info@contextsmith.com"));
        msgs = Flux.fromIterable(testData);
        filtered = crawler.filterMessages(source, cluster, msgs);
        assertEquals(testData.size(), filtered.size());

        cluster.clear();

        // test with cluster data that is not matching the e-mail
        cluster.add(cluster("other@example.com"));
        msgs = Flux.fromIterable(testData);
        filtered = crawler.filterMessages(source, cluster, msgs);
        assertTrue(filtered.size() == 0);

    }

}