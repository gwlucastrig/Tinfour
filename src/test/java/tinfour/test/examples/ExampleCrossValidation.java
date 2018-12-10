/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 03/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.examples;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import tinfour.common.IIncrementalTin;
import tinfour.common.Vertex;
import tinfour.gwr.BandwidthSelectionMethod;
import tinfour.gwr.SurfaceModel;
import tinfour.gwr.GwrTinInterpolator;
import tinfour.interpolation.NaturalNeighborInterpolator;
import tinfour.interpolation.TriangularFacetInterpolator;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TabulatorDelta;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.VertexLoader;
import tinfour.utils.TinInstantiationUtility;
import tinfour.utils.Tincalc;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
public class ExampleCrossValidation implements IDevelopmentTest {

  static String[] mandatoryOptions = {
    "-in"
  };

  /**
   * Provides the main method for an example application
   * that attempts to find out how well each point in a set of
   * sample inputs can be predicted by its neighbors. A cross
   * validation approach is used.
   * <p>
   * Data is accepted from an LAS file. A TIN is constructed. Then
   * one-at-a-time, each point in the tin is removed, and a predicted
   * value at its position is computed using various interpolators.
   * A error value is computed as the difference between the predicted
   * value and the true value. Errors are tabulated. The removed point is
   * restored. And the process continues to the next point.
   * <p>
   * Command line arguments include the following:
   * <pre>
   *   -in &lt;file path&gt;    input LAS file
   *   -frame xmin xmax ymin ymax frame for processing.
   *
   *    Other arguments used by Tinfour test programs are supported
   * </pre>
   *
   * @param args command line arguments indicating the input LAS file
   * for processing and various output options.
   */
  public static void main(String[] args) {
    ExampleCrossValidation example = new ExampleCrossValidation();

    try {
      example.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }

  /**
   * Run the example code accepting a set of input vertices and looping
   * through to perform cross validation, tabulating the results.
   *
   * @param ps a valid print-stream for recording results of processing.
   * @param args a set of arguments for configuring the processing.
   * @throws IOException if unable to read input or write output files.
   */
  @Override
  public void runTest(PrintStream ps, String[] args) throws IOException {
    Date date = new Date();
    SimpleDateFormat sdFormat =
       new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.format("Example Cross Validation%n");
    ps.format("Date/time of test: %s (UTC)%n", sdFormat.format(date));

    // Load Options ---------------------------------------------
    //   The TestOptions class is designed to provide a convenient way
    // of collecting the most commonly used options in the TinFour
    // test suite.
    //   Setting option values before performing an argumentScan
    // is a way of populating defaults.  The preSort option performs a
    // Hilbert sort on input samples. For lidar files, it is usually unnecessary
    // but for randomly generated samples or sets of files taken in a
    // highly random order, it can reduce construction time.
    // The lidar classification value of 2 restricts the input to samples
    // that have been classified as ground points
    TestOptions options = new TestOptions();
    options.setLidarClass(2);
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    double[] frame = options.getFrame();
    boolean isFrameSet = options.isFrameSet();

    Double marginArg = options.scanDoubleOption(args, "-margin", optionsMatched);
    if (marginArg == null) {
      marginArg = 10.0; // 10 percent reserve
    }

    boolean enableAutoBW
      = options.scanBooleanOption(args, "-autoBW", optionsMatched, false);
    boolean logProgress
      = options.scanBooleanOption(args, "-showProgress", optionsMatched, false);

    // if any non-recognized options were supplied, complain
    options.checkForUnrecognizedArgument(args, optionsMatched);

    // Load Vertices from LAS file ------------------------------------
    //   The vertex loader implements logic to use test options such as
    // those that indicate Lidar classification for processing
    // (ground points only, etc.) and sorting options.
    File inputFile = options.getInputFile();
    ps.format("Input file: %s%n", inputFile.getAbsolutePath());
    VertexLoader loader = new VertexLoader();
    List<Vertex> vertexList = loader.readInputFile(options);
    int nVertices = vertexList.size();
    ps.format("Number of vertices: %8d%n", nVertices);
    double xmin = loader.getXMin();
    double xmax = loader.getXMax();
    double ymin = loader.getYMin();
    double ymax = loader.getYMax();
    double zmin = loader.getZMin();
    double zmax = loader.getZMax();
    // estimate the point spacing.  The estimate is based on the simplifying
    // assumption that the points are arranged in a uniformly spaced
    // triangulated mesh (consisting of equilateral triangles). There would
    // be 3*N triangules of area s^2*sqrt(3)/4.
    double area = (xmax - xmin) * (ymax - ymin);
    double sSpace = Tincalc.sampleSpacing(area, nVertices);
    double nominalPointSpacing = sSpace; //used as an input into TIN class/

    double geoScaleX;
    double geoScaleY;
    double geoOffsetX;
    double geoOffsetY;
    if (loader.isSourceInGeographicCoordinates()) {
      geoScaleX = loader.getGeoScaleX();
      geoScaleY = loader.getGeoScaleY();
      geoOffsetX = loader.getGeoOffsetX();
      geoOffsetY = loader.getGeoOffsetY();
      double gx0 = (xmin-geoOffsetX) / geoScaleX;
      double gx1 = (xmax-geoOffsetX) / geoScaleX;
      double gy0 = (ymin-geoOffsetY) / geoScaleY;
      double gy1 = (ymax-geoOffsetY) / geoScaleY;
      double gArea = (gx1 - gx0) * (gy1 - gy0);
      double gsSpace = Tincalc.sampleSpacing(gArea, nVertices);

      ps.format("Source data was in geographic coordinates%n");
      ps.format("Range x values:     %11.6f, %11.6f, (%f)%n", gx0, gx1, gx1 - gx0);
      ps.format("Range y values:     %11.6f, %11.6f, (%f)%n", gy0, gy1, gy1 - gy0);
      ps.format("Est. sample spacing:   %e degrees of arc%n", gsSpace);
      ps.format("Geographic coordinates are mapped to projected coordinates%n");
    }

    ps.format("Range x values:     %12.3f, %12.3f, (%f)%n", xmin, xmax, xmax - xmin);
    ps.format("Range y values:     %12.3f, %12.3f, (%f)%n", ymin, ymax, ymax - ymin);
    ps.format("Range z values:     %12.3f, %12.3f, (%f)%n", zmin, zmax, zmax - zmin);
    ps.format("Est. sample spacing:%12.3f%n", sSpace);

    ps.flush();

    double x0;
    double y0;
    double x1;
    double y1;
    if (isFrameSet) {
      // a frame is specified
      x0 = frame[0];
      x1 = frame[1];
      y0 = frame[2];
      y1 = frame[3];
      ps.println("Using frame settings from supplied arguments");
      ps.format("Frame x values:     %12.3f, %12.3f, (%f)%n", x0, x1, (x1 - x0));
      ps.format("Frame y values:     %12.3f, %12.3f, (%f)%n", y0, y1, (y1 - y0));
    } else {
      double s = marginArg / 100.0;
      double dx = xmax - xmin;
      double dy = ymax - ymin;
      x0 = xmin + s * dx;
      x1 = xmin + (1 - s) * dx;
      y0 = ymin + s * dy;
      y1 = ymin + (1 - s) * dy;
      ps.println("Computing frame to allow a " + marginArg + " percent margin");
      ps.format("Frame x values:     %12.3f, %12.3f, (%f)%n", x0, x1, (x1 - x0));
      ps.format("Frame y values:     %12.3f, %12.3f, (%f)%n", y0, y1, (y1 - y0));
    }

    // Determine which TIN class to use  --------------------------
    //  The IncrementalTin class runs 60 percent faster than the
    // VirtualIncrementalTin class, but requires roughly twice as much
    // memory.  The TestOptions permit the argument vector to specify
    // which class is used, but if a specific choice is not supplied,
    // the following logic will use the TinInstantiationUtility to pick
    // a TIN class based on the nuber of vertices and the amount of memory
    // that the application is willing to apportion to the TIN.  Since this
    // example doesn't do much except build a TIN, we assign it 50 percent
    // of the available memory. This value would vary by application.
    IIncrementalTin tin;
    Class<?> tinClass;
    TinInstantiationUtility tiu = new TinInstantiationUtility(0.5, nVertices);
    if (options.isTinClassSet()) {
      tinClass = options.getTinClass();
    } else {
      ps.println(
        "Performing automatic selection of TIN class"
        + " based on memory and number of vertices");
      tiu.printSummary(ps);
      tinClass = tiu.getTinClass();
    }

    tin = tiu.constructInstance(tinClass, nominalPointSpacing);

    // Add vertices to TIN  -------------------------------------------
    //   Vertices can be added one-at-a-time or via a list.  In general,
    // the use of a list has a small performance and robustness advantage.
    // The first time either TIN class is used, the performance is
    // affected by the overhead of class loaders and the Just-in-Time.
    // With repeated use, throughput improves substantially.
    ps.format("%nBuilding TIN using: %s%n", tin.getClass().getName());
    long time0 = System.nanoTime();
    tin.add(vertexList, null);
    long time1 = System.nanoTime();
    ps.format("Time to process vertices (milliseconds):    %12.3f%n",
      (time1 - time0) / 1000000.0);

    NaturalNeighborInterpolator inNni = new NaturalNeighborInterpolator(tin);
    TriangularFacetInterpolator inTri = new TriangularFacetInterpolator(tin);
    GwrTinInterpolator inGwr = new GwrTinInterpolator(tin);
    // for the regression interpolator, we are going to experiment with some
    // slightly more advanced options
    SurfaceModel sm3 = SurfaceModel.CubicWithCrossTerms;

    BandwidthSelectionMethod bsmFixed = BandwidthSelectionMethod.FixedBandwidth;
    double bandwidth = nominalPointSpacing;

    BandwidthSelectionMethod bsmPro
      = BandwidthSelectionMethod.FixedProportionalBandwidth;


    // Construct some tabulators to keep track of our results
    TabulatorDelta tabNni = new TabulatorDelta();
    TabulatorDelta tabTri = new TabulatorDelta();
    TabulatorDelta tabFix = new TabulatorDelta();
    TabulatorDelta tabPro = new TabulatorDelta();
    TabulatorDelta tabAdp = new TabulatorDelta();
    SurfaceModel[] smValues = SurfaceModel.values();
    int[] adpModelCount = new int[smValues.length];
    TabulatorDelta tabProB = new TabulatorDelta();
    TabulatorDelta tabBdw = new TabulatorDelta(); // just used for avg. auto bandwidth
    TabulatorDelta tabBdwPro = new TabulatorDelta(); // used for avg. auto bandwidth scaled
    TabulatorDelta tabSampleCount = new TabulatorDelta();

    // The list of vertices contained in the TIN may be slightly differnt
    // than the input list.  The input list may have contained duplicates or
    // vertices with nearly identical horizontal coordinates.
    // So some of the vertices in the test list below may be merged groups.
    // Thus the cross validation process is not degraded by samples with
    // the same or nearly identical horizontal coordinates.
    ps.println("Performing cross validation");
    ps.println("Fixed bandwidth is " + bandwidth);

    vertexList = tin.getVertices();
    int nExpected = 0;
    for (Vertex v : vertexList) {
      double x = v.getX();
      double y = v.getY();
      if (x0 <= x && x <= x1 && y0 <= y && y <= y1) {
        nExpected++;
      }
    }
    ps.println("Number of expected tests " + nExpected);
    int progressModulus = 0;
    if(logProgress){
      progressModulus = (nExpected+19)/20;
    }
    int nTest = 0;
    int nOrdinary = 0;
    time0 = System.nanoTime();
    long timePrior = time0;
    for (Vertex v : vertexList) {
      double x = v.getX();
      double y = v.getY();
      double z = v.getZ();
      if (x0 <= x && x <= x1 && y0 <= y && y <= y1) {
        if (tin.remove(v)) { //NOPMD
          nTest++;
          inNni.resetForChangeToTin();
          inTri.resetForChangeToTin();
          inGwr.resetForChangeToTin();
          double zNni = inNni.interpolate(x, y, null);
          double zTri = inTri.interpolate(x, y, null);
          double zFix = inGwr.interpolate(sm3, bsmFixed, bandwidth, x, y, null);
          double zPro = inGwr.interpolate(sm3, bsmPro, 0.45, x, y, null);

          tabNni.tabulate(zNni - z);
          tabTri.tabulate(zTri - z);
          tabFix.tabulate(zFix - z);
          tabPro.tabulate(zPro - z);

          tabSampleCount.tabulate(inGwr.getSampleCount());

          // abulate the actual bandwidth selected by the
          // proportional bandwidth setting
          tabProB.tabulate(inGwr.getBandwidth() );

          if (enableAutoBW) {
            if(progressModulus>0 && (nTest%progressModulus)==0){
              int percentDone =  (100*nTest)/nExpected;
              time1 = System.nanoTime();
              double deltaT = (time1-timePrior)/1000000.0;
              timePrior = time1;
              double rate = progressModulus/deltaT;  // test per ms
              long estTimeRemaining = (long)((nExpected-nTest)/rate);
              Date estFinish = new Date(System.currentTimeMillis()+estTimeRemaining); //NOPMD
              System.out.format("Completed %3d%%   (%f per sec)    est finish %s%n",
                percentDone, rate*1000.0, estFinish.toString());
              System.out.flush();
            }
            double zAdp
              = inGwr.interpolateUsingAutomaticModelAndBandwidth(x, y, null);
            double autoBandwidth = inGwr.getBandwidth();
            if (Double.isInfinite(autoBandwidth)) {
              nOrdinary++;
            } else {
              double autoPro = autoBandwidth/inGwr.getSampleDistanceMean();
              tabBdw.tabulate(autoBandwidth);
              tabBdwPro.tabulate(autoPro);
            }
            SurfaceModel autoModel = inGwr.getSurfaceModel();
            int index = autoModel.ordinal();
            adpModelCount[index]++;
            tabAdp.tabulate(zAdp - z);
          }

          tin.add(v);
        }
      }
    }
    time1 = System.nanoTime();
    double deltaSeconds = ((double)(time1-time0)/1000000000.0);
    double testPerSec = nTest/deltaSeconds;

    ps.format("Tested %d of %d vertices (%3.2f/sec)%n", nTest, vertexList.size(), testPerSec);
    ps.format("Method                        "
      + "mean |err|  std dev |err|     range of err     sum err%n");
    tabTri.summarize(ps, "Triangular Facet         ");
    tabNni.summarize(ps, "Natural Neighbor         ");
    tabFix.summarize(ps, String.format("GWR, Fixed Bandwith %4.2f ", bandwidth));
    tabPro.summarize(ps, String.format("GWR, Proportionate  %4.2f ", 0.45));
    if (enableAutoBW) {
      tabAdp.summarize(ps, "GWR, Automatic BW AICc   ");
      ps.format("%nValues for automatically selected bandwidth");
      ps.format("   Mean:           %12.6f   (%8.6f of mean dist)%n",
               tabBdw.getMeanAbsValue(), tabBdwPro.getMeanAbsValue());
      ps.format("   Std Dev         %12.6f   (%8.6f)%n",
        tabBdw.getStdDevAbsValue(), tabBdwPro.getStdDevAbsValue());
      ps.format("   Min,Max:        %12.6f, %12.6f  (%5.3f, %5.3f of mean dist)%n",
        tabBdw.getMinValue(),
        tabBdw.getMaxValue(),
        tabBdwPro.getMinValue(),
        tabBdwPro.getMaxValue());
      ps.format("Number of Ordinary Least Squares: %d%n", nOrdinary);
      for (int i = 0; i < adpModelCount.length; i++) {
        SurfaceModel sm = smValues[i];
        ps.format("%-25.25s  %8d%n", sm.name(), adpModelCount[i]);
      }
      long nAdpTest = inGwr.getAutomaticBandwidthTestCount();
      double adpRate = (double)nAdpTest/adpModelCount.length/nTest;
      ps.format("Number of Automatic Bandwidth Iterations %d (%f/model/vertex)%n",
              nAdpTest, adpRate);
    }
    ps.format("%nValues for proportionately selected bandwidth%n");
    ps.format("Mean:    %12.6f%n", tabProB.getMeanAbsValue());
    ps.format("Std Dev: %12.6f%n", tabProB.getStdDevAbsValue());

    ps.format("%nNumber of samples used for GWR%n");
    ps.format("Mean:    %12.6f%n", tabSampleCount.getMeanAbsValue());
    ps.format("Std Dev: %12.6f%n", tabSampleCount.getStdDevAbsValue());
    ps.println("Example application processing complete.");

  }



}
