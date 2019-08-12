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
 * 07/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ContourRegion {

  List<ContourRegionMember> memberList = new ArrayList<>();
  int regionIndex;
  double z;
  double area;
  double absArea;
  ContourRegion parent;
  List<ContourRegion> children = new ArrayList<>();

  ContourRegion() {

  }

  ContourRegion(Contour contour) {
    assert contour.closedLoop : "Single contour constructor requires closed loop";
    if (contour.closedLoop) {
      double a = 0;
      memberList.add(new ContourRegionMember(contour, true));
      double x0 = contour.xy[contour.n - 2];
      double y0 = contour.xy[contour.n - 1];
      for (int i = 0; i < contour.n; i += 2) {
        double x1 = contour.xy[i];
        double y1 = contour.xy[i + 1];
        a += x0 * y1 - x1 * y0;
        x0 = x1;
        y0 = y1;
      }
      area = a / 2;
      absArea = Math.abs(area);
      if (area < 0) {
        regionIndex = contour.rightIndex;
      } else {
        regionIndex = contour.leftIndex;
      }
      z = contour.z;
    }
  }

  
  public double[] getXY() {
    Contour contour = memberList.get(0).contour;
    return contour.getCoordinates();
  }

  void addChild(ContourRegion region) {
    children.add(region);
    region.parent = this;
  }

  public boolean isPointInsideRegion(double x, double y) {
    Contour c = memberList.get(0).contour;
    double[] xy = c.xy;
    int rCross = 0;
    int lCross = 0;
    int n = c.size();
    double x0 = xy[n * 2 - 2];
    double y0 = xy[n * 2 - 1];
    for (int i = 0; i < n; i++) {

      double x1 = xy[i * 2];
      double y1 = xy[i * 2 + 1];

      double yDelta = y0 - y1;
      if (y1 > y != y0 > y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest > x) {
          rCross++;
        }
      }
      if (y1 < y != y0 < y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest < x) {
          lCross++;
        }
      }
      x0 = x1;
      y0 = y1;
    }

    // (rCross%2) != (lCross%2)
    if (((rCross ^ lCross) & 0x01) == 1) {
      return false; // on border
    } else if ((rCross & 0x01) == 1) {
      return true; // unambiguously inside
    }
    return false; // unambiguously outside

  }

  @Override
  public String toString() {
    return String.format("%4d %12.2f  %s %d", regionIndex, area, parent == null ? "root " : "child", children.size());
  }
}
