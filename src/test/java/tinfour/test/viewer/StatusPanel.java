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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import tinfour.common.IMonitorWithCancellation;

public class StatusPanel extends JPanel {
  private static final long serialVersionUID=1L;
  JLabel statusLabel;
  StatusPanelMonitor currentMonitor;
  int taskIndex;

  StatusPanel() {
    super();
    setLayout(new GridBagLayout());
    //  Border lineBorder = BorderFactory.createLineBorder(Color.red);
    //  setBorder(lineBorder);
    statusLabel = new JLabel("To view a data source, drag file onto panel");
    statusLabel.setToolTipText("Indicates status");
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.9;
    c.ipadx = 5;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.LINE_END;
    add(statusLabel, c);
  }

  /**
   * Post a notice to the status panel from within or outside the
   * Event Dispatching Thread
   *
   * @param index the task index for the notice, or zero if not associated
   * with a task
   * @param message the message to be posted
   */
  public void postMessage(final int index, final String message) {
    if (SwingUtilities.isEventDispatchThread()) {
      spPostNotice(index, message);
    } else {
      SwingUtilities.invokeLater(new Runnable() {

        @Override
        public void run() {
          spPostNotice(index, message);
        }

      });
    }
  }

  public void clear(final int taskIndex) {
    if (SwingUtilities.isEventDispatchThread()) {
      spClear(taskIndex);
    } else {
      SwingUtilities.invokeLater(new Runnable() {

        @Override
        public void run() {
          spClear(taskIndex);
        }

      });
    }
  }

  private void spPostNotice(int index, String notice) {
    if (index < taskIndex) {
      return;
    }
    taskIndex = index;
    if (notice == null || notice.isEmpty()) {
      statusLabel.setText("");
    } else {
      statusLabel.setText(notice);
    }
  }

  private void spClear(int index) {
    if (index < taskIndex) {
      return;
    }
    statusLabel.setText("");
    taskIndex = 0;
  }

  /**
   * Creates a progress monitor associated with the specified task
   * and adds it to the status panel.
   * If the task is is not the currently registered task, a
   * monitor will be created, but it will not be added to the status
   * panel.
   *
   * @param index the index of the task associated with the monitor
   * @return a valid progress monitor.
   */
  public IMonitorWithCancellation getProgressMonitor(final int index) {
    if (index == this.taskIndex && currentMonitor != null) {
      return currentMonitor;
    }
    final StatusPanel self = this;
    final StatusPanelMonitor monitor
      = new StatusPanelMonitor(this, index, JProgressBar.HORIZONTAL, 0, 100);
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        if (index >= self.taskIndex) {
          self.taskIndex = index;
          if (currentMonitor != null) {
            remove(currentMonitor);
          }
          monitor.setValue(0);
          monitor.setStringPainted(true);
          GridBagConstraints c = new GridBagConstraints();
          c.gridx = 1;
          c.gridy = 0;
          c.weightx = 0.0;
          c.fill = GridBagConstraints.NONE;
          c.anchor = GridBagConstraints.LINE_END;
          c.ipadx = 5;
          add(monitor, c);
          currentMonitor = monitor;
          revalidate();
        }
      }

    });

    return monitor;
  }

  @Override
  public void remove(Component c) {
    super.remove(c);
    if (c.equals(currentMonitor)) {
      currentMonitor = null;
      revalidate();
      repaint();
    }
  }
}
