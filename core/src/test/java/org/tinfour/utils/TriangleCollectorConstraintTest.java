
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
 * 07/2021  G. Lucas     Created
 *
 * -----------------------------------------------------------------------
 */
 
package org.tinfour.utils;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;



/**
 * Performs tests of the triangle collector class to ensure that
 * is collects all relevant triangles exactly once.  This test was
 * instituted in response to Issue # 77.
 */
public class TriangleCollectorConstraintTest {

  private static class TriangleConsumer implements Consumer<SimpleTriangle> {

    final GeometricOperations geoOp = new GeometricOperations();

    int nTotal;
    int nConstrained;
    double areaTotal;
    double areaConstrained;

    @Override
    public void accept(SimpleTriangle t) {
      nTotal++;
      double area = geoOp.area(t.getVertexA(), t.getVertexB(), t.getVertexC());
      areaTotal += area;

      IConstraint con = t.getContainingRegion();
      if (con != null && con.definesConstrainedRegion()) {
        nConstrained++;
        areaConstrained += area;
      }
    }
  }

  private static class TripleVertexConsumer implements Consumer<Vertex[]> {

    final GeometricOperations geoOp = new GeometricOperations();

    int nTotal;
    double areaTotal;

    @Override
    public void accept(Vertex[] v) {
      nTotal++;
      double area = geoOp.area(v[0], v[1], v[2]);
      areaTotal += area;
    }
  }


  public TriangleCollectorConstraintTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }



  private double populateTin(IIncrementalTin tin){
    List<Vertex> vList = new ArrayList<>();
    int nSide = 8;
    int k = 0;
    for (int iRow = 0; iRow < nSide; iRow++) {
      for (int iCol = 0; iCol < nSide; iCol++) {
        double x = iCol / (nSide - 1.0);
        double y = iRow / (nSide - 1.0);
        vList.add(new Vertex(x, y, 0, k++));
      }
    }

    tin.add(vList, null);

    int nPoint = 32;
    double radius0 = 0.25;
    double radius1 = 0.125;
    PolygonConstraint pCon0 = new PolygonConstraint();
    PolygonConstraint  pCon1 = new PolygonConstraint();
    for (int i = 0; i < nPoint; i++) {
      double a = i * 2 * Math.PI / nPoint;
      double x = 0.5 + radius0 * Math.cos(a);
      double y = 0.5 + radius0 * Math.sin(a);
      pCon0.add(new Vertex(x, y, 0, k++));
    }
    pCon0.complete();

    for (int i = nPoint - 1; i >= 0; i--) {
      double a = i * 2 * Math.PI / nPoint;
      double x = 0.5 + radius1 * Math.cos(a);
      double y = 0.5 + radius1 * Math.sin(a);
      pCon1.add(new Vertex(x, y, 0, k++));
    }
    pCon1.complete();
    List<IConstraint> conList = new ArrayList<>();
    conList.add(pCon0);
    conList.add(pCon1);
    tin.addConstraints(conList, true);
    return pCon0.getArea() + pCon1.getArea();
  }

  @Test
  public void testSimpleTriangleVisitor(){
    IIncrementalTin tin = new IncrementalTin(1.0);
    double expectedAreaConstrained = populateTin(tin);

    TriangleConsumer tConsumer = new TriangleConsumer();
    TriangleCollector.visitSimpleTriangles(tin, tConsumer);
    double areaConstrained = tConsumer.areaConstrained;
    assertEquals(expectedAreaConstrained, areaConstrained, 1.0e-6, 
	    "Standard TIN, simple visitor miscomputed constrained area");
 
    tin = new SemiVirtualIncrementalTin(1.0);
    expectedAreaConstrained = populateTin(tin);

    tConsumer = new TriangleConsumer();
    TriangleCollector.visitSimpleTriangles(tin, tConsumer);
    areaConstrained = tConsumer.areaConstrained;
    assertEquals(expectedAreaConstrained, areaConstrained, 1.0e-6, 
	    "Semi-virtual TIN, simple visitor miscomputed constrained area");
  }
  
  @Test
  public void testVertexVisitor(){
    IIncrementalTin tin = new IncrementalTin(1.0);
    double expectedAreaConstrained = populateTin(tin);

	TripleVertexConsumer vConsumer = new TripleVertexConsumer();
    TriangleCollector.visitTriangles(tin, vConsumer);
	double areaTotal = vConsumer.areaTotal;
    assertEquals(1.0, areaTotal, 1.0e-6, "Standard TIN, triple vertex consumer computed wrong area");
	
	tin = new SemiVirtualIncrementalTin(1.0);
    expectedAreaConstrained = populateTin(tin);
	vConsumer = new TripleVertexConsumer();
    TriangleCollector.visitTriangles(tin, vConsumer);
	areaTotal = vConsumer.areaTotal;
    assertEquals(1.0, areaTotal, 1.0e-6, "Semi-virtual TIN, triple vertex consumer computed wrong area");
  }
  
  @Test
  public void testVisitTrianglesConstrained(){
    IIncrementalTin tin = new IncrementalTin(1.0);
    double expectedAreaConstrained = populateTin(tin);

	TripleVertexConsumer vConsumer = new TripleVertexConsumer();
    TriangleCollector.visitTrianglesConstrained(tin, vConsumer);
	double areaTotal = vConsumer.areaTotal;
    assertEquals(expectedAreaConstrained, areaTotal, 1.0e-6, "Standard TIN, computed wrong area");
	
	tin = new SemiVirtualIncrementalTin(1.0);
    expectedAreaConstrained = populateTin(tin);
	vConsumer = new TripleVertexConsumer();
    TriangleCollector.visitTrianglesConstrained(tin, vConsumer);
	areaTotal = vConsumer.areaTotal;
    assertEquals(expectedAreaConstrained, areaTotal, 1.0e-6, "Semi-virtual TIN, computed wrong area");
  }
  
}
