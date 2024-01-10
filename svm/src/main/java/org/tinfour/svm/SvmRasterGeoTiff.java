/* --------------------------------------------------------------------
 * Copyright (C) 2024  Gary W. Lucas.
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
 * 01/2024  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.imaging.ImageWriteException;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.gis.geotiff.GeoKey;
import org.tinfour.gis.geotiff.GeoTiffTableBuilder;
import org.tinfour.gis.geotiff.GtModelType;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.svm.properties.SvmUnitSpecification;
import org.tinfour.utils.KahanSummation;

/**
 * Provides methods for producing a grid-based output of the SVM results
 */
class SvmRasterGeoTiff {

  /**
   * Interpolates a raster grid from the TIN and stores the results
   * to a numerical GeoTIFF file (single-precision floating point samples).
   *
   * @param properties the SVM properties specifying output file name and
   * raster cell size
   * @param ps a valid instance for writing processing status information
   * @param tin a valid instance from SvmComputation
   * @param water an array mapping constraint index to water/land status
   * @param shoreReferenceElevation the reference elevation for processing
   */
  void buildAndWriteRaster(
    SvmProperties properties,
    SvmBathymetryData data,
    PrintStream ps,
    IIncrementalTin tin,
    boolean[] water,
    double shoreReferenceElevation) {

    File geoTiffFile = properties.getGeoTiffFile();
    double s = properties.getGridCellSize();
    if (geoTiffFile == null || Double.isNaN(s)) {
      return;
    }

    String geoTiffFileRootName = geoTiffFile.getName();

    int kTemp = geoTiffFileRootName.lastIndexOf('.');
    if (kTemp > 0) {
      String ext = geoTiffFileRootName.substring(kTemp);
      if (".tif".equalsIgnoreCase(ext) || ".tiff".equalsIgnoreCase(ext)) {
        geoTiffFileRootName = geoTiffFileRootName.substring(0, kTemp);
      }
    }

    File geoTiffParent = geoTiffFile.getParentFile();
    if (geoTiffParent == null) {
      geoTiffParent = new File(".");
    }

    // here we use the value -131072 because it results in a bit-format
    // such that the low-order 3 bytes are all zero.
    boolean useDepthModel = false;


    if (properties.isBathymetryModelSpecified()) {
      SvmBathymetryModel bathyModel = properties.getBathymetryModel();
      if (bathyModel.isDepth()) {
        useDepthModel = true;
      } else {
        useDepthModel = false;
      }
    }

    float noDataValue = -1000000f;  // raw bytes are 0xc8000000
    String gdalNoDataString = "-1000000";
    if (properties.isGeoTiffNoDataCodeSpecified()) {
      noDataValue = properties.getGeoTiffNoDataCode();
      if (noDataValue == ((int) noDataValue)) {
        gdalNoDataString = String.format("%d", (int) noDataValue);
      } else {
        gdalNoDataString = String.format("%f", noDataValue);
      }
    }

    boolean dataCompressionEnabled = properties.isGeoTiffDataCompressionEnabled();

    ps.println("");
    ps.format("Processing GeoTiff data%n");
    ps.format("  GDAL no-data code:  %s%n", gdalNoDataString);
    ps.format("  Data Compression:   %s%n", Boolean.toString(dataCompressionEnabled));

    boolean status = writeAuxiliaryFiles(ps, data, geoTiffFile, "SvmRasterTemplate.aux.xml");
    if (!status) {
      return;
    }

    KahanSummation sum = new KahanSummation();
    Rectangle2D bounds = tin.getBounds();
    double xMin = bounds.getMinX();
    double yMin = bounds.getMinY();
    double xMax = bounds.getMaxX();
    double yMax = bounds.getMaxY();

    long jMin, jMax, iMin, iMax;
    int nRows, nCols;
    long nCells;

    jMin = (long) Math.floor(xMin / s);
    iMin = (long) Math.floor(yMin / s);
    jMax = (long) Math.ceil(xMax / s);
    iMax = (long) Math.ceil(yMax / s);
    nRows = (int) (iMax - iMin) + 1;
    nCols = (int) (jMax - jMin) + 1;
    xMin = jMin * s;
    xMax = jMax * s;
    yMin = iMin * s;
    yMax = iMax * s;
    nCells = nRows * nCols;

    ps.format("  N Rows:    %8d%n", nRows);
    ps.format("  N Columns: %8d%n", nCols);
    ps.format("  Cell size: %8.3f%n", s);
    ps.println("");

    int tileSize = 256;
    int nRowsOfTiles = (int) ((nRows + tileSize - 1) / tileSize);
    int nColsOfTiles = (int) ((nCols + tileSize - 1) / tileSize);
    long rowWaste = (long) nRowsOfTiles * tileSize - nRows;
    long colWaste = (long) nColsOfTiles * tileSize - nCols;
    long totalWaste = rowWaste * colWaste;
    double wasteRatio = (double) totalWaste / (double) nCells;
    if (wasteRatio > 0.125) {
      tileSize /= 2;
      nRowsOfTiles = (int) ((nRows + tileSize - 1) / tileSize);
      nColsOfTiles = (int) ((nCols + tileSize - 1) / tileSize);
      rowWaste = nRowsOfTiles * tileSize - nRows;
      colWaste = nColsOfTiles * tileSize - nCols;
      totalWaste = rowWaste * colWaste;
    }

    int nTiles = (int) (nRowsOfTiles * nColsOfTiles);
    int nCellsInTile = (int) (tileSize * tileSize);
    byte[][] tiles = new byte[nTiles][];

    double zMin = Double.POSITIVE_INFINITY;
    double zMax = Double.NEGATIVE_INFINITY;

    long time0 = System.nanoTime();
    int nCovered = 0;
    int nUncovered = 0;

    // Populate the tiles --------------------------------
    // At present, the Apache Commons Imaging API pretty much requires
    // that we populate all of the tile data before writing it to the file.
    // In the future, there is the possibility of a new API that will permit
    // us to append tiles to the file without storing the entire set in memory.
    IIncrementalTinNavigator navigator = tin.getNavigator();
    NaturalNeighborInterpolator nni = new NaturalNeighborInterpolator(tin);
    GeoTiffTableBuilder gkTab = new GeoTiffTableBuilder();
    gkTab.setCompressionEnabled(dataCompressionEnabled);
    for (int iTileRow = 0; iTileRow < nRowsOfTiles; iTileRow++) {
      int row0 = iTileRow * tileSize;
      int row1 = row0 + tileSize;
      if (row1 > nRows) {
        row1 = nRows;
      }
      for (int iTileCol = 0; iTileCol < nColsOfTiles; iTileCol++) {
        int col0 = iTileCol * tileSize;
        int col1 = col0 + tileSize;
        if (col1 > nCols) {
          col1 = nCols;
        }
        float[] f = new float[nCellsInTile];
        Arrays.fill(f, noDataValue);
        for (int iRow = row0; iRow < row1; iRow++) {
          double y = yMax - iRow * s;
          int fOffset = (iRow - row0) * tileSize;

          for (int iCol = col0; iCol < col1; iCol++) {
            double x = xMin + iCol * s;
            IQuadEdge edge = navigator.getNeighborEdge(x, y);
            IConstraint con = tin.getRegionConstraint(edge);
            float zValue;
            if (con == null || !water[con.getConstraintIndex()]) {
              zValue = noDataValue;
              nUncovered++;
            } else {
              double z = nni.interpolate(x, y, null);
              if (Double.isFinite(z)) {
                if (useDepthModel) {
                  if (z > 0) {
                    zValue = 0;
                  } else {
                    zValue = (float) z;
                  }
                } else {
                  zValue = (float) z;
                }
                nCovered++;
                sum.add(-zValue);
                if (zValue < zMin) {
                  zMin = zValue;
                }
                if (zValue > zMax) {
                  zMax = zValue;
                }
              } else {
                nUncovered++;
                zValue = noDataValue;
              }
            }

            f[fOffset + iCol - col0] = zValue;
          }
        }

        int iTile = iTileRow * nColsOfTiles + iTileCol;
        //    tiles[iTile] = transcribeFloat2Byte(f, tileSize, false);
        tiles[iTile] = gkTab.encodeBlock(f, tileSize, tileSize);
      }
    }

    ps.format("Covered cells %5.1f%%%n", 100.0*nCovered/(nCovered+nUncovered));

    if(!properties.isGeoTiffProjectionCodeSpecified()){
      ps.println("\nWarning: no projection code was specified for GeoTIFF\n");
    }
    int projectionCode = properties.getGeoTiffProjectionCode();
    int unitOfMeasureCode = 9001; // meters
    SvmUnitSpecification unitOfDistance = properties.getUnitOfDistance();
    String uLab = unitOfDistance.getLabel().toLowerCase();
    if ("ft".equalsIgnoreCase(uLab)) {
      unitOfMeasureCode = 9002; // feet;
    } else if (uLab.startsWith("y")) {
      unitOfMeasureCode = 9012;
    }

    try {
      gkTab.addGeoKey(GeoKey.GTModelTypeGeoKey, GtModelType.ProjectedCoordinateSystem.getCode());
      gkTab.addGeoKey(GeoKey.GTRasterTypeGeoKey, 1);
      gkTab.addGeoKey(GeoKey.ProjectedCRSGeoKey, projectionCode);
      gkTab.addGeoKey(GeoKey.ProjLinearUnitsGeoKey, unitOfMeasureCode);
      gkTab.addGeoKey(GeoKey.GeogAngularUnitsGeoKey, 9102);
      gkTab.addGeoKey(GeoKey.GTCitationGeoKey, "SVM Test Output");

      double[] a = new double[6];
      a[0] = s;
      a[1] = 0;
      a[2] = 0;
      a[3] = s;
      a[4] = xMin;
      a[5] = yMax;
      AffineTransform af = new AffineTransform(a);
      gkTab.setPixelToModelTransform(af);

      gkTab.setGdalNoData(gdalNoDataString);

      gkTab.writeTiles(geoTiffFile, nCols, nRows, tileSize, tiles);

    } catch (IOException|ImageWriteException ex) {
      ps.println("IOException writing GeoTIFF file "+ex.getMessage());
    }

    long time1 = System.nanoTime();
    ps.format("Time to Process GeoTiff  %3.1f seconds %n",
      (time1 - time0) / 1.0e+9);

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

  private boolean writeAuxiliaryFiles(
    PrintStream ps,
    SvmBathymetryData data,
    File file,
    String target) {
    StringBuilder template = new StringBuilder();
    try (
      InputStream ins
      = SvmMain.class.getResourceAsStream(target)) {
      int c;
      while ((c = ins.read()) >= 0) {
        template.append((char) c);
      }
    } catch (IOException ioex) {
      ps.println("Failed to load auxiliary template " + ioex.getMessage());
      return false;
    }
    String tempStr = template.toString();
    int lenPrefix = tempStr.indexOf("<SRS>");
    int startPostfix = tempStr.indexOf("</SRS>");
    String prefix = tempStr.substring(0, lenPrefix + 5);
    String postfix = tempStr.substring(startPostfix, tempStr.length());
    template = new StringBuilder();
    template.append(prefix);
    template.append(data.getShapefilePrjContent());
    template.append(postfix);

    File parent = file.getParentFile();
    String name = file.getName() + ".aux.xml";
    File output = new File(parent, name);
    if (output.exists()) {
      output.delete();
    }
    try (
      FileOutputStream fos = new FileOutputStream(output);
      BufferedOutputStream bos = new BufferedOutputStream(fos);) {
      byte[] b = template.toString().getBytes(StandardCharsets.ISO_8859_1);
      bos.write(b);
      bos.flush();
    } catch (IOException ioex) {
      ps.println("Serious error: unable to write auxiliary file "
        + output.getPath() + ": " + ioex.getMessage());
      return false;
    }

    name = file.getName() + ".prj";
    output = new File(parent, name);
    if (output.exists()) {
      output.delete();
    }
    try (
      FileOutputStream fos = new FileOutputStream(output);
      BufferedOutputStream bos = new BufferedOutputStream(fos);) {
      String prjString = data.getShapefilePrjContent();
      byte[] b = prjString.getBytes(StandardCharsets.ISO_8859_1);
      bos.write(b);
      bos.flush();
    } catch (IOException ioex) {
      ps.println("Serious error: unable to write .prj file: "
        + output.getPath() + ": " + ioex.getMessage());
      return false;
    }

    return true;
  }

  private boolean approxEquals(double v, double d) {
     return Math.abs(v-d)<0.01;
  }

}
