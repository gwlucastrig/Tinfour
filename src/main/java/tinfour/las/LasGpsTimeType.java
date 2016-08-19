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
package tinfour.las;

import java.util.Date;

/**
 * Indicates which format was used to represent GPS time
 * values associated with the sample points in an file.
 */
public enum LasGpsTimeType {

  /**
   * Time value in seconds measured from the start of the week
   * at midnight Sunday morning UTC.
   */
  WeekTime,
  /**
   * Time value in seconds measured from the start of the epoch
   * at midnight, Sunday morning, January 6, 1980 and adjusted by
   * subtracting the offset 1.0e+9. This offset is used by the LAS
   * format to reduce the magnitude of the time and preserve
   * the precision of lower-order digits.
   */
  SatelliteTime;

  /**
   * Specifies the time <strong>in seconds</strong> for the GPS EPOCH
   * which is defined as 1980-01-06 00:00:00 UTC. Seconds are used rather than
   * Java milliseconds to facilitate calculations of the GPS time value without
   * losing precision.
   * <p>
   * The GPS Epoch was computed using the following code
   * <pre>
   * GregorianCalendar c=new GregorianCalendar(new SimpleTimeZone(0,"UTC"));
   * c.clear();
   * c.set(1980, 0, 6, 0, 0, 0);
   * Date d = c.getTime();
   * long t = c.getTimeInMillis();
   * </pre>
   * If you work with calendar, do not forget the call to clear. The
   * Java calendar class is a truly awful implementation and
   * one of its many shortcomings is that if you
   * do not call clear it will populate the milliseconds element with
   * the current system clock and the call to set() does not zero-out that
   * field.
   */
  public static final long GPS_EPOCH_SECONDS = 315964800000L;

  /**
   * Converts a LAS GPS time given from the representation
   * specified by this enumeration state to a Java time
   * in milliseconds from the EPOCH Jan 1, 1970.
   * <strong>Note:</strong> This class does not yet account for
   * GPS leap seconds. Further research is required to resolve this issue.
   *
   * @param lasGpsTime a valid LAS-formatted GPS Time in the same
   * representation as this enumeration.
   * @return a time in milliseconds
   */
  public long transformGpsTimeToMillis(double lasGpsTime) {
    // using the floor will ensure negative values are handled correctly.
    long f = (long) Math.floor(lasGpsTime);
    double d = lasGpsTime - (double) f;
    if (this == LasGpsTimeType.SatelliteTime) {
      double gpsTime = Math.floor(lasGpsTime)+1.0e+9;
      long s = GpsTimeConverter.gpsToMillis(gpsTime);
      return s + (long) (d * 1000 + 0.5);
    } else {
      // week time
      return f * 1000 + (long) (d * 1000 + 0.5);
    }
  }

  /**
   * Converts a LAS GPS time given from the representation
   * specified by this enumeration to a valid Java Date instance.
   * <p>
   * <strong>Note:</strong> This class does not yet account for
   * GPS leap seconds. Further research is required to resolve this issue.
   *
   * @param lasGpsTime a valid LAS-formatted GPS Time in the same
   * representation as this enumeration.
   * @return a valid Date instance
   */
  public Date transformGpsTimeToDate(double lasGpsTime) {
    return new Date(transformGpsTimeToMillis(lasGpsTime));
  }

}
