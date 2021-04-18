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
 * An implementation of an iterator for looping through the vertices that form
 * a Triangulated Irregular Network.
 * <p>
 * This iterator will identify all unique vertices that form the structure
 * of the TIN and will deliver them to the calling application. In the case where
 * an application has supplied multiple vertices with the same coordinates,
 * this application will use a "VertexMergerGroup" object in their place.
 * Thus the set of vertex objects input to an Incremental TIN instance is not
 * necessarily the same set of objects produced by this iterator.
 * <p>
 * This class is <i>not</i> thread safe. Applications must not modify the
 * TIN (by adding or removing vertices) while using the iterator.
 */
public class VertexIterator implements Iterator<Vertex> {

  IIncrementalTin tin;
  final Iterator<IQuadEdge> edgeIterator;

  final BitSet visited;
  Vertex nextVertex;
  IQuadEdge nextEdge;

  /**
   * Construct an instance of the iterator based on the specified
   * Incremental TIN structure.
   *
   * @param tin a valid instance
   */
  public VertexIterator(IIncrementalTin tin) {
    this.tin = tin;
    edgeIterator = tin.getEdgeIterator();
    visited = new BitSet(tin.getMaximumEdgeAllocationIndex() + 2);
  }

  @Override
  public boolean hasNext() {
    if (!tin.isBootstrapped()) {
      return false;
    }

    while (nextVertex == null) {
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
        for (IQuadEdge edge : e.pinwheel()) {
          visited.set(edge.getIndex());
        }
        nextVertex = e.getA();
        // if next vertex is a ghost vertex, it will have
        // a null value and will be advanced on the next
        // iteration of this loop
      }
    }
    return true;
  }

  @Override
  public Vertex next() {
    if (nextVertex == null && !hasNext()) {
      throw new NoSuchElementException("No more vertices in TIN");
    }
    Vertex result = nextVertex;
    nextVertex = null;
    return result;
  }

}
