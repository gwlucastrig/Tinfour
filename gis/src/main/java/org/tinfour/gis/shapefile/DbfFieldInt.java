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
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Extends DbfField with special handling for reading integer values.
 */
class DbfFieldInt extends DbfField {

  private int value;

  DbfFieldInt(
          String name,
          char fieldType,
          int dataAddress,
          int fieldLength,
          int fieldDecimalCount,
          int offset) {
    super(name, fieldType, dataAddress, fieldLength, fieldDecimalCount, offset);
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
        throw new IOException(
                "Invalid integer value, unknown character "
                +((char)b));
      }
    }

    if(!foundDigit){
       throw new IOException("Invalid integer value, blank field");
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
                +((char)b));
      }
      i++;
    }

    s *= sign;

    if (s > Integer.MAX_VALUE) {
      value = Integer.MAX_VALUE;
      throw new IOException("Invalid integer value out of range "+s);
    } else if (s < Integer.MIN_VALUE) {
      value = Integer.MIN_VALUE;
      throw new IOException("Invalid integer value out of range "+s);
    }

    value = (int) s;

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

  @Override
  public Object getApplicationData() {
    return value;
  }

}
