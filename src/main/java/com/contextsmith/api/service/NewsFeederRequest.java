package com.contextsmith.api.service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.Environment;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.service.NewsFeeder.MessageType;
import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.StringUtil;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class NewsFeederRequest {
  private static final Logger log = LoggerFactory.getLogger(
      NewsFeederRequest.class);

  public static final String OUTPUT_SEP = " | ";
  public static final MessageType DEFAULT_MESSAGE_TYPE = MessageType.EMAIL;
  public static final int DEFAULT_MAX_MESSAGES = 1_000;
  public static final boolean DEFAULT_SHOW_CONTENT = false;
  public static final boolean DEFAULT_PARSE_TIME = false;
  public static final boolean DEFAULT_PARSE_REQUEST = false;
  private Provider provider = Provider.gmail;

//  public static final boolean DEFAULT_RESOLVE_PROJECT_NAME = false;

  // Input: "abc|def|ghi@xyz.com"
  // Returns: [abc@xyz.com, def@xyz.com, ghi@xyz.com]
  public static List<InternetAddress> expandRequestAddresses(String text) {
    String[] parts = StringUtils.split(text, '@');
    if (parts.length != 2) return null;

    String domain = parts[1];
    if (Strings.isBlank(domain)) return null;

    String[] users = StringUtils.split(parts[0], '|');
    if (users.length == 0) return null;

    List<InternetAddress> addresses = new ArrayList<>();
    for (String user : users) {
      if (Strings.isBlank(user)) continue;
      String email = user + "@" + domain;
      InternetAddress ia = InternetAddressUtil.newIAddress(email);
      if (ia != null) addresses.add(ia);
    }
    return addresses;
  }

  public static List<Set<InternetAddress>> parseExternalClusterJson(String json) {
    if (StringUtils.isBlank(json)) return null;

    Type doubleArrayType = new TypeToken<String[][]>(){}.getType();
    String[][] clustersStr =
        StringUtil.getGsonInstance().fromJson(json, doubleArrayType);

    List<Set<InternetAddress>> clusters = new ArrayList<Set<InternetAddress>>();
    for (String[] clusterStr : clustersStr) {
      Set<InternetAddress> cluster = new HashSet<>();

      for (String address : clusterStr) {
        address = address.toLowerCase();
        if (address.contains("|")) {
          List<InternetAddress> addresses = expandRequestAddresses(address);
          if (addresses != null) cluster.addAll(addresses);
        } else {
          InternetAddress ia = InternetAddressUtil.newIAddress(address);
          if (ia != null) cluster.add(ia);
        }
      }
      clusters.add(cluster);
    }
    return clusters;
  }

  // These are mandatory (cannot be null).
  protected int maxMessages;
  protected boolean showContent;
  protected boolean parseTime;
  protected boolean parseRequest;
//  protected boolean resolveProjectName;
  protected Double posSentimentThreshold;
  protected Double negSentimentThreshold;
  protected List<TokenEmailPair> tokenEmailPairs;
  protected List<Credential> credentials;
  protected MessageType messageType;

  // These are optional (can be null).
  protected Long startTimeInSec;
  protected Long endTimeInSec;
  protected String callbackUrl;
  protected String userQuery;        // for user-specified search: not surfaced
  protected String internalDomain;
  protected String subjectToRetain;  // for subject search
  protected List<Set<InternetAddress>> externalClusters; // limit to these addresses

  public NewsFeederRequest() {
    this.messageType = DEFAULT_MESSAGE_TYPE;
    this.tokenEmailPairs = null;
    this.userQuery = null;
    this.externalClusters = null;
    this.maxMessages = DEFAULT_MAX_MESSAGES;
    this.callbackUrl = null;
    this.showContent = DEFAULT_SHOW_CONTENT;
    this.parseTime = DEFAULT_PARSE_TIME;
    this.parseRequest = DEFAULT_PARSE_REQUEST;
//    this.resolveProjectName = DEFAULT_RESOLVE_PROJECT_NAME;
    this.startTimeInSec = null;
    this.endTimeInSec = null;
    this.internalDomain = null;
    this.subjectToRetain = null;
    this.posSentimentThreshold = null;
    this.negSentimentThreshold = null;
  }

  public String getCallbackUrl() {
    return this.callbackUrl;
  }

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

  public MessageType getMessageType() {
    return this.messageType;
  }

  public Double getNegSentimentThreshold() {
    return this.negSentimentThreshold;
  }

  public Double getPosSentimentThreshold() {
    return this.posSentimentThreshold;
  }

  public Long getStartTimeInSec() {
    return this.startTimeInSec;
  }

  public String getSubjectToRetain() {
    return this.subjectToRetain;
  }

  public List<TokenEmailPair> getTokenEmailPairs() {
    return this.tokenEmailPairs;
  }

  public String getUserQuery() {
    return this.userQuery;
  }

  public boolean isParseRequest() {
    return this.parseRequest;
  }

  public boolean isParseTime() {
    return this.parseTime;
  }

  public boolean isShowContent() {
    return this.showContent;
  }

//  public boolean isResolveProjectName() {
//    return this.resolveProjectName;
//  }

  public List<TokenEmailPair> parseTokenEmailJson(String json) {
    if (StringUtils.isBlank(json)) return null;

    Type tokenEmailType = new TypeToken<List<TokenEmailPair>>(){}.getType();
    try {
      return StringUtil.getGsonInstance().fromJson(json, tokenEmailType);
    } catch (JsonSyntaxException e) {
      log.error(json);
      log.error(e.toString());
    }
    return null;
  }

  public List<Credential> parseCredentialsJson(String json) {
    if (StringUtils.isBlank(json)) return null;

    Type type = new TypeToken<List<Credential>>(){}.getType();
    try {
      return StringUtil.getGsonInstance().fromJson(json, type);
    } catch (JsonSyntaxException e) {
      log.error(json);
      log.error(e.toString());
    }
    return null;
  }


  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public void setEndTimeInSec(Long endTimeInSec) {
    this.endTimeInSec = endTimeInSec;
  }

  public void setExternalClusters(List<Set<InternetAddress>> externalClusters) {
    this.externalClusters = externalClusters;
  }

  public void setExternalClustersFromJson(String json) {
    List<Set<InternetAddress>> clusters = parseExternalClusterJson(json);
    if (clusters != null && !clusters.isEmpty()) {
      this.externalClusters = clusters;
    }
  }

  public void setInternalDomain(String internalDomain) {
    this.internalDomain = internalDomain;
  }

  public void setMaxMessages(Integer maxMessages) {
    if (maxMessages == null) {
      if (Environment.dev) {
        this.maxMessages = 100;
      }
      return;
    }
    this.maxMessages = maxMessages;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public void setNegSentimentThreshold(Double negSentimentThreshold) {
    this.negSentimentThreshold = negSentimentThreshold;
  }

  public void setParseRequest(Boolean parseRequest) {
    if (parseRequest == null) return;
    this.parseRequest = parseRequest;
  }

  public void setParseTime(Boolean parseTime) {
    if (parseTime == null) return;
    this.parseTime = parseTime;
  }

  public void setPosSentimentThreshold(Double posSentimentThreshold) {
    this.posSentimentThreshold = posSentimentThreshold;
  }

//  public void setResolveProjectName(Boolean resolve) {
//    if (resolve == null) return;
//    this.resolveProjectName = resolve;
//  }

  public void setShowContent(Boolean showContent) {
    if (showContent == null) return;
    this.showContent = showContent;
  }

  public void setStartTimeInSec(Long startTimeInSec) {
    this.startTimeInSec = startTimeInSec;
  }

  public void setSubjectToRetain(String subjectToRetain) {
    this.subjectToRetain = subjectToRetain;
  }

  public void setTokenEmailPairs(List<TokenEmailPair> tokenEmailPairs) {
    this.tokenEmailPairs = tokenEmailPairs;
  }

  public void setTokenEmailPairsFromJson(String json) {
    this.tokenEmailPairs = parseTokenEmailJson(json);
  }

  public void setCredentialsJson(String json) {
    this.credentials = parseCredentialsJson(json);
  }

  public void setUserQuery(String userQuery) {
    this.userQuery = userQuery;
  }

  @Override
  public String toString() {
    return StringUtil.toJson(this);
    /*StringBuilder builder = new StringBuilder();
    builder.append(this.tokenEmailPairs).append(OUTPUT_SEP);
    builder.append(this.searchQuery).append(OUTPUT_SEP);
    builder.append(this.externalClusters).append(OUTPUT_SEP);
    builder.append(this.maxMessages).append(OUTPUT_SEP);
    builder.append(this.callbackUrl).append(OUTPUT_SEP);
    builder.append(this.showContent).append(OUTPUT_SEP);
    builder.append(this.resolveProjectName).append(OUTPUT_SEP);
    builder.append(this.startTimeInSec).append(OUTPUT_SEP);
    builder.append(this.endTimeInSec).append(OUTPUT_SEP);
    return builder.toString();*/
  }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider p) {
      this.provider = p;
    }

  public List<Credential> getCredentials() {
    return credentials == null ? Collections.emptyList() : credentials;
  }

  public boolean hasTimeFilter() {
    return startTimeInSec != null && endTimeInSec != null;
  }

  public enum Provider {
      gmail,
      exchange
    }
}