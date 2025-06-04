
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
 * -------   ---------    -------------------------------------------------
 * 06/2025   G. Lucas     Created for Issue 118
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.semivirtual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.tinfour.common.IConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.utils.TriangleCollector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to see that constraint polygons remain correct when an
 * vertex is inserted to an existing Delaunay triangulation with constraints.
 */
public class SemiVirtualPolygonConstraintVertexInsertionTest {

  // For development, set this to a large number to exercise
  // a lot of test cases.  For deployment, keep it small
  // to minimize the build time.
  private static final int nTrials = 100;

  public SemiVirtualPolygonConstraintVertexInsertionTest() {
  }


  /**
   * Create an incremental TIN with covering a unit square with one or two
   * triangular constrained regions.
   *
   * @param fullyCovered indicates that both constraint regions are populated
   * @return a valid instance
   */
  private SemiVirtualIncrementalTin populateTin(boolean fullyCovered) {
    List<Vertex> vList = new ArrayList<>();
    List<IConstraint> constraints = new ArrayList<>();

    SemiVirtualIncrementalTin tin = new SemiVirtualIncrementalTin(1.0);
    vList.add(new Vertex(0, 0, 0, 0));
    vList.add(new Vertex(1, 0, 0, 1));
    vList.add(new Vertex(1, 1, 0, 2));
    vList.add(new Vertex(0, 1, 0, 3));
    tin.add(vList, null);

    PolygonConstraint poly = new PolygonConstraint();
    poly.add(vList.get(0));
    poly.add(vList.get(1));
    poly.add(vList.get(3));
    poly.complete();
    poly.setApplicationData(0);
    constraints.add(poly);

    if (fullyCovered) {
      poly = new PolygonConstraint();
      poly.add(vList.get(1));
      poly.add(vList.get(2));
      poly.add(vList.get(3));
      poly.complete();
      poly.setApplicationData(1);
      constraints.add(poly);
    }

    tin.addConstraints(constraints, true);

    return tin;
  }

  static class TriangleConsumer implements Consumer<SimpleTriangle> {

    int[] nTriangles = new int[3];
    double[] sumArea = new double[3];

    TriangleConsumer() {
    }

    @Override
    public void accept(SimpleTriangle t) {
      //  Vertex A = t.getVertexA();
      //  Vertex B = t.getVertexB();
      //  Vertex C = t.getVertexC();
      //
      //  IQuadEdge eA = t.getEdgeA();
      //  IQuadEdge eB = t.getEdgeB();
      //  IQuadEdge eC = t.getEdgeC();
      IConstraint con = t.getContainingRegion();
      if(con == null){
        nTriangles[2]++;
        sumArea[2] += t.getArea();
      }else {
        Object appData = con.getApplicationData();
        if (appData instanceof Integer) {
          int index = (Integer) appData;
          if (0 <= index && index <= 1) {
            nTriangles[index]++;
            sumArea[index] += t.getArea();
          }
        }
      }
    }

    double[] getAreas() {
      double[] a = new double[3];
      if (nTriangles[0] > 0) {
        a[0] = sumArea[0];
      }
      if (nTriangles[1] > 0) {
        a[1] = sumArea[1];
      }
      if(nTriangles[2] > 0){
        a[2] = sumArea[2];
      }
      return a;
    }
  }

  @Test
  public void testFullyCovered() {
    for (int iTrial = 0; iTrial < nTrials; iTrial++) {
      SemiVirtualIncrementalTin tin = populateTin(true);
      Random random = new Random(iTrial);
      double x = random.nextDouble();
      double y = random.nextDouble();
      tin.add(new Vertex(x, y, 0, 1066));
      double x2 = random.nextDouble();
      double y2 = random.nextDouble();
      tin.add(new Vertex(x2, y2, 0, 1067));
      double x3 = random.nextDouble();
      double y3 = random.nextDouble();
      tin.add(new Vertex(x3, y3, 0, 1068));

      TriangleConsumer consumer = new TriangleConsumer();
      TriangleCollector.visitSimpleTriangles(tin, consumer);

      double[] a = consumer.getAreas();
      assertEquals(a[0], a[1],  1.0e-4, "Unequal constraint coverage for trial " + iTrial);
    }
  }
  
  @Test
  public void testPartiallyCovered() {
    for (int iTrial = 0; iTrial < nTrials; iTrial++) {
      SemiVirtualIncrementalTin tin = populateTin(false);
      Random random = new Random(iTrial);
      double x = random.nextDouble();
      double y = random.nextDouble();
      tin.add(new Vertex(x, y, 0, 1066));
      double x2 = random.nextDouble();
      double y2 = random.nextDouble();
      tin.add(new Vertex(x2, y2, 0, 1067));
      double x3 = random.nextDouble();
      double y3 = random.nextDouble();
      tin.add(new Vertex(x3, y3, 0, 1068));

      TriangleConsumer consumer = new TriangleConsumer();
      TriangleCollector.visitSimpleTriangles(tin, consumer);

      double[] a = consumer.getAreas();
      assertEquals(a[0], a[2],  1.0e-4, "Unequal constraint coverage for trial " + iTrial);
    }
  }
  
}
