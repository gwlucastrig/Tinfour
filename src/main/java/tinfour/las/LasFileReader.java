/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 02/2015 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import tinfour.utils.LinearUnits;

/**
 * Reads the content of a LAS file. The file is divided into a
 * header and a variable sized set of records each corresponding
 * to a distinct lidar measurement. The header metadata is
 * read by the constructor. The records must be read one-at-a-time
 * through calls to the readRecord method.
 * <p>This code is based on information that
 * is given in LAS Specification Version 1.4-R13, 15 July 2013.
 */
@SuppressWarnings("PMD.UnusedPrivateField")
public class LasFileReader {

  private static final int BIT4 = 0x10;
  private static final int BIT1 = 0x01;

  /**
   * Provides definitions for the alternate methods for specifying
   * a coordinate reference system.
   */
  public enum CoordinateReferenceSystemOption {
    /** The LAS file uses GeoTIFF tags to identify CRS */
    GeoTIFF,
    /** The LAS file used Well-Known-Text to identify CRS */
    WKT
  };





    // For this block of variables, I have elected to avoid
  // Javdoc.  The variable names are self-explanatory and
  // the code is easier to read without the clutter of documentation.
  // For definitions, see the LAS Specification.
  private String fileSignature;
  int fileSourceID;
  int globalEncoding;
  int versionMajor;
  int versionMinor;
  String systemIdentifier;
  String generatingSoftware;
  int fileCreationDayOfYear;
  int fileCreationYear;
  Date fileCreationDate;
  int headerSize;
  long offsetToPointData;
  long numberVariableLengthRecords;
  int pointDataRecordFormat;
  int pointDataRecordLength;
  long legacyNumberOfPointRecords;
  long[] legacyNumberOfPointsByReturn;
  double xScaleFactor;
  double yScaleFactor;
  double zScaleFactor;
  double xOffset;
  double yOffset;
  double zOffset;
  private double minX;
  private double maxX;
  private double minY;
  private double maxY;
  private double minZ;
  private double maxZ;
  private long startOfWaveformDataPacket;
  private long startOfWaveformDataPacketRec;
  private long startOfExtendedVarLenRec;
  private long numberExtendedVarLenRec;
  private long numberOfPointRecords;
  private long[] numberOfPointsByReturn;
  private CoordinateReferenceSystemOption crsOption;
  private LasGpsTimeType lasGpsTimeType;
  private LinearUnits lasLinearUnits = LinearUnits.UNKNOWN;
  private boolean isGeographicModelTypeKnown;
  private boolean usesGeographicModel;
  private GeoTiffData gtData;

  private final BufferedRandomAccessForLidar braf;
  private boolean isClosed;

  private final List<LasVariableLengthRecord>vlrList;

  public LasFileReader(File path) throws IOException {
    braf = new BufferedRandomAccessForLidar(path);
    vlrList = new ArrayList<>();
    readHeader(); //NOPMD
  }

  /**
   * Reads the header information from the beginning of a
   * LAS file
   *
   * @param braf a valid instance for an open LAS file
   * @throws IOException in the event of an non recoverable I/O error
   * or LAS format violation
   */
  private void readHeader() throws IOException {
    fileSignature = braf.readAscii(4);
    if (!"LASF".equals(fileSignature)) {
      throw new IOException("File is not in recognizable LAS format");
    }
    fileSourceID = braf.readUnsignedShort();
    globalEncoding = braf.readUnsignedShort();
    // skip the GUID for now
    braf.skipBytes(16);
    versionMajor = braf.readUnsignedByte();
    versionMinor = braf.readUnsignedByte();
    systemIdentifier = braf.readAscii(32);
    generatingSoftware = braf.readAscii(32);
    fileCreationDayOfYear = braf.readUnsignedShort();
    fileCreationYear = braf.readUnsignedShort();
    GregorianCalendar cal = new GregorianCalendar();
    cal.set(Calendar.YEAR, fileCreationYear);
    cal.set(Calendar.DAY_OF_YEAR, fileCreationDayOfYear);
    cal.set(Calendar.HOUR, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    fileCreationDate = cal.getTime();
    headerSize = braf.readUnsignedShort();
    offsetToPointData = braf.readUnsignedInt();
    numberVariableLengthRecords = braf.readUnsignedInt();
    pointDataRecordFormat = braf.readUnsignedByte();
    pointDataRecordLength = braf.readUnsignedShort();
    legacyNumberOfPointRecords = braf.readUnsignedInt();
    legacyNumberOfPointsByReturn = new long[5];
    for (int i = 0; i < 5; i++) {
      legacyNumberOfPointsByReturn[i]
        = braf.readUnsignedInt();
    }
    xScaleFactor = braf.readDouble();
    yScaleFactor = braf.readDouble();
    zScaleFactor = braf.readDouble();
    xOffset = braf.readDouble();
    yOffset = braf.readDouble();
    zOffset = braf.readDouble();
    maxX = braf.readDouble();
    minX = braf.readDouble();
    maxY = braf.readDouble();
    minY = braf.readDouble();
    maxZ = braf.readDouble();
    minZ = braf.readDouble();

        // the following fields were not provided
    // in LAS format 1.2 and earlier (1.3 and earlier?).
    // Use the file size  to avoid reading them if they are not there.
    long pos = braf.getFilePosition();
    if (headerSize <= pos) {
      numberOfPointRecords = this.legacyNumberOfPointRecords;
      numberOfPointsByReturn = new long[15];
      System.arraycopy(
        numberOfPointsByReturn, 0,
        legacyNumberOfPointsByReturn, 0,
        5);
    } else {
      startOfWaveformDataPacketRec = braf.readLong();
      startOfExtendedVarLenRec = braf.readLong();
      numberExtendedVarLenRec = braf.readUnsignedInt();
      numberOfPointRecords = braf.readLong();
      numberOfPointsByReturn = new long[15];
      for (int i = 0; i < 15; i++) {
        numberOfPointsByReturn[i] = braf.readLong();
      }
    }

    if((globalEncoding&BIT4)==0){
      crsOption = CoordinateReferenceSystemOption.GeoTIFF;
    }else{
      crsOption = CoordinateReferenceSystemOption.WKT;
    }

    if((globalEncoding&BIT1)==0){
      lasGpsTimeType = LasGpsTimeType.WeekTime;
    }else{
      lasGpsTimeType = LasGpsTimeType.SatelliteTime;
    }

    for(int i=0; i<this.numberVariableLengthRecords; i++){
       LasVariableLengthRecord vlrHeader = readVlrHeader();
       braf.skipBytes(vlrHeader.recordLength);
       vlrList.add(vlrHeader);
    }

    if(crsOption==CoordinateReferenceSystemOption.GeoTIFF){
      loadGeoTiffSpecification();  //NOPMD

    }
  }

  private LasVariableLengthRecord readVlrHeader() throws IOException {
    braf.skipBytes(2); // reserved
    String userID = braf.readAscii(16);
    int recordID = braf.readUnsignedShort();
    int recordLength = braf.readUnsignedShort();
    String description = braf.readAscii(32);
    long offset = braf.getFilePosition();
    return new LasVariableLengthRecord(
         offset, userID, recordID, recordLength, description);
  }


  /**
   * Read a record from the LAS file. The LasPoint object is used
   * as a container to hold the results of the read. Since
   * this method may be called literally millions of times, it is
   * advantageous to reuse the point object rather than creating a
   * large number of instances.
   * <p>
   * Note that depending on the record type used in the LAS file,
   * not all elements may be populated.
   *
   * @param recordIndex the index of the record (0 to numberOfPointRecords-1)
   * @param p a valid instance to receive data
   * @throws IOException In the event of an unrecoverable IOException
   */
  public void readRecord(long recordIndex, LasPoint p) throws IOException {
    if (recordIndex < 0 || recordIndex >= this.numberOfPointRecords) {
      throw new IOException(
        "Record index "
        + recordIndex
        + " out of bounds ["
        + 0 + ".." + numberOfPointRecords + "]");
    }
    if (isClosed) {
      throw new IOException("File is closed");
    }

    long filePos = this.offsetToPointData
      + recordIndex * this.pointDataRecordLength;
    braf.seek(filePos);
    int lx = braf.readInt();
    int ly = braf.readInt();
    int lz = braf.readInt();

    p.x = lx * xScaleFactor + xOffset;
    p.y = ly * yScaleFactor + yOffset;
    p.z = lz * zScaleFactor + zOffset;
    p.intensity = braf.readUnsignedShort();
    int mask = braf.readUnsignedByte();
    p.returnNumber = mask & 0x07;
    p.numberOfReturns = (mask >> 3) & 0x7;
    p.scanDirectionFlag = (mask >> 5) & 0x01;
    p.edgeOfFlightLine = (mask & 0x80) != 0;


    // for record types 0 to 5, the classification
    // is packed in with some other bit-values, see Table 8
    mask = braf.readUnsignedByte();
    p.classification = mask &0x1f; // bits 0:4, values 0 to 32
    p.synthetic = (mask & 0x20) != 0;
    p.keypoint = (mask & 0x40) != 0;
    p.withheld = (mask & 0x80) != 0;

    // we currently skip
    //   scan angle rank  1 byte
    //   user data        1 byte
    //   point source ID  2 bytes
    braf.skipBytes(4); // scan angle rank


    if (pointDataRecordFormat == 1 || pointDataRecordFormat == 3) {
      p.gpsTime = braf.readDouble();
      // Depending on the gpsTimeType element, the GPS time can be
      // in one of two formats:
      //    GPS Week Time  seconds since 12:00 a.m. Sunday
      //    GPS Satellite Time   seconds since 12 a.m. Jan 6, 1980
      //                         minus an offset 1.0e+9
      //    The mapping to a Java time requires information about
      //    the GPS time type
    }

  }

  /**
   * Get the record format specified in the LAS file. The meaning
   * of this value is described in the LAS specification. Generally, the
   * record format is more useful than the actual LAS format version of
   * the file.
   *
   * @return a positive integer value.
   */
  public int getPointDataRecordFormat() {
    return this.pointDataRecordFormat;
  }

  @Override
  public String toString() {
    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    String yearString = sdf.format(this.fileCreationDate);
    return String.format("LAS vers %d.%d, created %s, nrecs %d",
      versionMajor, versionMinor, yearString, this.legacyNumberOfPointRecords);
    // Integer.toString(globalEncoding,2));
  }

  /**
   * Get the number of point records in file.
   *
   * @return a positive long integer.
   */
  public long getNumberOfPointRecords() {
    return this.numberOfPointRecords;
  }

  /**
   * Closes all internal data files.
   *
   * @throws IOException in the event of an non-recoverable IO condition.
   */
  public void close() throws IOException {
    braf.close();
  }

  /**
   * @return the fileSignature
   */
  public String getFileSignature() {
    return fileSignature;
  }

  /**
   * Gets the minimum value for the x coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the minimum value for the x coordinates in the file
   */
  public double getMinX() {
    return minX;
  }

  /**
   * Gets the maximum value for the x coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the maximum value for the x coordinates in the file
   */
  public double getMaxX() {
    return maxX;
  }

  /**
   * Gets the minimum value for the y coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the minimum value for the y coordinates in the file
   */
  public double getMinY() {
    return minY;
  }

  /**
   * Gets the maximum value for the y coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the maximum value for the y coordinates in the file.
   */
  public double getMaxY() {
    return maxY;
  }

  /**
   * Gets the minimum value for the z coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the minimum value for the z coordinates in the file.
   */
  public double getMinZ() {
    return minZ;
  }

  /**
   * Gets the maximum value for the z coordinates of the points in the LAS
   * file as specified in the LAS-standard file header.
   *
   * @return the maximum value for the z coordinates in the file.
   */
  public double getMaxZ() {
    return maxZ;
  }

  /**
   * Gets the option that was used for storing the coordinate reference
   * system in the LAS file. This information will indicate the appropriate
   * interpretation of the associated variable-length record instance.
   * @return a valid enumeration instance.
   */
  public CoordinateReferenceSystemOption getCoordinateReferenceSystemOption(){
    return this.crsOption;
  }

  /**
   * Gets a new instance of a list containing all variable length records.
   * @return a valid list.
   */
  public List<LasVariableLengthRecord> getVariableLengthRecordList(){
    List<LasVariableLengthRecord>list = new ArrayList<>();
    list.addAll(vlrList);
    return list;
  }

  /**
   * Gets the variable-length record with the specified recordId
   * @param recordId a valid record ID in agreement with the LAS specification
   * @return if found, a valid instance; otherwise, a null
   */
  public LasVariableLengthRecord getVariableLengthRecordByRecordId(int recordId){
    for(LasVariableLengthRecord vlr: vlrList){
      if(vlr.getRecordId()==recordId){
        return vlr;
      }
    }
    return null;
  }

  private boolean inLonRange(double x) {
    return -180 <= x && x <= 360;
  }

  private boolean inLatRange(double y) {
    return -90 <= y && y <= 90;
  }

  /**
   * Provides an incomplete and weak implementation of a method that determines
   * if the LAS file contains a geographic coordinate system. At present,
   * this result is obtained by inspecting the range of the x and y coordinates.
   * However, a valid implementation would involve looking at the
   * projection-related information in the Variable Length Records as
   * defined by the LAS specification.
   *
   * @return true if the LAS file uses geographic coordinates,
   * otherwise false.
   */
  public boolean usesGeographicCoordinates() {

    // while GeoTiff specification is implemented,
    // support for WKT is not.  So the class may or may not
    // know the geographic model type
    if( isGeographicModelTypeKnown){
       return usesGeographicModel;
    }

    // apply some rules of thumb

    if (inLonRange(minX) && inLonRange(maxX)) {
      if (maxX - minX > 10) {
        // a lidar sample would not contain a 10-degree range
        return false;
      }
      if (inLatRange(minY) && inLatRange(maxY)) {
        return maxY - minY <= 10;
      }
    }
    return false;
  }

  /**
   * Gets the representation of time that is assigned to
   * the sample point GPS time values.  This option
   * is arbitrarily assigned by the agency that collected and distributed
   * the LAS file. It is necessary to use this value in order to interpret
   * the GPS time of the samples.
   * @return an enumeration giving the time recording format used for the LAS file
   */
  public LasGpsTimeType getLasGpsTimeType(){
    return  lasGpsTimeType;
  }


  private void loadGeoTiffSpecification() throws IOException {
    // get the projection keys
    LasVariableLengthRecord vlr = getVariableLengthRecordByRecordId(34735);
    if (vlr == null) {
      return;  // this is actually a file-format error
    }

    // technically, we should check to make sure that the
    // thing that follows is the GeoTiff header.
    // but we just assume that it is correct and skip it.
    braf.seek(vlr.getFilePosition() + 6);
    int nR = braf.readUnsignedShort();
    List<GeoTiffKey> keyList = new ArrayList<>();
    for (int i = 0; i < nR; i++) {
      int keyCode = braf.readUnsignedShort();
      int location = braf.readUnsignedShort();
      int count = braf.readUnsignedShort();
      int valueOrOffset = braf.readUnsignedShort();
      GeoTiffKey key = new GeoTiffKey(keyCode, location, count, valueOrOffset); //NOPMD
      keyList.add(key);
    }

    vlr = getVariableLengthRecordByRecordId(34736);
    double []doubleData = null;
    if (vlr != null) {
       braf.seek(vlr.getFilePosition());
      int nD = vlr.recordLength/8;
      doubleData = new double[nD];
      for(int i=0; i<nD; i++){
          doubleData[i]= braf.readDouble();
      }
    }

    vlr = getVariableLengthRecordByRecordId(34737);
    char []asciiData = null;
    if(vlr !=null){
         braf.seek(vlr.getFilePosition());
       asciiData = new char[vlr.recordLength];
       for(int i=0; i<vlr.recordLength; i++){
           asciiData[i] = (char)braf.readUnsignedByte();
       }
    }

    gtData = new GeoTiffData(keyList, doubleData, asciiData);

    // see if the data is projected or geographic
    int gtModelType = gtData.getInteger(GeoTiffData.GtModelTypeGeoKey);
    if (gtModelType == 1) {
      // it's projected
      this.isGeographicModelTypeKnown = true;
      this.usesGeographicModel = false;
    } else if (gtModelType == 2) {
      // its geographic
      this.isGeographicModelTypeKnown = true;
      this.usesGeographicModel = true;
    }

    int unitsCode = -1;
    if (gtData.containsKey(GeoTiffData.VerticalUnitsGeoKey)) {
      unitsCode = gtData.getInteger(GeoTiffData.VerticalUnitsGeoKey);
    } else if (gtData.containsKey(GeoTiffData.ProjLinearUnitsGeoKey)) {
      unitsCode = gtData.getInteger(GeoTiffData.ProjLinearUnitsGeoKey);
    }

    // The following values are from GeoTIFF spec paragraph 6.3.1.3
    if (unitsCode == 9001) {
      lasLinearUnits = LinearUnits.METERS;
    } else if (9002 <= unitsCode && unitsCode <= 9006) {
      lasLinearUnits = LinearUnits.FEET;
    } else if (unitsCode == 9014) {
      // fathoms are probably not used, but could be supplied
      // in bathymetric lidar applications
      lasLinearUnits = LinearUnits.FATHOMS;
    }

    //if(gtData.isKeyDefined(GeoTiffData.PCSCitationGeoKey)){
    //    String s = gtData.getString(GeoTiffData.PCSCitationGeoKey);
    //    System.out.println(s);
    //}
    //
    //  if(gtData.isKeyDefined(GeoTiffData.GeoCitationGeoKey)){
    //    String s = gtData.getString(GeoTiffData.GeoCitationGeoKey);
    //    System.out.println(s);
    //}
  }


  /**
   * Gets a copy of the GeoTiffData associated with the LAS file, if any.
   * For those LAS files which use Well-Known Text (WKT) specifications,
   * the return value from this method will be null.
   * @return if available, a valid instance; otherwise a null.
   */
  public GeoTiffData getGeoTiffData(){
      return gtData;
  }


  /**
   * Get the linear units specified by the LAS file.  This method
   * assumes that the vertical and horizontal data are in the same
   * system (unless Geographic coordinates are used). In the future
   * this assumption may be revised, requiring a change to the API.
   * @return a valid instance of the enumeration.
   */
  public LinearUnits getLinearUnits(){
    return lasLinearUnits;
  }

//   A sample main for debugging and diagnostics.
//    public static void main(String[] args) throws IOException
//    {
//        LasPoint p = new LasPoint();
//        File file = new File(args[0]);
//        LasFileReader lf = new LasFileReader(file);
//
//        PrintStream ps = System.out;
//        ps.format("Number of records:   %8d\n", lf.getNumberOfPointRecords());
//        ps.format("X Min:               %10.2f\n", lf.getMinX());
//        ps.format("X Max:               %10.2f\n", lf.getMaxX());
//        ps.format("Y Min:               %10.2f\n", lf.getMinY());
//        ps.format("Y Max:               %10.2f\n", lf.getMaxY());
//        ps.format("Z Min:               %10.2f\n", lf.getMinZ());
//        ps.format("Z Max:               %10.2f\n", lf.getMaxZ());
//
//        lf.readRecord(0, p);
//        lf.readRecord(1, p);
//    }

}
