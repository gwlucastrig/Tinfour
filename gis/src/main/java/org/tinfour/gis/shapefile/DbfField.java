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
package org.tinfour.gis.shapefile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Provides definitions and access methods for a DBF field.
 * <p>
 * A number of features are not yet supported. Only the data types C
 * (character), N (numeric) and F (floating-point) are supported. Only
 * iso-8859-1 character sets are supported.
 *
 */
public class DbfField {

  protected final String name;
  protected final char fieldType;
  protected final int dataAddress;
  protected final int fieldLength;
  protected final int fieldDecimalCount;
  protected final int offset;
  protected final StringBuilder builder;
  protected final boolean numeric;
  protected final boolean isIntegral;

  /**
   * Constructs an instance based on specifications from the DBF file header
   *
   * @param name the name of the field
   * @param fieldType the type of the field
   * @param dataAddress not supported at this time
   * @param fieldLength the length of the field
   * @param fieldDecimalCount the number of decimal digits in the field
   * @param offset the offset of the field within the fixed-length record
   */
  DbfField(String name,
          char fieldType,
          int dataAddress,
          int fieldLength,
          int fieldDecimalCount,
          int offset) {
    this.name = name;
    this.fieldType = fieldType;
    this.dataAddress = dataAddress;
    this.fieldLength = fieldLength;
    this.fieldDecimalCount = fieldDecimalCount;
    this.offset = offset;
    this.builder = new StringBuilder(fieldLength + 1);
    this.numeric = fieldType == 'N' || fieldType == 'F';
    isIntegral = fieldType == 'N' && fieldDecimalCount == 0;
  }

  /**
   * Read specifications from the DBF header create an instance of a DbfField.
   *
   * @param brad a read-only random access file
   * @param offset the file offset position
   * @return a valid instance
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  static DbfField load(BufferedRandomAccessReader brad, int offset)
          throws IOException {
    String name = brad.readAscii(11);
    char fieldType = (char) brad.readUnsignedByte();
    int dataAddress = brad.readInt();
    int fieldLength = brad.readUnsignedByte();
    int fieldDecimalCount = brad.readUnsignedByte();

    if (fieldType == 'F' || (fieldType == 'N' && fieldDecimalCount > 0)) {
      return new DbfFieldDouble(
              name, fieldType, dataAddress,
              fieldLength, fieldDecimalCount,
              offset);
    }
    if (fieldType == 'N' && fieldDecimalCount == 0) {
      return new DbfFieldInt(
              name, fieldType, dataAddress,
              fieldLength, fieldDecimalCount,
              offset);
    }

    if (fieldType == 'L') {
      return new DbfFieldLogical(
              name, fieldType, dataAddress,
              fieldLength, fieldDecimalCount,
              offset);
    }

    if (fieldType == 'D') {
      return new DbfFieldDate(
              name, fieldType, dataAddress,
              fieldLength, fieldDecimalCount,
              offset);
    }

    return new DbfField(
            name, fieldType, dataAddress,
            fieldLength, fieldDecimalCount,
            offset);
  }

  /**
   * Read the content of the DBF file for the indicated record file position.
   * The record file position indicates the offset to the beginning of the
   * fixed-length record within the DBF file. The DbfField instance includes an
   * offset within that record so that it can perform a read operation.
   *
   * @param brad a valid instance
   * @param recordFilePos the position of the beginning of the record
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  void read(BufferedRandomAccessReader brad, long recordFilePos) throws IOException {
    brad.seek(recordFilePos + offset);
    builder.setLength(0);
    if (isNumeric()) {
      for (int i = 0; i < getFieldLength(); i++) {
        char c = (char) brad.readUnsignedByte();
        if (!Character.isWhitespace(c)) {
          builder.append((char) c);
        }
      }
    } else {
      int lastNonBlank = 0;
      for (int i = 0; i < getFieldLength(); i++) {
        char c = (char) brad.readUnsignedByte();
        if (!Character.isWhitespace(c)) {
          lastNonBlank = i + 1;
        }
        builder.append((char) c);
      }
      builder.setLength(lastNonBlank);
    }
  }

  /**
   * Indicates whether the field supports integral data value type.
   *
   * @return true if the field can return an integer; otherwise, false
   */
  public boolean isIntegral() {
    return isIntegral;
  }

  /**
   * Gets the integer value of the field, if it is of an integral type,
   * otherwise returns a zero.
   *
   * @return a valid integral value, or a zero if undefined.
   */
  public int getInteger() {
    if (isIntegral) {
      return Integer.parseInt(builder.toString());
    }
    return 0;
  }

  /**
   * Gets the double value stored in the field during the most recent read
   * operation, if any
   *
   * @return a valid double or a NaN if this is not a numeric field
   */
  public double getDouble() {
    if (isNumeric() && builder.length() > 0) {
      return Double.parseDouble(builder.toString());
    }
    return Double.NaN;
  }

  /**
   * Gets the string value stored in the field during the most recent read
   * operation.
   *
   * @return a valid string, potentially empty
   */
  public String getString() {
    return builder.toString();
  }

  /**
   * Gets the name of the data field
   *
   * @return a valid string
   */
  public String getName() {
    return name;
  }

  /**
   * Indicates the type of the data field as defined in the DBF spec.
   *
   * @return a single character as defined in the DBF spec.
   */
  public char getFieldType() {
    return fieldType;
  }

  /**
   * Indicates the overall length of the data field
   *
   * @return the fieldLength
   */
  public int getFieldLength() {
    return fieldLength;
  }

  /**
   * For numeric fields, indicates the number of decimal digits.
   *
   * @return the fieldDecimalCount
   */
  public int getFieldDecimalCount() {
    return fieldDecimalCount;
  }

  /**
   * Indicates if the field defines a numeric value
   *
   * @return the numeric
   */
  public boolean isNumeric() {
    return numeric;
  }

  /**
   * Gets a logical value.
   *
   * @return true if the field contains a logical value of TRUE; otherwise
   * false.
   */
  public boolean getLogicalValue() {
    return false;
  }

  @Override
  public String toString() {
    return String.format("DbfField (%s %2d.%-2d) %s",
            fieldType, fieldLength, fieldDecimalCount, name);
  }

  /**
   * Gets the data for the field as an Object of the appropriate class. For the
   * DbdField base class, the return value is a string. For others, the return
   * type may vary.
   *
   * @return an object, potentially null if the record has not been read.
   */
  public Object getApplicationData() {
    return builder.toString();
  }

  /**
   * Indicates whether the value in the field was encoded using engineering
   * notation (e.g. scientific notation). The return value will be false for all
   * field types except floating-point types.
   *
   * @return true if engineering notation was used; otherwise false.
   */
  public boolean usesEngineeringNotation() {
    return false;
  }

  /**
   * Gets the unique values for this field as a list of strings.
   *
   * @param dbf the DBF with which this field is associated
   * @return if successful, a valid list of unique values
   * @throws IOException in the event of an unrecoverable IO condition.
   */
  public List<String> getUniqueValues(DbfFileReader dbf) throws IOException {
    int nRecords = dbf.getRecordCount();
    List<String> list = new ArrayList<String>(nRecords);
    if (nRecords == 0) {
      return list;
    }

    // Investigate:  Because we are going to sort these strings anyway,
    // perhaps this logic would be better served by a Java tree rather
    // than a map.
    int k = 0;
    HashMap<String, String> map = new HashMap();
    String sMin = "";
    String sMax = "";
    for (int i = 1; i <= nRecords; i++) {
      dbf.readField(i, this);
      String s = getString();
      if (i == 1) {
        map.put(s, s);
        list.add(s);
        sMin = s;
        sMax = s;
      } else if (!map.containsKey(s)) {
        map.put(s, s);
        list.add(s);
        if (s.compareTo(sMin) < 0) {
          sMin = s;
        }
        if (s.compareTo(sMax) > 0) {
          sMax = s;
        }
        if (sMin.length() > 20) {
          sMin = sMin.substring(0, 17) + "...";
        }
        if (sMax.length() > 20) {
          sMax = sMax.substring(0, 17) + "...";
        }
      }
    }
    return list;
  }

  /**
   * Indicates whether the field contains a null value.
   * Because the interpretation of null fields is not well defined by the
   * DBF file specification, this method should be used with care.
   * @return true if the field is empty, otherwise false.
   */
  public boolean isNull(){
    return builder.length()==0;
  }
}
