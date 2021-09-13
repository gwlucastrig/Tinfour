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
 * 05/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.shapefile;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods and elements for specifying the parameters to be
 * used to create a new shapefile.
 */
public class ShapefileWriterSpecification {

  ShapefileType shapefileType = ShapefileType.PolyLine;

  int fieldOffset = 1;
  List<DbfField> fieldList = new ArrayList<>();

  String prjContent;

  /**
   * Sets the type of shapefile for output. At this time, only the following
   * Shapefile types are supported.
   * <ul>
   * <li>Polygon</li>
   * <li>PolygonZ</li>
   * <li>Polyline</li>
   * <li>PolylineZ</li>
   * </ul>
   *
   * @param type a valid enumeration value
   */
  public void setShapefileType(ShapefileType type) {
    if (!(type.isPolygon() || type.isPolyLine()) || type.hasM()) {
      throw new IllegalArgumentException("Shapefile type: " + type.name()
        + " is not yet supported");
    }
    this.shapefileType = type;
  }

  /**
   * Adds an integer field specification for the DBF file
   * @param name a valid, non-empty string of no more than 10 ASCII characters
   * @param fieldLength the length of the field
   */
  public void addIntegerField(String name, int fieldLength) {
    String a = checkName(name);
    fieldList.add(new DbfFieldInt(a, 'N', 0, fieldLength, 0, fieldOffset));
    fieldOffset += fieldLength;
  }


    /**
   * Adds a floating-point field specification for the DBF file
   * @param name a valid, non-empty string of no more than 10 ASCII characters
   * @param fieldLength the length of the field
   * @param fieldDecimalCount the number of decimal points for the specification
   * @param useEngineeringNotation indicates whether data should be formatted in
   * engineering notation when stored to file
   */
  public void addFloatingPointField(String name,
    int fieldLength, int fieldDecimalCount,
    boolean useEngineeringNotation) {
    String a = checkName(name);
    fieldList.add(
      new DbfFieldDouble(a, 'F', 0, fieldLength, fieldDecimalCount,
        fieldOffset, useEngineeringNotation));
    fieldOffset += fieldLength;
  }

  /**
   * Add a plain-text (character) field specification for the DSBF file
   * @param name a valid, non-empty string of no more than 10 ASCII characters
   * @param fieldLength the length of the field
   */
  public void addTextField(String name, int fieldLength) {
    String a = checkName(name);
    fieldList.add(new DbfField(a, 'C', 0, fieldLength, 0, fieldOffset));
    fieldOffset += fieldLength;
  }

  private String checkName(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Null value for name not allowed");
    }
    String a = name.trim();
    if (a.isEmpty()) {
      throw new IllegalArgumentException("Empty name specification not allowed");
    }

    if (a.length() > 10) {
      throw new IllegalArgumentException(
        "Name length exceeeds 10 character max: " + name);
    }
    return a;
  }

  /**
   * Sets the content to be used to write a projection file (.prj file)
   * when the shapefile is written.  Note that this class does not currently
   * implement any logic for checking whether the content of the string is valid.
   * @param prjContent a valid string in the WKT format.
   */
  public void setShapefilePrjContent(String prjContent){
    this.prjContent = prjContent;
  }

  /**
   * Gets the content to be used to write a projection file (.prj file).
   * @return if set a valid string; otherwise a null.
   */
  public String getShapefilePrjContent(){
    return prjContent;
  }
}
