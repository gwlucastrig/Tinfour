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


/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 05/2014  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import tinfour.common.Vertex;

/**
 * A utility that sorts points according to the Hilbert space-filling curve and
 * ensures a high-degree of spatial locality in the sequence of points.
 * <p>
 * During the insertion of points into a TIN, the incremental TIN
 * locates the triangle the contains the insertion point through a "walk"
 * method that traverses the mesh. The walk always on are the triangle
 * that it visited on the previous insertion. Thus, if the insertion vertices
 * are ordered so that subsequent points are close together, the walk is
 * short and the insertion proceeds more efficiently.
 * <p>
 * The Hilbert sort orders points according to their ranking in a
 * Hilbert space-filling curve. One of the characteristics of Hilbert functions
 * is that each ordered point is always close to its predecessor.
 * <p>
 * The code for the Hilbert ranking is based on the Lam&amp;Shapiro method
 * as described in "Hackers Delight (2nd ed.)" by H. Warren (2013)
 */
public class HilbertSort {

  double xMin, xMax, yMin, yMax;


  private int xy2Hilbert(final int px, final int py, final int n) {
    int i, xi, yi;
    int s, temp;

    int x = px;
    int y = py;
    s = 0;                         // Initialize.
    for (i = n - 1; i >= 0; i--) {
      xi = (x >> i) & 1;          // Get bit i of x.
      yi = (y >> i) & 1;          // Get bit i of y.

      if (yi == 0) {
        temp = x;                // Swap x and y and,
        x = y ^ (-xi);             // if xi = 1,
        y = temp ^ (-xi);          // complement them.
      }
      s = 4 * s + 2 * xi + (xi ^ yi);   // Append two bits to s.
    }
    return s;
  }

  /**
   * Sort the vertices in the list by their Hilbert ranking.
   * Because this method temporarily modifies the index element of the
   * input vertices and then restores their values when complete,
   * it is generally unwise to use it on a set of vertices that
   * may be accessed concurrently by more than one thread.
   *
   * @param vertexList the input vertex list.
   * @return if the list meets the conditions of the sort (has enough
   * points, etc.) and a sort is performed, true; otherwise, false.
   */
  public boolean sort(List<Vertex> vertexList) {
    /**
     * Find the (x,y) extent of the data
     */
    if (vertexList.isEmpty()) {
      return false;
    }

    Vertex v = vertexList.get(0);
    xMin = v.x;
    xMax = v.x;
    yMin = v.y;
    yMax = v.y;

    for (Vertex vertex : vertexList) {
      if (vertex.x < xMin) {
        xMin = vertex.x;
      } else if (vertex.x > xMax) {
        xMax = vertex.x;
      }
      if (vertex.y < yMin) {
        yMin = vertex.y;
      } else if (vertex.y > yMax) {
        yMax = vertex.y;
      }
    }

    double xDelta = xMax - xMin;
    double yDelta = yMax - yMin;
    if (xDelta == 0 || yDelta == 0) {
      return false;
    }
    if (vertexList.size() < 24) {
      return false;
    }

    double hn = Math.log(vertexList.size()) / 0.693147180559945 / 2.0;
    int nHilbert = (int) Math.floor(hn + 0.5);
    if (nHilbert < 4) {
      nHilbert = 4;
    }

	//    This sort temporarily modifies the index element of the vertices
	// to facilitate sorting, then sets them back to their original
	// state when its finished.
	//    Unfortunately, I could not think of any good way to perform the
	// sort without modifying the vertices to be sorted.  Two obvious
	// approaches can be rejected right away and the third has a drawback.
	//    1.  Compute the Hilbert ranking on the fly within the
	//        comparator.  Won't work because the computing the ranking is
	//        too expensive to be performed everytime the comparator is invoked
	//    2.  Create a class that holds a vertex and a ranking and use
	//        that as the object to be sorted. Won't work because of the
	//        high cost of constructing a large number of temporary
	//        objects.
	//    3.  Add a field to the vertex class to store its Hilbert ranking.
	//        This would work, but would increase the size of vertex instances
	//        by 4 to 8 bytes per (depending on memory layout).
	// Instead, we use the approach:
	//    4.  Record original indices in temporary storage. Replace the
	//        vertex indices with their Hilbert rankings. Sort. Restore
	//        the original vertices from the temporary storage.
	// This approach has the unfortunate consequence of making the vertex
	// index a mutable field, which is a less than ideal design practice...
	// but necessary in this case.

    int []savedIndices = new int[vertexList.size()];
	Vertex []savedVertices = new Vertex[vertexList.size()];
	int kVertex = 0;
	for(Vertex vh: vertexList){
	   savedVertices[kVertex] = vh;
	   savedIndices[kVertex] = vh.getIndex();
	   kVertex++;
	}

	// scale coordinates to 2^n - 1
    double hScale = (double) (1 << nHilbert) - 1.0;
    for (Vertex vh : vertexList) {
      int ix = (int) (hScale * (vh.x - xMin) / xDelta);
      int iy = (int) (hScale * (vh.y - yMin) / yDelta);
      vh.setIndex(xy2Hilbert(ix, iy, nHilbert));
    }

    // Sort used for testing random data
    Collections.sort(vertexList, new Comparator<Vertex>() {
      @Override
      public int compare(Vertex o1, Vertex o2) {
        return o1.getIndex() - o2.getIndex();
      }

    });

    // reassign the indices to order within the list
    int k = 0;
    for (Vertex vh : savedVertices) {
      vh.setIndex(savedIndices[k++]);
    }

    return true;
  }

}
