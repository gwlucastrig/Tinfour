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

class MvTaskLoad implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final IModel model;
  private final int taskIndex;
  private boolean isCancelled;

  MvTaskLoad(
    BackplaneManager backplaneManager,
    IModel model,
    int taskIndex) {
    this.model = model;
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

    try {
      IMonitorWithCancellation monitor
        = backplaneManager.getProgressMonitor(taskIndex);
      model.load(monitor);
      monitor.reportDone();
      backplaneManager.postModelLoadCompleted(this);
    } catch (IOException ioex) {
      String message = "Error loading " + model.getName() + " " + ioex.getMessage();
      System.err.println(message);
      backplaneManager.postStatusMessage(taskIndex, message);
    } catch (Exception ex) {
      ex.printStackTrace(System.out);
    }
  }

  int getTaskIndex() {
    return taskIndex;
  }

  IModel getModel() {
    return model;
  }

}
