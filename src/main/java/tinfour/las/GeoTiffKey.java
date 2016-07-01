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
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

/**
 * Provides elements and methods for accessing data related to
 * a GeoTIFF key specification.
 */
public class GeoTiffKey {

  /**
   * The integer key code from the GeoTIFF specification
   */
  public final int key;

  /**
   * The TIFF tag code indicating if data for this TiffKey comes
   * from a supplemental floating-point or string element.
   * If the key is zero, then the valueOrOffset element carries the
   * value for this key (the count will always be one). If the
   * key is 34736, then the data will come from a section of the
   * TIFF byte source that carries floating-point values. If the key is
   * 34737, then the data will come from a second of the TIFF
   * byte source that carries ASCII coded String values.
   */
  public final int location;

  /**
   * The number of elements associated with this key.
   */
  public final int count;

  /**
   * Alternately, the value associated with the key (if the location
   * is zero) or the offset into the TUFF byte source that carries
   * the relevant binary information for this key.
   */
  public final int valueOrOffset;

  /**
   * Standard constructor
   *
   * @param key the TIFF key
   * @param location the data location
   * @param count the number of elements associated with this key
   * @param valueOrOffset the value or data offset for obtaining
   * data from the TIFF byte source.
   */
  GeoTiffKey(int key, int location, int count, int valueOrOffset) {
    this.key = key;
    this.location = location;
    this.count = count;
    this.valueOrOffset = valueOrOffset;
  }

  /**
   * Gets the key code for this instance
   * @return a valid key code
   */
  public int getKeyCode(){
   return key;
  }

}
