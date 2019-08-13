/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 * 07/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.Arrays;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides methods and elements for constructing a contour.
 * Tinfour defines contours as specifying a boundary between two
 * regions in a plane. The region to the left of the contour is 
 * treated as including points with vertical coordinates greater than
 * or equal to the contour's vertical coordinate. The values to the
 * right are treated as including points with vertical coordinates
 * less than the contour's vertical coordinate.  Thus, in an elevation
 * set, a hill would be represented with a set of closed-loop
 * contours taken in counterclockwise order. A valley would be represented
 * as a set of closed-loop contours taken in clockwise order.
 */
public class Contour {

  private static final int GROWTH_FACTOR = 256;

  int n;
  double[] xy;

  final int contourIndex;
  final int leftIndex;
  final int rightIndex;
  final double z;
  final boolean closedLoop;

  IQuadEdge startEdge;
  Vertex startVertex;
  IQuadEdge terminalEdge;
  Vertex terminalVertex;

  /**
   * Constructs an instance of a contour
   * @param contourIndex an arbitrary ID value assigned to the contour
   * @param leftIndex the contour-interval index of the area to the left of the
   * contour.
   * @param rightIndex the contour-interval index of the area to the right
   * of the contour.
   * @param z the vertical coordinate for the contour
   * @param closedLoop indicates if the contour is to be treated as a 
   * closed loop.
   */
  public Contour(
          int contourIndex, 
          int leftIndex, 
          int rightIndex, 
          double z, 
          boolean closedLoop) 
  {
    this.contourIndex = contourIndex;
    this.leftIndex = leftIndex;
    this.rightIndex = rightIndex;
    this.z = z;
    this.xy = new double[GROWTH_FACTOR];
    this.closedLoop = closedLoop;
  }

  /**
   * Used during construction of the contour from a Delaunay Triangulation 
   * to create a through-edge transition point.
   * @param e the edge through which the contour passes.
   * @param zA the value of the first vertex of the edge
   * @param zB the value of the second vertex of the edge
   */
  void add(IQuadEdge e, double zA, double zB) {
    assert zA > zB : "Adding non-decending edge";
    if (n == 0) {
      startEdge = e;
      startVertex = null;
    } else {
      if (n == xy.length) {
        xy = Arrays.copyOf(xy, xy.length + GROWTH_FACTOR);
      }
    }
    terminalEdge = e;
    terminalVertex = null;

    // interpolate out next point
    Vertex A = e.getA();
    Vertex B = e.getB();
    double zDelta = zB - zA;
    xy[n++] = ((z - zA) * B.getX() + (zB - z) * A.getX()) / zDelta;
    xy[n++] = ((z - zA) * B.getY() + (zB - z) * A.getY()) / zDelta;
  }

  /**
   * Used during construction of the contour from a Delaunay Triangulation
   * to indicate a through-vertex transition of the contour.
   * The edge e is expected to end in the vertex v and begin with
   * a vertex that has a z-coordinate greater than or equal to the 
   * contour z value. During construction, this edge is used to 
   * indicate the area immediately to the left of the contour.
   * @param e a valid edge
   * @param v a valid vertex through which the contour passes.
   */
  void add(IQuadEdge e, Vertex v) {
    assert v.equals(e.getB()) : "Through-vertex case, edge not pointing at vertex";
    if (n == 0) {
      startEdge = null;
      startVertex = v;
    } else {
      if (n == xy.length) {
        xy = Arrays.copyOf(xy, xy.length + GROWTH_FACTOR);
      }
    }
    terminalEdge = e;
    terminalVertex = v;

    xy[n++] = v.getX();
    xy[n++] = v.getY();
  }

  /**
   * Gets a safe copy of the coordinates for the contour.
   * @return a valid, potentially zero-length array giving
   * x and y coordinates for a series of points.
   */
  public double[] getCoordinates() {
    return Arrays.copyOf(xy, n);
  }

  /**
   * Indicates whether the contour is empty.
   * @return true if the contour has no geometry defined;
   * otherwise false.
   */
  public boolean isEmpty() {
    return n <4; // recall n = nPoints*2.  a single-point contour is empty.
  }

  
  /**
   * Indicates the number of points stored in the contour
   * @return a positive integer value, potentially zero.
   */
  public int size() {
    return n / 2;
  }

  /**
   * Trim the memory for the collection of points (the geometry) to
   * the minimum required for the contour..
   */
  public void trimToSize() {
    if (xy.length > n) {
      xy = Arrays.copyOf(xy, n);
    }
  }

  /**
   * Gets the index of the contour.  When used with the ContourBuilder,
   * this value gives a unique serial ID assigned when the contour is constructed.
   * Other applications are free to use this index as they see fit.
   * This value should not be confused with the contour interval or
   * the left and right side index values.
   * @return an integer value.
   */
  public int getIndex() {
    return contourIndex;
  }

  /**
   * Gets a Path2D suitable for rendering purposes.
   *
   * @param transform a valid AffineTransform, typically specified to map the
   * Cartesian coordinates of the contour to pixel coordinate.
   * @return a valid instance
   */
  public Path2D getPath2D(AffineTransform transform) {
    Path2D path = new Path2D.Double();
    if (n >= 4) {
      double[] c = new double[n];
      transform.transform(xy, 0, c, 0, n / 2);

      path.moveTo(c[0], c[1]);
      for (int i = 1; i < n / 2; i++) {
        path.lineTo(c[i * 2], c[i * 2 + 1]);
      }
      if (this.closedLoop) {
        path.closePath();
      }
    }
    return path;
  }

}
