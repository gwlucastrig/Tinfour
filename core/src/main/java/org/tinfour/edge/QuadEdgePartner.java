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
package org.tinfour.edge;



import org.tinfour.common.Vertex;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_EDGE_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_INDEX_MASK;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_INDEX_MAX;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_LINE_MEMBER_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_INTERIOR_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_MEMBER_FLAGS;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_BORDER_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.SYNTHETIC_EDGE_FLAG;


/**
 * Used to define the dual (and side 1) of a pair of edge objects.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
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
    return dual.index+1;
  }

  @Override
  public int getBaseIndex() {
    return dual.index;
  }

  @Override
  protected void setIndex(final int index) {
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
   * An implementation of the equals method which check for a matching
   * reference.
   *
   * @param o a valid reference or a null
   * @return true if the specified reference matches this.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof QuadEdgePartner) {
      return this == o;
    }
    return false;
  }


  @Override
  public int hashCode() {
    int hash = 7;
	hash = 11 * hash + getIndex();
	return hash;
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
    return index & CONSTRAINT_INDEX_MASK;
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
    if (constraintIndex < 0 || constraintIndex >  CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." +  CONSTRAINT_INDEX_MAX + "]");
    }
    // this one sets the constraint index, but does not affect
    // whether the edge is constrained or not.  An edge that is
    // a constraint-area member may have a constraint index even if
    // it is not a constrained edge.
    index = (index & ~CONSTRAINT_INDEX_MASK) | constraintIndex;
  }



  /**
   * Sets an edge as constrained and sets its constraint index. Note that
   * once an edge is constrained, it cannot be set to a non-constrained
   * status. Constraint index values must be positive integers in
   * the range 0 to QuadEdge&#46;CONSTAINT_INDEX_MAX (1048575).
   *
   * @param constraintIndex positive number indicating which constraint
   * a particular edge is associated with, in the range 0 to 1048575.
   */
  @Override
  public void setConstrained(int constraintIndex) {
    if (constraintIndex < 0 || constraintIndex >  CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." +  CONSTRAINT_INDEX_MAX + "]");
    }
    index = CONSTRAINT_EDGE_FLAG | ((index & ~CONSTRAINT_INDEX_MASK) | constraintIndex);
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
  public boolean isConstrainedRegionBorder() {
    return (index & CONSTRAINT_REGION_BORDER_FLAG) !=0;
  }

  @Override
  public boolean isConstrainedRegionInterior() {
    return (index & CONSTRAINT_REGION_INTERIOR_FLAG) != 0;
  }

    @Override
  public boolean isConstrainedRegionMember() {
    return (index & CONSTRAINT_REGION_MEMBER_FLAGS) != 0;
  }


    @Override
  public void setConstrainedRegionBorderFlag() {
    index |= CONSTRAINT_REGION_BORDER_FLAG;
  }


  @Override
  public boolean isConstraintLineMember(){
    return (index & CONSTRAINT_LINE_MEMBER_FLAG)!=0;
  }

  @Override
  public void setConstraintLineMemberFlag(){
    index |= CONSTRAINT_EDGE_FLAG | CONSTRAINT_LINE_MEMBER_FLAG;
  }



  @Override
  public void setConstrainedRegionInteriorFlag() {
    index |= CONSTRAINT_REGION_INTERIOR_FLAG;
  }


  @Override
  public void setSynthetic(boolean status) {
    if (status) {
      index |= SYNTHETIC_EDGE_FLAG;
    } else {
      index &= ~SYNTHETIC_EDGE_FLAG;
    }
  }

  @Override
  public boolean isSynthetic() {
    return (index & SYNTHETIC_EDGE_FLAG) != 0;
  }
}
