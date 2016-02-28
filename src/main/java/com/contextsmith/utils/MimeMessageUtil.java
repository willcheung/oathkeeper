package com.contextsmith.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.mail.util.DecodingException;

public class MimeMessageUtil {

  public enum AddressField { FROM, REPLY_TO, TO, CC, BCC, ANY_RECIPIENT }

  static final Logger log = LogManager.getLogger(MimeMessageUtil.class);

  public static final String MIME_MESSAGE_ID_HEADER = "Message-ID";
  public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";
  public static final String REFERENCES_HEADER = "References";
  public static final String SOURCE_INBOX_HEADER = "Source-Inbox";
  public static final String DELIVERED_TO_HEADER = "Delivered-To";
  public static final String X_RECEIVED_HEADER = "X-Received";
  public static final String RETURN_PATH_HEADER = "Return-Path";
  public static final String IN_REPLY_TO_HEADER = "In-Reply-To";
  public static final String GMAIL_THREAD_ID_HEADER = "Gmail-Thread-Id";
  public static final String GMAIL_MESSAGE_ID_HEADER = "Gmail-Message-Id";

  // Mime types.
  public static final String TEXT_CALENDAR_TYPE = "text/calendar";

  public static void collectPartsRecursively(
      Part message, Multimap<String, String> multimap)
      throws IOException, MessagingException {
    Object contentObject = null;
    try { contentObject = message.getContent(); }
    catch (DecodingException e) {}

    if (contentObject instanceof Multipart) {
      log.trace("MimeMessage contains: multipart");
      Multipart parts = (Multipart) contentObject;
      for (int i = 0; i < parts.getCount(); i++) {
        // Recursive calls.
        collectPartsRecursively(parts.getBodyPart(i), multimap);
      }
    } else if (message.isMimeType(MediaType.TEXT_PLAIN)) {
      log.trace("MimeMessage contains: " + MediaType.TEXT_PLAIN);
      String s = contentToString(contentObject);
      if (StringUtils.isNotBlank(s)) multimap.put(MediaType.TEXT_PLAIN, s.trim());

    } else if (message.isMimeType(MediaType.TEXT_HTML)) {
      log.trace("MimeMessage contains: " + MediaType.TEXT_HTML);
      String s = contentToString(contentObject);
      if (StringUtils.isNotBlank(s)) multimap.put(MediaType.TEXT_HTML, s.trim());

    } else if (message.isMimeType(TEXT_CALENDAR_TYPE)) {
      log.trace("MimeMessage contains: " + TEXT_CALENDAR_TYPE);
      String s = contentToString(contentObject);
      if (StringUtils.isNotBlank(s)) multimap.put(TEXT_CALENDAR_TYPE, s.trim());

    } /*else if (contentObject instanceof String) {  // A simple text message
      String text = (String) contentObject;
      log.trace("string: {}", text);
      // Check if this text contains HTML tags.
      if (isHtml(text)) result.put(TEXT_HTML_TYPE, text);
      else result.put(TEXT_PLAIN_TYPE, text);
    }*/
  }

  public static String contentToString(Object obj) {
    if (obj instanceof InputStream) {
      return FileUtil.readStreamToString((InputStream) obj);
    } else if (obj instanceof String) {
      return (String) obj;
    }
    return null;
  }

  public static String convertHtmlToPlainText(String html) {
    /*Document doc = Jsoup.parse(html);
    StringBuilder builder = new StringBuilder();
    for (Element e : doc.select("p")) {
      builder.append(e.text()).append(System.lineSeparator());
    }
    return builder.toString();*/

    /* if(html==null)
        return html;
    Document document = Jsoup.parse(html);
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
    document.select("br").append("\\n");
    document.select("p").prepend("\\n\\n");
    String s = document.html().replaceAll("\\\\n", "\n");
    return Jsoup.clean(s, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
    */

     // Get pretty printed HTML with preserved br and p tags.
    String s = Jsoup.clean(html, "", Whitelist.none().addTags("br", "p"),
                           new Document.OutputSettings().prettyPrint(true));
    // Get plain text with preserved line breaks by disabled prettyPrint
    return Jsoup.clean(s, "", Whitelist.none(),
                       new Document.OutputSettings().prettyPrint(false));
  }

  public static boolean existHeader(MimeMessage message, String header) {
    return getFirstHeader(message, header) != null;
  }

  public static String extractPlainText(MimeMessage message)
      throws IOException, MessagingException {
    ListMultimap<String, String> multimap = ArrayListMultimap.create();
    collectPartsRecursively(message, multimap);

    StringBuilder builder = new StringBuilder();
    for (String plain : multimap.get(MediaType.TEXT_PLAIN)) {
      if (StringUtils.isNotBlank(plain)) {
        log.trace("Found plain text: {}", plain);
        builder.append(plain).append(System.lineSeparator());
      }
    }
    if (builder.length() > 0) return builder.toString();

    // We only look for HTML parts when there is no plain-text parts.
    for (String html : multimap.get(MediaType.TEXT_HTML)) {
      String plain = convertHtmlToPlainText(html);
      if (StringUtils.isNotBlank(plain)) {
        log.trace("Converted from HTML: {}", plain);
        builder.append(plain).append(System.lineSeparator());
      }
    }
    return builder.toString();
  }

  /*public static String extractHtmlText(MimeMessage message)
      throws IOException, MessagingException {
    ListMultimap<String, String> multimap = ArrayListMultimap.create();
    collectPartsRecursively(message, multimap);

    StringBuilder builder = new StringBuilder();
    List<String> htmls = multimap.get(TEXT_HTML_TYPE);
    if (htmls != null) {
      for (String html : htmls) {
        if (!isHtml(html)) continue;
        builder.append(html).append(System.lineSeparator());
      }
    }

    Object contentObject = message.getContent();

    if (contentObject instanceof Multipart) {
      Multipart content = (Multipart) contentObject;
      for (int i = 0; i < content.getCount(); i++) {
        BodyPart part = content.getBodyPart(i);
        if (!part.isMimeType("text/html")) continue;

        String text = null;
        try { text = (String) part.getContent(); }
        catch (DecodingException e) {}
        if (isHtml(text)) return text;
      }
    } else if (contentObject instanceof String) {  // A simple text message
      String text = (String) contentObject;
      if (isHtml(text)) return text;
    }
    return builder.toString();
  }*/

  /*public static String extractPlainText(MimeMessage message)
      throws IOException, MessagingException {
    String plainText = null;
    Object contentObject = message.getContent();

    if (contentObject instanceof Multipart) {
      log.debug("multipart");
      Multipart parts = (Multipart) contentObject;
      BodyPart plainTextPart = null;
      BodyPart htmlTextPart = null;

      for (int i = 0; i < parts.getCount(); i++) {
        BodyPart part = parts.getBodyPart(i);
        log.debug("bodypart: {}", part.getContent());
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
      log.debug("string: {}", text);
      // Check if this text contains HTML tags.
      if (isHtml(text)) {
        plainText = convertHtmlToPlainText(text);
      } else {
        plainText = text;
      }
    }
    return plainText;
  }*/

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

  public static InternetAddress getDeliveredTo(MimeMessage message) {
    String address = getFirstHeader(message, DELIVERED_TO_HEADER);
    if (StringUtils.isNotBlank(address)) {
      return InternetAddressUtil.newIAddress(
          InternetAddressUtil.normalizeAddress(address));
    }
    return null;
  }

  public static String getFirstHeader(MimeMessage message, String field) {
    String value = null;
    try { value = message.getHeader(field, null); }
    catch (MessagingException e) {}
    return value;
  }

  public static String getGmailMessageId(MimeMessage message) {
    return getFirstHeader(message, GMAIL_MESSAGE_ID_HEADER);
  }

  public static String[] getListUnsubscribe(MimeMessage message) {
    String value = getFirstHeader(message, LIST_UNSUBSCRIBE_HEADER);
    return (value == null) ? null : value.split("\\s+");
  }

  /*public static String getGmailMessageId(MimeMessage message) {
    return getFirstHeader(message, MimeMessageUtil.GMAIL_MESSAGE_ID_HEADER);
  }

  public static String getGmailThreadId(MimeMessage message) {
    return getFirstHeader(message, MimeMessageUtil.GMAIL_THREAD_ID_HEADER);
  }*/

  public static String getMessageId(MimeMessage message) {
    return getFirstHeader(message, MIME_MESSAGE_ID_HEADER);
  }

  public static String[] getReferences(MimeMessage message) {
    String value = getFirstHeader(message, REFERENCES_HEADER);
    return (value == null) ? null : value.split("\\s+");
  }

  public static String[] getSourceInboxes(MimeMessage message) {
    try {
      return message.getHeader(SOURCE_INBOX_HEADER);
    } catch (MessagingException e) {
      log.error(e);
    }
    return null;
  }

  public static Set<InternetAddress> getValidAddresses(MimeMessage message,
                                                       AddressField type) {
    Address[] addresses = null;
    try {
      switch(type) {
      case FROM: addresses = message.getFrom(); break;
      case REPLY_TO: addresses = message.getReplyTo(); break;
      case TO: addresses = message.getRecipients(RecipientType.TO); break;
      case CC: addresses = message.getRecipients(RecipientType.CC); break;
      case BCC: addresses = message.getRecipients(RecipientType.BCC); break;
      case ANY_RECIPIENT: addresses = message.getAllRecipients(); break;
      default: log.error("Unknown address field: {}", type);
      }
    } catch (AddressException e) {
      // ignore
    } catch (MessagingException e) {
      log.error(e);
    }

    Set<InternetAddress> results = new HashSet<>();
    if (addresses != null) {
      for (Address address : addresses) {
        InternetAddress addr = (InternetAddress) address;
        if (!InternetAddressUtil.isValidAddress(addr)) continue;

        // Normalize email addresses.
        addr.setAddress(InternetAddressUtil.normalizeAddress(addr.getAddress()));

        // Converts encoding and ensures personal field is populated.
        String personal = addr.getPersonal();
        if (personal != null) {
          try { addr.setPersonal(personal, StandardCharsets.UTF_8.name()); }
          catch (UnsupportedEncodingException e) {}
        }
        results.add(addr);
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

  public static boolean hasMailingListInRecipients(MimeMessage message) {
    String[] unsubscribeList = getListUnsubscribe(message);
    return unsubscribeList != null && unsubscribeList.length > 0;
  }

  // Check if this text contains HTML tags.
  public static boolean isHtml(String text) {
    if (StringUtils.isBlank(text)) return false;
    return Pattern.compile("</\\w+>").matcher(text).find();
  }

  public static boolean isSentGmail(MimeMessage message) {
    if (existHeader(message, RETURN_PATH_HEADER)) return false;

    // Make sure Delivered-To address is same as that of sender (Gmail-specific)
    InternetAddress deliveredTo = getDeliveredTo(message);
    if (deliveredTo == null) return false;
    Set<InternetAddress> senders = getValidSenders(message);
    if (senders.isEmpty()) return false;
    InternetAddress sender = senders.iterator().next();
    return sender.equals(deliveredTo);
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