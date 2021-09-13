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
 *   This class was originally written by Martin Janda.
 *
 * Collecting triangles from constrained regions ------------------
 *  The triangle collector for a constrained region uses a mesh-traversal
 * operation where it traverses from edge to edge, identifying triangles and
 * calling the accept() method from a Java consumer.  In general, this
 * process is straightforward, though there is one special case.
 *  Recall that in Tinfour, the interior to a constrained region is always to
 * the left of an edge.  Thus, a polygon enclosing a region would be given
 * in counterclockwise order.  Conversely, if a polygon were given in
 * clockwise order such that the area it enclosed was always to the right
 * of the edges, the polygon would define a "hole" in the constrained
 * region. The region it enclosed would not belong to the constrained region.
 *   Now imaging a case where a constrained region is defined by a single
 * clockwise polygon somewhere within the overall domain of the Delaunay
 * Triangulation. The "constrained region" that it establishes is somewhat
 * counterintuitively defined as being outside the polygon and extendending
 * to the perimeter of the overall triangulation.
 *   In this case, if we attempt to use traversal, some of the triangles
 * we collect will actually be the "ghost" triangles that define the
 * exterior to the triangulation. Ghost triangles are those that include
 * the so-called "ghost" vertex.  Tinfour manages the ghost vertex using
 * a null vertex.  Thus it would be possible to collect triangles which
 * contain null vertices.   In order to avoid passing null vertices to
 * the accept() method, Tinfour must screen for this condition.
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.util.ArrayDeque;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

import java.util.function.Consumer;
import org.tinfour.common.IConstraint;
import org.tinfour.common.SimpleTriangle;

/**
 * Provides a utility for collecting triangles from a TIN.
 */
@SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
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
   * Gets the edge mark bit. Each edge will have two mark bits, one for the base
   * reference and one for its dual.
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
   * Set the mark bit for an edge to 1. Each edge will have two mark bits, one
   * for the base reference and one for its dual.
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
    map[index >> DIV_BY_32] |= BIT1 << (index & MOD_BY_32);
  }

  /**
   * Traverses the TIN, visiting all triangles that are members of a constrained
   * region. As triangles are identified, this method calls the accept method of
   * a consumer. If the TIN has not been bootstrapped, this routine exits
   * without further processing.
   * <p>
   * All triangles produced by this method are valid (non-ghost) triangles
   * with valid, non-null vertices.
   * <p>
   * <strong>Note:</strong> If no region-based constraints have been
   * added to the Delaunay triangulation, then none of the triangles in
   * the TIN are treated as being constrained.  This method will
   * exit without further processing.
   *
   * @param tin a valid instance
   * @param consumer an application-specific consumer.
   */
  public static void visitTrianglesConstrained(
    final IIncrementalTin tin,
    final Consumer<Vertex[]> consumer) {
    if (tin.isBootstrapped()) {
      for (SimpleTriangle t : tin.triangles()) {
        IConstraint constraint = t.getContainingRegion();
        if (constraint != null && constraint.definesConstrainedRegion()) {
          Vertex[] v = new Vertex[3];
          v[0] = t.getVertexA();
          v[1] = t.getVertexB();
          v[2] = t.getVertexC();
          consumer.accept(v);
        }
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
        // the rationale for the null check is given in the
        // discussion at the beginning of this file.
        Vertex a = e.getA();
        Vertex b = f.getA();
        Vertex c = r.getA();
        if (a != null && b != null && c != null) {
          consumer.accept(new Vertex[]{a, b, c}); //NOPMD
        }

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

  /**
   * Identify all valid triangles in the specified TIN and
   * provide them to the application-supplied Consumer.
   * Triangles are provided as an array of three vertices
   * given in clockwise order. If the TIN
   * has not been bootstrapped, this routine exits without further processing.
   * This routine will not call the accept method for "ghost" triangles
   * (those triangles that include the ghost vertex).
   *
   * @param tin a valid TIN
   * @param consumer a valid consumer.
   */
  public static void visitTriangles(
    final IIncrementalTin tin,
    final Consumer<Vertex[]> consumer) {
    if (!tin.isBootstrapped()) {
      return;
    }
    for (SimpleTriangle t : tin.triangles()) {
      Vertex []v = new Vertex[3];
      v[0] = t.getVertexA();
      v[1] = t.getVertexB();
      v[2] = t.getVertexC();
      consumer.accept(v);
    }
  }

  /**
   * Identify all valid triangles in the specified TIN and
   * provide them to the application-supplied Consumer.
   * Triangles are provided as instances of the SimpleTriangle class. If the TIN
   * has not been bootstrapped, this routine exits without further processing.
   * This routine will not call the accept method for "ghost" triangles
   * (those triangles that include the ghost vertex).
   *
   * @param tin a valid TIN
   * @param consumer a valid consumer.
   */
  public static void visitSimpleTriangles(
    final IIncrementalTin tin,
    final Consumer<SimpleTriangle> consumer) {
    if (!tin.isBootstrapped()) {
      return;
    }
    for(SimpleTriangle t: tin.triangles()){
      consumer.accept(t);
    }
  }
}
