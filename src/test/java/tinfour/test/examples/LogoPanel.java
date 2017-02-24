/*
 * Copyright 2016 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.getA()pache.org/licenses/LICENSE-2.0
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
package tinfour.test.examples;

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
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;

public class LogoPanel extends JPanel {

    final static long serialVersionUID = 1;
    private static final double oversize = 1.5;

    IIncrementalTin tin;


    public LogoPanel(IIncrementalTin tin) {
        super(new BorderLayout());
        this.tin = tin;
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

        Point2D p0 = new Point2D.Double();
        Point2D p1 = new Point2D.Double();
        Line2D l2d = new Line2D.Double();

        g2d.setStroke(new BasicStroke(1.0f));
        List<IQuadEdge> edges = tin.getEdges();

        // Draw the TIN in two passes.
        //   Pass 0:   All edges, light gray
        //   Pass 1:   Only those edges wihin the constraint
        //               interior edges light gray
        //               constraint edges black
        g2d.setColor(Color.lightGray); // for pass 0
        for (int iPass = 0; iPass < 2; iPass++) {
            if (iPass == 1) {
                // on pass 1, adjust the AffineTransform so that
                // the second image appears below the first
                // allow a vertical gap 15 percent of the rendered height of the TIN.
                yOffset += 1.15 * scale * bounds.getHeight();
                af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset); // NOPMD
            }
            for (IQuadEdge e : edges) {
                if (e.getB() == null) {
                    continue; // ghost edge
                }
                if (iPass == 1) {
                    if (!e.isConstrainedAreaMember()) {
                        continue;
                    }
                    if (e.isConstrainedAreaEdge()) {
                        g2d.setColor(Color.black);
                    } else {
                        g2d.setColor(Color.lightGray);
                    }
                }
                p0.setLocation(e.getA().x, e.getA().y);
                p1.setLocation(e.getB().x, e.getB().y);
                af.transform(p0, p0);
                af.transform(p1, p1);
                l2d.setLine(p0, p1);
                g2d.draw(l2d);
            }
        }

        // The loop above already drew the constraint edges in black.
        // But purely for aesthetic reasons, we draw them again
        // using the Java Path2D.

        Path2D path2d = new Path2D.Double();
        List<IConstraint> constraints = tin.getConstraints();
        for (IConstraint c : constraints) {
            List<Vertex> vList = c.getVertices();
            boolean moveFlag = true;
            for (Vertex v : vList) {
                p0.setLocation(v.getX(), v.getY());
                af.transform(p0, p0);
                if (moveFlag) {
                    moveFlag = false;
                    path2d.moveTo(p0.getX(), p0.getY());
                } else {
                    path2d.lineTo(p0.getX(), p0.getY());
                }
            }
            path2d.closePath();
        }
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.setColor(Color.black);
        g2d.draw(path2d);
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
