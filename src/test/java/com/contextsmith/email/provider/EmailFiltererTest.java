package com.contextsmith.email.provider;

import com.contextsmith.utils.MimeMessageUtilTest;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class EmailFiltererTest {

    @Test
    public void testIssue34() throws IOException, MessagingException {
        List<MimeMessage> msgs = new ArrayList<>();
        MimeMessage msg = MimeMessageUtilTest.readMessage(getClass().getResource("/issue-34/original_msg_first.txt"));
        msgs.add(msg);

        EmailFilterer filterer = new EmailFilterer();
        Collection<MimeMessage> filtered = filterer
                .setSubjectRetainPattern(null)
                .setRemoveMailListMessages(false)
                .setRemovePrivateMessages(false)
                .filter(msgs);
        System.out.println(filtered);
        assertFalse(filtered.isEmpty());
    }

}