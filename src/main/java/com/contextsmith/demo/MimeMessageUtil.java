package com.contextsmith.demo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.util.Strings;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MimeMessageUtil {
  
  public enum AddressField { FROM, REPLY_TO, TO, CC, BCC, ANY_RECIPIENT }
  
  private static final Logger log = LogManager.getLogger(MimeMessageUtil.class);
  
  public static final String NO_REPLY_ADDR_RE = "(?i).*?reply.*?@.+";
  public static final String GIBBERISH_ADDR_RE = "(?i).*?([a-z]+[0-9]+){3,}.*?@.+";
  public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";
  public static final String MIME_MESSAGE_ID_HEADER = "Message-ID";
  public static final Set<String> COMMON_MAIL_HOST_DOMAINS =
      Sets.newHashSet("gmail.com", "yahoo.com", "live.com", "hotmail.com", 
                      "aol.com", "mail.com", "inbox.com", "outlook.com");
  
  public static String getAddressDomain(InternetAddress address) {
    return address.getAddress().replaceFirst("^.+?@", "");
  }
  
  public static String getHeader(MimeMessage message, String field) {
    String value = null;
    try { value = message.getHeader(field, null); } 
    catch (MessagingException e) {}
    return value;
  }
  
  public static String getMessageId(MimeMessage message) {
    return getHeader(message, MIME_MESSAGE_ID_HEADER);
  }
  
  public static Set<InternetAddress> getValidAddresses(MimeMessage message, 
                                                       AddressField type) {
    Set<InternetAddress> results = new HashSet<>();
    Address[] addresses = null;
    try {
      switch(type) {
      case FROM: addresses = message.getFrom(); break;
      case REPLY_TO: addresses = message.getReplyTo(); break;
      case TO: addresses = message.getRecipients(RecipientType.TO); break;
      case CC: addresses = message.getRecipients(RecipientType.CC); break;
      case BCC: addresses = message.getRecipients(RecipientType.BCC); break;
      case ANY_RECIPIENT: addresses = message.getAllRecipients(); break;
      default: log.error("Unknown address field: " + type);
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
  
  public static Set<InternetAddress> getValidRecipientBCC(MimeMessage message) {
    return getValidAddresses(message, AddressField.BCC);
  }
  
  public static Set<InternetAddress> getValidRecipientCC(MimeMessage message) {
    return getValidAddresses(message, AddressField.CC);
  }
  
  public static Set<InternetAddress> getValidRecipients(MimeMessage message) {
    return getValidAddresses(message, AddressField.ANY_RECIPIENT);
  }
  
  public static Set<InternetAddress> getValidRecipientTo(MimeMessage message) {
    return getValidAddresses(message, AddressField.TO);
  }
  
  public static Set<InternetAddress> getValidReplyTo(MimeMessage message) {
    return getValidAddresses(message, AddressField.REPLY_TO);
  }
  
  public static Set<InternetAddress> getValidSenders(MimeMessage message) {
    return getValidAddresses(message, AddressField.FROM);
  }
  
  public static boolean hasDomain(InternetAddress address, String domain) {
    return getAddressDomain(address).equalsIgnoreCase(domain);
  }
  
  public static boolean isCommonDomain(InternetAddress address) {
    String userDomain = getAddressDomain(address).toLowerCase();
    return COMMON_MAIL_HOST_DOMAINS.contains(userDomain);
  }
  
  // TODO(rcwang): Need to print out detailed 'debug' log messages.
  public static boolean isUsefulMessage(MimeMessage message) {
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
    if (senderAddr.matches(NO_REPLY_ADDR_RE)) return false;
    if (senderAddr.matches(GIBBERISH_ADDR_RE)) return false;
    
    Set<InternetAddress> replyToAddrs = getValidReplyTo(message);
    if (!replyToAddrs.isEmpty()) {
      if (replyToAddrs.size() > 1) return false;
      String replyToAddr = replyToAddrs.iterator().next().getAddress();
      if (replyToAddr.matches(NO_REPLY_ADDR_RE)) return false;
      if (replyToAddr.matches(GIBBERISH_ADDR_RE)) return false;
    }
    Set<InternetAddress> recipients = getValidRecipients(message);
    if (recipients.isEmpty()) return false;
    if (senders.equals(recipients)) return false;
    return true;
  }
  
  // Check the validity of the e-mail address.
  public static boolean isValidAddress(InternetAddress address) {
    checkNotNull(address);
    return EmailValidator.getInstance().isValid(address.getAddress());
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
}