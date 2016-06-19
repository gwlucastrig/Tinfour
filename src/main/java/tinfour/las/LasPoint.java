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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.las;

/**
 * A simple data-holder class for transferring data from
 * LAS file records.  This class is intended for efficient
 * use in applications that may process millions of records.
 * Therefore, it is designed so that instances
 * can be used and reused over and over again as temporary
 * containers for data.  Thus elements are exposed as public
 * without accessors methods or other protections.
 * <p>
 * There is, however, no restriction on creating instances of this class.
 * Depending on the requirements of the implementation, it may be completely
 * reasonable to do so. However, when millions of points are to be
 * processed, it will be advantageous to not create persistent instances.
 */
public class LasPoint {
    /** The position within the file at which the record is stored */
    public long filePosition;
    /** The X coordinate from the record, always populated */
    public double x;
    /** The Y coordinate from the record, always populated */
    public double y;
    /** The Z coordinate from the record, always populated */
    public double z;
    /** The intensity of the return at the detected point,
     * by convention normalized to the range 0 to 65535
     */
    public int intensity;
    /** The return number for the point */
    public int returnNumber;
    /** The number of returns for the pulse for which the
     * point was detected.
     */
    public int numberOfReturns;
    /** The one bit scan direction flag */
    public int scanDirectionFlag;
    /** Indicates whether the detection was at the edge of a flight line */
    public boolean edgeOfFlightLine;
    /** The observation-category classification for the return */
    public int classification;
    /** Indicates that point was created by techniques other than LIDAR */
    public boolean synthetic;
    /** Indicate point is a model key point */
    public boolean keypoint;
    /** Indicates that point should not be included in processing */
    public boolean withheld;
    /** The GPS time (interpreted according to header GPS flag */
    public double gpsTime;


}
