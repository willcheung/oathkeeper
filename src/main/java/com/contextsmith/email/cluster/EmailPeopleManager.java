package com.contextsmith.email.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import com.contextsmith.api.data.Messageable;
import com.contextsmith.utils.MimeMessageUtil.AddressField;

public class EmailPeopleManager {

  public static final AddressField[] ADDRESS_FIELDS = new AddressField[] {
      AddressField.FROM,
      AddressField.TO,
      AddressField.CC,
  };

  /*public static void main(String[] args) throws IOException, MessagingException {
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
  }*/

  private Map<AddressField, Map<InternetAddress, List<Messageable>>> fieldAddrMsgMap;

  public EmailPeopleManager() {
    this.fieldAddrMsgMap = new HashMap<>();
  }

  /** Create a graph AddressField -> address -> list of messages */
  public void loadMessages(Collection<Messageable> messages) {
    for (Messageable message : messages) {
      for (AddressField field : ADDRESS_FIELDS) {
        Set<InternetAddress> addresses = message.getAddress(field);
        if (addresses == null || addresses.isEmpty()) continue;

        Map<InternetAddress, List<Messageable>> addrMsgMap =
            this.fieldAddrMsgMap.get(field);
        if (addrMsgMap == null) {
          this.fieldAddrMsgMap.put(field, addrMsgMap = new HashMap<>());
        }
        for (InternetAddress address : addresses) {
          List<Messageable> msgList = addrMsgMap.get(address);
          if (msgList == null) {
            addrMsgMap.put(address, msgList = new ArrayList<>());
          }
          msgList.add(message);
        }
      }
    }
  }

  public Collection<Messageable> lookupMessages(InternetAddress address) {
    Set<Messageable> allMessages = new HashSet<>();

    for (AddressField field : ADDRESS_FIELDS) {
      Collection<Messageable> messages = lookupMessages(address, field);
      if (messages != null) {
        // Uniqueness of message is determined by message's memory address only.
        allMessages.addAll(messages);
      }
    }
    return allMessages;
  }

  // Lookup messages that have the input address in the input field.
  // Returns null if no such messages exist.
  public Collection<Messageable> lookupMessages(InternetAddress address,
                                                AddressField field) {
    Map<InternetAddress, List<Messageable>> addrMsgMap =
        this.fieldAddrMsgMap.get(field);
    return (addrMsgMap == null) ? null : addrMsgMap.get(address);
  }
}
