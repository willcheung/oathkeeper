package com.contextsmith.api.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.nlp.annotation.Annotation;
import com.contextsmith.nlp.annotation.DateTimeAnnotator;
import com.contextsmith.nlp.annotation.TaskAnnotator;
import com.contextsmith.nlp.email.parser.EnglishEmailBodyParser;
import com.contextsmith.utils.MimeMessageUtil;
import com.contextsmith.utils.StringUtil;
import com.google.common.collect.Sets;
import com.joestelmach.natty.DateGroup;

public class ContextMessage implements Comparable<ContextMessage> {

  static final Logger log = LogManager.getLogger(ContextMessage.class);

  public static final int MAX_CONTENT_CHARS = 8000;

  public static ContextMessage build(MimeMessage message, boolean showContent,
                                     EmailNameResolver enResolver)
      throws IOException, MessagingException {
    return (new ContextMessage()).loadFrom(message, showContent, enResolver);
  }

  // From email header.
  private Set<InternetAddress> from;
  private Set<InternetAddress> to;
  private Set<InternetAddress> cc;
  private List<TemporalItem> temporalItems;
  private String[] sourceInboxes;
  private String messageId;
  private Date sentDate;
  private String subject;

  // From email content.
  private String content;

  public ContextMessage() {
    this.from = null;
    this.to = null;
    this.cc = null;
    this.temporalItems = null;
    this.sourceInboxes = null;
    this.messageId = null;
    this.sentDate = null;
    this.subject = null;
    this.content = null;
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

  public String getContent() {
    return this.content;
  }

  public Set<InternetAddress> getFrom() {
    return this.from;
  }

  public String getMessageId() {
    return this.messageId;
  }

  public Date getSentDate() {
    return this.sentDate;
  }

  public String[] getSourceInboxes() {
    return this.sourceInboxes;
  }

  public String getSubject() {
    return this.subject;
  }

  public Set<InternetAddress> getTo() {
    return this.to;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.messageId == null) ? 0 : this.messageId.hashCode());
    return result;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  protected ContextMessage loadFrom(MimeMessage message, boolean showContent,
                                    EmailNameResolver enResolver)
      throws IOException, MessagingException {

    this.messageId = message.getMessageID();  // For equals().
    this.sentDate = message.getSentDate();  // For sorting.
    if (this.messageId == null || this.sentDate == null) {
      throw new MessagingException();
    }

    this.subject = message.getSubject();
    this.from = MimeMessageUtil.getValidSenders(message);
    this.to = MimeMessageUtil.getValidRecipientTo(message);
    this.cc = MimeMessageUtil.getValidRecipientCC(message);
    this.sourceInboxes = MimeMessageUtil.getSourceInboxes(message);

    // Update personal names.
    enResolver.resolve(this.from);
    enResolver.resolve(this.to);
    enResolver.resolve(this.cc);

    if (showContent) {
      String plainTextContent = MimeMessageUtil.extractPlainText(message);
      if (plainTextContent == null) throw new MessagingException();

      // Find e-mail body content.
      EnglishEmailBodyParser parser = new EnglishEmailBodyParser();
      this.content =
          parser.parse(plainTextContent, this.from, Sets.union(this.to, this.cc))
                .getBody().replaceAll("[ ]+", " ").trim();
//          .replaceAll(EnglishEmailBodyParser.END_LINE_RE, " ")
//          .replaceAll("\\s+", " ").trim();
      this.content = StringUtil.truncateAtWordBoundary(this.content,
                                                       MAX_CONTENT_CHARS);
      // Find temporal items.
      List<Annotation> taskAnns = TaskAnnotator.getInstance().annotate(
          this.content, MimeMessageUtil.getSentDate(message));
      for (Annotation taskAnn : taskAnns) {
        TemporalItem item = TemporalItem.newInstance(taskAnn);
        if (item == null) continue;
        if (this.temporalItems == null) this.temporalItems = new ArrayList<>();
        this.temporalItems.add(item);
      }
    }
    return this;
  }
}

class TemporalItem {
  static final Logger log = LogManager.getLogger(TemporalItem.class);

  public static TemporalItem newInstance(Annotation taskAnn) {
    Annotation dateAnn = TaskAnnotator.getPayload(taskAnn);
    DateGroup group = DateTimeAnnotator.getPayload(dateAnn);
    if (dateAnn == null || group == null || group.getDates().isEmpty()) {
      log.error("Problem initializing temporal item!");
      return null;
    }
    if (group.isDateInferred()) {
      log.debug("Ignoring inferred date.");
      return null;
    }

    TemporalItem item = new TemporalItem();
    item.context = taskAnn.getText();
    item.contextOffsets = new int[]{ taskAnn.getBeginOffset(),
                                     taskAnn.getEndOffset() };
    item.content = dateAnn.getText();
    item.contentOffsets = new int[]{ dateAnn.getBeginOffset(),
                                     dateAnn.getEndOffset() };

    item.hasTime = !group.isTimeInferred();
    if (item.dates == null) item.dates = new ArrayList<>();
    item.dates.add(group.getDates().get(0).getTime() / 1000);
    if (group.getDates().size() > 1) {
      item.dates.add(group.getDates().get(1).getTime() / 1000);
    }
    return item;
  }

  public String context = null;
  public String content = null;
  public int[] contextOffsets = null;
  public int[] contentOffsets = null;
  public List<Long> dates = null;
  public boolean hasTime = false;
}