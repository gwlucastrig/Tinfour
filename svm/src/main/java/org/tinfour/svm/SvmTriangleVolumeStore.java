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

import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.utils.KahanSummation;

/**
 * Provides a collection and associated analysis methods for storing partial
 * volume results (triangles) and computing analysis results.
 */
class SvmTriangleVolumeStore {

  private static final int PAGE_ALLOC_SIZE = 1000;
  private static final int N_VALUES_PER_ENTRY = 11;

  private static class VolumePage {

    int n;
    final double[] p;

    // layoyt for []p array
    //   0  area
    //   1  partial volume
    //   2,3,4 (x,y,z)  for vertex with min z
    //   5,6,7 (x,y,z)  for vertex with mid z
    //   8,9,10 (y,y,z) for vertex with max z
    // thus
    //   zmin p[4]
    //   zmax p[10];
    VolumePage() {
      p = new double[PAGE_ALLOC_SIZE * N_VALUES_PER_ENTRY];
    }
  }

  class AreaVolumeResult {

    final double level;
    final double volume;
    final double area;

    AreaVolumeResult(double z, double area, double volume) {
      this.level = z;
      this.area = area;
      this.volume = volume;
    }
  }

  private List<VolumePage> pageList = new ArrayList<>();
  private VolumePage currentPage;
  private int nTriangles;
  private double maxArea = 0;

  private final GeometricOperations geoOp;

  /**
   * Standard constructor
   *
   * @param thresholds the thresholds associated with the spacing of the data.
   */
  SvmTriangleVolumeStore(Thresholds thresholds) {
    geoOp = new GeometricOperations(thresholds);
    currentPage = new VolumePage();
    pageList.add(currentPage);
  }

  void addTriangle(Vertex a, Vertex b, Vertex c, double test) {
    if (currentPage.n == PAGE_ALLOC_SIZE) {
      currentPage = new VolumePage();
      pageList.add(currentPage);
    }
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
    int i = currentPage.n * N_VALUES_PER_ENTRY;

    currentPage.n++;
    currentPage.p[i] = area;
    currentPage.p[i + 1] = partialVolume;
    currentPage.p[i + 2] = x0;
    currentPage.p[i + 3] = y0;
    currentPage.p[i + 4] = z0;
    currentPage.p[i + 5] = x1;
    currentPage.p[i + 6] = y1;
    currentPage.p[i + 7] = z1;
    currentPage.p[i + 8] = x2;
    currentPage.p[i + 9] = y2;
    currentPage.p[i + 10] = z2;

    double testPartialMean = (2220.0 - z2) + zPartialMean;
    double vtest = partialVolume + area * (2220.0 - z2);
    if (Math.abs(vtest - test) > 1.0e-4) {
      System.out.println("area error" + vtest + ", " + test + ", " + (2220.0 - testPartialMean));
    }
  }

  /**
   * Compute the volume and area for the specified water level.
   *
   * @param z the water level for computation
   * @return a valid instance containing the computation results.
   */
  AreaVolumeResult compute(double z) {
    // layout for []p array
    //   0  area
    //   1  partial volume
    //   2,3,4 (x,y,z)  for vertex with min z
    //   5,6,7 (x,y,z)  for vertex with mid z
    //   8,9,10 (y,y,z) for vertex with max z
    // thus
    //   zmin p[4]
    //   zmax p[10];
    KahanSummation areaSum = new KahanSummation();
    KahanSummation volumeSum = new KahanSummation();
    for (VolumePage page : pageList) {
      for (int i = 0; i < page.n; i++) {
        //   0  area
        //   1  partial volume
        //   2,3,4 (x,y,z)  for vertex with min z
        //   5,6,7 (x,y,z)  for vertex with mid z
        //   8,9,10 (y,y,z) for vertex with max z
        int offset = i * N_VALUES_PER_ENTRY;
        double area = page.p[offset];
        double partialVolume = page.p[offset + 1];
        double z0 = page.p[offset + 4];
        double z1 = page.p[offset + 7];
        double z2 = page.p[offset + 10];

        // recall triangle vertices are given in order of increasing z.
        // so z0 <= z1 <= z2.
        // if (z <= z0), we can trivially exclude the triangle
        // from the calculation because all vertices are above water level.
        if (z > z0) {
          if (z >= z2) {
            // trivial accept
            // double v = area*(z-z0 + z-z1 + z-z2)/3.0;
            //          = partialVolume+area*(z-z2 + z-z2 + z-z2)/3
            double v = partialVolume + area * (z - z2);
            areaSum.add(area);
            volumeSum.add(v);
          } else {
            // now we have to do some real work. 
            // the 3D triangle intersects the level plane of the elevation.
            double x0 = page.p[offset + 2];
            double y0 = page.p[offset + 3];
            double x1 = page.p[offset + 5];
            double y1 = page.p[offset + 6];
            double x2 = page.p[offset + 8];
            double y2 = page.p[offset + 9];

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
              double dryPolyArea = area-dryTrigArea;
              double dryPolyVolume = dryPolyArea*(z2-z);
              double v = partialVolume - dryTrigVolume - dryPolyVolume;
              areaSum.add(area - dryTrigArea);
              volumeSum.add(v);
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
              double a = Math.abs(geoOp.area(aX, aY, bX, bY, x0, y0));
              double v = a * (z - z0) / 3;
              areaSum.add(a);
              volumeSum.add(v);
            }
          }
        }
      }
    }
    return new AreaVolumeResult(z, areaSum.getSum(), volumeSum.getSum());
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
}
