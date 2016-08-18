/*
 * Copyright 2014 Gary W. Lucas.
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
 * Date      Name      Description
 * ------    --------- -------------------------------------------------
 * 07/2014   G. Lucas  Created
 * 03/2016   G. Lucas  Refactored to separate into a class which would
 *                       support interpolation from sample points without
 *                       a connection to a TIN.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.interpolation;

import java.util.ArrayList;
import java.util.List;
import tinfour.common.IIncrementalTin;
import tinfour.common.INeighborhoodPointsCollector;
import tinfour.common.Vertex;
import tinfour.gwr.BandwidthSelectionMethod;
import tinfour.gwr.GwrInterpolator;
import tinfour.gwr.SurfaceModel;

/**
 * Provides methods and elements for performing interpolation over a
 * surface using linear regression methods to develop a polynomial
 * z = f(x, y) describing the surface in the region of the interpolation
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
 * <strong>A Note on Safe Coding:</strong> This class maintains references to
 * its most recent inputs as member elements. For efficiency purposes,
 * it does not make copies of the input arrays, but uses them directly.
 * Therefore, it is imperative that the calling application not modify
 * these elements until it is done with the results from a computation. While
 * this approach violates well-known safe-coding practices advocated by
 * many parties, it is useful as a way of improving throughput
 * for data processing and should not be a problem for careful developers.
 */
public class GwrTinInterpolator extends GwrInterpolator implements IInterpolatorOverTin {

  IIncrementalTin tin;

  int minRequiredSamples;

  private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

  int maxDepth;
  int nSamples;
  int nSamplesSum;
  double interpolationPointX;
  double interpolationPointY;

  List<Vertex> sampleVertexList = new ArrayList<>();
  double[][] samples = new double[64][3];

  INeighborhoodPointsCollector neighborhoodPoints;

  private boolean prepSamples(
    double qx,
    double qy,
    IVertexValuator valuator) {

    nSamples = 0;
    interpolationPointX = qx;
    interpolationPointY = qy;

    IVertexValuator vq = valuator;
    if (vq == null) {
      vq = defaultValuator;
    }

    sampleVertexList = neighborhoodPoints.collectNeighboringVertices(qx, qy, maxDepth, minRequiredSamples);
    nSamples = sampleVertexList.size();
    nSamplesSum += nSamples;
    if (nSamples == 0) {
      return false;
    }

    if (nSamples > samples.length) {
      samples = new double[nSamples + 32][3];
    }

    int k = 0;
    for (Vertex sample : sampleVertexList) {
      samples[k][0] = sample.x;
      samples[k][1] = sample.y;
      samples[k][2] = vq.value(sample);
      //System.out.format("%2d: %12.2f,  %12.2f,  %10.5f %f\n",
      //  k, samples[k][0], samples[k][1], samples[k][2],
      //  sample.getDistance(qx, qy));
      k++;
    }

    return true;
  }

  /**
   * Construct an interpolator that operates on the specified TIN.
   * Because the interpolator will access the TIN on a read-only basis,
   * it is possible to construct multiple instances of this class and
   * allow them to operate in parallel threads.
   * <h1>Important Synchronization Issue</h1>
   * In order to provide maximum performance, the classes in this package
   * do not implement any kind of Java synchronization or or even the
   * relatively weak concurrent-modification testing provided by the
   * Java collection classes. If an application modified the TIN, instances
   * of this class will not be aware of the change. In such cases,
   * interpolation methods may fail by either throwing an exception or,
   * worse, returning an incorrect value. The onus is on the calling
   * application to make sure that the reset()
   * methods are called if the TIN has been modified.
   *
   * @param tin a valid instance of an incremental TIN.
   *
   */
  public GwrTinInterpolator(IIncrementalTin tin) {
    super();
    neighborhoodPoints = tin.getNeighborhoodPointsCollector();
    this.tin = tin;
    this.maxDepth = 3;

  }

  /**
   * Construct an interpolator that operates on the specified TIN.
   * Because the interpolator will access the TIN on a read-only basis,
   * it is possible to construct multiple instances of this class and
   * allow them to operate in parallel threads.
   * <h1>Important Synchronization Issue</h1>
   * In order to provide maximum performance, the classes in this package
   * do not implement any kind of Java synchronization or or even the
   * relatively weak concurrent-modification testing provided by the
   * Java collection classes. If an application modified the TIN, instances
   * of this class will not be aware of the change. In such cases,
   * interpolation methods may fail by either throwing an exception or,
   * worse, returning an incorrect value. The onus is on the calling
   * application to make sure that the reset()
   * methods are called if the TIN has been modified.
   *
   * @param tin a valid instance of an incremental TIN.
   * @param maxDepth the maximum depth of the search for neighboring points
   *
   */
  public GwrTinInterpolator(IIncrementalTin tin, int maxDepth) {
    super();
    neighborhoodPoints = tin.getNeighborhoodPointsCollector();

    this.tin = tin;
    this.maxDepth = maxDepth;
  }

  /**
   * Used by an application to reset the state data within the interpolator
   * when the content of the TIN may have changed. Reseting the state data
   * unnecessarily may result in a performance reduction when processing
   * a large number of interpolations, but is otherwise harmless.
   */
  @Override
  public void resetForChangeToTin() {
    neighborhoodPoints.resetForChangeToTin();
  }

  /**
   * Obtains an interpolated value at the specified coordinates using
   * a Geographically Weighted Linear Regression. The interpolation uses
   * the default Cubic surface model and selecting a bandwidth parameter based
   * on
   * one half the average distance of all samples from the query point.
   * <p>
   * Please see the documentation for IInterpolatorOverTin for more detail.
   *
   * @param qx the x coordinate for the interpolation point
   * @param qy the y coordinate for the interpolation point
   * @param valuator an optional valuator (supply null if none is required).
   * @return if successful, a valid floating point number; otherwise,
   * Double.NaN.
   */
  @Override
  public double interpolate(double qx, double qy, IVertexValuator valuator) {
    return interpolate(SurfaceModel.CubicWithCrossTerms,
      BandwidthSelectionMethod.FixedProportionalBandwidth, 0.5,
      qx, qy, valuator);
  }

  /**
   * Perform regression using adaptive bandwidth selection and testing all
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
   * @param valuator a valid valuator for interpreting the z value of each
   * vertex or a null value to use the default.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a NaN.
   */
  public double interpolateUsingAutomaticModelAndBandwidth(
    double qx, double qy, IVertexValuator valuator) {

    if (!prepSamples(qx, qy, valuator)) {
      return Double.NaN;
    }

    return  interpolateUsingAutomaticModelAndBandwidth(
      qx, qy, nSamples, samples);
  }

  /**
   * Performs regression using the specified surface model and
   * bandwidth-selection method. The bandwidth parameter will be applied
   * according
   * to which bandwidth-selection method is supplied.
   * <p>
   * When in doubt, a good general specification for bandwidth-selection is
   * to use BandwidthSelectionMethod.FixedProportionalBandwidth
   * with a bandwidth parameter of 1.0. The model should be selected based
   * on the anticipated behavior of the surface. SurfaceModel.Quadratic is
   * usually a good choice for all but flat surfaces, though
   * QuadraticWithCrossTerms is useful for curvature-related computations.
   *
   * @param model a valid model specification
   * @param bandwidthMethod a valid bandwidth-selection method specification
   * @param bandwidthParameter a bandwidth parameter
   * @param qx the x coordinate of the interpolation point
   * @param qy the y coordinate of the interpolation point
   * @param valuator an optional valuator for interpolation
   * @return if successful, an valid floating-point number; otherwise
   * a Double.NaN.
   */
  public double interpolate(SurfaceModel model,
    BandwidthSelectionMethod bandwidthMethod,
    double bandwidthParameter,
    double qx,
    double qy,
    IVertexValuator valuator) {

    if (!prepSamples(qx, qy, valuator)) {
      return Double.NaN;
    }
    return interpolate(
      model,
      bandwidthMethod,
      bandwidthParameter,
      qx,
      qy,
      nSamples,
      samples);

  }

  /**
   * Gets the number of samples used in the most recent calculation.
   * Intended for diagnostic and analysis purposes.
   *
   * @return if a valid calculation was performed, a value greater
   * than the number of degrees of freedom for the model that was used.
   */
  public int getSampleCount() {
    return nSamples;
  }

  /**
   * Get the samples used in the most recent interpolation. Intended for
   * diagnostic and analysis purposes.
   *
   * @return a valid array, zero sized if no interpolation has been performed.
   */
  public Vertex[] getSampleVertices() {
    if (nSamples == 0) {
      return new Vertex[0];
    }
    return sampleVertexList.toArray(new Vertex[nSamples]);
  }

  @Override
  public boolean isSurfaceNormalSupported() {
    return true;
  }

  @Override
  public String getMethod() {
    BandwidthSelectionMethod bMethod = getBandwidthSelectionMethod();
    double bParameter = this.getBandwidth();
    SurfaceModel model = getSurfaceModel();
    if (model == null) {
      return "GWR: No interpolation performed";
    }else{
    return "GWR "
      + model
      + ", depth "
      + maxDepth
      + ", bandwidth "
      + bMethod + ":"
      + bParameter ;
    }
  }


  /**
   * Gets the mean distance of the samples from the most
   * recent interpolation point
   *
   * @return if available, a positive value; if not interpolation has been
   * performed, Double&#46;NaN.
   */
  public double getSampleDistanceMean() {
    double sumDist = 0;

    if (nSamples == 0) {
      return Double.NaN;
    }
    for (int i = 0; i < nSamples; i++) {
      double[] s = samples[i];
      double dx = s[0] - interpolationPointX;
      double dy = s[1] - interpolationPointY;
      double d2 = dx * dx + dy * dy;
      sumDist += Math.sqrt(d2);
    }
    return sumDist / nSamples;
  }

    /**
   * Indicates that the most recent target coordinates were
   * exterior to the TIN. Set by the most recent interpolation.
   *
   * @return true if the most recent coordinates were exterior to the
   * TIN; otherwise, false.
   */

  public boolean wasTargetExteriorToTin() {
    return this.neighborhoodPoints.wasTargetExteriorToTin();
  }
}
