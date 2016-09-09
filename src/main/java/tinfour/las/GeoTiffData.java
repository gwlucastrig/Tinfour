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
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 *  At this time, the floating-point and string values are not implemented
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides the main data collection for accessing data
 * from the embedded GeoTIFF tags in a LAS file.
 */
public class GeoTiffData {

    /**
     * The code tag for a TIFF directory containing floating point parameters
     * (doubles) for the GeoTIFF specification
     */
    public static final int GeoDoubleParamsTag = 34736;
    /**
     * The code tag for a TIFF directory containing ASCII string parameters for
     * the GeoTIFF specification
     */
    private static final int GeoAsciiParamsTag = 34737;

    /**
     * Key code for the GtModelTypeGeoKey specification
     */
    public static final int GtModelTypeGeoKey = 1024;

    /**
     * Key code for the GeoCitationGeoKey specification
     */
    public static final int GeoCitationGeoKey = 2049;

        /**
     * Key code for the PCSCitationGeoKey specification
     */
    public static final int PCSCitationGeoKey = 3073;


    /**
     * Key code for the ProjLinearUnitsGeoKey specification
     */
    public static final int ProjLinearUnitsGeoKey = 3076;

    /**
     * Key code for the VerticalLinearUnitsGeoKey specification
     */
  public static final int VerticalUnitsGeoKey = 4099;

  /**
   * Linear Unit Code for feet, from GeoTiff spec 6.3.1.3
   */
  public static final int LinearUnitCodeMeter = 9001;

  /**
   * Linear Unit Code for feet, from GeoTiff spec 6.3.1.3
   */
  public static final int LinearUnitCodeFeet = 9002;

    /**
   * Linear Unit Code for feet, from GeoTiff spec 6.3.1.3
   */
  public static final int LinearUnitCodeFeetUS = 9003;

    private final List<GeoTiffKey> keyList;
    private final HashMap<Integer, GeoTiffKey> keyMap;
    private final double[] doubleData;
    private final char[] asciiData;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    public GeoTiffData(
            List<GeoTiffKey> keyList,
            double[] fpData,
            char[] asciiData) {
        this.keyList = keyList;
        this.doubleData = fpData;
        this.asciiData = asciiData;
        this.keyMap = new HashMap<>();
        for (GeoTiffKey key : keyList) {
            keyMap.put(key.getKeyCode(), key);
        }
    }

    /**
     * Indicates whether the GeoTiffData collection contains an entry
     * for the specified key code. This method does not indicate whether
     * the specified code is a valid value, only whether it is
     * available in the data set.
     * @param keyCode an integer key code
     * @return true if the collection includes an entry for the code,
     * otherwise false.
     */
    public boolean containsKey(int keyCode) {
        return keyMap.containsKey(keyCode);
    }

    /**
     * Gets the single integer value associated with the GeoTIFF keycode.
     *
     * @param keyCode a valid GeoTIFF key
     * @return a positive integer value in the range 0 to 65535 (the range of an
     * unsigned short integer)
     * @throws IOException in the event of a TIFF format exception
     * or the key code is not found.
     */
    public int getInteger(int keyCode) throws IOException {
        GeoTiffKey key = keyMap.get(keyCode);
        if (key == null || key.location != 0) {
            throw new IOException("Invalid TIFF key for integer: " + keyCode);
        } else {
            return key.valueOrOffset;
        }
    }

    /**
     * Gets an array of one or more doubles containing the value or values
     * associated with the specified key code.
     *
     * @param keyCode a valid GeoTIFF key
     * @return a valid array
     * @throws IOException in the event of a TIFF format exception
     */
    public double[] getDouble(int keyCode) throws IOException {
        GeoTiffKey key = keyMap.get(keyCode);
        if (key == null || key.location != GeoDoubleParamsTag) {
            throw new IOException("Invalid TIFF key for double: " + keyCode);
        }
        if (doubleData == null || key.count + key.valueOrOffset > doubleData.length) {
            throw new IOException(
                    "Format violation: count exceeds available data for " + keyCode);
        }
        double[] d = new double[key.count];
        System.arraycopy(doubleData, key.valueOrOffset, d, 0, key.count);
        return d;

    }

    /**
     * Gets the single String value associated with the GeoTIFF keycode.
     *
     * @param keyCode a valid GeoTIFF key
     * @return a positive integer value in the range 0 to 65535 (the range of an
     * unsigned short integer)
     * @throws IOException in the event of a TIFF format exception
     */
    public String getString(int keyCode) throws IOException {
        GeoTiffKey key = keyMap.get(keyCode);
        if (key == null || key.location != GeoAsciiParamsTag) {
            throw new IOException("Invalid TIFF key for ASCII string: " + keyCode);
        }
        if (asciiData == null || key.count + key.valueOrOffset > asciiData.length) {
            throw new IOException(
                    "Format violation: count exceeds available data for " + keyCode);
        }
        StringBuilder sb = new StringBuilder(key.count);
        for (int i = 0; i < key.count; i++) {
            char c = asciiData[i];
            if (c == 0) {
                break; // not in the spec, but I've seen it happen
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Gets a list of the keys defined by this GeoTiffData collection
     * @return a safe copy of the key list from this collection.
     */
    public List<GeoTiffKey>getKeyList(){
        List<GeoTiffKey>list = new ArrayList<>(keyList.size());
        list.addAll(keyList);
        return list;
    }
}
