/*
 * Copyright 2014 Gary W. Lucas.
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
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 06/2014  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
 

package tinfour.interpolation;

import tinfour.common.Vertex;

/**
 * A valuator that returns the Z coordinate of a vertex.
 */
public class VertexValuatorDefault implements IVertexValuator {

    @Override
    public double value(Vertex v) {
       return v.getZ();
    }
    
}
