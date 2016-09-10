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
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.utils;

/**
 * An enumeration for specifying linear units of measure.
 */
public enum LinearUnits {
  UNKNOWN("unknown", 1.0, "Unknown"),
  METERS("m", 1.0, "Meters"),
  FEET("ft", 0.3048, "Feet"),
  FATHOMS("fathoms", 1.8288, "Fathoms");

  final String abbreviation;
  final double metersConversion;
  final String name;

  private LinearUnits(String abbreviation, double metersConversion, String name){
    this.abbreviation = abbreviation;
    this.metersConversion = metersConversion;
    this.name = name;
}

  /**
   * Get the abbreviation for the unit of measure. Where appropriate
   * this will be an SI abbreviation given in lower case.
   * @return a valid string
   */
  public String getAbbreviation(){
    return abbreviation;
  }

  /**
   * Convert the specified value to meters.
   * @param value a valid numeric value in the system of units specified
   * by the enumeration instance
   * @return a valid floating-point value, in meters.
   */
  public double toMeters(double value){
    return value*metersConversion;
  }

  /**
   * Gets the name of the units in a form suitable for user interface display.
   * @return a valid string.
   */
  public String getName(){
    return name;
  }

}
