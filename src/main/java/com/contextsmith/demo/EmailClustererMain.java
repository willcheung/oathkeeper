package com.contextsmith.demo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class EmailClustererMain {
  
  private static final Logger log = LogManager.getLogger(EmailClustererMain.class);
  
  public static final boolean USE_ENRON_DATA = false;
  public static final String ENRON_USER = "kean-s";  // "kean-s, smith-m"
  public static final int ENRON_MAX_MESSAGES = 1000;  // -1 = unlimited
  
  public static final String GMAIL_USER = "me";
  public static final String GMAIL_QUERY = "before:2014/8/31";
  public static final int GMAIL_MAX_MESSAGES = 300;
  
  public static void main(String[] args) {
    Stopwatch stopwatch = new Stopwatch();
    stopwatch.start();
    
    List<MimeMessage> messages = fetchEmails();
    
    // Predict user's e-mail addresses.
    Map<InternetAddress, Double> addressScoreMap = 
        UserAddressPredictor.predict(messages);
    
    // We use the user's e-mail address to obtain company's email domain;
    // so it must *not* be empty here.
    if (addressScoreMap.isEmpty()) return;
    
    // Extract company mail address domain.
    String internalDomain = 
        EmailClustererUtil.findInternalDomain(addressScoreMap.keySet());
    
    Set<Set<InternetAddress>> clusters = null;
    if (internalDomain == null) {  // A common domain (eg. gmail).
      clusters = EmailClusterer.findClustersIgnoringDomain(
          messages, addressScoreMap.keySet());
    } else {
      clusters = EmailClusterer.findExternalClusters(
          messages, addressScoreMap.keySet(), internalDomain);
    }
    if (clusters == null) return;
    EmailClustererUtil.printClusters(clusters);
    
    D3Object d3Object = EmailClustererUtil.makeD3Object(
        messages, clusters, addressScoreMap.keySet(), internalDomain);
    
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    log.info(gson.toJson(d3Object));
    log.info("Total elapsed time: " + stopwatch);
  }
  
  public static List<MimeMessage> fetchEmails() {
    log.info("Fetching e-mails...");
    Stopwatch stopwatch = new Stopwatch();
    stopwatch.start();
    
    List<MimeMessage> messages = null;
    if (USE_ENRON_DATA) {
      EmailProvider provider = new LocalFileProvider();
      messages = provider.provide(ENRON_USER, null, ENRON_MAX_MESSAGES);
    } else {  // Read from Gmail
      EmailProvider provider = new GmailServiceProvider();
      messages = provider.provide(GMAIL_USER, GMAIL_QUERY, GMAIL_MAX_MESSAGES);
    }
    
    log.info("Fetching e-mails took " + stopwatch + "\n");
    return messages;
  }
}
