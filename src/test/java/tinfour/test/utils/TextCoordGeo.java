
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
 * 09/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.text.ParseException;
import java.util.Formatter;

/**
 * Provides a utility for parsing or formatting a pair of geographic
 * coordinates.
 * The parse attempts to accept a broad range of user inputs
 * including decimal inputs, degrees minutes and seconds, and other forms.
 * Valid quadrants are N, S, E, and W.
 */
public class TextCoordGeo {

  private enum CoordType {
    Unspecified,
    Latitude,
    Longitude
  }

  boolean latFound;
  boolean lonFound;
  double[] v = new double[3];
  double[] coord = new double[2];
  CoordType[] coordType = new CoordType[2];
  int nV;
  int nCoord = 0;
  double latitude;
  double longitude;

  private boolean isQuad(char c) {
    if (Character.isUpperCase(c)) {
      return c == 'N' || c == 'S' || c == 'E' || c == 'W';
    } else {
      return c == 'n' || c == 's' || c == 'e' || c == 'w';
    }
  }

  private boolean isLat(char c) {
    return c == 'N' || c == 'S' || c == 'n' || c == 's';
  }

  private double quadSign(char c) {
    if (c == 'N' || c == 'n' || c == 'E' || c == 'e') {
      return 1;
    } else {
      return -1;
    }
  }

  /**
   * Parses a latitude/longitude string. If the parse is successful,
   * an application may access the results using the getLatitude() and
   * getLongitude() methods.
   *
   * @param text a valid string
   * @return if successful, an array of dimension two giving
   * geographic coordinates in the order latitude, longitude.
   * @throws java.text.ParseException in the event of invalid text
   */
  public double[] parse(String text) throws ParseException {
    nV = 0;
    nCoord = 0;
    boolean joinedNumbers = false;
    latitude = Double.NaN;
    longitude = Double.NaN;
    latFound = false;
    lonFound = false;

    if (text == null || text.isEmpty()) {
      throw new ParseException("Null input", 0);
    }
    int i0;
    char c = 0;
    for (i0 = 0; i0 < text.length(); i0++) {
      c = text.charAt(i0);
      if (!Character.isWhitespace(c)) {
        break;
      }
    }
    if (i0 == text.length()) {
      throw new ParseException("Null input", 0);
    }

    StringBuilder numStr = new StringBuilder();
    if (c == '-' || c == '+' || c == '.') {
      numStr.append(c);
    } else if (Character.isDigit(c)) {
      numStr.append(c);
    } else {
      throw new ParseException("Invalid text ", i0);
    }
    int i = i0 + 1;
    while (i < text.length()) {
      c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        if (numStr.length() > 0) {
          processNumberString(i, numStr);
          if (joinedNumbers) {
            // collapse the numerics
            processDMS(i);
            joinedNumbers = false;
          }
        }
        i++;
        continue;

      }

      if (Character.isDigit(c) || c == '.') {
        numStr.append(c);
        i++;
        continue;
      }

      if (c == 'E' || c == 'e') {
        if (i < text.length() - 1 && (text.charAt(i + 1) == '+' || text.charAt(i + 1) == '-')) {
          if (numStr.length() == 0) {
            throw new ParseException(
              "Improper text where exponential notation expected", i);
          }
          numStr.append(c);
          numStr.append(text.charAt(i + 1));
          i += 2;
          continue;
        }
      }

      if (isQuad(c)) {
        // make sure user doesn't give us two lats or two lons
        boolean latFlag = false;
        if (isLat(c)) {
          if (latFound) {
            throw new ParseException(
              "Multiple latitude specifications", i);
          }
          latFlag = true;
        } else if (lonFound) {
          throw new ParseException(
            "Multiple longitude specifications", i);
        }

        if (numStr.length() > 0) {
          processNumberString(i, numStr);
        }
        if (nV == 0) {
          if (nCoord > 0 && coordType[nCoord - 1] == CoordType.Unspecified) {
            // there is a coordinate pending, do nothing
          } else {
            throw new ParseException(
              "Incomplete coordinate specification", i);
          }
        }
        if (nV > 0) {
          processDMS(i);
        }
        if (coord[nCoord - 1] < 0) {
          throw new ParseException(
            "Text must not include both quadrant and negative value", i);
        }
        coord[nCoord - 1] *= quadSign(c);
        if (latFlag) {
          coordType[nCoord - 1] = CoordType.Latitude;
        } else {
          coordType[nCoord - 1] = CoordType.Longitude;
        }
        if (latFlag) {
          latFound = true;
        } else {
          lonFound = true;
        }
      } else if (c == '-') {
        // we will accept a leading - as a negative sign,
        // and embedded as a DD-MM-SS separator (if there are no spaces)
        if (i == text.length() - 1) {
          throw new ParseException(
            "Minus sign out of place", i);
        }
        if (nV == 1 && !joinedNumbers) {
          processDMS(i);
        }
        if (numStr.length() == 0) {
          if (Character.isDigit(text.charAt(i + 1))) {
            numStr.append(c);
          } else {
            throw new ParseException(
              "Minus sign out of place", i);
          }
        } else // can we interpret it as a separator between two adjacent numbers?
         if (Character.isDigit(text.charAt(i - 1)) && Character.isDigit(text.charAt(i + 1))) {
            processNumberString(i, numStr);
            joinedNumbers = true;
          } else {
            throw new ParseException(
              "Minus sign out of place", i);
          }
      } else if (c == '\u00b0' || c == '\'' || c == '"') {
        // the degrees/minutes/seconds symbols
        // perhaps the user cut-and-pasted a label from the ModelFromLas class.
        if (numStr.length() > 0) {
          processNumberString(i, numStr);
        }
        if (c == '\u00b0') {
          if (nV != 1) {
            throw new ParseException(
              "Degrees symbol out of place", i);
          }
        } else if (c == '\'') {
          if (nV != 2) {
            throw new ParseException(
              "Minutes symbol out of place", i);
          }
        } else // must be the seconds symbol
         if (nV == 3) {
            processDMS(i);
          } else {
            throw new ParseException(
              "Seconds symbol out of place", i);
          }
      } else if (c == '/') {
        // coordinate divider
        if (this.nCoord == 2) {
          throw new ParseException(
            "Too many coordinate specifications", i);
        }
        if (numStr.length() > 0) {
          processNumberString(i, numStr);
        }

        if (nV > 0) {
          processDMS(i);
        }
      }

      i++;
    }

    // perform post processing.  If there is anything in the number
    // string builder, extract the coordinate.
    if (numStr.length() > 0) {
      processNumberString(i, numStr);
      processDMS(i);
    }
    if (nCoord < 2) {
      if (this.nCoord == 2) {
        throw new ParseException(
          "Too few coordinate specifications", i);
      }
    }

    if (coordType[0] == CoordType.Unspecified || coordType[1] == CoordType.Unspecified) {
      coordType[0] = CoordType.Latitude;
      coordType[1] = CoordType.Longitude;
    } else if (coordType[0] == CoordType.Unspecified) {
      if (coordType[1] == CoordType.Latitude) {
        coordType[0] = CoordType.Longitude;
      } else if (coordType[1] == CoordType.Longitude) {
        coordType[0] = CoordType.Latitude;
      }
    } else if (coordType[1] == CoordType.Unspecified) {
      if (coordType[0] == CoordType.Latitude) {
        coordType[1] = CoordType.Longitude;
      } else if (coordType[0] == CoordType.Longitude) {
        coordType[1] = CoordType.Latitude;
      }
    }
    if (coordType[0] == CoordType.Latitude) {
      latitude = coord[0];
      longitude = coord[1];
    } else {
      latitude = coord[1];
      longitude = coord[0];
    }

    if (Math.abs(latitude) > 90) {
      throw new ParseException("Latitude value out of range: " + latitude, i);
    } else if (Math.abs(longitude) > 180) {
      if (Math.abs(longitude) > 360) {
        throw new ParseException("Longitude value out of range: " + longitude, i);
      }
      if (longitude < 0) {
        longitude += 360;
      } else {
        longitude = 360 + longitude;
      }
    }

    double[] geo = new double[2];
    geo[0] = latitude;
    geo[1] = longitude;
    return geo;
  }

  private void processNumberString(int iPos, StringBuilder numStr) throws ParseException {
    if (nV == 3) {
      throw new ParseException(
        "More than 3 numeric specifications", iPos);
    }
    try {
      double d = Double.parseDouble(numStr.toString());
      if (nV > 0) {
        if (d < 0) {
          throw new ParseException(
            "Minutes and seconds specifications cannot be negative", iPos);
        }
        if (v[nV - 1] != Math.floor(v[nV - 1])) {
          throw new ParseException(
            "Non-integral value not allowed " + v[nV - 1], iPos);
        }
      }
      v[nV++] = d;
      numStr.setLength(0);
    } catch (NumberFormatException nex) {
      throw new ParseException(
        "Invalid text where numeric expected " + numStr.toString(), iPos);
    }
  }

  private void processDMS(int iPos) throws ParseException {
    if (nCoord == 2) {
      throw new ParseException(
        "Extra text after 2nd coordinate", iPos);
    }
    coordType[nCoord] = CoordType.Unspecified;
    if (nV == 1) {
      coord[nCoord++] = v[0];
    } else if (nV == 2) {
      if (v[1] >= 60) {
        throw new ParseException(
          "Minutes must be less than 60", iPos);
      }
      coord[nCoord++] = v[0] + v[1] / 60;
    } else {
      if (v[1] >= 60 || v[2] >= 60) {
        throw new ParseException(
          "Minutes and seconds must be less than 60", iPos);
      }
      coord[nCoord++] = v[0] + (v[1] + v[2] / 60) / 60;
    }
    nV = 0;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

    /**
   * Formats the geographic coordinates into a form that is suitable
   * for presentation in a user interface.
   *
   * @param lat a floating point value in the range -90 to 90
   * @param lon a floating point value in the range -360 to 360
   * (values are folded into range -180 &le; lon &lt; 180).
   * @return a formatted string
   */
  public String format(double lat, double lon) {

    // put long into range  -180 &le; lon &lt; 180
    double cLon = lon;
    if (cLon < -180) {
      cLon += 360;
    } else if (lon >= 180) {
      cLon -= 360;
    }else if(lon==360){
      cLon = 0;
    }

    String sLat;
    char quadLat = (lat >= 0 ? 'N' : 'S');
    char quadLon = (cLon >= 0 ? 'E' : 'W');
    StringBuilder sb = new StringBuilder(32);
    Formatter fmt = new Formatter(sb);
    formatDMS(fmt, lat, quadLat);
    sb.append(" / ");
    formatDMS(fmt, lon, quadLon);
    return sb.toString();
  }

  void formatDMS(Formatter fmt, double value, char quad) {
    // round to nearest 100th second
    int c = (int) Math.floor(Math.abs(value) * 3600.0 * 100.0 + 0.5);
    int d = c / 360000;
    c -= d * 360000;
    int m = c / 6000;
    c -= m * 6000;
    int s = c / 100;
    c -= s * 100;
    if (c == 0) {
      fmt.format("%d-%02d-%02d%c", d, m, s, quad);
    } else {
      fmt.format("%d-%02d-%02d.%02d%c", d, m, s, c, quad);
    }
  }

}
