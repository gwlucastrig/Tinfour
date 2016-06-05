/*
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
 */


/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2015  G. Lucas     Refactored from IncrementalTIN EdgePool
 *
 * Notes:
 *  The memory in this container is organized into pages, each page
 * holding a fixed number of Edges.   Some of the Edges are
 * committed to the TIN, others are in an "available state".  The pages
 * include links so that the container can maintain a single-direction linked
 * list of pages which have at least one QuadEdge in the "available" state.
 *
 * By design, the class guarantees that ALWAYS at least one page in an available
 * state.  This guarantee allows us to shave one conditional operation each
 * time a QuadEdge is inserted:
 *
 *    With guarantee:
 *       1) Add a QuadEdge to the page.
 *       2) Check to see if the page is full, if so add a new page
 *
 *    Without guarantee
 *       1) Check to see if there's an available page, if not add one
 *       2) Add QuadEdge to page
 *       3) Check to see if the page is full, if so add a new page
 *
 * The design of the class is based on the idea that Edges are added
 * and removed at random, but the number of Edges grows as the data
 * is processed. If this growth assumption is unfounded, then this class
 * would tend to end up with a lot of partially-populated pages
 *
 * Management ID
 *  The idea here is that the managementID element of a QuadEdge allows
 * the class to compute what page it belongs to.  So when a QuadEdge is
 * freed, it can modify the appropriate page.
 *--------------------------------------------------------------------------
 */
package tinfour.virtual;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import static tinfour.virtual.VirtualEdgePage.INDEX_MASK;
import static tinfour.virtual.VirtualEdgePage.INDICES_PER_PAGE;
import static tinfour.virtual.VirtualEdgePage.MASK_LOW_BIT_CLEAR;
import static tinfour.virtual.VirtualEdgePage.PAIRS_PER_PAGE;

/**
 * Provides an object-pool implementation that the manages
 * the allocation, deletion, and reuse of Edges.
 * <p>
 * This class is written using a very old-school approach as a way of
 * minimizing the frequency with which objects are garbage collected. Edges
 * are extensively allocated and freed as the TIN is built. Were they simply
 * constructed and put out-of-scope, the resulting garbage collection could
 * degrade performance.
 * <p>
 * Note that this class is <strong>not thread safe</strong>.
 */
@SuppressWarnings("PMD.AvoidArrayLoops")
class VirtualEdgePool implements Iterable<VirtualEdge> {

  VirtualEdgePool self;
  VirtualEdgePage[] pages;
  /**
   * The next page that includes available Edges. This reference is never
   * null. There is always at least one page with at least one free QuadEdge
   * in it.
   */
  VirtualEdgePage nextAvailablePage;
  int nAllocated;
  int nFree;
  int nAllocationOperations;
  int nFreeOperations;

  /**
   * Construct a Edge manager allocating a small number
   * of initial edges.
   *
   */
  VirtualEdgePool() {
    self = this;
    pages = new VirtualEdgePage[1];
    pages[0] = new VirtualEdgePage(0);
    nextAvailablePage = pages[0];
    nAllocated = 0;
    nFree = pages.length * PAIRS_PER_PAGE;

  }

//  private void reserveEdgeZero() {
//    pages[0].nAllocated = 1;
//    nAllocated = 1;
//    nFree--;
//  }
  /**
   * Gets the number of pages currently allocated.
   *
   * @return a value of 1 or greater.
   */
  int getPageCount() {
    return pages.length;
  }

  /**
   * Get the number of edges allocated in a page
   *
   * @return a value of 1 or greater, usually 1024.
   */
  int getPageSize() {
    return INDICES_PER_PAGE;
  }

  /**
   * Pre-allocates the specified number of edges. For a Delaunay
   * Triangulation with n vertices, there should be 3*n edges.
   *
   * @param n the number of edge (not vertices) to be allocated.
   */
  void preAllocateEdges(int n) {
    if (nFree >= n) {
      return;
    }
    int availablePageID = nextAvailablePage.pageID;
    int edgesNeeded = n - nFree; // number of  new Edges needed
    int pagesNeeded = (edgesNeeded + PAIRS_PER_PAGE - 1) / PAIRS_PER_PAGE;
    int oldLen = pages.length;
    int nP = oldLen + pagesNeeded;
    pages = Arrays.copyOf(pages, nP);
    for (int i = oldLen; i < nP; i++) {
      pages[i] = new VirtualEdgePage(i); //NOPMD
    }
    for (int i = 0; i < nP - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
    nextAvailablePage = pages[availablePageID];
    nFree += pagesNeeded * PAIRS_PER_PAGE;
  }

  private void allocatePage() {
    int oldLength = pages.length;
    VirtualEdgePage[] newPages = new VirtualEdgePage[oldLength + 1];
    System.arraycopy(pages, 0, newPages, 0, pages.length);
    newPages[oldLength] = new VirtualEdgePage(oldLength);
    pages = newPages;
    nFree += PAIRS_PER_PAGE;
    nextAvailablePage = pages[oldLength];
    for (int i = 0; i < pages.length - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
  }

  VirtualEdge allocateUnassignedEdge() {
    return new VirtualEdge(this);
  }

  void allocateEdgeWithReceiver(VirtualEdge receiver, Vertex a, Vertex b) {
    VirtualEdgePage page = nextAvailablePage;
    int absIndex = page.allocateEdge(a, b);
    if (page.isFullyAllocated()) {
      nextAvailablePage = page.nextPage;
      if (nextAvailablePage == null) {
        allocatePage();
      }
    }
    nFree--;
    nAllocated++;
    nAllocationOperations++;
    receiver.page = page;
    receiver.index = absIndex;
    receiver.indexOnPage = absIndex & INDEX_MASK;
    int side = absIndex & 1;
    int offset = absIndex & (INDEX_MASK & MASK_LOW_BIT_CLEAR);
    receiver.a = page.vertices[offset | side];
    receiver.b = page.vertices[offset | (side ^ 1)];
  }

  VirtualEdge allocateEdge(Vertex a, Vertex b) {
    VirtualEdgePage page = nextAvailablePage;
    int absIndex = page.allocateEdge(a, b);
    if (page.isFullyAllocated()) {
      nextAvailablePage = page.nextPage;
      if (nextAvailablePage == null) {
        allocatePage();
      }
    }
    nFree--;
    nAllocated++;
    nAllocationOperations++;
    return new VirtualEdge(this, page, absIndex);
  }

  public VirtualEdge getStartingEdge() {
    for (VirtualEdgePage page : pages) {
      int[] allocatedEdges = page.getAllocations();

      for (int j = 0; j < allocatedEdges.length; j++) {
        int iEdge = allocatedEdges[j];
        int index = iEdge & INDEX_MASK;
        if (page.vertices[index] != null && page.vertices[index + 1] != null) {
          return new VirtualEdge(this, page, iEdge);
        }
      }
    }
    return null;
  }

  public VirtualEdge getStartingGhostEdge() {
    for (int i = 0; i < pages.length; i++) {
      VirtualEdgePage page = pages[i];
      int[] allocatedEdges = page.getAllocations();

      for (int j = 0; j < allocatedEdges.length; j++) {
        int iEdge = allocatedEdges[j];
        int index = iEdge & INDEX_MASK;
        if (page.vertices[index] != null && page.vertices[index + 1] == null) {
          return new VirtualEdge(this, page, iEdge);
        }
      }
    }
    return null;
  }

  /**
   * Deallocates the QuadEdge returning it to the QuadEdge pool.
   *
   * @param e a valid QuadEdge
   */
  void deallocateEdge(int absIndex) {

    int iPage = absIndex / INDICES_PER_PAGE;
    VirtualEdgePage page = pages[iPage];
    if (page.isFullyAllocated()) {
      // since it will no longer be fully allocated,
      // add it to the linked list
      page.nextPage = nextAvailablePage;
      nextAvailablePage = page;
    }
    page.deallocateEdge(absIndex);
    nAllocated--;
    nFree++;
    nFreeOperations++;
  }

  void deallocateEdge(VirtualEdge e) {
    deallocateEdge(e.getIndex());
  }

  VirtualEdgePage getPageForIndex(int index) {
    return pages[index / INDICES_PER_PAGE];
  }

  /**
   * Get the number of Edges currently stored in the collection
   *
   * @return an integer value of zero or more
   */
  public int size() {
    return nAllocated;
  }

  /**
   * Get a list of the Edges currently stored in the collection
   *
   * @return a valid, potentially empty list of edges
   */
  public List<IQuadEdge> getEdges() {
    ArrayList<IQuadEdge> eList = new ArrayList<>(nAllocated);
    for (VirtualEdgePage p : pages) {
      int[] map = p.getAllocations();
      for (int i = 0; i < map.length; i++) {
        eList.add(new VirtualEdge(this, p, map[i])); //NOPMD
      }
    }
    return eList;
  }

  public List<VirtualEdge> getVirtualEdges() {
    ArrayList<VirtualEdge> eList = new ArrayList<>(nAllocated);
    for (VirtualEdgePage p : pages) {
      int[] map = p.getAllocations();
      for (int i = 0; i < map.length; i++) {
        eList.add(new VirtualEdge(this, p, map[i])); //NOPMD
      }
    }
    return eList;
  }

  public int getEdgeCount() {
    return nAllocated;
  }

  /**
   * Puts all references used in the collection out-of-scope as a way of
   * simplifying and expediting garbage collection.
   */
  void dispose() {
    nextAvailablePage = null;
    for (int i = 0; i < pages.length; i++) {
      pages[i].dispose();
      pages[i].nextPage = null;
      pages[i] = null;
    }
    pages = null;
  }

  /**
   * Deallocates all Edges, returning them to the free
   * list. Does not delete any existing objects.
   */
  void clear() {
    for (VirtualEdgePage p : pages) {
      p.clear();
    }

    nAllocationOperations = 0;
    nFreeOperations = 0;
    this.nextAvailablePage = pages[0];
    for (int i = 0; i < pages.length - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
    pages[pages.length - 1].nextPage = null;
    nAllocated = 0;
    nFree = pages.length * PAIRS_PER_PAGE;

  }

  @Override
  public String toString() {
    String s = "nEdges=" + nAllocated
      + ", nPages=" + pages.length
      + ", nFree=" + nFree;
    return s;
  }

  /**
   * Prints diagnostic information about the manager to the specified print
   * stream.
   *
   * @param ps a valid print stream.
   */
  public void printDiagnostics(PrintStream ps) {
    int nPartials = 0;
    VirtualEdgePage p = nextAvailablePage;
    while (p != null) {
      nPartials++;
      p = p.nextPage;
    }
    ps.format("\nEdge pool diagnostics\n");
    ps.format("   Edges allocated:             %8d\n", nAllocated);
    ps.format("   Edges free:                  %8d\n", nFree);
    ps.format("   Pages:                       %8d\n", pages.length);
    ps.format("   Partially used pages:        %8d\n", nPartials);
    ps.format("   Total allocation operations: %8d\n", nAllocationOperations);
    ps.format("   Total free operations        %8d\n", nFreeOperations);
  }

  /**
   * Gets the maximum value of an edge index that is currently allocated
   * within the edge pool.
   *
   * @return a positive number or zero if the pool is currently unallocated.
   */
  public int getMaximumAllocationIndex() {
    for (int iPage = pages.length - 1; iPage >= 0; iPage--) {
      VirtualEdgePage p = pages[iPage];
      if (p.nPairsAllocated > 0) {
        return (p.pageID + 1) * INDICES_PER_PAGE;
      }
    }
    return 0;
  }

  VirtualEdge getEdgeForIndex(int index) {
    int iPage = index / INDICES_PER_PAGE;
    VirtualEdgePage page = pages[iPage];
    return new VirtualEdge(this, page, index);
  }

  void getEdgeForIndexWithReceiver(final VirtualEdge receiver, final int index, Vertex a, Vertex b) {
    int iPage = index / INDICES_PER_PAGE;
    VirtualEdgePage page = pages[iPage];
    receiver.page = page;
    receiver.index = index;
    receiver.indexOnPage = index & INDEX_MASK;
    receiver.a = a;
    receiver.b = b;
    int side = index & 1;
    int offset = index & (INDEX_MASK & MASK_LOW_BIT_CLEAR);
    page.vertices[offset | side] = a;
    page.vertices[offset | (side ^ 1)] = b;
  }

  /**
   * Provides an implementation of iterator which loops through
   * the virtual edge collection, returning the base-edges in order
   * (dual edges are not included).
   *
   * @return a valid iterator.
   */
  @Override
  public Iterator<VirtualEdge> iterator() {
    return new Iterator<VirtualEdge>() {
      VirtualEdgePool pool;
      int iEdge;
      int iPage;
      int[] map = pages[0].getAllocations();
      boolean hasNext = processHasNext(0, -1);

      private boolean processHasNext(int pageIndex, int edgeIndex) {
        iPage = pageIndex;
        iEdge = edgeIndex + 1;
        while (iEdge >= map.length) {
          if (iPage == pages.length - 1) {
            return false;
          }
          iPage++;
          iEdge = 0;
          map = pages[iPage].getAllocations();
        }

        return true;
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public VirtualEdge next() {
        if (!hasNext) {
          return null;
        }
        int index = map[iEdge];
        VirtualEdgePage page = pages[iPage];

        VirtualEdge e = new VirtualEdge(self, page, index);
        hasNext = processHasNext(iPage, iEdge);
        return e;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException(
          "The remove method is not supported by this iterator");
      }

    };
  }

}
