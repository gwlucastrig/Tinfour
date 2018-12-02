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

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * A simple application that permits the user to view an image with pan-and-zoom
 * capability. The original intent of this implementation was to develop an
 * approach to image transformation and rendering.
 */
public final class TinfourViewerMain {

  private static DataViewerUI dataViewerUI;

  /**
   * The application main method.
   *
   * @param args a valid set of arguments (not currently used)
   */
  public static void main(String[] args) {
    {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long maxVertices = (long) (0.75 * maxMemory / 124.0);
      System.out.format(
        "Tinfour Viewer launched with max memory %3.1f megabytes.%n",
        maxMemory / (1024.0 * 1024.0));
      System.out.format(
        "Estimated maximum number of vertices is %d%n", maxVertices);
      System.out.format(
        "depending on whether full-resolution options are used.%n");
      System.out.flush();
    }
    try {
      // Set System L&F
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException ex) {
      System.err.println("exception: " + ex);
    } catch (InstantiationException ex) {
      System.err.println("exception: " + ex);
    } catch (IllegalAccessException ex) {
      System.err.println("exception: " + ex);
    } catch (UnsupportedLookAndFeelException ex) {
      System.err.println("exception: " + ex);
    }

    dataViewerUI = new DataViewerUI();

    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        //Turn off metal's use of bold fonts
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        dataViewerUI.createAndShowGUI();
      }

    });
  }

  /**
   * A private constructor to deter applications from making unnecessary
   * instantiations of this class.
   */
  private TinfourViewerMain() {

  }

}
