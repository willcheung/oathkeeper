package com.contextsmith.email.cluster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.contextsmith.api.service.NewsFeeder;
import com.contextsmith.api.service.NewsFeederRequest;
import com.contextsmith.email.provider.GmailQueryBuilder;
import com.contextsmith.email.provider.EmailFetcher;
import com.contextsmith.utils.MimeMessageUtil;
import com.contextsmith.utils.MimeMessageUtil.AddressField;

public class EmailPeopleManager {

  public static final AddressField[] ADDRESS_FIELDS = new AddressField[] {
      AddressField.FROM,
      AddressField.TO,
      AddressField.CC,
      AddressField.BCC
  };

  public static void main(String[] args) throws IOException, MessagingException {
    String query = new GmailQueryBuilder().addBeforeDate(
        NewsFeeder.DEFAULT_GMAIL_BEFORE_DATE).build();
    List<MimeMessage> messages = EmailFetcher.fetchGmails(
        query,
        EmailFetcher.DEFAULT_ACCESS_TOKEN,
        NewsFeederRequest.DEFAULT_MAX_MESSAGES);

    EmailPeopleManager epm = new EmailPeopleManager();
    epm.loadMessages(messages);  // Index messages.
    Collection<MimeMessage> msgs = epm.lookupMessages(
        new InternetAddress("Chad.Black@clarizen.com"));

    for (MimeMessage message : msgs) {
      message.writeTo(System.out);
    }
  }

  private Map<AddressField, Map<InternetAddress, List<MimeMessage>>> fieldAddrMsgMap;

  public EmailPeopleManager() {
    this.fieldAddrMsgMap = new HashMap<>();
  }

  public void loadMessages(Collection<MimeMessage> messages) {
    for (MimeMessage message : messages) {
//      if (!MimeMessageUtil.isUsefulMessage(message)) continue;

      for (AddressField field : ADDRESS_FIELDS) {
        Set<InternetAddress> addresses =
            MimeMessageUtil.getValidAddresses(message, field);
        if (addresses.isEmpty()) continue;

        Map<InternetAddress, List<MimeMessage>> addrMsgMap =
            this.fieldAddrMsgMap.get(field);
        if (addrMsgMap == null) {
          this.fieldAddrMsgMap.put(field, addrMsgMap = new HashMap<>());
        }
        for (InternetAddress address : addresses) {
          List<MimeMessage> msgList = addrMsgMap.get(address);
          if (msgList == null) {
            addrMsgMap.put(address, msgList = new ArrayList<>());
          }
          msgList.add(message);
        }
      }
    }
  }

  public Collection<MimeMessage> lookupMessages(InternetAddress address) {
    Set<MimeMessage> allMessages = new HashSet<>();

    for (AddressField field : ADDRESS_FIELDS) {
      Collection<MimeMessage> messages = lookupMessages(address, field);
      if (messages != null) {
        // Uniqueness of message is determined by message's memory address only.
        allMessages.addAll(messages);
      }
    }
    return allMessages;
  }

  // Lookup messages that have the input address in the input field.
  // Returns null if no such messages exist.
  public Collection<MimeMessage> lookupMessages(InternetAddress address,
                                                AddressField field) {
    Map<InternetAddress, List<MimeMessage>> addrMsgMap =
        this.fieldAddrMsgMap.get(field);
    return (addrMsgMap == null) ? null : addrMsgMap.get(address);
  }
}
