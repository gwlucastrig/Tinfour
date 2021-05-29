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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.tinfour.io.BufferedRandomAccessFile;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Extends DbfField with special handling for reading date values.
 */
public class DbfFieldDate extends DbfField {

  private ZonedDateTime value;
  private ZoneOffset zoneOffset = ZoneOffset.UTC;

  DbfFieldDate(
          String name,
          char fieldType,
          int dataAddress,
          int fieldLength,
          int fieldDecimalCount,
          int offset) {
    super(name, fieldType, dataAddress, fieldLength, fieldDecimalCount, offset);
  }

  private int scan1(int b, StringBuilder sb) throws IOException {
    sb.append((char) b);
    if (b < 48 || b > 57) {
      if (Character.isWhitespace((char) b)) {
        return 0;
      } else {
        throw new IOException("Invalid character (" + b
                + ") where numeric expected in date field");
      }
    }
    return b - 48;
  }

  @Override
  void read(BufferedRandomAccessReader brad, long recordFilePos) throws IOException {
    brad.seek(recordFilePos + offset);

    // The date format is simply YYYYMMDD
    builder.setLength(0);
    int y0 = scan1(brad.readUnsignedByte(), builder);
    int y1 = scan1(brad.readUnsignedByte(), builder);
    int y2 = scan1(brad.readUnsignedByte(), builder);
    int y3 = scan1(brad.readUnsignedByte(), builder);
    int year = ((y0 * 10 + y1) * 10 + y2) * 10 + y3;

    int m1 = scan1(brad.readUnsignedByte(), builder);
    int m2 = scan1(brad.readUnsignedByte(), builder);
    int month = m1 * 10 + m2;

    int d1 = scan1(brad.readUnsignedByte(), builder);
    int d2 = scan1(brad.readUnsignedByte(), builder);
    int day = d1 * 10 + d2;
    if(year==0 || month==0 || day==0){
      value = null;
    }else{
      value = ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zoneOffset);
    }
  }

  @Override
  void write(BufferedRandomAccessFile braf) throws IOException {
    int year = value.getYear();
    int month = value.getMonthValue();
    int day = value.getDayOfMonth();
    String s = String.format("%4d%02d%02d", year, month, day);
    byte[] b = s.getBytes();
    braf.write(b, 0, 8);
  }

  /**
   * Gets the value stored in the field during the most recent read as a
   * floating point value giving seconds since the Epoch 1970.
   *
   * @return a valid double or a NaN if the file content was invalid
   */
  @Override
  public double getDouble() {
    if (value == null) {
      return Double.NaN;
    }
    return value.toEpochSecond() * 1000.0;
  }

  /**
   * Gets an integer value indicating whether the data field has been correctly
   * read.
   * <p>
   * Although it would be tempting to make this field return an conventional
   * integer representation of seconds since Epoch 1970, a 32 bit integer value
   * will overflow and become undefined on 03:14:07 UTC on 19 January 2038. If
   * you require an integer time value, please use the getLong() method.
   *
   * @return a value of -1 if the field is uninitialized or zero if it is
   * initialized.
   */
  @Override
  public int getInteger() {
    if (value == null) {
      return 0;
    }
    return (int) value.toEpochSecond();
  }

  /**
   * Gets the date value as an long integer time value consistent with the Java
   * convention using Epoch 1970.
   *
   * @return an integer value; zero if undefined.
   */
  public long getLong() {
    if (value == null) {
      return 0;
    }
    return value.toEpochSecond() * 1000L;
  }

  @Override
  public Object getApplicationData() {
    return value;
  }

  /**
   * Gets a ZonedDateTime instance representing the value extracted from the
   * file.
   *
   * @return a valid local date time.
   */
  public ZonedDateTime getValue() {
    return value;
  }

  public void setValue(ZonedDateTime dateTime){
     this.value = dateTime;
  }



  /**
   * Gets an array of unique values for this field.
   *
   * @param dbf a valid instance
   * @return if successful an array of zero or more elements.
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public ZonedDateTime[] getUniqueValueArray(DbfFileReader dbf) throws IOException {
    long vMin = Long.MAX_VALUE;
    long vMax = Long.MIN_VALUE;
    int nRecords = dbf.getRecordCount();

    if (nRecords == 0) {
      return new ZonedDateTime[0];
    }

    long[] vArray = new long[nRecords];
    int k = 0;
    for (int i = 1; i <= nRecords; i++) {
      dbf.readField(i, this);
      if(isNull()){
        continue;
      }
      long v = getLong();
      if (v < vMin) {
        vMin = v;
      }
      if (v > vMax) {
        vMax = v;
      }
      vArray[k++] = v;
    }

    Arrays.sort(vArray, 0, k);
    int nUniqueValues = 1;
    long prior = vArray[0];
    for (int i = 1; i < k; i++) {
      if (vArray[i] != prior) {
        prior = vArray[i];
        vArray[nUniqueValues] = vArray[i];
        nUniqueValues++;
      }
    }

    ZonedDateTime[] ldtArray = new ZonedDateTime[nUniqueValues];
    for (int i = 0; i < nUniqueValues; i++) {
      Instant instant = Instant.ofEpochMilli(vArray[i]);
      ldtArray[i] = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    return ldtArray;
  }

  /**
   * Sets the zone offset to be used when reading data
   *
   * @param zoneOffset a valid instance
   */
  public void setZoneOffset(ZoneOffset zoneOffset) {
    if (zoneOffset == null) {
      throw new NullPointerException("Null zone-offset value not supported");
    }
    this.zoneOffset = zoneOffset;
  }

  @Override
  public boolean isNull(){
    return value==null;
  }
}
