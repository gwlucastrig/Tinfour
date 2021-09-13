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
 * 07/2017  G. Lucas     Refactored to better support constrained regions
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Defines the interface for constraints that can be added to
 * instances of the Incremental TIN classes.
 * <p><strong>About Constrained Regions</strong><br>
 * In plane geometry, a simple non-self-intersecting polygon divides the
 * plane into two disjoint regions. So a polygon constraint has the feature
 * that it defines regions.  On the other hand, a finite linear constraint
 * does not exhibit this feature.
 * <p>When one or more polygon constraints are added to a TIN,
 * Tinfour implements a special behavior in which any ordinary edges falling
 * in the interior of the associated regions are marked as being members of a
 * constrained region.  Here, the word "interior" actually means the
 * region of the plane lying to the left of an polygon edge.  So for a polygon
 * given as a simple loop of vertices taken in counterclockwise order,
 * the left side of each edge is to the inside of the loop and thus the
 * usage of the word "interior" agrees with the ordinary idea of
 * interior being the region inside the polygon.  However, if the polygon were
 * taken in clockwise order, the left side of each edge would be to the
 * outside of the polygon. So the polygon would essentially represent a
 * "hole" in a constrained region.
 * <p>
 * The edges associated with a constrained region fall into 3 categories:
 * <ul>
 * <li><strong>Border</strong> Edges on the border of the constrained region.
 * These edges are constrained and are defined by the polygon constraint.</li>
 * <li><strong>Interior</strong> Edges that lie within the constrained
 * region. The edges are not constrained, but are marked as
 * interior members due to the fact that they lie within the polygon region.</li>
 * <li><strong>Member</strong> Both border and interior edges are classified
 * as members</li>
 * </ul>
 * <p>
 * An interior edge is populated with the constraint-index of the constraint.
 * Because interior edges are unambiguously members of a single constrained
 * region, the index can be used to trace the edge back to its containing
 * constraint instance. It is possible for two adjacent polygons to share
 * common border edges. In such cases, the edge preserves the index of only one
 * of the constraint polygons. Thus the mapping from border edge to constraint
 * is ambiguous.
 * <p>
 * Tinfour allows non-polygon constraints to be specified with a geometry
 * that lines in the interior of a constrained region polygon. In such cases,
 * the edge-marking operation does not mark the constrained edges from
 * the linear constraints as being members of the polygon.  Instead, the
 * operation simply passes over them. A real-world example might include a
 * road (a linear constraint) passing through a forested area (a polygon
 * constraint).  Edges derived from a linear constraint maintain the
 * index of the constraint that specified them.
 *
 *
 */
public interface IConstraint extends IPolyline {

  /**
   * Indicates whether the constraint applies a constrained region behavior
   * when added to a TIN.
   *
   * @return true if the constraint is a data-region definition; otherwise
   * false.
   */
  boolean definesConstrainedRegion();

  /**
   * Permits an application to add data elements to the constraint for
   * its own uses. The reference stored in this instance is not accessed by
   * the Tinfour classes.
   *
   * @param object an object or null according to the needs of the
   * calling application.
   */
  void setApplicationData(Object object);

  /**
   * Gets the application data (if any) stored in the constraint.
   * The reference stored in this instance is not accessed by
   * the Tinfour classes.
   *
   * @return an object or null according to the needs of the
   * calling application.
   */
  Object getApplicationData();


  /**
   * Gets an index value used for internal bookkeeping by Tinfour code.
   * The index value is assigned to a constraint when it is inserted into
   * a Tinfour IIncrementalTin implementation. If an application used a
   * getConstraints() call to get a list of the constraints stored in
   * an IIncrementalTin, the constraint index can be used to get the
   * constraint from that list.
   *
   * @return the index of the constraint associated with the edge;
   * undefined if the edge is not constrained or an interior member
   * of a constrained region.
   */
  int getConstraintIndex();

  IQuadEdge getConstraintLinkingEdge();

  /**
   * Gets a new constraint that has the attributes of this constraint
   * and the specified geometry.  This method is primarily used in cases
   * where a geometry is very similar (or identical) to the input but either
   * simplified or with replacement vertices.
   * @param geometry a valid set of vertices.
   * @return a new constraint.
   */
  IConstraint getConstraintWithNewGeometry(List<Vertex> geometry);

  /**
   * Sets an index value used for internal bookkeeping by Tinfour code;
   * not intended for use by application code. Application code
   * that sets a constraint index runs the risk of damaging the
   * internal data relations maintained by Tinfour.
   *
   * @param tin the IIncrementalTin instance to while this constraint has been
   * added (or null if not applicable).
   * @param index a positive integer.
   */
  void setConstraintIndex(IIncrementalTin tin, int index);

    /**
   * Sets a reference to an arbitrarily selected edge that was produced
   * when the constraint was added to a TIN.  In effect, this reference
   * links the constraint to the TIN. In the case of constraints
   * that define a constrained region,
   * the edge will be the interior side of the edge.
   * <p>
   * This method is not intended for use by application code. Application code
   * that sets a constraint index runs the risk of damaging the
   * internal data relations maintained by Tinfour.
   *
   * @param  linkingEdge a valid edge reference
   */
  void setConstraintLinkingEdge(IQuadEdge linkingEdge);


  /**
   * Gets the instance of the incremental TIN interface that
   * is managing this constraint, if any.
   * @return if under management, a valid instance; otherwise, a null.
   */
  IIncrementalTin getManagingTin();


  /**
   * Indicates if a point at the specified coordinates is unambiguously
   * inside the constraint.  Points on the constraint border are not treated
   * as inside the constraint.
   * <p>
   * This method will only return true for polygon constraints.  For all other
   * constraint implementations, it will return a value of false.
   * @param x the Cartesian coordinate for the point
   * @param y the Cartesian coordinate for the point
   * @return true if the point is in the interior of the constraint.
   */
  boolean isPointInsideConstraint(double x, double y);



  /**
   * Gets a Java Path2D based on the geometry of the constraint mapped through
   * an optional affine transform.
   *
   * @param transform a valid transform, or the null to use the identity
   * transform.
   * @return a valid instance of a Java Path2D
   */
  Path2D getPath2D(AffineTransform transform);


  /**
   * Insert synthetic vertices into the constraint so that the spacing
   * between vertices does not exceed the specified threshold. Z values
   * for the inserted vertices are computed using a linear interpolation.
   * <p>
   * This method is intended to aid in analysis and computation. The vertices
   * that are inserted are to be marked as synthetic to indicate that they
   * are not source data.
   * @param threshold the spacing threshold.
   */
  void densify(double threshold);
}
