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
 * 11/2018  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.examples.lake;

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
import org.tinfour.utils.loaders.ICoordinateTransform;
import org.tinfour.utils.loaders.IVertexReader;
import org.tinfour.utils.loaders.VertexReaderText;

/**
 * A class for loading bathymetry and shoreline data to be used for estimating
 * lake volume.
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class BathymetryData {

  private final double zMin;
  private final double zMax;
  private final double zMean;
  private final int zMaxIndex;
  private final int zMinIndex;

  private final List<Vertex> soundings;
  private final List<PolygonConstraint> lakeConstraints;
  private final List<PolygonConstraint> islandConstraints;
  private final Rectangle2D soundingBounds;
  private final Rectangle2D bounds;
  private final double nominalPointSpacing;
  private final long timeToLoadData;

  /**
   * Load the test data from the specified files
   * <p>
   * The input soundings file may be a CSV file or a Shapefile. If the file is a
   * Shapefile, there are two options for obtaining the depth coordinate. The
   * depth coordinate may be taken from the z coordinates for each Shapefile
   * point (if supplied) or they may be taken from a named field in the DBF
   * file. If the dbfBathymetryField string is null, then the Z coordinate will
   * be used. Otherwise, this class will use the named field.
   *
   * @param inputSoundingsFile a mandatory path for a CSV file giving soundings
   * @param inputShorelineFile a mandatory path for a Shapefile file giving
   * soundings
   * @param inputIslandFile an optional path for a Shapefile file giving
   * soundings
   * @param dbfBathymetryField an option field used when the inputSoundingsFile
   * is a Shapefile indicating DBF field of interest.
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public BathymetryData(
          File inputSoundingsFile,
          File inputShorelineFile,
          File inputIslandFile,
          String dbfBathymetryField)
          throws IOException {
    long time0 = System.nanoTime();
    String extension = this.getFileExtension(inputSoundingsFile);

    IVertexReader vertexReader = null;

    if ("csv".equalsIgnoreCase(extension) || ".txt".equalsIgnoreCase(extension)) {
      vertexReader = new VertexReaderText(inputSoundingsFile);
      soundings = vertexReader.read(null);
    } else if ("shp".equalsIgnoreCase(extension)) {
      VertexReaderShapefile vls = new VertexReaderShapefile(inputSoundingsFile);
      vls.setDbfFieldForZ(dbfBathymetryField);
      soundings = vls.read(null);
      vertexReader = vls;
    } else {
      throw new IllegalArgumentException("Unsupported file format "
              + extension
              + " for input soundings " + inputSoundingsFile.getPath());
    }
    if (soundings.size() < 3) {
      throw new IllegalArgumentException(
              "Input file contains fewer than 3 samples, "
              + inputSoundingsFile.getPath());
    }
    // A hilbert sort will allow us to get a good selection for
    // a reduced sample by skipping a fixed set of points 
    if (soundings.size() > 16) {
      HilbertSort hilbertSort = new HilbertSort();
      hilbertSort.sort(soundings);
    }
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

    lakeConstraints = new ArrayList<>();
    islandConstraints = new ArrayList<>();

    // In order for the data to be treated consistently, the constraint
    // data must undergo the same coordinate transform as the vertices
    // (if one is supplied).  Thus, this code extracts the coordinate
    // transform from the vertex loader.  In practice, it would be best
    // if all the data is already expressed in a true projected-coordinate
    // system rather than in geographic coordinates.  But we should be able
    // to obtain satisfactory results even when the input data is given in
    // geographic coordinates.
    ICoordinateTransform coordinateTransform = vertexReader.getCoordinateTransform();
    List<PolygonConstraint> listL
            = loadConstraints(inputShorelineFile, coordinateTransform);
    List<PolygonConstraint> listI
            = loadConstraints(inputIslandFile, coordinateTransform);
    transcribeConstraints(listL, listI);

    r2d = new Rectangle2D.Double(
            soundingBounds.getX(),
            soundingBounds.getY(),
            soundingBounds.getWidth(),
            soundingBounds.getHeight());

    for (IConstraint con : lakeConstraints) {
      Rectangle2D b = con.getBounds();
      r2d.add(b);
    }
    for (IConstraint con : islandConstraints) {
      Rectangle2D b = con.getBounds();
      r2d.add(b);
    }
    bounds = r2d;
    long time1 = System.nanoTime();
    timeToLoadData = time1 - time0;
  }

  /**
   * Loads the constraints from the specified file. The Tinfour ConstraintLoader
   * class will produce List<IConstraint> containing instances of different
   * classes depending on the nature of the features in the input file. However,
   * the bathymetry analysis project requires polygon constraints and it will be
   * more convenient to use PolygonConstraint instances rather than the generic
   * interface. This method casts the features from the generic IConstraint
   * interface to the specific PolygonConstraint used for this particular
   * bathymetry analysis.
   * <p>
   * If the input file contains non-polygon features (open lines), this method
   * throws an IllegalArgumentException.
   *
   * @param file a valid file represent
   * @param coordinateTransform a valid coordinate transform; or a null if the
   * coordinates from the input file are to be accepted without a transform
   * operation.
   * @return a list of polygon constraints
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  private List<PolygonConstraint> loadConstraints(
          File file,
          ICoordinateTransform coordinateTransform) throws IOException {
    List<PolygonConstraint> list = new ArrayList<>();
    if (file == null) {
      return list;
    }

    try (ConstraintReaderShapefile cReader = new ConstraintReaderShapefile(file)) {
      cReader.setCoordinateTransform(coordinateTransform);
      List<IConstraint> temp = cReader.read();
      for (IConstraint con : temp) {
        if (con instanceof PolygonConstraint) {
          list.add((PolygonConstraint) con);
        } else {
          throw new IllegalArgumentException(
                  "Constraint file contains non-polygon features "
                  + file.getName());
        }
      }
    }
    return list;
  }

  /**
   * In practice, I've seen two kinds of input files.
   * <ol>
   * <li>Two Shapefiles, one for the lake and one for the island</li>
   * <li>One Shapefile, with "holes" where islands would go</li>
   * </ol>
   * <p>
   * The Silsbe sample for Lake Victoria gave one Shapefile which defined a
   * containing polygon for the entire lake and a separate file for islands. The
   * containing Shapefile did not include "holes" for the islands.
   * Traditionally, when Shapefiles include polygon features in which one
   * polygon encloses another, the Shapefile stores a "hole" where the alternate
   * feature would go. However, the holes are mandatory only in cases where the
   * enclosed feature is part of the SAME Shapefile as the larger feature. Since
   * Silsbe treated his islands as a separate file, there was no requirement for
   * holes.
   * <p>
   * The Salisbury University Lake Victoria sample also provided two Shapefiles,
   * one for the lake (they called it the "shoreline file") and one for the
   * islands. But the lake Shapefile had hole features where the islands would
   * go. And, since the lake file was sufficient to conduct the processing,
   * there was no need to use the island features.
   * <p>
   * This logic attempts to organize different potential inputs into a form that
   * is ready for processing by the LakeVolumeExample class.
   *
   * @param listL list of constraints loaded from the lake Shapefile
   * @param listI list of constraints loaded from the island Shapefile
   */
  private void transcribeConstraints(
          List<PolygonConstraint> listL,
          List<PolygonConstraint> listI) {
    boolean lakeContainsHoles = false;
    for (PolygonConstraint p : listL) {
      if (p.getArea() < 0) {
        lakeContainsHoles = true;
        break;
      }
    }

    // if the lake contains holes, it is sufficient for processing.
    // the constraints from the polygon file will be ignored
    if (lakeContainsHoles) {
      for (PolygonConstraint p : listL) {
        double a = p.getArea();
        if (a > 0) {
          lakeConstraints.add(p);
        } else if (a < 0) {
          islandConstraints.add(reverse(p));
        }
      }
    } else {
      lakeConstraints.addAll(listL);
      // as a precaution, check the island constraints to
      // ensure that all of them are oriented clockwise.
      // it is possible that the islands may also contain
      // holes (as in the case of an island lake)
      for (PolygonConstraint p : listI) {
        double a = p.getArea();
        if (a > 0) {
          islandConstraints.add(p);
        }
      }

    }
  }

  private PolygonConstraint reverse(PolygonConstraint c) {
    List<Vertex> vList = c.getVertices();
    ArrayList<Vertex> nList = new ArrayList<>(vList.size());
    for (int i = vList.size() - 1; i >= 0; i--) {
      nList.add(vList.get(i));
    }
    return c.getConstraintWithNewGeometry(nList);
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
   * Get a list of the soundings.
   *
   * @return the soundings
   */
  public List<Vertex> getSoundings() {
    ArrayList<Vertex> result = new ArrayList<>(soundings.size());
    result.addAll(soundings);
    return result;
  }

  /**
   * Get a reduced list of the soundings.
   *
   * @return the soundings
   */
  public List<Vertex> getReducedListOfSoundings(int nTarget) {
    int n = soundings.size();
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
   * Get the shoreline constraints
   *
   * @return a valid, potentially empty, list.
   */
  public List<PolygonConstraint> getLakeConstraints() {
    return lakeConstraints;
  }

  /**
   * Get the island constraints
   *
   * @return a valid, potentially empty, list.
   */
  public List<PolygonConstraint> getIslandConstraints() {
    return islandConstraints;
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

}
