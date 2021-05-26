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
package org.tinfour.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.tinfour.common.Vertex;
import org.tinfour.utils.NearestNeighborPointCollector;

/**
 * Performs round-trip tests for identifying the nearest points to
 * a specified coordinate pair.
 */
public class NearestNeighborPointCollectorTest {
    
    
  static class DistComp implements Comparator<Vertex> {

    final double xRef;
    final double yRef;

    DistComp(double xRef, double yRef) {
      this.xRef = xRef;
      this.yRef = yRef;
    }

    @Override
    public int compare(Vertex o1, Vertex o2) {
      double d1 = o1.getDistanceSq(xRef, yRef);
      double d2 = o2.getDistanceSq(xRef, yRef);
      return Double.compare(d1, d2);
    }

  }


  public NearestNeighborPointCollectorTest() {
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

  // The idea of the following test is that were create a random set of vertices and then test
  // to see if the collector returns the correct selection for a given coordinate pair (x,y).
  // To figure out what the correct set of vertices is, we sort the list of test vertices
  // by distance from the coordinates.  WE then know that the first nNeighbor points
  // in the sorted list should be the return value from the collector.
  // In development, this test is run thousands of times.  But for a unit test,
  // that would simply take too long.  Even so, this code is intended to be adapted
  // into a test program suitable for development.

  @Test
  public void testRandomPoints() {
    int nTests = 10;
    int nTestVertices = 2000;  // Number of vertices in the raw sample
    int nTestPoints = 10; // number of coordinate pairs for testing
    int nNeighbors = 20;  // Target number of neighbors to collect
    for (int iTest = 0; iTest < nTests; iTest++) {
      Random random = new Random(iTest);
      List<Vertex> vList = new ArrayList<>();
      for (int i = 0; i < nTestVertices; i++) {
        double x = random.nextDouble();
        double y = random.nextDouble();
        vList.add(new Vertex(x, y, 1, i));
      }

      NearestNeighborPointCollector nnpc = new NearestNeighborPointCollector(vList, false);

      // we wish to conduct this test in such a way that we can reproduce the
      // sequence of vertices and test coordinates for debugging purposes.
      // so we temporarily write the randomization result to arrays.
      // When debugging, all we would have to do is start the iPoint
      // loop at the indicated index in order to reach the problematic
      // sequence without stepping through a lot of code.
      double[] xTest = new double[nTestPoints];
      double[] yTest = new double[nTestPoints];
      for (int i = 0; i < nTestPoints; i++) {
        xTest[i] = random.nextDouble();
        yTest[i] = random.nextDouble();
      }

      // In order to test that we are getting the nTestNeighbor nearset
      // points, we need to sort the vertex list by distance from (x,y).
      // This is a "brute force" solution, but has the virtue of being
      // unambiguous.
      for (int iPoint = 0; iPoint < nTestPoints; iPoint++) {
        double x = xTest[iPoint];
        double y = yTest[iPoint];
        DistComp comp = new DistComp(x, y);
        Collections.sort(vList, comp);

        double[] d = new double[nNeighbors];

        Vertex[] v = new Vertex[nNeighbors];
        int n = nnpc.getNearestNeighbors(x, y, nNeighbors, d, v);
        assertEquals(n, nNeighbors, "getNearestNeighbors returned wrong number of vertices");
        
        for (int i = 0; i < n; i++) {
          d[i] = Math.sqrt(d[i]);
        }
        BitSet bSet = new BitSet(nTestVertices);
        for (int i = 0; i < n; i++) {
          bSet.set(v[i].getIndex());
        }
        // The vList is sorted from nearest (x,y) to farthest.
        // Test the first n vertices in vList to see if they got
        // included in the neighbors.
        for (int i = 0; i < n; i++) {
          Vertex test = vList.get(i);
          if (!bSet.get(test.getIndex())) {
            // The vertices returned from the collector do not match
            // those in the sorted list. This is probably a test failure.
            // But, there is one special case where
            // this is okay (though I've never seen it happen).
            // The collector makes no guarantee about what order
            // vertices are returned when multiple points are at the
            // same distance from the (x,y).  So it is possible that
            // there could have been additional points in the
            // vertex list that were equidistant with the last point
            // returned from the collector.
            double dTest = test.getDistance(x, y);
            if(Math.abs(dTest-d[n-1])>1.0e-8){
                fail("Results not consistent with test set for test case "+iTest+", iPoint=" + iPoint + ", i=" + i);
            }
            break;
          }
        }
      }
    } 
  }
}
