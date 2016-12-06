package com.contextsmith.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.util.Strings;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

public class InternetAddressUtil {
  public static final Set<String> COMMON_WEBMAIL_DOMAIN = Sets.newHashSet(
      "gmail.com",
      "yahoo.com",
      "live.com",
      "hotmail.com",
      "aol.com",
      "mail.com",
      "inbox.com",
      "outlook.com"
  );

  // Removes invalid addresses from the input collection of 'addressesToFilter'.
  public static void filterInvalidAddresses(
      Set<InternetAddress> addressesToFilter,
      Set<InternetAddress> addressesToIgnore,
      String domainToIgnore,
      boolean ignoreCommonWebmailDomain) {
    for (Iterator<InternetAddress> iter = addressesToFilter.iterator();
         iter.hasNext();) {
      InternetAddress address = iter.next();
      if (shouldIgnore(address, domainToIgnore, addressesToIgnore,
                       ignoreCommonWebmailDomain)) {
        iter.remove();
      }
    }
  }

  public static String findMostFrequentDomain(Set<InternetAddress> addresses) {
    checkNotNull(addresses);

    Multiset<String> domainFreqSet = HashMultiset.create();
    for (InternetAddress address : addresses) {
      String domain = InternetAddressUtil.getAddressDomain(address).toLowerCase();
      if (!StringUtils.isBlank(domain)) domainFreqSet.add(domain);
    }
    int maxCount = 0;
    String bestDomain = null;
    for (String domain : domainFreqSet) {
      int count = domainFreqSet.count(domain);
      if (count > maxCount) {
        maxCount = count;
        bestDomain = domain;
      }
    }
    return bestDomain;
  }

  public static String getAddressDomain(InternetAddress address) {
    return getAddressDomain(address.getAddress());
  }

  public static String getAddressDomain(String address) {
    return address.replaceFirst("^.+?@", "");
  }

  public static boolean hasDomain(InternetAddress address, String domain) {
    return getAddressDomain(address).equalsIgnoreCase(domain);
  }

  public static boolean isCommonDomain(InternetAddress address) {
    String userDomain = getAddressDomain(address).toLowerCase();
    return COMMON_WEBMAIL_DOMAIN.contains(userDomain);
  }

  // Check the validity of the e-mail address.
  public static boolean isValidAddress(InternetAddress address) {
    checkNotNull(address);
    return EmailValidator.getInstance().isValid(address.getAddress());
  }

  public static InternetAddress newIAddress(String address) {
    try {
      return new InternetAddress(address);
    } catch (AddressException e) {
      return null;
    }
  }

  public static InternetAddress newIAddress(String address, String personal) {
    try {
      if (Strings.isBlank(personal)) {
        return new InternetAddress(address);
      } else {
        return new InternetAddress(address, personal);
      }
    } catch (UnsupportedEncodingException | AddressException e) {
      return null;
    }
  }

  public static String normalizeAddress(String address) {
    address = address.replaceFirst("\\+.*?@", "@");
    return address.toLowerCase();
  }

  public static boolean shouldIgnore(InternetAddress address,
                                     String domainToIgnore,
                                     Set<InternetAddress> addressesToIgnore,
                                     boolean ignoreCommonWebmailDomain) {
    checkNotNull(address);
    if (addressesToIgnore != null && addressesToIgnore.contains(address)) {
      return true;
    }
    if (StringUtils.isNotBlank(domainToIgnore) &&
        getAddressDomain(address).toLowerCase().endsWith(domainToIgnore.toLowerCase())) {
      return true;
    }
    if (ignoreCommonWebmailDomain && isCommonDomain(address)) {
      return true;
    }
    return false;
  }

}
