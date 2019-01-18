/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
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
 * 11/2018  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

 

package org.tinfour.utils;

/**
 * Provides simple calculations useful for various Tinfour operations.
 */
public final class Tincalc {
  
  /** The sine of 60 degrees */
  static private final double SIN60 =  Math.sqrt(3)/2.0;  // about 0.866
  
  /**
   * Estimates the average distance between point samples distributed randomly
   * within a specified area with approximately uniform density,
   * specified in arbitrary units.
   * <p>
   * The approximation used by this method makes the simplifying assumption
   * that the points are organized in a regular tessellation of equilateral
   * triangles. For a sufficiently large number of points, N, the
   * number of triangles in the tessellation approaches 2N. And, for area, A,
   * the area of individual triangles approaches A/2N. The approximate 
   * space between points is just the length of the side of one of these
   * triangles.
   * <p>The quality of this estimate improves with increasing point count
   * and input data that conforms to the assumptions of the approximation.
   * Note that the 2N triangle ratio is also true for Delaunay Triangulations.
   * @param area the area of the region containing points
   * @param n the number of points
   * @return a distance value (units given in the square root of the area units)
   */
  public static double sampleSpacing(double area, int n){
    if(n<=0){
      throw new IllegalArgumentException(
              "Specified number of points must be greater than zero, "
              +"input n="+n);
    }
    return Math.sqrt(Math.abs(area/n)/SIN60);
  }
  
  /**
   * Private constructor to deter application code from
   * instantiating this class.
   */
  private Tincalc(){
    // no action required
  }
}
