/* --------------------------------------------------------------------
 * Copyright (C) 2021  Gary W. Lucas.
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
 * 05/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.shapefile;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.tinfour.io.BufferedRandomAccessFile;

/**
 * Provides utilities for creating and writing a shapefile.
 * <p>
 * At this time, this class represents only a partial implementation of the
 * shapefile standard. If there is sufficient interest from the user community,
 * it will be extended in future releases. Additionally, this implementation
 * lack numerous sanity checks and safety measures. Applications must take
 * care to ensure that it is used correctly.
 * <p>
 * A shapefile product is actually a collection of multiple files.
 * This class maintains the specified files and writes them as a group.
 * When the close method is called, all open file resources are closed.
 * <h1>Using this class</h1>
 * While Tinfour is not a Geographic Information System (GIS), some of the
 * most interesting data sets that it can be applied to are GIS products.
 * The shapefile writer is intended to support applications that may require
 * the export of Tinfour analysis products for use in GIS systems. However,
 * this implementation is limited to the most basic cases and does not
 * provide much in the way of error-checking for input.
 * <p>
 * The steps for using this class are as follows:
 * <ol>
 * <li>Specify the structure of the shapefile and its associated database
 * metadata file (DBF file) by constructing an instance of the
 * ShapefileWriterSpecification class.</li>
 * <li>Populate the ShapefileWriterSpecification with information about
 * the data fields to be used in the DFB file.</li>
 * <li>It is highly recommended that an application specify the content for
 * a projection file (.prj file) to accompany the shapefile. Many GIS
 * applications will not be able to access a shapefile without a .prj file.</li>
 * <li>Identify a path and file name for writing the shapefile and create
 * a Java File object to indicate the proper informaition</li>
 * <li>Construct an instance of the ShapefileWriter</li>
 * <li>For each feature (record) to be added to the shapefile,
 * set metadata and write geometry</li>
 * <li>Specify metadata for each record using the appropriate "setDbf"
 * calls.</li>
 * <li>Use a ShapefileRecord to specify geometry data for output and
 * pass it to the writeRecord() method to stored data</li>
 * <li>Repeat for as many records as required</li>
 * <li>Close the shapefile</li>
 * </ol>
 * The following code snippet illustrates the process. In this case,
 * we use Java's elegant try-with-resources syntax as an example.
 * This example writes only a single record, though most applications
 * will produce multiple records.
 * <p>
 * The calls to setDbfFieldValue() set the metadata data that will
 * be written to the associated .dbf file at the same time geometry
 * elements are written to the .shp file. When writeRecord() is called,
 * the ShapefileWriter stores the geometry from the application-supplied
 * ShapefileRecord
 * in the associated .shp file. It also stores the values for the
 * DBF metadata. Internally, the writer will also build a .shx (index) file.
 * All of these files are closed simultaneously at the end of the
 * try-with-resources block.
 * <pre><code>
 *     File outputFile = new File("ElevationContours.shp");
 *     String prjString; // content taken from existing file or other source.
 *     ShapefileWriterSpecification spec = new ShapefileWriterSpecification();
 *     spec.setShapefileType(ShapefileType.PolyLine);
 *     spec.addFloatingPointField("elevation" 8, 2, false);
 *     spec.setShapefilePrjContent(prjString);
 *     try(ShapefileWriter writer = new ShapefileWriter(outputFile, spec)){
 *        writer.setDbfFieldValue("elevation", 100);
 *        ShapefileRecord record = writer.createRecord(); // an empty record
 *        int nPoints; // number of points in a polyline, provided by application
 *        double []xy; // coordinates for a polyline, provided by application
 *        record.addPolyLine(nPoints, xy);
 *        writer.writeRecord(record);
 *     }catch(IOException ioex){
 *        ioex.printStackTrace(System.err);
 *     }
 * </code></pre>
 */
public class ShapefileWriter implements Closeable {

  final File folder;
  final String baseName;
  final ShapefileWriterSpecification spec;

  final BufferedRandomAccessFile shapefile;
  final BufferedRandomAccessFile shapeIndex;

  int nRecords;
  double xMin = Double.POSITIVE_INFINITY;
  double yMin = Double.POSITIVE_INFINITY;
  double zMin = Double.POSITIVE_INFINITY;
  double mMin = Double.POSITIVE_INFINITY;

  double xMax = Double.NEGATIVE_INFINITY;
  double yMax = Double.NEGATIVE_INFINITY;
  double zMax = Double.NEGATIVE_INFINITY;
  double mMax = Double.NEGATIVE_INFINITY;

  List<DbfField> dbfFields;
  DbfFileWriter dbfWriter;

  boolean isClosed;

  /**
   * Opens a set of files to output a shapefile to the specified path.
   * <p>
   * The path and names for the associated files (.dbf, .prj, .shx)
   * are derived from the path and name of the associated file.
   *
   * @param outputFile the file reference for the output shapefile;
   * this file should include the &#46;shp extension.
   * @param spec The structural specifications for the specified shapefile
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  public ShapefileWriter(File outputFile, ShapefileWriterSpecification spec) throws IOException {
    if (outputFile.exists()) {
      throw new IOException("Specified file already exists:" + outputFile.getPath());
    }
    folder = outputFile.getParentFile();
    String name = outputFile.getName();
    if (name.toLowerCase().endsWith(".shp")) {
      name = name.substring(0, name.length() - 4);
    }

    baseName = name;
    File testFile = new File(folder, baseName + ".shp");
    if (testFile.exists()) {
      throw new IOException("Specified file already exists:" + testFile.getPath());
    }

    if (!folder.exists()) {
      boolean status = folder.mkdirs();
      if (!status) {
        throw new IOException("Unable to create folder for output: " + folder.getPath());
      }
    }

    this.spec = spec;

    shapefile = initShapefile(baseName + ".shp");
    shapeIndex = initShapefile(baseName + ".shx");

    File dbfFile = new File(folder, baseName + ".dbf");
    dbfWriter = new DbfFileWriter(dbfFile, spec.fieldList);

    if (spec.prjContent != null) {
      File prjFile = new File(folder, baseName + ".prj");
      byte[] b = spec.prjContent.getBytes(StandardCharsets.ISO_8859_1);
      try (FileOutputStream fos = new FileOutputStream(prjFile);) {
        fos.write(b, 0, b.length);
      }
    }

  }

  /**
   * Create an empty record of the geometry type associated with the shapefile.
   * The record can be used for storing input for processing.
   *
   * @return a valid instance.
   */
  public ShapefileRecord createRecord() {
    return new ShapefileRecord(spec.shapefileType);
  }

  private BufferedRandomAccessFile initShapefile(String name) throws IOException {
    File file = new File(folder, name);
    BufferedRandomAccessFile braf = new BufferedRandomAccessFile(file, "rw");

    braf.writeInt(9994);
    braf.writeInt(0); // Unused
    braf.writeInt(0); // Unused
    braf.writeInt(0); // Unused
    braf.writeInt(0); // Unused
    braf.writeInt(0);  // Unused
    braf.writeInt(0); // file length, will be overwritten
    braf.leWriteInt(1000);   // version
    braf.leWriteInt(spec.shapefileType.getTypeCode());
    braf.leWriteDouble(0.0); // Xmin, will be overwritten
    braf.leWriteDouble(0.0); // Ymin, will be overwritten
    braf.leWriteDouble(0.0); // Xmax, will be overwritten
    braf.leWriteDouble(0.0); // Ymax, will be overwritten
    braf.leWriteDouble(0.0); // Zmin, may be overwritten
    braf.leWriteDouble(0.0); // Zmax, may be overwritten
    braf.leWriteDouble(0.0); // Mmin, may be overwritten
    braf.leWriteDouble(0.0); // Mmax, will may overwritten
    return braf;
  }

  /**
   * Flushes the content of the file.
   *
   * @throws IOException in the event of an unrecoverable I/O error
   */
  public void flush() throws IOException {
    shapefile.flush();
    int fileLength = (int) (shapefile.getFileSize() / 2L);
    shapefile.seek(24);
    shapefile.writeInt(fileLength);

    if (nRecords > 0) {
      shapefile.seek(36);
      shapefile.leWriteDouble(xMin); // Xmin, will be overwritten
      shapefile.leWriteDouble(yMin); // Ymin, will be overwritten
      shapefile.leWriteDouble(xMax); // Xmax, will be overwritten
      shapefile.leWriteDouble(yMax); // Ymax, will be overwritten
      if (spec.shapefileType.is3D()) {
        shapefile.leWriteDouble(zMin); // Zmin, may be overwritten
        shapefile.leWriteDouble(zMax); // Zmax, may be overwritten
      } else {
        shapefile.seek(84);
      }
      if (spec.shapefileType.hasM()) {
        shapefile.leWriteDouble(mMin); // Mmin, may be overwritten
        shapefile.leWriteDouble(mMax); // Mmax, will may overwritten
      }
    }
    shapefile.seek(shapefile.getFileSize());

    shapeIndex.seek(24);
    shapeIndex.writeInt((int) (shapeIndex.getFileSize() / 2L));
    shapeIndex.seek(shapeIndex.getFileSize());
    shapefile.flush();

  }

  @Override
  public void close() throws IOException {
    if (!isClosed) {
      flush();
      isClosed = true;
      shapefile.close();
      dbfWriter.close();
      shapeIndex.close();
    }
  }

  /**
   * Set the value of the named DBF field using the specified integer value.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, int value) {
    dbfWriter.setDbfFieldValue(name, value);
  }

  /**
   * Set the value of the named DBF field using the specified floating-point
   * value.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, double value) {
    dbfWriter.setDbfFieldValue(name, value);
  }

  /**
   * Set the value of the named DBF field using the specified string.
   * It is assumed that the indicated field is of a compatible type.
   *
   * @param name the name of the field
   * @param value the value to be set for the field.
   */
  public void setDbfFieldValue(String name, String value) {
    dbfWriter.setDbfFieldValue(name, value);
  }

  /**
   * Write the specified record to the shapefile and store any associated
   * metadata fields in the associated DBF file.
   *
   * @param record a valid record
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  public void writeRecord(ShapefileRecord record) throws IOException {
    if (record.nPoints == 0) {
      throw new IOException("Unable to write empty record");
    }
    dbfWriter.writeRecord();

    long recordOffset = shapefile.getFilePosition();
    nRecords++;
    shapefile.writeInt(nRecords);

    int contentLength = 0;

    if (record.x0 < xMin) {
      xMin = record.x0;
    }
    if (record.x1 > xMax) {
      xMax = record.x1;
    }
    if (record.y0 < yMin) {
      yMin = record.y0;
    }
    if (record.y1 > yMax) {
      yMax = record.y1;
    }
    if (spec.shapefileType.hasZ()) {
      if (record.z0 > zMin) {
        zMin = record.z0;
      }
      if (record.z1 > zMax) {
        zMax = record.z1;
      }
    }

    switch (spec.shapefileType) {
      case PolyLine:
      case Polygon:
      case PolyLineZ:
      case PolygonZ:
        // size specification ------------------
        //   4*8 for x0, y0, x1, y1
        //   4 for number of parts
        //   4 for number of points
        //   one byte each for part-start
        //   2*8 bytes each for point coordinates (x, y)
        contentLength = 4 + 32 + 4 + 4 + record.nParts * 4 + record.nPoints * 16;
        if (spec.shapefileType.hasZ()) {
          // size specification ---------------------
          //   8*2 bytes for z0, z1
          //   8 bytes each for z coordinates
          contentLength += 16 + 8 * record.nPoints;
        }
        shapefile.writeInt(contentLength / 2); // content length in 2-byte words
        shapefile.leWriteInt(spec.shapefileType.getTypeCode());
        shapefile.leWriteDouble(record.x0);
        shapefile.leWriteDouble(record.y0);
        shapefile.leWriteDouble(record.x1);
        shapefile.leWriteDouble(record.y1);
        shapefile.leWriteInt(record.nParts);
        shapefile.leWriteInt(record.nPoints);
        for (int i = 0; i < record.nParts; i++) {
          shapefile.leWriteInt(record.partStart[i]);
        }
        int k = 0;
        for (int i = 0; i < record.nPoints; i++) {
          shapefile.leWriteDouble(record.xyz[k++]);
          shapefile.leWriteDouble(record.xyz[k++]);
          k++; // hold off on writing the z coordinate
        }

        if (spec.shapefileType.hasZ()) {
          shapefile.leWriteDouble(record.z0);
          shapefile.leWriteDouble(record.z1);
          for (int i = 0; i < record.nPoints; i++) {
            shapefile.leWriteDouble(i * 3 + 2);
          }
        }
        //long filePos1 = shapefile.getFilePosition();
        shapeIndex.writeInt((int) (recordOffset / 2L));
        shapeIndex.writeInt(contentLength / 2);
        break;
      default:
        throw new IOException("Unsupported type " + spec.shapefileType);
    }

  }

}
