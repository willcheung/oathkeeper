package com.contextsmith.email.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.collect.Multimap;

public class EmailFilterer {
  private static final Logger log = LoggerFactory.getLogger(EmailFilterer.class);

  public static final boolean DEFAULT_REMOVE_MAILLIST_MSGS = false;
  public static final boolean DEFAULT_REMOVE_PRIVATE_MSGS = false;

  // Email address regex pattern.
  public static final String NO_REPLY_ADDR_RE = "(?i).*?reply.*?@.+";
  public static final String GIBBERISH_ADDR_RE = "(?i).*?([a-z]+[0-9]+){3,}.*?@.+";

  // Mailing-List indicator in email body.
  public static final String BODY_SUBSCRIBE_WORD = "subscribe";

  private boolean removeMailListMessages;
  private boolean removePrivateMessages;
  private Pattern subjectRetainPattern;

  public EmailFilterer() {
    this.removeMailListMessages = DEFAULT_REMOVE_MAILLIST_MSGS;
    this.removePrivateMessages = DEFAULT_REMOVE_PRIVATE_MSGS;
    this.subjectRetainPattern = null;
  }

  public Collection<MimeMessage> filter(Collection<MimeMessage> messages) {
    List<MimeMessage> toKeep = new ArrayList<>();
    for (MimeMessage message : messages) {
      if (isUseful(message)) toKeep.add(message);
    }
    int numBeforeFilter = messages.size();
    int numAfterFilter = toKeep.size();
    int numFiltered = numBeforeFilter - numAfterFilter;
    log.debug(String.format(
        "Filtered %d%% (%d out of %d) invalid emails, %d left.",
        Math.round(100.0 * numFiltered / numBeforeFilter),
        numFiltered, numBeforeFilter, numAfterFilter));
    return toKeep;
  }

  public boolean isRemoveMailListMessages() {
    return this.removeMailListMessages;
  }

  public boolean isRemovePrivateMessages() {
    return this.removePrivateMessages;
  }

  public boolean isUseful(MimeMessage message) {
    // Check subject. Every message should have a subject field.
    String subject = MimeMessageUtil.getSubject(message);
    if (subject == null) return false;

    if (this.subjectRetainPattern != null) {
      String cleanedSubject = MimeMessageUtil.normalizeSubject(subject);
      if (!this.subjectRetainPattern.matcher(cleanedSubject).matches()) {
        log.trace("Message subject does not match: {}", cleanedSubject);
        return false;
      }
    }

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
    } catch (MessagingException e) {
      log.error(e.toString());
    }

    if (MimeMessageUtil.hasMailingListInRecipients(message)) {
      if (this.removeMailListMessages) {
        log.trace("Message filtered: Contains header '{}'",
                  MimeMessageUtil.LIST_UNSUBSCRIBE_HEADER);
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

    Multimap<String, String> multimap =
        MimeMessageUtil.collectPartsRecursively(message);

    // Check if this message is HTML and contains the string 'subscribe'.
    for (String html : multimap.get(MediaType.TEXT_HTML)) {
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

    // Message is useful if it passes all the above tests.
    return true;
  }

  public EmailFilterer setRemoveMailListMessages(
      boolean removeMessagesViaMailList) {
    this.removeMailListMessages = removeMessagesViaMailList;
    return this;
  }

  public EmailFilterer setRemovePrivateMessages(boolean removePrivateMessages) {
    this.removePrivateMessages = removePrivateMessages;
    return this;
  }

  public EmailFilterer setSubjectRetainPattern(Pattern pattern) {
    this.subjectRetainPattern = pattern;
    return this;
  }
}
