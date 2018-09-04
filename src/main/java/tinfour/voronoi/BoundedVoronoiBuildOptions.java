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

import java.awt.geom.Rectangle2D;

/**
 * Specifies options for building a bounded Voronoi Diagram 
 */
public class BoundedVoronoiBuildOptions {
  
  protected boolean enableAdjustments = false;
  
  // The default adjustment value of 30 was chosen through trial and error.
  // A round number was chosen to reflect the fact that it is an arbitrary
  // choice and not a theory-defined value (a number like 32 might have
  // seemed to be more meaningful that it actually was).
  protected double  adjustmentThreshold = 30;
  
  protected Rectangle2D bounds;
  
  protected boolean enableAutomaticColorAssignment;
  
/**
   * Specifies whether adjustments to the perimeter triangle are
   * permitted to avoid excessive circumcircle radius values
   * from occurring at the perimeter triangles.  When a triangle
   * is defined by a perimeter edge and an interior vertex that lies
   * close to the edge, the geometry can give rise to a very large
   * circumcircle radius (with the resulting circumcircle center being
   * positioned far outside the boundaries of the Delaunay triangulation).
   * This, in turn results in a Bounded Voronoi Diagram structure with a
   * bounds much larger than the original bounds of the data.
   * @param status true if enabled; otherwise, false
   */
  public void setAdjustmentsEnabled(boolean status){
    this.enableAdjustments = status;
  }
  
  /**
   * Specify the threshold for deciding whether a triangle is a 
   * subject to potential adjustment to improve the ratio of its
   * circumcircle to its maximum edge length.
   * @param threshold a value in the range 8 to positive infinity
   */
  public void setAdjustmentThreshold(double threshold) {
    if (threshold < 8) {
      throw new IllegalArgumentException("Threshold value " + threshold
              + " is less than minimum of 8");
    }
    this.adjustmentThreshold = threshold;
  }
  
  /**
   * Sets the bounds for the Bounded Voronoi Diagram.  The domain of a true 
   * Voronoi Diagram is the entire coordinate plane. For practical purposes
   * the bounded Voronoi Diagram class limits the bounds to a finite
   * domain.  By default, the constructor will create bounds that are
   * slightly larger than the bounds of the input sample data set.
   * However, if an application has a specific need, it can specify
   * an alternate bounds.
   * <p>
   * <strong>Note:</strong> The alternate bounds must be at least as large
   * as the size of the sample data set.
   * @param bounds a valid rectangle.
   */
  public void setBounds(Rectangle2D bounds){
    this.bounds = bounds;
  }
  
  /**
   * Enable the automatic assignment of color values to input vertices.
   * @param status true if automatic coloring is enabled; otherwise false.
   */
  public void enableAutomaticColorAssignment(boolean status){
    this.enableAutomaticColorAssignment = status;
  }
}
