/*
 * Copyright 2014 Gary W. Lucas.
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
 * Date     Name        Description
 * ------   ---------   -------------------------------------------------
 * 08/2018  G. Lucas    Created
 *
 * Notes:
 *
 *--------------------------------------------------------------------------
 */ 
package org.tinfour.common;
 
/**
 * Provides a wrapper class used to represent the adjusted position 
 * of a vertex.
 */
public class VertexAdjustment extends Vertex {
  
  private final Vertex vertex;

  /**
   * Construct an instance with the specified Cartesian coordinates
   * while copying the attributes of the original vertex.
   * @param x the adjusted x coordinate
   * @param y the adjusted y coordinate
   * @param vertex the original vertex
   */
  public VertexAdjustment(double x, double y, Vertex vertex) {
    super(x, y, vertex.getZ(), vertex.getIndex());
    this.vertex = vertex;
    // transcribe attributes and set status to synthetic
    this.status = (byte)(vertex.status | BIT_SYNTHETIC);
    this.auxiliary = vertex.auxiliary;
  }
  
  /**
   * Gets the original vertex that was used to produce this instance
   * @return a valid vertex
   */
  public Vertex getVertex(){
    return vertex;
  }
  
  
  
}
