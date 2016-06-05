/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.common;

import java.util.List;

/**
 * Defines an interface for collecting the points in the
 * proximity of a specified pair of coordinates.
 */
public interface INeighborhoodPointsCollector extends IProcessUsingTin {

  /**
   * Gets the points in the neighborhood of a pair of query coordinates.
   * Points are collected by identifying the triangle that contains the
   * query coordinates, then recursively traversing to its neighbors
   * to identify nearby points. Unlike the conventional notion
   * of collecting "points within a distance", this method collects
   * "points within a designated number of transitions" from the
   * query point to adjacent triangles in the mesh. A depth of 1 will
   * include vertices from the triangles adjacent to the containing
   * triangle. A depth of 2 searches the neighbors to those triangles,
   * etc. The number of transitions is specified as the "search depth"
   * argument to this method.
   * <p>
   * By design, the first three points in the result are always the
   * vertices of the triangle that contains the query coordinates.
   * While these vertices are not necessarily ordered by distance, they
   * are guaranteed to be the nearest three vertices to the query point.
   * This requirement is also supported in the special case where the query
   * point lies exactly on the edge of a triangle. In the special case that
   * (x,y) is within the TIN distance tolerance for treating points as
   * non-unique, the matching vertex will be the first vertex in the list.
   * <p>
   * When the search is performed on the interior of a very large TIN,
   * the number of points found is roughly equivalent to the
   * square of depth of the search. When a search crosses the exterior
   * edge of the TIN , fewer points may be available.
   * The following table indicates the number of points expected in a search.
   * Note that a depth of zero returns the three vertices of the triangle
   * containing the search coordinates
   * <pre>
   *   depth  count
   *   0          3
   *   1          6
   *   2         11
   *   3         19
   *   4         25
   *   5         38
   *   6         47
   *   7         63
   *   8         78
   *   9         97
   *  10        116
   * </pre>
   * The application can also specify a minimum number of vertices
   * to be found. If the minimum value is non-zero, this method will
   * attempt to find at least the number of vertices specified.
   * If it cannot do so by apply the specified search depth, it will
   * extend to search further out from the initial triangle.
   * This process is expensive because in order to extend the search
   * further out, the method must first traverse the part of the TIN
   * already covered.
   *
   * @param x x coordinate of point of interest for vertex collection
   * @param y y coordinate of point of interest for vertex collection
   * @param searchDepth target depth of traversal for the recursive
   * transition to neighbor triangles.
   * @param targetMinVertexCount specifies a target value for the minimum
   * number of vertices to be collected, if available in TIN.
   * @return a valid List of vertices.
   */
  List<Vertex> collectNeighboringVertices(double x, double y, int searchDepth, int targetMinVertexCount);

  /**
   * Gets the max depth searched. Set by the most recent call to
   * getPoints(). In general, this value is meaningful
   * only when the test requires an extended search in order to meet
   * the getPoints method's targetMinvVertexCount.
   *
   * @return a positive integer.
   */
  int getMaxDepthSearched();


  /**
   * Indicates that the most recent target coordinates were
   * exterior to the TIN. Set by the most recent call to getPoints().
   *
   * @return true if the most recent coordinates were exterior to the
   * TIN; otherwise, false.
   */
  boolean wasTargetExteriorToTin();

}
