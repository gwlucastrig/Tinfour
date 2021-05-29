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
 *   Future Work: This module should be expanded to include processing for
 *   the Well-Known Format used by .prj files.  At the very least, it
 *   should be able to obtain
 *      raw data (key-value elements)
 *      linear units (from UNIT specification)
 *      whether geographic coordinates are used.
 *   A few other classes have some logic related to the PRJ file, this
 *   should all be centralized and included here.
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.shapefile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import org.tinfour.io.BufferedRandomAccessReader;

/**
 * Provides a partial implementation of a Shapefile reader intended to support
 * testing of the Constrained Delaunay Triangulation feature of the Tinfour
 * software library. The present version is based on the assumption that the
 * Shapefile supplies constraints. More general applicastions are not currently
 * supported. Only a few Shapefile types are supported.
 */
public class ShapefileReader implements Closeable {

  private final File file;
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
    this.file = file;
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
   * Reads the next record in the Shapefile. This method takes a reusable
   * instance of the ShapefileRecord class. If a null is passed in, it creates a
   * new instance. If a valid reference is supplied, the method returns the
   * reference that was supplied.
   *
   * @param pRecord a reusable instance to store data, or a null if the method
   * is to allocate a new instance.
   * @return if successful, a valid instance of ShapefileRecord
   * @throws IOException in the event of a file format error or unepected I/O
   * condition
   */
  @SuppressWarnings("PMD.SwitchDensity")
  public ShapefileRecord readNextRecord(ShapefileRecord pRecord) throws IOException {
    ShapefileRecord record = pRecord;
    if (record == null) {
      record = new ShapefileRecord(shapefileType);
    }else{
       record.shapefileType = shapefileType;
    }

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
        record.setSizes(1, 1);
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
   * Gets the total number of points read from the Shapefile; or zero if the
   * content of the Shapefile hasn't been read.
   *
   * @return a positive integer.
   */
  public int getTotalPointCount() {
    return nPointsTotal;
  }

  /**
   * Gets the total number of polyglines read from the Shapefile; or zero if the
   * content of the Shapefile hasn't been read.
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

  @Override
  public String toString() {
    return "ShapefileReader " + this.shapefileType + " " + file.getName();
  }

  /**
   * Get a reference to the file that shares the same root name as the Shapefile
   * but has an alternate extension. For example, this method could be used to
   * get a reference to the DBF or PRJ file.
   * <p>
   * Capitalization does not matter under Windows, but does apply under Linux
   * operating systems. Therefore, this method will try to find the matching
   * file based on the following three rules:
   * <ol>
   * <li>Look for a file with an extension having the same capitalization as the
   * main Shapefile</li>
   * <li>Look for a file having the extension as all lower case letters</li>
   * <li>Look for a file having the extension with all upper case letters</li>
   * </ol>
   * While there are other possible capitalization configurations, writing code
   * to support them seems silly and is not implemented at this time.
   *
   * @param extension a string giving the target extension (do not include period)
   * @return if found, a valid instance; otherwise, a null
   */
  public File getCoFile(String extension) {
    if (extension == null || extension.length() != 3) {
      return null; // not a valid Shapefile convention
    }
    String path = file.getPath();
    int rootLen = path.lastIndexOf('.');
    if (rootLen != path.length() - 4) {
      return null; // invalid input, not expected
    }
    rootLen++;
    StringBuilder sb = new StringBuilder(rootLen + 3);
    for (int i = 0; i < rootLen; i++) {
      sb.append(path.charAt(i));
    }
    for (int i = 0; i < 3; i++) {
      char c = path.charAt(i + rootLen);
      char x = extension.charAt(i);
      if (Character.isUpperCase(c) && Character.isLowerCase(x)) {
        sb.append(Character.toUpperCase(x));
      } else if (Character.isLowerCase(c) && Character.isUpperCase(x)) {
        sb.append(Character.toLowerCase(x));
      } else {
        sb.append(x);
      }
    }

    File target = new File(sb.toString());
    if (target.exists()) {
      return target;
    }

    String rootName = path.substring(0, rootLen);
    String test = rootName + extension.toLowerCase();
    target = new File(test);
    if (target.exists()) {
      return target;
    }

    test = rootName + extension.toUpperCase();
    target = new File(test);
    if (target.exists()) {
      return target;
    }

    return null;
  }

  /**
   * Get an DBF file reader for the current Shapefile
   * @return if successful, a valid DbfFileReader instance.
   * @throws IOException if the DBF file cannot be opened
   */
  public DbfFileReader getDbfFileReader() throws IOException {
    File target = getCoFile("DBF");
    if(target==null){
      throw new IOException("DBF file not found for "+file.getName());
    }

    return new DbfFileReader(target);
  }

  /**
   * Get the file associated with the Shapefile.
   * @return a valid instance
   */
  public File getFile(){
    return file;
  }

  /**
   * Gets the current size of the main Shapefile in bytes.
   *
   * @return A long integer giving file size in bytes.
   */
  public long getFileSize(){
    if(raf==null){
      return  0;
    }
    return raf.getFileSize();
  }

   /**
   * Provides the current position within the main Shapefile.
   *
   * @return a long integer value giving offset in bytes from beginning of file.
   */
  public long getFilePosition(){
    if(raf==null){
      return 0;
    }
    return raf.getFilePosition();
  }
}
