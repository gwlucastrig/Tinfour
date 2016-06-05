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
 * is currently uncommitted and available for use. The plan is to
 * use at least part of it as a bitmap to represent constrained edges
 * in a proposed implementation of the Constrained Delaunay Triangulation
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
   * @param partner a valid refernece.
   */
  QuadEdgePartner(final QuadEdge partner) {
    super(partner);
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

}
