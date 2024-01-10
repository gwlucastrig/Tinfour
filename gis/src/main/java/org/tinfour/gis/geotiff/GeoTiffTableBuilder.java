/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
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
package org.tinfour.gis.geotiff;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.common.mylzw.MyLzwCompressor;
import org.apache.commons.imaging.formats.tiff.TiffElement;
import org.apache.commons.imaging.formats.tiff.TiffImageData;
import static org.apache.commons.imaging.formats.tiff.constants.GdalLibraryTagConstants.EXIF_TAG_GDAL_NO_DATA;
import org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants;
import static org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants.EXIF_TAG_MODEL_PIXEL_SCALE_TAG;
import static org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants.EXIF_TAG_MODEL_TIEPOINT_TAG;
import static org.apache.commons.imaging.formats.tiff.constants.TiffConstants.TIFF_COMPRESSION_LZW;
import static org.apache.commons.imaging.formats.tiff.constants.TiffConstants.TIFF_COMPRESSION_UNCOMPRESSED;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import static org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.PLANAR_CONFIGURATION_VALUE_CHUNKY;
import static org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.SAMPLE_FORMAT_VALUE_IEEE_FLOATING_POINT;
import static org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.TIFF_TAG_SAMPLE_FORMAT;
import org.apache.commons.imaging.formats.tiff.fieldtypes.FieldType;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * Provides methods and elements for building a GeoTIFF file.
 * At this time, this class is hard-wired to tile-based TIFFs
 * with square tiles.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
public class GeoTiffTableBuilder {

  private static class GeoTiffKey {

    final GeoKey geoKey;
    final int reference;
    final int valueOrPos;
    final int len;

    GeoTiffKey(GeoKey geoKey, int reference, int valueOrPos, int len) {
      this.geoKey = geoKey;
      this.reference = reference;
      this.valueOrPos = valueOrPos;
      this.len = len;
    }

    GeoTiffKey(GeoKey geoKey, int value) {
      this.geoKey = geoKey;
      this.reference = 0;
      this.valueOrPos = value;
      this.len = 1;
    }

  }

  private final static int DOUBLE_REF = 34736;
  private final static int ASCII_REF = 34737;
  private final static byte PIPE_CHAR = (byte) 0x7C;
  List<GeoTiffKey> keyList = new ArrayList<>();
  List<Double> doubleList = new ArrayList<>();
  ByteArrayOutputStream baos = new ByteArrayOutputStream();
  AffineTransform model2Pixel;
  AffineTransform pixel2Model;
  private ByteOrder eTiffByteOrder;
  private String gdalNoData;
  private boolean compressionEnabled = false;

  /**
   * Constructs an instance that will produce GeoTIFF files with the
   * little-endian byte order.
   */
  public GeoTiffTableBuilder() {
    // At this time, the big-endian byte order logic has not received
    // sufficient testing, so we are not offering it as a constructor.
    this.eTiffByteOrder = ByteOrder.LITTLE_ENDIAN;
  }

  /**
   * Sets the optional data compression enabled. If set, the Deflate algorithm
   * is used
   * with a floating-point predictor.
   *
   * @param enabled if true, compression is applied; otherwise, compression is
   * suppressed.
   */
  public void setCompressionEnabled(boolean enabled) {
    this.compressionEnabled = enabled;
  }

  /**
   * Sets a string used to indicate no-data. This string follows the
   * conventions established by the GDAL software suite.
   *
   * @param noDataValue a valid string, or a null if there is no null-data
   * value.
   */
  public void setGdalNoData(String noDataValue) {
    this.gdalNoData = noDataValue;
  }

  /**
   * Add a GeoKey and an integer value setting
   *
   * @param geoKey a valid instance of the GeoKey enumeration
   * @param value the value to be assigned
   */
  public void addGeoKey(GeoKey geoKey, int value) {
    keyList.add(new GeoTiffKey(geoKey, value));
  }

  /**
   * Add a GeoKey and a floating-point (double) value setting
   *
   * @param geoKey a valid instance of the GeoKey enumeration
   * @param value the value to be assigned
   */
  public void addGeoKey(GeoKey geoKey, double value) {
    int pos = doubleList.size();
    doubleList.add(value);
    keyList.add(new GeoTiffKey(geoKey, DOUBLE_REF, 1, pos));
  }

  /**
   * Add a GeoKey and an array of floating-point (double) values
   *
   * @param geoKey a valid instance of the GeoKey enumeration
   * @param values an array of size 1 or larger
   */
  public void addGeoKey(GeoKey geoKey, double[] values) {
    int pos = doubleList.size();
    for (double v : values) {
      doubleList.add(v);
    }
    keyList.add(new GeoTiffKey(geoKey, DOUBLE_REF, pos, values.length));
  }

  /**
   * Add a GeoKey and a String value setting
   *
   * @param geoKey a valid instance of the GeoKey enumeration
   * @param value a valid string of length 1 or greater
   */
  public void addGeoKey(GeoKey geoKey, String value) {
    // assume all characters in value are ASCII or extended ASCII
    int pos = baos.size();
    for (int i = 0; i < value.length(); i++) {
      int a = value.charAt(i) & 0xff;
      baos.write(a);
    }
    baos.write(PIPE_CHAR);
    keyList.add(new GeoTiffKey(geoKey, ASCII_REF, pos, value.length() + 1));
  }

  /**
   * Sets the transform that maps model (real-valued) coordinates to pixels
   *
   * @param model2Pixel a valid, invertible transform.
   */
  public void setModelToPixelTransform(AffineTransform model2Pixel) {
    try {
      this.model2Pixel = model2Pixel;
      pixel2Model = model2Pixel.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException("Specified transform is not invertible", ex);
    }
  }

  /**
   * Sets the transform that maps pixels to model (real-valued) coordinates.
   *
   * @param pixel2Model a valid, invertible transform
   */
  public void setPixelToModelTransform(AffineTransform pixel2Model) {
    try {
      this.pixel2Model = pixel2Model;
      model2Pixel = pixel2Model.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException("Specified transform is not invertible", ex);
    }
  }

  /**
   * Stores the GeoKey content and model-pixel coordinate transform data to
   * the TIFF output directory instance
   *
   * @param tiffOutputDirectory a valid instance
   * @throws IOException in the event of an invalid specification
   * @throws ImageWriteException in the event of an invalid specification
   */
  public void storeContent(TiffOutputDirectory tiffOutputDirectory) throws IOException, ImageWriteException {
    if (pixel2Model != null) {
      double[] a = new double[6];
      pixel2Model.getMatrix(a);
      double[] d = new double[3];
      d[0] = a[0];
      d[1] = a[3];
      tiffOutputDirectory.add(EXIF_TAG_MODEL_PIXEL_SCALE_TAG, d);
      double[] tiepoint = new double[6];
      tiepoint[0] = 0;
      tiepoint[1] = 0;
      tiepoint[2] = 0;
      tiepoint[3] = a[4];
      tiepoint[4] = a[5];
      tiepoint[5] = 0;
      tiffOutputDirectory.add(EXIF_TAG_MODEL_TIEPOINT_TAG, tiepoint);
    }
    Collections.sort(keyList, new Comparator<GeoTiffKey>() {
      @Override
      public int compare(GeoTiffKey o1, GeoTiffKey o2) {
        return Integer.compare(o1.geoKey.getKeyCode(), o1.geoKey.getKeyCode());
      }
    });
    short[] sArray = new short[(keyList.size() + 1) * 4];
    sArray[0] = 1;
    sArray[1] = 1;
    sArray[2] = 0;
    sArray[3] = (short) keyList.size();
    int k = 4;
    for (GeoTiffKey gk : keyList) {
      sArray[k++] = (short) gk.geoKey.getKeyCode();
      sArray[k++] = (short) gk.reference;
      sArray[k++] = (short) gk.len;
      sArray[k++] = (short) gk.valueOrPos;
    }

    FieldType fType = FieldType.SHORT;
    byte bytes[] = fType.writeData(sArray, eTiffByteOrder);

    TagInfo tInfo = GeoTiffTagConstants.EXIF_TAG_GEO_KEY_DIRECTORY_TAG;

    TiffOutputField tiffOutputFieldGeoKey
      = new TiffOutputField(
        tInfo.tag,
        tInfo,
        fType,
        sArray.length,
        bytes);
    //EXIF_TAG_GEO_KEY_DIRECTORY_TAG;

    int nB = baos.size();
    if (nB > 0) {
      TagInfo aTagInfo = GeoTiffTagConstants.EXIF_TAG_GEO_ASCII_PARAMS_TAG;
      bytes = baos.toByteArray();
      TiffOutputField tiffOutputFieldGeoAscii = new TiffOutputField(
        aTagInfo.tag,
        aTagInfo,
        FieldType.ASCII,
        bytes.length,
        bytes);
      tiffOutputDirectory.add(tiffOutputFieldGeoAscii);
    }

    tiffOutputDirectory.add(tiffOutputFieldGeoKey);
    int nD = this.doubleList.size();
    if (nD > 0) {
      double[] d = new double[nD];
      this.encodeDouble(tInfo, d);

      TiffOutputField tiffOutputFieldGeoDoubleParams = encodeDouble(
        GeoTiffTagConstants.EXIF_TAG_GEO_DOUBLE_PARAMS_TAG, d);
      tiffOutputDirectory.add(tiffOutputFieldGeoDoubleParams);

    }

    if (gdalNoData != null && !gdalNoData.isBlank()) {
      tiffOutputDirectory.add(EXIF_TAG_GDAL_NO_DATA, gdalNoData);
    }
  }

  TiffOutputField encodeDouble(TagInfo tInfo, double[] d)
    throws IOException, ImageWriteException {
    FieldType fType = FieldType.DOUBLE;

    byte bytes[] = fType.writeData(d, eTiffByteOrder);

    TiffOutputField tiffOutputField
      = new TiffOutputField(
        tInfo.tag,
        tInfo,
        fType,
        d.length,
        bytes);
    return tiffOutputField;
  }

  TiffOutputField encodeASCII(TagInfo tInfo, String string)
    throws IOException, ImageWriteException {

    byte bytes[] = FieldType.ASCII.writeData(string, eTiffByteOrder);

    TiffOutputField tiffOutputField
      = new TiffOutputField(
        tInfo.tag,
        tInfo,
        FieldType.ASCII,
        bytes.length,
        bytes);
    return tiffOutputField;
  }

  /**
   * Encode a block of floating-point values as a tile. This method
   * is supports the storage of 32-bit float-point values. At present,
   * it only supports the TIFF tile format (with square tiles). It may
   * be enhanced in the future.
   * <p>
   * To prepare for the Deflater compression,
   * the TIFF specification calls for the bytes to be split on
   * on a ROW-BY-ROW basis. For example, for a row of length 3
   * pixels -- A, B, and C -- the data for two rows
   * would be given as shown below (ignoring endian issues) with the
   * high order bytes being given as A3, B3, etc., the low order bytes
   * being given as A0, B0, etc.
   * <pre>
   *   Original:
   *      A3 A2 A1 A0   B3 B2 B1 B0   C3 C2 C1 C0
   *      D3 D3 D1 D0   E3 E2 E2 E0   F3 F2 F1 F0
   *
   *   Bytes split into groups by order-of-magnitude:
   *      A3 B3 C3   A2 B2 C2   A1 B1 C1   A0 B0 C0
   *      D3 E3 F3   D2 E2 F2   D1 E1 F1   D0 E0 F0
   * </pre>
   * To further improve the compression, the predictor takes the difference
   * of each subsequent bytes. Again, the differences (deltas) are computed on a
   * row-byte-row basis. For the most part, the differences combine bytes
   * associated with the same order-of-magnitude, though there is a special
   * transition at the end of each order-of-magnitude set (shown in
   * parentheses):
   * <pre>
   *      A3, B3-A3, C3-B3, (A2-C3), B2-A2, C2-B2, (A1-C2), etc.
   *      D3, E3-D3, F3-D3, (D2-F3), E3-D2, etc.
   * </pre>
   *
   * @param f the source floating point data to be encoded
   * @param nRows the number of rows to be encoded.
   * @param scanSize the number of columns in each row
   * @return if successful, a valid byte array; otherwise, a null.
   */
  public byte[] encodeBlock(float[] f, int nRows, int scanSize) {
    if (!compressionEnabled) {
      int nCells = f.length;  // also tileSize*tileSize
      byte[] b = new byte[nCells * 4];
      int k = 0;
      for (int i = 0; i < nCells; i++) {
        // the following order would be inverted for big-endian formats
        int ix = Float.floatToRawIntBits(f[i]);
        b[k++] = (byte) (ix & 0xff);
        b[k++] = (byte) ((ix >> 8) & 0xff);
        b[k++] = (byte) ((ix >> 16) & 0xff);
        b[k++] = (byte) ((ix >> 24) & 0xff);
      }
      return b;
    }
    int nBytesInRow = scanSize * 4;

    // Populate the uncompressed byte array using the floating-point
    // differencing pattern
    byte[] b = new byte[f.length * 4];
    for (int iRow = 0; iRow < nRows; iRow++) {
      int rowOffset = iRow * scanSize; // offset into the source
      // compute the offsets into the byte array, broken out by
      // byte orders of magnitude.
      final int aOffset = rowOffset * 4;
      final int bOffset = aOffset + scanSize;
      final int cOffset = bOffset + scanSize;
      final int dOffset = cOffset + scanSize;
      // split the bytes into separate sections, high-order bytes first,
      // low-order bytes last.  The TIFF standard uses this ordering independent
      // of whether little or big endian data ise set.
      for (int i = 0; i < scanSize; i++) {
        int ix = Float.floatToRawIntBits(f[rowOffset + i]);
        b[aOffset + i] = (byte) ((ix >> 24) & 0xff);
        b[bOffset + i] = (byte) ((ix >> 16) & 0xff);
        b[cOffset + i] = (byte) ((ix >> 8) & 0xff);
        b[dOffset + i] = (byte) (ix & 0xff);
      }

      // apply differencng
      for (int i = nBytesInRow - 1; i > 0; i--) {
        b[aOffset + i] = (byte) ((b[aOffset + i] & 0xff) - (b[aOffset + i - 1] & 0xff));
      }
    }

    try {
      final int LZW_MINIMUM_CODE_SIZE = 8;
      MyLzwCompressor compressor = new MyLzwCompressor(
        LZW_MINIMUM_CODE_SIZE, ByteOrder.BIG_ENDIAN, true);
      final byte[] compressed = compressor.compress(b);
      return compressed;
    } catch (IOException iex) {
      return null;
    }

    //
    //    // apply the Deflate
    //    Deflater deflater = new Deflater(6);
    //    deflater.setInput(b, 0, b.length);
    //    deflater.finish();
    //    byte[] deflaterResult = new byte[b.length + 1024];
    //    int dN = deflater.deflate(deflaterResult, 0, deflaterResult.length, Deflater.FULL_FLUSH);
    //    if (dN <= 0) {
    //      // deflate failed
    //      return null;
    //    }
    //    return Arrays.copyOf(deflaterResult, dN);
  }

  /**
   * Create a GeoTIFF file with the specified tiles.
   *
   * @param geoTiffFile a valid file
   * @param width the width of the raster image
   * @param height the height of the raster image
   * @param tileSize the size of the tile (assumed square)
   * @param tiles the encoded, potentially compressed, bytes for the tiles
   * @throws IOException in the event of an unrecoverable IO exception
   * @throws ImageWriteException in the event of an invalid data specification
   */
  public void writeTiles(File geoTiffFile, int width, int height, int tileSize, byte[][] tiles) throws IOException, ImageWriteException {
    PixelDensity pixelDensity = PixelDensity.createFromPixelsPerInch(96, 96);
    short compression;
    short samplesPerPixel;
    short bitsPerSample;
    short photometricInterpretation;
    short planarConfiguration;

    if (compressionEnabled) {
      //compression = TIFF_COMPRESSION_DEFLATE_ADOBE;
      compression = TIFF_COMPRESSION_LZW;
    } else {
      compression = TIFF_COMPRESSION_UNCOMPRESSED;
    }
    samplesPerPixel = 1;
    bitsPerSample = 32;
    photometricInterpretation = 0;
    planarConfiguration = PLANAR_CONFIGURATION_VALUE_CHUNKY;

    ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    final TiffOutputSet outputSet = new TiffOutputSet(byteOrder);
    final TiffOutputDirectory directory = outputSet.addRootDirectory();
    directory.add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, width);
    directory.add(TiffTagConstants.TIFF_TAG_IMAGE_LENGTH, height);
    directory.add(TiffTagConstants.TIFF_TAG_PHOTOMETRIC_INTERPRETATION, photometricInterpretation);
    directory.add(TiffTagConstants.TIFF_TAG_COMPRESSION, compression);
    if (compressionEnabled) {
      directory.add(TiffTagConstants.TIFF_TAG_PREDICTOR, (short) TiffTagConstants.PREDICTOR_VALUE_FLOATING_POINT_DIFFERENCING);
    }
    directory.add(TiffTagConstants.TIFF_TAG_PLANAR_CONFIGURATION, planarConfiguration);
    directory.add(TiffTagConstants.TIFF_TAG_SAMPLES_PER_PIXEL, samplesPerPixel);
    directory.add(TiffTagConstants.TIFF_TAG_BITS_PER_SAMPLE, (short) bitsPerSample);
    directory.add(TiffTagConstants.TIFF_TAG_TILE_WIDTH, tileSize);
    directory.add(TiffTagConstants.TIFF_TAG_TILE_LENGTH, tileSize);
    directory.add(TiffTagConstants.TIFF_TAG_RESOLUTION_UNIT, (short) 2);
    directory.add(TiffTagConstants.TIFF_TAG_XRESOLUTION,
      RationalNumber.valueOf(pixelDensity.horizontalDensityInches()));
    directory.add(TiffTagConstants.TIFF_TAG_YRESOLUTION,
      RationalNumber.valueOf(pixelDensity.verticalDensityInches()));
    directory.add(TIFF_TAG_SAMPLE_FORMAT, (short) SAMPLE_FORMAT_VALUE_IEEE_FLOATING_POINT);

    this.storeContent(directory);

    final TiffElement.DataElement[] imageData = new TiffElement.DataElement[tiles.length];
    Arrays.setAll(imageData, i -> new TiffImageData.Data(0, tiles[i].length, tiles[i]));

    final TiffImageData abstractTiffImageData = new TiffImageData.Tiles(imageData, tileSize, tileSize);
    directory.setTiffImageData(abstractTiffImageData);

    try (FileOutputStream fos = new FileOutputStream(geoTiffFile);
      BufferedOutputStream bos = new BufferedOutputStream(fos);) {
      new TiffImageWriterLossy().write(bos, outputSet);
    }
  }

}
