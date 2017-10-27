/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 10/2016  G. Lucas     Created
 * 01/2017  G. Lucas     Fixed bounds bug reported by Martin Janda
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

import java.util.List;

/**
 * An implementation of the IConstraint interface intended to store
 * constraints comprised of a polygon. The polygon is allowed to be
 * non-convex, but the segments comprising the polygon must be not
 * intersect except at segment endpoints (e.g. the polygon must be a
 * simple, non intersecting closed loop). All segments
 * in the chain must be non-zero-length.
 * <p>
 * For polygons defining an area, the interior of the area is defined as
 * being bounded by a counter-clockwise polygon. Thus a clockwise polygon
 * would define a "hole" in the area. It is worth noting that this convention
 * is just the opposite of that taken by ESRI's Shapefile format, though
 * it is consistent with conventions used in general computational geometry.
 * <p>
 * <strong>Organizing the list of vertices that defines the polygon</strong>
 * Some implementations of polygon geometries include an extra "closure"
 * vertex so that the last vertex in the list of vertices that defines the
 * polygon is also the first. Although that approach has some advantages,
 * this class does not use it. Each vertex in the polygon geometry is assumed
 * to be unique. Thus, if the polygon represents a triangle, the
 * getVertices and Vertex iterator methods will return exactly three vertices.
 */
public class PolygonConstraint extends PolyLineConstraintAdapter implements IConstraint {

  private double squareArea;

  @Override
  public List<Vertex> getVertices() {
    return list;
  }

  @Override
  public void complete() {
    if (isComplete) {
      return;
    }

    isComplete = true;

    if (list.size() > 1) {
      // The calling application may have included a "closure" vertex
      // adding the same vertex to both the start and end of the polygon.
      // That approach is not in keeping with the requirement that all
      // vertices in the list be unique.  The following logic provides
      // a bit of forgiveness to the applicaiton by removing the extra vertex.
      Vertex a = list.get(0);
      Vertex b = list.get(list.size() - 1);
      if (a.getX() == b.getX() && a.getY() == b.getY()) {
        list.remove(list.size() - 1);
      } else {
        // since no closure was supplied, we need to complete the
        // length calculation to include the lsat segment.
        length += list.get(0).getDistance(list.get(list.size() - 1));
      }
    }

    if (list.size() < 3) {
      return;
    }

    squareArea = 0;
    length = 0;
    Vertex a = list.get(list.size() - 1);
    for (Vertex b : list) {
      length += a.getDistance(b);
      squareArea += a.getX() * b.getY() - a.getY() * b.getX();
      a = b;
    }
    squareArea /= 2;
  }

  @Override
  public boolean isPolygon() {
    return true;
  }


  @Override
  public boolean definesConstrainedRegion() {
    return true;
  }

  /**
   * Get the computed square area for the constraint polygon.
   * The area is not available until the complete() method is called.
   * It is assumed that the area of a polygon with a counterclockwise
   * orientation is positive and that the area of a polygon with a
   * clockwise orientation is negative.
   *
   * @return if available, a non-zero (potentially negative) square area
   * for the constraint; otherwise, a zero
   */
  public double getArea() {
    return squareArea;
  }

  @Override
  public double getNominalPointSpacing() {
    if (list.size() < 2) {
      return Double.NaN;
    }
    if (isComplete) {
      return length / list.size();
    }
    return length / (list.size() - 1);
  }

  @Override
  public PolygonConstraint getConstraintWithNewGeometry(Iterable<Vertex> geometry) {
    PolygonConstraint c = new PolygonConstraint();
    c.applicationData = applicationData;
    c.constraintIndex = constraintIndex;
    for (Vertex v : geometry) {
      c.add(v);
    }
    c.complete();
    return c;
  }

  @Override
  public PolygonConstraint refactor(Iterable<Vertex> geometry) {
    return this.getConstraintWithNewGeometry(geometry);
  }

  @Override
  public boolean isValid() {
    if (list.size() < 3) {
      return false;
    } else if (list.size() == 3) {
      Vertex v0 = list.get(0);
      Vertex v1 = list.get(2);
      if (v0.getX() == v1.getX() && v0.getY() == v1.getY()) {
        return false;
      }
    }
    return true;
  }

}
