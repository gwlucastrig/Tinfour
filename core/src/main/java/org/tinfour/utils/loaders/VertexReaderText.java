/* --------------------------------------------------------------------
 * Copyright 2015-2018 Gary W. Lucas.
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
 * 12/2018 G. Lucas Refactored
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.loaders;

import java.io.BufferedInputStream;
import java.io.Closeable;
import org.tinfour.io.DelimitedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.utils.LinearUnits;

/**
 * A utility for loading vertices from a file for testing. The coordinates for
 * vertices are expected to be loaded as three numeric values separated by a
 * delimiter character.
 * <p>
 * <strong>Comment Lines:</strong> Lines beginning with a "hash sign", or #,
 * will be treated as comments and ignored. Blank lines will be ignored.
 * <p>
 * <strong>Delimiters:</strong>
 * Specifications in text files depend on the presence of a delimiter character
 * to separate data fields given on a line of text. Typically delimiters are
 * specified using a space, tab, comma, or vertical-bar character (pipe). Files
 * with the extension ".csv" (for comma-separated-value) are assumed to be
 * comma-delimited. Otherwise, the constructor will attempt to inspect the
 * content of the specified file and determine the delimiter based on internal
 * clues. If a file includes a mix of potential delimiters, such as both spaces
 * and commas, the delimiter will be determined on the basis of precedence.
 * Supported delimiters, in increasing order of precedence, are comma, space,
 * tab, and vertical bar.
 * <p>
 * An application may also override the automatically determined delimiter
 * through the setDelimiter() method.
 * <p>
 * <strong>Column Headers:</strong>
 * By default, columns in the file are treated as giving x, y, and z coordinates
 * in that order. The order of the coordinate specifications can be altered
 * through the use of column headers. If the first non-comment line in a file
 * contains non-numeric specifications, the line will be treated as a "header"
 * line that gives the names of columns.
 * <p>
 * <strong>Geographic Coordinates:</strong>
 * While this class is not sufficient for Geographic Information Systems (GIS),
 * it does provide minimal support for geographic coordinates. If the values in
 * the file are to be treated as geographic coordinates, the input file must
 * include a header row indicating which columns are latitude and longitude.
 * This feature serves two purposes, it informs the model that the coordinates
 * are geographic and it dispels any ambiguity about which column is which.
 * Geographic coordinate values must be giving in standard decimal numeric form
 * using negative numbers for southern latitudes and western longitudes (the
 * quadrant characters N, S, E, W, and the degrees-minutes-seconds notion are
 * not supported at this time).
 * <p>
 * For example:
 * <pre><code>
 *    latitude, longitude, z
 *      42.5,     73.33,   2001
 * </code></pre>
 * <p>
 * Note, however that if both geographic and Cartesian coordinates are
 * provided, the Cartesian coordinates will take precedence.  The rationale,
 * for this design decision is that specifications of this type are usually
 * encountered in cases where data from geographic sources have be projected
 * to a planar coordinate system.
 * <p>
 * <strong>Linear Units: </strong> Although a specification of units for the
 * values in the source file is not supported by the text format, applications
 * can specify linear units through the use of the setLinearUnits() method. The
 * primary use for this feature is in the case of geographic coordinates. Linear
 * units are used to scale coordinates when geographic coordinates (latitude,
 * longitude) are transformed to Cartesian (planar) coordinates.
 *
 */
public class VertexReaderText implements Closeable, IVertexReader {

  private final File file;
  private char delimiter;

  double xMin, xMax, yMin, yMax, zMin, zMax;
  boolean isSourceInGeographicCoordinates;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;
  ICoordinateTransform coordinateTransform;

  public VertexReaderText(File file) throws IOException {
    if (file == null) {
      throw new NullPointerException("Null file specification");
    }
    if (!file.exists()) {
      throw new IOException("Specified file does not exist: " + file.getPath());
    }

    this.file = file;
    delimiter = 0;
    String name = file.getName();
    if (name != null) {
      int i = name.lastIndexOf('.');
      if (name.length() - i == 4) {
        String ext = name.substring(i, name.length());
        if (".csv".equalsIgnoreCase(ext)) {
          delimiter = ',';
        }
      }
    }
    if (delimiter == 0) {
      delimiter = scanFileForDelimiter(file);
    }
  }

  @Override
  public List<Vertex> read(IMonitorWithCancellation monitor) throws IOException {
    if (delimiter == 0) {
      throw new IOException("Unable to deduce delimiter character for file");
    }
    List<Vertex> list = readDelimitedFile(file, delimiter);

    if (list.isEmpty()) {
      xMin = Double.NaN;
      xMax = Double.NaN;
      yMin = Double.NaN;
      yMax = Double.NaN;
      zMin = Double.NaN;
      zMax = Double.NaN;
    } else {
      Vertex v = list.get(0);
      xMin = v.getX();
      xMax = xMin;
      yMin = v.getY();
      yMax = yMin;
      zMin = v.getZ();
      zMax = zMin;
      for (Vertex vertex : list) {
        double x = vertex.getX();
        double y = vertex.getY();
        double z = vertex.getZ();

        if (x < xMin) {
          xMin = x;
        } else if (x > xMax) {
          xMax = x;
        }
        if (y < yMin) {
          yMin = y;
        } else if (y > yMax) {
          yMax = y;
        }
        if (z < zMin) {
          zMin = z;
        } else if (z > zMax) {
          zMax = z;
        }
      }

    }

    return list;

  }

  List<Vertex> readDelimitedFile(File file, char delimiter)
          throws IOException {

    // The header logic includes special rules for columns giving z
    // values including "depth" and "elevation".  But "z" is preferred
    // over depth and elevation. So the z-alternate flag values will be used
    // only if z values are not set.
    try (DelimitedReader dlim = new DelimitedReader(file, delimiter)) {
      List<String> sList = dlim.readStrings();
      List<Vertex> vList = new ArrayList<>();
      boolean headerRow = false;
      int xColumn = 0;
      int yColumn = 1;
      int zColumn = 2;
      int iColumn = -1;
      int zAltColumn = -1;
      int nColumnsRequired = 3;
      boolean geoText = false;
      boolean xFound = false;
      boolean yFound = false;
      boolean zFound = false;
      boolean zAltFound = false;
      int k = 0;
      for (String s : sList) {
        String sLower = s.toLowerCase();
        char c = s.charAt(0);
        if (Character.isAlphabetic(c) || c == '_') {
          headerRow = true;
          int n = k + 1;
          if(sLower.contains("acc")||sLower.contains("err")||sLower.contains("certain")){
              // skip columns that give "accuracy", "error", or "uncertainty"
              continue;
          }
          if ("x".equalsIgnoreCase(s)) {
            xFound = true;
            xColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if ("y".equalsIgnoreCase(s)) {
            yFound = true;
            yColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if ("z".equalsIgnoreCase(s)) {
            zFound=true;
            zColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if (sLower.startsWith("depth")|| sLower.startsWith("elev")) {
            zAltFound=true;
            zAltColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if (sLower.startsWith("lon")) {
            geoText = true;
            xColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if (sLower.startsWith("lat")) {
            geoText = true;
            yColumn = k;
            if (n > nColumnsRequired) {
              nColumnsRequired = n;
            }
          } else if("i".equalsIgnoreCase(s) || "index".equalsIgnoreCase(s)){
            iColumn = k;
          }
        }
        k++;
      }

      if(!zFound && zAltFound){
          zColumn = zAltColumn;
      }

      int iVertex = 0;
      // The first row gets special processing.  If there was a header
      // row, we still haven't read any data (just the header)
      if (headerRow) {
        if(xFound && yFound && geoText){
          // if both  cartesian specifications and geographic coordinates
          // were provided, we assume that the (x,y) gives data in a projected
          // coordinate system and takes precedence.
          geoText = false;
        }
        sList = dlim.readStrings();
        if (sList.size() < nColumnsRequired) {
          throw new IOException("Insufficient columns in line "
                  + dlim.getLineNumber());
        }
        try {
          double x = Double.parseDouble(sList.get(xColumn));
          double y = Double.parseDouble(sList.get(yColumn));
          double z = Double.parseDouble(sList.get(zColumn));
          if(iColumn>=0){
            iVertex = Integer.parseInt(sList.get(iColumn));
          }
          if (geoText && coordinateTransform == null) {
            coordinateTransform
                    = new SimpleGeographicTransform(y, x, linearUnits);
            isSourceInGeographicCoordinates = true;
            CoordinatePair c = new CoordinatePair();
            boolean status = coordinateTransform.forward(x, y, c);
            if (!status) {
              throw new IOException("Invalid transformation for coordinates in line "
                      + dlim.getLineNumber());
            }
            x = c.x;
            y = c.y;
          }
          vList.add(new Vertex(x, y, z, iVertex));
          iVertex++;
        } catch (NumberFormatException nex) {
          throw new IOException("Invalid numeric format in "
                  + dlim.getLineNumber(), nex);
        }
      }

      // standard processing for rest of file
      try {
        CoordinatePair c = new CoordinatePair();
        while (!(sList = dlim.readStrings()).isEmpty()) {
          if (sList.size() < nColumnsRequired) {
            throw new IOException("Insufficient columns in line "
                    + dlim.getLineNumber());
          }
          double x = Double.parseDouble(sList.get(xColumn));
          double y = Double.parseDouble(sList.get(yColumn));
          double z = Double.parseDouble(sList.get(zColumn));
          if (iColumn >= 0) {
            iVertex = Integer.parseInt(sList.get(iColumn));
          }
          if (coordinateTransform != null) {
            boolean status = coordinateTransform.forward(x, y, c);
            if (!status) {
              throw new IOException("Undefined coordinates in line "
                      + dlim.getLineNumber() + ": " + x + ", " + y);
            }
            x = c.x;
            y = c.y;
          }
          vList.add(new Vertex(x, y, z, iVertex)); // NOPMD
          iVertex++;

        }
      } catch (NumberFormatException nex) {
        throw new IOException("Invalid numeric format in "
                + dlim.getLineNumber(), nex);
      }

      return vList;
    }
  }

  /**
   * Gets the delimiter character for a file. Normally, this delimiter is
   * determined in the constructor, but it can be set by applications if
   * desired.
   *
   * @return a valid character or a zero if no delimiter could be determined (in
   * which case, the file is treated as unreadable)
   */
  public char getDelimiter() {
    return delimiter;
  }

  /**
   * Sets the delimiter character for a file.
   *
   * @param delimiter a valid character
   */
  public void setDelimiter(char delimiter) {
    if (delimiter == 0) {
      throw new IllegalArgumentException("Invalid delimiter (charactter zero)");
    }
    this.delimiter = delimiter;
  }

  /**
   * Attempts to determine the delimiter character used in a text (ASCII) file
   * based on its content. Candidate symbols in order of precedence are the pipe
   * symbol (vertical bar), tabs, and spaces, and commas in that order
   *
   * @param file a valid file specification
   * @return if successful, a non-zero character.
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  private char scanFileForDelimiter(File file) throws IOException {
    try (FileInputStream fins = new FileInputStream(file)) {
      BufferedInputStream bins = new BufferedInputStream(fins);
      boolean commentLine = false;
      boolean commaFound = false;
      boolean pipeFound = false;
      boolean textFound = false;
      int embeddedWhitespace = 0;
      int embeddedWhitespaceCandidate = 0;
      boolean escape = false;
      int c;
      while ((c = bins.read()) > 0) {
        if (escape) {
          escape = false;
          continue;
        }
        if (commentLine) {
          if (c == '\n') {
            commentLine = false;
          }
          continue;
        }
        if (c == '\n') {
          if (textFound) {
            break;
          }
          continue;
        }

        if (Character.isWhitespace(c)) {
          // we always ignore leading and trailing whitespace.
          // if we've found some text on the current line, c could be either
          // some embedded whitespace or trailing whitespace... so we treat
          // c as a "candidate" until the next time non-whitespace is
          // encountered.  Also, embedded whitespace has a precedence,
          // with tabs taking priority over spaces
          if (textFound) {
            if (c == '\t') {
              embeddedWhitespaceCandidate = c;
            } else if (c == ' ' && embeddedWhitespaceCandidate == 0) {
              embeddedWhitespaceCandidate = c;
            }
          }
        } else {
          // for embedded whitespace, a tab has precedence
          // so if embedded whitespace is already set to tab,
          // no further action is required.
          if (embeddedWhitespaceCandidate != 0 && embeddedWhitespace != '\t') {
            embeddedWhitespace = embeddedWhitespaceCandidate;
          }

          if (c == '#' && !textFound) {
              commentLine = true;
              continue;
          }
          textFound = true;
          if (c == '\\') {
            // escape the next symbol
            escape = true;
            continue;
          }
          if (c == ',') {
            commaFound = true;
          } else if (c == '|') {
            pipeFound = true;
          }
        }
      }
      if (pipeFound) {
        return '|';
      }
      if (commaFound) {
        return ',';
      }
      if (embeddedWhitespace > 0) {
        return (char) embeddedWhitespace;
      }

      // we found nothing that could be identified as a delimiter.
      // default to a space
      return 0;

    } catch (IOException ioex) {
      throw ioex;
    }
  }

  @Override
  public void close() throws IOException {
    // no action required at this time
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
   * Indicates whether the source data was in geographic coordinates
   *
   * @return true if the source data used geographic coordinates; otherwise,
   * false.
   */
  @Override
  public boolean isSourceInGeographicCoordinates() {
    return isSourceInGeographicCoordinates;
  }

  /**
   * Gets the linear units for the coordinate system used by the data. It is
   * assumed that the vertical and horizontal coordinate systems will be in the
   * same unit system, though assumption could change in a future
   * implementation.
   *
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits() {
    return linearUnits;
  }

  /**
   * Sets the linear units for the coordinate system used by the horizontal (x
   * and y) coordinates of the data. This method is intended for cases when the
   * application can provide information that is not otherwise available in the
   * source data.
   *
   * @param linearUnits a valid instance
   */
  public void setLinearUnits(LinearUnits linearUnits) {
    if (linearUnits == null) {
      this.linearUnits = LinearUnits.UNKNOWN;
    } else {
      this.linearUnits = linearUnits;
    }
  }

  /**
   * Gets the coordinate transform associated with this instance. May be null if
   * no coordinate transform was set.
   *
   * @return a valid transform or a null if none was set.
   */
  @Override
  public ICoordinateTransform getCoordinateTransform() {
    return coordinateTransform;
  }

  /**
   * Sets a coordinate transform to be used for mapping values from the source
   * file to vertex coordinates.
   *
   * @param transform a valid transform or a null if none is to be applied.
   */
  @Override
  public void setCoordinateTransform(ICoordinateTransform transform) {
    this.coordinateTransform = transform;
  }
}
