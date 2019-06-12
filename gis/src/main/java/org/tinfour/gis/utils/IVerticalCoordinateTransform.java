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
 * 06/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

 

package org.tinfour.gis.utils;

/**
 * Defines an interface for transforming the vertical coordinate value
 * of a Shapefile feature when loading it.
 */
public interface IVerticalCoordinateTransform {
  
  /**
   * Transform the vertical coordinate of a Shapefile.
   * The arguments to this method are intended to provide considerable
   * flexibility for cases in which the Shapefile features may not
   * include a meaningful Z coordinate (which is often the case).
   * Thus the record index is also provided.  Implementations may
   * use the record index to access auxiliary data files such as the
   * DBF file associated with the Shapefile or for other purposes.
   * @param recordIndex the record index from the Shapefile (records are
   * numbered from 1 to N).  Implementations may also be implemented 
   * a fixed value if that is what is required for the application.
   * @param z the vertical coordinate from the shapefile (may be 
   * undefined or set to a meaningless value)
   * @return a floating point value to be assigned to the feature
   * according to the needs of the application.
   */
  public double transform(int recordIndex, double z);
}
