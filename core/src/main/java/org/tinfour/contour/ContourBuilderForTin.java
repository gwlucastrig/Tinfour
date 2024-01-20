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

import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.contour.ContourRegion.ContourRegionType;
import org.tinfour.interpolation.IVertexValuator;
import org.tinfour.utils.VisvalingamLineSimplification;

/**
 * Provides data elements and methods for constructing contours from a Delaunay
 * Triangulation. It is assumed that the data represented by the triangulation
 * can be treated as a continuous surface with no null values. Constrained
 * Delaunay Triangulations are allowed.
 *
 * <p>
 * <strong>Under development. </strong> At this time, the contouring implementation
 * does not support not-a-number or infinity values.  At this time, the contouring
 * implememtation does not support discrete (non-continuous) data.
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
 * Tinfour defines contours as specifying a boundary between two regions on a surface over a
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

  private IIncrementalTin tin;
  /**
   * The perimeter edges for the TIN.
   */
  private List<IQuadEdge> perimeter;
  /**
   * A class for assigning numeric values to contours.
   */
  private IVertexValuator valuator;
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
   * A bitmap for tracking whether edges have been processed during contour
   * construction.
   */
  private BitSet perimeterTermination;

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
  private final ArrayList<ContourRegion> outerRegions = new ArrayList<>();

  private final double[] envelope;

  private int nVertexTransit;
  private int nEdgeTransit;

  boolean regionsAreBuilt;
  private long timeToBuildContours;
  private long timeToBuildRegions;

  /**
   * A map relating edge index to a perimeter link
   */
  private Map<Integer, PerimeterLink> perimeterMap = new HashMap<>();

  /**
   * A list of the perimeter links. Even though the perimeter links form a
   * self-closing linked list, we track them just an array list just to simplify
   * the debugging and diagnostics. This representation is slightly redundant,
   * but the added overhead is less important that creating manageable code.
   */
  private List<PerimeterLink> perimeterList = new ArrayList<PerimeterLink>();

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
    this.zContour = Arrays.copyOf(zContour, zContour.length);

    int n = tin.getMaximumEdgeAllocationIndex();
    visited = new BitSet(n);
    perimeterTermination = new BitSet(n);

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

    // Set flags for all edges that terminate on a perimeter vertex.
    // The pinwheel iterator gives edges leading away from the perimeter
    // vertex A of p.   So we set the bit flag for its dual using an XOR
    for (IQuadEdge p : perimeter) {
      for (IQuadEdge w : p.pinwheel()) {
        perimeterTermination.set(w.getIndex() ^ 1);
      }
    }

    envelope = new double[2 * perimeter.size() + 2];
    k = 0;
    for (IQuadEdge p : perimeter) {
      Vertex A = p.getA();
      envelope[k++] = A.getX();
      envelope[k++] = A.getY();
    }
    envelope[k++] = envelope[0];
    envelope[k++] = envelope[1];

    buildAllContours();
    if (buildRegions) {
      buildRegions();
    }

    // Clean up all contruction elements including internal references.
    this.tin = null;
    this.valuator = null;
    visited = null;
    perimeterTermination = null;
    perimeterMap = null;
    perimeterList = null;
    perimeter = null;

    for (Contour contour : closedContourList) {
      contour.cleanUp();
    }
    for (Contour contour : openContourList) {
      contour.cleanUp();
    }
  }

  /**
   * Simplifies line features using an implementation of Visvalingam's
   * algorithm.   See the Tinfour VisvalingamLineSimplification class for
   * documentation on how this method works.
   * @param areaThreshold the minimum-area threshold for simplification.
   */
  public void simplify(double areaThreshold) {
    VisvalingamLineSimplification vis = new VisvalingamLineSimplification();
    for (Contour contour : closedContourList) {
      int nBefore = contour.n;
      contour.n = 2 * vis.simplify(contour.n / 2, contour.xy, areaThreshold);
      if (contour.n < nBefore) {
        contour.complete();
      }
    }
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

  /**
   * Gets the Cartesian coordinates of the convex hull of the triangulation
   * that was used to construct the contours for this instance.
   * This information in intended mainly for diagnostic and debugging
   * purposes, by may also be used for rendering.
   * <p>
   * Coordinates are stored in an array of doubles in the order
   * { (x0,y0), (x1,y1), (x2,y2), etc. }.
   *
   * @return a valid array of coordinates.
   */
  public double[] getEnvelope() {
    return Arrays.copyOf(envelope, envelope.length);
  }

  /**
   * Build the contours
   */
  private void buildAllContours() {
    long time0 = System.nanoTime();
    for (int i = 0; i < zContour.length; i++) {
      visited.clear();
      buildOpenContours(i);
      buildClosedLoopContours(i);
    }
    long time1 = System.nanoTime();
    timeToBuildContours = time1 - time0;
  }

  /**
   * Build contours that lie entirely inside the TIN and do not intersect
   * the perimeter edges. These contours form closed loops.
   * The left and right index values for the contour will be assigned
   * based on the specified iContour interval index.
   * The left-side index of the contour will be assigned a value of
   * iContour+1, the right-side index will be assigned a value of iContour.
   * Because contours are constructed with the locally high region
   *
   * on their left side, the value of the area enclosed by the contour
   * will determine their orientation. Contours enclosing a region of
   * values greater than or equal zContour[iContour] will be given
   * in counter-clockwise order. Contours enclosing a region of values
   * less than zContour[iContour] will be given in clockwise order.
   *
   * @param iContour the right-side index of the contours to be constructed.
   */
  private void buildClosedLoopContours(int iContour) {

    double z = zContour[iContour];

    for (IQuadEdge p : tin.edges()) {
      IQuadEdge e = p;
      int eIndex = e.getIndex();
      if (visited.get(eIndex)) {
        continue;
      }
      markAsVisited(e);
      Vertex A = e.getA();
      Vertex B = e.getB();
      double zA = valuator.value(A);
      double zB = valuator.value(B);
      double test = (zA - z) * (zB - z);
      if (test < 0) {
        // the edge crosses the contour value.
        if (zA < zB) {
          // e is an ascending edge, but the dual is a descending edge
          e = e.getDual();
          double zSwap = zA;
          zA = zB;
          zB = zSwap;
          A = e.getA();
          B = e.getB();
        }
        // e is an descending edge and a valid start
        Contour contour = new Contour(iContour + 1, iContour, z, true);
        contour.add(e, zA, zB);
        followContour(contour, z, e, null, 0, e, null);

      } else if (test == 0) {
        // at least one of the vertices is level with the contour value.
        if (zA == z && zB == z) {
          IQuadEdge f = e.getForward();
          IQuadEdge g = e.getDual();
          IQuadEdge h = g.getForward();
          markAsVisited(f);
          markAsVisited(g);
          markAsVisited(h);
          Vertex C = f.getB();
          Vertex D = h.getB();
          double zC = valuator.value(C);
          double zD = valuator.value(D);
          if (zC >= z && z > zD) {
            Contour contour = new Contour(iContour + 1, iContour, z, true);
            contour.add(A);
            contour.add(B);
            followContour(contour, z, e, A, 0, f, B);
          } else if (zD >= z && z > zC) {
            Contour contour = new Contour(iContour + 1, iContour, z, true);
            contour.add(B);
            contour.add(A);
            followContour(contour, z, g, B, 0, h, A);
          }
        }
      }
    }
  }

  /**
   * Builds the open-ended contours. These are the contours that terminate in a
   * point lying on the perimeter of the TIN. They do not form closed-loops.
   * These
   * contours should not be confused with perimeter-contours (those that lie
   * directly on perimeter edges).
   *
   * @param iContour the index of the z-contour value being used to generate
   * contours.
   */
  private void buildOpenContours(int iContour) {

    double z = zContour[iContour];

    // As edges are traversed, they are marked as "visited" so that
    // they will not be traversed in the closed-contour processing
    mainLoop:
    for (IQuadEdge p : perimeter) {
      markAsVisited(p);
      IQuadEdge e = p;
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();

      Vertex A = e.getA();
      Vertex B = f.getA();
      Vertex C = r.getA();
      double zA = valuator.value(A);
      double zB = valuator.value(B);
      double zC = valuator.value(C);

      if (zA > z && z > zB) {
        // e is an ascending edge and a valid start
        Contour contour = new Contour(iContour + 1, iContour, z, false);
        contour.add(e, zA, zB);
        followContour(contour, z, e, null, 0, e, null);
      } else if (zA == z) {
        // loop counterclockwise collecting all valid contours (there
        // may be zero, one, or more).
        int startSweepIndex = 0;
        IQuadEdge g = r.getDual();
        IQuadEdge h = g.getForward();
        Vertex G = h.getB();
        markAsVisited(h);
        while (true) {
          startSweepIndex++;
          if (zB < z && z < zC) {
            // exit through an ascending edge
            markAsVisited(f);
            Contour contour = new Contour(iContour + 1, iContour, z, false);
            contour.add(A);
            contour.add(f.getDual(), zC, zB);
            followContour(contour, z, e, A, startSweepIndex, f.getDual(), null);
          }
          if (G == null) {
            break;
          }
          double zG = valuator.value(G);
          if (zB < z && zC == z && zG >= z) {
            // transfer through vertex C with supporting edge h
            Contour contour = new Contour(iContour + 1, iContour, z, false);
            contour.add(A);
            contour.add(C);
            markAsVisited(g);
            markAsVisited(h);
            int dualIndex = h.getIndex() ^ 1;
            if (perimeterTermination.get(dualIndex)) {
              // This is a short traversal. The contour terminates
              // after a single segment.
              finishContour(contour, e, startSweepIndex, h, C);
            } else {
              followContour(contour, z, e, A, startSweepIndex, h, C);
            }
          }
          B = C;
          C = G;
          zB = zC;
          zC = zG;
          f = h;
          r = h.getForward();
          g = r.getDual();
          h = g.getForward();
          G = h.getB();
          markAsVisited(h);
        }
      }
    }
  }

  /**
   * Sets the visited flag for an edge and its dual.
   *
   * @param e a valid edge
   */
  private void markAsVisited(IQuadEdge e) {
    int index = e.getIndex();
    visited.set(index);
    visited.set(index ^ 1);
  }

  /**
   * Follow a contour to its completion. It is expected that when this
   * method is called, the startEdge will be either a descending edge
   * (in the through-edge case) or a support edge for a through-vertex case.
   * If the start passes through a vertex, the startVertex will be non-null.
   * The start sweep index will be zero in the through-edge case or
   * a value greater than zero in the through-vertex case.
   * The terminal edge and vertex follow the same rules as the start.
   * In fact, in some cases, the terminal edge may actually be the starting
   *
   * @param contour a valid isntance
   * @param z the z value for the contour
   * @param startEdge the starting edge
   * @param startVertex the starting vertex, potentially null.
   * @param startSweepIndex a value zero (through-edge case) or larger
   * (through-vertex case).
   * @param terminalEdge the last edge added to the contour, so far
   * @param terminalVertex the last vertex added to the contour, so far,
   * potentially null.
   * @return indicates a successful completion; at this time, always true.
   */
  private boolean followContour(Contour contour,
    double z,
    IQuadEdge startEdge,
    Vertex startVertex,
    int startSweepIndex,
    IQuadEdge terminalEdge,
    Vertex terminalVertex) {
    Vertex V = terminalVertex;
    IQuadEdge e = terminalEdge;
    markAsVisited(e);

    mainLoop:
    while (true) {
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();
      markAsVisited(e);
      Vertex A = e.getA();
      Vertex B = f.getA();
      Vertex C = r.getA();
      double zA = valuator.value(A);
      double zB = valuator.value(B);
      double zC;
      if (C == null) {
        zC = Double.NaN;
      } else {
        zC = valuator.value(C);
      }

      if (V == null) {
        // transition through edge
        // e should be a descending edge with z values
        // bracketing the contour z.
        nEdgeTransit++;
        assert zA > z && z > zB : "Entry not on a bracketed descending edge";
        if (zC < z) {
          // exit via edge C-to-A
          e = r.getDual();
          contour.add(e, zA, zC);
          markAsVisited(e);
        } else if (zC > z) {
          // exit through edge B-to-C
          e = f.getDual();
          contour.add(e, zC, zB);
          markAsVisited(e);
        } else if (zC == z) {
          // transition-vertex side
          e = r;
          V = C;
          contour.add(C);
        } else {
          // this could happen if zC is a null
          // meaning we have a broken contour
          // but because we tested on N==null above, so we should never reach
          // here unless there's an incorrect implementation.
          return false;
        }
      } else {
        // transition through vertex
        // sweep search clockwise starting from support edge
        nVertexTransit++;

        // since we couldn't find a transition within the
        // current triangle, we need to search in a clockwise
        // direction for the transition.
        IQuadEdge e0 = e;
        IQuadEdge g = e;
        while (true) {
          g = g.getForwardFromDual();
          IQuadEdge h = g.getForward();
          IQuadEdge k = h.getForward();
          Vertex K = h.getA();
          Vertex G = h.getB();
          double zK = valuator.value(K);
          double zG = valuator.value(G);
          markAsVisited(g);
          markAsVisited(h);
          markAsVisited(k);
          if (zG > z && z > zK) {
            e = h.getDual();
            V = null;
            contour.add(e, zG, zK);
            break;
          } else if (zG == z && z > zK) {
            contour.add(G);
            e = f;
            V = G;
            break;
          }
          f = h;
        }
        assert !e0.equals(e) : "trans-vertex search loop failed";
      }

      // check for termination conditions
      if (V == null) {
        if (contour.isClosed()) {
          if (startEdge.equals(e) && startVertex == null) {
            // closed loop
            finishContour(contour, startEdge, startSweepIndex, e, V);
            return true;
          }
        } else {
          C = e.getForward().getB();
          if (C == null) {
            finishContour(contour, startEdge, startSweepIndex, e, V);
            return true;
          }
        }
      } else {
        if (contour.isClosed()) {
          if (V == startVertex) {
            finishContour(contour, startEdge, 0, e, V);
            return true;
          }
        }
        int dualIndex = e.getIndex() ^ 1;
        if (perimeterTermination.get(dualIndex)) {
          finishContour(contour, startEdge, startSweepIndex, e, V);
          return true;
        }
      }
    }
  }

  /**
   * Finishes the construction of an individual contour by adding it
   * to the appropriate containers. If the contour is an open contour
   * and intersects a boundary, perimeter link instances are created
   * to support eventual construction of polygon (region) features.
   * <p>
   * The sweep index for the start (and termination) of the contour will be
   * zero if the tip of the contour is based on an edge construction, but
   * will be greater than zero if the tip of the contour passes
   * directly through a vertex.
   * <p>
   * If the contour terminates on an edge, the terminalEdge will be the edge
   * that was identified as its termination (it should be a perimeter edge).
   * If the contour terminates on a vertex, then the terminalEdge will be
   * the "supporting edge" which starts at the terminal vertex and indicates
   * the direction inward to an area of points with a value greater than
   * or equal to the contour value.
   *
   * @param contour the contour
   * @param startEdge the edge that was used to specify the start of
   * the contour.
   * @param startSweepIndex The sweep index for the start of the contour.
   * @param terminalEdge the edge that was used to create the end of the contour
   * @param terminalVertex if the contour terminates on a vertex, a valid
   * instance;
   * otherwise a null.
   */
  private void finishContour(Contour contour,
    IQuadEdge startEdge,
    int startSweepIndex,
    IQuadEdge terminalEdge,
    Vertex terminalVertex) {
    contour.complete();

    if (contour.isClosed()) {
      closedContourList.add(contour);
      return;
    }

    openContourList.add(contour);
    int startIndex = startEdge.getIndex();
    PerimeterLink pStart = perimeterMap.get(startIndex);
    pStart.addContourTip(contour, true, startSweepIndex);

    if (terminalVertex == null) {
      int termIndex = terminalEdge.getIndex() ^ 1;
      PerimeterLink pTerm = perimeterMap.get(termIndex);
      pTerm.addContourTip(contour, false, 0);
    } else {
      // terminalVertex != null, the contour terminates on a vertex.
      // the terminal edge will actually be the supporting edge, and
      // not necessarily the associated perimeter edge that we want.  So we need
      // to sweep around clockwise and find the inside perimeter edge.
      int terminalSweepIndex = 0;
      IQuadEdge s = terminalEdge;
      while (true) {
        terminalSweepIndex++;
        IQuadEdge n = s.getForwardFromDual();
        Vertex B = n.getB();
        if (B == null) {
          break;
        }
        s = n;
      }
      int termIndex = s.getIndex();
      PerimeterLink pTerm = perimeterMap.get(termIndex);
      pTerm.addContourTip(contour, false, terminalSweepIndex);

    }
  }

  private void buildRegions() {

    long time0 = System.nanoTime();

    // development note: the building of perimeter regions and closed
    // regions are independent, so you can do whichever one you want
    // first.  for debugging purposes, feel free to change the order.
    buildRegionsUsingPerimeter();

    for (Contour contour : closedContourList) {
      if(contour.isEmpty()){
        continue;
      }
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

  /**
   * Build regions (closed polygons) that include at elast one contour
   * lying on the perimeter of the TIN. In most, but not all cases,
   * the resulting regions will include at least one open-loop
   * interior contour. The region structure may specify that an
   * interior contour is traversed in a forward or reverse direction.
   * In the overall collection of all perimeter-originating regions,
   * all interior contours will be traversed twice, once in their forward
   * direction and once in the reverse. Perimeter contours are always traversed
   * just once, in a forward direction.
   * <p>
   * The perimeter contours are constructed by this method during
   * the construction of the regions. These contours are always oriented
   * so that the interior of the TIN is to their left. Their left-index
   * is assigned according to the z values that border their immediate
   * left. the right-index is assigned a value of -1, indicating that
   * the data for the region to the right of the perimeter contour is undefined.
   */
  private void buildRegionsUsingPerimeter() {
    // Test for a special case. If none of the interiors intersect
    // the perimeter edges, then construct a single closed-loop region
    // based on the geometry of the perimter.
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
      Contour contour = new Contour(leftIndex, -1, z, true);
      for (IQuadEdge p : perimeter) {
        A = p.getA();
        contour.add(A.getX(), A.getY());
      }
      // The perimeter contour will be incomplete because the loop above
      // only added the first point of the last edge of the permeter.
      // so add a closure point.
      A = perimeter.get(0).getA();
      contour.add(A);
      contour.complete();
      this.perimeterContourList.add(contour);
      ContourRegion region = new ContourRegion(contour);
      regionList.add(region);
      return;
    }

    // The perimeter links form a closed-loop linked-list.
    // However, we keep a redundant set of references in an
    // array list (perimeterLinks) to simplify debugging and diagnostsics.
    //
    // During construction, the through-vertex tips (if any) were
    // put in a temporary storage.  Now loop through, sorting them
    // and prepending them to each set of tip-links
    for (PerimeterLink pLink : perimeterList) {
      pLink.prependThroughVertexTips();
    }

    // The "stitching operation".   Connect various contours into
    // regions.  All of these regions will touch the edge of the
    // triangulation convex hull.  None of them will be contained by
    // another regions.
    //     Loop through each permimeter link looking for tips (contour
    // starts or terminations). In cases where there is a gap between
    // tips on the same edge, short edge contours are created.  After the last
    // tip in each link, a new edge contour is created from the last tip
    // on the edge to the first tip on the next edge.
    for (PerimeterLink pLink : perimeterList) {
      if (pLink.tip0 == null) {
        // there are no contour starts or terminations associated with
        // this perimeter edge.
        continue;
      }

      TipLink tip = pLink.tip0;
      while (tip != null) {
        if (tip.start) {
          if (!tip.contour.traversedForward) {
            int leftIndex = tip.contour.leftIndex;
            double z = tip.contour.z;
            List<ContourRegionMember> mList = traverseFromTipLink(tip, leftIndex, z, true);
            ContourRegion region = new ContourRegion(mList, leftIndex);
            regionList.add(region);
          }
        } else {
          // the tip is an contour termination
          if (!tip.contour.traversedBackward) {
            int rightIndex = tip.contour.rightIndex;
            double z = tip.contour.z;
            List<ContourRegionMember> mList = traverseFromTipLink(tip, rightIndex, z, false);
            ContourRegion region = new ContourRegion(mList, rightIndex);
            regionList.add(region);
          }
        }
        tip = tip.next;
      }
    }
  }

  /**
   * Construct a perimeter contour connecting the initial tip to its
   * nearest neighbor in a counter-clockwise direction. The neighbor
   * may be on the same edge as the initial tip or on a subsequent perimeter
   * edge in a counter-clockwise direction from the initial tip.
   * <p>
   * The initial tip may be either the start or termination of a contour.
   * It represents the point where the contour intersected a perimeter edge.
   * The flag forward0 indicates the direction of traversal for the contour
   * and is used to obtain the Cartesian coordinates for the first
   * point in the first node.
   *
   * @param tipLink0 the initial tip.
   * @param leftIndex the left index to be assigned to the perimeter contour.
   * @param z the z value to be assigned to the perimeter contour. This value
   * is primarily intended for diagnostic purposes.
   * @param forward0 Indicates the direction of traversal for the contour
   * associated with the node.
   * @return a list of member objects giving the contour and direction
   * of traversal for the region.
   */
  private List<ContourRegionMember> traverseFromTipLink(
    TipLink tipLink0, int leftIndex, double z, boolean forward0) {
    // TO DO: The forward0 argument is probably unnecessary
    //        we can get the same information from the tipLink0's
    //        "start" and "termination" flags.
    List<ContourRegionMember> mList = new ArrayList<>();
    TipLink node = tipLink0;
    boolean forward = forward0;
    do {
      Contour contour = node.contour;
      ContourRegionMember member = new ContourRegionMember(contour, forward);
      mList.add(member);
      double x, y;
      Contour boundaryContour = new Contour(leftIndex, -1, z, false);
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
      boundaryContour.complete(); // we're done building the boundary contour

    } while (node != tipLink0);

    return mList;
  }

  private void organizeNestedRegions() {
    int nRegion = regionList.size();
    if (nRegion < 2) {
      return;
    }

    // The nesting concept organizes the regions to identify which
    // regions enclose which.  The "parent" of a region is the region
    // that immediately encloses it. The parent may, in turn, be
    // enclosed by its own parent region. Metaphorically, this concept
    // resembles the way traditional Russian nesting dolls are configured.
    //   To establish the nesting structure, we sort the regions into
    // descending order of area.  Then, we loop through each region
    // comparing it to larger regions to see if the larger region encloses
    // it.  The "parent" reference may be reset multiple times as smaller
    // and smaller enclosing regions are discovered. At the end of the
    // process, the parent reference points to the smallest region that
    // encloses the region of interest.
    Collections.sort(regionList, (ContourRegion o1, ContourRegion o2)
      -> Double.compare(o2.absArea, o1.absArea) // sort largest to smalles
    );

    for (int i = 0; i < nRegion - 1; i++) {
      ContourRegion rI = regionList.get(i);
      double[] xy = rI.getXY();
      for (int j = i + 1; j < nRegion; j++) {
        ContourRegion rJ = regionList.get(j);
        if (rJ.contourRegionType == ContourRegionType.Perimeter) {
          // regions that include perimeter contours are never
          // enclosed by other regions.
          continue;
        }
        Point2D testPoint = rJ.getTestPoint();
        if (rI.isPointInsideRegion(xy, testPoint.getX(), testPoint.getY())) {
          rJ.setParent(rI);
        }
      }
    }

    for (ContourRegion region : regionList) {
      if (region.parent == null) {
        outerRegions.add(region);
      } else {
        region.parent.addChild(region);
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
    ps.format("Outer Regions:      %8d%n", outerRegions.size());
    ps.format("Edge transits:      %8d%n", nEdgeTransit);
    ps.format("Vertex transits:    %8d%n", nVertexTransit);
    ps.format("%n");
  }

}
