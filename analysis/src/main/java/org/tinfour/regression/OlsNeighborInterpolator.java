/* --------------------------------------------------------------------
 * Copyright (C) 2024  Gary W. Lucas.
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
 * 02/2024  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.regression;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.linear.RealMatrix;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.IInterpolatorOverTin;
import org.tinfour.interpolation.IVertexValuator;
import org.tinfour.interpolation.NaturalNeighborElements;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.interpolation.VertexValuatorDefault;
import org.tinfour.standard.IncrementalTin;

/**
 * Provides an implementation of a Tinfour interpolator based on
 * the method of Ordinary Least Squares (OLS).
 * Samples are selected by inspecting the Triangulated Irregular Network (TIN)
 * in the vicinity of the query coordinates. All samples are treated as having
 * a uniform weight and variance.
 * <p>
 * <strong>Notes on Usage</strong>
 * <p>
 * Internally, this class depends on an instance of the OlsSurface class to
 * perform regression operations. Developers should consult the Javadoc for
 * OlsSurface to see notes on the usage and performance considerations.
 * In particular, developers are encouraged to review the treatment of the
 * regression parameters (the "beta" parameters) and the offset coordinate
 * system used by the interpolator.
 */
public class OlsNeighborInterpolator implements IInterpolatorOverTin {

  /**
   * Indicates the method that is used to select points for interpolation
   */
  public enum SelectionMethod {
    /**
     * Points are in the neighborhood of the query coordinates
     */
    Neighborhood,
    /**
     * One point in the selection is coincident with the query coordinates
     */
    CoincidentVertex,
    /**
     * Points were selected using the cross-validation operation
     */
    CrossValidate;
  }

  private final NaturalNeighborInterpolator nni;
  private final IncrementalTin workTin;
  private final NaturalNeighborInterpolator workNni;
  private final IIncrementalTinNavigator navigator;
  private final Thresholds thresholds;
  private final OlsSurface olsSurface = new OlsSurface();

  // results
  private double[] beta;
  private double sigma;
  private double sigma2;
  private double zResult;
  private double xQuery;
  private double yQuery;
  private SelectionMethod selectionMethod;
  private boolean modelFallbackEnabled;
  private PrintStream reportSamples;

  private final SurfaceModel surfaceModel;
  private final List<Vertex>samplesFromRegression = new ArrayList<>();
  private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

  /**
   * Construct an interpolator using the specified Delaunay triangulation (TIN)
   * and surface model.
   *
   * @param tin a valid instance of a Tinfour incremental TIN
   * @param model a valid instance of the surface model enumeration
   */
  public OlsNeighborInterpolator(IIncrementalTin tin, SurfaceModel model) {
    if (tin == null) {
      throw new IllegalArgumentException("Null TIN instance passed to constructor");
    }
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException("Unitialized TIN passed to constructor");
    }
    surfaceModel = model;
    nni = new NaturalNeighborInterpolator(tin);
    thresholds = tin.getThresholds();
    double spacing = thresholds.getNominalPointSpacing();
    workTin = new IncrementalTin(spacing / 10.0);
    workNni = new NaturalNeighborInterpolator(workTin);
    navigator = tin.getNavigator();
  }

  /**
   * Construct an interpolator using the specified Delaunay triangulation (TIN)
   * and the default quadratic surface model.
   *
   * @param tin a valid instance of a Tinfour incremental TIN
   */
  public OlsNeighborInterpolator(IIncrementalTin tin) {
    if (tin == null) {
      throw new IllegalArgumentException("Null TIN instance passed to constructor");
    }
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException("Unitialized TIN passed to constructor");
    }
    surfaceModel = SurfaceModel.Quadratic;
    nni = new NaturalNeighborInterpolator(tin);
    thresholds = tin.getThresholds();
    double spacing = thresholds.getNominalPointSpacing();
    workTin = new IncrementalTin(spacing / 10.0);
    workNni = new NaturalNeighborInterpolator(workTin);
    navigator = tin.getNavigator();
  }

  /**
   * Sets the optional fallback action allowing the interpolator to
   * use a model that features fewer variables when it cannot obtain
   * a sufficient number of neighboring samples for the specified model.
   *
   * @param enabled true if fallback is enabled; otherwise, false.
   */
  public void setModelFallbackEnabled(boolean enabled) {
    modelFallbackEnabled = enabled;
  }

  @Override
  public double interpolate(final double x, final double y, IVertexValuator valuator) {
    clearResults();
    xQuery = x;
    yQuery = y;
    IQuadEdge edgeQuery = this.getRelatedEdge(x, y);
    if (edgeQuery == null) {
      return Double.NaN;
    }
    return computeRegression(x, y, valuator, edgeQuery, false, false);
  }

  /**
   * Perform interpolation using the specified valuator and optionally
   * computing extended statistics.The extended statistics include
   * the "hat" matrix, R-student values, etc. If an application does
   * not require these values, it may save processing time by not computing
   * them.
   *
   * @param x the x coordinate for the interpolation point
   * @param y the y coordinate for the interpolation point
   * @param valuator a valid valuator for interpreting the z value of each
   * vertex or a null value to use the default.
   * @param computeExtendedStatistics compute the extended statistics such
   * as the "hat" matrix and R-Student values.
   * @return if the interpolation is successful, a valid floating point
   * value; otherwise, a NaN.
   */
  public double interpolate(final double x, final double y, IVertexValuator valuator, boolean computeExtendedStatistics) {
    clearResults();
    xQuery = x;
    yQuery = y;
    IQuadEdge edgeQuery = this.getRelatedEdge(x, y);
    if (edgeQuery == null) {
      return Double.NaN;
    }
    return computeRegression(x, y, valuator, edgeQuery, false, computeExtendedStatistics);
  }



  private double computeRegression(final double x, final double y,
    IVertexValuator valuator, IQuadEdge edgeQuery,
    boolean crossValidate, boolean computeExtendedStatistics) {
    // in the logic below, we access the Vertex x and y coordinates directly
    // but we use the getZ() method to get the z value.  Some vertices
    // may actually be VertexMergerGroup instances and so the Z value must
    // be selected according to whatever rules were configured for the TIN.
    // Also, all coordinates are adjusted with an offset (-x, -y) so that the
    // interpolation point is at the origin. This adjustment is made to
    // compensate for the fact that map-projected coordinates for adjacent
    // vertices often have very large coordinates (in the millions)
    // and that the products of pairs of such values would be large enough
    // to wash out the precision.
    IVertexValuator vq = valuator;
    if (vq == null) {
      vq = defaultValuator;
    }

    Vertex A = edgeQuery.getA();
    double distance = A.getDistance(x, y);

    int k = 0;
    double[][] samples;

    if (distance > thresholds.getVertexTolerance() * 8) {
      selectionMethod = SelectionMethod.Neighborhood;
      NaturalNeighborElements innerElements = nni.getNaturalNeighborElements(x, y);
      if (innerElements == null) {
        return Double.NaN;
      }
      List<IQuadEdge> envelope = nni.getBowyerWatsonEnvelope(x, y);
      NaturalNeighborElements outerElements = getAdjacentElements(envelope);
      if (outerElements == null) {
        return Double.NaN;
      }

      int n = innerElements.getElementCount() + outerElements.getElementCount();
      samples = new double[n][3];
      Vertex[] vArray = innerElements.getNaturalNeighbors();
      for (int i = 0; i < vArray.length; i++) {
        samplesFromRegression.add(vArray[i]);
        samples[k][0] = vArray[i].getX() - x;
        samples[k][1] = vArray[i].getY() - y;
        samples[k][2] = vq.value(vArray[i]);
        k++;
      }
      vArray = outerElements.getNaturalNeighbors();
      for (int i = 0; i < vArray.length; i++) {
        samplesFromRegression.add(vArray[i]);
        samples[k][0] = vArray[i].getX() - x;
        samples[k][1] = vArray[i].getY() - y;
        samples[k][2] = vq.value(vArray[i]);
        k++;
      }
    } else {
      IQuadEdge edge0 = edgeQuery;
      List<IQuadEdge> envelope = new ArrayList<>();
      List<Vertex> innerList = new ArrayList<>();
      for (IQuadEdge e : edge0.pinwheel()) {
        envelope.add(e.getForward());
        if (e.getB() == null) {
          return Double.NaN;
        }
        innerList.add(e.getB());
      }

      workTin.add(innerList, null);
      if (!workTin.isBootstrapped()) {
        workTin.clear();
        return Double.NaN;
      }
      workNni.resetForChangeToTin();
      NaturalNeighborElements innerElements = workNni.getNaturalNeighborElements(x, y);
      workTin.clear();
      if (innerElements == null) {
        return Double.NaN;
      }

      NaturalNeighborElements outerElements = getAdjacentElements(envelope);
      if (outerElements == null) {
        return Double.NaN;
      }

      int n = innerElements.getElementCount() + outerElements.getElementCount() + 1;
      samples = new double[n][3];

      Vertex[] vArray = innerElements.getNaturalNeighbors();

      k = 0;
      if (crossValidate) {
        selectionMethod = SelectionMethod.CrossValidate;
      } else {
        selectionMethod = SelectionMethod.CoincidentVertex;
        Vertex C = edgeQuery.getA();
        samples[0][0] = 0;
        samples[0][1] = 0;
        samples[0][2] = vq.value(C);
        k = 1;
        samplesFromRegression.add(C);
      }
      for (int i = 0; i < vArray.length; i++) {
         samplesFromRegression.add(vArray[i]);
        samples[k][0] = vArray[i].getX() - x;
        samples[k][1] = vArray[i].getY() - y;
        samples[k][2] = vq.value(vArray[i]);
        k++;
      }
      vArray = outerElements.getNaturalNeighbors();
      for (int i = 0; i < vArray.length; i++) {
          samplesFromRegression.add(vArray[i]);
        samples[k][0] = vArray[i].getX() - x;
        samples[k][1] = vArray[i].getY() - y;
        samples[k][2] = vq.value(vArray[i]);
        k++;
      }
    }

    if (reportSamples != null) {
      writeSamplesToPrintStream(k, samples);
      reportSamples = null;
    }

    // A computation is defined only if the number of samples, k, is greater
    // than the number of coefficients to be produced by the regression.
    // If the fallback-option is enabled, the pickModel(k) method will
    // return a lower-order model when there are insufficient samples
    // to support the use of the specified model.
    SurfaceModel model = pickModel(k);
    if (k > model.getCoefficientCount()) {
      zResult = olsSurface.computeRegression(model, 0, 0, k, samples, computeExtendedStatistics);
      if (Double.isFinite(zResult)) {
        beta = olsSurface.getParameters();
        sigma2 = olsSurface.getVariance();
        sigma = Math.sqrt(sigma2);
        return zResult;
      }
    }
    return Double.NaN;
  }

  @Override
  public boolean isSurfaceNormalSupported() {
    return true;
  }

  @Override
  public double[] getSurfaceNormal() {
    if (Double.isFinite(zResult)) {
      // the normal is computed as (1, 0, x) cross (0, 1, y)
      // to give  (-x, -y, 1.0), and then normalized
      double x = beta[1];
      double y = beta[2];
      double s = Math.sqrt(x * x + y * y + 1.0);
      return new double[]{-x / s, -y / s, 1.0 / s};
    }
    return new double[0];
  }

  @Override
  public String getMethod() {
    return "OLS Neighbor Interpolation";
  }

  @Override
  public void resetForChangeToTin() {
    navigator.resetForChangeToTin();
    nni.resetForChangeToTin();
  }

  /**
   * Compute the parameters that would be estimated at the position of the
   * vertex if it were not incorporated into the Delaunay triangulation.
   *
   * @param v a valid reference to a vertex instance currently incorporated
   * into the Delaunay triangulation.
   * @return a valid floating-point value if the cross validation is successful;
   * otherwise, NaN.
   */
  public double crossValidate(Vertex v) {
    clearResults();
    xQuery = v.getX();
    yQuery = v.getY();
    double x = v.getX();
    double y = v.getY();
    IQuadEdge edgeQuery = this.getRelatedEdge(x, y);
    if (edgeQuery == null) {
      return Double.NaN;
    }
    Vertex A = edgeQuery.getA();
    if (!A.equals(v)) {
      // did not match a vertex in the TIN with the input
      return Double.NaN;
    }
    return this.computeRegression(x, y, null, edgeQuery, true, true);
  }

  /**
   * Get the coordinates used for the most recent interpolation
   *
   * @return an array of dimension 3 containing the x and y
   * values used for the most recently completed interpolation
   * and the z value computed as a result.
   */
  public double[] getInterpolationCoordinates() {
    double[] p = new double[3];
    p[0] = xQuery;
    p[1] = yQuery;
    p[2] = zResult;
    return p;
  }

  private void clearResults() {
    beta = null;
    sigma = Double.NaN;
    sigma2 = Double.NaN;
    zResult = Double.NaN;
    selectionMethod = SelectionMethod.Neighborhood;
    samplesFromRegression.clear();
  }

  private IQuadEdge getRelatedEdge(double x, double y) {
    IQuadEdge e = navigator.getNeighborEdge(x, y);
    if (e == null) {
      return null;
    }
    Vertex A = e.getA();
    Vertex B = e.getB();
    double a2 = A.getDistanceSq(x, y);
    double b2 = B.getDistanceSq(x, y);
    if (b2 < a2) {
      IQuadEdge f = e.getForward();
      Vertex C = f.getB();
      if (C == null) {
        return null;
      }
      double c2 = C.getDistanceSq(x, y);
      if (c2 < b2) {
        return f.getForward();
      } else {
        return f;
      }
    } else {
      IQuadEdge r = e.getReverse();
      Vertex C = r.getA();
      if (C == null) {
        return null;
      }
      double c2 = C.getDistanceSq(x, y);
      if (c2 < a2) {
        return r;
      } else {
        return e;
      }
    }
  }

  private NaturalNeighborElements getAdjacentElements(List<IQuadEdge> envelope) {
    int n = envelope.size();
    if (n < 3) {
      return null;
    }
    if (reportSamples != null) {
      reportSamples.println("Envelope");
      for (IQuadEdge q : envelope) {
        reportSamples.println(q.toString());
      }
      reportSamples.println("\n\n");
    }

    List<Vertex> exList = new ArrayList<>();
    for (IQuadEdge q : envelope) {
      exList.add(q.getA());
    }

    List<Vertex> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      IQuadEdge e0 = envelope.get(i);
      IQuadEdge e1 = envelope.get((i + 1) % n);
      IQuadEdge p = e0.getReverseFromDual().getDual();
      IQuadEdge haltEdge = e1.getForwardFromDual(); // the halting edge
      //    We add the B vertex from all edges leading up to the halting edge.
      // In the special case where the initial edge p equals the halting
      // edge, we will not add the vertex.  But it should be added on
      // the next segment of the envelope.
      //    If we encounter a null B, it means that the sweep led out
      // past the convex hull of the TIN.  This is benign and we simply
      // skip the null vertex.

      while (!p.equals(haltEdge)) {
        Vertex B = p.getB();
        if (B != null && !exList.contains(B) && !list.contains(B)) {
          list.add(B);
          if (this.reportSamples != null) {
            reportSamples.println(p.toString());
          }
        }
        p = p.getDualFromReverse();
      }
    }

    NaturalNeighborElements nne = null;
    workTin.clear(); // not needed once everything else is correctly implemented
    workTin.add(list, null);
    if (workTin.isBootstrapped()) {
      workNni.resetForChangeToTin();
      nne = workNni.getNaturalNeighborElements(xQuery, yQuery);
    }
    workTin.clear();

    if (nne == null && list.size() >= 3) {
      Vertex[] neighbors = list.toArray(new Vertex[0]);
      return new NaturalNeighborElements(xQuery, yQuery, new double[list.size()], neighbors, 1.0);
    }
    return nne;
  }

 /**
   * Gets a safe copy of the computed polynomial coefficients from the
   * regression (the "beta" parameters). These coefficients can be used
   * for interpolation or surface modeling purposes. Developers
   * are reminded that the interpolation is based on treating the
   * query point as the origin, so x and y coordinates should be adjusted
   * accordingly when used in calculations based on the return values of this
   * method.
   *
   * @return a if the last regression computation was successful, a valid array
   * of coefficients for the selected surface model; otherwise, a zero-length
   * array.
   */
  public double[] getBeta() {
        if (beta == null) {
      return new double[0];
    }
    return Arrays.copyOf(beta, beta.length);
  }

  /**
   * Get the estimated variance at the most recent interpolation or
   * cross-validation coordinates.
   *
   * @return if successful, a valid floating-point value; otherwise NaN.
   */
  public double getVariance() {
    return sigma2;
  }

  /**
   * Get the estimated standard deviation at the most recent interpolation
   * or cross-validation coordinates.
   *
   * @return if successful, a valid floating-point value; otherwise NaN.
   */
  public double getSigma() {
    return sigma;
  }

  /**
   * Get the estimated z value at the most recent interpolation or
   * cross-validation coordinates.
   *
   * @return if successful, a valid floating-point value; otherwise NaN
   */
  public double getResultZ() {
    return zResult;
  }

  private SurfaceModel pickModel(int n) {
    int k = surfaceModel.getCoefficientCount();
    if (n < k + 1 && modelFallbackEnabled) {
      return SurfaceModel.Planar;
    }
    return surfaceModel;
  }

  /**
   * Get the half span (single side) of the confidence interval
   * for the most recent ordinary least squares analysis. The full confidence
   * interval is may be computed by subtracting and adding this result
   * from the result (mean) value obtained from the main interpolation.
   * For example, given the input population fraction value of 0.95,
   * 95 percent of the observed population would be expected to be
   * in the range mean &plusmn; result (half span).
   *
   * @param populationFraction a value in the range 0 &lt; factor &lt; 1.
   * @return a positive floating-point value
   */
  public double getConfidenceIntervalHalfSpan(double populationFraction) {
    if (!(0 < populationFraction && populationFraction < 1)) {
      throw new IllegalArgumentException("Population fraction is not in the range (0,1): " + populationFraction);
    }
    return olsSurface.getConfidenceIntervalHalfSpan(populationFraction);
  }

  /**
   * Get the half span (single side) of the prediction interval
   * for the most recent ordinary least squares analysis.The full prediction
   * interval is may be computed by subtracting and adding this result
   * from the result (mean) value obtained from the main interpolation. For
   * example, given the input population fraction value of 0.95,
   * 95 percent of the predicted values would be expected to be
   * in the range mean &plusmn; result (half span).
   *
   * @param populationFraction a value in the range 0 &lt; factor &lt; 1.
   * @return a positive floating-point value
   */
  public double getPredictonIntervalHalfSpan(double populationFraction) {
    if (!(0 < populationFraction && populationFraction < 1)) {
      throw new IllegalArgumentException("Population fraction is not in the range (0,1): " + populationFraction);
    }
    return olsSurface.getPredictonIntervalHalfSpan(populationFraction);
  }

  /**
   * Set a print stream to be used as output for writing the samples
   * to a file or other I/O device. Intended for diagnostic purposes.
   * The output is used once and then cleared at the end of an operation.
   *
   * @param ps a valid print stream or a null to disable this feature
   */
  public void setSampleReportStream(PrintStream ps) {
    reportSamples = ps;
  }

  private void writeSamplesToPrintStream(int k, double[][] samples) {
    reportSamples.println("x,y,z");
    for (int i = 0; i < k; i++) {
      reportSamples.format("%9.8f\t%9.8f\t%9.8f %n",
        samples[i][0], samples[i][1], samples[i][2]);
    }
  }

  /**
   * Gets the method that was used to select neighbor points for the
   * most recent interpolation operation.
   *
   * @return a valid instance.
   */
  public SelectionMethod getSelectionMethod() {
    return selectionMethod;
  }

  /**
   * Gets the "hat" matrix from the most recent computation, if enabled.
   * The computation of a HAT matrix is optional.
   * If the extended-statistics option was not enabled,
   * then this method will return a null.
   *
   * @return if enabled, a valid instance; otherwise, a null.
   */
  public RealMatrix getHatMatrix() {
    return olsSurface.getHatMatrix();
  }

  /**
   * Get the array of R-Student values corresponding to the input
   * sample data.  The R-Student values is often a useful tool in detecting
   * anomalous points in the input same set.
   * The computation of the R-Student values is optional.
   * If the extended-statistics option was not enabled,
   * then this method will return a null.
   * @return if enabled, a valid array; otherwise, a nukll.
   */
  public double[] getRStudentValues() {
    return olsSurface.rStudentValues;
  }

  /**
   * Gets the number of degrees of freedom for the most recent computation
   * based on a ordinary least squares treatment
   *
   * @return if the most recent computation was successful, a positive
   * integer; otherwise, a value &lt;= 0.
   */
  public int getDegreesOfFreedom() {
    return olsSurface.nDegOfFreedom;
  }

  /**
   * Gets a list containing the vertices used in the most recent regression
   * operation.  The index of the vertices within the list will correspond
   * to the corresponding positions in the hat matrix and the R-Student Values
   * array (if they were computed).
   * @return a valid, potentially empty list.
   */
  public List<Vertex>getVertices(){
    ArrayList<Vertex>list = new ArrayList();
    list.addAll(samplesFromRegression);
    return list;
  }
}
