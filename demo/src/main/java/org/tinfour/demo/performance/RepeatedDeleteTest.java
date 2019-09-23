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
 * 11/2015   G. Lucas     Expanded to support virtual edge implementations
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.performance;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.Vertex;
import org.tinfour.common.VertexMergerGroup;
import org.tinfour.demo.utils.IDevelopmentTest;
import org.tinfour.demo.utils.TestOptions;
import org.tinfour.demo.utils.VertexLoader;

/**
 * A test application that repeatedly builds a TIN and then removes points
 * tracking the time required for each iteration.
 */
public class RepeatedDeleteTest implements IDevelopmentTest {

  /**
   * Perform a simple test of the TIN building and vertex removal functions over
   * a fixed number of repetitions collecting timing statistics.
   *
   * @param args command line arguments providing specifications for test
   */
  public static void main(String args[]) {

    RepeatedDeleteTest test = new RepeatedDeleteTest();
    try {
      test.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }

  static final String[] mandatoryOptions = {
    "-in"
  };

  static final String[] usage = {
    "Usage for Test Repeated Deletion and Reinsert",
    "   Mandatory Arguments:",
    "       -in <valid LAS, CSV, or TXT file>",
    "   Optional Arguments:",
    "       -lidarClass      value in the range 0 to 255, defaults to not applied",
    "       -lidarThinning   thinnging factor, range 0 to 1.0, defaults to not applied",
    "       -clip            xmin, xmax, ymin, ymax",
    "       -prealloc, -noPrealloc  boolean, default noPreAlloc",
    "       -preSort,  -noPreSort   boolean, dfault noPreSort",
    "       -tinClass <class>   path of class for testing, defaults to IncrementalTin"
  };

  @Override
  public void runTest(PrintStream ps, String args[]) throws IOException {
    if (args.length == 0) {
      for (String s : usage) {
        ps.println(s);
      }
      return;
    }

    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);

    Class<?> tinClass = options.getTinClass();

    boolean usePreAlloc = options.isPreAllocateEnabled(false);
    int nTests = options.getTestCount(8);

    File input = options.getInputFile();
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));

    long time0;
    long time1;
    long sumTotal = 0;
    long maxTime = 0;
    int nTotal = 0;

    long mem0 = getUsedMemory();
    List<Vertex> vertexList;
    VertexLoader loader = new VertexLoader();
    vertexList = loader.readInputFile(options);
    double timeForPreSort = loader.getTimeForPreSort();
    int nVertices = vertexList.size();
    long mem1 = getUsedMemory();
    long vertexMemory = mem1 - mem0;
    long mem2 = mem1;
    long memTotal;

    ps.println("");
    ps.println("Date of test:       " + sdFormat.format(date) + " UTC");
    ps.println("Input file:         " + input.getAbsolutePath());
    ps.println("TIN class:          " + tinClass.getName());
    ps.println("Time for pre-sort   " + timeForPreSort);
    ps.println("Number of vertices  " + vertexList.size());

    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    ps.println("run,        build,    avg_build,     total_mem,   delete test time");

    int iAvg = 3;  // minimum index to start collecting average
    for (int iTest = 0; iTest < nTests; iTest++) {
      tin = options.getNewInstanceOfTestTin();
      if (usePreAlloc) {
        tin.preAllocateEdges(nVertices);
      }
      time0 = System.nanoTime();
      tin.add(vertexList, null);
      time1 = System.nanoTime();
      long deltaBuild = (time1 - time0);

      List<Vertex> testList = tin.getVertices();
      time0 = System.nanoTime();
      for (Vertex v : testList) {
        if (v instanceof VertexMergerGroup) {
          continue;
        }
        tin.remove(v);
        tin.add(v);
      }
      time1 = System.nanoTime();
      long deltaDelete = (time1 - time0);

      double avgTotal = 0;
      if (iTest >= iAvg) {
        nTotal = iTest - (iAvg) + 1;
        sumTotal += deltaBuild;
        avgTotal = (double) sumTotal / (double) nTotal;
        if (deltaBuild > maxTime) {
          maxTime = deltaBuild;
        }
      }

      if (iTest == 0) {
        mem2 = getUsedMemory();
        memTotal = mem2;
      } else {
        memTotal = getUsedMemory();
      }
      if (iTest < nTests - 1) {
        tin.dispose();
      }

      ps.format("%3d, %12.3f, %12.3f, %12.3f, %12.3f%n",
              iTest,
              deltaBuild / 1000000.0,
              avgTotal / 1000000.0,
              memTotal / 1048576.0,
              deltaDelete / 1.0e+6);
    }
    if (nTotal > 1) {
      double avgTotal = (sumTotal - maxTime) / (nTotal - 1);
      ps.format("Avg max removed:   %12.3f%n", avgTotal / 1000000.0);
    }
    ps.println("");
    long deltaMemory = (mem2 - mem1);
    double bytesPerVertex = (double) (deltaMemory + vertexMemory) / (double) nVertices;
    double verticesOnly = (double) vertexMemory / (double) nVertices;

    ps.println("");
    ps.println("Stats for last run in series");
    ps.println("Vertices added to TIN:    " + nVertices);
    ps.println("Memory use (bytes/vertex) ");
    ps.format("   All objects:           %6.2f%n", bytesPerVertex);
    ps.format("   Vertices only:         %6.2f%n", verticesOnly);
    //tin.printDiagnostics(System.out);

    ps.println("");
    ps.println("Performing integrity check");
    IIntegrityCheck sane2 = tin.getIntegrityCheck();
    boolean status = sane2.inspect();
    if (!status) {
      ps.println("Integrity check failed " + sane2.getMessage());
    }
    tin.printDiagnostics(ps);
    ps.println("Test complete");
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
