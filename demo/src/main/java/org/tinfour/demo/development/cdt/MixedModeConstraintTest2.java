/* --------------------------------------------------------------------
 * Copyright 2025 Gary W. Lucas.
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
 * 06/2025  G. Lucas     Created
 *
 * Notes:
 *    The idea of this test is that we create a mix linear constraints
 * overlapping a polygon constraint and verify that all edges in the
 * resulting Delaunay triangulation have the correct constraint assignments.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.development.cdt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Line2D;
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
import javax.imageio.ImageIO;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.demo.utils.IDevelopmentTest;
import org.tinfour.demo.utils.TestOptions;
import org.tinfour.utils.rendering.RendererForTinInspection;

/**
 * Provides a test of mixed-type constraints.
 */
public class MixedModeConstraintTest2 implements IDevelopmentTest {
 
  static int iVertex;


  /**
   * Create a test image.
   *
   * @param args the command line arguments
   * @throws java.io.IOException in the event of an unrecoverable IO exception.
   */
  public static void main(String[] args) throws IOException {
    MixedModeConstraintTest2 mmcTest = new MixedModeConstraintTest2();
    mmcTest.runTest(System.out, args);
  }

  @Override
  public void runTest(PrintStream ps, String args[]) throws IOException {
    // at this time, only options are implemented.
    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForUnrecognizedArgument(args, optionsMatched);
    Class<?> tinClass = options.getTinClass();
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Mixed-mode Constraint Test 2");
    ps.format("Date of test:       %s UTC%n", sdFormat.format(date));
    ps.format("TIN class:          %s%n", tinClass.getName());

    // Establish vertices over the domain (0, 0) to (5, 5).
    Vertex v0 = new Vertex(0, 0, 0, iVertex++);
    Vertex v1 = new Vertex(4, 0, 0, iVertex++);
    Vertex v2 = new Vertex(4, 4, 0, iVertex++);
    Vertex v3 = new Vertex(0, 4, 0, iVertex++);

    List<Vertex> vList = new ArrayList<>();
    vList.add(v0);
    vList.add(v1);
    vList.add(v2);
    vList.add(v3);

    List<IConstraint> conList = new ArrayList<>();
    // make a rectangular polygon constraint
    conList.add(makePoly(1, 1, 3, 3, new Color(240, 240, 240)));
    // make vertical linear constraints
    conList.add(makeVertical(1, 0, 4,Color.red));
    conList.add(makeVertical(2, 0, 4, Color.green));
    conList.add(makeVertical(3, 0, 4, Color.blue));

    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    tin.add(vList, null);
    tin.addConstraints(conList, true);

    RendererForTinInspection rti = new RendererForTinInspection(tin);
    rti.setEdgeLabelDualSideConfiguration(true);
    rti.setEdgeColor(Color.black);

    rti.addUnderlay(new Rectangle2D.Double(1, 1, 2, 2), new Color(232, 232, 232), null, true);
    BasicStroke thickStroke = new BasicStroke(5.0f);
    for(IQuadEdge e: tin.edges()){
      Color c = null;
      if(e.isConstraintRegionInterior()){
        c = Color.gray;
      }else if(e.isConstraintRegionBorder()){
        c = Color.orange;
      }
      if(e.isConstraintLineMember()){
        IConstraint constraint = tin.getLinearConstraint(e);
        c = (Color)(constraint.getApplicationData());
      }
      if(c!=null){
        Vertex A = e.getA();
        Vertex B = e.getB();
        Line2D l2d = new Line2D.Double(A.getX(), A.getY(), B.getX(), B.getY());
        rti.addUnderlay(l2d, c, thickStroke, false);
      }
    }

    for(IQuadEdge e: tin.edges()){
      System.out.println(e.toString());
      System.out.println(e.getDual().toString());
    }

      BufferedImage bImage = rti.renderImage(900, 900, 100);
    File output = new File("MixedModeConstraintTest2.png");
    if (output.exists()) {
      output.delete();
    }
    ImageIO.write(bImage, "PNG", output);


  }


  PolygonConstraint makePoly(int xMin, int yMin, int xMax, int yMax, Color appData) {
    PolygonConstraint p = new PolygonConstraint();
    p.setApplicationData(appData);
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

  IConstraint makeVertical(int x, int yMin, int yMax, Color appData) {
    LinearConstraint lc = new LinearConstraint();
    lc.setApplicationData(appData);
    for (int y = yMin; y <= yMax; y++) {
      Vertex v = new Vertex(x, y, 0, iVertex++);
      lc.add(v);
    }
    return lc;
  }

}
