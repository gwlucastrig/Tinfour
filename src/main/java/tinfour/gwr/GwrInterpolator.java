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
 * z = p(x, y) describing the surface in the region of the interpolation
 * coordinates.
 * <p><strong>A Note on the Suitability of This Implementation: </strong>
 * Anyone who values his own time should respect the time of others.
 * With that regard, I believe it appropriate to make this note about
 * the current state of the Tinfour GWR implementation.  While I believe
 * that code is implemented correctly, it is not complete.
 * Statistics such as R values and F scores are not yet available.
 * The Tinfour GWR classes also lacks tools for detecting multi collinearities
 * in model coefficients.  These classes were developed with a specific
 * application in mind: the modeling of terrain and bathymetry.
 * And while they can be applied to many other problems, potential
 * users should consider whether the tool is suitable to their particular requirements.
 * <p>
 * <Strong>Development Notes</strong><br>
 * The current implementation of this class supports a family of surface
 * models based on polynomials p(x, y) of order 3 or less. While this approach
 * is appropriate for the original intent of this class, modeling terrain,
 * there is no reason why the class cannot be adapted to support
 * other models based on continuous, real-valued variables.
 * <p>
 * One of the special considerations in terrain modeling is "mass production".
 * Creating a raster grid from unstructured data can involve literally millions
 * of interpolation operations. The design of this class reflects
 * that requirement. In particular, it featured the reuse of Java
 * objects and arrays to avoid the cost of constructing or allocating
 * new instances. However, recent improvements in Java's handling
 * of short-persistence objects (through escape analysis) have made
 * some of these considerations less pressing. So future work
 * may not be coupled to the same approach as the existing implementation.
 *
 */
public class GwrInterpolator {

  SurfaceModel surfaceModel;
  final SurfaceGwr gwr;
  int minRequiredSamples;

  BandwidthSelectionMethod bandwidthMethod;
  double bandwidthParameter;
  double bandwidth;
  double[] beta;

  long nAutomaticBandwidthTests;

  // the number of intervals for subdividing the bandwidth test
  // domain when automatically choosing bandwidth.
  // These selections are not based on any theory, but were
  // chosen arbitrarily based on cross-validation testing and trading
  // off accuracy of predicted results and speed of calculation.
  private static final int nSubdivisionsOfBandwidthTestDomain = 6;
  private static final double bandwidthTestDomainScale0 = 0.3;
  private static final double bandwidthTestDomainScale1 = 1.0;

  private static final double[] automaticTestParameters;

  static {
    // The intervals are set up as a series of powers of sqrt(2).
    int n = nSubdivisionsOfBandwidthTestDomain;
    automaticTestParameters = new double[n];
     for (int i = 1; i < n-1; i++) {
      double x = Math.pow(2.0, -(n - i) / 2.0);
      automaticTestParameters[i] = x;
    }
    automaticTestParameters[n - 1] = 1.0;
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
    gwr = new SurfaceGwr();
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
   * Performs regression using automatic bandwidth selection and testing all
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
  public double interpolateUsingAutomaticModelAndBandwidth(
    double qx, double qy, int nSamples, double[][] samples) {

    checkInputs(nSamples, samples);
    double[] weights = new double[nSamples];
    double[][]sampleWeightsMatrix = new double[nSamples][nSamples];
    double[] distsq = new double[nSamples];
    double meanDist = prepDistances(qx, qy, nSamples, samples, distsq);
    double bestAICc = Double.POSITIVE_INFINITY;
    double bestBandwidth = Double.POSITIVE_INFINITY;
    SurfaceModel bestModel = null;
    for (SurfaceModel model: SurfaceModel.values()) {
      if (!prepWeights(model, BandwidthSelectionMethod.OptimalAICc,
        0, qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, meanDist)) {
        continue;
      }

      // make sure there are enough samples for the model
      if(nSamples<model.getCoefficientCount()+1){
        continue;
      }
      gwr.initWeightsMatrixUsingGaussianKernel(samples, nSamples, bandwidth, sampleWeightsMatrix);
      double AICc = gwr.evaluateAICc(
        model, qx, qy, nSamples, samples, weights, sampleWeightsMatrix);
      if (AICc < bestAICc) {
        bestAICc = AICc;
        bestBandwidth = bandwidth;
        bestModel = model;
      }
    }
    if(bestModel == null ){
      // none of the evalations produced a valid AICc, probably due to
      // defective inputs.
      return Double.NaN;
    }

    bandwidth = bestBandwidth;
    computeWeights(nSamples, distsq, weights, bestBandwidth);
    beta =
      gwr.computeRegression(
        bestModel, qx, qy, nSamples, samples, weights,null);
    if (beta == null) {
      return Double.NaN; // not expected
    }

    return beta[0];
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
    double[][]sampleWeightsMatrix,
    double meanDist) {

    minRequiredSamples = gwr.getMinimumRequiredSamples(model);
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
      case OptimalAICc:
        bandwidth = prepAutomaticBandwidthSelection(
          model, qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, meanDist);
        break;
      case OrdinaryLeastSquares:
        bandwidth = Double.POSITIVE_INFINITY;
        break;
      default:
        bandwidth = Double.POSITIVE_INFINITY;
        break;
    }

    // if the automatic bandwidth method decides that ordinary least squares
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
    SurfaceModel model,
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq,
    double weights[],
    double [][]sampleWeightsMatrix,
    double lambda) {
    nAutomaticBandwidthTests++;
    double lambda2 = lambda * lambda;
    for (int i = 0; i < nSamples; i++) {
      weights[i] = Math.exp(-0.5 * (distsq[i] / lambda2));
    }

    gwr.initWeightsMatrixUsingGaussianKernel(
      samples, nSamples, lambda, sampleWeightsMatrix);
    return gwr.evaluateAICc(
      model, qx, qy, nSamples, samples, weights, sampleWeightsMatrix);

  }

  private double testOrdinaryLeastSquares(
    SurfaceModel model,
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double []weights,
    double [][]sampleWeightsMatrix) {
    Arrays.fill(weights, 0, nSamples, 1.0);
    for(int i=0; i<nSamples; i++){
      Arrays.fill(sampleWeightsMatrix[i], 0, nSamples, 1.0);
    }
    gwr.computeRegression(
      model, qx, qy, nSamples, samples, weights, sampleWeightsMatrix);

    return gwr.getAICc();
  }

  private double prepAutomaticBandwidthSelection(
    SurfaceModel model,
    double qx,
    double qy,
    int nSamples,
    double[][] samples,
    double[] distsq,
    double[] weights,
    double [][]sampleWeightsMatrix,
    double meanDist) {
    // The Algorithm --------------------------------------------------
    // The goal here is to pick a bandwidth that results in good AICc score.
    // The lower the AICc, the better.  Unfortunately, computing AICc is
    // a very, very costly operation so we wish to reduce the number of times
    // the algorithm performs it.
    // When developing this method, I performed several plots of the curve
    // for the AICc as as function of bandwidth, or y = AICc(x) where x
    // is the bandwidth.  While many of these curves were monotonically
    // decreasing, some of them were quite complex with sharp increments
    // in value (perhaps even discontinuities?).  So techniques like
    // Newton's method are out.  Instead, I use the following approach
    //   1) divide the domain into a number of intervals, based on the
    //      mean distance of the samples from the query point. A minimum
    //      range is set because, in practice, very small values of bandwidth
    //      we not helpful.  These intervals are used to define coordinates
    //      x[0], x[1]..., x[n-1]
    //   2) Compute the AICc at each x[i] coording to obtain control points
    //      (x[0], y[0]), (x[1], y[1]), ..., (x[n-1], y[n-1]).
    //      Record the best AICc value and corresponding X as xBest and yBest.
    //   3) Loop through the control points, copying out sets of
    //      3 subsequential control points.
    //   4) Using the these "parabola points",
    //      compute the coefficients for a parabola that approximates the curve.
    //      Use elementary calculus to find the x coordinate, xTest, for a
    //      local minima of that curve.
    //   5) If a minima exists, compute yTest = AICc(xTest).  If the minima
    //      cannot be found, continue to the next set of 3 parabola poits and
    //      proceed from step 4.
    //   6) If yTest<yBest, set xBest = xTest, yBest = yTest.
    //   7) Adjust the coordinates of the three parabola points so that
    //      the (xTest, yTest) is now the center point.  Repeat the
    //      process starting at step 4 for a fixed number of times or until
    //      no improvement in yBest is available within the current interval.
    //      If no improvement is available, select the next set of three
    //      control points as parabola points and continue from step 4.
    //

    double m0 = meanDist * bandwidthTestDomainScale0;
    double m1 = meanDist * bandwidthTestDomainScale1;
    double deltaM = m1 - m0;
    if (deltaM < 0) {
      deltaM = 0;
    }

    int nCut = nSubdivisionsOfBandwidthTestDomain;
    double x[] = new double[nCut];
    double y[] = new double[nCut];
    for (int i = 0; i < nCut; i++) {
      x[i] = automaticTestParameters[i] * deltaM + m0;
      y[i] = testBandwidth(
        model, qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, x[i]);
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
      double yTest = testOrdinaryLeastSquares(
        model, qx, qy, nSamples, samples, weights,sampleWeightsMatrix);
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
      // pre-test.  If the curve is not convex upward, it will
      // not have a minima.
      if (s1 > s0) {
        for (int iTest = 0; iTest < 3; iTest++) {
          // to simplify algebra, we will translate the three
          // parabola points so that the initial point is the origin.
          // so the parabola is defined by (0,0), (Xb, yb),  (Xc,Yc)
          // In this modified coordinate system
          //    y = A*x^2 +B*x   and y(0) = 0.
          //  To solve for A and B, we set up a linear system
          //    Yb = A*Xb^2 + B*Xb
          //    YC = A*Xb^2 + B*Xc
          //  We can find the X coordinate for a local minima (if one exists)
          //  in this coordinate system and then translate it back to standard
          //  coordinates to compute the AICc
          double Xb = x1 - x0;
          double Xc = x2 - x0;
          double Xb2 = Xb * Xb;
          double Xc2 = Xc * Xc;
          double Yb = y1 - y0;
          double Yc = y2 - y0;
          double det = Xb2 * Xc - Xb * Xc2;  // this will never be zero
          double A = (Xc * Yb - Xb * Yc) / det;
          // 2*A is the second derivative of the parabolic equation
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
            yTest = testBandwidth(model, qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, xTest);
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
            yTest = testBandwidth(model, qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, xTest);
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
    double [][]sampleWeightsMatrix = null;
    if(bandwidthMethod==BandwidthSelectionMethod.OptimalAICc){
      sampleWeightsMatrix = new double[nSamples][nSamples];
    }
    if (!prepWeights(
      model,   bandwidthMethod,   bandwidthParameter,
      qx, qy, nSamples, samples, distsq, weights, sampleWeightsMatrix, distMean))
    {
      return Double.NaN;
    }

    // The prepSamples method obtained the samples from the TIN. Then
    // prepInputs selected the SurfaceGWR to be used and stuck it in
    // the member element regression.  It also populated the weights to
    // be used in the regression calculation.
    beta = gwr.computeRegression(
       model, qx, qy, nSamples, samples, weights, sampleWeightsMatrix);
    if (beta == null) {
      return Double.NaN;
    }
    return beta[0];
  }


  private void prepSampleWeightsMatrix(){
    if(!gwr.isSampleWeightsMatrixSet()){
        int nSamples = gwr.getSampleCount();
        if(nSamples == 0){
          return;
        }
        double [][]samples = gwr.getSamples();
        double [][]sampleWeightMatrix = new double[nSamples][nSamples];
        gwr.initWeightsMatrixUsingGaussianKernel(samples, nSamples, bandwidth, sampleWeightMatrix);
        gwr.setSampleWeightsMatrix(sampleWeightMatrix);
    }
  }

  /**
   * Get the Akaike information criterion (corrected) organized so that the
   * <strong>minimum</strong> value is preferred.
   *
   * @return a valid floating point number.
   */
  public double getAICc() {
    prepSampleWeightsMatrix();
    return gwr.getAICc();
  }

  /**
   * Gets the coefficients computed by the most recent regression
   * calculation, or a zero-length array if no results are available.
   * @return if available, a valid array; otherwise a zero-length array.
   */
  public double[] getCoefficients(){
    if(beta==null){
      return new double[0];
    }else{
    return Arrays.copyOf(beta, beta.length);
    }
  }

    /**
   * Get the effective degrees of freedom for the a chi-squared distribution
   * which approximates the distribution of the GWR. Combined with the
   * residual variance, this yields an unbiased estimator that can be
   * used in the construction of confidence intervals and prediction
   * intervals.
   * <p>
   * The definition of this method is based on Leung (2000).
   *
   * @return a positive, potentially non-integral value.
   */
  public double getEffectiveDegreesOfFreedom() {
    prepSampleWeightsMatrix();
    return gwr.getEffectiveDegreesOfFreedom();
  }

    /**
   * Get leung's delta parameter
   *
   * @return a positive value
   */
  public double getLeungDelta1() {
    prepSampleWeightsMatrix();
    return gwr.getLeungDelta1();
  }

  /**
   * Get Leung's delta2 parameter.
   *
   * @return a positive value
   */
  public double getLeungDelta2() {
    prepSampleWeightsMatrix();
     return gwr.getLeungDelta2();
  }

 /**
   * Gets the prediction interval at the interpolation coordinates
   * on the observed response for the most recent call to computeRegression.
   * According to Walpole (1995), the prediction interval
   * "provides a bound within which we can say with a preselected
   * degree of certainty that a new observed response will fall."
   * For example, we do not know the true values of the surface at the
   * interpolation points, but suppose observed values were to become
   * available. Given a significance level (alpha) of 0.05, 95 percent
   * of the observed values would occur within the prediction interval.
   *
   * @param alpha the significance level (typically 0&#46;.05, etc).
   * @return an array of dimension two giving the lower and upper bound
   * of the prediction interval.
   */
  public double[] getPredictionInterval(double alpha) {
    prepSampleWeightsMatrix();
    double h = getPredictionIntervalHalfRange(alpha);
    double a[] = new double[2];
    a[0] = beta[0] - h;
    a[1] = beta[0] + h;
    return a;
  }

  /**
   * Gets a value equal to one half of the range of the prediction interval
   * on the observed response at the interpolation coordinates for the
   * most recent call to computeRegression().
   *
   * @param alpha the significance level (typically 0&#46;.05, etc).
   * @return a positive value.
   */
  public double getPredictionIntervalHalfRange(double alpha) {
    // TO DO: if the method is OLS, it would make sense to
    //        use a OLS version of this calculation rather than
    //        the more costly Leung version...  Also, I am not 100 %
    //        sure that they converge to the same answer, though they should
    prepSampleWeightsMatrix();
    return gwr.getPredictionIntervalHalfRange(alpha);
  }


    /**
   * Gets the residuals from the most recent regression calculation.
   * For this application, the residual the difference between the predicted
   * result and the input sample.
   *
   * @return if computed, a valid array of double; otherwise, an empty array.
   */
  public double[] getResiduals() {
    prepSampleWeightsMatrix();
   return gwr.getResiduals();
  }

    /**
   * Gets the residual sum of the squared errors (residuals) for
   * the predicted versus the observed values at the sample locations.
   *
   * @return a positive number.
   */
  public double getResidualSumOfTheSquares() {
    prepSampleWeightsMatrix();
    return gwr.getResidualSumOfTheSquares();
  }

  /**
   * Gets the samples from the most recent computation.
   * The array returned from this method is an n-by-3 array that
   * may contain more than nSamples entries. Therefore it is important
   * to call the getSampleCount() method to know how many samples
   * are actually valid.
   *
   * @return if available, a valid array of samples ; otherwise an empty array
   */
  public double[][] getSamples() {
    return gwr.getSamples();
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
   * Gets an unbiased estimate of the the standard deviation
   * of the residuals for the predicted values for all samples.
   *
   * @return if available, a positive real value; otherwise NaN.
   */
  public double getStandardDeviation() {
    prepSampleWeightsMatrix();
      return gwr.getStandardDeviation();
  }

  /**
   * Gets the ML Sigma value used in the AICc calculation. This
   * value is the sqrt of the sum of the residuals squared divided by
   * the number of samples.
   *
   * @return in available, a positive real value; otherwise NaN.
   */
  public double getSigmaML() {
    prepSampleWeightsMatrix();
    return gwr.getSigmaML();
  }



  /**
   * Gets the surface model for the most recently performed
   * interpolation. In the case where an optimization routine was
   * used to perform the interpolation, this value will be the model
   * selected by the optimization.
   * @return  if available a valid instance; if no interpolation
   * has been performed, a null.
   */
  public SurfaceModel getSurfaceModel(){
    return gwr.getModel();
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
   * Gets an unbiased estimate of the variance of the residuals
   * for the predicted values for all samples.
   *
   * @return if available, a positive real value; otherwise NaN.
   */
  public double getVariance() {
    prepSampleWeightsMatrix();
   return gwr.getVariance();
  }

 /**
   * Gets an array of weights from the most recent computation.
   *
   * @return if available, a valid array of weights; otherwise, an empty array.
   */
  public double[] getWeights() {
   return gwr.getWeights();
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

        double [][]sampleWeightsMatrix = null;
    if(bandwidthMethod==BandwidthSelectionMethod.OptimalAICc){
      sampleWeightsMatrix = new double[nSamples][nSamples];
    }
    if (!prepWeights(
      model, bandwidthMethod, bandwidthParameter,
      qx, qy, nSamples, samples, distsq, weights,sampleWeightsMatrix, meanDist))
    {
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
      beta = gwr.computeRegression(
          model, qx, qy, k, jInputs, jWeights, null);
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
   * via the automatic selection method. Intended for diagnostic
   * purposes only.
   * @return a positive value
   */
  public long getAutomaticBandwidthTestCount() {
    return nAutomaticBandwidthTests;
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
