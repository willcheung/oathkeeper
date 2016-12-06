package com.contextsmith.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.mail.internet.InternetAddress;

import edu.uci.ics.jung.algorithms.scoring.EigenvectorCentrality;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.Graph;

public class GraphUtil {
  public enum GraphWalkType { PAGE_RANK, RANDOM_WALK }

  public static PageRank<InternetAddress, String> doGraphWalk(
      Graph<InternetAddress, String> graph,
      GraphWalkType walkType,
      Double dampingFactor) {
    checkNotNull(graph);
    checkNotNull(walkType);

    PageRank<InternetAddress, String> ranker = null;
    switch (walkType) {
    case PAGE_RANK: ranker = new PageRank<>(graph, 1 - dampingFactor); break;
    case RANDOM_WALK: ranker = new EigenvectorCentrality<>(graph); break;
    }
    if (ranker == null) return null;
    ranker.acceptDisconnectedGraph(true);
    ranker.evaluate();

    for (InternetAddress address : graph.getVertices()) {
      System.out.println(String.format("%f\t%s",
          ranker.getVertexScore(address), address.toUnicodeString()) );
    }
    return ranker;
  }

  public static PageRank<InternetAddress, String> pageRank(
      Graph<InternetAddress, String> graph,
      double dampingFactor) {
    return doGraphWalk(graph, GraphWalkType.PAGE_RANK, dampingFactor);
  }

  public static PageRank<InternetAddress, String> randomWalk(
      Graph<InternetAddress, String> graph) {
    return doGraphWalk(graph, GraphWalkType.RANDOM_WALK, null);
  }
}
