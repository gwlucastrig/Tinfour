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
package org.tinfour.common;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.utils.KahanSummation;

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
public class PolygonConstraint 
        extends PolyLineConstraintAdapter 
        implements IConstraint {

  private double squareArea;


  /**
   * Standard constructor
   */
  public PolygonConstraint() {
    // Although an empty constructor usually doesn't need to be specified,
    // Java requires that this empty constructor be included to
    // support the call to new PolygonConstraint in
    // getConstraintWithNewGeometry
  }

  /**
   * A convenience constructor intended for the frequently occurring case
   * in which an application wishes to define a constraint as a rectangle
   * or four-vertex polygon
   *
   * @param v0 the initial vertex of the polygon
   * @param v1 the second vertex of the polygon
   * @param v2 the third vertex of the polygon
   * @param v3 the final vertex of the polygon
   */
  public PolygonConstraint(Vertex v0, Vertex v1, Vertex v2, Vertex v3) {
    add(v0);
    add(v1);
    add(v2);
    add(v3);
    complete();
  }

  /**
   * Constructs a constraint with the specified vertices.  This approach is
   * generally faster than adding the vertices one at a time.
   * @param vList a valid list containing at least 3 distinct points.
   */
  public PolygonConstraint(List<Vertex>vList){
    super(vList);
  }

  @Override
  public List<Vertex> getVertices() {
    return list;
  }

  @Override
  public final void complete() {
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
        // length calculation to include the last segment.
        length += list.get(0).getDistance(list.get(list.size() - 1));
      }
    }

    if (list.size() < 3) {
      return;
    }

    double xCenter =0;
    double yCenter = 0;
    for (Vertex v : list) {
      xCenter += v.getX();
      yCenter += v.getY();
    }
    xCenter/=list.size();
    yCenter/=list.size();
    
    KahanSummation lenSum = new KahanSummation();
    KahanSummation areaSum = new KahanSummation();
    
    squareArea = 0;
    length = 0;
    Vertex a = list.get(list.size() - 1);
    for (Vertex b : list) {
      lenSum.add(a.getDistance(b));
      double aX = a.getX()-xCenter;
      double aY = a.getY()-yCenter;
      double bX = b.getX()-xCenter;
      double bY = b.getY()-yCenter;
      areaSum.add(aX*bY-aY*bX);
      a = b;
    }
    length = lenSum.getSum();
    squareArea = areaSum.getSum()/2.0;
  }

  @Override
  public boolean isPolygon() {
    return true;
  }


    /**
     * Indicates whether the constraint applies a constrained region behavior
     * when added to a TIN.
     *
     * @return for this implementation, this method returns a value of true
     */
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
  public PolygonConstraint getConstraintWithNewGeometry(List<Vertex> geometry) {
    PolygonConstraint c = new PolygonConstraint();
    c.applicationData = applicationData;
    c.constraintIndex = constraintIndex;
    c.maintainingTin = maintainingTin;
    c.constraintLinkingEdge = constraintLinkingEdge;
    for (Vertex v : geometry) {
      c.add(v);
      v.setConstraintMember(true);
    }
    c.complete();
    return c;
  }

  @Override
  public PolygonConstraint refactor(Iterable<Vertex> geometry) {
      ArrayList<Vertex> gList = new ArrayList<>();
      for (Vertex v : geometry) {
          gList.add(v);
      }
      return  getConstraintWithNewGeometry(gList);
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
  
  
  /**
   * Gets a Java Path2D based on the geometry of the constraint mapped through
   * an optional affine transform.
   *
   * @param transform a valid transform, or the null to use the identity
   * transform.
   * @return a valid instance of a Java Path2D
   */
  @Override
  public Path2D getPath2D(AffineTransform transform) {
    AffineTransform af = transform;
    if (transform == null) {
      af = new AffineTransform();
    }
    double[] c = new double[4];
    Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    boolean moveFlag = true;
    for (Vertex v : list) {
      c[0] = v.x;
      c[1] = v.y;
      af.transform(c, 0, c, 2, 1);
      if (moveFlag) {
        moveFlag = false;
        path.moveTo(c[2], c[3]);
      } else {
        path.lineTo(c[2], c[3]);
      }
    }
    path.closePath();
    return path;
  }
  
  
  
  @Override
  public String toString() {
    String appStr = "";
    if (applicationData == null) {
      return "PolygonConstraint, area=" + getArea();
    } else {
      appStr = applicationData.toString();

      return "PolygonConstraint, area=" + getArea() + ", appData=" + appStr;
    }
  }

}
