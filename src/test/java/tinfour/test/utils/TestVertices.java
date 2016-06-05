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
 * 02/2016  G. Lucas     Created
 *
 * Notes:
 *
 *  Credit where credit is due: I got the idea for this class from
 *  Bill Dwyer's page at https://github.com/themadcreator/delaunay
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tinfour.common.Vertex;

/**
 * A utility for creating randomly positioned vertices for test purposes.
 */
public class TestVertices {

  /**
   * A private constructor to deter applications from instantiating
   * this class directly.
   */
  private TestVertices() {

  }

  /**
   * Creates a set of vertices randomly positioned over a
   * square area from (0,0) to (1,1). The vertices are assigned
   * (x,y) values based an instances of Java Random with the specified
   * seed. Z values are computed based on z = (x-0.5)^2 + (y-0.5)^2
   * @param nVertices the number of vertices to be created
   * @param seed a seed for creating the pseudo-random series.
   * @return a list containing the specified number of vertices
   */
  public static List<Vertex> makeRandomVertices(int nVertices, int seed) {
    List<Vertex> vList = new ArrayList<>(nVertices);
    Random r = new Random(seed);
    for (int i = 0; i < nVertices; i++) {
      double x = r.nextDouble();
      double y = r.nextDouble();
      double z = x*x + y*y -(x+y) -0.5;  // (x-0.5)^2 + (y-0.5)^2
      vList.add(new Vertex(x, y, z, i));
    }

    return vList;
  }

}
