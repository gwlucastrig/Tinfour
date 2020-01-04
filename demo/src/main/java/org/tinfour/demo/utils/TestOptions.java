/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 09/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.gwr.GwrTinInterpolator;
import org.tinfour.interpolation.IInterpolatorOverTin;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.interpolation.TriangularFacetInterpolator;
import org.tinfour.standard.IncrementalTin;

/**
 * Provides a convenience utility for extracting command-line arguments for
 * running integration tests.
 * <p>
 * The intent of this class is to provide a uniform treatment of command-line
 * arguments. It also provides a way of discovering command-line arguments that
 * are not recognized by the application. This situation sometimes occurs when
 * the operator makes a typographical error in the specification of arguments.
 */
public class TestOptions {

  private static final String virtualClassName
    = "org.tinfour.semivirtual.SemiVirtualIncrementalTin";

  private static final String standardClassName
    = "org.tinfour.standard.IncrementalTin";

  /**
   * An enumeration giving options for how an input LAS file
   * with a geographic coordinate system is to be treated.
   * Values can be converted to meters, feet, or left as degrees
   * (not recommended).
   */
  public enum GeoCoordinateOption {
    /** Treat horizontal coordinates as being in meters */
    Meters,
    /** Treat horizontal coordinates as being in feet */
    Feet,
    /** Do not adjust horizontal coordinates. */
    Degrees
  }
    /**
     * The options that are recognized and processed by the
     * this class.
     */
  public final static String[] BUILT_IN_OPTIONS = {
    "-in",
    "-out",
    "-nRows",
    "-nColumns",
    "-nVertices",
    "-nTests",
    "-preSort",
    "-preAllocate",
    "-maxVertices",
    "-seed",
    "-lidarClass",
    "-lidarThinning",
    "-clip",
    "-frame",
    "-tinClass",
    "-geo",
    "-delimiter",
    "-palette",
    "-interpolator"
  };


  File inputFile;
  File outputFile;
  File constraintsFile;

  Integer nVertices;
  Integer nRows;
  Integer nColumns;
  Integer nTests;
  Integer lidarClass;
  Double lidarThinning;
  Long maxVertices;
  Long randomSeed;
  Boolean preSort;
  Boolean preAllocate;
  double[] clipBounds;
  double[] frame;
  String tinClassName;
  Class<?> tinClass;
  TestPalette palette;
  GeoCoordinateOption geoCoordOption;
  String delimiter;
  String interpolator;
  InterpolationMethod interpolationMethod;

  /**
   * Indicates whether the specified string matches the pattern of a
   * command-line argument (is introduced by a negative or plus symbol.
   *
   * @param s the test string
   * @return true if the test string is an option
   */
  private boolean isOption(String s) {
    char c = s.charAt(0);
    return  c=='-' || c=='+';
  }

  /**
   * Check supplied options for validity. Input file is checked for existence
   * and readability.
   */
  private void checkOptions() {
    if (inputFile != null) {
      if (!inputFile.exists()) {
        throw new IllegalArgumentException("Input file does not exist: " + inputFile.getPath());
      }
      if (!inputFile.canRead()) {
        throw new IllegalArgumentException("Unable to access input file: " + inputFile.getPath());
      }
    }

    if (tinClassName != null) {
      // check to see if user supplied short names.
      if("virtual".equalsIgnoreCase(tinClassName)){
        tinClassName = virtualClassName;
      }else if("semivirtual".equalsIgnoreCase(tinClassName)){
        tinClassName = virtualClassName;
      }else if("standard".equalsIgnoreCase(tinClassName)){
        tinClassName = standardClassName;
      }
      ClassLoader classLoader = this.getClass().getClassLoader();
      try {
        tinClass = classLoader.loadClass(tinClassName);
        if (!IIncrementalTin.class.isAssignableFrom(tinClass)) {
          throw new IllegalArgumentException(
            "Test Class " + tinClass.getName()
            + " is not instance of "
            + IIncrementalTin.class.getName());
        }
      } catch (ClassNotFoundException ex) {
        throw new IllegalArgumentException(
          "Test class not found: " + tinClassName, ex);
      }
    }
 

    if(delimiter!=null && delimiter.length()!=1){
      throw new IllegalArgumentException(
          "Delimiter must be a single character");
    }
  }

  /**
   * Search the arguments for the specified option followed by a integer
   * value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @param defaultValue a default value to be returned if the option is
   * not found, or a null if none is supplied.
   * @return if found, a valid instance of Integer; otherwise, a null.
   */
  public Integer scanIntOption(
    String[] args,
    String option,
    boolean[] matched,
    Integer defaultValue)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Integer.parseInt(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException("Illegal integer value for "
            + option + ", " + nex.getMessage(), nex);
        }
      }
    }
    return defaultValue;
  }

  /**
   * Search the arguments for the specified option followed by a long integer
   * value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid instance of Long; otherwise, a null.
   */
  public Long scanLongOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Long.parseLong(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
             "Illegal integer value for " + option + ", " + nex.getMessage(),
              nex);
        }
      }
    }
    return null;
  }

  /**
   * Search the arguments for the specified option followed by a
   * floating-point value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid instance of Double; otherwise, a null.
   */
  public Double scanDoubleOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Double.parseDouble(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
            "Illegal floating-point value for "
              + option + ", " + nex.getMessage(),
              nex);
        }
      }
    }
    return null;
  }

  /**
   * Scans the argument array to see if it included the specification of a
   * boolean option in the form "-Option" or "-noOption". Note that boolean
   * arguments are single strings and do not take a companion argument (such
   * as "true" or "false").
   *
   * @param args a valid array of command-line arguments
   * @param option the target option
   * @param matched an array paralleling args to indicate whether the args
   * matched a search option.
   * @param defaultValue a default value to be returned if the option is
   * not found, or a null if none is supplied.
   * @return if found, a valid instance of Boolean; otherwise, a null.
   */
  public Boolean scanBooleanOption(
    String[] args,
    String option,
    boolean[] matched,
    Boolean defaultValue) {
    String notOption = "-no" + option.substring(1, option.length());
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (matched != null && matched.length == args.length) {
          matched[i] = true;
        }
        return true;
      } else if (args[i].equalsIgnoreCase(notOption)) {
        if (matched != null && matched.length == args.length) {
          matched[i] = true;
        }
        return false;
      }

    }

    return defaultValue;
  }

  /**
   * Search the arguments for the specified option followed by a string,
   * marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid string; otherwise, a null.
   */
  public String scanStringOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
 
		if (matched != null && matched.length == args.length) {
			matched[i] = true;
			matched[i + 1] = true;
		}
		return args[i + 1];
      }
    }
    return null;
  }

  /**
   * Scan for an option giving ranges for a rectangular region in the
   * order minX, maxX, minY, maxY.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid array of 4 floating-point values; otherwise,
   * an empty array
   */
 public double[] scanBounds(String[] args, String option, boolean[] matched) {
    double[] a = new double[4];
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i > args.length - 5) {
          throw new IllegalArgumentException(
            "Fewer than 4 arguments where bounds specified for argument " + i);
        }
        matched[i] = true;
        for (int j = 0; j < 4; j++) {
          int index = i + 1 + j;
          matched[index] = true;
          String s = args[index];
          try {
            a[j] = Double.parseDouble(s);
          } catch (NumberFormatException nex) {
            throw new IllegalArgumentException(
              "Illegal floating-point value for " + option
                + ", argument " + s + ", " + nex.getMessage(), nex);
          }
        }
        if (a[0] >= a[1] || a[2] >= a[3]) {
          throw new IllegalArgumentException("Values for " + option + " should be minX maxX minY maxY");
        }
        return a;
      }
    }

    return new double[0];
  }

  /**
   * Checks to see that the args[] array is valid.
   *
   * @param args an array of arguments thay must not be null and must not
   * include null references, but zero-length arrays are allowed.
   */
  private void checkForValidArgsArray(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("Null argument array not allowed");
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i] == null || args[i].length() == 0) {
        throw new IllegalArgumentException(
          "Null or zero-length argument at index " + i);
      }
    }
  }

  /**
   * Gets the extension from the specified file
   * @param file a valid file reference
   * @return if found, a valid string (period not included); otherwise,
   * a null.
   */
  public String getFileExtension(File file) {
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
   * Scan the argument list, extracting standard arguments. A boolean array
   * with a one-to-one correspondence to args[] is returned indicating which
   * arguments were recognized.
   *
   * @param args a valid, potentially zero-length argument list.
   * @return a boolean array indicating which arguments were recognized.
   */
  public boolean[] argumentScan(String[] args) {

    checkForValidArgsArray(args);

    boolean[] matched = new boolean[args.length];
    if (args.length == 0) {
      return matched;
    }

    if (args.length == 1 && !isOption(args[0])) {
      // check for special case where one argument is passed
      // giving an input file name
      String inputFileName = args[0];
      matched[0] = true;
      inputFile = new File(inputFileName);
      checkOptions();
      return matched;
    } else {
      String inputFileName = scanStringOption(args, "-in", matched);
      if (inputFileName != null) {
        inputFile = new File(inputFileName);
      }
    }

    String outputFileName = scanStringOption(args, "-out", matched);
    if (outputFileName != null) {
      outputFile = new File(outputFileName);
    }

    String constraintsFileName = scanStringOption(args, "-constraints", matched);
    if (constraintsFileName != null) {
      constraintsFile = new File(constraintsFileName);
    }


    nRows = scanIntOption(args, "-nRows", matched, null);
    nColumns = scanIntOption(args, "-nColumns", matched, null);
    nVertices = scanIntOption(args, "-nVertices", matched, null);
    nTests = scanIntOption(args, "-nTests", matched, null);

    preSort = scanBooleanOption(args, "-preSort", matched, preSort);
    preAllocate = scanBooleanOption(args, "-preAllocate", matched, preAllocate);

    maxVertices = scanLongOption(args, "-maxVertices", matched);
    randomSeed = scanLongOption(args, "-seed", matched);

    lidarClass = scanIntOption(args, "-lidarClass", matched, lidarClass);
    lidarThinning = scanDoubleOption(args, "-lidarThinning", matched);

    clipBounds = scanBounds(args, "-clip", matched);
    frame = scanBounds(args, "-frame", matched);

    tinClassName = scanStringOption(args, "-tinClass", matched);

    String paletteName = scanStringOption(args, "-palette", matched);

    delimiter = scanStringOption(args, "-delimiter", matched);

    checkOptions();

    if (paletteName != null) {
      palette = TestPalette.getPaletteByName(paletteName);
      if (palette == null) {
        throw new IllegalArgumentException(
          "Unrecognized palette \"" + paletteName + "\"");
      }
    }

    String geoCoordOptionString = scanStringOption(args, "-geo", matched);
    if (geoCoordOptionString != null) {
      if ("Meters".equalsIgnoreCase(geoCoordOptionString)) {
        geoCoordOption = GeoCoordinateOption.Meters;
      } else if ("Feet".equalsIgnoreCase(geoCoordOptionString)) {
        geoCoordOption = GeoCoordinateOption.Feet;
      } else if ("Degrees".equalsIgnoreCase(geoCoordOptionString)) {
        geoCoordOption = GeoCoordinateOption.Degrees;
      } else {
        throw new IllegalArgumentException(
          "Invalid specification for -geo option,"
          + " must be Meters, Feet, or Degrees (not recommended)");
      }
    }

    interpolator = scanStringOption(args, "-interpolator", matched);
    if (interpolator != null) {
      interpolationMethod = InterpolationMethod.lenientValue(interpolator);
      if (interpolationMethod == null) {
        String gripe = "Invalid specification for -interpolator option \""
          + interpolator
          + "\"";

        throw new IllegalArgumentException(gripe);
      }
    }



    return matched;
  }

  /**
   * Gets the input file.
   *
   * @return if specified, a valid input file; otherwise, a null
   */
  public File getInputFile() {
    return inputFile;
  }

  /**
   * Gets the output file.
   *
   * @return if specified, a valid input file; otherwise, a null.
   */
  public File getOutputFile() {
    return outputFile;
  }

  /**
   * Gets the root string for the input file, removing the file-type suffix
   * from the end of the string. This method is intended for use in
   * applications where the output file has the same root name as the input
   * file, but a different file-type suffix is appended.
   *
   * @return if the input file is specified, a valid string; otherwise, a
   * null.
   */
  public String getInputFileRootString() {
    if (inputFile == null) {
      return null;
    }
    String path = inputFile.getPath();
    int index = path.lastIndexOf('.');
    if (index == -1) {
      return path;
    }
    return path.substring(0, index);
  }

  /**
   * Gets the number of rows to be used in applications which process a grid
   * of data values.
   *
   * @param defaultCount the default number of rows; supply an impossible
   * value if no default is available.
   * @return if specified, the value from the "-nRows" argument; otherwise,
   * the default.
   */
  public int getRowCount(int defaultCount) {
    if (nRows == null) {
      return defaultCount;
    } else {
      return nRows;
    }
  }

  /**
   * Gets the number of columns to be used in applications which process a
   * grid of data values.
   *
   * @param defaultCount the default number of columns; supply an impossible
   * value if no default is available.
   * @return if specified, the value from the "-nColumns" argument; otherwise,
   * the default.
   */
  public int getColumnCount(int defaultCount) {
    if (nColumns == null) {
      return defaultCount;
    } else {
      return nColumns;
    }
  }

  /**
   * Gets the number of points to be used in applications which process a
   * randomly generated list of vertices.
   *
   * @param defaultCount the default number of vertices; supply an impossible
   * value if no default is available.
   * @return if specified, the value from the "-nVertices" argument;
   * otherwise, the default.
   */
  public int getVertexCount(int defaultCount) {
    if (nVertices == null) {
      return defaultCount;
    } else {
      return nVertices;
    }
  }

  /**
   * Gets the number of tests to be performed by applications which feature
   * repeated tests.
   *
   * @param defaultCount the default number of tests; supply an impossible
   * value if no default is available.
   * @return if specified, the value from the "-nTest" argument; otherwise,
   * the default.
   */
  public int getTestCount(int defaultCount) {
    if (nTests == null) {
      return defaultCount;
    } else {
      return nTests;
    }
  }

  /**
   * Gets a limit for the number of vertices that are read from a LAS file,
   * intended for use in situations where available memory is limited.
   *
   * @param defaultMax the default maximum value, use Long.MAX_VALUE if there
   * is no limit.
   * @return if specified, the value from the "-maxVertices" argumnet;
   * otherwise, the default.
   */
  public long getMaxVertices(long defaultMax) {
    if (maxVertices == null) {
      return defaultMax;
    } else {
      return maxVertices;
    }
  }

  /**
   * Gets a value for a seed to be used for producing a sequence of values
   * using a random-number generator. While the Java Random class is often
   * used for this purpose, applications are free to use whether means or
   * alternatives that are appropriate.
   *
   * @param defaultSeed a default value .
   * @return if specified, the value from the "-seed" argument; otherwise, the
   * default.
   */
  public long getRandomSeed(long defaultSeed) {
    if (randomSeed == null) {
      return defaultSeed;
    } else {
      return randomSeed;
    }
  }

  /**
   * Indicates whether the vertex list should be Hilbert sorted before
   * insertion into the TIN.
   *
   * @param defaultEnabled a default value supplied by the applicat
   * @return true if pre-sort is enabled; otherwise, false
   */
  public boolean isPreSortEnabled(boolean defaultEnabled) {
    if (preSort == null) {
      return defaultEnabled;
    } else {
      return preSort;
    }
  }

  /**
   * Set the pre-sort option (may be overridden when processing arguments)
   * @param enablePreSort the status for the pre-sort option.
   */
  void setPreSortEnabled(boolean enablePreSort){
    preSort = enablePreSort;
  }

  /**
   * Indicates whether edge pre-allocation is enabled.
   *
   * @param defaultEnabled a default value supplied by the application
   * @return true if pre-allocation is enabled; otherwise, false.
   */
  public boolean isPreAllocateEnabled(boolean defaultEnabled) {
    if (preAllocate == null) {
      return defaultEnabled;
    } else {
      return preAllocate;
    }
  }

  /**
   * Performs a check for any arguments that were not matched.
   *
   * @param args a valid array of arguments
   * @param matched a parallel array to args, used to track which arguments
   * were matched to supported options.
   */
  public void checkForUnrecognizedArgument(String[] args, boolean[] matched) {
    checkForValidArgsArray(args);
    if (matched == null || matched.length < args.length) {
      throw new IllegalArgumentException(
        "Implementation error: matched array must correspond to args array");
    }

    for (int i = 0; i < args.length; i++) {
      if (isOption(args[i]) && !matched[i]) {
        throw new IllegalArgumentException("Unrecognized argument " + args[i]);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (!matched[i]) {
        throw new IllegalArgumentException("Unrecognized argument " + args[i]);
      }
    }
  }

  public void checkForMandatoryOptions(String args[], String[] options) {
    for (String s : options) {
      if ("-in".equalsIgnoreCase(s)) {
        // the -in option has a special syntax where it may
        // be specified as a single-argument on the command line
        if (inputFile != null) {
          continue;
        }
        boolean found = false;
        for (String t : args) {
          if (s.equalsIgnoreCase(t)) {
            found = true;
            break;
          }
        }
        if (!found) {
          throw new IllegalArgumentException("Missing mandatory setting for " + s);
        }
      }
    }
  }

  /**
   * Gets the target lidar classification for samples to be read from an LAS
   * file. This setting is sometimes combined with the lidar thinning setting.
   *
   * @return a value in the range 0 to 255, or -1 for all classes.
   */
  public int getLidarClass() {
    if (lidarClass == null) {
      return -1;
    } else {
      return lidarClass.intValue();
    }
  }

  /**
   * Set the default classification for lidar points to be accepted when
   * reading a LAS file. Use 2 for ground points and -1 to accept all.
   * @param classification an integer in the range 0 to 255, or -1 for all.
   */
  public void setLidarClass(int classification){
    if(classification<-1 || classification>255){
      throw new IllegalArgumentException(
        "Lidar classification must be in range 0 to 255 "
        +" (0 to 15 is commonly used)");
    }
    lidarClass = classification;
  }

  /**
   * Gets a lidar thinning factor used for selecting random subsets of lidar
   * samples from an LAS file. If used, the number of points retrieved from
   * the file will be reduced to a count approximately proportional to the
   * thinning factor. For example, given a thinning factor of 0.5,
   * approximately 1/2 of the sample points in the lidar file will be
   * retrieved. This setting is often compiled with the lidar-class setting.
   *
   * @return a value greater than zero and less than or equal to 1.0, with 1.0
   * corresponding to 100 percent acceptance.
   */
  public double getLidarThinningFactor() {
    if (lidarThinning == null) {
      return 1.0;
    } else {
      return lidarThinning.doubleValue();
    }
  }

  /**
   * Get the clip bounds specified as a sequence of 4 floating-point values
   * giving minX, maxX, minY, maxY.
   *
   * @return if specified, a valid array of 4 values;
   * otherwise a zero-length array
   */
  public double[] getClipBounds() {
    if(clipBounds==null){
      return new double[0];
    }
    return Arrays.copyOf(clipBounds, clipBounds.length);
  }

  /**
   * Indicates whether the clip bounds was specified.
   * @return true if the bounds are specified; otherwise, false
   */
  public boolean isClipSet() {
    return clipBounds != null && clipBounds.length == 4;
  }

    /**
   * Indicates whether the frame was specified.
   * @return true if the frame was specified; otherwise, false
   */
  public boolean isFrameSet() {
    return frame != null && frame.length == 4;
  }

   /**
   * Get the frame specified as a sequence of 4 floating-point values
   * giving minX, maxX, minY, maxY.
   *
   * @return if specified, a valid array of 4 values;
   * otherwise a zero-length array
   */
  public double[] getFrame() {
    if(frame==null){
      return new double[0];
    }
    return Arrays.copyOf(frame, 4);
  }
  /**
   * Indicates whether the options specified a class for the Incremental TIN.
   * @return true if the options specified a class; otherwise, false.
   */
  public boolean isTinClassSet(){
    return tinClass!=null;
  }
  /**
   * Gets the class specified for the test (default: IncrementalTin)
   * @return a valid reference for a class that implements IIncrementalTin
   */
  public Class<?> getTinClass() {
    if (tinClass == null) {
      return IncrementalTin.class;
    }

    return tinClass;
  }

  /**
   * Gets a new instance of the class specified via the command-line options
   * by invoking its default constructor.
   * @return a valid instances of the specified class, or IncrementalTin
   * if no class was specified.
   */
  public IIncrementalTin getNewInstanceOfTestTin() {
    if (tinClass == null) {
      return new IncrementalTin();
    }
    Constructor<?> constructor = null;
    try {
      constructor = tinClass.getConstructor();
    } catch (NoSuchMethodException | SecurityException ex) {
      throw new IllegalArgumentException(
        "No-argument constructor not available for " + tinClass.getName(), ex);
    }
    try {
      return (IIncrementalTin) (constructor.newInstance());
    } catch (InstantiationException
      | IllegalAccessException
      | IllegalArgumentException
      | InvocationTargetException ex)
    {
      throw new IllegalArgumentException(
        "Unable to instantiate class " + tinClass.getName(), ex);
    }
  }

  /**
   * Indicates whether a palette was specified
   * @return true if a palette was specified; otherwise false.
   */
  public boolean isPaletteSet(){
    return palette != null;
  }

  /**
   * Get an instance of a palette for rendering.
   * @return a valid palette (uses default if none specified).
   */
  public TestPalette getPalette(){
    if(isPaletteSet()){
      return palette;
    }else{
      return TestPalette.getDefaultPalette();
    }
  }

  /**
   * Gets the geographic coordinate conversion option, if specified.
   * @return if specified, a valid member of the enumeration; otherwise,
   * a null.
   */
  public GeoCoordinateOption getGeoCoordinateOption(){
    return this.geoCoordOption;
  }


  /**
   * Gets the delimiter character specified in the options.
   * @return A valid character or a zero (null) if not specified.
   */
  public char getDelimiter() {
    if (delimiter == null) {
      return 0;
    } else {
      return delimiter.charAt(0);
    }
  }

  /**
   * Constructs an interpolator based on the specified options,
   * default value is NaturalNeighbor.
   * @param tin a valid instance of an IIncrementalTin implementation.
   * @return a valid interpolator.
   */
  public IInterpolatorOverTin getInterpolator(IIncrementalTin tin){
      switch(interpolationMethod){
        case TriangularFacet:
          return new TriangularFacetInterpolator(tin);
        case GeographicallyWeightedRegression:
          return new GwrTinInterpolator(tin);
        case NaturalNeighbor:
        default:
          return new NaturalNeighborInterpolator(tin);
      }
  }

  /**
   * Gets the interpolation method based on the specified options,
   * default value is NaturalNeighbor;
   * @return a valid enumeration value.
   */
  public InterpolationMethod getInterpolationMethod(){
    if(interpolationMethod == null){
      return InterpolationMethod.NaturalNeighbor;
    }else{
      return interpolationMethod;
    }
  }

  /**
   * Gets the constraints file (if specified).
   * @return if specified, a valid file; otherwise, a null.
   */
  public File getConstraintsFile(){
    return constraintsFile;
  }
}
