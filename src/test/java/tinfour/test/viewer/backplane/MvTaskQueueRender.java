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

/**
 * A task added to the loaderQueue in order to launch
 * rendering operations. The purpose of this task is to handle cases
 * where multiple user-initiated rendering-related operations may be
 * queued while the application is busy loading model data.
 * It ensures that rendering is delayed until the loading is complete.
 */
class MvTaskQueueRender implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final MvComposite composite;
  private final int taskIndex;
  private boolean isCancelled;

  MvTaskQueueRender(
    BackplaneManager backplaneManager,
    MvComposite composite,
    int taskIndex) {
    this.composite = composite;
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

    MvTaskBuildTinAndRender renderTask
      = new MvTaskBuildTinAndRender(
        backplaneManager,
        composite,
        taskIndex);
    backplaneManager.renderPool.queueTask(renderTask);

  }

  @Override
  public int getTaskIndex() {
    return taskIndex;
  }

  @Override
  public boolean isRenderingTask() {
    return true;
  }

}
