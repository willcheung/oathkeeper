package com.contextsmith.email.cluster;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.utils.MimeMessageUtil;

public class EmailThreadResolver {

  static final Logger log = LogManager.getLogger(EmailThreadResolver.class);

  private Map<String, String> msgIdToRootMap;
  private Set<String> messageIds;

  public EmailThreadResolver() {
    this.msgIdToRootMap = new HashMap<>();
    this.messageIds = new HashSet<>();
  }

  public String get(MimeMessage message) {
    String messageId = MimeMessageUtil.getMessageId(message);
    if (StringUtils.isBlank(messageId)) return null;
    return this.get(messageId);
  }

  public String get(String messageId) {
    if (!this.messageIds.contains(messageId)) {
      log.warn("Could not find input message id: {}", messageId);
      return null;
    }
    String rootId = this.msgIdToRootMap.get(messageId);
    return (rootId == null) ? messageId : rootId;
  }

  public EmailThreadResolver loadMessages(Collection<MimeMessage> messages) {
    this.messageIds.clear();
    this.msgIdToRootMap.clear();

    for (MimeMessage message : messages) {
      String currentId = MimeMessageUtil.getMessageId(message);
      if (StringUtils.isBlank(currentId)) continue;
      this.messageIds.add(currentId);
    }

    for (MimeMessage message : messages) {
      String[] referenceIds = MimeMessageUtil.getReferences(message);
      if (referenceIds == null || referenceIds.length == 0) continue;

      String currentId = MimeMessageUtil.getMessageId(message);
      if (StringUtils.isBlank(currentId)) continue;

      String rootId = referenceIds[0];
      this.msgIdToRootMap.put(currentId, rootId);
      for (int i = 1; i < referenceIds.length; ++i) {
        String referenceId = referenceIds[i];
        // We only care about the message ids in user's inbox.
        if (!this.messageIds.contains(referenceId)) continue;
        this.msgIdToRootMap.put(referenceId, rootId);
      }
    }
    return this;
  }

  public void resolve() {
    for (Entry<String, String> entry : this.msgIdToRootMap.entrySet()) {
      this.msgIdToRootMap.put(entry.getKey(), resolve(entry.getValue()));
    }
  }

  private String resolve(String messageId) {
    String rootId = this.msgIdToRootMap.get(messageId);
    // No ancestor means it is root itself.
    if (rootId == null) return messageId;
    else return resolve(rootId);
  }
}
