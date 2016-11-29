/*
 * Copyright 2013 Gary W. Lucas.
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
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------   ---------  -------------------------------------------------
 * 08/2014  G. Lucas   Initial implementation
 * 08/2015  G. Lucas   Refactored for VirtualEdge implementation
 * 02/2016  G. Lucas   Added pinwheel method for case where there is
 *                        an exact vertex match.
 *
 * Notes:
 *
 * This method is rather slow for moderate search depths. Searches have the
 * habit of looping back and covering triangles multiple times.
 * Perhaps there is a way to improve this.
 *
 * -----------------------------------------------------------------------
 */
package tinfour.semivirtual;

import java.util.ArrayList;
import java.util.List;
import tinfour.common.INeighborhoodPointsCollector;
import tinfour.common.IProcessUsingTin;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;

/**
 * Obtains vertices in the neighborhood of a specified set of coordinates
 * by using a TIN traversal scheme.
 */
class SemiVirtualNeighborhoodPointsCollector implements IProcessUsingTin, INeighborhoodPointsCollector {

  final SemiVirtualStochasticLawsonsWalk walker;
  final SemiVirtualIncrementalTin tin;
  final double vertexTolerance2;

  SemiVirtualEdge searchEdge;

  // diagnostic values
  boolean isQueryPointToExterior;
  int maxDepthSearched;

  /**
   * Standard constructor
   *
   * @param tin a valid TIN to be accessed on a read-only basis.
   */
  SemiVirtualNeighborhoodPointsCollector(SemiVirtualIncrementalTin tin, Thresholds thresholds) {
    //   vList = new ArrayList<>();
    this.tin = tin;
    walker = new SemiVirtualStochasticLawsonsWalk(thresholds);
    vertexTolerance2 = thresholds.getVertexTolerance2();
  }

  /**
   * Used by an application to reset the state data within the interpolator
   * when the content of the TIN may have changed. Reseting the state data
   * unnecessarily may result in a minor performance reduction when processing
   * a large number of interpolations, but is otherwise harmless.
   */
  @Override
  public void resetForChangeToTin() {
    searchEdge = null;
    walker.reset();
    //   vList.clear();
    isQueryPointToExterior = false;
  }

  boolean checkForAmbiguity(Vertex a, Vertex b, double x, double y) {
    double ax = a.getX();
    double ay = a.getY();
    double dx = b.getX() - ax;
    double dy = b.getY() - ay;
    double vx = x - ax;
    double vy = y - ay;
    double h = vx * dy - vy * dx;
    return (h == 0);
  }

  private List<Vertex> pinwheel(
    SemiVirtualEdge e0,
    int searchDepth,
    int targetMinVertexCount
  ) {

    List<Vertex> vList = new ArrayList<>();
    Vertex v0 = e0.getA();
    vList.add(v0);
    SemiVirtualEdge c = e0;
    do {
      c = c.getForward();
      Vertex a = c.getA();
      if (a != null) {
        vList.add(c.getA());
      }
      c = c.getForward().getDual();
    } while (!c.equals(e0));

    if (searchDepth > 1) {
      c = e0;
      do {
        c = c.getForward();
        Vertex a = c.getA();
        if (a != null) {
          SemiVirtualEdge sDual = c.getDual();
          standardSearch(vList, sDual.getForward().getDual(), 2, searchDepth);
          standardSearch(vList, sDual.getReverse().getDual(), 2, searchDepth);
        }
        c = c.getForward().getDual();
      } while (!c.equals(e0));
    }

    // The following extends the search from above attempting to search
    // deeper to find more vertices until the min count is reached.
    int nFound = vList.size();
    int nExtras = 0;
    while (nFound < targetMinVertexCount) {
      int nPrior = nFound;
      nExtras++;
      c = e0;
      do {
        c = c.getForward();
        Vertex a = c.getA();
        if (a != null) {
          SemiVirtualEdge sDual = c.getDual();
          extendedSearch(vList, sDual.getForward().getDual(), 2, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, sDual.getReverse().getDual(), 2, searchDepth, searchDepth + nExtras);
        }
        c = c.getForward().getDual();
      } while (c.equals(e0));
      nFound = vList.size();
      if (nFound <= nPrior) {
        break;
      }
    }

    return vList;
  }

  @Override
  public List<Vertex> collectNeighboringVertices(
    double x,
    double y,
    int searchDepth,
    int targetMinVertexCount) {

    isQueryPointToExterior = false;
    maxDepthSearched = 0;

    // search for an edge of a triangle that contains (x,y).
    // because the search is stochastic and statefull, multiple
    // searches on the same coordinate pair can end up identifying
    // different edges. Normally, this will not be an issue.
    // But if (x,y) lies directly on an edge, or exactly matches one
    // of the vertices in the collection, special logic will be required
    // for disambiguation.
    if (searchEdge == null) {
      searchEdge = tin.getStartingEdge();
    }
    searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x, y);
    SemiVirtualEdge eEdge = searchEdge;

    // The walker found an edge of the triangle that encloses (x,y)
    // or a perimeter edge if the point is outside the TIN.
    // There is no guarantee that the resulting edge was the one
    // closest to the coordinates. All three vertices will be the
    // vertices closest to the coordinates except in the case where
    // the query point lies exactly on an edge.
    Vertex v0 = eEdge.getA();
    Vertex v1 = eEdge.getB();
    Vertex v2 = eEdge.getForward().getB();

    if (v2 == null) {
      // The code below assumes that the starting triangle is a regular
      // triangle fully integrated with the TIN.  So jump
      // to its dual.
      eEdge = eEdge.getDual();
      v2 = eEdge.getForward().getB();
      isQueryPointToExterior = true;
    }

    // The return from this routine is supposed to list the nearest
    // three vertices first. Normally, this is not a problem, but it
    // could occur that the test coordinate pair (x,y) lie exactly one
    // one of the vertices. If so, the geometry for neighbor point collection
    // is a little different and we use the pinwheel method.  It is also
    // possible that (x,y) lies exactly on an edge and that the opposite vertex
    // in the adjacent triangle is closer to (x,y) than the vertex in the
    //triangle we have chosen.
    if (v0.getDistanceSq(x, y) < vertexTolerance2) {
      return pinwheel(eEdge,  searchDepth, targetMinVertexCount);
    } else if (v1.getDistanceSq(x, y) < vertexTolerance2) {
      return pinwheel(eEdge.getDual(),  searchDepth, targetMinVertexCount);
    } else if (v2.getDistanceSq(x, y) < vertexTolerance2) {
      return pinwheel(eEdge.getReverse(),  searchDepth, targetMinVertexCount);
    }

    // check for ambiguous case where (x,y) lies on the edge of
    // a triangle. If discovered, reassign the edges if necessary so that
    // (x,y) lies on eEdge.
    boolean ambiguity = false;
    if (checkForAmbiguity(v0, v1, x, y)) {
      ambiguity = true;
    } else if (checkForAmbiguity(v1, v2, x, y)) {
      ambiguity = true;
      eEdge = eEdge.getForward();
      Vertex temp = v0;
      v0 = v1;
      v1 = v2;
      v2 = temp;
    } else if (checkForAmbiguity(v2, v0, x, y)) {
      ambiguity = true;
      eEdge = eEdge.getReverse();
      Vertex temp = v2;
      v2 = v1;
      v1 = v0;
      v0 = temp;
    }

    final SemiVirtualEdge rEdge = eEdge.getReverse();
    final SemiVirtualEdge fEdge = eEdge.getForward();
    final SemiVirtualEdge dEdge = eEdge.getDual();

    Vertex vq = null;
    if (ambiguity) {
      // set vq to the vertex opposite the dual of eEdge.
      // if (x,y) lies on a perimeter edge, then
      // this action may end up seeing vq to null
      // and the logic below will treat this case as a standard search
      vq = dEdge.getForward().getB();
    }

    List<Vertex> vList = new ArrayList<>();
    vList.add(v0);
    vList.add(v1);

    if (vq == null) {
      // treat this as the standard, unamiguous case where (x,y)
      // is within a triangle and does not lie on an edge.
      vList.add(v2);

      final SemiVirtualEdge mEdge = rEdge.getDual();
      final SemiVirtualEdge nEdge = fEdge.getDual();
      standardSearch(vList, dEdge, 1, searchDepth);
      standardSearch(vList, mEdge, 1, searchDepth);
      standardSearch(vList, nEdge, 1, searchDepth);

      maxDepthSearched = searchDepth;

      int nFound = vList.size();
      if (nFound < targetMinVertexCount) {
        int nPrior = 0;
        int nExtras = 0;
        do {
          nExtras++;
          if (nFound == nPrior) {
            // extending the recursion depth did not increase the
            // number of vertices found, no further collection
            // will produce more vertices
            break;
          }
          nPrior = nFound;
          extendedSearch(vList, dEdge, 1, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, mEdge, 1, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, nEdge, 1, searchDepth, searchDepth + nExtras);
          nFound = vList.size();
        } while (nFound < targetMinVertexCount);
      }
    } else {
      // this is the ambiguous case where (x,y) lies on the edge.
      // the searches start with the FOUR neighboring edges.
      // Before beginning, add both v2 and vq to the vList,
      // adding the one closest to (x,y) first so as to guarantee that
      // the first three vertices in vList are the closest to (x,y).
      if (v2.getDistanceSq(x, y) < vq.getDistanceSq(x, y)) {
        vList.add(v2);
        vList.add(vq);
      } else {
        vList.add(vq);
        vList.add(v2);
      }
      final SemiVirtualEdge mEdge = rEdge.getDual();
      final SemiVirtualEdge nEdge = fEdge.getDual();
      final SemiVirtualEdge sEdge = dEdge.getForward().getDual();
      final SemiVirtualEdge tEdge = dEdge.getReverse().getDual();
      standardSearch(vList, mEdge, 1, searchDepth);
      standardSearch(vList, nEdge, 1, searchDepth);
      standardSearch(vList, sEdge, 1, searchDepth);
      standardSearch(vList, tEdge, 1, searchDepth);

      maxDepthSearched = searchDepth;

      int nFound = vList.size();
      if (nFound < targetMinVertexCount) {
        int nPrior = 0;
        int nExtras = 0;
        do {
          nExtras++;
          if (nFound == nPrior) {
            // extending the recursion depth did not increase the
            // number of vertices found, no further collection
            // will produce more vertices
            break;
          }
          nPrior = nFound;
          extendedSearch(vList, mEdge, 1, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, nEdge, 1, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, sEdge, 1, searchDepth, searchDepth + nExtras);
          extendedSearch(vList, tEdge, 1, searchDepth, searchDepth + nExtras);
          nFound = vList.size();
        } while (nFound < targetMinVertexCount);
      }
    }

    return vList;
  }

  private void standardSearch(
    List<Vertex> vList,
    SemiVirtualEdge e,
    int depth,
    int traversalDepth) {
    Vertex b = e.getForward().getB();
    if (b == null) {
      return;
    }
    if (!vList.contains(b)) {
      vList.add(b);
    }
    if (depth < traversalDepth) {
      standardSearch(
        vList, e.getForward().getDual(), depth + 1, traversalDepth);
      standardSearch(
        vList, e.getReverse().getDual(), depth + 1, traversalDepth);
    }
  }

  private void extendedSearch(
    List<Vertex> vList,
    SemiVirtualEdge e,
    int depth,
    int previousDepth,
    int traversalDepth) {
    if (depth > maxDepthSearched) {
      maxDepthSearched = depth;
    }
    Vertex b = e.getForward().getB();
    if (b == null) {
      return;
    }
    if (depth >= previousDepth && !vList.contains(b)) {
      vList.add(b);
    }
    if (depth < traversalDepth) {
      extendedSearch(
        vList,
        e.getForward().getDual(),
        depth + 1,
        previousDepth,
        traversalDepth);
      extendedSearch(
        vList,
        e.getReverse().getDual(),
        depth + 1,
        previousDepth,
        traversalDepth);
    }

  }

  /**
   * Indicates that the most recent target coordinates were
   * exterior to the TIN. Set by the most recent call to getPoints().
   *
   * @return true if the most recent coordinates were exterior to the
   * TIN; otherwise, false.
   */
  @Override
  public boolean wasTargetExteriorToTin() {
    return isQueryPointToExterior;
  }

  /**
   * Gets the max depth searched. Set by the most recent call to
   * getPoints(). In general, this value is meaningful
   * only when the test requires an extended search in order to meet
   * the getPoints method's targetMinvVertexCount.
   *
   * @return a positive integer.
   */
  @Override
  public int getMaxDepthSearched() {
    return maxDepthSearched;
  }

}
