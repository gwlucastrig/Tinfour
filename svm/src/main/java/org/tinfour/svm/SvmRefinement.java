/* --------------------------------------------------------------------
 * Copyright (C) 2021  Gary W. Lucas.
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
 * 06/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.utils.KahanSummation;

/**
 * Implements logic to reduce the visibility of triangular artifacts
 * in contours by inserting supplemental vertices in large triangles.
 */
class SvmRefinement {

  NaturalNeighborInterpolator nni;
  int kIndex;

  /**
   * Subdivide the largest triangles in the collection.
   * <p>
   * The subdivision cut-off value indicates which triangles are to
   * be subdivided. For example, a value of 0.75 indicates that only
   * those triangles larger than 75 percent of all triangles (e.g. the
   * largest 25 percent) will be subdivided.
   *
   * @param ps a print stream for recording results
   * @param tin a valid, fully populated instance
   * @param subdivisionCutoff the cutoff given as a fraction in the range
   * 0.75 to 0.99 indicating which triangles are to be subdivided.
   * @return a valid, potentially empty, list of supplemental vertices.
   */
  List<Vertex> subdivideLargeTriangles(
    PrintStream ps,
    IIncrementalTin tin,
    double subdivisionCutoff) {
    double cutOff;
    if (subdivisionCutoff <= 0) {
      return new ArrayList<Vertex>();
    }
    if (subdivisionCutoff < 0.75) {
      cutOff = 75;
    } else if (subdivisionCutoff > 0.975) {
      cutOff = 0.99;
    } else {
      cutOff = subdivisionCutoff;
    }
    this.nni = new NaturalNeighborInterpolator(tin);
    kIndex = 0;

    int n = tin.getMaximumEdgeAllocationIndex();
    int k = 0;
    double[] areaArray = new double[2 * n];
    long time0 = System.nanoTime();
    KahanSummation asum = new KahanSummation();
    KahanSummation asum2 = new KahanSummation();
    for (SimpleTriangle trig : tin.triangles()) {
      IConstraint constraint = trig.getContainingRegion();
      if (constraint != null && constraint.definesConstrainedRegion()) {
        Object appData = constraint.getApplicationData();
        if (appData instanceof Boolean && (Boolean) appData) {
          double a = trig.getArea();
          if (a == 0) {
            continue;
          }
          asum.add(a);
          asum2.add(a * a);
          if (k < areaArray.length) {
            areaArray[k++] = a;
          }
        }
      }
    }

    Arrays.sort(areaArray, 0, k);
    int mIndex = (int) (cutOff * k);
    double areaThreshold = areaArray[mIndex];
    double areaMean = asum.getMean();
    double sumD2 = asum2.getSum();
    double sumD = asum.getSum();
    int nD = asum.getSummandCount();
    double areaStd = Math.sqrt((sumD2 - (sumD / nD) * sumD) / (nD - 1));

    ps.println("Analysis of triangles");
    ps.format("   Area mean    %15.5f%n", areaMean);
    ps.format("   Area std dev %15.5f%n", areaStd);
    ps.format("   Area thresh  %15.5f%n", areaThreshold);
    if (areaThreshold < 2 * areaMean) {
      areaThreshold = 2 * areaMean;
      for (int i = mIndex; i < k; i++) {
        if (areaArray[i] > areaThreshold) {
          mIndex = i;
          break;
        }
      }
    }

    int nPartitions = k - mIndex;
    ps.println("Subdividing approximately " + nPartitions + " triangles");
    ps.println("");
    ps.println("Population percentile and counts for triangles sorted by area.");
    ps.println("The area column is the base threshold for subset.");
    ps.println("So the 50% line would give the minimum area for all");
    ps.println("triangles in the upper 50 percentile");
    ps.println("Percentile     Area     Count");
    for (int i = 50; i < 100; i += 5) {
      mIndex = (int) ((i / 100.0) * k);
      ps.format("%3d %% %16.5f %8d%n", i, areaArray[mIndex], k - mIndex);
    }

    int partitionsPerLogEntry = nPartitions / 10;
    if (partitionsPerLogEntry == 0) {
      partitionsPerLogEntry = 1;
    }
    int kPartitions = 0;
    List<Vertex> vertices = new ArrayList<>();
    for (SimpleTriangle trig : tin.triangles()) {
      IConstraint constraint = trig.getContainingRegion();
      if (constraint != null && constraint.definesConstrainedRegion()) {
        Object appData = constraint.getApplicationData();
        if (appData instanceof Boolean && (Boolean) appData) {
          double a = trig.getArea();
          if (a < areaThreshold) {
            continue;
          }
          partitionTriangle(vertices,
            trig.getVertexA(),
            trig.getVertexB(),
            trig.getVertexC(),
            areaThreshold, 0);
          kPartitions++;
          if (kPartitions % partitionsPerLogEntry == 0) {
            double percentDone = kPartitions * 100.0 / nPartitions;
            System.out.format("Subdivided %8d triangles, %3.0f %% done%n", kPartitions, percentDone);
          }

        }
      }
    }

    int nAdded = vertices.size();
    long time1 = System.nanoTime();
    ps.println("Finished subdivision in "
      + ((time1 - time0) / 1.0e+6) + " ms, added " + nAdded + " vertices");

    return vertices;
  }

  private void partitionTriangle(List<Vertex> vList, Vertex A, Vertex B, Vertex C, double threshold, int level) {
    double area = computeArea(A, B, C);
    if (area > threshold) {
      double x = (A.getX() + B.getX() + C.getX()) / 3.0;
      double y = (A.getY() + B.getY() + C.getY()) / 3.0;
      double z = nni.interpolate(x, y, null);
      if (Double.isFinite(z)) {
        Vertex M = new Vertex(x, y, z, kIndex++);
        vList.add(M);
        if (level < 2) {
          partitionTriangle(vList, M, A, B, threshold, level + 1);
          partitionTriangle(vList, M, B, C, threshold, level + 1);
          partitionTriangle(vList, M, C, A, threshold, level + 1);
        }
      }
    }
  }

  private double computeArea(Vertex a, Vertex b, Vertex c) {
    return ((c.y - a.y) * (b.x - a.x) - (c.x - a.x) * (b.y - a.y)) / 2;
  }
}
