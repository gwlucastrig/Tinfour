/*
 * Copyright 2014 Gary W. Lucas.
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
 * ------  --------- -------------------------------------------------
 * 10/2015 G. Lucas  Adapted from original IntegrityCheck implementation
 *                     to use virtual edges.
 * 11/2016 G. Lucas  Added support for constrained Delaunay
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.semivirtual;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.common.VertexMergerGroup;

/**
 * A tool for checking the correctness of a tin, in particular the relationship
 * between adjacent triangles. The name of this class is inspired by the idea of
 * a "relational integrity check" in relational database management systems. And
 * that choice of nomenclature should give some sense of its intended role.
 */
class SemiVirtualIntegrityCheck implements IIntegrityCheck {

  private final SemiVirtualIncrementalTin tin;
  private final Thresholds thresholds;
  private final GeometricOperations geoOp;
  private final List<SemiVirtualEdge> edges;

  private String message;
  private int nDelaunayViolations;
  private double sumDelaunayViolations;
  private double maxDelaunayViolation;
  private int nDelaunayViolationsConstrained;
  private double sumDelaunayViolationsConstrained;
  private double maxDelaunayViolationConstrained;
  /**
   * Constructs an instance to be associated with a specified TIN.
   *
   * @param tin A valid instance of a class that implements the
   * IIncrementalTin interface.
   */
  SemiVirtualIntegrityCheck(SemiVirtualIncrementalTin tin) {
    this.tin = tin;
    thresholds = tin.getThresholds();
    geoOp = new GeometricOperations(thresholds);
    edges = tin.getVirtualEdges();
    message = null;
  }

  /**
   * Performs an inspection of the TIN checking for conditions that
   * violate the construction rules.
   * <h3>The Rules</h3>
   * <ul>
   * <li>Ensure that every edge links to two valid triangular circuits
   * (one on each side).
   * <li>Ensure that the set of ghost triangles forms a closed loop around the
   * convex hull (perimeter) of the TIN</li>
   * <li>Ensure that all ghost triangles are included in the perimeter
   * loop</li>
   * <li>Ensure that no triangles are degenerate (negative or zero area)</li>
   * <li>Ensure that all triangle pairs are Delaunay or
   * close-to-Delaunay optimal</li>
   * </ul>
   */
  @Override
  public boolean inspect() {

    if (edges.isEmpty()) {
      message = "TIN was not successfully bootstrapped";
      return false;
    }

    boolean test;
    test = inspectLinks();
    if (!test) {
      return false;
    }

    test = inspectPerimeterLinks();
    if (!test) {
      return false;
    }

    return inspectTriangleGeometry();  // our last test for now
  }

  /**
   * Inspects the references that connect various edges in the
   * TIN. This test passes if the following conditions are met:
   * <ul>
   * <li>Proper triangular circuits are formed</li>
   * <li>Forward and reverse references are mutually consistent</li>
   * <li>All allocated edges are fully populated with references</li>
   * </ul>
   * If an inspection fails, information about the cause may be
   * obtained through a call to getMessage().
   *
   * @return true if all inspection criteria are met; otherwise false.
   */
  public boolean inspectLinks() {

    final SemiVirtualEdge s = edges.get(0).getUnassignedEdge();
    final SemiVirtualEdge dual = s.getUnassignedEdge();
    for (SemiVirtualEdge e : edges) {
      s.loadForwardFromEdge(e);
      if (e.equals(s)) {
        message = "Edge has forward reference to itself " + e;
        return false;
      }
      s.loadForwardFromEdge(s);
      s.loadForwardFromEdge(s);

      if (!e.equals(s)) {
        message
          = "Incomplete forward circuit starting with edge " + e;
        return false;
      }

      s.loadReverseFromEdge(e);
      if (e.equals(s)) {
        message = "Edge has reverse reference to itself " + e;
        return false;
      }
      s.loadReverseFromEdge(s);
      s.loadReverseFromEdge(s);
      if (!e.equals(s)) {
        message
          = "Incomplete reverse circuit starting with edge " + e;
        return false;
      }

      dual.loadDualFromEdge(e);
      s.loadForwardFromEdge(dual);
      if (dual.equals(s)) {
        message = "Edge has forward reference to itself " + dual;
        return false;
      }

      s.loadForwardFromEdge(s);
      s.loadForwardFromEdge(s);
      if (!dual.equals(s)) {
        message
          = "Incomplete forward circuit starting with edge " + dual;
        return false;
      }

      s.loadReverseFromEdge(dual);
      if (dual.equals(s)) {
        message = "Edge has reverse reference to itself " + dual;
        return false;
      }
      s.loadReverseFromEdge(s);
      s.loadReverseFromEdge(s);
      if (!dual.equals(s)) {
        message
          = "Incomplete reverse circuit starting with edge " + dual;
        return false;
      }

    }

    return true;
  }

  /**
   * Inspects the edges related to the perimeter (convex hull) of the
   * TIN. This test passes if the following conditions are met:
   * <ul>
   * <li>The dual of all perimeter edges is the base of a
   * ghost triangle.</li>
   * <li>Forward and reverse references are mutually consistent</li>
   * <li>The number of perimeter edges equals the number of ghost edges</li>
   * <li>The area of the perimeter as obtained using the getPermiter()
   * method is greater than zero (e.g. has a counterclockwise orientation).
   * </ul>
   * If an inspection fails, information about the cause may be
   * obtained through a call to getMessage().
   *
   * @return true if all inspection criteria are met; otherwise false.
   */
  public boolean inspectPerimeterLinks() {

    // by convention, ghost edges are always stored in the edge pool
    // so that the base edge ends at the null vertex.
    if (edges.isEmpty()) {
      message = "TIN was not successfully bootstrapped";
      return false;
    }
    int nGhostEdges = 0;
    SemiVirtualEdge p = null;
    for (SemiVirtualEdge e : edges) {
      if (e.getB() == null) {
        p = e;
        nGhostEdges++;
      } else if (e.getA() == null) {
        message = "Edge starts with null vertex " + e;
        return false;
      }
    }

    SemiVirtualEdge s0 = p.getReverse();
    SemiVirtualEdge s = s0;
    int n = 0;
    do {
      n++;
      if (n > edges.size()) {
        // infinite loop
        message = "Infinite loop building perimeter ";
        return false;
      }
      s = s.getForward();
      s = s.getForward();
      s = s.getDual();
      s = s.getReverse();
    } while (!s.equals(s0));

    List<IQuadEdge> pList = tin.getPerimeter();
    if (n != nGhostEdges) {
      message = "Perimeter edge count," + n + ", does not match number of ghost edges, " + nGhostEdges;

      return false;
    }

    double aSum = 0;
    for (IQuadEdge e : pList) {
      double x0 = e.getA().getX();
      double y0 = e.getA().getY();
      double x1 = e.getB().getX();
      double y1 = e.getB().getY();
      aSum += x0 * y1 - x1 * y0;
    }

    aSum /= 2.0;
    if (aSum <= 0) {
      message = "Negative perimeter area " + aSum;
      return false;
    }
    return true;
  }

  /**
   * Inspects the triangles forming the TIN.
   * This test passes if the following conditions are met:
   * <ul>
   * <li>All triangles are non-degenerate (have an area greater than zero).</li>
   * <li>The two triangles associated with each edge do not violate
   * the Delaunay criterion within a degree of tolerance specified
   * by the delaunayThreshold for the TIN.</li>
   * </ul>
   * This inspection has a limitation in that the method of checking the
   * Delaunay criterion uses the same finite-precision logic that the
   * TIN building routine uses. So, at best, a passing result verifies
   * that the logic is self-consistent, but does not guarantee that the
   * triangular mesh is truly Delaunay optimal.
   * <p>
   * If an inspection fails, information about the cause may be
   * obtained through a call to getMessage().
   *
   * @return true if all inspection criteria are met; otherwise false.
   */
  public boolean inspectTriangleGeometry() {
    // the following loop will inspect the area of each triangle
    // three times.  Since we aren't interested in performance,
    // we simply allow it to do so rather than complicating the
    // code (and potentially coding the thing incorrectly).
    final SemiVirtualEdge d = edges.get(0).copy();
    for (SemiVirtualEdge e : edges) {
      if (e.getA() != null && e.getB() != null) {
        if (e.getForward().getB() != null && !checkAreaAndInCircle(e)) {
          return false;
        }
        d.loadDualFromEdge(e);
        if (d.getTriangleApex() != null && !checkAreaAndInCircle(d)) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean checkAreaAndInCircle(final SemiVirtualEdge e) {
    final Vertex a = e.getA();
    final Vertex b = e.getB();
    final Vertex c = e.getTriangleApex(); //e.getForward().getB();

    double area = geoOp.area(a, b, c);
    if (area < 0) {
      message
        = "Triangle with negative area " + area +  ", vertices: " + a.getIndex()
        + ", " + b.getIndex()
        + ", " + c.getIndex()
        + " starting at edge " + e;
      return false;
    }

    if (area == 0) {
      message
        = "Triangle with zero area  " + area + " starting at edge " + e
        + ", vertices: " + a.getLabel()
        + ", " + b.getLabel()
        + ", " + c.getLabel();
      geoOp.area(a, b, c); // just for debugging
      return false;
    }

    final SemiVirtualEdge dual = e.getDual();

    final Vertex d = dual.getTriangleApex();
    if (d == null) {
      return true; // no further testing is possible or required
    }

    double h = geoOp.inCircle(a, b, c, d);

    if (h > 0) {
      if (e.isConstrained()) {
        // because the edge is constrained, the rules are different.
        // In particular, conformity is not necessarily restord.
        // So we record statistics, but do not treat this condition as a failure.
        if (h > thresholds.getDelaunayThreshold()) {
          this.nDelaunayViolationsConstrained++;
          this.sumDelaunayViolationsConstrained += h;
          if (h > this.maxDelaunayViolationConstrained) {
            this.maxDelaunayViolationConstrained = h;
          }
        }
      } else {
        this.nDelaunayViolations++;
        this.sumDelaunayViolations += h;
        if (h > this.maxDelaunayViolation) {
          this.maxDelaunayViolation = h;
        }

        if (h > thresholds.getDelaunayThreshold()) {
          message = "InCircle failure h=" + h
            + ", starting at edge " + e
            + ": ("
            + a.getIndex()
            + ", " + b.getIndex()
            + ", " + c.getIndex()
            + ", " + d.getIndex() + ")";
          return false;

        }
      }
    }
    return true;
  }

  /**
   * Gets descriptive information about the cause of a test failure.
   *
   * @return if a failure occurred, descriptive information; otherwise
   * a string indicating that No Error Detected.
   */
  @Override
  public String getMessage() {
    if (message == null) {
      return "No Error Detected";
    }
    return message;
  }

  /**
   * Determines whether the most recent integrity check completed okay.
   *
   * @return true if the integrity check was okay.
   */
  public boolean isCheckOkay() {
    return message == null;
  }

  @Override
  public void printSummary(PrintStream ps) {
    Formatter fmt = new Formatter(ps);
    fmt.format("Integrity Check Results:%n   %s%n", getMessage());
    if (nDelaunayViolations == 0) {
      fmt.format("   No Delaunay violations detected%n");
    } else {
      fmt.format("   Detected acceptable Delaunay violations within tolerance: %8.4e%n", thresholds.getDelaunayThreshold());
      fmt.format("      N Violations:  %8d%n", nDelaunayViolations);
      fmt.format("      Avg Violation: %8.4e%n", sumDelaunayViolations / nDelaunayViolations);
      fmt.format("      Max Violation: %8.4e%n", maxDelaunayViolation);
    }
    if (nDelaunayViolationsConstrained > 0) {
      fmt.format("   Counted %d violations at constrained edges%n",
        nDelaunayViolationsConstrained);
      fmt.format("      Avg Violation: %8.4e%n",
        sumDelaunayViolationsConstrained / nDelaunayViolationsConstrained);
      fmt.format("      Max Violation: %8.4e%n", maxDelaunayViolationConstrained);
    }

    fmt.flush();
  }


  @Override
  public boolean testGetVerticesAgainstInputList(List<Vertex> inputList) {
    if (!tin.getConstraints().isEmpty()) {
      message = "Cannot compare input list after constraints are added";
      return false;
    }
    ArrayList<Vertex> inList = new ArrayList<>(inputList.size());
    inList.addAll(inputList);

    List<Vertex> outputList = tin.getVertices();
    ArrayList<Vertex> outList = new ArrayList<>(inputList.size());
    for (Vertex v : outputList) {
      if (v instanceof VertexMergerGroup) {
        VertexMergerGroup group = (VertexMergerGroup) v;
        Vertex[] s = group.getVertices();
        for (Vertex sv : s) {
          outList.add(sv);
        }
      } else {
        outList.add(v);
      }
    }

    Comparator<Vertex> vComp = new Comparator<Vertex>() {
      @Override
      public int compare(Vertex t, Vertex t1) {
        return Integer.compare(t.getIndex(), t1.getIndex());
      }

    };

    inList.sort(vComp);
    outList.sort(vComp);
    int n = inList.size();
    if (outList.size() < n) {
      n = outList.size();
    }

    for (int i = 0; i < n; i++) {
      Vertex vIn = inList.get(i);
      Vertex vOut = outList.get(i);
      if (vIn.getIndex() != vOut.getIndex()) {
        message = "Vertex mismatch at index "
          + vIn.getIndex() + ", " + vOut.getIndex();
        return false;
      }
    }

    if (inList.size() != outList.size()) {
      message = "Vertex list sizes not equal "
        + inList.size() + ", " + outList.size();
      return false;
    }

    return true;

  }

  @Override
  public int getConstrainedViolationCount() {
    return nDelaunayViolationsConstrained;
  }

  @Override
  public double getConstrainedViolationMaximum() {
    return this.maxDelaunayViolationConstrained;
  }

  @Override
  public double getContrainedViolationAverage() {
    if (nDelaunayViolationsConstrained == 0) {
      return 0;
    }
    return sumDelaunayViolationsConstrained / nDelaunayViolationsConstrained;
  }
}
