package com.contextsmith.api.data;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.email.cluster.EmailPeopleManager;
import com.contextsmith.utils.InternetAddressUtil;

public class ProjectFactory {

  static final Logger log = LogManager.getLogger(ProjectFactory.class);

  //https://en.wikipedia.org/wiki/List_of_email_subject_abbreviations
   public static final Pattern REPLY_FORWARD_PREFIX_PAT =
  //     Pattern.compile("^.*\\b(?i:re|r|fwd|fw|f):");
  //     Pattern.compile("\\b(?i:re|r|fwd|fw|f):");
       Pattern.compile("(\\b(?i:re|r|fwd|fw|f):|\\[.*?\\])");

   public static final String DEFAULT_HASHING_ALGORITHM = "SHA-256";

/*  public static ProjectFactory createNewInstance(
      Collection<MimeMessage> messages) {
    ProjectFactory factory = new ProjectFactory();
    return factory.loadMessages(messages);
  }*/

  public static String makeConversationId(String subject,
                                          String internalDomain) {
    // Normalize the subject.
    String cleaned = REPLY_FORWARD_PREFIX_PAT.matcher(subject)
        .replaceAll(" ").replaceAll("\\s+", " ").trim();

    try {
      MessageDigest hasher = MessageDigest.getInstance(DEFAULT_HASHING_ALGORITHM);
      byte[] bytes = hasher.digest(cleaned.getBytes(
          StandardCharsets.UTF_8.name()));
      String hashed = Base64.encodeBase64URLSafeString(bytes);
      return String.format("<%s@%s>", hashed, internalDomain.toLowerCase());

    } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
      log.error(e);
      e.printStackTrace();
      return null;
    }
  }

  private EmailPeopleManager epManager;
  private EmailNameResolver enResolver;
//  private EmailThreadResolver etResolver;

  public ProjectFactory() {
    this.epManager = new EmailPeopleManager();
    this.enResolver = new EmailNameResolver();
    //    this.etResolver = new EmailThreadResolver();
  }

  public Project createProject(String internalDomain,
                               Set<InternetAddress> externalMembers,
                               boolean includePreview,
                               boolean resolveProjectName) {
    Project project = new Project();
    Set<InternetAddress> participants = new HashSet<>();
    Map<String, Conversation> threadIdToConversationMap = new HashMap<>();

    for (InternetAddress externalAddress : externalMembers) {
      Collection<MimeMessage> messages =
          this.epManager.lookupMessages(externalAddress);
      log.debug("  Processing {} emails from: {}",
                messages.size(), externalAddress.toUnicodeString());

      for (MimeMessage message : messages) {
        // Get message subject.
        String subject = null;
        try { subject = message.getSubject(); }
        catch (MessagingException e) {}
        if (subject == null) continue;

        // Generate a thread id based on subject and internal domain.
        String conversationId = makeConversationId(subject, internalDomain);

        Conversation conversation = threadIdToConversationMap.get(conversationId);
        if (conversation == null) {
          conversation = new Conversation(conversationId);
        }

        try {
          // Convert from MimeMessage to ContextMessage.
          ContextMessage contextMessage = ContextMessage.build(
              message, includePreview, this.enResolver);
          conversation.addContextMessage(contextMessage);

          // Collect all participants: sender and recipients.
          participants.addAll(contextMessage.getFrom());
          participants.addAll(contextMessage.getTo());
          participants.addAll(contextMessage.getCc());

        } catch (IOException | MessagingException e) {
          log.error(e);
          e.printStackTrace();
          continue;
        }

        if (!threadIdToConversationMap.containsKey(conversationId)) {
          threadIdToConversationMap.put(conversationId, conversation);
          project.addConversation(conversation);
        }
      }
    }
    if (project.getConversations().isEmpty()) return null;

    Set<InternetAddress> internalMembers = new HashSet<>();
    Set<InternetAddress> newExternalMembers = new HashSet<>();
    // Divide 'participants' into 3 groups: internal, external, new external.
    groupParticipants(participants, externalMembers, internalDomain,
                      internalMembers, newExternalMembers);

    // Udpate personal names.
    this.enResolver.resolve(internalMembers);
    this.enResolver.resolve(externalMembers);
    this.enResolver.resolve(newExternalMembers);

    project.addInternalMembers(internalMembers);
    project.addExternalMembers(externalMembers, resolveProjectName);
    project.addNewExternalMembers(newExternalMembers);

    return project.sortConversations();
  }

  public void groupParticipants(Set<InternetAddress> participants,
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

  public ProjectFactory loadMessages(Collection<MimeMessage> messages) {
    this.epManager.loadMessages(messages);
    this.enResolver.loadMessages(messages);
//    this.etResolver.loadMessages(messages);
    return this;
  }
}
