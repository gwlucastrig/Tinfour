/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
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
 * 01/2024  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.geotiff;

/**
 * Provides an enumeration for GeoTIFF model types
 */
public enum GtModelType {
       ProjectedCoordinateSystem(1),
       GeographicCoordinateSystem(2),
       GeocentricCoordinateSystem(3);  // geocentric is seldom used.

       int code;
        GtModelType(final int code) {
            this.code = code;
        }

        /**
         * Gets the code associated with the enumeration
         * @return a valid integer
         */
        public int getCode(){
          return code;
        }
}
