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
package org.tinfour.demo.viewer;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.tinfour.common.Vertex;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.demo.viewer.backplane.BackplaneManager;
import org.tinfour.demo.viewer.backplane.CompositeImageScale;
import org.tinfour.demo.viewer.backplane.IModel;
import org.tinfour.demo.viewer.backplane.IModelChangeListener;
import org.tinfour.demo.viewer.backplane.LidarPointSelection;
import org.tinfour.demo.viewer.backplane.ModelFromLas;
import org.tinfour.demo.viewer.backplane.MvComposite;
import org.tinfour.demo.viewer.backplane.MvQueryResult;
import org.tinfour.demo.viewer.backplane.RenderProduct;
import org.tinfour.demo.viewer.backplane.RenderProductType;
import org.tinfour.demo.viewer.backplane.ViewOptions;
import org.tinfour.demo.viewer.backplane.ViewOptions.RasterInterpolationMethod;

/**
 * A test panel to demonstrate mouse events and coordinate transformation when
 * viewing images.
 */
@SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
public class DataViewingPanel extends JPanel {

  private static final long serialVersionUID = 1L;

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

  private boolean redrawInProgress;
  boolean firstDraw = true;

  /**
   * The point at which the mouse was most recently pressed; used for
   * click-and-drag operations.
   */
  Point2D imagePressPoint;

  private ViewOptions viewOptions;
  private MvComposite mvComposite;
  private BufferedImage compositeImage;
  private BufferedImage legendImage;
  private AffineTransform c2p; // composite to panel
  private AffineTransform p2c; // panel to composite
  private AffineTransform p2m;  // panel to model
  private RenderProduct[] renderProducts = new RenderProduct[3];
  private MvQueryResult mvQueryResult;
  private boolean showScale;
  private boolean showLegend;

  Timer redrawTimer;

  private final List<IModelChangeListener> modelChangeListeners = new ArrayList<>();

  DataViewingPanel() {
    super();
    viewOptions = new ViewOptions();
  }

  void clear() {
    if (mvComposite != null && mvComposite.getModel().isLoaded()) {
      for (IModelChangeListener listener : modelChangeListeners) {
        listener.modelRemoved();
      }
    }
    backplaneManager.clear();
    mvComposite = null;
    compositeImage = null;
    legendImage = null;
    renderProducts[0] = null;
    renderProducts[1] = null;
    renderProducts[2] = null;
    reportPane.setText(null);
    queryPane.setText(null);
    repaint();
  }

  void setViewOptions(ViewOptions view) {
    this.viewOptions = view;
    setBackground(viewOptions.getBackground());
    backplaneManager.setViewOptions(view);

    if (!view.isWireframeSelected()) {
      renderProducts[RenderProductType.Wireframe.getStackingOrder()] = null;
    }

    if (!view.isConstraintRenderingSelected()) {
      renderProducts[RenderProductType.Constraints.getStackingOrder()] = null;
    }

    if (!view.isGridBasedRenderingSelected()) {
      renderProducts[RenderProductType.Raster.getStackingOrder()] = null;
    }

    assembleComposite();  // removes any layers that were turned off.
    repaint();

    /**
     * Ideally, the logic for model and the view would be strictly separated,
     * but for memory management there is a special case where the two are
     * conflated in the case of the Lidar point selection. The view options
     * point selection controls which samples from the LAS/LAZ file are
     * displayed. To reduce memory consumption and expedite processing, the
     * viewer only loads those vertices from the Lidar file that are to be
     * rendered. So if the user selects a different selection option, the model
     * needs to be reloaded. While this approach is acceptable for this
     * demonstrator implementation, the model and view concepts should not be
     * allowed to become confused in a fully realized implementation of a data
     * analysis application.
     */
    if (mvComposite != null && mvComposite.getModel() instanceof ModelFromLas) {
      IModel model = mvComposite.getModel();
      ModelFromLas lasModel = (ModelFromLas) model;
      LidarPointSelection oldPointSelection = lasModel.getLidarPointSelection();
      LidarPointSelection newPointSelection = view.getLidarPointSelection();
      if (!oldPointSelection.equals(newPointSelection)) {
        model = new ModelFromLas(
                lasModel.getFile(),
                newPointSelection);

        CompositeImageScale ccs = this.getImageScaleForContinuity();

        // if the application failed to queue the constraints
        // if could return a null.  This should be rare.
        mvComposite = backplaneManager.queueReloadTask(model, view, ccs);
        return;
      }
    }
    checkForRedrawWithView(view);
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

          if (mvComposite == null || !mvComposite.isReady()) {
            readoutLabel.setText("");
          } else if (e.getButton() == MouseEvent.BUTTON1) {{
            double[] c = new double[4];
            c[0] = e.getX();
            c[1] = e.getY();
            p2c.transform(c, 0, c, 2, 1);
            mvQueryResult = mvComposite.performQuery(c[2], c[3]);
            queryPane.setText(mvQueryResult.getText());
            repaint();
            Vertex v  =mvQueryResult.getNearestVertex();
            System.out.format("%13.4f,%13.4f,%13.4f,%8d%n",
              v.getX(), v.getY(), v.getZ(), v.getIndex());
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

  ExportImage getRenderedImage(boolean transparentBackground, boolean addFrame) {

    int iW = getWidth();
    int iH = getHeight();
    BufferedImage bImage =
            new BufferedImage(iW, iH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bImage.createGraphics();
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

    if (!transparentBackground) {
      Color c = viewOptions.getBackground();
      g2d.setColor(c);
      g2d.fillRect(0, 0, iW + 1, iH + 1);
    }



    if (compositeImage == null) {
      return null;
    }
    g2d.drawImage(compositeImage, c2p, null);

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
          si.render(g2d, x0, y0, f, Color.white, Color.black);
        }
      }
      if (showLegend && legendImage != null) {
        int h = legendImage.getHeight();
        int y0 = getHeight() - h - 5;
        if (y0 < 5) {
          y0 = 5;
        }
        g2d.drawImage(legendImage, 5, y0, this);
      }
    }

    if (addFrame) {
      g2d.setColor(Color.darkGray);
      g2d.drawRect(0, 0, iW-1, iH-1);
    }
    return new ExportImage(bImage, p2m);

  }

  private void triggerModelRemoved() {
    if (mvComposite != null) {
      for (IModelChangeListener listener : modelChangeListeners) {
        listener.modelRemoved();
      }
    }
  }

  private void triggerModelAdded(IModel model) {
    for (IModelChangeListener listener : modelChangeListeners) {
      listener.modelAdded(model);
    }
  }

  /**
   * Load the model from the specified file and display in panel. This method
   * must be called from the Event Dispatch Thread
   *
   * @param file A valid file
   */
  void loadModel(File file) {
    clearState();
    String ext = getFileExtension(file);
    if ("shp".equalsIgnoreCase(ext)) {
      raiseShapefileOptions(file);
    } else {
      IModel model = backplaneManager.loadModel(file, null);
      postModelName(model); // will handle null case
      repaint();
    }
  }

  /**
   * Load the specified model. This method must be called from the Event
   * Dispatch Thread
   *
   * @param model a valid model instance.
   */
  void loadModel(IModel model) {
    clearState();
    postModelName(model);
    backplaneManager.loadModel(model);
    repaint();
  }

  private void postModelName(IModel model) {
    String title = "Tinfour viewer";
    if (model != null) {
      String name = model.getName();
      if (name != null) {
        name = name.trim();
        if (!name.isEmpty()) {
          title = title + ": " + name; //NOPMD
        }
      }
    }

    Component component = this.getParent();
    while (component != null) {
      if (component instanceof JFrame) {
        ((JFrame) component).setTitle(title);
        break;
      }
      component = component.getParent();
    }
  }

  void clearState() {
    triggerModelRemoved();
    backplaneManager.clear();
    mvComposite = null;
    mvQueryResult = null;
    renderProducts[0] = null;
    renderProducts[1] = null;
    renderProducts[2] = null;
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
  }

  void loadConstraintsAndAddToModel(File file) {
    if (mvComposite == null) {
      JOptionPane.showMessageDialog(
              this,
              "Cannot add constraints when no model is loaded",
              "Error adding constraints",
              JOptionPane.ERROR_MESSAGE);
      return;
    }

    String ext = getFileExtension(file);

    IModel model = mvComposite.getModel();
    CompositeImageScale ccs = this.getImageScaleForContinuity();
    // if the application failed to queue the constraints
    // if could return a null.  This should be rare.
    if ("shp".equalsIgnoreCase(ext)) {
      this.raiseShapefileOptionsForConstraint(model, file, ccs);
      return;
    }

    MvComposite mvc
            = backplaneManager.queueConstraintLoadingTask(model, file, ccs, null);
    if (mvc != null) {
      mvComposite = mvc;
      model = mvc.getModel();
    }

  }

  /**
   * Center the image in the display panel, zooming to the largest scale that
   * permits the entire image to be seen on the display.
   */
  void zoomToSource() {
    if (mvComposite != null && mvComposite.isReady()) {

      IModel model = mvComposite.getModel();
      CompositeImageScale ccs
              = getImageScaleToCenterModelInPanel(model);
      MvComposite newComposite = backplaneManager.queueHeavyweightRenderTask(
              model,
              viewOptions,
              ccs);
      setMvComposite(newComposite);
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

    int index = product.layerType.getStackingOrder();
    renderProducts[index] = product;

    assembleComposite();
    repaint();
  }

  public void postModelLoadFailed(IModel model) {
    clear();
  }

  public void postMvComposite(MvComposite composite) {

    setMvComposite(composite);
    this.triggerModelAdded(composite.getModel());
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
   * image coordinates (mx, my) applying the scale factor s where s scales image
   * points to pixels. For example s*(mx1-mx0) = pixel_width.
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
        AffineTransform m2c = mvComposite.getModel2CompositeTransform();
        a.concatenate(c2p);
        a.concatenate(m2c);
        for (int i = 0; i < renderProducts.length; i++) {
          if (renderProducts[i] != null) {
            AffineTransform compatibility
                    = AffineTransform.getTranslateInstance(pad, pad);
            compatibility.concatenate(c2p);
            if (renderProducts[i].compatibilityTransform == null) {
              renderProducts[i].compatibilityTransform = compatibility;
            } else {
              renderProducts[i].compatibilityTransform.preConcatenate(compatibility);
            }
          }
        }

        CompositeImageScale ccs = getImageScaleForContinuity();
        mvComposite = backplaneManager.queueHeavyweightRenderTask(
                mvComposite.getModel(),
                mvComposite.getView(),
                ccs);
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

    CompositeImageScale ccs = this.getImageScaleForContinuity();

    IModel model = mvComposite.getModel();
    ViewOptions oldView = mvComposite.getView();
    this.viewOptions = view;
    boolean heavyweightRedrawRequired = false;

    //TO DO: This is not quite right.  Rendering improved since it was first
    //       coded...  Now the TIN-building for the wireframe
    //       often doesn't take long and can be treated as a light-weight
    //       process.  So perhaps only cases where heavyweight is required
    //       is when the wireframe switches to a dense sampling or when the
    //       raster drawing is required.
    if (view.isWireframeSelected()
            && oldView.getWireframeSampleThinning() != view.getWireframeSampleThinning()) {
      heavyweightRedrawRequired = true;
    }

    if (view.isWireframeSelected() && !oldView.isWireframeSelected()) {
      heavyweightRedrawRequired = true;
    }

    if (view.isRasterSelected()) {
      if (!oldView.isRasterSelected()) {
        heavyweightRedrawRequired = true;
      }
      if (view.isHillshadeSelected()) {
        // if the previous view was GWR, it would have build the hillshade
        // data anyway.
        boolean hillshadeBuilt
                = oldView.isHillshadeSelected()
                || oldView.getRasterInterpolationMethod()
                == RasterInterpolationMethod.GeographicallyWeightedRegression;
        if (!hillshadeBuilt) {
          heavyweightRedrawRequired = true;
        }
      }
      if (view.isFullResolutionGridSelected() != oldView.isFullResolutionGridSelected()) {
        heavyweightRedrawRequired = true;
      }
      if (view.getRasterInterpolationMethod() != oldView.getRasterInterpolationMethod()) {
        heavyweightRedrawRequired = true;
      }
    }

    if (heavyweightRedrawRequired) {
      // perform a heavy-weight render, building up new TINs as necessary
      MvComposite newComposite = backplaneManager.queueHeavyweightRenderTask(
              model, view, ccs);
      mvComposite = newComposite;
      assembleLegend();
      mvQueryResult = null;
    } else {
      // perform a light-weight render reusing existing TINs
      MvComposite newComposite
              = backplaneManager.queueLightweightRenderTask(
                      mvComposite, view);
      mvComposite = newComposite;
      assembleLegend();
      mvQueryResult = null;
    }
  }

  void zoomToModelPosition(double x, double y, double targetModelWidth) {
    if (mvComposite == null || !mvComposite.isReady()) {
      return;
    }
    if (redrawTimer != null) {
      redrawTimer.stop();
      redrawTimer = null;
    }

    int pad = viewOptions.getPadding();

    IModel model = mvComposite.getModel();
    int w = getWidth();
    int h = getHeight();
    double currentUnitPerPixel = Math.sqrt(Math.abs(p2m.getDeterminant()));
    double currentModelWidth = w * currentUnitPerPixel;
    double scaleFactor = targetModelWidth / currentModelWidth;
    double unitPerPixel = currentUnitPerPixel * scaleFactor;
    double pixelPerUnit = 1 / unitPerPixel;

    // compute model to composite transform
    AffineTransform m2c
            = new AffineTransform(
                    pixelPerUnit, 0, 0, -pixelPerUnit,
                    w / 2 + pad - pixelPerUnit * x,
                    h / 2 + pad + pixelPerUnit * y);

    // verify that transform is invertible... it should always be so.
    AffineTransform c2m;
    try {
      c2m = m2c.createInverse();
    } catch (NoninvertibleTransformException ex) {
      ex.printStackTrace(System.out); // not expected to happen
      return;
    }

    CompositeImageScale ccs
            = new CompositeImageScale(
                    getWidth() + 2 * pad,
                    getHeight() + 2 * pad,
                    m2c, c2m);

    renderProducts[0] = null;
    renderProducts[1] = null;
    renderProducts[2] = null;
    mvComposite = null;
    mvQueryResult = null;
    compositeImage = null;
    legendImage = null;
    resizeAnchorX = Double.NaN;
    resizeAnchorY = Double.NaN;
    reportPane.setText(null);
    queryPane.setText(null);
    readoutLabel.setText("");
    c2p = AffineTransform.getTranslateInstance(-pad, -pad);
    p2c = AffineTransform.getTranslateInstance(pad, pad);
    // perform a heavy-weight render, building up new TINs as necessary
    MvComposite newComposite
            = backplaneManager.queueHeavyweightRenderTask(
                    model, viewOptions, ccs);

    // call setMvComposite... this will also set up the proper
    // panel-to-model transforms
    setMvComposite(newComposite);
    assembleLegend();

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

  /**
   * Gets the current model-view composite (note that there is no guarantee that
   * the model is loaded and ready when this method is called).
   *
   * @return if populated, a valid instance; otherwise, a null.
   */
  MvComposite getMvComposite() {
    return mvComposite;
  }

  /**
   * Gets the panel to model transform.
   *
   * @return if available, a valid transform. Otherwise, a null
   */
  AffineTransform getPanelToModelTransform() {
    return p2m;
  }

  /**
   * Adds a model change listener to the panel
   *
   * @param listener a valid model change listener.
   */
  public void addModelChangeListener(IModelChangeListener listener) {
    modelChangeListeners.add(listener);
  }

  /**
   * Removes a model change listener from the panel
   *
   * @param listener a valid model change listener.
   */
  public void removeModelChangeListener(IModelChangeListener listener) {
    modelChangeListeners.remove(listener);
  }

  /**
   * Gets an instance of the current model, if any. The model may not yet be
   * loaded.
   *
   * @return if available, a valid model; otherwise, a null.
   */
  public IModel getModel() {
    if (mvComposite != null) {
      return mvComposite.getModel();
    }
    return null;
  }

  /**
   * Gets a composite image scale to center the model in the panel with the
   * amount of padding specified by the current view.
   *
   * @param model a valid model
   * @return a valid, fully populated instance.
   */
  public CompositeImageScale getImageScaleToCenterModelInPanel(IModel model) {
    int width = getWidth();
    int height = getHeight();
    double mx0 = model.getMinX();
    double my0 = model.getMinY();
    double mx1 = model.getMaxX();
    double my1 = model.getMaxY();

    double uPerPixel; // unit of measure from model per pixel
    // the model to composite transform is not yet established.
    // compute a transform that will ensure that the model is
    // presented as large as it possibly can, fitting it into
    // the available space in the composite image.
    double cAspect = (double) width / (double) height; // aspect of composite
    double mAspect = (mx1 - mx0) / (my1 - my0); // aspect of model
    double aspect = cAspect / mAspect;

    double xOffset = 0;
    double yOffset = 0;
    if (aspect >= 1) {
      // the proportions of the panel is wider than the proportions of
      // the image.  The vertical extent is the limiting factor
      // for the size of the image
      uPerPixel = height / (my1 - my0);
      double w = uPerPixel * (mx1 - mx0);
      xOffset = (width - w) / 2;
    } else {
      // the horizontal extent is the limiting factor
      // for the size of the image
      uPerPixel = width / (mx1 - mx0);
      double h = uPerPixel * (my1 - my0);
      yOffset = (height - h) / 2;
    }

    int pad = viewOptions.getPadding();
    AffineTransform m2c
            = new AffineTransform(
                    uPerPixel, 0, 0, -uPerPixel,
                    xOffset + pad - uPerPixel * mx0,
                    yOffset + pad + uPerPixel * my1);

    // verify that transform is invertible... it should always be so.
    AffineTransform c2m;
    try {
      c2m = m2c.createInverse();
    } catch (NoninvertibleTransformException ex) {
      return null;
    }

    CompositeImageScale ccs
            = new CompositeImageScale(
                    width + 2 * pad, height + 2 * pad, m2c, c2m);

    return ccs;
  }

  CompositeImageScale getImageScaleForContinuity() {
    int pad = viewOptions.getPadding();
    AffineTransform a = AffineTransform.getTranslateInstance(pad, pad);
    AffineTransform m2c = mvComposite.getModel2CompositeTransform();
    a.concatenate(c2p);
    a.concatenate(m2c);
    AffineTransform aInv;
    try {
      aInv = a.createInverse();
    } catch (NoninvertibleTransformException ex) {
      return null; // should never happen
    }

    return new CompositeImageScale(
            getWidth() + 2 * pad,
            getHeight() + 2 * pad,
            a, aInv
    );
  }

  String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  private void raiseShapefileOptions(final File file) {
    JFrame frame = null;
    Component c = this.getParent();
    while (c != null) {
      if (c instanceof JFrame) {
        frame = (JFrame) c;
        break;
      }
      c = c.getParent();
    }
    if (frame == null) {
      return;
    }

    ShapefileReader reader;
    try {
      reader = new ShapefileReader(file);
    } catch (IOException ioex) {
      return;
    }

    final ShapefileOptionsPanel shapefilePanel = new ShapefileOptionsPanel();
    shapefilePanel.applyShapefile(reader);
    try {
      reader.close();
    } catch (IOException ioex) {
      return;
    }

    ActionListener okActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent ae) {
        String s = shapefilePanel.getMetadataSelection();
        IModel model = backplaneManager.loadModel(file, s);
        postModelName(model); // will handle null case
        repaint();
      }
    };

    shapefilePanel.setOkActionListener(okActionListener);

    JDialog shapefileDialog = new JDialog(frame,
            "Shapefile Options",
            false);
    shapefileDialog.setContentPane(shapefilePanel);
    shapefileDialog.setDefaultCloseOperation(
            JDialog.HIDE_ON_CLOSE);
    //shapefileDialog.addWindowListener(new WindowAdapter() {
    //  @Override
    //  public void windowClosing(WindowEvent we) {
    //
    //  }
    //
    //});

    shapefileDialog.pack();
    shapefileDialog.setLocationRelativeTo(frame);
    shapefileDialog.setVisible(true);

  }

  void raiseShapefileOptionsForConstraint(IModel model, File file, CompositeImageScale ccs) {
    JFrame frame = null;
    Component c = this.getParent();
    while (c != null) {
      if (c instanceof JFrame) {
        frame = (JFrame) c;
        break;
      }
      c = c.getParent();
    }
    if (frame == null) {
      return;
    }

    ShapefileReader reader;
    try {
      reader = new ShapefileReader(file);
    } catch (IOException ioex) {
      return;
    }

    final ShapefileOptionsPanel shapefilePanel = new ShapefileOptionsPanel();
    shapefilePanel.applyShapefile(reader);
    try {
      reader.close();
    } catch (IOException ioex) {
      return;
    }

    final DataViewingPanel self = this;
    ActionListener okActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent ae) {
        String s = shapefilePanel.getMetadataSelection();
        MvComposite mvc
                = backplaneManager.queueConstraintLoadingTask(model, file, ccs, s);
        if (mvc != null) {
          mvComposite = mvc;
        }
        self.repaint();
      }
    };

    shapefilePanel.setOkActionListener(okActionListener);

    JDialog shapefileDialog = new JDialog(frame,
            "Shapefile Options",
            false);
    shapefileDialog.setContentPane(shapefilePanel);
    shapefileDialog.setDefaultCloseOperation(
            JDialog.HIDE_ON_CLOSE);
    //shapefileDialog.addWindowListener(new WindowAdapter() {
    //  @Override
    //  public void windowClosing(WindowEvent we) {
    //
    //  }
    //
    //});

    shapefileDialog.pack();
    shapefileDialog.setLocationRelativeTo(frame);
    shapefileDialog.setVisible(true);

  }

}
