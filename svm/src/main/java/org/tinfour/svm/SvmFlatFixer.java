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
 * 04/2019  G. Lucas     Created
 *
 * Notes:
 *   At this time, the flat-fixer is not completely working.
 *   Part of its action is to subdivide triangles creating a new
 *   subset of non-flat triangles. Unfortunately, near the constraint
 *   boundaries, it can produce a potentially unlimited number of
 *   "skinny" triangles.  I am investigating this problem.
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;

/**
 * Implements logic for remediating flat triangles.
 */
class SvmFlatFixer {

  private final IIncrementalTin tin;
  private final double zShore;

  private int nRemediations;
  private double remediatedArea;
  private double remediatedVolume;

  private boolean isEquiv(double a, double b) {
    return Math.abs(a - b) < 1.0e-6;
  }

  SvmFlatFixer(IIncrementalTin tin, double zShore) {
    this.tin = tin;
    this.zShore = zShore;
  }

  List<Vertex> fixFlats(PrintStream ps) {
    // Initialize the visited bit-set with the perimeter.
    // Doing so will prevent the perimeter from being included
    // in the computations of means as well as in the searches to follow
    Thresholds thresholds = tin.getThresholds();
    GeometricOperations geoOp = new GeometricOperations(thresholds);

    List<IQuadEdge> fixList = new ArrayList<>();
    int iMax = tin.getMaximumEdgeAllocationIndex();
    BitSet visited = new BitSet(iMax);
    // we don't want to insert any points on the perimeter
    // so mark the perimeter as visited.
    List<IQuadEdge> perimeter = tin.getPerimeter();
    for (IQuadEdge edge : perimeter) {
      int index = edge.getIndex();
      visited.set(index);
      visited.set(index ^ 1); // XOR to get the dual
    }

    for (IQuadEdge edge : tin.edges()) {
      int index = edge.getIndex();
      if (visited.get(index)) {
        continue;
      }
      visited.set(index);
      visited.set(index ^ 1); // XOR to get the dual
      if (edge.isConstrained()) {
        continue;
      }
      Vertex A = edge.getA();
      Vertex B = edge.getB();
      if (isEquiv(A.getZ(), zShore) && isEquiv(B.getZ(), zShore)) {
        IQuadEdge dual = edge.getDual();
        Vertex C = edge.getForward().getB();
        Vertex D = dual.getForward().getB();
        if (C == null || D == null) {
          continue;  // not anticipated to happen
        }
        if (isEquiv(C.getZ(), zShore)) {
          if (!isEquiv(D.getZ(), zShore)) {
            fixList.add(edge);
          }
        } else if (isEquiv(D.getZ(), zShore)) {
          // we've already established that C.getZ() != zShore
          fixList.add(dual);
        }
      }
    }

    List<Vertex> fixVertices = new ArrayList<>(fixList.size());
    for (IQuadEdge edge : fixList) {
      IQuadEdge dual = edge.getDual();
      Vertex A = edge.getA();
      Vertex B = edge.getB();
      Vertex C = edge.getForward().getB();
      Vertex D = dual.getForward().getB();

      double area = geoOp.area(A, B, C);
      if (area < 1) {
        continue;
      }

      double mX = (A.getX() + B.getX()) / 2;
      double mY = (A.getY() + B.getY()) / 2;
      nRemediations++;
      double sC = C.getDistance(mX, mY);
      double sD = D.getDistance(mX, mY);
      double mZ;
      if (D.getAuxiliaryIndex() == SvmBathymetryData.FLAT_ADJUSTMENT) {
        // since the earlier vertex was already interpolated
        // the model just propagates its value inward.
        mZ = D.getZ();
      } else {
        // interpolate a new depth value combining an actual
        // sample (D) and a shoreline vertex (C).
        // In cases where the interpolation produces a very shallow
        // result, we limit the value to ensure that at least a small
        // volume constibution is made.
        mZ = (sC * D.getZ() + sD * C.getZ()) / (sC + sD);
        if (mZ > zShore -1) {
          mZ = zShore - 1;
        }
      }

      Vertex M = tin.splitEdge(edge, mZ, false);
      M.setSynthetic(true);
      M.setAuxiliaryIndex(SvmBathymetryData.FLAT_ADJUSTMENT);
      fixVertices.add(M);

      //  mean depth(A, M, C) is (0 + zShore-mZ + 0)/3
      //  mean depth(M, B, C) is (zShore-mZ + 0 + 0)/3
      //  so mean depth for both triangles is equal.
      //  the area of both split triangles is equal and given by area/2
      //  so volumes of both split triangles is equal
      //  so the volume is  2 * (area/2)*(zShore-mZ)/3
      double meanDepth = (zShore - mZ) / 3.0;
      double volume = area * meanDepth;
      remediatedArea += area;
      remediatedVolume += volume;

    }

    return fixVertices;
  }

  /**
   * Gets a count for the number of remediations that were performed.
   *
   * @return a positive integer value
   */
  int getRemediationCount() {
    return nRemediations;
  }

  /**
   * Gets the area of the triangles that were remediated.
   *
   * @return the area of the remediated triangles
   */
  double getRemediatedArea() {
    return remediatedArea;
  }

  /**
   * Gets the post-remediation volume contribution of the triangles that were
   * adjusted.
   *
   * @return the remediatedVolume
   */
  double getRemediatedVolume() {
    return remediatedVolume;
  }
}
