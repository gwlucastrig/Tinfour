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
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 02/2015 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.tinfour.common.Vertex;
import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasPoint;
import org.tinfour.gis.utils.VertexReaderLas;
import org.tinfour.gis.utils.VertexReaderShapefile;
import org.tinfour.utils.HilbertSort;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.IVertexReader;
import org.tinfour.utils.loaders.VertexReaderText;

/**
 * A utility for loading vertices from a file for testing
 */
public class VertexLoader {

  static final double eRadius = 6378137; // WGS-84 equatorial radius
  static final double eFlattening = 1 / 298.257223560; // WGS-84

  double xMin, xMax, yMin, yMax, zMin, zMax;
  long maximumNumberOfVertices = Integer.MAX_VALUE;
  long numberOfVerticesInSource;
  boolean hilbertSortEnabled;
  double timeForSort;
  double timeForLoad;
  boolean isClippingSet;
  double xClipMin, xClipMax, yClipMin, yClipMax;
  boolean isSourceInGeographicCoordinates;
  ICoordinateTransform coordinateTransform;
  TestOptions.GeoCoordinateOption geoCoordOpt;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;

  /**
   * Sets the maximum number of vertices that will be loaded from a source file.
   * Useful in memory constrained environments
   *
   * @param maxN the maximum number of vertices.
   */
  public void setMaximumNumberOfVertices(long maxN) {
    maximumNumberOfVertices = maxN;
  }

  //private boolean isLazFile(File file) {
  //  String name = file.getName();
  //  int n = name.length();
  //  return n > 4
  //          && ".LAZ".equalsIgnoreCase(name.substring(n - 4, n));
  //}

  /**
   * Set the loader to pre-sort the vertices to improve their spatial locality
   * before processing. Default is to not sort vertices.
   *
   * @param enabled true if the sort is enabled; otherwise, false.
   */
  public void setPreSortEnabed(boolean enabled) {
    hilbertSortEnabled = enabled;
  }

  /**
   * @param args the command line arguments
   */
  private static class ThinningClassificationFilter implements ILasRecordFilter {

    int classification;
    double thinningFactor;
    double xClipMin, xClipMax, yClipMin, yClipMax;
    boolean isClipBoundsSet;
    Random random = new Random();

    /**
     * Implement a thinning filter.
     *
     * @param classification only accept points of the designated classification
     * (or -1 for wildcards).
     * @param thinningFactor the fraction of the sample points to accept (1.0 to
     * include all sample points).
     * @param clipBounds bounds for clipping (xmin, xmax, ymin, ymax)
     */
    public ThinningClassificationFilter(int classification, double thinningFactor, double[] clipBounds) {
      this.classification = classification;
      this.thinningFactor = thinningFactor;

      if (clipBounds != null && clipBounds.length >= 4) {
        this.isClipBoundsSet = true;
        xClipMin = clipBounds[0];
        xClipMax = clipBounds[1];
        yClipMin = clipBounds[2];
        yClipMax = clipBounds[3];
      }
    }

    @Override
    public boolean accept(LasPoint record) {
      // on the theory that withheld records are relatively uncommon
      // test on classification first
      if (record.withheld) {
        return false;
      }
      if (classification >= 0 && record.classification != classification) {
        return false;
      }
      if (isClipBoundsSet) {
        double x = record.x;
        double y = record.y;
        if (x < xClipMin || x > xClipMax || y < yClipMin || y > yClipMax) {
          return false;
        }
      }
      if (thinningFactor < 1.0) {
        double test = random.nextDouble();
        return test < thinningFactor;
      }
      return true;

    }

  }

  /**
   * Read the LAS file as specified in the supplied options, applying any
   * settings such as maxNumberOfVertices or setPreSortEnabled that are passed
   * as part of the options object.
   *
   * @param options a valid options object
   * @return a list of vertices (potentially empty)
   * @throws IOException in the event of a non-recoverable I/O condition such as
   * file-not-found.
   */
  public List<Vertex> readInputFile(TestOptions options) throws IOException {
    geoCoordOpt = options.getGeoCoordinateOption();
    this.hilbertSortEnabled = options.isPreSortEnabled(hilbertSortEnabled);

    File file = options.getInputFile();
    if (file == null) {
      throw new IllegalArgumentException("Missing specification for input file");
    }
    String ext = options.getFileExtension(file);
    if (ext == null) {
      ext = "text";
    } else {
      ext = ext.toLowerCase();
    }
    IVertexReader reader = null;
    List<Vertex> list = null;
    if ("csv".equals(ext)) {
      try (VertexReaderText textReader = new VertexReaderText(file)) {
        reader = textReader;
        list = reader.read(null);
      }
    } else if ("txt".equals(ext)) {
      try (VertexReaderText textReader = new VertexReaderText(file)) {
        reader = textReader;
        char delimiter = options.getDelimiter();
        if (delimiter != 0) {
          ((VertexReaderText) reader).setDelimiter(delimiter);
        }
        list = reader.read(null);
      }
    } else if ("shp".equals(ext)) {
      try (VertexReaderShapefile sReader = new VertexReaderShapefile(file)) {
        reader = sReader;
        list = sReader.read(null);
      }
    } else if ("las".equals(ext) || "laz".equals(ext)) {
      try (VertexReaderLas lasReader = new VertexReaderLas(file)) {
        reader = lasReader;
        long nRecords = lasReader.getNumberOfVerticesInSource();
        linearUnits = lasReader.getLinearUnits();

        int classification = options.getLidarClass();
        double thinning = options.getLidarThinningFactor();
        maximumNumberOfVertices = options.getMaxVertices(Long.MAX_VALUE);
        if (maximumNumberOfVertices < Long.MAX_VALUE
                && nRecords > maximumNumberOfVertices) {
          double tv = (double) maximumNumberOfVertices / (double) nRecords;
          if (tv < thinning) {
            thinning = tv;
          }
        }

        ILasRecordFilter filter;

        double[] clipBounds = options.getClipBounds();
        if (classification == -1 && thinning == 1.0 && clipBounds == null) {
          filter = new ILasRecordFilter() {
            @Override
            public boolean accept(LasPoint record) {
              return !record.withheld;
            }

          };
        } else {
          filter = new ThinningClassificationFilter(
                  classification, thinning, clipBounds);
        }

        lasReader.setFilter(filter);

        list = lasReader.read(null);
      }
    }
    if (list == null || reader==null) {
      throw new IOException("Unable to obtain vertices from input "
              + file.getPath());
    }

    isSourceInGeographicCoordinates =
            reader.isSourceInGeographicCoordinates();
    this.coordinateTransform = reader.getCoordinateTransform();
    postProcessList(list);
    return list;
  }

  private void postProcessList(List<Vertex> list) {

    if (list.isEmpty()) {
      xMin = Double.NaN;
      xMax = Double.NaN;
      yMin = Double.NaN;
      yMax = Double.NaN;
      zMin = Double.NaN;
      zMax = Double.NaN;
      return;
    }

    Vertex v = list.get(0);
    xMin = v.getX();
    xMax = xMin;
    yMin = v.getY();
    yMax = yMin;
    zMin = v.getZ();
    zMax = zMin;
    for (Vertex vertex : list) {
      double x = vertex.getX();
      double y = vertex.getY();
      double z = vertex.getZ();

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

    if (hilbertSortEnabled) {
      long time0 = System.nanoTime();
      HilbertSort hilbert = new HilbertSort();
      hilbert.sort(list);
      long time1 = System.nanoTime();
      timeForSort = (time1 - time0) / 1000000.0;
    }
  }

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getXMin() {
    return xMin;
  }

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getXMax() {
    return xMax;
  }

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getYMin() {
    return yMin;
  }

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getYMax() {
    return yMax;
  }

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getZMin() {
    return zMin;
  }

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  public double getZMax() {
    return zMax;
  }

  /**
   * Gets the time required for the spatial locality sort or zero if not
   * performed
   *
   * @return a positive floating point value in milliseconds.
   */
  public double getTimeForPreSort() {
    return timeForSort;
  }

  /**
   * Gets the total time to load a file (including the time required for
   * pre-sort if enabled).
   *
   * @return a time in milliseconds
   */
  public double getTimeForLoad() {
    return timeForLoad;
  }

  /**
   * Gets the number of vertices in source file (including those that were
   * marked as withheld.
   *
   * @return a value of zero or greater.
   */
  public long getNumberOfVerticesInFile() {
    return numberOfVerticesInSource;
  }

  /**
   * Sets the limits for accepting vertices
   *
   * @param xClipMin the minimum x coordinate for accepting vertices.
   * @param xClipMax the maximum x coordinate for accepting vertices.
   * @param yClipMin the minimum y coordinate for accepting vertices.
   * @param yClipMax the maximum y coordinate for accepting vertices.
   */
  public void setClip(double xClipMin, double xClipMax, double yClipMin, double yClipMax) {
    this.isClippingSet = true;
    this.xClipMin = xClipMin;
    this.xClipMax = xClipMax;
    this.yClipMin = yClipMin;
    this.yClipMax = yClipMax;
  }

  /**
   * Indicates whether the source data was in geographic coordinates
   *
   * @return true if the source data used geographic coordinates; otherwise,
   * false.
   */
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
   * Gets the coordinate transform, if any
   * @return if provided, a valid instance; otherwise, a null.
   */
  public ICoordinateTransform getCoordinateTransform(){
    return coordinateTransform;
  }
}
