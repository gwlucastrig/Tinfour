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

import org.tinfour.utils.KahanSummation;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.TriangleCollector;

/**
 *
 */
public class LakeVolumeExample {

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    File folder;
    File inputSoundingsFile;
    File inputLakeFile;
    File inputIslandFile;
    String dbfFieldForDepth;

    boolean useSilsbeSample = false;
    String path = ".";
    for (int i = 0; i < args.length; i++) {
      if ("-Silsbe".equalsIgnoreCase(args[i])) {
        useSilsbeSample = true;
      }
      if ("-input".equalsIgnoreCase(args[i])) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("-input option requires a folder path");
        }
        path = args[i + 1];
      }
    }
    // set up for processing.  In this case, the default is to
    // use the Silsbe sample, but if an argument is passed into the
    // main, it uses the Salisbury University sample
    if (useSilsbeSample) {
      if (path == null) {
        folder = new File("LakeVictoriaSilsbe");
      } else {
        folder = new File(path);
      }
      inputSoundingsFile = new File(folder, "finbath.shp");
      inputLakeFile = new File(folder, "LAKE.SHP");
      inputIslandFile = new File(folder, "ISLANDS.SHP");
      dbfFieldForDepth = "DEPTH";
    } else {
      if (path == null) {
        folder = new File("LakeVictoriaSalisburyUniversity");
      } else {
        folder = new File(path);
      }
      inputSoundingsFile = new File(folder, "LV_Bathymetry_Points_V7.shp");
      inputLakeFile = new File(folder, "LakeVictoriaShoreline.shp");
      inputIslandFile = null;
      dbfFieldForDepth = "Z";
    }

    try {
      BathymetryData bathyData = new BathymetryData(
              inputSoundingsFile,
              inputLakeFile,
              inputIslandFile,
              dbfFieldForDepth);
      bathyData.printSummary(System.out);
      LakeVolumeExample lvCalc = new LakeVolumeExample();
      lvCalc.processVolume(System.out, bathyData);
    } catch (IOException ioex) {
      System.out.println(
              "Exception processing lake volume files, "
              + ioex.getMessage());
      ioex.printStackTrace(System.out);
    }
  }

  /**
   * A Java Consumer to collect the contribution from each water triangle in the
   * Constrained Delaunay Triangulation.
   */
  private static class LakeData implements Consumer<SimpleTriangle> {

    boolean[] water;
    final GeometricOperations geoOp;
    int nTriangles;

    KahanSummation volumeSum = new KahanSummation();
    KahanSummation areaSum = new KahanSummation();

    /**
     * Constructs an instance for processing and extracts the water/land values
     * based on the integer index assigned to the constraints.
     *
     * @param tin A valid Constrained Delaunay Triangulation
     */
    LakeData(IIncrementalTin tin) {
      List<IConstraint> constraintsFromTin = tin.getConstraints();
      water = new boolean[constraintsFromTin.size()];
      for (IConstraint con : constraintsFromTin) {
        water[con.getConstraintIndex()] = (Boolean) con.getApplicationData();
      }
      Thresholds thresholds = tin.getThresholds();
      geoOp = new GeometricOperations(thresholds);
    }

    @Override
    public void accept(SimpleTriangle t) {


      IConstraint constraint = t.getContainingRegion();
      if (constraint instanceof PolygonConstraint) {
        Boolean appData = (Boolean)constraint.getApplicationData();
        if (appData) {
          IQuadEdge a = t.getEdgeA();
          IQuadEdge b = t.getEdgeB();
          IQuadEdge c = t.getEdgeC();
          Vertex vA = a.getA();
          Vertex vB = b.getA();
          Vertex vC = c.getA();
          double zA = vA.getZ();
          double zB = vB.getZ();
          double zC = vC.getZ();
          double zMean = (zA + zB + zC) / 3;
          double area = geoOp.area(vA, vB, vC);

          nTriangles++;
          volumeSum.add(zMean * area);
          areaSum.add(area);

        }
      }
    }

    boolean isWater(IQuadEdge edge) {
      if (edge.isConstraintRegionBorder()) {
        return false;
      }
      if (edge.isConstraintRegionInterior()) {
        int index = edge.getConstraintRegionInteriorIndex();
        return water[index];
      }
      return false;
    }

    double getVolume() {
      return Math.abs(volumeSum.getSum());
    }

    double getSurfaceArea() {
      return areaSum.getSum();
    }
  }

  /**
   * Performs the main process, printing the results to the specified print
   * stream.
   *
   * @param ps a valid print stream instance for reporting output
   * @param data a valid data instance for processing
   */
  public void processVolume(PrintStream ps, BathymetryData data) {
    List<Vertex> soundings = data.getSoundings();
    List<PolygonConstraint> lakeConstraints = data.getLakeConstraints();
    List<PolygonConstraint> islandConstraints = data.getIslandConstraints();

    for (PolygonConstraint con : lakeConstraints) {
      con.setApplicationData(true);
    }
    for (PolygonConstraint con : islandConstraints) {
      con.setApplicationData(false);
    }

    List<IConstraint> allConstraints = new ArrayList<>();
    allConstraints.addAll(lakeConstraints);
    allConstraints.addAll(islandConstraints);

    long time0 = System.nanoTime();
    IIncrementalTin tin;
    if (soundings.size() < 500000) {
      tin = new IncrementalTin(1.0);
    } else {
      tin = new SemiVirtualIncrementalTin(1.0);
    }
    tin.add(soundings, null);
    tin.addConstraints(allConstraints, true);
    long time1 = System.nanoTime();

    LakeData results = new LakeData(tin);
    TriangleCollector.visitSimpleTriangles(tin, results);
    long time2 = System.nanoTime();

    double lakeArea = getAreaSum(lakeConstraints);
    double islandArea = getAreaSum(islandConstraints);
    double lakePerimeter = getPerimeterSum(lakeConstraints);
    double islandPerimeter = getPerimeterSum(islandConstraints);
    double netArea = lakeArea - islandArea;
    double totalShore = lakePerimeter + islandPerimeter;
    ps.format("%nData from Shapefiles%n");
    ps.format("  Lake area        %10.8e %,20.0f m2 %9.1f km2%n", lakeArea, lakeArea, lakeArea / 1.0e+6);
    ps.format("  Island area      %10.8e %,20.0f m2 %9.1f km2%n", islandArea, islandArea, islandArea / 1.0e+6);
    ps.format("  Net area (water) %10.8e %,20.0f m2 %9.1f km2%n", netArea, netArea, netArea / 1.0e+6);
    ps.format("  Lake shoreline   %10.8e %,20.0f m  %9.1f km%n", lakePerimeter, lakePerimeter, lakePerimeter / 1000);
    ps.format("  Island shoreline %10.8e %,20.0f m  %9.1f km%n", islandPerimeter, islandPerimeter, islandPerimeter / 1000);
    ps.format("  Total shoreline  %10.8e %,20.0f m  %9.1f km%n", totalShore, totalShore, totalShore / 1000);
    ps.format("  N Islands        %d%n", islandConstraints.size());

    double volume = results.getVolume();
    double surfArea = results.getSurfaceArea();
    double avgDepth = volume / surfArea;
    double sampleSpacing = estimateSampleSpacing(tin, results);
    ps.format("%nComputations from Constrained Delaunay Triangulation%n");
    ps.format("  Volume           %10.8e %,20.0f m3 %9.1f km3%n", volume, volume, volume / 1.0e+9);
    ps.format("  Surface Area     %10.8e %,20.0f m2 %9.1f km2%n", surfArea, surfArea, surfArea / 1.0e+6);
    ps.format("  Avg depth       %5.2f m%n", avgDepth);
    ps.format("  N Triangles     %d%n", results.nTriangles);
    ps.format("  Sample Spacing %8.2f m%n", sampleSpacing);

    ps.format("%n%n%n");
    ps.format("Time to load data           %7.1f ms%n", data.getTimeToLoadData() / 1.0e+6);
    ps.format("Time to build TIN           %7.1f ms%n", (time1 - time0) / 1.0e+6);
    ps.format("Time to compute lake volume %7.1f ms%n", (time2 - time1) / 1.0e+6);
    ps.format("Time for all analysis       %7.1f ms%n", (time2 - time0) / 1.0e+6);
    ps.format("Time for all operations     %7.1f ms%n",
            (data.getTimeToLoadData() + time2 - time0) / 1.0e+6);
  }

  private double getAreaSum(List<PolygonConstraint> constraints) {
    KahanSummation areaSum = new KahanSummation();
    for (PolygonConstraint con : constraints) {
      areaSum.add(con.getArea());
    }
    return areaSum.getSum();
  }

  private double getPerimeterSum(List<PolygonConstraint> constraints) {
    KahanSummation perimeterSum = new KahanSummation();
    for (PolygonConstraint con : constraints) {
      perimeterSum.add(con.getLength());
    }
    return perimeterSum.getSum();
  }

  private double estimateSampleSpacing(IIncrementalTin tin, LakeData lakeData) {
    KahanSummation sumLength = new KahanSummation();
    int n = 0;
    for (IQuadEdge e : tin.edges()) {
      if (lakeData.isWater(e)) {
        // the edge lies in a water area, but we also need
        // to exclude any edges that connect a sounding to
        // a constraint border.
        Vertex a = e.getA();
        Vertex b = e.getB();
        if (!a.isConstraintMember() && !b.isConstraintMember()) {
          n++;
          sumLength.add(e.getLength());
        }
      }
    }
    return sumLength.getSum() / n;
  }

}
