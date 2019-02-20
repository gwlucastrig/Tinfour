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
 * 01/2017  G. Lucas     Created
 *
 * Notes:
 *   Soon to be completely revamped
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.demo.utils.cdt.ConstraintLoader;
import org.tinfour.gis.utils.ConstraintReaderShapefile;

class MvTaskLoadConstraints implements IModelViewTask {

  private final BackplaneManager backplaneManager;
  private final MvComposite mvComposite;
  private final File constraintsFile;
  private final int taskIndex;
  private boolean isCancelled;
  private final String optionString;

  MvTaskLoadConstraints(
    BackplaneManager backplaneManager,
    File file,
    MvComposite mvComposite,
    int taskIndex,
    String optionString) {
    this.mvComposite = mvComposite;
    this.constraintsFile = file;
    this.taskIndex = taskIndex;
    this.backplaneManager = backplaneManager;
    this.optionString = optionString;
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
    try {
      IMonitorWithCancellation monitor
              = backplaneManager.getProgressMonitor(taskIndex);
      monitor.postMessage("Loading constraints from " + constraintsFile.getName());
      List<IConstraint> constraints = null;

      if ("shp".equalsIgnoreCase(getFileExtension(constraintsFile))) {
        ConstraintReaderShapefile reader
                = new ConstraintReaderShapefile(constraintsFile);
        if (this.optionString != null) {
          reader.setDbfFieldForZ(optionString);
        }
        reader.setCoordinateTransform(model.getCoordinateTransform());
        constraints = reader.read();
      } else {
        ConstraintLoader loader = new ConstraintLoader();
        loader.setCoordinateTransform(model.getCoordinateTransform());
        constraints = loader.readConstraintsFile(constraintsFile);
      }
      model.addConstraints(constraintsFile, constraints);
      monitor.reportDone();
      backplaneManager.postModelRefreshCompleted(this, mvComposite);
    } catch (IOException ioex) {
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
  public boolean isRenderingTask(){
    return  false;
  }

    String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }
}
