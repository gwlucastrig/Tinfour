/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 * 10/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.tinfour.utils.GridSpecification.CellPosition;

/**
 * Provides a utility for reading a file specified in the Esri ASCII file
 * format. This class implements a simple one-pass file access approach. The
 * constructor reads the file header to obtain the overall grid dimensions. The
 * readGrid() method reads the rest of the file. Once the grid is read, this
 * class cannot be used to perform further file access. If an application is
 * using the constructor that accepts a File, the close method should be called.
 * If the application is using the InputStream variation, the management of the
 * InputStream is left to the calling application.
 * <p>
 * Because grids can be quite large, there may be an advantage to reading only
 * part of the grid into memory. A future version of this class may include
 * access methods for reading the grid a row at a time.
 * <p>
 * The ASCII grid file format was created by Esri, Inc. and is widely documented
 * on the web. However, an official description of the format was never released
 * to the public. Therefore most information about the format has been obtained
 * through inspection by various developers.
 * <p>
 * <strong>Development Note: </strong>The ASCII grid format supports two
 * approaches to grid geometry depending on whether the specified
 * coordinates for the lower-left corner indicate the center of the 
 * cell or the edge of the cell (e.g. cell-center or cell-corner 
 * representations).  The Tinfour implementation of the cell-center approach
 * has been verified using independent software applications.  The 
 * implementation of the cell-corner approach has not been independently
 * verified. The Tinfour project welcomes the assistance of anyone with 
 * resources to perform further testing and verification of this class
 * and the GridSpecification class.
 */
public class GridFileReader implements Closeable {

  private final InputStream bins;
  private final StringBuilder sb = new StringBuilder(256);
  private final GridSpecification gridSpec;
  double noDataValue = Double.NaN;
  String noDataString = null;

  /**
   * Opens the specified file, reads the grid specification header, and prepares
   * to read the grid. The internal input stream is advanced through the header
   * and positioned at the start of the data section of the file.
   *
   * @param file a valid file reference
   * @throws IOException in the event of an unrecoverable I/O condition or
   * file-format error
   */
  public GridFileReader(File file) throws IOException {
    FileInputStream fins = new FileInputStream(file);
    bins = new BufferedInputStream(fins);
    gridSpec = readHeader();
  }

  /**
   * Reads the grid specification header, from the specified input stream and
   * prepares to read the grid. The input stream is advanced through the header
   * and positioned at the start of the data section of the file.
   * <p>
   * <strong>Performance note:</strong> It is strongly advised that the
   * specified input stream include some kind of buffering to ensure efficient
   * operations. For example, if the input is a Java FileInputStream, it should
   * be wrapped in a Java BufferedInputStream.
   *
   * @param input a valid input stream positioned to the start of the ASCII file
   * header.
   * @throws IOException in the event of an unrecoverable I/O condition or
   * file-format error
   */
  public GridFileReader(InputStream input) throws IOException {
    bins = input;
    gridSpec = readHeader();
  }

  /**
   * Gets the specification read from the file header. The specification
   * indicates the dimensions of the grid section of the file.
   *
   * @return a valid instance.
   */
  public GridSpecification getGridSpecification() {
    return gridSpec;
  }

  /**
   * Reads the grid portion of the file. This method should be called only once.
   * The grid is organized following the Esri convention giving data in
   * row-major order starting from the upper-left row of the gird.
   *
   * @return if successful, a valid instance of a fully populated grid
   * @throws IOException in the event of an unrecoverable I/O condition or
   * file-format error
   */
  public double[][] readGrid() throws IOException {
    int nRows = gridSpec.getRowCount();
    int nCols = gridSpec.getColumnCount();
    double g[][] = new double[nRows][nCols];

    int iRow = 0;
    int iCol = 0;
    sb.setLength(0);

    if (noDataString == null) {
      // the no-data value is used 
      try {
        for (iRow = 0; iRow < nRows; iRow++) {
          for (iCol = 0; iCol < nCols; iCol++) {
            g[iRow][iCol] = Double.parseDouble(readString());
          }
        }
      } catch (NumberFormatException nex) {
        throw new IOException(
                "Invalid numeric reading row " + iRow + ", column " + iCol, nex);
      }
    } else {
      // the no-data string is used 
      try {
        for (iRow = 0; iRow < nRows; iRow++) {
          for (iCol = 0; iCol < nCols; iCol++) {
            String s = readString();
            if (noDataString.equalsIgnoreCase(s)) {
              g[iRow][iCol] = Double.NaN;
            } else {
              g[iRow][iCol] = Double.parseDouble(s);
            }
          }
        }
      } catch (NumberFormatException nex) {
        throw new IOException(
                "Invalid numeric reading row " + iRow + ", column " + iCol, nex);
      }
    }

    return g;
  }

  private GridSpecification readHeader() throws IOException {
    int nCols = 0;
    int nRows = 0;
    double xLL = Double.NaN;
    double yLL = Double.NaN;
    double cellSize = Double.NaN;

    CellPosition cellPos = CellPosition.CenterOfCell;

    for (int i = 0; i < 6; i++) {
      String s = readString().toUpperCase();
      if ("NCOLS".equals(s)) {
        nCols = readIntParameter(s);
      } else if ("NROWS".equals(s)) {
        nRows = readIntParameter(s);
      } else if (s.startsWith("XLL")) {
        xLL = readDoubleParameter(s);
        if (s.contains("CENTER")) {
          cellPos = CellPosition.CenterOfCell;
        } else if (s.contains("CORNER")) {
          cellPos = CellPosition.CornerOfCell;
        }
      } else if (s.startsWith("YLL")) {
        yLL = readDoubleParameter(s);
        if (s.contains("CENTER")) {
          cellPos = CellPosition.CenterOfCell;
        } else if (s.contains("CORNER")) {
          cellPos = CellPosition.CornerOfCell;
        }
      } else if ("CELLSIZE".equals(s)) {
        cellSize = readDoubleParameter(s);
      } else if ("NODATA_VALUE".equals(s)) {
        String p = readString();
        if (p.isEmpty()) {
          throw new IOException("Missing value for NODATA_VALUE");
        }

        if ("NaN".equalsIgnoreCase(p)) {
          noDataString = p;
        }
        try {
          noDataValue = Double.parseDouble(p);
        } catch (NumberFormatException dontCare) {
          noDataString = p;
        }
      } else {
        throw new IOException("Unrecognized specification in header " + s);
      }
    }

    // we've finished scanning the header, String s should be the first 
    // data value.  Check to verify that all required parameters were
    // supplied
    if (nCols <= 0 || nRows <= 0) {
      throw new IOException("Invalid column,row specification "
              + nCols + ", " + nRows);
    }
    if (Double.isNaN(xLL) || Double.isNaN(yLL) || Double.isNaN(cellSize)) {
      throw new IOException("Invalid coordinate or cellsize specification");
    }

    double xmin, xmax, ymin, ymax;
    if (cellPos == CellPosition.CenterOfCell) {
      xmin = xLL - 0.5 * cellSize;
      ymin = yLL - 0.5 * cellSize;
      xmax = xLL + nCols * cellSize;
      ymax = yLL + nRows * cellSize;
    } else {
      xmin = xLL;
      ymin = yLL;
      xmax = xLL + nCols * cellSize;
      ymax = yLL + nRows * cellSize;
    }

    return new GridSpecification(
            cellPos,
            cellSize,
            xmin,
            xmax,
            ymin,
            ymax);

  }

  private String readString() throws IOException {
    sb.setLength(0);
// advance over any white space
    int c = bins.read();
    if (c <= 0) {
      return "";
    }
    while (Character.isWhitespace(c)) {
      c = bins.read();
      if (c <= 0) {
        return "";
      }
    }

    sb.append((char) c);
    c = bins.read();
    if (c <= 0) {
      return sb.toString();
    }
    while (!Character.isWhitespace(c)) {
      sb.append((char) c);
      c = bins.read();
      if (c <= 0) {
        break;
      }

    }
    return sb.toString();
  }

  private int readIntParameter(
          String parameter) throws IOException {
    String s = readString();
    if (s.isEmpty()) {
      throw new IOException("Missing value for " + parameter);
    }

    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException nex) {
      throw new IOException("Invalid value \"" + s
              + "\" for integer " + parameter);
    }

  }

  private double readDoubleParameter(
          String parameter) throws IOException {
    String s = readString();
    if (s.isEmpty()) {
      throw new IOException("Missing value for " + parameter);
    }

    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nex) {
      throw new IOException("Invalid value \"" + s + "\" for numeric "
              + parameter);
    }

  }

  @Override
  public void close() throws IOException {
    bins.close();
  }
}
