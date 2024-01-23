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
 * 02/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.utils.KahanSummation;

/**
 * Provides a collection and associated analysis methods for storing partial
 * volume results (triangles) and computing analysis results.
 */
class SvmTriangleVolumeTabulator {

  /**
   * Provides elements to support a running sum at the specified depth
   */
  static class AreaVolumeSum {

    final double level;
    final KahanSummation areaSum = new KahanSummation();
    final KahanSummation volumeSum = new KahanSummation();

    AreaVolumeSum(double z) {
      this.level = z;
    }

    void addTriangleResult(double area, double volume) {
      areaSum.add(area);
      volumeSum.add(volume);
    }

    void addTriangleArea(double area) {
      areaSum.add(area);
    }

    double getVolume(){
      return volumeSum.getSum();
    }

    double getArea(){
      return areaSum.getSum();
    }
  }

  private final double shoreReferenceElevation;
  boolean[] water;
  private final GeometricOperations geoOp;
  int nTriangles;
  int nFlatTriangles;

  KahanSummation flatAreaSum = new KahanSummation();
  KahanSummation depthAreaSum = new KahanSummation();
  KahanSummation depthAreaWeightedSum = new KahanSummation();

  private double maxArea = 0;

  final AreaVolumeSum[] avSumArray;

  /**
   * Standard constructor
   *
   * @param thresholds the thresholds associated with the spacing of the data.
   */
  SvmTriangleVolumeTabulator(IIncrementalTin tin, double shoreReferenceElevation, double[] zArray) {
    Thresholds thresholds = tin.getThresholds();
    geoOp = new GeometricOperations(thresholds);

    this.shoreReferenceElevation = shoreReferenceElevation;

    List<IConstraint> constraintsFromTin = tin.getConstraints();
    water = new boolean[constraintsFromTin.size()];
    for (IConstraint con : constraintsFromTin) {
      water[con.getConstraintIndex()] = (Boolean) con.getApplicationData();
    }

    avSumArray = new AreaVolumeSum[zArray.length];
    for (int i = 0; i < zArray.length; i++) {
      avSumArray[i] = new AreaVolumeSum(zArray[i]);
    }

  }


  /**
   * Adds the triangle data to the running sums.  The three vertices
   * are taken from the Incremental TIN and form a proper triangle given in
   * counterclockwise order (positive area).
   * @param a a valid instance
   * @param b a valid instance
   * @param c a valid instance
   */
  void addTriangle(Vertex a, Vertex b, Vertex c) {
    nTriangles++;
    double aZ = a.getZ();
    double bZ = b.getZ();
    double cZ = c.getZ();

    // order the three vertices in ascending order.
    // note that while tri(a,b,c) will be ordered counterclockwise,
    // the resulting triangle may be ordered clockwise and have a
    // negative area.
    Vertex v0, v1, v2;

    if (aZ <= bZ) {
      if (aZ <= cZ) {
        v0 = a;
        v1 = b;
        v2 = c;
      } else {
        v0 = c;
        v1 = a;
        v2 = b;
      }
    } else if (bZ <= cZ) {
      v0 = b;
      v1 = c;
      v2 = a;
    } else {
      v0 = c;
      v1 = b;
      v2 = a;
    }

    if (v2.getZ() < v1.getZ()) {
      Vertex swap = v1;
      v1 = v2;
      v2 = swap;
    }

    double x0 = v0.getX();
    double y0 = v0.getY();
    double z0 = v0.getZ();

    double x1 = v1.getX();
    double y1 = v1.getY();
    double z1 = v1.getZ();

    double x2 = v2.getX();
    double y2 = v2.getY();
    double z2 = v2.getZ();

    assert z0 <= z1 && z1 <= z2 : "vertex ordering failure";

    // (z2-z2 + z2-z1 + z2-z0)/3
    double zPartialMean = (2 * z2 - z1 - z0) / 3;
    double area = Math.abs(geoOp.area(v0, v1, v2));
    double partialVolume = area * zPartialMean;

    if (area > maxArea) {
      maxArea = area;
    }

    if (nEqual(aZ, shoreReferenceElevation)
      && nEqual(bZ, shoreReferenceElevation)
      && nEqual(cZ, shoreReferenceElevation)) {
      nFlatTriangles++;
      flatAreaSum.add(area);
    } else if (aZ < shoreReferenceElevation
      || bZ < shoreReferenceElevation
      || cZ < shoreReferenceElevation) {
      depthAreaSum.add(area);
      depthAreaWeightedSum.add(area * (shoreReferenceElevation - (aZ + bZ + cZ) / 3.0));
    }

    for (int i = 0; i < avSumArray.length; i++) {
      AreaVolumeSum avc = avSumArray[i];
      double z = avc.level;

      // recall triangle vertices are given in order of increasing z.
      // so z0 <= z1 <= z2.
      // if (z <= z0), we can trivially exclude the triangle
      // from the calculation because all vertices are above water level.
      if (z <= z0) {
        if (z == z0 && z0 == z1 && z1 == z0) {
          // a flat triangle at the level z, does not contribute
          // volume, but does contribute to the area calculation.
          avc.addTriangleArea(area);
        }
      } else if (z >= z2) {
        // trivial accept
        // double v = area*(z-z0 + z-z1 + z-z2)/3.0;
        //          = partialVolume+area*(z-z2 + z-z2 + z-z2)/3
        double v = partialVolume + area * (z - z2);
        avc.addTriangleResult(area, v);
      } else {
        // now we have to do some real work.
        // the 3D triangle intersects the level plane of the elevation.
        // above we've established that z is not equal to either
        // z0 or z2...  this fact restricts the cases we review
        if (z > z1) {
          // the upper part of the triangle is above water.
          // it will have the form of a smaller triangle.
          // the submerged part has the form of an irregular trapezoid.
          // computing the volume of the lower part is messy,
          // so we compute the volume of the upper part and subtract
          // that from the overall partial-volume.  This dry volume is
          // the sum of the dry triangle volume plus the volume of the
          // adjacent part of the original (the irregular polygon) that
          // is now above the water level.
          //   Note also that we reach this code block only when tz2>z.
          // And, because z>z1, we know that z2>z1 and z2>z0
          // so there will be no divide-by-zero in the interpolation
          //    This code calculation might actually be better implemented
          // by solving the integral for the irregular area using
          // a contour intergral and Green's theorem.
          double aX = interp(z, z0, z2, x0, x2);
          double aY = interp(z, z0, z2, y0, y2);
          double bX = interp(z, z1, z2, x1, x2);
          double bY = interp(z, z1, z2, y1, y2);
          double dryTrigArea = Math.abs(geoOp.area(aX, aY, bX, bY, x2, y2));
          // mean depth is (z2-z + z2-z + z2-z2 )/3
          double dryTrigVolume = dryTrigArea * (2 * (z2 - z)) / 3;
          double dryPolyArea = area - dryTrigArea;
          double dryPolyVolume = dryPolyArea * (z2 - z);
          double v = partialVolume - dryTrigVolume - dryPolyVolume;
          avc.addTriangleResult(area - dryTrigArea, v);
        } else {
          // z0 < z and z <= z1
          // the part of the triangle below water is a
          // smaller triangle.  we compute its volume directly
          // the three vertices will be at (x0, y0, z0),
          // (aX, aY, z) and (bX, bY, z).  So the average
          // depth contribution is AVG(z-z0, 0, 0), or (z-z0)/3
          double aX = interp(z, z0, z1, x0, x1);
          double aY = interp(z, z0, z1, y0, y1);
          double bX = interp(z, z0, z2, x0, x2);
          double bY = interp(z, z0, z2, y0, y2);
          double absArea = Math.abs(geoOp.area(aX, aY, bX, bY, x0, y0));
          double v = absArea * (z - z0) / 3;
          avc.addTriangleResult(absArea, v);
        }
      }
    }
  }

  /**
   * Interpolates a coordinate on a line segment from (z0, c0) to (z1, c1) at
   * parameter z. Note that the logic above should have excluded the case where
   * z0 equals z1,
   *
   * @param z the specified interpolation parameter
   * @param z0 initial z value
   * @param z1 final z value
   * @param c0 initial c coordinate
   * @param c1 final c coordinate
   * @return a valid coordinate
   */
  private double interp(double z, double z0, double z1, double c0, double c1) {
    return (c0 * (z1 - z) + c1 * (z - z0)) / (z1 - z0);
  }

  /**
   * Gets the number of triangles added to the volume store.
   *
   * @return a positive value.
   */
  int getTriangleCount() {
    return nTriangles;
  }

  private boolean nEqual(double a, double b) {
    return Math.abs(a - b) < 1.0e-5;
  }

  double getVolume() {
    return Math.abs(avSumArray[0].volumeSum.getSum());
  }

  double getSurfaceArea() {
    return avSumArray[0].areaSum.getSum();
  }

  double getFlatArea() {
    return flatAreaSum.getSum();
  }

  double getAdjustedMeanDepth() {
    return depthAreaWeightedSum.getSum() / this.depthAreaSum.getSum();
  }

  void summarize(SvmProperties properties, PrintStream ps) {
    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    double volumeFactor = properties.getUnitOfVolume().getScaleFactor();
    for (int i = 0; i < this.avSumArray.length; i++) {
      AreaVolumeSum a = avSumArray[i];
      ps.format("%3d   %12.3f   %8d  %12.3f  %12.3f%n",
        i, a.level, a.areaSum.getSummandCount(),
        a.areaSum.getSum() / areaFactor,
        a.volumeSum.getSum() / volumeFactor);
      if (a.volumeSum.getSum() == 0) {
        break;
      }
    }
  }

  void process(IIncrementalTin tin) {
    BitSet visited = new BitSet(tin.getMaximumEdgeAllocationIndex());
    for (IQuadEdge testEdge : tin.edges()) {
      IQuadEdge edge = testEdge;
      for (int i = 0; i < 2; i++, edge = testEdge.getDual()) {
        int eIndex = edge.getIndex();
        if (visited.get(eIndex)) {
          continue;
        }
        visited.set(eIndex);
        IQuadEdge forward = edge.getForward();
        IQuadEdge reverse = edge.getReverse();
        visited.set(forward.getIndex());
        visited.set(reverse.getIndex());
        Vertex A = edge.getA();
        Vertex B = edge.getB();
        Vertex C = forward.getB();
        IConstraint constraint = tin.getRegionConstraint(edge);
        Boolean appData = Boolean.FALSE;
        if (constraint instanceof PolygonConstraint) {
          appData = (Boolean) constraint.getApplicationData();
        }
        if (!appData) {
          continue;
        }
        if (edge.isConstrainedRegionBorder()) {
          if (C == null) {
            // perimeter edge, no further consideration required
            continue;
          }
          double vx = B.getX() - A.getX();
          double vy = B.getY() - A.getY();
          double cx = C.getX() - A.getX();
          double cy = C.getY() - A.getY();
          double s = vx * cy - vy * cx;
          if (s > 0) {
            // edge is to the left of the opposite vertex, so area is interior
            this.addTriangle(A, B, C);
          }
        } else if (edge.isConstrainedRegionInterior()) {
          this.addTriangle(A, B, C);
        }
      }
    }
  }

  /**
   * Get a list of tabulated area and volume objects ordered by descending
   * depth.  The list is truncated so that the last element has a zero
   * volume.
   * @return a valid, non-empty list.
   */
  List<AreaVolumeSum>getResults(){
    ArrayList<AreaVolumeSum>resultList = new ArrayList<>();
    for(AreaVolumeSum avSum: avSumArray){
      resultList.add(avSum);
      if(avSum.getVolume()==0){
        break;
      }
    }
    return resultList;
  }
}
