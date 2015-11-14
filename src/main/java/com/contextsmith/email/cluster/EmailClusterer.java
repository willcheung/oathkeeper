package com.contextsmith.email.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.collections15.Transformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.api.client.util.Strings;

import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;

public class EmailClusterer {

  public static final Logger log = LogManager.getLogger(EmailClusterer.class);

  // Graph-related constants.
  public static final int MIN_RECIPIENTS_PER_EMAIL_IN_GRAPH = 2;
  public static final boolean IS_FILTER_ADDITIONAL_NODES = false;
  public static final String EDGE_ID_PREFIX = "MAILED";

  /*public static void addInternalMembers(
      List<MimeMessage> messages,
      Set<Set<InternetAddress>> externalClusters,
      Set<InternetAddress> userAddresses,
      String internalDomain,
      D3Object d3Object) {

    // Construct a graph that includes external and internal employee's e-mails,
    // but excludes nodes of owner's email aliases.
    Graph<InternetAddress, String> graph =
        buildGraph(messages, userAddresses, null);

    for (Set<InternetAddress> cluster : externalClusters) {
      Set<InternetAddress> tempCluster = new HashSet<>(cluster);

      for (InternetAddress externalAddr : tempCluster) {
        Collection<InternetAddress> neighborAddrs =
            graph.getNeighbors(externalAddr);
        if (neighborAddrs == null) continue;

        for (InternetAddress address : neighborAddrs) {
          // Make sure the email address has the 'internal domain'.
          if (MessageUtil.hasDomain(address, internalDomain)) {
            cluster.add(address);  // Add only internal address to external cluster.
          }
        }
      }
    }
    if (d3Object == null) return;

  }*/

  public static Graph<InternetAddress, String> buildGraph(
      List<MimeMessage> messages) {
    return buildGraph(messages, null, null);
  }

  public static Graph<InternetAddress, String> buildGraph(
      List<MimeMessage> messages,
      Set<InternetAddress> addressesToIgnore,
      String domainToIgnore) {

    Graph<InternetAddress, String> graph = new SparseGraph<>();
    Map<InternetAddress, InternetAddress> vertexIdentityMap = new HashMap<>();
    int edgeCount = 0;

    for (MimeMessage message : messages) {
      Set<InternetAddress> senders = MimeMessageUtil.getValidSenders(message);
      Set<InternetAddress> recipients = MimeMessageUtil.getValidRecipients(message);

      if (recipients.size() < MIN_RECIPIENTS_PER_EMAIL_IN_GRAPH) {
        continue;
      }
      InternetAddressUtil.filterInvalidAddresses(senders, addressesToIgnore, domainToIgnore);
      InternetAddressUtil.filterInvalidAddresses(recipients, addressesToIgnore, domainToIgnore);
      if (senders.isEmpty() || recipients.isEmpty()) continue;

      // Add edges to the graph.
      for (InternetAddress sender : senders) {
        for (InternetAddress recipient : recipients) {

          boolean success = graph.addEdge(
              EDGE_ID_PREFIX + "_" + edgeCount,  // Edge must be an unique id.
              new edu.uci.ics.jung.graph.util.Pair<InternetAddress>(sender, recipient),
              EdgeType.DIRECTED);

          if (success) {
            updateEmptyName(vertexIdentityMap, sender);
            updateEmptyName(vertexIdentityMap, recipient);
            edgeCount++;
          }
        }
      }
    }
    if (IS_FILTER_ADDITIONAL_NODES) {
      // Filter vertex that has zero inDegree or outDegree.
      filterVertices(graph, vertexIdentityMap.keySet(),
                     addressesToIgnore, domainToIgnore);
    }
    return graph;
  }

  // Returns all possible clusters, including both internal and external.
  public static List<Set<InternetAddress>> findClustersIgnoringDomain(
      List<MimeMessage> messages,
      Set<InternetAddress> userAddressesToIgnore) {
    checkNotNull(messages);
    checkNotNull(userAddressesToIgnore);
    if (userAddressesToIgnore.isEmpty()) return null;

    Graph<InternetAddress, String> graph =
        buildGraph(messages, userAddressesToIgnore, null);

    return transformGraphToClusters(graph);
  }

  // Returns only clusters containing external e-mail addresses.
  public static List<Set<InternetAddress>> findExternalClusters(
      List<MimeMessage> messages,
      String internalDomain,
      Set<InternetAddress> userAddressesToIgnore) {
    checkNotNull(messages);
    checkNotNull(userAddressesToIgnore);
    if (userAddressesToIgnore.isEmpty()) return null;
    if (Strings.isNullOrEmpty(internalDomain)) return null;

    Graph<InternetAddress, String> graph =
        buildGraph(messages, userAddressesToIgnore, internalDomain);

    return transformGraphToClusters(graph);
  }

  public static List<Set<InternetAddress>> findInternalClusters(
      List<MimeMessage> messages,
      String internalDomain,
      List<Set<InternetAddress>> externalClusters) {

    // Construct a graph that includes external and internal employee's e-mails,
    // but excludes nodes of owner's email aliases.
    Graph<InternetAddress, String> graph =
        buildGraph(messages, null, null);

    List<Set<InternetAddress>> internalClusters =
        new ArrayList<>(externalClusters.size());
    // Initialize each index to an empty set.
    for (int i = 0; i < externalClusters.size(); ++i) {
      internalClusters.add(new HashSet<InternetAddress>());
    }

    for (int i = 0; i < externalClusters.size(); ++i) {
      Set<InternetAddress> externalCluster = externalClusters.get(i);
      Set<InternetAddress> internalCluster = internalClusters.get(i);

      for (InternetAddress externalAddr : externalCluster) {
        Collection<InternetAddress> neighborAddrs =
            graph.getNeighbors(externalAddr);
        if (neighborAddrs == null) continue;

        for (InternetAddress address : neighborAddrs) {
          // Make sure the email address has the 'internal domain'.
          if (InternetAddressUtil.hasDomain(address, internalDomain)) {
            // Add only internal address to external cluster.
            internalCluster.add(address);
          }
        }
      }
    }
    return internalClusters;
  }

  private static void filterVertices(Graph<InternetAddress, String> graph,
                                     Set<InternetAddress> vertices,
                                     Set<InternetAddress> addressesToIgnore,
                                     String domainToIgnore) {
    Set<InternetAddress> verticesToRemove = new HashSet<>();

    for (InternetAddress address : vertices) {
      int inDegree = graph.inDegree(address);
      int outDegree = graph.outDegree(address);
      if (inDegree == 0 || outDegree == 0) {
        verticesToRemove.add(address);
      }
      if (InternetAddressUtil.shouldIgnore(address, domainToIgnore, addressesToIgnore)) {
        verticesToRemove.add(address);
      }
    }
    for (InternetAddress address : verticesToRemove) {
      graph.removeVertex(address);
    }
  }

  private static List<Set<InternetAddress>> transformGraphToClusters(
      Graph<InternetAddress, String> graph) {
    // Detects disconnected sub-graphs from the graph.
    Transformer<Graph<InternetAddress, String>, Set<Set<InternetAddress>>> transformer =
        new WeakComponentClusterer<InternetAddress, String>();
    Set<Set<InternetAddress>> clusters = transformer.transform(graph);
    return new ArrayList<>(clusters);
  }

  private static void updateEmptyName(
      Map<InternetAddress, InternetAddress> vertexIdentityMap,
      InternetAddress address) {
    InternetAddress cached = vertexIdentityMap.get(address);
    if (cached == null) {
      vertexIdentityMap.put(address, address);
      return;
    }
    String inputName = address.getPersonal();
    if (Strings.isNullOrEmpty(inputName) || inputName.contains("@")) return;

    String cachedName = cached.getPersonal();
    if (Strings.isNullOrEmpty(cachedName)) {
      try {
        // Store the name that is more complete.
        cached.setPersonal(inputName.trim());
      } catch (UnsupportedEncodingException e) {
        System.err.println(e);
      }
    }
  }
}