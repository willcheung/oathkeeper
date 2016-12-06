package com.contextsmith.api.data;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.MimeMessageUtil.AddressField;
import com.google.common.collect.Sets;

public abstract class AbstractMessage implements Messageable {
//  private static final Logger log = LoggerFactory.getLogger(SimpleMessage.class);

  protected String messageId;
  protected String[] sourceInboxes;
  protected Set<InternetAddress> from;   // Sender.
  protected Set<InternetAddress> to;     // Primary recipients.
  protected Set<InternetAddress> cc;     // Secondary recipients.
  protected String subject;
  protected transient String plainText;  // Original content.

  public AbstractMessage() {
    this.messageId = null;
    this.sourceInboxes = null;
    this.from = null;
    this.to = null;
    this.cc = null;
    this.subject = null;
    this.plainText = null;
  }

  @Override
  public int compareTo(Messageable message) {
    return this.getDateForTimeline().compareTo(message.getDateForTimeline());
  }

  // Compare by Message-Id.
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    AbstractMessage other = (AbstractMessage) obj;
    if (this.messageId == null) {
      if (other.messageId != null) return false;
    } else if (!this.messageId.equals(other.messageId)) {
      return false;
    }
    return true;
  }

  public Set<InternetAddress> getAddress(AddressField field) {
    switch (field) {
    case FROM: return this.getFrom();
    case TO: return this.getTo();
    case CC: return this.getCc();
    default: return null;
    }
  }

  @Override
  public Set<InternetAddress> getAllRecipients() {
    if (this.to == null && this.cc == null) return new HashSet<>();
    else if (this.to == null) return this.cc;
    else if (this.cc == null) return this.to;
    else return Sets.union(this.to, this.cc);
  }

  @Override
  public Set<InternetAddress> getCc() {
    return this.cc;
  }

  @Override
  public abstract Date getDateForTimeline();

  @Override
  public Set<InternetAddress> getFrom() {
    return this.from;
  }

  @Override
  public String getMessageId() {
    return this.messageId;
  }

  @Override
  public String getPlainText() {
    return this.plainText;
  }

  @Override
  public String[] getSourceInboxes() {
    return this.sourceInboxes;
  }

  @Override
  public String getSubject() {
    return this.subject;
  }

  @Override
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
}
