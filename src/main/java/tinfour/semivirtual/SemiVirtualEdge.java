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
 * 10/2015  G. Lucas     Created
 *
 * Notes:
 *
 *  TO DO:  Recently found a need to add the update method for cases
 *          where the geometry of an edge is changed by the flip or
 *          the split operation which were introduced for performing
 *          constraint restorations.  However, I wonder if this could
 *          be avoided by simple changing the code for getA() and getB()
 *          to always consult the state data in the edge pool rather than
 *          maintaining local copies of a,b.  This would be less error
 *          vulnerable to coding errors.  But we must check to see how
 *          often getA() and getB() are called.  If its more than a
 *          couple, we probably should keep local copies.  If it's less
 *          than an average of 2, we would probably gain performance by
 *          not maintaining local copies and would benefit by being able
 *          to get rid of the update() method.
 *
 * -----------------------------------------------------------------------
 */

package tinfour.semivirtual;

import tinfour.common.IQuadEdge;
import static tinfour.common.QuadEdge.CONSTRAINT_AREA_BASE_FLAG;
import static tinfour.common.QuadEdge.CONSTRAINT_AREA_FLAG;
import static tinfour.common.QuadEdge.CONSTRAINT_FLAG;
import static tinfour.common.QuadEdge.CONSTRAINT_INDEX_MASK;
import static tinfour.common.QuadEdge.CONSTRAINT_INDEX_MAX;
import tinfour.common.Vertex;
import static tinfour.semivirtual.SemiVirtualEdgePage.INDEX_MASK;
import static tinfour.semivirtual.SemiVirtualEdgePage.INDICES_PER_PAGE;

/**
 * Provides methods and elements implementing the QuadEdge data structure
 * using a virtual representation of the links based on integer arrays
 * rather than direct class instances.
 */
public final class SemiVirtualEdge implements IQuadEdge {

  private static final int LOW_BIT = 1;
  private static final int MASK_LOW_BIT_CLEAR = ~LOW_BIT;

  final SemiVirtualEdgePool pool;
  SemiVirtualEdgePage page;
  int index;
  int indexOnPage;


  /**
   * Constructs a virtual edge tied to the specifed edge pool.
   *
   * @param pool A valid instance
   * @param page The page on which this edge is to be tied
   * @param index The index at which this edge is to be tied.
   */
  SemiVirtualEdge(SemiVirtualEdgePool pool, SemiVirtualEdgePage page, int index) {
    this.pool = pool;
    this.index = index;
    this.page = page;
    indexOnPage = index & INDEX_MASK;


  }

  /**
   * Constructs an instance not currently tied to any edge/
   *
   * @param pool
   */
  SemiVirtualEdge(SemiVirtualEdgePool pool) {
    this.pool = pool;
  }

  /**
   * Constructs a copy of the current instance.
   *
   * @return a valid instance.
   */
  SemiVirtualEdge copy() {
    return new SemiVirtualEdge(pool, page, index);
  }

  /**
   * Constructs an unassigned edge tied to the same edge pool
   * as the current instance.
   *
   * @return a valid instance.
   */
  SemiVirtualEdge getUnassignedEdge() {
    return new SemiVirtualEdge(pool);
  }

  /**
   * Load the content of the specified edge
   *
   * @param e a valid instance
   */
  void loadFromEdge(SemiVirtualEdge e) {
    this.page = e.page;
    this.index = e.index;
    this.indexOnPage = e.indexOnPage;

  }

  /**
   * Loads the content of the dual of the specified edge.
   *
   * @param e a valid instance.
   */
  void loadDualFromEdge(SemiVirtualEdge e) {
    // one issue to be careful about is that e is often the
    // same instance as the current instance (when we get an
    // edges own dual). so we have to use a temporary variable for the vertices.
    // also, the dual of e is on the same page as e, so we
    // can conserve some operations.
    page = e.page;
    index = e.index ^ LOW_BIT;
    indexOnPage = e.indexOnPage ^ LOW_BIT;

  }

  /**
   * Load the content of the dual of the forward of the specified edge.
   *
   * @param e a valid instance
   */
  void loadDualFromForwardOfEdge(SemiVirtualEdge e) {
    int forwardIndex = e.page.links[e.indexOnPage * 2];
    loadEdgeForIndex(forwardIndex ^ LOW_BIT);
  }

  /**
   * Load the content of the dual of the reverse of the specified edge.
   *
   * @param e a valid instance
   */
  void loadDualFromReverseOfEdge(SemiVirtualEdge e) {
    int reverseIndex = e.page.links[e.indexOnPage * 2 + 1];
    loadEdgeForIndex(reverseIndex ^ LOW_BIT);
  }

  /**
   * Load the edge from the associated edge pool that has the specified
   * index
   *
   * @param index a positive integer corresponding to an edge in the pool
   */
  private void loadEdgeForIndex(int index) {
    this.page = pool.pages[index / INDICES_PER_PAGE];
    this.index = index;
    this.indexOnPage = index & INDEX_MASK;

  }

  /**
   * Load the content of the forward of the specified edge
   *
   * @param e a valid instance
   */
  public void loadForwardFromEdge(SemiVirtualEdge e) {
    int forwardIndex = e.page.links[e.indexOnPage * 2];
    loadEdgeForIndex(forwardIndex);
  }

  /**
   * Load the content of the reverse of the specified edge
   *
   * @param e a valid instance
   */
  public void loadReverseFromEdge(SemiVirtualEdge e) {
    int reverseIndex = e.page.links[e.indexOnPage * 2 + 1];
    loadEdgeForIndex(reverseIndex);
  }

  private SemiVirtualEdge getEdgeForIndex(final int index) {
    final int iPage = index / INDICES_PER_PAGE;
    return new SemiVirtualEdge(pool, pool.pages[iPage], index);
  }

  @Override
  public Vertex getA() {
    int side = index & LOW_BIT;
    int offset = indexOnPage & MASK_LOW_BIT_CLEAR;
   return page.vertices[offset | side];

  }

  @Override
  public Vertex getB() {
       int side = index & LOW_BIT;
    int offset = indexOnPage & MASK_LOW_BIT_CLEAR;
    return page.vertices[offset | (side ^ LOW_BIT)];
  }

  @Override
  public SemiVirtualEdge getForward() {
    int forwardIndex = page.links[indexOnPage * 2];
    return getEdgeForIndex(forwardIndex);
  }

  /**
   * When the edge exists within a TIN, this method gets the
   * apex of a triangle formed with the edge as the base.
   *
   * @return if defined, a valid instance; otherwise, a null.
   */
  public Vertex getTriangleApex() {
    final int forwardIndex = page.links[indexOnPage * 2];
    final SemiVirtualEdgePage fpage = pool.pages[forwardIndex / INDICES_PER_PAGE];
    final int forwardIndexOnPage = forwardIndex & INDEX_MASK;
    return fpage.vertices[forwardIndexOnPage ^ LOW_BIT];
  }

  /**
   * Indicates if the edge is exterior to a TIN.
   *
   * @return true if the edge is exterior; otherwise, false.
   */
  public boolean isExterior() {
    final int forwardIndex = page.links[indexOnPage * 2];
    final SemiVirtualEdgePage fpage = pool.pages[forwardIndex / INDICES_PER_PAGE];
    final int forwardIndexOnPage = forwardIndex & INDEX_MASK;
    return (fpage.vertices[forwardIndexOnPage | LOW_BIT] == null);
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the forward of the current edge.
   *
   * @return a new instances
   */
  @Override
  public SemiVirtualEdge getReverse() {
    int reverseIndex = page.links[indexOnPage * 2 + 1];
    return getEdgeForIndex(reverseIndex);
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the dual of the current edge's reverse.
   *
   * @return a new instances
   */
  public SemiVirtualEdge getDualFromReverse() {
    int reverseIndex = page.links[indexOnPage * 2 + 1];
    int dualIndexOfReverse = reverseIndex ^ LOW_BIT;
    return getEdgeForIndex(dualIndexOfReverse);
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the dual of the current edge.
   *
   * @return a new instances
   */
  @Override
  public SemiVirtualEdge getDual() {
    int dualIndex = index ^ LOW_BIT; // toggle low order bit
    return getEdgeForIndex(dualIndex);
  }

  /**
   * Clear all reference in the virtual edge.
   */
  public void clear() {
    int offset = indexOnPage & MASK_LOW_BIT_CLEAR;
    page.vertices[offset] = null;
    page.vertices[offset + 1] = null;
    offset *= 2;
    page.links[offset] = 0;
    page.links[offset + 1] = 0;
    page.links[offset + 2] = 0;  // links for dual
    page.links[offset + 3] = 0;
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the base of the current edge (may be a copy
   * of the current edge if it is a base).
   *
   * @return a new instances
   */
  @Override
  public SemiVirtualEdge getBaseReference() {
    return getEdgeForIndex(index & MASK_LOW_BIT_CLEAR);
  }

  /**
   * Gets the index of the base of the current edge. The index value
   * for a base is always an even number.
   *
   * @return an even integer value
   */
  public int getBaseIndex() {
    return index & MASK_LOW_BIT_CLEAR;
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the forward of the dual of the current edge
   *
   * @return a new instances
   */
  @Override
  public SemiVirtualEdge getForwardFromDual() {
    int forwardIndex = page.links[(indexOnPage ^ LOW_BIT) * 2];
    return getEdgeForIndex(forwardIndex);
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the dual of the forward of the current edge
   *
   * @return a new instances
   */
  public SemiVirtualEdge getDualFromForward() {
    final int forwardIndex = page.links[indexOnPage * 2];
    final int dualIndexOfForward = forwardIndex ^ LOW_BIT;
    return getEdgeForIndex(dualIndexOfForward);
  }

  /**
   * Constructs a new instance of the virtual edge class
   * referencing the reverse of the dual of the current edge
   *
   * @return a new instances
   */
  @Override
  public SemiVirtualEdge getReverseFromDual() {
    int reverseIndex = page.links[(indexOnPage ^ LOW_BIT) * 2 + 1];
    return getEdgeForIndex(reverseIndex);
  }

  @Override
  public int getIndex() {
    return index;
  }

  @Override
  public double getLength() {
    Vertex a = getA();
    Vertex b = getB();
    if (a == null || b == null) {
      return Double.NaN;
    }
    return a.getDistance(b);

  }

  /**
   * Gets the low-order bit of the index of the current edge
   *
   * @return a zero or a one.
   */
  @Override
  public int getSide() {
    return index & LOW_BIT;
  }

  /**
   * Sets the initial vertex of the current edge (and final vertex
   * of its dual)
   *
   * @param a a valid reference or a null.
   */
  public void setA(Vertex a) {
    page.vertices[indexOnPage] = a;

  }

  /**
   * Sets the final vertex of the current edge (and initial vertex
   * of its dual)
   *
   * @param b a valid reference or a null.
   */
  public void setB(Vertex b) {
    page.vertices[indexOnPage ^ LOW_BIT] = b;

  }

  /**
   * Sets the forward link for the dual of the current edge.
   *
   * @param forward the forward reference/
   */
  public void setDualForward(SemiVirtualEdge forward) {
    int dualIndex = index ^ LOW_BIT;
    int dualIndexOnPage = dualIndex & INDEX_MASK;
    page.links[dualIndexOnPage * 2] = forward.index;
    forward.page.links[forward.indexOnPage * 2 + 1] = dualIndex;
  }

  /**
   * Sets the reverse link for the dual of the current edge.
   *
   * @param reverse the forward reference/
   */
  public void setDualReverse(SemiVirtualEdge reverse) {
    int dualIndex = index ^ LOW_BIT;
    int dualIndexOnPage = dualIndex & INDEX_MASK;
    page.links[dualIndexOnPage * 2 + 1] = reverse.index;
    reverse.page.links[reverse.indexOnPage * 2] = dualIndex;
  }

  /**
   * Sets the forward link for the current edge.
   *
   * @param e the forward reference/
   */
  public void setForward(SemiVirtualEdge e) {
    page.links[indexOnPage * 2] = e.index;
    e.page.links[e.indexOnPage * 2 + 1] = index;
  }

  /**
   * Sets the reverse link for the current edge.
   *
   * @param e the forward reference/
   */
  public void setReverse(SemiVirtualEdge e) {
    page.links[indexOnPage * 2 + 1] = e.index;
    e.page.links[e.indexOnPage * 2] = index;
  }

  /**
   * Sets both vertices for the current edge (and the opposite vertices
   * of its dual).
   *
   * @param a the initial vertex
   * @param b the final vertex
   */
  public void setVertices(Vertex a, Vertex b) {
    int side = indexOnPage & LOW_BIT;
    int offset = indexOnPage & MASK_LOW_BIT_CLEAR;
    page.vertices[offset | side] = a;
    page.vertices[offset | (side ^ LOW_BIT)] = b;

  }

  @Override
  public String toString() {
    Vertex a = getA();
    Vertex b = getB();
    if (a == null && b == null) {
      return String.format("%9d -- Undefined", getIndex());
    }
    int r = page.links[indexOnPage * 2 + 1];
    int f = page.links[indexOnPage * 2];
    String s = String.format("%9d  %9s <-- (%9s,%9s) --> %9s",
      index,
      (r == 0 ? "null" : Integer.toString(r)),
      (a == null ? "gv" : a.getLabel()),
      (b == null ? "gv" : b.getLabel()),
      (f == 0 ? "null" : Integer.toString(f))
    );
    return s;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 29 * hash + this.index;
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SemiVirtualEdge) {
      return index == ((SemiVirtualEdge) o).getIndex();
    }
    return false;
  }

  @Override
  public int getConstraintIndex() {
    if (page.constraints == null) {
      return 0;
    }
    int test = page.constraints[indexOnPage / 2];
    // the CONSTRAINT_FLAG is also the sign bit.
    if (test < 0) {
      return test & CONSTRAINT_INDEX_MASK;
    }
    return 0;
  }

  @Override
  public void setConstraintIndex(int constraintIndex) {
    if (constraintIndex < 0 || constraintIndex > CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." + CONSTRAINT_INDEX_MAX + "]");
    }

    // this one sets the constraint index, but does not affect
    // whether the edge is constrained or not.  An edge that is
    // a constraint-area member may have a constraint index even if
    // it is not a constrained edge.
    int ix = indexOnPage / 2;
    int c[] = page.readyConstraints();
    c[ix] = ((c[ix] & ~CONSTRAINT_INDEX_MASK) | constraintIndex);
  }

  @Override
  public void setConstrained(int constraintIndex) {
    if (constraintIndex < 0 || constraintIndex > CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." + CONSTRAINT_INDEX_MAX + "]");
    }

    int ix = indexOnPage / 2; // both sides of the edge are constrained.
    int c[] = page.readyConstraints();
    c[ix] = CONSTRAINT_FLAG | (c[ix] & ~CONSTRAINT_INDEX_MASK) | constraintIndex;

  }

  @Override
  public boolean isConstrained() {
    if (page.constraints == null) {
      return false;
    } else {
      // the CONSTRAINT_FLAG is also the sign bit.
      return page.constraints[indexOnPage / 2] < 0;
    }
  }

  @Override
  public boolean isConstrainedAreaEdge() {
    if (page.constraints == null) {
      return false;
    } else {
      // this test requires that the edge be both constrained
      // and have its constrained-area flag set.
      // recall that the CONSTRAINT_FLAG is also the sign bit.
      int test = page.constraints[indexOnPage / 2];
      return test < 0 & (test & CONSTRAINT_AREA_FLAG) != 0;
    }
  }

  @Override
  public boolean isConstrainedAreaMember() {
    if (page.constraints == null) {
      return false;
    } else {
      // this tests to see if the edge is a constrained-area member
      // and doesn't care whether or not it is a constraint edge.
      return (page.constraints[indexOnPage / 2] & CONSTRAINT_AREA_FLAG) != 0;
    }
  }

  public boolean isConstraintAreaOnThisSide() {
    if (page.constraints == null) {
      return false;
    } else {
      int flags = page.constraints[indexOnPage / 2];
      if ((flags & CONSTRAINT_AREA_FLAG) == 0) {
        return false;
      }else{
        int side = indexOnPage & LOW_BIT;
        if (side == 0) {
          return (flags & CONSTRAINT_AREA_BASE_FLAG) != 0;
        } else {
          return (flags & CONSTRAINT_AREA_BASE_FLAG) == 0;
        }
      }
    }
  }

  @Override
  public void setConstrainedAreaMemberFlag() {
    int ix = indexOnPage / 2;
    int side = indexOnPage & LOW_BIT;
    int constraintAreaFlag;
    if (side == 0) {
      constraintAreaFlag = CONSTRAINT_AREA_FLAG | CONSTRAINT_AREA_BASE_FLAG;
    } else {
      constraintAreaFlag = CONSTRAINT_AREA_FLAG;
    }
    int c[] = page.readyConstraints();
    c[ix] |= constraintAreaFlag;
  }

  @Override
  public Iterable<IQuadEdge> pinwheel() {
    return new SemiVirtualPinwheel(this);
  }


}
