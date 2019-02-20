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
 * ------  --------- -------------------------------------------------
 * 12/2018 G. Lucas  Created
 *
 * Notes:
 *  * Notes:
 *   Future Work: This module has some logic for handling the
 *   the Well-Known Format used by .prj files. It should be replaced with
 *   a centralized treatment.  See also the ShapefileReader for this discussion.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.gis.shapefile.DbfField;
import org.tinfour.gis.shapefile.DbfFileReader;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.IVertexReader;
import org.tinfour.utils.loaders.SimpleGeographicTransform;

/**
 * A utility for loading vertices from a file for testing
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class VertexReaderShapefile implements IVertexReader, Closeable {

  final File file;
  final String rootPath;
  ShapefileReader reader;

  double xMin, xMax, yMin, yMax, zMin, zMax;

  double timeForLoad;

  boolean geographicCoordinates;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;
  ICoordinateTransform coordinateTransform;
  String dbfFieldForZ;

  /**
   * Construct an instance for the specified Shapefile
   *
   * @param file a Shapefile reference
   * @throws java.io.IOException in the event of an unrecoverable I/O condition
   *
   */
  public VertexReaderShapefile(File file) throws IOException {
    // TO DO: The linear units should be in the PRJ file
    this.file = file;

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
   * Sets the name of the field in the DBF file to use as a source for Z
   * coordinates for data.
   *
   * @param dbfFieldForZ a valid string, or a null to use the Z coordinates from
   * the Shapefile geometry file (the &#48;shp file).
   */
  public void setDbfFieldForZ(String dbfFieldForZ) {
    if (dbfFieldForZ != null) {
      this.dbfFieldForZ = dbfFieldForZ.trim();
      if (this.dbfFieldForZ.isEmpty()) {
        this.dbfFieldForZ = null;
      }
    } else {
      this.dbfFieldForZ = null;
    }
  }

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getXMin() {
    return xMin;
  }

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getXMax() {
    return xMax;
  }

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getYMin() {
    return yMin;
  }

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getYMax() {
    return yMax;
  }

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getZMin() {
    return zMin;
  }

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
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
  @Override
  public boolean isSourceInGeographicCoordinates() {
    return geographicCoordinates;
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
   * @param monitor an optional progress monitor, or null if not required.
   * @return a valid, potentially empty list of vertices
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  @Override
  public List<Vertex> read(IMonitorWithCancellation monitor) throws IOException {
    double mainFileSize = 0;
    if(monitor!=null ){
      mainFileSize = reader.getFileSize();
    }
    DbfFileReader dbfReader = null;
    DbfField zField = null;
    boolean useShapefileZ = true;
    CoordinatePair scratch = new CoordinatePair();
    if (dbfFieldForZ != null) {
      File dbfFile = reader.getCoFile("DBF");
      dbfReader = reader.getDbfFileReader();
      zField = dbfReader.getFieldByName(dbfFieldForZ);
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
    int nRecordsRead = 0;
    while (reader.hasNext()) {
      if( monitor!=null && mainFileSize>0 && (nRecordsRead%1000)==0 ){
        double filePos = reader.getFilePosition();
        monitor.reportProgress((int) (100.0 * filePos/mainFileSize)); 
      }
      record = reader.readNextRecord(record);
      int recNo = record.recordNumber;
      double[] xyz = record.xyz;
      double z = 0;
      if (dbfReader != null && zField != null) {
        dbfReader.readField(recNo, zField);
        z = zField.getDouble();
      }
      
      for (int i = 0; i < record.nPoints; i++) {
        int index =i*3;
        double x = xyz[index];
        double y = xyz[index+1];
        if (useShapefileZ) {
          z = xyz[index+2];
        }

        if (coordinateTransform != null) {
          boolean status = coordinateTransform.forward(x, y, scratch);
          if (!status) {
            throw new IOException(
                    "Invalid transformation for coordinates in record "
                    + recNo + ": " + x + "," + y);
          }
          x = scratch.x;
          y = scratch.y;
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
    ShapefileReader reader =  new ShapefileReader(target);
    try {
      checkForGeographicCoordinates(reader);
    } catch (IOException ioex) {
      try {
        if (reader != null) {
          reader.close();
          reader = null;
        }
        throw ioex;
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
        int n;
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
        
        int indexUnit=content.indexOf("UNIT");
        if(indexUnit>0){
          if(content.indexOf("FOOT")>indexUnit || content.indexOf("FEET")>indexUnit){
            linearUnits = LinearUnits.FEET;
          }
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
      double xCenter = (x0 + x1) / 2.0;
      double yCenter = (y0 + y1) / 2.0;
      coordinateTransform
              = new SimpleGeographicTransform(
                      yCenter,
                      xCenter,
                      linearUnits);

    }
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  @Override
  public ICoordinateTransform getCoordinateTransform() {
    return coordinateTransform;
  }

  @Override
  public void setCoordinateTransform(ICoordinateTransform transform) {
    coordinateTransform = transform;
    geographicCoordinates = transform instanceof SimpleGeographicTransform;
  }
  
  
  /**
   * Gets the linear units for the horizontal coordinate system from 
   * the Shapefile. This value is typically obtained from the 
   * PRJ file associated with the Shapefile.
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits(){
    return linearUnits;
  }
}
