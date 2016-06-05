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

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import tinfour.common.IIncrementalTin;
import tinfour.common.IMonitorWithCancellation;

/**
 * Provides a runnable for performing rendering in response to a style
 * change on a previously rendering image so that TIN and grid elements
 * may be reused without being rebuilt.
 */
class MvTaskRender implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final MvComposite composite;
  private final int taskIndex;
  private final ViewOptions view;
  private final int height;
  private boolean isCancelled;

  MvTaskRender(
    BackplaneManager backplaneManager,
    MvComposite composite,
    int taskIndex) {
    this.backplaneManager = backplaneManager;
    this.composite = composite;
    this.view = this.composite.getView();
    this.height = this.composite.getHeight();
    this.taskIndex = taskIndex;

  }

  @Override
  public void cancel() {
    isCancelled = true;
  }

  @Override
  public boolean isCancelled() {
    return isCancelled;
  }

  @Override
  public void run() {
    if (isCancelled) {
      return; // done
    }

    if (view.isWireframeSelected()) {
      backplaneManager.postStatusMessage(taskIndex, "Rendering wireframe image");

      BufferedImage bImage = composite.renderWireframe();
      RenderProduct product = new RenderProduct(
        RenderProductType.Wireframe,
        composite,
        bImage);
      backplaneManager.postImageUpdate(this, product);
    }

    // There are two cases of interest where new grid processing
    // might be required:
    //   1)  The grid was not previously built because the raster-related
    //       options were turned off and the user just activated one or both.
    //   2)  The previous grid did not include hillshade and the user
    //       just turned it on.
    boolean sRaster = view.isRasterSelected();
    boolean sHillshade = view.isHillshadeSelected();
    boolean sGrid = sRaster || sHillshade;
    if (!isCancelled && sGrid) {
      boolean sGridComplete = composite.zGridComplete;
      if (sGridComplete && sHillshade && !composite.zGridIncludesHillshade) {
        sGridComplete = false;
      }
      if (sGridComplete) {
        composite.transferGridToRasterImage();
        RenderProduct product = new RenderProduct(
          RenderProductType.Raster,
          composite,
          composite.rasterImage);
        backplaneManager.postImageUpdate(this, product);
      } else {
        launchRasterProcessing(composite.rasterTin);
      }
    }

  }

  void launchRasterProcessing(IIncrementalTin tin) {

    backplaneManager.postStatusMessage(
         taskIndex, "Interpolating surface for raster image");
    IMonitorWithCancellation monitor = backplaneManager.getProgressMonitor(taskIndex);
    composite.startGridBuildTimer();
    int poolSize = BackplaneExecutor.getInstance().getCorePoolSize();
    int nTasks = poolSize;
    if (nTasks < 4) {
      nTasks = 4;
    }
    int k = height / nTasks;
    if (k == 0) {
      k = height;
    }
    int nBlock = (height + k - 1) / k;
    AtomicInteger blockCounter = new AtomicInteger(nBlock);
    for (int i = 0; i < nBlock; i++) {
      int row0 = i * k;
      int row1 = row0 + k;
      if (row1 > height) {
        row1 = height;
      }

      int nRow = row1 - row0;
      MvTaskBuildRasterBlock blockBuilder
        = new MvTaskBuildRasterBlock( //NOPMD
          backplaneManager,
          composite,
          blockCounter,
          nBlock,
          row0,
          nRow,
          taskIndex,
          monitor);
      BackplaneExecutor.getInstance().runTask(blockBuilder);

    }

  }

}
