package com.contextsmith.api.service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.utils.InternetAddressUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class NewsFeederRequest {

  static final Logger log = LogManager.getLogger(NewsFeederRequest.class);

  public static final String OUTPUT_SEP = " | ";
  public static final int DEFAULT_MAX_MESSAGES = 1_000;
//  public static final int DEFAULT_ENRON_MAX_MESSAGES = -1;  // -1 = unlimited
  public static final boolean DEFAULT_SHOW_PREVIEW = false;
  public static final boolean DEFAULT_RESOLVE_PROJECT_NAME = false;

  // These are mandatory (cannot be null).
  protected int maxMessages;
  protected boolean showPreview;
  protected boolean resolveProjectName;
  protected List<TokenEmailPair> tokenEmailPairs;
//  protected EmailFilterer messageFilterer;

  // These are optional (can be null).
  protected Long startTimeInSec;
  protected Long endTimeInSec;
  protected String callbackUrl;
  protected String searchQuery;
  protected String internalDomain;
  protected List<Set<InternetAddress>> externalClusters;

  public NewsFeederRequest() {
    this.tokenEmailPairs = null;
    this.searchQuery = null;
    this.externalClusters = null;
    this.maxMessages = DEFAULT_MAX_MESSAGES;
    this.callbackUrl = null;
    this.showPreview = DEFAULT_SHOW_PREVIEW;
    this.resolveProjectName = DEFAULT_RESOLVE_PROJECT_NAME;
    this.startTimeInSec = null;
    this.endTimeInSec = null;
    this.internalDomain = null;
//    this.messageFilterer = null;
  }

  public String getCallbackUrl() {
    return this.callbackUrl;
  }

  /*public String getAccessToken() {
    return this.accessToken;
  }*/

  public Long getEndTimeInSec() {
    return this.endTimeInSec;
  }

  public List<Set<InternetAddress>> getExternalClusters() {
    return this.externalClusters;
  }

  public String getInternalDomain() {
    return this.internalDomain;
  }

  public int getMaxMessages() {
    return this.maxMessages;
  }

  /*public EmailFilterer getMessageFilterer() {
    return this.messageFilterer;
  }*/

  public String getSearchQuery() {
    return this.searchQuery;
  }

  public Long getStartTimeInSec() {
    return this.startTimeInSec;
  }

  public List<TokenEmailPair> getTokenEmailPairs() {
    return this.tokenEmailPairs;
  }

  /*public InternetAddress getUserEmail() {
    return this.userEmail;
  }*/

  public boolean isResolveProjectName() {
    return this.resolveProjectName;
  }

  public boolean isShowPreview() {
    return this.showPreview;
  }

  public void parseExternalClusterJson(String json) {
    if (StringUtils.isBlank(json)) return;

    Type doubleArrayType = new TypeToken<String[][]>(){}.getType();
    String[][] clustersStr = new Gson().fromJson(json, doubleArrayType);

    List<Set<InternetAddress>> clusters = new ArrayList<Set<InternetAddress>>();
    for (String[] clusterStr : clustersStr) {
      Set<InternetAddress> cluster = new HashSet<>();
      for (String address : clusterStr) {
        InternetAddress ia = InternetAddressUtil.newIAddress(address.toLowerCase());
        if (ia != null) cluster.add(ia);
      }
      clusters.add(cluster);
    }
    if (!clusters.isEmpty()) this.externalClusters = clusters;
  }

  public void parseTokenEmailJson(String json) {
    if (StringUtils.isBlank(json)) return;

    Type tokenEmailType = new TypeToken<List<TokenEmailPair>>(){}.getType();
    try {
      this.tokenEmailPairs = new Gson().fromJson(json, tokenEmailType);
    } catch (JsonSyntaxException e) {
      log.error(json);
      log.error(e);
    }
  }

  /*public void setExternalClusters(String[][] emailClusters) {
    if (emailClusters == null) return;

    List<Set<InternetAddress>> clusters = new ArrayList<Set<InternetAddress>>();
    for (String[] clusterStr : emailClusters) {
      Set<InternetAddress> cluster = new HashSet<>();
      for (String address : clusterStr) {
        InternetAddress ia = InternetAddressUtil.newIAddress(address.toLowerCase());
        if (ia != null) cluster.add(ia);
      }
      clusters.add(cluster);
    }
    if (!clusters.isEmpty()) this.externalClusters = clusters;
  }*/

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public void setEndTimeInSec(Long endTimeInSec) {
    this.endTimeInSec = endTimeInSec;
  }

  public void setInternalDomain(String internalDomain) {
    this.internalDomain = internalDomain;
  }

  public void setMaxMessages(Integer maxMessages) {
    if (maxMessages == null) return;
    this.maxMessages = maxMessages;
  }

  /*public void setMessageFilterer(EmailFilterer messageFilterer) {
    this.messageFilterer = messageFilterer;
  }*/

  public void setResolveProjectName(Boolean resolve) {
    if (resolve == null) return;
    this.resolveProjectName = resolve;
  }

  public void setSearchQuery(String searchQuery) {
    this.searchQuery = searchQuery;
  }

  public void setShowPreview(Boolean showPreview) {
    if (showPreview == null) return;
    this.showPreview = showPreview;
  }

  public void setStartTimeInSec(Long startTimeInSec) {
    this.startTimeInSec = startTimeInSec;
  }

  public void setTokenEmailPairs(List<TokenEmailPair> tokenEmailPairs) {
    this.tokenEmailPairs = tokenEmailPairs;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(this.tokenEmailPairs).append(OUTPUT_SEP);
    builder.append(this.searchQuery).append(OUTPUT_SEP);
    builder.append(this.externalClusters).append(OUTPUT_SEP);
    builder.append(this.maxMessages).append(OUTPUT_SEP);
    builder.append(this.callbackUrl).append(OUTPUT_SEP);
    builder.append(this.showPreview).append(OUTPUT_SEP);
    builder.append(this.resolveProjectName).append(OUTPUT_SEP);
    builder.append(this.startTimeInSec).append(OUTPUT_SEP);
    builder.append(this.endTimeInSec).append(OUTPUT_SEP);
    return builder.toString();
  }
}