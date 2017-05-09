package com.contextsmith.email.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.api.service.TokenEmailPair;
import com.contextsmith.email.provider.EmailFilterer;
import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.MimeMessageUtil;

import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;

public class EmailClusterer {
  public static enum ClusteringMethod { BY_WEAK_COMPONENT, BY_EMAIL_DOMAIN }

  // Graph-related constants.
  public static final int MIN_RECIPIENTS_PER_EMAIL_IN_GRAPH = 2;
  public static final int MIN_NODE_DEGREE_TO_KEEP_IN_GRAPH = 2;
  public static final int MIN_CLUSTER_SIZE_TO_OUTPUT = 2;
  public static final String EDGE_ID_PREFIX = "MAILED";

  private static final Logger log = LoggerFactory.getLogger(EmailClusterer.class);

  public static Graph<InternetAddress, String> buildGraph(
      Collection<MimeMessage> messages) {
    return buildGraph(messages, null, null);
  }

  public static Graph<InternetAddress, String> buildGraph(
      Collection<MimeMessage> messages,
      Set<InternetAddress> addressesToIgnore,
      String domainToIgnore) {

    Graph<InternetAddress, String> graph = new SparseGraph<>();
    int edgeCount = 0;

    for (MimeMessage message : messages) {
      // Filter recipient.
      Set<InternetAddress> recipients = MimeMessageUtil.getValidRecipients(message);
      if (recipients.size() < MIN_RECIPIENTS_PER_EMAIL_IN_GRAPH) {
        continue;
      }

      // Filter sender.
      Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
      // Must have only one sender.
      if (senders.size() != 1) continue;
      InternetAddress sender = senders.iterator().next();

      // Add edges to the graph.
      for (InternetAddress recipient : recipients) {
        boolean success = graph.addEdge(
            EDGE_ID_PREFIX + "_" + edgeCount,  // Edge must be an unique id.
            new edu.uci.ics.jung.graph.util.Pair<InternetAddress>(sender, recipient),
            EdgeType.DIRECTED);
        if (success) edgeCount++;
      }
    }
    Set<InternetAddress> removed =
        filterVertices(graph, addressesToIgnore, domainToIgnore, true);
    log.debug("Removed {} vertices from social network graph.", removed.size());

    return graph;
  }

  // Returns only clusters containing external e-mail addresses.
  public static List<Set<InternetAddress>> findExternalClusters(
      Collection<MimeMessage> messages,
      List<TokenEmailPair> tokenEmailPairs,
      String internalDomain,
      ClusteringMethod clusteringMethod) {
    checkNotNull(messages);
    checkNotNull(tokenEmailPairs);
    if (tokenEmailPairs.isEmpty()) return null;
    if (StringUtils.isBlank(internalDomain)) return null;

    // Find all user's alias e-mail addresses.
    UserEmailAnalyzer analyzer = new UserEmailAnalyzer().analyze(messages);
    Set<InternetAddress> sortedAliasEmails = analyzer.getAddresses();
    analyzer.printAllResults();

    // Insert all internal email addresses into the alias set.
    for (TokenEmailPair tokenEmailPair : tokenEmailPairs) {
      sortedAliasEmails.add(tokenEmailPair.getEmailAddress());
    }

    // Filter out irrelevant emails.
    Collection<MimeMessage> filtered = new EmailFilterer()
        .setRemoveMailListMessages(false)
        .setRemovePrivateMessages(false)
        .filter(messages);

    Graph<InternetAddress, String> graph = buildGraph(
        filtered, sortedAliasEmails, internalDomain);

    List<Set<InternetAddress>> clusters = null;
    switch (clusteringMethod) {
    case BY_EMAIL_DOMAIN:
        clusters = clusterGraphByEmailDomain(graph);
        break;
    case BY_WEAK_COMPONENT:
        clusters = clusterGraphByWeakComponent(graph);
        break;
    default:
        log.error("Unknown cluster method: " + clusteringMethod.name());
        return null;
    }

    for (Iterator<Set<InternetAddress>> iter = clusters.iterator(); iter.hasNext();) {
      Set<InternetAddress> cluster = iter.next();
      if (cluster.size() < MIN_CLUSTER_SIZE_TO_OUTPUT) {
        log.trace("Ignoring single-person cluster: {}", cluster);
        iter.remove();
      }
    }
    return clusters;
  }

  private static List<Set<InternetAddress>> clusterGraphByEmailDomain(
          Graph<InternetAddress, String> graph) {
      Map<String, Set<InternetAddress>> domainToInetAddr = new HashMap<>();
      for (InternetAddress address : graph.getVertices()) {
          String domain = InternetAddressUtil.getAddressDomain(address).toLowerCase();
          if (StringUtils.isBlank(domain)) {
              log.warn("Malformed email address: " + address);
              continue;
          }
          Set<InternetAddress> addresses = domainToInetAddr.get(domain);
          if (addresses == null) {
              addresses = new HashSet<InternetAddress>();
              domainToInetAddr.put(domain, addresses);
          }
          addresses.add(address);
      }
      return new ArrayList<>(domainToInetAddr.values());
  }

  private static List<Set<InternetAddress>> clusterGraphByWeakComponent(
      Graph<InternetAddress, String> graph) {
    // Detects disconnected sub-graphs from the graph.
    Transformer<Graph<InternetAddress, String>, Set<Set<InternetAddress>>> transformer =
        new WeakComponentClusterer<InternetAddress, String>();
    Set<Set<InternetAddress>> clusters = transformer.transform(graph);
    return new ArrayList<>(clusters);
  }

  // Returns vertices removed.
  private static Set<InternetAddress> filterVertices(
      Graph<InternetAddress, String> graph,
      Set<InternetAddress> addressesToIgnore,
      String domainToIgnore,
      boolean ignoreCommonWebmailDomain) {
    Set<InternetAddress> verticesToRemove = new HashSet<>();

    for (InternetAddress address : graph.getVertices()) {
      int inDegree = graph.inDegree(address);
      int outDegree = graph.outDegree(address);

      if (inDegree == 0) {
        verticesToRemove.add(address);
      } else if (inDegree == 1 && outDegree == 0) {
        verticesToRemove.add(address);
      } else if (InternetAddressUtil.shouldIgnore(
          address, domainToIgnore, addressesToIgnore,
          ignoreCommonWebmailDomain)) {
        verticesToRemove.add(address);
      }
    }
    for (InternetAddress address : verticesToRemove) {
      graph.removeVertex(address);
    }
    return verticesToRemove;
  }
}