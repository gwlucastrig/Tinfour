/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 08/2015  G. Lucas     Created
 * 01/2017  G. Lucas     Refactored to provide more generic functionality
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Provides descriptive data for a Triangulated Irregular Network (TIN).
 */
public class TriangleCount {

  private int count;
  private double sumArea;
  private double sumArea2;
  private double minArea = Double.POSITIVE_INFINITY;
  private double maxArea = Double.NEGATIVE_INFINITY;

  private double c;  // compensator for Kahan summation for area
  private double c2; // compensator for Kahan summation for area squared
  private final GeometricOperations geoOp;

  /**
   * Create an instance for tabulating a survey of
   * the triangles in an Incremental TIN
   */
  public TriangleCount() {
     geoOp = new GeometricOperations();
  }

  /**
   * Compute area for the triangle specified by the vertex arguments and
   * add it to the triangle count and area summations.
   *
   * @param vA the initial vertex, given in counterclockwise order
   * @param vB the second vertex, given in counterclockwise order
   * @param vC the third vertex, given in counterclockwise order
   */
  public void tabulateTriangle(Vertex vA, Vertex vB, Vertex vC) {
    // compute the area and tabulate using the Kahan Summation Algorithm

    count++;
    double a, y, t;
    a = geoOp.area(vA, vB, vC);

    y = a - c;
    t = sumArea + y;
    c = (t - sumArea) - y;
    sumArea = t;

    y = a * a - c2;
    t = sumArea2 + y;
    c2 = (t - sumArea2) - y;
    sumArea2 = t;

    if (a < minArea) {
      minArea = a;
    }
    if (a > maxArea) {
      maxArea = a;
    }
  }

  /**
   * Get the number of triangles in the TIN.
   *
   * @return a integer value of 1 or more (zero if TIN is undefined).
   */
  public int getCount() {
    return count;
  }

  /**
   * Gets the sum of the area of all triangles in the TIN.
   *
   * @return if the triangle count is greater than zero,
   * a positive floating point value
   *
   */
  public double getAreaSum() {
    return sumArea;
  }

  /**
   * Get the mean area of the triangles in the TIN.
   *
   * @return if the triangle count is greater than zero,
   * a positive floating point value
   */
  public double getAreaMean() {
    if (count == 0) {
      return 0;
    }
    return sumArea / count;
  }

  /**
   * Gets the standard deviation of the triangles in the TIN.
   *
   * @return if the triangle count is greater than one,
   * a positive floating point value
   */
  public double getAreaStandardDeviation() {
    if (count < 2) {
      return 0;
    }
    double n = count; // use double to avoid int overflow
    double s = n * sumArea2 - sumArea * sumArea;
    double t = n * (n - 1);
    return Math.sqrt(s / t);
  }

  /**
   * Gets the minimum area of the triangles in the TIN.
   *
   * @return if the triangle count is greater than zero,
   * a positive floating point value
   */
  public double getAreaMin() {
    return minArea;
  }

  /**
   * Gets the maximum area of the triangles in the TIN.
   *
   * @return if the triangle count is greater than zero,
   * a positive floating point value
   */
  public double getAreaMax() {
    return maxArea;
  }

}