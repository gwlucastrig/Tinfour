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
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.svm.SvmTriangleVolumeStore.AreaVolumeResult;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.svm.properties.SvmUnitSpecification;
import org.tinfour.utils.AxisIntervals;

/*
 * Provides data elements and methods for plotting a capacity graph.
 * 
 */
class SvmCapacityGraph {

  private final SvmProperties properties;
  private final List<AreaVolumeResult> resultList;
  private final double totalVolume;
  private final double minLevel;
  private final double maxLevel;

  private final SvmUnitSpecification unitOfVolume;
  private final SvmUnitSpecification unitOfLength;

  private static final int defaultImageHeightInPixels = 400;
  private static final double defaultImageHeightInPoints
          = defaultImageHeightInPixels * 72.0 / 96.0; // assuming 96 DPI
  private static final double defaultFontSizeTitle = 14;
  private static final double defaultFontSizeAxis = 12;

  private static final double fLeft = 0.1;
  private static final double fTop = 0.08;
  private static final double fRight = 0.9;
  private static final double fBottom = 0.85;

  private double computeFontSize(double dSize, double dHeight) {
    // assume 72 points per inch and a 96 DPI graphics resolution
    double dHeightInPoints = dHeight * 72 / 96;
    double f = dSize / defaultImageHeightInPoints;
    double fSize = f * dHeightInPoints;
    if (fSize < 1) {
      fSize = 1;
    }
    return fSize;
  }

  /**
   * Construct a instance with appropriate data for plotting
   *
   * @param properties a valid instance
   * @param resultList the results of the SvmComputation
   * @param shoreReferenceElevation the reference elevation for the shoreline
   * (obtained from data or properties)
   * @param totalVolume the total computed elevation of the body of water at the
   * shore reference elevation
   */
  SvmCapacityGraph(SvmProperties properties,
          List<AreaVolumeResult> resultList,
          double shoreReferenceElevation,
          double totalVolume) {
    this.properties = properties;
    this.resultList = resultList;
    this.totalVolume = totalVolume;

    double zMin = resultList.get(0).level;
    double zMax = resultList.get(resultList.size() - 1).level;
    if (zMin > zMax) {
      double swap = zMin;
      zMin = zMax;
      zMax = swap;
    }
    this.minLevel = zMin;
    this.maxLevel = zMax;

    unitOfVolume = properties.getUnitOfVolume();
    unitOfLength = properties.getUnitOfDistance();
  }

  /**
   * Write the output to the file specified in the properties.
   *
   * @return true if a file is written; otherwise, false
   * @throws IOException in the event of an unrecoverable IO condition
   */
  boolean writeOutput() throws IOException {
    File outputFile = properties.getCapacityGraphFile();
    if (outputFile == null) {
      return false;
    }
    Dimension dimension = properties.getCapacityGraphDimensions();
    String title = properties.getCapacityGraphTitle();

    int width = dimension.width;
    int height = dimension.height;
    BufferedImage bImage
            = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bImage.createGraphics();
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);

    g2d.setColor(Color.white);
    g2d.fillRect(0, 0, width, height);
    g2d.setColor(Color.gray);
    g2d.drawRect(0, 0, width - 1, height - 1);

    double wInPixels = dimension.getWidth();
    double hInPixels = dimension.getHeight();
    double titleFontSize = computeFontSize(defaultFontSizeTitle, hInPixels);
    double axisFontSize = computeFontSize(defaultFontSizeAxis, hInPixels);

    Font titleFont = new Font("Arial", Font.BOLD, (int) titleFontSize);
    Font axisFont = new Font("Arial", Font.BOLD, (int) axisFontSize);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    TextLayout testLayout = new TextLayout("00000", axisFont, frc);
    Rectangle2D fontR2D = testLayout.getBounds();
    double axisFontHeight = fontR2D.getHeight();
    double axisFontWidth = fontR2D.getWidth();
    int yFontAllowance = (int) (axisFontHeight * 1.1);
    int xFontAllowance = (int) (axisFontWidth * 1.3);

    AffineTransform af = AffineTransform.getQuadrantRotateInstance(3);
    Font axisFontRotated = axisFont.deriveFont(af);

    double volumeUnitsAdjustment = unitOfVolume.getScaleFactor();
    double xLabel;
    double yLabel;

    // compute coordinates for corners of graph area, in pixels
    double gLeft = wInPixels * fLeft;
    double gRight = wInPixels * fRight;
    double gTop = hInPixels * fTop;
    double gBottom = hInPixels * fBottom;
    double gdx = gRight - gLeft;
    double gdy = gBottom - gTop;

    TextLayout titleLayout = null;
    Rectangle2D titleR2D = null;
    if (title != null) {
      titleLayout = new TextLayout(title, titleFont, frc);
      titleR2D = titleLayout.getBounds();
      gTop = (int) (-3 * titleR2D.getY());
      gdy = gBottom - gTop;
    }

    // compute the intervals for the y axis based on percent capacity
    // allowing a little headroom above 100 percent
    double cHead = 110.0;
    AxisIntervals cIntervals = AxisIntervals.computeIntervals(0.0,
            cHead,
            yFontAllowance,
            yFontAllowance / 2,
            (int) gdy,
            true);
    double cUnitsPerPixel = cIntervals.getUnitsPerPixel();
    double[] cCoords = cIntervals.getLabelCoordinates();
    String[] cLabels = cIntervals.getLabels();
    double cDeltaPix = cHead / cUnitsPerPixel;

    // line up the vIntervals requires a few adjustments.  First, if the
    // calling module is appling a volume-units adjustment, we need to
    // apply it.  Next, we need to compute a units-per-pixel computation
    // that will scale the data so that the volumes line up with the
    // corresponding percent capacity.  Finally, the range for the
    // AxisIntervals call will have to be based on the "cHead" factor so
    // that we can ensure that we extend the tic marks past the 
    // total volume if there is room (and need) to do so.
    double cDeltaPix100 = 100 / cUnitsPerPixel; // delta pixels for 100 percent
    double vWithScale = totalVolume / volumeUnitsAdjustment;
    double vUnitsPerPixel = vWithScale / cDeltaPix100;
    AxisIntervals vIntervals = AxisIntervals.computeIntervals(
            0,
            vWithScale * (cHead / 100.0),
            yFontAllowance,
            yFontAllowance / 2,
            (int) gdy,
            false);
    double[] vCoords = vIntervals.getLabelCoordinates();
    String[] vLabels = vIntervals.getLabels();

    AxisIntervals xIntervals = AxisIntervals.computeIntervals(
            minLevel,
            maxLevel,
            xFontAllowance,
            xFontAllowance / 2,
            (int) (gdx - axisFontHeight * 2),
            true);
    double xUnitsPerPixel = xIntervals.getUnitsPerPixel();
    double[] xCoords = xIntervals.getLabelCoordinates();
    String[] xLabels = xIntervals.getLabels();
    double xDelta = xCoords[xCoords.length - 1] - xCoords[0];
    double xDeltaPix = xDelta / xUnitsPerPixel;

    // The Volume labels may have more digits that the percentage
    // capacity label.  So we may have to slide everything to the
    // left to make it fit correctly. 
    testLayout = new TextLayout("100", axisFont, frc);
    fontR2D = testLayout.getBounds();
    double cWidth = fontR2D.getWidth();
    double vWidth = 0;
    for (int i = 0; i < vLabels.length; i++) {
      testLayout = new TextLayout(vLabels[i], axisFont, frc);
      fontR2D = testLayout.getBounds();
      if (fontR2D.getWidth() > vWidth) {
        vWidth = fontR2D.getWidth();
      }
    }

    double x0 = wInPixels / 2.0 - xDeltaPix / 2.0 + (cWidth - vWidth);
    double x1 = x0 + xDeltaPix;
    double y0 = gTop;
    double y1 = gTop + cDeltaPix;

    g2d.setColor(Color.gray);
    g2d.setStroke(new BasicStroke(1.0f));
    if (titleLayout != null) {
      xLabel = (x0 + x1) / 2.0 - titleR2D.getCenterX();
      yLabel = -titleR2D.getY() + titleR2D.getHeight();
      titleLayout.draw(g2d, (float) xLabel, (float) yLabel);
    }

    Rectangle2D graphRect = new Rectangle2D.Double(x0, y0, xDeltaPix, cDeltaPix);
    g2d.draw(graphRect);
    Line2D l2d = new Line2D.Double();
    double yBoxTop = Double.POSITIVE_INFINITY;
    double xLabelMin = Double.POSITIVE_INFINITY;
    for (int i = 0; i < cCoords.length; i++) {
      double y = y1 - cCoords[i] / cUnitsPerPixel;
      l2d.setLine(x0, y, x1, y);
      g2d.draw(l2d);
      TextLayout tLayout = new TextLayout(cLabels[i], axisFont, frc);
      Rectangle2D r2d = tLayout.getBounds();
      yLabel = y - r2d.getCenterY();
      xLabel = x0 - r2d.getMaxX() - 10;
      tLayout.draw(g2d, (float) xLabel, (float) yLabel);
      yBoxTop = y;
      if (xLabel < xLabelMin) {
        xLabelMin = xLabel;
      }
    }

    String cLabel = "Estimated Capacity (percent)";
    TextLayout cLayout = new TextLayout(cLabel, axisFontRotated, frc);
    Rectangle2D cr2d = cLayout.getBounds();
    xLabel = xLabelMin + cr2d.getWidth() - axisFontHeight * 2;
    yLabel = (y0 + y1) / 2 - cr2d.getCenterY();
    cLayout.draw(g2d, (float) xLabel, (float) yLabel);

    double vMaxTextX = 0;
    for (int i = 0; i < vCoords.length; i++) {
      vLabels[i] = vLabels[i].trim();
      TextLayout tLayout = new TextLayout(vLabels[i], axisFont, frc);
      Rectangle2D r2d = tLayout.getBounds();
      if (r2d.getMaxX() > vMaxTextX) {
        vMaxTextX = r2d.getWidth();
      }
    }

    for (int i = 0; i < vCoords.length; i++) {
      double y = y1 - vCoords[i] / vUnitsPerPixel;
      if (y < yBoxTop) {
        break;
      }
      l2d.setLine(x1, y, x1 + 5, y);
      g2d.draw(l2d);
      TextLayout tLayout = new TextLayout(vLabels[i], axisFont, frc);
      Rectangle2D r2d = tLayout.getBounds();
      yLabel = y - r2d.getCenterY();
      xLabel = x1 + 10 + vMaxTextX - r2d.getWidth();
      tLayout.draw(g2d, (float) xLabel, (float) yLabel);
    }

    String vLabel = "Computed Volume (" + this.unitOfVolume.getLabel() + ")";
    TextLayout vLayout = new TextLayout(vLabel, axisFontRotated, frc);
    Rectangle2D vr2d = vLayout.getBounds();
    xLabel = x1 + 10 + vMaxTextX - vr2d.getX() + axisFontHeight * 2;
    yLabel = (y0 + y1) / 2 - vr2d.getCenterY();
    vLayout.draw(g2d, (float) xLabel, (float) yLabel);

    yLabel = y1 + axisFontHeight * 2;
    for (int i = 0; i < xCoords.length; i++) {
      double x = x0 + (xCoords[i] - xCoords[0]) / xUnitsPerPixel;
      l2d = new Line2D.Double(x, y0, x, y1);
      g2d.draw(l2d);
      TextLayout tLayout = new TextLayout(xLabels[i], axisFont, frc);
      Rectangle2D r2d = tLayout.getBounds();
      xLabel = x - r2d.getCenterX();
      tLayout.draw(g2d, (float) xLabel, (float) yLabel);
    }

    String aLabel = "Water Surface Elevation (" + unitOfLength.getLabel() + ")";
    TextLayout aLayout = new TextLayout(aLabel, axisFont, frc);
    Rectangle2D ar2d = aLayout.getBounds();
    xLabel = (x0 + x1) / 2 - ar2d.getCenterX();
    yLabel = y1 + axisFontHeight * 4 - ar2d.getX();
    aLayout.draw(g2d, (float) xLabel, (float) yLabel);

    g2d.setClip(graphRect);
    Path2D path = new Path2D.Double();
    boolean moveFlag = true;
    for (AreaVolumeResult avr : resultList) {
      double level = avr.level;
      double percent = 100.0 * avr.volume / totalVolume;
      double x = x0 + (level - xCoords[0]) / xUnitsPerPixel;
      double y = y1 - percent / cUnitsPerPixel;
      if (moveFlag) {
        moveFlag = false;
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    g2d.setColor(Color.BLUE);
    g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
    g2d.draw(path);
    g2d.setClip(null);

    ImageIO.write(bImage, "PNG", outputFile);

    return false;
  }
}
