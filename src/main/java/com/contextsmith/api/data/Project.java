package com.contextsmith.api.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.InternetAddressUtil;

public class Project implements Comparable<Project> {
  private Date lastSentDate;
  private String projectId;
  private String topExternalMemberName;
  private String topExternalMemberDomain;
  private String searchPattern;

  private List<Conversation> conversations;
  private Set<InternetAddress> internalMembers;
  private Set<InternetAddress> externalMembers;
  private Set<InternetAddress> newExternalMembers;

  public Project() {
    this.lastSentDate = null;
    this.topExternalMemberName = null;
    this.topExternalMemberDomain = null;
    this.searchPattern = null;

    this.conversations = new ArrayList<>();
    this.externalMembers = new HashSet<>();
    this.internalMembers = new HashSet<>();
    this.newExternalMembers = new HashSet<>();
  }

  public boolean addConversation(Conversation conversation) {
    return this.conversations.add(conversation);
  }

  /*public void addExternalMembers(Set<InternetAddress> members,
                                 boolean resolveProjectName) {
    this.externalMembers.addAll(members);
    this.topExternalMemberDomain =
        InternetAddressUtil.findMostFrequentDomain(members);

    if (resolveProjectName && this.topExternalMemberDomain != null) {
      this.topExternalMemberName =
          WhoisLookup.lookupRegistrantOrganization(this.topExternalMemberDomain);
    }
  }*/

  public void addExternalMembers(Set<InternetAddress> members) {
    this.externalMembers.addAll(members);
    this.topExternalMemberDomain =
        InternetAddressUtil.findMostFrequentDomain(members);
  }

  public void addInternalMembers(Set<InternetAddress> members) {
    this.internalMembers.addAll(members);
  }

  public void addNewExternalMembers(Set<InternetAddress> members) {
    this.newExternalMembers.addAll(members);
  }

  @Override
  public int compareTo(Project project) {
    if (!this.conversations.isEmpty() && this.lastSentDate == null) {
      this.sortConversations();
    }
    return project.getLastSentDate().compareTo(this.lastSentDate);
  }

  public List<Conversation> getConversations() {
    return this.conversations;
  }

  public Set<InternetAddress> getExternalMembers() {
    return this.externalMembers;
  }

  public Set<InternetAddress> getInternalMembers() {
    return this.internalMembers;
  }

  public Date getLastSentDate() {
    return this.lastSentDate;
  }

  public Set<InternetAddress> getNewExternalMembers() {
    return this.newExternalMembers;
  }

  public String getProjectId() {
    return this.projectId;
  }

  public String getSearchPattern() {
    return this.searchPattern;
  }

  public String getTopExternalMemberDomain() {
    return this.topExternalMemberDomain;
  }

  public String getTopExternalMemberName() {
    return this.topExternalMemberName;
  }

  public void setSearchPattern(String searchPattern) {
    this.searchPattern = searchPattern;
  }

  // Sort conversations by sent-date in ascending order (oldest first).
  public Project sortConversations() {
    if (!this.conversations.isEmpty()) {
      Collections.sort(this.conversations);
      this.lastSentDate = this.conversations.get(0).getLastSentDate();
    }
    return this;
  }
}
