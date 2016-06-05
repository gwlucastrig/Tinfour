/*
 * Copyright 2014 Gary W. Lucas.
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
 */


/**
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name        Description
 * ------   ---------   -------------------------------------------------
 * 12/2014  G. Lucas    Created
 *
 *--------------------------------------------------------------------------
 */

package tinfour.gwr;

import tinfour.common.ISamplePoint;

/**
 * Defines a data-container class for holding the results from neighboring point
 * analysis operations.
 */
public class NeighborhoodPoint implements ISamplePoint, Comparable<NeighborhoodPoint>
{

    double distance;
    ISamplePoint point;

    /**
     * Get the distance value associated with the point
     *
     * @return a valid floating-point value
     */
    public double getDistance()
    {
        return distance;
    }

    /**
     * get the sample-point object stored in this instance.
     *
     * @return a valid reference
     */
    public ISamplePoint getPoint()
    {
        return point;
    }

    /**
     * Store the specified point object in this instance and compute and store
     * its distance to a specified coordinate pair.
     *
     * @param point a valid reference
     * @param x the X coordinate of the comparison point
     * @param y the Y coordinate of the comparison point
     * @return the computed distance from the point to the comparison point
     */
    double computeAndSetValues(ISamplePoint point, double x, double y)
    {
        this.point = point;
        double dx = x - point.getX();
        double dy = y - point.getY();
        distance = Math.sqrt(dx * dx + dy * dy);
        return distance;
    }

    /**
     * Set the point and distance elements for this instance.
     *
     * @param point a valid reference
     * @param distance a valid, positive floating-point value.
     */
    void setPointAndDistance(ISamplePoint point, double distance)
    {
        this.point = point;
        this.distance = distance;
    }

    @Override
    public int compareTo(NeighborhoodPoint o)
    {
        return Double.compare(distance, o.distance);
    }

    @Override
    public double getX() {
       return point.getX();
    }

    @Override
    public double getY() {
        return point.getY();
    }

    @Override
    public double getZ() {
         return point.getZ();
    }

    @Override
    public double getDistanceSq(double x, double y) {
        return distance*distance;
    }

}
