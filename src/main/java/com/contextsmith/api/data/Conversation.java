package com.contextsmith.api.data;

import java.io.IOException;
import java.util.Date;
import java.util.TreeSet;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class Conversation implements Comparable<Conversation> {

  private String id;  // Gmail thread id?
  private String subject;
  private Date lastSentDate;
  private TreeSet<ContextMessage> contextMessages;

  public Conversation(String id) {
    this.id = id;
    this.subject = null;
    this.lastSentDate = null;
    this.contextMessages = new TreeSet<ContextMessage>();
  }

  public boolean addContextMessage(ContextMessage contextMessage) {
    boolean success = this.contextMessages.add(contextMessage);
    if (success) {
      // Use subject of the earliest message as subject of this conversation.
      this.subject = this.contextMessages.iterator().next().getSubject();
      // Use sent-date of the latest message as the sent-date of this conversation.
      this.lastSentDate = this.contextMessages.last().getSentDate();
    }
    return success;
  }

  public boolean addMimeMessage(MimeMessage mimeMessage)
      throws IOException, MessagingException {
    // Convert from MimeMessage to ContextMessage.
    ContextMessage contextMessage = new ContextMessage(mimeMessage);
    return addContextMessage(contextMessage);
  }

  @Override
  public int compareTo(Conversation conversation) {
    return conversation.getLastSentDate().compareTo(this.lastSentDate);
  }

  public TreeSet<ContextMessage> getContextMessages() {
    return this.contextMessages;
  }

  public String getId() {
    return this.id;
  }

  public Date getLastSentDate() {
    return this.lastSentDate;
  }

  public String getSubject() {
    return this.subject;
  }
}