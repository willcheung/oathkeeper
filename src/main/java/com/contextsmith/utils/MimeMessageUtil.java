package com.contextsmith.utils;

import com.contextsmith.api.data.Attachment;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.mail.util.DecodingException;
import jersey.repackaged.com.google.common.collect.Sets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.regex.Pattern;

public class MimeMessageUtil {

    public static final String SENT_DATE_HEADER = "Date";
    public static final String MIME_MESSAGE_ID_HEADER = "Message-ID";
    public static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";
    public static final String REFERENCES_HEADER = "References";
    public static final String SOURCE_INBOX_HEADER = "Source-Inbox"; // E-Mail address on which behalf a crawl was done
    public static final String DELIVERED_TO_HEADER = "Delivered-To";
    //  public static final String X_RECEIVED_HEADER = "X-Received";
    public static final String RETURN_PATH_HEADER = "Return-Path";
    //  public static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    public static final String GMAIL_THREAD_ID_HEADER = "Gmail-Thread-Id";
    public static final String GMAIL_MESSAGE_ID_HEADER = "Gmail-Message-Id";
    public static final String X_SOURCE = "X-Source"; // header used to denote the source system, i.e. one of the Provider. constants
    public static final String X_PRIVATE_ID= "X-Private-ID"; // header used to store source system specific ID for this message
    // Mime types.
    public static final String MIME_TYPE_HEADER_SUFFIX = "-content";
    public static final String TEXT_CALENDAR_TYPE = "text/calendar";
    public static final Set<String> RELEVANT_MIME_TYPES = Sets.newHashSet(
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_HTML,
            TEXT_CALENDAR_TYPE
    );
    // Used to parse dates
    public static final DateTimeFormatter MAIL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US);
    // Reference: https://en.wikipedia.org/wiki/List_of_email_subject_abbreviations
    public static final Pattern REPLY_FORWARD_PREFIX_PAT =
            Pattern.compile("(\\b(?i:re|r|fwd|fw|f):|\\[.*?\\])");

    private static final Logger log = LoggerFactory.getLogger(MimeMessageUtil.class);
    public static final String X_ATTACHMENT_ID = "X-Attachment-Id";
//      DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);

    public enum AddressField {FROM, REPLY_TO, TO, CC, BCC, ANY_RECIPIENT}

    public static List<Attachment> collectAttachments(MimeMessage msg, PartFilter... filters) throws IOException, MessagingException {
        List<Attachment> initial = new ArrayList<>();
        collectAttachments(msg, msg, initial, "", filters);
        return initial;
    }

    /**
     * Recursively collect mime parts that look like attachments and store them into collectionOfAttachments.
     * `path` is used to record navigation instructions through the Mime message structure to retrieve attachments
     * without an internal ID (otherwise the part-specific X-Attachment-Id header is being used)
     * @param msg
     * @param p
     * @param collectionOfAttachments
     * @param path
     * @param filters
     * @throws IOException
     * @throws MessagingException
     */
    static void collectAttachments(MimeMessage msg, Part p, List<Attachment> collectionOfAttachments, String path, PartFilter... filters) throws IOException, MessagingException {
        if (p.isMimeType("text/plain")) {
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                collectAttachments(msg, mp.getBodyPart(i), collectionOfAttachments, path + "," + i, filters);
            }
        } else if (p.isMimeType("message/rfc822")) {
            collectAttachments(msg, (Part) p.getContent(), collectionOfAttachments, path + ",c", filters);
        } else { // this might be an attachment
            String fileName = p.getFileName();
            String disp = p.getDisposition();
            if (fileName != null && Arrays.stream(filters).allMatch(pred -> {
                try {
                    return pred.test(p);
                } catch (MessagingException e) {
                    log.error("PartFilter error", e);
                    return false;
                }
            })) {
                log.debug("Attachment with " + disp + " and filename " + fileName);
                String provider = getFirstHeader(msg, X_SOURCE, "unknown");
                String email = getFirstHeader(msg, SOURCE_INBOX_HEADER, getFirstHeader(msg, "Delivered-To"));
                String messageID = getFirstHeader(msg, X_PRIVATE_ID, getMessageId(msg));
                String attachmentID = getFirstHeader(p, X_ATTACHMENT_ID, path); // series of child indices into MimeMessage tree
                String urn = toURN(provider, email, messageID, attachmentID);

                String sha = null;
                Object o = p.getContent();
                if (o instanceof String) {
                    log.debug("String content");
                    String stringContent = o.toString();
                    if (!stringContent.isEmpty()) {
                        sha = DigestUtils.sha256Hex(o.toString());
                    }
                } else if (o instanceof InputStream) {
                    log.debug("input stream content");
                    InputStream is = (InputStream) o;
                    sha = DigestUtils.sha256Hex(is);
                } else {
                    log.debug("Unknown type");
                }
                String fullMime = p.getContentType();
                String baseType = fullMime;
                try {
                    baseType = new MimeType(fullMime).getBaseType();
                } catch (MimeTypeParseException e) {
                    log.error("Problem parsing mime", e);
                }
                collectionOfAttachments.add(new Attachment(urn, fileName, sha, baseType));
            } else {
                log.debug("No filename");
            }
        }

    }

    public static Multimap<String, String> collectPartsRecursively(
            MimeMessage message) {
        Multimap<String, String> multimap = ArrayListMultimap.create();
    /*try {
      collectPartsRecursively(message, multimap);
    } catch (IOException | MessagingException e) {
      e.printStackTrace();
    }*/

        // Retrieve content from message header first.
        for (String mimeType : RELEVANT_MIME_TYPES) {
            String header = mimeType + MIME_TYPE_HEADER_SUFFIX;
            String[] contents = null;
            try {
                contents = message.getHeader(header);
            } catch (MessagingException e) {
                continue;
            }

            if (contents != null && contents.length > 0) {
                for (String content : contents) {
                    multimap.put(mimeType, content);
                }
            }
        }
        if (!multimap.isEmpty()) return multimap;

        // At this point, no content was found in message header;
        // so we parse from the message parts.
        try {
            collectPartsRecursively(message, multimap);
        } catch (IOException | MessagingException e) {
        }

        // If found content from message parts, store them into the header.
        for (String key : multimap.keySet()) {
            for (String value : multimap.get(key)) {
                try {
                    message.addHeader(key + MIME_TYPE_HEADER_SUFFIX, value);
                } catch (MessagingException e) {
                }
            }
        }

        return multimap;
    }

  /*public static void collectPartsRecursively(
      Part message,
      Multimap<String, String> multimap)
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
    }
  }*/

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
        Multimap<String, String> multimap = collectPartsRecursively(message);
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

    public static void filterByDateRange(List<MimeMessage> messages,
                                         Date startSentDate, Date endSentDate) {
        for (Iterator<MimeMessage> iter = messages.iterator(); iter.hasNext(); ) {
            MimeMessage message = iter.next();
            Date sentDate;
            try {
                sentDate = message.getSentDate();
            } catch (MessagingException e) {
                continue;
            }
            if (sentDate.before(startSentDate) || sentDate.after(endSentDate)) {
                iter.remove();
            }
        }
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
        try {
            value = message.getHeader(field, null);
        } catch (MessagingException e) {
        }
        return value;
    }

    public static String getFirstHeader(Part part, String field, String defaultValue) {
        String value = defaultValue;
        try {
            String[] headers = part.getHeader(field);
            if (headers != null && headers.length > 0) {
                value = headers[0];
            }
        } catch (MessagingException e) {
            log.error("Unable to get header " + field, e);
        }
        return value;
    }

    public static String getGmailMessageId(MimeMessage message) {
        return getFirstHeader(message, GMAIL_MESSAGE_ID_HEADER);
    }

    public static String getGmailThreadId(MimeMessage message) {
        return getFirstHeader(message, GMAIL_THREAD_ID_HEADER);
    }

    public static String[] getListUnsubscribe(MimeMessage message) {
        String value = getFirstHeader(message, LIST_UNSUBSCRIBE_HEADER);
        return (value == null) ? null : value.split("\\s+");
    }

    public static String getMessageId(MimeMessage message) {
        try {
            return message.getMessageID();
        } catch (MessagingException e) {
            return null;
        }
    }

    public static String[] getReferences(MimeMessage message) {
        String value = getFirstHeader(message, REFERENCES_HEADER);
        return (value == null) ? null : value.split("\\s+");
    }

    public static ZonedDateTime getSentDate(MimeMessage message)
            throws MessagingException {
        String mailDateStr = getFirstHeader(message, SENT_DATE_HEADER);
        if (mailDateStr == null) return null;

        // Remove beginning day-of-week strings (Mon, Tue, Wed, Thu, Fri, Sat, Sun)
        mailDateStr = mailDateStr.replaceAll("^[MTWFS][ouehra][neduit], ", "").trim();

        // Remove trailing zone name in parenthesis.
        mailDateStr = mailDateStr.replaceAll("\\([^()]+\\)$", "").trim();

        TemporalAccessor ta = null;
        try {
            ta = MAIL_DATE_FORMATTER.parse(mailDateStr);
        } catch (DateTimeParseException e) {
            log.error("Error parsing date time: {}", mailDateStr);
            return null;
        }
        return ZonedDateTime.from(ta);
    }

  /*public static String getGmailMessageId(MimeMessage message) {
    return getFirstHeader(message, MimeMessageUtil.GMAIL_MESSAGE_ID_HEADER);
  }

  public static String getGmailThreadId(MimeMessage message) {
    return getFirstHeader(message, MimeMessageUtil.GMAIL_THREAD_ID_HEADER);
  }*/

    public static String[] getSourceInboxes(MimeMessage message) {
        try {
            return message.getHeader(SOURCE_INBOX_HEADER);
        } catch (MessagingException e) {
            log.error(e.toString());
        }
        return null;
    }

    public static String getSubject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            return null;
        }
    }

    public static Set<InternetAddress> getValidAddresses(MimeMessage message,
                                                         AddressField type) {
        Address[] addresses = null;
        try {
            switch (type) {
                case FROM:
                    addresses = message.getFrom();
                    break;
                case REPLY_TO:
                    addresses = message.getReplyTo();
                    break;
                case TO:
                    addresses = message.getRecipients(RecipientType.TO);
                    break;
                case CC:
                    addresses = message.getRecipients(RecipientType.CC);
                    break;
                case BCC:
                    addresses = message.getRecipients(RecipientType.BCC);
                    break;
                case ANY_RECIPIENT:
                    addresses = message.getAllRecipients();
                    break;
                default:
                    log.error("Unknown address field: {}", type);
            }
        } catch (AddressException e) {
            // ignore
        } catch (MessagingException e) {
            log.error(e.toString());
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
                    try {
                        addr.setPersonal(personal, StandardCharsets.UTF_8.name());
                    } catch (UnsupportedEncodingException e) {
                    }
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

    // Check if this email is a sent email, instead of a received email.
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

    public static String normalizeSubject(String subject) {
        return REPLY_FORWARD_PREFIX_PAT
                .matcher(subject)
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private static void collectPartsRecursively(
            Part message,
            Multimap<String, String> multimap)
            throws IOException, MessagingException {
        Object contentObject = null;
        try {
            contentObject = message.getContent();
        } catch (DecodingException e) {
        }

        if (contentObject instanceof Multipart) {
            log.trace("MimeMessage contains: multipart");
            Multipart parts = (Multipart) contentObject;
            for (int i = 0; i < parts.getCount(); i++) {
                // Recursive calls.
                collectPartsRecursively(parts.getBodyPart(i), multimap);
            }
            return;
        }
        for (String mimeType : RELEVANT_MIME_TYPES) {
            if (message.isMimeType(mimeType)) {
                log.trace("MimeMessage contains: " + mimeType);
                String s = contentToString(contentObject);
                if (StringUtils.isNotBlank(s)) {
                    multimap.put(mimeType, s.trim());
                }
                break;  // We assume a message can only belong to one mime-type.
            }
        }
    }

    public static String toURN(String provider, String email, String internalID, String fragment) {
        return "urn:email:" + encode(provider) + ":" + encode(email) + ":" + encode(internalID)
                + (fragment != null ? "#" + encode(fragment) : "");
    }

    private static String encode(String toEncode) {
        try {
            URLEncoder.encode(toEncode, "UTF-8");
        } catch (UnsupportedEncodingException e) { // can be safely ignored as UTF-8 is supported encoding
        }
        return toEncode;
    }

    public interface PartFilter {
        boolean test(Part t) throws MessagingException;
    }
}