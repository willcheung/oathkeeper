package com.contextsmith.demo;

import static com.google.common.base.Preconditions.checkNotNull;

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.uci.ics.jung.graph.Graph;

public class EmailClustererUtil {
  
  private static final Logger log = LogManager.getLogger(EmailClustererUtil.class);
  
  // Cluster-related constants.
  public static final int INTERNAL_CLUSTER_ID = 0;
  public static final int MIN_CLUSTER_SIZE_TO_PRINT = 2;
  
  // Extract company mail address domain from owner's email aliases.
  public static String findInternalDomain(Set<InternetAddress> userAddresses) {
    checkNotNull(userAddresses);
    if (userAddresses.isEmpty()) return null;
    
    // Pick the first-ranked one for now.
    InternetAddress address = userAddresses.iterator().next();
    String userDomain = MimeMessageUtil.getAddressDomain(address).toLowerCase();
    log.debug("User e-mail address domain: " + userDomain);
    return userDomain;
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
        EmailClusterer.buildGraph(messages, userAddressesToIgnore, null);
    
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
