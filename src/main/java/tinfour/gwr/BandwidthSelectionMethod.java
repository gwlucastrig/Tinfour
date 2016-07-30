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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2014  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.gwr;

/**
 * Specify a method for selecting bandwidth for a Geographically
 * Weighted Regression.
 */
public enum BandwidthSelectionMethod {
    /**
     * Perform GWR using a specified bandwidth given in the same
     * units as the horizontal coordinate system.
     *//**
     * Perform GWR using a specified bandwidth given in the same
     * units as the horizontal coordinate system.
     */
    FixedBandwidth,

    /**
     * Perform GWR using a bandwidth that is a fixed proportion of the
     * average distance from the query point for the set of local sample
     * vertices used in the interpolation.
     */
    FixedProportionalBandwidth,

    /**
     * Attempt to automatically select the bandwidth selection
     * using the Akaike Information Criteria (corrected for sample size).
     * This technique performs multiple evaluations of the statistics
     * for the samples in the vicinity of the query point using different
     * bandwidth settings in an attempt to find an optimal fit.
     * Because of the large amount of processing required by this procedure
     * it is thus the slowest the available methods.
     */
    OptimalAICc,

    /**
     * Perform the regression using a uniform weighting for all
     * samples. This method essentially selects an infinite bandwidth.
     * This selection method is intended primarily for testing and
     * software development purposes. The computation used for OLS is
     * inefficient compared to conventional implementations.
     */
    OrdinaryLeastSquares;

}
