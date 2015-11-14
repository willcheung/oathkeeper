package com.contextsmith.api.data;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.Date;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.contextsmith.email.parser.EnglishEmailBodyParser;
import com.contextsmith.email.provider.GmailServiceProvider;
import com.contextsmith.utils.MimeMessageUtil;

public class ContextMessage implements Comparable<ContextMessage> {

  public static final int MAX_PREVIEW_CHARS = 280;  // Double tweets.

  private static String truncateAtWordBoundary(String text, int maxChars) {
    if (text.length() < maxChars) return text;
    BreakIterator bi = BreakIterator.getWordInstance();
    bi.setText(text);
    int firstBeforeOffset = bi.preceding(maxChars);
    return text.substring(0, firstBeforeOffset).trim() + "...";
  }

  private Set<InternetAddress> from;
  private Set<InternetAddress> to;
  private Set<InternetAddress> cc;
  private String mimeMessageId;  // Email message id.
  private String gmailMessageId;  // From gmail.
  private String subject;
  private Date sentDate;

  //  private String htmlContent;
  //  private String plainTextContent;
  private String previewContent;

  public ContextMessage(MimeMessage message)
        throws IOException, MessagingException {
      convertMimeToContextMessage(message);
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

//  public String getHtmlContent() {
//    return this.htmlContent;
//  }

  public String getGmailMessageId() {
    return this.gmailMessageId;
  }

//  public String getPlainTextContent() {
//    return this.plainTextContent;
//  }

  public String getMimeMessageId() {
    return this.mimeMessageId;
  }

  public String getPreviewContent() {
    return this.previewContent;
  }

  public Date getSentDate() {
    return this.sentDate;
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

  private void convertMimeToContextMessage(MimeMessage message)
      throws IOException, MessagingException {
    this.mimeMessageId = message.getMessageID();  // For equals().
    this.sentDate = message.getSentDate();  // For sorting.
    if (this.mimeMessageId == null || this.sentDate == null) {
      throw new MessagingException();
    }

    // this.htmlContent = MimeMessageUtil.extractHtmlText(message);
    String plainTextContent = MimeMessageUtil.extractPlainText(message);
    if (plainTextContent == null) throw new MessagingException();

    this.previewContent = EnglishEmailBodyParser
        .getInstance().parse(plainTextContent).getBody()
        .replaceAll(EnglishEmailBodyParser.END_LINE_RE, " ")
        .replaceAll("\\s+", " ").trim();
    this.previewContent = truncateAtWordBoundary(this.previewContent,
                                                 MAX_PREVIEW_CHARS);

    this.subject = message.getSubject();
    this.from = MimeMessageUtil.getValidSenders(message);
    this.to = MimeMessageUtil.getValidRecipientTo(message);
    this.cc = MimeMessageUtil.getValidRecipientCC(message);
    this.gmailMessageId = GmailServiceProvider.getGmailMessageId(message);
  }
}
