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
package org.tinfour.demo.viewer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

class DataDropTargetListener implements DropTargetListener {

  static final long serialVersionUID = 1L;

  private final DataViewingPanel testPanel;

  DataDropTargetListener(DataViewingPanel testPanel) {
    this.testPanel = testPanel;
  }

  @Override
  public void dragEnter(DropTargetDragEvent dtde) {
    if (isDragAcceptable(dtde)) {
      dtde.acceptDrag(DnDConstants.ACTION_COPY);
    } else {
      dtde.rejectDrag();
    }
  }

  @Override
  public void dragOver(DropTargetDragEvent dtde) {
    if (isDragAcceptable(dtde)) {
      dtde.acceptDrag(DnDConstants.ACTION_COPY);
    } else {
      dtde.rejectDrag();
    }
  }

  @Override
  public void dropActionChanged(DropTargetDragEvent dtde) {
    // no action
  }

  @Override
  public void dragExit(DropTargetEvent dte) {
    // no action
  }

  @Override
  public void drop(DropTargetDropEvent dtde) {
    Transferable trans = dtde.getTransferable();
    if (trans.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      dtde.acceptDrop(DnDConstants.ACTION_COPY);
      try {
        Object transferObject
          = trans.getTransferData(DataFlavor.javaFileListFlavor);
        if (transferObject instanceof List<?>) {
          List<?> list = (List<?>) transferObject;
          for (Object object : list) {
            if (object instanceof File) {
              File file = (File) object;
              testPanel.loadModel(file);
            }
          }
        }
      } catch (UnsupportedFlavorException | IOException ex) {
        ex.printStackTrace(System.out);
      }
    }
    dtde.getDropTargetContext().dropComplete(true);

  }

  /**
   * Indicates whether the drag target is a valid file. For this demo, folders
   * are not considered acceptable targets.
   *
   * @param dtde a valid drag target event received from Java/Swing
   * @return true if the target is a file; false if it is anything else.
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  private boolean isDragAcceptable(DropTargetDragEvent dtde) {
    Transferable trans = dtde.getTransferable();
    if (trans.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      try {
        Object transferObject
          = trans.getTransferData(DataFlavor.javaFileListFlavor);
        if (transferObject instanceof List<?>) {
          List<?> list = (List<?>) transferObject;
          for (Object object : list) {
            if (object instanceof File) {
              File file = (File) object;
              if (file.isFile()) {
                String name = file.getName();
                int i = name.lastIndexOf('.');
                if (i >= name.length() - 1) {
                  return false; // no file extension
                }
                String ext = name.substring(i, name.length());
                if (".LAS".equalsIgnoreCase(ext)) {
                  return true;
                } else if (".LAZ".equalsIgnoreCase(ext)) {
                  return true;
                } else if (".TXT".equalsIgnoreCase(ext)) {
                  return true;
                } else if (".CSV".equalsIgnoreCase(ext)) {
                  return true;
                } else if (".SHP".equalsIgnoreCase(ext)) {
                  return true;
                } else if(".XYZ".equalsIgnoreCase(ext)){
                    return true;
                }
                return false;
              }
            }
          }
        }
      } catch (UnsupportedFlavorException | IOException ex) {
        ex.printStackTrace(System.out);
      }
    }
    return false;
  }

}
