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
 * 05/2016  G. Lucas     Created
 *
 * Notes:
 */
package org.tinfour.demo.viewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

class ScaleIntervals {

  private static final double LN10 = Math.log(10.0);

  /**
   * Arbitrarily chosen divisions based on the ordinal value of the
   * range. Array index [i][0] is the primary division,
   * index[i][1] is the secondary. It is essential that the secondary
   * division evenly divides the primary.
   */
  private static final double[][] divisions = {
    {0, 0},
    {0.5, 0.1}, // 1
    {1, 0.5}, // 2
    {1, 0.5}, // 3
    {2, 1}, // 4
    {5, 1.0}, // 5
    {0, 0.0}, // special case, map 6 down to 5
    {0, 0}, // special case map 7 to 5
    {4, 2}, // 8
    {0, 0}, // special case, map 9 down to 8
  };

  final int maxSizeInPixels;
  final double maxValue;
  final double[][] cTics;
  final double[] cLabels;
  final double unitsPerPixel;
  final String labelFormat;

  ScaleIntervals(
    int maxSizeInPixels,
    double maxValue,
    double cTics[][],
    double cLabels[],
    double unitsPerPixel,
    String labelFormat) {
    this.maxSizeInPixels = maxSizeInPixels;
    this.maxValue = maxValue;
    this.cTics = Arrays.copyOf(cTics, cTics.length);
    this.cLabels = Arrays.copyOf(cLabels, cLabels.length);
    this.unitsPerPixel = unitsPerPixel;
    this.labelFormat = labelFormat;
  }

  double getMaxValue() {
    return maxValue;
  }

  double[][] getTicCoordinates() {
    double[][] d = new double[cTics.length][];
    for (int i = 0; i < cTics.length; i++) {
      d[i] = Arrays.copyOf(cTics[i], cTics[i].length);
    }
    return d;
  }

  double[] getLabelCoordinates() {
    return Arrays.copyOf(cLabels, cLabels.length);
  }

  String[] getLabels() {
    String[] a = new String[cLabels.length];
    for (int i = 0; i < a.length; i++) {
      a[i] = String.format(labelFormat, cLabels[i]);
    }
    return a;
  }

  String getLabelFormat() {
    return labelFormat;
  }

  double getCoordinateScale() {
    return 1 / unitsPerPixel;
  }

  private void drawLines(Graphics2D g2d, int x0, int y0, double extra) {
    Line2D l2d = new Line2D.Double();
    for (int iDraw = 0; iDraw < 2; iDraw++) {
      l2d.setLine(x0 + extra, y0, x0 + maxSizeInPixels - extra, y0);
      g2d.draw(l2d);
      for (int iTicSet = 0; iTicSet < cTics.length; iTicSet++) {
        double ticSize = 10;
        if (iTicSet == 1) {
          ticSize /= 2; // minor tics
        }
        for (int i = 0; i < cTics[iTicSet].length; i++) {
          double x = x0 + extra + cTics[iTicSet][i] / unitsPerPixel;
          l2d.setLine(x, y0, x, y0 - ticSize);
          g2d.draw(l2d);
        }
      }
    }
  }

  void render(Graphics g, int x0, int y0, Font font, Color color, Color relief) {
    Graphics2D g2d = (Graphics2D) g;
    Stroke thin = new BasicStroke(1.0f);
    Stroke fat = new BasicStroke(3.0f);

    double extra = (maxSizeInPixels - maxValue / unitsPerPixel) / 2;

    // draw the relief line
    if (relief != null) {
      g2d.setColor(relief);
      g2d.setStroke(fat);
      this.drawLines(g2d, x0, y0, extra);
    }

    g2d.setColor(color);
    g2d.setStroke(thin);
    this.drawLines(g2d, x0, y0, extra);

    Stroke outlineStroke
      = new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    String[] labels = this.getLabels();
    for (int i = 0; i < labels.length; i++) {
      TextLayout layout = new TextLayout(labels[i], font, frc); // NOPMD
      Rectangle2D bounds = layout.getBounds();

      double x = x0 + extra + cLabels[i] / unitsPerPixel - bounds.getCenterX();
      double y = y0 + bounds.getHeight() + 5;
      if (relief != null) {
        //  Rectangle2D r2d = layout.getPixelBounds(frc, (float) x, (float) y);
        //  r2d = new Rectangle2D.Double(
        //    r2d.getX() - 1,
        //    r2d.getY() - 1,
        //    r2d.getWidth() + 2,
        //    r2d.getHeight() + 2);
        //

        Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y + 0.5));

        g2d.setStroke(outlineStroke);
        g2d.setColor(relief);
        g2d.draw(outline);
      }
      g2d.setStroke(thin);
      g2d.setColor(color);
      layout.draw(g2d, (float) x, (float) y);

    }

  }

  /**
   * Compute human-friendly intervals that will subdivide a distance given
   * in pixels based on a specified units per pixel. This class is useful
   * for rendering scale bars and similar graphics.
   * <p>
   * The maxSizeInPixels specification indicates a maximum value for
   * how many pixels wide (or high) the subdivided distance is allowed to be.
   * The minGapInPixels indicates the minimum distance between
   * subdivisions. Given these constraints, and a value of unitsPerPixel,
   * the method tries to pick a number of subdivisions so that the
   * divisions relate to round numbers (1's, 2's, etc.) that start at
   * zero and run to a computed maximum. The computed max distance may be
   * slightly smaller than the specified maximum size, though the
   * algorithm tries for a good fit.
   * <p>
   * The units per pixel value may be given in meters, feet, miles,
   * parsecs, volts, hours, etc., depending on the needs of the
   * application.
   *
   * @param maxSizeInPixels the size of the working space
   * @param minGapInPixels the minimum gap between subdivisions
   * @param unitsPerPixel a scaling factor indicating the number if
   * units per pixel (non-integral values are expected).
   * @return an instance of this class to carry the results.
   */
  static ScaleIntervals computeIntervals(
    int maxSizeInPixels,
    int minGapInPixels,
    double unitsPerPixel) {
    double maxScale = maxSizeInPixels * unitsPerPixel; // maximum possible value
    double log10 = Math.log(maxScale) / LN10;  // log base 10
    double p10 = Math.floor(log10);
    double m10 = Math.exp(p10 * LN10);
    if (p10 > 0) {
      // for powers of 10 greater than equal to zero, m10 may have a very
      // small fractional part. truncate it. also handle the case
      // where m10 may be just shy of what its supposed to be.
      m10 = Math.floor(m10 + 1.0e-9);
    }
    double f = Math.exp((log10 - p10) * LN10);
    int n = (int) Math.floor(f + 1.0e-9);
    if (n == 6 || n == 7) {
      n = 5; // special case, drop 6 and 7 down to 5
    } else if (n == 9) {
      n = 8; // special case, drop  9 down to 8
    } else if (n == 10) {
      // round up resulted in 10, make an adjustment
      f = 0;
      n = 1;
      p10++;
      m10 *= 10;
    }

    double div1 = divisions[n][0] * m10;
    double div2 = divisions[n][1] * m10;

    double divPix1 = div1 / unitsPerPixel;
    double divPix2 = div2 / unitsPerPixel;
    int nDiv;
    if (divPix2 >= minGapInPixels) {
      nDiv = 2;
    } else if (divPix1 >= minGapInPixels) {
      nDiv = 1;
    } else {
      nDiv = 0;
    }

    double maxVal = n * m10;
    if (n == 1 && f >= 1.5) {
      // a special case.  when the fractional value is
      // in the range 1.5 to 2.0, clipping off the fractional
      // part would tend to produce a very short bar.
      // so we adjust the max value to extend the bar
      if (nDiv > 0) {  // NOPMD
        maxVal = 1.5 * m10;
      }
    }

    // don't label division 1 unless division 2 is enabled.
    double[] xLabel = new double[0];
    if (nDiv == 1) {
      xLabel = new double[2];
      xLabel[0] = 0;
      xLabel[1] = n * m10;
    } else if (nDiv == 2) {
      int nLabel = (int) Math.floor(maxVal / div1 + 1.0e-7) + 1;
      xLabel = new double[nLabel];
      for (int iLabel = 0; iLabel < nLabel; iLabel++) {
        xLabel[iLabel] = iLabel * div1;
      }
    }

    double[][] xTic = new double[nDiv][];
    if (nDiv > 0) {
      int nTic1 = (int) Math.floor(maxVal / div1 + 1.0e-7) + 1;
      xTic[0] = new double[nTic1];
      for (int i = 0; i < nTic1; i++) {
        xTic[0][i] = i * div1;
      }
      if (nDiv == 2) {
        // develop the coordinates for the secondary tic marks
        // however, when a secondary tic mark overlaps a primary
        // it doesn't get added to the xTic array.
        int nTic2 = (int) Math.floor(maxVal / div2 + 1.0e-7) + 1;
        xTic[1] = new double[nTic2 - nTic1];
        int k = 0;
        double divMod
          = (int) Math.floor(divisions[n][0] / divisions[n][1] + 1.0e-7);
        for (int i = 0; i < nTic2; i++) {
          if ((i % divMod) != 0) {
            xTic[1][k++] = i * div2;
          }
        }
      }
    }

    int iPowerOfTen = (int) p10; // integral power of 10

    // The label format is used for intervals in division 0 and 1.
    // intervals in division 2 are never labeled.  If the "n" value
    // is 1, division 1 can be fractional.  In all other cases,
    // the division is not fractional.
    String labelFmt = "%f";
    if (iPowerOfTen == 0) {
      if (n == 1 && nDiv > 0) {
        // all divisions of 1 are fractional
        labelFmt = "%3.1f";
      } else {
        // all divisions of the larger numbers are integral
        labelFmt = "%1.0f";
      }
    } else if (iPowerOfTen > 0) {
      if (iPowerOfTen < 7) {
        labelFmt = "%1.0f";
      }
    } else if (iPowerOfTen < 0 && iPowerOfTen >= -3) {
      int nDecimals = -iPowerOfTen;
      if (n == 1 && nDiv > 0) {
        nDecimals++; // one extra digit
      }
      labelFmt = "%" + (nDecimals + 2) + "." + nDecimals + "f";
    }

    return new ScaleIntervals(
      maxSizeInPixels, maxVal, xTic, xLabel, unitsPerPixel, labelFmt);
  }

}
