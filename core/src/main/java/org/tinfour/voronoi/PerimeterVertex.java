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
 * 09/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import org.tinfour.common.Vertex;

/**
 *  Extends the Vertex class to add perimeter parameter.
 */
class PerimeterVertex extends Vertex {
  /**
   * The perimeter parameter
   */
  final double p;
  
  /**
   * Construct a vertex at the specified horizontal Cartesian coordinates with
   * a z value indicating the parameterized position along the rectangular
   * bounds of the Voronoi Diagram.  The parameter is a value in the 
   * range 0 &le; z &lt 4.
   * @param x the x Cartesian coordinate of the vertex
   * @param y the y Cartesian coordinate of the vertex
   * @param z the parameterized position, in range 0 to 4.
   * @param index an arbitrary index value
   */
  public PerimeterVertex(double x, double y, double z, int index){
    super(x, y, z, index);
    this.p = z;
  }
  
  /**
   * Gets the perimeter parameter value for vertex.
   * @return a valid double-precision value
   */
  @Override
  public double getZ(){
    return p;
  }
  
  @Override
  public String toString() {
    if (this.isSynthetic()) {
      return String.format("Pv  %11.9f", p);
    } else {
      // the rare case of a circumcenter lying on the perimeter
      return String.format("Pcc %11.9f, center %d", p, getIndex());
    }
  }
}
