/*
 * Copyright 2013 Gary W. Lucas.
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
 * 08/2014   G. Lucas  Created as TerrainGWR
 * 12/2015   G. Lucas  Renamed to SurfaceGWR to reflect the potential
 *                       for other applications in addition to terrain.
 *
 * Notes:
 *   In the implementation of this class, I have tried to defer the
 * processing of computationally expensive quantities until they are
 * actually requested by a calling application. For example,
 * if pre-specified bandwidth options are used, this class can produce
 * a grid of interpolated values quite quickly because they do not
 * require the evaluation of residuals or the hat matrix.
 * -----------------------------------------------------------------------
 */
package tinfour.gwr;

import java.io.PrintStream;
import java.util.Arrays;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Provides an implementation of a weighted polynomial regression
 * for the surface f(x, y). A small set of models (cubic, quadradic,
 * planar) are available for the surface of interest which may
 * be elevation or some other phenomenon. Weights are computed
 * using an inverse distance weighted calculation.
 * <p>
 * While this class is by no means limited to terrain-based applications,
 * the original motivation for its creation was the modeling of
 * surface elevation. It is optimized for the computation of values
 * at specific points in which a set of irregularly spaced sample points
 * are available in the vicinity of the point of interest. Each value
 * calculation involves a separate regression operation.
 * <p>
 * Regression methods are used as a way of dealing with uncertainty
 * in the observed values passed into the calculation. As such, it provides
 * methods for evaluating the uncertainty in the computed results.
 * <p>
 * In terrain-based applications, it is common to treat points nearest
 * a query position as more significant than those farther away (in
 * accordance to the precept of "spatial autocorrelation"). In order
 * to support that, a criterion for inverse-distance weighting of the
 * regression is provided.
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
 * linear regression, its descriptive statistics (particularly the
 * prediction interval), and their use. The calculations for
 * <strong>weighted</strong> regression are not covered in their work, but were
 * derived from the information they provided. Because these calculations
 * are not taken from published literature, they have not been vetted
 * by expert statisticians.
 * <p>
 * Details of the descriptive of statistics specific to a weighted
 * regression are taken from
 * Leung, Yee; Mei, Chang-Lin; and Zhang, Wen-Xiu (2000). "Statistical
 * tests for spatial nonstationarity based on the geographically
 * weighted regression model", Environment and Planning A 2000, volumn 32,
 * p. 9-32.
 * <p>
 * Information related to the AICc criteria as applied to a GWR was found
 * in Charlton, M. and Fotheringham, A. (2009) "Geographically Weighted
 * Regression -- White Paper",  National Center for Geocomputation,
 * National University of Ireland Maynooth, a white paper downloaded from the web.
 * A number of other papers by Brunsdon, Fotheringham and Charlton (BRC) which
 * provide slightly different perspectives on the same material
 * can be found  on the web.
 * <p>
 * <strong>A Note on Safe Coding:</strong> This class maintains references to
 * its most recent inputs as member elements. For efficiency purposes,
 * it does not make copies of the input arrays, but uses them directly.
 * Therefore, it is imperative that the calling application not modify
 * these elements until it is done with the results from a computation.
 * Also, some of the getter methods in the class expose internal arrays.
 * So the results obtained from these methods should not be modified by
 * application code but are subject to modification by subsequent interpolation
 * operations. While approach violates well-known safe-coding practices,
 * it is necessary in this case for efficiency reasons. Instances of this
 * class are often used in raster-processing operations that require
 * millions of interpolations in tight loops where the overhead of
 * repeatedly creating arrays would be detrimental to processing.
 *
 *
 */
public class SurfaceGwr {
  private static final double log2PI = Math.log(2 * Math.PI);

  private double xOffset;
  private double yOffset;

  int nSamples;
  double[][] samples;
  double[] weights;
  double[]residuals;
  int nVariables; // number of independent or "explanatory" variables
  int nDegOfFreedom;
  double[] beta;

  boolean areVarianceAndHatPrepped;
  boolean areDeltasComputed;
  DecompositionSolver solver;
  RealMatrix solution;
  double sigma2;  // Residual standard variance (sigma squared)
  double rss; // resisdual sum squares

  double effectiveDegOfF;
  RealMatrix mX;
  RealMatrix vcMatrix;  // variance-covariance matrix
  RealMatrix hat;
  double traceHat;
  double delta1;
  double delta2;

  private final SurfaceModel model;


  /**
   * Construct an interpolator with default Quadratic model
   */
  public SurfaceGwr() {
    model = SurfaceModel.QuadraticWithCrossTerms;
  }

  /**
   * Construct an instance using the specified surface model.
   *
   * @param model a valid surface model.
   */
  public SurfaceGwr(SurfaceModel model) {
    if (model == null) {
      throw new NullPointerException("Null model not allowed");
    }
    this.model = model;
  }

  /**
   * Get the minimum number of samples required to perform a
   * regression for the associated surface model
   *
   * @return a positive integer
   */
  final public int getMinimumRequiredSamples() {
    return model.getCoefficientCount() + 1;
  }

  /**
   * Computes the elevation for a point at the specified query
   * coordinates, by performing a multiple-linear regression using the
   * observed values. A number of statistics are simultaneously computed
   * and may be obtained using access methods. The more heavy weight
   * computations (such as the computation of a variance and covariance
   * matrix) are deferred until actually requested by the calling application.
   * <p>
   * <strong>Note:</strong> For efficiency purposes, the arrays for
   * samples and weights passed to this method are stored in the class
   * directly.
   *
   * @param xQuery x coordinate of the query position
   * @param yQuery y coordinate of the query position
   * @param nSamples the number of sample points to be used for regression
   * @param samples an array of dimension [n][3] giving at least nSamples
   * points with the x, y, and z values for the regression.
   * @param weights an array of weighting factors for samples
   * @return an array of regression coefficients, or null if the
   * computation failed.
   */
  @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
  public double[] computeRegression(
    double xQuery,
    double yQuery,
    int nSamples,
    double[][] samples,
    double[] weights) {
    // clear out previous solutions
    areVarianceAndHatPrepped = false;
    areDeltasComputed = false;
    solution = null;
    this.sigma2 = Double.NaN;
    this.rss = Double.NaN;
    this.beta = null;
    this.vcMatrix = null;
    this.hat = null;

    if (nSamples < model.getCoefficientCount()) {
      throw new IllegalArgumentException(
        "Insufficient number of samples for regression: found "
        + nSamples + ", need " + model.getCoefficientCount());
    }

    this.nSamples = nSamples;
    this.samples = samples;
    this.weights = weights;
    this.xOffset = xQuery;
    this.yOffset = yQuery;

    double[][] g;
    double[][] input;

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
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;
        double xy = x * y;

        input[0][0] += w;

        input[0][1] += w * x;
        input[0][2] += w * y;
        input[0][3] += w * x2;
        input[0][4] += w * y2;
        input[0][5] += w * xy;
        input[0][6] += w * x2 * y;
        input[0][7] += w * x * y2;
        input[0][8] += w * x3;
        input[0][9] += w * y3;

        input[1][1] += w * x2;
        input[1][2] += w * xy;
        input[1][3] += w * x3;
        input[1][4] += w * x * y2;
        input[1][5] += w * x2 * y;
        input[1][6] += w * x * x2 * y;
        input[1][7] += w * x * x * y2;
        input[1][8] += w * x * x3;
        input[1][9] += w * x * y3;

        input[2][2] += w * y2;
        input[2][3] += w * x2 * y;
        input[2][4] += w * y3;
        input[2][5] += w * x * y2;
        input[2][6] += w * y * x2 * y;
        input[2][7] += w * y * x * y2;
        input[2][8] += w * y * x3;
        input[2][9] += w * y * y3;

        input[3][3] += w * x4;
        input[3][4] += w * x2 * y2;
        input[3][5] += w * x3 * y;
        input[3][6] += w * x2 * x2 * y;
        input[3][7] += w * x2 * x * y2;
        input[3][8] += w * x2 * x3;
        input[3][9] += w * x2 * y3;

        input[4][4] += w * y4;
        input[4][5] += w * x * y3;
        input[4][6] += w * y2 * x2 * y;
        input[4][7] += w * y2 * x * y2;
        input[4][8] += w * y2 * x3;
        input[4][9] += w * y2 * y3;

        input[5][5] += w * x2 * y2;
        input[5][6] += w * xy * x2 * y;
        input[5][7] += w * xy * x * y2;
        input[5][8] += w * xy * x3;
        input[5][9] += w * xy * y3;

        input[6][6] += w * x2 * y * x2 * y;
        input[6][7] += w * x2 * y * x * y2;
        input[6][8] += w * x2 * y * x3;
        input[6][9] += w * x2 * y * y3;

        input[7][7] += w * y2 * x * x * y2;
        input[7][8] += w * y2 * x * x3;
        input[7][9] += w * y2 * x * y3;

        input[8][8] += w * x3 * x3;
        input[8][9] += w * x3 * y3;

        input[9][9] += w * y3 * y3;

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
        g[3][0] += w * x2 * z;
        g[4][0] += w * y2 * z;
        g[5][0] += w * xy * z;
        g[6][0] += w * x2 * y * z;
        g[7][0] += w * x * y2 * z;
        g[8][0] += w * x3 * z;
        g[9][0] += w * y3 * z;
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
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;
        double xy = x * y;

        input[0][0] += w;

        input[0][1] += w * x;
        input[0][2] += w * y;
        input[0][3] += w * x2;
        input[0][4] += w * y2;
        input[0][5] += w * xy;

        input[1][1] += w * x2;
        input[1][2] += w * xy;
        input[1][3] += w * x * x2;
        input[1][4] += w * x * y2;
        input[1][5] += w * x * xy;

        input[2][2] += w * y2;
        input[2][3] += w * x2 * y;
        input[2][4] += w * y3;
        input[2][5] += w * x * y2;

        input[3][3] += w * x4;
        input[3][4] += w * x2 * y2;
        input[3][5] += w * x3 * y;

        input[4][4] += w * y4;
        input[4][5] += w * x * y3;

        input[5][5] += w * x2 * y2;

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
        g[3][0] += w * x2 * z;
        g[4][0] += w * y2 * z;
        g[5][0] += w * xy * z;
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
      //  z(x, y) = b0 + b1*x + b2*y +b3*x^2 +b4*y^2+b5*x*y
      nVariables = 4;
      g = new double[5][1];
      input = new double[5][5];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;
        double xy = x * y;

        input[0][0] += w;

        input[0][1] += w * x;
        input[0][2] += w * y;
        input[0][3] += w * x2;
        input[0][4] += w * y2;

        input[1][1] += w * x2;
        input[1][2] += w * xy;
        input[1][3] += w * x3;
        input[1][4] += w * x * y2;

        input[2][2] += w * y2;
        input[2][3] += w * x2 * y;
        input[2][4] += w * y3;

        input[3][3] += w * x4;
        input[3][4] += w * x2 * y2;

        input[4][4] += w * y4;

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
        g[3][0] += w * x2 * z;
        g[4][0] += w * y2 * z;
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
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;

        input[0][0] += w;
        input[0][1] += w * x;
        input[0][2] += w * y;

        input[1][1] += w * x2;
        input[1][2] += w * x * y;

        input[2][2] += w * y2;

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
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
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;


        input[0][0] += w;
        input[0][1] += w * x;
        input[0][2] += w * y;
        input[0][3] += w * xy;

        input[1][1] += w * x2;    // x*x
        input[1][2] += w * xy;    // y*x
        input[1][3] += w * xy*x;  // xy*x

        input[2][2] += w * y2;    // y*y
        input[2][3] += w * xy*y;  // xy*y

        input[3][3] += w * xy * xy;  //xy*xy

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
        g[3][0] += w * xy * z;
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
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double z = samples[i][2];
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;
        double xy = x * y;
        double x3 = x * x2;
        double y3 = y * y2;
        double x4 = x2 * x2;
        double y4 = y2 * y2;

        input[0][0] += w;

        input[0][1] += w * x;
        input[0][2] += w * y;
        input[0][3] += w * x2;
        input[0][4] += w * y2;
        input[0][5] += w * x3;
        input[0][6] += w * y3;

        input[1][1] += w * x2;
        input[1][2] += w * xy;
        input[1][3] += w * x3;
        input[1][4] += w * y2 * x;
        input[1][5] += w * x4;
        input[1][6] += w * y3 * x;

        input[2][2] += w * y2;
        input[2][3] += w * x2 * y;
        input[2][4] += w * y3;
        input[2][5] += w * x3 * y;
        input[2][6] += w * y4;

        input[3][3] += w * x4;
        input[3][4] += w * y2 * x2;
        input[3][5] += w * x3 * x2;  // x5
        input[3][6] += w * y3 * x2;

        input[4][4] += w * y4;
        input[4][5] += w * x3 * y2;
        input[4][6] += w * y3 * y2;  // y5

        input[5][5] += w * x3 * x3;  // x6
        input[5][6] += w * y3 * x3;

        input[6][6] += w * y3 * y3; // y6

        g[0][0] += w * z;
        g[1][0] += w * x * z;
        g[2][0] += w * y * z;
        g[3][0] += w * x2 * z;
        g[4][0] += w * y2 * z;
        g[5][0] += w * x3 * z;
        g[6][0] += w * y3 * z;
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

    RealMatrix matrixG = new Array2DRowRealMatrix(g, false);
    RealMatrix matrixA = new Array2DRowRealMatrix(input, false);

    // The Apache Commons Math MultipleLinearRegression implementation
    // uses the QRDecomposition, and we follow their lead.
    // When I first implemented this, I thought that the input matrix would be
    // a real symmetric and positive-definite matrix. If that were the case,
    // it could be solved using a Cholesky decomposition.
    // However, the weighting factors remove the positive-definite property
    // and even when evaluating ordinary least squares, I ran into numeric
    // issues that led to the matrix violating the positive-definite criterion.
    try {
      QRDecomposition cd = new QRDecomposition(matrixA);
      solver = cd.getSolver();
      solution = solver.solve(matrixG);
      beta = new double[nVariables + 1];
      for (int i = 0; i < beta.length; i++) {
        beta[i] = solution.getEntry(i, 0);
      }
    } catch (SingularMatrixException npex) {
      solution = null;
      solver = null;
      return null;
    }

    return beta;
  }

  void computeDeltas() {
    if (this.areDeltasComputed) {
      return;
    }
    computeVarianceAndHat();
    int nHat = hat.getRowDimension();
    double[][] itemp = new double[nHat][nHat];
    for (int i = 0; i < itemp.length; i++) {
      itemp[i][i] = 1.0;
    }
    RealMatrix mI = new BlockRealMatrix(itemp);
    RealMatrix mIL = mI.subtract(hat);
    RealMatrix mILT = mIL.transpose().multiply(mIL);
    delta1 = mILT.getTrace();
    delta2 = (mILT.multiply(mILT)).getTrace();
  }

  void computeVarianceAndHat() {
    if (this.areVarianceAndHatPrepped) {
      return;
    }
    if (beta == null) {
      // the regression failed
      return;
    }
    areVarianceAndHatPrepped = true;

    // when the weights are added to the summations, the algebraic
    // simplifications from the classic formulations no longer apply.
    // so some of the short cuts we'd like to use are unavailable to us.

    residuals = new double[nSamples];

    double rX[][];
    double rW[] = weights;
    if (rW.length != nSamples) {
      rW = new double[nSamples];
      System.arraycopy(weights, 0, rW, 0, nSamples);
    }
    DiagonalMatrix mW = new DiagonalMatrix(rW);

    double sse = 0; // sum squared errors
    // compute SSE, the Sum of the Squared Errors
    if (model == SurfaceModel.CubicWithCrossTerms) {
      rX = new double[nSamples][10];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;
        rX[i][3] = x * x;
        rX[i][4] = y * y;
        rX[i][5] = x * y;
        rX[i][6] = x * x * y;
        rX[i][7] = x * y * y;
        rX[i][8] = x * x * x;
        rX[i][9] = y * y * y;
        double ssrS = (((beta[8] * x + beta[3]) * x + beta[1]) * x
          + ((beta[9] * y + beta[4]) * y + beta[2]) * y
          + ((beta[7] * y + beta[6] * x) + beta[5]) * x * y)
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    } else if (model == SurfaceModel.QuadraticWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y.
      rX = new double[nSamples][6];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;
        rX[i][3] = x * x;
        rX[i][4] = y * y;
        rX[i][5] = x * y;

        double ssrS
          = (beta[3] * x + beta[1]) * x
          + (beta[4] * y + beta[2]) * y
          + beta[5] * x * y
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    } else if (model == SurfaceModel.Quadratic) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
      rX = new double[nSamples][5];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;
        rX[i][3] = x * x;
        rX[i][4] = y * y;

        double ssrS
          = (beta[3] * x + beta[1]) * x
          + (beta[4] * y + beta[2]) * y
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    } else if (model == SurfaceModel.Planar) {
      //  z(x, y) = b0 + b1*x + b2*y
      rX = new double[nSamples][3];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;

        double ssrS
          = (beta[1] * x + beta[2] * y)
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    } else if (model == SurfaceModel.PlanarWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*xy
      rX = new double[nSamples][4];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        double xy = x * y;
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;
        rX[i][3] = xy;

        double ssrS
          = (beta[1] * x + beta[2] * y + beta[3] * xy)
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    } else { // model == SurfaceModel.CubicNoCrossTerms
      rX = new double[nSamples][7];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xOffset;
        double y = samples[i][1] - yOffset;
        double z = samples[i][2];
        rX[i][0] = 1.0;
        rX[i][1] = x;
        rX[i][2] = y;
        rX[i][3] = x * x;
        rX[i][4] = y * y;
        rX[i][5] = x * x * x;
        rX[i][6] = y * y * y;
        double ssrS = (((beta[5] * x + beta[3]) * x + beta[1]) * x
          + ((beta[6] * y + beta[4]) * y + beta[2]) * y)
          + (beta[0] - z);

        sse += ssrS * ssrS;
        residuals[i] = ssrS;
      }
    }

    if (sse <= 0) {
      // this would occur when all the weighted zHat values very closely
      // match the weighted sample z values (either because of a very
      // good match with the model or very small weight values where
      // they don't match) so that the difference between
      // sst and ssr is so close to zero that numeric issues
      // result in a negative value.
      sigma2 = 0;
      rss = 0;
      return;
    }


    // The traditional variance calculation for an ordinary least squares
    // fit,  sigma2 = sumSquaredErrors/(nSamples-degreesOfFreedom)
    // does not apply in the weighted regression
    // because it would not account for the fact that the regression
    // coefficients were derived by assigning different levels of
    // significance (weights) to the samples.  So in the calculation
    // below, we need to use alternate methods.
    //
    // Brunsdon, Fotheringham and Charlton (BRC) use the calculation
    //       sse/(nSamples - (2*tr(S) - tr(S'S)))
    // where S is the hat matrix and S' is its transpose.  They
    // state that tr(S'S) should be close to tr(S) and that one could
    // use just plain
    //     tr(S) approximates 2*tr(s) - tr(S'S)
    // to save some computation.  In testing I frequently observed cases
    // where tr(S'S) was nothing like tr(S). In fact, tr(S'S) was sometimes
    // GREATER than 2*tr(S) which means that the last subtraction above
    // would actually INCREASE the denominator.
    //    I've done a lot of testing and do not understand what's going
    // on.  My best guess is that the GWR is not an unbasiased estimator
    // and that the BRC shorter form works only when the regression
    // is approximately unbiased. BRC actually mentions, but does not
    // apply, a bias-related term. But depending on the bandwidth selection,
    // the ommitted terms become significant.
    //   Finally, there isn't a pressing reason to remove the tr(S'S)
    // calculation from the processing since time trials suggest that by
    // the time we've invested in computing the hat matrix, the extra
    // processing for tr(S'S) doesn't add significantly to the runtime.


      vcMatrix = solver.getInverse();

      mX = new BlockRealMatrix(rX);
      //mX = new Array2DRowRealMatrix(rX, false);
      RealMatrix mX1 = mX.transpose();
      RealMatrix mTemp = mX.multiply(vcMatrix);
      RealMatrix mTemp2 = mTemp.multiply(mX1);
      hat = mTemp2.multiply(mW);
      traceHat = hat.getTrace();

      // let S be the hat matrix computed above with S' being transpose(S)
      // the following computes trace(S'S)
      // The block of code was used to verify above calculation for tr(S'S)
      //     RealMatrix mS1 = hat.transpose();
      //      double tmSS1 = (mS1.multiply(hat)).getTrace();
      double sq2 = 0;
      for (int i = 0; i < hat.getRowDimension(); i++) {
        double row[] = hat.getRow(i);
        double rs = 0;
        for (int j = 0; j < row.length; j++) {
          rs += (row[j] * row[j]);
        }
        sq2 += rs;
      }

      rss = sse;
      sigma2 = sse / (nSamples - (2*traceHat - sq2));
      // the denominator of the sigma2 calculation is identical to
      // the value of delta1 computed elsewhere.

  }

  // This mess was meant to test the stats computations using matrix methods
  // that could more directly be correlated with published documents.
  // The calculations will be less efficient than the customized computeRegression
  // but provide an independent verification...
  //public  void testComputeStatistics() {
  //
  //    if (this.areStatsComputed) {
  //        return;
  //    }
  //    if (beta == null) {
  //        // the regression failed
  //        return;
  //    }
  //    areStatsComputed = true;
  //    // when the weights are added to the summations, the algebraic
  //    // simplifications from the classic formulations no longer apply.
  //
  //    sst = 0;  // total sum of the squares
  //    ssr = 0;  // regression sum of the squares
  //    double rX[][];
  //    double rW[] = weights;
  //    if (rW.length != nSamples) {
  //        rW = new double[nSamples];
  //        System.arraycopy(weights, 0, rW, 0, nSamples);
  //    }
  //    DiagonalMatrix mW = new DiagonalMatrix(rW);
  //    double rZ[][] = new double[nSamples][1];
  //    for (int i = 0; i < nSamples; i++) {
  //        rZ[i][0] = samples[i][2];
  //    }
  //    double rEst[] = new double[nSamples];
  //    RealMatrix mZ = new Array2DRowRealMatrix(rZ, false);
  //
  //    // compute SSE, the Sum of the Squared Errors (weighted)
  //    if (model == SurfaceModel.Cubic) {
  //        rX = new double[nSamples][11];
  //        for (int i = 0; i < nSamples; i++) {
  //            double x = samples[i][0] - xOffset;
  //            double y = samples[i][1] - yOffset;
  //            double z = samples[i][2];
  //            rX[i][0] = 1.0;
  //            rX[i][1] = x;
  //            rX[i][2] = y;
  //            rX[i][3] = x * x;
  //            rX[i][4] = y * y;
  //            rX[i][5] = x * y;
  //            rX[i][6] = x * x * y;
  //            rX[i][7] = x * y * y;
  //            rX[i][8] = x * x * x;
  //            rX[i][9] = y * y * y;
  //            double ssrS = (((beta[8] * x + beta[3]) * x + beta[1]) * x
  //                + ((beta[9] * y + beta[4]) * y + beta[2]) * y
  //                + ((beta[7] * y + beta[6] * x) + beta[5]) * x * y)
  //                + (beta[0] - z);
  //
  //            sse += weights[i] * ssrS * ssrS;
  //        }
  //    } else {
  //        //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y.
  //        rX = new double[nSamples][6];
  //        for (int i = 0; i < nSamples; i++) {
  //            double x = samples[i][0] - xOffset;
  //            double y = samples[i][1] - yOffset;
  //            double z = samples[i][2];
  //            rX[i][0] = 1.0;
  //            rX[i][1] = x;
  //            rX[i][2] = y;
  //            rX[i][3] = x * x;
  //            rX[i][4] = y * y;
  //            rX[i][5] = x * y;
  //
  //            double ssrS = ((beta[3] * x + beta[1]) * x
  //                + (beta[4] * y + beta[2]) * y
  //                + beta[5] * x * y)
  //                + (beta[0] - z);
  //
  //            rEst[i] = ssrS + z;
  //            sse += ssrS * ssrS;
  //
  //        }
  //    }
  //
  //    if (sse <= 0) {
  //        sse = 0;
  //        s2 = 0;
  //        r2 = 1.0;
  //
  //        // this would occur when all the weighted zHat values very closely
  //        // match the weighted sample z values (either because of a very
  //        // good match with the model or very small weight values where
  //        // they don't match) so that the difference between
  //        // sst and ssr is so close to zero that numeric issues
  //        // result in a negative value.
  //    } else {
  //
  //        vcMatrix = solver.getInverse();
  //
  //        RealMatrix mX = new Array2DRowRealMatrix(rX, false);
  //        RealMatrix mX1 = mX.transpose();
  //
  //        RealMatrix mXWX1 = mX1.multiply(mW).multiply(mX);
  //        QRDecomposition cd = new QRDecomposition(mXWX1);
  //        DecompositionSolver cdSolver = cd.getSolver();
  //        RealMatrix mXWXInv = cdSolver.getInverse();
  //        RealMatrix mS = mX.multiply(mXWXInv);
  //        mS = mS.multiply(mX1);
  //        mS = mS.multiply(mW);
  //        double tmS = mS.getTrace();
  //        RealMatrix mS1 = mS.transpose();
  //        double tmSS1 = (mS1.multiply(mS)).getTrace();
  //
  //        double a0 = mXWXInv.transpose().getTrace();
  //        double a1 = vcMatrix.transpose().getTrace();
  //
  //        RealMatrix mTemp = mX.multiply(vcMatrix);
  //        RealMatrix mTemp2 = mTemp.multiply(mX1);
  //        hat = mTemp2.multiply(mW);
  //        double sq = hat.getTrace();
  //
  //        int nHat = hat.getRowDimension();
  //        double[][] itemp = new double[nHat][nHat];
  //        for (int i = 0; i < itemp.length; i++) {
  //            itemp[i][i] = 1.0;
  //        }
  //        RealMatrix mI = new BlockRealMatrix(itemp);
  //        RealMatrix mIL = mI.subtract(hat);
  //        RealMatrix mILT = mIL.transpose().multiply(mIL);
  //        double delta1 = mILT.getTrace();
  //        double delta2 = (mILT.multiply(mILT)).getTrace();
  //        double nLeungDOF = (delta1 * delta1 / delta2);
  //
  //        RealMatrix mXWX = mX1.multiply(mW).multiply(mX);
  //
  //        QRDecomposition cdx = new QRDecomposition(mXWX);
  //        DecompositionSolver xsolver = cdx.getSolver();
  //        RealMatrix mXMXInv = xsolver.getInverse();
  //        RealMatrix mLeung = mXMXInv.multiply(mX1).multiply(mW).multiply(mW).multiply(mX).multiply(mXMXInv);
  //        double xLeung = mLeung.getEntry(0, 0);
  //
  ////        RealMatrix zEst = hat.multiply(mZ);
  ////        RealMatrix zEst2 = hat.multiply(zEst);
  ////        for(int i=0; i<nSamples; i++){
  ////            System.out.format("%9.5f %9.5f  %9.5f\n", zEst.getEntry(i,0), rEst[i], zEst2.getEntry(i,0));
  ////        }
  ////       System.out.flush();
  //        RealMatrix hat1 = hat.transpose();
  //        double sqm2 = (hat.multiply(hat1)).getTrace();
  //        double sq2 = 0;
  //        for (int i = 0; i < hat.getRowDimension(); i++) {
  //            double row[] = hat.getRow(i);
  //            double rs = 0;
  //            for (int j = 0; j < row.length; j++) {
  //                rs += (row[j] * row[j]);
  //            }
  //            sq2 += rs;
  //        }
  //
  //        effectiveDegOfF = nSamples - (2 * sq - sq2);
  //
  //        s2 = sse / effectiveDegOfF;
  //
  //        TDistribution tdist = new TDistribution(nLeungDOF);
  //        double ta = tdist.inverseCumulativeProbability(0.975);
  //        double pLeung = Math.sqrt(s2) * Math.sqrt(1 + xLeung) * ta;
  //        System.out.println("Leung pred interval " + pLeung);
  //
  //    }
  //}
  /**
   * Print a summary of the parameters and correlation results for
   * the most recent interpolation.
   *
   * @param ps a valid print stream to receive the output of this method.
   */
  public void printSummary(PrintStream ps) {
    computeVarianceAndHat();
    if (!this.areVarianceAndHatPrepped) {
      ps.format("Regression statistics not available\n");
      return;
    }
    ps.format("Regression coefficients & variance\n");
    for (int i = 0; i < beta.length; i++) {
      System.out.format("beta[%2d] %12.6f  %f\n",
        i, beta[i], Math.sqrt(vcMatrix.getEntry(i, i) * sigma2));
    }
    ps.format("Residual standard error %f on %d degrees of freedom\n",
      getResidualStandardDeviation(), this.nDegOfFreedom);
    ps.format("Correlation coefficient (r^2): %f\n", getR2());
    ps.format("Adusted r^2:                   %f\n", getAdjustedR2());
    ps.format("F statistic:  %f\n", getF());

  }

  /**
   * Gets the computed polynomial coefficients from the regression
   * (the "beta" parameters that).  These coefficients can be used
   * for interpolation or surface modeling purposes. Developers
   * are reminded that the interpolation is based on treating the
   * query point as the origin, so x and y coordinates should be adjusted
   * accordingly when used in calculations based on the
   * return values of this method.
   *
   * @return a valid array of coefficients for the selected surface model.
   */
  public double[] getCoefficients() {
    double[] b = new double[beta.length];
    System.arraycopy(beta, 0, b, 0, beta.length);
    return b;
  }

  /**
   * Get the r-squared value, the coefficient of multiple regression.
   * This value is basically the proportion of the variation in the
   * data that is explained by the postulated model.
   *
   * @return a positive value between 0 and 1.0
   */
  public double getR2() {
    throw new UnsupportedOperationException(
      "R2 statistics not yet implemented");
    //computeStatistics();
    //return r2;
  }

  /**
   * Gets the adjusted R2 value.
   *
   * @return a positive value between 0 and 1.0
   */
  public double getAdjustedR2() {
    throw new UnsupportedOperationException(
      "Adjusted R2 statistics not yet implemented");
    //computeStatistics();
    // return 1.0 - (sse / (nSamples - nVariables - 1)) /(sst /(nSamples - 1));
  }

  /**
   * Gets the F statistic for the regression result which may be used in
   * hypothesis testing for evaluating the regression.
   *
   * @return if available, a valid floating point number;
   * if the regression failed, NaN; if the regression was close to an
   * exact match with the samples, positive infinity
   */
  public double getF() {
    throw new UnsupportedOperationException(
      "Adjusted R2 statistics not yet implemented");
//        computeStatistics();
//        return (ssr / nVariables) / s2;
  }

  /**
   * Gets an unbiased estimate of the variance of the residuals
   * for the predicted values for all samples.
   *
   * @return if available, a positive real value; otherwise NaN.
   */
  public double getResidualVariance() {
    computeVarianceAndHat();
    return sigma2;
  }

  /**
   * Gets the square root of the residual variance.
   *
   * @return if available, a positive value; otherwise a NaN
   */
  public double getResidualStandardDeviation() {
    computeVarianceAndHat();
    return Math.sqrt(sigma2);
  }

  /**
   * Gets the residual sum of the squared errors (residuals) for
   * the predicted versus the observed values at the sample locations.
   *
   * @return a positive number.
   */
  public double getResidualSumOfTheSquares() {
    computeVarianceAndHat();
    return rss;
  }

  /**
   * Get leung's delta parameter
   *
   * @return a positive value
   */
  public double getLeungDelta1() {
    computeDeltas();
    return delta1;
  }

  /**
   * Get Leung's delta2 parameter.
   *
   * @return a positive value
   */
  public double getLeungDelta2() {
    computeDeltas();
    return delta2;
  }

  /**
   * Get the effective degrees of freedom for the a chi-squared distribution
   * which approximates the distribution of the GWR. Combined with the
   * residual variance, this yields an unbiased estimator that can be
   * used in the construction of confidence intervals and prediction
   * intervals.
   * <p>The definition of this method is based on Leung (2000).
   *
   * @return a positive, potentially non-integral value.
   */
  public double getEffectiveDegreesOfFreedom() {
    computeDeltas();
    return delta1 * delta1 / delta2;
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
    computeDeltas(); // calls computeStatistics()
    //double effDegOfF = getEffectiveDegreesOfFreedom(); // should match delta1

    double nLeungDOF = (delta1 * delta1 / delta2);

    double[] rW = new double[nSamples];
    for (int i = 0; i < nSamples; i++) {
      rW[i] = weights[i] * weights[i];
    }

    DiagonalMatrix mW2 = new DiagonalMatrix(rW);
    RealMatrix mXT = mX.transpose();
    RealMatrix mS = vcMatrix.multiply(mXT).multiply(mW2).multiply(mX).multiply(vcMatrix);
    double pS = mS.getEntry(0, 0);
    double p = Math.sqrt(this.sigma2 * (1 + pS));

    TDistribution td = new TDistribution(nLeungDOF);

    double ta = td.inverseCumulativeProbability(1.0 - alpha / 2.0);

    return ta * p;
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
   * @param alpha the significance level (typically 0&#46;.05, etc).
   * @return an array of dimension two giving the lower and upper bound
   * of the prediction interval.
   */
  public double []getPredictionInterval(double alpha){
    double h = getPredictionIntervalHalfRange(alpha);
    double a[] = new double[2];
    a[0] = beta[0]-h;
    a[1] = beta[0]+h;
    return a;
  }

  /**
   * Gets the number of degrees of freedom for the most recent computation.
   *
   * @return if the most recent computation was successful, a positive
   * integer;
   * otherwise, a value &lt;= 0.
   */
  public int getDegreesOfFreedom() {
    computeVarianceAndHat();
    return nDegOfFreedom;
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
   * Get the surface model associated with this instance.
   *
   * @return a valid surface model.
   */
  public SurfaceModel getModel() {
    return model;
  }



  /**
   * Get the Akaike information criterion (corrected) organized so that the
   * <strong>minimum</strong> value is preferred.
   *
   * @return a valid floating point number.
   */
  public double getAICc() {
    // the following logic is due to Chartyon and Fotheringham's
    // "Geographically Weighted Regression White Paper"
    // Other sources omit the log(2 PI) term.  When comparing sets
    // of equal sample size, it doesn't matter.  Further research is
    // required to verify the correctness of the implementation given
    // below.
    if (nSamples - 2 - model.getCoefficientCount() < 1) {
      return Double.NaN;
    }
    computeVarianceAndHat();
    double lv = Math.log(sigma2); // this is 2*log(sigma)
    double x = (nSamples + traceHat) / (nSamples - 2 - traceHat);
    if (x < 0) {
      return Double.NaN;
    }
    return nSamples * (lv + log2PI) +  x;
  }

  public double getEstimatedValue(double xQuery, double yQuery) {
    double x = xQuery - xOffset;
    double y = yQuery - yOffset;

    switch (model) {
      case Planar:
        // z(x, y) = b0 + b1*x + b2*y.
        return beta[0] + (beta[1] * x + beta[2] * y);
      case PlanarWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x*y.
        return beta[0] + (beta[1] * x + beta[2] * y + beta[3] * x * y);
      case QuadraticWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y + beta[5] * x * y);
      case Quadratic:
        //  z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y);
      case CubicWithCrossTerms:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
        //         + b6*x^2*y + b7*x*y^2 + b8*x^3 + b9*y^3.
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y + beta[5] * x * y
          + beta[6] * x * x * y + beta[7] * x * y * y
          + beta[8] * x * x * x + beta[9] * y * y * y);
      case Cubic:
        // z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
        //         + b5*x^3 + b6*y^3.
        return beta[0] + (beta[1] * x + beta[2] * y
          + beta[3] * x * x + beta[4] * y * y
          + beta[5] * x * x * x + beta[6] * y * y * y);
      default:
        return Double.NaN;
    }

  }

  /**
   * Clear all state variables and external references that may have
   * been set in previous operations.
   */
  public void clear() {
    nSamples = 0;
    areVarianceAndHatPrepped = false;
    areDeltasComputed = false;
    samples = null;
    weights = null;
    residuals = null;
    solver = null;
    solution = null;
    mX = null;
    vcMatrix = null;  // variance-covariance matrix
    hat = null;
  }

  /**
   * Gets the residuals from the most recent regression calculation.
   * For this application, the residual the difference between the predicted
   * result and the input sample.
   *
   * @return if computed, a valid array of double; otherwise, an empty array.
   */
  public double[] getResiduals() {
    if (nSamples == 0) {
      return new double[0];
    }
    this.computeVarianceAndHat();
    return Arrays.copyOf(residuals, nSamples);
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
    if (nSamples == 0) {
      return new double[0][0];
    }
    return Arrays.copyOf(samples, nSamples);
  }

  /**
   * Gets an array of weights from the most recent computation.
   *
   * @return if available, a valid array of weights; otherwise, an empty array.
   */
  public double[] getWeights() {
    if (nSamples == 0) {
      return new double[0];
    }
    return Arrays.copyOf(weights, nSamples);
  }

  /**
   * Gets the number of samples from the most recent computation
   *
   * @return a positive integer (zero if not available)
   */
  public int getSampleCount() {
    return nSamples;
  }

  @Override
  public String toString() {
    return "SurfaceGWR: model=" + model;
  }

}
