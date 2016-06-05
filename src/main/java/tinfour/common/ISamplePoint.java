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


/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name        Description
 * ------   ---------   -------------------------------------------------
 * 12/2014  G. Lucas    Created
 *
 *--------------------------------------------------------------------------
 */
package tinfour.common;

/**
 * Defines a sample point interface to be used for spatial
 * data analysis.
 */
public interface ISamplePoint
{

    /**
     * Get the X coordinate of the sample point
     *
     * @return a valid floating-point value
     */
    double getX();

    /**
     * Get the Y coordinate of the sample point
     *
     * @return a valid floating-point value
     */
    double getY();

    /**
     * Get the Z coordinate of the sample point
     *
     * @return a valid floating point value
     */
    double getZ();

    /**
     * Get the square of the distance to the specified coordinates
     *
     * @param x X coordinate for distance calculation
     * @param y Y coordinate for distance calculation
     * @return a positive floating-point value
     */
    double getDistanceSq(double x, double y);
}
