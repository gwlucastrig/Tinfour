/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * Date Name Description
 * ------   --------- -------------------------------------------------
 * 08/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.utils.Polyside;

/**
 * Provides elements and methods for representing a Thiessen Polygon created by
 * the BoundedVoronoi class.
 */
public class ThiessenPolygon {

  final boolean open;
  private final Vertex vertex;
  final IQuadEdge[] edges;
  final double area;
  final Rectangle2D bounds;

  /**
   * Constructs a Thiessen Polygon representation. The open flag is used to
   * indicate polygons of an infinite area that extend beyomd the bounds of the
   * Delaunay Triangulation associated with the Voronoi Diagram
   *
   * @param vertex The vertex at the center of the polygon
   * @param edgeList a list of the edges comprising the polygon
   * @param open indicates whether the polygon is infinite (open) finite
   * (closed).
   */
  public ThiessenPolygon(Vertex vertex, List<IQuadEdge> edgeList, boolean open) {
    this.vertex = vertex;
    this.edges = edgeList.toArray(new IQuadEdge[edgeList.size()]);
    this.open = open;

    Vertex v = edgeList.get(0).getA();
    bounds = new Rectangle2D.Double(v.getX(), v.getY(), 0, 0);
    double s = 0;
    for (IQuadEdge e : edgeList) {
      Vertex A = e.getA();
      Vertex B = e.getB();
      s += A.getX() * B.getY() - A.getY() * B.getX();
      bounds.add(B.getX(), B.getY());
    }
    area = s / 2;

  }

  /**
   * Gets the edges that comprise the polygon
   *
   * @return a valid array of edges
   */
  public List<IQuadEdge> getEdges() {
    //List<IQuadEdge>list = new ArrayList<>(edges.length);
    return Arrays.asList(edges);
    //return list;
  }

  /**
   * Gets the area of the Voronoi polygon. If the polygon is an open polygon,
   * its actual area would be infinite,  but the reported area matches
   * the domain of the Bounded Voronoi Diagram class.
   *
   * @return  a valid, finite floating point value
   */
  public double getArea() {
    return area;
  }

  /**
   * Gets the defining vertex of the polygon.
   *
   * @return the vertex
   */
  public Vertex getVertex() {
    return vertex;
  }

  /**
   * Gets the bounds of the polygon
   *
   * @return a safe copy of a rectangle instance.
   */
  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(
            bounds.getX(),
            bounds.getY(),
            bounds.getWidth(),
            bounds.getHeight());
  }

  /**
   * Indicates whether the specified coordinate point lies inside or on an edge
   * of the polygon associated with this instance.
   *
   * @param x the Cartesian x coordinate of the query point
   * @param y the Cartesian y coordinate of the query point
   * @return true if the point is inside the polygon; otherwise, false
   */
  public boolean isPointInPolygon(double x, double y) {
    List<IQuadEdge> edgeList = Arrays.asList(edges);
    Polyside.Result result = Polyside.isPointInPolygon(edgeList, x, y);
    return result.isCovered();
  }

  /**
   * Indicates that in a true Voronoi Diagram the polygon would
   * not form a closed polygon and would have an infinite domain.
   * @return true if the polygon is open, otherwise false.
   */
  public boolean isOpen(){
      return open;
  }
  
  /**
   * Gets the index element of the defining vertex for this polygon.
   * The vertex index is under the control of the calling application
   * and is not modified by the Voronoi classes. Note that the
   * index of a vertex is not necessarily unique but left to the
   * requirements of the application that constructs it.
   * @return an integer value
   */
  public int getIndex(){
    return vertex.getIndex();
  }
  
  
  @Override
  public String toString() {
    return String.format("ThiessenPolygon vertex=%s", vertex.getLabel());
  }
}
