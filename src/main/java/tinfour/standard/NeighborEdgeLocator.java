/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.standard;

import tinfour.common.INeighborEdgeLocator;
import tinfour.common.IQuadEdge;
import tinfour.common.NeighborEdgeVertex;
import tinfour.common.QuadEdge;
import tinfour.common.Vertex;

/**
 * Provides a specific instance of the INeighborEdge interface tuned for
 * efficient use with this package's TIN implementation.
 */
class NeighborEdgeLocator implements INeighborEdgeLocator {

  QuadEdge neighborEdge;
  final StochasticLawsonsWalk walker;
  final IncrementalTin tin;

  /**
   * Constructs an instance coupled to the specified TIN.
   * @param tin a valid instance
   */
  NeighborEdgeLocator(IncrementalTin tin) {

    this.tin = tin;
    double nominalPointSpacing = tin.getNominalPointSpacing();
    walker = new StochasticLawsonsWalk(nominalPointSpacing);

  }

  @Override
  public IQuadEdge getNeigborEdge(double x, double y) {
    if(!tin.isBootstrapped()){
      return null;
    }
    if (neighborEdge == null) {
      neighborEdge = tin.getStartingEdge();
    }
    QuadEdge e = walker.findAnEdgeFromEnclosingTriangle(neighborEdge, x, y);
    neighborEdge = e;
    return e;
  }

  @Override
  public NeighborEdgeVertex getEdgeWithNearestVertex(double x, double y) {
    if (neighborEdge == null) {
      neighborEdge = tin.getStartingEdge();
    }
    final QuadEdge e = walker.findAnEdgeFromEnclosingTriangle(neighborEdge, x, y);
    neighborEdge = e;
    Vertex a = e.getA();
    Vertex b = e.getB();
    Vertex c = e.getForward().getB();
    double dA = a.getDistanceSq(x, y);
    double dB = b.getDistanceSq(x, y);
    if (c == null) {
      if (dA < dB) {
        return new NeighborEdgeVertex(e, Math.sqrt(dA), x, y, false);
      } else {
        return new NeighborEdgeVertex(e.getForward(), Math.sqrt(dB), x, y, false);
      }
    }

    double dC = c.getDistanceSq(x, y);
    if (dA < dB) {
      if (dA < dC) {
        return new NeighborEdgeVertex(e, Math.sqrt(dA), x, y, true);
      } else {
        return new NeighborEdgeVertex(e.getReverse(), Math.sqrt(dC), x, y, true);
      }
    } else {
      if (dB < dC) {
        return new NeighborEdgeVertex(e.getForward(), Math.sqrt(dB), x, y, true);
      } else {
        return new NeighborEdgeVertex(e.getReverse(), Math.sqrt(dC), x, y, true);
      }
    }
  }

  @Override
  public void resetForChangeToTin() {
    neighborEdge = null;
  }

}
