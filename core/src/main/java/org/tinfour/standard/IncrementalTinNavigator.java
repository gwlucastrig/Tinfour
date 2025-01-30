/* --------------------------------------------------------------------
 * Copyright 2015-to-2025 Gary W. Lucas.
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
 * 11/2015  G. Lucas     Created as part of introducing the IQuadEdge interface
 * 09/2019  G. Lucas     Refactored from NeighborEdgeLocator
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.standard;

import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.NearestEdgeResult;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.edge.QuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides a specific instance of the IIncrementalTinNavigator interface tuned
 * for efficient use with this package's TIN implementation.
 */
class IncrementalTinNavigator implements IIncrementalTinNavigator {

  QuadEdge neighborEdge;
  final StochasticLawsonsWalk walker;
  final IncrementalTin tin;

  /**
   * Constructs an instance coupled to the specified TIN.
   *
   * @param tin a valid instance
   */
  IncrementalTinNavigator(IncrementalTin tin) {
    this.tin = tin;
    walker = new StochasticLawsonsWalk(tin.getThresholds());
  }

  @Override
  public IQuadEdge getNeighborEdge(double x, double y) {
    if (!tin.isBootstrapped()) {
      return null;
    }
    if (neighborEdge == null) {
      neighborEdge = tin.getStartingEdge();
    }
    neighborEdge = walker.findAnEdgeFromEnclosingTriangle(neighborEdge, x, y);
    return neighborEdge;
  }

  @Override
  public NearestEdgeResult getNearestEdge(double x, double y) {
    IQuadEdge a = getNeighborEdge(x, y);
    return getNearestEdge(a, x, y);
  }

  private double edgeDistance(Vertex A, Vertex B, double x, double y) {
    double dX = x - A.getX();
    double dY = y - A.getY();
    double vX = B.getX() - A.getX();
    double vY = B.getY() - A.getY();
    double vM = Math.sqrt(vX * vX + vY * vY);  // magnitude of vector (vX, vY)
    double t = (dX * vX + dY * vY) / vM;
    if (t < 0) {
      // (x,y) is positioned before the start of the edge.
      // report the distance from the starting vertex.
      return Math.sqrt(dX * dX + dY * dY);
    } else if (t > vM) {
      // (x,y) is beyond the end of the edge.
      // report the distance from the ending vertex.
      double bX = x - B.getX();
      double bY = y - B.getY();
      return Math.sqrt(bX * bX + bY * bY);
    }
    // report the perpendicular distance from the line.
    double pX = -vY;
    double pY = vX;
    return Math.abs(dX * pX + dY * pY) / vM;
  }

  /**
   * Gets the edge closest to the specified coordinates where (x,y) is a
   * point known to be within the bounds of a triangle containing edge e
   * or in the proximity of an exterior edge (in which case, the opposite
   * vertex will be the ghost vertex (null).
   * Typically, edge e is discovered using a stochastic Lawson's walk or
   * similar algorithm, but other approaches are feasible.
   * The nearest edge to the coordinates may be any of a, a.getForward(), or
   * a.getReverse().
   *
   * @param a an edge belonging to a triangle that contains coordinates (x,y).
   * @param x Cartesian coordinate of a point within the associated triangle.
   * @param y Cartesian coordinate of a point within the associated triangle.
   * @return if successful, a valid result; otherwise, a null.
   */
  public NearestEdgeResult getNearestEdge(IQuadEdge a, double x, double y) {
    if (a == null) {
      return null;  //  the TIN was not initialized
    }
    IQuadEdge b = a.getForward();
    IQuadEdge c = a.getReverse();

    Vertex A = a.getA();
    Vertex B = b.getA();
    Vertex C = c.getA();

    // NOTE: in the following computations, we assume that x and y are
    //       inside the triangle ABC.  Thus, when we compute the
    //       perpendicular distance it will always be positive.
    //       Even so, we call math.abs to account for round-off.
    double test;
    double pMin = edgeDistance(A, B, x, y);
    IQuadEdge e = a;

    if (C == null) {
      // point is outside TIN, C is the ghost vertex.  we're done.
      return new NearestEdgeResult(e, pMin, x, y, false);
    }

    test = edgeDistance(B, C, x, y);
    if (test < pMin) {
      pMin = test;
      e = b;
    }

    test = edgeDistance(C, A, x, y);
    if (test < pMin) {
      pMin = test;
      e = c;
    }

    return new NearestEdgeResult(e, pMin, x, y, true);
  }

  @Override
  public Vertex getNearestVertex(double x, double y) {
    NearestEdgeResult n = getNearestEdge(x, y);
    if (n == null) {
      return null;
    }
    return n.getNearestVertex();
  }

  @Override
  public SimpleTriangle getContainingTriangle(double x, double y) {
    IQuadEdge a = getNeighborEdge(x, y);
    if (a == null) {
      return null;  //  the TIN was not initialized
    }
    IQuadEdge b = a.getForward();
    IQuadEdge c = a.getReverse();
    return new SimpleTriangle(tin, a, b, c);
  }

  @Override
  public boolean isPointInsideTin(double x, double y) {
    IQuadEdge e = getNeighborEdge(x, y);
    if (e == null) {
      return false;  //  the TIN was not initialized
    }

    return e.getForward().getB() != null;
  }

  @Override
  public void resetForChangeToTin() {
    neighborEdge = null;
    walker.reset();
  }

}
