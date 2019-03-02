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

  
  public ConstraintVertex(double x, double y, double z) {
    super(x, y, z);
  }

  public ConstraintVertex(double x, double y, double z, int index) {
    super(x, y, z, index);
  }

  @Override
  public String toString() {
    return "C" + super.toString();
  }
}
