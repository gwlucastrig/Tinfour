/**
 * Provides a demonstration implementation illustrating ways to use
 * the Tinfour AlphaShape class. This demonstration uses a character glyph
 * as a source of vertices for a Delaunay triangulation.  The resulting
 * Incremental TIN instance is processed using the AlphaShape class and
 * an image is created for inspection.
 */
package org.tinfour.demo.examples.alphashape;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.alphashape.AlphaCircle;
import org.tinfour.utils.alphashape.AlphaPart;
import org.tinfour.utils.alphashape.AlphaShape;
import org.tinfour.utils.rendering.RendererForTinInspection;

/**
 * Demonstrates the use of the Tinfour AlphaShape class.
 * This implementation uses a conventional Java String (typically a single
 * letter or other character) to provide vertices for analysis by the
 * AlphaShape class.
 */
public class AlphaShapeDemoImage {

  /**
   * Creates an image file containing a depiction of a Delaunay triangulation
   * and the associated alpha shape. Developers may configure this
   * demonstration by adjusting the parameters found in the main method.
   *
   * @param args the command line arguments (not used at this time)
   */
  public static void main(String[] args) throws IOException {
    // ------------------------------------------------------------
    // Options for building the alpha shape from a simple letter form
    // given by the string named "text".
    // The font size and radius were chosen through trial-and-error.
    // The alpha-radius gives the radius of the alpha-circles used for
    // analysis.  The populate-internal-vertices option can be used
    // to fill the interior of the letter form with randomly generated
    // vertices.  Interior vertices will increase the density of the
    // input data.  If you use the high-density configuration, it may be
    // advantageous to suppress the labeling of edges and vertices
    // in order to avoid a cluttered image.

    Font font = new Font("Arial", Font.PLAIN, 72);
    String text = "A";
    boolean populateInteriorVertices = true;
    double alphaRadius = 4.25;

    // Options for display and image production
    boolean labelVertices = false;
    boolean labelEdges = false;
    boolean drawAlphaShape = true;
    boolean fillAlphaShape = true;
    boolean fillInputShape = false;
    boolean showAlphaClassificationForEdges = false;
    boolean printDiagnosticText = false;
    String outputFileName = "AlphaShapeTestImage.png";
    int outputImageWidth = 500;
    int outputImageHeight = 500;

    // -----------------------------------------------------------------
    // Process data:
    //    1.  Generate vertices using font and text options
    //    2.  Create a Delaunay triangulation using Tinfour's IncrementalTin class
    //    3.  Use the triangulation to create an alpha shape.
    List<Vertex> vertices = new ArrayList<>();
    Shape inputShape = makeTestVertices(vertices, font, text, populateInteriorVertices);
    IIncrementalTin tin = new IncrementalTin(1);
    tin.add(vertices, null);
    AlphaShape alpha = new AlphaShape(tin, alphaRadius);

    if (printDiagnosticText) {
      printDiagnosticText(tin, alpha);
    }

    verifyCircleTestPlausibility(tin, alphaRadius, vertices);

    RendererForTinInspection renderer = new RendererForTinInspection(tin);
    renderer.setEdgeLabelEnabled(labelEdges);
    renderer.setVertexLabelEnabledToShowIndex(labelVertices);

    BasicStroke thinStroke = new BasicStroke(1.0f);
    BasicStroke thickStroke = new BasicStroke((4.0f));

    if (fillInputShape) {
      // Filling the input shape provides a way of inspecting
      // how well an alpha shape matches the source data.
      Color fillColor = new Color(0, 0, 0, 16);
      renderer.addOverlay(inputShape, fillColor, thinStroke, true);
    }

    if (fillAlphaShape) {
      Path2D alphaShape = alpha.getPath2D();
      Color fillColor = new Color(0, 0, 0, 16);
      renderer.addOverlay(alphaShape, fillColor, thinStroke, true);
    }

    if (drawAlphaShape) {
      Path2D alphaShape = alpha.getPath2D();
      renderer.addOverlay(alphaShape, Color.red, thickStroke, false);
    }

    if (showAlphaClassificationForEdges) {
      showAlphaClassifications(tin, alphaRadius, renderer);
    }

    BufferedImage image
      = renderer.renderImage(outputImageWidth, outputImageHeight, 50);
    File output = new File(outputFileName);
    if (output.exists()) {
      output.delete();
    }
    ImageIO.write(image, "PNG", output);
  }

  /**
   * Make a list of vertices for test input using a letter form.
   * Although letter forms are not necessarily an ideal match for the
   * assumptions
   * of an alpha shape, they provide a convenient way to generate a test set/
   *
   * @param font the font to be used, should have a point size of at least 72.
   * @param text a non-empty text for input.
   * @param populateInterior populate the interior regions of the letter forms
   * with
   * randomly generated vertices.
   * @return a valid test set.
   */
  static Shape makeTestVertices(List<Vertex> vertices, Font font, String text, boolean populateInterior) {
    AffineTransform flipY = new AffineTransform(1, 0, 0, -1, 0, 0);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    TextLayout layout = new TextLayout(text, font, frc);
    Shape shape = layout.getOutline(new AffineTransform());

    PathIterator path = shape.getPathIterator(flipY, 0.25);
    shape = flipY.createTransformedShape(shape);

    double[] d = new double[6];
    int vIndex = 0; // gives each vertex a unique index
    int n;
    double x0 = 0;
    double y0 = 0;
    while (!path.isDone()) {
      int flag = path.currentSegment(d);
      switch (flag) {
        case PathIterator.SEG_MOVETO:
          vertices.add(new Vertex(d[0], d[1], 0, vIndex++)); // NOPMD
          x0 = d[0];
          y0 = d[1];
          break;
        case PathIterator.SEG_LINETO:
          double x1 = d[0];
          double y1 = d[1];
          double dx = x1 - x0;
          double dy = y1 - y0;
          double dS = Math.sqrt(dx * dx + dy * dy);
          n = (int) Math.ceil(dS / 4);
          for (int i = 1; i < n; i++) {
            double t = (double) i / (double) n;
            double x = t * (x1 - x0) + x0;
            double y = t * (y1 - y0) + y0;
            vertices.add(new Vertex(x, y, 0, vIndex++));
          }
          vertices.add(new Vertex(d[0], d[1], 0, vIndex++));
          x0 = x1;
          y0 = y1;
          break;
        case PathIterator.SEG_CLOSE:
          break;
        default:
          break;
      }
      path.next();
    }

    if (populateInterior) {
      Rectangle2D r2d = shape.getBounds2D();
      Random random = new Random(0);
      double dx = r2d.getWidth() / 100;
      double dy = r2d.getHeight() / 100;
      for (int i = 0; i < 100; i++) {
        for (int j = 0; j < 100; j++) {
          double x = i * dx + r2d.getMinX();
          double y = j * dy + r2d.getMinY();
          if (shape.contains(x, y)) {
            if (random.nextDouble() < 0.125) {
              vertices.add(new Vertex(x, y, 0, vIndex++));
            }
          }
        }
      }
    }

    return shape;
  }

  /**
   * A procedure to verify that by testing the vertices directly opposite an
   * edge
   * we can determine the alpha exposure for that edge without looking at
   * other vertices. In the absence of a mathematic proof, this test
   * will at least indicate the plausibility of that claim. Though, of course,
   * the lack of a negative result is not a proof...
   *
   * @param tin a valid tin
   * @param radius the alpha radius for testing
   * @param vertices a list of the vertices in the TIN.
   */
  static void verifyCircleTestPlausibility(IIncrementalTin tin, double radius, List<Vertex> vertices) {
    for (IQuadEdge e : tin.edges()) {
      if (e.getLength() >= 2 * radius) {
        continue;
      }
      Vertex A = e.getA();
      Vertex B = e.getB();
      Vertex C = e.getForward().getB();
      Vertex D = e.getForwardFromDual().getB();
      AlphaCircle circle = new AlphaCircle(radius, A.getX(), A.getY(), B.getX(), B.getY());
      boolean cStat = C != null && circle.isPointInCircles(C.getX(), C.getY());
      boolean dStat = D != null && circle.isPointInCircles(D.getX(), D.getY());
      if (!cStat && !dStat && D != null) {
        for (Vertex v : vertices) {
          if (v != A && v != B && v != C && v != D) {
            boolean status = circle.isPointInCircles(v.getX(), v.getY());
            if (status) {
              System.out.println("AlphaCircle assumption failed for edge " + e.getIndex() + ", vertex " + v);
              circle.isPointInCircles(v.getX(), v.getY());
              return;
            }
          }
        }
      }
    }
  }

  /**
   * Adds color-coded highlights to show how edges are rated by the
   * AlphaCircle test: orange for a potential border, green for a fully covered
   * edge. Exposed edges are not changed.
   *
   * @param tin a valid Delaunay triangulation
   * @param radius the alpha radius
   * @param renderer a valid instance
   */
  static void showAlphaClassifications(IIncrementalTin tin, double radius, RendererForTinInspection renderer) {
    Path2D alpha1 = new Path2D.Double();
    Path2D alpha2 = new Path2D.Double();
    for (IQuadEdge edge : tin.edges()) {
      if (edge.getLength() > 2 * radius) {
        continue;
      }
      Vertex A = edge.getA();
      Vertex B = edge.getB();
      Vertex C = edge.getForward().getB();
      Vertex D = edge.getForwardFromDual().getB();
      AlphaCircle circle = new AlphaCircle(radius, A.getX(), A.getY(), B.getX(), B.getY());
      int nC = 0;
      if (C != null && circle.isPointInCircles(C.getX(), C.getY())) {
        nC++;
      }
      if (D != null && circle.isPointInCircles(D.getX(), D.getY())) {
        nC++;
      }

      if (nC == 1) {
        alpha1.moveTo(A.getX(), A.getY());
        alpha1.lineTo(B.getX(), B.getY());
      } else if (nC == 2) {
        alpha2.moveTo(A.getX(), A.getY());
        alpha2.lineTo(B.getX(), B.getY());
      }
    }

    BasicStroke thickStroke = new BasicStroke(5.0f);
    renderer.addUnderlay(alpha1, Color.orange, thickStroke, false);
    renderer.addUnderlay(alpha2, Color.green, thickStroke, false);
  }

  static void printDiagnosticText(IIncrementalTin tin, AlphaShape alpha) {
    System.out.println("");
    System.out.println("Edges ---------------------------------------------");
    for (IQuadEdge edge : tin.edges()) {
      System.out.println("  " + edge.toString());
    }
    System.out.println("");
    System.out.println("Alpha Shape ------------------------------------------");
    List<AlphaPart> partList = alpha.getAlphaParts();
    System.out.println("Number of Parts: " + partList.size());
    for (AlphaPart part : partList) {
      System.out.println("  " + part.toString());
    }
  }
}
