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
 * Provides an enumeration providing GeoTIFF code values for
 * specified keys.
 */
public enum GeoKey{
      // From 6.2.1 GeoTiff Configuration Keys
        GTModelTypeGeoKey(             1024), /* Section 6.3.1.1 Codes       */
        GTRasterTypeGeoKey(            1025), /* Section 6.3.1.2 Codes       */
        GTCitationGeoKey(              1026), /* documentation */

        // From 6.2.2 Geographic Coordinate System Parameter Keys
        GeographicTypeGeoKey(          2048), /* Section 6.3.2.1 Codes     */
        GeogCitationGeoKey(            2049), /* documentation             */
        GeogGeodeticDatumGeoKey(       2050), /* Section 6.3.2.2 Codes     */
        GeogPrimeMeridianGeoKey(       2051), /* Section 6.3.2.4 codes     */
        GeogLinearUnitsGeoKey(         2052), /* Section 6.3.1.3 Codes     */
        GeogLinearUnitSizeGeoKey(      2053), /* meters                    */
        GeogAngularUnitsGeoKey(        2054), /* Section 6.3.1.4 Codes     */
        GeogAngularUnitSizeGeoKey(     2055), /* radians                   */
        GeogEllipsoidGeoKey(           2056), /* Section 6.3.2.3 Codes     */
        GeogSemiMajorAxisGeoKey(       2057), /* GeogLinearUnits           */
        GeogSemiMinorAxisGeoKey(       2058), /* GeogLinearUnits           */
        GeogInvFlatteningGeoKey(       2059), /* ratio                     */
        GeogAzimuthUnitsGeoKey(        2060), /* Section 6.3.1.4 Codes     */
        GeogPrimeMeridianLongGeoKey(   2061), /* GeogAngularUnit           */

        // From 6.2.3 Projected Coordinate System Parameter Keys
        ProjectedCRSGeoKey(              3072),  /* Section 6.3.3.1 codes   */
        PCSCitationGeoKey(               3073),  /* documentation           */
        ProjectionGeoKey(                3074),  /* Section 6.3.3.2 codes   */
        ProjCoordTransGeoKey(            3075),  /* Section 6.3.3.3 codes   */
        ProjLinearUnitsGeoKey(           3076),  /* Section 6.3.1.3 codes   */
        ProjLinearUnitSizeGeoKey(        3077),  /* meters                  */
        ProjStdParallel1GeoKey(          3078),  /* GeogAngularUnit */
        ProjStdParallel2GeoKey(          3079),  /* GeogAngularUnit */
        ProjNatOriginLongGeoKey(         3080),  /* GeogAngularUnit */
        ProjNatOriginLatGeoKey(          3081),  /* GeogAngularUnit */
        ProjFalseEastingGeoKey(          3082),  /* ProjLinearUnits */
        ProjFalseNorthingGeoKey(         3083),  /* ProjLinearUnits */
        ProjFalseOriginLongGeoKey(       3084),  /* GeogAngularUnit */
        ProjFalseOriginLatGeoKey(        3085),  /* GeogAngularUnit */
        ProjFalseOriginEastingGeoKey(    3086),  /* ProjLinearUnits */
        ProjFalseOriginNorthingGeoKey(   3087),  /* ProjLinearUnits */
        ProjCenterLongGeoKey(            3088),  /* GeogAngularUnit */
        ProjCenterLatGeoKey(             3089),  /* GeogAngularUnit */
        ProjCenterEastingGeoKey(         3090),  /* ProjLinearUnits */
        ProjCenterNorthingGeoKey(        3091),  /* ProjLinearUnits */
        ProjScaleAtNatOriginGeoKey(      3092),  /* ratio   */
        ProjScaleAtCenterGeoKey(         3093),  /* ratio   */
        ProjAzimuthAngleGeoKey(          3094),  /* GeogAzimuthUnit */
        ProjStraightVertPoleLongGeoKey(  3095),  /* GeogAngularUnit */
        // From 6.2.4 Vertical Coordinate System Keys
        VerticalCSTypeGeoKey(            4096),   /* Section 6.3.4.1 codes   */
        VerticalCitationGeoKey(          4097),   /* documentation */
        VerticalDatumGeoKey(             4098),   /* Section 6.3.4.2 codes   */
        VerticalUnitsGeoKey(             4099),   /* Section 6.3.1.3 codes   */

        // Widely used key not defined in original specification
        To_WGS84_GeoKey(                 2062);   /* Not in original spec */

        int key;
        GeoKey(final int key) {
            this.key = key;
        }

        /**
         * Gets the GeoTIFF key code associated with the enumeration.
         * @return a valid integer in the range of an unsigned short,
         * not equal to zero.
         */
        public int getKeyCode(){
          return key;
        }
    }
