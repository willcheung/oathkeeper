package com.contextsmith.email.cluster;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.utils.EventUtil;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.services.calendar.model.Event;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import jersey.repackaged.com.google.common.collect.Sets;

public class EmailNameResolver {
  private static final Logger log = LoggerFactory.getLogger(EmailNameResolver.class);

  public static String normalizePersonalName(String name) {
    // Remove parenthesized substrings.
    name = name.replaceAll("\\([^()]+\\)", "").trim();
    // Remove double and single surrounding quotes.
    name = name.replaceAll("^[\"']+|[\"']+$", "").trim();
    name = name.replaceAll("\\s+", " ").trim();
    name = flip(name, ',');  // Flip if "<last name>, <first name>"
    return name;
  }

  /**
   * Flips the front and back parts of a name with one another.
   * Front and back are determined by a specified character somewhere in the
   * middle of the string.
   *
   * @param flipAroundChar the character(s) demarcating the two halves you want to flip.
   */
  private static String flip(String name, char flipAroundChar) {
    String[] parts = StringUtils.split(name, flipAroundChar);
    if (parts.length == 2) {
      return parts[1].trim() + " " + parts[0].trim();
    }
    return name;
  }

  private Map<String, NameDist> emailNameDistMap;

  public EmailNameResolver() {
    this.emailNameDistMap = new HashMap<String, NameDist>();
  }

  public String getCommonName(String address) {
    NameDist nameDist = this.emailNameDistMap.get(address);
    if (nameDist == null) return null;
    return nameDist.getCommonName();
  }

  public void loadEvents(Iterable<Event> events) {
    for (Event event : events) {
      Set<InternetAddress> recipients = Sets.union(
          EventUtil.getOrganizer(event),
          EventUtil.getAttendees(event));
      for (InternetAddress address : recipients) {
        this.putAddress(address);
      }
    }
  }

  public void loadMimeMessages(Iterable<MimeMessage> messages) {
    for (MimeMessage message : messages) {
      Set<InternetAddress> recipients = Sets.union(
          MimeMessageUtil.getValidRecipients(message),
          MimeMessageUtil.getValidSenders(message));
      for (InternetAddress address : recipients) {
        this.putAddress(address);
      }
    }
  }

  public void putAddress(InternetAddress address) {
    String email = address.getAddress();
    if (StringUtils.isBlank(email)) return;
    String personal = address.getPersonal();  // Personal name.
    if (StringUtils.isBlank(personal)) return;
    if (personal.contains("@")) return;  // Name cannot be email address.

    NameDist nameDist = this.emailNameDistMap.get(email);
    if (nameDist == null) {
      this.emailNameDistMap.put(email, nameDist = new NameDist());
    }
    nameDist.add(normalizePersonalName(personal));
  }

  public void reset() {
    this.emailNameDistMap.clear();
  }

  public InternetAddress resolve(InternetAddress address) {
    String email = address.getAddress();
    if (StringUtils.isBlank(email)) return address;
    String commonName = this.getCommonName(email);
    if (StringUtils.isBlank(commonName)) return address;
    try {
      address.setPersonal(commonName, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      log.error(e.toString());
      e.printStackTrace();
    }
    return address;
  }

  public Set<InternetAddress> resolve(Set<InternetAddress> addresses) {
    for (InternetAddress address : addresses) {
      this.resolve(address);
    }
    return addresses;
  }
}

class NameDist {
  private int commonNameCount;
  private String commonName;
  private Multiset<String> nameCounts;

  public NameDist() {
    this.commonNameCount = 0;
    this.commonName = null;
    this.nameCounts = HashMultiset.create();
  }

  public void add(String name) {
    int count = this.nameCounts.add(name, 1) + 1;
    if (count > this.commonNameCount) {
      this.commonName = name;
      this.commonNameCount = count;
    }
  }

  public String getCommonName() {
    return this.commonName;
  }

  public int getCommonNameCount() {
    return this.commonNameCount;
  }
}