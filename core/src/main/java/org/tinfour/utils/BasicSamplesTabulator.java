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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a utility for collecting simple statistics on a set of samples
 * when the size of the set is not necessarily known <i>a priori.</i>
 * Samples are recorded by the tabulator one at a time, as they become
 * available. Results include percentile scores, mean and standard deviation, and
 * counts for building a histogram.
 * <p>
 * <strong>Note:</strong> Instances of this class keep an in-memory copy of all
 * samples that are recorded.  Application developers should consider this
 * behavior when estimating the memory use for this utility.
 */
public class BasicSamplesTabulator {

  private static final int BLOCK_SIZE = 2048;

  private static class StatBlock {

    int n;
    final double[] samples;

    StatBlock() {
      samples = new double[BLOCK_SIZE];
    }

    StatBlock(int n, double[] samples) {
      this.n = n;
      this.samples = samples;
    }
  }

  private StatBlock[] blocks = new StatBlock[]{new StatBlock()};
  KahanSummation asum = new KahanSummation();
  KahanSummation asum2 = new KahanSummation();
  double minValue = Double.POSITIVE_INFINITY;
  double maxValue = Double.NEGATIVE_INFINITY;

  private int nSamples;
  boolean ready = false;

  /**
   * Record a sample value for addition to the tabulator.
   * Samples that are not finite, or not a valid floating-point value,
   * will be ignored.
   * @param sample a valid, finite floating-point value.
   * @return true if a sample was accepted to by the tabulator;
   * otherwise false.
   */
  public boolean recordSample(double sample) {
    if (Double.isFinite(sample)) {
      if (sample < minValue) {
        minValue = sample;
      }
      if (sample > maxValue) {
        maxValue = sample;
      }
      asum.add(sample);
      asum2.add(sample * sample);

      StatBlock bin = blocks[blocks.length - 1];
      if (bin.n == bin.samples.length) {
        bin = new StatBlock();
        blocks = Arrays.copyOf(blocks, blocks.length + 1);
        blocks[blocks.length - 1] = bin;
      }
      bin.samples[bin.n++] = sample;
      nSamples++;
      ready = false;
      return true;
    }
    return false;
  }

  private boolean makeReady() {
    if (ready) {
      return true;
    }
    if (nSamples > 0) {
      if (blocks.length == 1) {
        // there is only one populated bin, we don't need to reallocate
        // anything, just sort and process.
        Arrays.sort(blocks[0].samples, 0, nSamples);
        ready = true;
        return true;
      }

      double[] samples = new double[nSamples];
      int k = 0;
      for (int i = 0; i < blocks.length; i++) {
        System.arraycopy(blocks[i].samples, 0, samples, k, blocks[i].n);
        k += blocks[i].n;
        blocks[i] = null;
      }
      Arrays.sort(samples);
      blocks = new StatBlock[1];
      blocks[0] = new StatBlock(k, samples);
      ready = true;
      return true;
    }
    return false;
  }

  /**
   * Standard constructor
   */
  public BasicSamplesTabulator() {
    // no additional declarations required
  }

  /**
   * Gets the number of samples that have been recorded by the tabulator
   *
   * @return a positive integer value, potentially zero.
   */
  public int getSampleCount() {
    return nSamples;
  }

  /**
   * Get the minimum value for the samples that have been recorded by the
   * tabulator.
   *
   * @return a valid floating-point value or Double&#46;NaN if no samples have
   * been recorded.
   */
  public double getMinimum() {
    if (nSamples == 0) {
      return Double.NaN;
    }
    return minValue;
  }

  /**
   * Get the maximum value for the samples that have been recorded by the
   * tabulator.
   *
   * @return a valid floating-point value or Double&#46;NaN if no samples have
   * been recorded.
   */
  public double getMaximum() {
    if (nSamples == 0) {
      return Double.NaN;
    }
    return maxValue;
  }

  /**
   * Get the corresponding value for the specified percentile score.
   *
   * @param percentile a value in the range 0 &le; percentile &lt; 100.
   * @return a valid floating points value or Double&#46;NaN if insufficient
   * samples have been recorded or the percentile specification is out-of-range.
   */
  public double getValueForPercentileScore(double percentile) {
    if (!makeReady() || percentile < 0 || percentile >= 100.0) {
      return Double.NaN;
    }
    double[] samples = blocks[0].samples;
    double p0 = (percentile / 100.0) * nSamples;
    double t = p0 - Math.floor(p0);
    if (p0 >= nSamples - 1) {
      return samples[nSamples - 1];
    } else if (p0 <= 0) {
      return samples[0];
    }
    int i0 = (int) p0;
    double s0 = samples[i0];
    double s1 = samples[i0 + 1];
    return t * (s1 - s0) + s0;
  }

  /**
   * Gets the index into set of recorded samples corresponding to the
   * specified percentile.
   *
   * @param percentile a value in the range 0 &le; percentile &lt; 100.
   * @return a valid floating points value or Double&#46;NaN if insufficient
   * samples have been recorded or the percentile specification is out-of-range.
   */
  public double getFractionalIndex(double percentile) {
    if (nSamples == 0 || percentile < 0 || percentile >= 100.0) {
      return Double.NaN;
    }
    return (percentile / 100.0) * nSamples;
  }

  /**
   * Gets the mean value for the recorded samples.
   *
   * @return a valid floating points value or Double&#46;NaN if insufficient
   * samples have been recorded or the percentile specification is out-of-range.
   */
  public double getMean() {
    if (nSamples == 0) {
      return Double.NaN;
    }
    return asum.getMean();
  }

  /**
   * Gets the median value for the recorded samples.
   *
   * @return a valid floating points value or Double&#46;NaN if insufficient
   * samples have been recorded or the percentile specification is out-of-range.
   */
  public double getMedian() {
    if (makeReady()) {
      return blocks[0].samples[nSamples / 2];
    }
    return Double.NaN;
  }

  /**
   * Gets the one-sigma value (one standard deviation) for the sample set,
   * based on an unbiased estimator for the population statistics.
   *
   * @return a valid floating points value or Double&#46;NaN if insufficient
   * samples have been recorded or the percentile specification is out-of-range.
   */
  public double getSigma() {
    if (nSamples < 2) {
      return Double.NaN;
    }

    double sumD2 = asum2.getSum();
    double sumD = asum.getSum();
    int nD = asum.getSummandCount();
    double sigma2 = (sumD2 - (sumD / nD) * sumD) / (nD - 1);
    return Math.sqrt(sigma2);
  }

  /**
   * Surveys the input sample set to create a list of counts in support
   * of a histogram with the specified number of bins (intervals).
   *
   * @param nBins the number of bins (counts) for the histogram
   * @param xMin the minimum value for the histogram
   * @param xMax the maximum value for the histogram
   * @return an ordered list containing the specified number of counting bins.
   */
  public List<BasicHistogramCount> getHistogram(int nBins, double xMin, double xMax) {
    double xDelta = xMax - xMin;
    if (xDelta <= 0) {
      throw new IllegalArgumentException("Invalid range for histogram");
    }
    if (nBins < 2) {
      throw new IllegalArgumentException("Bin count too small: " + nBins);
    }
    double xBin = xDelta / nBins;
    return this.buildHistogram(xMin, xMax, xBin, nBins);

  }

  /**
   * Surveys the input sample set to create a list of counts in support
   * of a histogram with the specified number of bins (intervals).
   * The range of values for the results is based on the minimum and maximum
   * values of the sample set, but may be adjusted to offer "human friendly"
   * values. The number of entries in the list
   * should be close to, but not necessarily equal to, the specified target
   * number of bins.
   *
   * @param nBinsTarget the desired number of bins for the histogram
   * @return an ordered list of counts for a histogram.
   */
  public List<BasicHistogramCount> getHistogram(int nBinsTarget) {
    if (nSamples < 1) {
      throw new IllegalStateException("Not enough samples in tabulator");
    }

    if (nBinsTarget < 1) {
      throw new IllegalArgumentException("Bin count too small: " + nBinsTarget);
    }

    double xMin = minValue;
    double xMax = maxValue;
    double xDelta = xMax - xMin;
    if (xDelta == 0) {
      // All samples in the collection have the same value.
      // There's not much we can do here.
      return buildHistogram(xMin, xMax, 1.0, 1);
    }

    // generate axis intervals based on "nice looking" numeric values.
    // we want to make sure that the range of coverage includes the true
    // minimum and maximum sample values, so we set the "bracket values"
    // flag to true.  The AxisIntervals class was designed to work over "pixels",
    // but in this case we can just say "any old integer coordinate system".
    AxisIntervals ax = AxisIntervals.computeIntervals(xMin, xMax,
      1, 1,
      nBinsTarget,
      true);

    // because we configured the axis intervals to generate tics for the
    // min and maximum values, the number of tics will be one larger than
    // the number of counting bins (number of intervals).
    double xBin = ax.getInterval();
    double[][] tc = ax.getTicCoordinates();
    int nBins = tc[0].length - 1;
    xMin = tc[0][0];
    xMax = tc[0][nBins];  // a diagnostic

    return buildHistogram(xMin, xMax, xBin, nBins);
  }

  private List<BasicHistogramCount> buildHistogram(double xMin, double xMax, double xBin, int nBins) {
    int[] count = new int[nBins];
    for (int iBlock = 0; iBlock < blocks.length; iBlock++) {
      double[] samples = blocks[iBlock].samples;
      int n = blocks[iBlock].n;
      for (int i = 0; i < n; i++) {
        int index = (int) Math.floor((samples[i] - xMin) / xBin);
        if (0 <= index && index < nBins) {
          count[index]++;
        } else if ((samples[i] - xMax) < 1.0e-9) {
          // the xMax case
          count[nBins - 1]++;
        }
      }
    }

    double[] y = new double[nBins];
    y[0] = (count[0] + count[1]) / 2.0;
    y[nBins - 1] = (count[nBins - 1] + count[nBins - 2]) / 2.0;
    for (int i = 1; i < nBins - 1; i++) {
      y[i] = (count[i - 1] + count[i] + count[i + 1]) / 3.0;
    }

    List<BasicHistogramCount> histogramCounts = new ArrayList<>(nBins);
    for (int i = 0; i < nBins; i++) {
      double x0 = i * xBin + xMin;
      double x1 = x0 + xBin;
      BasicHistogramCount histogramCount = new BasicHistogramCount(i, x0, x1, (x0 + x1) / 2.0, y[i], count[i]);
      histogramCounts.add(histogramCount);
    }
    return histogramCounts;
  }

  /**
   * Gets an ordered array of the samples recorded by the tabulator
   * @return a valid, potentially zero-length array.
   */
  public double []getSamples(){
    if(nSamples==0){
      return new double[0];
    }
    makeReady();
    return Arrays.copyOf(blocks[0].samples, nSamples);
  }
}
