/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 03/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.demo.utils;

import org.tinfour.common.IIncrementalTin;
import org.tinfour.gwr.GwrTinInterpolator;
import org.tinfour.interpolation.IInterpolatorOverTin;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.interpolation.TriangularFacetInterpolator;

/**
 * An enumeration indicating an interpolation method for test purposes.
 */
public enum InterpolationMethod {

  /** Sibson's Natural Neighbor interpolation method *//** Sibson's Natural Neighbor interpolation method */
  NaturalNeighbor,
  /** Planar triangular facet interpolation method */
  TriangularFacet,
  /** Geographically weighted regression interpolation method*/
  GeographicallyWeightedRegression;

  /**
   * Determine an interpolation method, applying a lenient rules
   * to determine which one is indicated by the string.
   * @param target a valid string
   * @return if recognized, a valid enumeration value; otherwise, a null.
   */
  public static InterpolationMethod lenientValue(String target) {
    if (target != null) {
      String s = target.trim().toLowerCase();
      if (s.startsWith("nat")) {
        return NaturalNeighbor;
      } else if (s.startsWith("tri")) {
        return TriangularFacet;
      } else if (s.contains("regress") || s.startsWith("gwr")) {
        return GeographicallyWeightedRegression;
      }
    }
    return null;
  }



  /**
   * Constructs an interpolator based on the enumeration value.
   * @param tin a valid instance of an IIncrementalTin implementation.
   * @return a valid interpolator.
   */
  public IInterpolatorOverTin getInterpolator(IIncrementalTin tin){
      switch(this){
        case TriangularFacet:
          return new TriangularFacetInterpolator(tin);
        case GeographicallyWeightedRegression:
          return new GwrTinInterpolator(tin);
        case NaturalNeighbor:
        default:
          return new NaturalNeighborInterpolator(tin);
      }
  }

}
