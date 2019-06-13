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
package org.tinfour.gis.utils;

import com.github.mreutegg.laszip4j.LASPoint;
import com.github.mreutegg.laszip4j.LASReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasPoint;
import org.tinfour.gis.las.LasScaleAndOffset;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;

/**
 * Provides methods and elements for reading a compressed LAS file 
 * (a LAZ file).   Intended to be called from within VertexReaderLas.
 */
class VertexReaderLaz {

  LasScaleAndOffset lasScaleAndOffset;
  ICoordinateTransform coordinateTransform;
  long maximumNumberOfVertices;

  VertexReaderLaz(
          LasScaleAndOffset lasScaleAndOffset,
          ICoordinateTransform coordinateTransform,
          long maximumNumberOfVertices) {
    this.lasScaleAndOffset = lasScaleAndOffset;
    this.coordinateTransform = coordinateTransform;
    this.maximumNumberOfVertices = maximumNumberOfVertices;
  }

 
  List<Vertex> loadVertices(
          File file,
          long nVertices,
          ILasRecordFilter filter,
          IMonitorWithCancellation monitor) throws IOException {
    List<Vertex> list = new ArrayList<>();

    int iProgressThreshold = Integer.MAX_VALUE;
    int pProgressThreshold = 0;
    if (monitor != null) {
      int iPercent = monitor.getReportingIntervalInPercent();
      int iTemp = (int) (nVertices * (iPercent / 100.0) + 0.5);
      if (iTemp > 1) {
        iProgressThreshold = iTemp;
      }
      monitor.reportProgress(0);
    }

    LASReader reader = new LASReader(file);

    LasScaleAndOffset so = lasScaleAndOffset;
    LasPoint t4Point = new LasPoint();
    int iRecord = 0;
    CoordinatePair scratch = new CoordinatePair();
    for (LASPoint p : reader.getPoints()) {
      if (pProgressThreshold == iProgressThreshold) {
        pProgressThreshold = 0;
        monitor.reportProgress((int) (0.1 + (100.0 * (iRecord + 1)) / nVertices));
        if (monitor.isCanceled()) {
          break;
        }
      } else {
        pProgressThreshold++;
      }
      if (list.size() >= this.maximumNumberOfVertices) {
        break;
      }

      iRecord++;

      // TO DO:  LASPoint does not yet have an accessor for "withheld" status.
      // to support the use of a Tinfour filter, the LASPoint is
      // transcribed to a Tinfour LasPoint.  This is confusing and non-optimal
      // but will have to do for now.
      if (p.isWithheld()) {
        continue;
      }
      t4Point.x = p.getX() * so.xScaleFactor + so.xOffset;
      t4Point.y = p.getY() * so.yScaleFactor + so.yOffset;
      t4Point.z = p.getZ() * so.zScaleFactor + so.zOffset;

      t4Point.classification = p.getClassification();
      t4Point.returnNumber = p.getReturnNumber();
      t4Point.numberOfReturns = p.getNumberOfReturns();

      if (filter.accept(t4Point)) {
        double x = t4Point.x;
        double y = t4Point.y;
        double z = t4Point.z;
        if (coordinateTransform != null) {
          boolean status = coordinateTransform.forward(x, y, scratch);
          if (status) {
            x = scratch.x;
            y = scratch.y;
          } else {
            throw new IOException(
                    "Unable to transform coordinates ("
                    + x + "," + y + ") in record " + iRecord);
          }
        }

        Vertex v = new VertexWithClassification( // NOPMD
                x, y, z, iRecord, t4Point.classification);
        list.add(v);

      }
    }
    return list;
  }

}
