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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import tinfour.io.BufferedRandomAccessReader;

/**
 * Provides a partial implementation of a Shapefile reader intended to
 * support testing of the Constrained Delaunay Triangulation feature of the
 * Tinfour software library. The present version is based on the assumption
 * that the Shapefile supplies constraints. More general applicastions are
 * not currently supported. Only a few Shapefile
 * types are supported.
 */
public class ShapefileReader implements Closeable{

  private final BufferedRandomAccessReader raf;
  final int fileLength; // 16-bit words
  final long fileLengthInBytes;
  final int version;
  ShapefileType shapefileType;
  private final double minX;
  private final double maxX;
  private final double minY;
  private final double maxY;
  private double minZ;
  private double maxZ;

  private int nPointsTotal;
  private int nPartsTotal;

  public ShapefileReader(File file) throws IOException {
    raf = new BufferedRandomAccessReader(file);

    int fileCode = raf.readIntBigEndian();
    if (fileCode != 9994) {
      throw new IOException("Specified file is not a Shapefile " + file.getPath());
    }
    raf.seek(24);
    fileLength = raf.readIntBigEndian();
    fileLengthInBytes = ((long) fileLength) * 2L;
    version = raf.readInt();
    int shapeTypeCode = raf.readInt();
    shapefileType = ShapefileType.getShapefileType(shapeTypeCode);
    if (shapefileType == null) {
      throw new IOException("Invalid Shapefile type code " + shapeTypeCode);
    }

    minX = raf.readDouble();
    minY = raf.readDouble();
    maxX = raf.readDouble();
    maxY = raf.readDouble();
    minZ = raf.readDouble();
    maxZ = raf.readDouble();
    // skip minM and maxM
    // minM = raf.readDouble();
    // maxM = raf.readDouble();
    raf.skipBytes(16);

    // The minZ and maxZ are not always defined in a Shapefile, even
    // if the geometry type should supply z coordinates.
    if (minZ == maxZ && minZ == 0) {
      minZ = Double.POSITIVE_INFINITY;
      maxZ = Double.NEGATIVE_INFINITY;
    }

  }

  /**
   * Close the associated file
   *
   * @throws IOException in the event of an unexpected I/O exception
   */
  @Override
  public void close() throws IOException {
    raf.close();
  }

  /**
   * Gets the minimum value for the x coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the minimum value for the x coordinates in the file
   */
  public double getMinX() {
    return minX;
  }

  /**
   * Gets the maximum value for the x coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the maximum value for the x coordinates in the file
   */
  public double getMaxX() {
    return maxX;
  }

  /**
   * Gets the minimum value for the y coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the minimum value for the y coordinates in the file
   */
  public double getMinY() {
    return minY;
  }

  /**
   * Gets the maximum value for the y coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the maximum value for the y coordinates in the file.
   */
  public double getMaxY() {
    return maxY;
  }

  /**
   * Gets the minimum value for the z coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the minimum value for the z coordinates in the file.
   */
  public double getMinZ() {
    return minZ;
  }

  /**
   * Gets the maximum value for the z coordinates of the points in the Shapefile
   * as specified in the Shapefile-standard file header.
   *
   * @return the maximum value for the z coordinates in the file.
   */
  public double getMaxZ() {
    return maxZ;
  }

  /**
   * Reads the next record in the Shapefile. This method takes a
   * reusable instance of the ShapefileRecord class. If a null is
   * passed in, it creates a new instance. If a valid reference is supplied,
   * the method returns the reference that was supplied.
   *
   * @param pRecord a reusable instance to store data, or a null
   * if the method is to allocate a new instance.
   * @return if successful, a valid instance of ShapefileRecord
   * @throws IOException in the event of a file format error or unepected
   * I/O condition
   */
  @SuppressWarnings("PMD.SwitchDensity")
  public ShapefileRecord readNextRecord(ShapefileRecord pRecord) throws IOException {
    ShapefileRecord record = pRecord;
    if (record == null) {
      record = new ShapefileRecord();
    }
    record.shapefileType = shapefileType;

    long offset0 = raf.getFilePosition();
    int recNo = raf.readIntBigEndian();
    int recLen = raf.readIntBigEndian();
    record.recordNumber = recNo;
    record.offset = offset0;
    int stc = raf.readInt();
    if (stc != shapefileType.getTypeCode()) {
      throw new IOException(
        "Error reading Shapefile record, typecode mismatch, found " + stc
        + ", expected " + shapefileType.getTypeCode());
    }
    switch (shapefileType) {
        case Point:
        // simple case, but we populate other record items for consistency
        record.setSizes(1,1);
        record.nParts = 1;
        record.nPoints = 1;
        record.partStart[1] = 1;
        record.x0 = raf.readDouble();
        record.y0 = raf.readDouble();
        record.x1 = record.x0;
        record.y1 = record.y0;
        record.xyz[0] = record.x0;
        record.xyz[1] = record.y0;        
        break;
      case PointZ:
        // simple case, but we populate other record items for consistency
        record.setSizes(1, 1);
        record.nParts = 1;
        record.nPoints = 1;
        record.partStart[1] = 1;
        record.x0 = raf.readDouble();
        record.y0 = raf.readDouble();
        record.z0 = raf.readDouble();
        record.x1 = record.x0;
        record.y1 = record.y0;
        record.z1 = record.z0;
        record.xyz[0] = record.x0;
        record.xyz[1] = record.y0;
        record.xyz[2] = record.z0;
        // there is also a measure, it is not processed at this time.
        break;
      case PolyLineZ:
      case PolygonZ: {
        record.x0 = raf.readDouble();
        record.y0 = raf.readDouble();
        record.x1 = raf.readDouble();
        record.y1 = raf.readDouble();
        int nParts = raf.readInt();
        int nPoints = raf.readInt();
        record.setSizes(nPoints, nParts);
        int[] partStart = record.partStart;
        double[] xyz = record.xyz;
        nPointsTotal += nPoints;
        nPartsTotal += nParts;
        for (int iPart = 0; iPart < nParts; iPart++) {
          partStart[iPart] = raf.readInt();
        }
        partStart[nParts] = nPoints;

        int k = 0;
        for (int i = 0; i < nPoints; i++) {
          xyz[k++] = raf.readDouble();
          xyz[k++] = raf.readDouble();
          k++;
        }
        record.z0 = raf.readDouble();
        record.z1 = raf.readDouble();

        if (record.z0 < minZ) {
          minZ = record.z0;
        }
        if (record.z1 > maxZ) {
          maxZ = record.z1;
        }
        for (int iPart = 0; iPart < nParts; iPart++) {
          int n = partStart[iPart + 1] - partStart[iPart];
          for (int i = 0; i < n; i++) {
            xyz[i * 3 + 2] = raf.readDouble();
          }
        }
      }
      break;

      case PolyLine:
      case Polygon: {
        record.x0 = raf.readDouble();
        record.y0 = raf.readDouble();
        record.x1 = raf.readDouble();
        record.y1 = raf.readDouble();
        int nParts = raf.readInt();
        int nPoints = raf.readInt();
        record.setSizes(nPoints, nParts);
        nPointsTotal += nPoints;
        nPartsTotal += nParts;
        for (int iPart = 0; iPart < nParts; iPart++) {
          record.partStart[iPart] = raf.readInt();
        }
        record.partStart[nParts] = nPoints;

        int k = 0;
        for (int i = 0; i < nPoints; i++) {
          record.xyz[k++] = raf.readDouble();
          record.xyz[k++] = raf.readDouble();
          record.xyz[k++] = 0; // undefined
        }
      }
      break;

      default:
        throw new IOException("Non-supported Shapefile type " + shapefileType);
    }

    raf.seek(offset0 + 8 + recLen * 2);
    return record;
  }

  /**
   * Checks to see if there are any more records remaining to be read
   *
   * @return true if more records remain; otherwise false.
   */
  public boolean hasNext() {
    long pos = raf.getFilePosition();
    return (fileLengthInBytes - pos) > 8;
  }

  /**
   * Gets the total number of points read from the Shapefile; or zero
   * if the content of the Shapefile hasn't been read.
   *
   * @return a positive integer.
   */
  public int getTotalPointCount() {
    return nPointsTotal;
  }

  /**
   * Gets the total number of polyglines read from the Shapefile; or zero
   * if the content of the Shapefile hasn't been read.
   *
   * @return a positive integer.
   */
  public int getTotalPartCount() {
    return nPartsTotal;
  }

  /**
   * Get the feature type of the Shapefile.
   *
   * @return a valid enumeration instance.
   */
  public ShapefileType getShapefileType() {
    return shapefileType;
  }

}
