/*
 * Copyright 2025 Gary W. Lucas.
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------   ---------   -------------------------------------------------
 * 03/2025  G. Lucas    Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.alphashape;

/**
 * Provides definitions to be used for alpha-shape construction
 * or other applications for a pair of circles of radius r
 * that intersects the endpoints of a line segment.
 */
public class AlphaCircle {

  /**
   * Radius used to construct the circles
   */
  public final double r;
  /**
   * X coordinate for a circle center that lies to the left of the defining
   * segment
   */
  public final double centerX0;
  /**
   * Y coordinate for a circle center that lies to the left of the defining
   * segment
   */
  public final double centerY0;
  /**
   * X coordinate for a circle center that lies to the right of the defining
   * segment
   */
  public final double centerX1;
  /**
   * Y coordinate for a circle center that lies to the right of the defining
   * segment
   */
  public final double centerY1;
  /**
   * Indicates that the alpha circles are properly defined
   */
  public final boolean valid;
  /**
   * Indicates that the input segment was of length greater than the radius
   */
  public final boolean partial;

  private final double r2test;

  /**
   * Construct an object giving elements for two circles of radius r that
   * intersect the specified line-segment endpoints. The primary computation
   * yields the
   * coordinates for two points giving the centers of the circles.
   *
   * @param r the radius of the two circles
   * @param x0 X coordinate of the first point
   * @param y0 Y coordinate of the first point
   * @param x1 X coordinate of the second point
   * @param y1 Y coordinate of the second point
   */
  public AlphaCircle(double r, double x0, double y0, double x1, double y1) {


    this.r = r;
    this.r2test = r * r;

    double h = (x1 - x0);
    double k = (y1 - y0);

    double seglen = Math.sqrt(h * h + k * k);
    if (seglen > 2 * r) {
      // The line segment is longer than the diameter.  Create a circle
      // of radius r centered on the line segment.  Mark it as a partial.
      centerX0 = (x0 + x1) / 2.0;
      centerY0 = (y0 + y1) / 2.0;
      centerX1 = centerX0;
      centerY1 = centerY0;
      valid = true;
      partial = true;
      return;
    }
    if (seglen / r < 1.0e-9) {
      // The line segment is of length close to zero (when scaled
      // for whatever is the magnitude of the radius).  Create a circle
      // of radius r centered on the line segment.  Mark partial as false.
      centerX0 = (x0 + x1) / 2.0;
      centerY0 = (y0 + y1) / 2.0;
      centerX1 = centerX0;
      centerY1 = centerY0;
      valid = true;
      partial = false;
      return;
    }

    partial = false;

    // A pair of points in geospatial analysis aften have coordinates with a large
    // magnitude compared to the spacing between the two points.
    // To avoid loss of precision in calculations, we remap the points
    //    (x0, y0) and (x1,y1)
    // so that (x0, y0) can be treated as the origin and we have points
    //    (0, 0, and (h, k)
    // with
    //    h = x1-x0 and k = y1-y0
    //
    // This approach also reduces the number of terms in the calculations.
    // We then seek solutions for the center of the circle at (x,y)
    //     x^2    + y^2     = r^2
    //    (x-h)^2 + (y-k)^2 = r^2
    //
    // One other issue we face is that for some of our calculations,
    // either h or k must go in the demoninator.  So we need to avoid
    // cases that would lead to division by zero (or nearly zero). The logic
    // above has already excluded the case where both h and k are zero.
    // So, in the following calculations, we branch based on whether the
    // delta x (h) or delta y (k) is of larger magnitude.
    if (Math.abs(k) >= Math.abs(h)) {
      // Here we compute x first and y as a function of x with  y = s + t*x
      double s = (h * h + k * k) / (2 * k);
      double t = -h / k;

      double a = (1 + t * t);
      double b = 2 * s * t;
      double c = s * s - r * r;

      double d = b * b - 4 * a * c;
      if (d < 0) {
        if (d > -1.0e-12) {
          // The discriminant is close enough to zero
          // that we treat it as zero. Perhaps the negative value
          // is due to round-off error or perhaps it is truly negative.
          d = 0;
        } else {
          centerX0 = Double.NaN;
          centerY0 = Double.NaN;
          centerX1 = Double.NaN;
          centerY1 = Double.NaN;
          valid = false;
          return;
        }
      }
      d = Math.sqrt(d);
      double x = (-b - d) / (2 * a);
      double y = s + t * x;
      // We wish to place centerX0, centerY0 to the left of the
      // segment (x0, y0), (x1, y0).  Compute the dot product of
      // vector (x, y) and the perpendicular to the segment vector (h, k)
      // which is (-k, h).  side = -k*x + h*y
      double side = h * y - k * x;
      if (side > 0) {
        centerX0 = x + x0;
        centerY0 = y + y0;
        x = (-b + d) / (2 * a);
        y = s + t * x;
        centerX1 = x + x0;
        centerY1 = y + y0;
      } else {
        centerX1 = x + x0;
        centerY1 = y + y0;
        x = (-b + d) / (2 * a);
        y = s + t * x;
        centerX0 = x + x0;
        centerY0 = y + y0;
      }
    } else {
      // Here we compute y first and x as a function of y with x = s + t*y
      double s = (k * k + h * h) / (2 * h);
      double t = -k / h;
      double a = (1 + t * t);
      double b = 2 * s * t;
      double c = s * s - r * r;

      double d = b * b - 4 * a * c;
      if (d < 0) {
        if (d > -1.0e-12) {
          d = 0;
        } else {
          centerX0 = Double.NaN;
          centerY0 = Double.NaN;
          centerX1 = Double.NaN;
          centerY1 = Double.NaN;
          valid = false;
          return;
        }
      }
      d = Math.sqrt(d);
      double y = (-b - d) / (2 * a);
      double x = s + t * y;
      double side = h * y - k * x;  // see note on side calculation above
      if (side > 0) {
        centerX0 = x + x0;
        centerY0 = y + y0;
        y = (-b + d) / (2 * a);
        x = s + t * y;
        centerX1 = x + x0;
        centerY1 = y + y0;
      } else {
        centerX1 = x + x0;
        centerY1 = y + y0;
        y = (-b + d) / (2 * a);
        x = s + t * y;
        centerX0 = x + x0;
        centerY0 = y + y0;
      }
    }
    valid = true;
  }

  /**
   * Indicates whether the specified coordinates are inside one of the two
   * open circles represented by this object.
   *
   * @param x the X coordinate for the point of interest.
   * @param y the Y coordinate for the point of interest
   * @return true if the point is inside an open circle defined by this object.
   */
  public boolean isPointInCircles(double x, double y) {
    double dx = x - centerX1;
    double dy = y - centerY1;
    double d = dx * dx + dy * dy;
    if (d < r2test) {
      return true;
    }
    dx = x - centerX0;
    dy = y - centerY0;
    d = dx * dx + dy * dy;
    return d < r2test;
  }

  /**
   * Computes the distance from the specified coordinates to the nearest
   * circle center.
   *
   * @param x a valid real-valued coordinate
   * @param y a valid real-valued coordinate
   * @return
   */
  public double getDistance(double x, double y) {
    double dMin = Double.POSITIVE_INFINITY;
    double dx = x - centerX0;
    double dy = y - centerY0;
    double d = dx * dx + dy * dy;
    if (d < dMin) {
      dMin = d;
    }
    dx = x - centerX1;
    dy = y - centerY1;
    d = dx * dx + dy * dy;
    if (d < dMin) {
      dMin = d;
    }
    return Math.sqrt(dMin);
  }

}
