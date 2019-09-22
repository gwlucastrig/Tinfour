/*
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 11/2016 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.examples;

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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
import org.tinfour.common.Vertex;
import org.tinfour.utils.TriangleCollector;

@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
class LogoPanel extends JPanel {

  final static long serialVersionUID = 1;
  private static final double oversize = 1.5;

  IIncrementalTin tin;

  public LogoPanel(IIncrementalTin tin) {
    super(new BorderLayout());
    this.tin = tin;
  }

  private void mapEdge(IQuadEdge e, AffineTransform af, Point2D p0, Point2D p1, Line2D l2d) {
    p0.setLocation(e.getA().x, e.getA().y);
    p1.setLocation(e.getB().x, e.getB().y);
    af.transform(p0, p0);
    af.transform(p1, p1);
    l2d.setLine(p0, p1);
  }

  private Path2D mapPolygon(IConstraint constraint, AffineTransform af) {
    Path2D path = new Path2D.Double();
    Point2D p = new Point2D.Double();
    List<Vertex> vList = constraint.getVertices();
    boolean moveFlag = true;
    for (Vertex v : vList) {
      p.setLocation(v.getX(), v.getY());
      af.transform(p, p);
      if (moveFlag) {
        moveFlag = false;
        path.moveTo(p.getX(), p.getY());
      } else {
        path.lineTo(p.getX(), p.getY());
      }
    }
    path.closePath();
    return path;
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

    // set up an AffineTransform to map the TIN to the rendering area:
    //    a.  The TIN should be scaled uniformly so that its
    //        overall shape is preserved
    //    b.  The TIN should be drawn as large as room will allow
    //    c.  The TIN should be centered in the display
    int w = getWidth();
    int h = getHeight();
    g.setColor(Color.white);
    g.fillRect(0, 0, w, h);

    // This particular application is going to draw the TIN twice,
    // one image above the other.  To do that, we adjust the h value to
    // half its height, fit the TIN to that space, and then adjust it back.
    h = h / 2;  // half high
    Rectangle2D bounds = tin.getBounds();
    // compare aspect ratios of the data bounds and panel to determine
    // how to scale the data
    double scale;
    double aB = bounds.getWidth() / bounds.getHeight();
    double aP = (double) w / (double) h;
    if (aP / aB > 1) {
      // the panel is fatter than the bounds
      // the height of the panel is the limiting factor
      scale = h / (bounds.getHeight() * oversize);
    } else {
      // the panel is skinnier than the bounds
      // the width of the bounds is the limiting factor
      scale = w / (bounds.getWidth() * oversize);
    }

    // find the offset by scaling the center of the bounds and
    // computing how far off from the pixel center it ends up
    int xOffset = (int) ((w / 2.0 - scale * bounds.getCenterX()));
    int yOffset = (int) ((h / 2.0 + scale * bounds.getCenterY()));
    AffineTransform af
            = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

    final Point2D p0 = new Point2D.Double();
    final Point2D p1 = new Point2D.Double();
    final Point2D p2 = new Point2D.Double();
    final Line2D l2d = new Line2D.Double();

    g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
    List<IQuadEdge> edges = tin.getEdges();
    List<IConstraint> constraints = tin.getConstraints();

    // Step 1, draw all edges in the TIN in a uniform color
    g2d.setColor(Color.lightGray);
    for (IQuadEdge e : edges) {
      if (e.getB() == null) {
        continue; // ghost edge
      }
      mapEdge(e, af, p0, p1, l2d);
      g2d.draw(l2d);
    }

    // Step 2:  Advance y offset and draw only constrained region member
    //          edges.  The border edges are drawn in black, the interior edges
    //          are drawn in light gray.  For aesthetic reasons,  we want
    //          the border edges to be drawn on top of the interior edges,
    //          so we conduct this render operation in two passes.
    yOffset += 1.15 * scale * bounds.getHeight();
    af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

    g2d.setColor(Color.lightGray);
    for (IQuadEdge e : edges) {
      if (e.isConstrainedRegionInterior()) {
        mapEdge(e, af, p0, p1, l2d);
        g2d.draw(l2d);
      }
    }
    g2d.setColor(Color.black);
    for (IQuadEdge e : edges) {
      if (e.isConstrainedRegionBorder()) {
        mapEdge(e, af, p0, p1, l2d);
        g2d.draw(l2d);
      }
    }

    // Step 3 -- Loop on constraints.  Use the TriangleCollector
    //           to get the triangles for the constraint.  
    //           If a character includes a hole, the hole polygon will
    //           be oriented in a clockwise order.  All the triangles
    //           associated with the hole will be to its exterior.
    //           These could be plotted if we chose to do so, but they 
    //           would be the same triangles as the ones created by the
    //           containing polygon.  So we check the polygon getArea() method.
    //           If it returns a negative value, we skip the polygon.
    yOffset += 1.15 * scale * bounds.getHeight();
    af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

    final AffineTransform at = af; // Java needs this to be final
    final Path2D path = new Path2D.Double();
    for (IConstraint constraint : constraints) {
      // This demonstration is written so that all constraints
      // will be polygons and all will have an associated
      // Color object.  So we don't bother checking for these conditions.
      PolygonConstraint poly = (PolygonConstraint) constraint;
      double area = poly.getArea();
      if (area <= 0) {
        continue;
      }
      Object obj = constraint.getApplicationData();
      g2d.setColor((Color) obj);
      TriangleCollector.visitTrianglesForConstrainedRegion(
              constraint, 
              new Consumer<Vertex[]>() 
      {
        @Override
        public void accept(final Vertex[] triangle) {
          p0.setLocation(triangle[0].x, triangle[0].y);
          p1.setLocation(triangle[1].x, triangle[1].y);
          p2.setLocation(triangle[2].x, triangle[2].y);
          at.transform(p0, p0);
          at.transform(p1, p1);
          at.transform(p2, p2);

          path.reset();
          path.moveTo(p0.getX(), p0.getY());
          path.lineTo(p1.getX(), p1.getY());
          path.lineTo(p2.getX(), p2.getY());
          path.closePath();

          // a quirk of most graphics packages is that we 
          // have to call g2d.draw(path) to ensure that all triangles covered
          // all pixels.   
          g2d.fill(path);
          g2d.draw(path);

        }
      });
    }

    // Again, for aesthetic reasons, draw the interior edges in a semi-transparent
    // gray and then draw the borders in black.  This time, we handle
    // the borders in a different manner from above.  We get the outline
    // from the constraint itself and draw it in a continuous path.  The
    // result takes advantage of Java's rendering logic and produces a more
    // pleasing line.
    g2d.setColor(new Color(128, 128, 128, 128));
    for (IQuadEdge e : edges) {
      if (e.isConstrainedRegionInterior()) {
        mapEdge(e, af, p0, p1, l2d);
        g2d.draw(l2d);
      }
    }

    g2d.setColor(Color.black);
    g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
    for (IConstraint constraint : constraints) {
      Path2D polyPath = mapPolygon(constraint, af);
      g2d.draw(polyPath);
    }

  }

  /**
   * A convenience routine to open up a window (Java Frame) showing the plot
   *
   * @param tin a valid TIN
   * @param header a label for the window
   * @return a valid instance
   */
  public static LogoPanel plot(final IIncrementalTin tin, final String header) {

    try {
      // Set System L&F
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException |
            InstantiationException |
            IllegalAccessException |
            UnsupportedLookAndFeelException ex) {
      ex.printStackTrace(System.err);
    }

    final LogoPanel testPanel;
    testPanel = new LogoPanel(tin);
    testPanel.setPreferredSize(new Dimension(500, 500));
    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        //Turn off metal's use of bold fonts
        UIManager.put("swing.getB()oldMetal", Boolean.FALSE);

        //Create and set up the window.
        JFrame frame = new JFrame(header);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add content to the window.
        frame.add(testPanel);

        //Display the window.
        frame.pack();
        frame.setVisible(true);

      }

    });

    return testPanel;
  }

}
