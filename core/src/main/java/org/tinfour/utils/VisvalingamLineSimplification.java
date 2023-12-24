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
 * 05/2021  G. Lucas     Created
 *
 * Notes:
 *  This implementation is based on the Visvalingam & Whyatt algoritm
 * described in
 *
 *       Visvalingam, M. & Whyatt, Duncan. (1993).
 *       Line generalisation by repeated elimination of points.
 *       Cartographic Journal, The. 30. 46-51. 10.1179/000870493786962263.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.io.PrintStream;

/**
 * Provides an implementation of Visvalingam's algorithm for simplifying
 * lines by repeated elimination of the smallest area features.
 * <p>
 * This class is based on the algorithm described in
 * <cite>
 *       Visvalingam, M. &#38; Whyatt, Duncan. (1993).
 *       Line generalisation by repeated elimination of points.
 *       Cartographic Journal, The. 30. 46-51. 10.1179/000870493786962263.
 * </cite>
 */
public class VisvalingamLineSimplification {

  private long nProcessed;
  private long nRemoved;
  private long nOperations;

  /**
   * Simplify the geometry of the input line or polygon by progressively
   * removing points related to small-area features. The algorithm
   * repeatedly inspects a chain of points to identify the set of three sequential
   * points that forms the smallest area, it eliminates then, and
   * continues.
   * <p>
   * Coordinates are given as an array in the order (x0, y0, x1, y1, etc.).
   * @param nPoints the number of points in the feature.
   * @param xy the coordinates of the feature
   * @param areaThreshold the area threshold to be used as a reduction criterion.
   * @return the number of points remaining after the thinning operation.
   */
  public int simplify(int nPoints, double[] xy, double areaThreshold) {
    if (nPoints < 2) {
      return nPoints;
    }

    int  n = nPoints;
    // remove duplicates
     int k = 2;
    for(int i=2; i<n*2; i+=2){
      if(xy[i]!=xy[k-2] || xy[i+1]!=xy[k-1]){
        if(k<i){
          xy[k] = xy[i];
          xy[k+1] = xy[i+1];
        }
        k+=2;
      }
    }
     n = k/2;


    // To investigate: It may be possible to expedite this processing
    // by using a priority queue.  The Java PriorityQueue class is
    // promising in this regard.

    boolean closedLoop = xy[0]==xy[n*2-2] && xy[1] == xy[n*2-1] ;
    int nMin = closedLoop? 4 : 2;
    if(n==nMin){
      return nPoints;
    }

    nOperations++;
    nProcessed+=nPoints;

    if (areaThreshold == 0) {
      nRemoved += (nPoints - n);
      return nPoints;
    }

    double[] a = new double[n];
    a[0] = Double.POSITIVE_INFINITY;
    a[nPoints - 1] = Double.POSITIVE_INFINITY;

    int iMin = 0;
    double aMin = Double.POSITIVE_INFINITY;
    for (int i = 1; i < n - 1; i++) {
      int ix = i*2-2;
      double xOffset = xy[ix++];
      double yOffset = xy[ix++];
      double x0 = xy[ix++] - xOffset;
      double y0 = xy[ix++] - yOffset;
      double x1 = xy[ix++] - xOffset;
      double y1 = xy[ix++] - yOffset;
      a[i] = Math.abs(x0 * y1 - x1 * y0)/2;
      if (a[i] < aMin) {
        aMin = a[i];
        iMin = i;
      }
    }

    while (aMin <= areaThreshold) {
      for (int i = iMin; i < n - 1; i++) {
        int ix = i * 2;
        xy[ix]   = xy[ix + 2];
        xy[ix+1] = xy[ix + 3];
        a[i] = a[i+1];
      }
      n--;
      if (n == nMin) {
        break;
      }
      // the areas for iMin and those points that bracket it need to be
      // recomputed.  But point 0 and point n-1 are protected.
      int i0 = iMin == 1 ? 1 : iMin-1;
      int i1 = iMin >= n - 2 ? n - 2 : iMin + 1;
      for (int i = i0; i <= i1; i++) {
        int ix = i * 2-2;
        double xOffset = xy[ix++];
        double yOffset = xy[ix++];
        double x0 = xy[ix++] - xOffset;
        double y0 = xy[ix++] - yOffset;
        double x1 = xy[ix++] - xOffset;
        double y1 = xy[ix++] - yOffset;
        a[i] =Math.abs(x0 * y1 - x1 * y0)/2;
      }
      aMin = a[1];
      iMin = 1;
      for(int i=2; i<n-1; i++){
        if(a[i]<aMin){
          aMin = a[i];
          iMin = i;
        }
      }
    }

    k = 2;
    for(int i=2; i<n*2; i+=2){
      if(xy[i]!=xy[k-2] || xy[i+1]!=xy[k-1]){
        if(k<i){
          xy[k] = xy[i];
          xy[k+1] = xy[i+1];
        }
        k+=2;
      }
    }
    n = k/2;

    nRemoved+=(nPoints-n);
    return n;
  }

  public void report(PrintStream ps){
    ps.format("Visvalingam nFeatures=%d, nPointsInput=%d, nRemoved=%d%n", nOperations, nProcessed, nRemoved);
  }
}
