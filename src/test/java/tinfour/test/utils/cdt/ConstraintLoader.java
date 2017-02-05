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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils.cdt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.IConstraint;
import tinfour.common.LinearConstraint;
import tinfour.common.PolygonConstraint;
import tinfour.common.Vertex;
import tinfour.test.shapefile.ShapefileReader;
import tinfour.test.shapefile.ShapefileRecord;
import tinfour.test.shapefile.ShapefileType;
import tinfour.test.utils.DelimitedReader;

/**
 * Provides tools for loading constraints from either a Shapefile or
 * a text file.
 */
public class ConstraintLoader {

  private int nPointsTotal;

  double xClipMin, xClipMax, yClipMin, yClipMax;
  double geoScaleX = 1;
  double geoScaleY = 1;
  double geoOffsetX;
  double geoOffsetY;
  boolean isSourceInGeographicCoordinates;

  /**
   * Gets the extension from the specified file
   *
   * @param file a valid file reference
   * @return if found, a valid string (period not included); otherwise,
   * a null.
   */
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
   * Reads the content of a constraints file which may be either a
   * Shapefile or a text file in the supported format. Note that
   * not all Shapefile types are supported.
   *
   * @param file a valid file.
   * @return a valid list of constraint instances.
   * @throws IOException in the event of a format violation or
   * unrecoverable I/O exception.
   */
  public List<IConstraint> readConstraintsFile(File file) throws IOException {

    String ext = getFileExtension(file);
    if ("shp".equalsIgnoreCase(ext)) {
      return readShapefile(file);
    } else if ("txt".equalsIgnoreCase(ext)) {
      return readTextFile(file);
    } else if ("csv".equalsIgnoreCase(ext)) {
      return readTextFile(file);
    }
    return null;
  }

  private List<IConstraint> readShapefile(File file) throws IOException {
    List<IConstraint> conList = new ArrayList<>();
    ShapefileReader reader = null;
    try {
      reader = new ShapefileReader(file);
      ShapefileType shapefileType = reader.getShapefileType();

      if (shapefileType != ShapefileType.PolyLineZ) {
        throw new IOException("Not yet implemented Shapefile type " + shapefileType);
      }

      int vertexID = 0;
      ShapefileRecord record = null;
      while (reader.hasNext()) {
        record = reader.readNextRecord(record);
        switch (shapefileType) {
          case PolyLineZ:
          case PolygonZ:
            nPointsTotal += record.nPoints;
            int k = 0;
            for (int iPart = 0; iPart < record.nParts; iPart++) {
              IConstraint con;
              if (shapefileType.isPolygon()) {
                con = new PolygonConstraint(); //NOPMD
              } else {
                con = new LinearConstraint(); //NOPMD
              }
              con.setApplicationData(record.recordNumber);
              int n = record.partStart[iPart + 1] - record.partStart[iPart];
              for (int i = 0; i < n; i++) {
                double x = record.xyz[k++];
                double y = record.xyz[k++];
                double z = record.xyz[k++];
                if (isSourceInGeographicCoordinates) {
                  x = (x - geoOffsetX) * geoScaleX;
                  y = (y - geoOffsetY) * geoScaleY;
                }
                Vertex v = new Vertex(x, y, z, vertexID++); //NOPMD
                con.add(v);
              }
              conList.add(con);
            }
            break;
          default:
        }
      }
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException dontCare) {
          // no action required
        }
      }
    }
    return conList;
  }

  /**
   * Gets the total number of points read from the constraint file; or zero
   * if the content of the constraint file hasn't been read.
   *
   * @return a positive integer.
   */
  public int getTotalPointCount() {
    return nPointsTotal;
  }

  /**
   * Sets the loader to treat the input coordinates as geographic and
   * scale them using the specified parameters.
   *
   * @param geoScaleX the scale factor for X coordinates
   * @param geoScaleY the scale factor for Y coordinates
   * @param geoOffsetX the offset for X coordinates
   * @param geoOffsetY the offset for Y coordinate
   */
  public void setGeographic(
    double geoScaleX,
    double geoScaleY,
    double geoOffsetX,
    double geoOffsetY) {
    this.geoScaleX = geoScaleX;
    this.geoScaleY = geoScaleY;
    this.geoOffsetX = geoOffsetX;
    this.geoOffsetY = geoOffsetY;
    this.isSourceInGeographicCoordinates = true;
  }

  private void processLine(
    DelimitedReader reader,
    int vertexID,
    List<String> sList,
    List<Vertex> vList) throws IOException {
    if (sList.size() != 3) {
      throw new IOException(
        "Invalid entry where x,y,z coordinates expected "
        + "on line " + reader.getLineNumber());
    }
    try {
      double x = Double.parseDouble(sList.get(0));
      double y = Double.parseDouble(sList.get(1));
      double z = Double.parseDouble(sList.get(2));
      if (isSourceInGeographicCoordinates) {
        x = (x - geoOffsetX) * geoScaleX;
        y = (y - geoOffsetY) * geoScaleY;
      }
      Vertex v = new Vertex(x, y, z, vertexID);
      vList.add(v);
    } catch (NumberFormatException nex) {
      throw new IOException("Invalid entry where x,y,z coordinates expected "
        + "on line " + reader.getLineNumber(), nex);
    }
  }

  private List<IConstraint> readTextFile(File file) throws IOException {
    List<IConstraint> conList = new ArrayList<>();
    DelimitedReader reader = null;
    int vertexID = 0;
    try {
      reader = new DelimitedReader(file, ',');
      List<String> sList;
      int nCon = 0;
      List<Vertex> vList = new ArrayList<>();
      sList = reader.readStrings();
      if (sList.isEmpty()) {
        throw new IOException("Empty constraint file " + file.getAbsolutePath());
      }

      if (sList.size() == 3) {
        // special case, assume the file is just one constraint
        // given as a set of vertices
        processLine(reader, vertexID++, sList, vList);
        while (true) {
          sList = reader.readStrings();
          if (sList.isEmpty()) {
            break;
          } else {
            processLine(reader, vertexID++, sList, vList);
          }
        }
        IConstraint con;

        if (vList.size() > 3
          && (vList.get(0)).getDistance(vList.get(vList.size() - 1)) < 1.0e-32) {
          con = new PolygonConstraint();
        } else {
          con = new LinearConstraint();
        }
        con.setApplicationData(nCon);
        for (Vertex v : vList) {
          con.add(v);
        }
        conList.add(con);
        return conList;
      }

      int nPoints;
      while (true) {
        if (sList.isEmpty()) {
          break; //  end of file
        }
        if (sList.size() == 1) {
          String s = sList.get(0);
          try {
            nPoints = Integer.parseInt(s);
            sList = reader.readStrings();
          } catch (NumberFormatException nex) {
            throw new IOException(
              "Invalid entry for point count,\"" + s + "\" on line "
              + reader.getLineNumber(), nex);
          }
        } else {
          throw new IOException(
            "Invalid entry for point count; a single string is expected on line "
            + reader.getLineNumber());
        }
        for (int i = 0; i < nPoints; i++) {
          sList = reader.readStrings();
          processLine(reader, vertexID++, sList, vList);
        }
        IConstraint con;
        if (nPoints > 3 && (vList.get(0)).getDistance(vList.get(1)) < 1.0e-32) {
          con = new PolygonConstraint(); //NOPMD
        } else {
          con = new LinearConstraint(); //NOPMD
        }
        con.setApplicationData(nCon);
        nCon++;
        for (Vertex v : vList) {
          con.add(v);
        }
        con.complete();
        conList.add(con);
        sList = reader.readStrings();
      }
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException dontCare) {
          // no action required
        }
      }
    }
    return conList;
  }

}
