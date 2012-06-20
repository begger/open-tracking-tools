package org.openplans.tools.tracking.impl;

import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.statistics.bayesian.conjugate.UnivariateGaussianMeanVarianceBayesianEstimator;
import gov.sandia.cognition.statistics.distribution.NormalInverseGammaDistribution;

import java.util.List;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.openplans.tools.tracking.impl.InferredEdge;
import org.openplans.tools.tracking.impl.util.GeoUtils;
import org.openplans.tools.tracking.impl.util.OtpGraph;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.linearref.LengthLocationMap;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

public class InferredEdge implements
    Comparable<InferredEdge> {

  private final Integer edgeId;
  private final Vertex startVertex;
  private final Vertex endVertex;
  private final Vector endPoint;
  private final Vector startPoint;
  private final double length;
  private final NormalInverseGammaDistribution velocityPrecisionDist;
  private final UnivariateGaussianMeanVarianceBayesianEstimator velocityEstimator;
  private final InferredGraph graph;

  private final Edge edge;

  private final Geometry posGeometry;
  private final LocationIndexedLine posLocationIndexedLine;
  private final LengthIndexedLine posLengthIndexedLine;
  private final LengthLocationMap posLengthLocationMap;

  // FIXME remove this negative junk; use turns.
  private final Geometry negGeometry;
  private final LocationIndexedLine negLocationIndexedLine;
  private final LengthIndexedLine negLengthIndexedLine;
  private final LengthLocationMap negLengthLocationMap;

  /*
   * This is the empty edge, which stands for free movement
   */
  private final static InferredEdge emptyEdge = new InferredEdge();

  public static InferredEdge getEmptyEdge() {
    return InferredEdge.emptyEdge;
  }

  private InferredEdge() {
    this.edgeId = null;
    this.endPoint = null;
    this.startPoint = null;
    this.length = 0;
    this.velocityEstimator = null;
    this.velocityPrecisionDist = null;
    this.startVertex = null;
    this.endVertex = null;
    this.graph = null;

    this.edge = null;

    this.posGeometry = null;
    this.posLocationIndexedLine = null;
    this.posLengthIndexedLine = null;
    this.posLengthLocationMap = null;

    this.negGeometry = null;
    this.negLocationIndexedLine = null;
    this.negLengthIndexedLine = null;
    this.negLengthLocationMap = null;
  }

  InferredEdge(Edge edge, Integer edgeId,
    InferredGraph graph) {
    this.graph = graph;
    this.edgeId = edgeId;
    this.edge = edge;

    /*
     * Warning: this geometry is in lon/lat and may contain more than one
     * straight line.
     */
    this.posGeometry = edge.getGeometry();
    this.negGeometry = edge.getGeometry().reverse();

    this.posLocationIndexedLine = new LocationIndexedLine(
        posGeometry);
    this.posLengthIndexedLine = new LengthIndexedLine(posGeometry);
    this.posLengthLocationMap = new LengthLocationMap(posGeometry);

    this.negLocationIndexedLine = new LocationIndexedLine(
        negGeometry);
    this.negLengthIndexedLine = new LengthIndexedLine(negGeometry);
    this.negLengthLocationMap = new LengthLocationMap(negGeometry);

    this.startVertex = edge.getFromVertex();
    this.endVertex = edge.getToVertex();

    final Coordinate startPoint = this.posLocationIndexedLine
        .extractPoint(this.posLocationIndexedLine.getStartIndex());
    /*
     * We need to flip these coords around to get lat/lon.
     */
    final Coordinate startPointCoord = GeoUtils
        .convertToEuclidean(new Coordinate(
            startPoint.y, startPoint.x));
    this.startPoint = VectorFactory.getDefault().createVector2D(
        startPointCoord.x, startPointCoord.y);

    final Coordinate endPoint = this.posLocationIndexedLine
        .extractPoint(this.posLocationIndexedLine.getEndIndex());
    final Coordinate endPointCoord = GeoUtils
        .convertToEuclidean(new Coordinate(endPoint.y, endPoint.x));
    this.endPoint = VectorFactory.getDefault().createVector2D(
        endPointCoord.x, endPointCoord.y);

    this.length = GeoUtils.getAngleDegreesInMeters(posGeometry
        .getLength());

    this.velocityPrecisionDist =
    // ~4.4 m/s, std. dev ~ 30 m/s, Gamma with exp. value = 30 m/s
    // TODO perhaps variance of velocity should be in m/s^2. yeah...
    new NormalInverseGammaDistribution(
        4.4d, 1d / Math.pow(30d, 2d), 1d / Math.pow(30d, 2d) + 1d,
        Math.pow(30d, 2d));
    this.velocityEstimator = new UnivariateGaussianMeanVarianceBayesianEstimator(
        velocityPrecisionDist);
  }

  @Override
  public int compareTo(InferredEdge o) {
    final CompareToBuilder comparator = new CompareToBuilder();
    comparator.append(
        this.endVertex.getLabel(), o.endVertex.getLabel());
    comparator.append(
        this.startVertex.getLabel(), o.startVertex.getLabel());

    return comparator.toComparison();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final InferredEdge other = (InferredEdge) obj;
    if (endVertex == null) {
      if (other.endVertex != null) {
        return false;
      }
    } else if (!endVertex.equals(other.endVertex)) {
      return false;
    }
    if (startVertex == null) {
      if (other.startVertex != null) {
        return false;
      }
    } else if (!startVertex.equals(other.startVertex)) {
      return false;
    }
    return true;
  }

  public Coordinate getCenterPointCoord() {
    return this.posGeometry.getCentroid().getCoordinate();
  }
  
  public boolean isEmptyEdge() {
    return this == emptyEdge;
  }

  public Coordinate getCoordOnEdge(Vector obsPoint) {
    if (this == InferredEdge.emptyEdge)
      return null;
    final Coordinate revObsPoint = new Coordinate(
        obsPoint.getElement(1), obsPoint.getElement(0));
    final LinearLocation here = posLocationIndexedLine
        .project(revObsPoint);
    final Coordinate pointOnLine = posLocationIndexedLine
        .extractPoint(here);
    final Coordinate revOnLine = new Coordinate(
        pointOnLine.y, pointOnLine.x);
    return revOnLine;
  }

  public Edge getEdge() {
    return this.edge;
  }

  public Integer getEdgeId() {
    return edgeId;
  }

  public Vector getEndPoint() {
    return this.endPoint;
  }

  public Vertex getEndVertex() {
    return endVertex;
  }

  public InferredGraph getGraph() {
    return graph;
  }

  /**
   * This returns a list of edges that are incoming, wrt the direction of this
   * edge, and that are reachable from this edge (e.g. not one way in the
   * direction of this edge).
   * 
   * @return
   */
  public List<InferredEdge> getIncomingTransferableEdges() {

    final List<InferredEdge> result = Lists.newArrayList();
    this.graph.getNarratedGraph();
    for (final Edge edge : OtpGraph
        .filterForStreetEdges(this.startVertex.getIncoming())) {
      if (graph.getGraph().getIdForEdge(edge) != null)
        result.add(graph.getInferredEdge(edge));
    }

    return result;
  }

  public double getLength() {
    return length;
  }

  public Geometry getNegGeometry() {
    return negGeometry;
  }

  public LengthIndexedLine getNegLengthIndexedLine() {
    return negLengthIndexedLine;
  }

  public LengthLocationMap getNegLengthLocationMap() {
    return negLengthLocationMap;
  }

  public LocationIndexedLine getNegLocationIndexedLine() {
    return negLocationIndexedLine;
  }

  /**
   * This returns a list of edges that are outgoing, wrt the direction of this
   * edge, and that are reachable from this edge (e.g. not one way against the
   * direction of this edge).
   * 
   * @return
   */
  public List<InferredEdge> getOutgoingTransferableEdges() {
    final List<InferredEdge> result = Lists.newArrayList();
    for (final Edge edge : this.endVertex.getOutgoingStreetEdges()) {
      result.add(graph.getInferredEdge(edge));
    }

    return result;
  }

  /**
   * Get the snapped location in projected/euclidean coordinates for the given
   * obsPoint (in lat/lon).
   * 
   * @param obsPoint
   * @return
   */
  public Vector getPointOnEdge(Coordinate obsPoint) {
    if (this == InferredEdge.emptyEdge)
      return null;
    final Coordinate revObsPoint = new Coordinate(
        obsPoint.y, obsPoint.x);
    final LinearLocation here = posLocationIndexedLine
        .project(revObsPoint);
    final Coordinate pointOnLine = posLocationIndexedLine
        .extractPoint(here);
    final Coordinate revOnLine = new Coordinate(
        pointOnLine.y, pointOnLine.x);
    final Coordinate projPointOnLine = GeoUtils
        .convertToEuclidean(revOnLine);
    return VectorFactory.getDefault().createVector2D(
        projPointOnLine.x, projPointOnLine.y);
  }

  public Geometry getPosGeometry() {
    return posGeometry;
  }

  public LengthIndexedLine getPosLengthIndexedLine() {
    return posLengthIndexedLine;
  }

  public LengthLocationMap getPosLengthLocationMap() {
    return posLengthLocationMap;
  }

  public LocationIndexedLine getPosLocationIndexedLine() {
    return posLocationIndexedLine;
  }

  public Vector getStartPoint() {
    return startPoint;
  }

  public Vertex getStartVertex() {
    return startVertex;
  }

  public UnivariateGaussianMeanVarianceBayesianEstimator getVelocityEstimator() {
    return velocityEstimator;
  }

  public NormalInverseGammaDistribution getVelocityPrecisionDist() {
    return velocityPrecisionDist;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((endVertex == null) ? 0 : endVertex.hashCode());
    result = prime * result
        + ((startVertex == null) ? 0 : startVertex.hashCode());
    return result;
  }

  @Override
  public String toString() {
    if (this == emptyEdge)
      return "InferredEdge [empty edge]";
    else
      return "InferredEdge [edgeId=" + edgeId + ", length="
          + length + "]";
  }

}