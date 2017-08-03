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

import static org.junit.Assert.*;

/**
 * Created by beders on 6/28/17.
 */
public class MimeMessageUtilTest {
    static final Session session = Session.getDefaultInstance(new Properties(), null);
    @Test
    public void collectAttachments() throws Exception {
        MimeMessage msg = readMessage(this.getClass().getResource("/EmailWithDoc.msg"));
        List<Attachment> attachments = MimeMessageUtil.collectAttachments(msg);
        System.out.println(attachments);
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
    public void ignoreForwardedAttachments() throws Exception {
        MimeMessage msg = readMessage(this.getClass().getResource("/EmailWithQuotedMsgAndPDF.msg"));
        List<Attachment> attachments = MimeMessageUtil.collectAttachments(msg);
        System.out.println(attachments);
        assertTrue(attachments.size() == 1);
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

    public static MimeMessage readMessage(URL url) throws IOException, MessagingException {
        return new MimeMessage(session, url.openStream());
    }

    @Test
    public void testToURN() {
        String userEmail = "beders@contextsmith.com";
        String urn = MimeMessageUtil.toURN("gmail", userEmail, "bubu", "lala");
        MimeMessageUtil.fromURN(urn, (provider, email, internalID, fragment) -> {
            assertEquals("gmail", provider);
            assertEquals(userEmail, email);
            assertEquals("bubu", internalID);
            assertEquals("lala", fragment);
            return null;
        });

        urn = MimeMessageUtil.toURN("gmail", userEmail, "bubu", null);
        MimeMessageUtil.fromURN(urn, (provider, email, internalID, fragment) -> {
            assertEquals("gmail", provider);
            assertEquals(userEmail, email);
            assertEquals("bubu", internalID);
            assertNull(fragment);
            return null;
        });

        String internalID1 = "@$^&*()LKJ=-_[]}{\\|HGFRGHJK?><MNB";
        urn = MimeMessageUtil.toURN("gmail", userEmail, internalID1, null);
        MimeMessageUtil.fromURN(urn, (provider, email, internalID, fragment) -> {
            assertEquals("gmail", provider);
            assertEquals(userEmail, email);
            assertEquals(internalID1, internalID);
            assertNull(fragment);
            return null;
        });
    }

}