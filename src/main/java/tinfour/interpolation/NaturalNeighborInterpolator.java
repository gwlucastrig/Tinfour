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
 *
 * Notes:
 *
 *   As a reminder, this class must not perform direct comparison of
 * edges because it is used not just for the direct (QuadEdge) implementation
 * of edges, but also for the VirtualEdge implementation.  Therefore,
 * comparisons must be conducted by invoking the equals() method.
 * -----------------------------------------------------------------------
 */
package tinfour.interpolation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.Circumcircle;
import tinfour.common.GeometricOperations;
import tinfour.common.IIncrementalTin;
import tinfour.common.INeighborEdgeLocator;
import tinfour.common.IQuadEdge;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;

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
  INeighborEdgeLocator locator;

  private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

  private double xQuery;
  private double yQuery;
  private double barycentricCoordinateDeviation;

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
    double nominalPointSpacing = tin.getNominalPointSpacing();
    Thresholds thresholds = new Thresholds(nominalPointSpacing);
    geoOp = new GeometricOperations(thresholds);

    vertexTolerance2 = thresholds.getVertexTolerance2();
    inCircleThreshold = thresholds.getInCircleThreshold();
    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();

    this.tin = tin;
    locator = tin.getNeighborEdgeLocator();
  }

  /**
   * Used by an application to reset the state data within the interpolator
   * when the content of the TIN may have changed. Reseting the state data
   * unnecessarily may result in a minor performance reduction when processing
   * a large number of interpolations, but is otherwise harmless.
   */
  @Override
  public void resetForChangeToTin() {
    locator.resetForChangeToTin();
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
    // in the logic below, we access the Vertex x and y coordinates directly
    // but we use the getZ() method to get the z value.  Some vertices
    // may actually be VertexMergerGroup instances and so the Z value must
    // be selected according to whatever rules were configured for the TIN.
    // Also, all coordinates are adjusted with an offset (-x, -y) so that the
    // interpolation point is at the origin. This adjustment is made to
    // compensate for the fact that map-projected coordinates for adjacent
    // vertices often have very large coordinates (in the millions)
    // and that the products of pairs of such values would be large enough
    // to wash out the precision.

    xQuery = x;
    yQuery = y;
    IVertexValuator vq = valuator;
    if (vq == null) {
      vq = defaultValuator;
    }
    List<IQuadEdge> eList = this.getBowyerWatsonPolygon(x, y);
    int nEdge = eList.size();
    if (nEdge == 0) {
      // (x,y) is outside defined area
      return Double.NaN;
    } else if (nEdge == 1) {
      // (x,y) matches the first vertex of the one edge in the list
      IQuadEdge e = eList.get(0);
      Vertex v = e.getA();
      return vq.value(v);

    }

    // The eList contains a series of edges definining the cavity
    // containing the polygon.
    Vertex a, b, c;
    Circumcircle c0 = new Circumcircle();
    Circumcircle c1 = new Circumcircle();
    IQuadEdge e0, e1, n, n1, ed0, ed1;
    double x0, y0, x1, y1, wThiessen, wXY, wDelta;
    double barycenterX = 0;
    double barycenterY = 0;
    double wSum = 0;
    double zSum = 0;
    for (int i0 = 0; i0 < nEdge; i0++) {
      int i1 = (i0 + 1) % nEdge;
      e0 = eList.get(i0);
      e1 = eList.get(i1);
      a = e0.getA();
      b = e1.getA(); // same as e0.getB();
      c = e1.getB();
      double ax = a.getX() - x;
      double ay = a.getY() - y;
      double bx = b.getX() - x;
      double by = b.getY() - y;
      double cx = c.getX() - x;
      double cy = c.getY() - y;

      ed0 = e0.getDual();
      Vertex d0 = ed0.getForward().getB();
      if (d0 == null) {
        x0 = (ax + bx) / 2;
        y0 = (ay + by) / 2;
      } else {
        c0.compute(bx, by, ax, ay, d0.getX() - x, d0.getY() - y);
        x0 = c0.getX();
        y0 = c0.getY();
      }
      ed1 = e1.getDual();
      Vertex d1 = ed1.getForward().getB();
      if (d1 == null) {
        x1 = (bx + cx) / 2;
        y1 = (by + cy) / 2;
      } else {
        c1.compute(cx, cy, bx, by, d1.getX() - x, d1.getY() - y);
        x1 = c1.getX();
        y1 = c1.getY();
      }

      c0.compute(ax, ay, bx, by, 0, 0);
      c1.compute(bx, by, cx, cy, 0, 0);
      wXY = x0 * c0.getY()
        - c0.getX() * y0
        + c0.getX() * c1.getY()
        - c1.getX() * c0.getY()
        + c1.getX() * y1
        - x1 * c1.getY();

      n = e0.getForward();
      Vertex nb = n.getB();
      c1.compute(ax, ay, bx, by, nb.getX() - x, nb.getY() - y);
      wThiessen = x0 * c1.getY() - c1.getX() * y0;
      while (!(n.equals(e1))) {
        n1 = n.getDual();
        n = n1.getForward();
        c0.copy(c1);
        a = n1.getA();
        b = n.getA();  // same as n1.getB();
        c = n.getB();
        ax = a.getX() - x;
        ay = a.getY() - y;
        bx = b.getX() - x;
        by = b.getY() - y;
        cx = c.getX() - x;
        cy = c.getY() - y;
        c1.compute(ax, ay, bx, by, cx, cy);
        wThiessen += c0.getX() * c1.getY() - c1.getX() * c0.getY();
      }
      wThiessen += c1.getX() * y1 - x1 * c1.getY();

      // for convenience, both the wXY and wThiessen weights were
      // computed in a clockwise order, which means they are the
      // negative of what we need for the weight computation, so
      // negate them.
      wDelta = -(wThiessen - wXY);  // the /2 here is unnecessary
      wSum += wDelta;
      b = e0.getB();
      zSum += wDelta * vq.value(b);
      barycenterX += wDelta * b.getX();
      barycenterY += wDelta * b.getY();
    }
    barycenterX /= wSum;
    barycenterY /= wSum;
    double xError = barycenterX - x;
    double yError = barycenterY - y;
    barycentricCoordinateDeviation
      = Math.sqrt(xError * xError + yError * yError);

    return zSum / wSum;
  }

  /**
   * Perform interpolation using Sibson's C0 method and a test algorithm
   * based on computing the barycentric coordinates rather than
   * through the circumcenter approach used for the original
   * interpolation method. This method was developed to test the
   * getBarycentricCoordinates method and investigate replacing the
   * circumcenter method with simpler code. However, the results appear
   * to be slightly less accurate than the original, so further investigation
   * is required.
   * <p>
   * The domain of the interpolator is limited to the interior
   * of the convex hull. Methods for extending to the edge of the
   * TIN or beyond are being investigated.
   *
   * @param x the x coordinate for the interpolation point
   * @param y the y coordinate for the interpolation point
   * @param valuator a valid valuator for interpreting the z value of each
   * vertex or a null value to use the default.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a NaN.
   */
  public double interpolateUsingTestMethod(
    double x, double y, IVertexValuator valuator) {
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
    List<IQuadEdge> eList = this.getBowyerWatsonPolygon(x, y);
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

    // The eList contains a series of edges definining the cavity
    // containing the polygon.
    double[] w = this.getBarycentricCoordinates(eList, x, y);
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
  public List<IQuadEdge> getBowyerWatsonPolygon(double x, double y) {
    // in the logic below, we access the Vertex x and y coordinates directly
    // but we use the getZ() method to get the z value.  Some vertices
    // may actually be VertexMergerGroup instances

    ArrayList<IQuadEdge> eList = new ArrayList<>();

    IQuadEdge locatorEdge = locator.getNeigborEdge(x, y);
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
  public boolean isSurfaceNormalSupported(){
    return true;
  }

  /**
   * Gets the unit normal to the surface at the position of the most
   * recent interpolation. The unit normal is computed based on the
   * partial derivatives of the surface polynomial evaluated at the
   * coordinates of the query point. Note that this method
   * assumes that the vertical and horizontal coordinates of the
   * input sample points are isotropic.
   * <p>NOT COMPLETELY TESTED AND VERIFIED YET. WHen I visually inspect
   * the results, the surface appears to be unexpectedly blurred and flattened.
   * At the very least, I think a code review is required to determine
   * whether the implementation is correct or if the behavior I am seeing
   * is just the way the algorithm works.
   * @return if defined, a valid array of dimension 3 giving
   * the x, y, and z components of the unit normal, respectively; otherwise,
   * a zero-sized array.
   */
  @Override
  public double[] getSurfaceNormal() {

    List<IQuadEdge> bwList = this.getBowyerWatsonPolygon(xQuery, yQuery);
    int nEdge = bwList.size();
    if (nEdge == 0) {
      // (x,y) is outside defined area
      return new double[0];
    } else if (nEdge == 1) {
            // (x,y) is an exact match with the one edge in the list
      // so the bwList is replaced with a connected polygon list
      // and used the same way
      bwList = getConnectedPolygon(bwList.get(0));
    }

    double[] bwWeights = getBarycentricCoordinates(bwList, xQuery, yQuery);
    double xNormal = 0;
    double yNormal = 0;
    double zNormal = 0;
    int i = 0;
    for (IQuadEdge e : bwList) {
      List<IQuadEdge> rim = getConnectedPolygon(e);
      Vertex hub = e.getA();
      double xHub = hub.getX();
      double yHub = hub.getY();
      double zHub = hub.getZ();
      double[] w = getBarycentricCoordinates(rim, xHub, yHub);
      if (w == null) {
              // edge e is on the perimeter and does not have valid
        // barycentric coordinates.  Also, some of the edges in the
        // rim may start with ghost vertices. Since no barycentric
        // coordinates are available, we use inverse distance weighting
        // for those rim edges that are not ghosts and have a valid
        // geometry.
        w = this.repairRim(e, rim, xHub, yHub);
        if (rim.isEmpty()) {
          return new double[0]; // not expected
        }
      }
      int k = 0;
      double xSum = 0;
      double ySum = 0;
      double zSum = 0;
      for (IQuadEdge edge : rim) {
        Vertex v = edge.getA();
        double x = v.getX() - xHub;
        double y = v.getY() - yHub;
        double z = v.getZ() - zHub;
        double s = Math.sqrt(x * x + y * y);
        double nx = -z * x / s;
        double ny = -z * y / s;
        double nz = s;
        double n = w[k++] / Math.sqrt(nx * nx + ny * ny + nz * nz);
        xSum += nx * n;
        ySum += ny * n;
        zSum += nz * n;
      }
      double n = bwWeights[i++] / Math.sqrt(xSum * xSum + ySum * ySum + zSum * zSum);
      xNormal += xSum * n;
      yNormal += ySum * n;
      zNormal += zSum * n;
    }

    double[] normal = new double[3];
    double n = Math.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
    normal[0] = xNormal / n;
    normal[1] = yNormal / n;
    normal[2] = zNormal / n;

    return normal;
  }

   // Rebuilds a rim that contains ghost edges. Computes new weights based
  // on inverse distance weighting rather than barycentric coordinates.
  private double[] repairRim(IQuadEdge e, List<IQuadEdge> rim, double xHub, double yHub) {
    double[] w = new double[rim.size()];
    rim.clear();
    int k = 0;
    double wSum = 0;
    IQuadEdge s = e;
    do {
      IQuadEdge f = s.getForward();
      Vertex v = f.getA();
      if (v != null) {
        double dx = v.getX() - xHub;
        double dy = v.getY() - yHub;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > 0) {
          double weight = 1.0/d;
          w[k++] = weight;
          wSum += weight;
          rim.add(f);
        }

      }
      s = s.getReverse().getDual();
    } while (!s.equals(e));

    if (wSum == 0) {
      // not expected
      rim.clear();
    } else {
      for (int i = 0; i < k; i++) {
        w[i] /= wSum;
      }
    }
    return w;
  }

  /**
   * Gets a polygon consisting of edges connected to
   * the specified edge (in effect providing the set of vertices
   * connected to the starting vertex of the specified edge).
   * The polygon is ordered in counterclockwise order.
   *
   * @param e the starting edge
   *
   * @return a valid list of edges (some of which may be
   * perimeter or ghost edges).
   */
  public List<IQuadEdge> getConnectedPolygon(IQuadEdge e) {
    List<IQuadEdge> eList = new ArrayList<>();

    IQuadEdge s = e;
    do {
      eList.add(s.getForward());
      s = s.getReverse().getDual();
    } while (!s.equals(e));
    return eList;
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
   * If the point is on the perimeter of the TIN, this method will
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
    barycentricCoordinateDeviation = Double.NaN;  // start off pessimistic
    int nEdge = polygon.size();
    IQuadEdge p0 = polygon.get(nEdge - 1);
    IQuadEdge p1 = polygon.get(0);
    double[] weights = new double[nEdge];
    int k = 0;

    double xSum = 0;
    double ySum = 0;
    double wSum = 0;
    Vertex v0 = p0.getA();
    Vertex v1 = p1.getA();
    if (v0 == null || v1 == null) {
      // the search extends to the exterior, indicating
      // that the reference point is on the perimeter.
      // no barycentric solution is feasible.
      return null;
    }
    double x0 = v0.getX() - x;
    double y0 = v0.getY() - y;
    double x1 = v1.getX() - x;
    double y1 = v1.getY() - y;
    double r0 = Math.sqrt(x0 * x0 + y0 * y0);
    double r1 = Math.sqrt(x1 * x1 + y1 * y1);
    double t1 = (r0 * r1 - (x0 * x1 + y0 * y1)) / (x0 * y1 - x1 * y0);
    for (IQuadEdge e1 : polygon) {
      double t0 = t1;
      x0 = x1;
      y0 = y1;
      r0 = r1;
      v1 = e1.getB();
      if (v1 == null) {
        // the reference point is on the perimeter
        return null;
      }
      x1 = v1.getX() - x;
      y1 = v1.getY() - y;
      r1 = Math.sqrt(x1 * x1 + y1 * y1);
      t1 = (r0 * r1 - (x0 * x1 + y0 * y1)) / (x0 * y1 - x1 * y0);
      double w = (t0 + t1) / r0;
      xSum += w * x0;
      ySum += w * y0;
      wSum += w;
      weights[k++] = w;
    }

    // normalize the weights
    for (int i = 0; i < k; i++) {
      weights[i] = weights[i] / wSum;
    }

    wSum = 0;
    for (int i = 0; i < k; i++) {
      wSum += weights[i];
    }

    barycentricCoordinateDeviation = Math.sqrt(xSum * xSum + ySum * ySum);

    return weights;
  }

}
