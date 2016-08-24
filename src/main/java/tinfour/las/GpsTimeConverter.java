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
 * -----------------------------------------------------------------------
 * Notes:
 *    The source for the leapSeconds table given below is an article
 *    from Wikipedia and should  be verified with a primary source
 *    before this class is given significant use.
 *    See <cite>https://en.wikipedia.org/wiki/Leap_second</cite> for more detail.
 *
 *    The conversions performed by this class were tested using the converter at
 *         https://www.andrews.edu/~tzs/timeconv/timeconvert.php
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

/**
 * Provides methods for converting GPS times to Java's internal
 * milliseconds standard based on epoch 1 January 1970. Java is based
 * on the UTC system which depends on the use of leap seconds
 * which are issued by international committees at irregular intervals.
 * The classic Java Date API does not, itself support leap seconds, but
 * assumes that the length of a day is uniformly 86400 seconds.
 * However, the GPS time is a strictly linear time (adjusted for
 * relativistic effects) that is not not affected by leap second rulings.
 * So, in applications where we wish to be able to determine what time
 * the Java would report at a particular GPS time, we need to apply an
 * adjustment for leap seconds.
 * <p>
 * The GPS to Java time transition is complicated somewhat by the effect that
 * the conversation may actually produce the same Java value for two different
 * GPS times. For example, there was a leap second at the transition
 * from 30 June to 1 July in 2015. Thus the GPS times 1119744016 and
 * 1119744017 both map to 2015-07-01 00:00:00 UTC.
 */
public final class GpsTimeConverter {

  /**
   * A table of leap-second adjustments by year. By convention, adjustments
   * occur at the end of the day on 30 June or 31 December. The columns below
   * give year, 30 June adjustment and 31 December adjustment.
   * <p>
   * The source for this table is an article on Wikipedia and should be
   * verified with a primary source before significant use. See
   * <cite>https://en.wikipedia.org/wiki/Leap_second</cite> for more detail.
   * <p>
   * The current implementation would not handle negative time adjustments
   * correctly.
   */
  private static final short[][] leapSeconds = {
    {1980, 0, 0},
    {1981, +1, 0},
    {1982, +1, 0},
    {1983, +1, 0},
    {1984, 0, 0},
    {1985, +1, 0},
    {1986, 0, 0},
    {1987, 0, +1},
    {1988, 0, 0},
    {1989, 0, +1},
    {1990, 0, +1},
    {1991, 0, 0},
    {1992, +1, 0},
    {1993, +1, 0},
    {1994, +1, 0},
    {1995, 0, +1},
    {1996, 0, 0},
    {1997, +1, 0},
    {1998, 0, +1},
    {1999, 0, 0},
    {2000, 0, 0},
    {2001, 0, 0},
    {2002, 0, 0},
    {2003, 0, 0},
    {2004, 0, 0},
    {2005, 0, +1},
    {2006, 0, 0},
    {2007, 0, 0},
    {2008, 0, +1},
    {2009, 0, 0},
    {2010, 0, 0},
    {2011, 0, 0},
    {2012, +1, 0},
    {2013, 0, 0},
    {2014, 0, 0},
    {2015, +1, 0},
    {2016, 0, 0},};

  private static final long[] linearTime;
  private static final long[] javaTime;

  static {
    int nTimeFixes = 0;
    for (int i = 0; i < leapSeconds.length; i++) {
      if (leapSeconds[i][1] != 0) {
        nTimeFixes += 2;
      }
      if (leapSeconds[i][2] != 0) {
        nTimeFixes += 2;
      }
    }
    linearTime = new long[nTimeFixes + 1];
    javaTime = new long[nTimeFixes + 1];
    GregorianCalendar c = new GregorianCalendar(new SimpleTimeZone(0, "UTC"));
    c.clear();  // absolutely necessary to clear milliseconds field
    c.set(1980, 0, 6, 0, 0, 0);
    long priorTimestamp = c.getTimeInMillis() / 1000L;
    int iAdjustment = 0;
    linearTime[iAdjustment] = 0;
    javaTime[iAdjustment] = priorTimestamp * 1000;
    iAdjustment++;
    long gpsOffset = 0;
    for (int i = 1; i < leapSeconds.length; i++) {
      for (int j = 1; j < 3; j++) {
        // The following code assumes that the leap second
        // adjustments are always positive.
        if (leapSeconds[i][j] != 0) {
          long timestamp;
          c.clear();
          if (j == 1) {
            // June 30/July 1 transistion
            c.set(i + 1980, 6, 1, 0, 0, 0); // July 1
          } else {
            c.set(i + 1981, 0, 1, 0, 0, 0); // Jan 1
          }
          timestamp = c.getTimeInMillis() / 1000L;
          long delta = timestamp - priorTimestamp;
          gpsOffset += delta;
          linearTime[iAdjustment] = gpsOffset;
          javaTime[iAdjustment] = timestamp * 1000L;
          iAdjustment++;
          gpsOffset++;
          linearTime[iAdjustment] = gpsOffset;
          javaTime[iAdjustment] = timestamp * 1000L;
          iAdjustment++;
          priorTimestamp = timestamp;
        }
      }
    }
  }

  /**
   A private constructor to deter application code from creating an
   * instance of this class.
  */
  private GpsTimeConverter() {

  }

  /**
   * Convert a GPS time to milliseconds since the Java epoch 1 January 1970.
   * This value will be the time that would have been presented by a call to
   * the Java System.currentTimeMillis() at the instant indicated by the GPS
   * time.
   * <p>
   * Note that the GPS time is based on the Epoch 6 January 1980 while the
   * milliseconds system is based on the Epoch 1 January 1970. So a GPS
   * time of zero will have value milliseconds value of 315964800000.
   * <p>
   * This routine does not handle leap seconds in the period before 1980,
   * thus negative gpsTime values will not be adjusted for leap seconds.
   *
   * @param gpsTime a valid positive number giving a GPS time in seconds
   * measured from the GPS epoch 6 January 1980.
   * @return a valid Java time in milliseconds
   */
  public static long gpsToMillis(double gpsTime) {
    if (gpsTime < 0) {
      // a negative input is not handled correctly.
      return (long)(javaTime[0]-gpsTime*1000);
    }
    long target = (long) gpsTime;
    long ms = (long) ((gpsTime - Math.floor(gpsTime)) * 1000);
    if (target > linearTime[linearTime.length - 1]) {
      // the target is more recent than the latest information
      // in the table.  Special handling is required.
      long deltaSec = target - linearTime[linearTime.length - 1];
      return javaTime[javaTime.length - 1] + deltaSec * 1000L + ms;
    }
    int index = Arrays.binarySearch(linearTime, target);
    if (index < 0) {
      int i = -index - 1;
      long deltaSec = target - linearTime[i];
      return javaTime[i] + deltaSec * 1000L + ms;
    } else {
      // an exact match
      return javaTime[index] + ms;
    }
  }

  /**
   * Convert a GPS time to a Java Date object which represents time in
   * milliseconds since the Java epoch 1 January 1970. This value will be the
   * time that would have been presented by a call to Java's new Date()
   * constructor at the instant indicated by the GPS time.
   *
   * @param gpsTime a valid positive number giving a GPS time in seconds
   * measured from the GPS epoch 6 January 1980.
   * @return a valid Java time in milliseconds
   */
  public static Date gpsToDate(double gpsTime) {
    long t = gpsToMillis(gpsTime);
    return new Date(t);
  }


  //public static void main(String[] args) {
  //    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
  //    sdf.setTimeZone(new SimpleTimeZone(0, "UTC"));
  //    GpsTimeConverter gtc = GpsTimeConverter.getInstance();
  //    System.err.println("adjustments = " + gtc.linearTime.length);
  //    for (int i = 0; i < gtc.linearTime.length; i++) {
  //        double g = gtc.linearTime[i];
  //        long j = gtc.javaTime[i];
  //        Date d = new Date(j);
  //        System.out.format("%12.1f %s\n", g, sdf.format(d));
  //    }
  //
  //    double g = 1119744016; // 1 July 2015,   00:00:00, leap second
  //    Date d = gtc.gpsToDate(g);
  //
  //    System.out.format("%12.1f %s\n", g, sdf.format(d));
  //}
}
