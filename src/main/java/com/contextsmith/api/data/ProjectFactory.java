package com.contextsmith.api.data;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.email.cluster.EmailPeopleManager;
import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.MimeMessageUtil;

public class ProjectFactory {
  private static final Logger log = LoggerFactory.getLogger(ProjectFactory.class);

  public static final String DEFAULT_HASHING_ALGORITHM = "SHA-256";

  public static String makeConversationId(String subject,
                                          String internalDomain) {
    // Normalize the subject.
    String normalized = MimeMessageUtil.normalizeSubject(subject);

    try {
      MessageDigest hasher = MessageDigest.getInstance(DEFAULT_HASHING_ALGORITHM);
      byte[] bytes = hasher.digest(normalized.getBytes(
          StandardCharsets.UTF_8.name()));
      String hashed = Base64.encodeBase64URLSafeString(bytes);
      return String.format("<%s@%s>", hashed, internalDomain.toLowerCase());

    } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
      log.error(e.toString());
      e.printStackTrace();
      return null;
    }
  }

  private EmailPeopleManager epManager;
//  private EmailNameResolver enResolver;

  public ProjectFactory() {
    this.epManager = new EmailPeopleManager();
//    this.enResolver = new EmailNameResolver();
  }

  /*public Project createProject(String internalDomain,
                               Set<InternetAddress> externalMembers,
                               boolean showContent,
                               boolean parseTime,
                               boolean parseRequest,
                               boolean resolveProjectName,
                               Double posSentimentThreshold,
                               Double negSentimentThreshold,
                               Pattern searchPattern) {*/
  public Project createProject(String internalDomain,
                               Set<InternetAddress> externalMembers) {
    Project project = new Project();
    Set<InternetAddress> participants = new HashSet<>();
    Map<String, Conversation> threadIdToConversationMap = new HashMap<>();

//    Map<String, Messageable> msgIdToMsgMap =
//        mapMessageIdToMessage(externalMembers, showContent, parseTime,
//                                     parseRequest, posSentimentThreshold,
//                                     negSentimentThreshold, searchPattern);

    for (InternetAddress externalAddress : externalMembers) {
      Collection<Messageable> messages =
          this.epManager.lookupMessages(externalAddress);

      for (Messageable message : messages) {
        String messageId = message.getMessageId();
        if (messageId == null) continue;

//        Messageable contextMessage = msgIdToMsgMap.get(messageId);
//        if (contextMessage == null) continue;

        String subject = message.getSubject();
        if (subject == null) continue;

        // Generate a thread id based on subject and internal domain.
        String conversationId = makeConversationId(subject, internalDomain);

        // See if the conversation with this thread id has already exist.
        Conversation conversation =
            threadIdToConversationMap.get(conversationId);
        if (conversation == null) {
          conversation = new Conversation(conversationId);
          threadIdToConversationMap.put(conversationId, conversation);
          project.addConversation(conversation);
        }
        conversation.addMessage(message);

        // Collect all participants: sender and recipients.
        if (message.getFrom() != null) {
          participants.addAll(message.getFrom());
        }
        if (message.getTo() != null) {
          participants.addAll(message.getTo());
        }
        if (message.getCc() != null) {
          participants.addAll(message.getCc());
        }
      }
    }
    if (project.getConversations().isEmpty()) return null;

    Set<InternetAddress> internalMembers = new HashSet<>();
    Set<InternetAddress> newExternalMembers = new HashSet<>();
    // Divide 'participants' into 3 groups: internal, external, new external.
    groupParticipants(participants, externalMembers, internalDomain,
                      internalMembers, newExternalMembers);

    // Update personal names.
//    this.enResolver.resolve(internalMembers);
//    this.enResolver.resolve(externalMembers);
//    this.enResolver.resolve(newExternalMembers);

    project.addInternalMembers(internalMembers);
//    project.addExternalMembers(externalMembers, resolveProjectName);
    project.addExternalMembers(externalMembers);
    project.addNewExternalMembers(newExternalMembers);

//    if (searchPattern != null) {
//      project.setSearchPattern(searchPattern.pattern());
//    }
    return project.sortConversations();
  }

  public ProjectFactory loadMessages(Collection<Messageable> messages) {
    this.epManager.loadMessages(messages);
//    this.enResolver.loadMessages(messages);
    return this;
  }

  private void groupParticipants(Set<InternetAddress> participants,
                                 Set<InternetAddress> externalMembers,
                                 String internalDomain,
                                 Set<InternetAddress> internalMembers,
                                 Set<InternetAddress> newExternalMembers) {
    for (InternetAddress participant : participants) {
      if (InternetAddressUtil.hasDomain(participant, internalDomain)) {
        internalMembers.add(participant);
      } else if (!externalMembers.contains(participant)) {
        newExternalMembers.add(participant);
      }
    }
  }

  /*private Map<String, ContextMessage> mapMessageIdToContextMessage(
      Set<InternetAddress> externalMembers,
      boolean showContent,
      boolean parseTime,
      boolean parseRequest,
      Double posSentimentThreshold,
      Double negSentimentThreshold,
      Pattern searchPattern) {
    int numKeywords = (searchPattern == null) ? 0 :
      StringUtils.countMatches(searchPattern.pattern(), '|') + 1;

    Map<String, ContextMessage> msgIdToContextMsgMap = new HashMap<>();
    for (InternetAddress externalAddress : externalMembers) {
      Collection<MimeMessage> messages =
          this.epManager.lookupMessages(externalAddress);

      log.debug("  Processing {} emails from: {}",
          messages.size(), externalAddress.toUnicodeString());

      for (MimeMessage message : messages) {
        String messageId = MimeMessageUtil.getMessageId(message);
        // Ensure every message is analyzed only once.
        if (messageId == null || msgIdToContextMsgMap.containsKey(messageId)) {
          continue;
        }
        log.trace("    Analyzing content of: {}", messageId);

        // Convert from MimeMessage to ContextMessage.
        ContextMessage contextMessage = ContextMessage.newInstance(
            message, showContent, parseTime, parseRequest,
            posSentimentThreshold, negSentimentThreshold, searchPattern,
            this.enResolver);
        if (contextMessage == null) continue;

        if (searchPattern != null) {
          // Must have search results here or discard this context message.
          if (contextMessage.getSearchAnnotations() == null ||
              AnnotationUtil.uniqueText(contextMessage.getSearchAnnotations()).size() < numKeywords) {
            continue;
          }
        }
        msgIdToContextMsgMap.put(messageId, contextMessage);
      }
    }
    return msgIdToContextMsgMap;
  }*/
}
