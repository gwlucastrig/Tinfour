/*
 * Copyright 2014 Gary W. Lucas.
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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 03/2014  G. Lucas     Created
 * 06/2014  G. Lucas     Refactored from earlier implementation
 * 12/2015  G. Lucas     Moved into common package
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

import tinfour.vividsolutions.jts.math.DD;

/**
 * Provides elements and methods to support geometric operations
 * using "double double" precision where necessary. The double-double
 * precision calculations use extended precision arithmetic to provide
 * 108 bits of mantissa or about 30 decimal digits of precision.
 */
public class GeometricOperations {

  /* Reusable extended precision data objects */
  private final DD q11 = new DD();
  private final DD q21 = new DD();
  private final DD q31 = new DD();
  private final DD q12 = new DD();
  private final DD q22 = new DD();
  private final DD q32 = new DD();
  private final DD q11s = new DD();
  private final DD q12s = new DD();
  private final DD q21s = new DD();
  private final DD q22s = new DD();
  private final DD q31s = new DD();
  private final DD q32s = new DD();
  private final DD q21_32 = new DD();
  private final DD q31_22 = new DD();
  private final DD q31_12 = new DD();
  private final DD q11_32 = new DD();
  private final DD q11_22 = new DD();
  private final DD q21_12 = new DD();

  /* Parameters related to magnitude of numeric values */
  private final Thresholds thresholds;
  private final double inCircleThreshold;
  private final double halfPlaneThresholdNeg;
  private final double halfPlaneThreshold;

  /* Diagnostics for counting operations   */
  private int nInCircleCalls;
  private int nExtendedPrecisionInCircle;
  private int nExtendedConflict;
  private int nHalfPlaneCalls;

  /**
   * Construct an instance based on a nominal point spacing of 1 unit.
   */
  public GeometricOperations() {
    thresholds = new Thresholds(1.0);
    this.inCircleThreshold = thresholds.getInCircleThreshold();
    this.halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    this.halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
  }

  /**
   * Construct an instance based on the specified threshold values.
   * @param thresholds a valid instance
   */
  public GeometricOperations(Thresholds thresholds) {
    this.thresholds = thresholds;
    this.inCircleThreshold = thresholds.getInCircleThreshold();
    this.halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    this.halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
  }

  /**
   * Determines if vertex d lies within the circumcircle of triangle a,b,c,
   * using extended-precision arithmetic when required by small
   * magnitude results.
   *
   * @param a a valid vertex
   * @param b a valid vertex
   * @param c a valid vertex
   * @param d a valid vertex
   * @return positive if d is inside the circumcircle; negative if it is
   * outside; zero if it is on the edge.
   */
  public double inCircle(Vertex a, Vertex b, Vertex c, Vertex d) {
    return inCircle(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y);
  }

  /**
   * Determines if vertex d lies within the circumcircle of triangle a,b,c,
   * using extended-precision arithmetic when required by small
   * magnitude results.
   *
   * @param ax the x coordinate of vertex a
   * @param ay the y coordinate of vertex a
   * @param bx the x coordinate of vertex b
   * @param by the y coordinate of vertex b
   * @param cx the x coordinate of vertex c
   * @param cy the y coordinate of vertex c
   * @param dx the x coordinate of vertex d
   * @param dy the y coordinate of vertex d
   * @return positive if d is inside the circumcircle; negative if it is
   * outside; zero if it is on the edge.
   */
  public double inCircle(
    double ax,
    double ay,
    double bx,
    double by,
    double cx,
    double cy,
    double dx,
    double dy) {
    nInCircleCalls++;

    // Shewchuk presents versions of the determinant calculations
    // in which all the terms are expressed as differences.
    // So, for example term a11 becomes ax - dx, etc.  This has the
    // advantage with map coordinates which may be quite large in
    // magnitude for a particular data set even though the range of
    // values is relatively small.  For example, coordinates for a
    // Lidar sample in Northern Connecticut in UTM Zone 18N coordinates
    // might range from (640000,4000000) to (640500, 4000500).
    // by taking the differences, we get smaller magnitude values
    // this is significant when we start multiplying terms together and
    // taking differences... we want to reduce the loss of precision in
    // lower-order digits.
    // column 1
    double a11 = ax - dx;
    double a21 = bx - dx;
    double a31 = cx - dx;

    // column 2
    double a12 = ay - dy;
    double a22 = by - dy;
    double a32 = cy - dy;

    // column 3 (folded into code below)
    //double a13 = a11 * a11 + a12 * a12;
    //double a23 = a21 * a21 + a22 * a22;
    //double a33 = a31 * a31 + a32 * a32;
    // the following is organized so that terms of like-magnitude are
    // grouped together when difference are taken.  the column 3 terms
    // involve squared terms.  We do not want to take a difference between
    // once of these and a non-squared term because we do not want to
    // lose precision in the low-order digits.
    double inCircle
      = (a11 * a11 + a12 * a12) * (a21 * a32 - a31 * a22)
      + (a21 * a21 + a22 * a22) * (a31 * a12 - a11 * a32)
      + (a31 * a31 + a32 * a32) * (a11 * a22 - a21 * a12);

    if (-inCircleThreshold < inCircle && inCircle < inCircleThreshold) {
      this.nExtendedPrecisionInCircle++;
      double inCircle2 = this.inCircleQuadPrecision(ax, ay, bx, by, cx, cy, dx, dy);

      if (inCircle2 * inCircle <= 0 && (inCircle != 0 || inCircle2 != 0)) {
        this.nExtendedConflict++;
        // The following is used to try to find a threshold value
        // for the inCircleThreshold.  Find the maximum abs
        // inCircle value that triggers a conflict.  Our theory is
        // that conflicts only occur for inCircle results close to zero
        //   as coded, this test is probably fast enough that we could
        // use it (except for the print statement) to collect
        // diagnostic statistics.
        //s2.setValue(inCircle).selfSubtract(s1);
        //double err = s2.doubleValue();
        //double trigger = Math.abs(inCircle);
        //if (trigger > maxTrigger) {
        //    maxTrigger = trigger;
        //    System.err.format(
        //    "inCircle, inCircle2, err = %20.12e %22.12e %20.12e \n",
        //            inCircle, inCircle2, err);
        //}
      }
      inCircle = inCircle2;
    }

    return inCircle;
  }

  /**
   * Uses quad-precision methods to determines if vertex d lies
   * within the circumcircle of triangle a,b,c. Similar to inCircle()
   * but requires more processing and delivers higher accuracy.
   *
   * @param ax the x coordinate of vertex a
   * @param ay the y coordinate of vertex a
   * @param bx the x coordinate of vertex b
   * @param by the y coordinate of vertex b
   * @param cx the x coordinate of vertex c
   * @param cy the y coordinate of vertex c
   * @param dx the x coordinate of vertex d
   * @param dy the y coordinate of vertex d
   * @return positive if d is inside the circumcircle; negative if it is
   * outside; zero if it is on the edge.
   */
  public double inCircleQuadPrecision(
    double ax,
    double ay,
    double bx,
    double by,
    double cx,
    double cy,
    double dx,
    double dy) {

    this.nExtendedPrecisionInCircle++;
    //     (a11 * a11 + a12 * a12) * (a21 * a32 - a31 * a22)
    //   + (a21 * a21 + a22 * a22) * (a31 * a12 - a11 * a32)
    //   + (a31 * a31 + a32 * a32) * (a11 * a22 - a21 * a12);

    q11.setValue(ax).selfSubtract(dx);
    q21.setValue(bx).selfSubtract(dx);
    q31.setValue(cx).selfSubtract(dx);

    q12.setValue(ay).selfSubtract(dy);
    q22.setValue(by).selfSubtract(dy);
    q32.setValue(cy).selfSubtract(dy);

    q11s.setValue(q11).selfMultiply(q11);
    q12s.setValue(q12).selfMultiply(q12);
    q21s.setValue(q21).selfMultiply(q21);
    q22s.setValue(q22).selfMultiply(q22);
    q31s.setValue(q31).selfMultiply(q31);
    q32s.setValue(q32).selfMultiply(q32);

    q11_22.setValue(q11).selfMultiply(q22);
    q11_32.setValue(q11).selfMultiply(q32);
    q21_12.setValue(q21).selfMultiply(q12);
    q21_32.setValue(q21).selfMultiply(q32);
    q31_22.setValue(q31).selfMultiply(q22);
    q31_12.setValue(q31).selfMultiply(q12);

    // the following lines are destructive of values computed above
    DD s1 = q11s.selfAdd(q12s);
    DD s2 = q21s.selfAdd(q22s);
    DD s3 = q31s.selfAdd(q32s);

    DD t1 = q21_32.selfSubtract(q31_22);
    DD t2 = q31_12.selfSubtract(q11_32);
    DD t3 = q11_22.selfSubtract(q21_12);

    s1.selfMultiply(t1);
    s2.selfMultiply(t2);
    s3.selfMultiply(t3);
    s1.selfAdd(s2).selfAdd(s3);

    return s1.doubleValue();
  }

 /**
  * Uses extended arithmetic to find the side on which a point lies with
  * respect to a directed edge.
   * @param ax the x coordinate of the first vertex in the segment
   * @param ay the y coordinate of the first vertex in the segment
   * @param bx the x coordinate of the second vertex in the segment
   * @param by the y coordinate of the second vertex in the segment
   * @param cx the x coordinate of the point of interest
   * @param cy the y coordinate of the point of interest
  * @return  positive if the point is to the left of the edge,
  * negative if it is to the right, or zero if it lies on the ray
  * coincident with the edge.
  */
  public double halfPlane(double ax, double ay,
          double bx, double by,
          double cx, double cy)
  {
    nHalfPlaneCalls++;
    q11.setValue(cx).selfSubtract(ax);
    q12.setValue(ay).selfSubtract(by);
    q21.setValue(cy).selfSubtract(ay);
    q22.setValue(bx).selfSubtract(ax);
    q11.selfMultiply(q12);
    q21.selfMultiply(q22);
    q11.selfAdd(q21);
    return q11.doubleValue();
  }

  /**
   * Uses extended arithmetic to find the direction of
   * a point with coordinates (cx, cy) compared to a
   * directed edge from vertex A to B. This value is given by the dot
   * product (cx-ax, cy-ay) dot (bx-ax, by-ay).
   * @param ax the x coordinate of the initial point on the edge
   * @param ay the y coordinate of the initial point on the edge
   * @param bx the x coordinate of the second point on the edge
   * @param by the y coordinate of the second point on the edge
   * @param cx the coordinate of interest
   * @param cy the coordinate of interest
   * @return a valid, signed floating point number, potentially zero.
   */
  public double direction(
          double ax, double ay,
          double bx, double by,
          double cx, double cy)
  {
    nHalfPlaneCalls++;
    q11.setValue(bx).selfSubtract(ax);
    q12.setValue(by).selfSubtract(ay);
    q21.setValue(cx).selfSubtract(ax);
    q22.setValue(cy).selfSubtract(ay);
    q11.selfMultiply(q21);
    q12.selfMultiply(q22);
    q11.selfAdd(q12);
    return q11.doubleValue();
  }

  /**
   * Use extended arithmetic to compute the signed orientation
   * of the triangle defined by three points
   *
   * @param ax x coordinate of the first point
   * @param ay y coordinate of the first point
   * @param bx x coordinate of the second point
   * @param by y coordinate of the second point
   * @param cx x coordinate of the third point
   * @param cy y coordinate of the third point
   * @return if the triangle has a counterclockwise order, a positive value;
   * if the triangle is degenerate, a zero value; if the triangle has
   * a clockwise order, a negative value.
   */
  public double orientation(
          double ax, double ay,
          double bx, double by,
          double cx, double cy)
  {
    double a = (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);

    if (a > halfPlaneThresholdNeg && a < halfPlaneThreshold) {
      q11.setValue(ax).selfSubtract(cx);
      q12.setValue(by).selfSubtract(cy);
      q21.setValue(bx).selfSubtract(cx);
      q22.setValue(ay).selfSubtract(cy);
      q11.selfMultiply(q12);
      q21.selfMultiply(q22);
      q11.selfSubtract(q21);
      return q11.doubleValue();
    }
    return a;
  }

    /**
     * Get a diagnostic count of the number of times an in-circle calculation
     * was performed.
     *
     * @return a positive integer value
     */
  int getInCircleCount() {
    return nInCircleCalls;
  }

      /**
   * Get a diagnostic count of the number of incidents where an extended
   * precision calculation was required for an in-circle calculation
   * due to the small-magnitude value of the computed value.
   * @return a positive integer value
   */
  public int getExtendedPrecisionInCircleCount() {
    return nExtendedPrecisionInCircle;
  }

    /**
   * Get a diagnostic count of the number of incidents where an extended
   * precision calculation was in conflict with the ordinary precision
   * calculation.
   * @return a positive integer value
   */
  public int getExtendedConflictCount() {
    return nExtendedConflict;
  }

  /**
   * Get a diagnostic count of the number of half-plane calculations
   * @return a positive integer value
   */
  public int getHalfPlaneCount() {
    return nHalfPlaneCalls;
  }

  /**
   * Determines the signed area of triangle ABC
   *
   * @param a the initial vertex
   * @param b the second vertex
   * @param c the third vertex
   * @return an area calculation signed positive or negative according to the
   * order of the vertices
   */
  public double area(Vertex a, Vertex b, Vertex c) {
    double h = (c.x - a.x) * (a.y - b.y) + (c.y - a.y) * (b.x - a.x);
    if (-inCircleThreshold < h && h < inCircleThreshold) {
      h = halfPlane(a.x, a.y, b.x, b.y, c.x, c.y);
    }
    return h / 2.0;
  }

  /**
   * Clear the diagnostic operation counts maintained by this class.
   */
  public void clearDiagnostics() {
    nInCircleCalls = 0;
    nExtendedPrecisionInCircle = 0;
    nExtendedConflict = 0;
    nHalfPlaneCalls = 0;
  }

    /**
   * Computes the circumcircle for the coordinates of three vertices.
   * For efficiency purposes, results are stored in a reusable container
   * instance.
   * @param a Vertex A
   * @param b Vertex B
   * @param c Vertex C
   * @param result a valid instance to store the result.
   */
  public void computeCircumcircle(
          final Vertex a,
          final Vertex b,
          final Vertex c,
          final Circumcircle result)
  {

    double bx, by, cx, cy, d, c2, b2;
    double x0 = a.x;
    double y0 = a.y;

    bx = b.x - x0;
    by = b.y - y0;
    cx = c.x - x0;
    cy = c.y - y0;

    d = 2 * (bx * cy - by * cx);
    if (d < 1.0e-11) {
      // the triangle is close to the degenerate case
      // (all 3 points in a straight line)
      // even if determinant d is not zero, numeric precision
      // issues might lead to a very poor computation for
      // the circumcircle.
      result.setCircumcenter(
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY);
      return;
    }
    b2 = bx * bx + by * by;
    c2 = cx * cx + cy * cy;
    double x = (cy * b2 - by * c2) / d;
    double y = (bx * c2 - cx * b2) / d;
    result.setCircumcenter(x + a.x, y + a.y, x * x + y * y);
  }

  /**
   * Computes the circumcircle for the coordinates of three vertices.
   * For efficiency purposes, results are stored in a reusable container
   * instance.
   * @param vax The x coordinate of vertex A
   * @param vay The y coordinate of vertex A
   * @param vbx The x coordinate of vertex B
   * @param vby The y coordinate of vertex B
   * @param vcx The x coordinate of vertex C
   * @param vcy The y coordinate of vertex C
   * @param result a valid instance to store the result.
   */
  public void computeCircumcircle(
          final double vax, final double vay,
          final double vbx, final double vby,
          final double vcx, final double vcy,
          final Circumcircle result)
  {
    double x0, y0, bx, by, cx, cy, d, c2, b2;
    x0 = vax;
    y0 = vay;
    bx = vbx - x0;
    by = vby - y0;
    cx = vcx - x0;
    cy = vcy - y0;

    d = 2 * (bx * cy - by * cx);
    if (d < 1.0e-11) {
      // the triangle is close to the degenerate case
      // (all 3 points in a straight line)
      // even if determinant d is not zero, numeric precision
      // issues might lead to a very poor computation for
      // the circumcircle.
      result.setCircumcenter(
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY);
      return;
    }
    b2 = bx * bx + by * by;
    c2 = cx * cx + cy * cy;
    double x = (cy * b2 - by * c2) / d;
    double y = (bx * c2 - cx * b2) / d;

    result.setCircumcenter(x + vax, y + vay, x * x + y * y);
  }

}
