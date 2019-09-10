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
package org.tinfour.semivirtual;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import static org.tinfour.semivirtual.SemiVirtualEdgePage.INDEX_MASK;
import static org.tinfour.semivirtual.SemiVirtualEdgePage.INDICES_PER_PAGE;
import static org.tinfour.semivirtual.SemiVirtualEdgePage.MASK_LOW_BIT_CLEAR;
import static org.tinfour.semivirtual.SemiVirtualEdgePage.PAIRS_PER_PAGE;

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
class SemiVirtualEdgePool implements Iterable<IQuadEdge> {

  SemiVirtualEdgePool self;
  SemiVirtualEdgePage[] pages;
  /**
   * The next page that includes available Edges. This reference is never
   * null. There is always at least one page with at least one free QuadEdge
   * in it.
   */
  SemiVirtualEdgePage nextAvailablePage;
  int nAllocated;
  int nFree;
  int nAllocationOperations;
  int nFreeOperations;
  
  
  /**
   * The constraint maps provide a way of tying a constraint object reference to
   * the edges that are associated with it. Separate maps are maintained for the
   * borders of region constraints (borders) and linear constraints. This
   * indirect method is used to economize on memory use by edges. Although it
   * would be possible to add constraint references to the edge structure, doing
   * so would increase the edge memory use by an unacceptably large degree.
   */
  HashMap<Integer, IConstraint> borderConstraintMap = new HashMap<>();
  HashMap<Integer, IConstraint> linearConstraintMap = new HashMap<>();


  /**
   * Construct a Edge manager allocating a small number
   * of initial edges.
   *
   */
  SemiVirtualEdgePool() {
    self = this;
    pages = new SemiVirtualEdgePage[1];
    pages[0] = new SemiVirtualEdgePage(0);
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
      pages[i] = new SemiVirtualEdgePage(i); //NOPMD
    }
    for (int i = 0; i < nP - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
    nextAvailablePage = pages[availablePageID];
    nFree += pagesNeeded * PAIRS_PER_PAGE;
  }

  private void allocatePage() {
    int oldLength = pages.length;
    SemiVirtualEdgePage[] newPages = new SemiVirtualEdgePage[oldLength + 1];
    System.arraycopy(pages, 0, newPages, 0, pages.length);
    newPages[oldLength] = new SemiVirtualEdgePage(oldLength);
    pages = newPages;
    nFree += PAIRS_PER_PAGE;
    nextAvailablePage = pages[oldLength];
    for (int i = 0; i < pages.length - 1; i++) {
      pages[i].nextPage = pages[i + 1];
    }
  }

  SemiVirtualEdge allocateUnassignedEdge() {
    return new SemiVirtualEdge(this);
  }

  void allocateEdgeWithReceiver(SemiVirtualEdge receiver, Vertex a, Vertex b) {
    SemiVirtualEdgePage page = nextAvailablePage;
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
  }

  SemiVirtualEdge allocateEdge(Vertex a, Vertex b) {
    SemiVirtualEdgePage page = nextAvailablePage;
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
    return new SemiVirtualEdge(this, page, absIndex);
  }

  public SemiVirtualEdge getStartingEdge() {
    for (SemiVirtualEdgePage page : pages) {
      int[] allocatedEdges = page.getAllocations();

      for (int j = 0; j < allocatedEdges.length; j++) {
        int iEdge = allocatedEdges[j];
        int index = iEdge & INDEX_MASK;
        if (page.vertices[index] != null && page.vertices[index + 1] != null) {
          return new SemiVirtualEdge(this, page, iEdge);
        }
      }
    }
    return null;
  }

  public SemiVirtualEdge getStartingGhostEdge() {
    for (SemiVirtualEdgePage page : pages) {
      int[] allocatedEdges = page.getAllocations();

      for (int j = 0; j < allocatedEdges.length; j++) {
        int iEdge = allocatedEdges[j];
        int index = iEdge & INDEX_MASK;
        if (page.vertices[index] != null && page.vertices[index + 1] == null) {
          return new SemiVirtualEdge(this, page, iEdge);
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
    SemiVirtualEdgePage page = pages[iPage];
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

  void deallocateEdge(SemiVirtualEdge e) {
    deallocateEdge(e.getIndex());
  }

  SemiVirtualEdgePage getPageForIndex(int index) {
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
    for (SemiVirtualEdgePage p : pages) {
      int[] map = p.getAllocations();
      for (int i = 0; i < map.length; i++) {
        eList.add(new SemiVirtualEdge(this, p, map[i])); //NOPMD
      }
    }
    return eList;
  }

  public List<SemiVirtualEdge> getVirtualEdges() {
    ArrayList<SemiVirtualEdge> eList = new ArrayList<>(nAllocated);
    for (SemiVirtualEdgePage p : pages) {
      int[] map = p.getAllocations();
      for (int i = 0; i < map.length; i++) {
        eList.add(new SemiVirtualEdge(this, p, map[i])); //NOPMD
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
    for (SemiVirtualEdgePage p : pages) {
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
    linearConstraintMap.clear();
    borderConstraintMap.clear();
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
    SemiVirtualEdgePage p = nextAvailablePage;
    while (p != null) {
      nPartials++;
      p = p.nextPage;
    }
    int nConstrained = 0;
    int nConstraintInterior = 0;
    int nConstraintBorder = 0;
    Iterator<IQuadEdge> it = iterator();
    while (it.hasNext()) {
      IQuadEdge e = it.next();
      if (e.isConstrained()) {
        nConstrained++;
        if (e.isConstrainedRegionBorder()) {
          nConstraintBorder++;
        }
      } else if (e.isConstrainedRegionInterior()) {
        nConstraintInterior++;
      }
    }
    ps.format("%nEdge pool diagnostics%n");
    ps.format("   Edges allocated:             %8d%n", nAllocated);
    ps.format("   Edges free:                  %8d%n", nFree);
    ps.format("   Pages:                       %8d%n", pages.length);
    ps.format("   Partially used pages:        %8d%n", nPartials);
    ps.format("   Total allocation operations: %8d%n", nAllocationOperations);
    ps.format("   Total free operations        %8d%n", nFreeOperations);
    ps.format("Constrained edges               %8d%n", nConstrained);
    ps.format("   Region borders:              %8d%n", nConstraintBorder);
    ps.format("   Region interior:             %8d%n", nConstraintInterior);
  }

  /**
   * Gets the maximum value of an edge index that is currently allocated
   * within the edge pool.
   *
   * @return a positive number or zero if the pool is currently unallocated.
   */
  public int getMaximumAllocationIndex() {
    for (int iPage = pages.length - 1; iPage >= 0; iPage--) {
      SemiVirtualEdgePage p = pages[iPage];
      if (p.nPairsAllocated > 0) {
        return (p.pageID + 1) * INDICES_PER_PAGE;
      }
    }
    return 0;
  }

  SemiVirtualEdge getEdgeForIndex(int index) {
    int iPage = index / INDICES_PER_PAGE;
    SemiVirtualEdgePage page = pages[iPage];
    return new SemiVirtualEdge(this, page, index);
  }

  void getEdgeForIndexWithReceiver(final SemiVirtualEdge receiver, final int index, Vertex a, Vertex b) {
    int iPage = index / INDICES_PER_PAGE;
    SemiVirtualEdgePage page = pages[iPage];
    receiver.page = page;
    receiver.index = index;
    receiver.indexOnPage = index & INDEX_MASK;

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
  public Iterator<IQuadEdge> iterator() {
    return getIterator(true);
  }
    
  /**
   * Constructs an iterator that will optionally skip ghost edges.
   *
   * @param includeGhostEdgess indicates that ghost edges are included.
   * @return a valid instance of an iterator
   */
  Iterator<IQuadEdge> getIterator(final boolean includeGhostEdges) {
    return new Iterator<IQuadEdge>() {
      boolean skipGhostEdges = !includeGhostEdges;
      int iEdge;
      int iPage;
      int[] map = pages[0].getAllocations();
      boolean hasNext = processHasNext(0, -1);

      private boolean processHasNext(int pageIndex, int edgeIndex) {
        iPage = pageIndex;
        iEdge = edgeIndex;
        while (iPage < pages.length) {
          iEdge++;
          if (iEdge >= map.length) {
            iPage++;
            iEdge = -1;
            if (iPage == pages.length) {
              return false;
            }
            map = pages[iPage].getAllocations();
            continue;
          } else {
            // there is an edge, do we need to test whether it's a ghost?
            if (skipGhostEdges) {
              int i = map[iEdge]&INDEX_MASK;
              Vertex[] v = pages[iPage].vertices;
              if (v[i] == null || v[i + 1] == null) {
                continue;
              }
            }
            return true;
          }
        }

        return false;
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public SemiVirtualEdge next() {
        if (!hasNext) {
          return null;
        }
        int index = map[iEdge];
        SemiVirtualEdgePage page = pages[iPage];

        SemiVirtualEdge e = new SemiVirtualEdge(self, page, index);
        hasNext = processHasNext(iPage, iEdge);
        return e;
      }

      /**
       * Overrides the default remove operation with an implementation that
       * throws an UnsupportedOperationException. Tinfour requires a specific
       * set of relationships between edges, and removing an edge from an
       * iterator would damage the overall structure and result in faulty
       * behavior. Therefore, Tinfour iterators do not support remove
       * operations.
       */
      @Override
      public void remove() {
        throw new UnsupportedOperationException(
                "Remove operation not supported by this iterator");
      }

    };
  }

  /**
   * Split the edge e into two by inserting a new vertex m into
   * the edge. The insertion point does not necessarily have to lie
   * on the segment. This method splits the segment into two segments
   * so that edge e(a,b) becomes edges p(a,m) and and e(m,b),
   * with forward and reverse links for both segments being adjusted
   * accordingly. The new segment p(a,m) is returned and the input segment
   * e is adjusted with new vertices (m,b).
   * <p>
   * The split edge method preserves constraint flags and other attributes
   * associated with the edge.
   *
   * @param e the input segment
   * @param m the insertion vertex
   * @return a valid instance of a QuadEdge or QuadEdgePartner (depending
   * on the class of the input)
   */
  public SemiVirtualEdge splitEdge(SemiVirtualEdge e, Vertex m) {
    //TO DO: The code where we set up links is just the same stuff
    //       as the conventional edge pool.  We may be able to improve
    //       performance by just setting the links directly rather than
    //       creating SemiVirtualEdge instances. However, Java is pretty
    //       amazing at optimizing these days, and there is a good
    //       chance that the saving will be small compared to the effort
    //       of writing the code.
    SemiVirtualEdge d = e.getDual();
    SemiVirtualEdge eR = e.getReverse();
    SemiVirtualEdge dF = d.getForward();

    Vertex a = e.getA();

    e.setA(m);
    SemiVirtualEdge p = this.allocateEdge(a, m);
    SemiVirtualEdge q = p.getDual();

    p.setForward(e);
    p.setReverse(eR);
    q.setForward(dF);
    q.setReverse(d);

    SemiVirtualEdgePage ePage = e.page;
    SemiVirtualEdgePage pPage = p.page;
    if (ePage.constraints == null) {
      return p; // we're done
    }
    pPage.readyConstraints();
    // recall that constraints flags are shared between edge pairs
    // so we divide index by two
    int constraintFlags = ePage.constraints[e.indexOnPage / 2];
    pPage.constraints[p.indexOnPage / 2] = constraintFlags;
    
        // p is on the same side of the original edge e and
    // q is on the same side as the dual edge d.
    if (e.isConstrainedRegionBorder()) {
      IConstraint c = borderConstraintMap.get(e.getIndex());
      if (c != null) {
        this.addBorderConstraintToMap(p, c);
      }
      c = borderConstraintMap.get(d.getIndex());
      if (c != null) {
        addBorderConstraintToMap(q, c);
      }
    }
    
    return p;
  }

  

  /**
   * Adds the specified constraint to the border constraint map, thus recording
   * which region constraint lies to the left side of the edge (e.g. which
   * region is bordered by the specified edge).
   * @param edge a valid edge instance
   * @param constraint a valid constraint instance
   */
  public void addBorderConstraintToMap(IQuadEdge edge, IConstraint constraint){
     borderConstraintMap.put(edge.getIndex(), constraint);
  }
  
  
  /**
   * Adds the specified constraint to the linear constraint map, thus recording
   * which constraint lies to the left side of the edge.
   * @param edge a valid edge instance
   * @param constraint a valid constraint instance
   */
  public void addLinearConstraintToMap(IQuadEdge edge, IConstraint constraint){
    int index = edge.getIndex();
     linearConstraintMap.put(index, constraint);
     linearConstraintMap.put(index^1, constraint);
  }
  
  /**
   * Removes any existing border constraint from the constraint map.
   * @param edge a valid edge instance
   */
  public void removeBorderConstraintFromMap(IQuadEdge edge){
    borderConstraintMap.remove(edge.getIndex());
  }
  
    /**
   * Removes any existing border constraint from the constraint map.
   * @param edge a valid edge instance
   */
  public void removeLinearConstraintFromMap(IQuadEdge edge){
    linearConstraintMap.remove(edge.getIndex());
  }
  
  /**
   * Gets the border constraint associated with the edge.
   * @param edge a valid edge instance.
   * @return if a border constraint is associated with the edge, a valid
   * instance; otherwise, a null.
   */
  public IConstraint getBorderConstraint(IQuadEdge edge){
    if(edge.isConstrainedRegionBorder()){
     return borderConstraintMap.get(edge.getIndex());
    }
    return null;
  }
  
  /**
   * Gets the linear constraint associated with the edge, if any.
   *
   * @param edge a valid edge instance.
   * @return if a linear constraint is associated with the edge, a valid
   * instance; otherwise, a null.
   */
  public IConstraint getLinearConstraint(IQuadEdge edge) {
    if (edge.isConstraintLineMember()) {
      return linearConstraintMap.get(edge.getIndex());
    }
    return null;
  }

  
  
  
  
  
}
