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
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.alphashape.AlphaShape;
import org.tinfour.utils.rendering.RenderingSurfaceAid;

/**
 * Demonstrates the use of the Tinfour AlphaShape class and its
 * triangle iterator.
 * This implementation uses a conventional Java String (typically a single
 * letter or other character) to provide vertices for analysis by the
 * AlphaShape class.
 */
public class AlphaShapeTriangleIterator {

  /**
   * Creates an image file containing a depiction of a Delaunay triangulation
   * and the associated alpha shape. 
   *
   * @param args the command line arguments (not used at this time)
   * @throws java.io.IOException in the event of an unrecoverable I/O error.
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
    double alphaRadius = 3.0;

    String outputFileName = "AlphaShapeTriangleIterator.png";
    int outputImageWidth = 1000;
    int outputImageHeight = 800;
    int outputImagePad = 50;

    // -----------------------------------------------------------------
    // Process data:
    //    1.  Generate vertices using font and text options
    //    2.  Create a Delaunay triangulation using Tinfour's IncrementalTin class
    //    3.  Use the triangulation to create an alpha shape.
    List<Vertex> vertices = new ArrayList<>();
    AlphaShapeTestVertices.makeVerticesFromText(vertices, font, text, populateInteriorVertices);
    IIncrementalTin tin = new IncrementalTin(1);
    tin.add(vertices, null);
    AlphaShape alpha = new AlphaShape(tin, alphaRadius);

    Rectangle2D bounds = tin.getBounds();
    RenderingSurfaceAid rsa
      = new RenderingSurfaceAid(outputImageWidth, outputImageHeight, outputImagePad,
        bounds.getMinX(),
        bounds.getMinY(),
        bounds.getMaxX(),
        bounds.getMaxY(),
        false);

    AffineTransform c2p = rsa.getCartesianToPixelTransform();
    BasicStroke thinStroke = new BasicStroke(1.0f);
    BasicStroke thickStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    BufferedImage image = rsa.getBufferedImage();
    Graphics2D g2d = rsa.getGraphics2D();
    g2d.setColor(Color.lightGray);
    g2d.fillRect(0, 0, outputImageWidth, outputImageHeight);
    double[] xy = new double[12];

    List<Path2D> trianglePaths = new ArrayList<>();
    for (SimpleTriangle t : alpha.triangles()) {
      Vertex A = t.getVertexA();
      Vertex B = t.getVertexB();
      Vertex C = t.getVertexC();
      double area = t.getArea();
      if(area<=0){
        continue;
      }
      xy[0] = A.getX();
      xy[1] = A.getY();
      xy[2] = B.getX();
      xy[3] = B.getY();
      xy[4] = C.getX();
      xy[5] = C.getY();
      c2p.transform(xy, 0, xy, 6, 3);

      Path2D path = new Path2D.Double();
      path.moveTo(xy[6], xy[7]);
      path.lineTo(xy[8], xy[9]);
      path.lineTo(xy[10], xy[11]);
      path.closePath();
      trianglePaths.add(path);
    }

    g2d.setColor(Color.magenta);
    g2d.setStroke(thinStroke);
    for (Path2D path : trianglePaths) {
      g2d.fill(path);
    }

    g2d.setColor(Color.white);
    g2d.setStroke(thickStroke);
    for (Path2D path : trianglePaths) {
      g2d.draw(path);
    }

    Path2D alphaPath = alpha.getPath2D(true);
    g2d.setColor(Color.black);
    Shape shapeLines = c2p.createTransformedShape(alphaPath);
    g2d.draw(shapeLines);

    System.out.println("");
    System.out.println("Writing output to file " + outputFileName);
    File output = new File(outputFileName);
    if (output.exists()) {
      output.delete();
    }
    ImageIO.write(image, "PNG", output);
  }

}
