package com.contextsmith.demo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Set;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.util.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MimeMessageUtil {
  
  public enum PeopleType { SENDERS, REPLY_TO, ALL_RECIPIENTS }
  
  private static final Logger log = LogManager.getLogger(MimeMessageUtil.class);
  
  public static final String NO_REPLY_RE = "(?i).*?reply.*?@.+";
  public static final String GIBBERISH_RE = "(?i).*?([a-z]+[0-9]+){3,}.*?@.+";
  public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";
  
  public static String getAddressDomain(InternetAddress address) {
    return address.getAddress().replaceFirst("^.+?@", "");
  }
  
  public static Set<InternetAddress> getValidRecipients(MimeMessage message) {
    return getAddresses(message, PeopleType.ALL_RECIPIENTS);
  }
  
  public static Set<InternetAddress> getValidReplyToAddrs(MimeMessage message) {
    return getAddresses(message, PeopleType.REPLY_TO);
  }
  
  public static Set<InternetAddress> getValidSenders(MimeMessage message) {
    return getAddresses(message, PeopleType.SENDERS);
  }
  
  public static boolean hasDomain(InternetAddress address, String domain) {
    return getAddressDomain(address).equalsIgnoreCase(domain);
  }
  
  // Check the validity of the e-mail address.
  public static boolean isValidAddress(InternetAddress address) {
    checkNotNull(address);
    return EmailValidator.getInstance().isValid(address.getAddress());
  }
  
  // TODO(rcwang): Need to print out detailed 'debug' log messages.
  public static boolean isValidMessage(MimeMessage message) {
    String[] unsubscribeList = null;
    String messageId = null;
    try {
      unsubscribeList = message.getHeader(LIST_UNSUBSCRIBE_HEADER);
      messageId = message.getMessageID();
    } catch (MessagingException e) {
      System.err.println(e);
    }
    if (unsubscribeList != null && unsubscribeList.length > 0) return false;
    if (Strings.isNullOrEmpty(messageId)) return false;
    
    Set<InternetAddress> senders = getValidSenders(message);
    if (senders.size() != 1) return false;
    String senderAddr = senders.iterator().next().getAddress();
    if (senderAddr.matches(NO_REPLY_RE)) return false;
    if (senderAddr.matches(GIBBERISH_RE)) return false;
    
    Set<InternetAddress> replyToAddrs = getValidReplyToAddrs(message);
    if (!replyToAddrs.isEmpty()) {
      if (replyToAddrs.size() > 1) return false;
      String replyToAddr = replyToAddrs.iterator().next().getAddress();
      if (replyToAddr.matches(NO_REPLY_RE)) return false;
      if (replyToAddr.matches(GIBBERISH_RE)) return false;
    }
    Set<InternetAddress> recipients = getValidRecipients(message);
    if (recipients.isEmpty()) return false;
    if (senders.equals(recipients)) return false;
    return true;
  }
  
  // For debugging purpose.
  public static final void printMimeMessage(MimeMessage message) 
      throws MessagingException {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    log.info("From: " + gson.toJson(message.getFrom()));
    log.info("To: " + gson.toJson(message.getAllRecipients()));
    log.info("Subject: " + message.getSubject());
    log.info("==================");
  }

  public static boolean shouldIgnore(InternetAddress address, 
                                     String domainToIgnore,
                                     Set<InternetAddress> addressesToIgnore) {
    checkNotNull(address);
    if (addressesToIgnore != null && addressesToIgnore.contains(address)) {
      return true;
    }
    if (!Strings.isNullOrEmpty(domainToIgnore) && 
        address.getAddress().matches("(?i).+\\b\\Q" + domainToIgnore + "\\E")) {
      return true;
    }
    return false;
  }
  
  private static Set<InternetAddress> getAddresses(MimeMessage message, 
                                                   PeopleType type) {
    Set<InternetAddress> results = new HashSet<>();
    Address[] addresses = null;
    try {
      switch(type) {
      case SENDERS: addresses = message.getFrom(); break;
      case ALL_RECIPIENTS: addresses = message.getAllRecipients(); break;
      case REPLY_TO: addresses = message.getReplyTo(); break;
      }
    } catch (AddressException e) {
      // ignore
    } catch (MessagingException e) {
      System.err.println(e);
    } 
    if (addresses != null) {
      for (Address address : addresses) {
        InternetAddress addr = (InternetAddress) address;
        if (isValidAddress(addr)) results.add(addr);
      }
    }
    return results;
  }
}