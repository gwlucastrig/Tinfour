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
 * 08/2019  G. Lucas     Created
 *
 * Notes:
 *    The idea of this test is that we create a number of adjacent 
 *    polygon constraints given as square regions organized in a 
 *    regular grid.  Given a random (x,y) coordinate point, we can compute
 *    which polygon it outght to be inside.  We then look it up using
 *    the TIN structure and see if it matches.
 *      Note that this test avoids constraints that lie exactly on
 *    a polygon boundary, because constraint membership would be ambiguous.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.development.cdt;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.SimpleTimeZone;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.INeighborEdgeLocator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.demo.utils.IDevelopmentTest;
import org.tinfour.demo.utils.TestOptions;

/**
 * Performs a test of adjacent polygon constraints by using multiple square
 * cells and evaluating Tinfour lookup operations.
 */
public class MultiSquareConstraintTest implements IDevelopmentTest {

  private int nRows = 5;
  private int nCols = 5;
  private int nTests = 10000000;

  /**
   * Process the test series using specifications from 
   * command line arguments.
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    MultiSquareConstraintTest test = new MultiSquareConstraintTest();
    test.runTest(System.out, args);
  }

  @Override
  public void runTest(PrintStream ps, String args[]) {

    // at this time, only options are implemented.
    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);
    Class<?> tinClass = options.getTinClass();

    nRows = options.getRowCount(nRows);
    nCols = options.getRowCount(nCols);
    nTests = options.getTestCount(nTests);
    int reportInterval = nTests / 100;
    if (reportInterval == 0) {
      reportInterval = 1;
    }
    long randomSeed = options.getRandomSeed(0);
    Random random = new Random(randomSeed);
    
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Multi-square Constraint Test");
    ps.format("Date of test:       %s UTC%n", sdFormat.format(date));
    ps.format("TIN class:          %s%n", tinClass.getName());
    ps.format("nRows:              %d%n", nRows);
    ps.format("nCols:              %d%n", nCols);

    List<IConstraint> constraints = new ArrayList<>(nRows * nCols);
    int iVertex = 0;
    for (int iRow = 0; iRow < nRows; iRow++) {
      for (int iCol = 0; iCol < nCols; iCol++) {
        int iConstraint = iRow * nCols + iCol;
        PolygonConstraint p = new PolygonConstraint();
        p.setApplicationData(iConstraint);
        int ix0 = iCol;
        int iy0 = iRow;
        int ix1 = ix0 + 1;
        int iy1 = iy0 + 1;
        // vertices are constructed in counter-clockwise order
        p.add(new Vertex(ix0, iy0, 0, iVertex++));
        p.add(new Vertex(ix1, iy0, 0, iVertex++));
        p.add(new Vertex(ix1, iy1, 0, iVertex++));
        p.add(new Vertex(ix0, iy1, 0, iVertex++));
        p.complete();
        constraints.add(p);
      }
    }
 

    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    tin.addConstraints(constraints, true);
    INeighborEdgeLocator locator = tin.getNeighborEdgeLocator();


    ps.format("%n");
    ps.format("       Test        Inside     Border Even   Border Odd    Errors%n");
    int kTests = 0;
    int kInterior = 0;
    int kBorderEven = 0;
    int kBorderOdd = 0;
    int kError = 0;
    for (int iTest = 0; iTest < nTests; iTest++) {
      double x = random.nextDouble() * nCols;
      double y = random.nextDouble() * nRows;
      if (x == Math.floor(x + 1.0e-6) || y == Math.floor(y + 1.0e+6)) {
        // one of the coordinates is very nearly integral, which would
        // place the query point directly on an edge.  Such points have
        // an ambiguous constraint membership, so we exclude from the test.
        continue;
      }

      IQuadEdge e = locator.getNeigborEdge(x, y);
      if (e == null) {
        kError++;
        continue;
      }
      Vertex C = e.getForward().getB();
      if (C == null) {
        kError++;
        continue;
      }

      int index = -1;
      IConstraint constraint = tin.getRegionConstraint(e);
      if (constraint != null) {
        index = (Integer) constraint.getApplicationData();
        if (e.isConstrainedRegionInterior()) {
          kInterior++;
        } else if (e.isConstrainedRegionBorder()) {
          if ((e.getIndex() & 1) == 0) {
            kBorderEven++;
          } else {
            kBorderOdd++;
          }
        }
      }

      int coordIndex = ((int) y) * nCols + (int) x;
      if (coordIndex != index) {
        Vertex A = e.getA();
        Vertex B = e.getB();
        ps.format("Error at %d %s %s%n", e.getIndex(), A.toString(), B.toString());
        kError++;
      }
      kTests++;
      if ((kTests % reportInterval) == 0) {
        ps.format("%12d %12d %12d %12d %12d%n", kTests, kInterior, kBorderEven, kBorderOdd, kError);
      }

    }

    if ((kTests % reportInterval) != 0) {
      ps.format("%12d %12d %12d %12d%n", kTests, kInterior, kBorderEven, kBorderOdd, kError);
    }
    if(kError==0){
      ps.format("%nNo errors encountered, test passes");
    }else{
      ps.format("%nErrors encountered, test fails");
    }
  }
}
