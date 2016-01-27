package com.contextsmith.api.data;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.email.cluster.EmailPeopleManager;
import com.contextsmith.email.provider.GmailServiceProvider;
import com.contextsmith.utils.InternetAddressUtil;

public class ProjectFactory {

  static final Logger log = LogManager.getLogger(ProjectFactory.class);

  public static ProjectFactory load(Collection<MimeMessage> messages) {
    ProjectFactory factory = new ProjectFactory();
    return factory.loadMessages(messages);
  }

  private EmailPeopleManager epManager;
  private EmailNameResolver enResolver;

  public ProjectFactory() {
    this.epManager = new EmailPeopleManager();
    this.enResolver = new EmailNameResolver();
  }

  public void categorize(Set<InternetAddress> participants,
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

  public Project createProject(String projectId,
                               String companyDomain,
                               Set<InternetAddress> externalMembers,
                               boolean includePreview,
                               boolean resolveProjectName) {
    Project project = new Project(projectId);
    Set<InternetAddress> participants = new HashSet<>();
    Map<String, Conversation> threadIdToConversationMap = new HashMap<>();

    for (InternetAddress externalAddress : externalMembers) {
      Collection<MimeMessage> messages =
          this.epManager.lookupMessages(externalAddress);
      log.debug("  Processing {} emails from: {}",
                messages.size(), externalAddress.toUnicodeString());

      for (MimeMessage message : messages) {
        // Use thread ID as key.
        String gmailThreadId = GmailServiceProvider.getGmailThreadId(message);
        if (StringUtils.isBlank(gmailThreadId)) continue;

        // TODO(rcwang): Use "References:" header field.
        Conversation conversation = threadIdToConversationMap.get(gmailThreadId);
        if (conversation == null) {
          conversation = new Conversation(gmailThreadId);
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

        if (!threadIdToConversationMap.containsKey(gmailThreadId)) {
          threadIdToConversationMap.put(gmailThreadId, conversation);
          project.addConversation(conversation);
        }
      }
    }
    if (project.getConversations().isEmpty()) return null;

    Set<InternetAddress> internalMembers = new HashSet<>();
    Set<InternetAddress> newExternalMembers = new HashSet<>();
    // Divide 'participants' into 3 groups: internal, external, new external.
    categorize(participants, externalMembers, companyDomain,
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

  protected ProjectFactory loadMessages(Collection<MimeMessage> messages) {
    this.epManager.loadMessages(messages);
    this.enResolver.loadMessages(messages);
    return this;
  }
}
