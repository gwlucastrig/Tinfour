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
 * 09/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

 

/**
 * Provides utilities for performing geometry-based query operations
 * on an incremental tin. 
 */
public interface IIncrementalTinNavigator extends IProcessUsingTin {

  /**
   * Gets a triangle from the TIN that contains the specified coordinate
   * point (if any).  If the coordinates lie outside the bounds of the TIN,
   * this method returns a null.
   * @param x a valid Cartesian coordinate
   * @param y a valid Cartesian coordinate
   * @return if successful, a valid triangle instance; otherwise, a null.
   */
  SimpleTriangle getContainingTriangle(double x, double y);

  /**
   * Gets nearest edge to the specified coordinate point. If the coordinates
   * are exterior to the tin, gets the outside element of the nearest
   * perimeter edge.   This method is slightly more costly than the
   * getNeighborEdge() method (see below), but it provides more information.
   * @param x a valid Cartesian coordinate 
   * @param y a valid Cartesian coordinate 
   * @return if the TIN is properly initialized, a valid edge instance;
   * otherwise, a null.
   */
  NearestEdgeResult getNearestEdge(double x, double y);

  /**
   * Gets the nearest vertex to the specified coordinates
   * @param x a valid Cartesian coordinate 
   * @param y a valid Cartesian coordinate 
   * @return if the TIN is properly initialized, a valid vertex instance;
   * otherwise, a null.
   */
  Vertex getNearestVertex(double x, double y);

  
    /**
   * Gets a neighboring edge for the coordinates. If the coordinates
   * are exterior to the tin, gets the outside element of the nearest
   * perimeter edge.   The <strong>neighbor</strong> edge is not
   * necessarily the <strong>nearest</strong> edge to the query point.
   * If the query point is inside the bounds of the TIN, the neighbor
   * edge will be any of the three edges from the triangle that contains
   * the specified coordinate point.
   * @param x a valid Cartesian coordinate 
   * @param y a valid Cartesian coordinate 
   * @return if the TIN is properly initialized, a valid edge instance;
   * otherwise, a null.
   */
  IQuadEdge getNeighborEdge(double x, double y);

  
    /**
   * Determines whether the point is inside the convex polygon boundary
   * of the TIN. If the TIN is not bootstrapped, this method will
   * return a value of false.
   *
   * @param x The x coordinate of interest
   * @param y THe y coordinate of interest
   * @return true if the coordinates identify a point inside the
   * boundary of the TIN; otherwise, false.
   */
  boolean isPointInsideTin(double x, double y);

     /**
     * Reset the navigator due to a change in the TIN.  This call is important
     * because the navigators maintain state data about the TIN in order
     * to expedite queries.  If the TIN structure changes, that state data,
     * is rendered obsolete and may cause serious error results.
     * For reasons of efficiency, the Tinfour library offers no protection
     * from changes to the TIN. So it is important that an application
     * manage this issue correctly.
     * <p>
     * Resetting the state data unnecessarily may result in a
     * performance reduction when processing a large number of operations,
     * but is otherwise harmless.  
     */
  @Override
  void resetForChangeToTin();
  
}
