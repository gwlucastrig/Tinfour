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

  final boolean isPolygon;
  final List<IQuadEdge> edges = new ArrayList<>();
  double area;
  AlphaPart parent;
  final List<AlphaPart> children = new ArrayList<>();

  /**
   * Standard constructor, package scope.
   */
  AlphaPart() {
    isPolygon = true;
  }

  AlphaPart(boolean isPolygon){
    this.isPolygon = isPolygon;
  }

  /**
   * Gets a list of any child (embedded) alpha part components.
   * @return a valid, potentially empty list.
   */
  public List<AlphaPart> getChildren() {
    return new ArrayList<>(children);
  }

  /**
   * Gets a list of the edges that define the alpha part.
   * @return a valid, potentially empty list
   */
  public List<IQuadEdge> getEdges(){
    return new ArrayList<>(edges);
  }

  /**
   * Gets the alpha part that contains this instance (if any).
   * @return if a parent exists, a valid reference; otherwise, a null.
   */
  public AlphaPart getParent(){
    return parent;
  }

  /**
   * Gets the vertices that define the alpha part.
   * @return a valid, non-empty list.
   */
  public List<Vertex>getVertices(){
    ArrayList<Vertex>vList = new ArrayList<>(edges.size());
    for(IQuadEdge edge: edges){
      vList.add(edge.getA());
    }
    return vList;
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
   * @return true if the part is a polygon feature; false if it is a
   * an open-line.
   */
   public boolean isPolygon(){
     return isPolygon;
   }

  /**
   * Performs area computation and other operations related to the completion
   * of an alpha shape.
   */
  void complete() {
    if (edges.size() < 3) {
      return;
    }
    if(!isPolygon){
      area = 0;
      return;
    }
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
    double aSum = 0;
    for (IQuadEdge e : edges) {
      Vertex B = e.getB();
      double x1 = B.getX() - xC;
      double y1 = B.getY() - yC;
      aSum += x0 * y1 - x1 * y0;
      x0 = x1;
      y0 = y1;
    }
    area = aSum / 2.0;
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
    if(isPolygon){
      geoString = "polygon   ";
    }else{
      geoString = "open-line ";
    }
    String a = parent != null ? "child" : "";
    if (children.size() > 0) {
      if (a.isEmpty()) {
        a = "parent";
      } else {
        a = "parent+child";
      }
    }

    return String.format("AlphaPart %s n=%3d, area=%6.3f, %s",
      geoString, edges.size(), getArea(), a);
  }
}
