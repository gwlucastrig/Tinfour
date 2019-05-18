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
 * 04/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm.properties;

/**
 * Specifies a unit of measure and associated scaling factor
 */
public class SvmUnitSpecification {
  private final String name;
  private final String label;
  private final double scaleFactor;

  SvmUnitSpecification( String name, String label, double scaleFactor) {
    this.name = name;
    this.label = label;
    this.scaleFactor = scaleFactor;
 
  }

  /**
   * Get the name of the unit
   * @return a valid string
   */
  public String getLabel() {
    return label;
  }

  /**
   * Get a scaling factor for the unit of measure
   * @return a valid, non-zero floating point value
   */
  public double getScaleFactor() {
    return scaleFactor;
  }
}
