/* --------------------------------------------------------------------
 * Copyright (C) 2024  Gary W. Lucas.
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
 * 01/2024  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.io.PrintStream;
import java.util.BitSet;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.svm.properties.SvmProperties;

/**
 * Implements logic for removing single-point anomalies from the
 * source data.  This class is an experiment and is not yet ready for
 * general use.
 */
class SvmSinglePointAnomalyFilter {

    private static final double DEFAULT_SLOPE_OF_ANOMALY = 0.5;
    private static final double DEFAULT_SLOPE_OF_SUPPORT = 0.015;

    private final double slopeOfAnomaly;
    private final double slopeOfSupport;

    SvmSinglePointAnomalyFilter(SvmProperties properties) {
      slopeOfAnomaly = properties.getExperimentalFilterSlopeOfAnomaly(DEFAULT_SLOPE_OF_ANOMALY);
      slopeOfSupport = properties.getExperimentalFilterSlopeOfSupport(DEFAULT_SLOPE_OF_SUPPORT);
    }
  /**
   * Process the input sample data in the TIN and remove single-point anomalies.
   * @param ps a valid instance to receive results
   * @param tin a valid instance
   * @return
   */
 int process(
    PrintStream ps,
    IIncrementalTin tin) {
    int maxEdgeIndex = tin.getMaximumEdgeAllocationIndex();
    BitSet edgeSet = new BitSet(maxEdgeIndex);
    for (IQuadEdge edge : tin.getPerimeter()) {
      int baseIndex = edge.getBaseIndex();
      edgeSet.set(baseIndex);
      edgeSet.set(baseIndex | 1);
    }

    double mCutOff = slopeOfSupport;
    double mReject = slopeOfAnomaly;
    double mMax = 0;
    int nNegReject = 0;
    int nPosReject = 0;
    for (IQuadEdge qEdge : tin.edges()) {
      IQuadEdge edge = qEdge;
      innerLoop:
      for (int iEdge = 0; iEdge < 2; iEdge++, edge = edge.getDual()) {
        int eIndex = edge.getIndex();
        if (edgeSet.get(eIndex)) {
          continue;
        }
        edgeSet.set(eIndex);
        Vertex a = edge.getA();
        if (a.isConstraintMember()) {
          continue;
        }
        double aZ = a.getZ();
        int nPos = 0;
        int nNeg = 0;
        int n = 0;
        double m = 0;
        int nSupport = 0;
        for (IQuadEdge e : edge.pinwheel()) {
          edgeSet.set(e.getIndex());
          Vertex b = edge.getB();
          double bZ = b.getZ();
          double d = e.getLength();
          double mZ = (bZ - aZ) / d; // slope
          if (mZ > 0) {
            nPos++;
          } else {
            nNeg++;
          }

          double mAbs = Math.abs(mZ);
          m += mAbs;
          if (mAbs < mCutOff) {
            nSupport++;
          }

          n++;
          if (mAbs > mMax) {
            mMax = mAbs;
          }
        }

        if (nSupport==0 && nPos > 0 && nNeg == 0) {
          m /= n;
          if (m > mReject) {
            //System.out.format("reject pos %10d %9.3f %9.3f%n", a.getIndex(), m, dSum);
            a.setWithheld(true);
            nPosReject++;
          }
        }
        if (nSupport==0 && nPos == 0 && nNeg > 0) {
          m /= n;
          if (m > mReject) {
            //System.out.format("reject neg %10d %9.3f %9.3f%n", a.getIndex(), m, dSum);
            a.setWithheld(true);
            nNegReject++;
          }
        }


      }
    }

    ps.println("Single-point Anomaly Filter Rejected " + (nPosReject+nNegReject)+" soundings");
    ps.format("  Pos: %8d%n", nPosReject);
    ps.format("  Neg: %8d%n", nNegReject);
    return nPosReject+nNegReject;
  }

  /**
   * Gets the slope criteria used to identify a data point as a potential anomaly.
   * @return a valid slope
   */
   double getSlopeOfAnomaly() {
    return slopeOfAnomaly;
  }

  /**
   * Gets the slope criterial used to identify a data point as a likely member
   * of a local feature.
   * @return a valid slope
   */
   double getSlopeOfSupport() {
    return slopeOfSupport;
  }
}
