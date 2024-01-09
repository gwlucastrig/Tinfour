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
 * 04/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm.properties;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.tinfour.svm.SvmBathymetryModel;

/**
 * Provides parameter and resource specifications for a SVM analysis run.
 */
public class SvmProperties {

  private final static String bathymetryModelKey = "bathymetryModel";

  private final static String unitOfDistanceKey = "unitOfDistance";
  private final static String unitOfAreaKey = "unitOfArea";
  private final static String unitOfVolumeKey = "unitOfVolume";
  private final static String reportKey = "report";
  private final static String shorelineReferenceElevationKey = "shorelineReferenceElevation";
  private final static String tableKey = "table";
  private final static String tableIntervalKey = "tableInterval";
  private final static String flatFixerKey = "remediateFlatTriangles";
  private final static String soundingSpacingKey = "computeSoundingSpacing";
  private final static String inputFolderKey = "inputFolder";
  private final static String outputFolderKey = "outputFolder";

  private final static String gridFileName = "rasterFileName";
  private final static String gridCellSize = "rasterCellSize";
  private final static String gridImageFileName = "rasterImageFileName";
  private final static String rasterGeoTiffFileName = "rasterGeoTiffFileName";
  private final static String rasterGeoTiffProjectionCode = "rasterGeoTiffProjectionCode";

  private final static String supplementalConstraintsFileKey = "supplementalConstraints";

  private final static String capacityGraphFileKey = "capacityGraphFileName";
  private final static String capacityGraphSizeKey = "capacityGraphSize";
  private final static String capacityGraphTitleKey = "capacityGraphTitle";

  private final static String contourGraphFileKey = "contourGraphFileName";
  private final static String contourGraphSizeKey = "contourGraphSize";
  private final static String contourGraphLegendTextKey = "contourGraphLegendText";
  private final static String contourGraphIntervalKey = "contourGraphInterval";

  private final static String contourRegionShapefileKey = "contourRegionShapefile";
  private final static String contourLineShapefileKey = "contourLineShapefile";
  private final static String contourShapefileShorelineEnabledKey = "contourShapefileShorelineEnabled";

  private final static String anomalyTableFileKey = "anomalyTableFileName";

  final Properties properties = new Properties();
  final List<String> keyList = new ArrayList<>();
  private File specificationFile;
  private SvmUnitSpecification unitOfDistance;
  private SvmUnitSpecification unitOfArea;
  private SvmUnitSpecification unitOfVolume;

  private Locale explicitLocale;
  private boolean usesCommaForDecimal;

  /**
   * Standard constructor
   */
  public SvmProperties() {
    unitOfDistance = new SvmUnitSpecification("Distance", "m", 1.0);
    unitOfArea = new SvmUnitSpecification("Area", "m^2", 1.0);
    unitOfVolume = new SvmUnitSpecification("Volume", "m^3", 1.0);

    // see how the Locale formats a string
    String qf = String.format("%f", 3.14);
    if (qf.indexOf(',') > 0) {
      usesCommaForDecimal = true;
    }
  }

  /**
   * Checks to see if an argument list includes the specified target and
   * returns its index within the argument array.
   *
   * @param args a valid list of strings
   * @param target the desired target string
   * @param valueRequired indicates that the target argument requires
   * a value specification.
   * @return if found, a value of zero or greater; otherwise, -1.
   */
  static public int indexArg(String[] args, String target, boolean valueRequired) {
    if (args == null || target == null || target.isEmpty()) {
      return -1;
    }
    for (int i = 0; i < args.length; i++) {
      if (target.equalsIgnoreCase(args[i])) {
        if (valueRequired) {
          if (i == args.length - 1 || args[i + 1].isEmpty()) {
            throw new IllegalArgumentException(
              "Missing value for option " + target);
          }
          // there is another string in the args array, and it may be
          // a candidate for the required value.  If it starts with a hyphen
          // it could be a negative numeric value (which might be
          // wanted by the application), but it could also be another
          // option specification (which would mean that the parameter
          // for the target option is missing).  So we need to check.
          String s = args[i + 1];
          if (s.charAt(0) == '-' && s.length() > 1 && !Character.isDigit(s.charAt(1))) {
              throw new IllegalArgumentException(
                "Missing value for option " + target);
          }
        }
        return i;
      }
    }
    return -1;
  }

  private static String findArgString(String[] args, String target) {
    int index = indexArg(args, target, true);
    if (index >= 0) {
      return args[index + 1];
    }
    return null;
  }

  /**
   * Loads a properties instance using values and paths specified through the
   * command-line arguments.
   *
   * @param args a valid, potentially zero-length array of strings
   * @return if successful, a populated instance
   * @throws IOException in the event of a unrecoverable I/O condition.
   */
  static public SvmProperties load(String[] args) throws IOException {
    String s = findArgString(args, "-properties");
    if (s == null) {
      if (args.length == 1) {
        String test = args[0].toLowerCase();
        int i = test.lastIndexOf(".properties");
        if (i == test.length() - 11) {
          s = args[0];
        }
      }
      if (s == null) {
        throw new IllegalArgumentException(
          "Missing properties file specification");
      }
    }
    File file = new File(s);
    SvmProperties p = new SvmProperties();
    p.load(file);
    return p;
  }

  /**
   * Checks to see if the properties object contains an explicit setting
   * for Locale and, if so, puts it into effect.
   * @throws IOException in the event of an ill-formatted or unknown setting.
   */
  private void checkForLocaleSettings() throws IOException {
      String q = properties.getProperty("Locale");
    if(q==null){
      q = properties.getProperty("locale");
    }

    if(q!=null){
      // a specific Locale was provided.  See if it works
      // in this code, we check for an unchecked
      // Note that we use an UNCHECKED exception here. When this code was
      // developed under Java 8, it was unclear what Java would do in the
      // future for handling ill-formed locales.
      try{
        Locale locale = Locale.forLanguageTag(q);
        if(locale == null){
            throw new IOException("Unrecognized specification for Locale, \""+q+"\"");
        }
        Locale.setDefault(locale);
        explicitLocale = locale;
        String qf = String.format("%f", 3.14);
        if(qf.indexOf(',')>0){
          usesCommaForDecimal = true;
        }
      }catch(RuntimeException rex){
        throw new IOException("Invalid specification for Locale, \""+q+"\"");
      }
    }
  }

  /**
   * Loads a properties instance using values and paths specified in the
   * indicated file.
   *
   * @param file a valid file instance
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public void load(File file) throws IOException {
    specificationFile = file;
    try (FileInputStream fins = new FileInputStream(file);
      BufferedInputStream bins = new BufferedInputStream(fins)) {
      properties.load(bins);
    }

    checkForLocaleSettings();

    Set<String> nset = properties.stringPropertyNames();
    for (String s : nset) {
      keyList.add(s);
    }
    Collections.sort(keyList);

    unitOfDistance = extractUnit("Distance", unitOfDistanceKey, unitOfDistance);
    unitOfArea = extractUnit("Area", unitOfAreaKey, getUnitOfArea());
    unitOfVolume = extractUnit("Volume", unitOfVolumeKey, getUnitOfVolume());
  }


  /**
   * Gets the explicit value for the locale, if it was set.
   * @return if explicitly set, a valid instance; otherwise, a null
   */
  public Locale getExplicitLocale(){
    return explicitLocale;
  }

  /**
   * Indicates whether the Locale uses the comma character for the
   * decimal part of floating-point values.
   * This method is used for formatting numeric strings in SVM.  Many non-English
   * speaking conventions use the comma rather than the period.
   * @return  true if the Locale uses periods for decimals; otherwise, false.
   */
  public boolean doesLocaleUseCommaForDecimal(){
    return usesCommaForDecimal;
  }
  /**
   * Get a list of the SVM file specifications for input samples. The
   * SvmFileSpecification may include metadata such as DBF file field name and
   * unit conversion values.
   *
   * @return a valid, potentially empty list
   */
  public List<SvmFileSpecification> getSampleSpecifications() {
    return getTargetSpecifications("samples");
  }

  /**
   * Get a list of the SVM file specifications for supplemental samples.
   * Typically these are samples derived for shallow-water areas. The
   * SvmFileSpecification may include metadata such as DBF file field name and
   * unit conversion values.
   *
   * @return a valid, potentially empty list
   */
  public List<SvmFileSpecification> getSupplementSpecifications() {
    return getTargetSpecifications("supplement");
  }

  /**
   * Get a list of the SVM file specifications for teh polygon bounding
   * constraints of the body of water. The SvmFileSpecification may include
   * metadata such as DBF file field name and unit conversion values.
   *
   * @return a valid, potentially empty list
   */
  public List<SvmFileSpecification> getBoundarySpecifications() {
    return getTargetSpecifications("bounds");
  }

  /**
   * Get the a description and potential conversion factor unit to be used for
   * specifying horizontal and vertical distances. Note that the SVM model
   * assumes uniform metrics in all directions.
   *
   * @return a valid unit of distance instance.
   */
  public SvmUnitSpecification getUnitOfDistance() {
    return unitOfDistance;
  }

  private SvmUnitSpecification extractUnit(
    String name,
    String key,
    SvmUnitSpecification defaultUnit) {
    String value = properties.getProperty(key);
    if (value == null) {
      return defaultUnit;
    }
    List<String> splitList = split(value);
    if (splitList.isEmpty()) {
      throw new IllegalArgumentException("Missing specification for " + key);
    }
    String label = splitList.get(0);
    double scaleFactor = 1.0;
    if (splitList.size() > 1) {

      try {
        scaleFactor = Double.parseDouble(splitList.get(1));
      } catch (NumberFormatException nex) {
        throw new IllegalArgumentException(
          "Invalid specification for " + key);
      }
      if (scaleFactor == 0) {
        throw new IllegalArgumentException(
          "Zero scaling factor not allowed for " + key);
      }
    }
    return new SvmUnitSpecification(name, label, scaleFactor);
  }

  private File extractFile(String folderKey, String fileProperty) {
    if (fileProperty == null) {
      return null;
    }
    String property = fileProperty.trim();
    if (property.isEmpty()) {
      return null;
    }
    File f = new File(property);
    File folder = getFolderForKey(folderKey);
    if (folder != null && !f.isAbsolute()) {
      return new File(folder, property);
    }
    return f;
  }

  private File extractOutputFile(String folderKey, String fileProperty) {
    if (fileProperty == null) {
      return null;
    }
    String property = fileProperty.trim();
    if (property.isEmpty()) {
      return null;
    }
    File f = new File(property);
    File folder = getFolderForKey(folderKey);
    if (folder != null && !f.isAbsolute()) {
      f = new File(folder, property);
    }
    File parent = f.getParentFile();
    if (parent != null && !parent.exists()) {
      boolean status = parent.mkdirs();
      if (!status) {
        throw new IllegalArgumentException(
          "Unable to create file for property " + property + "=" + f.getPath());
      }
    }
    return f;
  }

  private List<SvmFileSpecification> getTargetSpecifications(String target) {
    SvmBathymetryModel model = getBathymetryModel();
    File folder = getInputFolder();
    List<SvmFileSpecification> specList = new ArrayList<>();
    for (String key : keyList) {
      if (key.startsWith(target) && !key.contains(supplementalConstraintsFileKey)) {
        String value = properties.getProperty(key);
        List<String> splitList = split(value);
        specList.add(new SvmFileSpecification(key, model, splitList, folder));
      }
    }
    return specList;
  }

  private List<String> split(String value) {
    List<String> splitList = new ArrayList<>();
    if (value == null || value.isEmpty()) {
      return splitList;
    }
    StringBuilder sb = new StringBuilder();
    boolean spacePending = false;
    boolean spaceEnable = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isWhitespace(c)) {
        if (spaceEnable) {
          spacePending = true;
        }
        spaceEnable = false;
      } else if (c == '|') {
        // delimiter
        String s = sb.toString();
        sb.setLength(0);
        splitList.add(s);
        spaceEnable = false;
        spacePending = false;
      } else {
        if (spacePending) {
          sb.append(' ');
          spacePending = false;
        }
        spaceEnable = true;
        sb.append(c);
      }
    }
    if (sb.length() > 0) {
      splitList.add(sb.toString());
    }

    return splitList;
  }

  /**
   * Gets the input folder specification from the properties;
   * if the properties do not contain a key/value for inputFolder,
   * a null is returned.
   *
   * @return a valid instance; or a null if a folder is not specified.
   */
  public File getInputFolder() {
    return getFolderForKey(inputFolderKey);
  }

  /**
   * Gets the output folder specification from the properties; if the properties
   * do not contain a key/value for outputFolder, a null is returned.
   *
   * @return a valid instance; or a null if a folder is not specified.
   */
  public File getOutputFolder() {
    return getFolderForKey(outputFolderKey);
  }

  /**
   * Gets a folder specification from the properties for the named key;
   * if the properties do not contain the key,
   * a null is returned.
   *
   * @return a valid instance; or a null if a key is not specified.
   */
  private File getFolderForKey(String key) {
    String s = properties.getProperty(key);
    if (s == null) {
      return null;
    }
    return new File(s);
  }

  /**
   * Get the path to a file for writing an output report giving a record of the
   * computation parameters and results used for running the Simple Volumetric
   * Model. If not specified, a null value is returned and it is assumed that
   * the
   * report should be written to standard output.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getReportFile() {
    return extractOutputFile(outputFolderKey, properties.getProperty(reportKey));
  }

  /**
   * Get the path to a file for writing an output table giving volume and
   * elevation at fixed intervals (specified by getTableInterval()). If not
   * specified, a null value is returned and it is assumed that the table should
   * be written to standard output.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getTableFile() {
    return extractOutputFile(outputFolderKey, properties.getProperty(tableKey));
  }

  /**
   * Get the interval for computing a series of surface elevation values to be
   * used for modeling. By default, the value for this specification is 1 unit
   * (1 foot, 1 meter, etc.), but fractional values are allowed.
   *
   * @return a floating point value greater than 0.
   */
  public double getTableInterval() {
    String s = properties.getProperty(tableIntervalKey, "1.0");

    try {
      double d = Double.parseDouble(s);
      if (d <= 0) {
        throw new IllegalArgumentException(
          "Invalid value for table interval: " + s);
      }
      return d;
    } catch (NumberFormatException nex) {
      throw new IllegalArgumentException(
        "Invalid numeric for table interval: " + s);
    }
  }

  /**
   * Indicates whether the near-shore, flat-triangle remediation is enabled.
   *
   * @return true if remediation is to be performed; otherwise, false.
   */
  public boolean isFlatFixerEnabled() {
    String s = properties.getProperty(flatFixerKey, "false");
    boolean test = Boolean.parseBoolean(s.trim());
    return test;
  }

  /**
   * Indicates whether the computation of sounding spacing is enabled.
   *
   * @return true if computation is to be performed; otherwise, false.
   */
  public boolean isSoundingSpacingEnabled() {
    String s = properties.getProperty(soundingSpacingKey, "false");
    boolean test = Boolean.parseBoolean(s.trim());
    return test;
  }

  private int findMaxNameLength(int m0, List<SvmFileSpecification> samples) {
    int m = m0;
    for (SvmFileSpecification sample : samples) {
      String path = sample.file.getName();
      if (path.length() > m) {
        m = path.length();
      }
    }
    return m;
  }

  private void writeFileList(
    PrintStream ps,
    List<SvmFileSpecification> samples,
    String nameFmt) {
    if (samples.size() == 0) {
      ps.println("   None");
    } else {
      for (int i = 0; i < samples.size(); i++) {
        SvmFileSpecification sample = samples.get(i);
        String name = sample.file.getName();
        ps.format(nameFmt, i + 1, name);
        if (sample.field == null) {
          ps.format("%n");
        } else {
          ps.format("   (%s)%n", sample.field);
        }
      }
    }
  }

  private void writeUnit(PrintStream ps, String name, SvmUnitSpecification s) {
    double f = s.getScaleFactor();
    String fStr = "";
    if (f != 1.0) {
      if (f - Math.floor(+1.0e-5) == 0) {
        // integral value
        fStr = String.format("   %8d", (long) f);
      } else {
        fStr = String.format("   %11.3f", f);
      }
    }
    ps.format("   %-12s%-12s%s%n", name, s.getLabel(), fStr);
  }

  /**
   * Writes summary data to the specified PrintStream instance. Note that the
   * input to this method is often, but not always, System.out.
   *
   * @param ps a valid instance
   */
  public void writeSummary(PrintStream ps) {
    ps.format("Specifications for processing%n");
    if (specificationFile != null) {
      ps.format("Properties file: %s%n", specificationFile.getPath());
    }

    SvmBathymetryModel model = this.getBathymetryModel();
    if (model == null) {
      ps.format("Bathymetry model is not specified, no processing is possible");
    } else {
      ps.format("%nBathymetry model: %s%n%n", model.name());
    }
    File f = getInputFolder();
    ps.format("Input folder:   %s%n", f == null ? "Not specified" : f.getPath());
    f = getOutputFolder();
    ps.format("Output folder:  %s%n", f == null ? "Not specified" : f.getPath());
    ps.format("%n");
    List<SvmFileSpecification> samples = getSampleSpecifications();
    List<SvmFileSpecification> bounds = getBoundarySpecifications();
    List<SvmFileSpecification> supplements = getSupplementSpecifications();
    int m = findMaxNameLength(0, samples);
    m = findMaxNameLength(m, bounds);
    String nameFmt = "   %2d. %-" + m + "s";
    ps.format("Sample Files:%n");
    writeFileList(ps, samples, nameFmt);
    ps.format("Boundry Files:%n");
    writeFileList(ps, bounds, nameFmt);
    ps.format("Supplemental Sample Files:%n");
    writeFileList(ps, supplements, nameFmt);
    ps.format("%nUnits of Measure%n");
    writeUnit(ps, "Distance", unitOfDistance);
    writeUnit(ps, "Area:", getUnitOfArea());
    writeUnit(ps, "Volume:", getUnitOfVolume());
    ps.println("");
    String s = properties.getProperty(reportKey);
    ps.format("Report:                   %s%n", s == null || s.isEmpty() ? "None" : s);
    s = properties.getProperty(tableKey);
    ps.format("Table output:             %s%n", s == null || s.isEmpty() ? "None" : s);
    if (s != null && !s.isEmpty()) {
      ps.format("Table interval:           %4.2f%n", getTableInterval());
    }
    s = properties.getProperty(shorelineReferenceElevationKey);
    ps.format("Shoreline Elevation:      ");
    if (s == null || s.isEmpty()) {
      ps.format("To be obtained from boundary data");
    } else {
      ps.format("Explicitly specified as " + s);
    }
    ps.format("%n");
    boolean fixFlats = isFlatFixerEnabled();
    ps.format("Remediate Flat Triangles: %s%n", Boolean.toString(fixFlats));
  }

  /**
   * Get the a description and potential conversion factor unit to be used for
   * specifying surface area.
   *
   * @return a valid unit specification instance.
   */
  public SvmUnitSpecification getUnitOfArea() {
    return unitOfArea;
  }

  /**
   * Get the a description and potential conversion factor unit to be used for
   * specifying volume.
   *
   * @return a valid unit specification instance.
   */
  public SvmUnitSpecification getUnitOfVolume() {
    return unitOfVolume;
  }

  /**
   * Get the shoreline reference elevation, if provided.
   *
   * @return a valid floating point number or a NaN if undefined
   */
  public double getShorelineReferenceElevation() {
    String s = properties.getProperty(shorelineReferenceElevationKey);
    if (s == null) {
      return Double.NaN;
    }
    s = s.trim();
    if (s.isEmpty()) {
      return Double.NaN;
    }

    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nex) {
      throw new IllegalArgumentException(
        "Invalid numeric for shoreline reference elevation: " + s);
    }
  }

  /**
   * Get the grid cell size, if provided.
   *
   * @return a valid floating point number or a NaN if undefined
   */
  public double getGridCellSize() {
    String s = properties.getProperty(gridCellSize);
    if (s == null) {
      return Double.NaN;
    }
    s = s.trim();
    if (s.isEmpty()) {
      return Double.NaN;
    }

    try {
      double d = Double.parseDouble(s);
      if (d <= 0) {
        throw new IllegalArgumentException(
          "Invalid value for grid cell size: " + s);
      }
      return d;
    } catch (NumberFormatException nex) {
      throw new IllegalArgumentException(
        "Invalid numeric for gridCellSize: " + s);
    }
  }

  /**
   * Get the path to a file for writing an output data file giving an
   * interpolated grid of water bottom elevations.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getGridFile() {
    return extractOutputFile(outputFolderKey, properties.getProperty(gridFileName));
  }

  /**
   * Get the path to a file for writing an output image file giving an
   * interpolated grid of water bottom elevations.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getGridImageFile() {
    return extractOutputFile(outputFolderKey, properties.getProperty(gridImageFileName));
  }


  /**
   * Gets the path to a file for writing an output GeoTIFF file containing
   * the interpolated grid of water bottom elevations.
   * @return a valid File instance or a null if not specified.
   */
  public File getGeoTiffFile(){
    return extractOutputFile(outputFolderKey, properties.getProperty(rasterGeoTiffFileName));
  }



  /**
   * Get a reference to a supplemental shapefile providing
   * constraints for SVM processing.
   *
   * @return if specified, a valid File instance; otherwise, a null
   */
  public File getSupplementalConstraintsFile() {
    return extractFile(inputFolderKey, properties.getProperty(supplementalConstraintsFileKey));
  }

  /**
   * Get the path to a file for writing an output file to be used for
   * processing raster grid. The properties file is treated as containing
   * the root name. An extension is added as appropriate.
   *
   * @param extension the file extension (including the period).
   * @return a valid File instance or a null if not specified.
   */
  public File getGridFile(String extension) {
    String rootName = properties.getProperty(gridFileName);
    if (rootName == null) {
      return null;
    }
    String targetName = rootName + extension;
    return extractOutputFile(outputFolderKey, targetName);
  }

  /**
   * Get the path to a file for writing a graph of the capacity as a function of
   * water level. .
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getCapacityGraphFile() {
    if (properties.containsKey(capacityGraphFileKey)) {
      return extractOutputFile(outputFolderKey, properties.getProperty(capacityGraphFileKey));
    }
    return null;
  }

  /**
   * Get the dimensions for the capacity graph image file.
   *
   * @return a valid instance of non-trivial size.
   */
  public Dimension getCapacityGraphDimensions() {
    return extractDimension(capacityGraphSizeKey, 650, 400);
  }

  /**
   * Gets the title for the capacity graph image
   *
   * @return if defined, a valid non-empty string instance;
   * if undefined, a null.
   */
  public String getCapacityGraphTitle() {
    String s = properties.getProperty(capacityGraphTitleKey);
    if (s == null || s.trim().isEmpty()) {
      return null;
    }
    return s.trim();
  }

  /**
   * Get the path to a file for writing an image file showing a contour
   * plot of the reservoir data.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getContourGraphFile() {
    if (properties.containsKey(contourGraphFileKey)) {
      return extractOutputFile(outputFolderKey, properties.getProperty(contourGraphFileKey));
    }
    return null;
  }

  /**
   * Get the path to a file for writing contour region Shapefile showing
   * a contour plot of the reservoir data.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getContourRegionShapefile() {
    if (properties.containsKey(contourRegionShapefileKey)) {
      return extractFile(
        outputFolderKey, properties.getProperty(contourRegionShapefileKey));
    }
    return null;
  }

  /**
   * Get the path to a file for writing contour region Shapefile showing
   * a contour plot of the reservoir data.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getContourLineShapefile() {
    if (properties.containsKey(contourLineShapefileKey)) {
      return extractFile(
        outputFolderKey, properties.getProperty(contourLineShapefileKey));
    }
    return null;
  }


  /**
   * Indicates whether the optional shoreline append operation is enabled.
   * @return true if the append operation is enabled; otherwise, false.
   */
  public boolean isContourShapefileShorelineEnabled() {
    if (properties.containsKey(contourShapefileShorelineEnabledKey)) {
      String s = properties.getProperty(contourShapefileShorelineEnabledKey);
      return s.toLowerCase().startsWith("t");
    }
    return false;
  }
  /**
   * Get the dimensions for the contour graph image file.
   *
   * @return a valid instance of non-trivial size.
   */
  public Dimension getContourGraphDimensions() {
    return extractDimension(contourGraphSizeKey, 650, 650);
  }

  /**
   * Gets the title for the capacity graph image
   *
   * @return if defined, a valid non-empty string instance;
   * if undefined, a null.
   */
  public String getContourGraphLegendText() {
    String s = properties.getProperty(contourGraphLegendTextKey);
    if (s == null || s.trim().isEmpty()) {
      return "Legend";
    }
    return s.trim();
  }

  /**
   * Gets the contour graph interval
   *
   * @return if defined, a floating point value greater than zero;
   * if undefined, zero.
   */
  public double getContourGraphInterval() {
    String s = properties.getProperty(contourGraphIntervalKey);
    if (s == null || s.trim().isEmpty()) {
      return 0;
    }
    s = s.trim();
    if ("automatic".equalsIgnoreCase(s)) {
      return 0;
    }
    try {
      double d = Double.parseDouble(s);
      if (d <= 0) {
        return 0;
      }
      return d;
    } catch (NumberFormatException nfe) {
      return 0;
    }
  }

  private Dimension extractDimension(String key, int width, int height) {
    String s = properties.getProperty(key);
    if (s == null) {
      return new Dimension(width, height);
    }

    int[] values = new int[2];
    int nNumeric = 0;
    int value = 0;
    boolean numeric = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        if (!numeric) {
          value = 0;
          numeric = true;
        }
        value = value * 10 + ((int) c - 48);
      } else if (numeric) {
        numeric = false;
        values[nNumeric++] = value;
        if (nNumeric == 2) {
          // we've completed the second numeric
          return new Dimension(values[0], values[1]);
        }
      }
    }

    if (numeric) {
      values[nNumeric++] = value;
      if (nNumeric == 2) {
        // we've completed the second numeric
        return new Dimension(values[0], values[1]);
      }
    }
    throw new IllegalArgumentException(
      "Incomplete specification for dimension: " + key + "=" + s);
  }

  /**
   * Get the path to a file for writing a table reporting anomalies.
   * This setting controls whether anomaly analysis is performed.
   *
   * @return a valid File instance or a null if not specified.
   */
  public File getAnomalyTableFile() {
    if (properties.containsKey(anomalyTableFileKey)) {
      return extractOutputFile(outputFolderKey, properties.getProperty(anomalyTableFileKey));
    }
    return null;
  }

  /**
   * Gets the bathymetry model specified for the soundings.
   *
   * @return a valid instance or a null if a value was not properly specified.
   */
  public SvmBathymetryModel getBathymetryModel() {
    String s = properties.getProperty(bathymetryModelKey);
    return SvmBathymetryModel.lenientValueOf(s);
  }

  /**
   * Indicates whether the source data includes a bathymetry model
   * specification. This method does not reflect whether that specification
   * included a valid value.
   *
   * @return true if a specification was supplied; otherwise, false.
   */
  public boolean isBathymetryModelSpecified() {
    String s = properties.getProperty(bathymetryModelKey);
    return s != null && !s.trim().isEmpty();
  }

  /**
   * Indicates whether the source properties includes a specification
   * for the GeoTiff Projection code (usually an EPSG projection code).
   * @return true if a projection code is set; otherwise, false
   */
  public boolean isGeoTiffProjectionCodeSpecified(){
    String s = properties.getProperty(SvmProperties.rasterGeoTiffProjectionCode);
    return s != null && !s.isBlank();
  }

  /**
   * Gets the specified GeoTIFF projection code, if any.
   * @return if available, a valid integer; otherwise, a zero.
   */
  public int getGeoTiffProjectionCode(){
    String s = properties.getProperty(SvmProperties.rasterGeoTiffProjectionCode);
    if(s==null || s.isBlank()){
      return 0;
    }
    try{
      return Integer.parseInt(s.trim());
    }catch(NumberFormatException nfe){
      return 0;
    }
  }

}
