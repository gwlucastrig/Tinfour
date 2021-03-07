/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * ------   --------- -------------------------------------------------
 * 08/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.utils.TinInstantiationUtility;
import org.tinfour.utils.Tincalc;
import org.tinfour.utils.VertexColorizerKempe6;

/**
 * Provides utilities for drawing graphical representations of a
 * BoundedVoronoiDiagram instance.
 * <p>
 * <strong>Note: This class is under construction and not yet fully
 * tested.</strong> Use with caution.
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class BoundedVoronoiDrawingUtility {

  private final BoundedVoronoiDiagram diagram;
  private final AffineTransform af;
  private final Rectangle2D bounds;
  private final Rectangle2D clipBounds;

  private static final Color defaultColors[] = {
    Color.YELLOW,
    Color.MAGENTA,
    Color.ORANGE,
    Color.LIGHT_GRAY,
    Color.PINK,
    Color.GREEN.brighter(),
    Color.RED,
    Color.BLUE,};

  /**
   * Constructs drawing utility instance for the specified Bounded Voronoi
   * Diagram. An affine transform is created that will scale the diagram to the
   * specified graphics surface.
   * <p>
   * The width and height define the size of the intended graphics surface. An
   * affine transform will be created to map the coordinate system of the
   * Voronoi Diagram to the specified graphics surface. The aspect ratio of the
   * diagram will be preserved and fit to use the maximum available space. If
   * desired, an application may specify a padding factor to reserve some blank
   * space around the edges of the diagram.
   * <p>
   * Normally, the transform will be initialized using the a rectangle slightly
   * larger than the sample bounds of the diagram, but if desired an application
   * may specify an alternate alternate bounds specification.
   *
   * @param diagram a valid instance of a Voronoi Diagram structure
   * @param width the width of the graphics surface
   * @param height the height of the graphics surface
   * @param pad an optional padding factor
   * @param optionalBounds an alternate bounds specification, or a null if the
   * defaults are to be used.
   */
  public BoundedVoronoiDrawingUtility(
          BoundedVoronoiDiagram diagram,
          int width,
          int height,
          int pad,
          Rectangle2D optionalBounds) {
    if (diagram == null) {
      throw new IllegalArgumentException("Null input diagram not allowed");
    }
    if (pad < 0) {
      throw new IllegalArgumentException("Negative pad value not allowed");
    }
    if (width - 2 * pad < 1 || height - 2 * pad < 1) {
      throw new IllegalArgumentException(
              "Width and height (minus padding) too small");
    }
    this.diagram = diagram;

    if (optionalBounds == null) {
      Rectangle2D sb = diagram.getSampleBounds();
      double w = sb.getWidth();
      double h = sb.getHeight();
      double x = sb.getX();
      double y = sb.getY();
      bounds = new Rectangle2D.Double(
              x - w * 0.1,
              y - h * 0.1,
              w * 1.2,
              h * 1.2
      );

    } else {
      bounds = new Rectangle2D.Double(
              optionalBounds.getX(),
              optionalBounds.getY(),
              optionalBounds.getWidth(),
              optionalBounds.getHeight());
    }

    double x0 = bounds.getMinX();
    double y0 = bounds.getMinY();
    double x1 = bounds.getMaxX();
    double y1 = bounds.getMaxY();
    af = this.initTransform(width, height, pad, x0, x1, y0, y1);
    clipBounds = computeClipBounds();
  }

  public BoundedVoronoiDrawingUtility(
          BoundedVoronoiDiagram diagram,
          Rectangle2D clipBounds,
          AffineTransform af) {
    this.diagram = diagram;
    this.af = af;
    this.bounds = diagram.getBounds();
    this.clipBounds = clipBounds;
  }

  /**
   * Gets an affine transform mapping the Cartesian coordinates of the Voronoi
   * Diagram to the pixel coordinates of the graphics surface.
   *
   * @return a valid transform
   */
  AffineTransform getTransform() {
    return af;
  }

  /**
   * Gets an affine transform mapping the pixel coordinates of the graphics
   * surface to the Cartesian coordinates of the Voronoi Diagram.
   *
   * @return a valid transform
   */
  AffineTransform getInverseTransform() {
    try {
      return af.createInverse();
    } catch (NoninvertibleTransformException ex) {
      // this will never happen because it was already tested
      // when the class was instantiated.  But the Java API demands that we
      // declare it.
      return new AffineTransform();
    }
  }

  /**
   * Initializes an affine transform for Cartesian coordinates from a TIN to
   * pixel coordinates for rendering an image.
   *
   * @param width the width of the output image
   * @param height the height of the output image
   * @param pad spacing around edge of image
   * @param x0 the x coordinate of the left side of the data area
   * @param x1 the x coordinate of the right side of the data area
   * @param y0 the y coordinate of the lower side of the data area
   * @param y1 the y coordinate of the upper side of the data area
   * @return if successful, a valid instance.
   */
  private AffineTransform initTransform(
          int width,
          int height,
          int pad,
          double x0, double x1, double y0, double y1) {
    // The goal is to create an AffineTransform that will map coordinates
    // from the source data to image (pixel) coordinates).  We wish to
    // render the rectangle defined by the source coordinates on an
    // image with as large a scale as possible.  However, the shapes of
    // the image and the data rectangles may be different (one may be
    // wide and one may be narrow).  So we need to use the aspect ratios
    // of the two rectangles to determine whether to fit it to the
    // vertical axis or to fit it to the horizontal.

    double rImage = (double) (width - pad) / (double) (height - pad);
    double rData = (x1 - x0) / (y1 - y0);
    double rAspect = rImage / rData;

    double uPerPixel; // units of distance per pixel
    if (rAspect >= 1) {
      // the shape of the image is fatter than that of the
      // data, the limiting factor is the vertical extent
      uPerPixel = (y1 - y0) / (height - pad);
    } else { // r<1
      // the shape of the image is skinnier than that of the
      // data, the limiting factor is the horizontal extent
      uPerPixel = (x1 - x0) / (width - pad);
    }
    double scale = 1.0 / uPerPixel;

    double xCenter = (x0 + x1) / 2.0;
    double yCenter = (y0 + y1) / 2.0;
    double xOffset = (width - pad) / 2 - scale * xCenter + pad / 2;
    double yOffset = (height - pad) / 2 + scale * yCenter + pad / 2;

    AffineTransform transform;
    transform = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

    try {
      transform.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(
              "Input elements result in a degenerate transform: " + ex.getMessage(),
              ex);
    }
    return transform;
  }

  private Rectangle2D computeClipBounds() {
    double x0 = bounds.getMinX();
    double y0 = bounds.getMinY();
    double x1 = bounds.getMaxX();
    double y1 = bounds.getMaxY();
    Point2D llCorner = new Point2D.Double(x0, y0);
    Point2D urCorner = new Point2D.Double(x1, y1);
    af.transform(llCorner, llCorner);
    af.transform(urCorner, urCorner);
    return new Rectangle2D.Double(
            llCorner.getX(), urCorner.getY(),
            urCorner.getX() - llCorner.getX(),
            llCorner.getY() - urCorner.getY());
  }

  /**
   * Draws a the edges and vertices of the Voronoi Diagram. The font
   * specification is optional. If it is specified, the vertices will be
   * labeled. If it is null, the vertices will not be labeled. All other
   * specifications are mandatory
   *
   * @param g the graphics surface for drawing
   * @param foreground the color for drawing the graphics
   * @param strokeWidth the width of the line features to be draw (typically, a
   * value of 2).
   * @param font an optional font specification for labeling vertices; or null
   * if no labeling is to be performed.
   */
  public void drawWireframe(
          Graphics g,
          Color foreground,
          double strokeWidth,
          Font font) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // edges are drawn with clip on, but vertices are drawn
    // with the clip off.
    g2d.setColor(foreground);
    g2d.setStroke(new BasicStroke((float) strokeWidth));

    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();
    Line2D l2d = new Line2D.Double();
    Ellipse2D e2d = new Ellipse2D.Double();

    // draw the edges
    List<IQuadEdge> edgeList = diagram.getEdges();
    for (IQuadEdge e : edgeList) {
      Vertex A = e.getA();
      Vertex B = e.getB();
      p0.setLocation(A.getX(), A.getY());
      p1.setLocation(B.getX(), B.getY());
      af.transform(p0, p0);
      af.transform(p1, p1);
      l2d.setLine(p0, p1);
      g2d.draw(l2d);
    }

    g2d.setStroke(new BasicStroke(1.0f));
    FontRenderContext frc = null;
    if (font != null) {
      g2d.setFont(font);
      frc = new FontRenderContext(null, true, true);
    }

    g2d.setClip(null);
    List<Vertex> vertexList = diagram.getVertices();
    for (Vertex v : vertexList) {
      double x = v.getX();
      double y = v.getY();
      p0.setLocation(x, y);
      af.transform(p0, p1);
      e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 6.0f, 6.0f);
      g2d.fill(e2d);
      g2d.draw(e2d);
      if (font != null) {
        String text = v.getLabel();
        TextLayout layout = new TextLayout(text, font, frc);
        double yOffset = layout.getAscent() + 2;
        g2d.drawString(text,
                (int) (p1.getX() + 3),
                (int) (p1.getY() + 3 + yOffset));
      }
    }
  }

  /**
   * Draws a the vertices of the specified list. The font specification is
   * optional. If it is specified, the vertices will be labeled. If it is null,
   * the vertices will not be labeled. All other specifications are mandatory
   *
   * @param g the graphics surface for drawing
   * @param foreground the color for drawing the graphics
   * @param font an optional font specification for labeling vertices; or null
   * if no labeling is to be performed.
   * @param vertexList the list of vertices to be drawn
   */
  public void drawVertices(
          Graphics g,
          Color foreground,
          Font font,
          List<Vertex> vertexList) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();

    Ellipse2D e2d = new Ellipse2D.Double();

    g2d.setClip(null);
    g2d.setStroke(new BasicStroke(1.0f));

    g2d.setColor(foreground);
    FontRenderContext frc = null;
    if (font != null) {
      g2d.setFont(font);
      frc = new FontRenderContext(null, true, true);
    }
    for (Vertex v : vertexList) {
      double x = v.getX();
      double y = v.getY();
      p0.setLocation(x, y);
      af.transform(p0, p1);
      e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 6.0f, 6.0f);
      g2d.fill(e2d);
      g2d.draw(e2d);
      if (font != null) {
        String text = v.getLabel();
        TextLayout layout = new TextLayout(text, font, frc);
        double yOffset = layout.getAscent() + 2;
        g2d.drawString(text,
                (int) (p1.getX() + 3),
                (int) (p1.getY() + 3 + yOffset));
      }
    }
  }

  /**
   * Performs area-fill operations on the polygons that are defined by the
   * Voronoi Diagram. The polygons are colored according to the specifications
   * in an array of Java Paint or Color objects. The color index from the
   * vertices is used to decide which color to use for rendering. If color
   * indices have not been previously assigned, the calling application may
   * request that this method assign them automatically. If the automatic option
   * is specified, the Paint array must contain at least six object (unique
   * colors are highly recommended).
   * <p>
   * Recall that in Java, the Color object implemented the Paint interface. So
   * an array of Color objects is a valid input.
   *
   * @param g the graphics surface for drawing
   * @param paintSpec an array of paint instances corresponding to the color
   * index values of each vertex
   * @param assignAutomaticColors indicates that the vertices are to be assigned
   * color index values automatically. Doing so will override any color-index
   * values previously stored in the vertices.
   */
  public void drawPolygons(
          Graphics g,
          Paint[] paintSpec,
          boolean assignAutomaticColors) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    Paint[] paint = paintSpec;
    if (paintSpec == null) {
      paint = defaultColors;
    }
    if (assignAutomaticColors) {
      if (paint.length < 6) {
        throw new IllegalArgumentException(
                "Input paint specification must include at least 6 values");
      }
      assignAutomaticColors();
    }

    // edges are drawn with clip on, but vertices are drawn
    // with the clip off.
    g2d.setClip(clipBounds);
    g2d.setStroke(new BasicStroke(1.0f));

    Point2D p0 = new Point2D.Double();
    List<ThiessenPolygon> polygons = diagram.getPolygons();

    for (ThiessenPolygon poly : polygons) {
      List<IQuadEdge> qList = poly.getEdges();
      Path2D path = new Path2D.Double();
      IQuadEdge q0 = qList.get(0);
      p0.setLocation(q0.getA().getX(), q0.getA().getY());
      af.transform(p0, p0);
      path.moveTo(p0.getX(), p0.getY());
      for (IQuadEdge q : qList) {
        p0.setLocation(q.getB().getX(), q.getB().getY());
        af.transform(p0, p0);
        path.lineTo(p0.getX(), p0.getY());
      }
      path.closePath();
      Vertex v = poly.getVertex();

      int k = v.getAuxiliaryIndex();

      g2d.setPaint(paint[k]);
      g2d.fill(path);
      g2d.draw(path);

    }

    g2d.setClip(null);
  }

  /**
   * Draws a the edges from the specified list.
   *
   * @param g the graphics surface for drawing
   * @param foreground the color for drawing the graphics
   * @param strokeWidth the width of the line features to be draw (typically, a
   * value of 2).
   * @param edgeList the edges to be drawn
   */
  public void drawEdges(
          Graphics g,
          Color foreground,
          double strokeWidth,
          List<IQuadEdge> edgeList) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    Shape saveClip = g2d.getClip();
    g2d.setClip(null);
    g2d.setColor(foreground);
    g2d.setStroke(new BasicStroke((float) strokeWidth));

    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();
    Line2D l2d = new Line2D.Double();

    // draw the edges
    for (IQuadEdge e : edgeList) {
      Vertex A = e.getA();
      Vertex B = e.getB();
      if (A == null || B == null) {
        continue;
      }
      p0.setLocation(A.getX(), A.getY());
      p1.setLocation(B.getX(), B.getY());
      af.transform(p0, p0);
      af.transform(p1, p1);
      l2d.setLine(p0, p1);
      g2d.draw(l2d);
    }
    g2d.setClip(saveClip);
  }

  /**
   * Assigns automatic color selections to the vertices and associated polygons
   * in the bounded Voronoi diagram stored when this instance was constructed.
   * <p>
   * This method is potentially costly for diagrams containing a large number of
   * vertices. Therefore, it should only be called once when rendering.
   * <p>
   * This method assigns six color indices to polygons (in the range 0 to 5). If
   * applications that use automatic color assignment require a custom palette,
   * they must specify and array of Color or Paint instances with at least six
   * elements. If a smaller palette is supplied, the results are undefined.
   */
  public void assignAutomaticColors() {
    List<Vertex> vertexList = diagram.getVertices();
    int nVertices = vertexList.size();
    Rectangle2D sampleBounds = new Rectangle2D.Double(
            vertexList.get(0).getX(),
            vertexList.get(0).getY(),
            0, 0);

    for (Vertex v : vertexList) {
      sampleBounds.add(v.getX(), v.getY());
    }

    // estimate a nominal point spacing based on the domain of the
    // input data set and assuming a rougly uniform density.
    // the estimated spacing is based on the parameters of a regular
    // hexagonal tesselation of a plane
    double area = sampleBounds.getWidth() * sampleBounds.getHeight();
    double nominalPointSpacing = Tincalc.sampleSpacing(area, nVertices);
    TinInstantiationUtility maker
            = new TinInstantiationUtility(0.25, vertexList.size());
    IIncrementalTin tin = maker.constructInstance(nominalPointSpacing);
    tin.add(vertexList, null);
    if (!tin.isBootstrapped()) {
      return;
    }
    VertexColorizerKempe6 kempe6 = new VertexColorizerKempe6();
    kempe6.assignColorsToVertices(tin);
    tin.dispose();
  }

  /**
   * Draws the Bounded Voronoi Diagram using the specified styler
   * <p>
   * <strong>This method is under development.</strong>
   * It is not yet ready for use and may be subject to changes.
   *
   * @param g a valid graphic surface
   * @param specifiedStyler a valid styler implementation, or a null if defaults
   * are to be used
   */
  public void draw(Graphics g, IBoundedVoronoiStyler specifiedStyler) {
    IBoundedVoronoiStyler styler = specifiedStyler;
    if (styler == null) {
      styler = new BoundedVoronoiStylerDefault();
    }

    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // edges are drawn with clip on, but vertices are drawn
    // with the clip off.
    g2d.setClip(clipBounds);
    g2d.setStroke(new BasicStroke(1.0f));

    if (styler.isFeatureTypeEnabled(BoundedVoronoiRenderingType.Area)) {
      drawAreaFill(g2d, styler);
    }

    if (styler.isFeatureTypeEnabled(BoundedVoronoiRenderingType.Line)) {
      drawLines(g2d, styler);
    }

    if (styler.isFeatureTypeEnabled(BoundedVoronoiRenderingType.Vertex)) {
      drawVertices(g2d, styler);
    }

  }

  private void drawAreaFill(
          Graphics2D g2d,
          IBoundedVoronoiStyler styler) {

    Point2D p0 = new Point2D.Double();
    List<ThiessenPolygon> polygons = diagram.getPolygons();

    for (ThiessenPolygon poly : polygons) {
      if (styler.isRenderingEnabled(poly, BoundedVoronoiRenderingType.Area)) {
        List<IQuadEdge> qList = poly.getEdges();
        Path2D path = new Path2D.Double();
        IQuadEdge q0 = qList.get(0);
        p0.setLocation(q0.getA().getX(), q0.getA().getY());
        af.transform(p0, p0);
        path.moveTo(p0.getX(), p0.getY());
        for (IQuadEdge q : qList) {
          p0.setLocation(q.getB().getX(), q.getB().getY());
          af.transform(p0, p0);
          path.lineTo(p0.getX(), p0.getY());
        }
        path.closePath();

        styler.applyStylingForAreaFill(g2d, poly);
        g2d.fill(path);
        g2d.draw(path);
      }
    }
  }

  private void drawLines(Graphics2D g2d, IBoundedVoronoiStyler styler) {
    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();
    Line2D l2d = new Line2D.Double();

    boolean drawn[] = new boolean[diagram.getMaximumEdgeAllocationIndex()];
    for (ThiessenPolygon poly : diagram.getPolygons()) {

      if (styler.isRenderingEnabled(poly, BoundedVoronoiRenderingType.Line)) {
        styler.applyStylingForLineDrawing(g2d, poly);
        for (IQuadEdge e : poly.getEdges()) {
          if (!drawn[e.getIndex()]) {
            drawn[e.getIndex()] = true;
            drawn[e.getIndex() ^ 0x01] = true;
            Vertex A = e.getA();
            Vertex B = e.getB();

            p0.setLocation(A.getX(), A.getY());
            p1.setLocation(B.getX(), B.getY());
            af.transform(p0, p0);
            af.transform(p1, p1);
            l2d.setLine(p0, p1);
            g2d.draw(l2d);
          }
        }
      }
    }

  }

  private void drawVertices(Graphics2D g2d, IBoundedVoronoiStyler styler) {
    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();

    for (ThiessenPolygon poly : diagram.getPolygons()) {
      if (styler.isRenderingEnabled(poly, BoundedVoronoiRenderingType.Vertex)) {
        Vertex v = poly.getVertex();
        p0.setLocation(v.getX(), v.getY());
        af.transform(p0, p1);
        if(clipBounds!=null && !clipBounds.contains(p1)){
          continue;
        }
        IBoundedVoronoiVertexSymbol s = styler.getVertexSymbol(poly);
        s.draw(g2d, p1.getX(), p1.getY());
      }
    }
  }

}
