package com.contextsmith.api.data;

import java.util.Date;
import java.util.TreeSet;

public class Conversation implements Comparable<Conversation> {
  private String conversationId;
  private String subject;
  private Date lastSentDate;
  private TreeSet<Messageable> messages;

  public Conversation(String conversationId) {
    this.conversationId = conversationId;
    this.subject = null;
    this.lastSentDate = null;
    this.messages = new TreeSet<>();
  }

  public boolean addMessage(Messageable message) {
    boolean success = this.messages.add(message);
    if (success) {
      // Use subject of the earliest message as subject of this conversation.
      this.subject = this.messages.iterator().next().getSubject();
      // Use sent-date of the latest message as the sent-date of this conversation.
      this.lastSentDate = this.messages.last().getDateForTimeline();
    }
    return success;
  }

  @Override
  public int compareTo(Conversation conversation) {
    return conversation.getLastSentDate().compareTo(this.getLastSentDate());
  }

  public String getConversationId() {
    return this.conversationId;
  }

  public Date getLastSentDate() {
    return this.lastSentDate;
  }

  public TreeSet<Messageable> getMessages() {
    return this.messages;
  }

  public String getSubject() {
    return this.subject;
  }
}