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
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides methods and elements for constructing a contour. Tinfour defines
 * contours as specifying a boundary between two regions in a plane. The region
 * to the left of the contour is treated as including points with vertical
 * coordinates greater than or equal to the contour's vertical coordinate. The
 * values to the right are treated as including points with vertical coordinates
 * less than the contour's vertical coordinate. Thus, in an elevation set, a
 * hill would be represented with a set of closed-loop contours taken in
 * counterclockwise order. A valley would be represented as a set of closed-loop
 * contours taken in clockwise order.
 * <p>
 * The complete() method should always be called when a contour is fully
 * populated (e.g. it is complete). The complete call trims the internal buffers
 * and performs any sanity checking required for contour management.
 * <p>
 * A closed-loop contour is expected to always include a "closure point" so that
 * the first point in the contour matches the last. This approach is taken to
 * simplify internal logic in the contour building routines. The complete()
 * method ensures that a closure point is added to closed-loop contours if none
 * is provided by the application.
 *
 */
public class Contour {

  static final AtomicInteger serialIdSource = new AtomicInteger();

  /**
   * An enumeration that indicates the type of a contour
   */
  public enum ContourType {
    /**
     * Contour lies entirely in the interior of the TIN with the possible
     * exception of the two end points which may lie on perimeter edges. Both
     * the left and right index of the contour will be defined (zero or
     * greater).
     */
    Interior,
    /**
     * Contour is lies entirely on the boundary of the TIN.
     */
    Boundary
  }

  private static final int GROWTH_FACTOR = 256;

  int n;
  double[] xy = new double[GROWTH_FACTOR];

  final int contourId;
  final int leftIndex;
  final int rightIndex;
  final double z;
  final boolean closedLoop;

  boolean traversedForward;
  boolean traversedBackward;
  TipLink startTip;
  TipLink terminalTip;

  /**
   * Constructs an instance of a contour
   *
   * @param leftIndex the contour-interval index of the area to the left of the
   * contour.
   * @param rightIndex the contour-interval index of the area to the right of
   * the contour.
   * @param z the vertical coordinate for the contour
   * @param closedLoop indicates if the contour is to be treated as a closed
   * loop.
   */
  public Contour(
    int leftIndex,
    int rightIndex,
    double z,
    boolean closedLoop) {
    // the contour ID is just a debugging aid.  It gives a way of detecting
    // when a problematic contour is constructed.  Once the software is mature
    // it may not be necessary to preserve it.
    this.contourId = serialIdSource.incrementAndGet();
    this.leftIndex = leftIndex;
    this.rightIndex = rightIndex;
    this.z = z;
    this.closedLoop = closedLoop;
  }

  /**
   * Used during construction of the contour from a Delaunay Triangulation to
   * create a through-edge transition point.
   *
   * @param e the edge through which the contour passes.
   * @param zA the value of the first vertex of the edge
   * @param zB the value of the second vertex of the edge
   */
  void add(IQuadEdge e, double zA, double zB) {
    if (n == xy.length) {
      xy = Arrays.copyOf(xy, xy.length + GROWTH_FACTOR);
    }
    // interpolate out next point
    Vertex A = e.getA();
    Vertex B = e.getB();
    double zDelta = zB - zA;
    double x = ((z - zA) * B.getX() + (zB - z) * A.getX()) / zDelta;
    double y = ((z - zA) * B.getY() + (zB - z) * A.getY()) / zDelta;
    if (n > 1) {
      if (xy[n - 2] == x && xy[n - 1] == y) {
        return;
      }
    }
    xy[n++] = x;
    xy[n++] = y;
  }

  /**
   * Used during construction of the contour from a Delaunay Triangulation to
   * indicate a through-vertex transition of the contour. The edge e is expected
   * to end in the vertex v and begin with a vertex that has a z-coordinate
   * greater than or equal to the contour z value. During construction, this
   * edge is used to indicate the area immediately to the left of the contour.
   *
   * @param v a valid vertex through which the contour passes.
   */
  void add(Vertex v) {
    if (n == xy.length) {
      xy = Arrays.copyOf(xy, xy.length + GROWTH_FACTOR);
    }
    double x = v.getX();
    double y = v.getY();
    if(n>1){
      if(xy[n-2] == x && xy[n-1] == y){
        return;
      }
    }
    xy[n++] = x;
    xy[n++] = y;
  }

  /**
   * Add an coordinate point to the contour.
   *
   * @param x the Cartesian x-coordinate for the point
   * @param y the Cartesian y-coordinate for the point
   */
  public void add(double x, double y) {
    if (n == xy.length) {
      xy = Arrays.copyOf(xy, xy.length + GROWTH_FACTOR);
    }
    if(n>1){
      if(xy[n-2] == x && xy[n-1] == y){
        return;
      }
    }
    xy[n++] = x;
    xy[n++] = y;
  }

  /**
   * Gets a safe copy of the coordinates for the contour. This method
   * is scheduled for replacement in a future release. Please use getXY().
   *
   * @return a valid, potentially zero-length array giving x and y coordinates
   * for a series of points.
   */
  public double[] getCoordinates() {
    return Arrays.copyOf(xy, n);
  }

  /**
   * Gets a safe copy of the coordinates for the contour. Coordinates
   * are stored in a one-dimensional array of doubles in the order:
   * <pre>
   * { (x0,y0), (x1,y1), (x2,y2), etc. }.
   *</pre>
   * @return a valid, potentially zero-length array giving x and y coordinates
   * for a series of points.
   */
  public double[] getXY() {
    return Arrays.copyOf(xy, n);
  }

  /**
   * Gets the z value associated with the contour
   * @return the z value used to construct the contour.
   */
  public double getZ(){
    return z;
  }


  /**
   * Indicates whether the contour is empty.
   *
   * @return true if the contour has no geometry defined; otherwise false.
   */
  public boolean isEmpty() {
    return n < 4; // recall n = nPoints*2.  a single-point contour is empty.
  }

  /**
   * Indicates the number of points stored in the contour
   *
   * @return a positive integer value, potentially zero.
   */
  public int size() {
    return n / 2;
  }

  /**
   * Called when the construction of a contour is complete to trim the memory
   * for the internal point collection. This method also ensures that a
   * closed-loop contour includes a closure point.
   * <p>
   * References to edges and contour-building elements are not affected by this
   * call.
   */
  public void complete() {
    if (closedLoop && n > 6) {
      // ensure that there is a "closure" vertex included in the contour.
      // If there existing endpoints are numerically close, they are adjusted
      // slightly to ensure exact matches.  Otherwise, an additional
      // vertex is added to the contour.
      double x0 = xy[0];
      double y0 = xy[1];
      double x1 = xy[n - 2];
      double y1 = xy[n - 1];
      if (x0 != x1 || y0 != y1) {
        if (numericallySame(x0, x1) && numericallySame(y0, y1)) {
          xy[n - 2] = x0;
          xy[n - 1] = y0;
        } else {
          add(x0, y0);
        }
      }
    }
    if (xy.length > n) {
      xy = Arrays.copyOf(xy, n);
    }
  }

  private boolean numericallySame(double a, double b) {
    if (Double.isNaN(a) || Double.isNaN(b)) {
      return false;
    } else if (a == b) {
      // this will take care of case where both are zero
      return true;
    } else {
      double threshold = Math.ulp(((Math.abs(a) + Math.abs(b))) / 2.0) * 16;
      double absDelta = Math.abs(a - b);
      return absDelta <= threshold;
    }
  }


  /**
   * Gets the serialized identification code for the contour.
   * When used with the ContourBuilder, this
   * value gives a unique serial ID assigned when the contour is constructed.
   * This value should not be confused with the contour interval
   * or the left and right side index values.
   *
   * @return an integer value.
   */
  public int getContourId() {
    return contourId;
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

  /**
   * Indicates whether the contour is an interior or perimeter contour.
   * Note: future implementations may include additional types.
   *
   * @return a valid enumeration instance
   */
  public ContourType getContourType() {
    if (rightIndex == -1) {
      return ContourType.Boundary;
    } else {
      return ContourType.Interior;
    }
  }

  /**
   * Indicates whether the contour is a boundary contour.
   * @return true if the contour is a boundary; otherwise, false.
   */
  public boolean isBoundary(){
    return rightIndex == -1;
  }

  /**
   * Indicates that the contour forms a closed loop
   *
   * @return true if the contour forms a closed loop; otherwise false
   */
  public boolean isClosed() {
    return closedLoop;
  }

  /**
   * Get the bounds of the contour.
   * @return a valid instance.
   */
  public Rectangle2D getBounds(){
    Rectangle2D r2d = new Rectangle2D.Double(xy[0], xy[1], 0, 0);
    for(int i=1; i<n/2; i++){
      r2d.add(xy[i*2], xy[i*2+1]);
    }
    return r2d;

  }
  @Override
  public String toString() {
    String cString = "";
    if (n >= 4) {
      double x0 = xy[0];
      double y0 = xy[1];
      double x1 = xy[n - 2];
      double y1 = xy[n - 1];
      cString = String.format("(x0,y0)=(%f,%f)  (x1,y1)=(%f,%f)", x0, y0, x1, y1);
    }

    return "Contour " + contourId
      + ": L=" + leftIndex
      + ", R=" + rightIndex
      + ", z=" + z
      + ", closed=" + closedLoop
      + "  " + cString;
  }


  /**
   * Gets the index for the value of the input contour array that
   * was used to build this contour, or a notional value if this
   * instance is a boundary contour.
   * <p>
   * It is strongly recommended that application code check
   * to see if this instance is a boundary contour before using the contour index.
   * @return a value in the range 0 to the length of the input z contour array.
   */
  public int getContourIndex(){
     if(rightIndex<0){
       // this is a boundary contour.  the contour index value
       // is not truly meaningful.
       return leftIndex;
     }
     return leftIndex-1;
  }

  /**
   * Get the index for the region lying to the left of the contour.
   *
   * @return an integer in the range 0 to nContour, or -1 if the contour
   * borders a null-data area
   */
  public int getLeftIndex() {
    return leftIndex;
  }

  /**
   * Get the index for the region lying to the right of the contour.
   *
   * @return an integer in the range 0 to nContour, or -1 if the contour
   * borders a null-data area
   */
  public int getRightIndex() {
    return rightIndex;
  }

  /**
   * Null-out any resources that were required for building the contours
   * or regions, but are no longer needed.
   */
  void cleanUp() {
    startTip = null;
    terminalTip = null;
  }

}
