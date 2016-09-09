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
 * 08/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.io.PrintStream;

/**
 * Tabulates mean and variance for "delta" values obtained from
 * tests in which the result is found by comparing an estimated value
 * (such as an interpolation result) with a known or expected value.
 * The tabulator collects sums using both the signed values of the deltas as
 * well as the absolute values of the deltas. The Kahan Summation algorithm
 * is used to provide extended precision when processing a very large
 * number of samples.
 */
public class TabulatorDelta {

  double sumD; // sum of abs(delta)
  double sumD2; // sum of delta^2
  double sumSignedD;
  double maxD = Double.NEGATIVE_INFINITY;  // max signed delta
  double minD = Double.POSITIVE_INFINITY;  // nim signed delta

  double cD; // compensator for Kahan Summation, sum delta
  double cD2;// compensator, sum delta squared

  int nD; // number of tabulated deltas
  int nNaN; // number of NaN's

  /**
   * Adds the specified delta value to the running tally of observed value.
   *
   * @param delta a valid floating point value, NaN's are ignored.
   */
  public void tabulate(double delta) {
    double d2 = delta * delta;
    double dAbs = Math.abs(delta);
    if (Double.isNaN(delta)) {
      nNaN++;
    } else {
      double y, t;
      nD++;
      sumSignedD += delta;

      // to avoid numeric issues, apply Kahan summation algorithm.
      y = dAbs - cD;
      t = sumD + y;
      cD = (t - sumD) - y;
      sumD = t;

      y = d2 - cD2;
      t = sumD2 + y;
      cD2 = (t - sumD2) - y;
      sumD2 = t;

      if (delta > maxD) {
        maxD = delta;
      }
      if (delta < minD) {
        minD = delta;
      }
    }
  }

  /**
   * Print a summary of the mean, standard deviation, min, max, and
   * sum of signed errors.
   *
   * @param ps the print stream to which output is to be streamed
   * @param label a label for the beginning of the output line
   */
  public void summarize(PrintStream ps, String label) {
    double meanE = 0;
    double sigma = 0;
    if (nD > 1) {
      meanE = sumD / nD;
      // to reduce errors due to loss of precision,
      // rather than using the conventional form for std dev
      // nE*sumE2-sumE*sumE)/((nE*(nE-1))
      // use the form below
      sigma = Math.sqrt((sumD2 - (sumD / nD) * sumD) / (nD - 1));
    }
    ps.format("%s %13.6f %13.6f %10.3f %8.3f %9.3f\n",
      label, meanE, sigma, minD, maxD, sumSignedD);
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
    return sumD / nD;
  }

  /**
   * Get an unbiased estimate of the standard deviation of the population
   * based on the tabulated samples.
   *
   * @return the standard deviation of the absolute values of the inputs.
   */
  public double getStdDevAbsValue() {
    if (nD < 1) {
      return 0;
    }

    // to reduce errors due to loss of precision,
    // rather than using the conventional form for std dev
    // nE*sumE2-sumE*sumE)/((nE*(nE-1))
    // use the form below
    return Math.sqrt((sumD2 - (sumD / nD) * sumD) / (nD - 1));
  }

  /**
   * Get the signed minimum value of the input samples
   *
   * @return a valid floating point number
   */
  public double getMinValue() {
    return minD;
  }

  /**
   * Get the signed maximum value of the input samples
   *
   * @return a valid floating point number
   */
  public double getMaxValue() {
    return maxD;
  }

  /**
   * Gets the sum of the signed sample values as input into this tabulator.
   * In practice, a sum with a large positive or negative value would
   * mean that the process used to estimate values consistently
   * overshot or undershot the actual value.
   *
   * @return the sum of the signed values.
   */
  public double getSumSignedValues() {
    return this.sumSignedD;
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

}
