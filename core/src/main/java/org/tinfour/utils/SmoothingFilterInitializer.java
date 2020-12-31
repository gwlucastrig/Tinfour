/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 08/2019  G. Lucas     Created
 *
 * Notes:
 *
 *   This class creates set of "smoothed" z values for a surface represented
 * by a Delaunay Triangulation.  The smoothing algoritm begins by generating
 * a set of Barycentric weights that can be used to adjust the z values
 * of the vertices in the Tin by combining them with the values of their
 * immediate neighbors. The process them follws an interative process
 * performing several sets of combinations until the overall complexity
 * of the surface is reduced. In effect, it is a low-pass filter, providing
 * a smoother, less complex representation of the surface.
 *   In the initialization phase of this routine, the weights are computed and
 * stored in memory. While generating the weights requires a large processing
 * overhead, they can be used over-and-over-again in the subsequent iterative
 * combination process.
 *   Rather than storing the weights and indices need for the iterative
 * processing in contiguous arrays, this class use a "paging" scheme
 * similar to those used elsewhere in the Tinfour library.  This approach
 * is necessary to support data sets containing a large number of vertices.
 * In such cases, allocating correspondingly large blocks of memory would
 * place undue stress on Java's heap management and might result in a
 * premature out-of-memory condition.
 *   While the page management and indexing is both more complicated and less
 * convenient than using simple arrays, testing reveals that it is a more
 * robust solution because it provides a more conservative
 * use of resources in memory-constrained situations.
 *    Indices are stored as integers, weights as floats. The organization
 * of the page-based storage is as follows:
 *
 *    masterIndex[nVertices+1]
 *       the master link from the vertex index to the page and offset where
 *       information about its weights and neighbor indices is stored.
 *    iPages[][PAGE_SIZE]
 *       the array of pages for storing data about
 *       the number of neighbors, the vertex-index of each neighbor, and
 *       the position of the weights data within the weights pages.
 *           organized as:
 *                0.  number of neighbors
 *                1.  index into weights pages
 *                2.. 2_nNeighbors   the index of each neighbor
 *    wPages[][PAGE_SIZE}
 *        the weights pages.  for each vertex, this gives the weights
 *        for combining the z values of its neighbors.
 *
 *  Vertices on the perimeter of the TIN will not have a valid set of
 *  barycentric coordinates.  At this time, we have not implemented a
 *  way of combining their neighbor values, so the entry in the masterIndex[]
 *  array for these points will be -1, indicating no mapping.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * An implementation of the vertex valuator that processes the vertices in a
 * Constrained Delaunay Triangulation and applies a low-pass filter over the
 * data.
 * <p>
 * Note that this class modifies the index values of the vertices stored in the
 * TIN. It also depends on the modified values as a way of tracking vertices.
 * Therefore, calling applications should not modify these values while the
 * smoothing filter is being used.
 */
public class SmoothingFilterInitializer {

  private static final int PAGE_SIZE = 32768;

  int nVertex;

  // the master index maps a vertex index to the location in the page-based
  // indexing system for the location of weights and identification of
  // neighboring vertices.
  int[] masterIndex;  // combines page index and offset within page

  int[][] iPages;
  int iPagesCount;  // number of pages actually allocated
  int iPageIndex; // index within current page
  int iLostElements;
  // the current iPage would be iPagesCount-1

  float[][] wPages;
  int wPagesCount;  // number of pages actually allocated
  int wPageIndex; // index within current page
  int wLostElements;
  // the current wPage would be wPagesCount-1

  // output producxts
  double[] result;
  private final double timeToConstructFilter;

  /**
   * Construct a smoothing filter. Note that this class modifies the index
   * values of the vertices stored in the TIN.
   * <p>
   * <strong>Important usage note:</strong> this constructor modifies the index
   * values of the vertices stored in the TIN. It also depends on the modified
   * values as a way of tracking vertices. Therefore, calling applications
   * should not modify these values while the smoothing filter is being used.
   * <p>
   * The vertices belonging to constraints are not smoothed, but are represented
   * with their original values by the smoothing filter.
   *
   * @param tin a valid Delaunay Triangulation
   * @param nPasses the number of passes to perform over the data for smoothing;
   * the more passes, the more the complexity of the data is reduced.
   */
  public SmoothingFilterInitializer(IIncrementalTin tin, int nPasses) {
    long time0 = System.nanoTime();

    List<Vertex> vList = tin.getVertices();
    nVertex = vList.size();
    masterIndex = new int[nVertex];
    Arrays.fill(masterIndex, -1);
    double[] zArray = new double[nVertex];
    int k = 0;
    for (Vertex v : vList) {
      zArray[k] = v.getZ();
      v.setIndex(k++);
    }

    // initialize pages buffer and initial page
    iPages = new int[128][];
    iPages[0] = new int[PAGE_SIZE];
    iPagesCount = 1;

    wPages = new float[128][];
    wPages[0] = new float[PAGE_SIZE];
    wPagesCount = 1;

    BitSet visited = new BitSet(nVertex + 1);
    for (IQuadEdge e : tin.edges()) {
      initForEdge(visited, e);
      initForEdge(visited, e.getDual());
    }

    for (int i = 0; i < nPasses; i++) {
      zArray = processZ(zArray);
    }
    result = zArray;

    // dispose of anything we aren't going to need anymore.
    for (int i = 0; i < iPages.length; i++) {
      iPages[i] = null;
    }
    iPages = null;
    for (int i = 0; i < wPages.length; i++) {
      wPages[i] = null;
    }
    wPages = null;

    long time1 = System.nanoTime();
    timeToConstructFilter = (time1 - time0) / 1.0e+6;
  }

  /**
   * Gets the time required to construct the filter, in milliseconds. Intended
   * for diagnostic and development purposes.
   *
   * @return a value in milliseconds.
   */
  public double getTimeToConstructFilter() {
    return timeToConstructFilter;
  }

   /**
   * Gets a polygon consisting of edges connected to
   * the specified edge (in effect providing the set of vertices
   * connected to the starting vertex of the specified edge).
   * The polygon is ordered in counterclockwise order.
   *
   * @param e the starting edge
   *
   * @return a valid list of edges (some of which may be
   * perimeter or ghost edges).
   */
  public List<Vertex> getConnectedPolygon(IQuadEdge e) {
    List<Vertex> vList = new ArrayList<>();

    for(IQuadEdge s: e.pinwheel()){
      vList.add(s.getB());
    }
    return vList;
  }


  private void initForEdge(BitSet visited, IQuadEdge edge) {
    Vertex A = edge.getA();
    if (A == null) {
      return;
    }
    int vertexIndex = A.getIndex();
    if (visited.get(vertexIndex)) {
      return;
    }
    visited.set(vertexIndex);
    if (A.isConstraintMember()) {
      return;
    }
    double x = A.getX();
    double y = A.getY();

    List<Vertex> pList = getConnectedPolygon(edge);
    BarycentricCoordinates bcoord = new BarycentricCoordinates();
    double[] w = bcoord.getBarycentricCoordinates(pList, x, y);
    if (w == null) {
      return;
    }
    // double figureOfMerit = bcoord.getBarycentricCoordinateDeviation();

    assert w.length == pList.size() : "Incorrect barycentric weights result";

    int iPage = iPagesCount - 1;
    int[] iArray = iPages[iPage];
    int nTest = iPageIndex + w.length + 2;
    if (nTest >= PAGE_SIZE) {
      iLostElements += PAGE_SIZE - iPageIndex;
      // we need another page
      if (iPage == iPagesCount - 1) {
        iPages = Arrays.copyOf(iPages, iPages.length + 128);
      }
      iPageIndex = 0;
      iPage++;
      iPagesCount++;
      iArray = new int[PAGE_SIZE];
      iPages[iPage] = iArray;
    }

    int wPage = wPagesCount - 1;
    float[] wArray = wPages[wPage];
    nTest = wPageIndex + w.length;
    if (nTest >= PAGE_SIZE) {
      wLostElements += PAGE_SIZE - wPageIndex;
      // we need another page
      if (wPage == wPagesCount - 1) {
        wPages = Arrays.copyOf(wPages, wPages.length + 128);
      }
      wPageIndex = 0;
      wPage++;
      wPagesCount++;
      wArray = new float[PAGE_SIZE];
      wPages[wPage] = wArray;
    }

    masterIndex[vertexIndex] = iPage * PAGE_SIZE + iPageIndex;

    iArray[iPageIndex++] = w.length;  // offset to start of weights
    iArray[iPageIndex++] = wPage * PAGE_SIZE + wPageIndex;
    for (int i = 0; i < w.length; i++) {
      iArray[iPageIndex++] = pList.get(i).getIndex();
      wArray[wPageIndex++] = (float) w[i];
    }

  }

  private double[] processZ(double[] zArray) {
    double[] z = new double[zArray.length];

    for (int index = 0; index < nVertex; index++) {
      int iOffset = masterIndex[index];
      if (iOffset == -1) {
        z[index] = zArray[index];
      } else {
        int iPage = iOffset / PAGE_SIZE;
        iOffset -= iPage * PAGE_SIZE;
        int[] iArray = iPages[iPage];
        int wCount = iArray[iOffset++];
        int wOffset = iArray[iOffset++];
        int wPage = wOffset / PAGE_SIZE;
        wOffset -= wPage * PAGE_SIZE;
        float[] wArray = wPages[wPage];
        double zSum = 0;
        double wSum = 0;
        for (int i = 0; i < wCount; i++) {
          int vIndex = iArray[iOffset++];
          double w = wArray[wOffset++];
          zSum += zArray[vIndex] * w;
          wSum += w;
        }
        z[index] = zSum / wSum;
      }
    }
    return z;
  }

}
