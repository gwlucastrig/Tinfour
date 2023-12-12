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
import java.util.Arrays;
import org.tinfour.io.BufferedRandomAccessFile;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Extends DbfField with special handling for reading numeric values.
 */
public class DbfFieldDouble extends DbfField {

  private final static double LOG10 = Math.log(10.0);
  private double value;
  private boolean engineeringNotation;

  private String writingFormat;

  DbfFieldDouble(
          String name,
          char fieldType,
          int dataAddress,
          int fieldLength,
          int fieldDecimalCount,
          int offset,
          boolean useEngineeringNotation) {
    super(name, fieldType, dataAddress, fieldLength, fieldDecimalCount, offset);
    engineeringNotation = useEngineeringNotation;
    if (useEngineeringNotation) {
      writingFormat = String.format("%%%d.%de", fieldLength, fieldLength-7);
    } else {
      writingFormat = String.format("%%%d.%df", fieldLength, fieldDecimalCount);
    }
  }

  @Override
  void read(BufferedRandomAccessReader brad, long recordFilePos) throws IOException {
    brad.seek(recordFilePos + offset);
    builder.setLength(0);
    engineeringNotation = false;

    int i = 0;
    double sign = 1;
    long s = 0; // should these be longs?
    long f = 0;
    long d = 1;
    // find first non-space character
    while (i < fieldLength) {
      int b = brad.readUnsignedByte();
      builder.append((char)b);
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
        break;
      }else if(b=='.' || b==','){
        // assume this is a leading decimal point and defer processing
        break;
      } else {
        // a non-whitespace character.  at this time,
        // the meaning of this is unknown.
        value = Double.NaN;
        builder.setLength(0);
        return;
      }
    }

    if(i==fieldLength){
      value = Double.NaN;
      builder.setLength(0);
      return;
    }

    // process the non-fractional part
    while (i < fieldLength) {
      int b = brad.readUnsignedByte();
      builder.append((char)b);
      if (b == '.' || b == ',') {
        // transition to fractiional part
        i++;
        break;
      }
      s = s * 10 + (b - 48);
      i++;
    }

    // process the fractional part
    engineeringNotation = false;
    while (i < fieldLength) {
      int b = brad.readUnsignedByte();
      builder.append((char)b);
      if (b == 32) {
        break;
      }else if(b=='e' || b=='E'){
        engineeringNotation = true;
        break;
      }
      d = d * 10;
      f = f * 10 + (b - 48);
      i++;
    }

    value = sign * ((double) s + (double) f / (double) d);

    if(engineeringNotation){
      if(i>fieldLength-3){
        value = Double.NaN;
      }else{
        s = 0;
        i++;
        int b = brad.readUnsignedByte();
        builder.append((char)b);
        if(b=='-'){
          sign = -1;
        }else{
          sign = 1;
        }
        i++;
        d = 0;
        while(i<fieldLength){
            b = brad.readUnsignedByte();
            builder.append((char)b);
           if(b==32){
             break;
           }
           d = d*10+(b-48);
           i++;
        }
        if(d!=0){
          double e = sign*d;
          value = value*Math.exp(LOG10*e);
        }
      }
    }

  }

  @Override
  void write(BufferedRandomAccessFile braf) throws IOException {

    String s = String.format(writingFormat, value);
    byte[] b = s.getBytes();
    if(b.length>fieldLength){
      throw new IOException("Formatted output exceeds fieldLength of "
        +fieldLength+": \""+s+"\"");
    }
    braf.write(b, 0, fieldLength);
  }

     /**
   * Gets the double value stored in the field
   * during the most recent read operation, if any
   * @return a valid double or a NaN if the file content was invalid
   */
  @Override
  public double getDouble(){
    return value;
  }

  /**
   * Sets a value for the field.  Intended for writing files.
   * @param value a valid floating point value (finite, not NaN).
   */
  public void setDouble(double value){
    this.value = value;
  }

    /**
   * Sets a value for the field.  Intended for writing files.
   * @param value a valid integer value.
   */
  public void setInteger(int value){
    this.value = value;
  }

  /**
   * Indicates whether the value in the field was encoded
   * using engineering notation (e.g. scientific notation)
   * @return true if engineering notation was used; otherwise false.
   */
  @Override
  public boolean usesEngineeringNotation(){
    return engineeringNotation;
  }

  /**
   * Gets the equivalent integer value of the field.
   *
   * @return a valid integral value, or a zero if undefined.
   */
  @Override
  public int getInteger(){
    if(Double.isNaN(value)){
      return 0;
    }
    if(value<Integer.MIN_VALUE){
      return Integer.MIN_VALUE;
    }else if(value>Integer.MAX_VALUE){
      return Integer.MAX_VALUE;
    }
    return (int)value;

  }


  @Override
  public Object getApplicationData(){
    return value;
  }


  /**
   * Gets an array of unique values for this field.
   * @param dbf a valid instance
   * @return if successful an array of zero or more elements.
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public double [] getUniqueValueArray(DbfFileReader dbf) throws IOException {
     Double vMin = Double.POSITIVE_INFINITY;
    Double vMax = Double.NEGATIVE_INFINITY;
    int nRecords = dbf.getRecordCount();
    double[] vArray = new double[nRecords];
    if (nRecords == 0) {
      return new double[0];
    }


    int k = 0;
    for (int i = 1; i <= nRecords; i++) {
      dbf.readField(i, this);
      double v = getDouble();
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
    double prior = vArray[0];
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
