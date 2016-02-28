package com.contextsmith.api.data;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.Date;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.nlp.email.parser.EnglishEmailBodyParser;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.collect.Sets;

public class ContextMessage implements Comparable<ContextMessage> {

  /*public static class Builder {
    private MimeMessage message;
    private EmailNameResolver enResolver;
    private boolean includePreview;

    public ContextMessage build() throws IOException, MessagingException {
      ContextMessage m = new ContextMessage();
      return m.loadFrom(this.message, this.includePreview, this.enResolver);
    }

    public Builder setEnResolver(EmailNameResolver enResolver) {
      this.enResolver = enResolver;
      return this;
    }

    public Builder setIncludePreview(boolean includePreview) {
      this.includePreview = includePreview;
      return this;
    }

    public Builder setMessage(MimeMessage message) {
      this.message = message;
      return this;
    }
  }*/

  static final Logger log = LogManager.getLogger(ContextMessage.class);

  public static final int MAX_CONTENT_CHARS = 8000;  // 280;  // Double tweets.

  public static ContextMessage build(MimeMessage message, boolean includePreview,
                                     EmailNameResolver enResolver)
      throws IOException, MessagingException {
    return (new ContextMessage()).loadFrom(message, includePreview, enResolver);
  }

  private static String truncateAtWordBoundary(String text, int maxChars) {
    if (text.length() < maxChars) return text;
    BreakIterator bi = BreakIterator.getWordInstance();
    bi.setText(text);
    int firstBeforeOffset = bi.preceding(maxChars);
    return text.substring(0, firstBeforeOffset).trim() + "...";
  }

  // From email header.
  private Set<InternetAddress> from;
  private Set<InternetAddress> to;
  private Set<InternetAddress> cc;
  private String[] sourceInboxes;
  private String messageId;
//  private String gmailMessageId;  // From gmail.
  private Date sentDate;
  private String subject;

  // From email content.
  private String content;

  public ContextMessage() {
    this.from = null;
    this.to = null;
    this.cc = null;
    this.sourceInboxes = null;
    this.messageId = null;
//    this.gmailMessageId = null;
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

  /*public String getGmailMessageId() {
    return this.gmailMessageId;
  }*/

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

  protected ContextMessage loadFrom(MimeMessage message, boolean includePreview,
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
//    this.gmailMessageId = MimeMessageUtil.getGmailMessageId(message);

    // Update personal names.
    enResolver.resolve(this.from);
    enResolver.resolve(this.to);
    enResolver.resolve(this.cc);

    if (includePreview) {
      String plainTextContent = MimeMessageUtil.extractPlainText(message);
      if (plainTextContent == null) throw new MessagingException();

      EnglishEmailBodyParser parser = new EnglishEmailBodyParser();
      this.content =
          parser.parse(plainTextContent, this.from, Sets.union(this.to, this.cc))
                .getBody().replaceAll("[ ]+", " ").trim();
//          .replaceAll(EnglishEmailBodyParser.END_LINE_RE, " ")
//          .replaceAll("\\s+", " ").trim();
      this.content = truncateAtWordBoundary(this.content, MAX_CONTENT_CHARS);
    }
    return this;
  }
}
