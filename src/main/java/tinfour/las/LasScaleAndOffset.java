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
 * 02/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

/**
 * A container class to provide the scale and offset
 * factors obtained from the LAS file header.
 */
public class LasScaleAndOffset {

  /**
   * The x coordinate scale factor
   */
  public final double xScaleFactor;
  /**
   * The y coordinate scale factor
   */
  public final double yScaleFactor;
  /**
   * The z coordinate scale factor
   */
  public final double zScaleFactor;
  /**
   * The x coordinate offset
   */
  public final double xOffset;
  /**
   * The y coordinate offset
   */
  public final double yOffset;
  /**
   * The z coordinate offset
   */
  public final double zOffset;

  LasScaleAndOffset(
    double xScaleFactor,
    double yScaleFactor,
    double zScaleFactor,
    double xOffset,
    double yOffset,
    double zOffset
  ) {
    this.xScaleFactor = xScaleFactor;
    this.yScaleFactor = yScaleFactor;
    this.zScaleFactor = zScaleFactor;
    this.xOffset = xOffset;
    this.yOffset = yOffset;
    this.zOffset = zOffset;
  }

}
