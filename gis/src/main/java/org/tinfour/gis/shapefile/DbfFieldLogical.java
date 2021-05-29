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
import org.tinfour.io.BufferedRandomAccessFile;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Represents logical data field types.
 */
 class DbfFieldLogical extends DbfField {
   boolean value;
   boolean valid;

   DbfFieldLogical(String name, char fieldType, int dataAddress, int fieldLength, int fieldDecimalCount, int offset) {
    super(name, fieldType, dataAddress, fieldLength, fieldDecimalCount, offset);
   }

   @Override
    void read(BufferedRandomAccessReader brad, long recordFilePos) throws IOException {
        brad.seek(recordFilePos + offset);
    builder.setLength(0);

    valid = false;
    value = false;

     for (int i = 0; i < fieldLength; i++) {
       int b = brad.readUnsignedByte();
       builder.append((char) b);
       if (b == 'Y' || b == 'y' || b == 'T' || b == 't') {
         value = true;
         valid = true;
       } else if (b == 'N' || b == 'n' || b == 'F' || b == 'f') {
         value = true;
         valid = true;
       }
     }
    }


   @Override
   void write(BufferedRandomAccessFile braf) throws IOException {
     int b = value ? 'T' : 'F';
     braf.writeByte(b);
   }

      /**
   * Gets a logical value.
   * @return true if the field contains a logical value of TRUE; otherwise
   * false.
   */
   @Override
  public boolean getLogicalValue(){
    return value;
  }

  /**
   * Sets the value for the field. Intended for writing files.
   * @param value true or false.
   */
  public void setLogicalValue(boolean value){
    this.value = value;
  }

   @Override
  public Object getApplicationData(){
    return value;
  }

}
