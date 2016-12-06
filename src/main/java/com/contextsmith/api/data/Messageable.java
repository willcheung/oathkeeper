package com.contextsmith.api.data;

import java.util.Date;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.MimeMessageUtil.AddressField;

public interface Messageable extends Comparable<Messageable> {
  public Set<InternetAddress> getAddress(AddressField field);
  public Set<InternetAddress> getAllRecipients();
  public Set<InternetAddress> getCc();
  public Date getDateForTimeline();
  public Set<InternetAddress> getFrom();
  public String getMessageId();
  public String getPlainText();
  public String[] getSourceInboxes();
  public String getSubject();
  public Set<InternetAddress> getTo();
}
