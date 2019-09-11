/* --------------------------------------------------------------------
 * Copyright 2016-2019 Gary W. Lucas.
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
 * 05/2016  G. Lucas     Created original NeighborEdgeVertex class
 * 09/2019  G. Lucas     Adapted from NeighborEdgeVertex 
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

/**
 * Provides a minimal set of data elements for the result of a nearest-edge
 * location operation.
 */
public class NearestEdgeResult {

  final IQuadEdge edge;
  final double edgeD;
  final double x;
  final double y;
  final boolean interior;

  Vertex vertex; // the nearest vertex, when populated
  double vertexD; // the distance to nearest vertex, when populated

  /**
   * Standard constructor.
   *
   * @param edge the edge that starts with the vertex nearest (x,y)
   * @param edgeD the distance of the query point to the edge
   * @param queryX the X coordinate for the query point
   * @param queryY the Y coordinate for the query point
   * @param interior indicates that the query point is inside the TIN boundary,
   */
  public NearestEdgeResult(
          IQuadEdge edge,
          double edgeD,
          double queryX,
          double queryY,
          boolean interior) {
    this.edge = edge;
    this.edgeD = edgeD;
    this.x = queryX;
    this.y = queryY;
    this.interior = interior;
  }

  private void prepareNearestVertex() {
    if (vertex == null) {
      Vertex vMin = edge.getA();
      double dX = vMin.getX() - x;
      double dY = vMin.getY() - y;
      double d2Min = dX * dX + dY * dY;
      for (IQuadEdge e : edge.pinwheel()) {
        Vertex v = e.getB();
        if (v != null) {
          dX = v.getX() - x;
          dY = v.getY() - y;
          double d2 = dX * dX + dY * dY;
          if (d2 < d2Min) {
            d2Min = d2;
            vMin = v;
          }
        }
      }
      vertex = vMin;
      vertexD = Math.sqrt(d2Min);
    }
  }

  /**
   * Gets the edge that is nearest to the query point. The nearest point on that
   * edge may be either of the endpoints of the edge or some point in between.
   * The nearest vertex may not necessarily lie on the edge.
   * <p>
   * If the query point is in the interior of the TIN, it will lie either on the
   * edge or inside the triangle to the left of the edge.
   *
   * @return a valid edge.
   */
  public IQuadEdge getEdge() {
    return edge;
  }

  /**
   * Gets the distance from the query point to the nearest edge. This distance
   * is measured in a perpendicular direction from the edge.
   *
   * @return a positive, potentially zero, floating point value.
   */
  public double getDistanceToEdge() {
    return edgeD;
  }

  /**
   * Gets the vertex nearest the query point.
   *
   * @return A valid vertex (never null).
   */
  public Vertex getNearestVertex() {
    prepareNearestVertex();
    return vertex;
  }

  /**
   * Gets the distance from the query point to the nearest vertex.
   *
   * @return a positive floating point value, potentially zero.
   */
  public double getDistanceToNearestVertex() {
    prepareNearestVertex();
    return vertexD;
  }

  /**
   * Get the X coordinate of the query point
   *
   * @return a valid coordinate
   */
  public double getX() {
    return x;
  }

  /**
   * Get the Y coordinate of the query point
   *
   * @return a valid coordinate
   */
  public double getY() {
    return y;
  }

  /**
   * Indicates whether the query point was inside the convex polygon boundary of
   * the TIN.
   *
   * @return true if the query point was inside the TIN; false otherwise
   */
  public boolean isInterior() {
    return interior;
  }

}
