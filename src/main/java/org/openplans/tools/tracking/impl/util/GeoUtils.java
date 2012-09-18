package org.openplans.tools.tracking.impl.util;

import java.awt.geom.Point2D;

import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.projection.PointOutsideEnvelopeException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.ProjectionFactory;


public class GeoUtils {

  public static class GeoSetup {

    final CoordinateReferenceSystem mapCRS;
    final CoordinateReferenceSystem dataCRS;
    final MathTransform transform;

    public GeoSetup(CoordinateReferenceSystem mapCRS,
      CoordinateReferenceSystem dataCRS, MathTransform transform) {
      this.mapCRS = mapCRS;
      this.dataCRS = dataCRS;
      this.transform = transform;
    }

    public CoordinateReferenceSystem getLatLonCRS() {
      return dataCRS;
    }

    public CoordinateReferenceSystem getProjCRS() {
      return mapCRS;
    }

    public MathTransform getTransform() {
      return transform;
    }

  }

  public static ThreadLocal<GeoSetup> geoData =
      new ThreadLocal<GeoSetup>() {

        @Override
        public GeoSetup get() {
          return super.get();
        }

        @Override
        protected GeoSetup initialValue() {
          System.setProperty("org.geotools.referencing.forceXY",
              "true");
          try {
            // EPSG:4326 -> WGS84
            // EPSG:3785 is web mercator
            final String googleWebMercatorCode = "EPSG:4326";

            // Projected CRS
            // CRS code: 3785
            final String cartesianCode = "EPSG:4499";

            // UTM zone 51N
            // final String cartesianCode = "EPSG:3829";

            final CRSAuthorityFactory crsAuthorityFactory =
                CRS.getAuthorityFactory(true);

            final CoordinateReferenceSystem mapCRS =
                crsAuthorityFactory
                    .createCoordinateReferenceSystem(googleWebMercatorCode);

            final CoordinateReferenceSystem dataCRS =
                crsAuthorityFactory
                    .createCoordinateReferenceSystem(cartesianCode);

            final boolean lenient = true; // allow for some error due to different
                                          // datums
            final MathTransform transform =
                CRS.findMathTransform(mapCRS, dataCRS, lenient);

            return new GeoSetup(mapCRS, dataCRS, transform);

          } catch (final Exception e) {
            e.printStackTrace();
          }

          return null;
        }

      };

  public static ProjectedCoordinate convertToEuclidean(Coordinate latlon) {

    String[] spec = new String[5];
    final int utmZone = GeoUtils.getUTMZoneForLongitude(latlon.y);
    spec[0] = "+proj=utm";
    spec[1] = "+zone=" + utmZone;
    spec[2] = "+ellps=clrk66";
    spec[3] = "+units=m";
    spec[4] = "+no_defs";
    Projection projection = ProjectionFactory.fromPROJ4Specification(spec);
    Point2D.Double from = new Point2D.Double(latlon.y, latlon.x);
    Point2D.Double to = new Point2D.Double();
    to = projection.transform(from, to);

    return new ProjectedCoordinate(projection, utmZone, to);
  }

  public static ProjectedCoordinate convertToEuclidean(Vector vec) {
    return convertToEuclidean(new Coordinate(vec.getElement(0),
        vec.getElement(1)));
  }

  public static Coordinate convertToLatLon(ProjectedCoordinate xy) {
    final Coordinate converted = new Coordinate();
    Point2D.Double from = new Point2D.Double(xy.x, xy.y);
    Point2D.Double to = new Point2D.Double();
    to = xy.getProjection().inverseTransform(from, to);

    return new Coordinate(converted.y, converted.x);
  }

  public static Coordinate convertToLatLon(Point2D.Double point, int zone) {
    String[] spec = new String[5];
    spec[0] = "+proj=utm";
    spec[1] = "+zone=" + zone;
    spec[2] = "+ellps=clrk66";
    spec[3] = "+units=m";
    spec[4] = "+no_defs";
    Projection projection = ProjectionFactory.fromPROJ4Specification(spec);
    return convertToLatLon(new ProjectedCoordinate(projection, zone, point));
  }
  
  public static Coordinate convertToLatLon(Vector vec, ProjectedCoordinate projCoord) {
    final Point2D.Double point = new Point2D.Double(vec.getElement(0),
        vec.getElement(1));
    return convertToLatLon(new ProjectedCoordinate(projCoord.getProjection(), 
        projCoord.getUtmZone(), point));
  }
  
  public static Coordinate convertToLatLon(Vector vec, Projection projection, int zone) {
    final Point2D.Double point = new Point2D.Double(vec.getElement(0),
        vec.getElement(1));
    return convertToLatLon(new ProjectedCoordinate(projection, zone, point));
  }
  
  /*
   * Taken from OneBusAway's UTMLibrary class
   */
  public static int getUTMZoneForLongitude(double lon) {

    if (lon < -180 || lon > 180)
      throw new IllegalArgumentException(
          "Coordinates not within UTM zone limits");

    int lonZone = (int) ((lon + 180) / 6);

    if (lonZone == 60)
      lonZone--;
    return lonZone + 1;
  }
  
  public static Object getCoordinates(Vector meanLocation) {
    return new Coordinate(meanLocation.getElement(0),
        meanLocation.getElement(1));
  }

  public static MathTransform getCRSTransform() {
    return geoData.get().getTransform();
  }

  public static Vector getEuclideanVectorFromLatLon(
    Coordinate coordinate) {
    final Coordinate resCoord = convertToEuclidean(coordinate);
    return VectorFactory.getDefault().createVector2D(resCoord.x,
        resCoord.y);
  }

  public static CoordinateReferenceSystem getLatLonCRS() {
    return geoData.get().getLatLonCRS();
  }

  public static double getMetersInAngleDegrees(double distance) {
    return distance / (Math.PI / 180d) / 6378137d;
  }

  public static CoordinateReferenceSystem getProjCRS() {
    return geoData.get().getProjCRS();
  }

  public static Vector getVector(Coordinate coord) {
    return VectorFactory.getDefault()
        .createVector2D(coord.x, coord.y);
  }

  public static Point lonlatToGeometry(Coordinate lonlat) {
    return JTS
        .toGeometry(JTS.toDirectPosition(lonlat, getLatLonCRS())
            .getDirectPosition());
  }

  public static Coordinate makeCoordinate(Vector vec) {
    return new Coordinate(vec.getElement(0), vec.getElement(1));
  }

  public static Coordinate reverseCoordinates(Coordinate startCoord) {
    return new Coordinate(startCoord.y, startCoord.x);
  }
}
