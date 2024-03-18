/*
 * Copyright 2024 Gary W. Lucas.
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
 * 01/2024   G. Lucas  Adapted from the original SurfaceGWR. Simplified
 *                     to use a uniform variance at all samples with
 *                     no covariance between samples.
 *
 * Notes:
 *    This class is written with the assumption that instances will be used
 * many times in a tight loop.  This approach is consistent with the
 * approach used in creating raster products.
 * -----------------------------------------------------------------------
 */
package org.tinfour.regression;

import java.util.Arrays;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Provides an implementation of an Ordinary Least Squares (OLS) polynomial
 * regression for the surface z = p(x, y). The input samples are assumed to
 * define points from a 3D Cartesian coordinate system. A small set of models
 * (cubic, quadratic, planar) are available for the surface of interest which
 * may
 * be elevation, bathymetry, temperature, or some other phenomenon.
 * <p>
 * <strong>Specialization: </strong>This class takes an approach
 * that differs from most regression implementations. It is optimized for
 * interpolation operations:
 * <ol>
 * <li>The input requires the specification of a "query point" for which
 * an interpolated value is computed</li>
 * <li>The design assumes that this class may be used in tight loops
 * for creating gridded data sets from scattered input data. Therefore
 * it prioritizes efficiency over flexibility.</li>
 * <li>Basic statistics are available (estimates of error at the query
 * point, "beta" parameters and parameter variance), but those requiring
 * extra computational overhead are treated as optional.</li>
 * </ol>
 * <p>
 * <strong>Special considerations regarding computed statistics: </strong>
 * Applications that use the computed statistics ("beta" parameters, "hat"
 * matrix, etc.)
 * must include logic for handling coordinate offsets that this class
 * applies to its input.
 * <p>
 * In some data sets, closely spaced sample points may feature
 * large magnitude coordinates. This consideration is especially true
 * for the horizontal (x,y) coordinates of geophysical data where it is
 * common to see data such as:
 * <pre>
 *       x            y            z
 *    605075.31    4891924.60    1610.51
 *    604074.19    4891924.54    1610.56
 *    605072.07    4892924.84    2610.90
 * </pre>
 * In the example above, significant differences in value are stored in the
 * low-order digits. In such cases, the limits of floating-point arithmetic
 * could lead to a loss of precision and inaccurate results
 * from the regression calculations. To avoid that issue, the input coordinates
 * are adjusted as follows:
 * <ul>
 * <li>The input query position is treated as the origin. An offset is applied
 * to
 * x and y coordinates.</li>
 * <li>The mean z value is computed and used as an offset to the vertical
 * coordinates.</li>
 * </ul>
 * <p>
 * For example, the following code snippet shows how an interpolated value
 * could be obtained at a specified position (xQuery, yQuery) using a planar
 * surface model. The beta parameters from the ordinary least squares
 * operation could be used to compute a value an an alternate position
 * (xTest, yTest), but the relevant offsets would need to be applied.
 * <pre>
 *  OlsSurface olsSurface = new OlsSurface();
 *
 *   double zInterpolated =
 *     olsSurface.computeRegression(
 *       SurfaceModel.Planar, xQuery, yQuery, nSamples, samples, true);
 *
 *   double []b     = olsSurface.getParameters();  // beta parameters
 *   double xOffset = olsSurface.getOffsetX(); //  will be xQuery
 *   double yOffset = olsSurface.getOffsetY(); //  will be yQuery
 *   double zOffset = olsSurface.getOffsetZ();
 *   zTest = b[0] + b[1]*(xTest-xOffset) + b[2]*(yTest-yOffset) + zOffset;
 * </pre>
 * This class provides a convenience method for estimating a value
 * at an alternate set of coordinates:
 * <pre>
 *   zTest = olsSurface.computeEstimatedValue(xTest, yTest);
 * </pre>
 * <p>
 * Given a set of sample points in the vicinity
 * of the point of interest,(x,y), the class solves for the coefficients
 * treating (x,y) as the origin. Thus algebraic simplifications result
 * in a case where the parameter b0 gives the surface height at point (x,y).
 * Furthermore, the ordering of coefficients is specified
 * for all models so that the coefficients b1 and b2 give the partial
 * derivatives of the surface when evaluated at (x,y). Parameter b1
 * is the partial derivative with respect to x, b2 with respect to y.
 * Applications requiring information about slope or surface normal may do so
 * by using this construct.
 * <p>
 * The calculations used to derive regression coefficients are adapted
 * from "Probability and Statistics for Engineers and Scientists (4th ed.)",
 * 1989 by Ronald E. Walpole and Raymond H. Myers, Macmillan Publishing Company,
 * New York City, NY Chapter 10, "Multiple Linear Regression". Walpole and
 * Myers provide an excellent introduction to the problem of multiple
 * linear regression, its general statistics (particularly the
 * prediction interval), and their use.
 * <p>
 * <Strong>Development Notes</strong><br>
 * The current implementation of this class supports a family of surface
 * models based on polynomials p(x, y) of order 3 or less. While this approach
 * is appropriate for the original intent of this class, modeling terrain,
 * there is no reason why the class cannot be adapted to support arbitrary
 * models.
 */
public class OlsSurface {

  private double xOffset;
  private double yOffset;
  private double zOffset;

  int nSamples;
  int nVariables; // number of independent or "explanatory" variables
  int nDegOfFreedom;
  double[] beta;

  double zEstimate;
  double sigma2;  // Residual standard variance (sigma squared)
  double sse;
  double ssr;
  double sst;
  double r2; // coefficient of multiple determination
  double standardErrorOfPrediction; // used for confidence interval
  double adjustedStandardErrorOfPrediction; // used for prediction interval
  double[] standardErrorOfParameters;

  RealMatrix hat;
  double[] rStudentValues;

  private SurfaceModel model;

  /**
   * Standard constructor.
   */
  public OlsSurface() {
    xOffset = Double.NaN;
    yOffset = Double.NaN;
    zOffset = Double.NaN;
    zEstimate = Double.NaN;
    sigma2 = Double.NaN;
    standardErrorOfPrediction = Double.NaN;
    nSamples = 0;
    nDegOfFreedom = 0;
    nVariables = 0;
    beta = null;
    standardErrorOfParameters = null;
    model = SurfaceModel.Quadratic;
  }

  /**
   * Computes the elevation for a point at the specified query
   * coordinates, by performing a multiple-linear regression using the
   * observed values.A number of statistics are simultaneously computed
   * and may be obtained using access methods.<p>
   *
   * @param model the model to be used for the regression
   * @param xQuery x coordinate of the query position
   * @param yQuery y coordinate of the query position
   * @param nSamples the number of sample points to be used for regression
   * @param samples an array of dimension [n][3] giving at least nSamples
   * points with the x, y, and z values for the regression.
   * @param computeExtendedStatistics indicates that operation should compute
   * additional statistics (the "hat" matrix, R-student values, etc&#46;)
   * @return if successful, the estimated value at the query coordinates;
   * otherwise, Double&#46;NaN.
   */
  @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
  public double computeRegression(
    SurfaceModel model,
    double xQuery,
    double yQuery,
    int nSamples,
    double[][] samples,
    boolean computeExtendedStatistics) {

    clear();

    this.model = model;
    if (nSamples < model.getCoefficientCount()) {
      throw new IllegalArgumentException(
        "Insufficient number of samples for regression: found "
        + nSamples + ", need " + model.getCoefficientCount());
    }

    this.nSamples = nSamples;
    this.xOffset = xQuery;
    this.yOffset = yQuery;

    double[][] g;
    double[][] input;

    // In testing, we observed the solver failing when the magnitude
    // of the z coordinates was larger and their range-of-values small.
    // So in constructing the input for the computation, this code
    // will subtract the mean z value, compute the beta parmeters and then add
    // the zMean to beta[0].  This approach works because the code also adjusts
    // the x,y coordinates so that the query point is treated as the origin.
    double zSum = 0;
    for (int i = 0; i < nSamples; i++) {
      zSum += samples[i][2];
    }
    double zMean = zSum / nSamples;

    // TO DO:   There may be an advantage to using simple variables
    //          to store the running sums inside the loops below
    //          rather than storing them in the NxN array of values.
    //          Doing so migh reduce overhead for array indexing.
    //
    // In the following expressions, the layout of the regression
    // variables is organized to simplify the computation of quantities
    // such as slope and curvature.  Recall that the samples are always
    // mapped so that the query position (xQUery, yQuery) is treated
    // as the origin. Therefore, then the derivatives are evaluated at
    // the query position (adjusted origin), many terms drop out and
    // the following relationships apply
    //     Z   = b[0]
    //     Zx  = b[1]     //   partial of z(x,y) with respect to x, etc.
    //     Zy  = b[2]
    //     Zxx = 2*b[3]   //   2nd partial of z(x,y) with respect to x, etc.
    //     Zyy = 2*b[4]
    //     Zxy = b[5]
    if (model == SurfaceModel.CubicWithCrossTerms) {
      nVariables = 9;
      g = new double[10][1];
      input = new double[10][10];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;
        double xy = x * y;

        input[0][1] += x;
        input[0][2] += y;
        input[0][3] += x2;
        input[0][4] += y2;
        input[0][5] += xy;
        input[0][6] += x2 * y;
        input[0][7] += x * y2;
        input[0][8] += x3;
        input[0][9] += y3;

        input[1][1] += x2;
        input[1][2] += xy;
        input[1][3] += x3;
        input[1][4] += x * y2;
        input[1][5] += x2 * y;
        input[1][6] += x * x2 * y;
        input[1][7] += x * x * y2;
        input[1][8] += x * x3;
        input[1][9] += x * y3;

        input[2][2] += y2;
        input[2][3] += x2 * y;
        input[2][4] += y3;
        input[2][5] += x * y2;
        input[2][6] += y * x2 * y;
        input[2][7] += y * x * y2;
        input[2][8] += y * x3;
        input[2][9] += y * y3;

        input[3][3] += x4;
        input[3][4] += x2 * y2;
        input[3][5] += x3 * y;
        input[3][6] += x2 * x2 * y;
        input[3][7] += x2 * x * y2;
        input[3][8] += x2 * x3;
        input[3][9] += x2 * y3;

        input[4][4] += y4;
        input[4][5] += x * y3;
        input[4][6] += y2 * x2 * y;
        input[4][7] += y2 * x * y2;
        input[4][8] += y2 * x3;
        input[4][9] += y2 * y3;

        input[5][5] += x2 * y2;
        input[5][6] += xy * x2 * y;
        input[5][7] += xy * x * y2;
        input[5][8] += xy * x3;
        input[5][9] += xy * y3;

        input[6][6] += x2 * y * x2 * y;
        input[6][7] += x2 * y * x * y2;
        input[6][8] += x2 * y * x3;
        input[6][9] += x2 * y * y3;

        input[7][7] += y2 * x * x * y2;
        input[7][8] += y2 * x * x3;
        input[7][9] += y2 * x * y3;

        input[8][8] += x3 * x3;
        input[8][9] += x3 * y3;

        input[9][9] += y3 * y3;

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
        g[3][0] += x2 * z;
        g[4][0] += y2 * z;
        g[5][0] += xy * z;
        g[6][0] += x2 * y * z;
        g[7][0] += x * y2 * z;
        g[8][0] += x3 * z;
        g[9][0] += y3 * z;
      }

      // the input for a least-squares fit is a real-symmetric
      // matrix.  So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];

      input[2][0] = input[0][2];
      input[2][1] = input[1][2];

      input[3][0] = input[0][3];
      input[3][1] = input[1][3];
      input[3][2] = input[2][3];

      input[4][0] = input[0][4];
      input[4][1] = input[1][4];
      input[4][2] = input[2][4];
      input[4][3] = input[3][4];

      input[5][0] = input[0][5];
      input[5][1] = input[1][5];
      input[5][2] = input[2][5];
      input[5][3] = input[3][5];
      input[5][4] = input[4][5];

      input[6][0] = input[0][6];
      input[6][1] = input[1][6];
      input[6][2] = input[2][6];
      input[6][3] = input[3][6];
      input[6][4] = input[4][6];
      input[6][5] = input[5][6];

      input[7][0] = input[0][7];
      input[7][1] = input[1][7];
      input[7][2] = input[2][7];
      input[7][3] = input[3][7];
      input[7][4] = input[4][7];
      input[7][5] = input[5][7];
      input[7][6] = input[6][7];

      input[8][0] = input[0][8];
      input[8][1] = input[1][8];
      input[8][2] = input[2][8];
      input[8][3] = input[3][8];
      input[8][4] = input[4][8];
      input[8][5] = input[5][8];
      input[8][6] = input[6][8];
      input[8][7] = input[7][8];

      input[9][0] = input[0][9];
      input[9][1] = input[1][9];
      input[9][2] = input[2][9];
      input[9][3] = input[3][9];
      input[9][4] = input[4][9];
      input[9][5] = input[5][9];
      input[9][6] = input[6][9];
      input[9][7] = input[7][9];
      input[9][8] = input[8][9];

    } else if (model == SurfaceModel.QuadraticWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2+b5*x*y
      nVariables = 5;
      g = new double[6][1];
      input = new double[6][6];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;
        double xy = x * y;

        input[0][1] += x;
        input[0][2] += y;
        input[0][3] += x2;
        input[0][4] += y2;
        input[0][5] += xy;

        input[1][1] += x2;
        input[1][2] += xy;
        input[1][3] += x * x2;
        input[1][4] += x * y2;
        input[1][5] += x * xy;

        input[2][2] += y2;
        input[2][3] += x2 * y;
        input[2][4] += y3;
        input[2][5] += x * y2;

        input[3][3] += x4;
        input[3][4] += x2 * y2;
        input[3][5] += x3 * y;

        input[4][4] += y4;
        input[4][5] += x * y3;

        input[5][5] += x2 * y2;

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
        g[3][0] += x2 * z;
        g[4][0] += y2 * z;
        g[5][0] += xy * z;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];

      input[2][0] = input[0][2];
      input[2][1] = input[1][2];

      input[3][0] = input[0][3];
      input[3][1] = input[1][3];
      input[3][2] = input[2][3];

      input[4][0] = input[0][4];
      input[4][1] = input[1][4];
      input[4][2] = input[2][4];
      input[4][3] = input[3][4];

      input[5][0] = input[0][5];
      input[5][1] = input[1][5];
      input[5][2] = input[2][5];
      input[5][3] = input[3][5];
      input[5][4] = input[4][5];
    } else if (model == SurfaceModel.Quadratic) {
      //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2

      nVariables = 4;
      g = new double[5][1];
      input = new double[5][5];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;

        input[0][1] += x;
        input[0][2] += y;
        input[0][3] += x2;
        input[0][4] += y2;

        input[1][1] += x2; // x*x
        input[1][2] += x * y;
        input[1][3] += x * x2;
        input[1][4] += x * y2;

        input[2][2] += y2; // y*y
        input[2][3] += y * x2;
        input[2][4] += y * y2;

        input[3][3] += x2 * x2;
        input[3][4] += x2 * y2;

        input[4][4] += y2 * y2;

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
        g[3][0] += x2 * z;
        g[4][0] += y2 * z;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];

      input[2][0] = input[0][2];
      input[2][1] = input[1][2];

      input[3][0] = input[0][3];
      input[3][1] = input[1][3];
      input[3][2] = input[2][3];

      input[4][0] = input[0][4];
      input[4][1] = input[1][4];
      input[4][2] = input[2][4];
      input[4][3] = input[3][4];

    } else if (model == SurfaceModel.Planar) {
      //  z(x, y) = b0 + b1*x + b2*y;
      nVariables = 2;
      g = new double[3][1];
      input = new double[3][3];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;

        input[0][1] += x;
        input[0][2] += y;

        input[1][1] += x2;
        input[1][2] += x * y;

        input[2][2] += y2;

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];
      input[2][0] = input[0][2];
      input[2][1] = input[1][2];
    } else if (model == SurfaceModel.PlanarWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x*y;
      nVariables = 3;
      g = new double[4][1];
      input = new double[4][4];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;

        input[0][1] += x;
        input[0][2] += y;
        input[0][3] += xy;

        input[1][1] += x2;    // x*x
        input[1][2] += xy;    // y*x
        input[1][3] += xy * x;  // xy*x

        input[2][2] += y2;    // y*y
        input[2][3] += xy * y;  // xy*y

        input[3][3] += xy * xy;  //xy*xy

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
        g[3][0] += xy * z;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];

      input[2][0] = input[0][2];
      input[2][1] = input[1][2];

      input[3][0] = input[0][3];
      input[3][1] = input[1][3];
      input[3][2] = input[2][3];
    } else { // if(model==SurfaceModel.Cubic){
      nVariables = 6;
      g = new double[7][1];
      input = new double[7][7];
      input[0][0] = nSamples;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2] - zMean;
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;

        input[0][1] += x;
        input[0][2] += y;
        input[0][3] += x2;
        input[0][4] += y2;
        input[0][5] += x3;
        input[0][6] += y3;

        input[1][1] += x2;
        input[1][2] += xy;
        input[1][3] += x3;
        input[1][4] += y2 * x;
        input[1][5] += x4;
        input[1][6] += y3 * x;

        input[2][2] += y2;
        input[2][3] += x2 * y;
        input[2][4] += y3;
        input[2][5] += x3 * y;
        input[2][6] += y4;

        input[3][3] += x4;
        input[3][4] += y2 * x2;
        input[3][5] += x3 * x2;  // x5
        input[3][6] += y3 * x2;

        input[4][4] += y4;
        input[4][5] += x3 * y2;
        input[4][6] += y3 * y2;  // y5

        input[5][5] += x3 * x3;  // x6
        input[5][6] += y3 * x3;

        input[6][6] += y3 * y3; // y6

        g[0][0] += z;
        g[1][0] += x * z;
        g[2][0] += y * z;
        g[3][0] += x2 * z;
        g[4][0] += y2 * z;
        g[5][0] += x3 * z;
        g[6][0] += y3 * z;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];

      input[2][0] = input[0][2];
      input[2][1] = input[1][2];

      input[3][0] = input[0][3];
      input[3][1] = input[1][3];
      input[3][2] = input[2][3];

      input[4][0] = input[0][4];
      input[4][1] = input[1][4];
      input[4][2] = input[2][4];
      input[4][3] = input[3][4];

      input[5][0] = input[0][5];
      input[5][1] = input[1][5];
      input[5][2] = input[2][5];
      input[5][3] = input[3][5];
      input[5][4] = input[4][5];

      input[6][0] = input[0][6];
      input[6][1] = input[1][6];
      input[6][2] = input[2][6];
      input[6][3] = input[3][6];
      input[6][4] = input[4][6];
      input[6][5] = input[5][6];
    }

    nDegOfFreedom = nSamples - nVariables - 1;
    if (nDegOfFreedom < 1) {
      throw new IllegalArgumentException(
        "Inadequate sample size " + nSamples
        + " for " + nDegOfFreedom + " degrees of freedom");
    }

    RealMatrix matrixG = new BlockRealMatrix(g);
    RealMatrix matrixA = new BlockRealMatrix(input);

    // The Apache Commons Math MultipleLinearRegression implementation
    // uses the QRDecomposition, and we follow their lead.
    // When I first implemented this, I thought that the input matrix would be
    // a real symmetric and positive-definite matrix. If that were the case,
    // it could be solved using a Cholesky decomposition.  But, even
    // when evaluating ordinary least squares, I ran into numeric
    // issues that led to the matrix violating the positive-definite criterion.
    try {
      QRDecomposition cd = new QRDecomposition(matrixA);
      DecompositionSolver solver = cd.getSolver();
      RealMatrix solution = solver.solve(matrixG);
      RealMatrix aInv = solver.getInverse();
      beta = new double[nVariables + 1];
      for (int i = 0; i < beta.length; i++) {
        beta[i] = solution.getEntry(i, 0);
      }

      zOffset = zMean;
      zEstimate = zMean + beta[0];

//
      double[] e = new double[nSamples];
      initializeResidualArray(model, nSamples, samples, e); // sse, ssr, sst
      sigma2 = sse / nDegOfFreedom; // also "error variance"
      standardErrorOfPrediction = Math.sqrt(sigma2 * aInv.getEntry(0, 0));
      adjustedStandardErrorOfPrediction = Math.sqrt(sigma2 * (1 + aInv.getEntry(0, 0)));
      r2 = ssr / sst;

      standardErrorOfParameters = new double[nVariables + 1];
      for (int i = 0; i < standardErrorOfParameters.length; i++) {
        standardErrorOfParameters[i] = Math.sqrt(sigma2 * aInv.getEntry(i, i));
      }

      if (computeExtendedStatistics) {
        double[][] dm = computeDesignMatrix(model, nSamples, samples);
        RealMatrix mX = new BlockRealMatrix(dm);
        RealMatrix mXT = mX.transpose();
        RealMatrix hat = mX.multiply(aInv).multiply(mXT);
        rStudentValues = new double[nSamples];
        for (int i = 0; i < nSamples; i++) {
          double s2 = 0;
          for (int j = 0; j < nSamples; j++) {
            if (j != i) {
              s2 += e[i] * e[i];
            }
          }
          double s1 = Math.sqrt(s2 / (nDegOfFreedom - 1));
          double hii = hat.getEntry(i, i);
          rStudentValues[i] = e[i] / (s1 * Math.sqrt(1 - hii));
        }
      }
      // Temporary code to compare results of this class
      // (quadratic model only) with the Apache Math implementation
      //
      //  int nVar = model.nCoefficients-1;
      //  double[] data = new double[nSamples * (nVar+1)];
      //  double[][] qm = computeDesignMatrix(model, nSamples, samples);
      //  int k = 0;
      //  for (int i = 0; i < nSamples; i++) {
      //    data[k++] = samples[i][2];
      //    for(int j=0; j<nVar; j++){
      //      data[k++] = qm[i][j+1];
      //    }
      //  }
      //  OLSMultipleLinearRegression mls = new OLSMultipleLinearRegression();
      //  mls.newSampleData(data, nSamples, nVar);
      //  double[] q = mls.estimateRegressionParameters();
      //  double[][] v = mls.estimateRegressionParametersVariance();
      //  double []erp = mls.estimateRegressionParametersStandardErrors();
      //  double erv = mls.estimateRegressandVariance();
      //  double eev = mls.estimateErrorVariance();
      //  double rss = mls.calculateResidualSumOfSquares();
      //  double er2 = mls.calculateRSquared();
      //  double dummy = rss; // to provide a break point for debugging
    } catch (SingularMatrixException npex) {
      return Double.NaN;
    }

    return zEstimate;
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
  public double[] getParameters() {
    if (beta == null) {
      return new double[0];
    }
    return Arrays.copyOf(beta, beta.length);
  }

  /**
   * Gets a safe copy of the computed standard error for the polynomial
   * coefficients from the regression (the error of the "beta" parameters).
   * <p>
   *
   * @return a if the last regression computation was successful, a valid array
   * of standard-error values for the parameters corresponding
   * to the selected surface model; otherwise, an array of length zero.
   */
  public double[] getStandardErrorOfParameters() {
    if (standardErrorOfParameters == null) {
      return new double[0];
    }
    return Arrays.copyOf(standardErrorOfParameters, standardErrorOfParameters.length);
  }

  /**
   * Gets an unbiased estimate of the variance for the regression
   * when evaluated at the query position.
   *
   * @return if available, a positive real value; otherwise Double&#46;NaN.
   */
  public double getVariance() {
    return sigma2;
  }

  /**
   * Gets the standard error of prediction at the coordinates of the
   * interpolation point. This quantity may be used directly in the computation
   * of confidence intervals and prediction intervals. See Walpole &amp; Myers
   * pg. 418.
   *
   * @return if successful, a valid floating-point value; otherwise
   * Double&#46;NaN.
   */
  public double getStandardErrorOfPrediction() {
    return standardErrorOfPrediction;
  }

  /**
   * Gets the number of degrees of freedom for the most recent computation
   * based on a ordinary least squares treatment
   *
   * @return if the most recent computation was successful, a positive
   * integer; otherwise, a value &lt;= 0.
   */
  public int getDegreesOfFreedom() {
    return nDegOfFreedom;
  }

  /**
   * Get the minimum number of samples required to perform a
   * regression for the specified surface model
   *
   * @param sm the surface model to be evaluated
   * @return a positive integer
   */
  final public int getMinimumRequiredSamples(SurfaceModel sm) {
    return sm.getCoefficientCount() + 1;
  }

  /**
   * Get the coordinates used for the initial query
   *
   * @return an array of dimension 2 containing the x and y
   * values of the most recently completed query.
   */
  public double[] getQueryCoordinates() {
    double[] p = new double[2];
    p[0] = xOffset;
    p[1] = yOffset;
    return p;
  }

  /**
   * Get the x offset that is subtracted from sample coordinates in order
   * to adjust the application-supplied query position (xQuery, yQuery)
   * from the regression computation. In effect, the query position
   * is treated as the origin.
   *
   * @return a valid floating-point value.
   */
  public double getOffsetX() {
    return xOffset;
  }

  /**
   * Get the y offset that is subtracted from sample coordinates in order
   * to adjust the application-supplied query position (xQuery, yQuery)
   * from the regression computation. In effect, the query position
   * is treated as the origin.
   *
   * @return a valid floating-point value.
   */
  public double getOffsetY() {
    return yOffset;
  }

  /**
   * Gets the offset value for the z coordinates used to perform computations.
   * This value is equivalent to the mean value of the z coordinates of the
   * input sample used during the interpolation.
   *
   * @return if regression was successful, a finite floating-point value;
   * otherwise, Double&#46;NaN.
   */
  public double getOffsetZ() {
    return zOffset;
  }

  /**
   * Gets the value estimated for the coordinates of the most recently
   * computed regression. This value is equivalent to beta[0]
   *
   * @return if regression was successful, a finite floating-point value;
   * otherwise, Double&#46;NaN.
   */
  public double getEstimatedZ() {
    return zEstimate;
  }

  /**
   * Get the surface model associated with this instance.
   *
   * @return a valid surface model.
   */
  public SurfaceModel getModel() {
    return model;
  }

  /**
   * Get the estimated value at the specified coordinates using the
   * parameters from the most recently completed regression operation.
   *
   * @param xEstimate the x coordinate of interest
   * @param yEstimate the y coordinate of interest
   * @return if successful, a valid floating-point value; otherwise,
   * Double&#46;NaN.
   */
  public double computeEstimatedValue(double xEstimate, double yEstimate) {
    double x = xEstimate - xOffset;
    double y = yEstimate - yOffset;

    switch (model) {
      case Planar:
        // z(x, y) = b0 + b1*x + b2*y.
        return beta[0] + (beta[1] * x + beta[2] * y) + zOffset;
      case PlanarWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x*y.
        return beta[0] + (beta[1] * x + beta[2] * y + beta[3] * x * y) + zOffset;
      case QuadraticWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y + beta[5] * x * y) + zOffset;
      case Quadratic:
        //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y) + zOffset;
      case CubicWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
        //         + b6*x^2*y + b7*x*y^2 + b8*x^3 + b9*y^3.
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y + beta[5] * x * y
          + beta[6] * x * x * y + beta[7] * x * y * y
          + beta[8] * x * x * x + beta[9] * y * y * y) + zOffset;
      case Cubic:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
        //         + b5*x^3 + b6*y^3.
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y
          + beta[5] * x * x * x + beta[6] * y * y * y) + zOffset;
      default:
        return Double.NaN;
    }

  }

  /**
   * Clear all state variables and external references that may have
   * been set in previous operations.
   */
  void clear() {
    xOffset = Double.NaN;
    yOffset = Double.NaN;
    zOffset = Double.NaN;
    zEstimate = Double.NaN;
    sigma2 = Double.NaN;
    standardErrorOfPrediction = Double.NaN;
    nSamples = 0;
    nDegOfFreedom = 0;
    nVariables = 0;
    beta = null;
    standardErrorOfParameters = null;
    hat = null;
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
    double alpha = 1 - populationFraction;
    TDistribution td = new TDistribution(nDegOfFreedom);
    double ta = td.inverseCumulativeProbability(1.0 - alpha / 2.0);
    return ta * standardErrorOfPrediction;
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
    double alpha = 1 - populationFraction;
    TDistribution td = new TDistribution(nDegOfFreedom);
    double ta = td.inverseCumulativeProbability(1.0 - alpha / 2.0);
    return ta * adjustedStandardErrorOfPrediction;
  }

  /**
   * Computes the "design matrix" for the input set of samples and
   * coordinate offset. The design matrix is a n by (k+1) matrix
   * where n is the number of samples and k is the number of explanatory
   * variables. The first column of each row in the matrix is populated
   * with the value 1. Subsequent columns are populated with explanatory
   * variables which are computed based on the selection of surface model.
   *
   * @param x0 a coordinate offset for adjusting the sample coordinates
   * @param y0 a coordinate offset for adjusting the sample coordinates
   * @param n the number of samples
   * @param s an array dimensioned to at least n-by-k
   * @return a two dimensional array giving values for the design matrix.
   */
  double[][] computeDesignMatrix(
    SurfaceModel sm, int n, double[][] s) {
    double[][] matrix;
    if (sm == SurfaceModel.CubicWithCrossTerms) {
      matrix = new double[n][10];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double xy = x * y;

        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = x2;
        matrix[i][4] = y2;
        matrix[i][5] = xy;
        matrix[i][6] = x2 * y;
        matrix[i][7] = x * y2;
        matrix[i][8] = x3;
        matrix[i][9] = y3;
      }
    } else if (sm == SurfaceModel.QuadraticWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2+b5*x*y
      matrix = new double[n][6];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = x2;
        matrix[i][4] = y2;
        matrix[i][5] = xy;
      }
    } else if (sm == SurfaceModel.Quadratic) {
      //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2+b5*x*y
      matrix = new double[n][5];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = x2;
        matrix[i][4] = y2;
      }
    } else if (sm == SurfaceModel.Planar) {
      //  z(x, y) = b0 + b1*x + b2*y;
      matrix = new double[n][3];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
      }
    } else if (sm == SurfaceModel.PlanarWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x*y;
      matrix = new double[n][4];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double xy = x * y;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = xy;
      }
    } else { // if(sm==SurfaceModel.Cubic){
      matrix = new double[n][7];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = x2;
        matrix[i][4] = y2;
        matrix[i][5] = x3;
        matrix[i][6] = y3;
      }
    }
    return matrix;
  }

  @Override
  public String toString() {
    return "Ordinary Least Squares: model=" + model == null ? "None" : model.name();
  }

    /**
   * Gets the "hat" matrix from the most recent computation, if enabled.
   * The computation of a HAT matrix is optional.
   * If the extended-statistics option was not enabled,
   * then this method will return a null.
   *
   * @return if enabled, a valid instance; otherwise, a null.
   */
  RealMatrix getHatMatrix() {
    return hat;
  }

  double initializeResidualArray(
    SurfaceModel sm, int n, double[][] s, double[] r) {

    double r2Sum = 0;
    sst = 0;
    if (sm == SurfaceModel.CubicWithCrossTerms) {
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      double b3 = beta[3];
      double b4 = beta[4];
      double b5 = beta[5];
      double b6 = beta[6];
      double b7 = beta[7];
      double b8 = beta[9];
      double b9 = beta[10];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double z = s[i][2] - zOffset;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double xy = x * y;
        //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
        //  + b6*x^2*y + b7*x*y^2 + b8*x^3 + b9*y^3
        r[i] = b0 + b1 * x + b2 * y + b3 * x2 + b4 * y2 + b5 * xy
          + b6 * x2 * y + b7 * x * y2 + b8 * x3 + b9 * y3 - z;
        r2Sum += r[i] * r[i];
        sst += z * z;

      }
    } else if (sm == SurfaceModel.QuadraticWithCrossTerms) {
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      double b3 = beta[3];
      double b4 = beta[4];
      double b5 = beta[5];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;
        double z = s[i][2] - zOffset;
        //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2+b5*x*y
        r[i] = b0 + b1 * x + b2 * y + b3 * x2 + b4 * y2 + b5 * xy - z;
        r2Sum += r[i] * r[i];
        sst += z * z;
      }
    } else if (sm == SurfaceModel.Quadratic) {
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      double b3 = beta[3];
      double b4 = beta[4];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double z = s[i][2] - zOffset;
        //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2
        double q = b0 + b1 * x + b2 * y + b3 * x2 + b4 * y2 - z;
        r2Sum += q * q;
        r[i] = q;
        sst += z * z;
      }
    } else if (sm == SurfaceModel.Planar) {
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double z = s[i][2] - zOffset;
        //  z(x, y) = b0 + b1*x + b2*y;
        r[i] = b0 + b1 * x + b2 * y - z;
        r2Sum += r[i] * r[i];
        sst += z * z;
      }
    } else if (sm == SurfaceModel.PlanarWithCrossTerms) {
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      double b3 = beta[3];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double xy = x * y;
        double z = s[i][2] - zOffset;
        //  z(x, y) = b0 + b1*x + b2*y + b3*x*y;
        r[i] = b0 + b1 * x + b2 * y + b3 * xy - z;
        r2Sum += r[i] * r[i];
        sst += z * z;
      }
    } else { // if(sm==SurfaceModel.Cubic){
      double b0 = beta[0];
      double b1 = beta[1];
      double b2 = beta[2];
      double b3 = beta[3];
      double b4 = beta[4];
      double b5 = beta[5];
      double b6 = beta[6];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - xOffset;
        double y = s[i][1] - yOffset;
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double z = s[i][2] - zOffset;
        //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x^3 + b6*y^3.
        r[i] = b0 + b1 * x + b2 * y + b3 * x2 + b4 * y2 + b5 * x3 + b6 * y3 - z;
        r2Sum += r[i] * r[i];
        sst += z * z;
      }
    }
    sse = r2Sum;
    ssr = sst - sse;
    return r2Sum;
  }

  /**
   * Get the r-squared value, also known as the coefficient of multiple determination.
   * @return a value in the range 0 to 1.
   */
  public double getRSquared(){
    return r2;
  }
}
