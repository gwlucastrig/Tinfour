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
 * 11/2017  G. Lucas     Replaced recursion with deque
 *
 * Notes:
 *   This class was written by Martin Janda.
 *
 * -----------------------------------------------------------------------
 */
package tinfour.utils;

import java.util.ArrayDeque;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;

import java.util.List;
import java.util.function.Consumer;
import tinfour.common.IConstraint;

/**
 * Provides a utility for collecting triangles from a TIN.
 */
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
   * @param map an array at least as large as the largest edge index divided by
   * 32, rounded up.
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
   * @param map an array at least as large as the largest edge index divided by
   * 32, rounded up.
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
    List<IConstraint> constraintList = tin.getConstraints();
    for (IConstraint constraint : constraintList) {
      if (constraint.definesConstrainedRegion()) {
        visitTrianglesForConstrainedRegion(constraint, consumer);
      }
    }
  }

  /**
   * Traverses the interior of a constrained region, visiting the triangles in
   * its interior. As triangles are identified, this method calls the accept
   * method of a consumer.
   *
   * @param constraint a valid instance defining a constrained region that has
   * been added to a TIN.
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

    if (getMarkBit(map, linkEdge) == 0) {
      visitTrianglesUsingStack(linkEdge, map, consumer);
    }
  }
 
  private static void visitTrianglesUsingStack(
          final IQuadEdge firstEdge,
          final int[] map,
          final Consumer<Vertex[]> consumer) {
    ArrayDeque<IQuadEdge> deque = new ArrayDeque<>();
    deque.push(firstEdge);
    while (!deque.isEmpty()) {
      IQuadEdge e = deque.pop();
      if (getMarkBit(map, e) == 0) {
        IQuadEdge f = e.getForward();
        IQuadEdge r = e.getReverse();
        setMarkBit(map, e);
        setMarkBit(map, f);
        setMarkBit(map, r);
        consumer.accept(new Vertex[]{e.getA(), f.getA(), r.getA()}); //NOPMD

        IQuadEdge df = f.getDual();
        IQuadEdge dr = r.getDual();
        if (getMarkBit(map, df) == 0 && !f.isConstrainedRegionBorder()) {
          deque.push(df);
        }
        if (getMarkBit(map, dr) == 0 && !r.isConstrainedRegionBorder()) {
          deque.push(dr);
        }
      }
    }

  }

}
