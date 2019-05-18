/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
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
 * 14/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.gis.utils.ConstraintReaderShapefile;
import org.tinfour.gis.utils.VertexReaderShapefile;
import org.tinfour.utils.HilbertSort;
import org.tinfour.utils.Tincalc;
import org.tinfour.utils.loaders.VertexReaderText;

/**
 * A class for loading bathymetry and shoreline data to be used for estimating
 * lake volume.
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class SvmBathymetryData {

  private double zMin;
  private double zMax;
  private double zMean;
  private int zMaxIndex;
  private int zMinIndex;

  private List<Vertex> soundings = new ArrayList<>();
  private List<Vertex> supplement = new ArrayList<>();
  private  List<PolygonConstraint> boundaryConstraints = new ArrayList<>();;
  private List<PolygonConstraint> lakeConstraints = new ArrayList<>();
  private List<PolygonConstraint> islandConstraints = new ArrayList<>();
  
  double shoreReferenceElevation;

  private Rectangle2D soundingBounds;
  private Rectangle2D bounds;
  private double nominalPointSpacing;
  private long timeToLoadData;

  /**
   * Standard constructor
   */
  public SvmBathymetryData() {

  }

  private List<Vertex>loadVertices(File vertexFile, String dbfBathymetryField) throws IOException {
        String extension = this.getFileExtension(vertexFile); 
    List<Vertex> list ;
    if ("csv".equalsIgnoreCase(extension) || ".txt".equalsIgnoreCase(extension)) {
      VertexReaderText vertexReader = new VertexReaderText(vertexFile);
      list = vertexReader.read(null);
    } else if ("shp".equalsIgnoreCase(extension)) {
      VertexReaderShapefile vls = new VertexReaderShapefile(vertexFile);
      vls.setDbfFieldForZ(dbfBathymetryField);
      list = vls.read(null);
    } else {
      throw new IllegalArgumentException("Unsupported file format "
              + extension
              + " for input soundings " + vertexFile.getPath());
    }
    return list;
  }
  
  
    /**
   * Load main set of soundings from a file. This process is incremental
   * and any new soundings will be added to the list of those already
   * loaded.
   * @param inputSoundingsFile the input file giving soundings.
   * @param dbfBathymetryField the optional string giving the name
   * of the DBF field to be used to extracting data from the input file
   * (used for Shapefiles).
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public void loadSamples(File inputSoundingsFile, String dbfBathymetryField) throws IOException {

    long time0 = System.nanoTime();
 
 
    List<Vertex> list = this.loadVertices(inputSoundingsFile, dbfBathymetryField);
 
    soundings.addAll(list);

    double z0 = Double.POSITIVE_INFINITY;
    double z1 = Double.NEGATIVE_INFINITY;
    double zSum = 0;
    int indexOfMaxZ = -1;
    int indexOfMinZ = -1;

    Vertex v0 = soundings.get(0);
    Rectangle2D r2d = new Rectangle2D.Double(v0.getX(), v0.getY(), 0, 0);
    for (Vertex v : soundings) {
      r2d.add(v.getX(), v.getY());
      double z = v.getZ();
      if (z > z1) {
        z1 = z;
        indexOfMaxZ = v.getIndex();
      }
      if (z < z0) {
        z0 = z;
        indexOfMinZ = v.getIndex();
      }
      zSum += z;

    }

    zMin = z0;
    zMax = z1;
    zMean = zSum / soundings.size();
    zMaxIndex = indexOfMaxZ;
    zMinIndex = indexOfMinZ;
    soundingBounds = r2d;
    double area = soundingBounds.getWidth() * soundingBounds.getHeight();
    if (area == 0) {
      throw new IllegalArgumentException(
              "Degenerate set of input samples, "
              + inputSoundingsFile.getPath());
    }
    int n = soundings.size();
    nominalPointSpacing = Tincalc.sampleSpacing(area, n);
    long time1 = System.nanoTime();

    timeToLoadData += (time1 - time0);
  }
  
  /**
   * Load supplemental soundings from a file. This process is incremental
   * and any new soundings will be added to the list of those already
   * loaded.
   * @param inputSoundingsFile the input file giving soundings.
   * @param dbfBathymetryField the optional string giving the name
   * of the DBF field to be used to extracting data.
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public void loadSupplement(File inputSoundingsFile, String dbfBathymetryField) throws IOException {

    long time0 = System.nanoTime();
 
 
    List<Vertex> list = this.loadVertices(inputSoundingsFile, dbfBathymetryField);
 
    getSupplement().addAll(list);
 
    long time1 = System.nanoTime();
    timeToLoadData += (time1 - time0);
  }
  
  

  public void loadBoundaryConstraints(File target, String dbfFieldForZ) throws IOException {
    long time0 = System.nanoTime();

    try (ConstraintReaderShapefile reader = new ConstraintReaderShapefile(target)) {
      reader.setDbfFieldForZ(dbfFieldForZ);
      List<IConstraint> list = reader.read();
      shoreReferenceElevation = Double.NaN;
      for (IConstraint c : list) {
        if (c instanceof PolygonConstraint) {
          PolygonConstraint p = (PolygonConstraint) c;
          // this is true for both fills and holes because 
          // the fills are oriented clockwise.
          p.setApplicationData(true);
          boundaryConstraints.add(p);
          if(p.getArea()>0){
            lakeConstraints.add(p);
          }else{
            islandConstraints.add(p);
          }
          List<Vertex> vList = p.getVertices();
          if (Double.isNaN(shoreReferenceElevation)) {
            Vertex v = vList.get(0);
            shoreReferenceElevation = v.getZ();
          }
        }
      }
    }
    long time1 = System.nanoTime();
    timeToLoadData += (time1 - time0);
  }

   

  /**
   * Get the minimum sounding value in the source data
   *
   * @return the minimum sounding value
   */
  public double getMinZ() {
    return zMin;
  }

  /**
   * Get the maximum sounding value in the source data
   *
   * @return the maximum sounding value
   */
  public double getMaxZ() {
    return zMax;
  }

  /**
   * Get the mean of the sounding values in the source data
   *
   * @return the mean sounding value
   */
  public double getMeanZ() {
    return zMean;
  }

  /**
   * Gets the nominal spacing for the data set.
   *
   * @return a positive floating point value
   */
  public double getNominalPointSpacing() {
    return nominalPointSpacing;
  }

  /**
   * Get a list of the soundings. The result includes the main set
   * of soundings, but does not include any supplementatal soundings
   * that may have been loaded.
   *
   * @return the soundings
   */
  public List<Vertex> getSoundings() {
    ArrayList<Vertex> result = new ArrayList<>(soundings.size());
    result.addAll(soundings);
    return result;
  }

  /**
   * Gets all bathymetry sounding data, including both the main
   * soundings list and any supplemental data that was loaded.
   * @return a valid list of vertices.
   */
  public List<Vertex>getSoundingsAndSupplements(){
    ArrayList<Vertex>result = new ArrayList<>(soundings.size()+supplement.size());
    result.addAll(soundings);
    result.addAll(supplement);
    
    return result;
  }
  
  /**
   * Get a reduced list of the soundings.  Intended for diagnostic 
   * and rendering purposes.
   * @param nTarget the target number of soundings for the list
   * @return the soundings
   */
  public List<Vertex> getReducedListOfSoundings(int nTarget) {
    int n = soundings.size();
    if (n > 16) {
      HilbertSort hilbertSort = new HilbertSort();
      hilbertSort.sort(soundings);
    }
    ArrayList<Vertex> result = new ArrayList<>(nTarget + 10);
    int skip = (int) (soundings.size() / (double) nTarget + 0.5);
    if (skip == 0) {
      skip = 1;
    }
    int k = 0;
    for (int i = 0; i < nTarget; i++) {
      if (k >= n) {
        break;
      }
      result.add(soundings.get(k));
      k += skip;
    }

    return result;
  }

  /**
   * Get the bounds of the sounding data
   *
   * @return a safe copy of a valid, non-empty rectangle.
   */
  public Rectangle2D getSoundingBounds() {
    return new Rectangle2D.Double(
            soundingBounds.getX(),
            soundingBounds.getY(),
            soundingBounds.getWidth(),
            soundingBounds.getHeight());
  }

  /**
   * Get the overall bounds of the sounding and constraint data
   *
   * @return a safe copy of a valid, non-empty rectangle.
   */
  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(
            bounds.getX(),
            bounds.getY(),
            bounds.getWidth(),
            bounds.getHeight());
  }

  /**
   * Get the reference elevation for the shoreline (conservation pool
   * elevation)
   * @return a valid floating point value greater than the vertical
   * coordinate of the set of bathymetry samples to be used for analysis.
   */
    public double getShoreReferenceElevation() {
    return shoreReferenceElevation;
  }
  
  /**
   * Gets a list of constraints defining the boundary of the body of
   * water to be analyzed.
   * @return a valid, potentially empty list
   */
  public List<PolygonConstraint> getBoundaryConstraints() {
    return boundaryConstraints;
  }
  
  /**
   * Gets the time required to load the input data
   *
   * @return a valid time in nanoseconds\
   */
  public long getTimeToLoadData() {
    return timeToLoadData;
  }

  /**
   * Print a summary of the input data.
   *
   * @param ps a valid print stream such as system output.
   */
  public void printSummary(PrintStream ps) {
    double x0 = soundingBounds.getMinX();
    double y0 = soundingBounds.getMinY();
    double x1 = soundingBounds.getMaxX();
    double y1 = soundingBounds.getMaxY();
    ps.format("Input Data%n");
    ps.format("  Soundings%n");
    ps.format("     Count:               %7d%n", soundings.size());
    ps.format("     Min (x,y,z):         %9.1f, %9.1f, %9.2f (feature %d)%n", x0, y0, zMin, zMinIndex);
    ps.format("     Max (x,y,z):         %9.1f, %9.1f, %9.2f (feature %d)%n", x1, y1, zMax, zMaxIndex);
    ps.format("     width,height:        %9.1f, %9.1f%n", x1 - x0, y1 - y0);
    ps.format("     Est. sample spacing: %9.1f%n", nominalPointSpacing);
  }

  private String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  /**
   * Gets a list of those constraints which enclose the data area
   * @return A valid, potentially empty list of polygon constraints
   * oriented in anticlockwise order.
   */
  public List<PolygonConstraint> getLakeConstraints() {
    return lakeConstraints;
  }

   /**
   * Gets a list of those constraints which enclose island areas (non
   * data areas)
   * @return A valid, potentially empty list of polygon constraints
   * oriented in clockwise order.
   */
  public List<PolygonConstraint> getIslandConstraints() {
    return islandConstraints;
  }

  /**
   * Gets a list of supplementary samples.
   * @return A valid, potentially list
   */
  public List<Vertex> getSupplement() {
    return supplement;
  }

}
