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
 * Extends DbfField with special handling for reading numeric values.
 */
class DbfFieldDouble extends DbfField {

  private final static double LOG10 = Math.log(10.0);
  private double value;

  DbfFieldDouble(
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
        return;
      }
    }
    
    if(i==fieldLength){
      value = Double.NaN;
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
    boolean engineeringNotation = false;
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
   * Gets the equivalent integer value of the field.
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

}
