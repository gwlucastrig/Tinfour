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

import java.io.IOException;
import tinfour.common.IMonitorWithCancellation;

class MvTaskReload implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final MvComposite mvComposite;
  private final int taskIndex;
  private boolean isCancelled;

  MvTaskReload(
    BackplaneManager backplaneManager,
    MvComposite mvComposite,
    int taskIndex) {
    this.mvComposite = mvComposite;
    this.taskIndex = taskIndex;
    this.backplaneManager = backplaneManager;
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
    IModel model = mvComposite.getModel();
    if (model.isLoaded()) {
      System.err.println("Internal error, reloading a loaded model");
      return;
    }

    IMonitorWithCancellation monitor
      = backplaneManager.getProgressMonitor(taskIndex);

    try {
      monitor.postMessage("Reloading " + model.getName());
      monitor.reportProgress(0);
      model.load(monitor);
      // When a MvComposite is constructed from a model that has already
      // been loaded, the constructor determines the range of visible samples.
      // But in the special case where a model is reloaded, the samples
      // would not be available during MvComposite construction. So
      // the range must be obtained now.
      mvComposite.applyRangeOfVisibleSamples(model.getVertexList());
      mvComposite.submitCandidateTinForInterpolation(
        model.getReferenceTin(), model.getReferenceReductionFactor());

      backplaneManager.postModelRefreshCompleted(this, mvComposite);
    } catch (IOException ioex) {
      monitor.reportDone(); // to dismiss progress bar
      String message = "Error loading " + model.getName() + " " + ioex.getMessage();
      System.err.println(message);
      backplaneManager.postStatusMessage(taskIndex, message);
    } catch (Exception ex) {
      ex.printStackTrace(System.out);
    }
  }

  @Override
  public int getTaskIndex() {
    return taskIndex;
  }

  @Override
  public boolean isRenderingTask() {
    return false;
  }

}
