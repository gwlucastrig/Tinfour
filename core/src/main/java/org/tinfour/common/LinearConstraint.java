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
 * 01/2016  G. Lucas     Fixed bounds bug reported by Martin Janda
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

/**
 * An implementation of the IConstraint interface intended to store
 * constraints comprised of a chain of connected line segments.
 * Constraint chains must be non-self-intersecting (except at segment
 * endpoints). The chain must never "fold back" on itself. All segments
 * in the chain must be non-zero-length.
 * <p>
 * Do not use this class for closed polygons.
 */
public class LinearConstraint extends PolyLineConstraintAdapter implements IConstraint {

  /**
   * The standard constructor
   */
  public LinearConstraint(){
    // Although an empty constructor usually doesn't need to be specified,
    // Java requires that this empty constructor be included to
    // support the call to new LinearConstraint in
    // getConstraintWithNewGeometry
  }

  /**
   * A convience constructor intended for the frequently occurring case
   * in which an application wishes to define a constraint as a single
   * line segment.
   * @param v0 the initial vertex of the edge
   * @param v1 the final vertex of the edge
   */
  public LinearConstraint(Vertex v0, Vertex v1){
     add(v0);
     add(v1);
      complete();
  }

   /**
   * Constructs a constraint with the specified vertices.  This approach is
   * generally faster than adding the vertices one at a time.
   * @param vList a valid list containing at least 2 distinct points.
   */
  public LinearConstraint(List<Vertex>vList){
    super(vList);
  }

  @Override
  public final void complete() {
    isComplete = true;
  }

  @Override
  public boolean isPolygon() {
    return false;
  }


  /**
   * Indicates whether the constraint defines a data area.
   * Because linear constraints cannot define an area, this method
   * always returns false.
   *
   * @return always false for linear constraints.
   */
  @Override
  public boolean definesConstrainedRegion() {
    return false;
  }


  @Override
  public double getNominalPointSpacing() {
    if(list.size()<2){
      return Double.NaN;
    }
    return length/(list.size()-1);
  }

  @Override
  public LinearConstraint getConstraintWithNewGeometry(List<Vertex> geometry) {
    LinearConstraint c = new LinearConstraint(geometry);
    c.applicationData = applicationData;
    c.constraintIndex = constraintIndex;
    c.maintainingTin = maintainingTin;
    c.constraintLinkingEdge = constraintLinkingEdge;
    c.complete();
    return c;
  }

    @Override
    public LinearConstraint refactor(Iterable<Vertex> geometry) {
        ArrayList<Vertex> gList = new ArrayList<>();
        for (Vertex v : geometry) {
            gList.add(v);
        }
        return this.getConstraintWithNewGeometry(gList);
    }

  @Override
  public boolean isValid(){
    return list.size()>=2;
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
    Path2D path = new Path2D.Double();
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
    return path;
  }
  
}
