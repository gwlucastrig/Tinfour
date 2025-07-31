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
import java.util.List;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides a set of edges that define the path of an alpha shape.
 * The path may represent either the outer bounds of a region or an enclosed
 * region.
 */
public class AlphaPart {

  final AlphaPartType partType;
  final List<IQuadEdge> edges = new ArrayList<>();
  final List<Vertex>vertices = new ArrayList<>();
  final double area;
  AlphaPart parent;
  final List<AlphaPart> children = new ArrayList<>();


  /**
   * Standard constructor, package scope.  Not used
   */
  AlphaPart() {
    partType = AlphaPartType.Unspecified;
    area = 0;
  }


  AlphaPart(AlphaPartType partType, double area, List<IQuadEdge>eList) {
    this.partType = partType;
    this.area = area;
    this.edges.addAll(eList);
  }

  AlphaPart(List<Vertex>inputVertices){
    this.partType = AlphaPartType.Vertices;
    this.area = 0;
    this.vertices.addAll(inputVertices);
  }

  /**
   * Gets the type of part (polygon, unconnected lines, or unassociated vertices)
   * for this alpha part.
   * @return a valid enumeration instance.
   */
  public AlphaPartType getPartType(){
    return partType;
  }


  /**
   * Gets a list of any child (embedded) alpha part components.
   *
   * @return a valid, potentially empty list.
   */
  public List<AlphaPart> getChildren() {
    return new ArrayList<>(children);
  }

  /**
   * Gets a list of the edges that define the alpha part. Because this call exposes
   * the internal list element of the part, applications should not
   * modify the List instance that is returned from this call.
   *
   * @return a valid, potentially empty list
   */
  public List<IQuadEdge> getEdges() {
    return new ArrayList<>(edges);
  }

  /**
   * Gets a list of the Vertices that define the alpha part. Because this call
   * exposes
   * the internal list element of the part, applications should not
   * modify the List instance that is returned from this call.
   *
   * @return a valid, potentially empty list
   */
  public List<Vertex> getVertices() {
    if (vertices.isEmpty()) {
      if (!edges.isEmpty()) {
        edges.forEach(e -> {
          vertices.add(e.getA());
        });
      }
    }
    return vertices;
  }

  /**
   * Gets the alpha part that contains this instance (if any).
   *
   * @return if a parent exists, a valid reference; otherwise, a null.
   */
  public AlphaPart getParent() {
    return parent;
  }

 

  /**
   * Indicates that the path encloses a region.
   *
   * @return true if the region indicated by the path encloses
   * a set of points definition an alpha shape.
   */
  public boolean isAnEnclosure() {
    return children.size() > 0;
  }

  /**
   * Indicates that the path represents a region that is in the interior
   * of an alpha shape..
   *
   * @return true if the region indicated by the path is enclosed by
   * a set of points defining an alpha shape.
   */
  public boolean isEnclosed() {
    return parent != null;
  }

  /**
   * Indicates whether the part is a polygon feature.
   *
   * @return true if the part is a polygon feature; false if it is a
   * an open-line.
   */
  public boolean isPolygon() {
    return partType==AlphaPartType.Polygon;
  }


  /**
   * Gets the computed area of a polygon feature.
   *
   * @return a signed real value (positive for polygons with a
   * counterclockwise orientation, negative for clockwise).
   */
  public double getArea() {
    return area;
  }

  /**
   * Add a child (enclosed) part to this feature and assigns this
   * instance as the parent of the child feature.
   *
   * @param child a valid alpha part.
   */
  void addChild(AlphaPart child) {
    child.parent = this;
    children.add(child);
  }

  @Override
  public String toString() {
    String geoString;
    switch (partType) {
      case Polygon:
        geoString = "polygon     ";
        break;
      case OpenLine:
        geoString = "open-line   ";
        break;
      case Vertices:
        geoString = "vertices    ";
        break;
      default:
        geoString = "Unspecified ";
        break;
    }

    String a = parent != null ? "child" : "";
    if (children.size() > 0) {
      if (a.isEmpty()) {
        a = "parent";
      } else {
        a = "parent+child";
      }
    }

    int n;
    if(partType==AlphaPartType.Vertices){
      n = vertices.size();
    }else{
      n = edges.size();
    }
    return String.format("AlphaPart %s n=%3d, area=%6.3f, %s",
      geoString, n, getArea(), a);
  }

  /**
   * Get an instance of Path2D populated with coordinates taken from
   * the edges that define this alpha part. For a polygon part, the defining
   * points will be linked together as a series of connected line segments.
   * For a non-polygon part, the Path2D will consist of a series of separate
   * line segments.  Non-polygon parts are no suitable for rendering using
   * Java's area-fill routines.
   * @return a valid instance
   */
  public Path2D getPath2D() {
    Path2D p = new Path2D.Double();

    if (isPolygon() && Math.abs(getArea()) > 1.0e-6) {
      // edges are connected.  Move to first vertex of first edge,
      // and then line-to second vertex of all subsequent edges.
      IQuadEdge aEdge = edges.get(0);
      Vertex A = aEdge.getA();
      p.moveTo(A.getX(), A.getY());
      for (IQuadEdge e : edges) {
        Vertex B = e.getB();
        p.lineTo(B.getX(), B.getY());
      }
    } else {
      // edges are not connected.
      for (IQuadEdge e : edges) {
        Vertex A = e.getA();
        Vertex B = e.getB();
        p.moveTo(A.getX(), A.getY());
        p.lineTo(B.getX(), B.getY());
      }
    }
    return p;
  }
}
