package com.contextsmith.api.data;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.whois.WhoisClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

public class WhoisLookup {
  static final Logger log = LogManager.getLogger(WhoisLookup.class);

  public static final String WHOIS_SERVER_FIELD = "Whois Server";
  public static final String WHOIS_REG_ORG_FIELD = "Registrant Organization";
  public static final String WHOIS_REG_NAME_FIELD = "Registrant Name";
  public static final String WHOIS_DOMAIN_NAME_FIELD = "Domain Name";

  public static final int SOCKET_TIMEOUT_IN_MILLIS = 5 * 1000;  // 5 seconds.
  public static final int CONNECTION_TIMEOUT_IN_MILLIS = 5 * 1000;  // 5 seconds.
  private static final String WHOIS_FIELD_PAT = "\\Q%s\\E: (.+)";

  public static Set<String> extractWhoisFieldValues(String whoisOutput,
                                                    String field) {
    checkNotNull(whoisOutput);
    checkNotNull(field);

    return extractSortedByFreq(
        whoisOutput, Pattern.compile(String.format(WHOIS_FIELD_PAT, field)));
  }

  public static String lookup(String domain, List<String> fields) {
    Set<String> servers = retrieveWhoisServers(domain);
    if (servers == null) return null;

    for (String server : servers) {
      String response = queryWhois(domain, server, false);
      if (response == null) continue;
      log.trace(response);

      Set<String> values = null;
      int fieldIndex = 0;
      for (; fieldIndex < fields.size(); ++fieldIndex) {
        values = extractWhoisFieldValues(response, fields.get(fieldIndex));
        if (values != null && !values.isEmpty()) break;
      }
      if (values == null || values.isEmpty()) continue;

      // Returns the highest count field value.
      String value = values.iterator().next();
      log.debug("{} of {} is \"{}\"", fields.get(fieldIndex), domain, value);
      return value;
    }
    return null;
  }

  public static String lookupRegistrantOrganization(String domain) {
    return lookup(domain, Arrays.asList(WHOIS_REG_ORG_FIELD));
  }

  public static String lookupRegistrantOrganizationOrName(String domain) {
    return lookup(domain,
                  Arrays.asList(WHOIS_REG_ORG_FIELD, WHOIS_REG_NAME_FIELD));
  }

  public static void main(String[] args) {
    log.info(lookupRegistrantOrganization("astellas.com"));
    log.info(lookupRegistrantOrganization("costco.com"));
    log.info(lookupRegistrantOrganization("ABCNEWS.COM"));
  }

  public static Set<String> retrieveWhoisFieldValues(String domain,
                                                     String whoisServer,
                                                     String field) {
    checkNotNull(domain);
    checkNotNull(whoisServer);
    checkNotNull(field);

    String response = queryWhois(domain, whoisServer, false);
    if (response == null) return null;
    log.trace(response);
    return extractWhoisFieldValues(response, field);
  }

  public static Set<String> retrieveWhoisServers(String domain) {
    checkNotNull(domain);

    String response = queryWhois(domain, WhoisClient.DEFAULT_HOST, true);
    if (response == null) return null;

    int offset = response.indexOf(WHOIS_DOMAIN_NAME_FIELD + ": ");
    if (offset != -1) response = response.substring(offset);
    return extractWhoisFieldValues(response, WHOIS_SERVER_FIELD);
  }

  private static Set<String> extractSortedByFreq(String text, Pattern pattern) {
    checkNotNull(text);
    checkNotNull(pattern);

    Multiset<String> set = HashMultiset.create();
    Matcher m = pattern.matcher(text);
    while (m.find()) set.add(m.group(1));
    return new LinkedHashSet<String>(Multisets.copyHighestCountFirst(set));
  }

  private static String queryWhois(String domain, String whoisServer,
                                   boolean exactMatch) {
    checkNotNull(domain);
    checkNotNull(whoisServer);

    WhoisClient whois = new WhoisClient();
    whois.setConnectTimeout(CONNECTION_TIMEOUT_IN_MILLIS);
    String response = null;
    try {
      log.trace("Connecting to Whois server: {}", whoisServer);
      whois.connect(whoisServer);
      whois.setSoTimeout(SOCKET_TIMEOUT_IN_MILLIS);
      String query = (exactMatch ? "=" : "") + domain;
      log.debug("Querying Whois server ({}) for query: {}", whoisServer, query);
      response = whois.query(query);
    } catch (IOException e) {
      log.error(e);
    } finally {
      log.trace("Disconnecting from Whois server: {}", whoisServer);
      try { whois.disconnect(); }
      catch (IOException e) {}
    }
    return response;
  }
}
