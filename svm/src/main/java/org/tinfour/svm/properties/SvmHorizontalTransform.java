/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 03/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.svm.properties;


import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;

/**
 * Provides methods and elements to transform horizontal coordinates.
 */
class SvmHorizontalTransform implements ICoordinateTransform {

  final double scale;
  final double offset;
  SvmHorizontalTransform(double scale, double offset){
    this.scale = scale;
    this.offset = offset;
  }
  @Override
  public boolean forward(double xSource, double ySource, CoordinatePair transformedCoordinates) {
      transformedCoordinates.x = xSource*scale + offset;
      transformedCoordinates.y = ySource*scale+offset;
      return true;
  }

  @Override
  public boolean inverse(double xTransformed, double yTransformed, CoordinatePair sourceCoordinates) {
       sourceCoordinates.x = (xTransformed-offset)/scale;
       sourceCoordinates.y = (yTransformed-offset)/scale;
       return true;
  }

}
