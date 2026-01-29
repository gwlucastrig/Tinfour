/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2026  Gary W. Lucas.
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
 * 01/2026  G. Lucas     Created
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

/**
 * Provides information for a single bin (count) from a histogram.
 */
public class BasicHistogramCount {

  /**
   * the index of the counting bin (starting from zero).
   */
  public final int index;
  /**
   * the lower bounds for the range of values represented by the count.
   */
  public final double x0;
  /**
   * the upper bounds for the range of values represented by the count.
   */
  public final double x1;
  /**
   * the middle value for the range of values represented by the count.
   */
  public final double x;
  /**
   * a smoothed value for the count.
   */
  public final double y;
  /**
   * the number of samples that fell within the range of values.
   */
  public final int count;

  /**
   * Construct a bin to store the results from a histogram tabulation.
   *
   * @param index the index of the bin (counting from zero)
   * @param x0 the lower bounds for the bin
   * @param x1 the upper bounds for the bin
   * @param x the middle value for the bin
   * @param y a smoothed value for the count
   * @param count the count for the bin
   */
  public BasicHistogramCount(int index, double x0, double x1, double x, double y, int count) {
    this.index = index;
    this.x0 = x0;
    this.x1 = x1;
    this.x = x;
    this.y = y;
    this.count = count;
  }

}
