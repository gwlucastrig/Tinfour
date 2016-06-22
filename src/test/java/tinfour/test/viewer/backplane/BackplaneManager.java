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
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import tinfour.common.IMonitorWithCancellation;
import tinfour.test.viewer.DataViewingPanel;
import tinfour.test.viewer.StatusPanel;
import tinfour.test.viewer.backplane.ViewOptions.LidarPointSelection;

public class BackplaneManager {

  private static final Logger LOGGER
    = Logger.getLogger(BackplaneManager.class.getName());

  final DataViewingPanel viewingPanel;
  final StatusPanel statusPanel;
  final AtomicInteger taskIndexSource = new AtomicInteger();

  ViewOptions viewOptions = new ViewOptions();

  public BackplaneManager(DataViewingPanel viewingPanel, StatusPanel statusPanel) {
    this.viewingPanel = viewingPanel;
    this.statusPanel = statusPanel;
  }

  public void postStatusMessage(int index, final String message) {
    statusPanel.postMessage(index, message);
  }

  /**
   * Post an image update to the user interface
   *
   * @param product a valid render product
   */
  public void postImageUpdate(final IModelViewTask task, final RenderProduct product) {
    final int taskIndex = product.composite.getTaskIndex();
    if (taskIndex < taskIndexSource.intValue()) {
      return; // there are more recent tasks at work
    }
    if (task.isCancelled()) {
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        viewingPanel.postImageUpdate(product);
        statusPanel.clear(taskIndex);
      }

    });
  }

  /**
   * Cancel all running tasks and clear all current status posts.
   */
  public void clear() {
    BackplaneExecutor.getInstance().cancelAllTasks();
    final int taskIndex = taskIndexSource.incrementAndGet();
    statusPanel.clear(taskIndex);
  }

  /**
   * Initiate a load task for an instance of a model and, ultimately,
   * queue rendering tasks.
   *
   * @param model a valid model
   */
  public void loadModel(final IModel model) {
    BackplaneExecutor.getInstance().cancelAllTasks();
    final int taskIndex = taskIndexSource.incrementAndGet();

    String name = model.getName();
    if (model.isLoaded()) {
      centerModelInPanelAndRunRender(model, getViewOptions(), taskIndex);
    } else {
      postStatusMessage(taskIndex, "Loading " + name);
      MvTaskLoad task
        = new MvTaskLoad(this, model, taskIndex);
      BackplaneExecutor.getInstance().runTask(task);
    }
  }

  /**
   * Instantiate a file-based model and queue a loading task. Ultimately
   * the loading task will queue rendering tasks.
   *
   * @param file a valid reference to a readable file in a supported format.
   */
  public void loadModel(File file) {

    BackplaneExecutor.getInstance().cancelAllTasks();
    int taskIndex = taskIndexSource.incrementAndGet();

    String name = file.getName();

    if (!file.exists() || !file.canRead()) {
      // not likely, but test just in case
      postStatusMessage(taskIndex,
        "File "
        + name
        + " does not exist or cannot be read");
      return;
    }

    String ext = extractFileExtension(file);
    IModel model;
    ViewOptions view = getViewOptions();

    if ("LAZ".equalsIgnoreCase(ext)) {
      postStatusMessage(taskIndex, "Tinfour does not yet support LAZ files");
      return;
    } else if ("LAS".equalsIgnoreCase(ext)) {
      LidarPointSelection selections = view.getLidarPointSelection();
      model = new ModelFromLas(file, selections);
    } else if ("TXT".equalsIgnoreCase(ext)) {
      model = new ModelFromText(file, ' ');
    } else if ("CSV".equalsIgnoreCase(ext)) {
      model = new ModelFromText(file, ',');
    } else {
      postStatusMessage(taskIndex, "Unrecognized file extension " + ext);
      return;
    }
    postStatusMessage(taskIndex, "Loading " + name);
    MvTaskLoad task = new MvTaskLoad(this, model, taskIndex);
    BackplaneExecutor.getInstance().runTask(task);
  }

  /**
   * Posted by a loading task to indicate that the load is completed
   * and that follow on rendering may be initiated.
   *
   * @param task a valid instance with a completed load operation.
   */
  void postModelLoadCompleted(final MvTaskLoad task) {
    final int taskIndex = task.getTaskIndex();
    if (taskIndex < taskIndexSource.intValue()) {
      return; // there are more recent tasks at work
    }
    IModel model = task.getModel();
    ViewOptions view = viewOptions;
    centerModelInPanelAndRunRender(model, view, taskIndex);
  }



  /**
   * Prepares a composite from a specified model and view options,
   * establishing transformations to center the model rendering on
   * the display panel.
   *
   * @param model a valid, fully loaded model
   * @param view a valid set of view options
   * @param taskIndex a task index associated with the load operation
   */
  private void centerModelInPanelAndRunRender(
    final IModel model, final ViewOptions view, final int taskIndex) {
    final BackplaneManager self = this;
    // Create model to panel transform --------------------------------
    // this transform will place the model graphics at the center of
    // the current viewing panel.  Since the logic for that action needs
    // to access the DataViewingPanel, it needs to be conducted within
    // the event dispatching thread.
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        int width = viewingPanel.getWidth();
        int height = viewingPanel.getHeight();
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
        double mAspect = (double) (mx1 - mx0) / (my1 - my0); // aspect of model
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

        //   s   0   xOffset
        //   0   s   yOffset
        //   0   0   1
        AffineTransform m2p
          = new AffineTransform(uPerPixel, 0, 0, -uPerPixel,
            xOffset - uPerPixel * mx0,
            yOffset + uPerPixel * my1);

        int pad = view.getPadding();
        AffineTransform m2c
          = new AffineTransform(
            uPerPixel, 0, 0, -uPerPixel,
            xOffset + pad - uPerPixel * mx0,
            yOffset + pad + uPerPixel * my1);

        // verify that transform is invertible... it should always be so.
        AffineTransform  c2m;
        try {
          c2m = m2c.createInverse();
        } catch (NoninvertibleTransformException ex) {
          LOGGER.log(Level.SEVERE, "Unexpected transform failure", ex);
          return;
        }

        MvComposite mvComposite
          = new MvComposite(model, view,
            width + 2 * pad, height + 2 * pad,
            m2c, c2m, taskIndex);

        MvTaskBuildTinAndRender renderTask
          = new MvTaskBuildTinAndRender(self, mvComposite, taskIndex);
        BackplaneExecutor.getInstance().runTask(renderTask);

      }

    });
  }

  /**
   * Queues a render task; typically invoked from the UI when the user changes
   * view options that require that a new model-view composite be created.
   * Typically, this is due to a change in the visible map-area (a pan or zoom)
   * or in view options that alter the point-selection
   * and require that any existing TINs be rebuilt.
   *
   * @param model a fully loaded model
   * @param view a valid instance
   * @param width width of the composite
   * @param height height of the composite
   * @param m2c model to composite transform
   * @param c2m composite to model transform
   * @return a valid instance.
   */
  public MvComposite queueHeavyweightRenderTask(IModel model, ViewOptions view, int width, int height, AffineTransform m2c, AffineTransform c2m) {
    BackplaneExecutor.getInstance().cancelAllTasks();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    MvComposite mvComposite
      = new MvComposite(model, view, width, height, m2c, c2m, taskIndex);

    if (model.isLoaded()) {
      MvTaskBuildTinAndRender renderTask
        = new MvTaskBuildTinAndRender(this, mvComposite, taskIndex);
      BackplaneExecutor.getInstance().runTask(renderTask);
    } else {
      // the reload task will eventually launch a build tin and render task
      MvTaskReload reloadTask = new MvTaskReload(this, mvComposite, taskIndex);
      BackplaneExecutor.getInstance().runTask(reloadTask);
    }

    return mvComposite;
  }

  /**
   * Queues a new rendering task; typically invoked from the UI when the
   * user makes changes to the view options that do not require reconstructing
   * the TINs associated with the image.
   *
   * @param mvComposite an existing, model-view composite that contains
   * elements that can be transferred intact for reuse in a new composite
   * (TINs, grids, etc.).
   * @param view a set of view options
   * @param width the width of the new composite
   * @param height the height of the new composite
   * @param m2c the model to composite transform
   * @param c2m the composite to model transform
   * @return a valid model-view composite instance.
   */
  public MvComposite queueLightweightRenderTask(
    MvComposite mvComposite,
    ViewOptions view,
    int width, int height, AffineTransform m2c, AffineTransform c2m) {
    BackplaneExecutor.getInstance().cancelAllTasks();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    MvComposite newComposite = new MvComposite(mvComposite, view, taskIndex);

    MvTaskRender renderTask = new MvTaskRender(this, newComposite, taskIndex);
    BackplaneExecutor.getInstance().runTask(renderTask);

    return newComposite;

  }

  private String extractFileExtension(File file) {
    String path = file.getPath();
    int i = path.lastIndexOf('.');
    if (i <= 0 || i >= path.length() - 1) {
      return null;
    }
    return path.substring(i + 1, path.length());
  }

  /**
   * Set the current view options to be used when loading any new models.
   * This method is synchronized to permit communication with background
   * processes.
   *
   * @param view a valid instance
   */
  public void setViewOptions(ViewOptions view) {
    synchronized (this) {
      this.viewOptions = view;
    }
  }

  /**
   * Gets the current view options that will be used when loading new models.
   * This method is synchronized to permit communication with background
   * processes.
   *
   * @return a valid instance
   */
   ViewOptions getViewOptions() {
     synchronized(this){
    return viewOptions;
     }
  }

  /**
   * Get an instance of a progress monitor tied to the indicated task.
   * If the task is known to be obsolete or overcome by events,
   * this method may return a null.
   *
   * @param taskIndex the task instance for processing
   * @return a valid instance, or a null if no monitor is available for
   * the indicated task index.
   */
  IMonitorWithCancellation getProgressMonitor(int taskIndex) {
    return statusPanel.getProgressMonitor(taskIndex);
  }

}
