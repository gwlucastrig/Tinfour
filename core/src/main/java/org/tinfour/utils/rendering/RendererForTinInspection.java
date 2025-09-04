/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 04/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.rendering;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides a utility for rendering instances of the incremental TIN
 * classes (the Delaunay triangulation classes) to an image. This class
 * provides a visual representation of the TIN that can be useful for
 * development and diagnostic purposes.
 */
public class RendererForTinInspection {

  private static class Layer {

    Color color;
    BasicStroke stroke;
    Shape shape;
    boolean fill;

    Layer(Shape shape, Color color, BasicStroke stroke, boolean fill) {
      if (color == null) {
        this.color = Color.red;
      } else {
        this.color = color;
      }
      if (stroke == null) {
        this.stroke = new BasicStroke(1.0f);
      } else {
        this.stroke = stroke;
      }
      this.shape = shape;
      this.fill = fill;
    }
  }

  private final static double CENTERING_THRESHOLD = 1.0e-4;

  private final IIncrementalTin tin;

  private Color background = Color.white;
  private Color foreground = Color.black;
  private final BasicStroke thinStroke = new BasicStroke(1.0f);
  private final Font labelFont = new Font("SANS_SERIF", Font.BOLD, 12);
  private final Font constraintFont = new Font("SANS_SERIF", Font.BOLD | Font.ITALIC, 12);

  private boolean edgeRenderingEnabled = true;
  private boolean edgeLabelEnabled = true;
  private boolean edgeLabelDualSideConfiguration = false;
  private boolean vertexRenderingEnabled = true;
  private boolean vertexLabelEnableIndex = true;
  private boolean vertexLabelEnableZ = false;
  private String vertexLabelFormatZ = "%f";
    private double vertexRenderingSize = 5;

  private boolean coordinateSystemIsPixels;

  private Color edgeColor = Color.lightGray;

  private boolean drawFrameEnabled;

  private final List<Layer> underlays = new ArrayList<>();
  private final List<Layer> overlays = new ArrayList<>();

  /**
   * Construct an instance of a renderer configured for the default
   * presentation of a Delaunay triangulation (e.g, a TIN).
   *
   * @param tin a valid instance of an incremental TIN class.
   */
  public RendererForTinInspection(IIncrementalTin tin) {
    this.tin = tin;
  }

  /**
   * Adds a shape to be rendered before the Delaunay triangulation (TIN)
   * content is drawn. The Delaunay depiction may cover features that are
   * drawn as underlays.
   *
   * @param shape a valid shape instance
   * @param color the color in which the shape will be drawn.
   * @param stroke the stroke in which the shape will be drawn, or null
   * to use the default one-pixel line.
   * @param fill true if the shape is to be area-filled; false if only
   * an outline is required.
   */
  public void addUnderlay(Shape shape, Color color, BasicStroke stroke, boolean fill) {
    if (shape == null) {
      throw new NullPointerException("Null shape specified");
    }
    underlays.add(new Layer(shape, color, stroke, fill));
  }

  /**
   * Adds a shape to be rendered after the Delaunay triangulation (TIN)
   * content is drawn. Overlays will be placed on top of the TIN depiction
   * and may cover features that are drawn below them.
   *
   * @param shape a valid shape instance
   * @param color the color in which the shape will be drawn.
   * @param stroke the stroke in which the shape will be drawn, or null
   * to use the default one-pixel line.
   * @param fill true if the shape is to be area-filled; false if only
   * an outline is required.
   */
  public void addOverlay(Shape shape, Color color, BasicStroke stroke, boolean fill) {
    if (shape == null) {
      throw new NullPointerException("Null shape specified");
    }
    overlays.add(new Layer(shape, color, stroke, fill));
  }

  /**
   * Depicts the content of the Delaunay triangulation (TIN) associated
   * with this instance. The results are returned in the form of an
   * BufferedImage.
   * <p>
   * This method is a wrapper around the render method. The render method
   * returns a result that provides additional metadata about the image.
   * The metadata may be useful for interactive applications or in cases
   * where the developer wishes to depict features not directly supported
   * by this utility.
   *
   * @param width the width of the image
   * @param height the height of the image
   * @param pad reserved space between the Delaunay triangulation features
   * and the edge of the image; may be used for labels, etc.
   * @return if successful, a valid instance.
   */
  public BufferedImage renderImage(int width, int height, int pad) {
    RenderingSurfaceAid rsa = render(width, height, pad);
    return rsa.getBufferedImage();
  }

  /**
   * Depicts the content of the Delaunay triangulation (TIN) associated
   * with this instance. The results are returned in the form of an
   * instance of the RenderingSurfaceAid class. The results include both
   * a BufferedImage instance and a pair of associated AffineTransform instances
   * that allow an application to convert coordinate between pixels
   * and the real-valued coordinate system associated with the TIN.
   *
   * @param width the width of the image
   * @param height the height of the image
   * @param pad reserved space between the Delaunay triangulation features
   * and the edge of the image; may be used for labels, etc.
   * @return if successful, a valid instance.
   */
  public RenderingSurfaceAid render(int width, int height, int pad) {
    if (tin == null || !tin.isBootstrapped()) {
      // invalid TIN, return blank image
      RenderingSurfaceAid rsa = new RenderingSurfaceAid(
        width, height, pad, 0, 0, width - 1, height - 1, true);
      Graphics2D g2d = rsa.getGraphics2D();
      g2d.setColor(background);
      g2d.fillRect(0, 0, width + 1, height + 1);
      if (drawFrameEnabled) {
        g2d.setColor(foreground);
        g2d.drawRect(0, 0, width - 1, height - 1);
      }
      return rsa;
    }

    Rectangle2D r2d = tin.getBounds();
    double yLower = r2d.getMinY();
    double yUpper = r2d.getMaxY();
    if (this.coordinateSystemIsPixels) {
      yUpper = r2d.getMinY();
      yLower = r2d.getMaxY();
    }

    RenderingSurfaceAid rsa = new RenderingSurfaceAid(
      width, height, pad,
      r2d.getMinX(), yLower, r2d.getMaxX(), yUpper,
      true);

    render(rsa);
    return rsa;
  }


  /**
   * Uses the application-provided RenderingSurfaceAir to depicts the content
   * of the Delaunay triangulation (TIN) associated with this instance.
   * @param rsa A valid instance providing coordinate transforms and graphics
   * resources for depiction.
   */
  public void render(RenderingSurfaceAid rsa){
	if (tin == null || !tin.isBootstrapped()) {
		return;
	}
	BufferedImage bImage = rsa.getBufferedImage();
	int width = bImage.getWidth();
	int height = bImage.getHeight();			
    Graphics2D g2d = rsa.getGraphics2D();
    if (background != null) {
      g2d.setColor(background);
      g2d.fillRect(0, 0, width + 1, height + 1);
    }
    if (drawFrameEnabled) {
      rsa.drawFrame(foreground);
    }

    Line2D l2d = new Line2D.Double();
    Ellipse2D e2d = new Ellipse2D.Double();
    double[] xy = new double[8];

    AffineTransform af = rsa.getCartesianToPixelTransform();

    // plot underlays
    for (Layer layer : underlays) {
      g2d.setColor(layer.color);
      g2d.setStroke(layer.stroke);
      Shape s = af.createTransformedShape(layer.shape);
      if (layer.fill) {
        g2d.fill(s);
      }
      g2d.draw(s);
    }

    if (edgeRenderingEnabled) {
      g2d.setStroke(thinStroke);
      g2d.setColor(edgeColor);
      for (IQuadEdge edge : tin.edges()) {
        Vertex A = edge.getA();
        Vertex B = edge.getB();
        xy[0] = A.getX();
        xy[1] = A.getY();
        xy[2] = B.getX();
        xy[3] = B.getY();
        af.transform(xy, 0, xy, 4, 2);
        l2d.setLine(xy[4], xy[5], xy[6], xy[7]);
        g2d.draw(l2d);
        if (edgeLabelEnabled) {
          drawEdgeLabel(g2d, af, edge);
          if(edgeLabelDualSideConfiguration){
            drawEdgeLabel(g2d, af, edge.getDual());
          }
        }
      }
    }

    if (vertexRenderingEnabled) {
      g2d.setColor(foreground);
      g2d.setStroke(thinStroke);
      for (Vertex v : tin.vertices()) {
        xy[0] = v.getX();
        xy[1] = v.getY();

        af.transform(xy, 0, xy, 2, 1);
        double vs = vertexRenderingSize;
        e2d.setFrame(xy[2] - vs / 2, xy[3] - vs / 2, vs, vs);

        g2d.fill(e2d);
        g2d.draw(e2d);

        if (vertexLabelEnableIndex || vertexLabelEnableZ) {
          String s = "";
          if (this.vertexLabelEnableIndex) {
            s += v.getLabel();
          }
          if (this.vertexLabelEnableZ) {
            if (vertexLabelEnableIndex) {
              s += "|";
            }
            s += String.format(vertexLabelFormatZ, v.getZ());
          }
          if (!s.isBlank()) {
            g2d.drawString(s, (float) xy[2] + 3f, (float) xy[3] - 3f);
          }
        }
      }
    }

    // plot overlays
    for (Layer layer : overlays) {
      g2d.setColor(layer.color);
      g2d.setStroke(layer.stroke);
      Shape s = af.createTransformedShape(layer.shape);
      if (layer.fill) {
        g2d.fill(s);
      }
      g2d.draw(s);
    }
  }

  /**
   * Sets the background color for the image.
   *
   * @param background a valid instance.
   */
  public void setBackgroundColor(Color background) {
    this.background = background;
  }

  /**
   * Sets the foreground color used to draw vertices, frames, and other
   * features.
   *
   * @param foreground a valid instance.
   */
  public void setForegroundColor(Color foreground) {
    this.foreground = foreground;
  }

  /**
   * Enables the drawing of a rectangular frame around the image
   *
   * @param enabled true if a frame is to be rendered; otherwise false.
   */
  public void setDrawFrameEnabled(boolean enabled) {
    drawFrameEnabled = enabled;
  }

  /**
   * Sets the color for drawing edges.
   *
   * @param edgeColor a valid instance.
   */
  public void setEdgeColor(Color edgeColor) {
    this.edgeColor = edgeColor;
  }

  /**
   * Sets the rendering operation to draw edges.
   *
   * @param enabled true if edges are to be drawn; false if edges are not
   * rendered.
   */
  public void setEdgeRenderingEnabled(boolean enabled) {
    edgeRenderingEnabled = enabled;
  }

  /**
   * Sets the render operation to label edges.  By default, only the
   * <i>baseline side</i> for an edge is labeled. But the label selection
   * can be configured for two-sided labeling using the
   * setEdgeLabelDualSideConfiguration method.
   *
   * @param enabled true if edges are labeled; false if edge labels are not
   * rendered.
   */
  public void setEdgeLabelEnabled(boolean enabled) {
    edgeLabelEnabled = enabled;
  }

  /**
   * Sets the edge-rendering operation to label both sides of an edge.
   * By default, only the <i>baseline side</i> for an edge is labeled.
   * Single sided labeling is advantageous in cases where an image includes
   * a dense arrangement of edges and might become overly cluttered if edges
   * were labeled on both sides.
   * @param enabled true if both sides of an edge are to be labeled; false
   * if only the baseline-side of the edge (the even-numbered side) is labeled.
   */
  public void setEdgeLabelDualSideConfiguration(boolean enabled){
    edgeLabelDualSideConfiguration = enabled;
    if(enabled){
      edgeLabelEnabled = true;
    }
  }

  /**
   * Sets the render operation to draw vertices.
   *
   * @param enabled true if vertices are to be drawn; false if vertices are not
   * rendered.
   */
  public void setVertexRenderingEnabled(boolean enabled) {
    this.vertexRenderingEnabled = enabled;
  }

  /**
   * Sets the render operation to label vertices based on their integer index
   * value.
   *
   * @param enabled true if index-value rendering is enabled; otherwise false.
   */
  public void setVertexLabelEnabledToShowIndex(boolean enabled) {
    vertexLabelEnableIndex = enabled;
  }

  /**
   * Sets the render operation to label vertices based on their z value.
   *
   * @param enabled true if z-value rendering is enabled; otherwise false.
   */
  public void setVertexLabelEnabledToDisplayZ(boolean enabled) {
    vertexLabelEnableZ = enabled;

  }

  /**
   * Sets the format used when creating a label based on the numerical
   * value of the z coordinate for a vertex.
   *
   * @param format a valid Java format string.
   */
  public void setVertexLabelFormatZ(String format) {
    vertexLabelFormatZ = format;
  }

  private void drawEdgeLabel(Graphics2D g2d, AffineTransform af, IQuadEdge edge) {

    g2d.setStroke(thinStroke);
    g2d.setColor(edgeColor);
    double[] xy = new double[8];
    Vertex A = edge.getA();
    Vertex B = edge.getB();
    xy[0] = A.getX();
    xy[1] = A.getY();
    xy[2] = B.getX();
    xy[3] = B.getY();
    af.transform(xy, 0, xy, 4, 2);
    // compute the midpoint  and the perpendicular (px, py)
    // in pixel coordinates.  The perpendicular is to the left
    // of the edge. The edge has unit coordinates ux, uy.
    // But, since we are working in pixel coordinates,
    // the perpendicular is (uy, -ux). This computation is different than
    // the perpendicular for Cartesian coordinates, which would be(-uy, ux).
    double xMidpoint = (xy[4] + xy[6]) / 2;
    double yMidpoint = (xy[5] + xy[7]) / 2;
    double ux = xy[6] - xy[4];
    double uy = xy[7] - xy[5];
    double d = Math.sqrt(ux * ux + uy * uy);
    if (d < 1) {
      return;
    }
    ux /= d;
    uy /= d;
    double px = uy;
    double py = -ux;
    String s = Integer.toString(edge.getIndex());
    Font font = labelFont;
    if (edge.isConstrained()) {
      font = constraintFont;
      g2d.setFont(constraintFont);
      if (edge.isConstraintRegionBorder()) {
        int condex = edge.getConstraintBorderIndex();
        if (condex < 0) {
          s += "c--";
        } else {
          s += "c" + condex;
        }

      }
      if(edge.isConstraintLineMember()){
        // edge is a constrained line.  If this edge is also a border
        // then the associated constraint index will not be available from
        // the edge itself and must be obtained from the TIN. It should never
        // be null, but we check anyway just to be sure
        IConstraint constraint = tin.getLinearConstraint(edge);
        if(constraint!=null){
          int condex = constraint.getConstraintIndex();
          s += "n" + condex;
        }
      }
    } else {
      g2d.setFont(labelFont);
      if(edge.isConstraintRegionInterior()){
        int condex = edge.getConstraintRegionInteriorIndex();
        s+="i"+condex;
      }
    }

    FontRenderContext frc = new FontRenderContext(null, true, true);
    TextLayout labelLayout = new TextLayout(s, font, frc);
    Rectangle2D r2d = labelLayout.getPixelBounds(frc, 0, 0);

    double xOffset = r2d.getCenterX();
    double yOffset = r2d.getCenterY();
    double xCenter;
    double yCenter;

    if (px > CENTERING_THRESHOLD) {
      xCenter = r2d.getCenterX();
    } else if (px < -CENTERING_THRESHOLD) {
      xCenter = -r2d.getCenterX();
    } else {
      xCenter = 0;
    }

    if (py > CENTERING_THRESHOLD) {
      yCenter = -r2d.getCenterY();
    } else if (py < -CENTERING_THRESHOLD) {
      yCenter = r2d.getCenterY();
    } else {
      yCenter = 0;
    }

    // For horizontal and nearly horizontal lines
    // The text will tend to sit to close to line.
    // The yBaselineFactor adds a slight vertical adjustment
    // to increase the gap size between the text and the line.
    double yBaselineFactor;

    if (Math.abs(py) >= 0.86602) {
      // the line inclination steepness is greater than 30 degrees
       yBaselineFactor = Math.signum(py) * 1.5;
    }else{
      yBaselineFactor = 0;
    }

    // For nearly vertical lines, also add a little spacing
    double xSidewaysFactor;
    if(Math.abs(px) >= 0.9659){
      xSidewaysFactor = Math.signum(px) * 1.5;
    }else{
      xSidewaysFactor = 0;
    }

    // To improve the visual intelligibility of the label, we wish
    // to put the position of the text close to the midpoint
    // of the line.  If we were to just put some corner of the text
    // on the midpoint, the center of the text might be off to the
    // side a bit. So we slide the text some distance along
    // the vector (ux, uy) to make its position look more centered.
    // The perpencicular vector (px, py) tells us which direction
    // to put the text. Based on (px, py), compute the coordinates
    // of a point at the center of the text, (xCenter, yCenter).
    // Treating xCenter, yCenter as a vector, we compute
    // the coordinate of the vector in the direction of vector u = (ux, uy).
    // this will give us the distance to slide the text center
    // towards midpoint (mx, my).
    double a = ux * xCenter + uy * yCenter;
    double x = xMidpoint - xOffset + xCenter - a * ux + xSidewaysFactor;
    double y = yMidpoint - yOffset + yCenter - a * uy + yBaselineFactor;
    g2d.drawString(s, (float) x, (float) y);
  }


  /**
   * Creates a Path2D from a list of edges. In cases where the first vertex
   * in an edge matches the second vertex in the previous edge, it is assumed
   * that the edges are connected and they will be jointed using the Path2D.lineTo()
   * method. In cases where there is a gap between edges, they will be processed
   * using the Path2D.moveTo() method.
   * @param edges a list containing zero or more edges.
   * @return a valid, potentially empty, Path2D instance
   */
  public Path2D transcribeEdgesToPath2D(List<IQuadEdge> edges) {
    Path2D path = new Path2D.Double();
    if (edges.isEmpty()) {
      return path;
    }
    IQuadEdge e0 = edges.get(0);
    Vertex prior = e0.getA();
    path.moveTo(prior.getX(), prior.getY());
    for (IQuadEdge e : edges) {
      Vertex A = e.getA();
      if (A.getDistance(prior) != 0) {
        path.moveTo(A.getX(), A.getY());
      }
      Vertex B = e.getB();
      path.lineTo(B.getX(), B.getY());
      prior = B;
    }

    return path;
  }

  /**
   * Indicates that the coordinate system associated with the Delaunay
   * triangulation should be rendered as if the coordinates were based on
   * pixels rather than a Cartesian coordinate system.  In a pixel system
   * y coordinates are defined to be increasing downward (so that the origin
   * would be the upper-left corner of the display surface).  Setting the
   * coordinate system to be based on pixels has the effect of flipping the
   * rendering upside down.
   * @param coordinateSystemIsPixels true if the coordinate system is to be
   * treated as based on pixels; false if the corrdinate system is to be treated
   * as Cartesian.
   */
  public void setCoordinateSystemIsPixels(boolean coordinateSystemIsPixels){
    this.coordinateSystemIsPixels = coordinateSystemIsPixels;
  }

  /**
   * Set the size in pixels for the vertex symbol.  The default size
   * is 5 pixels.
   * @param size a positive floating point value, in pixels.
   */
  public void setVertexRenderingSize(double size){
    vertexRenderingSize = size;
  }

}
