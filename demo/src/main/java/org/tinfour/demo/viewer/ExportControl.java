/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 03/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.viewer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Formatter;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Defines a JMenuItem to provide export controls under the File Menu. At
 * present, this class supports the image-export function, but in the future it
 * may include the save-grid function.
 */
public class ExportControl extends JMenuItem {

  private JFileChooser fileChooser;
  private ExportImageOptionsPanel exportImageOptions;
  final JMenu fileMenu;
  File currentDirectory = new File(".");

  private static final String[] validExtensions = {
    "PNG", "GIF", "JPG", "JPEG"
  };

  private String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  ExportControl(final JMenu fileMenu, final DataViewingPanel dvPanel) {
    super("Export Image");
    this.fileMenu = fileMenu;
    setToolTipText("Save current display to an image");
    addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (fileChooser == null) {
          fileChooser = makeFileChooser();
        }

        int returnVal = fileChooser.showSaveDialog(fileMenu);
        String[] extensions = new String[0];
        currentDirectory = fileChooser.getCurrentDirectory();
        if (returnVal != JFileChooser.APPROVE_OPTION) {
          return;
        }
        File file = fileChooser.getSelectedFile();
        javax.swing.filechooser.FileFilter filter
                = fileChooser.getFileFilter();
        if (filter instanceof FileNameExtensionFilter) {
          FileNameExtensionFilter fne
                  = (FileNameExtensionFilter) filter;
          extensions = fne.getExtensions();
        }
        String ext = getFileExtension(file);
        if (extensions.length == 0) {
          // the user selected the all-files filter.  see if the file
          // he selected has its own extension
          for (String s : validExtensions) {
            if (s.equalsIgnoreCase(ext)) {
              extensions = new String[1];
              extensions[0] = ext;
            }
          }
        }
        if (extensions.length == 0) {
          JOptionPane.showMessageDialog(fileChooser,
                  "To save an image, you must select or specify"
                  + "a valid image format (file extension)",
                  "Missing Format Extension",
                  JOptionPane.ERROR_MESSAGE);
          return;
        }

        boolean fileNameHasExtension = false;
        for (int i = 0; i < extensions.length; i++) {
          if (extensions[i].equalsIgnoreCase(ext)) {
            fileNameHasExtension = true;
          }
        }
        if (!fileNameHasExtension) {
          ext = extensions[0];
          String path = file.getPath();
          file = new File(path + "." + ext);
        }

        if (file.exists()) {
          // the file already exists.  confirm that the
          // user really wants to overwrite it.
          String name = file.getName();
          int n = JOptionPane.showConfirmDialog(fileChooser,
                  "Do you wish to overwrite existing file " + name + "?",
                  "Confirm replacement of existing file",
                  JOptionPane.YES_NO_OPTION);
          if (n != JOptionPane.YES_OPTION) {
            return;
          }
        }

        boolean transparentBackground
                = exportImageOptions.isTransparentBackgroundEnabled();
        boolean frameImage
                = exportImageOptions.isImageFrameEnabled();
        boolean writeWorldFile
                = exportImageOptions.isWorldFileEnabled();
        ExportImage eImage = dvPanel.getRenderedImage(
                transparentBackground, frameImage);
        if (eImage == null) {
          return;
        }

        try {
          ImageIO.write(eImage.bImage, ext, file);
        } catch (IOException ioex) {
          ioex.printStackTrace(System.out);
        }

        if (writeWorldFile) {
          String wExt = null;
          if (ext.equalsIgnoreCase("JPEG") || ext.equalsIgnoreCase("JPG")) {
            wExt = "jgw";
          } else if (ext.equalsIgnoreCase("PNG")) {
            wExt = "pgw";
          } else {
            wExt = "gfw";
          }
          String path = file.getPath();
          int i = path.lastIndexOf('.');
          path = path.substring(0, i) + wExt;
          file = new File(path);
          try (
                  FileOutputStream fos = new FileOutputStream(file)) {
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            Formatter fmt = new Formatter(bos, "US-ASCII");
            AffineTransform i2m = eImage.i2m;
            fmt.format("%f%n", i2m.getScaleX());
            fmt.format("0%n");
            fmt.format("0%n");
            fmt.format("%f%n", i2m.getScaleY());
            fmt.format("%f%n", i2m.getTranslateX());
            fmt.format("%f%n", i2m.getTranslateY());
            fmt.flush();
            bos.flush();
          } catch (IOException ioex) {
            ioex.printStackTrace(System.out);
          }
        }
      }
    }
    );
  }

  private JFileChooser makeFileChooser() {
    fileChooser = new JFileChooser();
    exportImageOptions = new ExportImageOptionsPanel();
    fileChooser.setAccessory(exportImageOptions);

    FileNameExtensionFilter jpegFilter = new FileNameExtensionFilter(
            "JPEG (.jpeg, .jpg)", "jpg", "jpeg"
    );

    FileNameExtensionFilter gifFilter = new FileNameExtensionFilter(
            "GIF (.gif)", "gif"
    );

    FileNameExtensionFilter pngFilter = new FileNameExtensionFilter(
            "PNG (.png)", "png"
    );

    fileChooser.addChoosableFileFilter(jpegFilter);
    fileChooser.addChoosableFileFilter(pngFilter);
    fileChooser.addChoosableFileFilter(gifFilter);
    fileChooser.setFileFilter(jpegFilter);
    fileChooser.setDialogTitle("Specify a path for exporting the image");

    return fileChooser;
  }

}
