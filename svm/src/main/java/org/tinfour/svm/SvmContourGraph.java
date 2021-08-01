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
 * 08/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.contour.Contour;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourIntegrityCheck;
import org.tinfour.contour.ContourRegion;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.gis.shapefile.ShapefileType;
import org.tinfour.gis.shapefile.ShapefileWriter;
import org.tinfour.gis.shapefile.ShapefileWriterSpecification;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.utils.AxisIntervals;
import org.tinfour.utils.SmoothingFilter;
import org.tinfour.utils.rendering.RenderingSurfaceAid;

/**
 * A utility for writing contour graphs.
 */
class SvmContourGraph {

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

  private static Color getColor(double index, double iMin, double iMax) {
    int i = (int) ((paletteB2Y.length - 1) * index / (iMax - iMin));
    return new Color(
      paletteB2Y[i][0],
      paletteB2Y[i][1],
      paletteB2Y[i][2]
    );
  }

  private SvmContourGraph() {
    // a private constructor to deter application code from
    // constructing instances of this class.
  }

  static void write(
    PrintStream ps,
    SvmProperties properties,
    SvmBathymetryData data,
    double shoreReferenceElevation,
    IIncrementalTin tin) {

    boolean useDepthModel = false;
    boolean hasShorelineElevation = false;
    boolean produceElevations = false;
    if (shoreReferenceElevation > 0) {
      hasShorelineElevation = true;
    }

    if (properties.isBathymetryModelSpecified()) {
      SvmBathymetryModel bathyModel = properties.getBathymetryModel();
      if (bathyModel.isDepth()) {
        useDepthModel = true;
        produceElevations = hasShorelineElevation;
      } else {
        useDepthModel = false;
        produceElevations = true;
      }
    }

    File output = properties.getContourGraphFile();
    if (output == null) {
      // the properties did not request a contour graph,
      // so don't write one.
      return;
    }

    ps.println("Constructing smoothing filter");
    SmoothingFilter filter = new SmoothingFilter(tin);
    ps.println("Time to construct smoothing filter "
      + filter.getTimeToConstructFilter() + " ms");

    double zMin = filter.getMinZ();
    double zMax = filter.getMaxZ();

    List<PolygonConstraint> boundaryConstraints = new ArrayList<>();
    List<IConstraint> allConstraints = tin.getConstraints();
    int maxIndex = 0;
    for (IConstraint icon : allConstraints) {
      if (icon instanceof PolygonConstraint) {
        if (icon.getConstraintIndex() > maxIndex) {
          maxIndex = icon.getConstraintIndex();
        }
        boundaryConstraints.add((PolygonConstraint) icon);
      }
    }

    boolean[] water = new boolean[maxIndex + 1];
    for (PolygonConstraint p : boundaryConstraints) {
      Object o = p.getApplicationData();
      if (o instanceof Boolean && (Boolean) o) {
        water[p.getConstraintIndex()] = true;
      }
    }

    // even if all vertices in the source were inside the boundary constraints
    // the flat-fixer logic would have create boundaries outside the
    // constraints (that's a bug, not a feature).  In either case,
    // it is necessary to force outside values to be at least the
    // shoreline reference elevation.
    ps.println("\nChecking for vertices lying outside of constraints");
    IIncrementalTinNavigator navigator = tin.getNavigator();

    long time0 = System.currentTimeMillis();
    int nOutsiders = 0;
    List<Vertex> vList = tin.getVertices();
    double[] zArray = filter.getVertexAdjustments();
    for (Vertex v : vList) {
      int index = v.getIndex();
      double x = v.getX();
      double y = v.getY();
      IQuadEdge test = navigator.getNeighborEdge(x, y);
      IConstraint con = tin.getRegionConstraint(test);
      if (con == null || !water[con.getConstraintIndex()]) {
        nOutsiders++;
        if (zArray[index] < shoreReferenceElevation) {
          zArray[index] = shoreReferenceElevation;
        }
      }
    }
    filter.setVertexAdjustments(zArray);
    long time1 = System.currentTimeMillis();
    ps.println("Found " + nOutsiders
      + " vertices outside constraints,"
      + "check required " + (time1 - time0) + " ms");

    // For different data sets, we will need different contour intervals.
    // Attempt to create a specification with about 10 contour intervals
    // using the axis too.  Then, take the results from the axis tool
    // and create our zContour array.  Not that the zContours must be
    // greater than the zMin value and less than the shoreReferenceElevation
    double[] aArray = null;
    double contourInterval = properties.getContourGraphInterval();
    if (contourInterval > 0) {
      long i0 = (long) Math.ceil(zMin / contourInterval);
      long i1 = (long) Math.floor(zMax / contourInterval);
      int nC = (int) (i1 - i0 + 1);
      if (nC >= 1 && nC <= 100) {
        aArray = new double[nC];
        for (int i = 0; i < nC; i++) {
          aArray[i] = (i + i0) * contourInterval;
        }
      }
    }

    AxisIntervals aIntervals = AxisIntervals.computeIntervals(
      zMin,
      zMax,
      2,
      1,
      40,
      false);
    if (aArray == null) {
      aArray = aIntervals.getLabelCoordinates();
    }
    int i0 = -1;
    int i1 = 0;
    for (int i = 0; i < aArray.length; i++) {
      if (i0 == -1 && aArray[i] > zMin) {
        i0 = i;
      }
      if (aArray[i] < shoreReferenceElevation) {
        i1 = i;
      }
    }

    if (i0 == -1) {
      ps.format("Failed to construct intervals for contour plot");
      return;
    }
    double[] zContour = new double[i1 - i0 + 1];
    for (int i = i0; i <= i1; i++) {
      zContour[i - i0] = aArray[i];
    }
    double[] zBandMin = new double[zContour.length + 1];
    double[] zBandMax = new double[zContour.length + 1];
    zBandMin[0] = zContour[0] - contourInterval;
    zBandMax[0] = zContour[0];
    for (int i = 1; i < zContour.length; i++) {
      zBandMin[i] = zContour[i - 1];
      zBandMax[i] = zContour[i];
    }
    zBandMin[zContour.length] = zBandMax[zContour.length - 1];
    zBandMax[zContour.length] = shoreReferenceElevation;

    double simplificationFactor;
    if(contourInterval>0){
      // the properties specified a contour interval
      double s = contourInterval/8;
      simplificationFactor = s*s;
    }else if (zContour.length>2){
      // the properties did not specify a contour interval,
      // so the interval was derived using the axis-interval logic.
      double s = (zContour[1]-zContour[0])/8;
      simplificationFactor = s*s;
    }else{
      simplificationFactor = 0.5;
    }

    ps.println("\nBuilding contours for graph");
    ContourBuilderForTin builder
      = new ContourBuilderForTin(tin, filter, zContour, true);
    builder.simplify(simplificationFactor);

    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    builder.summarize(ps, areaFactor);
    ContourIntegrityCheck check = new ContourIntegrityCheck(builder);
    check.inspect();
    ps.println("Contour integrity check status: " + check.getMessage());

    Dimension dimension = properties.getContourGraphDimensions();
    int width = dimension.width;
    int height = dimension.height;

    Rectangle2D bounds = tin.getBounds();
    double x0 = bounds.getMinX();
    double y0 = bounds.getMinY();
    double x1 = bounds.getMaxX();
    double y1 = bounds.getMaxY();
    RenderingSurfaceAid rsa;

    rsa = new RenderingSurfaceAid(width, height, 10, x0, y0, x1, y1);
    rsa.fillBackground(Color.white);

    BufferedImage bImage = rsa.getBufferedImage();
    Graphics2D g2d = rsa.getGraphics2D();
    AffineTransform af = rsa.getCartesianToPixelTransform();
    g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

    // The first step is to draw a color-fill representation of the
    // bounding (shoreline) constraint for the body of water.
    int iN = zContour.length;
    Color color = getColor(iN, 0, iN);
    g2d.setColor(color);
    for (PolygonConstraint p : boundaryConstraints) {
      if (water[p.getConstraintIndex()]) {
        g2d.setColor(color);
      } else {
        g2d.setColor(Color.white);
      }
      Path2D path = p.getPath2D(af);
      g2d.fill(path);
      g2d.draw(path);
    }

    // Next, draw the area filled regions
    List<ContourRegion> regions = builder.getRegions();
    for (ContourRegion region : regions) {
      int rIndex = region.getRegionIndex();
      if (rIndex >= iN) {
        continue;
      }
      color = getColor(rIndex, 0, iN);
      g2d.setColor(color);
      Path2D path = region.getPath2D(af);
      g2d.fill(path);
      g2d.draw(path);
    }

    // Next, draw the contours in semi-transparent gray
    g2d.setColor(Color.black); // new Color(128, 128, 128, 128));

    List<Contour> contours = builder.getContours();
    for (Contour c : contours) {
      if (c.getContourType() == Contour.ContourType.Interior) {
        Path2D path = c.getPath2D(af);
        g2d.draw(path);
      }
    }

    // Draw the land-areas in gray
    g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
    g2d.setColor(Color.white);
    for (PolygonConstraint p : boundaryConstraints) {
      if (p.getArea() < 0) {
        Path2D path = p.getPath2D(af);
        g2d.fill(path);
      }
    }

    //Draw the shoreline.  Do this last so that it
    // is on top of all other water features and gives a strong finish
    // to the rendering.
    g2d.setColor(Color.black);
    for (IConstraint con : boundaryConstraints) {
      if (con instanceof PolygonConstraint) {
        PolygonConstraint p = (PolygonConstraint) con;
        Path2D path = p.getPath2D(af);
        g2d.draw(path);
      }
    }

//    g2d.setStroke(new BasicStroke(1.0f));
//    g2d.setColor(Color.gray);
//    for(IQuadEdge edge: tin.edges()){
//      Vertex A = edge.getA();
//      Vertex B = edge.getB();
//      double []c = new double[8];
//      c[0] = A.getX();
//      c[1] = A.getY();
//      c[2] = B.getX();
//      c[3] = B.getY();
//      af.transform(c, 0, c, 4, 2);
//      Line2D l2d = new Line2D.Double(c[4], c[5], c[6], c[7]);
//      g2d.draw(l2d);
//
//    }
    g2d.setColor(Color.gray);
    g2d.drawRect(0, 0, width - 1, height - 1);

    BufferedImage compositeImage
      = new BufferedImage(width, height + 200, BufferedImage.TYPE_INT_ARGB);
    g2d = compositeImage.createGraphics();
    g2d.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setColor(Color.white);
    g2d.fillRect(0, 0, width + 1, height + 200 + 1);
    g2d.drawImage(bImage, 0, 0, null);

    Font font = new Font("Arial", Font.PLAIN, 22);
    Font legendFont = new Font("Arial", Font.BOLD, 22);
    String labFmt = aIntervals.getLabelFormat();
    if (contourInterval - Math.floor(contourInterval) > 1.0e-5 && labFmt.contains(".0f")) {
      // fractional contour interval, but the labeling logic decided on integer
      // labels.  So we adjust accordingly
      labFmt = "%3.1f";
    }
    String[] label = new String[zContour.length + 1];
    String testFmt = labFmt + " to " + labFmt;
    if (useDepthModel) {
      label[0] = String.format("Below " + labFmt, shoreReferenceElevation - zContour[0]);
      for (int i = 1; i < zContour.length; i++) {
        label[i] = String.format(testFmt,
          shoreReferenceElevation - zContour[i - 1],
          shoreReferenceElevation - zContour[i]);
      }
      label[zContour.length]
        = String.format("Above " + labFmt,
          shoreReferenceElevation - zContour[zContour.length - 1]);
    } else {
      label[0] = String.format("Below " + labFmt, zContour[0]);
      for (int i = 1; i < zContour.length; i++) {
        label[i] = String.format(testFmt,
          zContour[i - 1],
          zContour[i]);
      }
      label[zContour.length]
        = String.format("Above " + labFmt, zContour[zContour.length - 1]);
    }

    FontRenderContext frc = new FontRenderContext(null, true, true);
    TextLayout[] layout = new TextLayout[label.length];
    double xLabMax = 0;
    double yLabMax = 0;
    for (int i = 0; i < label.length; i++) {
      layout[i] = new TextLayout(label[i], font, frc);
      Rectangle2D r2d = layout[i].getBounds();
      if (r2d.getMaxX() > xLabMax) {
        xLabMax = r2d.getMaxX();
      }
      if (r2d.getMaxY() > yLabMax) {
        yLabMax = r2d.getMaxY();
      }
    }
    if (yLabMax < 20) {
      yLabMax = 20;
    }

    double yLegend = height + 10 + yLabMax;
    g2d.setFont(legendFont);
    g2d.setColor(Color.black);
    g2d.drawString(properties.getContourGraphLegendText(), 20, (int) yLegend);

    int nCol = (label.length + 4) / 5;
    double colWidth = 30 + 5 + xLabMax + 30;
    int k = 0;
    legendPlotLoop:
    for (int iCol = 0; iCol < nCol; iCol++) {
      double xCol = colWidth * iCol + 30;
      for (int iRow = 0; iRow < 5; iRow++) {
        double yRow = iRow * (yLabMax + 5) + yLegend + yLabMax;
        color = getColor(k, 0, iN);
        g2d.setColor(color);
        Rectangle2D r2d = new Rectangle2D.Double(xCol, yRow, 30, yLabMax);
        g2d.fill(r2d);
        g2d.setColor(Color.gray);
        g2d.draw(r2d);
        g2d.setColor(Color.black);
        r2d = layout[k].getBounds();
        float xLab = (float) (xCol + 35);
        float yLab = (float) (yRow + yLabMax / 2 - r2d.getY() / 2);
        layout[k].draw(g2d, xLab, yLab);
        k++;
        if (k == label.length) {
          break legendPlotLoop;
        }
      }
    }

    try {
      ImageIO.write(compositeImage, "PNG", output);
    } catch (IOException ioex) {
      ps.println("IOException writing " + output.getAbsolutePath()
        + ", " + ioex.getMessage());
    }

    File shapefileRef = properties.getContourRegionShapefile();
    if (shapefileRef != null) {
      removeOldShapefiles(ps, shapefileRef);

      ps.println("Writing shapefile " + shapefileRef.getPath());

      int iRegion = 0;
      for (ContourRegion region : regions) {
        int rIndex = region.getRegionIndex();
        if (rIndex >= iN) {
          continue;
        }
        iRegion++;
        region.setApplicationIndex(iRegion);
      }

      ShapefileWriterSpecification regionSpec = new ShapefileWriterSpecification();
      regionSpec.setShapefileType(ShapefileType.Polygon);
      regionSpec.addIntegerField("feature_id", 8);
      regionSpec.addIntegerField("parent_id", 8);
      regionSpec.addIntegerField("band_idx", 4);
      regionSpec.addFloatingPointField("band_min", 8, 2, false);
      regionSpec.addFloatingPointField("band_max", 8, 2, false);

      regionSpec.addFloatingPointField("Shape_area", 13, 6, true);
      regionSpec.setShapefilePrjContent(data.getShapefilePrjContent());

      try (ShapefileWriter regionWriter = new ShapefileWriter(shapefileRef, regionSpec);) {
        for (ContourRegion region : regions) {
          int rIndex = region.getRegionIndex();
          if (rIndex >= iN) {
            continue;
          }
          double bandMin = zBandMin[rIndex];
          double bandMax = zBandMax[rIndex];

          ShapefileRecord record = regionWriter.createRecord();
          List<ContourRegion> holes = region.getEnclosedRegions();
          double[] xy = region.getXY();
          record.addPolygon(xy.length / 2, xy, false);
          for (ContourRegion hole : holes) {
            xy = hole.getXY();
            record.addPolygon(xy.length / 2, xy, true);
          }

          int parent_id = 0;
          ContourRegion parent = region.getParent();
          if (parent != null) {
            parent_id = parent.getApplicationIndex();
          }

          regionWriter.setDbfFieldValue("feature_id", region.getApplicationIndex());
          regionWriter.setDbfFieldValue("parent_id", parent_id);
          regionWriter.setDbfFieldValue("band_idx", region.getRegionIndex());
          regionWriter.setDbfFieldValue("Shape_area", region.getAdjustedArea());
          if (useDepthModel) {
            regionWriter.setDbfFieldValue("band_min", shoreReferenceElevation - bandMin);
            regionWriter.setDbfFieldValue("band_max", shoreReferenceElevation - bandMax);
          } else {
            regionWriter.setDbfFieldValue("band_min", bandMin);
            regionWriter.setDbfFieldValue("band_max", bandMax);
          }

          regionWriter.writeRecord(record);
        }

      } catch (IOException ioex) {
        ps.println("Encounted IOException while writing contour-region shapefile " + ioex.getMessage());
        return;
      }
    }

    shapefileRef = properties.getContourLineShapefile();
    if (shapefileRef != null) {
      removeOldShapefiles(ps, shapefileRef);
      ps.println("Writing shapefile " + shapefileRef.getPath());
      ShapefileWriterSpecification contourSpec = new ShapefileWriterSpecification();
      contourSpec.setShapefileType(ShapefileType.PolyLine);
      contourSpec.addIntegerField("feature_id", 8);
      contourSpec.addIntegerField("cntr_idx", 4);
      contourSpec.addFloatingPointField("depth", 8, 2, false);
      if (produceElevations) {
        contourSpec.addFloatingPointField("elevation", 8, 2, false);
      }
      contourSpec.addFloatingPointField("Shape_len", 13, 6, true);
      contourSpec.setShapefilePrjContent(data.getShapefilePrjContent());

      int nContour = 0;
      try (ShapefileWriter contourWriter = new ShapefileWriter(shapefileRef, contourSpec);) {
        for (Contour contour : contours) {
          if (contour.isBoundary()) {
            continue;
          }
          nContour++;
          int cIndex = contour.getLeftIndex();
          double z = contour.getZ();

          ShapefileRecord record = contourWriter.createRecord();

          double[] xy = contour.getXY();
          record.addPolyLine(xy.length / 2, xy);

          double dSum = 0;
          for (int i = 1; i < xy.length / 2; i++) {
            double dx = xy[i * 2] - xy[i * 2 - 2];
            double dy = xy[i * 2 + 1] - xy[i * 2 - 1];
            dSum += Math.sqrt(dx * dx + dy * dy);
          }
          contourWriter.setDbfFieldValue("feature_id", nContour);
          contourWriter.setDbfFieldValue("cntr_idx", cIndex);
          contourWriter.setDbfFieldValue("depth", shoreReferenceElevation - z);
          if (produceElevations) {
            contourWriter.setDbfFieldValue("elevation", z);
          }
          contourWriter.setDbfFieldValue("Shape_len", dSum);

          contourWriter.writeRecord(record);
        }

      } catch (IOException ioex) {
        ps.println("Encounted IOException while writing contour-line shapefile " + ioex.getMessage());
        return;
      }
    }

  }

  static final String[] targetExtensions = {"shp", "shx", "dbf", "sbn", "prj"};

  static void removeOldShapefiles(PrintStream ps, File shapefile) {
    String ext = getFileExtension(shapefile);
    if (!"shp".equalsIgnoreCase(ext)) {
      return;
    }
    File parent = shapefile.getParentFile();
    if (parent == null) {
      parent = new File(".");
    }

    String name = shapefile.getName();
    String basename = name.substring(0, name.length() - 4);
    for (String s : targetExtensions) {
      name = basename + '.' + matchCase(ext, s);
      File target = new File(parent, name);
      if (target.exists()) {
        //ps.println("Removing old shapefile element: " + target.getPath());
        target.delete();
      }
    }
  }

  private static String getFileExtension(File file) {
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
   * When we are trying to find one of the Shapefile auxilliary files
   * (.dbf, .prj. etc.), we try to format the file extension to match the
   * same case structure as the Shapefile. This is relevant under Linux which
   * uses case-sensitive file name. So if we have the extension .SHP, we would
   * use .PRJ, not .prj, etc.
   *
   * @param source the extension from the source string.
   * @param target the extension that we wish to format.
   * @return if successful, the target extension with the proper case structure.
   */
  private static String matchCase(String source, String target) {
    StringBuilder sb = new StringBuilder();
    int i = 0;

    for (i = 0; i < target.length(); i++) {
      char s;
      if (i < source.length()) {
        s = source.charAt(i);
      } else {
        s = source.charAt(source.length() - 1);
      }
      char t = target.charAt(i);
      if (Character.isLowerCase(s) && Character.isUpperCase(t)) {
        t = Character.toLowerCase(t);
      } else if (Character.isUpperCase(s) && Character.isLowerCase(t)) {
        t = Character.toUpperCase(t);
      }
      sb.append(t);
    }
    return sb.toString();
  }

}
