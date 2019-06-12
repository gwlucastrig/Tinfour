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
package org.tinfour.svm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import org.tinfour.gis.utils.ShapefileMetadataReporter;
import org.tinfour.svm.properties.SvmFileSpecification;
import org.tinfour.svm.properties.SvmProperties;

/**
 * Provides the main method for running the Simple Volumetric Model (SVM).
 */
public class SvmMain {

  private final static String[] usage = {
    "Usage information for Simple Volumetric Model (SVM)",
    "",
    "  -properties  <file> Input properties file path",
    "  -template           Prints an example properties file to the console",
    "  -inspect     <file or directory>   Inspects and reports on the content",
    "                      of the specified Shapefile and its associated",
    "                      DBF file.  If a directory is specified, reports",
    "                      on the content of every Shapefile in the directory"
  };

  private static void printUsageAndExit() {
    for (String s : usage) {
      System.out.println(s);
    }
    System.exit(0);  //NOPMD
  }

  private static boolean isSpecified(String[] args, String target) {
    if (args != null && target != null) {
      for (String arg : args) {
        if (target.equalsIgnoreCase(arg)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void checkForUsage(String[] args) {
    if (args == null || args.length == 0) {
      printUsageAndExit();
    } else if (isSpecified(args, "-?") || isSpecified(args, "-help")) {
      printUsageAndExit();
    }
  }

  private static void checkForTemplate(String[] args) {
    if (isSpecified(args, "-template")) {
      try (
              InputStream ins
              = SvmMain.class.getResourceAsStream("SvmTemplate.properties");) {
        int c;
        while ((c = ins.read()) >= 0) {
          System.out.append((char) c);
        }
        System.out.flush();
      } catch (IOException dontCare) {
        // no action required
      }
      System.exit(0); // NOPMD
    }
  }

  private static void checkForInspection(String[] args) throws IOException {
    int index = SvmProperties.indexArg(args, "-inspect", true);
    if (index < 0) {
      return;
    }
    File target = new File(args[index + 1]);
    if (!target.exists()) {
      throw new IllegalArgumentException("Inspection target does not exist "
              + target.getPath());
    }
    performShapefileInspection(target, System.out);
    System.exit(0); // NOPMD

  }

  private static void performShapefileInspection(
          File target, PrintStream ps) throws IOException {
    FilenameFilter filter = new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        int n = name.length();
        if (n >= 5) {
          int i = name.lastIndexOf('.');
          return (i > 0 && ".shp".equalsIgnoreCase(name.substring(i, n)));
        }
        return false;
      }
    };

    if (target.isDirectory()) {
      String[] targets = target.list(filter);
      for (String name : targets) {
        System.out.println("\n------------------------------------------------");
        File t = new File(target, name);
        ShapefileMetadataReporter reporter
                = new ShapefileMetadataReporter(t, true);
        reporter.printReport(ps);
      }
    } else {
      String name = target.getName();
      if (!filter.accept(target, name)) {
        throw new IllegalArgumentException(
                "Invalid Shapefile specification " + target.getPath());
      }
      ShapefileMetadataReporter reporter
              = new ShapefileMetadataReporter(target, true);
      reporter.printReport(ps);
    }
  }

  private SvmMain() {
    // constructor scoped to private to deter application code
    // from creating instances of this class.
  }

  /**
   * The main method for running the Simple Volumetric Model (SVM).
   *
   * @param args a valid, potentially empty array of specifications
   * @throws IOException in the event of an unrecoverable I/O condition.
   */
  public static void main(String[] args) throws IOException {
    checkForUsage(args);
    checkForTemplate(args);
    checkForInspection(args);

    Date dateOfAnalysis = new Date(); // set to clock time
    writeIntroduction(System.out, dateOfAnalysis);
    SvmProperties prop = SvmProperties.load(args);
    prop.writeSummary(System.out);

    // Check for missing input or output folders (if specified).
    // This is a frequent source of error, so we try to catch
    // it early.
    File inputFolder = prop.getInputFolder();
    if (inputFolder != null && !inputFolder.exists()) {
      System.err.println("\nInput folder not found for " + inputFolder.getPath());
      System.exit(-1);
    }

    File outputFolder = prop.getOutputFolder();
    if (outputFolder != null && !outputFolder.exists()) {
      System.err.println("\nOutput folder not found for " + outputFolder.getPath());
      System.exit(-1);
    }

    // Load the input data ----------------------------------
    SvmBathymetryData data = new SvmBathymetryData();

    List<SvmFileSpecification> bathyFiles = prop.getSampleSpecifications();
    for (SvmFileSpecification bathyFile : bathyFiles) {
      data.loadSamples(
              bathyFile.getFile(),
              bathyFile.getField(),
              bathyFile.getVerticalTransform());
    }

    List<SvmFileSpecification> supplementFiles = prop.getSupplementSpecifications();
    for (SvmFileSpecification supplementFile : supplementFiles) {
      data.loadSupplement(
              supplementFile.getFile(),
              supplementFile.getField(),
              supplementFile.getVerticalTransform());
    }

    List<SvmFileSpecification> boundaryFiles = prop.getBoundarySpecifications();
    for (SvmFileSpecification boundaryFile : boundaryFiles) {
      data.loadBoundaryConstraints(
              boundaryFile.getFile(),
              boundaryFile.getField(),
              boundaryFile.getVerticalTransform());
    }

    PrintStream reportPrintStream = System.out; // the default
    FileOutputStream reportOutputStream = null;
    File reportFile = prop.getReportFile();
    if (reportFile != null) {
      reportOutputStream = new FileOutputStream(reportFile);
      BufferedOutputStream bos = new BufferedOutputStream(reportOutputStream);
      reportPrintStream = new PrintStream(bos);
      writeIntroduction(reportPrintStream, dateOfAnalysis);
      prop.writeSummary(reportPrintStream);
      reportPrintStream.flush();
    }

    SvmComputation svmComp = new SvmComputation();
    svmComp.processVolume(reportPrintStream, prop, data);

    reportPrintStream.flush();
    if (reportOutputStream != null) {
      reportOutputStream.close();
    }

  }

  private static void writeIntroduction(PrintStream ps, Date date) {
    Locale locale = Locale.getDefault();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Simple Volumetric Model (Version 1.0 beta)");
    ps.println("");
    ps.println("Date of analysis:  " + sdFormat.format(date) + " UTC");
  }
}
