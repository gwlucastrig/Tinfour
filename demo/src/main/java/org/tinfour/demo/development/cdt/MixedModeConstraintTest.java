/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 *    The idea of this test is that we create a pair of adjacent 
 *    polygon constraints given as square regions organized in a 
 *    regular grid.  Then we add overlapping linear constraints.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.development.cdt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.demo.utils.IDevelopmentTest;
import org.tinfour.demo.utils.TestOptions;
import org.tinfour.utils.TriangleCollector;
import org.tinfour.utils.rendering.RenderingSurfaceAid;

/**
 * Provides a test of mixed-type constraints.
 */
public class MixedModeConstraintTest implements IDevelopmentTest {

  static class TriangleRenderer implements Consumer<SimpleTriangle> {

    private final AffineTransform af;
    Graphics2D g2d;
    IIncrementalTin tin;

    TriangleRenderer(Graphics2D g2d, AffineTransform af, IIncrementalTin tin) {
      this.g2d = g2d;
      this.af = af;
      this.tin = tin;
    }

    @Override
    public void accept(SimpleTriangle t) {
      IQuadEdge a = t.getEdgeA();
      Color color = Color.white;
      IConstraint con = tin.getRegionConstraint(a);
      if (con != null) {
        Object obj = con.getApplicationData();
        if ("L".equals(obj)) {
          color = Color.orange;
        } else if ("R".equals(obj)) {
          color = Color.yellow;
        }
      }

      Path2D path2d = t.getPath2D(af);
      g2d.setColor(color);
      g2d.fill(path2d);
      g2d.draw(path2d);
    }
  }

  static int iVertex;
  static final float dash[] = {10.0f};
 
  /**
   * Create a test image.
   *
   * @param args the command line arguments
   * @throws java.io.IOException in the event of an unrecoverable IO exception.
   */
  public static void main(String[] args) throws IOException {
    MixedModeConstraintTest mmcTest = new MixedModeConstraintTest();
    mmcTest.runTest(System.out, args);
  }

  @Override
  public void runTest(PrintStream ps, String args[]) {
    // at this time, only options are implemented.
    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);
    Class<?> tinClass = options.getTinClass();
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Multi-square Constraint Test");
    ps.format("Date of test:       %s UTC%n", sdFormat.format(date));
    ps.format("TIN class:          %s%n", tinClass.getName());

    List<Vertex> vList = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j <= 4; j++) {
        vList.add(new Vertex(j, i, 0, iVertex++));
        if (i < 4 && j < 4) {
          vList.add(new Vertex(j + 0.5, i + 0.5, 0, iVertex++));
        }
      }
    }
    List<IConstraint> conList = new ArrayList<>();
    // make Left and Right side polygon constraints (region constraints)
    conList.add(makePoly("L", 0, 0, 2, 4));
    conList.add(makePoly("R", 2, 0, 4, 4));
    // make vertical and horizontal linear constraints
    conList.add(makeHorizontal("H1", -1, 5, 1));  // y = 1
    conList.add(makeHorizontal("H3", -1, 5, 3)); // y = 3
    conList.add(makeVertical("V1", 1, -1, 5));
    conList.add(makeVertical("V4", 3, -1, 5));
    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    tin.add(vList, null);
    tin.addConstraints(conList, false);
    IIntegrityCheck checker = tin.getIntegrityCheck();
    if (checker.inspect()) {
      ps.println("Integrity check passed");
    } else {
      ps.println("Integrity check failed " + checker.getMessage());
      return;
    }

    Rectangle2D bounds = tin.getBounds();
    double x0 = bounds.getMinX();
    double y0 = bounds.getMinY();
    double x1 = bounds.getMaxX();
    double y1 = bounds.getMaxY();
    RenderingSurfaceAid rsa;

    int width = 400;
    int height = 400;
    rsa = new RenderingSurfaceAid(width, height, 10, x0, y0, x1, y1);
    rsa.fillBackground(Color.white);

    BufferedImage bImage = rsa.getBufferdImage();
    Graphics2D g2d = rsa.getGraphics2D();
    AffineTransform af = rsa.getCartesianToPixelTransform();
    g2d.setStroke(new BasicStroke(1.0f));

    TriangleRenderer tRend = new TriangleRenderer(g2d, af, tin);
    TriangleCollector.visitSimpleTriangles(tin, tRend);
    g2d.setColor(Color.lightGray);
    for (IQuadEdge edge : tin.edges()) {
      if (!edge.isConstrained()) {
        double[] c = new double[8];
        c[0] = edge.getA().getX();
        c[1] = edge.getA().getY();
        c[2] = edge.getB().getX();
        c[3] = edge.getB().getY();
        af.transform(c, 0, c, 4, 2);
        Line2D l2d = new Line2D.Double(c[4], c[5], c[6], c[7]);
        g2d.draw(l2d);
      }
    }
    BasicStroke solid = new BasicStroke(3.0f);
    BasicStroke dashx = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            10.0f, dash, 0.0f);
    g2d.setColor(Color.black);
    for (IConstraint c : conList) {
      if (c.definesConstrainedRegion()) {
        g2d.setStroke(solid);
      } else {
        g2d.setStroke(dashx);
      }
      Path2D path = c.getPath2D(af);
      g2d.draw(path);
    }

    try {
      ImageIO.write(bImage, "PNG", new File("test.png"));
    } catch (IOException ioex) {
      ioex.printStackTrace(ps);
    }
   
  }

  String fail(IQuadEdge edge, String message){
    return "Edge +("+edge.getIndex()+"): "+message;
  }
  
  String eString (IQuadEdge edge){
    Vertex A=edge.getA();
    Vertex B = edge.getB();
    return edge.getIndex()+": "+A+", "+B+">> "+edge.toString();
  }
  
  
  PolygonConstraint makePoly(String label, int xMin, int yMin, int xMax, int yMax) {
    PolygonConstraint p = new PolygonConstraint();
    p.setApplicationData(label);
    for (int x = xMin; x <= xMax; x++) {
      Vertex v = new Vertex(x, yMin, 0, iVertex++);
      p.add(v);
    }
    for (int y = yMin; y <= yMax; y++) {
      Vertex v = new Vertex(xMax, y, 0, iVertex++);
      p.add(v);
    }
    for (int x = xMax; x >= xMin; x--) {
      Vertex v = new Vertex(x, yMax, 0, iVertex++);
      p.add(v);
    }
    for (int y = yMax; y >= yMin; y--) {
      Vertex v = new Vertex(xMin, y, 0, iVertex++);
      p.add(v);
    }
    p.complete();
    return p;
  }

  IConstraint makeHorizontal(String label, int xMin, int xMax, int y) {
    LinearConstraint lc = new LinearConstraint();
    lc.setApplicationData(label);
    for (int x = xMin; x <= xMax; x++) {
      Vertex v = new Vertex(x, y, 0, iVertex++);
      lc.add(v);
    }
    lc.complete();
    return lc;
  }

  IConstraint makeVertical(String label, int x, int yMin, int yMax) {
    LinearConstraint lc = new LinearConstraint();
    lc.setApplicationData(label);
    for (int y = yMin; y <= yMax; y++) {
      Vertex v = new Vertex(x, y, 0, iVertex++);
      lc.add(v);
    }
    return lc;
  }

  boolean isVertical(IQuadEdge edge) {
    Vertex A = edge.getA();
    Vertex B = edge.getB();
    return A.getX()==B.getX();
  }

  boolean isHorizontal(IQuadEdge edge) {
    Vertex A = edge.getA();
    Vertex B = edge.getB(); 
    return A.getY()==B.getY();
  }
}
