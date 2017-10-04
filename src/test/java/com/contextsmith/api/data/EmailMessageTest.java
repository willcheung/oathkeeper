package com.contextsmith.api.data;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.utils.MimeMessageUtilTest;
import org.junit.Test;

import javax.mail.internet.MimeMessage;
import java.util.Collections;
import java.util.List;

public class EmailMessageTest {
    @Test
    public void issue34() throws Exception {
        EmailMessage emailMessage = new EmailMessage();
        EmailNameResolver emailNameResolver = new EmailNameResolver();
        MimeMessage msg = MimeMessageUtilTest.readMessage(getClass().getResource("/issue-34/original_msg_first.txt"));
        List<MimeMessage> mimeMessages = Collections.singletonList(msg);
        emailNameResolver.loadMimeMessages(mimeMessages);

        EmailMessage email = emailMessage.loadFrom(msg, emailNameResolver);
        System.out.println(email);
    }

}