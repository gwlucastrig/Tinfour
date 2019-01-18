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
 * 08/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.demo.utils;

import org.tinfour.common.Vertex;

/**
 * A representation of a vertex with a lidar classification value
 * (see LAS Specification 1.4, American Society for Photogrammetry
 * and Remote Sensing (ASPRS)).
 */
public class VertexWithClassification extends Vertex  {


    /**
     * Constructs a vertex with the lidar classification value
     * extracted from the source file. The ASPRS spec allows for
     * classification codes from 0 to 255. It formally defines values
     * 0 to 18 (with up to 63 reserved for future use) and leaves the
     * rest as user definable.  Here are the main ones:
     * <ol start="0">
     * <li>Created, never classified</li>
     * <li>Unclassified</li>
     * <li>Ground</li>
     * <li>Low vegetation</li>
     * <li>Medium vegetation</li>
     * <li>High vegetation</li>
     * <li>Building</li>
     * <li>Low point (noise)</li>
     * <li>Reserved</li>
     * <li>Water</li>
     * </ol>
     * @param x the X horizontal coordinate for the lidar sample point
     * @param y the Y horizontal coordinate for the lidar sample point
     * @param z the vertical coordinate for the lidar sample point
     * @param index the record number for the lidar sample point
     * @param classification the classification value assigned to the
     * lidar sample point.
     */
    public VertexWithClassification(
        double x,
        double y,
        double z,
        int index,
        int classification)
    {
        super(x,y,z, index);
        this.reserved0 = (byte)classification;
    }

    /**
     * Gets the classification code indicating the kind of feature
     * from which the sample was captured.
     * @return an integer value in the range zero to 255, usually
     * in the range 0 to 9.
     */
    public int getClassification(){
        return ((int)reserved0)&0xff;
    }

    @Override
    public String toString(){
        return super.toString()+", c="+getClassification();
    }

}
