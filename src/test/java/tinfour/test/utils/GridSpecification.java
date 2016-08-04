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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Provides metadata for describing a grid with convenience routines for
 * writing data to Esri's ASCII raster format (&#46;asc files).
 */
public class GridSpecification {

  /**
   * Specifies how the cells are oriented relative to the coordinates of the
   * of the grid. If CenterOfCell is specified, then the coordinates
   * for the lower-left corner of the grid would lie in the center of the
   * cell. If the CornerOfCell argument was specified, then the corner of
   * the grid would lay on the lower-left corner of the cell.
   */
  public enum CellPosition {

    /**
     * The coordinates give the center of the cell.
     */
    CenterOfCell,
    /**
     * The coordinates given the lower-left corner of the cell
     */
    CornerOfCell
  }

  private final CellPosition cellPosition;
  private final double cellSize;
  private final double xLowerLeft;
  private final double yLowerLeft;
  private final double xUpperRight;
  private final double yUpperRight;
  private final int nRows;
  private final int nCols;
  private final int nCells;
  private final double xCoordinateScale;
  private final double yCoordinateScale;
  private final double xCoordinateOffset;
  private final double yCoordinateOffset;

  /**
   * Constructs an instance based on a specified set of bounds with the
   * requirement that the grid coordinates are integral multiples of
   * the cellSize.
   * <p>
   * Because the grid coordinate points will
   * be aligned on integral multiples of the cellSize, there is no
   * guarantee that the grid coordinates will exactly match the
   * xmin/xmax, ymin/ymax values. The guarantee is that the grid
   * points will be within the specified bounds. The cell
   * alignment will be treated according to the specified CellPosition.
   * The number of rows and columns will be computed based on the
   * bounds and cell size.
   * <p>
   * <strong>Geographic Coordinate</strong> present a special problem in that
   * they are non-isotropic. The Tinfour vertex loader will optionally load
   * geographic coordinates and apply a weak map projection to convert them
   * to an isotropic coordinate system. If the source data is in geographic
   * coordinate, obtain the geoScaleX and geoScaleY values from the vertex
   * loader and pass them to this constructor so that the ASCII-formatted
   * raster file may be written correctly.
   *
   * @param cellPosition the position of the cell relative to the
   * coordinates of the grid.
   * @param cellSize the dimension of the grid cell in the same coordinate
   * system as the bounds
   * @param xmin the minimum x coordinate for the area of interest
   * @param xmax the maximum x coordinate for the area of interest
   * @param ymin the minimum y coordinate for the area of interest
   * @param ymax the maximum y coordinate for the area of interest
   * @param geoScaleX a scale factor to be used when storing data
   * from a geographic coordinate system (enter either 1.0 or 0.0 if not used)
   * @param geoScaleY a scale factor to be used when storing data
   * from a geographic coordinate system (enter either 1.0 or 0.0 if not used)
   * @param geoOffsetX an offset factor to be used when storing data
   * from a geographic coordinate system (enter 0.0 if not used)
   * @param geoOffsetY an offset factor to be used when storing data
   * from a geographic coordinate system (enter 0.0 if not used)
   */
  public GridSpecification(
    CellPosition cellPosition,
    double cellSize,
    double xmin,
    double xmax,
    double ymin,
    double ymax,
    double geoScaleX,
    double geoScaleY,
    double geoOffsetX,
    double geoOffsetY) {
    this.cellPosition = cellPosition;
    this.cellSize = cellSize;
    if (cellSize <= 0) {
      throw new IllegalArgumentException("Zero or negative cell size not allowed");
    }
    if (xmin >= xmax || ymin >= ymax) {
      throw new IllegalArgumentException("Min/max bounds incorrect");
    }
    int j0 = (int) Math.ceil(xmin / cellSize);
    int j1 = (int) Math.floor(xmax / cellSize);
    int i0 = (int) Math.ceil(ymin / cellSize);
    int i1 = (int) Math.floor(ymax / cellSize);
    nRows = (i1 - i0 + 1);
    nCols = (j1 - j0 + 1);
    nCells = nRows * nCols;
    if (nRows < 1 || nCols < 1) {
      throw new IllegalArgumentException(
        "Bounds are two small for specified cellSize");
    }
    xLowerLeft = j0 * cellSize;
    yLowerLeft = i0 * cellSize;
    xUpperRight = j1 * cellSize;
    yUpperRight = i1 * cellSize;

    if (geoScaleX <= 0 || Double.isNaN(geoScaleX)
      || geoScaleY <= 0 || Double.isNaN(geoScaleY)) {
      xCoordinateScale = 1.0;
      yCoordinateScale = 1.0;
      xCoordinateOffset = 0;
      yCoordinateOffset = 0;
    } else {
      xCoordinateScale = geoScaleX;
      yCoordinateScale = geoScaleY;
      xCoordinateOffset = geoOffsetX;
      yCoordinateOffset = geoOffsetY;
    }

  }

  /**
   * Writes a two dimensional array of values to a file in a form compatible
   * with Esri's ASCII raster file format. The array is assumed
   * to be structured to match the specifications of the current instance.
   * Esri uses the extension ".asc" to indicate ASCII raster files.
   * <p>
   * The dataFormat is a Java-style format string, such as "%f" or
   * "%8.3f". No default is provided because it is assumed that the
   * application has more valid information about the nature of the data
   * than could be readily deduced from the inputs. The no-data value
   * is usually a string giving numeric value outside the range of what
   * could be expected in the normal course of operations. These formats
   * should follow the conventions defined by Esri (see on-line
   * ArcGIS documentation for more detail).
   *
   * @param file a value file reference.
   * @param values a two-dimensional array of values to be written to
   * the output file; array must be dimensioned compatible with the
   * specifications of the current instance.
   * @param dataFormat the format converter for formatting numerical values.
   * @param noDataString the string to be used for no-data values.
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  @SuppressWarnings("ConvertToTryWithResources")
  public void writeAsciiFile(File file,
    float[][] values,
    String dataFormat,
    String noDataString) throws IOException {
    FileOutputStream fos = new FileOutputStream(file);
    BufferedOutputStream bos = new BufferedOutputStream(fos);
    PrintStream output = new PrintStream(bos);

    output.format("NCOLS %d\n", nCols);
    output.format("NROWS %d\n", nRows);
    switch (getCellPosition()) {
      case CornerOfCell:
        output.format("XLLCORNER %f\n", xCoordinateOffset+xLowerLeft/xCoordinateScale);
        output.format("YLLCORNER %f\n", yCoordinateOffset+yLowerLeft/yCoordinateScale);
        break;
      case CenterOfCell:
      default:
        output.format("XLLCENTER %f\n", xLowerLeft/xCoordinateScale);
        output.format("YLLCENTER %f\n", yLowerLeft/yCoordinateScale);
        break;
    }
    double adjCellSize = getCellSize()/yCoordinateScale;
    if (adjCellSize < 1.0e-3) {
      // probably geographic coordiates
      output.format("CELLSIZE %e\n", adjCellSize);
    } else {
      output.format("CELLSIZE %f\n", adjCellSize);
    }
    output.format("NODATA_VALUE %s\n", noDataString);
    output.flush();

    output.flush();

    for (int iRow = 0; iRow < nRows; iRow++) {
      // the first row is at the top of the raster
      for (int iCol = 0; iCol < nCols; iCol++) {
        if (iCol > 0) {
          output.append(" ");
        }
        float z = values[iRow][iCol];
        if (Float.isNaN(z)) {
          output.append(noDataString);
        } else {
          output.format(dataFormat, z);
        }
      }
      output.format("\n");
    }
    output.flush();
    output.close();
  }


  /**
   * @return the cellPosition
   */
  public CellPosition getCellPosition() {
    return cellPosition;
  }

  /**
   * @return the cellSize
   */
  public double getCellSize() {
    return cellSize;
  }

  /**
   * @return the x coordinate of the lower-left bounds of the grid
   */
  public double getLowerLeftX() {
    return xLowerLeft;
  }

  /**
   * @return the y coordinate of the lower-left bounds of the grid
   */
  public double getLowerLeftY() {
    return yLowerLeft;
  }

  /**
   * @return the x coordinate of the upper-right bounds of the grid
   */
  public double getUpperRightX() {
    return xUpperRight;
  }

  /**
   * @return the y coordinate of the upper-right bounds of the grid
   */
  public double getUpperRightY() {
    return yUpperRight;
  }

  /**
   * @return the number of rows in the grid
   */
  public int getRowCount() {
    return nRows;
  }

  /**
   * @return the number of Columns in the grid.
   */
  public int getColumnCount() {
    return nCols;
  }

  /**
   * @return the number of cells in the grid.
   */
  public int getCellCount() {
    return nCells;
  }

}
