package com.contextsmith.demo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.collections15.Transformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.util.Strings;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;

public class EmailClusterer {
  
  private static final Logger log = LogManager.getLogger(EmailClusterer.class);
  
  public static final boolean USE_ENRON_DATA = false;
  public static final String ENRON_USER = "kean-s";  // "kean-s, smith-m"
  public static final int ENRON_MAX_MESSAGES = -1;  // -1 = unlimited
  
  public static final String GMAIL_USER = "me";
  public static final String GMAIL_QUERY = "before:2014/8/31";
  public static final int GMAIL_MAX_MESSAGES = 10_000;
  
  // Graph-related constants.
  public static final int MIN_RECIPIENTS_PER_EMAIL_IN_GRAPH = 2;
  public static final boolean IS_FILTER_ADDITIONAL_NODES = false;
  public static final String EDGE_ID_PREFIX = "MAILED";
  
  // Cluster-related constants.
  public static final int INTERNAL_CLUSTER_ID = 0;
  public static final int MIN_CLUSTER_SIZE_TO_PRINT = 2;
  public static final double MIN_EMAIL_PREDICT_THRESHOLD = 2;
  
  public static final Set<String> COMMON_MAIL_HOST_DOMAINS =
      Sets.newHashSet("gmail.com", "yahoo.com", "live.com", "hotmail.com", 
                      "aol.com", "mail.com", "inbox.com", "outlook.com");

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
      filterInvalidAddresses(senders, addressesToIgnore, domainToIgnore);
      filterInvalidAddresses(recipients, addressesToIgnore, domainToIgnore);
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
  public static Set<Set<InternetAddress>> findClustersIgnoringDomain(
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
  public static Set<Set<InternetAddress>> findExternalClusters(
      List<MimeMessage> messages,
      Set<InternetAddress> userAddressesToIgnore,
      String internalDomain) {
    checkNotNull(messages);
    checkNotNull(userAddressesToIgnore);
    if (userAddressesToIgnore.isEmpty()) return null;
    if (Strings.isNullOrEmpty(internalDomain)) return null;
    
    Graph<InternetAddress, String> graph = 
        buildGraph(messages, userAddressesToIgnore, internalDomain);
    
    return transformGraphToClusters(graph);
  }
  
  public static void main(String[] args) {
    Stopwatch stopwatch = new Stopwatch();
    stopwatch.start();
    
    log.info("Fetching e-mails...");
    List<MimeMessage> messages = null;
    if (USE_ENRON_DATA) {
      EmailProvider provider = new LocalFileProvider();
      messages = provider.provide(ENRON_USER, null, ENRON_MAX_MESSAGES);
    } else {  // Read from Gmail
      EmailProvider provider = new GmailServiceProvider();
      messages = provider.provide(GMAIL_USER, GMAIL_QUERY, GMAIL_MAX_MESSAGES);
    }
    if (messages == null) return;
    log.info("Fetching e-mails took " + stopwatch + "\n");
    
    // Predict user's e-mail addresses.
    Map<InternetAddress, Double> addressScoreMap = UserAddressPredictor.predict(
        messages, MIN_EMAIL_PREDICT_THRESHOLD);
    
    // We use the user's e-mail address to obtain company's email domain;
    // so it must *not* be empty here.
    if (addressScoreMap.isEmpty()) return;
    
    // Extract company mail address domain.
    String internalDomain = findInternalDomain(addressScoreMap.keySet());
    
    Set<Set<InternetAddress>> clusters = null;
    if (internalDomain == null) {  // A common domain (eg. gmail).
      clusters = findClustersIgnoringDomain(messages, addressScoreMap.keySet());
    } else {
      clusters = findExternalClusters(messages, addressScoreMap.keySet(), 
                                      internalDomain);
    }
    if (clusters == null) return;
    printClusters(clusters);
    
    D3Object d3Object = makeD3Object(messages, clusters, 
                                     addressScoreMap.keySet(), internalDomain);
    
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    log.info(gson.toJson(d3Object));
    log.info("Total elapsed time: " + stopwatch);
  }
  
  public static D3Object makeD3Object(
      List<MimeMessage> messages, 
      Set<Set<InternetAddress>> externalClusters,
      Set<InternetAddress> userAddressesToIgnore,
      String internalDomain) {
    // Convert external clusters into d3 nodes.
    int clusterId = 1;
    Map<InternetAddress, D3Node> addrNodeMap = new HashMap<>();
    for (Iterator<Set<InternetAddress>> iter1 = externalClusters.iterator(); 
         iter1.hasNext(); clusterId++) {
      Set<InternetAddress> cluster = iter1.next();
      
      for (Iterator<InternetAddress> iter2 = cluster.iterator(); iter2.hasNext();) {
        InternetAddress address = iter2.next();
        
        D3Node d3Node = addrNodeMap.get(address);
        if (d3Node != null) {
          d3Node.belongTo.add(clusterId);
          continue;
        }
        d3Node = new D3Node();
        d3Node.id = address.getAddress();
        d3Node.clusterId = clusterId;
        d3Node.belongTo.add(clusterId);
        addrNodeMap.put(address, d3Node);
      }
    }
    
    // Construct a graph that includes internal employee's e-mails,
    // but excludes owner's email aliases.
    Graph<InternetAddress, String> graph = 
        buildGraph(messages, userAddressesToIgnore, null);
    
    // Use external addresses to find internal addresses using the graph.
    Map<InternetAddress, D3Node> externalMap = new HashMap<>(addrNodeMap);
    for (Entry<InternetAddress, D3Node> entry : externalMap.entrySet()) {
      InternetAddress externalAddr = entry.getKey();
      D3Node externalNode = entry.getValue();
      Collection<InternetAddress> neighborAddrs = 
          graph.getNeighbors(externalAddr);
      if (neighborAddrs == null) continue;
      
      for (InternetAddress internalAddr : neighborAddrs) {
        // Make sure the email address has the 'internal domain'.
        if (!MimeMessageUtil.hasDomain(internalAddr, internalDomain)) {
          continue;
        }
        D3Node node = addrNodeMap.get(internalAddr);
        if (node == null) {
          node = new D3Node();
          node.id = internalAddr.getAddress();
          node.clusterId = INTERNAL_CLUSTER_ID;
          addrNodeMap.put(internalAddr, node);
        }
        node.belongTo.add(externalNode.clusterId);
      }
    }
    
    D3Object d3Object = new D3Object();
    d3Object.d3Nodes.addAll(addrNodeMap.values());  // Output d3 nodes here.
    
    // Build source/target node edge mappings using the graph.
    for (InternetAddress sourceAddr : addrNodeMap.keySet()) {
      Collection<InternetAddress> targetAddrs = graph.getSuccessors(sourceAddr);
      if (targetAddrs == null) continue;
      
      for (InternetAddress targetAddr : targetAddrs) {
        // Filter out addresses that we have not seen before.
        if (!addrNodeMap.containsKey(targetAddr)) continue;
        
        D3Link link = new D3Link();
        link.source = sourceAddr.getAddress();
        link.target = targetAddr.getAddress();
        d3Object.d3Links.add(link);  // Output d3 links here.
      }
    }
    return d3Object;
  }
  
  // Print out the clusters.
  public static void printClusters(Set<Set<InternetAddress>> clusters) {
    int clusterId = 1;
    for (Set<InternetAddress> cluster : clusters) {
      if (cluster.size() < MIN_CLUSTER_SIZE_TO_PRINT) continue;
      StringBuilder builder = new StringBuilder();
      
      for (InternetAddress address : cluster) {
        if (builder.length() != 0) builder.append(", ");
        builder.append(address.toUnicodeString());
      }
      log.info(String.format("Cluster #%d: [%s]", clusterId++, builder));
    }
  }
  
  // Removes invalid addresses from the input collection of 'addressesToFilter'.
  private static void filterInvalidAddresses(
      Set<InternetAddress> addressesToFilter, 
      Set<InternetAddress> addressesToIgnore, 
      String domainToIgnore) {
    for (Iterator<InternetAddress> iter = addressesToFilter.iterator(); 
         iter.hasNext();) {
      InternetAddress address = iter.next();
      if (MimeMessageUtil.shouldIgnore(address, domainToIgnore, addressesToIgnore)) {
        iter.remove();
      }
    }
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
      if (MimeMessageUtil.shouldIgnore(address, domainToIgnore, addressesToIgnore)) {
        verticesToRemove.add(address);
      }
    }
    for (InternetAddress address : verticesToRemove) {
      graph.removeVertex(address);
    }
  }
  
  // Extract company mail address domain from owner's email aliases.
  private static String findInternalDomain(Set<InternetAddress> userAddresses) {
    checkNotNull(userAddresses);
    if (userAddresses.isEmpty()) return null;
    
    // Pick the top-ranked one for now.
    InternetAddress address = userAddresses.iterator().next();
    String userDomain = MimeMessageUtil.getAddressDomain(address).toLowerCase();
    
    log.debug("User e-mail address domain: " + userDomain);
    /*if (COMMON_MAIL_HOST_DOMAINS.contains(userDomain)) {
      System.out.println("\"" + userDomain + "\" is a common domain, ignoring!");
      userDomain = null;
    }*/
    return userDomain;
  }
  
  private static Set<Set<InternetAddress>> transformGraphToClusters(
      Graph<InternetAddress, String> graph) {
    // Detects disconnected sub-graphs from the graph.
    Transformer<Graph<InternetAddress, String>, Set<Set<InternetAddress>>> transformer = 
        new WeakComponentClusterer<InternetAddress, String>();
    return transformer.transform(graph);
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

class D3Link {
  String source = null;
  String target = null;
}

class D3Node {
  String id = null;
  Integer clusterId = null;
  Set<Integer> belongTo = new HashSet<>();
}

class D3Object {
  List<D3Node> d3Nodes = new ArrayList<>();
  List<D3Link> d3Links = new ArrayList<>();
}