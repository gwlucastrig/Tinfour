/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * ------   --------- -------------------------------------------------
 * 08/2018  G. Lucas  Initial implementation 
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Assign vertices color index values so that no two connected vertices have the
 * same color. The algorithm used for the assignment is based on Alfred Kempe's
 * 6-color colorization scheme. Vertices will be assigned color indices in the
 * range 0 to 5. These can be applied by rendering applications as required.
 * <p>
 * This implementation runs in O(n^2) time.
 * <p>
 * A 5-color algorithm for color-coding vertices also exists and has
 * similar time-complexity as this implementation. It is more complicated
 * than this routine, but not unreasonably so. Therefore, the Tinfour 
 * project may consider the 5-color algorithm for future implementations.
 */
public class VertexColorizerKempe6 {

  /**
   * Assign color index values to vertices.
   *
   * @param tin a valid instance
   */
  public void assignColorsToVertices(IIncrementalTin tin) {
    if (tin == null) {
      throw new IllegalArgumentException(
              "Null input not supported");
    }
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Unable to process input, TIN is not bootstrapped");
    }

    // this implementation uses the vertex index member element to
    // keep track of neighbor counts.  Since we do not wish permanently
    // modify the input application's vertices, we will restore those 
    // indicates at the end.
    List<Vertex> masterList = tin.getVertices();
    int[] masterIndex = new int[masterList.size()];
    for (int i = 0; i < masterList.size(); i++) {
      Vertex v = masterList.get(i);
      masterIndex[i] = v.getIndex();
      v.setAuxiliaryIndex(6); // special code for unassigned
    }

    List<Vertex> vertexList = new ArrayList<>();
    boolean visited[] = new boolean[tin.getMaximumEdgeAllocationIndex() + 1];

    for (IQuadEdge e : tin.edges()) {
      if (!visited[e.getIndex()]) {
        setDegree(e, visited, vertexList);
      }
      IQuadEdge d = e.getDual();
      if (!visited[d.getIndex()]) {
        setDegree(d, visited, vertexList);
      }
    }

    // A proof in graph theory shows that the input TIN will always include
    // at least one vertex that connects to no more than 5 neighbors.
    // The minimum number of connections is two.  For a sufficiently large
    // population, the average number of connections is 6.
    ArrayDeque<Vertex> stack = new ArrayDeque<>();
    IIncrementalTinNavigator navigator = tin.getNavigator();
    while (!vertexList.isEmpty()) {
      Vertex vRemove = null;
      for (Vertex v : vertexList) {
        if (v.getIndex() <= 5) {
          vRemove = v;
          break;
        }
      }
      vertexList.remove(vRemove);
      stack.push(vRemove);
      IQuadEdge eRemove = locateEdge(navigator, vRemove);
      if(eRemove==null){
        throw new IllegalStateException("Internal error, unable to locate edge");
      }
      for (IQuadEdge p : eRemove.pinwheel()) {
        Vertex B = p.getB();
        if (B != null) {
          B.setIndex(B.getIndex() - 1);
        }
      }
    }

    for (int i = 0; i < masterList.size(); i++) {
      masterList.get(i).setIndex(masterIndex[i]);
    }

    boolean[] flag = new boolean[7];  // allow room for special index 6
    int iStart = 0;
    while (!stack.isEmpty()) {
      Vertex v = stack.pop();
      IQuadEdge e = locateEdge(navigator, v);
      Arrays.fill(flag, true);
      for (IQuadEdge p : e.pinwheel()) {
        Vertex B = p.getB();
        if (B != null) {
          int colorIndex = B.getAuxiliaryIndex();
          flag[colorIndex] = false;
        }
      }
      for (int i = 0; i < 6; i++) {
        int iTest = (i + iStart) % 6;
        if (flag[iTest]) {
          v.setAuxiliaryIndex(iTest);
          break;
        }
      }
      iStart++;
    }
  }

  /**
   * A test utility for verifying the results from the assignColorsToVertices
   * method. This method is intended for debugging and development purposes
   * only.
   * <p>
   * This method tests for two potential failure conditions.
   * <ol>
   * <li>a vertex that was not assigned a valid color.</li>
   * <li>a pair of adjacent vertices assigned the same color</li>
   * </ol>
   * At this time, there are no known conditions under which the
   * color-assignment algorithm fails.
   *
   * @param tin a valid instance
   * @param ps a optional print source, or a null if no output is desired.
   * @return true if the color indices for the vertices in the TIN are
   * successfully assigned; otherwise, false.
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  public boolean verifyAssignments(IIncrementalTin tin, PrintStream ps) {
    if (tin == null) {
      throw new IllegalArgumentException(
              "Null input not supported");
    }
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Unable to process input, TIN is not bootstrapped");
    }
    
    boolean visited[] = new boolean[tin.getMaximumEdgeAllocationIndex() + 1];
    for (IQuadEdge edge : tin.edges()) {
      if (!visited[edge.getIndex()]) {
        Vertex A = edge.getA();
        int test = A.getAuxiliaryIndex();
        if (test < 0 || test > 5) {
          if (ps != null) {
            ps.println("Unassigned color index for vertex " + A.getIndex());
          }
          return false;
        }
        for (IQuadEdge e : edge.pinwheel()) {
          visited[e.getIndex()] = true;
          Vertex B = e.getB();
          if (B != null && B.getAuxiliaryIndex() == test) {
            if (ps != null) {
              ps.println("Adjacent vertices share a common color index for vertices: "
                      + A.getIndex() + ", " + B.getIndex());
            }
            return false;
          }
        }
      }
      IQuadEdge dual = edge.getDual();
      if (!visited[dual.getIndex()]) {
        Vertex A = dual.getA();
        int test = A.getAuxiliaryIndex();
        if (test < 0 || test > 5) {
          if (ps != null) {
            ps.println("Unassigned color index for vertex " + A.getIndex());
          }
          return false;
        }
        for (IQuadEdge e : dual.pinwheel()) {
          visited[e.getIndex()] = true;
          Vertex B = e.getB();
          if (B != null && B.getAuxiliaryIndex() == test) {
            if (ps != null) {
              ps.println("Adjacent vertices share a common color index for vertices: "
                      + A.getIndex() + ", " + B.getIndex());
            }
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Sets the degree (number of connected neighbors) for a vertex,
   * also setting the edge-visited array and adding the vertex to
   * the working vertex list.
   * @param e a valid edge
   * @param visited boolean flags for edge visits
   * @param vertexList the working vertex list
   */
  private void setDegree(IQuadEdge e, boolean[] visited, List<Vertex> vertexList) {
    Vertex A = e.getA();
    if (A != null) {
      vertexList.add(A);
      int n = 0;
      for (IQuadEdge p : e.pinwheel()) {
        visited[p.getIndex()] = true;
        if (p.getB() != null) {
          n++;
        }
      }
      A.setIndex(n);
      A.setAuxiliaryIndex(0);
    }
  }

  /**
   * Locate an edge which begins with the specified vertex.
   * @param navigator the edge locator associated with the tin
   * @param v a valid vertex
   * @return  a valid edge
   */
  private IQuadEdge locateEdge(IIncrementalTinNavigator navigator, Vertex v) {
    // The Tinfour edge locator identifies an edge belonging to a
    // Triangle in which vertex V lies.  The locator is a general-purpose
    // utility that does not assume that the input coordinates necessarily
    // belong to a vertex. Thus it does not guarantee that the edge
    // will include the input vertex.  Since the colorization logic
    // requires a vertex that begins with input vertex v, a bit more
    // work is required to select the correct edge.
    IQuadEdge e = navigator.getNeighborEdge(v.getX(), v.getY());
    if(e == null){
      // won't happen except when the TIN is not bootstraped
      return null;
    }
    if (e.getA() == v) {
      return e;
    }
    IQuadEdge f = e.getForward();
    if (f.getA() == v) {
      return f;
    }

    IQuadEdge r = e.getReverse();
    if (r.getA() == v) {
      return r;
    }
    throw new IllegalStateException("Internal error");
  }

}
