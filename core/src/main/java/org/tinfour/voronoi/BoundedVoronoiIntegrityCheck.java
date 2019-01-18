/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * Date Name Description
 * ------   --------- -------------------------------------------------
 * 09/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides tools for checking the correctness of the construction of
 * a Voronoi Diagram.
 */
public class BoundedVoronoiIntegrityCheck {

    String message;
    BoundedVoronoiDiagram lmv;

    /**
     * Constructs an instance of the integrity checker tied to the
     * specified instance.
     * @param BoundedVoronoi a valid, correctly populated instance 
     */
    public BoundedVoronoiIntegrityCheck(BoundedVoronoiDiagram BoundedVoronoi) {
        lmv = BoundedVoronoi;
        message = null;
    }

    /**
     * Tests the Voronoi Diagram to see if it passes a number of sanity 
     * checks for correctness of implementaiton.
     * @return true if the structure passes the test series; otherwise, false.
     */
    public boolean inspect() {
        message = null;

        int maxEdgeIndex = lmv.getMaximumEdgeAllocationIndex();
        boolean visited[] = new boolean[maxEdgeIndex];
        List<ThiessenPolygon> polyList = lmv.getPolygons();

        // Test Series 1:
        //   Verify that the edges of polygons make a complete circuit
        //   Verify that the anchor vertex is always inside the polygon
        //   Verify that all polygons have positive area
        //   Also:
        //       Populate the visited array to indicate which edges
        //          are members of polygons.
        for (int index = 0; index < polyList.size(); index++) {
            ThiessenPolygon poly = polyList.get(index);
            double area = poly.getArea();
            if(area<=0){
              message = "Polygon " + index+ " has non-positive area "+area;
              return false;
            }
            List<IQuadEdge> eList = poly.getEdges();
            IQuadEdge first = eList.get(0);
            IQuadEdge edge = first;
            IQuadEdge ePrior = first.getReverse();
            boolean synthetic = false;
            int n = 0;
            do {
                n++;
                visited[edge.getIndex()] = true;
                synthetic |= edge.isSynthetic();
                Vertex vEdge = edge.getA();
                Vertex vPrior=ePrior.getB();
                double test = vPrior.getDistance(vEdge);
                if (test > 0) {
                    message = "Polygon " + index
                            + " edge " + edge.getIndex()
                            + " vertex mismatch with predecessor, dist=" + test
                            + " vPrior="+vPrior.getLabel()
                            +", vEdge="+vEdge.getLabel();
                    return false;
                }
                ePrior = edge;
                edge = edge.getForward();
            } while (!edge.equals(first));
            if (n != eList.size()) {
                message = "Polygon " + index
                        + " edge list size not equal to count "
                        + eList.size() + ", " + n;
                return false;
            }

            Vertex v = poly.getVertex();
            if (!poly.isPointInPolygon(v.getX(), v.getY())) {
                message = "Vertex " + v.getLabel()
                        + " fails point-in-polygon test for polygon " + index;
                return false;
            }
        }

        // Test Series 2:
        //   Verify that interior edges belong to two polygons and
        //     perimeter edges belong to one polygon.
        List<IQuadEdge> eList = lmv.getEdges();
        for (IQuadEdge e : eList) {
            int index = e.getIndex();
            int n = (visited[index] ? 1 : 0) + (visited[index + 1] ? 1 : 0);
            if (e.isSynthetic()) {
                if (n != 1) {
                    message = "Exterior edge " + index
                            + " has polygon membership count of " + n;
                    return false;
                }
            } else if (n != 2) {
                message = "Interior edge " + index
                        + " has polygon membership of " + n;
                return false;
            }
        }

   //    Test to see if sum of polygon areas equals area of bounds
      double sumArea = 0;
      for(ThiessenPolygon p: lmv.getPolygons()){
        sumArea+=p.getArea();
      }
      Rectangle2D bounds = lmv.getBounds();
      double boundsArea = bounds.getWidth()*bounds.getHeight();
      double meanArea = (boundsArea+sumArea)/2.0;
      double areaTest = Math.abs(boundsArea-sumArea)/meanArea;
      if( areaTest> 1.0e-3){
        message = String.format(
                "Sum of polygon area differs from bounds specification "
                +"by more than %5.3f percent", areaTest*100);
        return false;
      }
        return true;
    }

    /**
     * Gets descriptive information about the cause of a test failure.
     *
     * @return if a failure occurred, descriptive information; otherwise a
     * string indicating that No Error Detected.
     */
    public String getMessage() {
        if (message == null) {
            return "No Error Detected";
        }
        return message;
    }

}
