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
import com.contextsmith.email.parser.EnglishEmailBodyParser;
import com.contextsmith.email.provider.EmailFetcher;
import com.contextsmith.email.provider.GmailServiceProvider;
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

  public static final int MAX_PREVIEW_CHARS = 8000;  // 280;  // Double tweets.

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
  private String mimeMessageId;  // Email message id.
  private String gmailMessageId;  // From gmail.
  private Date sentDate;
  private String subject;

  // From email content.
  private String previewContent;

  public ContextMessage() {
    this.from = null;
    this.to = null;
    this.cc = null;
    this.sourceInboxes = null;
    this.mimeMessageId = null;
    this.gmailMessageId = null;
    this.sentDate = null;
    this.subject = null;
    this.previewContent = null;
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
    if (this.mimeMessageId == null) {
      if (other.mimeMessageId != null) return false;
    } else if (!this.mimeMessageId.equals(other.mimeMessageId)) {
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

  public String getGmailMessageId() {
    return this.gmailMessageId;
  }

  public String getMimeMessageId() {
    return this.mimeMessageId;
  }

  public String getPreviewContent() {
    return this.previewContent;
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
    result = prime * result + ((this.mimeMessageId == null) ? 0 : this.mimeMessageId.hashCode());
    return result;
  }

  public void setMimeMessageId(String messageId) {
    this.mimeMessageId = messageId;
  }

  protected ContextMessage loadFrom(MimeMessage message, boolean includePreview,
                                    EmailNameResolver enResolver)
      throws IOException, MessagingException {

    this.mimeMessageId = message.getMessageID();  // For equals().
    this.sentDate = message.getSentDate();  // For sorting.
    if (this.mimeMessageId == null || this.sentDate == null) {
      throw new MessagingException();
    }

    this.subject = message.getSubject();
    this.from = MimeMessageUtil.getValidSenders(message);
    this.to = MimeMessageUtil.getValidRecipientTo(message);
    this.cc = MimeMessageUtil.getValidRecipientCC(message);
    this.sourceInboxes = EmailFetcher.getSourceInboxes(message);
    this.gmailMessageId = GmailServiceProvider.getGmailMessageId(message);

    // Update personal names.
    enResolver.resolve(this.from);
    enResolver.resolve(this.to);
    enResolver.resolve(this.cc);

    if (includePreview) {
      String plainTextContent = MimeMessageUtil.extractPlainText(message);
      if (plainTextContent == null) throw new MessagingException();

      this.previewContent = EnglishEmailBodyParser
          .getInstance().parse(plainTextContent, this.from, Sets.union(this.to, this.cc))
          .getBody().replaceAll("[ ]+", " ").trim();
//          .replaceAll(EnglishEmailBodyParser.END_LINE_RE, " ")
//          .replaceAll("\\s+", " ").trim();
      this.previewContent = truncateAtWordBoundary(this.previewContent,
                                                   MAX_PREVIEW_CHARS);
    }
    return this;
  }
}
