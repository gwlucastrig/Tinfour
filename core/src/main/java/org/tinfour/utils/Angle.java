/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2019  G. Lucas     Created
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

/**
 * Simple, robust conversions for constraining the value of angles to specified
 * ranges.
 */
public class Angle {

  private Angle(){
    // a private constructor to deter application code
    // from making direct instances of this class.
  }

  /**
   * Computes an equivalent value for the angle in the range 180 &le; value &lt;
   * 180
   *
   * @param angle a valid floating point value
   * @return a value in the range from 180 &le; value &lt; 180
   */
  public static double to180(double angle) {
    double a = angle % 360;
    if (a == 0) {
      return 0; // avoid -0.000000
    } else if (a < -180) {
      return 360 + a;
    } else if (a >= 180) {
      return a - 360;
    }
    return a;
  }

  /**
   * Computes an equivalent value for the angle in the range 0 &le; value &lt;
   * 360
   *
   * @param angle a valid floating point value
   * @return a value in the range from 0 &le; value &lt; 360
   */
  public static double to360(double angle) {
    double a = angle % 360;
    if (a < 0) {
      return a + 360;
    } else if (a == 0) {
      return 0;  // avoid -0.0000
    } else {
      return a;
    }
  }

}
