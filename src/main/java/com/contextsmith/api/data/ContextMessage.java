package com.contextsmith.api.data;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.email.parser.EnglishEmailTextParser;
import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.nlp.annotator.RequestAnnotator;
import com.contextsmith.nlp.sentiment.SentimentItem;
import com.contextsmith.nlp.sentiment.SentimentScorer;
import com.contextsmith.nlp.time.TaskAnnotator;
import com.contextsmith.nlp.time.TemporalItem;
import com.contextsmith.utils.AnnotationUtil;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.collect.Sets;

public class ContextMessage implements Comparable<ContextMessage> {
  private static final Logger log = LoggerFactory.getLogger(ContextMessage.class);

  public static final int MAX_CONTENT_CHARS = 8000;

  public static ContextMessage newInstance(MimeMessage message,
                                           boolean showContent,
                                           boolean parseTime,
                                           boolean parseRequest,
                                           Double posSentimentThreshold,
                                           Double negSentimentThreshold,
                                           Pattern searchPattern,
                                           EmailNameResolver enResolver) {
    try {
      return (new ContextMessage()).loadFrom(
          message, showContent, parseTime, parseRequest, posSentimentThreshold,
          negSentimentThreshold, searchPattern, enResolver);
    } catch (IOException | MessagingException e) {
      log.error(e.toString());
      e.printStackTrace();
      return null;
    }
  }

  // From email header.
  private Set<InternetAddress> from;
  private Set<InternetAddress> to;
  private Set<InternetAddress> cc;
  private String[] sourceInboxes;
  private String messageId;
  private Date sentDate;
  private String subject;
  private boolean isPrivate;

  // From email content.
  private Content content;
  private List<Annotation> searchAnnotations;
  private List<Annotation> requestAnnotations;
  private List<TemporalItem> temporalItems;
  private List<SentimentItem> sentimentItems;

  public ContextMessage() {
    this.from = null;
    this.to = null;
    this.cc = null;
    this.sourceInboxes = null;
    this.messageId = null;
    this.sentDate = null;
    this.subject = null;

    this.content = null;
    this.searchAnnotations = null;
    this.requestAnnotations = null;
    this.temporalItems = null;
    this.sentimentItems = null;
  }

  @Override
  public int compareTo(ContextMessage message) {
    return this.sentDate.compareTo(message.getSentDate());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ContextMessage other = (ContextMessage) obj;
    if (this.messageId == null) {
      if (other.messageId != null) return false;
    } else if (!this.messageId.equals(other.messageId)) {
      return false;
    }
    return true;
  }

  public Set<InternetAddress> getCc() {
    return this.cc;
  }

  public Set<InternetAddress> getFrom() {
    return this.from;
  }

  public String getMessageId() {
    return this.messageId;
  }

  public List<Annotation> getRequestAnnotations() {
    return this.requestAnnotations;
  }

  public List<Annotation> getSearchAnnotations() {
    return this.searchAnnotations;
  }

  public Date getSentDate() {
    return this.sentDate;
  }

  public List<SentimentItem> getSentimentItems() {
    return this.sentimentItems;
  }

  public String[] getSourceInboxes() {
    return this.sourceInboxes;
  }

  public String getSubject() {
    return this.subject;
  }

  public List<TemporalItem> getTemporalItems() {
    return this.temporalItems;
  }

  public Set<InternetAddress> getTo() {
    return this.to;
  }

  public Set<InternetAddress> getToAndCC() {
    if (this.to == null && this.cc == null) return new HashSet<>();
    else if (this.to == null) return this.cc;
    else if (this.cc == null) return this.to;
    else return Sets.union(this.to, this.cc);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.messageId == null) ? 0 : this.messageId.hashCode());
    return result;
  }

  public boolean isPrivate() {
    return this.isPrivate;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public void setPrivate(boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  protected ContextMessage loadFrom(MimeMessage message,
                                    boolean showContent,
                                    boolean parseTime,
                                    boolean parseRequest,
                                    Double posSentimentThreshold,
                                    Double negSentimentThreshold,
                                    Pattern searchPattern,
                                    EmailNameResolver enResolver)
      throws IOException, MessagingException {

    this.messageId = message.getMessageID();  // For equals().
    this.sentDate = message.getSentDate();  // For sorting.
    if (this.messageId == null || this.sentDate == null) {  // Must have both.
      throw new MessagingException();
    }

    this.subject = message.getSubject();
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

    if (showContent) {
      parseEmailText(message);
      if (parseTime) parseTemporalItems(message);
      if (parseRequest) annotateRequests();
      if (searchPattern != null) annotateKeywords(searchPattern);
      if (posSentimentThreshold != null || negSentimentThreshold != null) {
        analyzeSentiment(posSentimentThreshold, negSentimentThreshold);
      }
    }
    return this;
  }

  private void analyzeSentiment(Double posSentimentThreshold,
                                Double negSentimentThreshold) {
    if (StringUtils.isBlank(this.content.body)) return;
    List<SentimentItem> sentences = null;
    try {
      sentences = SentimentScorer.process(this.content.body);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (sentences == null) return;

    SentimentItem mostPosItem = null;
    SentimentItem mostNegItem = null;
    List<SentimentItem> results = new ArrayList<>();

    for (SentimentItem item : sentences) {
      if (posSentimentThreshold != null &&
          item.score > posSentimentThreshold &&
          (mostPosItem == null || item.score > mostPosItem.score)) {
        mostPosItem = item;
      } else if (negSentimentThreshold != null &&
          item.score < negSentimentThreshold &&
          (mostNegItem == null || item.score < mostNegItem.score)) {
        mostNegItem = item;
      }
      // Compute sentiment score at message level by summing up.
//      this.sentimentScore = (this.sentimentScore == null) ?
//          item.score : this.sentimentScore + item.score;
    }
    if (mostPosItem != null) results.add(mostPosItem);
    if (mostNegItem != null) results.add(mostNegItem);
    if (!results.isEmpty()) this.sentimentItems = results;
  }

  private void annotateKeywords(Pattern searchPattern) {
    List<Annotation> annotations = new ArrayList<>();
    Annotation parent = null;

    // Requires e-mail headers: from, to, cc fields.
    String header = Sets.union(this.from, this.getToAndCC()).toString();
    parent = new Annotation(header);
    parent.setType("header");
    annotations.addAll(AnnotationUtil.match(searchPattern, parent));

    // E-mail salutation.
    if (StringUtils.isNotBlank(this.content.salutation)) {
      parent = new Annotation(this.content.salutation);
      parent.setType("salutation");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    // E-mail body.
    if (StringUtils.isNotBlank(this.content.body)) {
      parent = new Annotation(this.content.body);
      parent.setType("body");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    if (StringUtils.isNotBlank(this.content.signature)) {
      parent = new Annotation(this.content.signature);
      parent.setType("signature");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    if (!annotations.isEmpty()) {
      this.searchAnnotations = annotations;
    }
  }

  private void annotateRequests() {
    if (StringUtils.isBlank(this.content.body)) return;
    List<Annotation> requests =
        RequestAnnotator.getInstance().annotate(this.content.body);
    if (!requests.isEmpty()) this.requestAnnotations = requests;
  }

  private void parseEmailText(MimeMessage message)
      throws IOException, MessagingException {
    String plainTextContent = MimeMessageUtil.extractPlainText(message);
    if (plainTextContent == null) throw new MessagingException();

    log.trace("      Parsing email text...");
    EnglishEmailTextParser parser = new EnglishEmailTextParser();
    parser.parse(plainTextContent, this.from, this.getToAndCC());

    if (this.content == null) this.content = new Content();
    this.content.salutation = parser.getSalutation().replaceAll("[ ]+", " ").trim();
    this.content.body = parser.getBody().replaceAll("[ ]+", " ").trim();
    this.content.signature = parser.getSignature().replaceAll("[ ]+", " ").trim();
//    this.content = StringUtil.truncateAtWordBoundary(this.content,
//                                                     MAX_CONTENT_CHARS);
  }

  // Must execute after parseEmailBody.
  private void parseTemporalItems(MimeMessage message)
      throws IOException, MessagingException {
    // Find temporal items.
    log.trace("      Extracting temporal expressions...");
    ZonedDateTime zdt = MimeMessageUtil.getSentDate(message);
    if (zdt == null) return;
    if (StringUtils.isBlank(this.content.body)) return;

    List<Annotation> taskAnns =
        TaskAnnotator.getInstance().annotate(this.content.body, zdt);
    for (Annotation taskAnn : taskAnns) {
      TemporalItem item = TemporalItem.newInstance(taskAnn);
      if (item == null) continue;
      if (this.temporalItems == null) this.temporalItems = new ArrayList<>();
      this.temporalItems.add(item);
    }
  }
}

class Content {
  public String salutation;
  public String body;
  public String signature;
}