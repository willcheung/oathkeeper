package com.contextsmith.api.data;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.nlp.sentiment.SentimentItem;
import com.contextsmith.nlp.time.TemporalItem;
import com.contextsmith.utils.MimeMessageUtil;
import org.apache.commons.lang3.StringUtils;

public class EmailMessage extends AbstractMessage {
  private ZonedDateTime sentDate;
  private MailContent content;
  private Boolean isPrivate;
  private List<Annotation> searchAnnotations;
  private List<Annotation> requestAnnotations;
  private List<TemporalItem> temporalItems;
  private List<SentimentItem> sentimentItems;

  public EmailMessage() {
    super();
    this.sentDate = null;
    this.isPrivate = null;
    this.content = null;
    this.searchAnnotations = null;
    this.requestAnnotations = null;
    this.temporalItems = null;
    this.sentimentItems = null;
  }

  public MailContent getContent() {
    return this.content;
  }

  @Override
  public Date getDateForTimeline() {
    return Date.from(this.sentDate.toInstant());
  }

  public List<Annotation> getRequestAnnotations() {
    return this.requestAnnotations;
  }

  public List<Annotation> getSearchAnnotations() {
    return this.searchAnnotations;
  }

  public ZonedDateTime getSentDate() {
    return this.sentDate;
  }

  public List<SentimentItem> getSentimentItems() {
    return this.sentimentItems;
  }

  public List<TemporalItem> getTemporalItems() {
    return this.temporalItems;
  }

  public Boolean isPrivate() {
    return this.isPrivate;
  }

  public EmailMessage loadFrom(MimeMessage message,
                               EmailNameResolver enResolver)
          throws IOException, MessagingException {
    this.messageId = message.getMessageID();  // For equals().
    this.sentDate = MimeMessageUtil.getSentDate(message);
    if (this.messageId == null || this.sentDate == null) {  // Must have both.
      throw new MessagingException();
    }

    if (StringUtils.isNotBlank(message.getSubject())) {
      this.subject = message.getSubject();
    }
    this.plainText = MimeMessageUtil.extractPlainText(message);
    this.sourceInboxes = MimeMessageUtil.getSourceInboxes(message);
    this.isPrivate =
        MimeMessageUtil.getValidRecipients(message).size() == 1 &&
        !MimeMessageUtil.hasMailingListInRecipients(message);

    Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
    if (senders != null && !senders.isEmpty()) {
      this.from = enResolver.resolve(senders);
    }
    Set<InternetAddress> recipientTo = MimeMessageUtil.getValidRecipientTo(message);
    if (recipientTo != null && !recipientTo.isEmpty()) {
      this.to = enResolver.resolve(recipientTo);
    }
    Set<InternetAddress> recipientCC = MimeMessageUtil.getValidRecipientCC(message);
    if (recipientCC != null && !recipientCC.isEmpty()) {
      this.cc = enResolver.resolve(recipientCC);
    }
    return this;
  }

  public void processContent(boolean parseTime,
                             boolean parseRequest,
                             Double posSentimentThreshold,
                             Double negSentimentThreshold,
                             Pattern searchPattern) {
    this.content = EmailMessageProcessor.parseEmailText(this);
    // Cannot continue if there is no content parsed.
    if (this.content == null) return;

    if (parseTime) {
      this.temporalItems = EmailMessageProcessor.parseTemporalItems(this);
    }
    if (parseRequest) {
      this.requestAnnotations = EmailMessageProcessor.annotateRequests(this);
    }
    if (searchPattern != null) {
      this.searchAnnotations =
          EmailMessageProcessor.annotateKeywords(this, searchPattern);
    }
    if (posSentimentThreshold != null || negSentimentThreshold != null) {
      this.sentimentItems = EmailMessageProcessor.analyzeSentiment(
          this, posSentimentThreshold, negSentimentThreshold);
    }
  }
}

class MailContent {
  public String salutation;
  public String body;
  public String signature;
}