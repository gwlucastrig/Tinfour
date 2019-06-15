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
 * 12/2018  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.loaders;

/**
 * Defines an interface for performing coordinate transforms. These transforms
 * operate on the horizontal (2D) coordinates of the input.
 */
public interface ICoordinateTransform {

  /**
   * Transforms a point in a source coordinate system to the equivalent output.
   * The return value indicates whether the transform was successful. In some
   * cases, a transform may be defined only over a finite region.
   * <p>
   * All transforms are assumed to be fully invertible (bijective) over their
   * domain. If a forward transform is defined for a set of input coordinates,
   * then the inverse transform must be defined for the resulting output
   * coordinates.
   *
   * @param xSource the x value in the source coordinate system
   * @param ySource the y value in the source coordinate system
   * @param transformedCoordinates a simple container to receive the results of
   * the computation, the transformed coordinates for (xSource, ySource).
   * @return true if the transform is successful; otherwise, false
   */
  boolean forward(
          double xSource,
          double ySource,
          CoordinatePair transformedCoordinates);

  /**
   * Transforms a point in an output coordinate system to the equivalent point
   * in the source coordinate system. The return value indicates whether the
   * transform was successful. In some cases, a transform may be defined only
   * over a finite region.
   * <p>
   * All transforms are assumed to be fully invertible (bijective) over their
   * domain. If a forward transform is defined for a set of input coordinates,
   * then the inverse transform must be defined for the resulting output
   * coordinates.
   *
   * @param xTransformed x value in the transformed coordinate system
   * @param yTransformed y value in the transformed coordiante system
   * @param sourceCoordinates a simple container to receive the results of the
   * computation, the transformed coordinates for (xSource, ySource).
   * @return true if the inverse transform is successful; otherwise, false
   */
  boolean inverse(
          double xTransformed,
          double yTransformed,
          CoordinatePair sourceCoordinates);

}
