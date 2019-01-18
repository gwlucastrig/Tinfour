/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
 * 05/2016  G. Lucas     Added the getEdgeWithNearestVertex method
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.common;

/**
 * Defines methods for a reusable instance of a class for  searching a TIN and
 * locating the neighboring edge. Intended for interpolation actions
 * and similar applications. Note that many instances of this class
 * are more efficient if they are reused over a stable TIN,
 * but most be reset when the TIN changes (see IProcessUsingTin for
 * more explanation).
 */
public interface INeighborEdgeLocator extends IProcessUsingTin{

  /**
   * Gets a neighboring edge for the coordinates. If the coordinates
   * are exterior to the tin, gets the outside reference of the
   * nearest edge
   * @param x a valid Cartesian coordinate consistent with the TIN
   * @param y a valid Cartesian coordinate consistent with the TIN
   * @return A valid edge instance.
   */
  IQuadEdge getNeigborEdge(double x, double y);


  /**
   * Locates the edge which begins with the vertex closest to the
   * query coordinates (x,y). If the query point is to the interior
   * of the TIN, it will be either on the indicated edge or inside
   * the triangle that lies to the left of the edge. If the query point
   * is to the exterior of the TIN, the neighboring edge may
   * have an undefined geometry (e.g. it may be a a "ghost edge").
   * <p>
   * Note that the edge included in the result is not necessarily
   * the <strong>edge</strong> that lies closest to the query point.
   * One of the other edges in the containing triangle may actually be
   * closer to the query point. This method is concerned with the
   * nearness of the starting vertex of the edge, not the edge itself.
   * However, one of the edges in the containing triangle will, indeed,
   * be the closest edge.
   *
   * @param x the X coordinate of the query point
   * @param y the Y coordinate of the query point
   * @return a valid instance, or a null if the TIN is not bootstrapped.
   */
  NeighborEdgeVertex getEdgeWithNearestVertex(double x, double y);

}
