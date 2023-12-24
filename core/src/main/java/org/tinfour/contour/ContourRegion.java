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

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.contour.Contour.ContourType;

/**
 * Provides a elements and access methods for a region created through a
 * contour-building process.
 */
public class ContourRegion {

  /**
   * An enumeration that indicates the type of a contour region.
   */
  public enum ContourRegionType {
    /**
     * All contours lie entirely within the interior of the TIN and do not
     * intersect its perimeter.
     */
    Interior,
    /**
     * At least one contour lies on the perimeter of the TIN. Note that
     * primary regions are never enclosed by another region.
     */
    Perimeter
  }

  final ContourRegionType contourRegionType;
  final List<ContourRegionMember> memberList = new ArrayList<>();
  final int regionIndex;
  final double area;
  final double absArea;
  final double xTest;
  final double yTest;

  final List<ContourRegion> children = new ArrayList<>();
  ContourRegion parent;

  int applicationIndex;

  ContourRegion(List<ContourRegionMember> memberList, int regionIndex) {
    if (memberList.isEmpty()) {
      throw new IllegalArgumentException(
        "An empty specification for a region geometry is not supported");
    }

    this.regionIndex = regionIndex;
    this.memberList.addAll(memberList);
    double a = 0;
    ContourRegionType rType = ContourRegionType.Interior;
    for (ContourRegionMember member : memberList) {
      if (member.contour.getContourType() == ContourType.Boundary) {
        rType = ContourRegionType.Perimeter;
      }
      double s = calculateAreaContribution(member.contour);
      if (member.forward) {
        a += s;
      } else {
        a -= s;
      }
    }

    contourRegionType = rType;

    area = a / 2.0;
    absArea = Math.abs(area);

    Contour contour = memberList.get(0).contour;
    xTest = (contour.xy[0] + contour.xy[2]) / 2.0;
    yTest = (contour.xy[1] + contour.xy[3]) / 2.0;

  }

  /**
   * Construct a region based on a single, closed-loop contour.
   *
   * @param contour a valid instance describing a single, closed-loop contour.
   */
  public ContourRegion(Contour contour) {
    if (contour.getContourType() == ContourType.Interior) {
      contourRegionType = ContourRegionType.Interior;
    } else {
      contourRegionType = ContourRegionType.Perimeter;
    }

    assert contour.closedLoop : "Single contour constructor requires closed loop";

    memberList.add(new ContourRegionMember(contour, true));
    area = calculateAreaContribution(contour) / 2;
    absArea = Math.abs(area);
    if (area < 0) {
      regionIndex = contour.rightIndex;
    } else {
      regionIndex = contour.leftIndex;
    }

    xTest = (contour.xy[0] + contour.xy[2]) / 2.0;
    yTest = (contour.xy[1] + contour.xy[3]) / 2.0;
  }

  private double calculateAreaContribution(Contour contour) {
    double x0 = contour.xy[0];
    double y0 = contour.xy[1];
    int n = contour.n / 2;
    double a = 0;
    for (int i = 1; i < n; i++) {
      double x1 = contour.xy[i * 2];
      double y1 = contour.xy[i * 2 + 1];
      a += x0 * y1 - x1 * y0;
      x0 = x1;
      y0 = y1;
    }
    return a;
  }

  /**
   * Get the XY coordinates for the contour region.  Coordinates
   * are stored in a one-dimensional array of doubles in the order:
   * <pre>
   * { (x0,y0), (x1,y1), (x2,y2), etc. }.
   *</pre>
   *
   * @return a safe copy of the geometry of the contour region.
   */
  public double[] getXY() {
    Contour contour = memberList.get(0).contour;
    if (memberList.size() == 1 && memberList.get(0).contour.isClosed()) {
      return contour.getXY();
    }
    int n = 0;
    for (ContourRegionMember member : memberList) {
      n += member.contour.size() - 1;
    }
    n++; // closure point
    double[] xy = new double[n * 2];
    int k = 0;
    for (ContourRegionMember member : memberList) {
      contour = member.contour;
      n = contour.size();
      if (member.forward) {
        for (int i = 0; i < n - 1; i++) {
          xy[k++] = contour.xy[i * 2];
          xy[k++] = contour.xy[i * 2 + 1];
        }
      } else {
        for (int i = n - 1; i > 0; i--) {
          xy[k++] = contour.xy[i * 2];
          xy[k++] = contour.xy[i * 2 + 1];
        }
      }
    }
    xy[k++] = xy[0];
    xy[k++] = xy[1];

    // remove any duplicate points
    n = 2;
    double px = xy[0];
    double py = xy[1];
    boolean removed = false;
    for (int i = 2; i < k; i += 2) {
      if (xy[i] == px && xy[i + 1] == py) {
        removed = true; // it's a duplicte, remove it
      } else {
        // it does not match the previous point
        px = xy[i];
        py = xy[i + 1];
        if (removed) {
          xy[n] = px;
          xy[n + 1] = py;
        }
        n += 2;
      }
    }
    if (removed) {
      // copy the coordinates to a downsized array
      double[] oldXy = xy;
      xy = new double[n];
      System.arraycopy(oldXy, 0, xy, 0, n);
    }
    return xy;
  }

  /**
   * Adds a child (nested) region to the internal list and sets
   * the child-region's parent link.
   * @param region a valid reference.
   */
  public void addChild(ContourRegion region) {
    children.add(region);
    region.parent = this;
  }

  /**
   * Removes all child (nested) regions from the internal list and
   * nullifies any of the child-region parent links.
   */
  public void removeChildren(){
    for(ContourRegion c: children){
      c.setParent(null);
    }
    children.clear();
  }
  /**
   * Sets the reference to the contour region that encloses the region
   * represented by this class. A null parent reference indicates that the
   * region is not enclosed by another.
   *
   * @param parent a valid reference; or a null.
   */
  public void setParent(ContourRegion parent) {
    this.parent = parent;
  }

  /**
   * Gets the parent region for this region, if any. Regions that are
   * enclosed in other regions will have a parent. Regions that are
   * not enclosed will not have a parent.
   *
   * @return a valid instance, or a null.
   */
  public ContourRegion getParent() {
    return parent;
  }


  /**
   * Indicates whether the specified point is inside the region
   *
   * @param x the Cartesian coordinate for the point of interest
   * @param y the Cartesian coordinate for the point of interest
   * @return true if the point is inside the contour; otherwise, fakse
   */
  public boolean isPointInsideRegion(double x, double y) {

    double[] xy = getXY();

    return isPointInsideRegion(xy, x, y);
  }

  /**
   * Indicates whether the specified point is inside a closed polygon.
   *
   * @param xy an array giving the Cartesian coordinates of the closed, simple
   * polygon for the region to be tested.
   * @param x the Cartesian coordinate for the point of interest
   * @param y the Cartesian coordinate for the point of interest
   * @return true if the point is inside the contour; otherwise, fakse
   */
  public boolean isPointInsideRegion(double[] xy, double x, double y) {
    int rCross = 0;
    int lCross = 0;
    int n = xy.length / 2;
    double x0 = xy[0];
    double y0 = xy[1];
    for (int i = 1; i < n; i++) {
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

  /**
   * Gets the absolute value of the overall area of the region.
   * No adjustment is made for enclosed regions.
   *
   * @return a positive value.
   */
  public double getAbsoluteArea() {
    return absArea;
  }

  /**
   * Get the area for the region excluding that of any enclosed
   * regions. The enclosed regions are not, strictly speaking,
   * part of this region and, so, are not included in the adjusted area.
   *
   * @return a positive value.
   */
  public double getAdjustedArea() {
    double sumArea = absArea;
    for (ContourRegion enclosedRegion : children) {
      sumArea -= enclosedRegion.getAbsoluteArea();
    }
    return sumArea;
  }

  /**
   * Gets the <strong>signed</strong> area of the region. If the points that specify the region
   * are given in a counter-clockwise order, the region will have a positive
   * area. If the points are given in a clockwise order, the region will have a
   * negative area.
   *
   * @return a signed real value.
   */
  public double getArea() {
    return area;
  }

  /**
   * Gets the absolute value of the area for this region.
   * @return a positve value greater than zero
   */
  public double getAbsArea(){
    return absArea;
  }
  /**
   * Gets the index of the region. The indexing scheme is based on the original
   * values of the zContour array used when the contour regions were built. The
   * minimum proper region index is zero.
   * <p>
   * At this time, regions are not constructed for areas of null data. In future
   * implementations, null-data regions will be indicated by a region index of
   * -1.
   *
   * @return a positive integer value, or -1 for null-data regions.
   */
  public int getRegionIndex() {
    return regionIndex;
  }

  /**
   * Gets a Path2D suitable for rendering purposes. The path includes only the
   * outer polygon for the region and does not include the internal (nested)
   * polygons. Because the contour builder class sorts polygons by descending
   * area, this method will general attain the same effect as the conventional
   * getPath2D() call when rendering the full set of regions. However, this
   * method is not suitable in cases where interior regions are to be omitted
   * or filled with a semi-transparent color.
   *
   * @param transform a valid AffineTransform, typically specified to map the
   * Cartesian coordinates of the contour to pixel coordinate.
   * @return a valid instance
   */
  public Path2D getPath2DWithoutNesting(AffineTransform transform) {
    AffineTransform af = transform;
    if (af == null) {
      af = new AffineTransform();  // identity transform
    }
    double[] xy = getXY();
    Path2D path = new Path2D.Double();
    appendPathForward(af, path, xy);
    return path;
  }

  /**
   * Gets a Path2D suitable for rendering purposes including both the outer
   * polygon and any internal (nested child) polygons. In used for fill
   * operations, regions that include nested child regions will be rendered with
   * "holes" where the child polygons are indicated.
   *
   * @param transform a valid AffineTransform, typically specified to map the
   * Cartesian coordinates of the contour to pixel coordinate.
   * @return a valid instance
   */
  public Path2D getPath2D(AffineTransform transform) {
    AffineTransform af = transform;
    if (af == null) {
      af = new AffineTransform();  // identity transform
    }
    double[] xy = getXY();
    Path2D path = new Path2D.Double();
    path.setWindingRule(Path2D.WIND_EVEN_ODD);
    appendPathForward(transform, path, xy);
    for (ContourRegion child : children) {
      xy = child.getXY();
      appendPathForward(af, path, xy);
    }

    return path;
  }

  private void appendPathForward(
    AffineTransform transform, Path2D path, double[] xy) {
    int n = xy.length / 2;
    if (n < 2) {
      return;
    }

    double[] c = new double[n * 2];
    transform.transform(xy, 0, c, 0, n);

    path.moveTo(c[0], c[1]);
    for (int i = 1; i < n; i++) {
      path.lineTo(c[i * 2], c[i * 2 + 1]);
    }
    path.closePath();
  }

  /**
   * Gets a point lying on one of the segments in the region to support testing
   * for polygon enclosures. Note that the test point is never one of the
   * vertices of the segment.
   *
   * @return a valid instance of a Point2D object
   */
  public Point2D getTestPoint() {
    return new Point2D.Double(xTest, yTest);
  }

  /**
   * Gets a list of regions that are enclosed by this region.
   * This list includes only those regions that are immediately
   * enclosed by this region, but does not include any that may
   * be "nested" within the enclosed regions.
   * @return a valid, potentially empty, list.
   */
  public List<ContourRegion> getEnclosedRegions() {
    List<ContourRegion> nList = new ArrayList<>();
    nList.addAll(children);
    return nList;
  }

  /** Gets an enumerated value indicating what kind of contour
   * region this instance represents.
   *
   * @return a valid enumeration.
   */
  public ContourRegionType getContourRegionType(){
    return this.contourRegionType;
  }


  @Override
  public String toString() {
    String areaString;
    if (absArea > 0.1) {
      areaString = String.format("%12.3f", area);
    } else {
      areaString = String.format("%f", area);
    }
    return String.format("%4d %s  %s %3d",
      regionIndex,
      areaString,
      parent == null ? "root " : "child",
      children.size());
  }

  /**
   * Allows a calling implementation to set an application-defined index value
   * to be used to associate supplemental data with the region
   * @param applicationIndex an arbitrary, application-specific index value.
   */
  public void setApplicationIndex(int applicationIndex){
    this.applicationIndex = applicationIndex;
  }

  /**
   * Gets an application-defined index value to be used by the calling
   * implementation to associate supplemental data with the region.
   * @return an arbitaru, application-specific index value.
   */
  public int getApplicationIndex(){
    return this.applicationIndex;
  }

  public ContourRegionType getRegionType(){
    return contourRegionType;
  }

  public boolean hasChildren(){
    return !children.isEmpty();
  }
}
