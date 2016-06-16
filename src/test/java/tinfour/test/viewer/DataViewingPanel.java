/* --------------------------------------------------------------------
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
 * ---------------------------------------------------------------------
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * Anchor Points and the buildTransform Method
 *   One thing we want to avoid in user/mouse interactions is the tendancy
 *   of the image to "drift" away from the mouse pointer as the user
 *   pans and zooms. We also want to avoid unpredictable jumps in the
 *   relative image position when the user resizes.  Resizes
 *     To avoid these issues, we establish "anchor" points on the backing
 *   composite when the user first clicks the mouse button or mouse wheel.
 *   The pan, zoom, or resize is then a matter of creating c2p transformation
 *   that maps the anchor point from the backing image to that reference point
 *   on the panel (typically, this is the mouse position or, in the case
 *   of a resize the center of the map panel).
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import tinfour.test.viewer.backplane.BackplaneManager;
import tinfour.test.viewer.backplane.IModel;
import tinfour.test.viewer.backplane.ModelFromLas;
import tinfour.test.viewer.backplane.MvComposite;
import tinfour.test.viewer.backplane.MvQueryResult;
import tinfour.test.viewer.backplane.RenderProduct;
import tinfour.test.viewer.backplane.ViewOptions;

/**
 * A test panel to demonstrate mouse events and coordinate transformation
 * when viewing images.
 */
public class DataViewingPanel extends JPanel {

  /**
   * The label for viewing mouse motion data
   */
  private JLabel readoutLabel;

  private StatusPanel statusPanel;
  private JEditorPane reportPane;
  private JEditorPane queryPane;

  private int priorWidth;
  private int priorHeight;
  private double resizeAnchorX = Double.NaN;
  private double resizeAnchorY = Double.NaN;

  /**
   * The companion instance used for graphics-related operations.
   */
  BackplaneManager backplaneManager;

  boolean redrawInProgress;
  boolean firstQuery = true;
  boolean firstDraw = true;

  /**
   * The point at which the mouse was most recently pressed; used
   * for click-and-drag operations.
   */
  Point2D imagePressPoint;

  private ViewOptions viewOptions;
  private MvComposite mvComposite;
  private BufferedImage compositeImage;
  private BufferedImage legendImage;
  private AffineTransform c2p; // composite to panel
  private AffineTransform p2c; // panel to composite
  private AffineTransform p2m;  // panel to model
  private RenderProduct[] renderProducts = new RenderProduct[2];
  private MvQueryResult mvQueryResult;
  private boolean showScale;
  private boolean showLegend;

  Timer redrawTimer;

  DataViewingPanel() {
    super();
    viewOptions = new ViewOptions();
  }

  void clear() {
    backplaneManager.clear();
    mvComposite = null;
    compositeImage = null;
    legendImage = null;
    renderProducts[0] = null;
    renderProducts[1] = null;
    reportPane.setText(null);
    queryPane.setText(null);
    repaint();
  }

  void setViewOptions(ViewOptions view) {
    this.viewOptions = view;
    setBackground(viewOptions.getBackground());
    backplaneManager.setViewOptions(view);

    if (!view.isWireframeSelected()) {
      renderProducts[0] = null;
    }

    if (!view.isGridBasedRenderingSelected()) {
      renderProducts[1] = null;
    }

    assembleComposite();  // removes any layers that were turned off.
    repaint();

    if (mvComposite != null) {
      checkForRedrawWithView(view);
    }
  }

  void setQueryPane(JEditorPane queryPane) {
    this.queryPane = queryPane;
  }

  void setReadoutLabel(JLabel readoutLabel) {
    this.readoutLabel = readoutLabel;
  }

  void setReportPane(JEditorPane reportPane) {
    this.reportPane = reportPane;
  }

  void setStatusPanel(StatusPanel statusPanel) {
    this.statusPanel = statusPanel;
  }

  private void performAppInitialization() {
    int w = getWidth();
    int h = getHeight();
    if (w == 0 || h == 0) {
      // apparently things are not ready yet.
      return;
    }

    backplaneManager = new BackplaneManager(this, statusPanel);
    DataDropTargetListener dropTargetListener
      = new DataDropTargetListener(this);

    DropTarget dropTarget = new DropTarget(
      this,
      DnDConstants.ACTION_COPY,
      dropTargetListener,
      true);

    dropTarget.setDefaultActions(DnDConstants.ACTION_COPY);

    installStandardMouseListeners();
  }

  private void installStandardMouseListeners() {

    this.addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          if (mvComposite == null || !mvComposite.isReady()) {
            readoutLabel.setText("");
          } else {
            double[] c = new double[4];
            c[0] = e.getX();
            c[1] = e.getY();
            p2c.transform(c, 0, c, 2, 1);
            mvQueryResult = mvComposite.performQuery(c[2], c[3]);
            queryPane.setText(mvQueryResult.getText());
            queryPane.setCaretPosition(0);
            repaint();
          }
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (mvComposite == null || !mvComposite.isReady()) {
          return;
        }
        imagePressPoint = new Point2D.Double(e.getX(), e.getY());
        p2c.transform(imagePressPoint, imagePressPoint);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        imagePressPoint = null;
        checkForRedraw();
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        resizeAnchorX = Double.NaN;
        resizeAnchorY = Double.NaN;

      }

      @Override
      public void mouseExited(MouseEvent e) {
        resizeAnchorX = Double.NaN;
        resizeAnchorY = Double.NaN;
      }

    });

    this.addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        if (imagePressPoint == null) {
          return; // no image is registered, do nothing.
        }
        double s = Math.sqrt(Math.abs(c2p.getDeterminant()));
        c2p = buildTransform(e.getX(), e.getY(), imagePressPoint.getX(), imagePressPoint.getY(), s);
        repaint();
        try {
          p2c = c2p.createInverse();
        } catch (NoninvertibleTransformException nex) {
          return;
        }

      }

      @Override
      public void mouseMoved(MouseEvent e) {
        if (mvComposite != null && mvComposite.isReady()) {
          double[] c = new double[4];
          c[0] = e.getX();
          c[1] = e.getY();
          p2c.transform(c, 0, c, 2, 1);
          String s = mvComposite.getModelDataStringAtCoordinates(c[2], c[3]);
          readoutLabel.setText(s);
        }

      }

    });

    this.addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (mvComposite == null || !mvComposite.isReady()) {
          return;
        }
        int clicks = e.getWheelRotation();
        int x = e.getX();
        int y = e.getY();
        Point2D anchor = new Point2D.Double(x, y);
        p2c.transform(anchor, anchor);
        double zf = Math.pow(2.0, -clicks / 16.0);
        double s = Math.sqrt(Math.abs(c2p.getDeterminant()));

        c2p = buildTransform(x, y, anchor.getX(), anchor.getY(), s * zf);
        try {
          p2c = c2p.createInverse();
        } catch (NoninvertibleTransformException nex) {
          return;
        }
        repaint();
        checkForRedraw();
      }

    });

    addComponentListener(new ComponentListener() {

      @Override
      public void componentResized(ComponentEvent e) {

        if (mvComposite == null || !mvComposite.isReady()) {
          return;
        }

        if (Double.isNaN(resizeAnchorX)) {
          double[] c = new double[4];
          c[0] = priorWidth / 2.0;
          c[1] = priorHeight / 2.0;
          p2c.transform(c, 0, c, 2, 1);
          resizeAnchorX = c[2];
          resizeAnchorY = c[3];
        }

        double xPixel = getWidth() / 2.0;
        double yPixel = getHeight() / 2.0;
        double s = Math.sqrt(Math.abs(c2p.getDeterminant()));
        c2p = buildTransform(xPixel, yPixel, resizeAnchorX, resizeAnchorY, s);
        try {
          p2c = c2p.createInverse();
        } catch (NoninvertibleTransformException nex) {
          return;
        }
        // the resize will send a repaint to the panel anyway, so it
        // is not helpful to issue one here.
        checkForRedraw();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        // no special action
      }

      @Override
      public void componentShown(ComponentEvent e) {
        // no special action
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        // no special action
      }

    });
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON);

    priorWidth = getWidth();
    priorHeight = getHeight();
    if (backplaneManager == null) {
      // the JPanel may not be fully realized when the constructor
      // is called.  However, it should be ready by the first
      // time the paintComponent method is called.  So elements
      // that depend on size of the panel are initialized here.
      performAppInitialization();
      if (backplaneManager == null) {
        // this is not expected.  we could reach here if the
        // panel is still not fully realized.  Hopefully, there
        // will be another chance later on...
      }
    }

    if (compositeImage != null) {
      g2d.drawImage(compositeImage, c2p, this);
      if (mvComposite != null && mvComposite.isReady()) {
        if (showScale) {
          // draw a scale bar, but suppress it if the user has engaged
          // a zoom action
          double p2cScale = Math.abs(p2c.getDeterminant());
          if (Math.abs(p2cScale - 1) < 1.0e-6) {
            double s = Math.sqrt(Math.abs(p2m.getDeterminant()));
            int x0 = getWidth() - 240;
            int y0 = getHeight() - 25;
            ScaleIntervals si = ScaleIntervals.computeIntervals(200, 10, s);
            Font f = getFont();
            String family = f.getFamily();
            f = new Font(family, Font.BOLD, 14);
            si.render(g, x0, y0, f, Color.white, Color.black);
          }
        }
        if (showLegend && legendImage != null) {
          int h = legendImage.getHeight();
          int y0 = getHeight() - h - 5;
          if (y0 < 5) {
            y0 = 5;
          }
          g.drawImage(legendImage, 5, y0, this);
        }
      }

      if (mvQueryResult != null) {
        Point2D p = new Point2D.Double();
        c2p.transform(mvQueryResult.getCompositePoint(), p);

        int px = (int) p.getX();
        int py = (int) p.getY();
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.setColor(Color.black);
        g2d.drawLine(px - 10, py, px + 10, py);
        g2d.drawLine(px, py - 10, px, py + 10);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.setColor(Color.white);
        g2d.drawLine(px - 10, py, px + 10, py);
        g2d.drawLine(px, py - 10, px, py + 10);

      }
    }
  }

  /**
   * Load the specified image and display in panel.
   *
   * @param file A valid file
   */
  void loadModel(File file) {
    backplaneManager.loadModel(file);
    renderProducts[0] = null;
    renderProducts[1] = null;
    mvComposite = null;
    mvQueryResult = null;
    compositeImage = null;
    legendImage = null;
    resizeAnchorX = Double.NaN;
    resizeAnchorY = Double.NaN;
    reportPane.setText(null);
    queryPane.setText(null);
    readoutLabel.setText("");
    int pad = viewOptions.getPadding();
    c2p = AffineTransform.getTranslateInstance(-pad, -pad);
    p2c = AffineTransform.getTranslateInstance(pad, pad);
    String name = file.getName();
    Component component = this.getParent();
    while (component != null) {
      if (component instanceof JFrame) {
        ((JFrame) component).setTitle("Tinfour Viewer: " + name);
        break;
      }
      component = component.getParent();
    }
    repaint();

  }

  void loadModel(IModel model) {
    backplaneManager.loadModel(model);
    mvComposite = null;
    mvQueryResult = null;
    renderProducts[0] = null;
    renderProducts[1] = null;
    compositeImage = null;
    legendImage = null;
    resizeAnchorX = Double.NaN;
    resizeAnchorY = Double.NaN;
    reportPane.setText(null);
    queryPane.setText(null);
    readoutLabel.setText("");
    int pad = viewOptions.getPadding();
    c2p = AffineTransform.getTranslateInstance(-pad, -pad);
    p2c = AffineTransform.getTranslateInstance(pad, pad);
    String name = model.getName();
    Component component = this.getParent();
    while (component != null) {
      if (component instanceof JFrame) {
        ((JFrame) component).setTitle("Tinfour Viewer: " + name);
        break;
      }
      component = component.getParent();
    }
    repaint();
  }

  /**
   * Center the image in the display panel, zooming to the
   * largest scale that permits the entire image to be seen on
   * the display.
   */
  void zoomToSource() {
    if (mvComposite != null && mvComposite.isReady()) {
      IModel model = mvComposite.getModel();
      loadModel(model);
    }
  }

  private void setMvComposite(MvComposite mvComposite) {
    int pad = viewOptions.getPadding();
    c2p = AffineTransform.getTranslateInstance(-pad, -pad);
    p2c = AffineTransform.getTranslateInstance(pad, pad);
    this.mvComposite = mvComposite;
    assembleLegend(); // if any
    mvQueryResult = null;
    AffineTransform c2m = mvComposite.getComposite2ModelTransform();
    p2m = new AffineTransform(c2m);
    p2m.concatenate(p2c);  // becomes c2m * p2c
    resizeAnchorX = Double.NaN;
    resizeAnchorY = Double.NaN;
    this.repaint();
  }

  public void postImageUpdate(RenderProduct product) {
    if (mvComposite == null) {
      setMvComposite(product.composite);
    } else if (!product.composite.equals(mvComposite)) {
      return;
    }
    String reportText = mvComposite.getModelAndRenderingReport();
    reportPane.setText(reportText);
    reportPane.setCaretPosition(0);

    int index = product.layerType.ordinal();
    renderProducts[index] = product;

    assembleComposite();
    repaint();
  }

  void assembleComposite() {
    if (mvComposite == null) {
      compositeImage = null;
      return;
    }
    compositeImage = new BufferedImage(
      mvComposite.getWidth(),
      mvComposite.getHeight(),
      BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = compositeImage.createGraphics();
    for (int i = renderProducts.length - 1; i >= 0; i--) {
      if (renderProducts[i] != null) {
        if (renderProducts[i].compatibilityTransform == null) {
          g.drawImage(renderProducts[i].image, 0, 0, null);
        } else {
          g.drawImage(
            renderProducts[i].image,
            renderProducts[i].compatibilityTransform,
            null);
        }
      }
    }
  }

  /**
   * Build an affine transform that will map the pixel coordinates (px, py) to
   * image coordinates (mx, my) applying the scale factor s where s scales
   * image points to pixels. For example s*(mx1-mx0) = pixel_width.
   *
   * @param pX Pixel coordinate for transformation
   * @param pY Pixel coordinate for transformation
   * @param mX Image coordinate for transformation
   * @param mY Image coordinate for transformation
   * @param s Scaling factor multiple image by s to get pixel.
   */
  private AffineTransform buildTransform(double pX, double pY, double mX, double mY, double s) {
    double xOffset = pX - s * mX;
    double yOffset = pY - s * mY;
    return new AffineTransform(s, 0, 0, s, xOffset, yOffset);
  }

  private boolean isRedrawRequired() {
    if (mvComposite == null || compositeImage == null) {
      return false;
    }
    double s = Math.sqrt(Math.abs(c2p.getDeterminant()));
    if (Math.abs(1.0 - s) > 1.0e-3) {
      return true;
    }
    Rectangle2D rectP = new Rectangle2D.Double(0, 0, this.getWidth(), this.getHeight());
    double[] c = new double[8];
    c[0] = 0;
    c[1] = 0;
    c[2] = compositeImage.getWidth();
    c[3] = compositeImage.getHeight();

    c2p.transform(c, 0, c, 4, 2);
    Rectangle2D rectC = new Rectangle2D.Double(c[4], c[5], c[6] - c[4], c[7] - c[5]);

    Area areaP = new Area(rectP);
    Area areaC = new Area(rectC);

    p2m = mvComposite.getComposite2ModelTransform();
    p2m.concatenate(p2c);
    AffineTransform m2p;
    try {
      m2p = p2m.createInverse();
    } catch (NoninvertibleTransformException ex) {
      return true; // not expected
    }

    IModel model = mvComposite.getModel();
    double mx0 = model.getMinX();
    double mx1 = model.getMaxX();
    double my0 = model.getMinY();
    double my1 = model.getMaxY();

    c[0] = mx0;
    c[1] = my1;
    c[2] = mx1;
    c[3] = my0;
    m2p.transform(c, 0, c, 4, 2);
    Rectangle2D rectM = new Rectangle2D.Double(c[4], c[5], c[6] - c[4], c[7] - c[5]);

    // rule 1: if the panel does not intersect the model, there is no point in
    // rendering because nothing would be visible anyway
    if (!areaP.intersects(rectM)) {
      return false;
    }

    //rule 2: if the composite completely encloses the panel, then
    // there is no need to render because it would not expose any part
    // of the model that was already shown in the composite.
    areaP.subtract(areaC);
    if (areaP.isEmpty()) {
      return false;
    }

    // rule 3: if the part of the panel that does not overlap the
    //   composite does not intersect the model, then there is no
    //   point in rendering because nothing new would show up in
    //   the panel.
    return areaP.intersects(rectM);

  }

  private void checkForRedraw() {
    if (redrawTimer != null) {
      redrawTimer.stop();
      redrawTimer = null;
    }
    if (redrawInProgress) {
      return;
    }

    redrawTimer = new Timer(500, new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        redrawTimer = null;
        boolean status = isRedrawRequired();
        if (!status) {
          return;
        }
        //redrawInProgress = true;
        int pad = viewOptions.getPadding();
        AffineTransform a = AffineTransform.getTranslateInstance(pad, pad);
        AffineTransform m2c;
        m2c = mvComposite.getModel2CompositeTransform();
        a.concatenate(c2p);
        a.concatenate(m2c);
        AffineTransform aInv;

        try {
          aInv = a.createInverse();
        } catch (NoninvertibleTransformException ex) {
          return;
        }
        for (int i = 0; i < renderProducts.length; i++) {
          if (renderProducts[i] != null) {
            AffineTransform compatibility = AffineTransform.getTranslateInstance(pad, pad);
            compatibility.concatenate(c2p);
            if (renderProducts[i].compatibilityTransform == null) {
              renderProducts[i].compatibilityTransform = compatibility;
            } else {
              renderProducts[i].compatibilityTransform.preConcatenate(compatibility);
            }
          }
        }

        mvComposite = backplaneManager.queueHeavyweightRenderTask(
          mvComposite.getModel(),
          mvComposite.getView(),
          getWidth() + 2 * pad,
          getHeight() + 2 * pad,
          a, aInv);
        assembleLegend();
        setMvComposite(mvComposite);
        for (int i = 0; i < renderProducts.length; i++) {
          if (renderProducts[i] != null) {
            renderProducts[i].composite = mvComposite;
          }
        }
        assembleComposite();
      }

    });

    redrawTimer.setRepeats(false);
    redrawTimer.start();

  }

  private void checkForRedrawWithView(ViewOptions view) {
    if (mvComposite == null || !mvComposite.isReady()) {
      return;
    }
    if (redrawTimer != null) {
      redrawTimer.stop();
      redrawTimer = null;
    }

    int pad = viewOptions.getPadding();
    AffineTransform a = AffineTransform.getTranslateInstance(pad, pad);
    AffineTransform m2c;
    m2c = mvComposite.getModel2CompositeTransform();
    a.concatenate(c2p);
    a.concatenate(m2c);
    AffineTransform aInv;

    try {
      aInv = a.createInverse();
    } catch (NoninvertibleTransformException ex) {
      return;
    }

    IModel model = mvComposite.getModel();
    ViewOptions oldView = mvComposite.getView();
    this.viewOptions = view;
    boolean redrawRequired = false;

    // in most cases, the process can complete with any existing
    // resources such as a TIN that have been developed for the
    // mvComposite.  The exception is in the case where view options
    // change the selection of sample points that make up the TIN.
    // At this time, the only option that does so is the lidar
    // ground-point filter option
    if (model instanceof ModelFromLas) {
      ModelFromLas lasModel = (ModelFromLas) model;
      boolean modelWasFiltered = lasModel.hasGroundPointFilter();
      boolean newViewIsFiltered = view.isLidarGroundPointsOptionSelected();
      if (modelWasFiltered != newViewIsFiltered) {
        model = new ModelFromLas(
          lasModel.getFile(),
          newViewIsFiltered);
        redrawRequired = true;
      }
    }

    if (view.isWireframeSelected()
      && oldView.getWireframeSampleThinning() != view.getWireframeSampleThinning()) {
      redrawRequired = true;
    }

    if (view.isWireframeSelected() && !oldView.isWireframeSelected()) {
      redrawRequired = true;
    }
    if ((view.isRasterSelected() || view.isHillshadeSelected())
      && view.isFullResolutionGridSelected() != oldView.isFullResolutionGridSelected()) {
      redrawRequired = true;
    }

    if (redrawRequired) {
      // perform a heavy-weight render, building up new TINs as necessary
      MvComposite newComposite = backplaneManager.queueHeavyweightRenderTask(
        model, view,
        getWidth() + 2 * pad,
        getHeight() + 2 * pad,
        a, aInv);
      mvComposite = newComposite;
      assembleLegend();
      mvQueryResult = null;
    } else {
      // perform a light-weight render reusing existing TINs
      MvComposite newComposite = backplaneManager.queueLightweightRenderTask(
        mvComposite, view,
        getWidth() + 2 * pad,
        getHeight() + 2 * pad,
        a, aInv);
      mvComposite = newComposite;
      assembleLegend();
      mvQueryResult = null;
    }
  }

  void setShowScale(boolean showScale) {
    this.showScale = showScale;
    repaint();
  }

  void setShowLegend(boolean showLegend) {
    this.showLegend = showLegend;
    assembleLegend();
    repaint();
  }

  void assembleLegend() {
    if (showLegend && mvComposite != null) {
      Font font = new Font("Arial", Font.BOLD, 10);
      this.legendImage = mvComposite.renderLegend(
        mvComposite.getView(),
        mvComposite.getModel(),
        50, 100, 10, font, true);
    } else {
      legendImage = null;
    }
  }

}
