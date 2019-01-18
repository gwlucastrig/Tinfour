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

/**
 * Defines methods that supply style information for rendering.
 */
public enum BoundedVoronoiRenderingType {
  
  /**
   * Feature type for area-fill operations
   */
  Area,
  /**
   * Feature type for line-drawing features
   */
  Line,
  /**
   * Feature type for vertex rendering features
   */
  Vertex,
  /**
   * Featuure type for the outer boundary of a Voronoi structure.
   */
  Boundary
}

