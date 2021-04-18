/*
 * Copyright 2021 Gary W. Lucas.
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

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 03/2021 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.interpolation;

import java.io.PrintStream;
import java.util.List;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.INeighborhoodPointsCollector;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.utils.KahanSummation;

/**
 * Provides interpolation based on the classic method of inverse distance
 * weighting (IDW).
 * <p>
 * This class is intended primarily for diagnostic purposes and does not
 * implement a comprehensive set of options in support of the
 * inverse distance-weighting concept.
 */
public class InverseDistanceWeightingInterpolator implements IInterpolatorOverTin {
  // tolerance for identical vertices.
  // the tolerance factor for treating closely spaced or identical vertices
  // as a single point.

  private enum IdwVariation {
    Shepard,
    Power,
    GaussianKernel;
  }

  private final IdwVariation idwVariation;

  private final double vertexTolerance2; // square of vertexTolerance;
  private final double precisionThreshold;

  private final IIncrementalTinNavigator navigator;
  private final INeighborhoodPointsCollector neighborhoodPoints;

  private final double lambda;
  private final double power;

  private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

  private int maxDepth = 1;
  private int minPoints = 6;

  private long nInterpolation;
  private long sumVertexCount;
  private final KahanSummation sumDist = new KahanSummation();

  /**
   * Construct an interpolator that operates on the specified TIN.
   * Because the interpolator will access the TIN on a read-only basis,
   * it is possible to construct multiple instances of this class and
   * allow them to operate in parallel threads.
   * <h1>Important Synchronization Issue</h1>
   * To improve performance, the classes in this package
   * frequently maintain state-data about the TIN that can be reused
   * for query to query. They also avoid run-time overhead by not
   * implementing any kind of Java synchronization or or even the
   * concurrent-modification testing provided by the
   * Java collection classes. If an application modifies the TIN, instances
   * of this class will not be aware of the change. In such cases,
   * interpolation methods may fail by either throwing an exception or,
   * worse, returning an incorrect value. The onus is on the calling
   * application to manage the use of this class and to ensure that
   * no modifications are made to the TIN between interpolation operations.
   * If the TIN is modified, the internal state data for this class must
   * be reset using a call to resetForChangeToTIN().
   * <p>
   * This constructor creates an interpolator based on Shepard's classic
   * weight = 1/(d^2) formula.
   *
   * @param tin a valid instance of an incremental TIN.
   */
  public InverseDistanceWeightingInterpolator(IIncrementalTin tin) {
    idwVariation = IdwVariation.Shepard;
    Thresholds thresholds = tin.getThresholds();

    vertexTolerance2 = thresholds.getVertexTolerance2();
    precisionThreshold = thresholds.getPrecisionThreshold();

    navigator = tin.getNavigator();
    neighborhoodPoints = tin.getNeighborhoodPointsCollector();
    lambda = 3.5 / 2;
    power = 2.0;

  }

  /**
   * Constructs an interpolator using the specified method.
   * <p>
   * <strong>Gaussian Kernel:</strong> If the Gaussian Kernel option is
   * specified,
   * the parameter will be interpreted as the bandwidth (lambda) for
   * the formula weight = exp(-(1/2)(d/lambda)).
   * <p>
   * <strong>Power formula:</strong> If the Gaussian Kernel options is not
   * specified,
   * the parameter will be interpreted as a power for the formula
   * weight = 1/pow(d, power);
   *
   * @param tin a valid TIN
   * @param parameter a parameter specifying either bandwidth (for Gaussian)
   * or power. In both cases the parameter must be greater than zero.
   * @param gaussian true if the Gaussian kernel is to be used; otherwise
   * the non-Gaussian method (power) will be used
   */
  public InverseDistanceWeightingInterpolator(
    IIncrementalTin tin, double parameter, boolean gaussian) {
    Thresholds thresholds = tin.getThresholds();

    vertexTolerance2 = thresholds.getVertexTolerance2();
    precisionThreshold = thresholds.getPrecisionThreshold();
    navigator = tin.getNavigator();
    neighborhoodPoints = tin.getNeighborhoodPointsCollector();
    if (gaussian) {
      this.lambda = parameter;
      this.power = 0;
      idwVariation = IdwVariation.GaussianKernel;
    } else {
      idwVariation = IdwVariation.Power;
      this.power = parameter;
      this.lambda = 0;
    }
  }

  /**
   * Used by an application to reset the state data within the interpolator
   * when the content of the TIN may have changed. Resetting the state data
   * unnecessarily may result in a performance reduction when processing
   * a large number of interpolations, but is otherwise harmless.
   */
  @Override
  public void resetForChangeToTin() {
    navigator.resetForChangeToTin();
    neighborhoodPoints.resetForChangeToTin();
  }

  /**
   * Perform inverse distance weighting interpolation.
   * <p>
   * This interpolation is not defined beyond the convex hull of the TIN
   * and this method will produce a Double.NaN if the specified coordinates
   * are exterior to the TIN.
   *
   * @param x the x coordinate for the interpolation point
   * @param y the y coordinate for the interpolation point
   * @param valuator a valid valuator for interpreting the z value of each
   * vertex or a null value to use the default.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a NaN.
   */
  @Override
  public double interpolate(double x, double y, IVertexValuator valuator) {

    IQuadEdge e = navigator.getNeighborEdge(x, y);

    if (e == null) {
      // this should happen only when TIN is not bootstrapped
      return Double.NaN;
    }

    // confirm that the query coordinates are inside the TIN
    Vertex v2 = e.getForward().getB();
    if (v2 == null) {
      // (x,y) is either on perimeter or outside the TIN.
      return Double.NaN;

    }

    List<Vertex> neighbors
      = neighborhoodPoints.collectNeighboringVertices(x, y, maxDepth, minPoints);

    IVertexValuator val;
    if (valuator == null) {
      val = defaultValuator;
    } else {
      val = valuator;
    }

    double wSum = 0;
    double wzSum = 0;
    double sSum = 0;
    for (Vertex v : neighbors) {
      double z = val.value(v);
      double dx = v.getX() - x;
      double dy = v.getY() - y;
      double s2 = dx * dx + dy * dy;
      double s = Math.sqrt(s2);
      sSum += s;
      double w;
      switch (idwVariation) {
        case Shepard:
          if (s2 < this.vertexTolerance2) {
            // the distance is so small, we call it a match
            return z;
          }
          w = 1 / s2;
          break;
        case Power:
          if (s2 < this.vertexTolerance2) {
            // the distance is so small, we call it a match
            return z;
          }
          w = 1.0 / Math.pow(s2, power / 2);
          break;
        case GaussianKernel:
        default:
          w = Math.exp(-0.5 * s / lambda);
          break;
      }
      wSum += w;
      wzSum += z * w;
    }

    if (wSum < this.precisionThreshold) {
      // this should only happen if the neighbor point collector failed
      return Double.NaN;
    }

    nInterpolation++;
    this.sumVertexCount += neighbors.size();
    sumDist.add(sSum);

    return wzSum / wSum;
  }

  @Override
  public boolean isSurfaceNormalSupported() {
    return false;
  }

  /**
   * Not supported at this time.
   *
   * @return a zero-sized array.
   */
  @Override
  public double[] getSurfaceNormal() {
    return new double[0];
  }

  @Override
  public String getMethod() {
    if (idwVariation == IdwVariation.GaussianKernel) {
      return String.format("IDW (Gaussian: %4.2f)", lambda);
    }
    return String.format("IDW (power: %4.2f)", power);
  }

  /**
   * Estimates a nominal bandwidth for the Gaussian kernal method of
   * interpolation
   * using the mean length of the distances between samples
   *
   * @param pointSpacing a positive value
   * @return if successful, a positive value; otherwise, Double&#46;NaN
   */
  static public double estimateNominalBandwidth(double pointSpacing) {
    if (pointSpacing <= 0) {
      return Double.NaN;
    }
    return -Math.log(1 / 12.0) * pointSpacing / 2;
  }

  /**
   * Computes the average sample spacing.
   * <p>
   * In many cases, the sample spacing of the perimeter edges of a TIN
   * is much larger than the mean sample spacing for the interior.
   * So if the TIN contains more than 3 points, the perimeter edges are
   * not included in the tabulation.
   *
   * @param tin a valid instance
   * @return if the TIN is valid, a positive value; otherwise Double&#46;NaN
   */
  public static double computeAverageSampleSpacing(IIncrementalTin tin) {
    KahanSummation kahanSum = new KahanSummation();
    int nSum = 0;
    for (IQuadEdge e : tin.edges()) {
      kahanSum.add(e.getLength());
      nSum++;
    }
    if (nSum == 0) {
      // only occurs if the tin has not been properly bootstrapped
      return Double.NaN;
    }
    if (nSum == 3) {
      // there are only 3 edges, so we don't need to remove the perimeter
      return kahanSum.getMean();
    }

    // remove the perimeter edges
    for (IQuadEdge e : tin.getPerimeter()) {
      kahanSum.add(-e.getLength());
      nSum--;
    }
    return kahanSum.getSum() / nSum;
  }

  /**
   * Prints diagnostic information about sample sizes and spacing
   * used for interpolation
   *
   * @param ps a valid instance (such as System&#46;out).
   */
  public void printDiagnostics(PrintStream ps) {
    long n = 1;
    if (nInterpolation > 0) {
      n = nInterpolation;
    }
    long nV = 1;
    if (sumVertexCount > 0) {
      nV = sumVertexCount;
    }
    ps.format("Interpolations       %10d%n", nInterpolation);
    ps.format("Avg. sample size:    %12.1f%n", (double) sumVertexCount / n);
    ps.format("Avg. sample spacing: %15.4f%n", sumDist.getSum() / nV);
  }

  @Override
  public String toString() {
    if (idwVariation == IdwVariation.GaussianKernel) {
      return String.format("IDW (Gaussian: %4.2f)", lambda);
    } else {
      return String.format("IDW (Power: %3.1f)", power);
    }
  }

}
