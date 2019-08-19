/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.IVertexValuator;
import org.tinfour.interpolation.NaturalNeighborInterpolator;

/**
 * An implementation of the vertex valuator that processes the vertices in a
 * Constrained Delaunay Triangulation and applies a low-pass filter over the
 * data.
 * <p>
 * Note that this class modifies the index values of the vertices stored in the
 * TIN. It also depends on the modified values as a way of tracking vertices.
 * Therefore, calling applications should not modify these values while the
 * smoothing filter is being used.
 */
public class SmoothingFilter implements IVertexValuator {

  NaturalNeighborInterpolator nni;
  IIncrementalTin tin;
  double[] zArray;
  private final double timeToConstructFilter;

  /**
   * Construct a smoothing filter. Note that this class modifies the index
   * values of the vertices stored in the TIN.
   * <p>
   * <strong>Important usage note:</strong> this constructor modifies the index
   * values of the vertices stored in the TIN. It also depends on the modified
   * values as a way of tracking vertices. Therefore, calling applications
   * should not modify these values while the smoothing filter is being used.
   * <p>
   * The vertices belonging to constraints are not smoothed, but are represented
   * with their original values by the smoothing filter.
   *
   * @param tin a valid Delaunay Triangulation
   */
  public SmoothingFilter(IIncrementalTin tin) {
    long time0 = System.nanoTime();
    this.tin = tin;
    nni = new NaturalNeighborInterpolator(tin);

    List<Vertex> vList = tin.getVertices();
    zArray = new double[vList.size() + 1];
    BitSet visited = new BitSet(vList.size() + 1);
    int k = 0;
    for (Vertex v : vList) {
      zArray[k] = v.getZ();
      v.setIndex(k++);
    }

    for (int i = 0; i < 25; i++) {
      visited.clear();
      double[] zSource = Arrays.copyOf(zArray, zArray.length);
      for (IQuadEdge e : tin.edges()) {
        process(visited, zSource, e);
        process(visited, zSource, e.getDual());
      }
    }

    long time1 = System.nanoTime();
    timeToConstructFilter = (time1 - time0) / 1.0e+6;
  }

  /**
   * Gets the time required to construct the filter, in milliseconds. Intended
   * for diagnostic and development purposes.
   *
   * @return a value in milliseconds.
   */
  public double getTimeToConstructFilter() {
    return timeToConstructFilter;
  }

  private void process(BitSet visited, double[] zSource, IQuadEdge edge) {
    Vertex A = edge.getA();
    if (A == null) {
      return;
    }
    int index = A.getIndex();
    if (visited.get(index)) {
      return;
    }
    visited.set(index);
    if (A.isConstraintMember()) {
      return;
    }
    double x = A.getX();
    double y = A.getY();

    List<IQuadEdge> pList = nni.getConnectedPolygon(edge);
    double[] w = nni.getBarycentricCoordinates(pList, x, y);
    if (w == null) {
      return;
    }

    double sumW = 0;
    double sumZ = 0;
    int k = 0;
    for (IQuadEdge p : pList) {
      double zP = zSource[p.getA().getIndex()];
      sumW += w[k];
      sumZ += zP * w[k];
      k++;
    }
    double zNeighbors = sumZ / sumW;
    zArray[index] = zNeighbors; //(zNeighbors + zSource[index]) / 2.0;
  }

  @Override
  public double value(Vertex v) {
    int index = v.getIndex();
    return zArray[index];
  }

  /**
   * Gets the array of adjustment for vertices.
   *
   * @return a valid array.
   */
  public double[] getVertexAdjustments() {
    return Arrays.copyOf(zArray, zArray.length);
  }

  /**
   * Sets the array of adjustments for vertices
   *
   * @param adjustments a valid array of same length as internal storage
   */
  public void setVertexAdjustments(double[] adjustments) {
    if (adjustments.length != zArray.length) {
      throw new IllegalArgumentException("Adjustment size "
              + adjustments.length
              + " does not match internal value "
              + zArray.length);
    }
    System.arraycopy(adjustments, 0, zArray, 0, adjustments.length);
  }
}
