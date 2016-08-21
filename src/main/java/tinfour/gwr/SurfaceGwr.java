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
 * 08/2014   G. Lucas  Created as TerrainGWR
 * 12/2015   G. Lucas  Renamed to SurfaceGWR to reflect the potential
 *                       for other applications in addition to terrain.
 * 07/2016   G. Lucas  Extensive changes to fix incorrect implementation
 *                     of population statistics such as the variance, etc.
 *                     Removed the specification of
 *                     a surface model from the constructor (it was originally
 *                     intended to support allocation of re-usable data elements
 *                     based on number of parameters for the model, but that
 *                     approach was abandoned and the constructor changed
 *                     accordingly).
 *
 * Notes:
 *   In the implementation of this class, I have tried to defer the
 * processing of computationally expensive quantities until they are
 * actually requested by a calling application. For example,
 * computing the regression coefficients requires constructing and inverting
 * one matrix while computing the "hat" matrix (needed to compute variance,
 * standard deviation, etc.) requires repeating this operation n-sample times.
 * So if an application does not need the additional statistics, there is no
 * need to build these elements.
 *   However, this approach does have a consequence. In order to
 * preserve the necessary data for computing these statistics, it is
 * necessary for an instance of this class to retain an internal reference to
 * the samples and weight arrays passed to it when the calculation
 * is invoked.  Although it would be safer to make copies of these arrays,
 * a concern for "mass production" calculations leads to the class
 * just keeps references to them.
 * -----------------------------------------------------------------------
 */
package tinfour.gwr;

import java.io.PrintStream;
import java.util.Arrays;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Provides an implementation of a weighted polynomial regression
 * for the surface z=p(x, y). A small set of models (cubic, quadradic,
 * planar) are available for the surface of interest which may
 * be elevation or some other phenomenon. Weights are computed
 * using an inverse distance weighted calculation.
 * <p><strong>A Note on the Suitability of This Implementation: </strong>
 * Anyone who values his own time should respect the time of others.
 * With that regard, I believe it appropriate to make this note about
 * the current state of the Tinfour GWR implementation.  While I believe
 * that code is implemented correctly, it is not complete.
 * Statistics such as R values and F scores are not yet available.
 * The Tinfour GWR classes also lacks tools for detecting multi-collinearities
 * in model coefficients.  These classes were developed with a specific
 * application in mind: the modeling of terrain and bathymetry.
 * And while they can be applied to many other problems, potential
 * users should consider whether the tool is suitable to their particular
 * requirements.
 * <p><strong>Usage Notes:</strong>
 * This class is optimized for the computation of values
 * at specific points in which a set of irregularly spaced sample points
 * are available in the vicinity of the point of interest. Each value
 * calculation involves a separate regression operation.
 * <p>
 * Regression techniques are used as a way of dealing with uncertainty
 * in the observed values passed into the calculation. As such, they provide
 * methods for evaluating the uncertainty in the computed results.
 * In terrain-based applications, it is common to treat points nearest
 * a query position as more significant than those farther away (in
 * accordance to the precept of "spatial autocorrelation"). In order
 * to support that, a criterion for inverse-distance weighting of the
 * regression is provided based on the Gaussian Kernel
 * described in the references cited below.
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
 * prediction interval), and their use. The calculations for
 * <strong>weighted</strong> regression are not covered in their work, but were
 * derived from the information they provided. Because these calculations
 * are not taken from published literature, they have not been vetted
 * by expert statisticians.
 * <p>
 * Details of the residual variance and other statistics specific to a weighted
 * regression are taken from
 * Leung, Yee; Mei, Chang-Lin; and Zhang, Wen-Xiu (2000). "Statistical
 * tests for spatial nonstationarity based on the geographically
 * weighted regression model", Environment and Planning A 2000, volumn 32,
 * p. 9-32.
 * <p>
 * Information related to the AICc criteria as applied to a GWR was found
 * in "Geographically Weighted Regression" by David C Wheeler and Antonio Paez,
 * a white paper I found on the web. It appears to be a chapter from
 * "Handbook of Applied Spatial Analysis: Software Tool, Methods, and
 * Applications", Springer Verlag, Berlin (2010). I also found information
 * in Charlton, M. and Fotheringham, A. (2009) "Geographically Weighted
 * Regression -- White Paper", National Center for Geocomputation,
 * National University of Ireland Maynooth, a white paper downloaded from the
 * web. A number of other papers by Brunsdon, Fotheringham and Charlton (BRC)
 * which provide slightly different perspectives on the same material
 * can be found on the web.
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
 * <p>
 * <Strong>Development Notes</strong><br>
 * The current implementation of this class supports a family of surface
 * models based on polynomials p(x, y) of order 3 or less. While this approach
 * is appropriate for the original intent of this class, modeling terrain,
 * there is no reason why the class cannot be adapted to support arbitrary
 * models.
 * Originally, I felt that users interested in other problems might
 * be better served by R, GWR4, or even the Apache Commons Math
 * GSLMultipleLinearRegression class. But this implementation has
 * demonstrated sufficient utility, that it may be worth considering
 * expanding its capabilities in future development.
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
public class SurfaceGwr {

  private static final double log2PI = Math.log(2 * Math.PI);

  private double xOffset;
  private double yOffset;

  int nSamples;
  double[][] samples;
  double[] weights;
  double[][] sampleWeightsMatrix;
  double[] residuals;
  int nVariables; // number of independent or "explanatory" variables
  int nDegOfFreedom;
  double[] beta;

  boolean areVarianceAndHatPrepped;

  double sigma2;  // Residual standard variance (sigma squared)
  double mlSigma2;
  double rss; // resisdual sum squares

  double effectiveDegOfF;
  RealMatrix hat;
  double traceHat;
  double traceHat2;
  double delta1;
  double delta2;

  private SurfaceModel model;

  /**
   * Standard constructor.
   */
  public SurfaceGwr() {
    beta = new double[0];
    nSamples = 0;
    sigma2 = Double.NaN;
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
   * <p>
   * The sample weights matrix is a two dimensional array giving
   * weights based on the distance between samples. It is used when performing
   * calculations for general statistics such as standard deviation,
   * confidence intervals, etc. Because of the high cost of initializing this
   * array, it can be treated as optional in cases where only the regression
   * coefficients are required.
   * <p>
   * A convenience routine for populating the sample weights matrix is
   * supplied by the initWeightsUsingGaussianKernal method.
   *
   * @param model the model to be used for the regression
   * @param xQuery x coordinate of the query position
   * @param yQuery y coordinate of the query position
   * @param nSamples the number of sample points to be used for regression
   * @param samples an array of dimension [n][3] giving at least nSamples
   * points with the x, y, and z values for the regression.
   * @param weights an array of weighting factors for samples
   * @param sampleWeightsMatrix an optional array of weights based on the
   * distances between different samples; if general statistics are
   * not required, pass a null value for this argument.
   * @return an array of regression coefficients, or null if the
   * computation failed.
   */
  @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
  public double[] computeRegression(
    SurfaceModel model,
    double xQuery,
    double yQuery,
    int nSamples,
    double[][] samples,
    double[] weights,
    double[][] sampleWeightsMatrix) {
    // clear out previous solutions
    areVarianceAndHatPrepped = false;
    this.model = model;
    this.sigma2 = Double.NaN;
    this.rss = Double.NaN;
    this.beta = null;
    this.hat = null;

    if (nSamples < model.getCoefficientCount()) {
      throw new IllegalArgumentException(
        "Insufficient number of samples for regression: found "
        + nSamples + ", need " + model.getCoefficientCount());
    }

    this.nSamples = nSamples;
    this.samples = samples;
    this.weights = weights;
    this.sampleWeightsMatrix = sampleWeightsMatrix;
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
        input[1][3] += w * xy * x;  // xy*x

        input[2][2] += w * y2;    // y*y
        input[2][3] += w * xy * y;  // xy*y

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

    RealMatrix matrixG = new BlockRealMatrix(g );
    RealMatrix matrixA = new BlockRealMatrix(input );

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
      beta = new double[nVariables + 1];
      for (int i = 0; i < beta.length; i++) {
        beta[i] = solution.getEntry(i, 0);
      }
    } catch (SingularMatrixException npex) {
      return null;
    }

    return beta;
  }

  public RealMatrix computeXWX(
    double xQuery,
    double yQuery,
    int nSamples,
    double[][] samples,
    double[] weights) {

    if (nSamples < model.getCoefficientCount()) {
      throw new IllegalArgumentException(
        "Insufficient number of samples for regression: found "
        + nSamples + ", need " + model.getCoefficientCount());
    }

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
      input = new double[10][10];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
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
      input = new double[6][6];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
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
      input = new double[5][5];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
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
      input = new double[3][3];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
        double w = weights[i];
        double x2 = x * x;
        double y2 = y * y;

        input[0][0] += w;
        input[0][1] += w * x;
        input[0][2] += w * y;

        input[1][1] += w * x2;
        input[1][2] += w * x * y;

        input[2][2] += w * y2;
      }

      // the input for a least-squares fit is a real-symmetric matrix.
      // So here the code assigns the symmetric terms.
      input[1][0] = input[0][1];
      input[2][0] = input[0][2];
      input[2][1] = input[1][2];
    } else if (model == SurfaceModel.PlanarWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x*y;
      input = new double[4][4];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
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
        input[1][3] += w * xy * x;  // xy*x

        input[2][2] += w * y2;    // y*y
        input[2][3] += w * xy * y;  // xy*y

        input[3][3] += w * xy * xy;  //xy*xy
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
      input = new double[7][7];
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0] - xQuery;
        double y = samples[i][1] - yQuery;
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

    return new BlockRealMatrix(input);

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
    SurfaceModel sm,
    double x0, double y0,
    int n, double[][] s) {
    double[][] matrix;
    if (sm == SurfaceModel.CubicWithCrossTerms) {
      matrix = new double[n][10];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
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
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
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
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
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
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
      }
    } else if (sm == SurfaceModel.PlanarWithCrossTerms) {
      //  z(x, y) = b0 + b1*x + b2*y + b3*x*y;
      matrix = new double[n][4];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
        double xy = x * y;
        matrix[i][0] = 1;
        matrix[i][1] = x;
        matrix[i][2] = y;
        matrix[i][3] = xy;
      }
    } else { // if(sm==SurfaceModel.Cubic){
      matrix = new double[n][7];
      for (int i = 0; i < n; i++) {
        double x = s[i][0] - x0;
        double y = s[i][1] - y0;
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

  public void computeVarianceAndHat() {

    if (areVarianceAndHatPrepped) {
      return;
    }
    areVarianceAndHatPrepped = true;

    if (sampleWeightsMatrix == null) {
      throw new NullPointerException("Null specification for sampleWeightsMatrix");
    } else if (sampleWeightsMatrix.length != nSamples) {
      throw new IllegalArgumentException("Incorrectly specified sampleWeightsMatrix");
    }
    double[][] bigS = new double[nSamples][nSamples];
    double[][] bigW = sampleWeightsMatrix;

    double[][] input = computeDesignMatrix(model, xOffset, yOffset, nSamples, samples);
    RealMatrix mX = new BlockRealMatrix(input );
    RealMatrix mXT = mX.transpose();

    // in the loop below, we compute
    //   Tr(hat)  and  Tr(Hat' x Hat)
    //   this second term is actually the square of the
    //   Frobenius Norm. Internally, the Apache Commons classes
    //   may provide a more numerically stable implementation of this operation.
    //   This may be worth future investigation.
    double sTrace = 0;
    double sTrace2 = 0;
    for (int i = 0; i < nSamples; i++) {
      DiagonalMatrix mW = new DiagonalMatrix(bigW[i]); //NOPMD
      RealMatrix mXTW = mXT.multiply(mW);
      RealMatrix rx = mX.getRowMatrix(i);
      RealMatrix c = mXTW.multiply(mX);
      QRDecomposition cd = new QRDecomposition(c); // NOPMD
      DecompositionSolver cdSolver = cd.getSolver();
      RealMatrix cInv = cdSolver.getInverse();
      RealMatrix r = rx.multiply(cInv).multiply(mXTW);
      double[] row = r.getRow(0);
      sTrace += row[i];
      System.arraycopy(row, 0, bigS[i], 0, nSamples);
      for (int j = 0; j < nSamples; j++) {
        sTrace2 += row[j] * row[j];
      }
    }

    hat = new BlockRealMatrix(bigS);
    traceHat = sTrace;
    traceHat2 = sTrace2;

    double[][] zArray = new double[nSamples][1];
    for (int i = 0; i < nSamples; i++) {
      zArray[i][0] = samples[i][2];
    }
    RealMatrix mY = new BlockRealMatrix(zArray);
    RealMatrix mYH = hat.multiply(mY);
    double sse = 0;
    for (int i = 0; i < nSamples; i++) {
      double yHat = mYH.getEntry(i, 0);
      double e = zArray[i][0] - yHat;
      sse += e * e;
    }
    rss = sse;

    double d1 = nSamples - (2 * traceHat - sTrace2);
    sigma2 = rss / d1;
    mlSigma2 = rss/nSamples;

    RealMatrix mIL = hat.copy();
    for (int i = 0; i < nSamples; i++) {
      double c = 1.0 - mIL.getEntry(i, i);
      mIL.setEntry(i, i, c);
    }
    RealMatrix mILT = mIL.transpose().multiply(mIL);
    delta1 = mILT.getTrace();
    delta2 = (mILT.multiply(mILT)).getTrace();

  }

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
//    ps.format("Regression coefficients & variance\n");
//    for (int i = 0; i < beta.length; i++) {
//      System.out.format("beta[%2d] %12.6f  %f\n",
//        i, beta[i], Math.sqrt(vcMatrix.getEntry(i, i) * sigma2));
//    }
    ps.format("Regression coefficients & variance\n");
    for (int i = 0; i < beta.length; i++) {
      ps.format("beta[%2d] %12.6f\n",
        i, beta[i]);
    }
    ps.format("Residual standard deviation %f on %d degrees of freedom\n",
      getStandardDeviation(), this.nDegOfFreedom);
    ps.format("Correlation coefficient (r^2): %f\n", getR2());
    ps.format("Adusted r^2:                   %f\n", getAdjustedR2());
    ps.format("F statistic:  %f\n", getF());

  }

  /**
   * Gets the computed polynomial coefficients from the regression
   * (the "beta" parameters that). These coefficients can be used
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
//    computeVarianceAndHat();
//    return r2;
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
  public double getVariance() {
    computeVarianceAndHat();
    return sigma2;
  }

  /**
   * Gets an unbiased estimate of the the standard deviation
   * of the residuals for the predicted values for all samples.
   *
   * @return if available, a positive real value; otherwise NaN.
   */
  public double getStandardDeviation() {
    computeVarianceAndHat();
    return Math.sqrt(sigma2);
  }

  /**
   * Gets the ML Sigma value used in the AICc calculation. This
   * value is the sqrt of the sum of the residuals squared divided by
   * the number of samples.
   *
   * @return in available, a positive real value; otherwise NaN.
   */
  public double getSigmaML() {
    computeVarianceAndHat();
    return Math.sqrt(mlSigma2);
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
    computeVarianceAndHat();
    return delta1;
  }

  /**
   * Get Leung's delta2 parameter.
   *
   * @return a positive value
   */
  public double getLeungDelta2() {
    computeVarianceAndHat();
    return delta2;
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
    computeVarianceAndHat();
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
    computeVarianceAndHat();
    //double effDegOfF = getEffectiveDegreesOfFreedom(); // should match delta1

    double[][] input = computeDesignMatrix(model, xOffset, yOffset, nSamples, samples);
    RealMatrix mX = new BlockRealMatrix(input );
    RealMatrix mXT = mX.transpose();

    // the weights array may not necessarily be of dimension nSamples,
    // so we need to copy it
    double[] rW = Arrays.copyOf(weights, nSamples);
    RealMatrix mW = new DiagonalMatrix(rW);
    RealMatrix design = mXT.multiply(mW).multiply(mX);
    RealMatrix vcm;
    try {
      QRDecomposition cd = new QRDecomposition(design);
      DecompositionSolver s = cd.getSolver();
      vcm = s.getInverse();
    } catch (SingularMatrixException npex) {
      return Double.NaN;
    }

    double nLeungDOF = (delta1 * delta1 / delta2);

    for (int i = 0; i < nSamples; i++) {
      rW[i] = weights[i] * weights[i];
    }

    DiagonalMatrix mW2 = new DiagonalMatrix(rW);
    RealMatrix mS = vcm.multiply(mXT).multiply(mW2).multiply(mX).multiply(vcm);
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
   *
   * @param alpha the significance level (typically 0&#46;.05, etc).
   * @return an array of dimension two giving the lower and upper bound
   * of the prediction interval.
   */
  public double[] getPredictionInterval(double alpha) {
    double h = getPredictionIntervalHalfRange(alpha);
    double a[] = new double[2];
    a[0] = beta[0] - h;
    a[1] = beta[0] + h;
    return a;
  }

  /**
   * Gets the number of degrees of freedom for the most recent computation
   * based on a ordinary least squares treatment (weighting neglected)
   *
   * @return if the most recent computation was successful, a positive
   * integer;
   * otherwise, a value &lt;= 0.
   */
  public int getDegreesOfFreedom() {
    computeVarianceAndHat();
    return nDegOfFreedom;
  }

  public RealMatrix getHatMatrix() {
    computeVarianceAndHat();
    return hat;
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
   // the following logic is based on Charlton and Fotheringham's
    // "Geographically Weighted Regression White Paper" (2009) which
    // is available on the web.  The authors give the equation
    //    AICc = 2*n*log(sigma) + n*log(2*PI) +  n * (n+tr(S))/(n-2-tr(S))
    // where
    //    n is the number of observations in the data set
    //    S is the hat matrix
    //    sigma is the "estimate of the standard deviation of the residuals"
    // When I first coded this routine, I assumed that by sigma,
    // Charlton and Fotheringham meant the unbiased estimate of "population"
    // standard deviation as computed in the computeVarianceAndHat() method
    // of this class.  However, in comparing the output of this method with
    // their GWR4 program, I was unable to match the results.  Fortunately,
    // GWR4 exposes a value it labels as "ML sigma" in its output text and
    // a little investigation showed that to be the value GWR4 was using in
    // its own calculations. The ML sigma is the "sample" standard deviation
    // (or the biased estimator for the population standard deviation).
    // By replacing sigma with ML sigma, I was able to get the output from this
    // method to consistently agree with the values computed by GWR4.
    //
    // The value of ML sigma (for "maximum likihood sigma"?) is just
    //    ML_sigma = sqrt(RSS/n)
    //    where RSS is the sum of the squared residuals (squared errors).
    //
    //   Statistics and information theory are not my areas of expertise,
    // but I have reviewed a number of web articles and I think that this use
    // (rather than the unbiased sigma) may actually be the correct
    // interpretation of the AICc defintion.
    //   In any case, I tested this change by comparing the prediction results
    // from Tinfour using automatic bandwidth selection (which depends on the
    // AICc value) against known checkpoint values.  The ML_sigma variation
    // appears to give a small improvement in the agreement of the
    // prediction to the checkpoint. So I am following the lead of the GWR4 team
    // and adopting the ML_sigma in the AICc computation.

    computeVarianceAndHat();
    double lv = Math.log(mlSigma2); // recall 2*log(sigma) is log(sigma^2)
    double x = (nSamples + traceHat) / (nSamples - 2 - traceHat);
    return nSamples * (lv + log2PI + x);
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
    samples = null;
    weights = null;
    residuals = null;
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

  /**
   * Evaluates the AICc score for the specified coordinates. This method
   * does not change the state of any of the member elements of this class.
   * It is intended to be used in the automatic bandwidth selection
   * operations implemented by calling classes.
   *
   * @param xQuery the x coordinate of the query point for evaluation
   * @param yQuery the y coordinate of the query point for evaluation
   * @param nSamples the number of samples.
   * @param samples an array of nSamples samples including x, y, and z.
   * @param weights an array of nSamples weights for each sample
   * @param lambda the bandwidth parameter for evaluation
   * @return if successful, a valid AICc; if unsuccessful, a NaN
   */
  double evaluateAICc(
    SurfaceModel sm,
    double xQuery,
    double yQuery,
    int nSamples,
    double[][] samples,
    double[] weights,
    double[][] sampleWeightsMatrix) {

    // RealMatrix xwx = computeXWX(xQuery, yQuery, nSamples, samples, weights);
    double[][] bigS = new double[nSamples][nSamples];
    double[][] bigW = sampleWeightsMatrix;

    double[][] input = computeDesignMatrix(sm, xQuery, yQuery, nSamples, samples);
    RealMatrix mX = new BlockRealMatrix(input);
    RealMatrix mXT = mX.transpose();

    // in the loop below, we compute
    //   Tr(hat)  and  Tr(Hat' x Hat)
    //   this second term is actually the square of the
    //   Frobenius Norm. Internally, the Apache Commons classes
    //   may provide a more numerically stable implementation of this operation.
    //   This may be worth future investigation.
    double traceS = 0;
    for (int i = 0; i < nSamples; i++) {
      DiagonalMatrix mW = new DiagonalMatrix(bigW[i]); //NOPMD
      RealMatrix mXTW = mXT.multiply(mW);
      RealMatrix rx = mX.getRowMatrix(i);
      RealMatrix c = mXTW.multiply(mX);
      QRDecomposition cd = new QRDecomposition(c); // NOPMD
      DecompositionSolver cdSolver = cd.getSolver();
      RealMatrix cInv;
      try {
        cInv = cdSolver.getInverse();
      } catch (SingularMatrixException | NullPointerException badMatrix) {
        return Double.NaN;
      }
      RealMatrix r = rx.multiply(cInv).multiply(mXTW);
      double[] row = r.getRow(0);
      traceS += row[i];
      System.arraycopy(row, 0, bigS[i], 0, nSamples); //NOPMD
    }

    RealMatrix mS = new BlockRealMatrix(bigS); // the Hat matrix

    double[][] zArray = new double[nSamples][1];
    for (int i = 0; i < nSamples; i++) {
      zArray[i][0] = samples[i][2];
    }
    RealMatrix mY = new BlockRealMatrix(zArray);
    RealMatrix mYH = mS.multiply(mY);
    double sse = 0;
    for (int i = 0; i < nSamples; i++) {
      double yHat = mYH.getEntry(i, 0);
      double e = zArray[i][0] - yHat;
      sse += e * e;
    }

    double mls2 = sse/nSamples;
    double lv = Math.log(mls2); // this is 2*log(sqrt(mls2))
    double x = (nSamples + traceS) / (nSamples - 2 - traceS);
    return nSamples * (lv + log2PI + x);
  }

  /**
   * Initializes an array of weights based on the distance of samples
   * from a specified pair of coordinates by using the Gaussian kernel.
   * This method is intended to support the initialization of weights
   * for a regression computation.
   * <p>
   * If Double.POSITIVE_INFINITY is passed as the bandwidth parameter,
   * all weights will be set uniformly to 1.0, which would be equivalent
   * to an Ordinary Least Squares regression.
   *
   * @param x the coordinate of the query point
   * @param y the coordinate of the query point
   * @param samples a two dimensional array giving (x,y) coordinates of the
   * samples
   * @param nSamples the number of samples
   * @param bandwidth the bandwidth parameter
   * @param weights an array to store the resulting weights
   */
  public void initWeightsUsingGaussianKernel(
    double x, double y, double[][] samples, int nSamples, double bandwidth, double[] weights) {
    if (Double.isInfinite(bandwidth)) {
      Arrays.fill(weights, 0, nSamples, 1.0);
    } else {
      double lambda2 = bandwidth * bandwidth;
      for (int i = 0; i < nSamples; i++) {
        double dx = samples[i][0] - x;
        double dy = samples[i][1] - y;
        double d2 = dx * dx + dy * dy;
        weights[i] = Math.exp(-0.5 * d2 / lambda2);
      }
    }
  }

  /**
   * Initializes a square matrix of weights based on the distance between
   * samples using the Gaussian kernel. Each ith row of the matrix is set
   * to the weights for samples based on their distance from the
   * ith sample. Thus the main diagonal of the matrix is based on the
   * distance of the sample from itself, and will be assigned a
   * weight of 1.0
   *
   * @param samples a two dimensional array giving (x,y) coordinates of the
   * samples
   * @param nSamples the number of samples
   * @param bandwidth the bandwidth parameter specification
   * @param matrix a square matrix of dimension nSamples to store the
   * computed weights.
   */
  public void initWeightsMatrixUsingGaussianKernel(
    double[][] samples, int nSamples, double bandwidth, double[][] matrix) {
    if (Double.isInfinite(bandwidth)) {
      for (int i = 0; i < nSamples; i++) {
        Arrays.fill(matrix[i], 0, nSamples, 1.0);
      }
    } else {
      double lambda2 = bandwidth * bandwidth;
      for (int i = 0; i < nSamples; i++) {
        double x = samples[i][0];
        double y = samples[i][1];
        for (int j = 0; j < i; j++) {
          double dx = samples[j][0] - x;
          double dy = samples[j][1] - y;
          double d2 = dx * dx + dy * dy;
          matrix[i][j] = Math.exp(-0.5 * d2 / lambda2);
        }
        matrix[i][i] = 1;
        for (int j = i + 1; j < nSamples; j++) {
          double dx = samples[j][0] - x;
          double dy = samples[j][1] - y;
          double d2 = dx * dx + dy * dy;
          matrix[i][j] = Math.exp(-0.5 * d2 / lambda2);
        }
      }
    }
  }

  /**
   * Indicates whether a sample weights matrix was set. Because the matrix
   * is an optional argument of the computeRegression method, it could
   * be set to a null value.
   *
   * @return true if the matrix is set; otherwise, false
   */
  public boolean isSampleWeightsMatrixSet() {
    return sampleWeightsMatrix != null;
  }

  /**
   * Allows an application to set the sample weights matrix.
   *
   * @param sampleWeightsMatrix a valid two dimensional array dimensions
   * to the same size as the number of samples.
   */
  @SuppressWarnings("PMD.ArrayIsStoredDirectly")
  public void setSampleWeightsMatrix(double[][] sampleWeightsMatrix) {
    this.sampleWeightsMatrix = sampleWeightsMatrix;
  }

}
