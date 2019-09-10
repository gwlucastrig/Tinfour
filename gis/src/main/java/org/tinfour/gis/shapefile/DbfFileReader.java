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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.SimpleTimeZone;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Provides elements and methods for reading the DBF file associated with a
 * Shapefile
 */
public class DbfFileReader implements Closeable {

  private static final int N_BYTES_IN_FIELD_DEF = 32;

  File file;
  BufferedRandomAccessReader brad;

  final int version;
  final int nRecords;
  final int nBytesInHeader;
  final int nBytesInRecord;
  final int nFields;
  final List<DbfField> fields;
  final Date dateTime;

  /**
   * Construct a DbfFileReader from the specified file
   *
   * @param file a valid file reference to a .dbf file
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public DbfFileReader(File file) throws IOException {
    this.file = file;
    brad = new BufferedRandomAccessReader(file);
    version = brad.readUnsignedByte() & 0x03;
    // right now, only version 3 is supported
    if (version != 3) {
      throw new IOException("Unsupported DBF version " + version);
    }
    int year = brad.readUnsignedByte() + 1900;
    int month = brad.readUnsignedByte();
    int day = brad.readUnsignedByte();

    // I'm not sure, but I believe that DBF files give the months
    // in the range 1 to 12 while Java likes 0 to 11.
    if (month > 0) {
      month--;
    }

    SimpleTimeZone zone = new SimpleTimeZone(0, "UTC");
    Calendar calendar = Calendar.getInstance(zone);
    calendar.set(year, month, day);
    dateTime = calendar.getTime();
    nRecords = brad.readInt();
    nBytesInHeader = brad.readUnsignedShort();
    nBytesInRecord = brad.readUnsignedShort();
    nFields = (nBytesInHeader - 32 - 1) / N_BYTES_IN_FIELD_DEF;
    fields = new ArrayList<>(nFields);

    brad.seek(32);
    // allow 1 for the control character
    int offset = 1;
    for (int i = 0; i < nFields; i++) {
      brad.seek(32 + i * N_BYTES_IN_FIELD_DEF);
      DbfField f = DbfField.load(brad, offset);
      offset += f.getFieldLength();
      fields.add(f);
    }
  }

  /**
   * Get the number of records in the DBF file
   *
   * @return a positive integer
   */
  public int getRecordCount() {
    return nRecords;
  }

  /**
   * Get a safe copy of a list of the fields specified in the DBF file.
   *
   * @return a valid list.
   */
  public List<DbfField> getFields() {
    List<DbfField> fList = new ArrayList<>(nFields);
    fList.addAll(this.fields);
    return fList;
  }

  /**
   * Get the field matching the specified name
   *
   * @param name a valid string
   * @return if found, a valid instance; otherwise, a null.
   */
  public DbfField getFieldByName(String name) {
    for (DbfField f : fields) {
      if (f.getName().equals(name)) {
        return f;
      }
    }
    for (DbfField f : fields) {
      if (f.getName().equalsIgnoreCase(name)) {
        return f;
      }
    }
    return null;
  }

  /**
   * Read the content of the file into the field object
   *
   * @param recordNumber a valid record in the range 1 to record-count.
   * @param field a valid field obtained from the DbfFileReader instance and
   * used to hold the data read from the file
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public void readField(int recordNumber, DbfField field) throws IOException {
    if (recordNumber < 1 || recordNumber > nRecords) {
      throw new IOException("Record " + recordNumber
              + " out of range 1 to " + nRecords);
    }
    long recordOffset = (long)nBytesInHeader + (long)(recordNumber - 1) * nBytesInRecord;
    field.read(brad, recordOffset);
  }

  @Override
  public void close() throws IOException {
    if (brad != null) {
      brad.close();
    }
  }
}
