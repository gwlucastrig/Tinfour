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

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import tinfour.test.utils.TestPalette;

/**
 * Provides specifications and options for rendering a model.
 */
public class ViewOptions {

  private int foregroundBackgroundOption;
  private Color foreground = Color.black;
  private Color background = Color.white;
  private String paletteName;
  private boolean useRangeOfValuesForPalette;
  private double[] rangeForPalette;

  // wireframe options
  private boolean isWireframeSelected;
  private boolean isThinningSelected;
  private boolean isEdgeRenderingSelected;
  private boolean isVertexRenderingSelected;
  private boolean isLabelRenderingSelected;
  private String fieldForLabel;
  private boolean usePaletteForWireframe;

  private boolean isRasterSelected;
  private boolean isHillshadeSelected;
  private double hillshadeAzimuth;
  private double hillshadeElevation;
  private double hillshadeAmbient;

  private boolean isLidarGroundPointsOptionSelected;
  private boolean isFullResolutionGridSelected;

  /**
   * Create a new instance copying the state data from the supplied reference
   * @param v a valid instance
   */
  public ViewOptions(ViewOptions v) {
    // general color options
    foregroundBackgroundOption = v.foregroundBackgroundOption;
    foreground = v.foreground;
    background = v.background;
    paletteName = v.paletteName;
    useRangeOfValuesForPalette = v.useRangeOfValuesForPalette;
    rangeForPalette = Arrays.copyOf(v.rangeForPalette, 2);

    // wireframe options
    isWireframeSelected = v.isWireframeSelected;
    isThinningSelected = v.isThinningSelected;
    isEdgeRenderingSelected = v.isEdgeRenderingSelected;
    isVertexRenderingSelected = v.isVertexRenderingSelected;
    isLabelRenderingSelected = v.isLabelRenderingSelected;
    fieldForLabel = v.fieldForLabel;
    usePaletteForWireframe = v.usePaletteForWireframe;

    // raster and hillshade options
    isRasterSelected = v.isRasterSelected;
    isHillshadeSelected = v.isHillshadeSelected;
    hillshadeAzimuth = v.hillshadeAzimuth;
    hillshadeElevation = v.hillshadeElevation;
    hillshadeAmbient = v.hillshadeAmbient;
    isFullResolutionGridSelected = v.isFullResolutionGridSelected;

    isLidarGroundPointsOptionSelected = v.isLidarGroundPointsOptionSelected;
  }

  /**
   * Create a new instance with default parameters.
   */
  public ViewOptions() {
    // general color options
    List<String> paletteNames = TestPalette.getPaletteNames();
    paletteName = paletteNames.get(0);
    foregroundBackgroundOption = 0;
    foreground = Color.black;
    background = Color.white;
    useRangeOfValuesForPalette = false;
    rangeForPalette = new double[2];
    rangeForPalette[0] = 0;
    rangeForPalette[1] = 8000;

    // wireframe options
    isWireframeSelected = true;
    isThinningSelected = true;
    isEdgeRenderingSelected = true;
    isVertexRenderingSelected = true;
    isLabelRenderingSelected = true;
    fieldForLabel = "Z";
    usePaletteForWireframe = false;

    // raster options
    isRasterSelected = true;
    isHillshadeSelected = false;
    hillshadeAzimuth = 135;  // degrees counterclockwise from x axis
    hillshadeElevation = 45; // degrees
    hillshadeAmbient = 25; // percent

    isLidarGroundPointsOptionSelected = true;
  }

  /**
   * Indicates if the colors are set to black on white (value of zero)
   * or white on black (value of 1)
   *
   * @return the foregroundBackgroundOption
   */
  public int getForegroundBackgroundOption() {
    return foregroundBackgroundOption;
  }

  /**
   * Set the foregroundBackgroundOption
   *
   * @param foregroundBackgroundOption the foregroundBackgroundOption to set
   */
  public void setForegroundBackgroundOption(int foregroundBackgroundOption) {
    this.foregroundBackgroundOption = foregroundBackgroundOption;
  }

  /**
   * Gets the foreground color for rendering
   *
   * @return a valid color instance
   */
  public Color getForeground() {
    return foreground;
  }

  /**
   * Set the foreground color for rendering
   *
   * @param foreground a valid color instance
   */
  public void setForeground(Color foreground) {
    this.foreground = foreground;
  }

  /**
   * Gets the background color for rendering
   *
   * @return a valid color instance
   */
  public Color getBackground() {
    return background;
  }

  /**
   * Set the background color for rendering
   *
   * @param background a valid color instance
   */
  public void setBackground(Color background) {
    this.background = background;
  }

  /**
   * Get the name of the palette to be used for rendering
   *
   * @return a valid string from the static list of palette names
   */
  public String getPaletteName() {
    return paletteName;
  }

  /**
   * Sets the name of the palette to be used for rendering
   *
   * @param paletteName a valid string from the static list of palette names
   */
  public void setPaletteName(String paletteName) {
    this.paletteName = paletteName;
  }

  /**
   * Indicates that the color assignment should be based on a
   * fixed range of z values rather than the minimum and maximum values
   * available from the model. See getRangeForPalette.
   *
   * @return true if a fixed range should be used; false if the minimum and
   * maximum values from the model should be used
   */
  public boolean useRangeOfValuesForPalette() {
    return useRangeOfValuesForPalette;
  }

  /**
   * Indicates that the color assignment should be based on a
   * fixed range of z values rather than the minimum and maximum values
   * available from the model.
   *
   * @param useRangeOfValuesForPalette true if a fixed range should be used;
   * false if the minimum and maximum values from the model should be used
   */
  public void setUseRangeOfValuesForPalette(boolean useRangeOfValuesForPalette) {
    this.useRangeOfValuesForPalette = useRangeOfValuesForPalette;
  }

  /**
   * Gets the specified minimum and maximum values that should be used
   * for assigning a color to elevations when rendering
   *
   * @return a valid, two-dimensional array giving the minimum
   * and maximum values for the color-assignment range.
   */
  public double[] getRangeForPalette() {
    return Arrays.copyOf(rangeForPalette, rangeForPalette.length);
  }

  /**
   * Sets the specified minimum and maximum values that should be used
   * for assigning a color to elevations when rendering
   *
   * @param rangeForPalette a valid, two-dimensional array giving the minimum
   * and maximum values for the color-assignment range.
   */
  public void setRangeForPalette(double[] rangeForPalette) {
    this.rangeForPalette = Arrays.copyOf(rangeForPalette, rangeForPalette.length);
  }

  /**
   * Indicates whether wireframe rendering is enabled
   *
   * @return true if rendering is enabled; otherwise, false.
   */
  public boolean isWireframeSelected() {
    return isWireframeSelected;
  }

  /**
   * @param isWireframeSelected the isWireframeSelected to set
   */
  public void setWireframeSelected(boolean isWireframeSelected) {
    this.isWireframeSelected = isWireframeSelected;
  }

  /**
   * @return the isThinningSelected
   */
  public boolean isThinningSelected() {
    return isThinningSelected;
  }

  /**
   * @param isThinningSelected the isThinningSelected to set
   */
  public void setThinningSelected(boolean isThinningSelected) {
    this.isThinningSelected = isThinningSelected;
  }

  /**
   * @return the isEdgeRenderingSelected
   */
  public boolean isEdgeRenderingSelected() {
    return isEdgeRenderingSelected;
  }

  /**
   * @param isEdgeRenderingSelected the isEdgeRenderingSelected to set
   */
  public void setEdgeRenderingSelected(boolean isEdgeRenderingSelected) {
    this.isEdgeRenderingSelected = isEdgeRenderingSelected;
  }

  /**
   * @return the isVertexRenderingSelected
   */
  public boolean isVertexRenderingSelected() {
    return isVertexRenderingSelected;
  }

  /**
   * @param isVertexRenderingSelected the isVertexRenderingSelected to set
   */
  public void setVertexRenderingSelected(boolean isVertexRenderingSelected) {
    this.isVertexRenderingSelected = isVertexRenderingSelected;
  }

  /**
   * @return the isLabelRenderingSelected
   */
  public boolean isLabelRenderingSelected() {
    return isLabelRenderingSelected;
  }

  /**
   * @param isLabelRenderingSelected the isLabelRenderingSelected to set
   */
  public void setLabelRenderingSelected(boolean isLabelRenderingSelected) {
    this.isLabelRenderingSelected = isLabelRenderingSelected;
  }

  /**
   * @return the fieldForLabel
   */
  public String getFieldForLabel() {
    return fieldForLabel;
  }

  /**
   * @param fieldForLabel the fieldForLabel to set
   */
  public void setFieldForLabel(String fieldForLabel) {
    this.fieldForLabel = fieldForLabel;
  }

  /**
   * @return the usePaletteForWireframe
   */
  public boolean usePaletteForWireframe() {
    return usePaletteForWireframe;
  }

  /**
   * @param usePaletteForWireframe the usePaletteForWireframe to set
   */
  public void setUsePaletteForWireframe(boolean usePaletteForWireframe) {
    this.usePaletteForWireframe = usePaletteForWireframe;
  }

  /**
   * @return the isRasterSelected
   */
  public boolean isRasterSelected() {
    return isRasterSelected;
  }

  /**
   * @param isRasterSelected the isRasterSelected to set
   */
  public void setRasterSelected(boolean isRasterSelected) {
    this.isRasterSelected = isRasterSelected;
  }

  /**
   * @return the isHillshadeSelected
   */
  public boolean isHillshadeSelected() {
    return isHillshadeSelected;
  }

  /**
   * @param isHillshadeSelected the isHillshadeSelected to set
   */
  public void setHillshadeSelected(boolean isHillshadeSelected) {
    this.isHillshadeSelected = isHillshadeSelected;
  }

  /**
   * @return the hillshadeAzimuth
   */
  public double getHillshadeAzimuth() {
    return hillshadeAzimuth;
  }

  /**
   * @param hillshadeAzimuth the hillshadeAzimuth to set
   */
  public void setHillshadeAzimuth(double hillshadeAzimuth) {
    this.hillshadeAzimuth = hillshadeAzimuth;
  }

  /**
   * @return the hillshadeElevation
   */
  public double getHillshadeElevation() {
    return hillshadeElevation;
  }

  /**
   * @param hillshadeElevation the hillshadeElevation to set
   */
  public void setHillshadeElevation(double hillshadeElevation) {
    this.hillshadeElevation = hillshadeElevation;
  }

  /**
   * @return the hillshadeAmbient
   */
  public double getHillshadeAmbient() {
    return hillshadeAmbient;
  }

  /**
   * @param hillshadeAmbient the hillshadeAmbient to set
   */
  public void setHillshadeAmbient(double hillshadeAmbient) {
    this.hillshadeAmbient = hillshadeAmbient;
  }

  public boolean isFullResolutionGridSelected() {
    return isFullResolutionGridSelected;
  }

  public void setFullResolutionGridSelected(boolean status) {
    isFullResolutionGridSelected = status;
  }

  /**
   * @return the isLidarGroundPointsOptionSelected
   */
  public boolean isLidarGroundPointsOptionSelected() {
    return isLidarGroundPointsOptionSelected;
  }

  /**
   * @param isLidarGroundPointsOptionSelected the
   * isLidarGroundPointsOptionSelected to set
   */
  public void setIsLidarGroundPointsOptionSelected(boolean isLidarGroundPointsOptionSelected) {
    this.isLidarGroundPointsOptionSelected = isLidarGroundPointsOptionSelected;
  }

  /**
   * A "roll up" method that detects if either raster or hillshade rendering
   * is selected.
   *
   * @return if either grid-based rendering is selected, true; otherwise, false.
   */
  public boolean isGridBasedRenderingSelected() {
    return isRasterSelected || isHillshadeSelected;
  }

  /**
   * Gets the padding for the backplane image
   *
   * @return a fixed value.
   */
  public int getPadding() {
    return 150;
  }

}