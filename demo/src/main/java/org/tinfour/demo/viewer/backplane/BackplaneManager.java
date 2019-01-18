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
package org.tinfour.demo.viewer.backplane;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.demo.viewer.DataViewingPanel;
import org.tinfour.demo.viewer.StatusPanel;

public class BackplaneManager {

  /*
   * The renderPool and loaderQueue are named to reflect their functions.
   * The loaderQueue has only one thread, and is used to load models one
   * at a time, so it's named "queue". The render operation is permitted
   * to use multiple threads running concurrently, so is named "pool"
   */
  final BackplaneExecutor renderPool;
  final BackplaneExecutor loaderQueue;

  final DataViewingPanel viewingPanel;
  final StatusPanel statusPanel;
  final AtomicInteger taskIndexSource = new AtomicInteger();

  ViewOptions viewOptions = new ViewOptions();

  public BackplaneManager(DataViewingPanel viewingPanel, StatusPanel statusPanel) {
    this.viewingPanel = viewingPanel;
    this.statusPanel = statusPanel;
    renderPool = new BackplaneExecutor(0); // size determined by CPU
    loaderQueue = new BackplaneExecutor(1);
  }
 

  public void postStatusMessage(int index, final String message) {
    statusPanel.postMessage(index, message);
  }

  /**
   * Post an image update to the user interface
   *
   * @param task the task associated with the update
   * @param product a valid render product
   */
  public void postImageUpdate(final IModelViewTask task, final RenderProduct product) {
    final int taskIndex = product.composite.getTaskIndex();

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

  void cancelAllTasks() {
    loaderQueue.cancelAllTasks();
    renderPool.cancelAllTasks();
  }

  void cancelRenderingTasks() {
    loaderQueue.cancelRenderingTasks();
    renderPool.cancelRenderingTasks();
  }

  /**
   * Cancel all running tasks and clear all current status posts.
   */
  public void clear() {
    cancelAllTasks();
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
    if (model == null) {
      throw new IllegalArgumentException("Null model not allowed");
    }
    cancelAllTasks();
    final int taskIndex = taskIndexSource.incrementAndGet();
    String name = model.getName();
    if (model.isLoaded()) {
      centerModelInPanelAndRunRender(model, getViewOptions(), taskIndex);
    } else {
      postStatusMessage(taskIndex, "Loading " + name);
      MvTaskLoad task
        = new MvTaskLoad(this, model, taskIndex);
      loaderQueue.queueTask(task);
    }
  }

  /**
   * Instantiate a file-based model and queue a loading task. Ultimately
   * the loading task will queue rendering tasks.
   *
   * @param file a valid reference to a readable file in a supported format.
   * @param option an optional data element based on the file-type
   * @return if the model was successfully initialized, a valid instance;
   * otherwise a null.
   */
  public IModel loadModel(File file, String option) {
    cancelAllTasks();
    int taskIndex = taskIndexSource.incrementAndGet();

    String name = file.getName();

    if (!file.exists() || !file.canRead()) {
      // not likely, but test just in case
      postStatusMessage(taskIndex,
        "File "
        + name
        + " does not exist or cannot be read");
      return null;
    }

    String ext = extractFileExtension(file);
    IModel model;
    ViewOptions view = getViewOptions();

    if ("LAZ".equalsIgnoreCase(ext) || "LAS".equalsIgnoreCase(ext)) {
      LidarPointSelection selections = view.getLidarPointSelection();
      model = new ModelFromLas(file, selections);
    } else if ("TXT".equalsIgnoreCase(ext)) {
      model = new ModelFromText(file );
    } else if ("CSV".equalsIgnoreCase(ext)) {
      model = new ModelFromText(file );
    } else if ("SHP".equalsIgnoreCase(ext)) {
      model = new ModelFromShapefile(file, option);
    } else {
      postStatusMessage(taskIndex, "Unrecognized file extension " + ext);
      return null;
    }

    postStatusMessage(taskIndex, "Loading " + name);
    MvTaskLoad task = new MvTaskLoad(this, model, taskIndex);
    loaderQueue.queueTask(task);
    return model;
  }

  public MvComposite queueConstraintLoadingTask(
    IModel model, File file, CompositeImageScale ccs) {
    cancelRenderingTasks();
    int width = ccs.getWidth();
    int height = ccs.getHeight();
    AffineTransform m2c = ccs.getModelToCompositeTransform();
    AffineTransform c2m = ccs.getCompositeToModelTransform();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    ViewOptions vOpt = getViewOptions();
    MvComposite mvComposite
      = new MvComposite(model, vOpt, width, height, m2c, c2m, taskIndex);

    String name = file.getName();

    if (!file.exists() || !file.canRead()) {
      // not likely, but test just in case
      postStatusMessage(taskIndex,
        "File "
        + name
        + " does not exist or cannot be read");
      return null;
    }

    MvTaskLoadConstraints task
      = new MvTaskLoadConstraints(this, file, mvComposite, taskIndex);
    loaderQueue.queueTask(task);
    return mvComposite;
  }

  public MvComposite queueReloadTask(
    IModel model,
    ViewOptions vOpt,
    CompositeImageScale ccs) {
    cancelRenderingTasks();
    int width = ccs.getWidth();
    int height = ccs.getHeight();
    AffineTransform m2c = ccs.getModelToCompositeTransform();
    AffineTransform c2m = ccs.getCompositeToModelTransform();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    MvComposite mvComposite
      = new MvComposite(model, vOpt, width, height, m2c, c2m, taskIndex);

    MvTaskReload task
      = new MvTaskReload(this, mvComposite, taskIndex);
    loaderQueue.queueTask(task);
    return mvComposite;
  }

  /**
   * Posted by a loading task to indicate that the load is completed
   * and that follow on rendering may be initiated.
   *
   * @param model The model that was loaded from the data source
   * @param taskIndex the task associated with loading the model.
   */
  void postModelLoadCompleted(final IModelViewTask task, final IModel model, final int taskIndex) {
    if (task.isCancelled()) {
      return;
    }

    ViewOptions vOpt = getViewOptions();
    centerModelInPanelAndRunRender(model, vOpt, taskIndex);
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
        CompositeImageScale imgScale
          = viewingPanel.getImageScaleToCenterModelInPanel(model);

        MvComposite mvComposite
          = new MvComposite(model, view,
            imgScale.getWidth(),
            imgScale.getHeight(),
            imgScale.getModelToCompositeTransform(),
            imgScale.getCompositeToModelTransform(),
            taskIndex
          );

        viewingPanel.postMvComposite(mvComposite);
        MvTaskBuildTinAndRender renderTask
          = new MvTaskBuildTinAndRender(self, mvComposite, taskIndex);
        renderPool.queueTask(renderTask);

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
   * @param ccs specifications for model position and scale for imaging
   * @return a valid instance
   */
  public MvComposite queueHeavyweightRenderTask(IModel model,
    ViewOptions view,
    CompositeImageScale ccs) {
    cancelRenderingTasks();
    int width = ccs.getWidth();
    int height = ccs.getHeight();
    AffineTransform m2c = ccs.getModelToCompositeTransform();
    AffineTransform c2m = ccs.getCompositeToModelTransform();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    MvComposite mvComposite
      = new MvComposite(model, view, width, height, m2c, c2m, taskIndex);

    MvTaskQueueRender renderTask = new MvTaskQueueRender(this, mvComposite, taskIndex);
    loaderQueue.queueTask(renderTask);

    return mvComposite;
  }

  /**
   * Queues a new rendering task; typically invoked from the UI when the
   * user makes changes to the view options that do not require reconstructing
   * the TINs associated with the image.
   *
   * @param oldComposite an existing, model-view composite that contains
   * elements that can be transferred intact for reuse in a new composite
   * (TINs, grids, etc.).
   * @param view view-related options for rendering
   * @return a valid model-view composite instance.
   */
  public MvComposite queueLightweightRenderTask(
    MvComposite oldComposite,
    ViewOptions view) {
    cancelRenderingTasks();
    int taskIndex = this.taskIndexSource.incrementAndGet();
    MvComposite newComposite = new MvComposite(oldComposite, view, true, taskIndex);

    MvTaskQueueRender renderTask = new MvTaskQueueRender(this, newComposite, taskIndex);
    loaderQueue.queueTask(renderTask);
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
    synchronized (this) {
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

  void postModelRefreshCompleted(IModelViewTask task, MvComposite pmvComposite) {
    if (task.isCancelled()) {
      return;
    }

    MvComposite mvComposite = pmvComposite;
    IModel model = mvComposite.getModel();
    if (!model.isLoaded()) {
      System.err.println("load failed");
      return; // somehow the load failed
    }

    int taskIndex = task.getTaskIndex();

    ViewOptions vOpt = getViewOptions();
    if (vOpt != mvComposite.getView()) {
      // The view option changed while the data was being  loaded
      mvComposite = new MvComposite(mvComposite, vOpt, false, taskIndex);
    }
    MvTaskBuildTinAndRender renderTask
      = new MvTaskBuildTinAndRender(this, mvComposite, taskIndex);
    renderPool.queueTask(renderTask);

  }

}
