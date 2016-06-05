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
 * 06/2015  G. Lucas     Adapted from ProtoTIN implementation of TriangleManager
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
package tinfour.standard;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import tinfour.common.IQuadEdge;
import tinfour.common.QuadEdge;
import tinfour.common.Vertex;

/**
 * Provides an object-pool implementation that the manages
 * the allocation, deletion, and reuse of Edges.
 * <p>This class is written using a very old-school approach as a way of
 * minimizing the frequency with which objects are garbage collected. Edges
 * are extensively allocated and freed as the TIN is built. Were they simply
 * constructed and put out-of-scope, the resulting garbage collection could
 * degrade performance.
 * <p>Note that this class is <strong>not thread safe</strong>.
 */
@SuppressWarnings("PMD.AvoidArrayLoops")
class EdgePool implements Iterable<QuadEdge> {

  /**
   * The number of edges in an edge-pool page.
   */
  private static final int EDGE_POOL_PAGE_SIZE = 1024;

  /**
   * The number of Edges stored in a page
   */
  private final int pageSize;

  Page[] pages;
  /**
   * The next page that includes available Edges. This reference is never
   * null. There is always at least one page with at least one free QuadEdge
   * in it.
   */
  Page nextAvailablePage;
  int nAllocated;
  int nFree;
  int nAllocationOperations;
  int nFreeOperations;

  /**
   * Construct a QuadEdge manager allocating a small number
   * of initial edges.
   *
   */
  EdgePool() {
    this.pageSize = EDGE_POOL_PAGE_SIZE;
    pages = new Page[1];
    pages[0] = new Page(0);
    nextAvailablePage = pages[0];
    nextAvailablePage.initializeEdges();
    nFree = pageSize;

  }

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
    return pageSize;
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
    int pagesNeeded = (edgesNeeded + pageSize - 1) / pageSize;
    int oldLen = pages.length;
    int nP = oldLen + pagesNeeded;
    pages = Arrays.copyOf(pages, nP);
    for (int i = oldLen; i < nP; i++) {
      pages[i] = new Page(i); // NOPMD
      pages[i].initializeEdges();
    }
    for (int i = 0; i < nP - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
    nextAvailablePage = pages[availablePageID];
    nFree += pagesNeeded * pageSize;
  }

  private void allocatePage() {
    int oldLength = pages.length;
    Page[] newPages = new Page[oldLength + 1];
    System.arraycopy(pages, 0, newPages, 0, pages.length);
    newPages[oldLength] = new Page(oldLength);
    newPages[oldLength].initializeEdges();
    pages = newPages;
    nFree += pageSize;
    nextAvailablePage = pages[oldLength];
    for (int i = 0; i < pages.length - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
  }

  QuadEdge allocateEdge(Vertex a, Vertex b) {
    Page page = nextAvailablePage;
    QuadEdge e = page.allocateEdge();
    if (page.isFullyAllocated()) {
      nextAvailablePage = page.nextPage;
      if (nextAvailablePage == null) {
        allocatePage();
      }
    }
    nFree--;
    nAllocated++;
    nAllocationOperations++;

    e.setVertices(a, b);
    return e;
  }

  /**
   * Allocates a QuadEdge with null vertices, assigning the responsibility
   * for populating the QuadEdge to the calling application. Because the
   * resulting QuadEdge is part of the QuadEdge collection, it is important
   * that the calling application fully populate the QuadEdge according
   * to its own processing requirements.
   *
   * @return a valid QuadEdge under the management of this collection.
   */
  QuadEdge allocateUndefinedEdge() {
    Page page = nextAvailablePage;
    QuadEdge t = page.allocateEdge();
    if (page.isFullyAllocated()) {
      nextAvailablePage = page.nextPage;
      if (nextAvailablePage == null) {
        allocatePage();
      }
    }
    nFree--;
    nAllocated++;
    nAllocationOperations++;

    return t;
  }

  /**
   * Deallocates the QuadEdge returning it to the QuadEdge pool.
   *
   * @param e a valid QuadEdge
   */
  void deallocateEdge(QuadEdge e) {

    int iPage = e.getIndex() / pageSize;
    Page page = pages[iPage];
    if (page.isFullyAllocated()) {
            // since it will no longer be fully allocated,
      // add it to the linked list
      page.nextPage = nextAvailablePage;
      nextAvailablePage = page;
    }
    page.deallocateEdge(e);
    nAllocated--;
    nFree++;
    nFreeOperations++;
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
   * Get first valid, non-ghost QuadEdge in collection
   *
   * @return for a non-empty collection, a valid QuadEdge; otherwise a null
   */
  public QuadEdge getStartingEdge() {
    for (Page p : pages) {
      if (p.nAllocated > 0) {
        for (int i = 0; i < p.nAllocated; i++) {
          if (p.edges[i].getB() != null && p.edges[i].getA() != null) {
            return p.edges[i];
          }
        }
      }
    }
    return null;
  }

  public QuadEdge getStartingGhostEdge() {
    for (Page p : pages) {
      if (p.nAllocated > 0) {
        for (int i = 0; i < p.nAllocated; i++) {
          QuadEdge e = p.edges[i];
          if (e.getB() == null) {
            return e;
          }
        }
      }
    }
    return null;
  }

  /**
   * Get a list of the Edges currently stored in the collection
   *
   * @return a valid, potentially empty list of edges
   */
  public List<IQuadEdge> getEdges() {
    ArrayList<IQuadEdge> eList = new ArrayList<>(nAllocated);
    for (Page p : pages) {
      for (int j = 0; j < p.nAllocated; j++) {
        eList.add(p.edges[j]);
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
      Page page = pages[i];
      page.nextPage = null;
      for (int j = 0; j < pageSize; j++) {
        QuadEdge e = page.edges[j];
        e.clear();
        page.edges[j] = null;
      }
      page.edges = null;
      pages[i] = null;
    }
    pages = null;
  }

  /**
   * Deallocates all Edges, returning them to the free
   * list. Does not delete any existing objects.
   */
  void clear() {
    for (Page p : pages) {
      for (QuadEdge t : p.edges) {
        t.clear();
      }
      p.nAllocated = 0;
    }
    nAllocated = 0;
    nFree = pages.length * pageSize;
    nAllocationOperations = 0;
    nFreeOperations = 0;
    this.nextAvailablePage = pages[0];
    for (int i = 0; i < pages.length - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
    pages[pages.length - 1].nextPage = null;
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
    Page p = nextAvailablePage;
    while (p != null) {
      nPartials++;
      p = p.nextPage;
    }
    ps.format("Edges allocated:             %8d\n", nAllocated);
    ps.format("Edges free:                  %8d\n", nFree);
    ps.format("Pages:                       %8d\n", pages.length);
    ps.format("Partially used pages:        %8d\n", nPartials);
    ps.format("Total allocation operations: %8d\n", nAllocationOperations);
    ps.format("Total free operations        %8d\n", nFreeOperations);
  }

  @Override
  public Iterator<QuadEdge> iterator() {
    Iterator<QuadEdge> ix = new Iterator<QuadEdge>() {
      QuadEdge currentEdge;
      int nextPage;
      int nextEdge;
      boolean hasNext = findNextEdge(0, -1);

      private boolean findNextEdge(int iPage, int iEdge) {
        nextPage = iPage;
        nextEdge = iEdge + 1;
        while (nextPage < pages.length) {
          if (nextEdge < pages[nextPage].nAllocated) {
            return true;
          }
          nextPage++;
          nextEdge = 0;
        }
        return false;
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public void remove() {
        if (currentEdge == null) {
          return;
        }

                // the deallocation operation will potentially move
        // a QuadEdge into the place of the one to be deleted.
        // so the next QuadEdge flags will have to be adjusted.
        // If the iEdge is less than nAllocated-1, this shift will
        // happen and so we reset the "next" flags to point at the
        // page and QuadEdge-index of the current QuadEdge.
        // But if the iEdge is greater than or equal to nAllocated-1,
        // we're deleting the last QuadEdge on the page and we need
        // to move to the next page to find the next QuadEdge.
        int iPage = currentEdge.getIndex() / pageSize;
        int iEdge = currentEdge.getIndex() % pageSize;
        if (hasNext) {
          nextPage = iPage;
          nextEdge = iEdge;
          if (nextEdge >= pages[iPage].nAllocated - 1) {
            hasNext = findNextEdge(iPage + 1, -1);
          }
        }

        deallocateEdge(currentEdge);
        currentEdge = null;

      }

      @Override
      public QuadEdge next() {
        currentEdge = null;
        if (hasNext) {
          currentEdge = pages[nextPage].edges[nextEdge];
          hasNext = findNextEdge(nextPage, nextEdge);
        }
        return currentEdge;
      }

    };
    return ix;
  }

  /**
   * Gets the maximum value of an edge index that is currently allocated
   * within the edge pool.
   *
   * @return a positive number or zero if the pool is currently unallocated.
   */
  public int getMaximumAllocationIndex() {
    for (int iPage = pages.length - 1; iPage >= 0; iPage--) {
      Page p = pages[iPage];
      if (p.nAllocated > 0) {
        return p.pageID * this.pageSize + p.nAllocated - 1;
      }
    }
    return 0;
  }

  private class Page {
    int pageID;
    int pageOffset;
    int nAllocated;
    QuadEdge[] edges;
    Page nextPage;

    Page(int pageID) {
      this.pageID = pageID;
      pageOffset = pageID * pageSize;
      edges = new QuadEdge[pageSize];
    }

    /**
     * Sets up the array of free Edges. This method is almost always
     * called when a new page is created. The only time it is not is in the
     * compact() operation where Edges will be shifted around.
     */
    void initializeEdges() {
      for (int i = 0; i < pageSize; i++) {
        edges[i] = new QuadEdge(pageOffset + i); //NOPMD
      }
    }

    QuadEdge allocateEdge() {
      QuadEdge e = edges[nAllocated];
      e.setIndex(pageID * edges.length + nAllocated);
      nAllocated++;
      return e;
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
    void deallocateEdge(QuadEdge be) {
      // reset to initialization state as necessary.
      // in this following block, we clear all flags that matter.
      // We also set any references to null to prevent
      // object retention and expedite garbage collection.
      int index = be.getIndex() - pageOffset;
      QuadEdge e = be.getBaseReference();
      e.clear();

      // The array of Edges must be kept
      // so that all allocated Edges are together at the beginning
      // of the array and all the free Edges are together at
      // the end of the array.  If the removal
      // left a "hole" in the section of the array dedicated to allocated
      // Edges, shift Edges around, reassigning the managementID
      // of the QuadEdge that was shifted into the hole.
      nAllocated--;
      // nAllocated is now the index of the last allocated QuadEdge
      // in the array.  We can modify the allocationID of that
      // QuadEdge and its position in the array because the
      // EdgeManager class is the only one that manipulates these
      // values.

      if (index < nAllocated) {
        QuadEdge swap = edges[nAllocated];
        edges[index] = swap;
        swap.setIndex(pageOffset + index);
        edges[nAllocated] = e;
        e.setIndex(pageOffset + nAllocated);
      }
    }

    boolean isFullyAllocated() {
      return nAllocated == edges.length;
    }
  }
}
