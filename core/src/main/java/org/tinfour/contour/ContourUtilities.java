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
 * 04/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.contour;

/**
 * Provides utility methods for application that perform contouring.
 */
public class ContourUtilities {

  /**
   * A private constructor to deter applications from creating instances
   * of this class.
   */
  private ContourUtilities(){
    // no action required
  }

 /**
  * Compute contour values as even multiples of zInterval within the
  * range of zMin and zMax.  If no contour can be constructed with the
  * interval, a zero-sized array will be returned.
  *
  * @param zMin the minimum value of interest
  * @param zMax the maximum value of interest
  * @param zInterval the contour spacing interval
  * @return a valid, potentially zero-sized array
  */
 public static double[] computeContourValues(
   double zMin,
   double zMax,
   double zInterval)
 {
    long i0 = (long) Math.ceil(zMin / zInterval);
    long i1 = (long) Math.floor(zMax / zInterval);
    if(i1<i0){
      // this could happen if the values for zMin and zMax were bad
      // and zMin > zMax, but will also happen in cases such as
      // zMin=7, zMax=8, and zInterval=10, where no contours can be constructed.
      return new double[0];
    }
    long n = i1 - i0 + 1;
    double[] zContour = new double[(int) n];
    for (int i = 0; i < n; i++) {
      zContour[i] = (i0 + i) * zInterval;
    }

    return zContour;
  }

}
