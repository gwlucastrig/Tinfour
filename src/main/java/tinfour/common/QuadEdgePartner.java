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
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 07/2015 G. Lucas Created
 *
 * Notes:
 *
 * The index element which is inherited from the parent class QuadEdge
 * is used as a way of representing constraints according to
 * the definition of the Constrained Delaunay Triangulation.
 *
 * See the parent class for discussion of memory layout and conservation.
 *
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Used to define the dual (and side 1) of a pair of edge objects.
 */
class QuadEdgePartner extends QuadEdge {

  /**
   * Constructs a version of this instance with the specified partner (dual).
   *
   * @param partner a valid refernece.
   */
  QuadEdgePartner(final QuadEdge partner) {
    super(partner);
    index = 0;
  }

  @Override
  public int getIndex() {
    return dual.index;
  }

  @Override
  public void setIndex(final int index) {
    dual.index = index;

  }

  @Override
  public QuadEdge getBaseReference() {
    return dual;
  }

  @Override
  public void setVertices(final Vertex a, final Vertex b) {
    this.v = a;
    this.dual.v = b;
  }

  @Override
  public int getSide() {
    return 1;
  }

  /**
   * Sets all vertices and link references to null (the link to a dual
   * is not affected).
   */
  @Override
  public void clear() {
    // note that the index of the QuadEdgePartner is set to zero but the
    // index of the base QuadEdge, which is used for management purposes
    // is left alone.
    this.v = null;
    this.f = null;
    this.r = null;
    dual.v = null;
    dual.f = null;
    dual.r = null;
    index = 0;
  }

  /**
   * Gets the index of the constraint associated with this edge.
   * Constraint index values must be in the range 0 to 65534
   * with negative numbers being reserved.
   *
   * @return if constrained, a positive integer; otherwise, a negative value.
   */
  @Override
  public int getConstraintIndex() {
    return (index & CONSTRAINT_INDEX_MASK);
  }

  /**
   * Sets the constraint index for this edge. Note that setting the constraint
   * index does not necessarily set an edge to a constrained status.
   * In some cases it may be used to indicate the constraint with which a
   * non-constrained edge is associated. Index values must be in the range
   * 0 to QuadEdge&#46;CONSTAINT_INDEX_MAX (1048575).
   *
   * @param constraintIndex a positive number in the range 0 to 1048575
   * indicating the constraint with which the edge is associated.
   */
  @Override
  public void setConstraintIndex(int constraintIndex) {
    if (constraintIndex < 0 || constraintIndex > QuadEdge.CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." + QuadEdge.CONSTRAINT_INDEX_MAX + "]");
    }
    // this one sets the constraint index, but does not affect
    // whether the edge is constrained or not.  An edge that is
    // a constraint-area member may have a constraint index even if
    // it is not a constrained edge.
    index = ((index & ~CONSTRAINT_INDEX_MASK) | constraintIndex);
  }

  @Override

  /**
   * Sets an edge as constrained and sets its constraint index. Note that
   * once an edge is constrained, it cannot be set to a non-constrained
   * status. Constraint index values must be positive integers in
   * the range 0 to QuadEdge&#46;CONSTAINT_INDEX_MAX (1048575).
   *
   * @param constraintIndex positive number indicating which constraint
   * a particular edge is associated with, in the range 0 to 1048575.
   */
  public void setConstrained(int constraintIndex) {
    if (constraintIndex < 0 || constraintIndex > QuadEdge.CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." + QuadEdge.CONSTRAINT_INDEX_MAX + "]");
    }
    index = CONSTRAINT_FLAG | ((index & ~CONSTRAINT_INDEX_MASK) | constraintIndex);
  }

  /**
   * Gets the index of the constrain associated with
   *
   * @return true if the edge is constrained; otherwise, false.
   */
  @Override
  public boolean isConstrained() {
    return index < 0;  // the CONSTRAINT_FLAG is also the sign bit.
  }

  @Override
  public boolean isConstrainedAreaEdge() {
    return index < 0 && (index & CONSTRAINT_AREA_FLAG) != 0;
  }

  @Override
  public boolean isConstrainedAreaMember() {
    return (index & CONSTRAINT_AREA_FLAG) != 0;
  }

  @Override
  public void setConstrainedAreaMemberFlag() {
    index |= CONSTRAINT_AREA_FLAG;
  }

  @Override
  public boolean isConstraintAreaOnThisSide() {
    return  (index & CONSTRAINT_AREA_BASE_FLAG) == 0;
  }

}
