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
 * 02/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import com.github.mreutegg.laszip4j.LASPoint;
import com.github.mreutegg.laszip4j.LASReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.las.ILasRecordFilter;
import tinfour.las.LasPoint;

public class VertexLoaderLaz {

  double geoOffsetX;
  double geoScaleX;
  double geoOffsetY;
  double geoScaleY;
  long  maximumNumberOfVertices;

  VertexLoaderLaz(
    double geoOffsetX,
    double geoScaleX,
    double geoOffsetY,
    double geoScaleY,
    long  maximumNumberOfVertices) {
    this.geoOffsetX = geoOffsetX;
    this.geoScaleX = geoScaleX;
    this.geoOffsetY = geoOffsetY;
    this.geoScaleY = geoScaleY;
    this.maximumNumberOfVertices = maximumNumberOfVertices;
  }

  public List<Vertex> loadVertices(
    File file,
    long nVertices,
    ILasRecordFilter filter,
    int iProgressThreshold,
    IMonitorWithCancellation progressMonitor) throws IOException {
    List<Vertex> list = new ArrayList<>();

    int pProgressThreshold = 0;
    LASReader reader = new LASReader(file);

    LasPoint t4Point = new LasPoint();
    int iRecord = 0;
    for (LASPoint p : reader.getPoints()) {
      if (pProgressThreshold == iProgressThreshold) {
        pProgressThreshold = 0;
        progressMonitor.reportProgress((int) (0.1 + (100.0 * (iRecord + 1)) / nVertices));
      }
      iRecord++;
      pProgressThreshold++;

      // TO DO:  LASPoint does not yet have an accessor for "withheld" status.

      // to support the use of a Tinfour filter, the LASPoint is
      // transcribed to a Tinfour LasPoint.  This is confusing and non-optimal
      // but will have to do for now.
      t4Point.x = p.getX();
      t4Point.y = p.getY();
      t4Point.z = p.getZ();
      t4Point.classification = p.getClassification();
      t4Point.returnNumber = p.getReturnNumber();
      t4Point.numberOfReturns = p.getNumberOfReturns();

      if (filter.accept(t4Point)) {
        double x = (t4Point.x - geoOffsetX) * geoScaleX;
        double y = (t4Point.y - geoOffsetY) * geoScaleY;
        double z = t4Point.z;
        Vertex v = new VertexWithClassification( // NOPMD
          x, y, z, iRecord, t4Point.classification);
        list.add(v);
        if (list.size() >= this.maximumNumberOfVertices) {
          break;
        }
      }
    }
    return list;
  }

}
