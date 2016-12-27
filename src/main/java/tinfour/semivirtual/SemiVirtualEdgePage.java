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
 * 10/2015  G. Lucas     Calved off from VirtualEdgePool
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.semivirtual;

import java.util.Arrays;
import tinfour.common.Vertex;

class SemiVirtualEdgePage {

  /** The number of pairs per page is limited to the range of the
   * short integer used for the freePairs array, so the maximum
   * value is 2.  Making the number of pairs a power of 2
   * allows for a efficient computation of the index within the page
   * by bit-masking (rather than using modulus).  */
  static final int PAIRS_PER_PAGE_SCALE = 10;

  /** The number of complete edge pairs per page */
  static final int PAIRS_PER_PAGE = (1 << PAIRS_PER_PAGE_SCALE);

  /** number of edge indicates per page (two per edge) */
  static final int INDICES_PER_PAGE = 2 * PAIRS_PER_PAGE;

  /** Bit mask to extract page index from absolute index. */
  static final int INDEX_MASK = (INDICES_PER_PAGE - 1);

  /** Bit mask to clear low-order bit */
  static final int MASK_LOW_BIT_CLEAR = ~1;

  int pageID;
  int pageOffset;
  int nPairsAllocated;
  final Vertex[] vertices;
  final int[] links;
  short[] freePairs;
  int []constraints;
  SemiVirtualEdgePage nextPage;

  SemiVirtualEdgePage(int pageID) {

    this.pageID = pageID;
    pageOffset = pageID * INDICES_PER_PAGE;
    vertices = new Vertex[INDICES_PER_PAGE];
    links = new int[INDICES_PER_PAGE * 2];
  }

  void clear() {
    nPairsAllocated = 0;
    freePairs = null;
    constraints = null;
    Arrays.fill(vertices, 0, vertices.length, null);
    Arrays.fill(links, 0, links.length, 0);
  }

  void dispose(){
    for(int i=0; i<INDICES_PER_PAGE; i++){
      vertices[i] = null;
    }
    nPairsAllocated = 0;
    freePairs = null;
    constraints = null;
  }

  int allocateEdge(Vertex a, Vertex b) {
    int index;
    if (freePairs == null) {
      index = nPairsAllocated++;
    } else {
      nPairsAllocated++;
      int iLastFree = PAIRS_PER_PAGE - nPairsAllocated;
      index = freePairs[iLastFree];
    }
    index *= 2;
    vertices[index] = a;
    vertices[index + 1] = b;
    return pageOffset + index;
  }

  /**
   * Free the QuadEdge for reuse, setting any external references to null,
   * but not damaging any arrays or management structures.
   * <p>
   * Note that it is important that deallocation set the
   * QuadEdge back to its initialization states. To conserve processing
   * the allocation routine assumes that any unused QuadEdge in
   * the collection is already in its initialized state and so doesn't
   * do any extra work.
   *
   * @param e a valid QuadEdge
   */
  void deallocateEdge(int absIndex) {
    int index = absIndex & (INDEX_MASK & MASK_LOW_BIT_CLEAR);
    int offset = index;
    vertices[offset] = null;
    vertices[offset + 1] = null;
    offset *= 2;
    links[offset] = 0;
    links[offset + 1] = 0;
    links[offset + 2] = 0;
    links[offset + 3] = 0;
    if (nPairsAllocated == 1) {
      // the last one on the page just got freed
      freePairs = null;
    } else {
      // put pair (index/2) on the free list
      if (freePairs == null) {
        // the free list doesn't exist yet, initialize the free list
        freePairs = new short[PAIRS_PER_PAGE];
        for (int i = nPairsAllocated; i < PAIRS_PER_PAGE; i++) {
          freePairs[i - nPairsAllocated] = (short) i;
        }
      }
      freePairs[PAIRS_PER_PAGE - nPairsAllocated] = (short) (index / 2);
    }
    nPairsAllocated--;
  }

  boolean isFullyAllocated() {
    return nPairsAllocated == PAIRS_PER_PAGE;
  }

  int[] getAllocations() {
    int[] allocations = new int[nPairsAllocated];
    if (nPairsAllocated == 0) {
      return allocations;
    }
    if (this.freePairs == null) {
      for (int i = 0; i < nPairsAllocated; i++) {
        allocations[i] = pageOffset + i * 2;
      }
    } else {
      // assemble an array of all edges that aren't in the free list
      boolean[] isPairInFreeList = new boolean[PAIRS_PER_PAGE];
      int nFreePairs = PAIRS_PER_PAGE - nPairsAllocated;
      for (int i = 0; i < nFreePairs; i++) {
        isPairInFreeList[freePairs[i]] = true;
      }
      int k = 0;
      for (int i = 0; i < PAIRS_PER_PAGE; i++) {
        if (!isPairInFreeList[i]) {
          allocations[k++] = pageOffset + i * 2;
        }
      }
    }
    // logic no longer used. was intended to support a diagnostic in
    // which edge pair zero was reserved so that any reference in the
    // index table was clearly a de-allocated edge.
    //if (pageOffset == 0 && allocations.length > 0 && allocations[0] == 0) {
    //  // special handling to remove edge zero
    //  int[] scratch = new int[allocations.length - 1];
    //  for (int i = 0; i < scratch.length; i++) {
    //    scratch[i] = allocations[i + 1];
    //  }
    //  allocations = scratch;
    //}
    return allocations;
  }

  @SuppressWarnings("PMD.MethodReturnsInternalArray")
  int [] readyConstraints() {
     if(constraints==null){
       constraints = new int[PAIRS_PER_PAGE];
     }
     return constraints;
  }

}
