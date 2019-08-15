/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 * 07/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.IVertexValuator;

/**
 * Provides data elements and methods for constructing contours from a Delaunay
 * Triangulation. It is assumed that the data represented by the triangulation
 * can be treated as a continuous surface with no null values. Constrained
 * Delaunay Triangulations are allowed.
 *
 * <p>
 * <strong>Under development. </strong> Initial implementation is finished
 * but additional access methods are required and substantial testing 
 * still remains.
 * <p>
 * <strong>Contour left-and-right index:</strong> The left and right index
 * elements for the contours created by this class are set to the array index of
 * the zContour array supplied to the constructor. For example, in the case of a
 * zContour array that specified a single contour value, the right-index of the
 * contours would be assigned the value 0 and the left-index would be assigned
 * the value 1. The maximum possible left-index value is always the
 * zContour.length. The minimum possible right-index value is always zero,
 * except in the special case of perimeter contours. Perimeter contours are
 * those that lie on the outer edges of the TIN and represent the boundary
 * between "data and no-data". The right index value for perimeter contours is
 * -1.
 * <p>
 * Tinfour defines contours as specifying a boundary between two regions in a
 * plane. The region to the left of the contour is treated as including points
 * with vertical coordinates greater than or equal to the contour's vertical
 * coordinate. The values to the right are treated as including points with
 * vertical coordinates less than the contour's vertical coordinate. Thus, in an
 * elevation set, a hill would be represented with a set of closed-loop contours
 * taken in counterclockwise order. A valley would be represented as a set of
 * closed-loop contours taken in clockwise order.
 */
public class ContourBuilderForTin {

  private static class DefaultValuator implements IVertexValuator {

    @Override
    public double value(Vertex v) {
      assert v != null : "Internal method failure, accessing value for null vertex";
      double z = v.getZ();
      if (Double.isNaN(z)) {
        throw new IllegalArgumentException(
                "Input includes vertices with NaN z values");
      }
      return z;
    }

  }

  private final IIncrementalTin tin;
  /**
   * The perimeter edges for the TIN.
   */
  private final List<IQuadEdge> perimeter;
  /**
   * A class for assigning numeric values to contours.
   */
  private final IVertexValuator valuator;
  /**
   * A safe copy of the contour value specifications.
   */
  private final double[] zContour;

  /**
   * A bitmap for tracking whether edges have been processed during contour
   * construction.
   */
  private BitSet visited;
  /**
   * A list of "closed contours" which lie entirely in the interior of the TIN
   * and form closed loops.
   */
  private final ArrayList<Contour> closedContourList = new ArrayList<>();
  /**
   * A list of "open contours" which cross the interior of the TIN and terminate
   * at points lying on its perimeter edges.
   */
  private final ArrayList<Contour> openContourList = new ArrayList<>();

  /**
   * A list of contours lying along the boundary. These contours consist
   * exclusively of points lying on the perimeter of the TIN. These contours are
   * predominantly open, but may be closed in the special case that there are no
   * interior contours that terminate at the perimeters (e.g. those in the
   * openContourList).
   */
  private final ArrayList<Contour> perimeterContourList = new ArrayList<>();

  /**
   * The list of all regions (may be empty).
   */
  private final ArrayList<ContourRegion> regionList = new ArrayList<>();

  /**
   * The list of regions that are not contained by other regions.
   */
  private final ArrayList<ContourRegion> outterRegions = new ArrayList<>();

  private int nContour;
  private int nVertexTransit;
  private int nEdgeTransit;

  boolean regionsAreBuilt;
  private long timeToBuildContours;
  private long timeToBuildRegions;

  /**
   * A map relating edge index to a perimeter link
   */
  private final Map<Integer, PerimeterLink> perimeterMap = new HashMap<>();

  /**
   * A list of the perimeter links. Even though the perimeter links form a
   * self-closing linked list, we track them just an array list just to simplify
   * the debugging and diagnostics. This representation is slightly redundant,
   * but the added overhead is less important that creating manageable code.
   */
  private final List<PerimeterLink> perimeterList = new ArrayList<PerimeterLink>();

  /**
   * Creates a set of contours at the specified vertical coordinates from the
   * Delaunay Triangulation. It is assumed that the data represented by the
   * triangulation can be treated as a continuous surface with no null values.
   * Constrained Delaunay Triangulations are allowed.
   * <p>
   * The constructor for this class always builds a list of contours that is
   * maintained internally. Contours are defined as line features. If you wish,
   * you may set the buildRegions argument to true to connect the contours into
   * closed polygons. 
   * <p>
   * The vertex-valuator argument allows you to specify an alternate method for
   * obtaining the vertical coordinates from the vertices in the triangulation.
   * If you wish to use the values from the vertices directly (i.e. the z
   * coordinates of the vertices), you may do so by supplying a null reference
   * for the valuator argument. The primary intended use of the valuator is to
   * support the use of a smoothing filter, though many other applications are
   * feasible.
   * <p>
   * The values in the zContour array must be unique and monotonically
   * increasing.
   *
   * @param tin a valid TIN.
   * @param vertexValuator an optional valuator or a null reference if the
   * default is to be used.
   * @param zContour a value array of contour values.
   * @param buildRegions indicates whether the builder should produce region
   * (polygon) structures in addition to contours.
   */
  public ContourBuilderForTin(
          IIncrementalTin tin,
          IVertexValuator vertexValuator,
          double[] zContour,
          boolean buildRegions) {
    if (tin == null) {
      throw new IllegalArgumentException("Null reference for input TIN");
    }
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException("Input TIN is not properly populated");
    }
    if (zContour == null) {
      throw new IllegalArgumentException("Null reference for input contour list");
    }

    for (int i = 1; i < zContour.length; i++) {
      if (!(zContour[i - 1] < zContour[i])) {
        throw new IllegalArgumentException(
                "Input contours must be unique and specified in ascending order,"
                + " zContours[ " + i + "] does not meet this requirement");
      }
    }

    this.tin = tin;
    if (vertexValuator == null) {
      valuator = new DefaultValuator();
    } else {
      valuator = vertexValuator;
    }
    this.zContour = zContour;

    int n = tin.getMaximumEdgeAllocationIndex();
    visited = new BitSet(n);

    // Create a closed loop of perimeter links in a counter-clockwise
    // direction.  The edges in the perimeter are the interior side
    // of the TIN perimeter edges.  Their duals will be the exterior sides
    // (and will connect to the ghost vertex).
    perimeter = tin.getPerimeter();
    PerimeterLink prior = null;
    int k = 0;
    for (IQuadEdge p : perimeter) {
      PerimeterLink pLink = new PerimeterLink(k, p);
      perimeterMap.put(p.getIndex(), pLink);
      perimeterList.add(pLink);
      if (prior != null) {
        prior.next = pLink;
        pLink.prior = prior;
      }
      prior = pLink;
      k++;
    }

    assert !perimeterList.isEmpty() && prior != null : "Missing perimeter data";
    PerimeterLink pFirst = perimeterList.get(0);
    pFirst.prior = prior;
    prior.next = pFirst;

    buildContours();
    if (buildRegions) {
      buildRegions();
    }

    // TO DO: clean up all contruction elements including internal references.
    visited = null;
  }

  /**
   * Gets a list of the contours that were constructed by this class.
   *
   * @return a valid, potentially empty list.
   */
  public List<Contour> getContours() {
    int n = closedContourList.size()
            + openContourList.size()
            + perimeterContourList.size();
    List<Contour> cList = new ArrayList<>(n);
    cList.addAll(openContourList);
    cList.addAll(closedContourList);
    cList.addAll(perimeterContourList);
    return cList;
  }

  /**
   * Gets a list of the contour regions (polygon features) that were built by
   * the constructor, if any.
   *
   * @return a valid, potentially empty list of regions.
   */
  public List<ContourRegion> getRegions() {
    List<ContourRegion> aList = new ArrayList<>(regionList.size());
    aList.addAll(regionList);
    return aList;
  }

  
  private boolean storeContour(Contour contour, boolean reachedPerimeter) {
    if (contour.closedLoop) {
      assert !reachedPerimeter : "Reached perimeter while building interior contour";
      contour.trimToSize();
      closedContourList.add(contour);
    } else {
      assert reachedPerimeter : "Failed to reached perimeter while building perimeter-intersection contour";
      contour.trimToSize();
      openContourList.add(contour);

    }
    return true;
  }

  /**
   * Build the contours
   */
  private void buildContours() {
    long time0 = System.nanoTime();
    for (int i = 0; i < zContour.length; i++) {
      visited.clear();
      buildOpenContours(i);
      buildInteriorContours(i);
    }
    long time1 = System.nanoTime();
    timeToBuildContours = time1 - time0;
  }

  private void buildInteriorContours(int iContour) {

    double z = zContour[iContour];

    for (IQuadEdge e : tin.edges()) {
      int eIndex = e.getIndex();
      if (visited.get(eIndex)) {
        continue;
      }
      mark(e);

      Vertex A = e.getA();
      Vertex B = e.getB();
      double zA = valuator.value(A);
      double zB = valuator.value(B);
      if (zA > zB) {
        // e is a descending edge, potentially valid start
        if (zA > z && z > zB) {
          Contour contour = new Contour(nContour++, iContour + 1, iContour, z, true);
          contour.add(e, zA, zB);
          followContour(contour, z);
        }
      } else if (zB > zA) {
        // dual is descending edge, potentially valid start
        if (zB > z && z > zA) {
          Contour contour = new Contour(nContour++, iContour + 1, iContour, z, true);
          contour.add(e.getDual(), zB, zA);
          followContour(contour, z);
        }
      } else { //  zA == zB, if zA==zB==z, we have the level edge case
        if (zA == z) {
          IQuadEdge f = e.getForward();
          IQuadEdge d = e.getDual();
          IQuadEdge g = d.getForward();
          Vertex C = f.getB();
          Vertex D = g.getB();
          double zC = valuator.value(C);
          double zD = valuator.value(D);
          if (zC >= z && z > zD) {
            mark(f);
            Contour contour = new Contour(nContour++, iContour + 1, iContour, z, true);
            contour.add(e, A);
            contour.add(e, B);
            followContour(contour, z);
          } else if (zD >= z && z > zC) {
            mark(g);
            Contour contour = new Contour(nContour++, iContour + 1, iContour, z, true);
            contour.add(d, B);
            contour.add(d, A);
            followContour(contour, z);
          }
        }
      }

    }
  }

  /**
   * Builds the open-ended contours. These are the contours that terminate in a
   * point lying on the perimeter of the TIN. They do form closed-loops. These
   * contours should not be confused with perimeter-contours (those that lie
   * directly on perimeter edges).
   *
   * @param iContour the index of the z-contour value being used to generate
   * contours.
   */
  private void buildOpenContours(int iContour) {

    double z = zContour[iContour];

    mainLoop:
    for (IQuadEdge p : perimeter) {
      IQuadEdge e = p;
      int eIndex = e.getIndex();
      if (visited.get(eIndex)) {
        continue;
      }
      mark(e);

      Vertex A = e.getA();
      Vertex B = e.getB();
      double zA = valuator.value(A);
      double zB = valuator.value(B);

      // for the perimeter case, the dual edge is always part of a ghost
      // triangle, so we do not need to consider it.
      //   We do not consider the case where zA==z and zB==z because
      // that would result in a contour lying directly on a perimeter
      // edge.  The perimeter-edge contours are treated as a special case
      // and constructed separately. 
      if (zA > z && z > zB) {
        // e is a descending edge and a valid start
        Contour contour = new Contour(nContour++, iContour + 1, iContour, z, false);
        contour.add(e, zA, zB);
        followContour(contour, z);
      } else if (zA > z && zB == z) {
        // e is a candidate for a starting edge in the through-vertex case,
        // but we need to inspect the interior to see if there is a 
        // valid transition. Alternately, it could be part of a 
        // ridge, trough, or the interior of a plateau.
        IQuadEdge f = e.getForward();
        mark(f);
        Vertex C = f.getB();
        double zC = valuator.value(C);
        // check for the special case of an immediate edge transition
        if (zC < z) {
          Contour contour = new Contour(nContour++, iContour + 1, iContour, z, false);
          contour.add(e, B);
          IQuadEdge n = e.getDualFromReverse();
          mark(n);
          contour.add(n, zA, zC);
          followContour(contour, z);
          continue mainLoop;
        }

        // loop clockwise looking for a transition.
        // for efficieny, the code in followContour() depends on a verified 
        // "correct" state when its called, so some of the logic
        // here is partially redundant with that of followContour()
        while (true) {
          IQuadEdge g = f.getDual();
          IQuadEdge h = g.getForward();
          IQuadEdge k = h.getForward();
          Vertex G = h.getB();
          mark(g);
          mark(h);
          mark(k);
          if (G == null) {
            // The search pivoted to the next edge on the perimeter without
            // finding a transition. Do not create a contour through vertex B.
            break;
          }
          double zG = valuator.value(G);

          if (zG < z) {
            if (zC == z) {
              // through vertex case
              Contour contour = new Contour(nContour++, iContour + 1, iContour, z, false);
              contour.add(e, B);
              IQuadEdge n = e.getDualFromReverse();
              mark(n);
              contour.add(n, C);
              followContour(contour, z);
              continue mainLoop;
            } else {
              assert zC > z : "Improper perimeter to vertex transition";
              Contour contour = new Contour(nContour++, iContour + 1, iContour, z, false);
              contour.add(e, B);
              IQuadEdge q = g.getDualFromReverse();
              mark(q);
              contour.add(q, zC, zG);
              followContour(contour, z);
              continue mainLoop;
            }
          }
          // advance search to triangle immediately clockwise, pivoting around B
          C = G;
          zC = zG;
          e = g;
          f = h;
        }
      }
    }
  }

  private void mark(IQuadEdge e) {
    int index = e.getIndex();
    visited.set(index);
    visited.set(index ^ 1);
  }

  private boolean followContour(Contour contour, double z) {
    mainLoop:
    while (true) {
      Vertex v = contour.terminalVertex;
      IQuadEdge e = contour.terminalEdge;
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();
      mark(e);
      mark(f);
      mark(r);
      Vertex A = e.getA();
      Vertex B = f.getA();
      Vertex C = r.getA();
      if (C == null) {
        // reached the perimeter
        return storeContour(contour, true);
      }

      double zA = valuator.value(A);
      double zB = valuator.value(B);
      double zC = valuator.value(C);

      if (v == null) {
        // transition-edge case
        // e should be a descending edge with z values
        // bracketing the contour z.
        nEdgeTransit++;
        assert zA > z && z > zB : "Entry not on a bracketed descending edge";
        if (zC < z) {
          // exit via edge C-to-A
          e = r.getDual();
          if (e.equals(contour.startEdge)) {
            return storeContour(contour, false);
          }
          contour.add(e, zA, zC);
        } else if (zC > z) {
          // exit through edge B-to-C
          e = f.getDual();
          if (e.equals(contour.startEdge)) {
            return storeContour(contour, false);
          }
          contour.add(e, zC, zB);
        } else if (zC == z) {
          // transition-vertex side
          e = r.getDual();
          contour.add(e, C);
          if (C.equals(contour.startVertex)) {
            return storeContour(contour, false);
          }
        } else {
          // this could happen if zC is a null
          // meaning we have a broken contour
          // but because we tested on N==null above, so we should never reach
          // here unless there's an incorrect implementation.
          return false;
        }
      } else {
        // transition-vertex case
        nVertexTransit++;
        assert zA >= z && z >= zB : "transition vertex-to-edge failure";
        if (zA > z && z > zC) {
          e = r.getDual();
          if (e.equals(contour.startEdge)) {
            return storeContour(contour, false);
          }
          contour.add(e, zA, zC);
          continue mainLoop;
        }

        // since we couldn't find a transition within the
        // current triangle, we need to search in a clockwise
        // direction for the transition.
        IQuadEdge e0 = e;
        IQuadEdge g = f.getDual();
        IQuadEdge h = g.getForward();
        IQuadEdge k = g.getReverse();
        Vertex G = h.getB();

        while (true) {
          mark(g);
          mark(h);
          mark(k);
          if (G == null) {
            return storeContour(contour, true);
          }
          double zG = valuator.value(G);

          if (zG < z) {
            // we've reached a transition
            if (zC == z) {
              // the transition is along the forward edge
              // to vertex c
              if (C.equals(contour.startVertex)) {
                return storeContour(contour, false);
              }
              IQuadEdge q = e.getDualFromReverse();
              contour.add(q, C);
              e = f;
              break; // inner loop
            } else {
              // zC must be > z so the transition
              // is through edge C-to-G 
              assert zC > z : "zC must be > z, value=" + z;
              e = k.getDual();
              if (e.equals(contour.startEdge)) {
                return storeContour(contour, false);
              }
              contour.add(e, zC, zG);
              break; // inner loop
            }
          }
          // zG >= z, keep searching and advance search
          // to triangle immediately clockwise, pivoting around B
          e = g;
          f = h;
          r = k;
          C = G;
          zC = zG;
          g = f.getDual();
          h = g.getForward();
          k = g.getReverse();
          G = h.getB();
          assert !e0.equals(e) : "trans-vertex search loop failed";
        }
      }
    }
  }

  private void buildRegions() {

    long time0 = System.nanoTime();

    // development note: the building of perimeter regions and closed
    // regions are independent, so you can do whichever one you want
    // first.  for debugging purposes, feel free to change the order.
    buildPerimeterRegions();

    for (Contour contour : closedContourList) {
      ContourRegion region = new ContourRegion(contour);
      regionList.add(region);
    }

    // Once both interior and exterior regions are built, organize
    // the nesting relationships.  Some larger regions may enclose
    // smaller regions, etc.   At this point, whether or not a region
    // is constructed from open or closed contours is immaterial.
    organizeNestedRegions();
    long time1 = System.nanoTime();
    timeToBuildRegions = time1 - time0;
  }

  private void buildPerimeterRegions() {
    if (openContourList.isEmpty()) {
      Vertex A = perimeter.get(0).getA();
      double z = valuator.value(A);
      int leftIndex = zContour.length;
      for (int i = 0; i < zContour.length; i++) {
        if (zContour[i] > z) {
          leftIndex = i;
          break;
        }
      }
      Contour contour = new Contour(nContour++, leftIndex, -1, z, true);
      for (IQuadEdge p : perimeter) {
        A = p.getA();
        contour.add(A.getX(), A.getY());
      }
      contour.trimToSize();
      this.perimeterContourList.add(contour);
      ContourRegion region = new ContourRegion(contour);
      regionList.add(region);
      return;
    }

    for (Contour contour : openContourList) {
      int startIndex = contour.startEdge.getIndex();
      PerimeterLink pStart = perimeterMap.get(startIndex);
      pStart.addContourTip(contour, true);

      // Because the follow-contour logic always stores the
      // "next transition" in the terminalEdge element, 
      // the terminal edge of the contour will be the outside
      // of the perimeter edge.  Since region builder logic is interested
      // in the inner edges, we need to get the dual.
      IQuadEdge termDual = contour.terminalEdge.getDual();
      int termIndex = termDual.getIndex();
      PerimeterLink pTerm = perimeterMap.get(termIndex);
      pTerm.addContourTip(contour, false);
    }

    // The perimeter links form a closed-loop linked-list.
    // However, we keep a redundant set of references in an
    // array list (perimeterLinks) to simplify debugging and diagnostsics.
    for (PerimeterLink pLink : perimeterList) {
      if (pLink.tip0 == null) {
        // there are no contour starts or terminations associated with
        // this perimeter edge.
        continue;
      }

      TipLink node = pLink.tip0;
      while (node != null) {
        if (node.start) {
          if (!node.contour.traversedForward) {
            int leftIndex = node.contour.leftIndex;
            double z = node.contour.z;
            List<ContourRegionMember> mList = traverseFromNode(node, leftIndex, z, true);
            ContourRegion region = new ContourRegion(mList, leftIndex);
            regionList.add(region);
          }
        } else {
          // the tip is an contour termination
          if (!node.contour.traversedBackward) {
            int rightIndex = node.contour.rightIndex;
            double z = node.contour.z;
            List<ContourRegionMember> mList = traverseFromNode(node, rightIndex, z, false);
            ContourRegion region = new ContourRegion(mList, rightIndex);
            regionList.add(region);
          }
        }
        node = node.next;
      }
    }
  }

  private List<ContourRegionMember> traverseFromNode(TipLink node0, int leftIndex, double z, boolean forward0) {
    List<ContourRegionMember> mList = new ArrayList<>();
    TipLink node = node0;
    boolean forward = forward0;
    do {
      Contour contour = node.contour;
      ContourRegionMember member = new ContourRegionMember(contour, forward);
      mList.add(member);
      double x, y;
      Contour boundaryContour = new Contour(nContour++, leftIndex, -1, z, false);
      perimeterContourList.add(boundaryContour);
      member = new ContourRegionMember(boundaryContour, true);
      mList.add(member);

      if (forward) {
        contour.traversedForward = true;
        node = contour.terminalTip;
        x = contour.xy[contour.xy.length - 2];
        y = contour.xy[contour.xy.length - 1];
      } else {
        contour.traversedBackward = true;
        node = contour.startTip;
        x = contour.xy[0];
        y = contour.xy[1];
      }
      boundaryContour.add(x, y);
      if (node.next != null) {
        node = node.next;
      } else {
        PerimeterLink pLink = node.pLink.next;
        IQuadEdge pEdge = pLink.edge;
        Vertex A = pEdge.getA();
        boundaryContour.add(A.getX(), A.getY());
        while (pLink.tip0 == null) {
          pLink = pLink.next;
          pEdge = pLink.edge;
          A = pEdge.getA();
          boundaryContour.add(A.getX(), A.getY());
        }
        node = pLink.tip0;
      }

      contour = node.contour;
      if (node.start) {
        forward = true;  // for next contour traversal
        x = contour.xy[0];
        y = contour.xy[1];
      } else {
        forward = false; // for next contour traversal
        x = contour.xy[contour.xy.length - 2];
        y = contour.xy[contour.xy.length - 1];
      }
      boundaryContour.add(x, y);
      boundaryContour.trimToSize(); // we're done building the boundary contour

    } while (node != node0);

    return mList;
  }

  private void organizeNestedRegions() {
    // TO DO: does not yet support multi-contour (open-contour) regions.
    int nRegion = regionList.size();
    if (nRegion < 2) {
      return;
    }

    Collections.sort(regionList, (ContourRegion o1, ContourRegion o2)
            -> Double.compare(o1.absArea, o2.absArea) // sort smallest to largest
    );

    for (int i = 0; i < nRegion - 1; i++) {
      ContourRegion rI = regionList.get(i);
      double[] xy = rI.getXY();
      double x = (xy[0] + xy[2]) / 2.0;
      double y = (xy[1] + xy[3]) / 2.0;
      for (int j = i + 1; j < nRegion; j++) {
        ContourRegion rJ = regionList.get(j);
        if (rJ.isPointInsideRegion(x, y)) {
          rJ.addChild(rI);
          break;
        }
      }
    }

    for (ContourRegion region : regionList) {
      if (region.parent == null) {
        outterRegions.add(region);
      }
    }

  }

  private int countPoints(List<Contour> cList) {
    int n = 0;
    for (Contour c : cList) {
      n += c.size();
    }
    return n;
  }

  /**
   * Provides a summary of statistics and measurements for the contour building
   * process and resulting data.
   *
   * @param ps a valid PrintStream instance, such as System&#46;out.
   * @param areaFactor a unit-conversion factor for scaling area values
   */
  public void summarize(PrintStream ps, double areaFactor) {
    ps.format("Summary of statistics for contour building%n");
    ps.format("Time to build contours %7.1f ms%n", timeToBuildContours / 1.0e+6);
    ps.format("Time to build regions  %7.1f ms%n", timeToBuildRegions / 1.0e+6);
    ps.format("Open contours:      %8d,  %8d points%n",
            openContourList.size(), countPoints(openContourList));
    ps.format("Closed contours:    %8d,  %8d points%n",
            closedContourList.size(), countPoints(closedContourList));
    ps.format("Regions:            %8d%n", regionList.size());
    ps.format("Outter Regions:     %8d%n", outterRegions.size());
    ps.format("Edge transits:      %8d%n", nEdgeTransit);
    ps.format("Vertex transits:    %8d%n", nVertexTransit);
    ps.format("%n");
//    ps.format("%nArea computations by contour level%n");
//    for (int iRegion = 0; iRegion < zContour.length + 1; iRegion++) {
//      String zString;
//      double z;
//      if (iRegion == zContour.length) {
//        z = zContour[zContour.length - 1];
//        zString = String.format("> %9.3f", z);
//      } else {
//        z = zContour[iRegion];
//        zString = String.format("  %9.3f", z);
//      }
//      double a = 0;
//      for (ContourRegion region : regionList) {
//        if (region.regionIndex == iRegion) {
//          a += region.area;
//        }
//      }
//      ps.format("%6d   %s  %f%n", iRegion, zString, a / areaFactor);
//    }
//
//    ps.format("%nPolygon nesting%n");
//    for (ContourRegion region : outterRegions) {
//      recursiveSummarize(ps, areaFactor, region, 0);
//    }
  }

//  private void recursiveSummarize(
//          PrintStream ps,
//          double areaFactor,
//          ContourRegion region, int iLevel) {
//    String pad = "";
//    if (iLevel > 0) {
//      StringBuilder sb = new StringBuilder();
//      for (int i = 0; i < iLevel * 3; i++) {
//        sb.append(' ');
//      }
//      pad = sb.toString();
//    }
//    System.out.format("%s%4d %12.4f%n", pad, region.regionIndex, region.area);
//    for (ContourRegion r : region.children) {
//      recursiveSummarize(ps, areaFactor, r, iLevel + 1);
//    }
//  }
}
