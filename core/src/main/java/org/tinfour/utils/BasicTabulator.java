/* --------------------------------------------------------------------
 * Copyright 2016-2024 Gary W. Lucas.
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
 * 08/2016  G. Lucas     Created TabulatorDelta
 * 03/2024  G. Lucas     Refactored
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.io.PrintStream;

/**
 * Provides methods and elements for tabulating simple statistics
 * for a set of values.  This class is suitable for tracking either
 * observations or residuals.  It automatically tracks sums and ranges of values
 * for both signed inputs and the absolute value of the inputs.  Internally,
 * this class uses the Kahan summation algorithm to provide extended
 * precision when processing a very large number of samples.
 */
public class BasicTabulator {

  final KahanSummation sumV = new KahanSummation(); // sum of values
  final KahanSummation sumV2 = new KahanSummation(); // sum of value^2
  final KahanSummation sumAbsV = new KahanSummation();

  double maxV = Double.NEGATIVE_INFINITY;  // max signed value
  double minV = Double.POSITIVE_INFINITY;  // min signed value
  double maxAbsV = Double.NEGATIVE_INFINITY;  // max absolute value
  double minAbsV = Double.POSITIVE_INFINITY;  // min absolute value

  int nD; // number of tabulated values
  int nNaN; // number of NaN's or infinite inputs

  /**
   * Adds the specified value to the running tallies.  Internally,
   * both the signed and absolute value equivalents of the input are tracked.
   * Infinite values and Double.NaN are not added to the tally.
   *
   * @param value a valid floating point value, non-finite and NaN's are
   * ignored.
   */
  public void tabulate(double value) {
    if (Double.isFinite(value)) {
      double d2 = value * value;
      double dAbs = Math.abs(value);
      nD++;
      sumV.add(value);
      sumV2.add(d2);
      sumAbsV.add(dAbs);
      if (value < minV) {
        minV = value;
      }
      if (value > maxV) {
        maxV = value;
      }
      if (dAbs < minAbsV) {
        minAbsV = dAbs;
      }
      if (dAbs > maxAbsV) {
        maxAbsV = dAbs;
      }
    } else {
      nNaN++;
    }
  }


  /**
   * Get the mean of the absolute values of the input sample values.
   *
   * @return a valid floating point number, zero if no input has occurred.
   */
  public double getMeanAbsValue() {
    if (nD == 0) {
      return 0;
    }
    return sumAbsV.getMean();
  }

  /**
   * Get the mean of the signed values of the input sample values.
   *
   * @return a valid floating point number, zero if no input has occurred.
   */
  public double getMean() {
    if (nD == 0) {
      return 0;
    }
    return sumV.getMean();
  }

  /**
   * Get an unbiased estimate of the standard deviation of the population
   * based on the tabulated samples.
   *
   * @return the standard deviation of the absolute values of the inputs,
   * or zero if insufficient data is available.
   */
  public double getStdDevAbsValue() {
    if (nD < 2) {
      return 0;
    }

    // to reduce errors due to loss of precision,
    // rather than using the conventional form for std dev
    // nE*sumE2-sumE*sumE)/((nE*(nE-1))
    // this method uses the form below
    double sD2 = sumV2.getSum();
    double sD = sumAbsV.getSum();
    return Math.sqrt((sD2 - (sD / nD) * sD) / (nD - 1));
  }

  /**
   * Get an unbiased estimate of the standard deviation of the population
   * based on the tabulated values (signed values).
   *
   * @return the standard deviation of the signed values of the inputs,
   * or zero if insufficient data is available.
   */
  public double getStdDev() {
    if (nD < 2) {
      return 0;
    }

    // to reduce errors due to loss of precision,
    // rather than using the conventional form for std dev
    // nE*sumE2-sumE*sumE)/((nE*(nE-1))
    // this method uses the form below
    double sD2 = sumV2.getSum();
    double sD = sumV.getSum();
    return Math.sqrt((sD2 - (sD / nD) * sD) / (nD - 1));
  }

  /**
   * Get the minimum of the signed input values
   *
   * @return a valid floating point number
   */
  public double getMinValue() {
    return minV;
  }

  /**
   * Get the maximum value of the signed input samples
   *
   * @return a valid floating point number
   */
  public double getMaxValue() {
    return maxV;
  }

  /**
   * Get the minimum of the signed input values
   *
   * @return a valid floating point number
   */
  public double getMinAbsValue() {
    return minAbsV;
  }

  /**
   * Get the maximum value of the signed input samples
   *
   * @return a valid floating point number
   */
  public double getMaxAbsValue() {
    return maxAbsV;
  }

  /**
   * Gets the number of sample values passed into this instance of the
   * tabulator.
   *
   * @return a positive, potentially zero value.
   */
  public int getNumberSamples() {
    return nD;
  }

  /**
   * Gets the number of non-finite sample values passed into this instance of the
   * tabulator.
   *
   * @return a positive, potentially zero value.
   */
  public int getNumberNonFiniteSamples() {
    return nNaN;
  }

  public static void printCaption(PrintStream ps){
    ps.println("                                 mean          sigma      min       max"
    + "       mean (abs)    sigma (abs)    min       max");
  }

  /**
   * Prints a summary of the statistics computed from signed inputs.
   * @param ps a valid print stream
   * @param label a label of 25 characters or fewer to be printed in the summary
   */
  public void summarize(PrintStream ps, String label ){
    String s;
    if(label==null || label.isEmpty()){
      s = "";
    }else if(label.length()>25){
      s = label.substring(0, 25);
    }else{
      s = label;
    }

    double mean = getMean();
    double sigma = getStdDev();
    double absMean = getMeanAbsValue();
    double absSigma = getStdDevAbsValue();
    double absMin = getMinAbsValue();
    double absMax = getMaxAbsValue();
        ps.format("%-25.25s %13.6f %13.6f %9.3f %9.3f %13.6f %13.6f %9.3f %9.3f%n",
      s,
      mean, sigma, getMinValue(), getMaxValue(),
      absMean, absSigma, absMin, absMax
        );
  }
 /**
   * Prints a summary of the statistics computed from absolute values of the inputs.
   * @param ps a valid print stream
   * @param label a label of 25 characters or fewer to be printed in the summary
   */
  public void summarizeAbsValues(PrintStream ps, String label ){
    String s;
    if(label==null || label.isEmpty()){
      s = "";
    }else if(label.length()>25){
      s = label.substring(0, 25);
    }else{
      s = label;
    }

    double mean = this.getMeanAbsValue();
    double sigma = this.getStdDevAbsValue();
        ps.format("%-25.25s %13.6f %13.6f %9.3f %9.3f%n",
      s, mean, sigma, getMinAbsValue(), getMaxAbsValue());
  }

}
