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
 * 11/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
public class TinRenderingUtility {

  /**
   * Initializes an affine transform for Cartesian coordinates
   * from a TIN to pixel coordinates for rendering an image.
   *
   * @param width the width of the output image
   * @param height the height of the output image
   * @param x0 the x coordinate of the left side of the data area
   * @param x1 the x coordinate of the right side of the data area
   * @param y0 the y coordinate of the lower side of the data area
   * @param y1 the y coordinate of the upper side of the data area
   * @return if successful, a valid instance.
   */
  public AffineTransform initTransform(int width, int height, double x0, double x1, double y0, double y1) {
    // The goal is to create an AffineTransform that will map coordinates
    // from the source data to image (pixel) coordinates).  We wish to
    // render the rectangle defined by the source coordinates on an
    // image with as large a scale as possible.  However, the shapes of
    // the image and the data rectangles may be different (one may be
    // wide and one may be narrow).  So we need to use the aspect ratios
    // of the two rectangles to determine whether to fit it to the
    // vertical axis or to fit it to the horizontal.

    double rImage = (double) width / (double) height;
    double rData = (x1 - x0) / (y1 - y0);
    double rAspect = rImage / rData;

    double uPerPixel; // units of distance per pixel
    if (rAspect >= 1) {
      // the shape of the image is fatter than that of the
      // data, the limiting factor is the vertical extent
      uPerPixel = (y1 - y0) / height;
    } else { // r<1
      // the shape of the image is skinnier than that of the
      // data, the limiting factor is the horizontal extent
      uPerPixel = (x1 - x0) / width;
    }
    double scale = 1.0 / uPerPixel;

    double xCenter = (x0 + x1) / 2.0;
    double yCenter = (y0 + y1) / 2.0;
    double xOffset = width / 2 - scale * xCenter;
    double yOffset = height / 2 + scale * yCenter;

    AffineTransform af;
    af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

    try {
      af.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(
        "Input elements result in a degenerate transform: " + ex.getMessage(),
        ex);
    }
    return af;
  }

  private boolean inBounds(Vertex p, double x0, double x1, double y0, double y1) {
    double x = p.getX();
    double y = p.getY();
    return x0 <= x && x <= x1 && y0 <= y && y <= y1;
  }

  /**
   * Creates an image of the specified size and renders data from
   * the TIN to the image.
   *
   * @param af transformation for converting Cartesian coordinates
   * to pixel coordinates
   * @param width width of the output image
   * @param height height of the output image
   * @param x0 the x coordinate of the left side of the data area
   * @param x1 the x coordinate of the right side of the data area
   * @param y0 the y coordinate of the lower side of the data area
   * @param y1 the y coordinate of the upper side of the data area
   * @param zMin minimum z value used for palette assignment
   * @param zMax maximum z value used for palette assignment
   * @param background valid color for rendering background
   * @param foreground valid color for rendering foreground features
   * @param palette valid palette for coloring features, or null
   * to use just the foreground color
   * @param tin a valid instance of a TIN
   * @return a valid buffered image of specified size
   */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  BufferedImage render(
    AffineTransform af,
    int width, int height,
    double x0, double x1, double y0, double y1,
    double zMin, double zMax,
    Color background,
    Color foreground,
    TestPalette palette,
    IIncrementalTin tin) {
    BufferedImage bImage
      = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bImage.createGraphics();
    g2d.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setColor(background);
    g2d.fillRect(0, 0, width, height);
    g2d.setColor(foreground);

    Point2D llCorner = new Point2D.Double(x0, y0);
    Point2D urCorner = new Point2D.Double(x1, y1);
    af.transform(llCorner, llCorner);
    af.transform(urCorner, urCorner);

    Rectangle2D clipBounds = new Rectangle2D.Double(
      llCorner.getX(), urCorner.getY(),
      urCorner.getX() - llCorner.getX(),
      llCorner.getY() - urCorner.getY());

    g2d.setStroke(new BasicStroke(3.0f));
    g2d.draw(clipBounds);
    g2d.setClip(clipBounds);

    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();
    Line2D l2d = new Line2D.Double();
    Ellipse2D e2d = new Ellipse2D.Double();

    g2d.setStroke(new BasicStroke(2.0f));

    int nEdgeLen = 0;
    double sumEdgeLen = 0;
    List<IQuadEdge> edges = tin.getEdges();
    for (IQuadEdge edge : edges) {
      Vertex v0 = edge.getA();
      Vertex v1 = edge.getB();
      if (v0 == null || v1 == null) {
        continue; // skip the ghost edges
      }

      if (inBounds(v0, x0, x1, y0, y1) || inBounds(v1, x0, x1, y0, y1)) {
        p0.setLocation(v0.getX(), v0.getY());
        p1.setLocation(v1.getX(), v1.getY());
        af.transform(p0, p0);
        af.transform(p1, p1);
        nEdgeLen++;
        sumEdgeLen += p0.distance(p1);
        l2d.setLine(p0, p1);
        double z0 = v0.getZ();
        double z1 = v1.getZ();
        if (palette != null) {
          Color c0 = palette.getColor(z0, zMin, zMax);
          Color c1 = palette.getColor(z1, zMin, zMax);
          GradientPaint paint = new GradientPaint(
            (float) v0.getX(), (float) v0.getY(), c0,
            (float) v1.getX(), (float) v1.getY(), c1);
          g2d.setPaint(paint);
        }
        g2d.draw(l2d);
      }
    }

    double avgLen = 0;
    if (nEdgeLen > 0) {
      avgLen = sumEdgeLen / nEdgeLen;
    }

    List<Vertex> vertexList = tin.getVertices();

    g2d.setStroke(new BasicStroke(1.0f));
    for (Vertex v : vertexList) {
      if (inBounds(v, x0, x1, y0, y1)) {
        p0.setLocation(v.getX(), v.getY());
        double z = v.getZ();
        if (palette != null) {
          Color c = palette.getColor(z, zMin, zMax);
          g2d.setColor(c);
        }
        af.transform(p0, p1);
        e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 6.0f, 6.0f);
        g2d.fill(e2d);
        g2d.draw(e2d);
      }
    }

    g2d.setColor(foreground);
    g2d.setStroke(new BasicStroke(3.0f));
    g2d.setClip(null);
    g2d.draw(clipBounds);
    g2d.dispose();
    return bImage;
  }

  /**
   * Provides a convenience method for rendering a TIN and writing the
   * results to an image file
   *
   * @param tin a valid TIN.
   * @param width the width of the output image
   * @param height the height of the output image
   * @param file the file for output
   * @throws IOException in the event of an unsuccessful write operation
   */
  public static void drawTin(IIncrementalTin tin, int width, int height, File file)
    throws IOException {
    Rectangle2D r2d = tin.getBounds();
    double x0 = r2d.getMinX();
    double x1 = r2d.getMaxX();
    double y0 = r2d.getMinY();
    double y1 = r2d.getMaxY();

    if (r2d == null) {
      throw new IllegalArgumentException("Input TIN is not bootstrapped");
    }
    TinRenderingUtility tru = new TinRenderingUtility();
    AffineTransform af = tru.initTransform(width, height, x0, x1, y0, y1);
    BufferedImage bImage = tru.render(
      af, width, height, x0, x1, y0, y1, y1, y1,
      Color.white, Color.darkGray, null, tin);

    String s = file.getName();
    int i = s.lastIndexOf(".");
    String fmt = null;
    if (i > 0) {
      fmt = s.substring(i + 1, s.length());
      if ("png".equalsIgnoreCase(fmt)) {
        fmt = "PNG";
      } else if ("jpg".equalsIgnoreCase(fmt)) {
        fmt = "JPEG";
      } else if ("jepg".equalsIgnoreCase(fmt)) {
        fmt = "JPEG";
      } else if ("gif".equalsIgnoreCase(fmt)) {
        fmt = "GIF";
      } else {
        fmt = null;
      }
    }
    if (fmt == null) {
      fmt = "PNG";
    }
    ImageIO.write(bImage, fmt, file);
  }

  //
  //public static void main(String []args) throws Exception {
  //    IncrementalTin tin = new IncrementalTin(1.0);
  //    List<Vertex>vertexList = TestVertices.makeRandomVertices(100, 0);
  //    tin.add(vertexList);
  //    TinRenderingUtility.drawTin(tin, 500, 500, new File("tin.png"));
  //}
}
