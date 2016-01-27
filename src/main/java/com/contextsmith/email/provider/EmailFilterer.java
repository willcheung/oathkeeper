package com.contextsmith.email.provider;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class EmailFilterer {

  static final Logger log = LogManager.getLogger(EmailFilterer.class);

  public static final boolean DEFAULT_REMOVE_MAILLIST_MSGS = false;
  public static final boolean DEFAULT_REMOVE_PRIVATE_MSGS = false;

  // Email address regex pattern.
  public static final String NO_REPLY_ADDR_RE = "(?i).*?reply.*?@.+";
  public static final String GIBBERISH_ADDR_RE = "(?i).*?([a-z]+[0-9]+){3,}.*?@.+";

  // MimeMessage header fields.
  public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";

  // Mailing-List indicator in email body.
  public static final String BODY_SUBSCRIBE_WORD = "subscribe";

  private boolean removeMailListMessages;
  private boolean removePrivateMessages;
//  private Set<InternetAddress> externalMembers;

  public EmailFilterer() {
    this.removeMailListMessages = DEFAULT_REMOVE_MAILLIST_MSGS;
    this.removePrivateMessages = DEFAULT_REMOVE_PRIVATE_MSGS;
//    this.externalMembers = new HashSet<>();
  }

  public void filterUselessMessages(Collection<MimeMessage> mimeMessages) {
    int numBeforeFilter = mimeMessages.size();
    for (Iterator<MimeMessage> iter = mimeMessages.iterator(); iter.hasNext();) {
      if (!isUseful(iter.next())) iter.remove();
    }
    int numFiltered = numBeforeFilter - mimeMessages.size();
    int numAfterFilter = mimeMessages.size();
    log.debug(String.format(
        "Filtered %d%% (%d out of %d) invalid emails, %d left.",
        Math.round(100.0 * numFiltered / numBeforeFilter),
        numFiltered, numBeforeFilter, numAfterFilter));
  }

  public boolean isRemoveMailListMessages() {
    return this.removeMailListMessages;
  }

  public boolean isRemovePrivateMessages() {
    return this.removePrivateMessages;
  }

  public boolean isUseful(MimeMessage message) {
    // Check sender.
    Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
    if (senders.size() != 1) {
      log.trace("Message filtered: # of sender is not 1.");
      return false;
    }
    String senderAddr = senders.iterator().next().getAddress();
    if (senderAddr.matches(NO_REPLY_ADDR_RE)) {
      log.trace("Message filtered: Sender has no-reply address '{}'", senderAddr);
      return false;
    }
    if (senderAddr.matches(GIBBERISH_ADDR_RE)) {
      log.trace("Message filtered: Sender has gibberish address '{}'", senderAddr);
      return false;
    }

    // Check recipients.
    Set<InternetAddress> recipients = MimeMessageUtil.getValidRecipients(message);
    if (recipients.isEmpty()) {
      log.trace("Message filtered: No valid recipients.");
      return false;
    }
    if (senders.equals(recipients)) {
      log.trace("Message filtered: Sender is recipient.");
      return false;
    }

    // Check 'Reply-To' field.
    Set<InternetAddress> replyToAddrs = MimeMessageUtil.getValidReplyTo(message);
    if (!replyToAddrs.isEmpty()) {
      if (replyToAddrs.size() > 1) {
        log.trace("Message filtered: Has more than one Reply-To addresses.");
        return false;
      }
      String replyToAddr = replyToAddrs.iterator().next().getAddress();
      if (replyToAddr.matches(NO_REPLY_ADDR_RE)) {
        log.trace("Message filtered: Reply-To has no-reply address '{}'", replyToAddr);
        return false;
      }
      if (replyToAddr.matches(GIBBERISH_ADDR_RE)) {
        log.trace("Message filtered: Reply-To has gibberish address '{}'", replyToAddr);
        return false;
      }
    }

    // Check header.
    try {
      Date date = message.getSentDate();
      if (date == null) {
        log.trace("Message filtered: Missing 'Date' field.");
        return false;
      }
      String messageId = message.getMessageID();
      if (StringUtils.isBlank(messageId)) {
        log.trace("Message filtered: Missing 'Message-ID' field.");
        return false;
      }

      String[] unsubscribeList = message.getHeader(LIST_UNSUBSCRIBE_HEADER);
      if (unsubscribeList != null && unsubscribeList.length > 0) {
        if (this.removeMailListMessages) {
          log.trace("Message filtered: Contains header '{}'",
                    LIST_UNSUBSCRIBE_HEADER);
          return false;
        }
      } else if (recipients.size() == 1) {
        if (this.removePrivateMessages) {
          // A mesage is defined as private if
          // 1) only one recipient, and
          // 2) message is not sent through mailing-list.
          log.trace("Message filtered: Message is private " +
                    "(sent to user directly and has only one recipient)");
          return false;
        }
      }

      /*String[] unsubscribeList = message.getHeader(LIST_UNSUBSCRIBE_HEADER);
      if (unsubscribeList != null && unsubscribeList.length > 0) {
        // Message was sent through a mailing-list.
        InternetAddress sender = senders.iterator().next();
        if (this.externalMembers == null) {
          // If no external members given, then filter out this message.
          log.trace("Message filtered: Contains header '{}'",
                    LIST_UNSUBSCRIBE_HEADER);
          return false;
        } else if (!this.externalMembers.contains(sender)) {
          // If there is external members given but sender is not one of them,
          // then also filter out this message.
          log.trace("Message ({}) filtered: Contains header '{}' and " +
                    "sender {} is not an external member.",
                    messageId, LIST_UNSUBSCRIBE_HEADER, sender);
          return false;
        }
      } else if (recipients.size() == 1) {
        // A mesage is defined as private if
        // 1) only one recipient, and
        // 2) message is not sent through mailing-list.
        log.trace("Message filtered: Message is private " +
                  "(sent to user directly and has only one recipient)");
      }*/

      ListMultimap<String, String> multimap = ArrayListMultimap.create();
      MimeMessageUtil.collectPartsRecursively(message, multimap);
      // Check if this message is HTML and contains the string 'subscribe'.
      for (String html : multimap.get(MimeMessageUtil.TEXT_HTML_TYPE)) {
        if (html != null && MimeMessageUtil.isHtml(html) &&
            StringUtils.containsIgnoreCase(html, BODY_SUBSCRIBE_WORD)) {
          log.trace("Message filtered: Message is HTML and contains '{}'",
                    BODY_SUBSCRIBE_WORD);
          return false;
        }
      }
      // Check if this message contains a calendar event.
      if (!multimap.get(MimeMessageUtil.TEXT_CALENDAR_TYPE).isEmpty()) {
        log.trace("Message filtered: Message contains a calendar event.");
        return false;
      }
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    } catch (MessagingException e) {
      log.error(e);
    }

    // Message is useful if it passes all the above tests.
    return true;
  }

  /*public MessageFilterer addExternalMembers(List<Set<InternetAddress>> clusters) {
    if (clusters == null) return this;
    for (Set<InternetAddress> cluster : clusters) {
      for (InternetAddress address : cluster) {
        if (StringUtils.isBlank(address.getAddress())) continue;
        this.externalMembers.add(address);
      }
    }
    return this;
  }*/

  public void setRemoveMailListMessages(boolean removeMessagesViaMailList) {
    this.removeMailListMessages = removeMessagesViaMailList;
  }

  public void setRemovePrivateMessages(boolean removePrivateMessages) {
    this.removePrivateMessages = removePrivateMessages;
  }
}
