/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 02/2015 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.Vertex;
import tinfour.test.shapefile.DbfField;
import tinfour.test.shapefile.DbfFileReader;
import tinfour.test.shapefile.ShapefileReader;
import tinfour.test.shapefile.ShapefileRecord;

/**
 * A utility for loading vertices from a file for testing
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class VertexLoaderShapefile implements Closeable {

  static final double eRadius = 6378137; // WGS-84 equatorial radius
  static final double eFlattening = 1 / 298.257223560; // WGS-84

  final File file;
  final String rootPath;
  ShapefileReader reader;

  double xMin, xMax, yMin, yMax, zMin, zMax;

  double timeForLoad;

  double geoScaleX = 1;
  double geoScaleY = 1;
  double geoOffsetX;
  double geoOffsetY;
  boolean geographicCoordinates;

  /**
   * Construct an instance for the specified Shapefile
   *
   * @param file a Shapefile reference
   * @throws java.io.IOException in the event of an unrecoverable I/O condition
   *
   */
  public VertexLoaderShapefile(File file) throws IOException {
    this.file = file;
    geoScaleX = 1.0;
    geoScaleY = 1.0;
    geoOffsetX = 0.0;
    geoOffsetY = 0.0;

    String testExt = null;
    String path = file.getPath();
    int lastPeriod = path.lastIndexOf('.');
    if (lastPeriod > 0) {
      testExt = path.substring(lastPeriod + 1, path.length());
    }
    if (!"shp".equalsIgnoreCase(testExt)) {
      throw new IllegalArgumentException("File must be of type .shp");
    }
    rootPath = path.substring(0, lastPeriod);
    reader = openFile(file);
  }

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getXMin() {
    return xMin;
  }

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getXMax() {
    return xMax;
  }

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getYMin() {
    return yMin;
  }

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getYMax() {
    return yMax;
  }

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getZMin() {
    return zMin;
  }

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getZMax() {
    return zMax;
  }

  /**
   * Gets the total time to load a file (including the time required for
   * pre-sort if enabled).
   *
   * @return a time in milliseconds
   */
  public double getTimeForLoad() {
    return timeForLoad;
  }

  /**
   * Indicates whether the source data was in geographic coordinates
   *
   * @return true if the source data used geographic coordinates; otherwise,
   * false.
   */
  public boolean isSourceInGeographicCoordinates() {
    return geographicCoordinates;
  }

  public double getGeoScaleX() {
    return geoScaleX;
  }

  public double getGeoScaleY() {
    return geoScaleY;
  }

  public double getGeoOffsetX() {
    return geoOffsetX;
  }

  public double getGeoOffsetY() {
    return geoOffsetY;
  }

  /**
   * Read the records from the Shapefile and use them to populate vertices. The
   * loader has the option of loading the z coordinate from either the main
   * Shapefile itself (the SHP file) or from the associated DBF file. If you
   * wish to use a field in the DBF file as a z coordinate, specify the name of
   * the field as an argument. If you wish to use the z coordinate from the
   * Shapefile, specify a null or empty string. If a null or empty string is
   * specified, and the Shapefile does not contain a feature type that provides
   * z coordinates, the z coordinates will be uniformly populated with zeroes.
   * <p>
   * The index of the vertex is set to be the Shapefile record number. Thus many
   * vertices may be assigned with the same record number, particularly if the
   * input is a polygon or line feature.
   *
   * @param dbfFieldForZ the name of the DBF field to use as a z value; use a
   * null or empty string to use the z coordinates form the Shapefile.
   * @return a valid, potentially empty list of vertices
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  public List<Vertex> loadVertices(String dbfFieldForZ) throws IOException {
    DbfFileReader dbfReader = null;
    DbfField zField = null;
    boolean useShapefileZ = true;
    if (dbfFieldForZ != null && !dbfFieldForZ.trim().isEmpty()) {
      File dbfFile = reader.getCoFile("DBF");
      dbfReader = reader.getDbfFileReader();
      zField = dbfReader.getFieldByName(dbfFieldForZ.trim());
      if (zField == null) {
        try {
          dbfReader.close();
        } catch (IOException dontCare) {
          // no action required.
        }
        throw new IllegalArgumentException("The specified field "
                + dbfFieldForZ + " was not found in " + dbfFile.getName());
      }
      if (!zField.isNumeric()) {
        try {
          dbfReader.close();
        } catch (IOException dontCare) {
          // no action required.
        }
        throw new IllegalArgumentException(
                "The specified field " + dbfFieldForZ + " is not numeric in"
                + dbfFile.getName());
      }
      useShapefileZ = false;
    }

    List<Vertex> vList = new ArrayList<>();
    ShapefileRecord record = null;
    xMin = Double.POSITIVE_INFINITY;
    yMin = Double.POSITIVE_INFINITY;
    zMin = Double.POSITIVE_INFINITY;
    xMax = Double.NEGATIVE_INFINITY;
    yMax = Double.NEGATIVE_INFINITY;
    zMax = Double.NEGATIVE_INFINITY;
    while (reader.hasNext()) {
      record = reader.readNextRecord(record);
      int recNo = record.recordNumber;
      double[] xyz = record.xyz;
      double z = 0;
      if (dbfReader != null && zField != null) {
        dbfReader.readField(recNo, zField);
        z = zField.getDouble();
      }
      for (int i = 0; i < record.nPoints; i++) {
        double x = xyz[0];
        double y = xyz[1];
        if (useShapefileZ) {
          z = xyz[2];
        }
        if (this.geographicCoordinates) {
          x = (x - geoOffsetX) * geoScaleX;
          y = (y - geoOffsetY) * geoScaleY;
        }
        vList.add(new Vertex(x, y, z, recNo));
        if (x < xMin) {
          xMin = x;
        }
        if (y < yMin) {
          yMin = y;
        }
        if (z < zMin) {
          zMin = z;
        }
        if (x > xMax) {
          xMax = x;
        }
        if (y > yMax) {
          yMax = y;
        }
        if (z > zMax) {
          zMax = z;
        }
      }
    }

    if (dbfReader != null) {
      dbfReader.close();
    }
    return vList;
  }

  private ShapefileReader openFile(File file)
          throws IOException, IllegalArgumentException {
    File target = file;
    ShapefileReader reader = null;
    try {
      reader = new ShapefileReader(target);
      checkForGeographicCoordinates(reader);
    } catch (IOException ioex) {
      try {
        if (reader != null) {
          reader.close();
          reader = null;
        }
      } catch (IOException dontCare) {
        // no action required
      }
    }
    return reader;
  }

  private void checkForGeographicCoordinates(ShapefileReader reader)
          throws IOException {
    File target = reader.getCoFile("prj");
    if (target != null) {
      try (FileInputStream fins = new FileInputStream(target)) {
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
 
  @Override
  public void close() throws IOException {
    reader.close();
  }
}
