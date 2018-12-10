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
 * 11/2015  G. Lucas     Created
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import tinfour.common.IIncrementalTin;
import tinfour.common.Vertex;
import tinfour.gwr.GwrTinInterpolator;
import tinfour.interpolation.IInterpolatorOverTin;
import tinfour.semivirtual.SemiVirtualIncrementalTin;
import tinfour.standard.IncrementalTin;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.VertexLoader;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
@SuppressWarnings("PMD.FieldDeclarationsShouldBeAtStartOfClassRule")
public class ExampleMultiThreadTest implements IDevelopmentTest {



  static String[] mandatoryOptions = {
    "-in"
  };

  /**
   * An arbitrary setting telling how much of the maximum
   * memory to commit for processing the TIN.
   */
  static final double MAX_MEMORY_FRACTION = 0.6;
  /**
   * Measured memory use by Hotspot JVM with a maximum
   * memory setting smaller than 32 gigabytes, using object
   * reference compression.
   */
  static final long MEMORY_FOR_VIRTUAL = 120L;

  /**
   * Measured memory use by Hotspot JVM with a maximum
   * memory setting smaller than 32 gigabytes,
   * using object reference compression.
   */
  static final long MEMORY_FOR_STANDARD = 240L;



  /**
   * A simple class that derives definitions for grid coordinates based on
   * the bounds of the area supplied by the LAS file or otherwise obtained
   * from
   * a collection of vertices.
   */
  class GridFromBounds {

    final double cellSize;
    final double xLowerLeft;
    final double yLowerLeft;
    final double xUpperRight;
    final double yUpperRight;
    final int nRows;
    final int nCols;
    final int nCells;

    /**
     * Constructs an instance based on the bounds of the bounds.
     * The grid coordinate points will lie inside or on the bounds and
     * be aligned on integral multiples of the cellSize. The cell
     * alignment will be treated as centered on the grid coordinate points.
     *
     * @param cellSize the dimension of the grid cell in the same coordinate
     * system as the bounds
     * @param xmin the minimum x coordinate for the area of interest
     * @param xmax the maximum x coordinate for the area of interest
     * @param ymin the minimum y coordinate for the area of interest
     * @param ymax the maximum y coordinate for the area of interest
     */
    GridFromBounds(double cellSize,
      double xmin,
      double xmax,
      double ymin,
      double ymax) {
      this.cellSize = cellSize;
      if (cellSize <= 0) {
        throw new IllegalArgumentException("Zero or negative cell size not allowed");
      }
      if (xmin >= xmax || ymin >= ymax) {
        throw new IllegalArgumentException("Min/max bounds incorrect");
      }
      int j0 = (int) Math.ceil(xmin / cellSize);
      int j1 = (int) Math.floor(xmax / cellSize);
      int i0 = (int) Math.ceil(ymin / cellSize);
      int i1 = (int) Math.floor(ymax / cellSize);
      nRows = (i1 - i0 + 1);
      nCols = (j1 - j0 + 1);
      nCells = nRows * nCols;
      if (nRows < 1 || nCols < 1) {
        throw new IllegalArgumentException(
          "Bounds are two small for specified cellSize");
      }
      xLowerLeft = j0 * cellSize;
      yLowerLeft = i0 * cellSize;
      xUpperRight = j1 * cellSize;
      yUpperRight = i1 * cellSize;
    }

  }

  /**
   * Run the example code accepting an input LAS file and writing an
   * output grid in Esri's ASCII raster format.
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
    ps.format("ExampleGridFromLasFile%n");
    ps.format("Date/time of test: %s (UTC)%n", sdFormat.format(date));

    // Load Options ---------------------------------------------
    //   The TestOptions class is designed to provide a convenient way
    // of collecting the most commonly used options in the TinFour
    // test suite.

    TestOptions options = new TestOptions();
    options.setLidarClass(2);
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    // process special options that are used by this example program:
    Double cellsizeArg
      = options.scanDoubleOption(args, "-cellSize", optionsMatched);
    double cellSize = 1.0; // the default
    if (cellsizeArg != null) {
      cellSize = cellsizeArg;
      if (cellSize <= 0) {
        throw new IllegalArgumentException("Invalid cell size: " + cellSize);
      }
    }

    int nThreads = options.scanIntOption(args, "-nThreads", optionsMatched, 1);
    int nTests = options.getTestCount(5);

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
    double xmin = loader.getXMin();
    double xmax = loader.getXMax();
    double ymin = loader.getYMin();
    double ymax = loader.getYMax();
    double zmin = loader.getZMin();
    double zmax = loader.getZMax();
    ps.format("Number of vertices: %8d%n", nVertices);
    ps.format("Range x values:     %11.3f, %11.3f, (%f)%n", xmin, xmax, xmax - xmin);
    ps.format("Range y values:     %11.3f, %11.3f, (%f)%n", ymin, ymax, ymax - ymin);
    ps.format("Range z values:     %11.3f, %11.3f, (%f)%n", zmin, zmax, zmax - zmin);
    ps.format("Grid cell size:     %11.3f%n", cellSize);
    ps.flush();

    GridFromBounds grid = new GridFromBounds(cellSize, xmin, xmax, ymin, ymax);
    ps.format("Output grid%n");
    ps.format("   Rows:              %8d%n", grid.nRows);
    ps.format("   Columns:           %8d%n", grid.nCols);
    ps.format("   Cells:             %8d%n", grid.nCells);
    ps.format("%n");
    ps.format("Number of threads:    %8d%n", nThreads);
    ps.format("Available processors: %8d%n",
      Runtime.getRuntime().availableProcessors());
    ps.format("Number of tests:      %8d%n", nTests);
    ps.format("%n");

    // Determine which TIN class to use  --------------------------
    //  The IncrementalTin class runs 60 percent faster than the
    // VirtualIncrementalTin class, but requires roughly twice as much
    // memory.  The TestOptions permit the argument vector to specify
    // which class is used, but if a specific choice is not supplied,
    // the following logic will attempt to use the IncrementalTin
    // class unless it exceeds a fixed fraction of the maximum allowed
    // memory.
    IIncrementalTin tin;
    if (options.isTinClassSet()) {
      tin = options.getNewInstanceOfTestTin();
    } else {
      // get max memory from Java's Runtime class
      long maxMemoryBytes = Runtime.getRuntime().maxMemory();
      double maxAllowedForUse = maxMemoryBytes * MAX_MEMORY_FRACTION;
      ps.format("Memory limit for JVM:                       %11.3f megabytes%n",
        maxMemoryBytes / 1024.0 / 1024.0);
      ps.format("Rule of thumb threshold for method choice:  %11.3f megabytes%n",
        maxAllowedForUse / 1024.0 / 1024.0);
      long nBytesNeededForStandard = nVertices * MEMORY_FOR_STANDARD;
      long nBytesNeededForVirtual = nVertices * MEMORY_FOR_VIRTUAL;
      ps.format("Memory required for standard edge class:    %11.3f megabytes%n",
        nBytesNeededForStandard / 1024.0 / 1024.0);
      ps.format("Memory required for virtual edge class:     %11.3f megabytes%n",
        nBytesNeededForVirtual / 1024.0 / 1024.0);
      if (nBytesNeededForStandard < maxAllowedForUse) {
        tin = new IncrementalTin();
      } else {
        ps.format("Virtual edge representation selected%n");
        tin = new SemiVirtualIncrementalTin();
      }
    }

    // Add vertices to TIN  -------------------------------------------
    ps.format("%nBuilding TIN using: %s%n", tin.getClass().getName());
    long time0 = System.nanoTime();
    tin.add(vertexList, null);
    long time1 = System.nanoTime();
    ps.format("Time to build TIN (milliseconds):  %11.3f%n",
      (time1 - time0) / 1000000.0);

    ThreadPoolExecutor executor
      = new ThreadPoolExecutor(nThreads, nThreads,
        1000, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>() {
          private static final long serialVersionUID=1;
      });

    // Build the elevation grid --------------------------------------
    ps.format("%n");
    ps.format("Performing time comparison tests for elevation grid%n%n");

    float[][] results1 = new float[grid.nRows][grid.nCols];
    float[][] resultsN = new float[grid.nRows][grid.nCols];

    ps.format("         Time to Process (ms)%n");
    ps.format("        Single        Multiple       Change (percent)%n");
    for (int iTest = 0; iTest < nTests; iTest++) {
      time0 = System.nanoTime();
      this.populateElevationGrid(tin, grid, 0, grid.nRows, results1);
      time1 = System.nanoTime();
      double delta1 = (time1-time0)/1000000.0;

      time0 = System.nanoTime();
      TestJobStatusBoard statusBoard = new TestJobStatusBoard(nThreads); //NOPMD
      int nRowsPerJob = (grid.nRows + nThreads - 1) / nThreads;
      for (int i = 0; i < nThreads; i++) {
        int row0 = i * nRowsPerJob;
        int nRow = nRowsPerJob;
        if (row0 + nRow > grid.nRows) {
          nRow = grid.nRows - row0;
        }
        TestJob job = new TestJob(tin, grid, row0, nRow, resultsN, statusBoard); //NOPMD
        executor.execute(job);
      }

      synchronized (statusBoard) {
        while (statusBoard.unfinishedJobsRemain()) {
          try {
            statusBoard.wait();
          } catch (InterruptedException iex) {

          }
        }
      }
      time1 = System.nanoTime();
      double deltaN = (time1 - time0) / 1000000.0;
      ps.format("%2d    %9.2f      %9.2f         %9.2f%n",
              iTest, delta1, deltaN, 100.0*deltaN/delta1);
      ps.flush();


    }
    int mismatches = 0;
    comparisonLoop:
    for (int iRow = 0; iRow < grid.nRows; iRow++) {
      for (int iCol = 0; iCol < grid.nCols; iCol++) {
        double delta = results1[iRow][iCol] - resultsN[iRow][iCol];
        double absDelta = Math.abs(delta);
        if (absDelta > 1.0e-16) {
          mismatches++;
          System.err.println("Mismatch "+mismatches+": "+ iRow + ", " + iCol + ": "
            + results1[iRow][iCol] + "-" + resultsN[iRow][iCol] + "=" + delta);
          //break comparisonLoop;
        }
      }
    }

    executor.shutdownNow();

  }

  /**
   * Populates a section of a 2 dimensional array of elevation values using the
   * specified interpolation method at each (x,y) coordinate specified by
   * the supplied grid object.
   *
   * @param tin a valid Triangulated Irregular Network populated with
   * vertices lying within the area specified by the grid object.
   * @param grid a grid object derived from the bounds of the vertex set
   * and the cell-spacing method specified at the command-line.
   * @param row0 the first row for processing
   * @param nRows the number of rows to process
   * @results a two-dimensional array to store the data.
   * @return if successful, a fully populated array of elevations.
   */
  void populateElevationGrid(
    IIncrementalTin tin,
    GridFromBounds grid,
    int row0,
    int nRows,
    float results[][]) {

    // for brevity, copy out values.
    int nCols = grid.nCols;
    double xLL = grid.xLowerLeft;
    double yUL = grid.yUpperRight;
    double cellSize = grid.cellSize;

    IInterpolatorOverTin interpolator = new GwrTinInterpolator(tin);

    for (int iRow = row0; iRow < row0 + nRows; iRow++) {
      // interpolator.resetForChangeToTin();
      float[] row = results[iRow];
      // the first row is at the top of the raster
      double yRow = yUL - iRow * cellSize;
      for (int iCol = 0; iCol < nCols; iCol++) {
        double xCol = iCol * cellSize + xLL;
        double z = interpolator.interpolate(xCol, yRow, null);
        if (Double.isNaN(z)) {
          row[iCol] = Float.NaN;
        } else {
          row[iCol] = (float) z;
        }
      }
    }
  }

  /**
   * Provides the main method for an example application
   * that demonstrates the use of concurrent processing when
   * building a raster data set from a TIN.
   * <p>
   * Data is accepted from an LAS file. For best results, the file
   * should be in a projected coordinate system rather than a geographic
   * coordinate system. In general, geographic coordinate systems are a
   * poor choice for Lidar data processing since they are non-isotropic,
   * however many data sources provide them in this form.
   * <p>
   * Command line arguments include the following:
   * <pre>
   *   -in &lt;file path&gt;    input LAS file
   *   -cellSize &lt;value &gt; 0, default 1&gt;    the cell size for the output
   *                      grids. cell size should be consistent with
   *                      nominal pulse spacing of LAS file ground points
   *   -nThreads &lt;value &gt; 0, default 2&gt;    the number of threads
   *                      to be used for processing the grid
   *    Other arguments used by Tinfour test programs are supported
   * </pre>
   *
   * @param args command line arguments indicating the input LAS file
   * for processing and various output options.
   */
  public static void main(String[] args) {
    ExampleMultiThreadTest example = new ExampleMultiThreadTest();

    try {
      example.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }


   private class TestJobStatusBoard {

    final int nJobs;
    int nJobsDone;

    TestJobStatusBoard(int nJobs) {
      this.nJobs = nJobs;
    }

    void checkIn() {
      synchronized (this) {
        nJobsDone++;
        if (nJobsDone == nJobs) {
          this.notifyAll();
        }
      }
    }

    boolean unfinishedJobsRemain() {
      synchronized (this) {
        return nJobsDone < nJobs;
      }
    }

  }

  private class TestJob implements Runnable {

    final IIncrementalTin tin;
    final GridFromBounds grid;
    final int row0;
    final int nRow;
    final float[][] results;
    final TestJobStatusBoard statusBoard;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    TestJob(
      IIncrementalTin tin,
      GridFromBounds grid,
      int row0,
      int nRow,
      float[][] results,
      TestJobStatusBoard statusBoard)
    {
      this.tin = tin;
      this.grid = grid;
      this.row0 = row0;
      this.nRow = nRow;
      this.results = results;
      this.statusBoard = statusBoard;
    }

    @Override
    public void run() {
      populateElevationGrid(tin, grid, row0, nRow, results);
      statusBoard.checkIn();
    }

  }
}
