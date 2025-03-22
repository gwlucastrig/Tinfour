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
 * 01/2017  G. Lucas     Created
 * 12/2018  G. Lucas     Refactored
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.utils;

import org.tinfour.utils.loaders.IVerticalCoordinateTransform;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.tinfour.common.IConstraint;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.gis.shapefile.ShapefileType;
import org.tinfour.gis.shapefile.DbfField;
import org.tinfour.gis.shapefile.DbfFileReader;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.SimpleGeographicTransform;

/**
 * Provides tools for reading constraints from either a Shapefile
 */
public class ConstraintReaderShapefile implements Closeable {

  private final ShapefileReader reader;
  private int nPointsTotal;

  boolean isSourceInGeographicCoordinates;

  boolean geographicCoordinates;
  ICoordinateTransform coordinateTransform;
  IVerticalCoordinateTransform verticalCoordinateTransform;
  String dbfFieldForZ;
  String dbfFieldForAppData;

  /**
   * The default constructor scoped to private to deter application code from
   * creating an instance without a valid file specification.
   */
  private ConstraintReaderShapefile() {
    this.reader = null;
  }

  /**
   * Constructs an instance tied to the specified file.
   *
   * @param file a valid file reference pointing to a Shapefile.
   * @throws IOException if the specified Shapefile cannot be accessed.
   */
  public ConstraintReaderShapefile(File file) throws IOException {
    this.reader = new ShapefileReader(file);
  }

  /**
   * Read constraints from a Shapefile. Values for the Z coordinate will be read
   * from either the Shapefile geometry or from the DBF file if a field name was
   * specified. Application data for the constraints will be either the
   * Shapefile record number, or will be read from the DBF file if an
   * application-data field name was specified.
   *
   * @return a valid, potentially empty list
   * @throws IOException in the event of an unrecoverable IO condition.
   */
  public List<IConstraint> read() throws IOException {
    List<IConstraint> conList = new ArrayList<>();

    // open the DBF reader.  It may not even be required, but it will
    // simplify the code to just open it whether it is necessary or not.
    try (DbfFileReader dbfReader = reader.getDbfFileReader()) {

      DbfField zField = null;
      DbfField appField = null;
      ShapefileType shapefileType = reader.getShapefileType();

      if (dbfFieldForZ != null) {
        zField = dbfReader.getFieldByName(dbfFieldForZ);
        if (zField == null || !zField.isNumeric()) {
          throw new IOException("Specified field for Z coordinates, "
                  + dbfFieldForZ
                  + " is not found or not numeric");
        }
      }
      if (dbfFieldForAppData != null) {
        appField = dbfReader.getFieldByName(dbfFieldForAppData);
        if (appField == null) {
          throw new IOException("Specified field for application data, "
                  + dbfFieldForAppData
                  + " not found");
        }
      }

      CoordinatePair scratch = new CoordinatePair();
      int vertexID = 0;
      ShapefileRecord record = null;
      while (reader.hasNext()) {
        record = reader.readNextRecord(record);
        boolean useShapefileZ = true;
        double dbfZ = 0;
        Object dbfA = null;
        if (zField != null) {
          dbfReader.readField(record.recordNumber, zField);
          dbfZ = zField.getDouble();
          useShapefileZ = false;
        }
        if (appField != null) {
          dbfReader.readField(record.recordNumber, appField);
          dbfA = appField.getApplicationData();
        }
        switch (shapefileType) {
          case PolyLineZ:
          case PolygonZ:
          case Polygon:
          case PolyLine:
            nPointsTotal += record.nPoints;
            int k = 0;
            for (int iPart = 0; iPart < record.nParts; iPart++) {
              // in the case of polygons, Tinfour takes the vertices in the
              // opposite order of the Shapefile standard
              IConstraint con;
              if (shapefileType.isPolygon()) {
                con = new PolygonConstraint(); //NOPMD
                int n = record.partStart[iPart + 1] - record.partStart[iPart];
                for (int i = n - 1; i >= 0; i--) {
                  k = (record.partStart[iPart] + i) * 3;
                  double x = record.xyz[k];
                  double y = record.xyz[k + 1];
                  double z;
                  if (useShapefileZ) {
                    z = record.xyz[k + 2];
                  } else {
                    z = dbfZ;
                  }
                  if(verticalCoordinateTransform!=null){
                    z = verticalCoordinateTransform.transform(
                            record.recordNumber, z);
                  }
                  if (coordinateTransform != null) {
                    coordinateTransform.forward(x, y, scratch);
                    x = scratch.x;
                    y = scratch.y;
                  }
                  Vertex v = new ConstraintVertex(x, y, z, vertexID++); //NOPMD
                  con.add(v);
                }
              } else {
                con = new LinearConstraint(); //NOPMD
                int n = record.partStart[iPart + 1] - record.partStart[iPart];
                for (int i = 0; i < n; i++) {
                  double x = record.xyz[k++];
                  double y = record.xyz[k++];
                  double z;
                  if (useShapefileZ) {
                    z = record.xyz[k++];
                  } else {
                    z = dbfZ;
                    k++;
                  }
                  if (verticalCoordinateTransform != null) {
                    z = verticalCoordinateTransform.transform(
                            record.recordNumber, z);
                  }
                  if (coordinateTransform != null) {
                    coordinateTransform.forward(x, y, scratch);
                    x = scratch.x;
                    y = scratch.y;
                  }
                  Vertex v = new ConstraintVertex(x, y, z, vertexID++); //NOPMD
                  con.add(v);
                }
              }
              if (appField == null) {
                con.setApplicationData(record.recordNumber);
              } else {
                con.setApplicationData(dbfA);
              }
              con.complete();
              conList.add(con);
            }
            break;

          default:
        }
      }
    }

    return conList;
  }

  /**
   * Gets the total number of points read from the constraint file; or zero if
   * the content of the constraint file hasn't been read.
   *
   * @return a positive integer.
   */
  public int getTotalPointCount() {
    return nPointsTotal;
  }

  /**
   * Writes a text file representing the content of the specified list of
   * constraints
   *
   * @param file the output file reference
   * @param list a valid list of constraints
   * @throws IOException in the event of a non-recoverable I/O condition
   */
  public static void writeConstraintFile(
          final File file,
          final List<IConstraint> list) throws IOException {
    Path path = file.toPath();
    writeConstraintFile(path, list);
  }

  /**
   * Writes a text file representing the content of the specified list of
   * constraints
   *
   * @param path the path for writing the file
   * @param list a valid list of constraints
   * @throws IOException in the event of a non-recoverable I/O condition
   */
  public static void writeConstraintFile(
          final Path path,
          final List<IConstraint> list) throws IOException {
    try (BufferedWriter w = Files.newBufferedWriter(path)) {
      for (IConstraint constraint : list) {
        List<Vertex> vertices = constraint.getVertices();
        String conType = "";
        if (constraint instanceof PolygonConstraint) {
          conType = ", polygon";
        } else if (constraint instanceof LinearConstraint) {
          conType = ", linear";
        }
        w.write(String.format(Locale.ENGLISH, "%d%s%n",
                vertices.size(), conType));
        for (Vertex vertex : vertices) {
          w.write(String.format(Locale.ENGLISH, "%s,%s,%s%n",
                  vertex.x, vertex.y, vertex.getZ()));
        }
      }
    }
  }

  /**
   * Gets the coordinate transform associated with this instance. May be null if
   * no coordinate transform was set.
   *
   * @return a valid transform or a null if none was set.
   */
  public ICoordinateTransform getCoordinateTransform() {
    return coordinateTransform;
  }

  /**
   * Sets a coordinate transform to be used for mapping values from the source
   * file to vertex coordinates.
   *
   * @param transform a valid transform or a null if none is to be applied.
   */
  public void setCoordinateTransform(ICoordinateTransform transform) {
    coordinateTransform = transform;
    geographicCoordinates = transform instanceof SimpleGeographicTransform;
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
   * Sets the name of the field in the DBF file to use as a source for
   * application data when creating constraints.
   *
   * @param dbfFieldForAppData a valid string, or a null to use the the
   * Shapefile record number as the application data.
   */
  public void setDbfFieldForAppData(String dbfFieldForAppData) {
    if (dbfFieldForAppData != null) {
      this.dbfFieldForAppData = dbfFieldForAppData.trim();
      if (this.dbfFieldForAppData.isEmpty()) {
        this.dbfFieldForAppData = null;
      }
    } else {
      this.dbfFieldForAppData = null;
    }
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
