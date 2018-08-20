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
package tinfour.voronoi;

/**
 * Specifies options for building a limited Voronoi Diagram 
 */
public class LimitedVoronoiBuildOptions {
  
  protected boolean enableAdjustments = true;
  protected double  adjustmentThreshold = 32;
  
  /**
   * Specifies whether adjustments to the perimeter triangle are
   * permitted to avoid excessive circumcircle radius values
   * from occurring at the perimeter triangles.
   * @param status true if enabled; otherwise, false
   */
  public void setAdjustmentsEnabled(boolean status){
    this.enableAdjustments = status;
  }
  
  /**
   * Specify the adjustment threshold factor.  The adjustment threshold 
   * factor will be applied to the length of the diagonal of the sample
   * bounds to compute a maximum target circumcircle radius for the
   * perimeter triangles.
   * @param threshold a value in the range 1.0 to positive infinity
   */
  public void setAdjustmentThreshold(double threshold) {
    if (threshold < 1) {
      throw new IllegalArgumentException("Threshold value " + threshold
              + " is less than minimum of 1");
    }
    this.adjustmentThreshold = threshold;
  }
}
