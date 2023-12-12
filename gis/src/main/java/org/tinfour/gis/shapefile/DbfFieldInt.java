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
package org.tinfour.gis.shapefile;

import java.io.IOException;
import java.util.Arrays;
import org.tinfour.io.BufferedRandomAccessFile;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Extends DbfField with special handling for reading integer values.
 */
public class DbfFieldInt extends DbfField {

  private int value;
  private String writingFormat;


  DbfFieldInt(
          String name,
          char fieldType,
          int dataAddress,
          int fieldLength,
          int fieldDecimalCount,
          int offset) {
    super(name, fieldType, dataAddress, fieldLength, fieldDecimalCount, offset);
    writingFormat = String.format("%%%dd",fieldLength);
  }

  @Override
  void read(BufferedRandomAccessReader brad, long recordFilePos) throws IOException {
    brad.seek(recordFilePos + offset);
    builder.setLength(0);

    int i = 0;
    int sign = 1;
    long s = 0;

    // find first non-space character
    boolean foundDigit = false;
    while (i < fieldLength) {
      int b = brad.readUnsignedByte();
      builder.append((char) b);
      if (b == 32) {
        i++;
      } else if (b == '-') {
        sign = -1;
        i++;
        break;
      } else if (b == '+') {
        i++;
        break;
      } else if (48 <= b && b <= 57) {
        s = b - 48;
        i++;
        foundDigit = true;
        break;
      } else {
        // a non-whitespace character.  at this time,
        // the meaning of this is unknown.
        value = 0;
        builder.setLength(0);
        if(b=='*'){
          return;
        }
        throw new IOException(
                "Invalid integer value, unknown character "
                + ((char) b));
      }
    }

    if (!foundDigit) {
      value = 0;
      builder.setLength(0);
      return;
    }

    // process the non-fractional part
    while (i < fieldLength) {
      int b = brad.readUnsignedByte();
      builder.append((char) b);
      if (48 <= b && b <= 57) {
        s = s * 10 + (b - 48);
      } else if (s == 32) {
        break;
      } else {
        value = 0;
        throw new IOException(
                "Invalid integer value, unknown character "
                + ((char) b));
      }
      i++;
    }

    s *= sign;

    if (s > Integer.MAX_VALUE) {
      value = Integer.MAX_VALUE;
      throw new IOException("Invalid integer value out of range " + s);
    } else if (s < Integer.MIN_VALUE) {
      value = Integer.MIN_VALUE;
      throw new IOException("Invalid integer value out of range " + s);
    }

    value = (int) s;
  }

  @Override
  void write(BufferedRandomAccessFile braf) throws IOException {
    String s = String.format(writingFormat, value);
    byte[] b = s.getBytes();
    braf.write(b, 0, fieldLength);
  }


  /**
   * Gets the double value stored in the field during the most recent read
   * operation, if any
   *
   * @return a valid double or a NaN if the file content was invalid
   */
  @Override
  public double getDouble() {
    return value;
  }

  /**
   * Gets the equivalent integer value of the field.
   *
   * @return a valid integral value, or a zero if undefined.
   */
  @Override
  public int getInteger() {
    return value;
  }


  /**
   * Sets a value for the field.  Intended for writing files.
   * @param value a valid floating point value in the range of integers
   */
  public void setDouble(double value){
    this.value = (int)value;
  }

    /**
   * Sets a value for the field.  Intended for writing files.
   * @param value a valid integer value.
   */
  public void setInteger(int value){
    this.value = value;
  }



  @Override
  public Object getApplicationData() {
    return value;
  }

  /**
   * Gets an array of unique values for this field.
   *
   * @param dbf a valid instance
   * @return if successful an array of zero or more elements.
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public int[] getUniqueValueArray(DbfFileReader dbf) throws IOException {
    int vMin = Integer.MAX_VALUE;
    int vMax = Integer.MIN_VALUE;
    int nRecords = dbf.getRecordCount();
    int[] vArray = new int[nRecords];
    if (nRecords == 0) {
      return new int[0];
    }

    int k = 0;
    for (int i = 1; i <= nRecords; i++) {
      dbf.readField(i, this);
      int v = getInteger();
      if (v < vMin) {
        vMin = v;
      }
      if (v > vMax) {
        vMax = v;
      }
      vArray[k++] = v;
    }

    Arrays.sort(vArray);
    int nUniqueValues = 1;
    int prior = vArray[0];
    for (int i = 1; i < nRecords; i++) {
      if (vArray[i] != prior) {
        prior = vArray[i];
        vArray[nUniqueValues] = vArray[i];
        nUniqueValues++;
      }
    }
    return Arrays.copyOf(vArray, nUniqueValues);
  }
}
