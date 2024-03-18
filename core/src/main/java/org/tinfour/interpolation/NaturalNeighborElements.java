/* --------------------------------------------------------------------
 * Copyright (C) 2022  Gary W. Lucas.
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
 * 08/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.interpolation;

import org.tinfour.common.Vertex;

/**
 * Provides a simple container for the component elements computed
 * during a natural neighbor interpolation. This class is intended to
 * support research and experimentation. This class is not used as part
 * of the internal operations in the NaturalNeighborInterpolator
 * implementation.
 * <h1> Interpreting the results</h1>
 * If the query is successful, the result type element will be set to
 * SUCCESS.  In that case, the interpolated value can be computed as shown
 * in the following example code.  This logic is similar to what is
 * used internally by the interpolator.
 *
 * <pre>
 *
 *   NaturalNeighborInterpolator nni;  //  defined by application code
 *   NaturalNeighborElements result = nni.getNaturalNeighborElements(x, y);
 *   if(result.getResultType() == ResultType.SUCCESS){
 *      // the weight and neighbor arrays will be of the same length
 *      double []weight   = result.getSibsonCoordinates();
 *      Vertex []neighbor = result.getNaturalNeighbors();
 *      double zSum = 0;
 *      for(int i=0; i&lt;weight.length; i++){
 *         zSum += weight[i]*neighbor[i].getZ();
 *      }
 *      // zSum is the interpolated value
 *    }
 * </pre>
 *
 */
@SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
public class NaturalNeighborElements {

  /**
   * Indicate the kind of results that are stored in this instance.
   */
  public enum ResultType {
    /**
     * Indicates that the interpolation was successful and all member elements
     * are populated
     */
    SUCCESS,
    /**
     * Indicates that the query point was co-located with one of
     * the defining vertices in the data set.  Although this result represents
     * a successful query, there is no meaningful assignments to the set
     * of natural neighbors.  Instead, the co-located vertex is stored in
     * the result. The array of natural neighbors
     * will contain exactly one element and the corresponding array of weights
     * will include one element with a value of 1.0
     */
    COLOCATION,
    /**
     * Indicates that the query point was located on or outside the
     * boundary of the underlying Delaunay triangulation.  The arrays of
     * natural neighbors and weights will be dimensioned to a size of zero.
     * This behavior may be subject to change in the future in order to
     * support extrapolation.
     */
    EXTERIOR;
  }

  /**
   * The result for the interpolation that produced this instance.
   */
  ResultType resultType;

  /**
   * The Cartesian x coordinate for the query that produced this instance.
   */
  public double x;
  /**
   * The Cartesian y coordinate for the query that produced this instance.
   */
  public double y;

  /**
   * The area of the polygon that would be constructed if the query coordinates
   * were integrated into Delaunay Triangulation or Voronoi Diagram.
   */
  double areaOfEmbeddedPolygon;

  /**
   * The Sibson coordinates (vertex weights) for the natural neighbors.
   * If defined, the sum of the lambdas will be 1.
   */
  double[] lambda;

  /**
   * The natural neighbors associated with the query position.
   */
  Vertex[] neighbors;


  /**
   * Constructs a result indicating that the query point was either
   * exterior to, or on the boundary of, the Delaunay triangulation.
   * @param x the Cartesian coordinate of the query
   * @param y the Cartesian coordinate of the query
   */
  NaturalNeighborElements(double x, double y){
    this.resultType = ResultType.EXTERIOR;
    this.x = x;
    this.y = y;
    areaOfEmbeddedPolygon = 0;
    lambda = new double[0];
    neighbors = new Vertex[0];
  }


  /**
   * Constructs a result indicating a successful query.  The specified
   * coordinates were located in the interior of the Delaunay triangulation.
   * @param x the Cartesian coordinate of the query
   * @param y the Cartesian coordinate of the query
   * @param lambda the Sibson coordinates (weights) for the natural neighbors
   * @param neighbors the natural neighbors
   * @param areaOfEmbeddedPolygon the area of the polygon that would be formed
   * if a point with the specified coordinates were inserted into the structure.
   */
  public NaturalNeighborElements(double x, double y, double[] lambda, Vertex[] neighbors, double areaOfEmbeddedPolygon) {
    this.resultType = ResultType.SUCCESS;
    this.x = x;
    this.y = y;
    this.areaOfEmbeddedPolygon = areaOfEmbeddedPolygon;
    this.lambda = lambda;
    this.neighbors = neighbors;
  }

   /**
   * Constructs a result indicating that the query was co-located with
   * a vertex in the Delaunay triangulation.
   * @param x the Cartesian coordinate of the query
   * @param y the Cartesian coordinate of the query
   * @param neighbor the vertex co-located with the query coordinates
   */
  NaturalNeighborElements(double x, double y, Vertex neighbor){
   this.resultType = ResultType.COLOCATION;
    this.x = x;
    this.y =y;
    this.areaOfEmbeddedPolygon = 0;
    this.lambda = new double[]{1.0};
    this.neighbors = new Vertex[]{neighbor};
  }

  /**
   * Gets the type of result that produced this instance.
   * @return a valid enumeration instance.
   */
  public ResultType getResultType(){
    return resultType;
  }

  /**
   * Gets the weights that were computed for the neighboring vertices
   * during the interpolation operation that produced these results.
   * For performance reasons, this method returns a direct reference to
   * the member elements of this class, not a safe-copy of the array.
   * <p>
   * If the query point point was not inside the polygon,
   * this method will return an empty, zero-sized array.  If the point is on the
   * perimeter of the polygon, this method will also return an empty array.
   * <p>
   * If the query point point was co-located or nearly co-located with a
   * vertex in the underlying Delaunay triangulation, this method will return
   * an array of size 1.
   *
   * @return a valid array, potentially of length zero if interpolation
   * was unsuccessful.
   */
  public double[] getSibsonCoordinates() {
    return lambda;
  }

  /**
   * Gets the set of natural neighbors that were identified for the
   * interpolation that produced these results.
   * For performance reasons, this method returns a direct reference to
   * the member elements of this class, not a safe-copy of the array.
   * <p>
   * If the query point point was not inside the polygon,
   * this method will return an empty, zero-sized array.  If the point is on the
   * perimeter of the polygon, this method will also return an empty array.
   * <p>
   * If the query point point was co-located or nearly co-located with a
   * vertex in the underlying Delaunay triangulation, this method will return
   * an array of size 1.
   *
   * @return a valid array, potentially of length zero if interpolation
   * was unsuccessful.
   */
  public Vertex[] getNaturalNeighbors() {
    return neighbors;
  }

  /**
   * Gets the Cartesian x coordinate that was specified for the interpolation
   *
   * @return a floating-point value
   */
  public double getX() {
    return x;
  }

  /**
   * Gets the Cartesian y coordinate that was specified for the interpolation
   *
   * @return a floating-point value
   */
  public double getY() {
    return x;
  }

  /**
   * Gets the area of the containing envelope from which natural
   * neighbor coordinates were derived.  This value is the overall
   * area of the polygon defined by the set of natural neighbors.
   * @return a positive, finite floating-point value.
   */
  public double getAreaOfEnvelope(){
    double areaSum = 0;
    if (neighbors.length > 2) {
      Vertex a = neighbors[neighbors.length - 1];
      for (Vertex b : neighbors) {
        double aX = a.getX() - x;
        double aY = a.getY() - y;
        double bX = b.getX() - x;
        double bY = b.getY() - y;
        areaSum += aX * bY - aY * bX;
        a = b;
      }
    }
    return Math.abs(areaSum/2.0);
  }

  /**
   * Gets the area of the embedded polygon that was calculated when the
   * natural neighbor elements were constructed.
   * @return a valid floating-point number.
   */
  public double getAreaOfEmbeddedPolygon(){
    return areaOfEmbeddedPolygon;
  }

  /**
   * Gets a count for the number of elements (neighbors, Sibson coordinates).
   * @return a positive integer, zero if undefined.
   */
  public int getElementCount(){
    return neighbors.length;
  }
}
