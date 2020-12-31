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
 * 03/2017  G. Lucas     Moved to public scope
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
 * The QuadEdge index
 *  The idea here is that the index element of a QuadEdge allows
 * the class to compute what page it belongs to.  So when a QuadEdge is
 * freed, it can modify the appropriate page.  However, there is a complication
 * in that we want the base reference for an edge and its dual to have
 * unique indices (for consistency with the SemiVirtualEdge classes).  So
 * the index for a distinct edge is multiplied by 2.  Thus, when trying to
 * relate an edge to a page, the page is identified by dividing the index
 * by 2.
 *--------------------------------------------------------------------------
 */
package org.tinfour.edge;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides an object-pool implementation that the manages
 * the allocation, deletion, and reuse of Edges.
 * <p>This class is written using a very old-school approach as a way of
 * minimizing the frequency with which objects are garbage collected. Edges
 * are extensively allocated and freed as the TIN is built. Were they simply
 * constructed and put out-of-scope, the resulting garbage collection could
 * degrade performance.
 * <p>Note that this class is <strong>not thread safe</strong>.
 * <p>For performance reasons, many of the methods in this class make the
 * assumption that any edges passed into the method are under the management
 * of the current instance. If this assumption is violated, serious
 * errors could occur. For example, if an application uses one edge pool
 * to allocate an edge and then passes it to the deallocEdge method
 * another edge pool instance, both instances could become seriously
 * corrupted.
 */
@SuppressWarnings("PMD.AvoidArrayLoops")
public class EdgePool implements Iterable<IQuadEdge> {

  /**
   * The number of edges in an edge-pool page.
   */
  private static final int EDGE_POOL_PAGE_SIZE = 1024;

  /**
   * The number of Edges stored in a page
   */
  private final int pageSize;

  /**
   * The number of edge indices for a page, a value equal to pageSize*2;
   */
   private final int pageSize2;


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
   * The constraint maps provide a way of tying a constraint object
   * reference to the edges that are associated with it.  Separate maps are
   * maintained for the borders of region constraints (borders) and linear
   * constraints. This indirect method is used to economize on memory use
   * by edges. Although it would be possible
   * to add constraint references to the edge structure, doing so would
   * increase the edge memory use by an unacceptably large degree.
   */
  HashMap<Integer, IConstraint>borderConstraintMap = new HashMap<>();
  HashMap<Integer, IConstraint>linearConstraintMap = new HashMap<>();

  /**
   * Construct a QuadEdge manager allocating a small number
   * of initial edges.
   *
   */
  public EdgePool() {
    this.pageSize = EDGE_POOL_PAGE_SIZE;
    this.pageSize2 = EDGE_POOL_PAGE_SIZE*2;
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
  public void preAllocateEdges(int n) {
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

  public QuadEdge allocateEdge(Vertex a, Vertex b) {
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
  public void deallocateEdge(QuadEdge e) {
    // Note: Although there is a sanity check method that can
    //       be used to verify that the input edge belongs to this
    //       edge pool, it is not used here for performance purposes.
    int iPage = e.getIndex() / pageSize2;
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
  public void dispose() {
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
  public void clear() {
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
    Page p = nextAvailablePage;
    while (p != null) {
      nPartials++;
      p = p.nextPage;
    }
    int nConstrained = 0;
    int nConstraintInterior = 0;
    int nConstraintBorder = 0;
    Iterator<IQuadEdge> it = iterator();
    while(it.hasNext()){
      IQuadEdge e = it.next();
      if(e.isConstrained()){
        nConstrained++;
        if(e.isConstrainedRegionBorder()){
          nConstraintBorder++;
        }
      }else if(e.isConstrainedRegionInterior()){
        nConstraintInterior++;
      }
    }
    ps.format("Edges allocated:             %8d%n", nAllocated);
    ps.format("Edges free:                  %8d%n", nFree);
    ps.format("Pages:                       %8d%n", pages.length);
    ps.format("Partially used pages:        %8d%n", nPartials);
    ps.format("Total allocation operations: %8d%n", nAllocationOperations);
    ps.format("Total free operations:       %8d%n", nFreeOperations);
    ps.format("Constrained edges            %8d%n", nConstrained);
    ps.format("   Region borders:           %8d%n", nConstraintBorder);
    ps.format("   Region interior:          %8d%n", nConstraintInterior);
  }

  @Override
  public Iterator<IQuadEdge> iterator() {
    return getIterator(true);
  }

  /**
   * Constructs an iterator that will optionally skip
   * ghost edges.
   * @param includeGhostEdges indicates that ghost edges are
   * to be included in the iterator production.
   * @return a valid instance of an iterator
   */
  public Iterator<IQuadEdge> getIterator(final boolean includeGhostEdges) {
    Iterator<IQuadEdge> ix = new Iterator<IQuadEdge>() {
      QuadEdge currentEdge;
      int nextPage;
      int nextEdge;
      boolean  skipGhosts = !includeGhostEdges;
      boolean hasNext = findNextEdge(0, -1);

      private boolean findNextEdge(int iPage, int iEdge) {
        nextPage = iPage;
        nextEdge = iEdge;
        while (nextPage < pages.length) {
          nextEdge++;
          if (nextEdge < pages[nextPage].nAllocated) {
            if (skipGhosts) {
              IQuadEdge e = pages[nextPage].edges[nextEdge];
              if (e.getA()==null || e.getB()==null) {
                continue;
              }
            }
            return true;
          } else {
            nextEdge = -1;
            nextPage++;
          }
        }
        return false;
      }

      @Override
      public boolean hasNext() {
        return hasNext;
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
        return p.pageID * pageSize2 + p.nAllocated*2;
      }
    }
    return 0;
  }


  /**
   * Split the edge e into two by inserting a new vertex m into
   * the edge. The insertion point does not necessarily have to lie
   * on the segment.  This method splits the segment into two segments
   * so that edge e(a,b) becomes edges p(a,m) and and e(m,b),
   * with forward and reverse links for both segments being adjusted
   * accordingly. The new segment p(a,m) is returned and the input segment
   * e is adjusted with new vertices (m,b).
   * <p>The split edge method preserves constraint flags and other attributes
   * associated with the edge.
   * @param e the input segment
   * @param m the insertion vertex
   * @return a valid instance of a QuadEdge or QuadEdgePartner (depending
   * on the class of the input)
   */
  public QuadEdge splitEdge(QuadEdge e, Vertex m) {
    QuadEdge b = e.getBaseReference();
    QuadEdge d = e.getDual();

    QuadEdge eR = e.getReverse();
    QuadEdge dF = d.getForward();

    Vertex a = e.getA();

    e.setA(m);
    QuadEdge p = this.allocateEdge(a, m);
    QuadEdge q = p.getDual();

    p.setForward(e);
    p.setReverse(eR);
    q.setForward(dF);
    q.setReverse(d);

    // copy the constraint flags, if any
    p.dual.index = b.dual.index;
    //    if (e instanceof QuadEdgePartner) {
    //      return n.dual;
    //    } else {
    //      return n;
    //    }


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
    }else if(e.isConstraintLineMember()){
      IConstraint c = linearConstraintMap.get(e.getIndex());
      if(c!=null){
        addLinearConstraintToMap(p, c);
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



  private class Page {
    int pageID;
    int pageOffset;
    int nAllocated;
    QuadEdge[] edges;
    Page nextPage;

    Page(int pageID) {
      this.pageID = pageID;
      pageOffset = pageID * pageSize2;
      edges = new QuadEdge[pageSize];
    }

    /**
     * Sets up the array of free Edges. This method is almost always
     * called when a new page is created. The only time it is not is in the
     * compact() operation where Edges will be shifted around.
     */
    void initializeEdges() {
      for (int i = 0; i < pageSize; i++) {
        edges[i] = new QuadEdge(pageOffset + i*2); //NOPMD
      }
    }

    QuadEdge allocateEdge() {
      QuadEdge e = edges[nAllocated];
      e.setIndex(pageID * pageSize2 + nAllocated*2);
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
    @SuppressWarnings("PMD.CollapsibleIfStatements")
    void deallocateEdge(QuadEdge be) {
      // reset to initialization state as necessary.
      // in this following block, we clear all flags that matter.
      // We also set any references to null to prevent
      // object retention and expedite garbage collection.
      //   Note that the variable arrayIndex is NOT the edge index,
      // but rather the array index for the edge within the array of edge pairs
      // stored by this class.

      QuadEdge e = be.getBaseReference();
      int arrayIndex = (e.getIndex() - pageOffset)/2;
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

      if (arrayIndex < nAllocated) {
        QuadEdge swap = edges[nAllocated];
        edges[arrayIndex] = swap;
        int oldIndex = swap.getIndex();
        int newIndex = pageOffset + arrayIndex*2;
        swap.setIndex(newIndex);
        edges[nAllocated] = e;

        // the swap operation will change the index of the line. And, because
        // the index is used as a key into the constraint maps, we need to
        // adjust the entries.  The fact that this action is necessarily
        // highlights one of the disadvantages of the design choice of
        // swapping edges.  It was chosen in an effort to save memory
        // (constrast it with the semi-virtual implementation which
        // maintains a free list).  But it did have side-effects. The
        // semi-virtual implementation may have the better approach.
        if (swap.isConstraintLineMember()) {
          if (linearConstraintMap.containsKey(oldIndex)) {
            IConstraint c = linearConstraintMap.get(oldIndex);
            linearConstraintMap.remove(oldIndex);
            linearConstraintMap.remove(oldIndex ^ 1);
            linearConstraintMap.put(newIndex, c);
            linearConstraintMap.put(newIndex ^ 1, c);
          }
        }
        if (swap.isConstrainedRegionBorder()) {
          if (borderConstraintMap.containsKey(oldIndex)) {
            IConstraint c = borderConstraintMap.get(oldIndex);
            borderConstraintMap.remove(oldIndex);
            borderConstraintMap.put(newIndex, c);
          }
          oldIndex ^= 1;  // set index to dual
          newIndex ^= 1;
          if (borderConstraintMap.containsKey(oldIndex)) {
            IConstraint c = borderConstraintMap.get(oldIndex);
            borderConstraintMap.remove(oldIndex);
            borderConstraintMap.put(newIndex, c);
          }
        }

        e.setIndex(pageOffset + nAllocated*2);  // pro forma, for safety
      }
    }

    boolean isFullyAllocated() {
      return nAllocated == edges.length;
    }
  }
}
