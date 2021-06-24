/* --------------------------------------------------------------------
 * Copyright (C) 2021  Gary W. Lucas.
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
 * 06/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.svm;

/**
 * Indicates the form in which the bathymetry data was specified.
 */
public enum SvmBathymetryModel {
  /**
   * Soundings are specified as depth given as positive numbers
   * below the surface.  A value of zero indicates the surface.
   */
  Depth,

  /**
   * Soundings are specified as depth given as negative numbers
   * below the surface. A value of zero indicates the surface.
   */
  DepthNegative,

  /**
   * Soundings are specified as elevations referenced to
   * to the vertical datum used by the source data, usually Mean Sea Level (MSL).
   */
  Elevation;

  /**
   * Return the value indicated by the string or a null if the string
   * is empty of does not include a recognizable value.
   * @param s a valid string
   * @return a valid instance of the enumeration or a null if no
   * value can be determined.
   */
  public static SvmBathymetryModel lenientValueOf(String s) {
    if (s != null) {
      String target = s.trim().toLowerCase();
      if (target.startsWith("depthneg")) {
        return DepthNegative;
      }else if (target.startsWith("depth")) {
        return Depth;
      } else if (target.startsWith("elev")) {
        return Elevation;
      }
    }
    return null;
  }

  /**
   * Indicates whether the model represents depth values (as opposed to elevation).
   * @return true if the model represents depth values; otherwise, false.
   */
  public  boolean isDepth(){
    return this==Depth || this==DepthNegative;
  }
}
