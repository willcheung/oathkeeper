package com.contextsmith.utils;

import com.contextsmith.api.data.Attachment;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by beders on 6/28/17.
 */
public class MimeMessageUtilTest {
    static final Session session = Session.getDefaultInstance(new Properties(), null);
    @Test
    public void collectAttachments() throws Exception {
        MimeMessage msg = readMessage(this.getClass().getResource("/EmailWithDoc.msg"));
        List<Attachment> attachments = MimeMessageUtil.collectAttachments(msg);
        assertEquals(1, attachments.size());
    }

    @Test
    public void collectSomeAttachments() throws Exception {
        MimeMessage msg = readMessage(this.getClass().getResource("/EmailWithMultipleAttachments.msg"));
        List<Attachment> attachments = MimeMessageUtil.collectAttachments(msg);
        System.out.println(attachments);
        assertTrue(attachments.size() > 0);
    }

    @Test
    public void ignoreInvite() throws Exception {
        MimeMessage msg = readMessage(this.getClass().getResource("/EmailWithInvite.msg"));
        List<Attachment> attachments = MimeMessageUtil.collectAttachments(msg);
        System.out.println(attachments);
        assertTrue(attachments.size() > 0);
        attachments = MimeMessageUtil.collectAttachments(msg, p -> !p.getContentType().startsWith("application/ics"));
        assertTrue(attachments.size() == 0);

    }

    MimeMessage readMessage(URL url) throws IOException, MessagingException {
        return new MimeMessage(session, url.openStream());
    }

}