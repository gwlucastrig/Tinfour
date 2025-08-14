/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 03/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.alphashape;

import java.awt.geom.Path2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.utils.Polyside;

/**
 * Provides methods and elements to support the creation of an alpha
 * shape from a Delaunay triangulation. The alpha shape concept introduced by
 * Edelsbrunner, et al., is a not-necessarily
 * convex bounding polygon that defines a region covered by a set of vertices.
 * The alpha shape may also include "holes" within the bounds of the outer
 * polygon.
 * <p>
 * <strong>Instances are coupled to the source triangulation: </strong> When
 * an instance of this class is created, it receives several elements
 * from the source Incremental TIN instance supplied in the constructor.
 * Therefore, it is imperative that the source triangulation not be modified
 * while the AlphaShape instance is still being used.
 * <p>
 * <strong>References:</strong>
 * <cite>H. Edelsbrunner, D. Kirkpatrick and R. Seidel,
 * "On the shape of a set of points in the plane,"
 * in IEEE Transactions on Information Theory, vol. 29, no. 4, pp. 551-559,
 * July 1983, doi: 10.1109/TIT.1983.1056714.</cite>
 */
public class AlphaShape {

  private final IIncrementalTin tin;
  private final double radius;
  private final double areaMinThreshold;
  private final List<AlphaPart> alphaParts = new ArrayList<>();

  /**
   * Constructs an alpha shape based on the specified Delaunay triangulation.
   * Note that this constructor takes the specification of a radius for the
   * alpha-circle features used to develop the alpha shape. Various authors
   * in the published literature use different definitions for the alpha
   * parameter. Software libraries other than Tinfour may use different
   * definitions alpha parameter than the simple radius.
   *
   * @param tin a valid instance of a Triangulated Irregular Network (TIN).
   * @param radius the radius for alpha circle computations.
   */
  public AlphaShape(IIncrementalTin tin, double radius) {
    this.tin = tin;
    this.radius = radius;
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException(
        "Invalid specification, incremental TIN is not bootstrapped");
    }

    // In some cases, the geometry may give rise to polygons that are
    // of nearly zero area. This is most common when processing the long
    // edges near the perimeter.  Based on the nominal point spacing of the
    // TIN, come up with a lower-bound area value for which we will
    // accept polygons.  This operation is not part of the original alpha-shape
    // algorithm, but is implemented for code robustness.
    Thresholds thresholds = tin.getThresholds();
    double sNominal = thresholds.getNominalPointSpacing();
    areaMinThreshold = sNominal * sNominal / 1048576.0;

    // About edge index scheme:
    // Each edge has two indices, one for each direction. The "base" index
    // is always even, the index of its dual is always one greater than
    // the base (e.g. always odd).  The assignment of index is arbitrary
    // so one direction of an edge is not more fundamental than the other.
    // Note that when finding the index for the dual of an edge, we can use
    // the exclusive or operation
    //      dual_index  = edge_index^1;
    int maxEdgeAllocationIndex = tin.getMaximumEdgeAllocationIndex();
    boolean[] covered = new boolean[maxEdgeAllocationIndex + 2];
    boolean[] border = new boolean[maxEdgeAllocationIndex + 2];

    // Step 1: Classify edges  ----------------------------
    // we define a border edge as an "outside border":
    //    a) the edge is covered
    //    b) the left side of the edge is exposed
    // note that it is possible for both sides of a covered
    // edge to be exposed.
    for (IQuadEdge edge : tin.edges()) {
      // we treat all edges of length greater than the diameter
      // as being fully uncovered.
      if (edge.getLength() > 2 * radius) {
        continue;
      }
      int eIndex = edge.getIndex();
      int dIndex = eIndex ^ 1;
      Vertex A = edge.getA();
      Vertex B = edge.getB();
      Vertex C = edge.getForward().getB();
      Vertex D = edge.getForwardFromDual().getB();
      AlphaCircle circle = new AlphaCircle(radius, A.getX(), A.getY(), B.getX(), B.getY());
      // The following flags are arranged to support debugging when we wish
      // to inspect how a particular inside/outside decision was made.
      boolean cInside = false;
      boolean dInside = false;
      boolean cInside0 = false;
      boolean cInside1 = false;
      boolean dInside0 = false;
      boolean dInside1 = false;
      if (C != null) {
        cInside0 |= circle.isPointInCircleLeft(C.getX(), C.getY());
        cInside1 |= circle.isPointInCircleRight(C.getX(), C.getY());
      }
      if (D != null) {
        dInside0 |= circle.isPointInCircleLeft(D.getX(), D.getY());
        dInside1 |= circle.isPointInCircleRight(D.getX(), D.getY());
      }
      cInside = cInside0 | cInside1;
      dInside = dInside0 | dInside1;

      if (cInside) {
        covered[dIndex] = true;
        covered[eIndex] = true;
        if (dInside) {
          covered[dIndex] = true;
        } else {
          border[dIndex] = true;
        }
      } else if (dInside) {
        // the logic already established that cInside is false
        covered[eIndex] = true;
        covered[dIndex] = true;
        border[eIndex] = true;
      }
    }


    // Step 1a: Reclassify edges based on neighboring triangles ----------
    //   If the forward and reverse edges for a border edge
    // are both covered, we treat the entire triangle as being
    // part of the interior of the alpha shape. This condition
    // causes the code to mark the border edge as fully covered
    // rather than a border.
    for (IQuadEdge e : tin.edges()) {
      int eIndex = e.getIndex();
      int dIndex = eIndex ^ 1;
      if (border[eIndex]) {
        int efIndex = e.getForward().getIndex();
        int erIndex = e.getReverse().getIndex();
        if (covered[efIndex] && covered[erIndex]) {
          border[eIndex] = false;
          covered[eIndex] = true;
        }
      } else if (border[dIndex]) {
        IQuadEdge d = e.getDual();
        int dfIndex = d.getForward().getIndex();
        int drIndex = d.getReverse().getIndex();
        if (covered[dfIndex] && covered[drIndex]) {
          border[dIndex] = false;
          covered[dIndex] = true;
        }
      }
    }


    // Step 2: Assemble alpha polygons ----------------------------
    // Build the alpha-shape polygons using the border classifications
    // established above. The border flags mark covered edges that have a side
    // that faces an exposed triangle. The visited flags tells the logic whether
    // an edge has already been processed and thus not available for use.
    // For each available border edge, the logic traverses around the outside
    // of the border in a counter clockwise direction to find the next
    // border edge in the construction.  Because the border edge faces an
    // exposed triangle, the dual of the border edge is added to the polygon.
    // As each border is processed, its index is added to the visited flags.
    // The polygon-building sub-loop terminates when it reaches its original
    // starting edge.
    //
    boolean[] visited = new boolean[maxEdgeAllocationIndex + 2];
    for (IQuadEdge edge : tin.edges()) {
      IQuadEdge e0 = edge;
      // The tin.edges() iterator yields only the base side of each edge.
      // But the border could be either the base edge or its dual.
      // So this logic needs to check both.
      for (int i = 0; i < 2; i++, e0 = e0.getDual()) {
        List<IQuadEdge> eList = new ArrayList<>();
        int startIndex = e0.getIndex();
        if (!border[startIndex] || visited[startIndex]) {
          continue;
        }
        visited[startIndex] = true;

        eList.add(e0.getDual());
        IQuadEdge e = e0.getReverse();
        while (true) {
          int eIndex = e.getIndex();
          if (eIndex == startIndex) {
            break;
          }
          if (border[eIndex] || covered[eIndex]) {
            visited[eIndex] = true;
            eList.add(e.getDual());
            e = e.getReverse();
          } else {
            e = e.getReverseFromDual();
          }
        }
        // if the eList size is less than 3, the traversal algorithm
        // was not successful in creating a polygoon.  The area computation
        // will come back as zero
        double area = computeArea(eList);
        AlphaPartType partType;
        if(Math.abs(area) >= areaMinThreshold){
          partType = AlphaPartType.Polygon;
        }else{
          partType = AlphaPartType.OpenLine;
        }
        AlphaPart aPath = new AlphaPart(partType, area, eList);
        alphaParts.add(aPath);
      }
    }

    if (alphaParts.size() > 1) {
      Collections.sort(alphaParts, new Comparator<AlphaPart>() {
        @Override
        public int compare(AlphaPart o1, AlphaPart o2) {
          double area1 = Math.abs(o1.getArea());
          double area2 = Math.abs(o2.getArea());
          return Double.compare(area2, area1);
        }
      });
      for (int i = 1; i < alphaParts.size(); i++) {
        AlphaPart iPart = alphaParts.get(i);
        Vertex A = iPart.edges.get(0).getA();
        double aX = A.getX();
        double aY = A.getY();
        // find the smallest polygon that contains (aX, aY).  That will
        // be the parent of AlphaPart i.
        for (int j = i - 1; j >= 0; j--) {
          AlphaPart jPart = alphaParts.get(j);
          if (jPart.isPolygon()) {
            Polyside.Result pr = Polyside.isPointInPolygon(jPart.edges, aX, aY);
            if (pr.isCovered()) {
              jPart.addChild(iPart);
              break; // break the j loop
            }
          }
        }
      }
    }

    // Step 3: Collect orphan vertices -------------------------------------
    // If the alpha radius is small enough, individual vertices might
    // be exposed. Collect unassociated vertices (if any).
    List<Vertex> vList = new ArrayList<>();
    for (IQuadEdge edge : tin.edges()) {
      IQuadEdge e = edge;
      int eIndex = e.getIndex();
      int dIndex = eIndex ^ 1;
      if (covered[eIndex] || covered[dIndex]) {
        // The vertex was identified as part of the alpha shape
        continue;
      }


      for (int iSide = 0; iSide < 2; iSide++) {
        // if vertex A of the edge was not already processed
        // check to see if it should be included.
        if (!visited[eIndex]) {
          boolean uncommitted = true;
          for (IQuadEdge p : e.pinwheel()) {
            int pIndex = p.getIndex();
            if (covered[pIndex] || visited[pIndex]) {
              uncommitted = false;
            }
            visited[pIndex] = true;
          }

          if (uncommitted) {
            Vertex A = e.getA();
            vList.add(A);
          }
        }

          // Set up to process the other side of the edge (to potentially
          // obtain vertex B).
          if (iSide == 0) {
            if (visited[dIndex]) {
              break;
            }
            eIndex = dIndex;
            dIndex = eIndex ^ 1;
            e = e.getDual();

        }
      }
    }

    if(vList.size()>0){
      AlphaPart part = new AlphaPart(vList);
      alphaParts.add(part);
    }


  }

  private double computeArea(List<IQuadEdge> edges) {
    if (edges.size() < 3) {
      return 0;
    }
    double aSum = 0;
    double xSum = 0;
    double ySum = 0;
    for (IQuadEdge edge : edges) {
      Vertex A = edge.getA();
      xSum += A.getX();
      ySum += A.getY();
    }
    double xC = xSum / edges.size();
    double yC = ySum / edges.size();

    Vertex A = edges.get(0).getA();
    double x0 = A.getX() - xC;
    double y0 = A.getY() - yC;
    for (IQuadEdge e : edges) {
      Vertex B = e.getB();
      double x1 = B.getX() - xC;
      double y1 = B.getY() - yC;
      aSum += x0 * y1 - x1 * y0;
      x0 = x1;
      y0 = y1;
    }
    return aSum / 2.0;
  }

  /**
   * Prints a summary of the content of the alpha shape to the specified
   * print-stream instance (such as System&#46;out, etc&#46;).
   * The standard summary provides a list of the polygons that comprise the
   * alpha shape. The extended summary also prints the edges for each polygon.
   *
   * @param ps a valid print stream.
   * @param extendedSummary true if more detail is to be printed; otherwise,
   * false.
   */
  public void summarize(PrintStream ps, boolean extendedSummary) {
    ps.println("Number of alpha parts: " + alphaParts.size());
    for (AlphaPart alphaPart : alphaParts) {
      ps.println(alphaPart.toString());
      if (extendedSummary) {
        alphaPart.edges.forEach(edge -> {
          ps.println("    " + edge.toString());
        });
      }
      ps.println();
    }

  }

  /**
   * Gets the components of the Alpha Shape. The result of this method
   * consists of a list of zero or more instances of the AlphaPart class.
   * At this time, all parts are expected to describe polygons, though
   * future releases may include support for line and point features.
   *
   * @return a valid, potentially empty list.
   */
  List<AlphaPart> getParts() {
    List<AlphaPart> results = new ArrayList<>();
    results.addAll(this.alphaParts);
    return results;
  }

  /**
   * Gets a instance of a Path2D based on the polygon geometry of the alpha
   * shape,
   * including any enclosed (hole) features. Non-polygon features are
   * excluded.
   *
   * @return a valid, potentially empty instance.
   */
  public Path2D getPath2D() {
    return getPath2D(true);
  }

  /**
   * Gets a instance of a Path2D based on the geometry of the alpha shape,
   * including any enclosed (hole) features. If polygonFlag option
   * is specified, only valid polygon features will be included
   * (a polygon is considered if it has a non-zero area and is suitable
   * for plotting with a fill operation). If polygonFlag option is false,
   * open-line (non-polygon) features and zero-area polygons will be included.
   *
   * @param polygonFlag true if only valid polygon features are included; false
   * if only open-line features are included.
   * @return a valid, potentially empty instance.
   */
  public Path2D getPath2D(boolean polygonFlag) {
    Path2D p = new Path2D.Double();
    for (AlphaPart a : alphaParts) {
      if (polygonFlag) {
        if (a.isPolygon() && Math.abs(a.getArea()) > areaMinThreshold) {
          // edges are connected.  Move to first vertex of first edge,
          // and then line-to second vertex of all subsequent edges.
          IQuadEdge aEdge = a.edges.get(0);
          Vertex A = aEdge.getA();
          p.moveTo(A.getX(), A.getY());
          for (IQuadEdge e : a.edges) {
            Vertex B = e.getB();
            p.lineTo(B.getX(), B.getY());
          }
        }
      } else {
        if (a.isPolygon()) {
          if (Math.abs(a.getArea()) <= areaMinThreshold) {
            IQuadEdge aEdge = a.edges.get(0);
            Vertex A = aEdge.getA();
            p.moveTo(A.getX(), A.getY());
            for (IQuadEdge e : a.edges) {
              Vertex B = e.getB();
              p.lineTo(B.getX(), B.getY());
            }
          }
        } else {
          // edges are not connected.
          for (IQuadEdge e : a.edges) {
            Vertex A = e.getA();
            Vertex B = e.getB();
            p.moveTo(A.getX(), A.getY());
            p.lineTo(B.getX(), B.getY());
          }
        }
      }
    }
    return p;
  }

  /**
   * Gets a path containing only those parts that are not enclosed by
   * other polygons. Typically, this selection set represents the
   * outer envelope of the alpha shape.
   *
   * @return a valid Path2D instance.
   */
  public Path2D getOuterPolygonsPath2D() {
    Path2D p = new Path2D.Double();
    for (AlphaPart a : alphaParts) {
      if (a.isPolygon() && !a.isEnclosed() && Math.abs(a.getArea()) > areaMinThreshold) {
        // edges are connected.  Move to first vertex of first edge,
        // and then line-to second vertex of all subsequent edges.
        IQuadEdge aEdge = a.edges.get(0);
        Vertex A = aEdge.getA();
        p.moveTo(A.getX(), A.getY());
        for (IQuadEdge e : a.edges) {
          Vertex B = e.getB();
          p.lineTo(B.getX(), B.getY());
        }
      }
    }
    return p;
  }

  /**
   * Constructs a set of constraint objects based on the geometry
   * of the alpha shape. For the current implementation, this method
   * produces only linear constraints based on the valid-polygon parts of the
   * alpha shape. Future implementation may broaden this behavior.
   *
   * @return a valid, potentially empty list.
   */
  public List<IConstraint> getConstraints() {
    List<IConstraint> constraints = new ArrayList<>();
    for (AlphaPart a : alphaParts) {
      LinearConstraint constraint = new LinearConstraint();
      if (a.isPolygon()) {
        Vertex A = a.edges.get(0).getA();
        constraint.add(A);
        for (IQuadEdge e : a.edges) {
          Vertex B = e.getB();
          constraint.add(B);
        }
      }
      constraint.complete();
      constraints.add(constraint);
    }
    return constraints;
  }

  /**
   * Gets a list of the components of the alpha-shape. Typically, these
   * include polygon features.
   *
   * @return a valid, potentially empty list of AlphaPart instances.
   */
  public List<AlphaPart> getAlphaParts() {
    List<AlphaPart> list = new ArrayList<>();
    list.addAll(alphaParts);
    return list;
  }

  /**
   * Gets the alpha-radius parameter that was used to construct the shape.
   *
   * @return a finite value greater than zero.
   */
  public double getRadius() {
    return radius;
  }

  /**
   * Gets the Delaunay triangulation from which this instance was derived.
   *
   * @return a valid reference to an instance of IIncrementalTin.
   */
  public IIncrementalTin getDelaunayTriangulation() {
    return this.tin;
  }

  @Override
  public String toString() {
    return "Alpha-shape with radius: " + radius;
  }
}
