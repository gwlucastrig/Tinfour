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

import java.util.concurrent.atomic.AtomicInteger;
import tinfour.common.IMonitorWithCancellation;

/**
 * Provide a runnable for concurrently building a sections of the grid (blocks)
 * needed to support raster rendering.
 */
class MvTaskBuildRasterBlock implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final IMonitorWithCancellation monitor;
  private final int nBlocks;

  private final MvComposite composite;
  private final boolean hillshade;
  private final int taskIndex;

  private boolean isCancelled;

  private final int row0, nRow;
  private final AtomicInteger blockCounter;

  MvTaskBuildRasterBlock(
    BackplaneManager backplaneManager,
    MvComposite composite,
    AtomicInteger blockCounter,
    int nBlocks,
    int row0,
    int nRow,
    int taskIndex,
    IMonitorWithCancellation monitor) {
    this.blockCounter = blockCounter;
    this.backplaneManager = backplaneManager;
    this.composite = composite;
    this.taskIndex = taskIndex;
    this.monitor = monitor;
    this.nBlocks = nBlocks;
    this.row0 = row0;
    this.nRow = nRow;
    hillshade = composite.getView().isHillshadeSelected();
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

    composite.buildGrid(row0, nRow, hillshade, this);

    int test = blockCounter.decrementAndGet();
    int percentDone = (int) (100.0 * (nBlocks - test) / (double) nBlocks + 0.5);
    monitor.reportProgress(percentDone);
    if (test == 0) {
      composite.stopGridBuildTimer();
      backplaneManager.statusPanel.postMessage(taskIndex, "Rendering raster image");
      composite.transferGridToRasterImage();
      RenderProduct product = new RenderProduct(
        RenderProductType.Raster,
        composite,
        composite.rasterImage);
      monitor.reportDone();
      backplaneManager.postImageUpdate(this, product);
    }
  }

}
