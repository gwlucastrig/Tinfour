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
import java.util.SimpleTimeZone;
import tinfour.common.IIncrementalTin;
import tinfour.common.Vertex;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.VertexLoader;

/**
 * Provides a parallel comparison of two different instances of the
 * Incremental TIN classes. Because a modern operating system is such a
 * noisy test environment, it is often difficult to isolate the effect
 * of different optimizations in an implementation. One never knows what
 * the system is doing at the same time one is performing a test.
 * To address this uncertainty, this class uses repeated tests to measure the
 * relative performance of two separate implementations. Alternately,
 * as a self-diagnostic, it can compare the relative performance of two
 * instances of the same class.
 */
public class TwinBuildTest implements IDevelopmentTest {

  // run test against self to establish that there is no bias
  // depending on the order in which builds are performed.
  static final String testClassA = "tinfour.standard.IncrementalTin";
  static final String testClassB = "tinfour.virtual.VirtualIncrementalTin";

  /**
   * Perform a simple test of the TIN building functions a fixed number of
   * times collecting timing statistics.
   *
   * @param args the command-line arguments
   */
  public static void main(String args[]) {

    TwinBuildTest test = new TwinBuildTest();
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
    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    File target = options.getInputFile();
    boolean usePreAlloc = options.isPreAllocateEnabled(false);
    int nTests = options.getTestCount(8);

    Class<?> classA, classB;
    try {
      ClassLoader classLoader = this.getClass().getClassLoader();
      classA = classLoader.loadClass(testClassA);
      classB = classLoader.loadClass(testClassB);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalArgumentException(
        "Error loading classes " + cnfe.getMessage(), cnfe);
    }

    ps.println("TwinBuildTest");

    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Date:    " + sdFormat.format(date));
    ps.println("Class A: " + classA.getCanonicalName());
    ps.println("Class B: " + classB.getCanonicalName());
    ps.println("Preallocation enabled: "+usePreAlloc);

    ps.println("Sample Data: " + target.getCanonicalPath());

    long time0;
    long time1;
    long sumTotalA = 0;
    long sumTotalB = 0;
    long maxA = 0;
    long maxB = 0;
    int nTotal = 0;

    List<Vertex> vertexListA, vertexListB;
    VertexLoader loader = new VertexLoader();
    vertexListA = loader.readInputFile(options);
    int nVertices = vertexListA.size();
    vertexListB = vertexListA;

    ps.println("\nTime for pre-sort " + loader.getTimeForPreSort());
    ps.println("Number of vertices  " + vertexListA.size());
    ps.println("\n" + sdFormat.format(date) + " UTC");

    ps.println(
      "run,          build1,   avg_build1,          build2,    avg_build2");

    IIncrementalTin tinA;
    IIncrementalTin tinB;

    int iAvg = 3;  // minimum index to start collecting average
    for (int iTest = 0; iTest < nTests; iTest++) {
      try {
        tinA = (IIncrementalTin) (classA.newInstance());
      } catch (InstantiationException | IllegalAccessException ex) {
        throw new IllegalArgumentException(
          "Error instantiating classes " + ex.getMessage(), ex);
      }
      if (usePreAlloc) {
        tinA.preAllocateEdges(nVertices);
      }
      time0 = System.nanoTime();
      tinA.add(vertexListA, null);

      time1 = System.nanoTime();
      long deltaBuildA = (time1 - time0);
      tinA.dispose();
      tinA = null;
      getUsedMemory(); // promotes garbage collection

      try {
        tinB = (IIncrementalTin) (classB.newInstance());
      } catch (InstantiationException | IllegalAccessException ex) {
        throw new IllegalArgumentException(
          "Error instantiating classes " + ex.getMessage(), ex);
      }
      if (usePreAlloc) {
        tinB.preAllocateEdges(nVertices);
      }
      time0 = System.nanoTime();
      tinB.add(vertexListB, null);
      // tin.insert(vertexList);
      time1 = System.nanoTime();
      long deltaBuildB = (time1 - time0);
      tinB.dispose();
      tinB = null;
      getUsedMemory(); // promotes garbage collection

      double avgTotalA = 0;
      double avgTotalB = 0;
      if (iTest >= iAvg) {
        nTotal = iTest - (iAvg) + 1;
        sumTotalA += deltaBuildA;
        avgTotalA = (double) sumTotalA / (double) nTotal;
        sumTotalB += deltaBuildB;
        avgTotalB = (double) sumTotalB / (double) nTotal;
        if (deltaBuildA > maxA) {
          maxA = deltaBuildA;
        }
        if (deltaBuildB > maxB) {
          maxB = deltaBuildB;
        }

      }

      ps.format("%3d,    %12.3f, %12.3f,     %12.3f, %12.3f\n",
        iTest,
        deltaBuildA / 1000000.0,
        avgTotalA / 1000000.0,
        deltaBuildB / 1000000.0,
        avgTotalB / 1000000.0);

    }

    if (nTotal > 1) {
      sumTotalA -= maxA;
      sumTotalB -= maxB;
      double avgTotalA = (double) sumTotalA / (double) (nTotal - 1);
      double avgTotalB = (double) sumTotalB / (double) (nTotal - 1);
      ps.format("avg with max removed  %12.3f,                   %12.3f\n",
        avgTotalA / 1000000.0,
        avgTotalB / 1000000.0);
      ps.println("");
      ps.println("comparitive time method a/b: " + avgTotalA / avgTotalB);
      ps.println("comparitive time method b/a: " + avgTotalB / avgTotalA);
    }
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
