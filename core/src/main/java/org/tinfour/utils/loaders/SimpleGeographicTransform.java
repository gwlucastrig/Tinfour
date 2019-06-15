/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 12/2018  G. Lucas     Created  
 *
 * Notes:
 *   An alternate implemented applied cos(lat) as part of every
 * transformation. The current version uses a fixed cos(centerLat) 
 * throughout. The alterate would have computed:
 *      x = toRadians(longitude-centerLon)*adjEarthRadius*cos(latitude)
 * whereas the current computes
 *      x = toRadians(longitude-denterLon)*adjEarthRadius*cos(centerLat)
 * This approach simplifies the computation, though I was concerned about
 * the cost of accuracy. Contrary to my expectations, when I tested the two
 * methods against a standard, conformal map projection (TransverseMercator)
 * in the neighborhood of 45 degrees, the fixed cos(centerLat) version
 * actually performed better than the variable cos(lat) version 
 * for distances less than 60 kilometers (after which, the variable cos(lat)
 * version became more accurate.
 *   Here are some example errors computed as how much the position of
 * a transformed coordinate using the simple method deviated from the position
 * of a very accurate conformal map projection.  Errors were computed along
 * a diagonal at a 45 degree angle, taken at the indicated distances.
 * Out to 127 kilometers the error was less than 1 percent.
 *   test #  Dist (m))    abs error (m)  err (abs_err/dist)
 *   10000    14142.1     19.502911456   0.14 %
 *   20000    28284.3     31.921204594   0.11 %
 *   30000    42426.4     82.235027567   0.19 %
 *   40000    56568.5    173.312585820   0.31 %
 *   50000    70710.7    301.643437425   0.43 %
 *   60000    84852.8    466.502557162   0.55 %
 *   70000    98994.9    667.800070714   0.67 %
 *   80000   113137.1    905.619275750   0.80 %
 *   90000   127279.2   1180.104602553   0.93 %
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.loaders;

import org.tinfour.utils.LinearUnits;

/**
 * Provides a rudimentary implementation of a geographic transform. This
 * implementation is intended as a demonstration, and does not provide
 * sufficient accuracy for true Geographic Information Analysis applications.
 * <p>
 * This transform will map geographic coordinates to an planar coordinate system
 * that can be used for Tinfour processing. The transformation attempts to scale
 * the coordinates to an isotropic coordinate system with positions given in
 * meters. It is important to note that this transform is not a true map
 * projection. In particular, the transformation is not suitable for data
 * collected over areas larger than a few hundred kilometers and is not suitable
 * for coordinates in polar areas. Because it assumes a spherical Earth, it is
 * not suitable for high-precision analysis.
 */
public class SimpleGeographicTransform implements ICoordinateTransform {

  /**
   * Radius of the Earth at the equator, as specified by World Geodetic Survey
   * 1984 (WGS84).
   */
  public static final double earthSemiMajorAxis = 6378137.0;

  /**
   * The flattening ratio for the Earth, as specified by World Geodetic Survey
   * 1984.
   */
  public static final double earthFlattening = 1 / 298.257223560; // WGS-84
  /**
   * Radius of the Earth at the poles, as specified by World Geodetic Survey
   * 1984 (WGS84).
   */
  public static final double earthSemiMinorAxis = 6356752.3142;

  private final double centerLatitude;
  private final double centerLongitude;
  private final LinearUnits linearUnits;

  private final double xScale;
  private final double yScale;

  /**
   * Constructs a transform with the origin at the specified center latitude and
   * longitude. All coordinates are given in degrees. The maximum absolute
   * latitude is 87.5 degrees, but the accuracy of this transformation is
   * degraded at high latitudes.
   *
   * @param centerLatitude a valid latitude, in degrees, positive to the North,
   * negative to the South.
   * @param centerLongitude a valid longitude, in degrees, positive to the East,
   * negative to the West.
   * @param linearUnits the linear unit system to be used for the
   * transformation, most commonly meters or feet.
   */
  public SimpleGeographicTransform(
          double centerLatitude,
          double centerLongitude,
          LinearUnits linearUnits) {
    this.centerLatitude = centerLatitude;
    this.centerLongitude = centerLongitude;
    if (linearUnits == null) {
      this.linearUnits = LinearUnits.METERS;
    } else {
      this.linearUnits = linearUnits;
    }

    if (Math.abs(centerLatitude) > 87.5) {
      throw new IllegalArgumentException(
              "Latitude must be in range -87.5 to 87.5");
    }

    double a = earthSemiMajorAxis;
    if (linearUnits != null) {
      // linear units Meters and Unknown will map to metters.
      a = linearUnits.toMeters(a);
    }

    double phi = Math.toRadians(centerLatitude);
    double sinPhi = Math.sin(phi);
    double adjustment = 1 - earthFlattening * sinPhi * sinPhi;
    double adjRadius = adjustment * a;

    xScale = (Math.PI / 180) * adjRadius * Math.cos(phi);
    yScale = (Math.PI / 180) * adjRadius;
  }

  @Override
  public boolean forward(double xSource, double ySource, CoordinatePair c) {
    if (Math.abs(ySource) > 89) {
      return false;
    }
    c.x = (xSource - getCenterLongitude()) * xScale;
    c.y = (ySource - getCenterLatitude()) * yScale;
    return true;
  }

  @Override
  public boolean inverse(double xOutput, double yOutput, CoordinatePair c) {
    c.x = xOutput / xScale + getCenterLongitude();
    c.y = yOutput / yScale + getCenterLatitude();
    return true;
  }

  /**
   * Gets the center latitude used for this projection
   *
   * @return a value in degrees, in the range -87.5 to 87.5
   */
  public double getCenterLatitude() {
    return centerLatitude;
  }

  /**
   * Gets the center longitude used for this projection
   *
   * @return a value in degrees
   */
  public double getCenterLongitude() {
    return centerLongitude;
  }

  /**
   * Gets the linear units specified for this object
   *
   * @return a valid instance
   */
  public LinearUnits getLinearUnits() {
    return linearUnits;
  }
}
