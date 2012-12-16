package org.openplans.tools.tracking.impl.util;

/*
 * Copy of JTS's PointBuilder
 */

import java.util.*;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.algorithm.*;
import com.vividsolutions.jts.geomgraph.*;

/**
 * Constructs {@link Point}s from the nodes of an overlay graph.
 * @version 1.7
 */
public class EdgePointBuilder {
  private EdgeOverlayOp op;
  private GeometryFactory geometryFactory;
  private List resultPointList = new ArrayList();

  public EdgePointBuilder(EdgeOverlayOp op, GeometryFactory geometryFactory, PointLocator ptLocator) {
    this.op = op;
    this.geometryFactory = geometryFactory;
    // ptLocator is never used in this class
  }

  /**
   * Computes the Point geometries which will appear in the result,
   * given the specified overlay operation.
   *
   * @return a list of the Points objects in the result
   */
  public List build(int opCode)
  {
    extractNonCoveredResultNodes(opCode);
    /**
     * It can happen that connected result nodes are still covered by
     * result geometries, so must perform this filter.
     * (For instance, this can happen during topology collapse).
     */
    return resultPointList;
  }

  /**
   * Determines nodes which are in the result, and creates {@link Point}s for them.
   *
   * This method determines nodes which are candidates for the result via their
   * labelling and their graph topology.
   *
   * @param opCode the overlay operation
   */
  private void extractNonCoveredResultNodes(int opCode)
  {
    // testing only
    //if (true) return resultNodeList;

    for (Iterator nodeit = op.getGraph().getNodes().iterator(); nodeit.hasNext(); ) {
      Node n = (Node) nodeit.next();

      // filter out nodes which are known to be in the result
      if (n.isInResult())
        continue;
      // if an incident edge is in the result, then the node coordinate is included already
      if (n.isIncidentEdgeInResult())
        continue;
      if (n.getEdges().getDegree() == 0 || opCode == EdgeOverlayOp.INTERSECTION) {

        /**
         * For nodes on edges, only INTERSECTION can result in edge nodes being included even
         * if none of their incident edges are included
         */
          Label label = n.getLabel();
          if (EdgeOverlayOp.isResultOfOp(label, opCode)) {
            filterCoveredNodeToPoint(n);
          }
      }
    }
    //System.out.println("connectedResultNodes collected = " + connectedResultNodes.size());
  }

  /**
   * Converts non-covered nodes to Point objects and adds them to the result.
   *
   * A node is covered if it is contained in another element Geometry
   * with higher dimension (e.g. a node point might be contained in a polygon,
   * in which case the point can be eliminated from the result).
   *
   * @param n the node to test
   */
  private void filterCoveredNodeToPoint(Node n)
  {
    Coordinate coord = n.getCoordinate();
    if (! op.isCoveredByLA(coord)) {
      Point pt = geometryFactory.createPoint(coord);
      resultPointList.add(pt);
    }
  }
}
