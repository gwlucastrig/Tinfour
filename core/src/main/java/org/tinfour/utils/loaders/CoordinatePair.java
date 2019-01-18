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
 * 01/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.loaders;

/**
 * A simple container for holding the results of a coordinate transform. All
 * elements have public access and can be written or read at will. This class
 * provides no guarantees or safety or protection. It's sole purpose is to
 * provide an efficient mechanism for transferring data.
 */
public class CoordinatePair {

  /**
   * The x horizontal coordinate for the pair
   */
  public double x;
  /**
   * The y horizontal coordinate for the pair
   */
  public double y;

}
