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
 * 05/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Provides a minimal set of data elements for the result of a neighbor-edge
 * location operation.
 */
public class NeighborEdgeVertex
{

    final IQuadEdge edge;
    final double x;
    final double y;
    final double d;
    final boolean interior;

    /**
     * Standard constructor.
     *
     * @param edge the edge that starts with the vertex nearest (x,y)
     * @param d the distance of the vertex to (x,y)
     * @param x the X coordinate for the query point
     * @param y the Y coordinate for the query point
     * @param interior indicates that the query point is inside the TIN
     * boundary,
     */
    public NeighborEdgeVertex(
            IQuadEdge edge, double d, double x, double y, boolean interior)
    {
        this.edge = edge;
        this.d = d;
        this.x = x;
        this.y = y;
        this.interior = interior;
    }

    /**
     * Gets the edge that begins with the vertex closest to the query
     * coordinates. If the query point is in the interior of the TIN, it will
     * lie either on the edge or inside the triangle to the left of the edge. If
     * the query point lies to the exterior, it is possible that the associated
     * edge may have an undefined termination (e.g. it may be a "ghost edge") so
     * that its second vertex is null.
     *
     * @return a valid edge.
     */
    public IQuadEdge getEdge()
    {
        return edge;
    }

    /**
     * Gets the vertex nearest the query point. This method is the equivalent to
     * a call to getEdge().getA().
     *
     * @return A valid vertex (never null).
     */
    public Vertex getNearestVertex()
    {
        return edge.getA();
    }

    /**
     * Gets the distance from the query point to the nearest vertex.
     *
     * @return a positive floating point value, potentially zero.
     */
    public double getDistance()
    {
        return d;
    }

    /**
     * Get the X coordinate of the query point
     *
     * @return a valid coordinate
     */
    public double getX()
    {
        return x;
    }

    /**
     * Get the Y coordinate of the query point
     *
     * @return a valid coordinate
     */
    public double getY()
    {
        return y;
    }

    /**
     * Indicates whether the query point was inside the
     * convex polygon boundary of the TIN.
     *
     * @return true if the query point was inside the TIN; false otherwise
     */
    public boolean isInterior()
    {
        return interior;
    }

}
