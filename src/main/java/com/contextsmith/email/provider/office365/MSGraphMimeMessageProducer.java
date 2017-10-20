package com.contextsmith.email.provider.office365;

import com.contextsmith.api.service.NewsFeederRequest;
import com.contextsmith.utils.Lambda;
import com.contextsmith.utils.MimeMessageUtil;
import com.microsoft.graph.extensions.*;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.List;
import java.util.function.Function;

import static javax.mail.Message.RecipientType.CC;
import static javax.mail.Message.RecipientType.TO;

/** Receives messages form MS Graph API and turns them into Mime Messages */

public class MSGraphMimeMessageProducer {
    static final Session session = Session.getDefaultInstance(new Properties());
    static final Logger log = LoggerFactory.getLogger(MSGraphMimeMessageProducer.class);

    private IGraphServiceClient service;
    private Queue<MimeMessage> currentBatch;
    private boolean moreResults;
    private int itemsRetrieved;
    private int maxCount = 10000;
    private int pageSize = 50;
    private String query;
    IMessageCollectionRequest page;

    public MSGraphMimeMessageProducer(IGraphServiceClient service) {
        this.service = service;
    }

    public void prepare() throws Exception {
        List<Option> ops = StringUtils.isBlank(query) ?  // either query or orderBy. Can't have both
                options("$orderBy","receivedDateTime desc") :
                options("$search", '"' + query + '"');

        page = service.getMe().getMailFolders("Inbox").getMessages().buildRequest(ops)
                .expand("attachments($select=name,id,contentType,isInline)").top(pageSize);
    }

    MimeMessage produceNext() throws Exception {
        if (page == null) {
            prepare();
        }
        if (currentBatch == null) {
            moreResults = getNextBatch();
        }
        MimeMessage email = currentBatch.poll();
        if (email == null && moreResults) {
            moreResults = getNextBatch();
            email = currentBatch.poll();
        }
        return email;
    }

    private boolean getNextBatch() throws Exception {
        currentBatch = new ArrayDeque<>(pageSize);

        IMessageCollectionPage collection = page.get();
        List<Message> results = collection.getCurrentPage();
        if (results.isEmpty()) return false;

        results.stream().map(this::buildMimeMessage).filter(Objects::nonNull).forEach(msg -> {
            if (itemsRetrieved < maxCount) {
                currentBatch.add(msg);
            } // ignore the rest of the page
            itemsRetrieved ++;
            if (itemsRetrieved % pageSize == 0) {
                log.info("Received " + itemsRetrieved + " e-mails:" + service);
            }
        });
        IMessageCollectionRequestBuilder nextPage = collection.getNextPage();
        page = nextPage != null ? nextPage.buildRequest().expand("attachments($select=name,id,contentType,isInline)") : null;

        return itemsRetrieved < maxCount && page != null; // false == no more items
    }

    private MimeMessage buildMimeMessage(Message msg) {
        try {
            MimeMessage mime = new MimeMessageUtil.FixedMimeMessage(session);

            Address[] from = new Address[]{toIA(msg.from)};
            mime.addFrom(from);
            mime.setSender(toIA(msg.sender));
            mime.setSubject(msg.subject);
            mime.setSentDate(msg.sentDateTime.getTime());
            Function<List<Recipient>, InternetAddress[]> convertRecipients = recipients ->
                    recipients.stream().map(MSGraphMimeMessageProducer::toIA).filter(Objects::nonNull).toArray(InternetAddress[]::new);
            mime.addRecipients(TO, convertRecipients.apply(msg.toRecipients));
            mime.addRecipients(CC, convertRecipients.apply(msg.ccRecipients));
            // BCC ignored currently mime.addRecipients(BCC, convertRecipients.apply(msg.bccRecipients));
            String mimeType = msg.body.contentType == BodyType.html ? "text/html" : "text/plain";
            mime.addHeader("Message-ID", msg.internetMessageId);
            mime.addHeader(MimeMessageUtil.X_PRIVATE_ID, msg.id != null ? msg.id : "");
            mime.addHeader(MimeMessageUtil.X_SOURCE, NewsFeederRequest.Provider.office365.toString());
            // mime.addHeader(MimeMessageUtil.X_WEBLINK, msg.webLink); TODO for the future

            if (msg.hasAttachments) {
                MimeMultipart mp = new MimeMultipart();
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setContent(msg.body.content, mimeType);
                mp.addBodyPart(textPart);

                for (Attachment at : msg.attachments.getCurrentPage()) {
                    if (at.isInline || StringUtils.isBlank(at.name))
                        continue; // ignore non-file attachments and inline attachments
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    if (!StringUtils.isBlank(at.contentType)) {
                        if (at.contentType.equalsIgnoreCase("message/rfc822")) {
                            attachmentPart.setContent(new EmptyMimeMessage(), at.contentType);
                        } else {
                            attachmentPart.setContent("", at.contentType);  // no actual content, we just pretend at the moment. TODO download content
                        }
                        attachmentPart.addHeader(MimeMessageUtil.X_ATTACHMENT_ID, at.id);
                        attachmentPart.setFileName(at.name);
                        mp.addBodyPart(attachmentPart);
                    } else {
                        log.warn("content type for outlook attachments is null " + msg + "at:" + at);
                    }
                }
                mime.setContent(mp);
            } else {
                mime.setContent(msg.body.content, mimeType);
            }
            mime.saveChanges();
            return mime;
        } catch (Exception exp) {
            log.error("Failed to build mime message for: " + msg.id, exp);
        }
        return null;
    }

    private static InternetAddress toIA(Recipient ea)  {
        try {
            return new InternetAddress(ea.emailAddress.address, ea.emailAddress.name);
        } catch (UnsupportedEncodingException e) {
            log.error("Invalid email address: " + ea.emailAddress.address + ":" + ea.emailAddress.name, e);
            return null;
        }
    }

    private void finish() {
       // nothing to do
    }

    static MSGraphMimeMessageProducer produce(MSGraphMimeMessageProducer producer, reactor.core.publisher.SynchronousSink<MimeMessage> sink) {
        try {
            MimeMessage email = producer.produceNext();
            if (email == null) {
                sink.complete();
            } else {
                sink.next(email);
            }
        } catch (Exception e) {

            sink.error(e);
        }
        return producer;
    }

    public Flux<MimeMessage> asFlux() {
        return Flux.generate(() -> this, MSGraphMimeMessageProducer::produce, MSGraphMimeMessageProducer::finish);
    }

    /** Query in AQS syntax https://technet.microsoft.com/en-us/library/bb232132(v=exchg.141).aspx */
    public MSGraphMimeMessageProducer query(String query) {
        this.query = query; return this;
    }

    public MSGraphMimeMessageProducer maxMessages(int max) {
        this.maxCount = max;
        return this;
    }

    private static List<Option> options(String... pairs) {
        return Lambda.listFromTuples(QueryOption::new, pairs);
    }

    static class EmptyMimeMessage extends MimeMessage {
        EmptyMimeMessage() {
            super(MSGraphMimeMessageProducer.session);
            try {
                setText("");
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }
    }

}