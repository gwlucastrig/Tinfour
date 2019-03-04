/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 02/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.utils;

import org.tinfour.common.Vertex;

/**
 * Provides a Vertex implementation with special labeling for indicating
 * vertices that were contributed by a constraint
 */
public class ConstraintVertex extends Vertex {
 
    /**
   * Construct a vertex with the specified coordinates and ID value. If the z
   * value is NaN then the vertex will be treated as a "null data value".
   *
   * @param x the coordinate on the surface on which the vertex is defined
   * @param y the coordinate on the surface on which the vertex is defined
   * @param z the data value (z coordinate of the surface)
   * @param index the ID of the vertex (intended as a diagnostic)
   */
  public ConstraintVertex(double x, double y, double z, int index) {
    super(x, y, z, index);
  }

  /**
   * Gets a string intended for labeling the vertex in images or
   * reports. The default label is the index of the vertex preceeded
   * by the letter C if the vertex is synthetic. Note that the
   * index of a vertex is not necessarily unique.  If constraints are
   * loaded from multiple sources, some of their vertices will have the
   * same indices.
   *
   * @return a valid, non-empty string.
   */
  @Override
  public String getLabel() {
    return "C"+ Integer.toString(getIndex());
  }
  
  
  @Override
  public String toString() {
    return "C" + super.toString();
  }
}
