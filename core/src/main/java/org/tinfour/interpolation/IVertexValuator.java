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
 * An interface for specifying methods of accessing a value
 * from a Vertex.
 * <p>Clearly, the most straight-forward way to create a surface from a sample
 * of vertices is by accessing their Z coordinates. But in cases where a 
 * derived value is required, or where a value needs to be computed using
 * information available to the application but outside the scope of the
 * TIN implementation, this interface provides a mechanism for doing so. 
 * <h1>Development Notes</h1>
 * <p>For a default implementation, see the VertexValuatorGetZ class.
 * <p>The functionality provided by this interface seems like a natural 
 * candidate for  the Java closure concept introduced with Java 8. 
 * At the time of this implementation, Java 8 is only a few months old
 * and not yet in wide use. This design decision may be revisited later.
 */
public interface IVertexValuator {
    /**
     * Given a vertex v, obtain its value
     * @param v a vertex
     * @return a value interpreted from vertex v.
     */
    public double value(Vertex v);
}
