package com.contextsmith.email.cluster;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class FrequencyInfo {
  public static FrequencyInfo countFrequency(List<MimeMessage> messages) {

    RecipientType[] types = new RecipientType[] {
        RecipientType.TO,
        RecipientType.CC,
        RecipientType.BCC,
    };

    FrequencyInfo freqInfo = new FrequencyInfo();
    for (MimeMessage message : messages) {
      //      boolean isInRecipient = false;

      for (RecipientType type : types) {
        Set<Address> recipients = new HashSet<>();
        try {
          recipients.addAll(Arrays.asList(message.getRecipients(type)));
        } catch (MessagingException e) {
          e.printStackTrace();
          continue;
        }
        if (recipients.isEmpty()) continue;

        for (Address recipient : recipients) {
          String address = 
              ((InternetAddress) recipient).getAddress().toLowerCase();
          freqInfo.increment(type, address);

          //          if (((InternetAddress) recipient).getAddress().toLowerCase().equals(USER_EMAIL)) {
          //            freqInfo.increment(type);
          //            isInRecipient = true;
          //            break;  // Exit if found.
          //          }
        }
      }
      //      if (isInRecipient) freqInfo.totalCounter++;
      freqInfo.totalCounter++;  // Total # of e-mails.
    }
    return freqInfo;
  }
  Multiset<String> toCounter;
  Multiset<String> ccCounter;
  Multiset<String> bccCounter;
  
  int totalCounter = 0;
  
  FrequencyInfo() {
    this.toCounter = HashMultiset.create();
    this.bccCounter = HashMultiset.create();
    this.ccCounter = HashMultiset.create();
    this.totalCounter = 0;
  }
  
  public void increment(RecipientType type, String address) {
    if (type == RecipientType.TO) this.toCounter.add(address);
    if (type == RecipientType.CC) this.ccCounter.add(address);
    if (type == RecipientType.BCC) this.bccCounter.add(address);
  }
  
  public String toString() {
    return String.format("Total: %d, To:%d, CC:%d, BCC:%d", 
                         totalCounter, toCounter.size(), ccCounter.size(), 
                         bccCounter.size());
  }
}
