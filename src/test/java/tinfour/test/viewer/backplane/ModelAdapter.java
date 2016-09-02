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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tinfour.common.IIncrementalTin;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import tinfour.utils.HilbertSort;
import tinfour.utils.LinearUnits;
import tinfour.virtual.VirtualIncrementalTin;

/**
 * A model for managing data taken from a text or comma-separated-value
 * file
 */
public class ModelAdapter implements IModel {

  private static final int MAX_VERTICES_IN_TIN = 100000;

  private static AtomicInteger modelSerialIndexSource = new AtomicInteger(0);

  /**
   * Gets the next serial index to be used when constructing new models.
   * This method is used as a way of ensuring that every model has
   * a unique serial index. It should be used when constructing new models
   * (and only when constructing new models).
   * @return a valid integer
   */
  public final static int getNextModelSerialIndex(){
    return modelSerialIndexSource.incrementAndGet();
  }

  final int modelSerialIndex;
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

  long timeToLoad;
  long timeToSort;


  boolean loaded;
  IIncrementalTin referenceTin;
  double referenceReductionFactor;

  List<Vertex> perimeterList;



  /**
   * Construct a model tied to the specified file.
   *
   * @param file a valid text or comma-separated value file
   *
   */
  public ModelAdapter(File file ) {
    this.file = file;
    vertexList = new ArrayList<>();
    modelSerialIndex = getNextModelSerialIndex();
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

    if (loaded) {
      throw new IllegalStateException(
        "Internal error, nultiple calls to load model");
    }

  }

  /**
   * Called to prepare the reference TIN, perimeter, and other supporting
   * elements after the vertices for the model has been loaded.
   * @param list a valid list of the vertices for the model
   * @param monitor the monitor instantiation, if any
   */
  void prepareModelForRendering(List<Vertex>list, IMonitorWithCancellation monitor){
    if(monitor.isCanceled()){
      return;
    }
    vertexListSortedByIndex = new ArrayList<>(list.size());
    vertexListSortedByIndex.addAll(list);
    monitor.postMessage("Preparing model for rendering");
    long time0 = System.currentTimeMillis();
    if (list.size() > 16) {
      HilbertSort hilbertSort = new HilbertSort();
      hilbertSort.sort(list);
    }
    long time1 = System.currentTimeMillis();
    timeToSort = time1 - time0;

    // The nominal point spacing is based on an idealized distribution
    // in which points are arranged in a grid of equilateral triangles
    // (the densest possible regular tesselation of points on a plane)
    int nVertices = list.size();
    double mx0 = getMinX();
    double my0 = getMinY();
    double mx1 = getMaxX();
    double my1 = getMaxY();
    area = (mx1 - mx0) * (my1 - my0);
    nominalPointSpacing = Math.sqrt(area / nVertices / 0.866);


    IIncrementalTin tin = new VirtualIncrementalTin(nominalPointSpacing);


    if (nVertices <= MAX_VERTICES_IN_TIN) {
      tin.add(list, monitor);
      referenceReductionFactor = 1.0;
    } else {
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
      tin.add(thinList, monitor);

      // ensure that the perimeter is fully formed by adding
      // any points that are not inside the tin.  In testing,
      // the number of points to be added has been less than
      // a couple hundred even for data sets containing millions
      // of samples.
      for (Vertex v : list) {
        if (!tin.isPointInsideTin(v.getX(), v.getY())) {
          tin.add(v);
        }
      }
    }

    List<IQuadEdge> pList = tin.getPerimeter();
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
    double xC = perimeterList.get(perimeterList.size()-1).getX();
    double yC = perimeterList.get(perimeterList.size()-1).getY();
    double x1 = 0;
    double y1 = 0;
    for(Vertex v: perimeterList){
      double x0 = x1;
      double y0 = y1;
      x1 = v.getX()-xC;
      y1 = v.getY()-yC;
      s += x0*y1 - x1*y0;
    }
    area = s/2;
    nominalPointSpacing =  Math.sqrt(area / nVertices / 0.866);

    referenceTin = tin;
    vertexList = list;
    loaded = true;
    monitor.postMessage("");
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
  public double getNominalPointSpacing(){
    return nominalPointSpacing;
  }

  @Override
  public double getArea(){
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
    return String.format("%4.2f,%4.2f", x, y);
  }

  @Override
  public String getFormattedX(double x) {
    return String.format("%11.2f", x);
  }

  @Override
  public String getFormattedY(double y) {
    return String.format("%11.2f", y);
  }

  @Override
  public boolean isLoaded() {
    return loaded;
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
  public boolean isCoordinateSystemGeographic() {
    return false;
  }


  @Override
  public int getModelSerialIndex() {
    return modelSerialIndex;
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

}
