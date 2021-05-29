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
  NullShape(0, false, false),
  Point(1, false, false),
  PolyLine(3, false, false),
  Polygon(5, false, false),
  MultiPoint(8, false, false),
  PointZ(11, true, false),
  PolyLineZ(13, true, false),
  PolygonZ(15, true, false),
  MultiPointZ(18, true, false),
  PointM(21, false, true),
  PolyLineM(23, false, true),
  PolygonM(25, false, true),
  MultiPointM(28, false, true),
  MultiPatch(31, false, true);

  private final int shapeTypeCode;
  private final boolean hasZ;
  private final boolean hasM;

  ShapefileType(int shapeTypeCode, boolean hasZ, boolean hasM) {
    this.shapeTypeCode = shapeTypeCode;
    this.hasZ = hasZ;
    this.hasM = hasM;

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
   * Polygons are treated as being different than polylines.
   *
   * @return true if the type is a polygon; otherwise, false
   */
  public boolean isPolygon() {
    return this == PolygonZ || this == Polygon || this == PolygonM;
  }

  /**
   * Indicates if the specified Shapefile type defines a polyline geometry.
   * Polylines are treated as being different than polygons.
   *
   * @return true if the type is a polyline; otherwise, false
   */
  public boolean isPolyLine() {
    return this == PolyLineZ || this == PolyLine || this == PolyLineM;
  }

  /**
   * Indicates whether the Shapefile has non-zero Z coordinates
   * (is three-dimensional).
   *
   * @return true if the file is three-dimensional; otherwise, false.
   */
  public boolean is3D() {
    return hasZ;
  }

  /**
   * Indicates whether the Shapefile has valid Z coordinates
   * (is three-dimensional).
   *
   * @return true if the file is has valid Z coordinates; otherwise, false.
   */
  public boolean hasZ() {
    return hasZ;
  }

  /**
   * Indicates whether the Shapefile type has a measure specification
   *
   * @return true if the file has a measure specification; otherwise, false.
   */
  public boolean hasM() {
    return hasM;
  }
}
