/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 07/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tinfour.common.Vertex;
import tinfour.common.VertexMergerGroup;

/**
 * Provides a utility for the efficient identification of the
 * K-nearest points to a specified set of query coordinates.
 * This utility works by creating a grid of bins into which
 * the vertices are initially separated. The bins are then
 * searched to find the nearest vertices.
 * <p>
 * This class is intended to support testing and verification.
 * It has no direct relationship with any of the graph-based structures
 * produced by the Tinfour library.
 * <p>
 * The design of this class is optimized for repeated searches
 * of very large vertex sets. While the up front processing is not
 * trivial, the time cost is compensated for by more efficient processing
 * across multiple searches.
 */
public class NearestNeighborPointCollector {

  private static final int TARGET_SAMPLES_PER_BIN = 200;
  private static final int MAX_BIN_COUNT = 10000;

  final int nBins;

  final Vertex[][] bins;
  final double xmin;
  final double xmax;
  final double ymin;
  final double ymax;

  final double sBin;
  final int nRow;
  final int nCol;
  final int nTier;

  VertexMergerGroup.ResolutionRule resolutionRule
    = VertexMergerGroup.ResolutionRule.MinValue;

  /**
   * Construct a collector based on the specified list of vertices and
   * bounds. It is assumed that the coordinates and values of all specifications
   * are valid floating-point values (no NaN's included).
   *
   * @param vList a list of valid vertices
   * @param mergeDuplicates indicates whether duplicates should be merged.
   */
  public NearestNeighborPointCollector(List<Vertex> vList, boolean mergeDuplicates) {
    Vertex a = vList.get(0);
    double x0 = a.getX();
    double x1 = a.getX();
    double y0 = a.getY();
    double y1 = a.getY();
    for (Vertex v : vList) {
      double x = v.getX();
      double y = v.getY();
      if (x < x0) {
        x0 = x;
      } else if (x > x1) {
        x1 = x;
      }
      if (y < y0) {
        y0 = y;
      } else if (y > y1) {
        y1 = y;
      }
    }

    this.xmin = x0;
    this.xmax = x1;
    this.ymin = y0;
    this.ymax = y1;
    double xDelta = xmax - xmin;
    double yDelta = ymax - ymin;
    int nV = vList.size();
    double nBinEst = (double) nV / (double) TARGET_SAMPLES_PER_BIN;
    if (nBinEst < 1) {
      // just make one big bin
      sBin = 1.01 * Math.max(xDelta, yDelta);
      nRow = 1;
      nCol = 1;
      nTier = 0;
    } else {
      if (nBinEst > MAX_BIN_COUNT) {
        nBinEst = MAX_BIN_COUNT;
      }
      double area = xDelta * yDelta;
      sBin = Math.sqrt(area / nBinEst);
      nRow = (int) Math.ceil(yDelta / sBin + 1.0e-4);
      nCol = (int) Math.ceil(xDelta / sBin + 1.0e-4);
      nTier = nRow > nCol ? nRow : nCol;
    }

    nBins = nRow * nCol;

    // 0. allocate an array to store counts.
    // 1. perform a pass to count up vertices per bin
    // 2. allocate storage for each bin based on count
    // 3. perform a pass to insert vertices.  If configured
    //    to screen duplicates, check for cases
    //    where a vertex has the same coordinates as
    //    an existing vertex. When that happens create a merger group.
    // 4. Resize arrays for bin storage in cases where duplicates were found.
    // 5. When complete, the iCount array is no longer needed and will be
    //    allowed to go out-of-scope.
    int[] iCount = new int[nBins];
    for (Vertex v : vList) {
      double x = v.getX();
      double y = v.getY();
      int iRow = (int) ((y - ymin) / sBin);
      int iCol = (int) ((x - xmin) / sBin);
      iCount[iRow * nCol + iCol]++;
    }
    bins = new Vertex[nBins][];
    for (int i = 0; i < nBins; i++) {
      bins[i] = new Vertex[iCount[i]]; // NOPMD
      iCount[i] = 0;
    }

    if (mergeDuplicates) {
      // the merge threshold is 1/10000th of the average spacing
      double averageSpacing = Math.sqrt(xDelta * yDelta / (nV * 0.866));
      double mergeThreshold = averageSpacing / 1.0e+5;
      double m2 = mergeThreshold * mergeThreshold;
      boolean mergeFound = false;
      collectionLoop:
      for (Vertex v : vList) {
        double x = v.getX();
        double y = v.getY();
        int iRow = (int) ((y - ymin) / sBin);
        int iCol = (int) ((x - xmin) / sBin);
        int index = iRow * nCol + iCol;
        int n = iCount[index];
        Vertex[] b = bins[index];
        for (int i = 0; i < n; i++) {
          double dx = x - b[i].getX();
          double dy = y - b[i].getY();
          if (dx * dx + dy * dy < m2) {
            mergeFound = true;
            VertexMergerGroup g;
            if (b[i] instanceof VertexMergerGroup) {
              g = (VertexMergerGroup) b[i];
            } else {
              g = new VertexMergerGroup(b[i]); // NOPMD
              g.setResolutionRule(resolutionRule);
            }
            g.addVertex(v);
            continue collectionLoop;
          }
        }
        b[n] = v;
        iCount[index]++;
      }
      if (mergeFound) {
        // any bins that had merges will now have fewer items
        // in their arrays than were originally allocated.
        // resize the arrays.
        for (int i = 0; i < nBins; i++) {
          if (iCount[i] < bins[i].length) {
            bins[i] = Arrays.copyOf(bins[i], iCount[i]);
          }
        }
      }
    } else {
      // no mergers are required, just add to appropriate bin.
      for (Vertex v : vList) {
        double x = v.getX();
        double y = v.getY();
        int iRow = (int) ((y - ymin) / sBin);
        int iCol = (int) ((x - xmin) / sBin);
        int index = iRow * nCol + iCol;
        bins[index][iCount[index]] = v;
        iCount[index]++;
      }
    }
  }

  /**
   * Sets the rule for resolving coincident vertices; recalculates
   * value for vertices in the collection, if necessary
   *
   * @param rule a valid member of the enumeration
   */
  public void setResolutionRule(VertexMergerGroup.ResolutionRule rule) {
    if (rule == null || rule == this.resolutionRule) {
      return;
    }
    this.resolutionRule = rule;
    for (int i = 0; i < nBins; i++) {
      Vertex[] vArray = bins[i];
      for (Vertex v : vArray) {
        if (v instanceof VertexMergerGroup) {
          ((VertexMergerGroup) v).setResolutionRule(rule);
        }
      }
    }
  }

  /**
   * Given a row/column index, constrains it to the
   * range of available bins
   *
   * @param index the computed coordinate index
   * @param n the number of available bins
   * @return a value in the range 0 to n-1
   */
  private int limitedIndex(int index, int n) {
    if (index < 0) {
      return 0;
    } else if (index >= n) {
      return n - 1;
    } else {
      return index;
    }
  }

  /**
   * Indicates whether the specified bin could possibly contain a point
   * that is closer than the maximum-distance point yet located.
   * <p>
   * If fewer than k points have been collected when this method is
   * called, it always returns a value of true. Since the k nearest
   * points have not yet been collected, there is no limitation on the
   * distance range for valid candidates.
   *
   * @param n the number of points collected so far
   * @param k the number of points to be collected
   * @param x the x coordinate of the query position
   * @param y the y coordinate of the query position
   * @param iRow the row of the bin
   * @param iCol the column of the bin
   * @param d the array of distances for samples collected so far (if any)
   * @return true if the bin needs to be searched; otherwise, false
   */
  private boolean isBinWorthSearching(
    int n, int k,
    double x, double y,
    int iRow, int iCol, double[] d) {
    if (n < k) {
      return true;
    }

    // find the x-coordinate offset to the nearest edge of the bin.
    // if the range of x coordinates for the bin contains
    // x, then then offset is treated as zero.
    double cx = x - (xmin + iCol * sBin);
    if (cx > 0) {
      if (cx > sBin) {
        cx -= sBin;
      } else {
        cx = 0; // cx within range of the bin, treat it as zero
      }
    }

    // find the y-coordinate offset to the nearest edge of the bin
    // using the same logic as described above.
    double cy = y - (ymin + iRow * sBin);
    if (cy > 0) {
      if (cy > sBin) {
        cy -= sBin;
      } else {
        cy = 0; // cy is within the range of the bin, treat it as zero
      }
    }

    double dc = cx * cx + cy * cy;
    double dMax = d[n - 1];
    return dc <= 1.000001 * dMax; // a little extra for round-off
  }

  /**
   * Gathers the nearest k Vertices in a bin, replacing any previously
   * collected points if better candidates are found.
   *
   * @param pn the previous count
   * @param k the target count
   * @param x the x coordinate of the query position
   * @param y the y coordinate of the query position
   * @param d storage for the distances of any previously collected vertices
   * or for those to be collected
   * @param v storage for any previously collected vertices or for
   * those to be collected
   * @param bin the array of vertices for the bin to be processed.
   * @return the number of vertices stored (from both the current
   * and previous searches).
   */
  int gather(int pn, int k, double x, double y, double[] d, Vertex[] v, Vertex bin[]) {
    if (bin.length == 0) {
      return pn;
    }
    int n = pn;
    int i = 0;
    if (n < k) {
      if (n == 0) {
        // special case, first vertex
        d[0] = bin[0].getDistanceSq(x, y);
        v[0] = bin[0];
        n = 1;
        i = 1;
      }
      while (i < bin.length && n < k) {
        Vertex vTest = bin[i++];
        double dTest = vTest.getDistanceSq(x, y);
        int index = Arrays.binarySearch(d, 0, i, dTest);
        if (index < 0) {
          index = -(index + 1);
        }
        if (index >= n) {
          // sample distance is >= the last one currently in list
          // just append it to the end
          d[n] = dTest;
          v[n] = vTest;
        } else {
          for (int j = n; j > index; j--) {
            d[j] = d[j - 1];
            v[j] = v[j - 1];
          }
          d[index] = dTest;
          v[index] = vTest;
        }
        n++;
      }
    }

    // only continue if there are more members in the bin to be tested.
    // if there are more bin members to be considered, then n will equal k
    // if the members were all tested, then n could be less than k
    // but the following loop condition will not be met
    while (i < bin.length) {
      Vertex vTest = bin[i++];
      double dTest = vTest.getDistanceSq(x, y);
      if (dTest < d[n - 1]) {
        int index = Arrays.binarySearch(d, 0, n - 1, dTest);
        if (index < 0) {
          index = -(index + 1);
        }
        for (int j = n - 1; j > index; j--) {
          d[j] = d[j - 1];
          v[j] = v[j - 1];
        }
        d[index] = dTest;
        v[index] = vTest;
      }
    }
    return n;
  }

  /**
   * Get the K nearest neighbors from the collection. The
   *
   * @param x the x coordinate of the search position
   * @param y the y coordinate of the search position
   * @param k the target number of vertices to be collected
   * @param d storage for the distances to the vertices to be collected
   * (must be dimensioned at least to size k, but can be larger)
   * @param v storage for the vertices to be collected
   * (must be dimensioned at least to size k, but can be larger)
   * @return the number of neighboring vertices identified.
   */
  public int getNearestNeighbors(
    double x, double y,
    int k, double[] d, Vertex[] v) {
    int iRow = (int) ((y - ymin) / sBin);
    int iCol = (int) ((x - xmin) / sBin);
    iRow = limitedIndex(iRow, nRow);
    iCol = limitedIndex(iCol, nCol);
    int bIndex = iRow * nCol + iCol;
    int n = this.gather(0, k, x, y, d, v, bins[bIndex]);
    for (int iTier = 1; iTier < nTier; iTier++) {
      boolean searched = (n < k);
      int i0 = limitedIndex(iRow - iTier, nRow);
      int i1 = limitedIndex(iRow + iTier, nRow);
      int j0 = limitedIndex(iCol - iTier, nCol);
      int j1 = limitedIndex(iCol + iTier, nCol);
      int testRow = iRow - iTier;
      if (testRow >= 0) {
        // test row of bins from column j0 to j1, inclusive
        for (int j = j0; j <= j1; j++) {
          if (isBinWorthSearching(n, k, x, y, testRow, j, d)) {
            searched = true;
            bIndex = testRow * nCol + j;
            n = gather(n, k, x, y, d, v, bins[bIndex]);
          }
        }
      }
      testRow = iRow + iTier;
      if (testRow < nRow) {
        // test row of bins from column j0 to j1, inclusive
        for (int j = j0; j <= j1; j++) {
          if (isBinWorthSearching(n, k, x, y, testRow, j, d)) {
            searched = true;
            bIndex = testRow * nCol + j;
            n = gather(n, k, x, y, d, v, bins[bIndex]);
          }
        }
      }
      int testCol = iCol - iTier;
      if (testCol >= 0) {
        // test column of bins from row i0 to 11, inclusive
        for (int i = i0; i <= i1; i++) {
          if (isBinWorthSearching(n, k, x, y, i, testCol, d)) {
            searched = true;
            bIndex = i * nCol + testCol;
            n = gather(n, k, x, y, d, v, bins[bIndex]);
          }
        }
      }
      testCol = iCol + iTier;
      if (testCol < nCol) {
        // test column of bins from row i0 to 11, inclusive
        for (int i = i0; i <= i1; i++) {
          if (isBinWorthSearching(n, k, x, y, i, testCol, d)) {
            searched = true;
            bIndex = i * nCol + testCol;
            n = gather(n, k, x, y, d, v, bins[bIndex]);
          }
        }
      }

      if (!searched) {
        // none of the neighboring bins were within search distance
        // there is no need for futher testing.
        break;
      }
    }
    return n;
  }

  /**
   * Gets a list of the vertices currently stored in the collection.
   * The result may be slightly smaller than the original input
   * if merge rules were in effect and causes some co-located vertices
   * to be merged.
   *
   * @return a valid list.
   */
  public List<Vertex> getVertices() {
    int n = 0;
    for (int i = 0; i < nBins; i++) {
      n += bins[i].length;
    }
    List<Vertex> list = new ArrayList<Vertex>(n);
    for (int i = 0; i < nBins; i++) {
      list.addAll(Arrays.asList(bins[i]));
    }
    return list;
  }

}
