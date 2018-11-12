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

import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import tinfour.test.utils.TestPalette;
import tinfour.test.viewer.backplane.LidarPointSelection;
import tinfour.test.viewer.backplane.ViewOptions;
import tinfour.test.viewer.backplane.ViewOptions.RasterInterpolationMethod;

/**
 * Provides UI for showing viewing options
 */
 @SuppressWarnings("PMD")  // due to non-compliant, automatically generated code
 class ViewOptionsPanel extends javax.swing.JPanel {
  private  static final long serialVersionUID=1L;
  private DataViewingPanel dvPanel;
  private ViewOptions view;
  private List<String> paletteNames;

  /**
   * Creates new form ViewOptions
   */
   ViewOptionsPanel() {
    this.view = new ViewOptions(); // defaults.
    initComponents();
    initializePalette();
  }

   void setDataViewingPanel(DataViewingPanel dvPanel) {
    this.dvPanel = dvPanel;
  }

   void setViewOptions(ViewOptions view) {
    this.view = new ViewOptions(view);
    transcribeViewToComponents();
  }

  final void initializePalette() {
    paletteNames = TestPalette.getPaletteNames();
    Icon[] icons = new Icon[paletteNames.size()];
    String[] names = new String[paletteNames.size()];
    Integer[] indices = new Integer[paletteNames.size()];
    for (int i = 0; i < icons.length; i++) {
      String s = paletteNames.get(i);
      names[i] = s;
      icons[i] = TestPalette.getIconByName(s, 96, 20);
      indices[i] = i;
    }

    DefaultComboBoxModel<Integer> dcb = new DefaultComboBoxModel<>(indices);

    paletteComboBox.setModel(dcb);
    paletteComboBox.setRenderer(new PaletteRenderer(names, icons));
  }

  private void setDouble(JTextField field, String fmt, double v) {
    // if the value is NaN, just leave field alone
    if (!Double.isNaN(v)) {
      if (Math.floor(v + 1.0e-6) == (int) v) {
        field.setText(Integer.toString((int) v));
      } else {
        field.setText(String.format(fmt, v));
      }
    }
  }

  /**
   * Set the component values based on the view settings
   */
  void transcribeViewToComponents() {
    int paletteIndex = 0;
    String s = view.getPaletteName();
    List<String> names = TestPalette.getPaletteNames();
    for (int i = 0; i < names.size(); i++) {
      if (names.get(i).equalsIgnoreCase(s)) {
        paletteIndex = i;
        break;
      }
    }
    paletteComboBox.setSelectedIndex(paletteIndex);
    int fgOption = view.getForegroundBackgroundOption();
    blackOnWhiteRadioButton.setSelected(fgOption == 0);
    whiteOnBlackRadioButton.setSelected(fgOption == 1);

    boolean useRangeOfValues = view.useRangeOfValuesForPalette();
    double[] d = view.getRangeForPalette();
    setDouble(paletteAssignMinRange, "%5.3f", d[0]);
    setDouble(paletteAssignMaxRange, "%5.3f", d[1]);
    paletteAssignEntireDataSet.setSelected(!useRangeOfValues);
    paletteAssignFixedRange.setSelected(useRangeOfValues);

    wireframeCheckBox.setSelected(view.isWireframeSelected());
    ViewOptions.SampleThinning wThin = view.getWireframeSampleThinning();
    switch (wThin) {
      case Medium:
        wireframeSampleThinningComboBox.setSelectedIndex(0);
        break;
      case Fine:
        wireframeSampleThinningComboBox.setSelectedIndex(1);
        break;
      case ExtraFine:
        wireframeSampleThinningComboBox.setSelectedIndex(2);
        break;
      default:
        wireframeSampleThinningComboBox.setSelectedIndex(0);
    }
    wireframeColorUsingForeground.setSelected(!view.usePaletteForWireframe());
    wireframeColorUsingPalette.setSelected(view.usePaletteForWireframe());
    edgesCheckBox.setSelected(view.isEdgeRenderingSelected());
    verticesCheckBox.setSelected(view.isVertexRenderingSelected());
    labelsCheckBox.setSelected(view.isLabelRenderingSelected());
    s = view.getFieldForLabel();
    if ("Z".equalsIgnoreCase(s)) {
      labelFieldComboBox.setSelectedItem(0);
    } else if ("ID".equalsIgnoreCase(s)) {
      labelFieldComboBox.setSelectedItem(1);
    } else {
      labelFieldComboBox.setSelectedItem(0);
    }

    constraintsCheckBox.setSelected(view.isConstraintRenderingSelected());
    constraintsColorButton.setColor(view.getConstraintColor());

    rasterCheckBox.setSelected(view.isRasterSelected());
    hillshadeCheckBox.setSelected(view.isHillshadeSelected());
    setDouble(azimuthTextField, "%3.1f", view.getHillshadeAzimuth());
    setDouble(elevationTextField, "%3.1f", view.getHillshadeElevation());
    setDouble(ambientTextField, "%3.1f", view.getHillshadeAmbient());
    fullResolutionGridCheckbox.setSelected(view.isFullResolutionGridSelected());
            lidarGroundPointsButton.setSelected(false);
        lidarFirstReturnButton.setSelected(false);
        lidarAllPointsButton.setSelected(false);
    switch(view.getLidarPointSelection()){
      case GroundPoints:
        lidarGroundPointsButton.setSelected(true);
        break;
      case FirstReturn:
        lidarFirstReturnButton.setSelected(true);
        break;
      case LastReturn:
        lidarLastReturnButton.setSelected(true);
        break;
      case AllPoints:
        lidarAllPointsButton.setSelected(false);
        break;
      default:
        lidarGroundPointsButton.setSelected(true);
    }

    int index = view.getRasterInterpolationMethod().ordinal();
    this.rasterMethodComboBox.setSelectedIndex(index);
  }

  private double extractField(JTextField field, double value) {
    double d = extractDouble(field);
    if (Double.isNaN(d)) {
      return value;
    }
    return d;
  }

  private double extractDouble(JTextField field) {
    try {
      String s = field.getText();
      if (s != null) {
        s = s.trim();
        if (!s.isEmpty()) {
          return Double.parseDouble(s.trim());
        }
      }
    } catch (NumberFormatException nex) {
    }
    return Double.NaN;
  }

  /**
   * Set the view parameters base on component states.
   */
  boolean transcribeComponentsToView() {
    boolean badInput = false;

    int paletteIndex = paletteComboBox.getSelectedIndex();
    String paletteName = paletteNames.get(paletteIndex);
    view.setPaletteName(paletteName);
    if (blackOnWhiteRadioButton.isSelected()) {
      view.setForegroundBackgroundOption(0);
      view.setForeground(Color.black);
      view.setBackground(Color.white);
    } else {
      view.setForegroundBackgroundOption(1);
      view.setForeground(Color.white);
      view.setBackground(Color.black);
    }

    // the handling of the range of values entry is not production
    // quality at all.  simply ignore anything that isn't valid.
    boolean useRangeOfValues = paletteAssignFixedRange.isSelected();

    double a = extractDouble(paletteAssignMinRange);
    double b = extractDouble(paletteAssignMaxRange);
    double[] d = new double[2];
    d[0] = a;
    d[1] = b;
    view.setRangeForPalette(d);
    if (useRangeOfValues) {
      if (Double.isNaN(a) || Double.isNaN(b)) {
        JOptionPane.showMessageDialog(paletteAssignFixedRange, "Invalid entry for specified palette range", "Bad Numeric Entry", JOptionPane.ERROR_MESSAGE);
        useRangeOfValues = false;
        badInput = true;
      } else if (Math.abs(a-b)<Math.abs(a+b)/1.0e+6) {
        JOptionPane.showMessageDialog(paletteAssignFixedRange, "Palette range entries must be distinct", "Values not distinct", JOptionPane.ERROR_MESSAGE);
        useRangeOfValues = false;
        badInput = true;
      }
    }
    view.setUseRangeOfValuesForPalette(useRangeOfValues);

    view.setWireframeSelected(wireframeCheckBox.isSelected());
    int index = wireframeSampleThinningComboBox.getSelectedIndex();
    switch (index) {
      case 0:
        view.setWireframeSampleThinning(ViewOptions.SampleThinning.Medium);
        break;
      case 1:
        view.setWireframeSampleThinning(ViewOptions.SampleThinning.Fine);
        break;
      case 2:
         view.setWireframeSampleThinning(ViewOptions.SampleThinning.ExtraFine);
        break;
      default:
        view.setWireframeSampleThinning(ViewOptions.SampleThinning.Medium);
    }
    view.setUsePaletteForWireframe(wireframeColorUsingPalette.isSelected());
    view.setEdgeRenderingSelected(edgesCheckBox.isSelected());
    view.setVertexRenderingSelected(verticesCheckBox.isSelected());
    view.setLabelRenderingSelected(labelsCheckBox.isSelected());
    String labelField = "Z";
    if (labelFieldComboBox.getSelectedIndex() == 1) {
      labelField = "ID";
    }
    view.setFieldForLabel(labelField);

    view.setConstraintRenderingSelected(constraintsCheckBox.isSelected());
    view.setConstraintColor(constraintsColorButton.colorChoice);


    view.setRasterSelected(rasterCheckBox.isSelected());
    view.setHillshadeSelected(hillshadeCheckBox.isSelected());

    view.setHillshadeAzimuth(extractField(azimuthTextField, 135));
    view.setHillshadeElevation(extractField(elevationTextField, 60));
    view.setHillshadeAmbient(extractField(ambientTextField, 135));

    view.setFullResolutionGridSelected(fullResolutionGridCheckbox.isSelected());
    if(lidarGroundPointsButton.isSelected()){
      view.setLidarPointSelection(LidarPointSelection.GroundPoints);
    }else if(lidarFirstReturnButton.isSelected()){
      view.setLidarPointSelection(LidarPointSelection.FirstReturn);
    }else if(lidarLastReturnButton.isSelected()){
      view.setLidarPointSelection(LidarPointSelection.LastReturn);
    }else{
      view.setLidarPointSelection(LidarPointSelection.AllPoints);
    }



      index = rasterMethodComboBox.getSelectedIndex();
      RasterInterpolationMethod rim = RasterInterpolationMethod.values()[index];
      view.setRasterInterpolationMethod(rim);

    //transcribeViewToComponents(); // just a diagnostic
    return badInput;
  }

  private void hideDialog() {
    Component c = this;
    do {
      c = c.getParent();
    } while (!(c instanceof JDialog));
    JDialog jd = (JDialog) c;
    jd.setVisible(false);

  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    paletteAssignButtonGroup = new javax.swing.ButtonGroup();
    lidarPointSelectionGroup = new javax.swing.ButtonGroup();
    wireframeColorButtonGroup = new javax.swing.ButtonGroup();
    foregroundBackgroundButtonGroup = new javax.swing.ButtonGroup();
    optionsPanel = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    jLabel2 = new javax.swing.JLabel();
    whiteOnBlackRadioButton = new javax.swing.JRadioButton();
    blackOnWhiteRadioButton = new javax.swing.JRadioButton();
    jLabel4 = new javax.swing.JLabel();
    paletteComboBox = new javax.swing.JComboBox<>();
    paletteAssignEntireDataSet = new javax.swing.JRadioButton();
    paletteAssignFixedRange = new javax.swing.JRadioButton();
    paletteAssignMinRange = new javax.swing.JTextField();
    jLabel5 = new javax.swing.JLabel();
    paletteAssignMaxRange = new javax.swing.JTextField();
    wireframeCheckBox = new javax.swing.JCheckBox();
    edgesCheckBox = new javax.swing.JCheckBox();
    verticesCheckBox = new javax.swing.JCheckBox();
    labelsCheckBox = new javax.swing.JCheckBox();
    labelFieldComboBox = new javax.swing.JComboBox<>();
    rasterCheckBox = new javax.swing.JCheckBox();
    hillshadeCheckBox = new javax.swing.JCheckBox();
    jLabel6 = new javax.swing.JLabel();
    azimuthTextField = new javax.swing.JTextField();
    jLabel7 = new javax.swing.JLabel();
    elevationTextField = new javax.swing.JTextField();
    jLabel8 = new javax.swing.JLabel();
    ambientTextField = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    lidarGroundPointsButton = new javax.swing.JRadioButton();
    lidarAllPointsButton = new javax.swing.JRadioButton();
    jLabel10 = new javax.swing.JLabel();
    wireframeColorUsingForeground = new javax.swing.JRadioButton();
    wireframeColorUsingPalette = new javax.swing.JRadioButton();
    fullResolutionGridCheckbox = new javax.swing.JCheckBox();
    jLabel3 = new javax.swing.JLabel();
    wireframeSampleThinningComboBox = new javax.swing.JComboBox<>();
    lidarFirstReturnButton = new javax.swing.JRadioButton();
    rasterMethodComboBox = new javax.swing.JComboBox<>();
    lidarLastReturnButton = new javax.swing.JRadioButton();
    constraintsCheckBox = new javax.swing.JCheckBox();
    constraintsColorButton = new tinfour.test.viewer.ColorButton();
    actionsPanel = new javax.swing.JPanel();
    applyButton = new javax.swing.JButton();
    cancelButton = new javax.swing.JButton();
    okayButton = new javax.swing.JButton();

    optionsPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

    jLabel1.setText("Color Options");

    jLabel2.setText("Foreground and Background Colors");

    foregroundBackgroundButtonGroup.add(whiteOnBlackRadioButton);
    whiteOnBlackRadioButton.setText("White on Black");
    whiteOnBlackRadioButton.setToolTipText("Select white foreground objects on a black background");
    whiteOnBlackRadioButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        whiteOnBlackRadioButtonActionPerformed(evt);
      }
    });

    foregroundBackgroundButtonGroup.add(blackOnWhiteRadioButton);
    blackOnWhiteRadioButton.setSelected(true);
    blackOnWhiteRadioButton.setText("Black on White");
    blackOnWhiteRadioButton.setToolTipText("Select black foreground objects on a white background");

    jLabel4.setText("Palette");

    paletteComboBox.setToolTipText("Select palette for rendering rasters");

    paletteAssignButtonGroup.add(paletteAssignEntireDataSet);
    paletteAssignEntireDataSet.setSelected(true);
    paletteAssignEntireDataSet.setText("Use Range of  Data Set");
    paletteAssignEntireDataSet.setToolTipText("Assign colors based on range of entire data set");

    paletteAssignButtonGroup.add(paletteAssignFixedRange);
    paletteAssignFixedRange.setText("Specified Range");
    paletteAssignFixedRange.setToolTipText("Assign colors based on specified range");

    paletteAssignMinRange.setColumns(6);
    paletteAssignMinRange.setText("0.0");
    paletteAssignMinRange.setToolTipText("Minimum value for range");

    jLabel5.setText("to");

    paletteAssignMaxRange.setColumns(6);
    paletteAssignMaxRange.setText("8000.0");
    paletteAssignMaxRange.setToolTipText("Maximum value for range");

    wireframeCheckBox.setSelected(true);
    wireframeCheckBox.setText("Wireframe");
    wireframeCheckBox.setToolTipText("Check to enable display of wireframe or point data");

    edgesCheckBox.setSelected(true);
    edgesCheckBox.setText("Edges");
    edgesCheckBox.setToolTipText("Enable depiction of edges from TIN");

    verticesCheckBox.setSelected(true);
    verticesCheckBox.setText("Vertices");
    verticesCheckBox.setToolTipText("Enable rendering of vertices from model");

    labelsCheckBox.setText("Labels");
    labelsCheckBox.setToolTipText("Enable labeling of vertices");

    labelFieldComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Z", "ID" }));
    labelFieldComboBox.setToolTipText("Select field for labeling");

    rasterCheckBox.setText("Raster");
    rasterCheckBox.setToolTipText("Enable color-coded raster background using palette");

    hillshadeCheckBox.setText("Hillshade");
    hillshadeCheckBox.setToolTipText("Enable rendering of hillshade using z value");

    jLabel6.setText("Azimuth");

    azimuthTextField.setColumns(5);
    azimuthTextField.setText("125");
    azimuthTextField.setToolTipText("Azimuth for light source in degrees");

    jLabel7.setText("Elevation");

    elevationTextField.setColumns(5);
    elevationTextField.setText("60");
    elevationTextField.setToolTipText("Elevation for light source 15 to 90 degrees");

    jLabel8.setText("Ambient");

    ambientTextField.setColumns(5);
    ambientTextField.setText("25");
    ambientTextField.setToolTipText("Percent ambient light 0 to 60 percent");

    jLabel9.setText("Lidar Options (LAS files only)");

    lidarPointSelectionGroup.add(lidarGroundPointsButton);
    lidarGroundPointsButton.setSelected(true);
    lidarGroundPointsButton.setText("Ground Points Only");
    lidarGroundPointsButton.setToolTipText("Select lidar ground points only");

    lidarPointSelectionGroup.add(lidarAllPointsButton);
    lidarAllPointsButton.setText("All samples");
    lidarAllPointsButton.setToolTipText("Use all lidar samples regardless of classification");

    jLabel10.setText("Color Options");

    wireframeColorButtonGroup.add(wireframeColorUsingForeground);
    wireframeColorUsingForeground.setSelected(true);
    wireframeColorUsingForeground.setText("Use Foreground");
    wireframeColorUsingForeground.setToolTipText("Use foreground color for wireframe rendering");

    wireframeColorButtonGroup.add(wireframeColorUsingPalette);
    wireframeColorUsingPalette.setText("Use Palette");
    wireframeColorUsingPalette.setToolTipText("Use palette for wireframe rendering");

    fullResolutionGridCheckbox.setText("Build Grid with Full Resolution (Use with caution)");
    fullResolutionGridCheckbox.setToolTipText("Optionally selects non-thinned sample set for raster grid");
    fullResolutionGridCheckbox.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fullResolutionGridCheckboxActionPerformed(evt);
      }
    });

    jLabel3.setText("Sample Thinning");

    wireframeSampleThinningComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " Medium", "Fine", "Extra Fine" }));

    lidarPointSelectionGroup.add(lidarFirstReturnButton);
    lidarFirstReturnButton.setText("First Returns Only");
    lidarFirstReturnButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        lidarFirstReturnButtonActionPerformed(evt);
      }
    });

    rasterMethodComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " Natural Neighbor Interpolation", "Geographically Weighted Regression", "Triangular Facets" }));
    rasterMethodComboBox.setToolTipText("Select method for raster interpolation");

    lidarPointSelectionGroup.add(lidarLastReturnButton);
    lidarLastReturnButton.setText("Last Returns Only");

    constraintsCheckBox.setText("Constraints");
    constraintsCheckBox.setToolTipText("Check to enable display of constraints");

    constraintsColorButton.setToolTipText("Select color for rendering constraints");
    constraintsColorButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
    constraintsColorButton.setMaximumSize(new java.awt.Dimension(20, 20));
    constraintsColorButton.setMinimumSize(new java.awt.Dimension(20, 20));
    constraintsColorButton.setPreferredSize(new java.awt.Dimension(20, 20));

    javax.swing.GroupLayout optionsPanelLayout = new javax.swing.GroupLayout(optionsPanel);
    optionsPanel.setLayout(optionsPanelLayout);
    optionsPanelLayout.setHorizontalGroup(
      optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(optionsPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addComponent(constraintsCheckBox)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(constraintsColorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addComponent(fullResolutionGridCheckbox)
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addComponent(hillshadeCheckBox)
            .addGap(18, 18, 18)
            .addComponent(jLabel6)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(azimuthTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(jLabel7)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(elevationTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(18, 18, 18)
            .addComponent(jLabel8)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(ambientTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addComponent(jLabel1)
          .addComponent(wireframeCheckBox)
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addComponent(rasterCheckBox)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(rasterMethodComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addGap(21, 21, 21)
            .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addComponent(edgesCheckBox)
              .addGroup(optionsPanelLayout.createSequentialGroup()
                .addComponent(verticesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(labelsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(labelFieldComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
              .addGroup(optionsPanelLayout.createSequentialGroup()
                .addComponent(jLabel3)
                .addGap(18, 18, 18)
                .addComponent(wireframeSampleThinningComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
          .addComponent(jLabel9)
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addGap(10, 10, 10)
            .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addComponent(lidarAllPointsButton)
              .addComponent(lidarGroundPointsButton)
              .addComponent(lidarFirstReturnButton)
              .addComponent(lidarLastReturnButton)))
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addGap(10, 10, 10)
            .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addGroup(optionsPanelLayout.createSequentialGroup()
                .addComponent(jLabel4)
                .addGap(34, 34, 34)
                .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addComponent(paletteAssignEntireDataSet)
                  .addComponent(paletteComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGroup(optionsPanelLayout.createSequentialGroup()
                    .addComponent(paletteAssignFixedRange)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(paletteAssignMinRange, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                      .addComponent(jLabel10)
                      .addGroup(optionsPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                          .addComponent(wireframeColorUsingPalette)
                          .addComponent(wireframeColorUsingForeground)))
                      .addGroup(optionsPanelLayout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(paletteAssignMaxRange, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))))
              .addComponent(jLabel2)
              .addComponent(blackOnWhiteRadioButton)
              .addComponent(whiteOnBlackRadioButton))))
        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );
    optionsPanelLayout.setVerticalGroup(
      optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(optionsPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jLabel1)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(jLabel2)
        .addGap(1, 1, 1)
        .addComponent(blackOnWhiteRadioButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(whiteOnBlackRadioButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(jLabel4)
          .addComponent(paletteComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(paletteAssignEntireDataSet)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(paletteAssignFixedRange)
          .addComponent(paletteAssignMinRange, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel5)
          .addComponent(paletteAssignMaxRange, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(3, 3, 3)
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
          .addComponent(constraintsCheckBox)
          .addComponent(constraintsColorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(wireframeCheckBox)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addComponent(jLabel10)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(wireframeColorUsingForeground)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(wireframeColorUsingPalette)
            .addGap(5, 5, 5))
          .addGroup(optionsPanelLayout.createSequentialGroup()
            .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
              .addComponent(jLabel3)
              .addComponent(wireframeSampleThinningComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(edgesCheckBox)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
              .addComponent(verticesCheckBox)
              .addComponent(labelsCheckBox)
              .addComponent(labelFieldComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(rasterCheckBox)
          .addComponent(rasterMethodComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(hillshadeCheckBox)
          .addComponent(jLabel6)
          .addComponent(azimuthTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel7)
          .addComponent(elevationTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel8)
          .addComponent(ambientTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(fullResolutionGridCheckbox)
        .addGap(18, 18, 18)
        .addComponent(jLabel9)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(lidarGroundPointsButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(lidarFirstReturnButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(lidarLastReturnButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(lidarAllPointsButton)
        .addContainerGap(17, Short.MAX_VALUE))
    );

    applyButton.setText("Apply");
    applyButton.setToolTipText("Apply settings");
    applyButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        applyButtonActionPerformed(evt);
      }
    });

    cancelButton.setText("Cancel");
    cancelButton.setToolTipText("Close dialog without applying settings");
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });

    okayButton.setText("OK");
    okayButton.setToolTipText("Apply settings and close dialog");
    okayButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        okButtonActionPerformed(evt);
      }
    });

    javax.swing.GroupLayout actionsPanelLayout = new javax.swing.GroupLayout(actionsPanel);
    actionsPanel.setLayout(actionsPanelLayout);
    actionsPanelLayout.setHorizontalGroup(
      actionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, actionsPanelLayout.createSequentialGroup()
        .addContainerGap(208, Short.MAX_VALUE)
        .addComponent(okayButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(cancelButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(applyButton)
        .addContainerGap())
    );

    actionsPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {applyButton, cancelButton, okayButton});

    actionsPanelLayout.setVerticalGroup(
      actionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, actionsPanelLayout.createSequentialGroup()
        .addGap(0, 0, Short.MAX_VALUE)
        .addGroup(actionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(okayButton)
          .addComponent(cancelButton)
          .addComponent(applyButton)))
    );

    actionsPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {applyButton, cancelButton, okayButton});

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(actionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
          .addComponent(optionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addContainerGap())
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(optionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(actionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap())
    );
  }// </editor-fold>//GEN-END:initComponents

  private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed

    boolean badInput = transcribeComponentsToView();
    if (badInput) {
      return;
    }
    hideDialog();
    if (dvPanel != null) {
      dvPanel.setViewOptions(new ViewOptions(view));
    }
  }//GEN-LAST:event_okButtonActionPerformed

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
    transcribeViewToComponents();
    hideDialog();
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
    boolean badInput = transcribeComponentsToView();
    if (badInput) {
      return;
    }
    if (dvPanel != null) {
      dvPanel.setViewOptions(new ViewOptions(view));
    }
  }//GEN-LAST:event_applyButtonActionPerformed

  private void whiteOnBlackRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_whiteOnBlackRadioButtonActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_whiteOnBlackRadioButtonActionPerformed

  private void fullResolutionGridCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullResolutionGridCheckboxActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_fullResolutionGridCheckboxActionPerformed

  private void lidarFirstReturnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lidarFirstReturnButtonActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_lidarFirstReturnButtonActionPerformed

  class PaletteRenderer extends JLabel implements ListCellRenderer<Integer> {
    private  static final long serialVersionUID=1L;
    String[] names;
    Icon[] icons;

    PaletteRenderer(String[] names, Icon[] icons) {
      this.names = names;
      this.icons = icons;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Integer> list, Integer value, int index, boolean isSelected, boolean cellHasFocus) {
      //Get the selected index. (The index param isn't
      //always valid, so just use the value.)
      int selectedIndex = (value).intValue();

      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }

      //Set the icon and text.  If icon was null, say so.
      Icon icon = icons[selectedIndex];
      String name = names[selectedIndex];
      setIcon(icon);
      if (icon != null) {
        setText(null);
        setToolTipText("Select the palette " + name);
        setFont(list.getFont());
      }
      return this;
    }

  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel actionsPanel;
  private javax.swing.JTextField ambientTextField;
  private javax.swing.JButton applyButton;
  private javax.swing.JTextField azimuthTextField;
  private javax.swing.JRadioButton blackOnWhiteRadioButton;
  private javax.swing.JButton cancelButton;
  private javax.swing.JCheckBox constraintsCheckBox;
  private tinfour.test.viewer.ColorButton constraintsColorButton;
  private javax.swing.JCheckBox edgesCheckBox;
  private javax.swing.JTextField elevationTextField;
  private javax.swing.ButtonGroup foregroundBackgroundButtonGroup;
  private javax.swing.JCheckBox fullResolutionGridCheckbox;
  private javax.swing.JCheckBox hillshadeCheckBox;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel10;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JLabel jLabel5;
  private javax.swing.JLabel jLabel6;
  private javax.swing.JLabel jLabel7;
  private javax.swing.JLabel jLabel8;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JComboBox<String> labelFieldComboBox;
  private javax.swing.JCheckBox labelsCheckBox;
  private javax.swing.JRadioButton lidarAllPointsButton;
  private javax.swing.JRadioButton lidarFirstReturnButton;
  private javax.swing.JRadioButton lidarGroundPointsButton;
  private javax.swing.JRadioButton lidarLastReturnButton;
  private javax.swing.ButtonGroup lidarPointSelectionGroup;
  private javax.swing.JButton okayButton;
  private javax.swing.JPanel optionsPanel;
  private javax.swing.ButtonGroup paletteAssignButtonGroup;
  private javax.swing.JRadioButton paletteAssignEntireDataSet;
  private javax.swing.JRadioButton paletteAssignFixedRange;
  private javax.swing.JTextField paletteAssignMaxRange;
  private javax.swing.JTextField paletteAssignMinRange;
  private javax.swing.JComboBox<Integer> paletteComboBox;
  private javax.swing.JCheckBox rasterCheckBox;
  private javax.swing.JComboBox<String> rasterMethodComboBox;
  private javax.swing.JCheckBox verticesCheckBox;
  private javax.swing.JRadioButton whiteOnBlackRadioButton;
  private javax.swing.JCheckBox wireframeCheckBox;
  private javax.swing.ButtonGroup wireframeColorButtonGroup;
  private javax.swing.JRadioButton wireframeColorUsingForeground;
  private javax.swing.JRadioButton wireframeColorUsingPalette;
  private javax.swing.JComboBox<String> wireframeSampleThinningComboBox;
  // End of variables declaration//GEN-END:variables
}
