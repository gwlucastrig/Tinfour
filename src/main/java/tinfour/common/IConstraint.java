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
package tinfour.common;

/**
 * Defines the interface for constraints that can be added to
 * instances of the Incremental TIN classes.
 * <p><strong>About Constrained Regions</strong><br>
 * In plane geometry, a simple, non-self-intersecting polygon divides the
 * plane into two disjoint regions. So a polygon constraint has the feature
 * that it defines regions.  On the other hand, a finite linear constraint
 * does not exhibit this feature.
 * <p>When one or more polygon constraints are added to a TIN,
 * Tinfour implements a special behavior in which any edges falling
 * in the interior of the associated regions are marked as being members of a
 * constrained region.  Here, the word "interior" actually means the
 * region of the plane lying to the left of an edge.  So for a polygon
 * given as a simple loop of vertices taken in counterclockwise order,
 * the left side of each edge is to the inside of the loop and thus the
 * usage of the word "interior" agrees with the ordinary idea of
 * interior being the region inside the polygon.  However, if the polygon were
 * taken in clockwise order, the left side of each edge would be to the
 * outside of the polygon.
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
  public boolean definesConstrainedRegion();

  /**
   * Permits an application to add data elements to the constraint for
   * its own uses. The reference stored in this instance is not accessed by
   * the Tinfour classes.
   *
   * @param object an object or null according to the needs of the
   * calling application.
   */
  public void setApplicationData(Object object);

  /**
   * Gets the application data (if any) stored in the constraint.
   * The reference stored in this instance is not accessed by
   * the Tinfour classes.
   *
   * @return an object or null according to the needs of the
   * calling application.
   */
  public Object getApplicationData();

  /**
   * Sets an index value used for internal bookkeeping by Tinfour code;
   * not intended for use by application code. Application code
   * that sets a constraint index runs the risk of damaging the
   * internal data relations maintained by Tinfour.
   *
   * @param index a positive integer.
   */
  public void setConstraintIndex(int index);

  /**
   * Gets an index value used for internal bookkeeping by Tinfour code;
   * not intended for use by application code.
   *
   * @return the index of the constraint associated with the edge;
   * undefined if the edge is not constrained or a member of a constrained
   * region.
   */
  public int getConstraintIndex();


  /**
   * Gets a new constraint that has the attributes of this contraint
   * and the specified geometry.  This method is primarily used in cases
   * where a geometry is very similar (or identical) to the input but either
   * simplified or with replacement vertices.
   * @param geometry a valid set of vertices.
   * @return a new constraint.
   */
  public IConstraint getConstraintWithNewGeometry(Iterable<Vertex> geometry);

}
