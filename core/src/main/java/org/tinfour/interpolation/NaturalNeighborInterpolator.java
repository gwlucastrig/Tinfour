/*
 * Copyright 2013 Gary W. Lucas.
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
 * ------  --------- -------------------------------------------------
 * 03/2013 G. Lucas  Created as part of IncrementalTIN
 * 08/2013 G. Lucas  Replaced stack-based edge flipping algorithm with
 *                    Bowyer-Watson approach that doesn't disturb TIN.
 * 05/2014 G. Lucas  Broken into separate class
 * 08/2015 G. Lucas  Refactored for QuadEdge class
 * 09/2015 G. Lucas  Added BarycenterCoordinateDeviation concept as a way
 *                     to double-check correctness of implementation and
 *                     adjusted calculation to map vertex coordinates so that
 *                     the query point is at the origin.
 * 01/2016 G. Lucas  Added calculation for surface normal
 * 11/2016 G. Lucas  Added support for constrained Delaunay
 * 12/2020 G. Lucas  Correction for misbehavior near edges of problematic
 *                     meshes. Removed ineffective surface normal calculation.
 *                     Modified barycentric coordinates to conform to Sibson's
 *                     definition and properly support transition across
 *                     neighboring point sets.
 *
 * Notes:
 *
 *   As a reminder, this class must not perform direct comparison of
 * edges because it is used not just for the direct (QuadEdge) implementation
 * of edges, but also for the VirtualEdge implementation.  Therefore,
 * comparisons must be conducted by invoking the equals() method.
 * -----------------------------------------------------------------------
 */
package org.tinfour.interpolation;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.Circumcircle;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;

/**
 * Provides interpolations based on Sibson&#039;s Natural Neighbor Interpolation
 * method. See Sibson, Robin, "A Brief Description of Natural Neighbor
 * Interpolation". <i>Interpreting Multivariate Data</i>. Ed. Barnett, Vic.
 * England. John Wiley &amp; Sons (1981).
 */
public class NaturalNeighborInterpolator implements IInterpolatorOverTin {

  // tolerance for identical vertices.
  // the tolerance factor for treating closely spaced or identical vertices
  // as a single point.
  final private double vertexTolerance2; // square of vertexTolerance;
  final private double inCircleThreshold;
  final private double halfPlaneThreshold;

  final IIncrementalTin tin;
  final GeometricOperations geoOp;
  IIncrementalTinNavigator navigator;

  private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

  private double barycentricCoordinateDeviation;

  // diagnostic counts
  private long sumN;
  private long sumSides;
  private long nInCircle;
  private long nInCircleExtended;

  /**
   * Construct an interpolator that operates on the specified TIN.
   * Because the interpolator will access the TIN on a read-only basis,
   * it is possible to construct multiple instances of this class and
   * allow them to operate in parallel threads.
   * <h1>Important Synchronization Issue</h1>
   * To improve performance, the classes in this package
   * frequently maintain state-data about the TIN that can be reused
   * for query to query. They also avoid run-time overhead by not
   * implementing any kind of Java synchronization or or even the
   * concurrent-modification testing provided by the
   * Java collection classes. If an application modifies the TIN, instances
   * of this class will not be aware of the change. In such cases,
   * interpolation methods may fail by either throwing an exception or,
   * worse, returning an incorrect value. The onus is on the calling
   * application to manage the use of this class and to ensure that
   * no modifications are made to the TIN between interpolation operations.
   * If the TIN is modified, the internal state data for this class must
   * be reset using a call to resetForChangeToTIN().
   *
   * @param tin a valid instance of an incremental TIN.
   */
  public NaturalNeighborInterpolator(IIncrementalTin tin) {
    Thresholds thresholds = tin.getThresholds();
    geoOp = new GeometricOperations(thresholds);

    vertexTolerance2 = thresholds.getVertexTolerance2();
    inCircleThreshold = thresholds.getInCircleThreshold();
    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();

    this.tin = tin;
    navigator = tin.getNavigator();
  }

  /**
   * Used by an application to reset the state data within the interpolator
   * when the content of the TIN may have changed. Resetting the state data
   * unnecessarily may result in a minor performance reduction when processing
   * a large number of interpolations, but is otherwise harmless.
   */
  @Override
  public void resetForChangeToTin() {
    navigator.resetForChangeToTin();
  }

  /**
   * Perform interpolation using Sibson's C0 method. This interpolation
   * develops a continuous surface, and provides first derivative
   * continuity at all except the input vertex points.
   * <p>
   * The domain of the interpolator is limited to the interior
   * of the convex hull. Methods for extending to the edge of the
   * TIN or beyond are being investigated.
   * <p>
   * The interpolation is treated as undefined at points that lie
   * directly on a constrained edge.
   *
   * @param x the x coordinate for the interpolation point
   * @param y the y coordinate for the interpolation point
   * @param valuator a valid valuator for interpreting the z value of each
   * vertex or a null value to use the default.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a Double&#46;NaN.
   */
  @Override
  public double interpolate(double x, double y, IVertexValuator valuator) {

    // in the logic below, we access the Vertex x and z coordinates directly
    // but we use the getZ() method to get the z value.  Some vertices
    // may actually be VertexMergerGroup instances and so the Z value must
    // be selected according to whatever rules were configured for the TIN.
    // Also, all coordinates are adjusted with an offset (-x, -y) so that the
    // interpolation point is at the origin. This adjustment is made to
    // compensate for the fact that map-projected coordinates for adjacent
    // vertices often have very large coordinates (in the millions)
    // and that the products of pairs of such values would be large enough
    // to wash out the precision.
    IVertexValuator vq = valuator;
    if (vq == null) {
      vq = defaultValuator;
    }
    List<IQuadEdge> eList = getBowyerWatsonEnvelope(x, y);
    int nEdge = eList.size();
    if (nEdge == 0) {
      // (x,y) is outside defined area
      return Double.NaN;
    } else if (nEdge == 1) {
      // (x,y) is an exact match with the one edge in the list
      IQuadEdge e = eList.get(0);
      Vertex v = e.getA();
      return vq.value(v);

    }

    sumN++;
    sumSides+=eList.size();
    // The eList contains a series of edges definining the cavity
    // containing the polygon.
    double[] w = this.getBarycentricCoordinates(eList, x, y);
    if (w == null) {
      // the coordinate is on the perimeter, no Barycentric coordinates
      // are available.
      return Double.NaN;
    }
    double zSum = 0;
    int k = 0;
    for (IQuadEdge s : eList) {
      double z = vq.value(s.getA());
      zSum += w[k++] * z;
    }
    return zSum;

  }

  /**
   * Gets the deviation of the computed equivalent of the input query (x,y)
   * coordinates based on barycentric coordinates. As a byproduct, Sibson's
   * method can be used to compute the coordinates of the query point
   * by combining the normalized interpolating weights with the coordinates
   * of the vertices. The normalized weights are, in fact, Barycentric
   * Coordinates for the query point. While the computed equivalent should
   * be an exact match for the query point, errors in implementation
   * or numeric errors due to float-point precision limitations would
   * result in a deviation. Thus, this method provides a diagnostic
   * on the most recent interpolation. A large non-zero value indicates a
   * potential implementation problem. A small non-zero value indicates an
   * error due to numeric issues.
   *
   * @return a positive value, ideally zero but usually a small number
   * slightly larger than that.
   */
  public double getBarycentricCoordinateDeviation() {
    return barycentricCoordinateDeviation;
  }

  /**
   * Gets a list of edges for the polygonal cavity that would be created
   * as part of the Bowyer-Watson insertion algorithm. If the list is empty,
   * it indicates that TIN was not bootstrapped or the query was to the
   * exterior of the TIN.
   *
   * @param x A Cartesian coordinate in the coordinate system used for the TIN
   * @param y A Cartesian coordinate in the coordinate system used for the TIN
   * @return a valid, potentially empty, list.
   */
  public List<IQuadEdge> getBowyerWatsonEnvelope(double x, double y) {
    // in the logic below, we access the Vertex x and y coordinates directly
    // but we use the getZ() method to get the z value.  Some vertices
    // may actually be VertexMergerGroup instances

    ArrayList<IQuadEdge> eList = new ArrayList<>();

    IQuadEdge locatorEdge = navigator.getNeighborEdge(x, y);
    if (locatorEdge == null) {
      // this would happen only if the TIN were not bootstrapped
      return eList;
    }

    IQuadEdge e = locatorEdge;
    IQuadEdge f = e.getForward();
    IQuadEdge r = e.getReverse();

    Vertex v0 = e.getA();
    Vertex v1 = e.getB();
    Vertex v2 = e.getForward().getB();

    double h;

    // by the way the getNeighborEdge() method is defined, if
    // the query is outside the TIN or on the perimeter edge,
    // the edge v0, v1 will be the perimeter edge and v2 will
    // be the ghost vertex (e.g. a null). In either case, v2 will
    // not be defined. So, if v2 is null, the NNI interpolation is not defined.
    if (v2 == null) {
      return eList; // empty list, NNI undefined.
    }

    if (v0.getDistanceSq(x, y) < vertexTolerance2) {
      eList.add(e);
      return eList;  // edge starting with v0
    }

    if (v1.getDistanceSq(x, y) < vertexTolerance2) {
      eList.add(e.getForward());
      return eList; // edge starting with v1
    }

    if (v2.getDistanceSq(x, y) < vertexTolerance2) {
      eList.add(e.getReverse());
      return eList; // edge starting with v2
    }

    if (e.isConstrained()) {
      h = geoOp.halfPlane(v0.x, v0.y, v1.x, v1.y, x, y);
      if (h < halfPlaneThreshold) {
        // (x,y) is on the edge v0, v1)
        return eList; // empty list, NNI undefined.
      }
    }

    if (f.isConstrained()) {
      h = geoOp.halfPlane(v1.x, v1.y, v2.x, v2.y, x, y);
      if (h < halfPlaneThreshold) {
        return eList; // empty list, NNI undefined.
      }
    }

    if (r.isConstrained()) {
      h = geoOp.halfPlane(v2.x, v2.y, v0.x, v0.y, x, y);
      if (h < halfPlaneThreshold) {
        return eList; // empty list, NNI undefined.
      }
    }

    // ------------------------------------------------------
    // The fundamental idea of natural neighbor interpolation is
    // based on measuring how the local geometry of a Voronoi
    // Diagram would change if a new vertex were inserted.
    // (recall that the Voronoi is the dual of a Delaunay Triangulation).
    // Thus the NNI interpolation has common element with an
    // insertion into a TIN.  In writing the code below, I have attempted
    // to preserve similarities with the IncrementalTIN insertion logic
    // where appropriate.
    //
    // Step 1 -----------------------------------------------------
    // Create an array of edges that would connect to the radials
    // from an inserted vertex if it were added at coordinates (x,y).
    // This array happens to describe a Thiessen Polygon around the
    // inserted vertex.
    ArrayDeque<IQuadEdge> stack = new ArrayDeque<>();
    IQuadEdge c, n0, n1;

    c = locatorEdge;
    while (true) {
      n0 = c.getDual();
      n1 = n0.getForward();

      if (c.isConstrained()) {
        // the search does not extend past a constrained edge.
        // set h=-1 to suppress further testing and add th edge.
        h = -1;
      } else if (n1.getB() == null) {
        // the search has reached a perimeter edge
        // just add the edge and continue.
        h = -1;
      } else {
        nInCircle++;
        // test for the Delaunay inCircle criterion.
        // see notes about efficiency in the IncrementalTIN class.
        double a11 = n0.getA().x - x;
        double a21 = n1.getA().x - x;
        double a31 = n1.getB().x - x;

        // column 2
        double a12 = n0.getA().y - y;
        double a22 = n1.getA().y - y;
        double a32 = n1.getB().y - y;

        h = (a11 * a11 + a12 * a12) * (a21 * a32 - a31 * a22)
          + (a21 * a21 + a22 * a22) * (a31 * a12 - a11 * a32)
          + (a31 * a31 + a32 * a32) * (a11 * a22 - a21 * a12);
        if (-inCircleThreshold < h && h < inCircleThreshold) {
          nInCircleExtended++;
          h = geoOp.inCircleQuadPrecision(
            n0.getA().x, n0.getA().y,
            n1.getA().x, n1.getA().y,
            n1.getB().x, n1.getB().y,
            x, y);
        }
      }

      if (h >= 0) {
        // The vertex is within the circumcircle the associated
        // triangle.  The Thiessen triangle will extend to include
        // that triangle and, perhaps, its neighbors.
        // So continue the search.
        stack.addFirst(n0);
        c = n1;
      } else {
        eList.add(c);
        c = c.getForward();
        IQuadEdge p = stack.peekFirst();
        while (c.equals(p)) {
          stack.remove();
          c = c.getDual().getForward();
          p = stack.peekFirst();
        }
        if (c.equals(locatorEdge)) {
          break;
        }
      }
    }

    return eList;
  }

  @Override
  public String getMethod() {
    return "Natural Neighbor (Sibson's C0)";
  }

  IQuadEdge checkTriangleVerticesForMatch(
    final IQuadEdge baseEdge,
    final double x,
    final double y,
    final double distanceTolerance2) {
    IQuadEdge sEdge = baseEdge;
    IQuadEdge sFwd = sEdge.getForward();
    double dMin = sEdge.getA().getDistanceSq(x, y);

    double dFwd = sFwd.getA().getDistanceSq(x, y);
    if (dFwd < dMin) {
      sEdge = sFwd;
      dMin = dFwd;
    }
    Vertex v2 = sEdge.getForward().getB();
    if (v2 != null && v2.getDistanceSq(x, y) < dMin) {
      return sFwd.getForward();
    } else {
      return sEdge;
    }
  }

  @Override
  public boolean isSurfaceNormalSupported() {
    return false;
  }

  /**
   * Note implemented at this time.
   * Gets the unit normal to the surface at the position of the most
   * recent interpolation. The unit normal is computed based on the
   * partial derivatives of the surface polynomial evaluated at the
   * coordinates of the query point. Note that this method
   * assumes that the vertical and horizontal coordinates of the
   * input sample points are isotropic.
   *
   *
   * @return if defined, a valid array of dimension 3 giving
   * the x, y, and z components of the unit normal, respectively; otherwise,
   * a zero-sized array.
   */
  @Override
  public double[] getSurfaceNormal() {
    return new double[0];
  }

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
   * @param polygon list of edges defining a non-self-intersecting,
   * potentially non-convex polygon.
   * @param x the x coordinate of the reference point
   * @param y the y coordinate of the reference point
   * @return if successful, a valid array; otherwise a null.
   */
  public double[] getBarycentricCoordinates(
    List<IQuadEdge> polygon,
    double x,
    double y) {

    int nEdge = polygon.size();
    if (nEdge < 3) {
      return new double[0];
    }

    // The eList contains a series of edges definining the cavity
    // containing the polygon.
    Vertex a, b, c;
    Circumcircle c0 = new Circumcircle();
    Circumcircle c1 = new Circumcircle();
    Circumcircle c2 = new Circumcircle();
    Circumcircle c3 = new Circumcircle();
    IQuadEdge e0, e1, n, n1;
    double x0, y0, x1, y1, wThiessen, wXY, wDelta;
    double wSum = 0;
    double[] weights = new double[nEdge];
    for (int i0 = 0; i0 < nEdge; i0++) {
      int i1 = (i0 + 1) % nEdge;
      e0 = polygon.get(i0);
      e1 = polygon.get(i1);
      a = e0.getA();
      b = e1.getA(); // same as e0.getB();
      c = e1.getB();
      double ax = a.getX() - x;
      double ay = a.getY() - y;
      double bx = b.getX() - x;
      double by = b.getY() - y;
      double cx = c.getX() - x;
      double cy = c.getY() - y;

      x0 = (ax + bx) / 2;
      y0 = (ay + by) / 2;
      x1 = (bx + cx) / 2;
      y1 = (by + cy) / 2;

      // for the first edge processed, the code needs to initialize values
      // for c0 and c3.  But after that, the code can reuse values from
      // the previous calculation.
      if (i0 == 0) {
        geoOp.circumcircle(ax, ay, bx, by, 0, 0, c0);
        Vertex nb = e0.getForward().getB();
        geoOp.circumcircle(ax, ay, bx, by, nb.getX() - x, nb.getY() - y, c3);
      } else {
        c0.copy(c1);
      }

      geoOp.circumcircle(bx, by, cx, cy, 0, 0, c1);

      // compute the reduced "component area" of the Theissen polygon
      // constructed around point B, the second point of edge[i0].
      wXY = (x0 * c0.getY()        - c0.getX() * y0)
        +   (c0.getX() * c1.getY() - c1.getX() * c0.getY())
        +   (c1.getX() * y1        - x1 * c1.getY());

      // compute the full "component area" of the Theissen polygon
      // constructed around point B, the second point of edge[i0]
      n = e0.getForward();
      wThiessen = x0 * c3.getY() - c3.getX() * y0;
      while (!(n.equals(e1))) {
        n1 = n.getDual();
        n = n1.getForward();
        c2.copy(c3);
        a = n1.getA();
        b = n.getA();  // same as n1.getB();
        c = n.getB();
        ax = a.getX() - x;
        ay = a.getY() - y;
        bx = b.getX() - x;
        by = b.getY() - y;
        cx = c.getX() - x;
        cy = c.getY() - y;
        geoOp.circumcircle(ax, ay, bx, by, cx, cy, c3);
        wThiessen += c2.getX() * c3.getY() - c3.getX() * c2.getY();
      }
      wThiessen += c3.getX() * y1 - x1 * c3.getY();

      // Compute wDelta, the amount of area that the Theissen polygon
      // constructed around vertex B would yield to an insertion at
      // the query point.
      //    for convenience, both the wXY and wThiessen weights were
      // computed in a clockwise order, which means they are the
      // negative of what we need for the weight computation, so
      // negate them and  -(wTheissen-wXY) becomes wXY-wTheissen
      // Also, there would normally be a divide by 2 factor from the
      // shoelace area formula, but that is ommitted because it will
      // drop out when we unitize the sum of the set of the weights.
      wDelta = wXY - wThiessen;
      wSum += wDelta;
      weights[i1] = wDelta;
    }

    // Normalize the weights
    for (int i = 0; i < weights.length; i++) {
      weights[i] /= wSum;
    }

    // Compute the barycentric coordinate deviation. This is a purely diagnostic
    // value and computing it adds some small overhead to the interpolation.
    double xSum = 0;
    double ySum = 0;
    int k = 0;
    for (IQuadEdge s : polygon) {
      Vertex v = s.getA();
      xSum += weights[k] * (v.getX() - x);
      ySum += weights[k] * (v.getY() - y);
      k++;
    }
    barycentricCoordinateDeviation
      = Math.sqrt(xSum * xSum + ySum * ySum);

    return weights;
  }

  /**
   * Prints a set of diagnostic information describing the operations
   * used to interpolate points.
   * @param ps a valid print stream such as System&#46;out.
   */
  public void printDiagnostics(PrintStream ps){
    long  nC = geoOp.getCircumcircleCount();
    long  nCE = geoOp.getExtendedCircumcircleCount();
    ps.format("N inCircle:          %12d%n", nInCircle);
    ps.format("N inCircle extended: %12d%n", nInCircleExtended);
    ps.format("N circumcircle:      %12d%n", nC);
    ps.format("N circumcircle ext:  %12d%n", nCE);
    long n = sumN>0 ? sumN:1;
    ps.format("Avg circumcircles per interpolation: %9.6f%n", (double)nC/n);
    ps.format("Avg sides per Theissen polygon:      %9.6f%n", (double)sumSides/n);
  }


  /**
   * Clears any diagnostic information accumulated during processing.
   */
  public void clearDiagnostics(){
    geoOp.clearDiagnostics();
    nInCircle = 0;
    nInCircleExtended = 0;
    sumSides = 0;
    sumN = 0;
  }
}
