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
 * 01/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.shapefile;

/**
 * A reusable container for Shapefile feature data
 */
public class ShapefileRecord {

  public ShapefileType shapefileType;
  public long offset;
  public int recordNumber;
  public int nPoints;
  public int nParts;
  public int[] partStart; // always 1 larger than nParts
  public double[] xyz; // array of coordinates dimensioned nPoints*3 or larger
  public double x0, x1, y0, y1, z0, z1;

  /**
   * Sets the sizes for the record and allocates memory to
   * store the coordinates and part specifications (if any).
   *
   * @param nPoints the number of points in the record
   * @param nParts the number of parts in the record
   */
  void setSizes(int nPoints, int nParts) {
    if (partStart == null) {
      partStart = new int[nParts + 1];
      xyz = new double[nPoints * 3];
    } else {
      if (partStart.length < nParts + 1) {
        partStart = new int[nParts + 1];
      } else if (partStart.length > nParts + 1) {
        partStart[nParts + 1] = 0; // diagnostic
      }
      if (xyz.length < nPoints * 3) {
        xyz = new double[nPoints * 3];
      }
      this.nPoints = nPoints;
      this.nParts = nParts;
    }
  }

  void setBounds(double x0, double x1, double y0, double y1, double z0, double z1) {
    this.x0 = x0;
    this.x1 = x1;
    this.y0 = y0;
    this.y1 = y1;
    this.z0 = z0;
    this.z1 = z1;
  }

}
