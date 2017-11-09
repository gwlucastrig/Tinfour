/* --------------------------------------------------------------------
 * Copyright 2017 Gary W. Lucas.
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
 * 10/2017  M. Janda     Created
 *
 * Notes:
 *   This class was written by Martin Janda.
 *
 * -----------------------------------------------------------------------
 */
package tinfour.utils;

import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;

import java.util.Iterator;
import java.util.function.Consumer;
import tinfour.common.IConstraint;

public final class TriangleCollector {

  /**
   * Number of bits in an integer.
   */
  private static final int INT_BITS = 32;  //NOPMD

  /**
   * Used to perform a modulus 32 operation on an integer through a bitwise AND.
   */
  private static final int MOD_BY_32 = 0x1f; //NOPMD

  /**
   * Number of shifts to divide an integer by 32.
   */
  private static final int DIV_BY_32 = 5;  //NOPMD

  /**
   * Number of sides for an edge (2 of course).
   */
  private static final int N_SIDES = 2;  //NOPMD

  /**
   * Used to extract the low-order bit via a bitwise AND.
   */
  private static final int BIT1 = 0x01;   //NOPMD

  private TriangleCollector() {
    throw new InternalError("Utility class - should not reach here");
  }

  /**
   * Gets the edge mark bit.
   *
   * @param map an array at least as large as the largest edge index
   * divided by 32, rounded up.
   * @param edge a valid edge
   * @return if the edge is marked, a non-zero value; otherwise, a zero.
   */
  private static int getMarkBit(final int[] map, final IQuadEdge edge) {
    int index = edge.getIndex();
    //int mapIndex = index >> DIV_BY_32;
    //int bitIndex = index & MOD_BY_32;
    //return (map[mapIndex]>>bitIndex)&BIT1;
    return (map[index >> DIV_BY_32] >> (index & MOD_BY_32)) & BIT1;
  }

  /**
   * Set the mark bit for an edge to 1.
   *
   * @param map an array at least as large as the largest edge index
   * divided by 32, rounded up.
   * @param edge a valid edge
   */
  private static void setMarkBit(final int[] map, final IQuadEdge edge) {
    int index = edge.getIndex();
    //int mapIndex = index >> DIV_BY_32;
    //int bitIndex = index & MOD_BY_32;
    //map[mapIndex] |= (BIT1<<bitIndex);
    map[index >> DIV_BY_32] |= (BIT1 << (index & MOD_BY_32));
  }

  /**
   * Traverses the TIN, visiting all triangles that are members of a constrained
   * region. As triangles are identified, this method calls the accept method of
   * a consumer.
   *
   * @param tin a valid instance
   * @param consumer an application-specific consumer.
   */
  public static void visitTrianglesConstrained(
          final IIncrementalTin tin,
          final Consumer<Vertex[]> consumer) {
    int maxMapIndex = tin.getMaximumEdgeAllocationIndex() + 2;
    int mapSize = (maxMapIndex + INT_BITS - 1) / INT_BITS;
    int[] map = new int[mapSize];
    Iterator<IQuadEdge> iterator = tin.getEdgeIterator();
    while (iterator.hasNext()) {
      IQuadEdge e = iterator.next();
      if (e.getA() == null || e.getB() == null) {
        setMarkBit(map, e);
        setMarkBit(map, e.getDual());
        continue;
      }
      if (e.isConstrainedRegionInterior()) {
        processEdge(consumer, map, e);
        processEdge(consumer, map, e.getDual());
      }
    }
  }

  private static void processEdge(
          final Consumer<Vertex[]> consumer,
          final int[] map,
          final IQuadEdge e) {

    if (getMarkBit(map, e) == 0) {
      setMarkBit(map, e);
      IQuadEdge f = e.getForward();

      if (getMarkBit(map, f) != 0) {
        return;
      }
      setMarkBit(map, f);

      IQuadEdge r = e.getReverse();
      if (getMarkBit(map, r) != 0) {
        return;
      }
      setMarkBit(map, r);
      if (r.getB() == null || f.getB() == null) {
        return;
      }

      Vertex[] trig = new Vertex[]{e.getA(), f.getA(), r.getA()};
      consumer.accept(trig);

    }
  }

  /**
   * Traverses the interior of a constrained region, visiting the triangles in
   * its interior. As triangles are identified, this method calls the accept
   * method of a consumer.
   *
   * @param constraint a valid instance defining a constrained region
   * that has been added to a TIN.
   * @param consumer an application-specific consumer.
   */
  public static void visitTrianglesForConstrainedRegion(
          final IConstraint constraint,
          final Consumer<Vertex[]> consumer) {
    final IIncrementalTin tin = constraint.getManagingTin();
    if (tin == null) {
      throw new IllegalArgumentException(
              "Constraint is not under TIN management");
    }
    if (!constraint.definesConstrainedRegion()) {
      throw new IllegalArgumentException(
              "Constraint does not define constrained region");
    }
    IQuadEdge linkEdge = constraint.getConstraintLinkingEdge();
    if (linkEdge == null) {
      throw new IllegalArgumentException(
              "Constraint does not have linking edge");
    }

    int maxMapIndex = tin.getMaximumEdgeAllocationIndex() + 2;
    int mapSize = (maxMapIndex + INT_BITS - 1) / INT_BITS;
    int[] map = new int[mapSize];

    recursiveTraversal(linkEdge, map, consumer);

  }

  private static void recursiveTraversal(
          IQuadEdge e,
          int[] map,
          Consumer<Vertex[]> consumer) {
    if (getMarkBit(map, e) == 0) {
      IQuadEdge f = e.getForward();
      if (getMarkBit(map, f) != 0) {
        return;
      }
      setMarkBit(map, f);

      IQuadEdge r = e.getReverse();
      if (getMarkBit(map, r) != 0) {
        return;
      }
      setMarkBit(map, r);

      Vertex[] trig = new Vertex[]{e.getA(), f.getA(), r.getA()};
      consumer.accept(trig);

      if (f.isConstrainedRegionInterior()) {
        recursiveTraversal(f.getDual(), map, consumer);
      }
      if (r.isConstrainedRegionInterior()) {
        recursiveTraversal(r.getDual(), map, consumer);
      }
    }
  }

}
