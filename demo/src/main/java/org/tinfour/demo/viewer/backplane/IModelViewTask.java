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

/**
 * Defines methods for processing related to the construction of
 * TINs and images for the presentation of data in the Tinfour Viewer
 * environment.
 */
interface IModelViewTask extends Runnable {

  /**
   * Marks the task as canceled. If a task is canceled before its run()
   * method is invoked, the BackplaneManager may be able to remove it from
   * the queue so that it is never executed and does not consume any
   * resources. When an implementation first enters its run method, it should
   * always check its cancellation status to see if it should continue.
   * Once processing begins, checking the cancellation status is largely
   * optional. A task should try to check the status when it can, but
   * not to the degree that it interferes with efficient processing.
   */
  public void cancel();

  /**
   * Indicates whether a task is cancelled.
   *
   * @return true if task is cancelled, otherwise false.
   */
  public boolean isCancelled();

  /**
   * Gets the sequentially assigned index for a task
   * @return a positive integer
   */
  public int getTaskIndex();

  /**
   * Indicates whether the task performs a rendering operation.
   * @return true if the task performs a rendering operation; otherwise false.
   */
  public boolean isRenderingTask();

}
