/*
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date    Name      Description
 * ------  --------- -------------------------------------------------
 * 09/2019 G. Lucas  Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.development.cdt;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.TriangleCollector;
import org.tinfour.utils.rendering.RenderingSurfaceAid;
 
/**
 * Provides an example implementation of region-based constraints
 * using the PolygonConstraint class.
 */
public class ConstraintStarDemo extends JPanel {

  final static long serialVersionUID = 1;

  /**
   * Provides logic for rendering triangles produced by the example
   * TIN implementation.
   */
  private static class TriangleRenderer implements Consumer<SimpleTriangle> {

    final Graphics2D g2d;
    final AffineTransform af;

    TriangleRenderer(Graphics2D g2d, AffineTransform af) {
      this.g2d = g2d;
      this.af = af;
    }

    @Override
    public void accept(SimpleTriangle t) {
      IConstraint constraint = t.getContainingRegion();
      if (constraint != null && constraint.definesConstrainedRegion()) {
        Object obj = constraint.getApplicationData();
        if (obj instanceof Color) {
          g2d.setColor((Color) obj);
          Path2D path = t.getPath2D(af);
          g2d.fill(path);
          g2d.draw(path);
        }
      }
    }
  }

  IIncrementalTin tin;
  int iVertex; // a counter for setting diagnostic vertex ID's
  BufferedImage backingImage;
  int backingWidth;
  int backingHeight;

  /**
   * Constructs a content-displaying panel for the demo. The data initialization
   * is conducted in the constructor, then used in the paintComponent call.
   */
  ConstraintStarDemo() {
    super(new BorderLayout());

    // Define two lists, one to receive vertices, one to receive constraints
    List<Vertex> vertices = new ArrayList<>();
    List<IConstraint> constraints = new ArrayList<>();

    // populate the elements to be added to the TIN
    //    1)   a grid of vertices
    //    2)   the constraint polygon in the form of a five-pointed star
    //    3)   the 5 wedge-shaped polygons bordering the star
    //    4)   a circular constraint enclosing the figure
    //  
    addGrid(vertices);
    addConstraintStar(constraints);
    addConstraintWedges(constraints);
    addEnclosingRing(constraints);

    // initialize the TIN, populating it with the elements to be presented
    tin = new IncrementalTin(1.0);
    tin.add(vertices, null);
    tin.addConstraints(constraints, true);
    
    int nInside = 0;
    int nBorder = 0;
    int nOutside = 0;
    for(IQuadEdge edge: tin.edges()){
      if(edge.isConstrainedRegionBorder()){
        nBorder++;
      }else if(edge.isConstrainedRegionInterior()){
        nInside++;
      }else {
        nOutside++;
      }
    }
    System.out.format("Edge Inside Constrained Regions:    %4d%n",nInside);
    System.out.format("Edge Bordering Constrained Regions: %4d%n",nBorder);
    System.out.format("Edge Outside Constrained Regions:   %4d%n",nOutside);
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    if (backingWidth != getWidth() || backingHeight != getHeight()) {
      backingImage = renderImage(getWidth(), getHeight());
      backingWidth = getWidth();
      backingHeight = getHeight();
    }

    if (backingImage != null) {
      g2d.drawImage(backingImage, 0, 0, null);
    }

  }

  static public void main(String[] args) {

    try {
      // Set System L&F
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException
            | InstantiationException
            | IllegalAccessException
            | UnsupportedLookAndFeelException ex) {
      ex.printStackTrace(System.err);
      System.exit(-1);
    }

    final ConstraintStarDemo testPanel = new ConstraintStarDemo();
    testPanel.setPreferredSize(new Dimension(600, 600));

    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        //Turn off metal's use of bold fonts
        UIManager.put("swing.getB()oldMetal", Boolean.FALSE);

        //Create and set up the window.
        JFrame frame = new JFrame("Constraint Star Demonstration");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add content to the window.
        frame.add(testPanel);
        //Display the window.
        frame.pack();
        frame.setVisible(true);
      }
    });

  }

  /**
   * Add vertices in a grid pattern to the vertices collection
   *
   * @param vertices a collection for storing the vertices.
   */
  private void addGrid(List<Vertex> vertices) {
    // create a grid of cells spaced at intervals of 0.25 units
    // will points inserted at the center of each cell.
    // The overall Cartesian coordinates of the grid are (-4, -4) to (4, 4)
    for (int i = 0; i <= 32; i++) {
      for (int j = 0; j <= 32; j++) {
        double y = i / 4.0 - 4;
        double x = j / 4.0 - 4;
        vertices.add(new Vertex(x, y, 0, iVertex++));
        if (i < 32 && j < 32) {
          vertices.add(new Vertex(x + 0.125, y + 0.125, iVertex++));
        }
      }
    }
  }

  // Define angles and dimensions for building the constraints.
  // The notches in the star have a radius of 1 unit.  The points have
  // a computed radius of approximately 2.62 units.
  final static double A6 = Math.toRadians(6);
  final static double A18 = Math.toRadians(18);
  final static double A36 = Math.toRadians(36);
  final static double A72 = Math.toRadians(72);

  final static double rNotch = 1.0; // radius for inner (notch) vertices
  final static double rPoint = rNotch * (Math.cos(A36) + Math.sin(A36) * Math.tan(A72));

  /**
   * Add a region (polygon) constraint in the form of a five-pointed start to
   * the list of constraints.
   *
   * @param constraints a collection for storing constraints.
   */
  private void addConstraintStar(List<IConstraint> constraints) {
    // the polygon is given in counter-clockwise order.
    PolygonConstraint p = new PolygonConstraint();
    p.setApplicationData(new Color(128, 255, 128));  // pale green
    for (int i = 0; i < 5; i++) {
      double a = A18 + i * A72;
      double x = rPoint * Math.cos(a);
      double y = rPoint * Math.sin(a);
      Vertex v = new Vertex(x, y, 0, iVertex++);
      p.add(v);
      a = A36 + A18 + i * A72;
      x = rNotch * Math.cos(a);
      y = rNotch * Math.sin(a);
      v = new Vertex(x, y, 0, iVertex++);
      p.add(v);
    }
    p.complete();
    constraints.add(p);
  }

  /**
   * Add five region (polygon) constraints in the form of wedges immediately
   * adjacent to the five-pointed star.
   *
   * @param constraints a collection for storing constraints.
   */
  private void addConstraintWedges(List<IConstraint> constraints) {
    // the critical consideration is that the vertices for the shared
    // edges must match.  Edges for adjacent polygons can be shared,
    // but polygons must never intersect or overlap.
    for (int iWedge = 0; iWedge < 5; iWedge++) {
      PolygonConstraint wedge = new PolygonConstraint();
      wedge.setApplicationData(new Color(64, 128, 255));  // pale blue
      double a = A36 + A18 + iWedge * A72;
      double x = rNotch * Math.cos(a);
      double y = rNotch * Math.sin(a);
      wedge.add(new Vertex(x, y, 0, iVertex++));
      double a0 = A18 + iWedge * A72;
      for (int j = 0; j <= 12; j++) {
        a = a0 + j * A6;
        x = rPoint * Math.cos(a);
        y = rPoint * Math.sin(a);
        wedge.add(new Vertex(x, y, 0, iVertex++));
      }
      wedge.complete();
      constraints.add(wedge);
    }
  }

  /**
   * Add a region in the form of a ring based on two polygons. The inner polygon
   * is oriented clockwise to indicate a "hole" in the overall region it
   * defines. The outer polygon is given in counter-clockwise order to indicate
   * that it encloses a region.
   *
   * @param constraints a collection for storing constraints.
   */
  private void addEnclosingRing(List<IConstraint> constraints) {
    // The key idea here is that a region constraint is defined by a
    // polygon oriented in such a way that the region lies to the left side
    // of each of its edges.  So, a polygon given in counter-clockwise order
    // encloses a region.  But for a polygon oriented in clockwise order, the
    // region to the left side of the edges is actually OUTSIDE the polygon.
    // Thus, a clockwise polygon defines a "hole" within a region.

    // Step 1.  Construct the outer circle of the ring, a polygon 
    //          of a larger radius oriented counter-clockwise.  This circle
    //          encloses a constrained region.
    PolygonConstraint c1 = new PolygonConstraint();
    for (int i = 0; i < 360; i += 6) {
      double a = Math.toRadians(i);
      double x = 3.5 * Math.cos(a);
      double y = 3.5 * Math.sin(a);
      c1.add(new Vertex(x, y, 0, iVertex++));
    }
    c1.complete();
    constraints.add(c1);

    // Step 2. Construct the inner circle of the ring, a polygon
    //         of a smaller radius oriented clockwise.  The circle defines
    //         a "hole" in the constrained region
    PolygonConstraint c2 = new PolygonConstraint();
    for (int i = 0; i < 360; i += 6) {
      double a = -Math.toRadians(i);
      double x = 3 * Math.cos(a);
      double y = 3 * Math.sin(a);
      c2.add(new Vertex(x, y, 0, iVertex++));
    }
    c2.complete();
    constraints.add(c2);

    // set colors for ring as application data
    c1.setApplicationData(new Color(128, 255, 128));
    c2.setApplicationData(new Color(128, 255, 128));
  }

  private BufferedImage renderImage(int w, int h) {
    if (w == 0 || h == 0) {
      // the component is not yet fully realized by Java Swing.
      return null;
    }
    Rectangle2D bounds = tin.getBounds();
    double x0 = bounds.getMinX();
    double y0 = bounds.getMinY();
    double x1 = bounds.getMaxX();
    double y1 = bounds.getMaxY();
    RenderingSurfaceAid rsa = new RenderingSurfaceAid(w, h, 10, x0, y0, x1, y1);
    rsa.fillBackground(Color.white);
    AffineTransform af = rsa.getCartesianToPixelTransform();

    BufferedImage bImage = rsa.getBufferedImage();
    Graphics2D g2d = rsa.getGraphics2D();
    g2d.setStroke(new BasicStroke(1.0f));

    TriangleRenderer triangleRenderer = new TriangleRenderer(g2d, af);
    TriangleCollector.visitSimpleTriangles(tin, triangleRenderer);

    // Loop through the edges and draw them.  The edges that border
    // constrained regions are to be drawn in black.  While this
    // drawing operation could have been accomplished in a single loop,
    // we draw the border edges in a second loop so that they will
    // be drawn over the light gray edge general edges and have a "cleaner"
    // appearance.
    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(new Color(160, 160, 160));  // lighter gray
    Line2D l2d = new Line2D.Double();
    for (IQuadEdge e : tin.edges()) {
      if (!e.isConstrainedRegionBorder()) {
        e.setLine2D(af, l2d);
        g2d.draw(l2d);
      }
    }
    
    g2d.setColor(Color.black);
    for (IQuadEdge e : tin.edges()) {
      if (e.isConstrainedRegionBorder()) {
        e.setLine2D(af, l2d);
        g2d.draw(l2d);
      }
    }
    
    

    return bImage;
  }

}
