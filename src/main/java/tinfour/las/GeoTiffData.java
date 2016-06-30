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

import java.util.HashMap;
import java.util.List;

/**
 * Provides elements and data for accessing GeoTiff data from
 * a GeoTIFF specification
 */
public class GeoTiffData {

  /**
   * Key code for the GtModelTypeGeoKey specification
   */
  public static final int GtModelTypeGeoKey = 1024;

  /**
   * Key code for the ProjLinearUnitsGeoKey specification
   */
  public static final int ProjLinearUnitsGeoKey = 3076;

  /**
   * Key code for the VerticalLinearUnitsGeoKey specification
   */
  public static final int VerticalUnitsGeoKey = 4099;

  private final List<GeoTiffKey>keyList;
  private final HashMap<Integer, GeoTiffKey> keyMap;
  private final  double []fpData;
  private final  byte []asciiData;

  public GeoTiffData(
    List<GeoTiffKey>keyList,
    double []fpData,
    byte []asciiData){
    this.keyList = keyList;
    this.fpData = fpData;
    this.asciiData = asciiData;
    this.keyMap = new HashMap<>();
    for(GeoTiffKey key: keyList){
      keyMap.put(key.getKeyCode(), key);
    }
  }


  public boolean isKeyDefined(int keyCode){
    return keyMap.containsKey(keyCode);
  }

  public int getInteger(int keyCode){
    GeoTiffKey key = keyMap.get(keyCode);
    if(key==null){
      return Integer.MIN_VALUE;
    }else if(key.location!=0){
      // key is not an integer.
      return Integer.MIN_VALUE;
    }else{
      return key.valueOrOffset;
    }
  }


}
