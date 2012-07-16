package org.openplans.tools.tracking.impl.graph.paths;

import gov.sandia.cognition.math.LogMath;
import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;
import gov.sandia.cognition.util.DefaultPair;
import gov.sandia.cognition.util.Pair;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.openplans.tools.tracking.impl.Observation;
import org.openplans.tools.tracking.impl.VehicleState;
import org.openplans.tools.tracking.impl.graph.InferredEdge;
import org.openplans.tools.tracking.impl.statistics.StandardRoadTrackingFilter;
import org.openplans.tools.tracking.impl.statistics.StatisticsUtil;
import org.openplans.tools.tracking.impl.statistics.WrappedWeightedValue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Inferred paths are collections of PathEdges that track the distance traveled
 * and the direction (by sign)
 * 
 * @author bwillard
 * 
 */
public class InferredPath implements Comparable<InferredPath> {

  public static class EdgePredictiveResults {
    final WrappedWeightedValue<MultivariateGaussian> weightedPredictiveDist;
    final Double edgeMarginalLikelihood;

    public EdgePredictiveResults(
      WrappedWeightedValue<MultivariateGaussian> weightedPredictiveDist,
      Double edgeMarginalLikelihood) {
      this.weightedPredictiveDist = weightedPredictiveDist;
      this.edgeMarginalLikelihood = edgeMarginalLikelihood;
    }

    public Double getEdgeMarginalLikelihood() {
      return edgeMarginalLikelihood;
    }

    public WrappedWeightedValue<MultivariateGaussian>
        getWeightedPredictiveDist() {
      return weightedPredictiveDist;
    }

  }

  private final ImmutableList<PathEdge> edges;
  private final Double totalPathDistance;

  public List<Integer> edgeIds = Lists.newArrayList();

  /*
   * These are the edges used in path finding.
   */
  private InferredEdge startSearchEdge;

  private InferredEdge endSearchEdge;

  /*
   * Note: single edges are considered forward
   */
  private Boolean isBackward = null;

  private final Geometry geometry;

  private static InferredPath emptyPath = new InferredPath();

  private InferredPath() {
    this.edges = ImmutableList.of(PathEdge.getEmptyPathEdge());
    this.totalPathDistance = null;
    this.isBackward = null;
    this.geometry = null;
  }

  private InferredPath(ImmutableList<PathEdge> edges,
    boolean isBackward) {
    Preconditions.checkArgument(edges.size() > 0);
    this.edges = edges;
    this.isBackward = isBackward;

    PathEdge lastEdge = null;
    //    double absTotalDistance = 0d;
    final List<Geometry> geometries = Lists.newArrayList();
    for (final PathEdge edge : edges) {

      if (!edge.isEmptyEdge()) {
        if (isBackward) {
          assert (lastEdge == null || lastEdge.getInferredEdge()
              .getStartVertex()
              .equals(edge.getInferredEdge().getEndVertex()));
        } else {
          assert (lastEdge == null || lastEdge.getInferredEdge()
              .getEndVertex()
              .equals(edge.getInferredEdge().getStartVertex()));

        }

        geometries.add(isBackward ? edge.getInferredEdge()
            .getGeometry().reverse() : edge.getInferredEdge()
            .getGeometry());

        //        absTotalDistance += edge.getInferredEdge().getLength();
        edgeIds.add(edge.getInferredEdge().getEdgeId());
      }

      lastEdge = edge;
    }

    if (edges.size() > 1) {
      this.geometry =
          JTSFactoryFinder.getGeometryFactory().buildGeometry(
              geometries);
    } else {
      this.geometry = edges.get(0).getInferredEdge().getGeometry();
    }

    final double direction = isBackward ? -1d : 1d;
    this.totalPathDistance = direction * this.geometry.getLength();
  }

  private InferredPath(InferredEdge inferredEdge) {
    Preconditions.checkArgument(!inferredEdge.isEmptyEdge());
    this.edges = ImmutableList.of(PathEdge.getEdge(inferredEdge, 0d));
    this.totalPathDistance = inferredEdge.getLength();
    this.isBackward = Boolean.FALSE;
    this.edgeIds.add(inferredEdge.getEdgeId());
    this.geometry = inferredEdge.getGeometry();
  }

  private InferredPath(PathEdge edge) {
    Preconditions.checkArgument(!edge.isEmptyEdge());
    Preconditions.checkArgument(edge.getDistToStartOfEdge() == 0d);
    this.edges = ImmutableList.of(edge);
    this.totalPathDistance = edge.getInferredEdge().getLength();
    this.isBackward = Boolean.FALSE;
    this.edgeIds.add(edge.getInferredEdge().getEdgeId());
    this.geometry = edge.getInferredEdge().getGeometry();
  }

  private MultivariateGaussian
      calcBeliefPrediction(VehicleState state) {
    /*-
     * A prior predictive is created for every path, since, in some instances,
     * we need to project onto an edge and then predict movement.
     */
    final MultivariateGaussian beliefPrediction =
        state.getBelief().clone();
    final StandardRoadTrackingFilter filter =
        state.getMovementFilter();

    filter.predict(beliefPrediction, this.getEdges().get(0),
        PathEdge.getEdge(state.getInferredEdge()));

    /*
     * Convert to this path's direction
     */
    this.normalizeToPath(beliefPrediction);

    return beliefPrediction;

  }

  @Override
  public int compareTo(InferredPath o) {
    final CompareToBuilder comparator = new CompareToBuilder();
    comparator.append(this.edges.toArray(), o.edges.toArray());
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
    final InferredPath other = (InferredPath) obj;
    if (edges == null) {
      if (other.edges != null) {
        return false;
      }
    } else if (!edges.equals(other.edges)) {
      return false;
    }
    return true;
  }

  public PathEdge getEdgeForDistance(double distance, boolean clamp) {
    final double direction = Math.signum(totalPathDistance);
    if (direction * distance > Math.abs(totalPathDistance)) {
      return clamp ? Iterables.getLast(edges) : null;
    } else if (direction * distance < 0d) {
      return clamp ? Iterables.getFirst(edges, null) : null;
    }

    for (final PathEdge edge : edges) {
      if (edge.isOnEdge(distance))
        return edge;
    }

    assert false;

    return null;
  }

  /**
   * Returns the edge that covers the given distance, or null.
   * 
   * @param distance
   * @return
   */
  //  public PathEdge getEdgeAtDistance(double distance) {
  //    Preconditions.checkArgument(this != emptyPath);
  //    Preconditions.checkArgument(distance >= 0d);
  //    // TODO pre-compute/improve this
  //    for (final PathEdge edge : this.edges) {
  //      if (edge.getDistToStartOfEdge() <= distance
  //          && distance < edge.getDistToStartOfEdge()
  //              // FIXME if you're going to use this, set the direction! 
  //              + edge.getInferredEdge().getLength()) {
  //        return edge;
  //      }
  //    }
  //
  //    return null;
  //  }

  public ImmutableList<PathEdge> getEdges() {
    return edges;
  }

  public InferredEdge getEndEdge() {
    return endSearchEdge;
  }

  public Geometry getGeometry() {
    return this.geometry;
  }

  /**
   * XXX: the state must have a prior predictive mean.
   * 
   * @param obs
   * @param state
   * @param edgeToPreBeliefAndLogLik
   * @return
   */
  public
      InferredPathEntry
      getPredictiveLogLikelihood(
        Observation obs,
        VehicleState state,
        Map<Pair<PathEdge, Boolean>, EdgePredictiveResults> edgeToPreBeliefAndLogLik) {

    /*
     * We allow transitions from off-road onto a path, and vice-versa.  
     * Otherwise, we require that the first edge of the path is the edge of the
     * current state.
     */
    Preconditions.checkArgument(state.getInferredEdge().isEmptyEdge()
        || this.isEmptyPath()
        || state.getInferredEdge().equals(
            Iterables.getFirst(this.getEdges(), null)
                .getInferredEdge()));
    /*
     * Lazily load this so we don't repeat work for dups.
     */
    MultivariateGaussian beliefPrediction = null;
    PathEdge prevEdge = PathEdge.getEdge(state.getInferredEdge());
    final StandardRoadTrackingFilter filter =
        state.getMovementFilter();

    double pathLogLik = Double.NEGATIVE_INFINITY;
    double edgePredMarginalTotalLik = Double.NEGATIVE_INFINITY;

    final List<WrappedWeightedValue<PathEdge>> weightedPathEdges =
        Lists.newArrayList();
    for (final PathEdge edge : this.getEdges()) {

      final Pair<PathEdge, Boolean> directionalEdge =
          new DefaultPair<PathEdge, Boolean>(edge, this.isBackward);
      final double localLogLik;
      final double edgePredMarginalLogLik;
      final PathEdge edgeOfLocPrediction;

      if (edgeToPreBeliefAndLogLik.containsKey(edge)) {
        localLogLik =
            edgeToPreBeliefAndLogLik.get(directionalEdge)
                .getWeightedPredictiveDist().getWeight();
        edgePredMarginalLogLik =
            edgeToPreBeliefAndLogLik.get(edge)
                .getEdgeMarginalLikelihood();
      } else {
        /*
         * If we're going off-road, then pass the edge we used to be on.
         */
        if (beliefPrediction == null) {
          beliefPrediction = this.calcBeliefPrediction(state);
        }

        final MultivariateGaussian locationPrediction =
            beliefPrediction.clone();
        if (edge.isEmptyEdge()) {
          // TODO meh?
          edgePredMarginalLogLik = 0d;
          edgeOfLocPrediction = edge;
        } else {
          predict(locationPrediction, obs, edge);
          edgePredMarginalLogLik =
              marginalPredictiveLogLikelihood(edge, beliefPrediction);
          /*
           * The predicted location for the stretch of a given edge does not
           * necessarily end up on that edge.  The distribution corresponding
           * to the approximate stretch of an edge will simply adjust our general
           * movement prediction (in a direction/velocity that brings it closer
           * to the considered edge). 
           * This being the case, we must find out exactly which edge it is on so
           * that we can compute the ground location properly.
           */
          edgeOfLocPrediction =
              this.getEdgeForDistance(locationPrediction.getMean()
                  .getElement(0), false);

          /*
           * If the result is not on the path, then skip this edge
           */
          if (edgeOfLocPrediction == null)
            continue;
        }

        final double edgePredTransLogLik =
            state.getEdgeTransitionDist().predictiveLogLikelihood(
                prevEdge.getInferredEdge(), edge.getInferredEdge());

        final double localPosVelPredLogLik =
            filter.logLikelihood(obs.getProjectedPoint(),
                locationPrediction, edgeOfLocPrediction);

        assert !Double.isNaN(edgePredMarginalLogLik);
        assert !Double.isNaN(edgePredTransLogLik);
        assert !Double.isNaN(localPosVelPredLogLik);

        localLogLik =
            edgePredMarginalLogLik + edgePredTransLogLik
                + localPosVelPredLogLik;

        /*
         * If we hit results with numerically zero likelihood, then
         * stop, because it's not going to get better from here.
         */
        if (Double.isInfinite(localLogLik))
          break;

        /*
         * We're only going to deal with the terminating edge for now.
         */
        edgeToPreBeliefAndLogLik.put(directionalEdge,
            new EdgePredictiveResults(
                new WrappedWeightedValue<MultivariateGaussian>(
                    locationPrediction.clone(), localLogLik),
                edgePredMarginalLogLik));
      }

      /*
       * Add likelihood for this edge to the path total
       */
      weightedPathEdges.add(new WrappedWeightedValue<PathEdge>(edge,
          localLogLik));
      pathLogLik = LogMath.add(pathLogLik, localLogLik);
      edgePredMarginalTotalLik =
          LogMath.add(edgePredMarginalTotalLik,
              edgePredMarginalLogLik);

      assert !Double.isNaN(edgePredMarginalLogLik);

      prevEdge = edge;
    }

    // TODO FIXME debug. remove.
    if (Double.isInfinite(pathLogLik)
        || Double.isInfinite(edgePredMarginalTotalLik))
      return null;

    /*
     * Normalize with respect to the edges' predictive marginal 
     */
    if (!this.isEmptyPath())
      pathLogLik = pathLogLik - edgePredMarginalTotalLik;

    assert !Double.isNaN(pathLogLik);

    return new InferredPathEntry(this, edgeToPreBeliefAndLogLik,
        filter, weightedPathEdges, pathLogLik);
  }

  public InferredEdge getStartEdge() {
    return startSearchEdge;
  }

  public Double getTotalPathDistance() {
    return totalPathDistance;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
        prime * result + ((edges == null) ? 0 : edges.hashCode());
    return result;
  }

  public Boolean isBackward() {
    return isBackward;
  }

  public boolean isEmptyPath() {
    return this == emptyPath;
  }

  public boolean isOnPath(double distance) {
    final double direction = Math.signum(totalPathDistance);
    if (direction * distance > Math.abs(totalPathDistance)) {
      return false;
    } else if (direction * distance < 0d) {
      return false;
    }

    return true;
  }

  public double marginalPredictiveLogLikelihood(PathEdge edge,
    MultivariateGaussian beliefPrediction) {
    Preconditions.checkArgument(beliefPrediction
        .getInputDimensionality() == 2);
    final Matrix Or = StandardRoadTrackingFilter.getOr();
    final double stdDev =
        Math.sqrt(Or.times(beliefPrediction.getCovariance())
            .times(Or.transpose()).getElement(0, 0));
    final double mean =
        Or.times(beliefPrediction.getMean()).getElement(0);
    final double direction = Math.signum(totalPathDistance);
    final double distToEndOfEdge =
        direction * edge.getInferredEdge().getLength()
            + edge.getDistToStartOfEdge();
    final double startDistance =
        direction > 0d ? edge.getDistToStartOfEdge()
            : distToEndOfEdge;
    final double endDistance =
        direction > 0d ? distToEndOfEdge : edge
            .getDistToStartOfEdge();

    // FIXME use actual log calculations
    final double result =
        LogMath
            .subtract(StatisticsUtil.normalCdf(endDistance, mean,
                stdDev, true), StatisticsUtil.normalCdf(
                startDistance, mean, stdDev, true));

    return result;
  }

  /**
   * Converts location component of the mean to a location on this path, if any.
   * For example, negative positions on the current edge are turned into
   * positive positions, and if they extend past the edge in the negative
   * direction, then the remaining negative distance is used.
   * 
   * @param beliefPrediction
   */
  private void normalizeToPath(MultivariateGaussian beliefPrediction) {
    final double currentLocation =
        beliefPrediction.getMean().getElement(0);
    if (isBackward == Boolean.TRUE && currentLocation > 0d) {
      final double newLocation =
          currentLocation
              - edges.get(0).getInferredEdge().getLength();
      beliefPrediction.getMean().setElement(0, newLocation);
    } else if (isBackward == Boolean.FALSE && currentLocation < 0d) {
      final double newLocation =
          currentLocation
              + edges.get(0).getInferredEdge().getLength();
      beliefPrediction.getMean().setElement(0, newLocation);
    }
  }

  /**
   * This method truncates the given belief over the interval defined by this
   * edge.
   * 
   * @param belief
   * @param edge2
   */
  public void predict(MultivariateGaussian belief, Observation obs,
    PathEdge edge) {

    Preconditions.checkArgument(belief.getInputDimensionality() == 2);
    Preconditions.checkArgument(edges.contains(edge));

    /*-
     * TODO really, this should just be the truncated/conditional
     * mean and covariance for the given interval/edge
     */
    final Matrix Or = StandardRoadTrackingFilter.getOr();
    final double S =
        Or.times(belief.getCovariance()).times(Or.transpose())
            .getElement(0, 0)
            // + 1d;
            + Math
                .pow(
                    edge.getInferredEdge().getLength()
                        / Math.sqrt(12), 2);
    final Matrix W =
        belief.getCovariance().times(Or.transpose()).scale(1 / S);
    final Matrix R =
        belief.getCovariance().minus(W.times(W.transpose()).scale(S));

    final double mean;
    //    if (edge.getDistToStartOfEdge() == 0d) {
    //      /*
    //       * When this edge is the entire path, choose the midpoint to be
    //       * the same direction as the movement.
    //       */
    //      final double direction = belief.getMean().getElement(0) >= 0d ? 1d
    //          : -1d;
    //      mean = direction * edge.getInferredEdge().getLength() / 2d;
    //    } else {
    final double direction = Math.signum(totalPathDistance);
    mean =
        (edge.getDistToStartOfEdge() + (edge.getDistToStartOfEdge() + direction
            * edge.getInferredEdge().getLength())) / 2d;

    //    }

    /*
     * The mean can be the center-length of the geometry, or something more
     * specific, like the snapped location?
     */
    // final LocationIndexedLine locIdxLine = isPositive ?
    // edge.getPosLocationIndexedLine()
    // : edge.getNegLocationIndexedLine();
    // final LinearLocation loc = locIdxLine.project(
    // GeoUtils.reverseCoordinates(obs.getObsCoords()));
    // final LengthLocationMap lengthLocLine = isPositive ?
    // edge.getPosLengthLocationMap()
    // : edge.getNegLengthLocationMap();
    // final double mean = (isPositive ? 1d : -1d)
    // * GeoUtils.getAngleDegreesInMeters(lengthLocLine.getLength(loc)) +
    // this.getDistToStartOfEdge();

    final double e = mean - Or.times(belief.getMean()).getElement(0);
    final Vector a = belief.getMean().plus(W.getColumn(0).scale(e));

    //    final double direction = distToStartOfEdge > 0d ? 1d : -1d;
    //    final double distToEndOfEdge = direction * edge.getLength() + this.distToStartOfEdge;
    //    final double startDistance = direction > 0d ? this.distToStartOfEdge : distToEndOfEdge; 
    //    final double endDistance = direction > 0d ? distToEndOfEdge : this.distToStartOfEdge;   
    //    final double stdDev = Math.sqrt(Or.times(R).times(Or.transpose()).getElement(0, 0));
    //
    //    final double tmean = a.getElement(0) 
    //     + stdDev * (UnivariateGaussian.PDF.evaluate(startDistance, a.getElement(0), stdDev)
    //       - UnivariateGaussian.PDF.evaluate(endDistance, a.getElement(0), stdDev))
    //       /(UnivariateGaussian.CDF.evaluate(endDistance, a.getElement(0), stdDev)
    //           - UnivariateGaussian.CDF.evaluate(startDistance, a.getElement(0), stdDev));

    belief.setMean(a);
    belief.setCovariance(R);
  }

  public void setEndSearchEdge(InferredEdge endEdge) {
    this.endSearchEdge = endEdge;
  }

  public void setIsBackward(Boolean isBackward) {
    this.isBackward = isBackward;
  }

  public void setStartSearchEdge(InferredEdge startEdge) {
    this.startSearchEdge = startEdge;
  }

  @Override
  public String toString() {
    if (this == emptyPath)
      return "InferredPath [empty path]";
    else
      return "InferredPath [edges=" + edgeIds
          + ", totalPathDistance=" + totalPathDistance + "]";
  }

  public static InferredPath getEmptyPath() {
    return emptyPath;
  }

  public static InferredPath
      getInferredPath(InferredEdge inferredEdge) {
    if (inferredEdge.isEmptyEdge())
      return emptyPath;
    else
      return new InferredPath(inferredEdge);
  }

  public static InferredPath getInferredPath(List<PathEdge> edges,
    boolean isBackward) {
    if (edges.size() == 1) {
      final PathEdge edge = Iterables.getOnlyElement(edges);
      if (edge.isEmptyEdge())
        return emptyPath;
      else
        return new InferredPath(edge);
    }
    return new InferredPath(ImmutableList.copyOf(edges), isBackward);
  }

  public static InferredPath getInferredPath(PathEdge pathEdge) {
    if (pathEdge.isEmptyEdge())
      return emptyPath;
    else
      return new InferredPath(pathEdge);
  }

}
