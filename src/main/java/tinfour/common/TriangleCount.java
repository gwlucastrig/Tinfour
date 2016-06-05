/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 08/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Provides descriptive data for a Triangulated Irregular Network (TIN).
 */
public class TriangleCount {

    private final int count;
    private final double sumArea;
    private final double sumArea2;
    private final double minArea;
    private final double maxArea;

    /**
     * Construct a triangle count instance to store results from
     * a TIN evaluation.
     * @param count the number of triangles in the TIN
     * @param sumArea the total area of the triangles in the TIN
     * @param sumArea2 the sum of the squares of the area of the triangles
     * in the TIN (used for computing variance, etc.)
     * @param minArea the minimum area of the triangles in the TIN
     * @param maxArea  the maximum area of the triangles in the TIN
     */
    public TriangleCount(int count, double sumArea, double sumArea2, double minArea, double maxArea) {
        this.count = count;
        this.sumArea = sumArea;
        this.sumArea2 = sumArea2;
        this.minArea = minArea;
        this.maxArea = maxArea;
    }

    /**
     * Get the number of triangles in the TIN.
     *
     * @return a integer value of 1 or more (zero if TIN is undefined).
     */
    public int getCount() {
        return count;
    }

    /**
     * Gets the sum of the area of all triangles in the TIN.
     *
     * @return if the TIN is defined, a i floating point va
     *
     */
    public double getAreaSum() {
        return sumArea;
    }

    /**
     * Get the mean area of the triangles in the TIN.
     *
     * @return if the TIN is defined, a positive floating point value.
     */
    public double getAreaMean() {
        if (count == 0) {
            return 0;
        }
        return sumArea / count;
    }

    /**
     * Gets the standard deviation of the triangles in the TIN.
     *
     * @return if the TIN is defined, a positive floating point value.
     */
    public double getAreaStandardDeviation() {
        if (count < 2) {
            return 0;
        }
        double n = count; // use double to avoid int overflow
        double s = n * sumArea2 - sumArea * sumArea;
        double t = n * (n - 1);
        return Math.sqrt(s / t);
    }

    /**
     * Gets the minimum area of the triangles in the TIN.
     *
     * @return if the TIN is defined, a positive floating point value.
     */
    public double getAreaMin() {
        return minArea;
    }

    /**
     * Gets the maximum area of the triangles in the TIN.
     *
     * @return if the TIN is defined, a positive floating point value.
     */
    public double getAreaMax() {
        return maxArea;
    }
}
