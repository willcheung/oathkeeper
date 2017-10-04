package com.contextsmith.email.provider.exchange;

import com.contextsmith.api.service.NewsFeederRequest;
import com.contextsmith.utils.MimeMessageUtil;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BodyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.property.complex.Attachment;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

/**
 * Created by beders on 4/24/17.
 */
public class MimeMessageProducer {
    static final Session session = Session.getDefaultInstance(new Properties());

    private ItemView view;
    private ExchangeService service;
    private Queue<MimeMessage> currentBatch;
    private boolean moreResults;
    private int itemsRetrieved;
    private int maxCount = 10000;
    private int pageSize = 50;
    private String query;

    public MimeMessageProducer(ExchangeService service) {
        this.service = service;
    }

    public void prepare() throws Exception {
        view = new ItemView(Math.min(pageSize, maxCount));
        view.getOrderBy().add(ItemSchema.DateTimeReceived, SortDirection.Ascending);
    }

    MimeMessage produceNext() throws Exception {
        if (view == null) {
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

        FindItemsResults<Item> findResults;
        findResults = service.findItems(WellKnownFolderName.Inbox, query, view);
        if (findResults.getItems().size() == 0) return false;

        service.loadPropertiesForItems(findResults, PropertySet.FirstClassProperties);

        for (Item item : findResults.getItems()) {
            if (item instanceof EmailMessage) {
                EmailMessage msg = ((EmailMessage) item);
                currentBatch.add(buildMimeMessage(msg));
            } else {
                System.out.println(item.getClass());
            }
            // Do something with the item.
            itemsRetrieved++;
            if (itemsRetrieved % pageSize == 0) {
                System.out.format("Received " + itemsRetrieved + " e-mails\n");
            }
        }
        view.setOffset(view.getOffset() + pageSize);

        return itemsRetrieved < maxCount ? findResults.isMoreAvailable() : false; // false == no more items
    }

    private MimeMessage buildMimeMessage(EmailMessage msg) throws Exception {
        MimeMessage mime = new MimeMessage(session);
        Address[] from = new Address[] { toIA(msg.getFrom()) };
        mime.addFrom(from);
        mime.setSender(toIA(msg.getSender()));
        mime.setSubject(msg.getSubject());
        mime.setSentDate(msg.getDateTimeSent());
        for (EmailAddress ea : msg.getToRecipients()) {
            mime.addRecipient(Message.RecipientType.TO, toIA(ea));
        }
        for (EmailAddress ea : msg.getCcRecipients()) {
            mime.addRecipient(Message.RecipientType.CC, toIA(ea));
        }
        String mimeType = msg.getBody().getBodyType() == BodyType.HTML ? "text/html" : "text/plain";
        mime.addHeader("Message-ID", msg.getInternetMessageId());
        mime.addHeader(MimeMessageUtil.X_PRIVATE_ID, msg.getId() != null ? msg.getId().getUniqueId() : "");
        mime.addHeader(MimeMessageUtil.X_SOURCE, NewsFeederRequest.Provider.exchange.toString());

        if (msg.getHasAttachments()) {
            MimeMultipart mp = new MimeMultipart();
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(msg.getBody().toString(), mimeType);
            mp.addBodyPart(textPart);

            for (Attachment at : msg.getAttachments()) {
                if (at.getIsInline() || StringUtils.isBlank(at.getName())) continue; // ignore non-file attachments and inline attachments
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setContent("", at.getContentType());  // no actual content, we just pretend at the moment. TODO download content
                attachmentPart.addHeader(MimeMessageUtil.X_ATTACHMENT_ID, at.getId());
                attachmentPart.setFileName(at.getName());
                mp.addBodyPart(attachmentPart);
            }
            mime.setContent(mp);
        } else {
            mime.setContent(msg.getBody().toString(), mimeType);
        }
        mime.saveChanges();
        return mime;
    }

    private static InternetAddress toIA(EmailAddress ea) throws UnsupportedEncodingException {
        return new InternetAddress(ea.getAddress(), ea.getName());
    }

    private void finish() {
        service.close();
    }

    static MimeMessageProducer produce(MimeMessageProducer producer, reactor.core.publisher.SynchronousSink<MimeMessage> sink) {
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
        return Flux.generate(() -> this, MimeMessageProducer::produce, MimeMessageProducer::finish);
    }

    /** Query in AQS syntax https://technet.microsoft.com/en-us/library/bb232132(v=exchg.141).aspx */
    public MimeMessageProducer query(String query) {
        this.query = query; return this;
    }

    public MimeMessageProducer maxMessages(int max) {
        this.maxCount = max;
        return this;
    }
}
