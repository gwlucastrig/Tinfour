/* --------------------------------------------------------------------
 * Copyright (C) 2020  Gary W. Lucas.
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
 * 12/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.util.List;
import org.tinfour.common.Vertex;

/**
 * Implements utilities for computing Barycentric Coordinates.
 * The algorithm for computing coordinates is based on
 * <cite>Hormann, Kai. (2005). "Barycentric Coordinates for Arbitrary
 * Polygons in the Plane -- Technical Report IfI-05-05",
 * Institute fur Informatik, Technische Universitat Clausthal.</cite>
 * <p>
 * <strong>Development Status:</strong> At this time, this class has
 * not been thoroughly reviewed and has undergone only superficial testing.
 */
public class BarycentricCoordinates {

  private double barycentricCoordinateDeviation;

  /**
   * Given a reference point inside a simple, but potentially non-convex
   * polygon, creates an array of barycentric coordinates for the point. The
   * coordinates are normalized, so that their sum is 1.0. This method
   * populates the barycentric deviation member element which may be
   * used as a figure of merit for evaluating the success of the
   * coordinate computation. If the point is not inside the polygon or if
   * the polygon is self-intersecting, the results are undefined
   * and the method may return a null array or a meaningless result.
   * If the point is on the perimeter of the polygon, this method will
   * return a null array.
   *
   * @param polygon list of vertices defining a non-self-intersecting,
   * potentially non-convex polygon.
   * @param x the x coordinate of the reference point
   * @param y the y coordinate of the reference point
   * @return if successful, a valid array; otherwise a null.
   */
  public double[] getBarycentricCoordinates(
    List<Vertex> polygon,
    double x,
    double y) {
    int nVertices = polygon.size();
    if (nVertices < 3) {
      return null;
    }

    Vertex v0 = polygon.get(nVertices - 1);
    Vertex v1 = polygon.get(0);
    if (v0 == null || v1 == null) {
      return null;
    }

    // some applications create polygons that give the same point
    // as the start and end of the polygon.  In such cases, we
    // adjust nEdge down to simplify the arithmetic below.
    if (v0.equals(v1)) {
      nVertices--;
      if (nVertices < 3) {
        return null;
      }
      v0 = polygon.get(nVertices - 1);
    }

    double[] weights = new double[nVertices];
    double wSum = 0;

    double x0 = v0.getX() - x;
    double y0 = v0.getY() - y;
    double x1 = v1.getX() - x;
    double y1 = v1.getY() - y;
    double r0 = Math.sqrt(x0 * x0 + y0 * y0);
    double r1 = Math.sqrt(x1 * x1 + y1 * y1);
    double t1 = (r0 * r1 - (x0 * x1 + y0 * y1)) / (x0 * y1 - x1 * y0); //NOPMD
    for (int iEdge = 0; iEdge < nVertices; iEdge++) {
      int index = (iEdge + 1) % nVertices;
      v1 = polygon.get(index);
      if (v1 == null) {
        // the reference point is on the perimeter
        return null;
      }
      double t0 = t1;
      x0 = x1;
      y0 = y1;
      r0 = r1;
      x1 = v1.getX() - x;
      y1 = v1.getY() - y;
      r1 = Math.sqrt(x1 * x1 + y1 * y1);
      t1 = (r0 * r1 - (x0 * x1 + y0 * y1)) / (x0 * y1 - x1 * y0); //NOPMD
      double w = (t0 + t1) / r0;
      wSum += w;
      weights[iEdge] = w;
    }

    // normalize the weights
    for (int i = 0; i < nVertices; i++) {
      weights[i] = weights[i] / wSum;
    }

    double xSum = 0;
    double ySum = 0;
    for (int i = 0; i < weights.length; i++) {
      Vertex v = polygon.get(i);
      xSum += weights[i] * (v.getX() - x);
      ySum += weights[i] * (v.getY() - y);
    }
    barycentricCoordinateDeviation
      = Math.sqrt(xSum * xSum + ySum * ySum);

    return weights;
  }

  /**
   * Gets the deviation of the computed equivalent of the input query (x,y)
   * coordinates based on barycentric coordinates.
   * While the computed equivalent should
   * be an exact match for the query point, errors in implementation
   * or numeric errors due to float-point precision limitations would
   * result in a deviation. Thus, this method provides a diagnostic
   * on the most recent computation. A large non-zero value indicates a
   * potential implementation problem. A small non-zero value indicates an
   * error due to numeric issues.
   *
   * @return a positive value, ideally zero but usually a small number
   * slightly larger than that.
   */
  public double getBarycentricCoordinateDeviation() {
    return barycentricCoordinateDeviation;
  }

}
