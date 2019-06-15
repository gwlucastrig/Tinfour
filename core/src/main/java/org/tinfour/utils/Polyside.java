/*
 * Copyright 2018 Gary W. Lucas.
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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 08/2018  G. Lucas     Initial implementation
 *
 * Notes:
 *  The algorithm used in this class is taken from 
 * "Computational Geometry in C (2nd Edition)", Joseph O'Rourke,
 * Cambridge Univeristy Press, 1998.  Page 239 ("Point in Polygon").
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.util.List;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * A utility for determining whether a specified coordinate is inside a polygon
 * defined by IQuadEdge instances.
 */
public final class Polyside {

  /**
   * An enumeration indicating the result of a point-in-poygon test
   */
  public enum Result {
    /**
     * The point is unambiguously outside the polygon
     */
    Outside(false),
    /**
     * The point is unambiguously inside the polygon
     */
    Inside(true),
    /**
     * The point is on the edge of the polygon
     */
    Edge(true);

    final private boolean covered;

    Result(boolean covered) {
      this.covered = covered;
    }

    
    /**
     * Indicates whether the polygon covers the specified coordinates
     *
     * @return true if the polygon is on the inside or on the edge of the
     * polygon; false if it is unambiguously outside the polygon.
     */
    public boolean isCovered() {
      return covered;
    }
  }

  
  /**
   * A private constructor to deter applications from creating direct instances
   * of this class.
   */
  private Polyside() {
    // no action required
  }

    
  /**
   * Determines if a point is inside a polygon. The polygon must be a simple
   * (non-self-intersecting) loop, but may be either convex or non-convex.
   * The polygon must have complete closure so that the terminal vertex of
   * the last edge has the same coordinates as the initial vertex of the
   * first edge. The polygon must have a non-zero area.
   *
   * @param list a list of edges.
   * @param x the Cartesian coordinate of the query point
   * @param y the Cartesian coordinate of the query point
   * @return a valid Result enumeration
   */
  public static Result isPointInPolygon(List<IQuadEdge> list, double x, double y)
  {
    int n = list.size();
    if (n < 3) {
      throw new IllegalArgumentException(
              "A polygon needs at least three edges, but the input size is "
                      + n);
    }

    IQuadEdge e0 = list.get(0);
    IQuadEdge e1 = list.get(n-1);
    if (!e0.getA().equals(e1.getB())) {
      throw new IllegalArgumentException("Input polygon is not closed "
              + "(last edge must at at start of first)");
    }

    int rCross = 0;
    int lCross = 0;
    for (IQuadEdge e : list) {
      Vertex v0 = e.getA();
      Vertex v1 = e.getB();
      double x0 = v0.getX();
      double y0 = v0.getY();
      double x1 = v1.getX();
      double y1 = v1.getY();
      double yDelta = y0 - y1;
      if (y1 > y != y0 > y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest > x) {
          rCross++;
        }
      }
      if (y1 < y != y0 < y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest < x) {
          lCross++;
        }
      }

    }

    // (rCross%2) != (lCross%2)
    if (((rCross ^ lCross) & 0x01) == 1) {
      return Result.Edge;
    } else if ((rCross & 0x01) == 1) {
      return Result.Inside;
    }
    return Result.Outside;
  }
}
