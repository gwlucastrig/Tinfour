/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2025  Gary W. Lucas.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 04/2021  G. Lucas     Created
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.standard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;

/**
 * Exercises the ability to add vertices after constraints are in place.
 */
public class AddVertexPostConstraintTest {

  public AddVertexPostConstraintTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @AfterAll
  public static void tearDownClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @AfterEach
  public void tearDown() {
  }

  private IIncrementalTin initTIN() {
    IIncrementalTin tin = new IncrementalTin(1.0);

    List<IConstraint> conList = new ArrayList<>();
    List<Vertex> polygonVertices = new ArrayList<>();
    polygonVertices.add(new Vertex(0.25, 0.25, 0, -1));
    polygonVertices.add(new Vertex(0.75, 0.25, 1, -2));
    polygonVertices.add(new Vertex(0.75, 0.75, 2, -3));
    polygonVertices.add(new Vertex(0.25, 0.75, 1, -4));

    PolygonConstraint polyCon = new PolygonConstraint(polygonVertices);
    polyCon.setApplicationData(Color.red);
    conList.add(polyCon);

    List<Vertex> lineVertices = new ArrayList<>();
    lineVertices.add(new Vertex(0.25, 0.25, 0, -10));
    lineVertices.add(new Vertex(0.75, 0.75, 2, -11));
    LinearConstraint linCon = new LinearConstraint(lineVertices);
    linCon.setApplicationData(Color.green);
    conList.add(linCon);

    tin.addConstraints(conList, true);

    return tin;
  }


  /**
   * Test insertion of vertices after constraints are added
   * to the TIN. Two constraints are constructed.  A rectangular
   * constraint with width and height of 0.5 and a corner at (0.25, 0.25).
   * A diagonal is constructed from (0.25, 0.25) to 0.75, 0.75.
   * Vertices are inserted at grid positions so that
   * some fall immediately on a constraint, some fall between constraints,
   * and some are placed outside the the bounds of the constrained region.
   */
  @Test
  public void testInsertion() {
    IIncrementalTin tin;

    int nIntervals = 8;
    int iCount = 0;
    List<Vertex> vertices = new ArrayList<>();
    for (int iRow = 0; iRow <= nIntervals; iRow++) {
      for (int iCol = 0; iCol <= nIntervals; iCol++) {
        double x = iCol / (double) nIntervals;
        double y = iRow / (double) nIntervals;
        Vertex v = new Vertex(x, y, 0, iCount++);
        vertices.add(v);

        tin = initTIN(); // init a TIN with constraints in place
        tin.add(v);
        assertTrue(tin.isBootstrapped(), "Bootstrap failed for case " + iCount + ", coordinates (" + x + ", " + y + ")");

        IIntegrityCheck checker = tin.getIntegrityCheck();
        boolean status = checker.inspect();
        assertTrue(status, "Integrity check failed for case " + iCount + ", coordinates (" + x + ", " + y + ")");
      }
    }

    tin = initTIN();
    tin.add(vertices, null);
    assertTrue(tin.isBootstrapped(), "Bootstrap failed for aggeregate case");

    IIntegrityCheck checker = tin.getIntegrityCheck();
    boolean status = checker.inspect();
    assertTrue(status, "Integrity check failed for aggeregate case");
  }

}
