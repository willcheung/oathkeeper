package com.contextsmith.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.api.client.util.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MimeMessageUtil {

  public enum AddressField { FROM, REPLY_TO, TO, CC, BCC, ANY_RECIPIENT }

  static final Logger log = LogManager.getLogger(MimeMessageUtil.class);

  public static final String NO_REPLY_ADDR_RE = "(?i).*?reply.*?@.+";
  public static final String GIBBERISH_ADDR_RE = "(?i).*?([a-z]+[0-9]+){3,}.*?@.+";
  public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";
  public static final String BODY_SUBSCRIBE_WORD = "subscribe";
  public static final String MIME_MESSAGE_ID_HEADER = "Message-ID";

  public static String convertHtmlToPlainText(String html) {
    Document doc = Jsoup.parse(html);
    StringBuilder builder = new StringBuilder();
    for (Element e : doc.select("p")) {
      builder.append(e.text()).append(System.lineSeparator());
    }
    return builder.toString();

    /* if(html==null)
        return html;
    Document document = Jsoup.parse(html);
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
    document.select("br").append("\\n");
    document.select("p").prepend("\\n\\n");
    String s = document.html().replaceAll("\\\\n", "\n");
    return Jsoup.clean(s, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
    */
    /*
     // get pretty printed html with preserved br and p tags
    String prettyPrintedBodyFragment = Jsoup.clean(bodyHtml, "", Whitelist.none().addTags("br", "p"), new OutputSettings().prettyPrint(true));
    // get plain text with preserved line breaks by disabled prettyPrint
    return Jsoup.clean(prettyPrintedBodyFragment, "", Whitelist.none(), new OutputSettings().prettyPrint(false));
     */
  }

  public static String extractHtmlText(MimeMessage message)
      throws IOException, MessagingException {
    Object contentObject = message.getContent();

    if (contentObject instanceof Multipart) {
      Multipart content = (Multipart) contentObject;
      for (int i = 0; i < content.getCount(); i++) {
        BodyPart part = content.getBodyPart(i);
        if (part.isMimeType("text/html")) {
          return (String) part.getContent();
        }
      }
    } else if (contentObject instanceof String) {  // A simple text message
      String text = (String) contentObject;
      // Check if this text contains HTML tags.
      if (Pattern.compile("</\\w+>").matcher(text).find()) {
        return text;
      }
    }
    return null;
  }

  public static String extractPlainText(MimeMessage message)
      throws IOException, MessagingException {
    String plainText = null;
    Object contentObject = message.getContent();

    if (contentObject instanceof Multipart) {
      Multipart content = (Multipart) contentObject;
      BodyPart plainTextPart = null;
      BodyPart htmlTextPart = null;

      for (int i = 0; i < content.getCount(); i++) {
        BodyPart part = content.getBodyPart(i);
        if (part.isMimeType("text/plain")) {
          plainTextPart = part;
          break;  // Found plain text, exit.
        } else if (part.isMimeType("text/html")) {
          htmlTextPart = part;
        }
      }
      if (plainTextPart != null) {
        plainText = (String) plainTextPart.getContent();
      } else if (htmlTextPart != null) {
        String html = (String) htmlTextPart.getContent();
        plainText = convertHtmlToPlainText(html);
      }
    } else if (contentObject instanceof String) {  // A simple text message
      String text = (String) contentObject;
      // Check if this text contains HTML tags.
      if (Pattern.compile("</\\w+>").matcher(text).find()) {
        plainText = convertHtmlToPlainText(text);
      } else {
        plainText = text;
      }
    }
    return plainText;
  }

  public static void filterByDateRange(List<MimeMessage> messages,
                                       Date startSentDate, Date endSentDate) {
    for (Iterator<MimeMessage> iter = messages.iterator(); iter.hasNext();) {
      MimeMessage message = iter.next();
      Date sentDate;
      try { sentDate = message.getSentDate(); }
      catch (MessagingException e) { continue; }
      if (sentDate.before(startSentDate) || sentDate.after(endSentDate)) {
        iter.remove();
      }
    }
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
      log.error(e);
    }
    if (addresses != null) {
      for (Address address : addresses) {
        InternetAddress addr = (InternetAddress) address;
        if (InternetAddressUtil.isValidAddress(addr)) {
          // Converts encoding and ensures personal field is populated.
          String personal = addr.getPersonal();
          if (personal != null) {
            try { addr.setPersonal(personal, StandardCharsets.UTF_8.name()); }
            catch (UnsupportedEncodingException e) {}
          }
          results.add(addr);
        }
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

  public static boolean isUsefulMessage(MimeMessage message) {
    // Check header.
    try {
      Date date = message.getSentDate();
      if (date == null) {
        log.trace("Message filtered: Missing 'Date' field.");
        return false;
      }
      String[] unsubscribeList = message.getHeader(LIST_UNSUBSCRIBE_HEADER);
      if (unsubscribeList != null && unsubscribeList.length > 0) {
        log.trace("Message filtered: Contains header '{}'",
                  LIST_UNSUBSCRIBE_HEADER);
        return false;
      }
      String messageId = message.getMessageID();
      if (Strings.isNullOrEmpty(messageId)) {
        log.trace("Message filtered: Message ID is null or empty.");
        return false;
      }
      String htmlText = extractHtmlText(message);
      if (htmlText != null &&
          StringUtils.containsIgnoreCase(htmlText, BODY_SUBSCRIBE_WORD)) {
        log.trace("Message filtered: Message is HTML and contains '{}'",
                  BODY_SUBSCRIBE_WORD);
        return false;
      }
      // Check if this is a calendar event.
      Object contentObject = message.getContent();
      if (contentObject instanceof Multipart) {
        Multipart content = (Multipart) contentObject;
        for (int i = 0; i < content.getCount(); i++) {
          BodyPart part = content.getBodyPart(i);
          if (part.isMimeType("text/calendar")) {
            log.trace("Message filtered: Message is a calendar event.");
            return false;
          }
        }
      }
    } catch (IOException | MessagingException e) {
      log.error(e);
    }

    // Check sender.
    Set<InternetAddress> senders = getValidSenders(message);
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

    // Check reply-to.
    Set<InternetAddress> replyToAddrs = getValidReplyTo(message);
    if (!replyToAddrs.isEmpty()) {
      if (replyToAddrs.size() > 1) {
        log.trace("Message filtered: Has more than one reply-to addresses.");
        return false;
      }
      String replyToAddr = replyToAddrs.iterator().next().getAddress();
      if (replyToAddr.matches(NO_REPLY_ADDR_RE)) {
        log.trace("Message filtered: Reply-to has no-reply address '{}'", replyToAddr);
        return false;
      }
      if (replyToAddr.matches(GIBBERISH_ADDR_RE)) {
        log.trace("Message filtered: Reply-to has gibberish address '{}'", replyToAddr);
        return false;
      }
    }

    // Check recipients.
    Set<InternetAddress> recipients = getValidRecipients(message);
    if (recipients.isEmpty()) {
      log.trace("Message filtered: No valid recipients.");
      return false;
    }
    if (senders.equals(recipients)) {
      log.trace("Message filtered: Sender is recipient.");
      return false;
    }
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
}