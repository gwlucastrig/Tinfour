/* --------------------------------------------------------------------
 * Copyright (C) 2021  Gary W. Lucas.
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
 * 03/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An implementation of an iterator for looping through the triangles
 * implicit in a Triangulated Irregular Network.
 * <p>
 * <strong>Note:</strong> The Tinfour incremental TIN classes do not actual
 * construct triangle objects as part of their structure.  The fundamental element
 * in a TIN is the edge structure.  Triangle objects are constructed by
 * the iterator at run time. This approach reduces the overall memory
 * requirements for the TIN.
 * <p>
 * This iterator will identify all unique triangles implicit in the structure
 * of the TIN and will deliver them to the calling application.
 * <p>
 * This class is <i>not</i> thread safe.  Applications must not modify the
 * TIN (by adding or removing points) while using the iterator.
 */
public class SimpleTriangleIterator implements Iterator<SimpleTriangle> {

  IIncrementalTin tin;
  final Iterator<IQuadEdge> edgeIterator;

  final BitSet visited;
  SimpleTriangle nextTriangle;
  IQuadEdge nextEdge;

  /**
   * Construct an instance of the iterator based on the specified
   * Incremental TIN structure.
   * @param tin a valid instance
   */
  public SimpleTriangleIterator(IIncrementalTin tin) {
    this.tin = tin;
    edgeIterator = tin.getEdgeIterator();

    visited = new BitSet(tin.getMaximumEdgeAllocationIndex() + 2);

  }

  @Override
  public boolean hasNext() {
    if (!tin.isBootstrapped()) {
      return false;
    }

    while (nextTriangle == null) {
      if (nextEdge == null) {
        if (!edgeIterator.hasNext()) {
          return false;
        }
        nextEdge = edgeIterator.next();
      }

      // Advance the nextEdge value as appropriate
      // and then check to see if the selected edge is a candidate
      IQuadEdge e = nextEdge;
      int eIndex = nextEdge.getIndex();
      if ((eIndex & 1) == 0) {
        nextEdge = nextEdge.getDual();
      } else {
        nextEdge = null; // to force retrieval from edge pool
      }

      // see if the edge is a viable candidate for a triangle
      if (!visited.get(eIndex)) {
        IQuadEdge ef = e.getForward();
        IQuadEdge er = e.getReverse();
        visited.set(eIndex);
        visited.set(ef.getIndex());
        visited.set(er.getIndex());
        Vertex A = e.getA();
        Vertex B = ef.getA();
        Vertex C = er.getA();
        if (A != null && B != null && C != null) {
          nextTriangle = new SimpleTriangle(tin, e, ef, er);
          break;
        }
      }
    }
    return true;
  }

  @Override
  public SimpleTriangle next() {
    if (nextTriangle == null) {
      hasNext();
      if (nextTriangle==null) {
        throw new NoSuchElementException("No more triangles in TIN");
      }
    }
    SimpleTriangle result = nextTriangle;
    nextTriangle = null;
    return result;
  }

}
