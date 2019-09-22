/*
 * Copyright 2014 Gary W. Lucas.
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
 * 04/2013 G. Lucas Created
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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

public class TestPanelForCdt extends JPanel {

  final static long serialVersionUID = 1;
  IIncrementalTin tin;
  AffineTransform af = new AffineTransform();
  AffineTransform afInverse = new AffineTransform();
  double afScale = 1;

  private Path2D path;
  private List<IQuadEdge> eList;
  private boolean fillPath;

  private double oversize = 1.5;

  private final Font labelFont = new Font("Arial", Font.BOLD, 12);
  private Point2D pointMark;
  private TextLayout labelLayout;
  private Rectangle labelRectangle;

  private boolean valueLabelEnabled = false;  //NOPMD
  private boolean indexLabelEnabled = true;
  private boolean directionEnabled = false;  //NOPMD
  private String valueLabelFormat = "%f";

  private final List<Vertex> specialVertexList = new ArrayList<>();
  private final List<Vertex[]> specialChainList = new ArrayList<>();

  private Color edgeColor = Color.lightGray;

  private List<Vertex> overpaintPolygon;
  private Color overpaintFill;
  private Color overpaintEdge;

  void setOverpaintPolygon(List<Vertex> poly, Color fill, Color edge) {
    this.overpaintPolygon = poly;
    this.overpaintFill = fill;
    this.overpaintEdge = edge;
  }

  TestPanelForCdt(IIncrementalTin tin) {
    super(new BorderLayout());
    this.tin = tin;
  }

  void setNewTin(IIncrementalTin tin) {
    this.tin = tin;
    this.eList = null;
    this.pointMark = null;
    this.fillPath = false;
    this.path = null;
    this.specialVertexList.clear();
    this.specialChainList.clear();
  }

  void addVertexToSpecialList(Vertex v) {
    synchronized (this) {
      specialVertexList.add(v);
    }
  }

  void addChainToSpecialList(Vertex[] v) {
    synchronized (this) {
      specialChainList.add(v);
    }
  }

  void clearSpecialChainList() {
    synchronized (this) {
      specialChainList.clear();
    }
  }

  void setOversize(double oversize) {
    this.oversize = oversize;
  }

  void setValueLabelEnabled(boolean valueLabelEnabled) {
    this.valueLabelEnabled = valueLabelEnabled;
  }

  void setValueLabelFormat(String format) {
    this.valueLabelFormat = format;
  }

  void setIndexLabelEnabled(boolean indexLabelEnabled) {
    this.indexLabelEnabled = indexLabelEnabled;
  }

  void setDirectionArrowEnabled(boolean enabled) {
    this.directionEnabled = enabled;
  }

  void setEdgeColor(Color edgeColor) {
    this.edgeColor = edgeColor;
  }

  /**
   * Sets a point to be marked with a cross indicating coordinates of
   * interest. The point should be given in TIN coordinates (not pixel
   * coordinates).
   *
   * @param pointMark a valid instance or a null to clear the mark.
   */
  void setPointMark(Point2D pointMark) {
    this.pointMark = pointMark;
    repaint();
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

    g.setFont(labelFont);
    int w = getWidth();
    int h = getHeight();
    g.setColor(Color.white);
    g.fillRect(0, 0, w, h);

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
    af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);
    afScale = scale;
    try {
      afInverse = af.createInverse();
    } catch (NoninvertibleTransformException ex) {

    }

    if (eList != null) {
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.setColor(Color.lightGray);
      for (IQuadEdge e : eList) {
        Path2D path2D = new Path2D.Double();  //NOPMD
        path2D.moveTo(e.getA().getX(), e.getA().getY());
        path2D.lineTo(e.getB().getX(), e.getB().getY());
        Vertex c = e.getForward().getB();
        if (c != null) {
          path2D.lineTo(c.getX(), c.getY());
          path2D.closePath();
          Shape s = path2D.createTransformedShape(af);
          g2d.fill(s);
          g2d.draw(s);
        }
      }

    }
    if (path != null) {
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.setColor(Color.ORANGE);
      Shape s = path.createTransformedShape(af);
      if (fillPath) {
        g2d.fill(s);
      } else {
        g2d.draw(s);
      }
    }

    Point2D p = new Point2D.Double();
    Point2D p0 = new Point2D.Double();
    Point2D p1 = new Point2D.Double();
    Line2D l2d = new Line2D.Double();
    Ellipse2D e2d = new Ellipse2D.Double();

    //g2d.setStroke(new BasicStroke(3.0f));
    //g2d.setColor(Color.RED);
    //QuadEdge[] edges = tin.getPerimeter();
    //for (int i = 0; i < edges.length; i++) {
    //    Vertex v0 = edges[i].getV0();
    //    Vertex v1 = edges[i].getV1();
    //    p0.setLocation(v0.getX(), v0.getY());
    //    p1.setLocation(v1.getX(), v1.getY());
    //    af.transform(p0, p0);
    //    af.transform(p1, p1);
    //    l2d.setLine(p0, p1);
    //    g2d.draw(l2d);
    //}

    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(this.edgeColor);
    List<IQuadEdge> edges = tin.getEdges();

    for (IQuadEdge e : edges) {

      if (e.getB() == null) {
        continue;
      }
      if(e.isConstrained()){
        g2d.setColor(this.edgeColor.darker());
      }else{
        g2d.setColor(this.edgeColor);
      }
      p0.setLocation(e.getA().x, e.getA().y);
      p1.setLocation(e.getB().x, e.getB().y);
      af.transform(p0, p0);
      af.transform(p1, p1);
      l2d.setLine(p0, p1);
      g2d.draw(l2d);

      double mx = (e.getA().x + e.getB().x) / 2.0;
      double my = (e.getA().y + e.getB().y) / 2.0;
      double ux = p1.getX() - p0.getX();
      double uy = p1.getY() - p0.getY();
      double u = Math.sqrt(ux * ux + uy * uy);
      ux /= u;
      uy /= u;
      double px = -uy;
      double py = ux;
      p0.setLocation(mx, my);
      af.transform(p0, p0);
      mx = p0.getX();
      my = p0.getY();
      if (directionEnabled) {
        l2d.setLine(mx + 2 * ux, my + 2 * uy, mx - 5 * ux + 3 * px, my - 5 * uy + 3 * py);
        g2d.draw(l2d);
        l2d.setLine(mx + 2 * ux, my + 2 * uy, mx - 5 * ux - 3 * px, my - 5 * uy - 3 * py);
        g2d.draw(l2d);
      }
      if (this.indexLabelEnabled) {
        String s = Integer.toString(e.getIndex());

        g2d.drawString(s, (float) (mx + 10 * px), (float) (my + 10 * py));
      }

    }

    if (this.overpaintPolygon != null) {
      Path2D path2D = new Path2D.Double();
      boolean move = true;
      for (Vertex vOver : overpaintPolygon) {
        if (move) {
          move = false;
          path2D.moveTo(vOver.getX(), vOver.getY());
        } else {
          path2D.lineTo(vOver.getX(), vOver.getY());
        }
      }
      path2D.closePath();
      Shape s = path2D.createTransformedShape(af);

      g2d.setColor(overpaintFill);
      g2d.fill(s);
      g2d.setStroke(new BasicStroke(2f));
      g2d.setColor(overpaintEdge);
      g2d.draw(s);
    }

    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(Color.BLACK);
    ArrayList<Vertex> junk = new ArrayList<>();
    for (IQuadEdge e : edges) {
      if (e.getA() == null) {
        continue;
      }
      if (!junk.contains(e.getA())) {
        junk.add(e.getA());
      }
      if (e.getB() != null && !junk.contains(e.getB())) {
        junk.add(e.getB());
      }
    }
    Vertex[] v = junk.toArray(new Vertex[junk.size()]);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < v.length; i++) {
      builder.setLength(0);
      p0.setLocation(v[i].x, v[i].y);
      af.transform(p0, p1);
      e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 4.0f, 4.0f);
      g2d.draw(e2d);
      g2d.fill(e2d);

      if (indexLabelEnabled) {
        builder.append(Integer.toString(v[i].getIndex()));
      }
      if (valueLabelEnabled) {
        if (indexLabelEnabled) {
          builder.append("|");
        }
        builder.append(String.format(valueLabelFormat, v[i].getZ()));
      }
      g2d.drawString(builder.toString(), (float) p1.getX() + 3f, (float) p1.getY() - 3f);
    }

    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(Color.RED);
    synchronized (this) {
      for (Vertex vS : specialVertexList) {
        p0.setLocation(vS.x, vS.y);
        af.transform(p0, p1);
        e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 6.0f, 6.0f);
        g2d.draw(e2d);
        g2d.fill(e2d);
      }
    }

    for (Vertex[] vC : specialChainList) {
      if (vC.length > 1) {
        for (int i = 0; i < vC.length - 1; i++) {
          p0.setLocation(vC[i].x, vC[i].y);
          p1.setLocation(vC[i + 1].x, vC[i + 1].y);
          af.transform(p0, p0);
          af.transform(p1, p1);
          l2d.setLine(p0, p1);
          g2d.draw(l2d);
          double mx = (vC[i].x + vC[i + 1].x) / 2.0;
          double my = (vC[i].y + vC[i + 1].y) / 2.0;
          double ux = p1.getX() - p0.getX();
          double uy = p1.getY() - p0.getY();
          double u = Math.sqrt(ux * ux + uy * uy);
          ux /= u;
          uy /= u;
          double px = -uy;
          double py = ux;
          p0.setLocation(mx, my);
          af.transform(p0, p0);
          mx = p0.getX();
          my = p0.getY();
          if (directionEnabled) {
            l2d.setLine(mx + 2 * ux, my + 2 * uy, mx - 5 * ux + 3 * px, my - 5 * uy + 3 * py);
            g2d.draw(l2d);
            l2d.setLine(mx + 2 * ux, my + 2 * uy, mx - 5 * ux - 3 * px, my - 5 * uy - 3 * py);
            g2d.draw(l2d);
          }
        }
      }
    }

    if (labelLayout != null) {
      double x = 5;
      double y = getHeight() - 10;
      g2d.setColor(Color.BLACK);
      labelLayout.draw(g2d, (float) x, (float) y);
    }

    if (pointMark != null) {
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.setColor(Color.RED);
      af.transform(pointMark, p);
      double xC = p.getX();
      double yC = p.getY();
      l2d.setLine(xC - 5, yC, xC + 5, yC);
      g2d.draw(l2d);
      l2d.setLine(xC, yC - 5, xC, yC + 5);
      g2d.draw(l2d);
    }

  }

  Point2D mapPanelToTIN(Point2D point) {
    Point2D result = new Point2D.Double();
    afInverse.transform(point, result);
    return result;
  }

  /**
   * Sets a path for drawing diagnostic markups, with an option to
   * fill.
   *
   * @param path the path to be drawn.
   * @param fill indicates whether the path is to be filled.
   */
  void setDiagnosticPath(Path2D path, boolean fill) {
    this.fillPath = fill;
    this.path = path;
    this.repaint();
  }

  /**
   * Gets the affine transform that maps the TIN coordinate system to
   * the Java Graphics User (Pixel) Coordinate System.
   *
   * @return a valid affine transform
   */
  AffineTransform getAffineTransform() {
    return new AffineTransform(af);
  }

  /**
   * Gets the affine transform that maps the the Java Graphics User (Pixel)
   * Coordinate System to the TIN coordinate system.
   *
   * @return a valid affine transform
   */
  AffineTransform getAffineTransformInverse() {
    return new AffineTransform(afInverse);
  }

  /**
   * A convenience routine to open up a Window showing the content of 
   * a Delaunay triangulation.  In order to avoid an excessive amount of
   * data processing, developers are cautioned to not use this
   * class in cases where the number of points in a triangulation is very
   * large.  
   *
   * @param tin a valid TIN
   * @param header a label for the window
   * @return a valid instance
   */
  static public TestPanelForCdt plot(
          final IIncrementalTin tin, 
          final String header) 
  {

    try {
      // Set System L&F
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException |
      InstantiationException |
      IllegalAccessException |
      UnsupportedLookAndFeelException ex) {
      ex.printStackTrace(System.err);
    }

    final TestPanelForCdt testPanel;
    testPanel = new TestPanelForCdt(tin);
    testPanel.setOversize(1.25);
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

  /**
   * Set the text for a label to be drawn in the lower-left corner
   * of the graphics area.
   *
   * @param label a valid string or a null to clear the label
   */
  void setLabel(String label) {
    double x = 5;
    double y = getHeight() - 10;
    if (labelLayout != null) {
      Rectangle2D r2d = labelLayout.getBounds();
      labelRectangle = new Rectangle(
        (int) (x + r2d.getX() - 2),
        (int) (y + r2d.getY() - 2),
        (int) (r2d.getWidth() + 4),
        (int) (r2d.getHeight() + 2));
      repaint(labelRectangle);
      labelLayout = null;
    }

    if (label != null && label.length() > 0) {
      FontRenderContext frc = new FontRenderContext(null, true, true);
      labelLayout = new TextLayout(label, labelFont, frc);
    }

  }

}
