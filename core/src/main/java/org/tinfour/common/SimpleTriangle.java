/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
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
 * 11/2018  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

 

package org.tinfour.common;

/**
 * Provides methods and elements for a simple representation of a 
 * triangle based on IQuadEdge edges.
 */
public class SimpleTriangle {

  private final IQuadEdge edgeA;
  private final IQuadEdge edgeB;
  private final IQuadEdge edgeC;
  
  /**
   * Construct a simple triangle from the specified edges.
   * For efficiency purposes, this constructor is very lean and
   * does not perform sanity checking on the inputs.
   * @param a a valid edge
   * @param b a valid edge
   * @param c a valid edge
   */
  public SimpleTriangle(IQuadEdge a, IQuadEdge b, IQuadEdge c){
    this.edgeA = a;
    this.edgeB = b;
    this.edgeC = c;
  }

  /**
   * Get edge A from the triangle
   * @return a valid edge
   */
  public IQuadEdge getEdgeA() {
    return edgeA;
  }

  /**
   * Get edge B from the triangle
   * @return a valid edge
   */
  public IQuadEdge getEdgeB() {
    return edgeB;
  }

  /**
   * Get edge C from the triangle
   * @return a valid edge
   */
  public IQuadEdge getEdgeC() {
    return edgeC;
  }
  
  /**
   * Gets the area of the triangle. This value is positive if the traingle
   * is given in counterclockwise order and negative if it is given
   * in clockwise order. A value of zero indicates a degenerate triangle.
   * @return a valid floating point number.
   */
  public double getArea() {
    Vertex a = edgeA.getA();
    Vertex b = edgeB.getA();
    Vertex c = edgeC.getA();
    double h = (c.y - a.y) * (b.x - a.x) - (c.x - a.x) * (b.y - a.y);
    return h / 2;
  }
  
}
