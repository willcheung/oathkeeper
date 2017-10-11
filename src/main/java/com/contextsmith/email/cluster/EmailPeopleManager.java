package com.contextsmith.email.cluster;

import com.contextsmith.api.data.Messageable;
import com.contextsmith.utils.MimeMessageUtil.AddressField;

import javax.mail.internet.InternetAddress;
import java.util.*;
import java.util.function.Function;

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
    if (address.getAddress().indexOf('*') != -1) { // support wildcard matches == evil hack
      final String glob = address.getAddress();
      Function<InternetAddress, Boolean> matcher = email -> EmailPeopleManager.matches(email.getAddress(), glob);
      Map<InternetAddress, List<Messageable>> addrMsgMap =
              this.fieldAddrMsgMap.get(field);
      if (addrMsgMap != null) {
        Optional<Map.Entry<InternetAddress, List<Messageable>>> result = addrMsgMap.entrySet().stream().filter(entry -> matcher.apply(entry.getKey())).findFirst();
        return result.isPresent() ? result.get().getValue() : null;
      } else {
        return null;
      }
    } else {
       Map<InternetAddress, List<Messageable>> addrMsgMap = addrMsgMap = this.fieldAddrMsgMap.get(field);
      return (addrMsgMap == null) ? null : addrMsgMap.get(address); // change this if address == *@bubu.com
    }

  }

  public static boolean matches(String text, String glob) {
    String rest = null;
    int pos = glob.indexOf('*');
    if (pos != -1) {
        rest = glob.substring(pos + 1);
        glob = glob.substring(0, pos);
    }

    if (glob.length() > text.length())
        return false;

    // handle the part up to the first *
    for (int i = 0; i < glob.length(); i++)
        if (glob.charAt(i) != '?'
                && !glob.substring(i, i + 1).equalsIgnoreCase(text.substring(i, i + 1)))
            return false;

    // recurse for the part after the first *, if any
    if (rest == null) {
        return glob.length() == text.length();
    } else {
        for (int i = glob.length(); i <= text.length(); i++) {
            if (matches(text.substring(i), rest))
                return true;
        }
        return false;
    }
}

}
