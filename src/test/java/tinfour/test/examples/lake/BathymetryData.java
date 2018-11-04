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
package tinfour.test.examples.lake;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.IConstraint;
import tinfour.common.Vertex;
import tinfour.test.utils.VertexLoader;
import tinfour.test.utils.cdt.ConstraintLoader;
import tinfour.utils.HilbertSort;

/**
 * A class for loading bathymetry and shoreline data to be used for estimating
 * lake volume.
 */
public class BathymetryData {

  private final double zMin;
  private final double zMax;
  private final double zMean;

  private final List<Vertex> soundings;
  private final List<IConstraint> lakeConstraints;
  private final List<IConstraint> islandConstraints;
  private final Rectangle2D soundingBounds;
  private final Rectangle2D bounds;
  private final double nominalPointSpacing;

  /**
   * Load the test data from the specified files
   *
   * @param inputSoundingsFile a mandatory path for a CSV file giving soundings
   * @param inputShorelineFile a mandatory path for a Shapefile file giving
   * soundings
   * @param inputIslandFile an optional path for a Shapefile file giving
   * soundings
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  BathymetryData(File inputSoundingsFile,
          File inputShorelineFile,
          File inputIslandFile)
          throws IOException {
    VertexLoader vLoader = new VertexLoader();
    soundings = vLoader.readDelimitedFile(inputSoundingsFile, ',');
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

    Vertex v0 = soundings.get(0);
    Rectangle2D r2d = new Rectangle2D.Double(v0.getX(), v0.getY(), 0, 0);
    for (Vertex v : soundings) {
      r2d.add(v.getX(), v.getY());
      double z = v.getZ();
      if (z > z1) {
        z1 = z;
      }
      if (z < z0) {
        z0 = z;
      }
      zSum += z;

    }

    zMin = z0;
    zMax = z1;
    zMean = zSum / soundings.size();
    soundingBounds = r2d;
    double area = soundingBounds.getWidth() * soundingBounds.getHeight();
    if (area == 0) {
      throw new IllegalArgumentException(
              "Degenerate set of input samples, "
              + inputSoundingsFile.getPath());
    }
    int n = soundings.size();
    nominalPointSpacing = Math.sqrt((area / n) * (2 / 0.866));

    ConstraintLoader cLoader = new ConstraintLoader();
    if (vLoader.isSourceInGeographicCoordinates()) {
      cLoader.setGeographic(
              vLoader.getGeoScaleX(),
              vLoader.getGeoScaleY(),
              vLoader.getGeoOffsetX(),
              vLoader.getGeoOffsetY());
    }
    if (inputShorelineFile == null) {
      lakeConstraints = new ArrayList<>(0); // empty list
    } else {
      lakeConstraints = cLoader.readConstraintsFile(inputShorelineFile);
    }
    if (inputIslandFile == null) {
      islandConstraints = new ArrayList<>(0); // empty list
    } else {
      islandConstraints = cLoader.readConstraintsFile(inputIslandFile);
    }

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
  public List<IConstraint> getLakeConstraints() {
    return lakeConstraints;
  }

  /**
   * Get the island constraints
   *
   * @return a valid, potentially empty, list.
   */
  public List<IConstraint> getIslandConstraints() {
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

  public void printSummary(PrintStream ps) {
    double x0 = soundingBounds.getMinX();
    double y0 = soundingBounds.getMinY();
    double x1 = soundingBounds.getMaxX();
    double y1 = soundingBounds.getMaxY();
    ps.format("Input Data%n");
    ps.format("  Soundings%n");
    ps.format("     Count:           %7d%n", soundings.size());
    ps.format("     Min (x,y,z):     %9.1f, %9.1f, %9.2f%n", x0, y0, zMin);
    ps.format("     Max (x,y,z):     %9.1f, %9.1f, %9.2f%n", x1, y1, zMax);
    ps.format("     width,height:    %9.1f, %9.1f%n", x1 - x0, y1 - y0);
    ps.format("     Nominal Spacing: %9.1f%n", nominalPointSpacing);
  }
}
