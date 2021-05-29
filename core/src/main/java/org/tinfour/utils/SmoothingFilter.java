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
 * 08/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils;

import java.util.Arrays;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.IVertexValuator;

/**
 * An implementation of the vertex valuator that processes the vertices in a
 * Constrained Delaunay Triangulation and applies a low-pass filter over the
 * data.
 * <p>
 * Note that this class modifies the index values of the vertices stored in the
 * TIN. It also depends on the modified values as a way of tracking vertices.
 * Therefore, calling applications should not modify these values while the
 * smoothing filter is being used.
 */
public class SmoothingFilter implements IVertexValuator {

  final double[] zArray;
  final double zMin;
  final double zMax;
  private final double timeToConstructFilter;

  /**
   * Construct a smoothing filter. Note that this class modifies the index
   * values of the vertices stored in the TIN.
   * <p>
   * <strong>Important usage note:</strong> this constructor modifies the index
   * values of the vertices stored in the TIN. It also depends on the modified
   * values as a way of tracking vertices. Therefore, calling applications
   * should not modify these values while the smoothing filter is being used.
   * <p>
   * The vertices belonging to constraints are not smoothed, but are represented
   * with their original values by the smoothing filter.
   *
   * @param tin a valid Delaunay Triangulation
   */
  public SmoothingFilter(IIncrementalTin tin) {
    long time0 = System.nanoTime();
    // the factor of 25 iterations was obtained through trial-and-error.
    SmoothingFilterInitializer smInit = new SmoothingFilterInitializer(tin, 25);

    zArray = smInit.result;
    double z0 = Double.POSITIVE_INFINITY;
    double z1 = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < zArray.length; i++) {
      double z = zArray[i];
      if (z < z0) {
        z0 = z;
      }
      if (z > z1) {
        z1 = z;
      }
    }
    zMin = z0;
    zMax = z1;

    long time1 = System.nanoTime();
    timeToConstructFilter = (time1 - time0) / 1.0e+6;
  }


  /**
   * Construct a smoothing filter. Note that this class modifies the index
   * values of the vertices stored in the TIN.
   * <p>
   * <strong>Important usage note:</strong> this constructor modifies the index
   * values of the vertices stored in the TIN. It also depends on the modified
   * values as a way of tracking vertices. Therefore, calling applications
   * should not modify these values while the smoothing filter is being used.
   * <p>
   * The vertices belonging to constraints are not smoothed, but are represented
   * with their original values by the smoothing filter.
   * <p>
   * The number of passes determines the degree to which features are smoothed.
   * The best choice for this value depends on the requirements of the application.
   * Values in the range 5 to 40 are good candidates for investigation.
   *
   * @param tin a valid Delaunay Triangulation
   * @param nPass the number of passes the filter performs over the vertices
   * during smoothing.
   */
  public SmoothingFilter(IIncrementalTin tin, int nPass) {
    long time0 = System.nanoTime();
    // the factor of 25 iterations was obtained through trial-and-error.
    SmoothingFilterInitializer smInit = new SmoothingFilterInitializer(tin, nPass);

    zArray = smInit.result;
    double z0 = Double.POSITIVE_INFINITY;
    double z1 = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < zArray.length; i++) {
      double z = zArray[i];
      if (z < z0) {
        z0 = z;
      }
      if (z > z1) {
        z1 = z;
      }
    }
    zMin = z0;
    zMax = z1;

    long time1 = System.nanoTime();
    timeToConstructFilter = (time1 - time0) / 1.0e+6;
  }


  /**
   * Gets the time required to construct the filter, in milliseconds. Intended
   * for diagnostic and development purposes.
   *
   * @return a value in milliseconds.
   */
  public double getTimeToConstructFilter() {
    return timeToConstructFilter;
  }

  @Override
  public double value(Vertex v) {
    int index = v.getIndex();
    return zArray[index];
  }

  /**
   * Gets the array of adjustment for vertices.
   *
   * @return a valid array.
   */
  public double[] getVertexAdjustments() {
    return Arrays.copyOf(zArray, zArray.length);
  }

  /**
   * Sets the array of adjustments for vertices
   *
   * @param adjustments a valid array of same length as internal storage
   */
  public void setVertexAdjustments(double[] adjustments) {
    if (adjustments.length != zArray.length) {
      throw new IllegalArgumentException("Adjustment size "
              + adjustments.length
              + " does not match internal value "
              + zArray.length);
    }
    System.arraycopy(adjustments, 0, zArray, 0, adjustments.length);
  }

  /**
   * Gets the minimum value from the set of possible values. Due to the
   * smoothing, this value may be larger than the minimum input value.
   *
   * @return a valid floating-point value.
   */
  public double getMinZ() {
    return zMin;
  }

  /**
   * Gets the maximum value from the set of possible values. Due to the
   * smoothing, this value may be smaller than the maximum input value.
   *
   * @return a valid floating-point value.
   */
  public double getMaxZ() {
    return zMax;
  }
}
