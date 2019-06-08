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
 * 02/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.tinfour.gis.shapefile.DbfField;
import org.tinfour.gis.shapefile.DbfFieldDate;
import org.tinfour.gis.shapefile.DbfFieldDouble;
import org.tinfour.gis.shapefile.DbfFieldInt;
import org.tinfour.gis.shapefile.DbfFileReader;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.gis.shapefile.ShapefileType;

/**
 * Provides utilities for extracting and printing the metadata associated with a
 * Shapefile and its companion DBF file.
 */
public class ShapefileMetadataReporter {

  private static class GeometryReport {

    double zMin;
    double zMax;
    double zSum;
    int nZ;
    int nRecords;
    int nParts;
    int nPoints;

    GeometryReport() {
      zMin = Double.POSITIVE_INFINITY;
      zMax = Double.NEGATIVE_INFINITY;
    }

    void tabulateZ(double z) {
      nZ++;
      zSum += z;
      if (z < zMin) {
        zMin = z;
      }
      if (z > zMax) {
        zMax = z;
      }
    }

    void tabulateRecord(int nParts, int nPoints) {
      this.nRecords++;
      this.nParts += nParts;
      this.nPoints += nPoints;
    }

    double[] getStatsZ() {
      double[] zStat = new double[3];
      if (nZ == 0) {
        zStat[0] = Double.NaN;
        zStat[1] = Double.NaN;
        zStat[2] = Double.NaN;
      } else {
        zStat[0] = zMin;
        zStat[1] = zMax;
        zStat[2] = zSum / nZ;
      }
      return zStat;
    }
  }

  private final File target;
  private final boolean suppressEngineeringNotation;

  /**
   * Constructs a reporter for the specified Shapefile
   *
   * @param target a valid Shapefile
   * @param suppressEngineeringNotation indicates that if the Shapefile includes
   * fields formatted with engineering (exponential) notation, the fields will
   * be formatted as simple decimal values.
   */
  public ShapefileMetadataReporter(File target, boolean suppressEngineeringNotation) {
    this.target = target;
    this.suppressEngineeringNotation = suppressEngineeringNotation;

  }

  /**
   * Prints a metadata report for the Shapefile to the specified print stream.
   *
   * @param ps a valid print stream to receive the report
   * @throws IOException in the event of a non-recoverable I/O condition.
   */
  public void printReport(PrintStream ps) throws IOException {
    ps.format("Shapefile Name:   %s%n", target.getName());
    try (ShapefileReader reader = new ShapefileReader(target)) {
      ShapefileType shapeType = reader.getShapefileType();
      ps.format("Shapefiile Type:  %s%s%n", shapeType,
              shapeType.is3D() ? "    (Z coordinate supplied)" : "");
      ps.format("Bounds%n");
      ps.format("   X Min,Max: %15.6f, %15.6f%n", reader.getMinX(), reader.getMaxX());
      ps.format("   Y Min,Max: %15.6f, %15.6f%n", reader.getMinY(), reader.getMaxY());
      if (shapeType.is3D()) {
        double zMax = reader.getMaxZ();
        double zMin = reader.getMinZ();
        if (zMax < -1.0e-308) {
          zMax = Double.NaN;
        }
        if (zMin < -1.0e-308) {
          zMin = Double.NaN;
        }
        ps.format("   Z Min,Max: %15.6f, %15.6f%n", zMin, zMax);
      }
      GeometryReport report = surveyShapeGeometry(reader);
      double[] zStat = report.getStatsZ();
      ps.format("%nSurvey results %n");
      ps.format("  N Records: %14d%n", report.nRecords);
      ps.format("  N Parts:   %14d%n", report.nParts);
      ps.format("  N Points:  %14d%n", report.nPoints);
      if (shapeType.is3D()) {
        ps.format("  Z Min:     %20.6f%n", zStat[0]);
        ps.format("  Z Max:     %20.6f%n", zStat[1]);
        ps.format("  Z Avg:     %20.6f%n", zStat[2]);
      }

      reportDBF(ps, reader);
    }

  }

  private void reportDBF(PrintStream ps, ShapefileReader reader) 
          throws IOException {
    try (DbfFileReader dbf = reader.getDbfFileReader()) {
      List<DbfField> fields = dbf.getFields();
      int nFields = fields.size();
      int nRecords = dbf.getRecordCount();
      ps.format("%nAnalysis of DBF file %n");
      ps.format("N Records:  %8d%n", nRecords);
      ps.format("N Fields:   %8d%n", nFields);
      int nameLen = 12;
      for (int i = 0; i < nFields; i++) {
        DbfField field = fields.get(i);
        String name = field.getName();
        if (name.length() > nameLen) {
          nameLen = name.length();
        }
      }
      String fieldFmt = String.format("%%3d. %%-%ds %%c %%s", nameLen);
      StringBuilder pad = new StringBuilder(nameLen);
      for (int i = 0; i < nameLen; i++) {
        pad.append(' ');
      }
      ps.format("     %s     width             min                   max"
              + "           unique values%n",
              pad.toString());
      for (int i = 0; i < nFields; i++) {
        DbfField field = fields.get(i);
        char fieldType = field.getFieldType();
        int fLen = field.getFieldLength();
        String lenStr;
        if (fieldType == 'F') {
          lenStr = String.format("%4d.%02d", fLen, field.getFieldDecimalCount());
        } else {
          lenStr = String.format("%4d   ", fLen);
        }
        ps.format(fieldFmt, i, field.getName(), fieldType, lenStr);
        if (fieldType == 'F') {
          reportFieldF(ps, dbf, nRecords, field);
        } else if (fieldType == 'N') {
          reportFieldN(ps, dbf, nRecords, field);
        } else if (fieldType == 'C') {
          reportFieldC(ps, dbf, nRecords, field);
        } else if (fieldType == 'D') {
          reportFieldD(ps, dbf, nRecords, field);
        }

        ps.format("%n");
      }

    }
  }

  private void reportFieldF(
          PrintStream ps, DbfFileReader dbf, int nRecords, DbfField field)
          throws IOException {

    if (!(field instanceof DbfFieldDouble)) {
      return;
    }

    DbfFieldDouble dField = (DbfFieldDouble) field;
    double vArray[] = dField.getUniqueValueArray(dbf);
    if (vArray.length == 0) {
      return;
    }
    Double vMin = vArray[0];
    Double vMax = vArray[vArray.length - 1];

    // The DBF standard doesn't specify engineering notation, but 
    // it can be detected by reading the string content of the data.
    // I suppose a wacky implementation could even mix formats.
    boolean engineeringNotation = dField.usesEngineeringNotation();
    if (this.suppressEngineeringNotation) {
      engineeringNotation = false;
    }

    String fmtN = "%20.6f";
    if (engineeringNotation) {
      int nCol = field.getFieldLength();
      int nDec = field.getFieldDecimalCount();
      if (nCol < 20) {
        nCol = 20; // to provide column alignment across multiple fields
      }
      fmtN = String.format("%%%d.%de", nCol, nDec);
    }

    String fmt = String.format(" %s  %s  %%12d", fmtN, fmtN);
    //ps.format(" %20.6f  %20.6f  %12d", vMin, vMax, nUniqueValues);
    ps.format(fmt, vMin, vMax, vArray.length);
  }

  private void reportFieldN(
          PrintStream ps, DbfFileReader dbf, int nRecords, DbfField field)
          throws IOException {
    if (!(field instanceof DbfFieldInt)) {
      return;
    }

    DbfFieldInt iField = (DbfFieldInt) field;
    int[] vArray = iField.getUniqueValueArray(dbf);
    if (vArray.length == 0) {
      return;
    }
    int vMin = vArray[0];
    int vMax = vArray[vArray.length - 1];

    ps.format(" %13d         %13d         %12d", vMin, vMax, vArray.length);

  }

  private void reportFieldC(
          PrintStream ps, DbfFileReader dbf, int nRecords, DbfField field)
          throws IOException {

    List<String> list = field.getUniqueValues(dbf);
    if (list.isEmpty()) {
      return;
    }

    int nUniqueValues = list.size();
    String sMin = list.get(0);
    String sMax = list.get(nUniqueValues - 1);

    ps.format(" %-20s  %-20s  %12d", sMin, sMax, nUniqueValues);

  }

  private void reportFieldD(
          PrintStream ps, DbfFileReader dbf, int nRecords, DbfField field)
          throws IOException {

    if (!(field instanceof DbfFieldDate)) {
      return;
    }

    DbfFieldDate dField = (DbfFieldDate) field;

    ZonedDateTime vArray[] = dField.getUniqueValueArray(dbf);
    if (vArray.length == 0) {
      return;
    }

    int nUniqueValues = vArray.length;
    ZonedDateTime dMin = vArray[0];
    ZonedDateTime dMax = vArray[nUniqueValues - 1];

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    String sMin = dMin.format(formatter);
    String sMax = dMax.format(formatter);

    ps.format(" %-20s  %-20s  %12d", sMin, sMax, nUniqueValues);
  }

  private GeometryReport surveyShapeGeometry(ShapefileReader reader)
          throws IOException {
    GeometryReport report = new GeometryReport();
    boolean hasZ = reader.getShapefileType().is3D();

    ShapefileRecord record = null;
    while (reader.hasNext()) {
      record = reader.readNextRecord(record);
      report.tabulateRecord(record.nParts, record.nPoints);
      if (hasZ) {
        for (int iPoint = 0; iPoint < record.nPoints; iPoint++) {
          double z = record.xyz[iPoint * 3 + 2];
          report.tabulateZ(z);

        }
      }
    }

    return report;
  }
}
