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
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_FLAG_MASK;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_INDEX_BIT_SIZE;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_INDEX_VALUE_MAX;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_LINE_MEMBER_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_LOWER_INDEX_MASK;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_LOWER_INDEX_ZERO;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_BORDER_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_INTERIOR_FLAG;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_REGION_MEMBER_FLAGS;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_UPPER_INDEX_MASK;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_UPPER_INDEX_ZERO;
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
     if(isConstraintRegionBorder()){
        return getUpperConstraintIndex();
    }
    if(isConstraintRegionInterior()){
      return getLowerConstraintIndex();
    }
    // it must be a constraint line
    return getUpperConstraintIndex();
  }

  /**
   * Sets the constraint index for this edge. Note that setting the constraint
   * index does not necessarily set an edge to a constrained status.
   * In some cases it may be used to indicate the constraint with which a
   * non-constrained edge is associated. Index values must be in the range
   * 0 to QuadEdge&#46;CONSTAINT_INDEX_MAX (8190).
   *
   * @param constraintIndex a positive number in the range 0 to 8190
   * indicating the constraint with which the edge is associated.
   */
  @Override
  public void setConstraintIndex(int constraintIndex) {
    if (isConstraintRegionMember()) {
      checkConstraintIndex(-1, constraintIndex);
    } else if (this.isConstraintLineMember()) {
      checkConstraintIndex(0, constraintIndex);
    } else {
      throw new IllegalArgumentException(
        "Unable to set constraint index for an edge that is not assigned a constraint type");
    }

    if(isConstraintRegionBorder()){
      setUpperConstraintIndex(constraintIndex);
    }else if(isConstraintLineMember()){
      setUpperConstraintIndex(constraintIndex);
    }else{
      setLowerConstraintIndex(constraintIndex);
    }
  }

  /**
   * Indicates whether the edge is constrained
   *
   * @return true if the edge is constrained; otherwise, false.
   */
  @Override
  public boolean isConstrained() {
    return index < 0;  // the CONSTRAINT_FLAG is also the sign bit.
  }

  @Override
  public boolean isConstraintRegionBorder() {
    return (index & CONSTRAINT_REGION_BORDER_FLAG) !=0;
  }

  @Override
  public boolean isConstraintRegionInterior() {
    return (index & CONSTRAINT_REGION_INTERIOR_FLAG) != 0;
  }

    @Override
  public boolean isConstraintRegionMember() {
    return (index & CONSTRAINT_REGION_MEMBER_FLAGS) != 0;
  }


    @Override
  public void setConstraintRegionBorderFlag() {

    if (!isConstraintRegionBorder()) {
      // The edge was not previously populated as a border.
      // Because border constraint settings supercede settings such as
      // linear or interior constraint values, clear out
      // any existing constraint values (the flags are preserved)
      index &= CONSTRAINT_FLAG_MASK;
    }

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

  @Override
  public void setConstraintBorderIndex(int constraintIndex) {
    if (constraintIndex < -1 || constraintIndex > CONSTRAINT_INDEX_VALUE_MAX) {
      throw new IllegalArgumentException(
        "Constraint index " + constraintIndex
        + " is out of range [0.." + CONSTRAINT_INDEX_VALUE_MAX + "]");
    }

    if (!isConstraintRegionBorder()) {
      // The edge was not previously populated as a border.
      // Because border constraint settings supercede settings such as
      // linear or interior constraint values, clear out
      // any existing constraint values (the flags are preserved)
      index &= CONSTRAINT_FLAG_MASK;
    }

    index
      = (CONSTRAINT_EDGE_FLAG | CONSTRAINT_REGION_BORDER_FLAG)
      | (index & CONSTRAINT_UPPER_INDEX_ZERO)
      | ((constraintIndex + 1) << CONSTRAINT_INDEX_BIT_SIZE);
  }



  @Override
  public int getConstraintBorderIndex() {
    if ((index & CONSTRAINT_REGION_BORDER_FLAG) == 0) {
      return -1;
    } else {
      return ((index >> CONSTRAINT_INDEX_BIT_SIZE) & CONSTRAINT_LOWER_INDEX_MASK) - 1;
    }
  }

  @Override
  public void setConstraintLineIndex(int constraintIndex) {
     checkConstraintIndex(0, constraintIndex);

    if (isConstraintRegionBorder()) {
      // Unfortunately, there is not room to store the constraint line index.
      // Just set the constraint line flag.
      index |= (CONSTRAINT_EDGE_FLAG | CONSTRAINT_LINE_MEMBER_FLAG);
    } else {
      index
        = (CONSTRAINT_EDGE_FLAG | CONSTRAINT_LINE_MEMBER_FLAG)
        | (index & CONSTRAINT_UPPER_INDEX_ZERO)
        | ((constraintIndex + 1) << CONSTRAINT_INDEX_BIT_SIZE);
    }
  }

  @Override
  public void setConstraintRegionInteriorIndex(int constraintIndex) {
    checkConstraintIndex(-1, constraintIndex);
    if (isConstraintRegionBorder()) {
      // Not an appropriate operation. No action supported.
      return;
    } else {
      index
        = CONSTRAINT_REGION_INTERIOR_FLAG
        | (index & CONSTRAINT_LOWER_INDEX_ZERO)
        | (constraintIndex + 1);
    }
  }

  @Override
  protected void setUpperConstraintIndex(int constraintIndex) {
    index = (index & CONSTRAINT_UPPER_INDEX_ZERO)
      | ((constraintIndex + 1) << CONSTRAINT_INDEX_BIT_SIZE);
  }

  @Override
  protected int getUpperConstraintIndex() {
    return ((index & CONSTRAINT_UPPER_INDEX_MASK) >> CONSTRAINT_INDEX_BIT_SIZE) - 1;
  }

  @Override
  protected void setLowerConstraintIndex(int constraintIndex) {
    index = (index & CONSTRAINT_LOWER_INDEX_ZERO)
      | (constraintIndex + 1);
  }

  @Override
  protected int getLowerConstraintIndex() {
    return (index & CONSTRAINT_LOWER_INDEX_MASK) - 1;
  }

  @Override
  public int getConstraintRegionInteriorIndex() {
    if (isConstraintRegionInterior()) {
      return getLowerConstraintIndex();
    }
    return -1;
  }

  @Override
  public int getConstraintLineIndex() {
    if (isConstraintLineMember()) {
      return getUpperConstraintIndex();
    }
    return -1;
  }
}
