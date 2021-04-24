/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2021  Gary W. Lucas.

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
package org.tinfour.contour;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.contour.ContourIntegrityCheck;
import org.tinfour.standard.IncrementalTin;

/**
 * Performs round-trip tests for coding and decoding integers using the
 * M32 codec.
 */
public class ContourBuilderForTinTest {

  public ContourBuilderForTinTest() {
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

  private void buildAndCheck(String name, List<Vertex> vList, double[] zContour) {
    IIncrementalTin tin = new IncrementalTin(1.0);
    tin.add(vList, null);
    ContourBuilderForTin builder = new ContourBuilderForTin(tin, null, zContour, true);
    ContourIntegrityCheck cic = new ContourIntegrityCheck(builder);
    boolean status = cic.inspect();
    assertTrue(status, name + ": " + cic.getMessage());
  }

  /**
   * Test through-vertex case with a "fanout" geometry.
   * The fanout geometry is basically a diamond shape. The
   * two vList of the diamond match the contour value 20,
   * the nVertex middle-band vList alternate between 0 and 40.
   */
  @Test
  public void testFanout() {
    double[] zContour = new double[]{20};
    for (int nV = 3; nV < 9; nV++) {
      for (int startIndex = 0; startIndex < 2; startIndex++) {
        String name = "Fanout test (" + nV + ", " + startIndex + ") ";
        List<Vertex> vList = makeFanout(nV, startIndex);
        buildAndCheck(name, vList, zContour);
      }
    }
  }

  private List<Vertex> makeFanout(int nV, int startIndex) {
    List<Vertex> vList = new ArrayList<>();
    for (int i = 0; i < nV; i++) {
      double z = 0;
      if (((startIndex + i) & 1) == 0) {
        z = 40;
      }
      vList.add(new Vertex(i, 0, z, i));
    }
    vList.add(new Vertex((nV - 1) / 2.0, 5, 20, nV + 1));
    vList.add(new Vertex((nV - 1) / 2.0, -5, 20, nV + 2));
    return vList;
  }

  /**
   * The a set of alternating vertices in a regular polygon shape with
   * values bracketing the contour value. A vertex with the contour value
   * is positioned at the center of the polygon.
   */
  @Test
  public void testWheel() {
    double[] zContour = new double[]{20};
    for (int nV = 7; nV < 20; nV++) {
      for (int nStep = 1; nStep < 3; nStep++) {
        String name = "Test (" + nV + ", " + nStep + ") ";
        List<Vertex> vList = makeWheel(nV, nStep);
        buildAndCheck(name, vList, zContour);
      }
    }
  }

  private List<Vertex> makeWheel(int nVertices, int nStep) {
    List<Vertex> vList = new ArrayList<>();
    double[] zValue = new double[]{0, 40};
    int kStep = 0;
    int iValue = 0;
    double theta = 2 * Math.PI / nVertices;
    for (int i = 0; i < nVertices; i++) {
      if (kStep == nStep) {
        kStep = 0;
        iValue ^= 1;
      } else {
        kStep++;
      }
      double x = Math.sin(i * theta);
      double y = Math.cos(i * theta);
      int index = (i == 0 ? 12 : i);
      Vertex v = new Vertex(x, y, zValue[iValue], index);
      vList.add(v);
    }
    vList.add(new Vertex(0, 0, 20, 0));
    return vList;
  }

  /**
   * Test for potential special cases that may arise for a minimal
   * set of vertices. In such cases, the contour-following logic
   * terminates early.
   */
  @Test
  public void testThreeVertexCases() {
    double[] zContour = new double[]{20};
    List<Vertex> vList = new ArrayList<>();
    vList.add(new Vertex(0, 0, 0, 0));
    vList.add(new Vertex(1, 0, 20, 1));
    vList.add(new Vertex(0.5, Math.cos(Math.toRadians(60)), 40));

    // Contours are built by looping on the perimeter edges in counter-clockwise order.
    // We can control which edge is processed first by using the order in which we add vertices
    // to the TIN.  To do so, we use the single-vertex add method of the tin.
    for (int iStart = 0; iStart < 3; iStart++) {
      IIncrementalTin tin = new IncrementalTin(1.0);
      for (int i = 0; i < 3; i++) {
        tin.add(vList.get((i + iStart) % 3));
      }
      ContourBuilderForTin builder = new ContourBuilderForTin(tin, null, zContour, true);
      ContourIntegrityCheck cic = new ContourIntegrityCheck(builder);
      assertTrue(cic.inspect(), "iStart=" + iStart + ": " + cic.getMessage());
      List<ContourRegion> rList = builder.getRegions();
      assertEquals(rList.size(), 2, "iStart=" + iStart + " invalid region count");
      assertEquals(rList.get(0).getArea(), rList.get(1).getArea(), 1.0e-8,
        "iStart=" + iStart + " invalid area values");
    }

    vList = new ArrayList<>();
    vList.add(new Vertex(0, 0, 0, 0));
    vList.add(new Vertex(1, 0, 0, 1));
    vList.add(new Vertex(0.5, Math.cos(Math.toRadians(60)), 40.0, 2));
    for (int iStart = 0; iStart < 3; iStart++) {
      IIncrementalTin tin = new IncrementalTin(1.0);
      for (int i = 0; i < 3; i++) {
        tin.add(vList.get((i + iStart) % 3));
      }
      ContourBuilderForTin builder = new ContourBuilderForTin(tin, null, zContour, true);
      ContourIntegrityCheck cic = new ContourIntegrityCheck(builder);
      assertTrue(cic.inspect(), "iStart=" + iStart + ": " + cic.getMessage());
      List<ContourRegion> rList = builder.getRegions();
      assertEquals(rList.size(), 2, "iStart=" + iStart + " invalid region count");
      // The builder sorts regions by area. So the first area will always be
      // the larger of the two, no matter what order the regions were generated
      // during construction.  In this case, the smaller region will be 1/3rd the
      // area of the larger
      assertEquals(rList.get(0).getArea() / 3.0, rList.get(1).getArea(), 1.0e-8,
        "iStart=" + iStart + " invalid area values");
    }
  }

  /**
   * Tests the case where a through-vertex contour spans a single segment,
   * crossing the TIN. This would also apply to the case where a corner
   * was lopped off, though that is not explicitly tested here.
   */
  @Test
  public void testShortTraversal() {
    double[] zContour = new double[]{20};  // just the through-vertex case
    double[] z2Contour = new double[]{10, 20, 30}; // the through-vertex and a couple of through-edge cases
    double[][] vCoord = new double[][]{
      {0, 0},
      {1, 0},
      {2, 0.75},
      {1, 0.75}
    };
    double[] zCoord = new double[]{0, 20, 40, 20};
    for (int iV = 0; iV < 4; iV++) {
      for (int iZ = 0; iZ < 4; iZ++) {
        IIncrementalTin tin = new IncrementalTin(1.0);
        for (int i = 0; i < 4; i++) {
          double x = vCoord[(iV + i) % 4][0];
          double y = vCoord[(iV + i) % 4][1];
          double z = zCoord[(iZ + i) % 4];
          tin.add(new Vertex(x, y, z, i));
        }
        ContourBuilderForTin builder = new ContourBuilderForTin(tin, null, zContour, true);
        ContourIntegrityCheck cic = new ContourIntegrityCheck(builder);
        boolean status = cic.inspect();
        String name = "zContour iV=" + iV + ", iZ=" + iZ;
        assertTrue(status, name + ": " + cic.getMessage());
        List<ContourRegion> rList = builder.getRegions();
        ContourRegion r0 = rList.get(0);
        ContourRegion r1 = rList.get(1);
        assertEquals(r0.getArea(), r1.getArea(), 1.0e-7, "Area mistmatch for case " + name);

        builder = new ContourBuilderForTin(tin, null, z2Contour, true);
        cic = new ContourIntegrityCheck(builder);
        status = cic.inspect();
        name = "z2Contour iV=" + iV + ", iZ=" + iZ;
        assertTrue(status, name + ": " + cic.getMessage());
      }
    }
  }

  /**
   * Test the problem set originally identified in Issue 66
   */
  @Test
  public void testIssue66() {

    ArrayList<Vertex> vList = new ArrayList<>();
    vList.add(new Vertex(0.0, 0.0, 0.0, 0));
    vList.add(new Vertex(0.0, 40.0, 40.0, 1));
    vList.add(new Vertex(40.0, 0.0, 40.0, 2));
    vList.add(new Vertex(40.0, 40.0, 40.0, 3));
    vList.add(new Vertex(80.0, 0.0, 40.0, 4));
    vList.add(new Vertex(80.0, 40.0, 40.0, 5));

    double[] zContour = new double[]{40.0};

    for (int iStart = 0; iStart < vList.size(); iStart++) {
      IIncrementalTin tin = new IncrementalTin(1.0);
      for (int i = 0; i < vList.size(); i++) {
        tin.add(vList.get((iStart + i) % vList.size()));
      }

      ContourBuilderForTin builder = new ContourBuilderForTin(tin, null, zContour, true);
      ContourIntegrityCheck cic = new ContourIntegrityCheck(builder);
      boolean status = cic.inspect();
      String name = "iStart=" + iStart;
      assertTrue(status, name + ": " + cic.getMessage());
    }
  }

}
