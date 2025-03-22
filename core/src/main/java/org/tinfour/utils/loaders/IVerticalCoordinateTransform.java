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
 * 03/2025  G. Lucas     Moved and modified for wider use.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.utils.loaders;

/**
 * Defines an interface for transforming the vertical coordinate value
 * of a feature when loading it.
 */
public interface IVerticalCoordinateTransform {

  /**
   * Transform the vertical coordinate of a feature.
   * The arguments to this method are intended to provide considerable
   * flexibility for cases in which the data source features may not
   * include a meaningful Z coordinate directly, but must obtain one from
   * a supplemental or associated metadata source.
   * Thus the reference index is also provided.  Implementations may
   * use the reference index as appropriate to access auxiliary data files.
   * For example, a shapefile reader might use it to access the metadata
   * in the DBF file associated with the Shapefile.
   * @param referenceIndex an optional, supplemental index defined by the implementation.
   * @param z the vertical coordinate from the data source. In some cases,
   * an implementation may elect to treat this value as undefined or
   * meaningless and may derive a vertical coordinate from the reference index.
   * @return a floating point value to be assigned to the feature
   * according to the needs of the application.
   */
  double transform(int referenceIndex, double z);
}
