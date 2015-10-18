package com.contextsmith.demo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UserAddressPredictor {
  
  private static final Logger log = LogManager.getLogger(UserAddressPredictor.class);
  
  public static final int MAX_ADDRESSES_TO_GUESS_FROM = 100;
  public static final int NUM_RECIPIENTS_PER_EMAIL = 1;
  public static final double MIN_EMAIL_PREDICT_THRESHOLD = 2;
  
  public static Map<InternetAddress, Double> predict(List<MimeMessage> inputs) {
    return predict(inputs, MIN_EMAIL_PREDICT_THRESHOLD);
  }
  
  public static Map<InternetAddress, Double> predict(List<MimeMessage> inputs, 
                                                     double minThreshold) {
    List<MimeMessage> messages = new ArrayList<>(inputs);
    Map<InternetAddress, Double> addressScoreMap = new LinkedHashMap<>();
    InternetAddress bestAddress = null;
    Integer maxCount = null;
    
    for (int i = 0; i < MAX_ADDRESSES_TO_GUESS_FROM; ++i) {
      // This method may modify 'messages' list.
      Pair<InternetAddress, Integer> bestAddressCount = 
          findMostFrequentAddress(messages, bestAddress);
      bestAddress = bestAddressCount.getKey();
      if (bestAddress == null) break;
      if (maxCount == null) maxCount = bestAddressCount.getValue();
//      double score = (double) bestAddressCount.getValue() / maxCount;
      double score = (double) bestAddressCount.getValue();
      if (score < minThreshold) break;
      addressScoreMap.put(bestAddress, score);
    }
    printPredictions(addressScoreMap);
    return addressScoreMap;
  }
  
  public static void printPredictions(
      Map<InternetAddress, Double> addressScoreMap) {
    checkNotNull(addressScoreMap);
    
    int rank = 1;
    log.debug("Result of predicting user's e-mail aliases:");
    for (Entry<InternetAddress, Double> entry : addressScoreMap.entrySet()) {
      log.debug(String.format(
          "%d. Confidence: %.2f, User email: %s", 
          rank++, entry.getValue(), entry.getKey().toUnicodeString()));
    }
  }
  
  // If an email's "from" or "to" field contains "addressToIgnore", 
  // then it will not contribute to a count.
  // Warning: Alters the input list of messages if 'addressToIgnore' is not null.
  private static Pair<InternetAddress, Integer> findMostFrequentAddress(
      List<MimeMessage> messages, 
      InternetAddress addressToIgnore) {
    Map<InternetAddress, Integer> recipientCountMap = new HashMap<>();
    
    // Iterate through each message.
    for (Iterator<MimeMessage> iter = messages.iterator(); iter.hasNext();) {
      MimeMessage message = iter.next();
      Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
      Set<InternetAddress> recipients = MimeMessageUtil.getValidRecipients(message);
      
      // We only care for e-mails with one sender and one recipient.
      if (senders.size() != NUM_RECIPIENTS_PER_EMAIL || 
          recipients.size() != NUM_RECIPIENTS_PER_EMAIL) {
        iter.remove();
        continue;
      }
      if (addressToIgnore != null) {
        if (senders.contains(addressToIgnore) || 
            recipients.contains(addressToIgnore)) {
          iter.remove();
          continue;
        }
      }
      for (InternetAddress recipient : recipients) {
        Integer count = recipientCountMap.get(recipient);
        recipientCountMap.put(recipient, (count == null) ? 1 : count + 1);
      }
    }
    
    // Find the best address and it's count.
    int maxCount = -1;
    InternetAddress bestAddress = null;
    for (Entry<InternetAddress, Integer> entry : recipientCountMap.entrySet()) {
      if (entry.getValue() > maxCount) {
        maxCount = entry.getValue();
        bestAddress = entry.getKey();
      }
    }
    return Pair.of(bestAddress, maxCount);
  }
}
