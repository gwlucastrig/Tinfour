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
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.test.utils.VertexLoader;
import tinfour.utils.LinearUnits;

/**
 * A model for managing data taken from a text or comma-separated-value
 * file
 */
public class ModelFromText extends ModelAdapter implements IModel {

  final char delimiter;

  double geoScaleX;
  double geoScaleY;
  double geoOffsetX;
  double geoOffsetY;
  boolean geographicCoordinates;

  /**
   * Construct a model tied to the specified file.
   * If the values in the file are in geographic coordinates,
   * the input file must include a header row indicating which
   * columns are latitude and longitude.  This feature serves two
   * purposes, it informs the model that the coordinates are geographic
   * and it dispels any ambiguity about which column is which.
   * Geographic coordinate values must be giving in standard decimal
   * numeric form using negative numbers for southern latitudes and
   * western longitudes (quadrants and degrees-minutes-seconds notion are
   * not supported at this time).
   * <p>
   * For example:
   * <pre><code>
   *    latitude, longitude, z
   *      42.5,     73.33,   2001
   * </code></pre>
   *
   * @param file a valid text or comma-separated value file
   * @param delimiter the delimiter character used for accessing files.
   *
   */
  public ModelFromText(File file, char delimiter) {
    super(file);
    this.delimiter = delimiter;
  }

  /**
   * Read the specified file.
   *
   * @param monitor an optional monitor for tracking progress (null
   * if not used)
   * @throws IOException In the event of a non-recoverable error
   * related to I/O or file access.
   */
  @Override
  public void load(IMonitorWithCancellation monitor) throws IOException {

    if (loaded) {
      System.out.println("Internal error, nultiple calls to load model");
      return;
    }

    long time0 = System.currentTimeMillis();
    VertexLoader loader = new VertexLoader();

    List<Vertex> list = loader.readDelimitedFile(file, delimiter);
    if (list.isEmpty()) {
      monitor.reportDone(); // remove the progress bar
      throw new IOException("Unable to read points from file");
    }

    xMin = loader.getXMin();
    yMin = loader.getYMin();
    xMax = loader.getXMax();
    yMax = loader.getYMax();
    zMin = loader.getZMin();
    zMax = loader.getZMax();

    geographicCoordinates = loader.isSourceInGeographicCoordinates();
    if (geographicCoordinates) {
      geoScaleX = loader.getGeoScaleX();
      geoScaleY = loader.getGeoScaleY();
      geoOffsetX = loader.getGeoOffsetX();
      geoOffsetY = loader.getGeoOffsetY();
    }

    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + list.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(list, monitor);

  }

  @Override
  public String getFormattedCoordinates(double x, double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / geoScaleY + geoOffsetY, true);
      sb.append(" / ");
      fmtGeo(fmt, x / geoScaleX + geoOffsetX, false);
      return sb.toString();
    }
    return String.format("%4.2f,%4.2f", x, y);
  }

  @Override
  public String getFormattedX(double x) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, x / geoScaleX + geoOffsetX, false);
      return sb.toString();
    }
    return String.format("%11.2f", x);
  }

  @Override
  public String getFormattedY(double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      sb.append(' '); // to provide vertical alignment with longitudes
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / geoScaleY + geoOffsetY, true);
      return sb.toString();
    }
    return String.format("%11.2f", y);
  }

  void fmtGeo(Formatter fmt, double coord, boolean latFlag) {
    double c = coord;
    if (c < -180) {
      c += 360;
    } else if (c >= 180) {
      c -= 360;
    }
    int x = (int) (Math.abs(c) * 360000 + 0.5);
    int deg = x / 360000;
    int min = (x - deg * 360000) / 6000;
    int sec = x % 6000;
    char q;
    if (latFlag) {
      if (c < 0) {
        q = 'S';
      } else {
        q = 'N';
      }
      fmt.format("%02d\u00b0 %02d' %05.2f\" %c", deg, min, sec / 100.0, q);
    } else {
      if (c < 0) {
        q = 'W';
      } else {
        q = 'E';
      }
      fmt.format("%03d\u00b0 %02d' %05.2f\" %c", deg, min, sec / 100.0, q);
    }
  }

  /**
   * Indicates whether the coordinates used by this instance are
   * geographic in nature.
   *
   * @return true if coordinates are geographic; otherwise, false.
   */
  @Override
  public boolean isCoordinateSystemGeographic() {
    return this.geographicCoordinates;
  }

  @Override
  public String getDescription() {
    if (Character.isWhitespace(delimiter)) {
      return "Space delimented text file";
    } else if (delimiter == ',') {
      return "Comma-separated values";
    } else {
      return "Text delimited by '" + delimiter + "'";
    }
  }

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return at this time, the method always returns LinearUnits.UNDEFINED
   */
  @Override
  public LinearUnits getLinearUnits() {
    return LinearUnits.UNKNOWN;
  }

}
