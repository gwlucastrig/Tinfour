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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.test.shapefile.ShapefileReader;
import tinfour.test.utils.VertexLoader;
import tinfour.utils.LinearUnits;

/**
 * A model for managing data taken from a Shapefile. At this time, only PointZ
 * format files are supported.
 */
public class ModelFromShapefile extends ModelAdapter implements IModel {

  static final double eRadius = 6378137; // WGS-84 equatorial radius
  static final double eFlattening = 1 / 298.257223560; // WGS-84

  String rootPath;
  String dbfFieldOption;

  /**
   * Construct a model tied to the specified file.
   *
   *
   * @param file a valid text or comma-separated value file
   *
   */
  public ModelFromShapefile(File file, String dbfFieldOption) {
    super(file);
    this.dbfFieldOption = dbfFieldOption;
    geoScaleX = 1.0;
    geoScaleY = 1.0;
    geoOffsetX = 0.0;
    geoOffsetY = 0.0;

    ShapefileReader reader = null;
    try {
      reader = openFile(file);
    } catch (IOException ioex) {
      throw new IllegalArgumentException("Unable to read "
              + file.getPath() + ", " + ioex.getMessage(), ioex);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException dontCare) {
          // no action required
        }
      }
    }
  }

  private String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  /**
   * Read the specified file.
   *
   * @param monitor an optional monitor for tracking progress (null if not used)
   * @throws IOException In the event of a non-recoverable error related to I/O
   * or file access.
   */
  @Override
  public void load(IMonitorWithCancellation monitor) throws IOException {

    if (areVerticesLoaded) {
      System.out.println("Internal error, multiple calls to load model");
      return;
    }

    long time0 = System.currentTimeMillis();

    VertexLoader vLoader = new VertexLoader();
 
         
    List<Vertex> vList = vLoader.readShapefile(file, dbfFieldOption);
 
    if (vList.isEmpty()) {
      monitor.reportDone(); // remove the progress bar
      throw new IOException("Unable to read points from file");
    }

    xMin = vLoader.getXMin();
    yMin = vLoader.getYMin();
    xMax = vLoader.getXMax();
    yMax = vLoader.getYMax();
    zMin = vLoader.getZMin();
    zMax = vLoader.getZMax();

    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + vList.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(vList, monitor);

  }

  @Override
  public String getDescription() {
    return "Shapefile " + this.file.getName();
  }

  /**
   * Gets the linear units for the coordinate system used by the data. It is
   * assumed that the vertical and horizontal coordinate systems will be in the
   * same unit system, though assumption could change in a future
   * implementation.
   *
   * @return at this time, the method always returns LinearUnits.UNDEFINED
   */
  @Override
  public LinearUnits getLinearUnits() {
    return LinearUnits.UNKNOWN;
  }

  @Override
  public String toString() {
    String conType = hasConstraints() ? " CDT" : "";
    String loaded = isLoaded() ? "Loaded" : "Unloaded";
    return String.format("Model From TXT %d %s%s", serialIndex, loaded, conType);
  }

  private void checkForGeographicCoordinates(ShapefileReader reader)
          throws IOException {
    File target = reader.getCoFile("prj");
    if (target!=null) {
      FileInputStream fins = null;
      try {
        fins = new FileInputStream(target);
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        int n = 0;
        while ((n = fins.read(buffer)) > 0) {
          for (int i = 0; i < n; i++) {
            sb.append((char) (buffer[i]));
          }
        }
        String content = sb.toString().toUpperCase();
        int indexPROJ = content.indexOf("PROJ");
        if (indexPROJ >= 0) {
          return; // not geographic
        }
        int indexGEOCS = content.indexOf("GEOCS");
        if (indexGEOCS > 0) {
          // definitely geographic
          geographicCoordinates = true;
        }

      } catch (IOException ioex) {
        // no action required
      } finally {
        fins.close();
      }
    }

    double x0 = reader.getMinX();
    double y0 = reader.getMinY();
    double x1 = reader.getMaxX();
    double y1 = reader.getMaxY();
    double dx = x1 - x0;
    double dy = y1 - y0;
    geographicCoordinates
            = (dx <= 360 && dy < 90
            && -180 <= x0 && x1 < 180
            && -90 <= y0 && y1 <= 90);

    if (geographicCoordinates) {
      // adjust the earth radius according to latitude.
      // if cenLat were zero, the adjusted radius would be the
      // equatorial radius. If it were 90, it would be the polar radius.
      double cenLat = (y0 + y1) / 2.0;
      double phi = Math.toRadians(cenLat);
      double sinPhi = Math.sin(phi);
      double adjustment = (1 - eFlattening * sinPhi * sinPhi);
      double adjRadius = adjustment * eRadius;

      geoScaleX = adjRadius * Math.cos(phi) * (Math.PI / 180);
      geoScaleY = adjRadius * (Math.PI / 180);
      geoOffsetX = x0;
      geoOffsetY = y0;
    }
  }

  private ShapefileReader openFile(File file) throws IOException, IllegalArgumentException {
    File target = file;
    String path = file.getPath();
    int lastPeriod = path.lastIndexOf('.');
    if (lastPeriod <= 0) {
      throw new IllegalArgumentException("File must be of type .shp");
    }
    rootPath = path.substring(0, lastPeriod);

    String extension = getFileExtension(target);
    if (!"shp".equalsIgnoreCase(extension)) {
      // we can try tacking on a .shp extension
      target = new File(rootPath + ".shp");
    }
    if (!target.exists()) {
      throw new IllegalArgumentException("File not found " + target.getPath());
    }

    ShapefileReader reader = null;
    try {
      reader = new ShapefileReader(target);
      checkForGeographicCoordinates(reader);
    } catch (IOException ioex) {
      try {
        reader.close();
      } catch (IOException dontCare) {
        // no action required
      }
      return null;
    }

    return reader;
  }
}
