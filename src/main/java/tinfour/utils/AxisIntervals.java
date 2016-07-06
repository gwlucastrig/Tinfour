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
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 */
package tinfour.utils;

import java.util.Arrays;

/**
 * Provides a utility for computing the intervals for labeling
 * a coordinate axis in a legend or other graph.
 */
@SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
public final class AxisIntervals {

  private static final double LN10 = Math.log(10.0);

  /**
   * Arbitrarily chosen divisions based on the ordinal value of the
   * range. Array index [i][0] is the candidate primary division,
   * index[i][1]..index[i][n] are the candidates for the the secondary.
   * It is essential that the secondary  division evenly divides the primary.
   */
  private static final double[][] testIntervals = {
    {1, 0.5, 0.1}, {2, 1.0}, {5, 1.0}
  };

  final double value0;
  final double value1;
  final double[][] cTics;
  final double[] cLabels;
  final double unitsPerPixel;
  final double interval;
  final double subInterval;
  final int intervalMagnitude;
  final String labelFormat;
  final boolean isValue0Labeled;
  final boolean isValue1Labeled;

  private AxisIntervals(
    double value0,
    double value1,
    double cTics[][],
    double cLabels[],
    double unitsPerPixel,
    double interval,
    double subInterval,
    int intervalMagnitude,
    String labelFormat,
    boolean isValue0Labeled,
    boolean isValue1Labeled) {

    this.value0 = value0;
    this.value1 = value1;
    this.cTics = Arrays.copyOf(cTics, cTics.length);
    this.cLabels = Arrays.copyOf(cLabels, cLabels.length);
    this.unitsPerPixel = unitsPerPixel;
    this.interval = interval;
    this.subInterval = subInterval;
    this.intervalMagnitude = intervalMagnitude;
    this.labelFormat = labelFormat;
    this.isValue0Labeled = isValue0Labeled;
    this.isValue1Labeled = isValue1Labeled;
  }

    /**
   * Gets the minimum value of the range used to specify the axis.
   * @return a valid floating point value
   */
  public double getValue0() {
    return value0;
  }


  /**
   * Gets the maximum value of the range used to specify the axis.
   * @return a valid floating point value
   */
  public double getValue1() {
    return value1;
  }

  /**
   * Indicates if value0 was included in the primary tic coordinates.
   * This happens when value0 is an integral multiple of the
   * primary interval.
   * @return true if value0 is included in the tic coordinates;
   * otherwise false
   */
 public boolean isValue0Labeled() {
    return isValue0Labeled;
  }

   /**
   * Indicates if value1 was included in the primary tic coordinates.
   * This happens when value1 is an integral multiple of the
   * primary interval.
   * @return true if value1 is included in the tic coordinates;
   * otherwise false
   */
  public boolean isValue1Labeled() {
    return isValue1Labeled;
  }


/**
 * Gets the coordinates for the tic marks to be added to the axis.
 * The array turned may be of dimension one or two. If it is of
 * dimension 1, only primary tic intervals are included. If it is
 * of dimension 2, then secondary tic intervals are included.
 * Note that the coordinates for the secondary tic marks do not
 * overlap those of the primary intervals.
 * @return a valid array of dimension one or two.
 */
  public double[][] getTicCoordinates() {
    double[][] d = new double[cTics.length][];
    for (int i = 0; i < cTics.length; i++) {
      d[i] = Arrays.copyOf(cTics[i], cTics[i].length);
    }
    return d;
  }

  /**
   * Gets the coordinates for labels.  This value is redundant
   * with the primary tic coordinates/
   * @return a valid array
   */
  public double[] getLabelCoordinates() {
    return Arrays.copyOf(cLabels, cLabels.length);
  }

  /**
   * Gets formatted labels for the tic coordinates.
   * @return a valid array
   */
  public String[] getLabels() {
    String[] a = new String[cLabels.length];
    for (int i = 0; i < a.length; i++) {
      a[i] = String.format(labelFormat, cLabels[i]);
    }
    return a;
  }

  /**
   * Get a label format that is intended to be appropriate to
   * the magnitude of the primary interval
   * @return a valid Java format string
   */
  public String getLabelFormat() {
    return labelFormat;
  }

  /**
   * Get a multiplicative factor for scaling coordinates to pixels.
   * @return a valid floating point value
   */
  public double getCoordinateScale() {
    return 1 / unitsPerPixel;
  }


  private static double pow10(int i10) {
    double m10 = Math.exp(i10 * LN10);
    if (i10 > 0) {
      // for powers of 10 greater than equal to zero, m10 may have a very
      // small fractional part. truncate it. also handle the case
      // where m10 may be just shy of what its supposed to be.
      m10 = Math.floor(m10 + 1.0e-9);
    }
    return m10;
  }

  /**
   * Compute the intervals over a range of values. This method
   * attempts to select the the finest real-valued intervals
   * that map to a pixel scale larger than the specified limits
   * and confirm to nice "human friendly" values.
   *
   * @param value0 the minimum value to be represented.
   * @param value1 the maximum value to be represented.
   * @param primaryMinIntervalInPixels the minimum spacing allowed for the
   * primary interval
   * @param secondaryMinIntervalInPixels the minimum space allowed for the
   * secondary interval
   * @param sizeInPixels the overall size of the area for labeling,
   * i&#46;.e$#46; the length of a horizontal axis or the height of
   * a vertical axis
   * @return if successful, a valid instance; otherwise, a null.
   */
  public static AxisIntervals computeIntervals(
    double value0,
    double value1,
    int primaryMinIntervalInPixels,
    int secondaryMinIntervalInPixels,
    int sizeInPixels)
  {
    double delta = Math.abs(value1 - value0);
    double uPerPixel = delta/sizeInPixels;
    double log10 = Math.log(delta) / LN10;  // log base 10
    int i10 = (int) Math.floor(log10 + 1.0e-9);

    double firstScore = Double.POSITIVE_INFINITY;
    double firstInterval = Double.NaN;
    double bestM10 = Double.NaN;
    int firstPowerOf10 = 0;
    int firstIndex = -1;
    for (int iPow = i10 - 1; iPow <= i10; iPow++) {
      double m10 = pow10(iPow);
      for (int iTest = 0; iTest < testIntervals.length; iTest++) {
        double vTest = testIntervals[iTest][0] * m10;
        double pTest = vTest / uPerPixel;
        double pScore = pTest - primaryMinIntervalInPixels;
        if (pScore >= 0 && pScore < firstScore) {
          bestM10 = m10;
          firstPowerOf10 = iPow;
          firstInterval = vTest;
          firstIndex = iTest;
          firstScore = pScore;
        }
      }
    }

    if (firstIndex < 0) {
      return null;
    }

    int secondIndex = -1;
    double secondInterval = Double.NaN;
    double secondScore = Double.POSITIVE_INFINITY;
    for (int i = 1; i < testIntervals[firstIndex].length; i++) {
      double vTest = testIntervals[firstIndex][i] * bestM10;
      double pTest = vTest / uPerPixel;
      double pScore = pTest - secondaryMinIntervalInPixels;
      if (pScore >= 0 && pScore < secondScore) {
        secondInterval = vTest;
        secondIndex = i;
        secondScore = pScore;
      }
    }

    int nDiv = 1;
    if (secondIndex > 0) {
      nDiv = 2;
    }

    // if value0 and value1 are of a very large magnitude, then an integer
    // conversion might overflow (not likely but it could happen)
    // so we bring them down using an offset
    double vOffset = Math.floor(value0 / firstInterval) * firstInterval;
    double v0 = value0 - vOffset;
    double v1 = value1 - vOffset;
    int i0 = (int) Math.ceil(v0 / firstInterval - 1.0e-5);
    if (i0 * firstInterval < v0 - firstInterval / 2) {
      i0++; // the -1.0e-5 took it down too far
    }
    int i1 = (int) Math.floor(v1 / firstInterval + 1.0e-5);
    if (i1 * firstInterval > v1 + firstInterval / 2) {
      i1--;
    }
    double f0 = i0 * firstInterval;
    double f1 = i1 * firstInterval;
    boolean isValue0Labeled = Math.abs(f0 - v0) / uPerPixel < 1;
    boolean isValue1Labeled = Math.abs(f1 - v1) / uPerPixel < 1;

    int nTics = i1 - i0 + 1;

    double[][] xTic = new double[nDiv][];
    xTic[0] = new double[nTics];
    double[] xLabel = new double[nTics]; // redundant
    for (int i = 0; i < nTics; i++) {
      double v = vOffset + (i + i0) * firstInterval;
      xTic[0][i] = v;
      xLabel[i] = v;
    }
    if (nDiv == 2) {
      int m = (int) Math.floor(firstInterval / secondInterval + 1.0e-5);
      xTic[1] = new double[nTics * m * 2];
      i0 = (int) Math.floor(v0 / secondInterval);
      i1 = (int) Math.ceil(v1 / secondInterval);
      int k = 0;
      for (int i = i0 + 1; i <= i1; i++) {
        if ((i % m) != 0) {
          double v = i * secondInterval;
          if (v > v0 && v < v1) {
            xTic[1][k++] = v+vOffset;
          }
        }
      }
      xTic[1] = Arrays.copyOf(xTic[1], k);
    }

    String labelFmt = "%f";
    if(firstPowerOf10>5){
      labelFmt = "%6.3e";
    }else  if (firstPowerOf10 >= 0) {
      // all divisions of the larger numbers are integral
      int nDigits = firstPowerOf10+1;
      if(xTic[0][0]<0){
        nDigits++; // room for minus sing
      }
      labelFmt = "%"+nDigits+".0f";
    } else if (firstPowerOf10 >= -3) {
      int nDecimals = -firstPowerOf10;
      labelFmt = "%" + (nDecimals + 2) + "." + nDecimals + "f";
    } else{
       labelFmt = "%6.3e";
    }

    //String fmt = "  %2d:   " + labelFmt + "\n";
    //for (int i = 0; i < xTic.length; i++) {
    //  System.out.println("TICS: " + xTic[i].length);
    //  for (int j = 0; j < xTic[i].length; j++) {
    //    System.out.format(fmt, j, xTic[i][j]);
    //  }
    //  fmt = "  %2d:   %f\n";
    //}

    return new AxisIntervals(
      value0,
      value1,
      xTic,
      xLabel,
      uPerPixel,
      firstInterval,
      secondInterval,
      firstPowerOf10,
      labelFmt,
      isValue0Labeled,
      isValue1Labeled);
  }


  /**
   * Maps a value to a corresponding pixel coordinate.
   * Value0 maps to zero. Value1 maps to (value1-value0)/unitsPerPixel.
   * @param value the value to be mapped.
   * @return a valid floating point value
   */
  public double mapValueToPixel(double value){
    return (value-value0)/unitsPerPixel;
  }

  /**
   * Gets the integer power of ten corresponding to the
   * magnitude of the primary interval.  For example,
   * if the primary interval were 0.2, the interval
   * magnitude would be -1.
   * @return an integer
   */
  public int getIntervalMagnitude(){
    return intervalMagnitude;
  }

  //public static void main(String[] args) {
  //  LegendIntervals s = computeIntervals(0.4, 9, 10, 1, 0.1);
  //}

}
