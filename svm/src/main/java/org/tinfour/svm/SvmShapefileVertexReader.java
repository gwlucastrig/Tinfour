/* --------------------------------------------------------------------
 * Copyright 2025 Gary W. Lucas.
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
 * ------   --------- -------------------------------------------------
 * 12/2018  G. Lucas  Created VertexReaderShapefi;e
 * 03/2025  G. Lucas  Adapted for SVM contours
 *
 * Notes:
 *   Future Work: This module has some logic for handling the
 *   the Well-Known Format used by .prj files. It should be replaced with
 *   a centralized treatment.  See also the ShapefileReader for this discussion.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.gis.shapefile.DbfField;
import org.tinfour.gis.shapefile.DbfFileReader;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.gis.shapefile.ShapefileType;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.IVertexReader;
import org.tinfour.utils.loaders.IVerticalCoordinateTransform;
import org.tinfour.utils.loaders.SimpleGeographicTransform;

/**
 * A utility for loading vertices from a file for testing
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class SvmShapefileVertexReader implements IVertexReader, Closeable {

  final File file;
  final String rootPath;
  ShapefileReader reader;

  double xMin, xMax, yMin, yMax, zMin, zMax;

  double timeForLoad;

  boolean geographicCoordinates;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;
  ICoordinateTransform coordinateTransform;
  IVerticalCoordinateTransform verticalCoordinateTransform;
  
  String dbfFieldForZ;

  List<Vertex> vertexList = new ArrayList<>();
  List<LinearConstraint> constraintList = new ArrayList<>();

  /**
   * Construct an instance for the specified Shapefile
   *
   * @param file a shapefile reference
   * @throws java.io.IOException in the event of an unrecoverable I/O condition
   *
   */
  public SvmShapefileVertexReader(File file) throws IOException {
    // TO DO: The linear units should be in the PRJ file
    if(file==null){
      throw new IllegalArgumentException("Null file specified for shapefile");
    }
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
   * the shapefile geometry file (the &#48;shp file).
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
   * Sets the vertical coordinate transform to be used when reading the
   * file (if any).
   * @param verticalTransform a valid instance if a transform is to be
   * applies; a null reference if no transform is required.
   */
  public void setVerticalCoordinateTransform(IVerticalCoordinateTransform verticalTransform){
    this.verticalCoordinateTransform = verticalTransform;
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
   * Read the records from the shapefile and use them to populate vertices.
   * If the input shapefile is based on PolyLine features, this method
   * assumes that it provides contours contours. In that case, the zero-depth
   * contours are ignored and other valued contours are added to the
   * linear constraint list.
   * <p>
   * The loader has the option of loading the z coordinate from either the main
   * shapefile itself (the SHP file) or from the associated DBF file. If you
   * wish to use a field in the DBF file as a z coordinate, specify the name of
   * the field as an argument. If you wish to use the z coordinate from the
   * shapefile, specify a null or empty string. If a null or empty string is
   * specified, and the shapefile does not contain a feature type that provides
   * z coordinates, the z coordinates will be uniformly populated with zeroes.
   * <p>
   * The index of the vertex is set to be the shapefile record number. Thus many
   * vertices may be assigned with the same record number, particularly if the
   * input is a polygon or line feature.
   * @param monitor an optional progress monitor, or null if not required.
   * @return a valid, potentially empty list of vertices
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  @Override
  public List<Vertex> read(IMonitorWithCancellation monitor) throws IOException {
    vertexList.clear();
    constraintList.clear();

    double mainFileSize = 0;
    if (monitor != null) {
      mainFileSize = reader.getFileSize();
    }
    DbfFileReader dbfReader = null;
    DbfField zField = null;
    boolean useShapefileZ = true;
    CoordinatePair scratch = new CoordinatePair();

    try {
      if (dbfFieldForZ != null) {
        File dbfFile = reader.getCoFile("DBF");
        if (dbfFile == null) {
          throw new IOException("Missing DBF file for " + file.getName());
        }
        dbfReader = reader.getDbfFileReader();
        zField = dbfReader.getFieldByName(dbfFieldForZ);
        if (zField == null) {
          try {
            dbfReader.close();
          } catch (IOException dontCare) {
            // NOPMD   no action required.
          }
          throw new IllegalArgumentException("The specified field "
                  + dbfFieldForZ + " was not found in " + dbfFile.getName());
        }
        if (!zField.isNumeric()) {
          try {
            dbfReader.close();
          } catch (IOException dontCare) {
            // NOPMD no action required.
          }
          throw new IllegalArgumentException(
                  "The specified field " + dbfFieldForZ + " is not numeric in"
                  + dbfFile.getName());
        }
        useShapefileZ = false;
      }

      ShapefileRecord record = null;
      xMin = Double.POSITIVE_INFINITY;
      yMin = Double.POSITIVE_INFINITY;
      zMin = Double.POSITIVE_INFINITY;
      xMax = Double.NEGATIVE_INFINITY;
      yMax = Double.NEGATIVE_INFINITY;
      zMax = Double.NEGATIVE_INFINITY;

      ShapefileType shapefileType = reader.getShapefileType();
       if(useShapefileZ && !shapefileType.hasZ()){
         throw new IllegalArgumentException(
           "No z-related field specified and shapefile lacks z coordinates");
       }

      int nRecordsRead = 0;
      while (reader.hasNext()) {
        if (monitor != null && mainFileSize > 0 && (nRecordsRead % 1000) == 0) {
          double filePos = reader.getFilePosition();
          monitor.reportProgress((int) (100.0 * filePos / mainFileSize));
        }
        record = reader.readNextRecord(record);
        if(record.nPoints == 0){
          // The record is empty.  Some shapefiles may use this treatment
          // for a missing record.
          continue;
        }

        int recNo = record.recordNumber;
        double[] xyz = record.xyz;
        double z = 0;
        double dbfZ = 0;
        if (dbfReader != null && zField != null) {
          dbfReader.readField(recNo, zField);
          z = zField.getDouble();
          if (verticalCoordinateTransform != null) {
            z = verticalCoordinateTransform.transform(recNo, z);
          }
          dbfZ = z;
        }


        // Map all input points using the vertical and horizontal
        // transforms (if any).  Collect the range of values.
        // This operation proceeds without regard to the shapefile type.
        for (int i = 0; i < record.nPoints; i++) {
          int index = i * 3;
          double x = xyz[index];
          double y = xyz[index + 1];
          if (useShapefileZ) {
            z = xyz[index + 2];
            if (verticalCoordinateTransform != null) {
               z = verticalCoordinateTransform.transform(recNo, z);
            }
          }else{
            // store the z value from the DBF file
            xyz[index+2] = z;
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
            xyz[index] = x;
            xyz[index + 1] = y;
          }

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

        // based on the shapefile type, perform appropriate actions
        switch (shapefileType) {
          case Point:
          case PointZ:
          case PointM:
            for (int i = 0; i < record.nPoints; i++) {
              int index = i * 3;
              double x = xyz[index];
              double y = xyz[index + 1];
              vertexList.add(new Vertex(x, y, xyz[index + 2], vertexList.size() + 1));
            }
            break;
          case PolyLine:
          case PolyLineZ:
            if (dbfZ == 0) {
              // we screen out the z contour
              break;
            }
            LinearConstraint lincon = new LinearConstraint();
            int k = 0;
            for (int iPart = 0; iPart < record.nParts; iPart++) {
              // in the case of polygons, Tinfour takes the vertices in the
              // opposite order of the shapefile standard
              int n = record.partStart[iPart + 1] - record.partStart[iPart];
              for (int i = 0; i < n; i++) {
                double x = record.xyz[k++];
                double y = record.xyz[k++];
                Vertex v = new Vertex(x, y, xyz[k++], vertexList.size() + 1);
                vertexList.add(v);
                lincon.add(v);
              }
              lincon.complete();
              lincon.setApplicationData(true);
              constraintList.add(lincon);
            }
            break;
          default:
            throw new IOException("NNon-supported shapefile type "+shapefileType.name());
        }
      }
    } finally {
      if (dbfReader != null) {
        dbfReader.close();
      }
    }
    return vertexList;
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
        // NOPMD no action required
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
        // NOPMD no action required
      }
    }

    double x0 = reader.getMinX();
    double y0 = reader.getMinY();
    double x1 = reader.getMaxX();
    double y1 = reader.getMaxY();
    double dx = x1 - x0;
    double dy = y1 - y0;
    geographicCoordinates
            = dx <= 360 && dy < 90
            && -180 <= x0 && x1 < 180
            && -90 <= y0 && y1 <= 90;

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
   * the shapefile. This value is typically obtained from the
   * PRJ file associated with the shapefile.
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits(){
    return linearUnits;
  }

  /**
   * Gets the list of vertices that were collected during the
   * most recent read operation.  The list may be empty if the
   * input shapefile included no usable data.
   * @return a valid, potentially empty, list of vertices.
   */
  public List<Vertex>getVertices(){
    return vertexList;
  }

    /**
   * Gets the list of constraints that were collected during the
   * most recent read operation.  The list will be populated only if
   * the input shapefile contains PolyLine features (assumed to be contours).
   * @return a valid, potentially empty, list of vertices.
   */
  public List<LinearConstraint>getLinearConstraints(){
    return constraintList;
  }

}
