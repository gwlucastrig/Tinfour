/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 06/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.INeighborEdgeLocator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.utils.GridSpecification;
import org.tinfour.utils.KahanSummation;

/**
 * Provides methods for producing a grid-based output of the SVM results
 */
class SvmRaster {

  /**
   * Interpolates a raster grid from the TIN and stores the results
   * to an Esri-standard ASC (grid) file.  
   * @param properties the SVM properties specifying output file name and
   * raster cell size
   * @param ps a valid instance for writing processing status information
   * @param tin a valid instance from SvmComputation
   * @param water an array mapping constraint index to water/land status
   * @param shoreReferenceElevation the reference elevation for processing
   */
  void buildAndWriteRaster(
          SvmProperties properties,
          PrintStream ps,
          IIncrementalTin tin,
          boolean[] water,
          double shoreReferenceElevation) {

    File gridFile = properties.getGridFile();
    double s = properties.getGridCellSize();
    if (gridFile==null || Double.isNaN(s)) {
      return;
    }

    ps.println("");
    ps.println("Processing raster data");
    long maxMemory = Runtime.getRuntime().maxMemory();

    KahanSummation sum = new KahanSummation();
    Rectangle2D bounds = tin.getBounds();
    double xMin = bounds.getMinX();
    double yMin = bounds.getMinY();
    double xMax = bounds.getMaxX();
    double yMax = bounds.getMaxY();

    long jMin, jMax, iMin, iMax;
    int nRows, nCols, nCells;
    boolean cellSizeAdjusted = false;
    while (true) {
      jMin = (long) Math.floor(xMin / s);
      iMin = (long) Math.floor(yMin / s);
      jMax = (long) Math.ceil(xMax / s);
      iMax = (long) Math.ceil(yMax / s);
      nRows = (int) (iMax - iMin);
      nCols = (int) (jMax - jMin);
      nCells = nRows * nCols;
      double memoryNeeded = nCells * 4.0;
      if (memoryNeeded > maxMemory * 0.75) {
        s *= 2;
        cellSizeAdjusted = true;
      } else {
        break;
      }
    }
    if (cellSizeAdjusted) {
      ps.println("Due to memory limits, cell size increased to " + s);
    }
    
    ps.format("  N Rows:    %8d%n", nRows);
    ps.format("  N Columns: %8d%n", nCols);
    ps.format("  Cell size: %8.3f%n", s);
    ps.println("");

    float[][] result = new float[nRows][nCols];
    long reportBlockSize = nRows / 10;
    long priorReportBlock = 0;
    int nCovered = 0;
    INeighborEdgeLocator locator = tin.getNeighborEdgeLocator();
    NaturalNeighborInterpolator nni = new NaturalNeighborInterpolator(tin);
    long time0 = System.nanoTime();
    for (long i = iMin; i < iMax; i++) {
      int iRow = (int) (i - iMin);
      for (long j = jMin; j < jMax; j++) {
        int jCol = (int) (j - jMin);
        double x = j * s;
        double y = i * s;
        IQuadEdge edge = locator.getNeigborEdge(x, y);
        double z = -1;
        if (testWater(edge, water)) {
          z = nni.interpolate(x, y, null);
          if (z < shoreReferenceElevation) {
            double c = (shoreReferenceElevation - z) * s * s;
            sum.add(c);
          } else {
            z = -1;
          }
        }
        if (z == -1) {
          result[iRow][jCol] = Float.NaN;
        } else {
          nCovered++;
          result[iRow][jCol] = (float) z;
        }
      }

      long reportBlock = iRow / reportBlockSize;
      if (reportBlock > priorReportBlock) {
        priorReportBlock = reportBlock;
        ps.format("%2d%% complete%n", reportBlock * 10);
        ps.flush();
      }
    }
    long time1 = System.nanoTime();
    ps.format("Time to Process Raster  %3.1f seconds %n", (time1 - time0) / 1.0e+9);
    String areaUnits = properties.getUnitOfArea().getLabel();
    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    String volumeUnits = properties.getUnitOfVolume().getLabel();
    double volumeFactor = properties.getUnitOfVolume().getScaleFactor();
    int n = sum.getSummandCount();
    double surfArea = (n * s * s) / areaFactor;
    double volume = (sum.getSum()) / volumeFactor;
    ps.format("%nComputations from Raster Methods%n");
    ps.format("  Volume              %10.8e %,20.0f %s%n", volume, volume, volumeUnits);
    ps.format("  Surface Area        %10.8e %,20.0f %s%n", surfArea, surfArea, areaUnits);
    ps.format("  Percent Covered     %4.1f%%%n", 100.0 * nCovered / (double) nCells);
    ps.println("");

    ps.format("%nWriting output to %s%n", gridFile.getPath());
    ps.flush();
    GridSpecification grid = new GridSpecification(
            GridSpecification.CellPosition.CenterOfCell,
            s,
            xMin, xMax, yMin, yMax);
    try {
      grid.writeAsciiFile(gridFile, result, "%4.1f", "-1");
    } catch (IOException ioex) {
      ps.println("Write operation failed " + ioex.getMessage());
    }

  }

  boolean testWater(IQuadEdge edge, boolean[] water) {
    if (edge.isConstrainedRegionInterior()) {
      int index = edge.getConstraintIndex();
      return water[index];
    }
    IQuadEdge fwd = edge.getForward();
    if (fwd.isConstrainedRegionInterior()) {
      int index = fwd.getConstraintIndex();
      return water[index];
    }
    IQuadEdge rev = edge.getReverse();
    if (rev.isConstrainedRegionInterior()) {
      int index = rev.getConstraintIndex();
      return water[index];
    }
    return false;
  }

}
