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
 * Date Name Description
 * ------   ---------   -------------------------------------------------
 * 04/2014  G. Lucas    Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Provides center coordinates and radius for a circumcircle.
 */
public class Circumcircle {

  /**
   * The x coordinate of the center of the circumcircle.
   */
  private double centerX;
  /**
   * The y coordinate of the center of the circumcircle.
   */
  private double centerY;
  /**
   * The square of the radius of the center of the circumcircle.
   */
  private double r2;

  /**
   * An arbitrary minimum area for which a circumcircle should be constructed
   * in order to avoid failures due to numerical precision issues.
   */
  private static final double MIN_TRIG_AREA = 1.0e-20;

  /**
   * Gets the square of the radius of the circumcircle.
   *
   * @return for a non-degenerate triangle, a positive floating point value
   * (potentially Infinity for a ghost triangle).
   */
  public double getRadiusSq() {
    return r2;
  }

  /**
   * Gets the x coordinate of the center of the circumcircle.
   *
   * @return a valid floating point value.
   */
  public double getX() {
    return centerX;
  }

  /**
   * Gets the y coordinate of the center of the circumcircle.
   *
   * @return a valid floating point value.
   */
  public double getY() {
    return centerY;
  }

  /**
   * Copies the content of the specified circumcircle instance.
   *
   * @param c a valid circumcircle instance.
   */
  public void copy(final Circumcircle c) {
    centerX = c.centerX;
    centerY = c.centerY;
    r2 = c.r2;
  }

  /**
   * Computes the circumcircle for the specified vertices.
   * Vertices are assumed to be given in counterclockwise order.
   * Any null inputs for the vertices results in an infinite circumcircle.
   * Vertices resulting in a degenerate (nearly zero area) triangle
   * result in an infinite circumcircle.
   *
   * @param a the initial vertex.
   * @param b the second vertex.
   * @param c the third vertex.
   * @return a valid circumcircle.
   */
  public static Circumcircle computeCircumcircle(
    final Vertex a,
    final Vertex b,
    final Vertex c) {
    Circumcircle circle = new Circumcircle();
    if (a == null || b == null || c == null) {
      circle.centerX = Double.POSITIVE_INFINITY;
      circle.centerY = Double.POSITIVE_INFINITY;
      circle.r2 = Double.POSITIVE_INFINITY;
      return circle;
    }
    double bx, by, cx, cy, d, c2, b2;
    double x0 = a.x;
    double y0 = a.y;

    bx = b.x - x0;
    by = b.y - y0;
    cx = c.x - x0;
    cy = c.y - y0;

    d = 2 * (bx * cy - by * cx);
    if (d < MIN_TRIG_AREA) {
      // the triangle is close to the degenerate case
      // (all 3 points in a straight line)
      // even if determinant d is not zero, numeric precision
      // issues might lead to a very poor computation for
      // the circumcircle.
      circle.centerX = Double.POSITIVE_INFINITY;
      circle.centerY = Double.POSITIVE_INFINITY;
      circle.r2 = Double.POSITIVE_INFINITY;
      return circle;
    }
    b2 = bx * bx + by * by;
    c2 = cx * cx + cy * cy;
    circle.centerX = (cy * b2 - by * c2) / d;
    circle.centerY = (bx * c2 - cx * b2) / d;
    circle.r2 = circle.centerX * circle.centerX + circle.centerY + circle.centerY;
    circle.centerX += a.x;
    circle.centerY += a.y;

    return circle;
  }

  /**
   * Computes the circumcircle for the specified vertices and stores
   * results in elements of this instance.
   * Vertices are assumed to be given in counterclockwise order.
   * Any null inputs for the vertices results in an infinite circumcircle.
   * Vertices resulting in a degenerate (nearly zero area) triangle
   * result in an infinite circumcircle.
   *
   * @param a the initial vertex.
   * @param b the second vertex.
   * @param c the third vertex.
   * @return true if the computation successfully yields a circle of
   * finite radius; otherwise, false.
   *
   */
  public boolean compute(final Vertex a, final Vertex b, final Vertex c) {

    if (a == null || b == null || c == null) {
      centerX = Double.POSITIVE_INFINITY;
      centerY = Double.POSITIVE_INFINITY;
      r2 = Double.POSITIVE_INFINITY;
      return false;
    }
    double bx, by, cx, cy, d, c2, b2;
    double x0 = a.x;
    double y0 = a.y;

    bx = b.x - x0;
    by = b.y - y0;
    cx = c.x - x0;
    cy = c.y - y0;

    d = 2 * (bx * cy - by * cx);
    if (d < MIN_TRIG_AREA) {
      // the triangle is close to the degenerate case
      // (all 3 points in a straight line)
      // even if determinant d is not zero, numeric precision
      // issues might lead to a very poor computation for
      // the circumcircle.
      this.centerX = Double.POSITIVE_INFINITY;
      this.centerY = Double.POSITIVE_INFINITY;
      r2 = Double.POSITIVE_INFINITY;
      return false;
    }
    b2 = bx * bx + by * by;
    c2 = cx * cx + cy * cy;
    centerX = (cy * b2 - by * c2) / d;
    centerY = (bx * c2 - cx * b2) / d;
    r2 = this.centerX * this.centerX + this.centerY + this.centerY;
    centerX += a.x;
    centerY += a.y;
    return true;
  }

  /**
   * Computes the circumcircle for the specified vertices and stores
   * results in elements of this instance.
   * Vertices are assumed to be given in counterclockwise order.
   * Any null inputs for the vertices results in an infinite circumcircle.
   * Vertices resulting in a degenerate (nearly zero area) triangle
   * result in an infinite circumcircle.
   *
   * @param x0 the x coordinate of the first vertex
   * @param y0 the y coordinate of the first vertex
   * @param x1 the x coordinate of the second vertex
   * @param y1 the y coordinate of the second vertex
   * @param x2 the x coordinate of the third vertex
   * @param y2 the y coordinate of the third vertex
   *
   */
  public void compute(
    final double x0,
    final double y0,
    final double x1,
    final double y1,
    final double x2,
    final double y2) {

    double bx, by, cx, cy, d, c2, b2;

    bx = x1 - x0;
    by = y1 - y0;
    cx = x2 - x0;
    cy = y2 - y0;

    d = 2 * (bx * cy - by * cx);
    if (d < MIN_TRIG_AREA) {
      // the triangle is close to the degenerate case
      // (all 3 points in a straight line)
      // even if determinant d is not zero, numeric precision
      // issues might lead to a very poor computation for
      // the circumcircle.
      this.centerX = Double.POSITIVE_INFINITY;
      this.centerY = Double.POSITIVE_INFINITY;
      r2 = Double.POSITIVE_INFINITY;
      return;
    }
    b2 = bx * bx + by * by;
    c2 = cx * cx + cy * cy;
    this.centerX = (cy * b2 - by * c2) / d;
    this.centerY = (bx * c2 - cx * b2) / d;
    r2 = this.centerX * this.centerX + this.centerY + this.centerY;
    this.centerX += x0;
    this.centerY += y0;
  }

  /**
   * Sets the coordinate for the circumcenter and radius for this
   * instance.
   *
   * @param x the x coordinate for the circumcenter
   * @param y the y coordinate for the circumcenter
   * @param r2 the square of the radius for the circumcircle
   */
  public void setCircumcenter(final double x, final double y, final double r2) {
    this.centerX = x;
    this.centerY = y;
    this.r2 = r2;
  }

}
