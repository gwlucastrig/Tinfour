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
package tinfour.test.viewer;

import java.awt.Container;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import tinfour.common.IMonitorWithCancellation;

class StatusPanelMonitor extends JProgressBar implements IMonitorWithCancellation {
  private static final long serialVersionUID = 1L;
  private final StatusPanel statusPanel;
  private final int taskIndex;
  private boolean cancelled;

  private StatusPanelMonitor() {
    statusPanel = null;
    taskIndex = 0;
  }

  public StatusPanelMonitor(StatusPanel statusPanel, int taskIndex, int orient, int min, int max) {
    super(orient, min, max);
    this.statusPanel = statusPanel;
    this.taskIndex = taskIndex;
  }

  @Override
  public int getReportingIntervalInPercent() {
    return 5;
  }

  @Override
  public void reportProgress(final int percentComplete) {
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        setValue(percentComplete);
      }

    });
  }

  @Override
  public boolean isCanceled() {
    return cancelled;
  }

  public void cancel() {
    cancelled = true;
    reportDone();
  }

  @Override
  public void reportDone() {
    final StatusPanelMonitor self = this;
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        Container parent = getParent();
        while (parent != null) {
          if (parent instanceof StatusPanel) {
            parent.remove(self);
            return;
          }
          parent = getParent();
        }
      }

    });
  }

  @Override
  public void postMessage(final String message) {
    // status panel handles event dispatch thread issue
    statusPanel.postMessage(taskIndex, message);
  }

}
