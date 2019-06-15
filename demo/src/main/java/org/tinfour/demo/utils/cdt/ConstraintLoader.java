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
package org.tinfour.demo.utils.cdt;

import java.io.BufferedWriter;
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
import org.tinfour.gis.utils.ConstraintReaderShapefile;
import org.tinfour.io.DelimitedReader;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;

/**
 * Provides tools for loading constraints from either a Shapefile or a text
 * file.
 */
public class ConstraintLoader {

  private int nPointsTotal;

  double xClipMin, xClipMax, yClipMin, yClipMax;
 
  boolean isSourceInGeographicCoordinates;
  ICoordinateTransform coordinateTransform;

  /**
   * Gets the extension from the specified file
   *
   * @param file a valid file reference
   * @return if found, a valid string (period not included); otherwise, a null.
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
   * Reads the content of a constraints file which may be either a Shapefile or
   * a text file in the supported format. Note that not all Shapefile types are
   * supported.
   *
   * @param file a valid file.
   * @return a valid list of constraint instances.
   * @throws IOException in the event of a format violation or unrecoverable I/O
   * exception.
   */
  public List<IConstraint> readConstraintsFile(File file) throws IOException {

    String ext = getFileExtension(file);
    if ("shp".equalsIgnoreCase(ext)) {
      try(ConstraintReaderShapefile crs = new ConstraintReaderShapefile(file)){
        crs.setCoordinateTransform(coordinateTransform);
        return crs.read();
      }
    } else if ("txt".equalsIgnoreCase(ext)) {
      return readTextFile(file);
    } else if ("csv".equalsIgnoreCase(ext)) {
      return readTextFile(file);
    }
    return null;
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
      if (coordinateTransform !=null ) {
        CoordinatePair cPair = new CoordinatePair();
        coordinateTransform.forward(x, y, cPair);
        x = cPair.x;
        y = cPair.y;
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
    List<String> sList = new ArrayList<>();
    try {
      reader = new DelimitedReader(file, ',');
      int nCon = 0;
      List<Vertex> vList = new ArrayList<>();
      reader.readStrings(sList);
      if (sList.isEmpty()) {
        throw new IOException("Empty constraint file " + file.getAbsolutePath());
      }

      if (sList.size() == 3) {
        // special case, assume the file is just one constraint
        // given as a set of vertices
        processLine(reader, vertexID++, sList, vList);
        while (true) {
          reader.readStrings(sList);
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
        con.complete();
        return conList;
      }

      // the above logic reads in a line in order to find the special
      // case where there was just one constraint given by triplets of
      // data values.  If we reached here, the first line contained other
      // than 3 points.  From here on, we assume that we are looking for
      // data in the form
      //   (one line)          n
      //   (n lines)           x, y, z
      //   (one line)          n2
      //   (n2-lines)          x, y, z
      //      etc.
      // The content of the first line is in sList.  In the code below, we
      // always start the head of the loop with the the point count being
      // stored in sList.  We extract the count, n.  Then read n more lines,
      // extracting the coordinate triplets. Then read one more line after
      // that and return to the top of the loop.
      int nPoints;
      boolean polygonSpecified;
      boolean linearSpecified;
      while (true) {
        if (sList.isEmpty()) {
          break; //  end of file
        }
        if (sList.size() < 3) {
          polygonSpecified = false;
          linearSpecified = false;
          String s = sList.get(0);
          try {
            nPoints = Integer.parseInt(s);
          } catch (NumberFormatException nex) {
            throw new IOException(
                    "Invalid entry for point count,\"" + s + "\" on line "
                    + reader.getLineNumber(), nex);
          }
          if (sList.size() > 1) {
            s = sList.get(1);
            if ("polygon".equalsIgnoreCase(s)) {
              polygonSpecified = true;
            } else if ("linear".equalsIgnoreCase(s)) {
              linearSpecified = true;
            }
          }
        } else {
          throw new IOException(
                  "Invalid entry for point count; a single string is expected on line "
                  + reader.getLineNumber());
        }
        for (int i = 0; i < nPoints; i++) {
          reader.readStrings(sList);
          processLine(reader, vertexID++, sList, vList);
        }
        IConstraint con;
        if (polygonSpecified) {
          if (nPoints < 3) {
            throw new IOException(
                    "Fewer than 3 distinct points specified for polygon");
          }
          con = new PolygonConstraint(vList); //NOPMD
        } else if (linearSpecified) {
          con = new LinearConstraint(vList); //NOPMD
        } else if (nPoints > 3 && (vList.get(0)).getDistance(vList.get(1)) < 1.0e-23) {
          con = new PolygonConstraint(vList); //NOPMD
        } else {
          con = new LinearConstraint(vList); //NOPMD
        }
        vList.clear();
        con.setApplicationData(nCon);
        nCon++;
        con.complete();
        conList.add(con);
        reader.readStrings(sList);
      }
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException dontCare) {
          // NOPMD no action required
        }
      }
    }
    return conList;
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
   * Set a coordinate transform to be applied to the input coordinates.
   * @param transform A valid transform or a null if no transform is to
   * be applied.
   */
  public void setCoordinateTransform(ICoordinateTransform transform){
    this.coordinateTransform = transform;
  }
}
