package com.contextsmith.email.cluster;

import com.contextsmith.api.data.EmailMessage;
import com.contextsmith.api.data.Messageable;
import com.contextsmith.utils.MimeMessageUtilTest;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class EmailPeopleManagerTest {

    private EmailPeopleManager epm;

    @Before
    public void setUp() throws IOException, MessagingException {
        epm = new EmailPeopleManager();
        EmailMessage msg = sampleMessage();
        epm.loadMessages(Collections.singletonList(msg));
    }

    private EmailMessage sampleMessage() throws IOException, MessagingException {
        EmailMessage emailMessage = new EmailMessage();

        EmailNameResolver emailNameResolver = new EmailNameResolver();
        MimeMessage msg = MimeMessageUtilTest.readMessage(getClass().getResource("/issue-34/original_msg_first.txt"));
        List<MimeMessage> mimeMessages = Collections.singletonList(msg);
        emailNameResolver.loadMimeMessages(mimeMessages);

        return emailMessage.loadFrom(msg, emailNameResolver);
    }

    @Test
    public void lookupMessages() throws Exception {
        Collection<Messageable> messageables = epm.lookupMessages(new InternetAddress("mark.kosoglow@outreach.io"));
        System.out.println(messageables);
    }

    @Test
    public void lookupWildcard() throws Exception {
        Collection<Messageable> messageables = epm.lookupMessages(new InternetAddress("*@outreach.io"));
        System.out.println(messageables);
        assertTrue(messageables.iterator().next().getAllRecipients().stream().anyMatch(addr -> addr.getAddress().contains("outreach.io")));
    }


}