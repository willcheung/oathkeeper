package com.contextsmith.api.service;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.Environment;
import com.contextsmith.utils.Mode;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.service.NewsFeeder.MessageType;
import com.contextsmith.email.cluster.EmailClusterer.ClusteringMethod;
import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.StringUtil;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import static com.contextsmith.api.service.NewsFeederRequest.Provider.exchange;
import static com.contextsmith.api.service.NewsFeederRequest.Provider.gmail;

public class NewsFeederRequest {
    public static final String OUTPUT_SEP = " | ";
    public static final MessageType DEFAULT_MESSAGE_TYPE = MessageType.EMAIL;
    public static final ClusteringMethod DEFAULT_CLUSTERING_METHOD = ClusteringMethod.BY_EMAIL_DOMAIN;
    public static final int DEFAULT_MAX_MESSAGES = 1_000;
    public static final boolean DEFAULT_SHOW_CONTENT = false;
    public static final boolean DEFAULT_PARSE_TIME = false;
    public static final boolean DEFAULT_PARSE_REQUEST = false;
    private static final Logger log = LoggerFactory.getLogger(NewsFeederRequest.class);
    // These are mandatory (cannot be null).
    protected int maxMessages;
    protected boolean showContent;
    protected boolean parseTime;
    protected boolean parseRequest;
    //  protected boolean resolveProjectName;
    protected Double posSentimentThreshold;
    protected Double negSentimentThreshold;
    protected MessageType messageType;
    // These are optional (can be null).
    protected Long startTimeInSec;
    protected Long endTimeInSec;
    protected String callbackUrl;
    protected String userQuery;        // for user-specified search: not surfaced
    protected String internalDomain;
    protected String subjectToRetain;  // for subject search
    protected List<Set<InternetAddress>> externalClusters; // limit to these addresses
    private ClusteringMethod clusteringMethod;
    private Provider provider = gmail;
    private SourceConfiguration sourceConfiguration = new SourceConfiguration();

    public NewsFeederRequest() {
        this.messageType = DEFAULT_MESSAGE_TYPE;
        this.userQuery = null;
        this.externalClusters = null;
        this.maxMessages = DEFAULT_MAX_MESSAGES;
        this.callbackUrl = null;
        this.showContent = DEFAULT_SHOW_CONTENT;
        this.parseTime = DEFAULT_PARSE_TIME;
        this.parseRequest = DEFAULT_PARSE_REQUEST;
        this.startTimeInSec = null;
        this.endTimeInSec = null;
        this.internalDomain = null;
        this.subjectToRetain = null;
        this.posSentimentThreshold = null;
        this.negSentimentThreshold = null;
        this.clusteringMethod = DEFAULT_CLUSTERING_METHOD;
        this.sourceConfiguration.sources = new Source[]{};
    }

    public String getCallbackUrl() {
        return this.callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public ClusteringMethod getClusteringMethod() {
        return this.clusteringMethod;
    }

    public void setClusteringMethod(ClusteringMethod clusteringMethod) {
        if (clusteringMethod == null) return;
        this.clusteringMethod = clusteringMethod;
    }

    public Long getEndTimeInSec() {
        return this.endTimeInSec;
    }

    public void setEndTimeInSec(Long endTimeInSec) {
        this.endTimeInSec = endTimeInSec;
    }

    public List<Set<InternetAddress>> getExternalClusters() {
        return this.sourceConfiguration.getExternalClusters();
    }

    public String getInternalDomain() {
        return this.internalDomain;
    }

    public void setInternalDomain(String internalDomain) {
        this.internalDomain = internalDomain;
    }

    public int getMaxMessages() {
        return this.maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        if (maxMessages == null) {
            if (Environment.mode == Mode.dev) {
                this.maxMessages = 100;
            }
            return;
        }
        this.maxMessages = maxMessages;
    }

    public MessageType getMessageType() {
        return this.messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public Double getNegSentimentThreshold() {
        return this.negSentimentThreshold;
    }

    public void setNegSentimentThreshold(Double negSentimentThreshold) {
        this.negSentimentThreshold = negSentimentThreshold;
    }

    public Double getPosSentimentThreshold() {
        return this.posSentimentThreshold;
    }

    public void setPosSentimentThreshold(Double posSentimentThreshold) {
        this.posSentimentThreshold = posSentimentThreshold;
    }

    public Long getStartTimeInSec() {
        return this.startTimeInSec;
    }

    public void setStartTimeInSec(Long startTimeInSec) {
        this.startTimeInSec = startTimeInSec;
    }

    public String getSubjectToRetain() {
        return this.subjectToRetain;
    }

    public void setSubjectToRetain(String subjectToRetain) {
        this.subjectToRetain = subjectToRetain;
    }

    public List<TokenEmailPair> getTokenEmailPairs() {
        return Arrays.stream(sourceConfiguration.sources)
                .filter(source -> source.kind == gmail)
                .map(source -> new TokenEmailPair(source.token, source.email)).collect(Collectors.toList());
    }

    public String getUserQuery() {
        return this.userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public boolean isParseRequest() {
        return this.parseRequest;
    }

    public void setParseRequest(Boolean parseRequest) {
        if (parseRequest == null) return;
        this.parseRequest = parseRequest;
    }

    public boolean isParseTime() {
        return this.parseTime;
    }

    public void setParseTime(Boolean parseTime) {
        if (parseTime == null) return;
        this.parseTime = parseTime;
    }

    public boolean isShowContent() {
        return this.showContent;
    }

    public void setShowContent(Boolean showContent) {
        if (showContent == null) return;
        this.showContent = showContent;
    }

    @Override
    public String toString() {
        return StringUtil.toJson(this);
    }

    public boolean hasTimeFilter() {
        return startTimeInSec != null && endTimeInSec != null;
    }

    public void setSourceConfiguration(SourceConfiguration sourceConfiguration) {
        this.sourceConfiguration = sourceConfiguration;
    }

    public SourceConfiguration getSourceConfiguration() {
        return sourceConfiguration;
    }

    public enum Provider {
        gmail,
        exchange
    }

    // Input: "abc|def|ghi@xyz.com"
    // Returns: [abc@xyz.com, def@xyz.com, ghi@xyz.com]
    public static List<InternetAddress> expandRequestAddresses(String text) {
        String[] parts = StringUtils.split(text, '@');
        if (parts.length != 2) return null;

        String domain = parts[1];
        if (StringUtils.isBlank(domain)) return null;

        String[] users = StringUtils.split(parts[0], '|');
        if (users.length == 0) return null;

        List<InternetAddress> addresses = new ArrayList<>();
        for (String user : users) {
            if (StringUtils.isBlank(user)) continue;
            String email = user + "@" + domain;
            InternetAddress ia = InternetAddressUtil.newIAddress(email);
            if (ia != null) addresses.add(ia);
        }
        return addresses;
    }

    public static List<Set<InternetAddress>> parseExternalClusterJson(String json) {
        if (StringUtils.isBlank(json)) return null;

        Type doubleArrayType = new TypeToken<String[][]>() {
        }.getType();
        String[][] clustersStr =
                StringUtil.getGsonInstance().fromJson(json, doubleArrayType);

        List<Set<InternetAddress>> clusters = new ArrayList<>();
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
}