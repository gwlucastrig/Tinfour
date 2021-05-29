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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import org.tinfour.io.BufferedRandomAccessFile;

/**
 *
 */
public class DbfFileWriter implements Closeable {

  private static final int N_BYTES_IN_FIELD_DEF = 32;

  final File outputFile;
  final BufferedRandomAccessFile braf;

  private int nRecords;
  private final List<DbfField> fields;

  public DbfFileWriter(File outputFile, List<DbfField> fields) throws IOException {
    if (fields.isEmpty()) {
      throw new IllegalArgumentException("Empty fields specification not allowed");
    }
    this.fields = fields;

    this.outputFile = outputFile;
    braf = new BufferedRandomAccessFile(outputFile, "rw");
    braf.writeByte(3);

    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR) - 1900;
    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    braf.writeByte(year);
    braf.writeByte(month);
    braf.writeByte(day);
    braf.leWriteInt(0); // number of records in the table
    int nBytesInHeader = fields.size() * N_BYTES_IN_FIELD_DEF + 32 + 1;
    braf.leWriteShort((short) nBytesInHeader); // number of bytes in the header
    int nBytesInRecord = 1;
    for (DbfField f : fields) {
      nBytesInRecord += f.getFieldLength();
    }
    braf.leWriteShort((short) nBytesInRecord);
    int fPos = (int) braf.getFilePosition();
    for (int i = fPos; i < 32; i++) {
      braf.writeByte(0);
    }
    for (DbfField field : fields) {
      braf.writeASCII(field.getName(), 11);
      braf.writeByte(field.fieldType);
      braf.leWriteInt(0);  // data address?
      braf.writeByte(field.getFieldLength());
      braf.writeByte(field.getFieldDecimalCount());
      for (int i = 18; i < 32; i++) {
        braf.writeByte(0);
      }
    }
    braf.write(0x0d); // The header terminator.  This is the +1 in 32+1
  }

  /**
   * Set the value of the named DBF field using the specified integer value.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, int value) {
    DbfField f = matchNameToField(name);
    if (f instanceof DbfFieldInt) {
      ((DbfFieldInt) f).setInteger(value);
    } else if (f instanceof DbfFieldDouble) {
      ((DbfFieldDouble) f).setInteger(value);
    } else {
      throw new IllegalArgumentException(
        "Field " + f.getName()
        + " is not a numeric type");
    }
  }

  /**
   * Set the value of the named DBF field using the specified floating-point
   * value.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, double value) {
    DbfField f = matchNameToField(name);
    if (f instanceof DbfFieldInt) {
      ((DbfFieldInt) f).setDouble(value);
    } else if (f instanceof DbfFieldDouble) {
      ((DbfFieldDouble) f).setDouble(value);
    } else {
      throw new IllegalArgumentException(
        "Field " + f.getName()
        + " is not a numeric type");
    }
  }

  /**
   * Set the value of the named DBF field using the specified string.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, String value) {
    DbfField f = matchNameToField(name);
    f.setString(value);
  }

  private DbfField matchNameToField(String name) {
    for (DbfField f : fields) {
      if (f.getName().equalsIgnoreCase(name)) {
        return f;
      }
    }
    throw new IllegalArgumentException("No field matches specified name " + name);
  }

  /**
   * Flushes any pending write operations and updates the header
   * elements to reflect the number of records.
   *
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  public void flush() throws IOException {
    braf.seek(4);
    braf.leWriteInt(nRecords);
    braf.flush();
    braf.seek(braf.getFileSize());
  }

  @Override
  public void close() throws IOException {
    if (!braf.isClosed()) {
      flush();
      long fileSize = braf.getFileSize();
      braf.seek(fileSize);
      braf.write(0x1A); // end-of-file marker
      braf.close();
    }
  }

  public void writeRecord() throws IOException {
    long fileSize = braf.getFileSize();
    braf.seek(fileSize);
    braf.write(0x20); // a space, indicating that the record is not deleted
    for (DbfField f : fields) {
      f.write(braf);
    }
    nRecords++;
  }

}
