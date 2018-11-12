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
package tinfour.test.shapefile;

import java.io.IOException;
import tinfour.io.BufferedRandomAccessReader;

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
          throws IOException 
  {
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

    if (fieldType == 'L') {
      return new DbfFieldLogical(
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
    return "DbfField(" + fieldType + ") " + name;
  }
}
