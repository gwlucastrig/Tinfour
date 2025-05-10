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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
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
 * <strong>Maturity of this implementation:</strong>
 * At this time, the current AlphaShape implementation supports cases
 * in which the results are polygon in nature. It has not been tested
 * extensively for potentially degenerate cases. For example, very small
 * values of the radius for the alpha-circle tests would correctly
 * produce an alpha shape consisting of individual points. Some sets of vertices
 * collapse into open line features.
 * <p>
 * <strong>References:</strong>
 * <cite>H. Edelsbrunner, D. Kirkpatrick and R. Seidel,
 * "On the shape of a set of points in the plane,"
 * in IEEE Transactions on Information Theory, vol. 29, no. 4, pp. 551-559, July
 * 1983,
 * doi: 10.1109/TIT.1983.1056714.</cite>
 */
public class AlphaShape {

  private final IIncrementalTin tin;
  private final double radius;
  private final List<AlphaPart> alphaParts = new ArrayList<>();

  /**
   * Constructs an alpha shape based on the specified Delaunay triangulation.
   * Note that this constructor takes the specification of a radius for the
   * alpha-circle features used to develop the alpha shape. Other
   * implementations
   * may use the alpha parameter rather than the radius.
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

    // About edge index scheme:
    // Each edge has two indices, one for each direction. The "base" index
    // is always even, the index of its dual is always one greater than
    // the base (e.g. always odd).  The assignment of index is arbitrary
    // so one direction of an edge is not more fundamental than the other.
    // Note that when finding the index for the dual of an edge, we can use
    // the exclusive or operation
    //      dual_index  = edge_index^1;
    int maxEdgeAllocationIndex = tin.getMaximumEdgeAllocationIndex();
    BitSet visited = new BitSet(maxEdgeAllocationIndex + 2);
    boolean[] coverSide = new boolean[maxEdgeAllocationIndex + 2];
    boolean[] covered = new boolean[maxEdgeAllocationIndex + 2];
    boolean[] border = new boolean[maxEdgeAllocationIndex + 2];
    for (IQuadEdge edge : tin.edges()) {
      // we treat all edges of length greater than the diameter
      // as being fully uncovered.
      if (edge.getLength() > 2 * radius) {
        continue;
      }
      int eIndex = edge.getIndex();
      int dIndex = eIndex ^ 1;
      IQuadEdge f = edge.getForward();
      Vertex A = edge.getA();
      Vertex B = edge.getB();
      Vertex C = f.getB();
      Vertex D = edge.getForwardFromDual().getB();
      AlphaCircle circle = new AlphaCircle(radius, A.getX(), A.getY(), B.getX(), B.getY());
      if (C != null && circle.isPointInCircles(C.getX(), C.getY())) {
        coverSide[eIndex] = true;
      }
      if (D != null && circle.isPointInCircles(D.getX(), D.getY())) {
        coverSide[dIndex] = true;
      }

      // Mark all fully covered edges.  These are the edges that
      // lie complete within the alpha shape
      if (coverSide[eIndex] && coverSide[dIndex]) {
        covered[eIndex] = true;
        covered[dIndex] = true;
      }
    }

    for (IQuadEdge edge : tin.edges()) {
      // if this is a partially covered edge, set it up so that
      // d is the exposed side and e is the covered side
      // if both or neither sides are covered, just continue
      IQuadEdge e = edge;
      IQuadEdge d = edge.getDual();
      int eIndex = e.getIndex();
      int dIndex = eIndex ^ 1;
      if (coverSide[eIndex]) {
        if (coverSide[dIndex]) {
          continue; // both sides are covered
        }
        // side e is covered, side d is exposed
      } else if (coverSide[dIndex]) {
        // we've determined that e is exposed and d is covered
        // switch over to the dual so that d is exposed and e is covered.
        IQuadEdge swap;
        swap = d;
        d = e;
        e = swap;
        eIndex = e.getIndex();
        dIndex = d.getIndex();
      } else {
        continue;
      }

      // we are now positioned so that e is covered and d is exposed.
      // The edge can be either on the border or to the exterior a covered area.
      // It is possible for a sequence of multiple adjacent triangles to have
      // uncovered edges in the vicinity of a border.  To qualify it as
      // a true border, we need to see if either of the adjacent edges
      // are unambiguously part of the alpha shape (one or both of them
      // is fully covered).  If we don't perform this test, we could get
      // multiple overlapping polygons forming near the border.
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();
      boolean fcovered = covered[f.getIndex()];
      boolean rcovered = covered[r.getIndex()];
      border[e.getIndex()] = fcovered || rcovered;
    }

    for (IQuadEdge edge : tin.edges()) {
      IQuadEdge e0 = edge;
      int eIndex = e0.getIndex();
      int dIndex = eIndex ^ 1;
      if (visited.get(eIndex) || visited.get(dIndex)) {
        continue;
      }
      IQuadEdge dEdge = e0.getDual();
      if (border[dIndex]) {
        // swap
        IQuadEdge swap = e0;
        e0 = dEdge;
        dEdge = swap;
        eIndex = dIndex;
        dIndex = eIndex ^ 1;
      } else if (!border[eIndex]) {
        continue;
      }

      visited.set(eIndex);
      visited.set(dIndex);

      AlphaPart aPath = new AlphaPart();
      alphaParts.add(aPath);
      IQuadEdge e = e0;
      int startIndex = e.getBaseIndex();
      while (true) {
        eIndex = e.getIndex();
        dIndex = eIndex ^ 1;
        if (border[eIndex]) {
          visited.set(eIndex);
          visited.set(dIndex);
          aPath.edges.add(e);
          e = e.getForward();
        } else {
          e = e.getForwardFromDual();
        }

        if (e.getBaseIndex() == startIndex) {
          break;
        }
      }
      aPath.complete();
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
          Polyside.Result pr = Polyside.isPointInPolygon(jPart.edges, aX, aY);
          if (pr.isCovered()) {
            jPart.addChild(iPart);
            break; // break the j loop
          }
        }
      }
    }

    //  System.out.println("Number of alpha parts: " + alphaParts.size());
    //  for (AlphaPart alphaPart : alphaParts) {
    //    System.out.println(alphaPart.toString());
    //    for (IQuadEdge edge : alphaPart.edges) {
    //      System.out.println("    " + edge.toString());
    //    }
    //  }
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
   * Gets a instance of a Path2D based on the geometry of the alpha shape,
   * including any enclosed (hole) features.
   *
   * @return a valid, potentially empty instance.
   */
  public Path2D getPath2D() {
    Path2D p = new Path2D.Double();
    for (AlphaPart a : alphaParts) {
      IQuadEdge aEdge = a.edges.get(0);
      Vertex A = aEdge.getA();
      p.moveTo(A.getX(), A.getY());
      for (IQuadEdge e : a.edges) {
        Vertex B = e.getB();
        p.lineTo(B.getX(), B.getY());
      }
    }
    return p;
  }

  /**
   * Constructs a set of constraint objects based on the geometry
   * of the alpha shape.
   *
   * @return a valid, potentially empty list.
   */
  public List<IConstraint> getConstraints() {
    List<IConstraint> constraints = new ArrayList<>();
    for (AlphaPart a : alphaParts) {
      LinearConstraint constraint = new LinearConstraint();
      Vertex A = a.edges.get(0).getA();
      constraint.add(A);
      for (IQuadEdge e : a.edges) {
        Vertex B = e.getB();
        constraint.add(B);
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

  @Override
  public String toString() {
    return "Alpha-shape with radius: " + radius;
  }
}
