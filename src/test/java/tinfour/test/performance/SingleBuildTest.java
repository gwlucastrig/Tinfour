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
 * Date Name Description
 * ------    ---------    -------------------------------------------------
 * 03/2013   G. Lucas     Created for original Triangle-based implementation
 * 07/2015   G. Lucas     Refactored to use QuadEdge implementations
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.performance;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import tinfour.common.IIncrementalTin;
import tinfour.common.IIntegrityCheck;
import tinfour.common.Vertex;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.VertexLoader;

/**
 * Builds a TIN from the specified Lidar file, running once and terminating to
 * provide a simple test of the IncrementalTin logic.
 */
public class SingleBuildTest implements IDevelopmentTest {

  /**
   * Perform the test procedure for this implementation.
   *
   * @param args command line arguments providing specifications for test
   */
  public static void main(String args[]) {

    SingleBuildTest test = new SingleBuildTest();
    try {
      test.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }

  static String[] mandatoryOptions = {
    "-in"
  };

  @Override
  public void runTest(PrintStream ps, String args[]) throws IOException {
    if (args.length == 0) {

      ps.println("usage: TestSingleBuild");
      ps.println("   Mandatory Arguments:");
      ps.println("       -in <valid LAS file>");
      ps.println("   Optional Arguments:");
      ps.println("       -lidarClass      value in the range 0 to 255, defaults to not applied");
      ps.println("       -lidarThinning   thinnging factor, range 0 to 1.0, defaults to not applied");
      ps.println("       -clip            xmin, xmax, ymin, ymax");
      ps.println("       -prealloc, -noPrealloc  boolean, default noPreAlloc");
      ps.println("       -preSort,  -noPreSort   boolean, dfault noPreSort");

      return;
    }

    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    File target = options.getInputFile();
    boolean usePreSort = options.isPreSortEnabled(false);
    boolean usePreAlloc = options.isPreAllocateEnabled(false);

    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));

    Class<?> tinClass = options.getTinClass();

    long time0;
    long time1;

    long mem0 = getUsedMemory();
    List<Vertex> vertexList;
    VertexLoader loader = new VertexLoader();
    vertexList = loader.readInputFile(options);
    double timeForPreSort = loader.getTimeForPreSort();

    ps.println("");
    ps.println("Date of Test: " + sdFormat.format(date) + " UTC");
    ps.println("TIN class:    " + tinClass.getName());
    ps.println("Input File:   " + target);

    ps.println("Number of vertices in file (all classes): "
      + loader.getNumberOfVerticesInFile());
      int nVertices = vertexList.size();
    ps.format("Number of vertices to process: %8d\n", nVertices);
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
    double area = (xmax-xmin)*(ymax-ymin);
    double sSpace = 0.87738*Math.sqrt(area/nVertices);

    if (loader.isSourceInGeographicCoordinates()) {
      double geoScaleX = loader.getGeoScaleX();
      double geoScaleY = loader.getGeoScaleY();
      double geoOffsetX = loader.getGeoOffsetX();
      double geoOffsetY = loader.getGeoOffsetY();
      double gx0 = (xmin - geoOffsetX) / geoScaleX;
      double gx1 = (xmax - geoOffsetX) / geoScaleX;
      double gy0 = (ymin - geoOffsetY) / geoScaleY;
      double gy1 = (ymax - geoOffsetY) / geoScaleY;
      double gArea = (gx1 - gx0) * (gy1 - gy0);
      double gsSpace = 0.87738 * Math.sqrt(gArea / nVertices);

      ps.format("Source data was in geographic coordinates\n");
      ps.format("Range x values:     %11.6f, %11.6f, (%f)\n", gx0, gx1, gx1-gx0);
      ps.format("Range y values:     %11.6f, %11.6f, (%f)\n", gy0, gy1, gy1-gy0);
      ps.format("Est. sample spacing:   %e degrees of arc\n", gsSpace);
      ps.format("Geographic coordinates are mapped to projected coordinates\n");
    }

    ps.format("Range x values:     %12.3f, %12.3f, (%f)\n", xmin, xmax, xmax - xmin);
    ps.format("Range y values:     %12.3f, %12.3f, (%f)\n", ymin, ymax, ymax - ymin);
    ps.format("Range z values:     %12.3f, %12.3f, (%f)\n", zmin, zmax, zmax - zmin);
    ps.format("Est. sample spacing:%12.3f\n", sSpace);

    if (usePreSort) {
      ps.format("Time for pre-sort:          %8.2f\n", timeForPreSort);

    } else {
      ps.format("Pre-sort option is not used\n");
    }

    loader = null; // to promote garbage collcetion
    long mem1 = getUsedMemory();
    long vertexMemory = mem1 - mem0;
    long mem2;

    time0 = System.nanoTime();
    IIncrementalTin tin = options.getNewInstanceOfTestTin();

    long preAllocTime = 0;
    if (usePreAlloc) {
      tin.preAllocateEdges(nVertices);
      time1 = System.nanoTime();
      preAllocTime = (time1 - time0);
      ps.format("Time to pre-allocate edges: %8.2f\n", preAllocTime / 1000000.0);
    } else {
      ps.format("Pre-alloc is not used\n");
    }

    ps.println("Begin insertion");
    time0 = System.nanoTime();
    tin.add(vertexList, null);
    time1 = System.nanoTime();
    long buildTime = (time1 - time0);
    ps.format("Time build TIN:             %8.2f\n", buildTime / 1000000.0);
    ps.format("Total time for TIN:         %8.2f\n",
      (buildTime + preAllocTime) / 1000000.0);

    ps.println("Checking memory");
    mem2 = getUsedMemory();

    ps.println("");
    long tinMemory = (mem2 - mem1);
    double bytesPerVertex = (double) (tinMemory + vertexMemory) / (double) nVertices;
    double verticesOnly = (double) vertexMemory / (double) nVertices;
    double tinOnly = tinMemory / (double) nVertices;
    long totalMemory = Runtime.getRuntime().totalMemory();

    ps.println("Memory use (bytes/vertex) ");
    ps.format("   All objects:              %6.2f\n", bytesPerVertex);
    ps.format("   Vertices only:            %6.2f\n", verticesOnly);
    ps.format("   Edges and other elements: %6.2f\n", tinOnly);
    ps.format("\n");
    ps.format("Total for application (mb): %6.2f\n", totalMemory / 1024.0 / 1024.0);
    ps.format("\n\n");

    tin.printDiagnostics(System.out);

    ps.println("\nPerforming integrity check");
    IIntegrityCheck sane2 = tin.getIntegrityCheck();
    boolean status = sane2.inspect();
    if (!status) {
      ps.println("Integrity check failed " + sane2.getMessage());
      return;
    }
    ps.println("Integrity test passed");
    sane2.printSummary(ps);
    status = sane2.testGetVerticesAgainstInputList(vertexList);
    if(status){
      ps.println("Input vertex set matches output");
    }else {
      ps.println("Input/output vertex set don't match: " + sane2.getMessage());
      return;
    }
    ps.println("Test complete");

    tin.dispose();
  }

   private long getUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    // hint to Java that you want the garbage collector to run
    // and give it a little time to cooperate with you.
    runtime.gc();
    try {
      Thread.sleep(2000);
    } catch (InterruptedException ex) {
    }
    return runtime.totalMemory() - runtime.freeMemory();
  }

}
