package org.openplans.tools.tracking.impl.graph.paths.algorithms;

import java.util.HashSet;
import java.util.Set;

import org.opentripplanner.common.geometry.DistanceLibrary;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.routing.algorithm.GenericAStar;
import org.opentripplanner.routing.algorithm.TraverseVisitor;
import org.opentripplanner.routing.algorithm.strategies.RemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.algorithm.strategies.SkipTraverseResultStrategy;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.IntersectionVertex;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Coordinate;

public class MultiDestinationAStar implements
    SearchTerminationStrategy, RemainingWeightHeuristic,
    SkipTraverseResultStrategy, TraverseVisitor {
  private static final long serialVersionUID = 1L;

  private final HashSet<Edge> end;
  private final Coordinate center;
  private final double radius;
  private final Graph graph;

  private final Edge start;

  private final DistanceLibrary distanceLibrary =
      SphericalDistanceLibrary.getInstance();

  private final double maxDistance;

  public MultiDestinationAStar(Graph graph, Set<Edge> endEdges,
    Coordinate center, double radius, Edge start,
    double distanceUpperLimit) {
    Preconditions.checkArgument(distanceUpperLimit > 0d);
    Preconditions.checkArgument(radius > 0d);
    this.maxDistance = distanceUpperLimit;
    this.graph = graph;
    this.end = new HashSet<Edge>(endEdges);
    this.center = center;
    this.radius = radius;
    this.start = start;
  }

  @Override
  public double computeForwardWeight(State s, Vertex target) {
    final Vertex v = s.getVertex();

    double distance = distanceLibrary.fastDistance(
        v.getCoordinate(), center);

    if (distance < radius)
      return 0;

    return distance / s.getOptions().getCarSpeed();
  }

  @Override
  public double computeInitialWeight(State s, Vertex target) {
    return computeForwardWeight(s, target);
  }

  @Override
  public double computeReverseWeight(State s, Vertex target) {
    return computeForwardWeight(s, target);
  }

  public ShortestPathTree getSPT(boolean arriveBy) {
    // set up
    final GenericAStar astar = new GenericAStar();
    astar.setSearchTerminationStrategy(this);
    astar.setSkipTraverseResultStrategy(this);
    astar.setTraverseVisitor(this);

    final RoutingRequest req = new RoutingRequest(TraverseMode.CAR);
    req.setArriveBy(arriveBy);

    final Vertex startVertex =
        arriveBy ? start.getToVertex() : start.getFromVertex();
    final String bogusName = "bogus" + Thread.currentThread().getId();
    final Vertex bogus =
        new IntersectionVertex(graph, bogusName,
            startVertex.getCoordinate(), bogusName);

    if (!arriveBy) {
      req.setRoutingContext(graph, startVertex, bogus);
    } else {
      req.setRoutingContext(graph, bogus, startVertex);
    }
    req.rctx.remainingWeightHeuristic = this;

    final ShortestPathTree result = astar.getShortestPathTree(req);
    graph.removeVertex(bogus);
    req.cleanup();

    return result;

  }

  @Override
  public void reset() {
    // not implemented because it is unused, unfortunately
  }

  @Override
  public boolean shouldSearchContinue(Vertex origin, Vertex target,
    State current, ShortestPathTree spt,
    RoutingRequest traverseOptions) {
    final double traveledDistance = current.getWalkDistance();
    if (Math.abs(traveledDistance) >= this.maxDistance)
      return false;
    return end.size() != 0;
  }

  @Override
  public boolean shouldSkipTraversalResult(Vertex origin,
    Vertex target, State parent, State current, ShortestPathTree spt,
    RoutingRequest traverseOptions) {
    if (parent.getVertex() == origin
        && current.getBackEdge() != start) {
      return true;
    }
    return false;
  }

  @Override
  public void visitEdge(Edge edge, State state) {
    end.remove(edge);
  }

  @Override
  public void visitEnqueue(State state) {
    // nothing
  }

  @Override
  public void visitVertex(State state) {
    // nothing
  }
}