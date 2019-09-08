/* --------------------------------------------------------------------
 * Copyright 2015-to-2019 Gary W. Lucas.
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
 * 11/2015  G. Lucas     Created as part of introducing the IQuadEdge interface
 * 09/2019  G. Lucas     Refactored from NeighborEdgeLocator
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.semivirtual;

import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.NearestEdgeResult;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;

/**
 * Provides a specific instance of the IIncrementalTinNavigator interface tuned
 * for efficient use with this package's TIN implementation.
 */
class SemiVirtualIncrementalTinNavigator implements IIncrementalTinNavigator{

  SemiVirtualEdge neighborEdge;
  final SemiVirtualStochasticLawsonsWalk walker;
  final SemiVirtualIncrementalTin tin;

  /**
   * Constructs an instance coupled to the specified TIN.
   *
   * @param tin a valid instance
   */
  SemiVirtualIncrementalTinNavigator(SemiVirtualIncrementalTin tin) {

    this.tin = tin;
    walker = new SemiVirtualStochasticLawsonsWalk(tin.getThresholds());

  }

  @Override
  public IQuadEdge getNeighborEdge(double x, double y) {
    if (!tin.isBootstrapped()) {
      return null;
    }
    if (neighborEdge == null) {
      neighborEdge = tin.getStartingEdge();
    }
    neighborEdge = walker.findAnEdgeFromEnclosingTriangle(neighborEdge, x, y);
    return neighborEdge;
  }

  @Override
  public NearestEdgeResult getNearestEdge(double x, double y) {
    IQuadEdge a = getNeighborEdge(x, y);
    if(a==null){
      return null;  //  the TIN was not initialized
    }
    IQuadEdge b = a.getForward();
    IQuadEdge c = a.getReverse();

    Vertex A = a.getA();
    Vertex B = b.getA();
    Vertex C = c.getA();

    double test;
    double dX = x - A.getX();
    double dY = y - A.getY();
    double vX = B.getX() - A.getX();
    double vY = B.getY() - A.getY();
    double vM = Math.sqrt(vX * vX + vY * vY);  // magnitude of vector (vX, vY)
    double pX = -vY;
    double pY = vX;
    double pMin = (dX * pX + dY * pY) / vM;   // min dist in perpendicular direction
    IQuadEdge e = a;

    if (C == null) {
      // point is outside TIN.  we're done.
      return new NearestEdgeResult(e, pMin, x, y, false);
    }

    vX = C.getX() - B.getX();
    vY = C.getY() - B.getY();
    pX = -vY;  // the perpendicular
    pY = vX;
    vM = Math.sqrt(vX * vX + vY * vY);
    test = (dX * pX + dY * pY) / vM;
    if (test < pMin) {
      pMin = test;
      e = b;
    }

    vX = A.getX() - C.getX();
    vY = A.getY() - C.getY();
    pX = -vY;  // the perpendicular
    pY = vX;
    vM = Math.sqrt(vX * vX + vY * vY);
    test = ((x - C.getX()) * pX + (y - C.getY()) * pY) / vM;
    if (test < pMin) {
      pMin = test;
      e = c;
    }

    return new NearestEdgeResult(e, pMin, x, y, true);
  }

  @Override
  public Vertex getNearestVertex(double x, double y) {
    NearestEdgeResult n = getNearestEdge(x, y);
    if (n == null) {
      return null;
    }
    return n.getNearestVertex();
  }

  @Override
  public SimpleTriangle getContainingTriangle(double x, double y) {
    IQuadEdge a = getNeighborEdge(x, y);
    if (a == null) {
      return null;  //  the TIN was not initialized
    }
    IQuadEdge b = a.getForward();
    IQuadEdge c = a.getReverse();
    return new SimpleTriangle(tin, a, b, c);
  }

  @Override
  public boolean isPointInsideTin(double x, double y) {
        IQuadEdge e = getNeighborEdge(x, y);
    if(e==null){
      return false;  //  the TIN was not initialized
    }
 
    return e.getForward().getB() != null;
  }

  @Override
  public void resetForChangeToTin() {
    neighborEdge = null;
  }

}
