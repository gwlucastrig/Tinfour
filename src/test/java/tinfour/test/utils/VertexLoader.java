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
package tinfour.test.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.las.GeoTiffData;
import tinfour.las.ILasRecordFilter;
import tinfour.las.LasFileReader;
import tinfour.las.LasPoint;
import tinfour.utils.HilbertSort;
import tinfour.utils.LinearUnits;

/**
 * A utility for loading vertices from a file for testing
 */
public class VertexLoader
{

  double xMin, xMax, yMin, yMax, zMin, zMax;
  long maximumNumberOfVertices = Integer.MAX_VALUE;
  long numberOfVerticesInSource;
  boolean hilbertSortEnabled;
  double timeForSort;
  double timeForLoad;
  boolean isClippingSet;
  double xClipMin, xClipMax, yClipMin, yClipMax;
  double geoScaleX = 1;
  double geoScaleY = 1;
  double geoOffsetX;
  double geoOffsetY;
  boolean isSourceInGeographicCoordinates;
  TestOptions.GeoCoordinateOption geoCoordOpt;
  LinearUnits linearUnits = LinearUnits.UNKNOWN;

  /**
   * Sets the maximum number of vertices that will be loaded from a source
   * file. Useful in memory constrained environments
   *
   * @param maxN the maximum number of vertices.
   */
  public void setMaximumNumberOfVertices(long maxN) {
    maximumNumberOfVertices = maxN;
  }

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
     * @param classification only accept points of the designated
     * classification (or -1 for wildcards).
     * @param thinningFactor the fraction of the sample points to accept
     * (1.0 to include all sample points).
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
   * @throws IOException in the event of a non-recoverable I/O condition such
   * as file-not-found.
   */
  public List<Vertex> readInputFile(TestOptions options) throws IOException {
    File file = options.getInputFile();
    if (file == null) {
      throw new IllegalArgumentException("Missing specification for input file");
    }
    String ext = options.getFileExtension(file);
    if ("csv".equalsIgnoreCase(ext)) {
      return readDelimitedFile(file, ',');
    } else if ("txt".equals(ext)) {
      char delimiter = options.getDelimiter();
      if (delimiter == 0) {
        // use the default delimiter, a space
        delimiter = ' ';
      }
      return readDelimitedFile(file, delimiter);
    }

    geoCoordOpt = options.getGeoCoordinateOption();

    LasFileReader reader = new LasFileReader(file);
    long nRecords = reader.getNumberOfPointRecords();
     linearUnits = reader.getLinearUnits();

    int classification = options.getLidarClass();
    double thinning = options.getLidarThinningFactor();
    maximumNumberOfVertices = options.getMaxVertices(Long.MAX_VALUE);
    if (maximumNumberOfVertices < Long.MAX_VALUE
      && nRecords > maximumNumberOfVertices)
    {
      double tv = (double) maximumNumberOfVertices / (double) nRecords;
      if (tv < thinning) {
        thinning = tv;
      }
    }

    hilbertSortEnabled = options.isPreSortEnabled(false);

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

    return readLasFile(reader, filter, null);

  }

  /**
   * Reads the vertices from the specified file. Options specified via setter
   * methods such as setMaximumNumberOfVertices() are and setPreSortEnabled()
   * are applied.
   *
   * @param file a valid file object
   * @param vFilter a filter for selecting records from the LAS file, or a
   * null to used the default (accept all records except those marked as
   * "withheld").
   * @param progressMonitor an optional implementation of an interface for
   * monitoring progress, or a null if not used
   * @return a valid, potentially empty list of vertices
   * @throws IOException in the event of a non-recoverable I/O condition such
   * as file-not-found.
   */
  public List<Vertex> readLasFile(File file,
    ILasRecordFilter vFilter,
    IMonitorWithCancellation progressMonitor) throws IOException {
    ILasRecordFilter filter = vFilter;
    if (filter == null) {
      filter = new ILasRecordFilter() {
        @Override
        public boolean accept(LasPoint record) {
          return !record.withheld;
        }

      };
    }

    LasFileReader reader = new LasFileReader(file);
    linearUnits = reader.getLinearUnits();

    List<Vertex> list = readLasFile(reader, filter, progressMonitor);
    reader.close();
    return list;
  }

  static final double eRadius = 6378137; // WGS-84 equatorial radius
  static final double eFlattening = 1 / 298.257223560; // WGS-84

  /**
   * Reads the vertices from the specified LAS file reader instance. The
   * reader is not closed when the process is complete. Options specified via
   * setter methods such as setMaximumNumberOfVertices() are and
   * setPreSortEnabled() are applied.
   *
   * @param reader a valid instance of the LAS file reader class.
   * @param vFilter a filter for selecting records from the LAS file, or a
   * null to used the default (accept all records except those marked as
   * "withheld").
   * @param progressMonitor an optional implementation of an interface for
   * monitoring progress, or a null if not used
   * @return a valid, potentially empty list of vertices
   * @throws IOException in the event of a non-recoverable I/O condition such
   * as file-not-found.
   */
  public List<Vertex> readLasFile(
    LasFileReader reader,
    ILasRecordFilter vFilter,
    IMonitorWithCancellation progressMonitor) throws IOException {
    double x0 = reader.getMinX();
    double x1 = reader.getMaxX();
    double y0 = reader.getMinY();
    double y1 = reader.getMaxY();
    double xm = (x1 + x0) / 2;
    double ym = (y1 + y0) / 2;

    geoScaleX = 1;
    geoScaleY = 1;
    isSourceInGeographicCoordinates = reader.usesGeographicCoordinates();

    if (isSourceInGeographicCoordinates
      && geoCoordOpt != TestOptions.GeoCoordinateOption.Degrees) {
        // compute simple scale for transforming x and y coordinates
      // from lat/lon to meters
      double r = eRadius;

      if (geoCoordOpt == TestOptions.GeoCoordinateOption.Feet) {
        r = eRadius * 1.0936 * 3;
      }else{
        // if the LAS file contains GeoTiffData, it may tell us
        // what kind of linear units to use.  Most often, if it's
        // not meters, it will be from a U.S State Plane coordinate
        // system given in feet.
        GeoTiffData gtd = reader.getGeoTiffData();
        if(gtd!=null && gtd.containsKey(GeoTiffData.ProjLinearUnitsGeoKey)){
          int linUnits = gtd.getInteger(GeoTiffData.ProjLinearUnitsGeoKey);
          if(linUnits==GeoTiffData.LinearUnitCodeFeet
            || linUnits==GeoTiffData.LinearUnitCodeFeetUS)
          {
            r = eRadius*1.0936*3;
          }
        }
      }

        // adjust the earth radius according to latitude.
      // if cenLat were zero, the adjusted radius would be the
      // equatorial radius. If it were 90, it would be the polar radius.
      double cenLat = (reader.getMinY() + reader.getMaxY()) / 2;
      double phi = Math.toRadians(cenLat);
      double sinPhi = Math.sin(phi);
      double adjustment = (1 - eFlattening * sinPhi * sinPhi);
      double adjRadius = adjustment * r;

      geoScaleX = adjRadius * Math.cos(phi) * (Math.PI / 180);
      geoScaleY = adjRadius * (Math.PI / 180);
      geoOffsetX = xm;
      geoOffsetY = ym;
    }

    ILasRecordFilter filter = vFilter;
    if (filter == null) {
      filter = new ILasRecordFilter() {
        @Override
        public boolean accept(LasPoint record) {
          return !record.withheld;
        }

      };
    }

    ArrayList<Vertex> list = new ArrayList<>();
    long time0 = System.nanoTime();
    long nVertices = reader.getNumberOfPointRecords();
    this.numberOfVerticesInSource = nVertices;

    int iProgressThreshold = Integer.MAX_VALUE;
    int pProgressThreshold = 0;
    if (progressMonitor != null) {
      int iPercent = progressMonitor.getReportingIntervalInPercent();
      int iTemp = (int) (nVertices * (iPercent / 100.0) + 0.5);
      if (iTemp > 1) {
        iProgressThreshold = iTemp;
      }
    }

    LasPoint p = new LasPoint();
    for (long iRecord = 0; iRecord < nVertices; iRecord++) {
      if (pProgressThreshold == iProgressThreshold) {
        pProgressThreshold = 0;
        progressMonitor.reportProgress((int) (0.1+(100.0 * (iRecord + 1)) / nVertices));
      }
      pProgressThreshold++;
      reader.readRecord(iRecord, p);
      if (filter.accept(p)) {
        double x = (p.x - geoOffsetX) * geoScaleX;
        double y = (p.y - geoOffsetY) * geoScaleY;
        double z = p.z;
        Vertex v = new VertexWithClassification( // NOPMD
          x, y, z, (int) iRecord, p.classification);
        //Vertex v = new Vertex(x, y, z, (int) iRecord);
        list.add(v);
        if (list.size() >= this.maximumNumberOfVertices) {
          break;
        }
      }
    }

    long time1 = System.nanoTime();
    timeForLoad = (time1 - time0) / 1000000.0;
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
   * @return true if the source data used geographic coordinates;
   * otherwise, false.
   */
  public boolean isSourceInGeographicCoordinates() {
    return isSourceInGeographicCoordinates;
  }

  public double getGeoScaleX() {
    return geoScaleX;
  }

  public double getGeoScaleY() {
    return geoScaleY;
  }

  public double getGeoOffsetX() {
    return geoOffsetX;
  }

  public double getGeoOffsetY() {
    return geoOffsetY;
  }

  public List<Vertex> readDelimitedFile(File file, char delimiter) throws IOException {
    DelimitedReader dlim = new DelimitedReader(file, delimiter);
    List<String> sList = dlim.readStrings();

    List<Vertex> vList = new ArrayList<>();
    boolean headerRow = false;
    int xColumn = 0;
    int yColumn = 1;
    int zColumn = 2;
    int k = 0;
    for (String s : sList) {
      char c = s.charAt(0);
      if (Character.isAlphabetic(c) || c == '_') {
        headerRow = true;
        if ("x".equalsIgnoreCase(s)) {
          xColumn = k;
        } else if ("y".equalsIgnoreCase(s)) {
          yColumn = k;
        } else if ("z".equalsIgnoreCase(s)) {
          zColumn = k;
        }
      }
      k++;
    }

    k = 0;
    if (!headerRow) {
      double x = Double.parseDouble(sList.get(xColumn));
      double y = Double.parseDouble(sList.get(yColumn));
      double z = Double.parseDouble(sList.get(zColumn));
      vList.add(new Vertex(x, y, z, k));
      k++;
    }
    while (!(sList = dlim.readStrings()).isEmpty()) {
      double x = Double.parseDouble(sList.get(xColumn));
      double y = Double.parseDouble(sList.get(yColumn));
      double z = Double.parseDouble(sList.get(zColumn));
      vList.add(new Vertex(x, y, z, k)); // NOPMD
      k++;
    }
    postProcessList(vList);

    return vList;
  }

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits() {
    return linearUnits;
  }
}
