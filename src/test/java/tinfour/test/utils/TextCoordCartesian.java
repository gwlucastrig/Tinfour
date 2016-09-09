/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 09/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.text.ParseException;

/**
 * Provides a utility for parsing or formatting a pair of Cartesian coordinates.
 */
public class TextCoordCartesian {

  private static final double LN10 = Math.log(10.0);

  private int iLog10(double test) {
    if (test == 0 || Double.isNaN(test)) {
      return 0;
    } else if (Double.isInfinite(test)) {
      return 306; // max double is on order 1.798e+308, leave some room
    }
    return (int) Math.floor(Math.log(Math.abs(test)) / LN10 + 1.0e-9);
  }

  /**
   * Parses a string containing a Cartesian coordinate pair,
   * returning an array of dimension two giving the x and y coordinates.
   *
   * @param text a valid string
   * @return a valid array of dimension 2.
   * @throws java.text.ParseException in response to invalid input
   */
  public double[] parse(String text) throws ParseException {
    StringBuilder numStr = new StringBuilder(32);
    if (text == null) {
      throw new ParseException("Null input", 0);
    }
    int i0 = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!Character.isWhitespace(c)) {
        i0 = i;
        break;
      }
    }
    if (i0 == -1) {
      throw new ParseException("Empty input", 0);
    }

    int nV = 0;
    double[] v = new double[2];
    for (int i = i0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        if (numStr.length() > 0) {
          try {
            double test = Double.parseDouble(numStr.toString());
            if (nV == 2) {
              throw new ParseException("Too many values", i);
            }
            v[nV++] = test;
          } catch (NumberFormatException nex) {
            throw new ParseException("Invalid numeric " + numStr.toString(), i);
          }
        }
      }
    }
    if (nV < 2) {
      throw new ParseException("Incomplete specification", text.length());
    }
    return v;
  }

  /**
   * Formats the x,y coordinates into a form that is suitable
   * for presentation in a user interface.
   *
   * @param x a valid floating point value
   * @param y a valid floating point value
   * @return a formatted string
   */
  public String format(double x, double y) {
    // in general, we are content to let Java pick to formatting
    // for us.  But for Lidar, we have cases where we have
    // largish numbers (greater than 9 million) and still
    // want 3 digits of decimal precision.  Also, sometimes Java
    // will get carried away with precision when it sees values
    // like 4.500000001 or 3.99999999

    String sx = formatV(x);
    String sy = formatV(y);
    return sx+", "+sy;
  }

  private String formatV(double v) {
    int test = iLog10(v);
    if (test > 5) {
      return String.format("%5.3f", v);
    } else if (test >= 1) {
      return String.format("%6.4f", v);
    } else if (test > -3) {
      return String.format("%8.6f", v);
    } else {
      // we're content to use Java's rules
      return Double.toString(v);
    }
  }

//  public static void main(String[] args) {
//    ftest(12345678.9101, 1234567.89);
//    ftest(1.9101, 2.89);
//
//  }
//
//  private static void ftest(double a, double b) {
//    TextCoordCartesian u = new TextCoordCartesian();
//    double[] d = new double[2];
//    d[0] = a;
//    d[1] = b;
//    String s = u.format(d);
//    System.out.println("test " + a + ", " + b + ":   \"" + s + "\"");
//
//  }
}
