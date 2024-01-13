/* --------------------------------------------------------------------
 * Copyright (C) 2024  Gary W. Lucas.
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
 * 01/2024  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasPoint;

/**
 * Implements logic for filtering sample points from LAS/LAZ files when
 * they are used for the Simple Volumetric Model (SVM).
 */
public class SvmLasFilter implements ILasRecordFilter {

  boolean allRecordsAccepted;
  boolean filterFirst;
  boolean filterLast;
  boolean[] cFlag = new boolean[256];

  /**
   * Construct a filter that accepts all records except those
   * that are explicitly marked as withheld.
   */
  public SvmLasFilter() {
    allRecordsAccepted = true;
  }

  /**
   * Construct a filter and optionally apply the rules specified in
   * the input string.
   *
   * @param specification an option string, potentially null or blank.
   */
  public SvmLasFilter(String specification) {
    if (specification == null || specification.isBlank()) {
      allRecordsAccepted = true;
      return;
    }
    String[] a = specification.split(",");
    for (int i = 0; i < a.length; i++) {
      String s = a[i].trim();
      if ("z".equalsIgnoreCase(s)) {
        // This option supports the legacy setting
        allRecordsAccepted = true;
      } else if ("first".equalsIgnoreCase(s)) {
        filterFirst = true;
      } else if ("last".equalsIgnoreCase(s)) {
        filterLast = true;
      } else if (s.indexOf('-') > 0) {
        String[] b = s.split("-");
        int x0 = parseClassification(b[0]);
        int x1 = parseClassification(b[1]);
        if (x1 < x0) {
          throw new IllegalArgumentException(
            "Out-of-order entry for LAZ/LAS classification range: " + s);
        }
        for (int x = x0; x <= x1; x++) {
          cFlag[x] = true;
        }
      } else {
        int x = parseClassification(s);
        cFlag[x] = true;
      }
    }
  }

  private int parseClassification(String sClassification) {
    if (sClassification == null || sClassification.isBlank()) {
      throw new IllegalArgumentException("Blank entry for LAZ/LAS classification");
    }
    String s = sClassification.trim();

    try {
      int x = Integer.parseInt(s);
      if (x < 0 || x > 255) {
        throw new IllegalArgumentException(
          "LAZ/LAZ classification out-of-range [0..255]: " + s);
      }
      return x;
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
        "Invalid numeric specification where LAS/LAZ classification expected: " + s);
    }
  }

  @Override
  public boolean accept(LasPoint record) {
    if (allRecordsAccepted) {
      return record.withheld ^ true;
    }
    if (filterFirst && record.returnNumber == 1) {
      return record.withheld ^ true;
    }
    if (filterLast && record.returnNumber == record.numberOfReturns) {
      return record.withheld ^ true;
    }
    // The AND operation ensures array index in bounds.
    if (cFlag[record.classification & 0xff]) {
      return record.withheld ^ true;
    }
    return false;
  }

  /**
   * Indicates that the filter will accept all records except those that
   * are explicitly marked as withheld.
   *
   * @return true if all records are accepted; otherwise, false
   */
  public boolean areAllRecordsAccepted() {
    return allRecordsAccepted;
  }


}
