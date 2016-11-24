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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Defines the interface for constraints that can be added to
 * instances of the Incremental TIN classes.
 */
public interface IConstraint {

  /**
   * Gets the vertices for this constraint. The vertices define a
   * non-self-intersecting chain of line segments (that is, no line segments
   * intersect except at their endpoints). The vertices are assumed to be
   * unique and far enough apart that they are stable in numeric operations.
   *
   * @return a valid list of two or more unique vertices.
   * <p>
   * <strong>Mutability:</strong> When a constraint added to the
   * TIN classes, there are conditions under which vertices may be added
   * or removed from the list. In cases where a constraint segment intersects
   * an existing vertex, the vertex will be inserted into the constraint
   * geometry. In cases where a constraint vertex is identical to an
   * existing vertex, it will be replaced with the merged-vertex group
   * into which the vertex was added.
   */
  List<Vertex> getVertices();

  /**
   * Adds a vertex to the linear constraint
   *
   * @param v a valid instance
   */
  public void add(Vertex v);

  /**
   * Gets the bounds of the constraint.
   * <p>
   * <strong>Caution:</strong> Implementations of this method expose
   * the Rectangle2D object used by the constraint instance.
   * Although this approach supports efficiency
   * for the potentially intense processing conducted by the TIN classes,
   * it does not provide a safe implementation for careless developers.
   * Therefore, applications should manipulate the rectangle instance
   * returned by this routine at any time.
   *
   * @return a valid, potentially empty rectangle instance.
   */
  public Rectangle2D getBounds();

  /**
   * Called to indicate that the constraint is complete and that
   * no further vertices will be added to it. Some implementing classes
   * may perform lightweight sanity checking on the constraint instance.
   * For instance, a polygon implementation may ensure that the polygon defines
   * a closed loop by appending the first vertex in the constraint to the
   * end of its vertex list.
   * <p>
   * Multiple calls to complete are benign and will be ignored.
   * If vertices are added after complete is called, the behavior is undefined.
   */
  public void complete();

  /**
   * Indicates whether the instance represents a polygon.
   * Some implementations may define a constant value for this method,
   * others may determine it dynamically.
   *
   * @return true if the instance is a polygon; otherwise, false.
   */
  public boolean isPolygon();

  /**
   * Sets the status of a polygon constraint to indicate whether it
   * defines a data area. This method is undefined for non-polygon constraints.
   * @param definesDataArea true if the constraint defines a data area;
   * otherwise false.
   */
  public void setDefinesDataArea(boolean definesDataArea);

  /**
   * Indicates whether the constraint is a data area definition.
   * @return true if the constraint is a data-area definition; otherwise
   * false.
   */
  public boolean definesDataArea();


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
   * not intended for use by application code.  Application code
   * that sets a constraint index runs the risk of damaging the
   * internal data relations maintained by Tinfour.
   * @param index a positive integer.
   */
  public void setConstraintIndex(int index);

  /**
   * Gets an index value used for internal bookkeeping by Tinfour code;
   * not intended for use by application code.
   * @return the index of the constraint associated with the edge;
   * undefined if the edge is not constrained or a member of a constrained
   * area.
   */
  public int getConstraintIndex();

}
