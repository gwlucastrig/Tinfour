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
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.utils.KahanSummation;

/**
 * Provides methods for producing a grid-based output of the SVM results
 */
class SvmRaster {

  /**
   * A palette giving values from blue to yellow based on the CIE LCH color
   * model.
   */
  private static final int[][] paletteB2Y = {
    {0, 0, 255},
    {0, 34, 255},
    {0, 50, 255},
    {0, 63, 255},
    {0, 73, 255},
    {0, 82, 255},
    {0, 90, 255},
    {0, 97, 255},
    {0, 104, 255},
    {0, 110, 255},
    {0, 116, 255},
    {0, 121, 255},
    {0, 127, 255},
    {0, 132, 255},
    {0, 136, 255},
    {0, 141, 255},
    {0, 145, 255},
    {0, 149, 255},
    {0, 153, 255},
    {0, 157, 255},
    {0, 160, 255},
    {0, 164, 255},
    {0, 167, 255},
    {0, 170, 255},
    {0, 174, 255},
    {0, 177, 255},
    {0, 180, 255},
    {0, 183, 255},
    {0, 186, 255},
    {0, 189, 255},
    {0, 192, 255},
    {0, 195, 249},
    {0, 198, 241},
    {0, 201, 233},
    {0, 203, 224},
    {0, 206, 216},
    {0, 209, 207},
    {0, 212, 199},
    {0, 214, 190},
    {0, 217, 181},
    {0, 220, 173},
    {0, 222, 164},
    {0, 225, 156},
    {0, 227, 147},
    {0, 230, 139},
    {0, 232, 131},
    {0, 234, 122},
    {0, 236, 114},
    {0, 238, 106},
    {0, 240, 98},
    {0, 242, 90},
    {0, 244, 82},
    {0, 245, 74},
    {65, 247, 67},
    {94, 248, 59},
    {117, 250, 51},
    {136, 251, 42},
    {154, 252, 34},
    {170, 253, 25},
    {186, 253, 15},
    {200, 254, 4},
    {215, 254, 0},
    {228, 255, 0},
    {242, 255, 0},
    {255, 255, 0}
  };

  private int getRgb(double z, double zMin, double zMax) {
    double t = (z - zMin) / (zMax - zMin);
    double s = t * paletteB2Y.length;
    int r, g, b;
    if (s <= 0) {
      r = paletteB2Y[0][0];
      g = paletteB2Y[0][1];
      b = paletteB2Y[0][2];
    } else if (s >= paletteB2Y.length - 1) {
      r = paletteB2Y[paletteB2Y.length - 1][0];
      g = paletteB2Y[paletteB2Y.length - 1][1];
      b = paletteB2Y[paletteB2Y.length - 1][2];
    } else {
      int i = (int) s;
      t = s - i;
      double r0 = paletteB2Y[i][0];
      double g0 = paletteB2Y[i][1];
      double b0 = paletteB2Y[i][2];
      double r1 = paletteB2Y[i + 1][0];
      double g1 = paletteB2Y[i + 1][1];
      double b1 = paletteB2Y[i + 1][2];
      r = (int) (t * (r1 - r0) + r0 + 0.5);
      g = (int) (t * (g1 - g0) + g0 + 0.5);
      b = (int) (t * (b1 - b0) + b0 + 0.5);
    }

    return 0xff000000 | (r << 16) | (g << 8) | b;
  }

  /**
   * Interpolates a raster grid from the TIN and stores the results
   * to an Esri-standard ASC (grid) file.
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

    File gridFile = properties.getGridFile();
    double s = properties.getGridCellSize();
    if (gridFile == null || Double.isNaN(s)) {
      return;
    }

    String gridRootName = gridFile.getName();
    if (gridRootName.length() > 4) {
      int k = gridRootName.length() - 4;
      if (gridRootName.charAt(k) == '.'
        && Character.isLetterOrDigit(gridRootName.charAt(k + 1))
        && Character.isLetterOrDigit(gridRootName.charAt(k + 2))
        && Character.isLetterOrDigit(gridRootName.charAt(k + 3))) {
        gridRootName = gridRootName.substring(0, gridRootName.length() - 4);
      }
      // Future work:  test for valid extensions
    }

    File gridParent = gridFile.getParentFile();
    if (gridParent == null) {
      gridParent = new File(".");
    }


    ps.println("");
    ps.println("Processing raster data");


    boolean status = writeAuxiliaryFile(
      ps, data, gridFile, "SvmRasterTemplate.aux.xml");
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
    int nRows, nCols, nCells;

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
    double cellSize = s;

    ps.format("  N Rows:    %8d%n", nRows);
    ps.format("  N Columns: %8d%n", nCols);
    ps.format("  Cell size: %8.3f%n", s);
    ps.println("");

    File headerFile = new File(gridParent, gridRootName + ".hdr");
    gridFile = new File(gridParent, gridRootName + ".flt");
    if (headerFile.exists()) {
      headerFile.delete();
    }
    if (gridFile.exists()) {
      gridFile.delete();
    }

    try (
      FileOutputStream headerStream = new FileOutputStream(headerFile);
      BufferedOutputStream bos = new BufferedOutputStream(headerStream);
      PrintStream headerPs = new PrintStream(bos, true, StandardCharsets.US_ASCII.name())) {
      headerPs.format("NCOLS %d%n", nCols);
      headerPs.format("NROWS %d%n", nRows);
      headerPs.format("XLLCENTER %f%n", xMin);
      headerPs.format("YLLCENTER %f%n", yMin);
      headerPs.format("CELLSIZE %f%n", s);
      headerPs.format("NODATA_VALUE 1%n");
      headerPs.format("BYTEORDER MSBFIRST%n");
    } catch (IOException ioex) {
      ps.println("Failure: Fatal I/O exception writing grid file "
        + headerFile + ": " + ioex.getMessage());
      return;
    }


    double zMin = Double.POSITIVE_INFINITY;
    double zMax = Double.NEGATIVE_INFINITY;

    long time0 = System.nanoTime();
    int nCovered = 0;
    int nUncovered = 0;
    try (
      FileOutputStream gridStream = new FileOutputStream(gridFile);
      BufferedOutputStream bos = new BufferedOutputStream(gridStream);
      DataOutputStream dos = new DataOutputStream(bos)) {
      IIncrementalTinNavigator navigator = tin.getNavigator();
      NaturalNeighborInterpolator nni = new NaturalNeighborInterpolator(tin);
      for (int iRow = 0; iRow < nRows; iRow++) {
        double y = yMax - iRow * s;
        for (int iCol = 0; iCol < nCols; iCol++) {
          double x = xMin + iCol * s;
          IQuadEdge edge = navigator.getNeighborEdge(x, y);
          IConstraint con = tin.getRegionConstraint(edge);
          double zValue = 1;
          if (con == null || !water[con.getConstraintIndex()]) {
            zValue = 1;
            nUncovered++;
          } else {
            double z = nni.interpolate(x, y, null);
            if (Double.isFinite(z)) {
              if (z > 0) {
                zValue = 0;
              } else {
                zValue = z;
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
            }
          }
          dos.writeFloat((float) zValue);
        }
      }

    } catch (IOException ioex) {
      ps.println("Failure: I/O exception writing grid file "
        + gridFile + ": " + ioex.getMessage());
      return;
    }
    long time1 = System.nanoTime();
    ps.format("Time to Process Raster  %3.1f seconds %n", (time1 - time0) / 1.0e+9);
        String lengthUnits = properties.getUnitOfDistance().getLabel();
    String areaUnits = properties.getUnitOfArea().getLabel();
    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    String volumeUnits = properties.getUnitOfVolume().getLabel();
    double volumeFactor = properties.getUnitOfVolume().getScaleFactor();
    int n = sum.getSummandCount();
    double rawSurfArea = n*s*s;
    double rawVolume = sum.getSum()*s*s;
    double surfArea = rawSurfArea / areaFactor;
    double volume = rawVolume / volumeFactor;

    ps.format("%nComputations from Raster Methods%n");
    ps.format("  Volume              %,18.2f %s     %,28.1f %s^3%n",
      volume, volumeUnits, rawVolume, lengthUnits);
    ps.format("  Surface Area        %,18.2f %s     %,28.1f %s^2%n",
      surfArea, areaUnits, rawSurfArea, lengthUnits);
    ps.format("  Percent Covered     %4.1f%%%n", 100.0 * nCovered / (double) nCells);
    ps.format("  Percent Uncovered   %4.1f%%%n", 100.0 * nUncovered / (double) nCells);
    ps.println("");

    File imageFile = properties.getGridImageFile();
    if (imageFile == null) {
      // we're done
      return;
    }

    String imageFormat = null;
    String worldFileExtension;
    String test = imageFile.getName().toLowerCase();
    String imageFileRootName = imageFile.getName();
    boolean useAlpha = false;
    if (test.endsWith(".png")) {
      imageFormat = "PNG";
      worldFileExtension = ".pgw";
      useAlpha = true;
      imageFileRootName
        = imageFileRootName.substring(0, imageFileRootName.length() - 4);
    } else if (test.endsWith(".jpg")) {
      imageFormat = "JPG";
      worldFileExtension = ".jgw";
      imageFileRootName
        = imageFileRootName.substring(0, imageFileRootName.length() - 4);
    } else if (test.endsWith(".jpeg")) {
      imageFormat = "JPG";
      worldFileExtension = ".jgw";
      imageFileRootName
        = imageFileRootName.substring(0, imageFileRootName.length() - 5);
    } else {
      ps.println("Error: At this time, the specified image file format is not supported");
      return;
    }

    File worldFile
      = new File(imageFile.getParent(), imageFileRootName + worldFileExtension);
    if(worldFile.exists()){
      worldFile.delete();
    }
    if(imageFile.exists()){
      imageFile.delete();
    }

    status = writeAuxiliaryFile(
      ps, data, imageFile, "SvmRasterImageTemplate.aux.xml");
    if(!status){
      return;
    }
    try (
      FileOutputStream worldOutputStream = new FileOutputStream(worldFile);
      BufferedOutputStream bos = new BufferedOutputStream(worldOutputStream);
      PrintStream wps = new PrintStream(bos, true, "US-ASCII")) {
      // The world-file format gives the matrix in the form
      //     A    B    C
      //     D    E    F
      //     0    0    1
      // The matrix is written to the file in column-major order (go figure)
      //    Line 1   A  pixel size in X direction (assuming 0 rotation)
      //    Line 2   D  rotation factor, about Y axis (always zero)
      //    Line 3   B  rotation factor, about X axis (always zero)
      //    Line 4   E   pixel size in Y direction (assuming 0 rotation)
      //    Line 5   C   X-coordinate center of upper-left pixel (row 0, col 0)
      //    Line 6   F   Y-coordinate center of upper-left pixel (row 0, col 0)
      wps.format("%7.6f%n", cellSize); // line 1, A
      wps.format("%7.6f%n", 0.0); // line 2, D
      wps.format("%7.6f%n", 0.0); // lin 3, B
      wps.format("%7.6f%n", -cellSize); // line 4, E
      wps.format("%7.6f%n", xMin); // line 5, C
      wps.format("%7.6f%n", yMax); // line 6 F
      wps.flush();
    } catch (IOException wioex) {
      ps.println("Error writing World File " + wioex.getMessage());
      return;
    }

    int[] argb = new int[nCells];

    try (
      FileInputStream gridStream = new FileInputStream(gridFile);
      BufferedInputStream bins = new BufferedInputStream(gridStream);
      DataInputStream dins = new DataInputStream(bins)) {
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int index = iRow * nCols + iCol;
          float z = dins.readFloat();
          if (z == 1) {
            if (useAlpha) {
              argb[index] = 0;
            } else {
              argb[index] = 0xffffffff; // white
            }
          } else {
            argb[index] = getRgb(z, zMin, zMax);
          }
        }
      }
    } catch (IOException ioex) {
      ps.println("Serious error: I/O exception reading grid file");
      return;
    }

    BufferedImage bImage;
    if (useAlpha) {
      bImage = new BufferedImage(nCols, nRows, BufferedImage.TYPE_INT_ARGB);
    } else {
      bImage = new BufferedImage(nCols, nRows, BufferedImage.TYPE_INT_RGB);
    }
    bImage.setRGB(0, 0, nCols, nRows, argb, 0, nCols);
    try {
      ImageIO.write(bImage, imageFormat, imageFile);
    } catch (IOException ioex) {
      ps.println("Error writing image file " + imageFile.getPath()
        + ": " + ioex.getMessage());
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


  boolean writeAuxiliaryFile(PrintStream ps, SvmBathymetryData data, File file, String target) {
    StringBuilder template = new StringBuilder();
    try (
      InputStream ins
      = SvmMain.class.getResourceAsStream(target)) {
      int c;
      while ((c = ins.read()) >= 0) {
        template.append((char)c);
      }
    } catch (IOException ioex) {
      ps.println("Failed to load auxiliary template " + ioex.getMessage());
      return false;
    }
    String tempStr = template.toString();
    int lenPrefix = tempStr.indexOf("<SRS>");
    int startPostfix = tempStr.indexOf("</SRS>");
    String prefix = tempStr.substring(0, lenPrefix+5);
    String postfix = tempStr.substring(startPostfix, tempStr.length());
    template = new StringBuilder();
    template.append(prefix);
    template.append(data.getShapefilePrjContent());
    template.append(postfix);
	tempStr = template.toString();

    File parent = file.getParentFile();
    String name = file.getName() + ".aux.xml";
    File output = new File(parent, name);
    if(output.exists()){
      output.delete();
    }
    try (
      FileOutputStream fos = new FileOutputStream(output);
      BufferedOutputStream bos = new BufferedOutputStream(fos);) {
      for (int i = 0; i < tempStr.length(); i++) {
        char c = tempStr.charAt(i);
        bos.write(c);
      }
      bos.flush();
    } catch (IOException ioex) {
      ps.println("Serious error: unable to read template " + template + ": " + ioex.getMessage());
      return false;
    }
    return true;
  }
}
