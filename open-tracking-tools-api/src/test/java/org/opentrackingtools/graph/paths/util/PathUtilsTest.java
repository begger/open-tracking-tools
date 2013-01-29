package org.opentrackingtools.graph.paths.util;


import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;

import static org.mockito.Mockito.mock;
import gov.sandia.cognition.math.matrix.MatrixFactory;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;

import org.opentrackingtools.graph.otp.impl.OtpGraph;
import org.opentrackingtools.graph.paths.InferredPath;
import org.opentrackingtools.graph.paths.edges.PathEdge;
import org.opentrackingtools.graph.paths.impl.TrackingTestUtils;

import com.google.common.collect.Iterables;
import com.vividsolutions.jts.geom.Coordinate;

public class PathUtilsTest {
  
  @DataProvider
  private static final Object[][] stateData() {
   
    return new Object[][] {
    {VectorFactory.getDefault().copyArray(
            new double[] {-9.999992857195395E7, 0.0, -13.537576549229717, 0.0}),
            1e8,
      VectorFactory.getDefault().copyArray(new double[] {-9.999992857195395E7 - 1e8, 0.0}),
      VectorFactory.getDefault().copyArray(new double[] {-9.999992857195395E7, 0d, 0d, 0.0})
      },
    {VectorFactory.getDefault().copyArray(
            new double[] {-9.999992857195395E1, 0.0, -13.537576549229717, 0.0}), 
            1e2,
      VectorFactory.getDefault().copyArray(new double[] {-9.999992857195395E1 - 1e2, 0.0}),
      VectorFactory.getDefault().copyArray(new double[] {-9.999992857195395E1, 0d, 0d, 0.0})
      }
    };
  }

  @Test(dataProvider="stateData")
  public void testProjection(Vector from, double length, Vector roadTo, Vector groundTo) {
    
    OtpGraph graph = mock(OtpGraph.class);
    final InferredPath path =
        TrackingTestUtils.makeTmpPath(graph, true,
            new Coordinate(-length, 0d),
            new Coordinate(0d, 0d),
            new Coordinate(length, 0d));
    
    MultivariateGaussian belief = new MultivariateGaussian(
        from, MatrixFactory.getDefault().copyArray(
                new double[][] {
                    {91.64766085510277, 0.0, -10.790534809853966, 0.0},
                    {0.0, 0.0, 0.0, 0.0},
                    {-10.790534809853973, 0.0, 110.08645314343424, 0.0},
                    {0.0, 0.0, 0.0, 0.0}
                })
            );
    
    final PathEdge pathEdge = Iterables.getLast(path.getPathEdges());
    MultivariateGaussian projBelief = PathUtils.getRoadBeliefFromGround(belief, 
        path.getGeometry(), path.isBackward(), 
        pathEdge.isBackward() ? pathEdge.getGeometry().reverse() : pathEdge.getGeometry(), 
        pathEdge.getDistToStartOfEdge(), true);
    
   
    ArrayAsserts.assertArrayEquals("convert to road", 
        roadTo.toArray(), 
        projBelief.getMean().toArray(), 1e-1);
    
    PathUtils.convertToGroundBelief(projBelief, pathEdge, false, true);
    
    ArrayAsserts.assertArrayEquals("convert back to ground", 
        groundTo.toArray(), 
        projBelief.getMean().toArray(), 1e-1);
  }
  

}
