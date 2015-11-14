package com.contextsmith.api.data;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import com.contextsmith.email.cluster.EmailPeopleManager;
import com.contextsmith.email.provider.GmailServiceProvider;

public class ProjectFactory {

  static final Logger log = LogManager.getLogger(ProjectFactory.class);
  private EmailPeopleManager epManager;

  public ProjectFactory() {
    this.epManager = new EmailPeopleManager();
  }

  public Project createProject(String projectId,
                               Set<InternetAddress> externalClusters,
                               Set<InternetAddress> internalClusters) {
    Project project = new Project(projectId);
    Map<String, Conversation> threadIdToConversationMap = new HashMap<>();

    for (InternetAddress externalAddress : externalClusters) {
      for (MimeMessage message : this.epManager.lookupMessages(externalAddress)) {
        String gmailThreadId = GmailServiceProvider.getGmailThreadId(message);
        if (Strings.isBlank(gmailThreadId)) continue;

        // TODO(rcwang): Use "References:" header field.
        Conversation conversation = threadIdToConversationMap.get(gmailThreadId);
        if (conversation == null) {
          conversation = new Conversation(gmailThreadId);
        }
        try {
          conversation.addMimeMessage(message);
        } catch (IOException | MessagingException e) {
          continue;
        }
        if (!threadIdToConversationMap.containsKey(gmailThreadId)) {
          threadIdToConversationMap.put(gmailThreadId, conversation);
          project.addConversation(conversation);
        }
      }
    }
    if (project.getConversations().isEmpty()) return null;
    project.addExternalMembers(externalClusters);
    project.addInternalMembers(internalClusters);
    return project.sortConversations();
  }

  public ProjectFactory loadMessages(List<MimeMessage> messages) {
    this.epManager.loadMessages(messages);
    return this;
  }
}
