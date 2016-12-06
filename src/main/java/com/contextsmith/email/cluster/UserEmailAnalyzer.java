package com.contextsmith.email.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.email.cluster.AddressInfo.AddressType;
import com.contextsmith.utils.MimeMessageUtil;

public class UserEmailAnalyzer {
  private static final Logger log = LoggerFactory.getLogger(UserEmailAnalyzer.class);

  public static final int MAX_RECIPIENTS_PER_EMAIL = 1;
  public static final int MAX_ADDRESSES_TO_GUESS_FROM = 100;
  public static final double MIN_EMAIL_PREDICT_THRESHOLD = 2;

  // If an email's "from" or "to" field contains "addressToIgnore",
  // then it will not contribute to a count.
  // Warning: This method alters the input list 'messages'.
  private static AddressInfo findMostFrequentAddress(
      List<MimeMessage> messages,
      InternetAddress addressToIgnore) {

    Map<InternetAddress, AddressInfo> recipientCountMap = new HashMap<>();
    AddressInfo bestAddressInfo = null;

    // Iterate through each message.
    for (Iterator<MimeMessage> iter = messages.iterator(); iter.hasNext();) {
      MimeMessage message = iter.next();
      Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
      Set<InternetAddress> recipients = MimeMessageUtil.getValidRecipients(message);

      if (senders.size() != 1 || recipients.isEmpty()) {
        iter.remove();
        continue;
      }
      // Ignore sent emails.
      if (MimeMessageUtil.isSentGmail(message)) {
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
        AddressInfo info = recipientCountMap.get(recipient);
        if (info == null) {
          recipientCountMap.put(recipient, info = new AddressInfo(recipient));
        }
        // Increment count.
        double value = info.addValue(1.0 / recipients.size(), AddressType.ALIAS);

        // Keep track of the address with highest count.
        if (bestAddressInfo == null ||
            bestAddressInfo.getValue(AddressType.ALIAS) < value) {
          bestAddressInfo = info;
        }
      }
    }
    return bestAddressInfo;
  }

  private List<AddressInfo> addressInfoList;

  public UserEmailAnalyzer() {
    this.addressInfoList = new ArrayList<>();
  }

  public UserEmailAnalyzer analyze(Collection<MimeMessage> messages) {
    return analyze(messages, MIN_EMAIL_PREDICT_THRESHOLD);
  }

  public Set<InternetAddress> getAddresses() {
    Set<InternetAddress> set = new LinkedHashSet<>();
    for (AddressInfo info : this.addressInfoList) {
      set.add(info.getAddress());
    }
    return set;
  }

  public void printAllResults() {
    int rank = 1;
    log.debug("Result of analyzing user's e-mail addresses:");
    for (AddressInfo info : this.addressInfoList) {
      log.debug("{}. {}", rank++, info);
    }
  }

  private UserEmailAnalyzer analyze(Collection<MimeMessage> messages,
                                    double minThreshold) {
    this.addressInfoList.clear();
    AddressInfo mostFreqInfo = null;
    List<MimeMessage> tempMessages = new ArrayList<>(messages);

    for (int i = 0; i < MAX_ADDRESSES_TO_GUESS_FROM; ++i) {
      // This method modifies 'tempMessages' list.
      mostFreqInfo = findMostFrequentAddress(tempMessages,
          mostFreqInfo == null ? null : mostFreqInfo.getAddress());

      if (mostFreqInfo == null) break;  // No more to output, exit loop.
      if (mostFreqInfo.getValue(AddressType.ALIAS) < minThreshold) break;

      this.addressInfoList.add(mostFreqInfo);
    }
    return this;
  }

  /*private void weightedSumContribution(Collection<MimeMessage> messages) {
    Map<InternetAddress, AddressInfo> addressInfoMap = new HashMap<>();

    for (MimeMessage message : messages) {
      if (MimeMessageUtil.isSentGmail(message)) continue;

      Set<InternetAddress> recipients =
          MimeMessageUtil.getValidRecipients(message);
      if (recipients == null || recipients.isEmpty()) continue;

      String[] sourceInboxes = MimeMessageUtil.getSourceInboxes(message);
      if (sourceInboxes != null) {
        boolean recipientHasSourceInbox = false;
        for (String sourceInbox : sourceInboxes) {
          if (recipients.contains(sourceInbox)) {
            recipientHasSourceInbox = true;
            break;
          }
        }
        if (recipientHasSourceInbox) continue;
      } else {
        InternetAddress deliveredTo = MimeMessageUtil.getDeliveredTo(message);
        if (deliveredTo == null) continue;
        if (recipients.contains(deliveredTo)) continue;
      }

      for (InternetAddress recipient : recipients) {
        AddressInfo info = addressInfoMap.get(recipient);
        if (info == null) {
          addressInfoMap.put(recipient, info = new AddressInfo(recipient));
        }
        info.addValue(1.0 / recipients.size(), AddressType.ALIAS);
      }
    }

    this.addressInfoList.clear();
    this.addressInfoList.addAll(addressInfoMap.values());
    Collections.sort(this.addressInfoList, new Comparator<AddressInfo>() {
      @Override
      public int compare(AddressInfo o1, AddressInfo o2) {
        return Double.compare(o2.getValue(AddressType.ALIAS),
                              o1.getValue(AddressType.ALIAS));
      }
    });
  }*/
}

class AddressInfo {
  enum AddressType { ALIAS }

  private double[] values;
  private InternetAddress address;

  public AddressInfo(InternetAddress address) {
    this.address = address;
    this.values = new double[AddressType.values().length];
    Arrays.fill(this.values, 0);
  }

  public double addValue(double value, AddressType type) {
    this.values[type.ordinal()] += value;
    return this.values[type.ordinal()];
  }

  public InternetAddress getAddress() {
    return this.address;
  }

  public double getValue(AddressType type) {
    return this.values[type.ordinal()];
  }

  public double sumValues() {
    double sum = 0;
    for (int i = 0; i < AddressType.values().length; ++i) {
      sum += this.values[i];
    }
    return sum;
  }

  @Override
  public String toString() {
    return String.format("Score: %.2f, User email: %s",
        this.sumValues(), this.getAddress().toUnicodeString());
  }
}
