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
 * 09/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.test.performance;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.SimpleTimeZone;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.demo.utils.IDevelopmentTest;
import org.tinfour.demo.utils.TestOptions;
import org.tinfour.demo.utils.VertexLoader;
import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasPoint;
 

/**
 * Performs time test on randomly selected subsets of the samples from an input
 * file. The subsets vary by size, providing a means of assessing the speed of
 * the class as a function of sample size.
 */
public class TimeDueToSampleSize implements IDevelopmentTest {

  private static class RecordFilter implements ILasRecordFilter {

    int classification;
    double thinningFactor;
    Random random = new Random();

    /**
     * Implement a thinning filter.
     *
     * @param classification only accept points of the designated
     * classification (or -1 for wildcards).
     * @param thinningFactor the fraction of the sample points to accept
     * (1.0 to include all sample points).
     */
    public RecordFilter(int classification, double thinningFactor) {
      this.classification = classification;
      this.thinningFactor = thinningFactor;
    }

    @Override
    public boolean accept(LasPoint record) {
      // on the theory that withheld records are relatively uncommon
      // test on classification first
      if (record.withheld) {
        return false;
      }
      if (classification >= 0 && record.classification != classification) {
        return false;
      }
      if (thinningFactor == 1) {
        return true;
      }
      double test = random.nextDouble();
      return test < thinningFactor;

    }
 
  }

  private static class Result implements Comparable<Result> {

    double timeMS;
    long nVertices;

    Result(double timeMS, long nVertices) {
      this.timeMS = timeMS;
      this.nVertices = nVertices;

    }

    @Override
    public int compareTo(Result o) {
      int test = Long.compare(nVertices, o.nVertices);
      if (test != 0) {
        return test;
      }
      return Double.compare(timeMS, o.timeMS);
    }

  }

  /**
   * Perform a simple test of the TIN building functions a fixed number of
   * times collecting timing statistics.
   *
   * @param args not used
   */
  public static void main(String args[]) {
    PrintStream ps = System.out;

    TimeDueToSampleSize test = new TimeDueToSampleSize();
    try {
      test.runTest(ps, args);
    } catch (IOException | IllegalArgumentException ex) {
      ps.println("Exception caught performing test " + ex.getMessage());
      ex.printStackTrace(ps);
    }

  }

private static final String[] usage = {
    "usage: TimeDueToSampleSize",
    "   Mandatory Arguments:",
    "       -in <valid LAS file or sample-point text file>",
    "   Optional Arguments:",
    "       -tinClass <class>  the full path of the class to be tested",
    "                          for example: tinfour.standard.IncrementalTin",
    "       -lidarClass <int> value in the range 0 to 255, default: not applied",
    "       -preallocate, -noPreallocate  boolean, default noPreAlloc",
    "                                     preallocation permits a test to",
    "                                     separate the cost of object creation",
    "                                     from the algorithm-based",
    "                                     cost of processing",
    "       -preSort,  -noPreSort         boolean, dfault noPreSort",
    "       -randomSize <float> randomly select a subset of points. If supplied",
    "                           the thinning factor must be greater than zero",
    "                           and less than or equal to 1.0. The most",
    "                           common value is 1.0, though smaller values may",
    "                           be used when an input file is so large that",
    "                           it becomes unweildy.",
    "                           The thinning factor is used to randomly ",
    "                           select a subset of points so that the effect",
    "                           of relative sample sizes can be assessed.",
    "                           When not supplied, the full set of vertices is",
    "                           processed by each iteration."
  };


  @Override
  public void runTest(PrintStream ps, String[] args) throws IOException {
    if (args.length == 0) {
      for(String s: usage){
          ps.println(s);
      }
      return;
    }
    TestOptions options = new TestOptions();
    boolean[] recognized = options.argumentScan(args);
    File target = options.getInputFile();

    Class<?> tinClass = options.getTinClass();

    Double randomSize = options.scanDoubleOption(args, "-randomSize", recognized);
    if (randomSize != null && (randomSize <= 0 || randomSize > 1.0)) {
      throw new IllegalArgumentException(
        "Random size " + randomSize
        + " is not in valid range >0 to 1.0");
    }


    options.checkForUnrecognizedArgument(args, recognized);

    long seed = options.getRandomSeed(0);
    Random random = new Random(seed);

    int classification = options.getLidarClass();
    int nTests = options.getTestCount(10);

    boolean prealloc = options.isPreAllocateEnabled(false);
    boolean presort = options.isPreSortEnabled(false);
    List<Vertex> masterList;
    VertexLoader loader = new VertexLoader();
    loader.setPreSortEnabed(true);
    masterList = loader.readInputFile(options);
      int nMasterVertices = masterList.size();

    Date date = new Date();
    SimpleDateFormat sdFormat =
      new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    String dateString = sdFormat.format(date);
    ps.println("Date of test:     " + dateString);
    ps.println("TIN Class:        " + tinClass.getName());
    ps.println("File:             " + options.getInputFile().getCanonicalPath());
    ps.println("Random size:      "
      + (randomSize == null
        ? "Unspecified, Use full size"
        : "0 to " + Double.toString(randomSize)));
    ps.println("Lidar class:      " + classification);
    ps.println("Pre-allocate:     " + prealloc);
    ps.println("Pre-sort:         " + presort);
    ps.println("Seed:             " + seed);
    ps.println("N Tests:          " + nTests);
    ps.println("Max Samples:      " + nMasterVertices);
    ps.println("");



    // The pre-test.  We load the Tin a few of times to make sure the
    // class loader and JIT have completed their initialization.
  
    for (int iPretest = 0; iPretest < 3; iPretest++) {
        ps.println("Running Pre-Test "+iPretest);
      IIncrementalTin tin = options.getNewInstanceOfTestTin();
      if (tin == null) {
        return;
      }
      tin.add(masterList,  null);
      tin.dispose();
      getUsedMemory();
    }

    long time0;
    long time1;

    long mem0 = getUsedMemory();
    ps.println("Pre-test complete, memory=" + mem0 / 1024.0 / 1024.0);

    ArrayList<Result> resultList = new ArrayList<>();
    ps.println(
      "   n_vertices,   m_vertices, time_ms,    used_mb,     total_mb,  used_bytes");
    for (int iThin = 1; iThin <= nTests; iThin++) {
      mem0 = getUsedMemory();
      double thinningFactor = 1.0;
      if (randomSize != null && randomSize > 0) {
        thinningFactor = randomSize * random.nextDouble();
      }
      if(thinningFactor<0.025){
        thinningFactor = 0.025;
      }
      List<Vertex>vertexList = new ArrayList<>();
      int skip = (int)Math.ceil(1.0/thinningFactor);
      for(int i=0; i<nMasterVertices; i+=skip){
        vertexList.add(masterList.get(i));
      }
      int nVertices = vertexList.size();
      IIncrementalTin tin = options.getNewInstanceOfTestTin();
      if (prealloc) {
        tin.preAllocateEdges(nVertices);
      }
      time0 = System.nanoTime();
      tin.add(vertexList, null);
      time1 = System.nanoTime();
      long deltaBuild = (time1 - time0);

      long totalMem = Runtime.getRuntime().totalMemory();
      vertexList.clear();
      vertexList = null;
      long usedMem = getUsedMemory() - mem0;
      tin.dispose();
      tin = null;
      getUsedMemory();
      double buildTime = deltaBuild / 1000000.0;
      resultList.add(new Result(buildTime, nVertices)); // NOPMD
      ps.format("%10d,  %10.7f, %10.2f,  %10.2f, %10.2f, %12d%n",
        nVertices,
        nVertices / 1000000.0,
        deltaBuild / 1000000.0,
        usedMem / 1024.0 / 1024.0,
        totalMem / 1024.0 / 1024.0,
        usedMem);
      ps.flush();

    }

    Collections.sort(resultList);
    ps.format("%nn_vertices,    m_vertices,    time_ms,     million_per_sec%n");
    for (Result r : resultList) {
      ps.format(
        "%10d, %10.3f, %10.3f, %10.3f\n",
        r.nVertices, r.nVertices / 1000000.0, r.timeMS,
        (r.nVertices / 1000000.0) / (r.timeMS / 1000));
    }
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
