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

/**
 * Defines the interface for constraints that can be added to
 * instances of the Incremental TIN classes.
 */
public interface IConstraint extends IPolyline {


  /**
   * Sets the status of a polygon constraint to indicate whether it
   * defines a data area. This method is undefined for non-polygon constraints.
   *
   * @param definesDataArea true if the constraint defines a data area;
   * otherwise false.
   */
  public void setDefinesDataArea(boolean definesDataArea);

  /**
   * Indicates whether the constraint is a data area definition.
   *
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
   * area.
   */
  public int getConstraintIndex();

}
