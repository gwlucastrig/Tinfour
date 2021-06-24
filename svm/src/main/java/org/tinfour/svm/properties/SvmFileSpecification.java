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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.gis.utils.IVerticalCoordinateTransform;
import org.tinfour.svm.SvmBathymetryModel;

/**
 *
 */
public class SvmFileSpecification {

  private static class FixedValueTransform implements IVerticalCoordinateTransform {

    final double value;

    FixedValueTransform(double value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "Fixed value transform z'=" + value;
    }

    @Override
    public double transform(int recordIndex, double z) {
      return value;
    }
  }

  private static class LinearValueTransform implements IVerticalCoordinateTransform {

    final double scale;
    final double offset;

    LinearValueTransform(double scale, double offset) {
      this.scale = scale;
      this.offset = offset;
    }

    @Override
    public double transform(int recordIndex, double z) {
      return scale * z + offset;
    }

    @Override
    public String toString() {
      return "Linear value transform z'=" + scale + "*z+" + offset;
    }
  }

  final String key;
  final File file;
  final String field;
  final IVerticalCoordinateTransform verticalTransform;
  final SvmBathymetryModel bathymetryModel;

  /**
   * A package-scope constructor used to create specifications by the
   * SvmProperties class. The file may be remapped based on the folder
   * specification. However, if the file specification gives an absolute path,
   * the folder argument will be ignored.
   *
   * @param key the properties key that provided the file specification
   * @param list a list of arguments including the file and any auxiliary
   * fields.
   * @param folder a file reference to a folder containing the file; or a null
   * if no folder reference is to be applied.
   *
   */
  SvmFileSpecification(String key, SvmBathymetryModel bathymetryModel, List<String> list, File folder) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Invalid key");
    }
    if (list.isEmpty()) {
      throw new IllegalArgumentException("Empty specification for " + key);
    }
    this.key = key;
    this.bathymetryModel = bathymetryModel;
    String path = list.get(0);
    File test = new File(path);
    if (test.isAbsolute() || folder == null) {
      file = test;
    } else {
      file = new File(folder, path);
    }

    // see if the specification supplies a fixed value for the
    // vertical coordinate.  In such a case,  the fixedValue variable
    // will be set to a valid floating point value.  Otherwise,
    // it will remain as NaN indicating that standard processing is required.
    double fixedValue = Double.NaN;
    String s = list.get(1).trim();
    if (s.length() > 0 && !Character.isAlphabetic(s.charAt(0))) {
      // see if it's a numeric giving a fixed value specification
      try {
        fixedValue = Double.parseDouble(s);
      } catch (NumberFormatException dontCare) {
        // it's not a fixed value
        fixedValue = Double.NaN;
      }
    }

    if (Double.isNaN(fixedValue)) {
      // standard processing
      if (list.size() > 1) {
        field = list.get(1);
      } else {
        field = null;
      }

      // the the arguments list specifies a vertical coordinate transform,
      // construct an instance.  Otherwise, a coordinate transform may be
      // necessary due to the bathymetry model.
      if (list.size() > 2) {
        verticalTransform = interpretVtrans(list.get(2));
      } else {
        if (bathymetryModel == SvmBathymetryModel.Depth) {
          verticalTransform = new LinearValueTransform(-1, 0);
        } else if (bathymetryModel == SvmBathymetryModel.DepthNegative) {
          verticalTransform = new LinearValueTransform(1, 0);
        } else {
          verticalTransform = null;
        }
      }
    } else {
      field = null;
      verticalTransform = new FixedValueTransform(fixedValue);
    }
  }

  private List<String> split(String s) {
    StringBuilder sb = new StringBuilder(128);
    List<String> sList = new ArrayList<String>();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        if (sb.length() > 0) {
          sList.add(sb.toString());
          sb.setLength(0);
        }
      } else {
        sb.append(c);
      }
    }
    if (sb.length() > 0) {
      sList.add(sb.toString());
    }
    return sList;
  }

  private IVerticalCoordinateTransform interpretVtrans(String string) {

    List<String> sList = split(string);
    int n = 0;
    double[] d = new double[sList.size()];
    for (String s : sList) {
      try {
        d[n++] = Double.parseDouble(s);
      } catch (NumberFormatException nex) {
        throw new IllegalArgumentException(
                "Invalid entry where numeric expected for "
                + string.trim());
      }
    }
    if (n == 0) {
      return null;
    } else if (n == 1) {
      return new FixedValueTransform(d[0]);
    } else if (n == 2) {
      return new LinearValueTransform(d[0], d[1]);
    } else {
      throw new IllegalArgumentException("Too many specifications for "
              + string.trim());
    }
  }

  /**
   * Get the file reference from the specification
   *
   * @return a valid instance
   */
  public File getFile() {
    return file;
  }

  /**
   * Get the named data field String from the specification (if supplied)
   *
   * @return if supplied, a valid, non-empty string; otherwise, a null.
   */
  public String getField() {
    return field;
  }

  /**
   * Get the vertical coordinate transform from the specification (if supplied)
   *
   * @return if supplied, a valid instance; otherwise, a null.
   */
  public IVerticalCoordinateTransform getVerticalTransform() {
    return verticalTransform;
  }

  @Override
  public String toString() {
    String path = file.getPath();
    return "SvmInput: " + key + "=" + path;
  }
}
