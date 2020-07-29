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
 * 12/2018  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.gis.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.gis.las.GeoTiffData;
import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasFileReader;
import org.tinfour.gis.las.LasPoint;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.CoordinatePair;
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.IVertexReader;
import org.tinfour.utils.loaders.SimpleGeographicTransform;

/**
 *
 */
public class VertexReaderLas implements IVertexReader, Closeable {

  private static class AcceptAll implements ILasRecordFilter {

    @Override
    public boolean accept(LasPoint record) {
      return true;
    }

  }
  double xMin, xMax, yMin, yMax, zMin, zMax;
  boolean isSourceInGeographicCoordinates;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;
  ICoordinateTransform coordinateTransform;
  long maximumNumberOfVertices = Integer.MAX_VALUE;
  long numberOfVerticesInSource;
  final LasFileReader reader;
  ILasRecordFilter filter;

  /**
   * A private constructor to deter application code from instantiating this
   * class without a valid file.
   */
  private VertexReaderLas() {
    reader = null;
  }

  public VertexReaderLas(File file) throws IOException {
    reader = new LasFileReader(file);
    long nVertices = reader.getNumberOfPointRecords();
    this.numberOfVerticesInSource = nVertices;
    isSourceInGeographicCoordinates = reader.usesGeographicCoordinates();

    // if the LAS file contains GeoTiffData, it may tell us
    // what kind of linear units to use.  Most often, if it's
    // not meters, it will be from a U.S State Plane coordinate
    // system given in feet.
    GeoTiffData gtd = reader.getGeoTiffData();
    if (gtd != null && gtd.containsKey(GeoTiffData.ProjLinearUnitsGeoKey)) {
      int linUnits = gtd.getInteger(GeoTiffData.ProjLinearUnitsGeoKey);
      if (linUnits == GeoTiffData.LinearUnitCodeFeet
              || linUnits == GeoTiffData.LinearUnitCodeFeetUS) {
        this.linearUnits = LinearUnits.FEET;
      }
    }

    if (isSourceInGeographicCoordinates) {
      // adjust the earth radius according to latitude.
      // if cenLat were zero, the adjusted radius would be the
      // equatorial radius. If it were 90, it would be the polar radius.
      double cenLat = (reader.getMinY() + reader.getMaxY()) / 2;
      double cenLon = (reader.getMinX() + reader.getMaxX()) / 2;
      coordinateTransform
              = new SimpleGeographicTransform(
                      cenLat,
                      cenLon,
                      linearUnits);
    }

    filter = new AcceptAll();

  }

  /**
   * Set a filter to be used when reading the content of a LAS file
   *
   * @param filter a valid filter or a null if all records are to be accepted.
   */
  public void setFilter(ILasRecordFilter filter) {
    if (filter == null) {
      this.filter = new AcceptAll();
    } else {
      this.filter = filter;
    }
  }

  /**
   * Set the maximum number of vertices to load from a file. This setting is
   * provided in response to the potential for a Lidar file to contain a very
   * large number of vertices.
   *
   * @param maximumNumberOfVertices a positive integer
   */
  public void setMaximumNumberOfVertices(long maximumNumberOfVertices) {
    if (maximumNumberOfVertices < 0) {
      throw new IllegalArgumentException(
              "Maximum number of vertices must not be less than zero");
    }
    this.maximumNumberOfVertices = maximumNumberOfVertices;
  }

  @Override
  public List<Vertex> read(IMonitorWithCancellation monitor) throws IOException {
    long nVertices = numberOfVerticesInSource;
    if (isLazFile(reader.getFile())) {
      VertexReaderLaz lazReader = new VertexReaderLaz(
              reader.getScaleAndOffset(),
              coordinateTransform,
              maximumNumberOfVertices);
      List<Vertex> list = lazReader.loadVertices(
              reader.getFile(),
              nVertices,
              filter,
              monitor);
      postProcessList(list);
      return list;
    }
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

    CoordinatePair scratch = new CoordinatePair();
    LasPoint p = new LasPoint();
    for (long iRecord = 0; iRecord < nVertices; iRecord++) {
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
      reader.readRecord(iRecord, p);
      if (p.withheld) {
        continue;
      }
      if (filter.accept(p)) {

        double x = p.x;
        double y = p.y;
        double z = p.z;
        if (this.coordinateTransform != null) {
          boolean status = coordinateTransform.forward(x, y, scratch);
          if (!status) {
            throw new IOException(
                    "Unable to transform coordinates ("
                    + x + "," + y + ") in record " + iRecord);
          }
          x = scratch.x;
          y = scratch.y;
        }
        Vertex v = new VertexWithClassification( // NOPMD
                x, y, z, (int) iRecord, p.classification);
        list.add(v);
      }
    }

    postProcessList(list);
    return list;
  }

  private void postProcessList(List<Vertex> list) {
    if (list.isEmpty()) {
      return; // nothing to do.
    }

    Vertex a = list.get(0);
    xMin = a.getX();
    xMax = a.getX();
    yMin = a.getY();
    yMax = a.getY();
    zMin = a.getZ();
    zMax = a.getZ();

    for (Vertex v : list) {
      double x = v.getX();
      double y = v.getY();
      double z = v.getZ();
      if (x < xMin) {
        xMin = x;
      } else if (x > xMax) {
        xMax = x;
      }
      if (y < yMin) {
        yMin = y;
      } else if (y > yMax) {
        yMax = y;
      }
      if (z < zMin) {
        zMin = z;
      } else if (z > zMax) {
        zMax = z;
      }
    }
  }

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getXMin() {
    return xMin;
  }

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getXMax() {
    return xMax;
  }

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getYMin() {
    return yMin;
  }

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getYMax() {
    return yMax;
  }

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getZMin() {
    return zMin;
  }

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getZMax() {
    return zMax;
  }

  /**
   * Indicates whether the source data was in geographic coordinates
   *
   * @return true if the source data used geographic coordinates; otherwise,
   * false.
   */
  @Override
  public boolean isSourceInGeographicCoordinates() {
    return isSourceInGeographicCoordinates;
  }

  /**
   * Gets the linear units for the coordinate system used by the data. It is
   * assumed that the vertical and horizontal coordinate systems will be in the
   * same unit system, though assumption could change in a future
   * implementation.
   *
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits() {
    return linearUnits;
  }

  /**
   * Sets the linear units for the coordinate system used by the horizontal (x
   * and y) coordinates of the data. This method is intended for cases when the
   * application can provide information that is not otherwise available in the
   * source data.
   *
   * @param linearUnits a valid instance
   */
  public void setLinearUnits(LinearUnits linearUnits) {
    if (linearUnits == null) {
      this.linearUnits = LinearUnits.UNKNOWN;
    } else {
      this.linearUnits = linearUnits;
    }
  }

  /**
   * Gets the coordinate transform associated with this instance. May be null if
   * no coordinate transform was set.
   *
   * @return a valid transform or a null if none was set.
   */
  @Override
  public ICoordinateTransform getCoordinateTransform() {
    return coordinateTransform;
  }

  /**
   * Sets a coordinate transform to be used for mapping values from the source
   * file to vertex coordinates.
   *
   * @param transform a valid transform or a null if none is to be applied.
   */
  @Override
  public void setCoordinateTransform(ICoordinateTransform transform) {
    this.coordinateTransform = transform;
  }

  @Override
  public void close() throws IOException {
    // no action required at this time
  }

  private boolean isLazFile(File file) {
    String name = file.getName();
    int n = name.length();
    return n > 4
            && ".LAZ".equalsIgnoreCase(name.substring(n - 4, n));
  }

  /**
   * Gets the number of vertices in the source file
   *
   * @return a positive integer value
   */
  public long getNumberOfVerticesInSource() {
    return this.numberOfVerticesInSource;
  }
}
