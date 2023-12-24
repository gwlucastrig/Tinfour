/* --------------------------------------------------------------------
 * Copyright 2017 Gary W. Lucas.
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
 * 05/2021  G. Lucas     Expanded to support writing operations
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.shapefile;

import java.util.Arrays;

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

  ShapefileRecord(ShapefileType shapefileType) {
    this.shapefileType = shapefileType;
  }

  /**
   * Sets the sizes for the record and allocates memory to
   * store the coordinates and part specifications (if any).
   *
   * @param nPoints the number of points in the record
   * @param nParts the number of parts in the record
   */
  void setSizes(int nPoints, int nParts) {
    this.nPoints = nPoints;
    this.nParts = nParts;
    if (partStart == null) {
      partStart = new int[nParts + 1];
      xyz = new double[nPoints * 3];
    } else {
      if (partStart.length < nParts + 1) {
        partStart = Arrays.copyOf(partStart, nParts + 1);
      } else if (partStart.length > nParts + 1) {
        partStart[nParts + 1] = 0; // diagnostic
      }
      if (xyz.length < nPoints * 3) {
        xyz = Arrays.copyOf(xyz, nPoints * 3);
      }
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

  /**
   * Adds a new polygon to current Shapefile record. The specified
   * points are provided in a one-dimensional array. If the Shapefile
   * does not have z coordinates, then the array is given as
   * (x0, y0, x1, y1, etc.). If the Shapefile has z coordinates, then the
   * array is given as (x0, y0, z0, x1, y1, z1, etc.).
   * The Shapefile specification allows for duplicate points, but requires
   * that the input polygon does not self-intersect and forms a non-zero area
   * (see Shapefile specification, pg. 8, "Polygon").
   * <p>
   * At this time, this class does not implement geometry checking for the
   * inputs.
   *
   * @param nPointsInput the number of points to be specified for this polygon
   * @param xyzInput the input coordinates
   * @param nested indicates that this polygon is a "hole" and is nested inside
   * another polygon.
   */
  public void addPolygon(int nPointsInput, double[] xyzInput, boolean nested) {
    switch (shapefileType) {
      case PolyLineZ:
      case PolyLine:
        if (nested) {
          throw new IllegalArgumentException(
            "The shapefile type " + shapefileType.name()
            + " does not support nested polygons");
        }
        break;
      case PolygonZ:
      case Polygon:
        break; // no action required
      default:
        throw new IllegalArgumentException(
          "Method not supported for shapefile type " + shapefileType.name());
    }

    if (nPointsInput < 3) {
      throw new IllegalArgumentException("Input polygon must contain at least 3 unique points");
    }
    int nCoordinates = this.shapefileType.hasZ() ? 3 : 2;
    int n = nPointsInput;

    // beacuse we will be subtracting the anchor coordinates
    // from all points, the first and last line segments will drop out
    // of the area computation.
    double area = 0;
    double xAnchor = xyzInput[0];
    double yAnchor = xyzInput[1];
    for (int i = 1; i < n - 1; i++) {
      double px0 = xyzInput[i * nCoordinates] - xAnchor;
      double py0 = xyzInput[i * nCoordinates + 1] - yAnchor;
      double px1 = xyzInput[(i + 1) * nCoordinates] - xAnchor;
      double py1 = xyzInput[(i + 1) * nCoordinates + 1] - yAnchor;
      area += px0 * py1 - px1 * py0;
    }
    if (nPoints == 0) {
      x0 = xyzInput[0];
      y0 = xyzInput[1];
      x1 = x0;
      y1 = y0;
    }
    for (int i = 0; i < n; i++) {
      double px = xyzInput[i * nCoordinates];
      double py = xyzInput[i * nCoordinates + 1];
      if (px < x0) {
        x0 = px;
      } else if (px > x1) {
        x1 = px;
      }
      if (py < y0) {
        y0 = py;
      } else if (py > y1) {
        y1 = py;
      }
    }
    if (shapefileType.hasZ()) {
      if (nPoints == 0) {
        z0 = xyzInput[2];
        z1 = z0;
      }
      for (int i = 0; i < n; i++) {
        double pz = xyzInput[i * nCoordinates + 2];
        if (pz < z0) {
          z0 = pz;
        } else if (pz > z1) {
          z1 = pz;
        }
      }
    }

    // If there were any infinities or NaN's in the input, the area
    // will have a non-finite value
    if (!Double.isFinite(area)) {
      throw new IllegalArgumentException("Coordinates of infinity or NaN not allowed");
    }

    if (area == 0) {
      throw new IllegalArgumentException("Zero area polygon not allowed");
    }

    boolean reversalRequired;
    if (nested) {
      // the feature is a hole, Shapefiles expect holes in counterclockwise order.
      // if the polygon is in clockwise order, a reversal is required
      reversalRequired = area < 0;
    } else {
      // the feature is not a  hole, Shapefiles expect primary polygons
      // to be in clockwise order. if the polygon is in counterclockwise
      // order, a reversal is required
      reversalRequired = area > 0;
    }

    // Transcribe the coordinates to the new polygon
    // The setSizes will allocate storage and record the indexing for the
    // polygon.   Note then when we store coordinates, the xyz[] output
    // array is always dimensioned to 3*n (even when we don't store z's).
    int nP = nPoints; // record pre-addition point count
    setSizes(nPoints + n, nParts + 1);
    this.partStart[nParts - 1] = nP;
    if (reversalRequired) {
      for (int i = 0; i < n; i++) {
        int iOutput = (i + nP) * 3;
        int iInput = (n - 1 - i) * nCoordinates;
        xyz[iOutput++] = xyzInput[iInput];
        xyz[iOutput++] = xyzInput[iInput + 1];
        if (shapefileType.hasZ()) {
          xyz[iOutput] = xyzInput[iInput + 2];
        }
      }
    } else {
      for (int i = 0; i < n; i++) {
        int iOutput = (i + nP) * 3;
        int iInput = i * nCoordinates;
        xyz[iOutput] = xyzInput[iInput];
        xyz[iOutput + 1] = xyzInput[iInput + 1];
        if (shapefileType.hasZ()) {
          xyz[iOutput + 2] = xyz[iInput + 2];
        }
      }
    }
  }

  /**
   * Adds a new polygon to current Shapefile record. The specified
   * points are provided in a one-dimensional array. If the Shapefile
   * does not have z coordinates, then the array is given as
   * (x0, y0, x1, y1, etc.). If the Shapefile has z coordinates, then the
   * array is given as (x0, y0, z0, x1, y1, z1, etc.).
   * The Shapefile specification allows for duplicate points, but requires
   * that the input polygon does not self-intersect and forms a non-zero area
   * (see Shapefile specification, pg. 8, "Polygon").
   * <p>
   * At this time, this class does not implement geometry checking for the
   * inputs.
   *
   * @param nPointsInput the number of points to be specified for this polygon
   * @param xyzInput the input coordinates
   * another polygon.
   */
  public void addPolyLine(int nPointsInput, double[] xyzInput) {
    switch (shapefileType) {
      case PolyLineZ:
      case PolyLine:
        break; // no action required
      case PolygonZ:
      case Polygon:
        addPolygon(nPointsInput, xyzInput, false);
        return;
      default:
        throw new IllegalArgumentException(
          "Method not supported for shapefile type " + shapefileType.name());
    }

    if (nPointsInput < 2) {
      throw new IllegalArgumentException(
        "Input polygon must contain at least 2 unique points");
    }
    int nCoordinates = this.shapefileType.hasZ() ? 3 : 2;
    int n = nPointsInput;

    if (nPoints == 0) {
      x0 = xyzInput[0];
      y0 = xyzInput[1];
      x1 = x0;
      y1 = y0;
      if (shapefileType.hasZ()) {
        z0 = xyzInput[2];
        z1 = z0;
      }
    }
    for (int i = 0; i < n; i++) {
      double px = xyzInput[i * nCoordinates];
      double py = xyzInput[i * nCoordinates + 1];
      if (px < x0) {
        x0 = px;
      } else if (px > x1) {
        x1 = px;
      }
      if (py < y0) {
        y0 = py;
      } else if (py > y1) {
        y1 = py;
      }
    }
    if (shapefileType.hasZ()) {
      for (int i = 0; i < n; i++) {
        double pz = xyzInput[i * nCoordinates + 2];
        if (pz < z0) {
          z0 = pz;
        } else if (pz > z1) {
          z1 = pz;
        }
      }
    }

    // Transcribe the coordinates to the new polyline
    // The setSizes will allocate storage and record the indexing for the
    // polygon.   Note then when we store coordinates, the xyz[] output
    // array is always dimensioned to 3*n (even when we don't store z's).
    int nP = nPoints; // record pre-addition point count
    setSizes(nPoints + n, nParts + 1);
    this.partStart[nParts - 1] = nP;

    for (int i = 0; i < n; i++) {
      int iOutput = (i + nP) * 3;
      int iInput = i * nCoordinates;
      xyz[iOutput] = xyzInput[iInput];
      xyz[iOutput + 1] = xyzInput[iInput + 1];
      if (shapefileType.hasZ()) {
        xyz[iOutput + 2] = xyzInput[iInput + 2];
      }
    }
  }

  /**
   * Clears the content of the current instance and prepares it for potential
   * reuse.
   */
  public void clear() {
    nParts = 0;
    nPoints = 0;
  }

  /**
   * Gets an array of coordinate points corresponding to the specified part
   * index. If the shapefile type has Z coordinates, this array
   * is of dimension n*3 where n is the number of points in the part.
   * If the shapefile type does not have Z coordinates, this array
   * is of dimension n*2.
   *
   * @param iPart the part index
   * @return if successful, a valid array of doubles
   */
  public double[] getCoordinatesForPart(int iPart) {
    if (!shapefileType.isPolyLine() && !shapefileType.isPolygon()) {
      throw new IllegalArgumentException("Shapefile type "
        + shapefileType.name()
        + " does not use a multi-part structure");
    }
    int i0 = partStart[iPart];
    int i1 = partStart[iPart + 1];
    int n = i1 - i0;
    double[] c;
    if (shapefileType.hasZ()) {
      c = new double[n * 3];
      System.arraycopy(xyz, i0 * 3, c, 0, n * 3);
    } else {
      c = new double[n * 2];
      for (int i = 0; i < n; i++) {
        int index = (i + i0) * 3;
        c[i * 2] = xyz[index];
        c[i * 2 + 1] = xyz[index + 1];
      }
    }
    return c;
  }
}
