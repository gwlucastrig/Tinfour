/*
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
 */

/**
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 03/2016 G. Lucas Adapted from Regression Interpolator
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.gwr;

import java.util.Arrays;
import java.util.Random;
import org.apache.commons.math3.distribution.TDistribution;

/**
 * Provides methods and elements for performing interpolation over a
 * surface using linear regression methods to develop a polynomial
 * z = f(x, y) describing the surface in the region of the interpolation
 * coordinates.
 */
public class GwrInterpolator {

  SurfaceModel surfaceModel;
  SurfaceGwr regression;
  SurfaceGwr[] regressionArray;
  int minRequiredSamples;

  BandwidthSelectionMethod bandwidthMethod;
  double bandwidthParameter;
  double bandwidth;
  double[] beta;

  long nAdaptiveBandwidthTests;

  // the number of intervals for subdividing the bandwidth test
  // domain when automatically choosing bandwidth.
  // Chosen arbitrarily based on cross-validation testing and trading
  // off accuracy of predicted results and speed of calculation.
  private static final int nSubdivisionsOfBandwidthTestDomain = 6;

  private static final double[] adaptiveTestParameters;

  static {
    // divide the range 0 to 1 into a series of intervals with size
    // roughly doubling every two steps
    adaptiveTestParameters = new double[nSubdivisionsOfBandwidthTestDomain];
    int n = nSubdivisionsOfBandwidthTestDomain;
    for (int i = 0; i < nSubdivisionsOfBandwidthTestDomain - 1; i++) {
      double x = Math.pow(2.0, -(n - 1 - i) / 2.0);
      adaptiveTestParameters[i + 1] = x;
    }
    adaptiveTestParameters[nSubdivisionsOfBandwidthTestDomain - 1] = 1.0;
  }

  /**
   * A container for the results from a bootstrap analysis
   */
  public class BootstrapResult {

    int n;
    double mean;
    double variance;

    BootstrapResult(int n, double mean, double variance) {
      this.n = n;
      this.mean = mean;
      this.variance = variance;
    }

    /**
     * Get the half space (single side) of the confidence interval
     * for the bootstrap analysis. The full confidence interval
     * range is the mean minus the half space to the mean plus the half space.
     *
     * @param alpha the significance level for the confidence interval
     * (typically,
     * 0&#46;05, etc&#46;).
     * @return a positive floating-point value.
     */
    public double getConfidenceIntervalHalfSpan(double alpha) {
      TDistribution td = new TDistribution(n);
      double ta = td.inverseCumulativeProbability(1.0 - alpha / 2.0);
      return Math.sqrt(variance) * ta;
    }

    /**
     * Get the number of successful trials used in the calculation of the
     * mean and variance for the bootstrap analysis.
     *
     * @return a positive whole number
     */
    public int getN() {
      return n;
    }

    /**
     * Get the mean predicted value at the query point as determined by
     * bootstrap analysis.
     *
     * @return a valid floating point value
     */
    public double getMean() {
      return mean;
    }

    /**
     * Get the variance of the predicted values as determined by
     * bootstrap analysis.
     *
     * @return a valid floating point value
     */
    public double getVariance() {
      return variance;
    }

    @Override
    public String toString() {
      return "Bootstrap result n=" + n + ", mean=" + mean + ", variance=" + variance;
    }

  }

  /**
   * Standard constructor.
   *
   */
  public GwrInterpolator() {

    this.bandwidth = 0; // not yet specified

    // IMPORTANT: the array indices must relfect the ordinal of the
    // SurfaceModel enumeration
    regressionArray = new SurfaceGwr[6];

    regressionArray[0] = new SurfaceGwr(SurfaceModel.Planar);
    regressionArray[1] = new SurfaceGwr(SurfaceModel.PlanarWithCrossTerms);
    regressionArray[2] = new SurfaceGwr(SurfaceModel.Quadratic);
    regressionArray[3] = new SurfaceGwr(SurfaceModel.QuadraticWithCrossTerms);
    regressionArray[4] = new SurfaceGwr(SurfaceModel.Cubic);
    regressionArray[5] = new SurfaceGwr(SurfaceModel.CubicWithCrossTerms);
  }

  private void checkInputs(int nSamples, double[][] samples) {
    int minRequired = SurfaceModel.Planar.getIndependentVariableCount();

    if (nSamples < minRequired) {
      throw new IllegalArgumentException(
        "Specified number of samples,"
        + nSamples
        + ", is smaller than minumum of "
        + minRequired);
    }

    if (samples.length < nSamples) {
      throw new IllegalArgumentException(
        "Input array length " + samples.length
        + "is smaller than specified number of samples " + nSamples);
    }

    for (int i = 0; i < nSamples; i++) {
      if (samples[i].length < 3) {
        throw new IllegalArgumentException(
          "Input sample " + i + " is of length "
          + samples[i].length + ", where 3 is required");
      }
    }
  }

  /**
   * Performs regression using adaptive bandwidth selection and testing all
   * available models using the AICc criterion. This method is intended
   * to achieve a good solution under general circumstances, though it is
   * also the most computationally expensive implementation.
   * <p>
   * Values are assigned to samples using the specified valuator,
   * or by taking their z-values directly if no valuator (e.g. a null)
   * is specified.
   *
   * @param qx the x coordinate for the interpolation point
   * @param qy the y coordinate for the interpolation point
   * @param nSamples the number of samples to process
   * @param samples a nSamples-by-3 array giving the x, y, and z
   * coordinates for input samples.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a NaN.
   */
  public double interpolateUsingAdaptiveModelAndBandwidth(
    double qx, double qy, int nSamples, double[][] samples) {

    checkInputs(nSamples, samples);
    double[] weights = new double[nSamples];
    double[] distsq = new double[nSamples];
    double meanDist = prepDistances(qx, qy, nSamples, samples, distsq);

    double zBest = Double.NaN;
    double bestAICc = Double.POSITIVE_INFINITY;
    double bestBandwidth = Double.POSITIVE_INFINITY;
    SurfaceGwr bestRegression = null;
    regression = null;
    for (SurfaceGwr reg : regressionArray) {
      regression = reg;
      SurfaceModel model = reg.getModel();
      if (!prepWeights(model, BandwidthSelectionMethod.AdaptiveBandwidth,
        0, qx, qy, nSamples, samples, distsq, weights, meanDist)) {
        continue;
      }

      beta = regression.computeRegression(qx, qy, nSamples, samples, weights);
      if (beta == null) {
        continue;
      }
      double zTest = beta[0];

      double AICc = reg.getAICc();
      if (AICc < bestAICc) {
        bestAICc = AICc;
        bestBandwidth = bandwidth;
        bestRegression = reg;
        zBest = zTest;
      }
    }
    regression = bestRegression;
    bandwidth = bestBandwidth;
    regression = bestRegression;
    // if the best regression results were not on the final entry
    // in the regression array, preform the interpolation again to
    // ensure that all internal state variables are set to the
    // correct values.
    if (regression != regressionArray[regressionArray.length - 1]) {
      computeWeights(nSamples, distsq, weights, bestBandwidth);
      beta = regression.computeRegression(qx, qy, nSamples, samples, weights);
      if (beta == null) {
        return Double.NaN; // not expected
      }
      zBest = beta[0];
    }

    //for (int i = 0; i < nSamples; i++) {
    //  double yEst = regression.getEstimatedValue(samples[i][0], samples[i][1]);
    //  double yErr = yEst - samples[i][2];
    //  double dx = samples[i][0] - qx;
    //  double dy = samples[i][1] - qy;
    //  double d = Math.sqrt(dx * dx + dy * dy);
    //  System.out.format(
    //    "%2d.  %12.4f   %12.4f   (%12.4f)  %12.4f  %12.4f(w) %12.4f(d)\n",
    //    i, samples[i][2], yEst, yErr, yErr * weights[i], weights[i], d);
    //}
    return zBest;
  }

  private void computeWeights(
    int nSamples,
    double[] distsq,
    double[] weights,
    double lambda) {
    double lambda2 = lambda * lambda;
    for (int i = 0; i < nSamples; i++) {
      weights[i] = Math.exp(-0.5 * (distsq[i] / lambda2));
    }
  }

  private boolean prepWeights(SurfaceModel model,
    BandwidthSelectionMethod bandwidthMethod,
    double bandwidthParameter,
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq,
    double[] weights,
    double meanDist) {

    int ordinal = model.ordinal();
    regression = regressionArray[ordinal];

    minRequiredSamples = regression.getMinimumRequiredSamples();
    if (minRequiredSamples > nSamples) {
      return false;
    }

    this.bandwidthMethod = bandwidthMethod;
    this.bandwidthParameter = bandwidthParameter;

    switch (bandwidthMethod) {
      case FixedBandwidth:
        bandwidth = bandwidthParameter;
        break;
      case FixedProportionalBandwidth:
        bandwidth = meanDist * bandwidthParameter;
        break;
      case AdaptiveBandwidth:
        bandwidth = prepAdaptiveBandwidthSelection(
          qx, qy, nSamples, samples, distsq, weights, meanDist);
        break;
      case OrdinaryLeastSquares:
        bandwidth = Double.POSITIVE_INFINITY;
        break;
      default:
        bandwidth = Double.POSITIVE_INFINITY;
        break;
    }

    // if the adaptive bandwidth method decides that ordinary least squares
    // is the best selection, it will select an infinite bandwidth value.
    if (bandwidthMethod == BandwidthSelectionMethod.OrdinaryLeastSquares
      || Double.isInfinite(bandwidth)) {
      Arrays.fill(weights, 0, nSamples, 1.0);
    } else {
      computeWeights(nSamples, distsq, weights, bandwidth);
    }
    return true;
  }

  /**
   * Copies the input samples to local storage and sets up
   * arrays of weights and distances.
   *
   * @param qx the x coordinate of the interpolation point
   * @param qy the y coordinate of the interpolation point
   * @param nInputSamples number of samples for input
   * @param inputSamples an nSamples-by-3 array of inputs.
   * @param distsq an array to store the squared distances of the samples
   * from the interpolation coordinates.
   * @return the mean distance for the samples.
   */
  private double prepDistances(
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq) {

    double sumDist = 0;

    for (int i = 0; i < nSamples; i++) {
      double[] s = samples[i];
      double dx = s[0] - qx;
      double dy = s[1] - qy;
      double d2 = dx * dx + dy * dy;
      distsq[i] = d2;
      sumDist += Math.sqrt(d2);
    }
    return sumDist / nSamples;

  }

  private double testBandwidth(
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq,
    double weights[],
    double lambda) {
    nAdaptiveBandwidthTests++;
    double lambda2 = lambda * lambda;
    for (int i = 0; i < nSamples; i++) {
      weights[i] = Math.exp(-0.5 * (distsq[i] / lambda2));
    }
    regression.computeRegression(qx, qy, nSamples, samples, weights);
    return regression.getAICc();
  }

  private double testOrdinaryLeastSquares(
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double weights[]) {
    Arrays.fill(weights, 0, nSamples, 1.0);
    regression.computeRegression(qx, qy, nSamples, samples, weights);

    return regression.getResidualVariance();
    // return regression.getPredictionIntervalHalfRange(0.05);
  }

  private double prepAdaptiveBandwidthSelection(
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq,
    double[] weights,
    double meanDist) {

    double m0 = meanDist * 0.4;
    double m1 = meanDist * 1.0;
    double deltaM = m1 - m0;
    if (deltaM < 0) {
      deltaM = 0;
    }

    int nCut = nSubdivisionsOfBandwidthTestDomain;
    double x[] = new double[nCut];
    double y[] = new double[nCut];
    for (int i = 0; i < nCut; i++) {
      x[i] = adaptiveTestParameters[i] * deltaM + m0;
      y[i] = testBandwidth(qx, qy, nSamples, samples, distsq, weights, x[i]);
    }

    double acceptX = deltaM / 1.0e+5;
    double xBest = x[0];
    double yBest = y[0];
    int iBest = 0;
    for (int i = 1; i < nCut; i++) {
      if (y[i] <= yBest) {
        xBest = x[i];
        yBest = y[i];
        iBest = i;
      }
    }

    if (iBest == nCut - 1) {
      // testing shows that when the best test value is the last
      // in the array, further optimization is unlikely to yield
      // improvement.  And, in fact, ordinary least squares may yield a
      // better soluton.
      double yTest = testOrdinaryLeastSquares(qx, qy, nSamples, samples, weights);
      if (yTest < yBest) {
        return Double.POSITIVE_INFINITY;
      }
      return xBest;
    }

    for (int i = 1; i < nCut - 1; i++) {
      double y0 = y[i - 1];
      double y1 = y[i];
      double y2 = y[i + 1];
      double x0 = x[i - 1];
      double x1 = x[i];
      double x2 = x[i + 1];
      double s0 = (y1 - y0) / (x1 - x0);
      double s1 = (y2 - y1) / (x2 - x1);
      if (s1 > s0) {
        for (int iTest = 0; iTest < 3; iTest++) {
          double Xb = x1 - x0;
          double Xc = x2 - x0;
          double Xb2 = Xb * Xb;
          double Xc2 = Xc * Xc;
          double Yb = y1 - y0;
          double Yc = y2 - y0;
          double det = Xb2 * Xc - Xb * Xc2;
          double A = (Xc * Yb - Xb * Yc) / det;
          // A the second derivative of the parabolic equation
          if (A <= 0) {
            // found a local maximum or inflection point
            break;
          }
          // found a minimum
          double B = (Xb2 * Yc - Xc2 * Yb) / det;
          double xTest = x0 - B / (2 * A);
          double delta;
          double yTest;
          if (xTest < x1) {
            // xtest on left side of x1
            if (xTest < x0) {
              break;  // out of range
            }
            yTest = testBandwidth(qx, qy, nSamples, samples, distsq, weights, xTest);
            if (Double.isNaN(yTest)) {
              // diagnostic, call it again to see what went wrong
              // testBandwidth(qx, qy, nSamples, samples, distsq, w, xTest);
              break;
            }
            delta = x1 - xTest;
            x2 = x1;
            y2 = y1;
            x1 = xTest;
            y1 = yTest;
          } else {
            // xTest on right side of x1
            if (xTest > x2) {
              break;
            }
            yTest = testBandwidth(qx, qy, nSamples, samples, distsq, weights, xTest);
            if (Double.isNaN(yTest)) {
              // not expected, potentially pathological. skip.
              // testBandwidth(qx, qy, w, xTest);
              break;
            }
            delta = xTest - x1;
            x0 = x1;
            y0 = y1;
            x1 = xTest;
            y1 = yTest;
          }

          if (yTest < yBest) {
            yBest = yTest;
            xBest = xTest;
          }
          if (delta < acceptX) {
            break;
          }
        }
      }
    }
    return xBest;
  }

  /**
   * Performs regression using the specified surface model and
   * bandwidth-selection method. The bandwidth parameter will be applied
   * according
   * to which bandwidth-selection method is supplied.
   * <p>
   * When in doubt, a good general specification for bandwidth-selection is
   * to use BandwidthSelectionMethod.FixedProportionalBandwidth
   * with a bandwidth parameter of 0.5. The model should be selected based
   * on the anticipated behavior of the surface. SurfaceModel.Cubic is
   * usually a good choice for all but flat surfaces.
   *
   * @param model a valid model specification
   * @param bandwidthMethod a valid bandwidth-selection method specification
   * @param bandwidthParameter a bandwidth parameter
   * @param qx the x coordinate of the interpolation point
   * @param qy the y coordinate of the interpolation point
   * @param nSamples the number of samples for input processing
   * @param samples an array of dimensions nSamples-by-3 giving x, y, and z
   * coordinates for input samples.
   * @return if successful, an valid floating-point number; otherwise
   * a Double.NaN.
   */
  public double interpolate(SurfaceModel model,
    BandwidthSelectionMethod bandwidthMethod,
    double bandwidthParameter,
    double qx,
    double qy,
    int nSamples,
    double[][] samples) {

    checkInputs(nSamples, samples);
    double[] distsq = new double[nSamples];
    double[] weights = new double[nSamples];

    double distMean = prepDistances(qx, qy, nSamples, samples, distsq);

    if (!prepWeights(model, bandwidthMethod, bandwidthParameter, qx, qy, nSamples, samples, distsq, weights, distMean)) {
      return Double.NaN;
    }

    // The prepSamples method obtained the samples from the TIN. Then
    // prepInputs selected the SurfaceGWR to be used and stuck it in
    // the member element regression.  It also populated the weights to
    // be used in the regression calculation.
    beta = regression.computeRegression(qx, qy, nSamples, samples, weights);
    if (beta == null) {
      return Double.NaN;
    }
    return beta[0];
  }

  /**
   * Gets the instance of the Geographically Weighted Regression (GWR)
   * class that was used in the most recent interpolation. The member elements
   * of the SurfaceGWR instance returned by this call will contain
   * statistical data and regression coefficients from the most
   * recent interpolation and is the preferred way to access such information.
   * <p>
   * For efficiency, this class class creates a single
   * instance of SurfaceGWR for each surface model and reuses them
   * across multiple interpolations. Thus, the state data stored in this
   * the SurfaceGWR's member elements may change if additional
   * interpolations are performed. And since some interpolation options
   * may select different surface models between interpolations, there
   * is no guarantee that the SurfaceGWR used for one interpolation (which
   * might require a planar surface) would be used for the next
   * (which might include a quadratic surface). So an application that requires
   * access to the data stored in a SurfaceGWR should call this method anew
   * after each interpolation action, extract the necessary data, and
   * not attempt to preserve the current reference to SurfaceGWR across
   * multiple calls to the interpolation methods.
   * <p>
   * This method is intended for diagnostic and analysis purposes.
   *
   * @return the instance that was used in the most recent interpolation
   * or a null if no interpolation has been performed.
   */
  public SurfaceGwr getCurrentSurfaceGWR() {
    return regression;
  }

  /**
   * Gets the slope for the most recent computation expressed as
   * the fraction change in elevation over a horizontal distance
   * (i&#46;e&#46; rise-over-run).
   * This calculation is non directional. Note that this method
   * assumes that the vertical and horizontal of the input sample
   * points are isotropic
   *
   * @return if available, a valid, positive value;
   * if the regression failed, NaN.
   */
  public double getSlope() {
    if (beta == null) {
      return Double.NaN;
    }
    final double fx = beta[1];
    final double fy = beta[2];
    return Math.sqrt(fx * fx + fy * fy);
  }

  /**
   * Gets the unit normal to the surface at the position of the most
   * recent interpolation. The unit normal is computed based on the
   * partial derivatives of the surface polynomial evaluated at the
   * coordinates of the query point. Note that this method
   * assumes that the vertical and horizontal coordinates of the
   * input sample points are isotropic.
   *
   * @return if defined, a valid array of dimension 3 giving
   * the x, y, and z components of the normal, respectively; otherwise,
   * a zero-sized array.
   */
  public double[] getSurfaceNormal() {
    if (beta == null) {
      // basically, undefined
      return new double[0];
    } else {
      final double[] n = new double[3];
      final double fx = -beta[1];
      final double fy = -beta[2];
      final double s = Math.sqrt(fx * fx + fy * fy + 1);
      n[0] = fx / s;
      n[1] = fy / s;
      n[2] = 1 / s;
      return n;
    }
  }

  /**
   * Perform a variation of a statistical bootstrap analysis in which the
   * resampling is based on random selection of samples without repetition.
   * The result gives a mean and variance for the predicted value of
   * the surface at the query point.
   *
   * @param model the model to be used to represent the surface
   * @param bandwidthMethod the method used for selecting bandwidth
   * @param bandwidthParameter the input parameter for the specified method
   * @param qx X coordinate of query point
   * @param qy Y coordinate of query point
   * @param nSamples the number of samples for processing
   * @param samples an nSamples-by-3 array of samples for processing
   * @param nRepetitions number of sub-samples to evaluate for bootstrap
   * analysis.
   * @param threshold the probability of accepting any single sample into
   * the subsample.
   * @return if successful, a non-null result instance; otherwise, a null.
   */
  public BootstrapResult bootstrap(SurfaceModel model,
    BandwidthSelectionMethod bandwidthMethod,
    double bandwidthParameter,
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    int nRepetitions,
    double threshold) {
    checkInputs(nSamples, samples);
    double[] distsq = new double[nSamples];
    double[] weights = new double[nSamples];
    double meanDist = prepDistances(qx, qy, nSamples, samples, distsq);

    if (!prepWeights(
      model, bandwidthMethod, bandwidthParameter,
      qx, qy, nSamples, samples, distsq, weights, meanDist)) {
      return null;
    }

    double[][] jInputs = new double[nSamples][];
    double[] jWeights = new double[nSamples];
    Random jRand = new Random(0);
    double jSum = 0;
    double j2Sum = 0;
    int n = 0;

    for (int jN = 0; jN < nRepetitions; jN++) {
      int k = 0;
      for (int i = 0; i < nSamples; i++) {
        double d = jRand.nextDouble();
        if (d < threshold) {
          continue;
        }
        jInputs[k] = samples[i];
        jWeights[k] = weights[i];
        k++;
      }

      if (k < minRequiredSamples) {
        continue;
      }
      beta = regression.computeRegression(qx, qy, k, jInputs, jWeights);
      if (beta == null || Double.isNaN(beta[0])) {
        continue;
      }
      jSum += beta[0];
      j2Sum += (beta[0] * beta[0]);
      n++;
    }
    double jMean = jSum / n;
    double s2 = (n * j2Sum - jSum * jSum) / (n * (n - 1));
    return new BootstrapResult(n, jMean, s2);

  }

  /**
   * Get the bandwidth setting used in the most recent interpolation.
   * This value may have been specified as an argument to the
   * interpolation method (when the FixedBandwidth option is used)
   * or may have been computed dynamically.
   *
   * @return if set, a real value greater than or equal to zero
   */
  public double getBandwidth() {
    return bandwidth;
  }

  /**
   * Gets the number of tests performed in selecting a bandwidth
   * via the adaptive selection method. Intended for diagnostic
   * purposes only.
   * @return a positive value
   */
  public long getAdaptiveBandwidthTestCount() {
    return nAdaptiveBandwidthTests;
  }

  /**
   * Gets the method used for the most recent bandwidth selection.
   * @return if a regression has been performed, a valid enumeration;
   * otherwise, a null.
   */
  public BandwidthSelectionMethod getBandwidthSelectionMethod(){
    return  bandwidthMethod;
  }
}
