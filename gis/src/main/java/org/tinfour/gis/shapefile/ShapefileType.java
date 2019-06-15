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
 * 01/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.shapefile;

/**
 * An enumeration defining Shapefile
 */
public enum ShapefileType {
  NullShape(0, false),
  Point(1, false),
  PolyLine(3, false),
  Polygon(5, false),
  MultiPoint(8, false),
  PointZ(11, true),
  PolyLineZ(13, true),
  PolygonZ(15, true),
  MultiPointZ(18, true),
  PointM(21, false),
  PolyLineM(23, false),
  PolygonM(25, false),
  MultiPointM(28, false),
  MultiPatch(31, false);

  private final int shapeTypeCode;
  private final boolean hasZ;

  ShapefileType(int shapeTypeCode, boolean hasZ) {
    this.shapeTypeCode = shapeTypeCode;
    this.hasZ = hasZ;
  }

  /**
   * Resolves the numeric code from a Shapefile to an enumeration instances
   *
   * @param code a valid integer code
   * @return if successful, a valid instance; otherwise, a null.
   */
  public static ShapefileType getShapefileType(int code) {
    for (ShapefileType v : ShapefileType.values()) {
      if (v.shapeTypeCode == code) {
        return v;
      }
    }

    return null;
  }

  /**
   * Gets the integer code value for this Shapefile type.
   *
   * @return a positive integer
   */
  public int getTypeCode() {
    return shapeTypeCode;
  }

  /**
   * Indicates if the specified Shapefile type defines a polygon geometry.
   *
   * @return true if the type is a polygon; otherwise false
   */
  public boolean isPolygon() {
    return this == PolygonZ || this == Polygon || this == PolygonM;
  }

  /**
   * Indicates whether the Shapefile has non-zero Z coordinates
   * (is three-dimensional).
   * @return true if the file is three-dimensional; otherwise, false.
   */
  public boolean is3D(){
    return hasZ;
  }
}
