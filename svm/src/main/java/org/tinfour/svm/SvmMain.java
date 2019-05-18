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
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import org.tinfour.svm.properties.SvmFileSpecification;
import org.tinfour.svm.properties.SvmProperties;

/**
 * Provides the main method for running the Simple Volumetric Model (SVM).
 */
public class SvmMain {

  private SvmMain(){
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
      data.loadSamples(bathyFile.getFile(), bathyFile.getField());
    }

    List<SvmFileSpecification> supplementFiles = prop.getSupplementSpecifications();
    for (SvmFileSpecification supplementFile : supplementFiles) {
      data.loadSupplement(supplementFile.getFile(), supplementFile.getField());
    }

    List<SvmFileSpecification> boundaryFiles = prop.getBoundarySpecifications();
    for (SvmFileSpecification boundaryFile : boundaryFiles) {
      data.loadBoundaryConstraints(boundaryFile.getFile(), boundaryFile.getField());
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
