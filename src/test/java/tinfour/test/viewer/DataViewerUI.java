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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.filechooser.FileFilter;
import tinfour.test.viewer.backplane.UnitSquareModel;
import tinfour.test.viewer.backplane.ViewOptions;

/**
 * Provides methods to construct the user interface and maintain
 * references and elements needed to coordinate component actions.
 */
class DataViewerUI {

  private JFrame frame;
  private DataViewingPanel dvPanel;
  private JDialog viewOptionsDialog;
  private BufferedImage appIconImage;
  private ViewOptions viewOptions;
  private JFileChooser fileChooser;
  private JDialog helpDialog;

  static String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  /**
   * Create the GUI and show it. For thread safety, this method should be
   * invoked from the event dispatch thread.
   */
  void createAndShowGUI() {
    viewOptions = new ViewOptions();
    //Create and set up the window.
    frame = new JFrame("Tinfour Viewer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    InputStream ins = DataViewerUI.class.getResourceAsStream(
      "resources/piece_of_cake_32_by_32.png");

    if (ins != null) {
      try {
        appIconImage = ImageIO.read(ins);
        frame.setIconImage(appIconImage);
      } catch (IOException dontCare) {

      }
    }

    dvPanel = new DataViewingPanel();
    dvPanel.setBackground(Color.white);
    dvPanel.setPreferredSize(new Dimension(650, 500));
    dvPanel.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

    JPanel dataPanel = new JPanel();
    dataPanel.setPreferredSize(new Dimension(250, 500));
    dataPanel.setLayout(new BorderLayout());

    JEditorPane reportPane = new JEditorPane();
    reportPane.setEditable(false);
    reportPane.setContentType("text/html");
    reportPane.setPreferredSize(new Dimension(250, 250));
    JScrollPane reportScrollPane = new JScrollPane(reportPane);

    JEditorPane queryPane = new JEditorPane();
    queryPane.setEditable(false);
    queryPane.setContentType("text/html");
    JScrollPane queryScrollPane = new JScrollPane(queryPane);

    JSplitPane infoSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reportScrollPane, queryScrollPane);

    dataPanel.add(infoSplit, BorderLayout.CENTER);

    JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dvPanel, dataPanel);
    mainSplit.setResizeWeight(0.95);

    //Add content to the window.
    frame.add(mainSplit, BorderLayout.CENTER);

    JPanel informationPanel = new JPanel();
    informationPanel.setLayout(new GridBagLayout());
    informationPanel.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

    StatusPanel statusPanel = new StatusPanel();
    statusPanel.setPreferredSize(new Dimension(800, 25));
    dvPanel.setStatusPanel(statusPanel);
    dvPanel.setReportPane(reportPane);
    dvPanel.setQueryPane(queryPane);

    GridBagConstraints c;

    c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.6;
    //c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.EAST;
    c.ipadx = 5;
    informationPanel.add(statusPanel, c);

    frame.add(informationPanel, BorderLayout.SOUTH);

    // add a place holder for now.  eventually may be the progress bar
    // and action notification.
    // add a place holder for now.  eventually may be the progress bar
    // and action notification.
    c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 0.0;
    c.ipadx = 5;
    JSeparator js1 = new JSeparator(JSeparator.VERTICAL);
    js1.setPreferredSize(new Dimension(2, 15));
    informationPanel.add(js1, c);

    c = new GridBagConstraints();
    c.gridx = 2;
    c.gridy = 0;
    c.weightx = 0.05;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.EAST;
    JLabel readoutLabel = new JLabel("  ");
    readoutLabel.setPreferredSize(new Dimension(250, 25));
    readoutLabel.setToolTipText("Displays data at mouse position");
    informationPanel.add(readoutLabel, c);
    dvPanel.setReadoutLabel(readoutLabel);

    // ---- set up main MenuBar -----------------------------
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = makeFileMenu();
    JMenu viewMenu = makeViewMenu();
    JMenu modelMenu = makeModelMenu();
    JMenu helpMenu = makeHelpMenu();

    // --- add menus to menu bar
    menuBar.add(fileMenu);
    menuBar.add(viewMenu);
    menuBar.add(modelMenu);
    menuBar.add(Box.createHorizontalGlue());
    menuBar.add(helpMenu);
    frame.setJMenuBar(menuBar);

    //Display the window.
    frame.pack();
    frame.setVisible(true);

  }

  JMenu makeFileMenu() {
    final JMenu fileMenu = new JMenu("File");
    JMenuItem newItem = new JMenuItem("New");
    newItem.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        dvPanel.clear();
        frame.setTitle("Tinfour Viewer");

      }

    });

    JMenuItem openItem = new JMenuItem("Open");
    openItem.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (fileChooser == null) {
          fileChooser = new JFileChooser();
          LasFileFilter lasFileFilter = new LasFileFilter();
          fileChooser.addChoosableFileFilter(lasFileFilter);
          TextFileFilter textFileFilter = new TextFileFilter();
          fileChooser.addChoosableFileFilter(textFileFilter);
          fileChooser.setFileFilter(lasFileFilter);
        }

        int returnVal = fileChooser.showOpenDialog(fileMenu);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fileChooser.getSelectedFile();
          if (file != null) {
            dvPanel.loadModel(file);
          }
        } else {
          System.err.println("Open command cancelled by user.");
        }
      }

    });
    JMenuItem exitItem = new JMenuItem("Exit");
    exitItem.setToolTipText("Exit application");
    exitItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        System.exit(0);
      }

    });

    fileMenu.add(newItem);
    fileMenu.add(openItem);
    fileMenu.add(new JSeparator(JSeparator.HORIZONTAL));
    fileMenu.add(exitItem);
    return fileMenu;
  }

  JMenu makeViewMenu() {
    // --- set up View menu -----------------------------
    JMenu viewMenu = new JMenu("View");
    JMenuItem optionsMenu = new JMenuItem("Styling and Presentation");
    optionsMenu.setToolTipText("Raise the view-options menu");
    optionsMenu.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (viewOptionsDialog == null) {
          ViewOptionsPanel viewOptionsPanel = new ViewOptionsPanel();
          viewOptionsDialog = new JDialog(frame,
            "Options for Viewing Data",
            false);
          viewOptionsDialog.setIconImage(appIconImage);
          viewOptionsDialog.setContentPane(viewOptionsPanel);
          viewOptionsDialog.setDefaultCloseOperation(
            JDialog.HIDE_ON_CLOSE);
          //viewOptionsDialog.addWindowListener(new WindowAdapter() {
          //  @Override
          //  public void windowClosing(WindowEvent we) {
          //
          //  }
          //
          //});

          viewOptionsPanel.setDataViewingPanel(dvPanel);
          viewOptionsPanel.setViewOptions(viewOptions);
          viewOptionsDialog.pack();
          viewOptionsDialog.setLocationRelativeTo(frame);
          viewOptionsDialog.setVisible(true);
        } else {
          viewOptionsDialog.setVisible(true);
          viewOptionsDialog.setLocationRelativeTo(frame);
        }
      }

    });

    final JCheckBoxMenuItem scaleEnabled = new JCheckBoxMenuItem("Show Scale");
    scaleEnabled.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        boolean showScale = scaleEnabled.isSelected();
        dvPanel.setShowScale(showScale);
      }

    });

    JMenuItem zoomToSource = new JMenuItem("Zoom to source");
    zoomToSource.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dvPanel.zoomToSource();
      }

    });
    viewMenu.add(optionsMenu);
    viewMenu.add(scaleEnabled);
    viewMenu.add(new JSeparator());
    viewMenu.add(zoomToSource);
    return viewMenu;
  }

  JMenu makeModelMenu() {
    JMenu modelMenu = new JMenu("Model");
    JMenuItem testModel = new JMenuItem("Load test model");
    testModel.setToolTipText("Loads simple model with one-by-one coordinate set");
    testModel.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        dvPanel.loadModel(new UnitSquareModel(150));
      }

    });
    modelMenu.add(testModel);
    return modelMenu;
  }

  void raiseHelpDialog() {
    if (helpDialog == null) {
      URL url = getClass().getResource("resources/chinese_noodles_32_by_32.png");
      String text = "<html><img src=" + url + "></img>";
      StringBuilder sb = new StringBuilder(2048);
      sb.append(text);
      try {
        InputStream ins = getClass().getResourceAsStream("resources/HelpInformation.txt");
        BufferedInputStream bins = new BufferedInputStream(ins);
        int c;
        while ((c = bins.read()) > 0) {
          sb.append((char) c);
        }
      } catch (IOException dontCare) {
        // no expected
      }
      sb.append("</html>");
      text = sb.toString();
      JEditorPane pane = new JEditorPane();
      pane.setEditable(false);
      pane.setContentType("text/html");
      pane.setText(text);

      pane.setPreferredSize(new Dimension(700, 600));
      JScrollPane scrollPane = new JScrollPane(pane);
      scrollPane.setPreferredSize(new Dimension(700, 600));
      helpDialog = new JDialog(frame,
        "Using Tinfour Viewer",
        false);
      helpDialog.setIconImage(appIconImage);
      helpDialog.setContentPane(scrollPane);
      helpDialog.setDefaultCloseOperation(
        JDialog.HIDE_ON_CLOSE);
      helpDialog.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent we) {
          // use the standard Swing window closing sequence
        }

      });

      helpDialog.pack();
      helpDialog.setLocationRelativeTo(frame);
      helpDialog.setVisible(true);
    } else {
      helpDialog.setVisible(true);
      helpDialog.setLocationRelativeTo(frame);
    }

  }

  JMenu makeHelpMenu() {

    JMenu helpModel = new JMenu("Help");
    JMenuItem showHelp = new JMenuItem("Show Help");
    showHelp.setToolTipText(
      "Show brief information about using this application");
    showHelp.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        raiseHelpDialog();
      }

    });
    helpModel.add(showHelp);
    return helpModel;
  }

  private class LasFileFilter extends FileFilter {

    @Override
    public String getDescription() {
      return "Lidar Files (LAS format only)";
    }

    @Override
    public boolean accept(File f) {
      if (f.isDirectory()) {
        return true;
      }
      String ext = getFileExtension(f);
      return ("LAS".equalsIgnoreCase(ext));
    }

  }

  private class TextFileFilter extends FileFilter {

    @Override
    public String getDescription() {
      return "Space or comma-delimited files (.txt or .csv)";
    }

    @Override
    public boolean accept(File f) {
      if (f.isDirectory()) {
        return true;
      }
      String ext = getFileExtension(f);
      return "TXT".equalsIgnoreCase(ext) || "CSV".equalsIgnoreCase(ext);
    }

  }

}
