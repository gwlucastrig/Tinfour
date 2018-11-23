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
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 *  TO DO: need a good way to pick a coordinate format.
 *         if the horizontal coordinates were in the millions,
 *         or in the millionths, different percision would
 *         be appropriate and at some point we would want to
 *         switch to engineering notation.
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import tinfour.semivirtual.SemiVirtualIncrementalTin;
import tinfour.utils.HilbertSort;
import tinfour.utils.LinearUnits;
import tinfour.utils.Tincalc;

/**
 * A model for managing data taken from a text or comma-separated-value
 * file
 */
public class ModelAdapter implements IModel {

  private static final int MAX_VERTICES_IN_TIN = 100000;
  protected final int serialIndex;
  final File file;

  List<Vertex> vertexList;
  List<Vertex> vertexListSortedByIndex;

  double xMin;
  double yMin;
  double zMin;
  double xMax;
  double yMax;
  double zMax;
  double nominalPointSpacing;
  double area;

  double geoScaleX;
  double geoScaleY;
  double geoOffsetX;
  double geoOffsetY;
  boolean geographicCoordinates;

  long timeToLoad;
  long timeToSort;

  boolean areVerticesLoaded;
  boolean areConstraintsLoaded;

  IIncrementalTin referenceTin;
  double referenceReductionFactor;

  List<Vertex> perimeterList;
  List<IConstraint> constraintList;

  /**
   * Construct a model tied to the specified file.
   *
   * @param file a valid text or comma-separated value file
   *
   */
  public ModelAdapter(File file) {
    this.file = file;
    vertexList = new ArrayList<>();
    constraintList = new ArrayList<>();
    serialIndex = ModelSerialSource.getSerialIndex();
  }

  /**
   * Read the specified file.
   *
   * @param monitor an optional monitor for tracking progress (null
   * if not used)
   * @throws IOException In the event of a non-recoverable error
   * related to I/O or file access.
   */
  @Override
  public void load(IMonitorWithCancellation monitor) throws IOException {

    if (this.isLoaded()) {
      throw new IllegalStateException(
        "Internal error, multiple calls to load model");
    }
  }

  /**
   * Called to prepare the reference TIN, perimeter, and other supporting
   * elements after the vertices for the model has been loaded.
   *
   * @param list a valid list of the vertices for the model
   * @param monitor the monitor instantiation, if any
   */
  void prepareModelForRendering(List<Vertex> list, IMonitorWithCancellation monitor) {
    if (monitor.isCanceled()) {
      return;
    }
    List<Vertex> sortedByIndexList = new ArrayList<>(list.size());
    sortedByIndexList.addAll(list);

    monitor.postMessage("Preparing model for rendering");
    long time0 = System.currentTimeMillis();
    if (list.size() > 16) {
      HilbertSort hilbertSort = new HilbertSort();
      hilbertSort.sort(list);
    }
    long time1 = System.currentTimeMillis();
    synchronized (this) {
      timeToSort = time1 - time0;
      vertexListSortedByIndex = sortedByIndexList;
      vertexList = list;
      // The nominal point spacing is based on an idealized distribution
      // in which points are arranged in a grid of equilateral triangles
      // (the densest possible regular tesselation of points on a plane)
      int nVertices = list.size();
      double mx0 = getMinX();
      double my0 = getMinY();
      double mx1 = getMaxX();
      double my1 = getMaxY();
      area = (mx1 - mx0) * (my1 - my0);
      nominalPointSpacing = Tincalc.sampleSpacing(area, nVertices);

      prepareReferenceTin(list, null, monitor);
      areVerticesLoaded = true;

    }
    monitor.postMessage("");
  }

  /**
   * Prepare the reference TIN and perimeter elements, computing
   * support variables such as nominal point spacing.
   *
   * @param list the master list of vertices
   * @param constraints the list of constraints, null or empty if none
   * @param monitor an optional monitor, or null if none.
   */
  void prepareReferenceTin(
    List<Vertex> list,
    List<IConstraint> constraints,
    IMonitorWithCancellation monitor) {
    int nVertices = list.size();
    referenceTin = new SemiVirtualIncrementalTin(nominalPointSpacing);

    if (nVertices <= MAX_VERTICES_IN_TIN) {
      referenceTin.add(list, monitor);
      referenceReductionFactor = 1.0;
    } else {
      // we're going to step through the list skipping a bunch of
      // vertices.  because the list is Hilbert sorted, the vertices that
      // do get selected should still  give  a pretty good coverage
      // of the overall area of the TIN.
      ArrayList<Vertex> thinList = new ArrayList<>(MAX_VERTICES_IN_TIN + 500);
      double s = (double) nVertices / (double) MAX_VERTICES_IN_TIN;
      referenceReductionFactor = s;
      int priorIndex = -1;
      for (int i = 0; i < MAX_VERTICES_IN_TIN; i++) {
        int index = (int) (i * s + 0.5);
        if (index > priorIndex) {
          thinList.add(list.get(index));
          priorIndex = index;
        }
      }
      if (priorIndex != nVertices - 1) {
        thinList.add(list.get(nVertices - 1));
      }
      referenceTin.add(thinList, monitor);

      // ensure that the perimeter is fully formed by adding
      // any points that are not inside the tin.  In testing,
      // the number of points to be added has been less than
      // a couple hundred even for data sets containing millions
      // of samples. So this should execute fairly quickly,
      // especially since the list has been Hilbert sorted and has a
      // high degree of spatial autocorrelation.
      for (Vertex v : list) {
        if (!referenceTin.isPointInsideTin(v.getX(), v.getY())) {
          referenceTin.add(v);
        }
      }
    }

    // TO DO: this next step is potentially quite time consuming.
    // If the constraints contain a lot of vertices, it could lead to
    // adding more points to the reference tin that were contributed by
    // the above steps.   So this operation may require some rethinking.
    if (constraints != null) {
      referenceTin.addConstraints(constraints, true);
    }

    List<IQuadEdge> pList = referenceTin.getPerimeter();
    perimeterList = new ArrayList<>();
    for (IQuadEdge e : pList) {
      Vertex a = e.getA();
      double x = a.getX();
      double y = a.getY();
      double z = a.getZ();
      perimeterList.add(new Vertex(x, y, z, -1));  // NOPMD
    }

    // recompute the area and nominal point spacing based on the
    // perimeter obtained from the reference TIN. For non-rectangular
    // samples this value map be significantly different than the one
    // computed above.  Note that we adjust all the coordinates of the
    // perimeter to avoid numeric issues for large-magnitude coordinates.
    double s = 0;
    double xC = perimeterList.get(perimeterList.size() - 1).getX();
    double yC = perimeterList.get(perimeterList.size() - 1).getY();
    double x1 = 0;
    double y1 = 0;
    for (Vertex v : perimeterList) {
      double x0 = x1;
      double y0 = y1;
      x1 = v.getX() - xC;
      y1 = v.getY() - yC;
      s += x0 * y1 - x1 * y0;
    }
    area = s / 2;
    nominalPointSpacing = Tincalc.sampleSpacing(area, nVertices);
  }

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMinX() {
    return xMin;
  }

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMaxX() {
    return xMax;
  }

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMinY() {
    return yMin;
  }

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMaxY() {
    return yMax;
  }

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMinZ() {
    return zMin;
  }

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  @Override
  public double getMaxZ() {
    return zMax;
  }

  @Override
  public double getNominalPointSpacing() {
    return nominalPointSpacing;
  }

  @Override
  public double getArea() {
    return area;
  }

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public String getName() {
    return file.getName();
  }

  @Override
  public String getDescription() {
    return "ModelAdapter";
  }

  @Override
  public int getVertexCount() {
    return vertexList.size();
  }

  @Override
  public List<Vertex> getVertexList() {
    return vertexList;
  }

  @Override
  public String getFormattedCoordinates(double x, double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / getGeoScaleY() + getGeoOffsetY(), true);
      sb.append(" / ");
      fmtGeo(fmt, x / getGeoScaleX() + getGeoOffsetX(), false);
      return sb.toString();
    }
    return String.format("%4.2f,%4.2f", x, y);
  }

  @Override
  public String getFormattedX(double x) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, x / getGeoScaleX() + getGeoOffsetX(), false);
      return sb.toString();
    }
    return String.format("%11.2f", x);
  }

  @Override
  public String getFormattedY(double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      sb.append(' '); // to provide vertical alignment with longitudes
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / getGeoScaleY() + getGeoOffsetY(), true);
      return sb.toString();
    }
    return String.format("%11.2f", y);
  }

  void fmtGeo(Formatter fmt, double coord, boolean latFlag) {
    double c = coord;
    if (c < -180) {
      c += 360;
    } else if (c >= 180) {
      c -= 360;
    }
    int x = (int) (Math.abs(c) * 360000 + 0.5);
    int deg = x / 360000;
    int min = (x - deg * 360000) / 6000;
    int sec = x % 6000;
    char q;
    if (latFlag) {
      if (c < 0) {
        q = 'S';
      } else {
        q = 'N';
      }
      fmt.format("%02d\u00b0 %02d' %05.2f\" %c", deg, min, sec / 100.0, q);
    } else {
      if (c < 0) {
        q = 'W';
      } else {
        q = 'E';
      }
      fmt.format("%03d\u00b0 %02d' %05.2f\" %c", deg, min, sec / 100.0, q);
    }
  }

  /**
   * Indicates whether the coordinates used by this instance are
   * geographic in nature.
   *
   * @return true if coordinates are geographic; otherwise, false.
   */
  @Override
  public boolean isCoordinateSystemGeographic() {
    return this.geographicCoordinates;
  }

  @Override
  public boolean isLoaded() {
    synchronized (this) {
      return areVerticesLoaded;
    }
  }

  @Override
  public IIncrementalTin getReferenceTin() {
    return referenceTin;
  }

  @Override
  public double getReferenceReductionFactor() {
    return referenceReductionFactor;
  }

  @Override
  public long getTimeToLoadInMillis() {
    return timeToLoad;
  }

  @Override
  public long getTimeToSortInMillis() {
    return timeToSort;
  }

  @Override
  public List<Vertex> getPerimeterVertices() {
    return perimeterList;
  }

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return at this time, the method always returns LinearUnits.UNDEFINED
   */
  @Override
  public LinearUnits getLinearUnits() {
    return LinearUnits.UNKNOWN;
  }

  @Override
  public Vertex getVertexForIndex(int index) {
    Vertex key = new Vertex(0, 0, 0, index);

    int i = Collections.binarySearch(vertexListSortedByIndex, key, new Comparator<Vertex>() {
      @Override
      public int compare(Vertex t, Vertex t1) {
        return t.getIndex() - t1.getIndex();
      }

    });

    if (i >= 0) {
      return vertexListSortedByIndex.get(i);
    }
    return null;
  }

 @Override
  public void xy2geo(double x, double y, double[] geo) {
    if (geographicCoordinates) {
      geo[0] = y / getGeoScaleY() + getGeoOffsetY();
      geo[1] = x / getGeoScaleX() + getGeoOffsetX();
    }
  }

  @Override
  public void geo2xy(double latitude, double longitude, double[] xy) {
    if (this.geographicCoordinates) {

      double delta = longitude - getGeoOffsetX();
      if (delta < -180) {
        delta += 360;
      } else if (delta >= 180) {
        delta -= 360;
      }
      xy[0] = delta * getGeoScaleX();
      xy[1] = (latitude - getGeoOffsetY()) * getGeoScaleY();
    }
  }

  void copyModelParameters(ModelAdapter model) {

    this.vertexList = model.vertexList;
    this.vertexListSortedByIndex = model.vertexListSortedByIndex;

    this.xMin = model.xMin;
    this.yMin = model.yMin;
    this.zMin = model.zMin;
    this.xMax = model.xMax;
    this.yMax = model.yMax;
    this.zMax = model.zMax;
    this.nominalPointSpacing = model.nominalPointSpacing;
    this.area = model.area;

    this.geoScaleX = model.getGeoScaleX();
    this.geoScaleY = model.getGeoScaleY();
    this.geoOffsetX = model.getGeoOffsetX();
    this.geoOffsetY = model.getGeoOffsetY();
    this.geographicCoordinates = model.geographicCoordinates;

    this.timeToLoad = model.timeToLoad;
    this.timeToSort = model.timeToSort;

    this.areVerticesLoaded = model.areVerticesLoaded;
    this.referenceTin = model.referenceTin;
    this.referenceReductionFactor = model.referenceReductionFactor;

    this.perimeterList = model.perimeterList;

  }

  @Override
  public void addConstraints(File constraintsFile, List<IConstraint> constraints) {
    if (constraints == null || constraints.isEmpty()) {
      return;
    }
    constraintList = new ArrayList<>(constraints.size());
    constraintList.addAll(constraints);
    prepareReferenceTin(vertexList, constraints, null);
  }

  @Override
  public boolean hasConstraints() {
    return constraintList != null && !constraintList.isEmpty();
  }

  @Override
  public List<IConstraint> getConstraints() {
    return constraintList;
  }

  @Override
  public boolean hasVertexSource() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean areVerticesLoaded() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean hasConstraintsSource() {
    return hasConstraints(); // probably to be deprecated
  }

  @Override
  public boolean areConstraintsLoaded() {
    return hasConstraints();  // probably to be deprecated.
  }

 
  @Override
  public double getGeoScaleX() {
    return geoScaleX;
  }

 
  @Override
  public double getGeoScaleY() {
    return geoScaleY;
  }
 
  @Override
  public double getGeoOffsetX() {
    return geoOffsetX;
  }

 
  @Override
  public double getGeoOffsetY() {
    return geoOffsetY;
  }

}
