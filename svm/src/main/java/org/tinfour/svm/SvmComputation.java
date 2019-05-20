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
package org.tinfour.svm;

import java.io.BufferedOutputStream;
import java.io.File;
import org.tinfour.utils.KahanSummation;
import java.io.FileOutputStream;
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
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.interpolation.NaturalNeighborInterpolator;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.svm.SvmTriangleVolumeStore.AreaVolumeResult;
import org.tinfour.utils.TriangleCollector;

/**
 * Provide logic and elements for performing a volume computation for the
 * specified input data.
 */
public class SvmComputation {

  /**
   * A Java Consumer to collect the contribution from each water triangle in the
   * Constrained Delaunay Triangulation.
   */
  private static class LakeData implements Consumer<SimpleTriangle> {

    PolygonConstraint pCon;
    double shoreReferenceElevation;
    boolean[] water;
    final GeometricOperations geoOp;
    int nTriangles;
    int nFlatTriangles;

    KahanSummation volumeSum = new KahanSummation();
    KahanSummation areaSum = new KahanSummation();
    KahanSummation flatAreaSum = new KahanSummation();

    NaturalNeighborInterpolator nni;
    SvmTriangleVolumeStore volumeStore;

    /**
     * Constructs an instance for processing and extracts the water/land values
     * based on the integer index assigned to the constraints.
     *
     * @param tin A valid Constrained Delaunay Triangulation
     */
    LakeData(IIncrementalTin tin, double shoreReferenceElevation) {
      this.shoreReferenceElevation = shoreReferenceElevation;
      List<IConstraint> constraintsFromTin = tin.getConstraints();
      water = new boolean[constraintsFromTin.size()];
      for (IConstraint con : constraintsFromTin) {
        water[con.getConstraintIndex()] = (Boolean) con.getApplicationData();
        if ((Boolean) con.getApplicationData() && con instanceof PolygonConstraint) {
          pCon = (PolygonConstraint) con;
        }
      }
      Thresholds thresholds = tin.getThresholds();
      geoOp = new GeometricOperations(thresholds);
      nni = new NaturalNeighborInterpolator(tin);
      volumeStore = new SvmTriangleVolumeStore(thresholds);
    }

    private boolean nEqual(double a, double b) {
      return Math.abs(a - b) < 1.0e-5;
    }

    @Override
    public void accept(SimpleTriangle t) {

      IConstraint constraint = t.getContainingRegion();
      if (constraint instanceof PolygonConstraint) {
        Boolean appData = (Boolean) constraint.getApplicationData();
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

          if (nEqual(zA, shoreReferenceElevation)
                  && nEqual(zB, shoreReferenceElevation)
                  && nEqual(zC, shoreReferenceElevation)) {
            nFlatTriangles++;
            flatAreaSum.add(area);
          }

          nTriangles++;
          double vtest = (shoreReferenceElevation - zMean) * area;

          volumeStore.addTriangle(vA, vB, vC, vtest);

          volumeSum.add(vtest);
          areaSum.add(area);

        }
      }
    }

    boolean hasDepth(IQuadEdge edge) {
      IQuadEdge e = edge.getReverseFromDual();
      Vertex v = e.getA();
      return v != null && v.getZ() < shoreReferenceElevation - 0.1;
    }

    boolean isWater(IQuadEdge edge) {
      if (edge.isConstrainedRegionBorder()) {
        return false;
      }
      if (edge.isConstrainedRegionInterior()) {
        int index = edge.getConstraintIndex();
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

    double getFlatAreaSum() {
      return flatAreaSum.getSum();
    }
  }

  /**
   * Performs the main process, printing the results to the specified print
   * stream.
   *
   * @param ps a valid print stream instance for reporting output.
   * @param properties a valid set of properties specification for processing
   * options.
   * @param data a valid instance giving data for processing
   * @throws java.io.IOException in the event of an unrecoverable I/O exception.
   */
  public void processVolume(
          PrintStream ps,
          SvmProperties properties,
          SvmBathymetryData data) throws IOException {

    String lengthUnits = properties.getUnitOfDistance().getLabel();
    double lengthFactor = properties.getUnitOfDistance().getScaleFactor();
    String areaUnits = properties.getUnitOfArea().getLabel();
    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    String volumeUnits = properties.getUnitOfVolume().getLabel();
    double volumeFactor = properties.getUnitOfVolume().getScaleFactor();

    double shoreReferenceElevation = data.getShoreReferenceElevation();
    List<Vertex> soundings = data.getSoundingsAndSupplements();
    List<PolygonConstraint> boundaryConstraints = data.getBoundaryConstraints();

    List<IConstraint> allConstraints = new ArrayList<>();
    allConstraints.addAll(boundaryConstraints);

    long time0 = System.nanoTime();
    IIncrementalTin tin;
    if (soundings.size() < 500000) {
      tin = new IncrementalTin(1.0);
    } else {
      tin = new SemiVirtualIncrementalTin(1.0);
    }
    // ps.println("TIN class: " + (tin.getClass().getName()));
    tin.add(soundings, null);
    tin.addConstraints(allConstraints, true);
    long timeToBuildTin = System.nanoTime() - time0;

    long timeToFixFlats = 0;
    if (properties.isFlatFixerEnabled()) {
      // During the flat-fixer loop, the total count of triangles
      // may bounce around a bit.  In the case of coves and similar
      // features, fixing one layer of flats may expose yet more
      // flats to be fixed. Also, not that the counts/areas are
      // not the total count/area of flat triangles, but the total
      // of those triangles that were subject to remediation.  Counting
      // the actual flat area would add too much processing for too
      // little added value.
      long timeF0 = System.nanoTime();
      int nRemediationVertices = 0;
      ps.println("Remediating flat triangles");
      ps.println("Pass   Remediated     Area        Volume     avg. depth");
      for (int iFlat = 0; iFlat < 10; iFlat++) {
        // construct a new flat-fixer each time
        // so we can gather counts
        SvmFlatFixer flatFixer = new SvmFlatFixer(tin);
        List<Vertex> fixList = flatFixer.fixFlats(ps, properties, data);
        if (fixList.isEmpty()) {
          break;
        }
        double fixArea = flatFixer.getRemediatedArea();
        double fixVolume = flatFixer.getRemediatedVolume();
        ps.format("  %2d  %8d  %12.3f  %12.3f  %7.3f%n",
                iFlat,
                flatFixer.getRemediationCount(),
                fixArea / areaFactor,
                fixVolume / volumeFactor,
                (fixVolume / fixArea) / lengthFactor
        );
        nRemediationVertices += fixList.size();
        soundings.addAll(fixList);
        if (soundings.size() < 500000) {
          tin = new IncrementalTin(1.0);
        } else {
          tin = new SemiVirtualIncrementalTin(1.0);
        }
        tin.add(soundings, null);
        tin.addConstraints(allConstraints, true);
      }
      long timeF1 = System.nanoTime();
      timeToFixFlats = timeF1 - timeF0;
      ps.println("N remediation vertices added " + nRemediationVertices);
    }

    ps.println("Processing data from Delaunay Triangulation");
    long time1 = System.nanoTime();
    LakeData lakeConsumer = new LakeData(tin, shoreReferenceElevation);
    TriangleCollector.visitSimpleTriangles(tin, lakeConsumer);
    SvmTriangleVolumeStore vStore = lakeConsumer.volumeStore;

    double tableInterval = properties.getTableInterval();
    double zShore = data.shoreReferenceElevation;
    double zMin = data.getMinZ();
    int nStep = (int) (Math.ceil(zShore - zMin) / tableInterval) + 1;
    if (nStep > 10000) {
      nStep = 10000;
    }
    List<AreaVolumeResult> resultList = new ArrayList<>(nStep);
    for (int i = 0; i < nStep; i++) {
      double zTest = zShore - i * tableInterval;
      AreaVolumeResult result = vStore.compute(zTest);
      resultList.add(result);
      if (result.volume == 0) {
        break;
      }
    }

    long time2 = System.nanoTime();

    List<PolygonConstraint> lakeConstraints = data.getLakeConstraints();
    List<PolygonConstraint> islandConstraints = data.getIslandConstraints();

    double lakeArea = getAreaSum(lakeConstraints) / areaFactor;
    double islandArea = Math.abs(getAreaSum(islandConstraints) / areaFactor);
    double lakePerimeter = getPerimeterSum(lakeConstraints) / lengthFactor;
    double islandPerimeter = getPerimeterSum(islandConstraints) / lengthFactor;
    double netArea = lakeArea - islandArea;
    double totalShore = lakePerimeter + islandPerimeter;
    ps.format("%nData from Shapefiles%n");
    ps.format("  Lake area        %10.8e %,20.0f %s%n", lakeArea, lakeArea, areaUnits);
    ps.format("  Island area      %10.8e %,20.0f %s%n", islandArea, islandArea, areaUnits);
    ps.format("  Net area (water) %10.8e %,20.0f %s%n", netArea, netArea, areaUnits);
    ps.format("  Lake shoreline   %10.8e %,20.0f %s%n", lakePerimeter, lakePerimeter, lengthUnits);
    ps.format("  Island shoreline %10.8e %,20.0f %s%n", islandPerimeter, islandPerimeter, lengthUnits);
    ps.format("  Total shoreline  %10.8e %,20.0f %s%n", totalShore, totalShore, lengthUnits);
    ps.format("  N Islands        %d%n", islandConstraints.size());

    double volume = lakeConsumer.getVolume() / volumeFactor;
    double surfArea = lakeConsumer.getSurfaceArea() / areaFactor;
    double avgDepth = volume / surfArea;
    double sampleSpacing = estimateSampleSpacing(tin, lakeConsumer);
    double flatArea = lakeConsumer.getFlatAreaSum() / areaFactor;
    ps.format("%nComputations from Constrained Delaunay Triangulation%n");
    ps.format("  Volume            %10.8e %,20.0f %s%n", volume, volume, volumeUnits);
    ps.format("  Surface Area      %10.8e %,20.0f %s%n", surfArea, surfArea, areaUnits);
    ps.format("  Flat Area         %10.8e %,20.0f %s%n", flatArea, flatArea, areaUnits);
    ps.format("  Avg depth         %5.2f ft%n", avgDepth);
    ps.format("  N Triangles       %d%n", lakeConsumer.nTriangles);
    ps.format("  N Flat Triangles  %d%n", lakeConsumer.nFlatTriangles);
    ps.format("  Sample Spacing    %8.2f%n", sampleSpacing);

    ps.format("%n%n%n");
    ps.format("Time to load data              %7.1f ms%n", data.getTimeToLoadData() / 1.0e+6);
    ps.format("Time to build TIN              %7.1f ms%n", timeToBuildTin / 1.0e+6);
    ps.format("Time to remedy flat triangles: %7.1f ms%n", timeToFixFlats / 1.0e+6);
    ps.format("Time to compute lake volume    %7.1f ms%n", (time2 - time1) / 1.0e+6);
    ps.format("Time for all analysis          %7.1f ms%n", (time2 - time0) / 1.0e+6);
    ps.format("Time for all operations        %7.1f ms%n",
            (data.getTimeToLoadData() + time2 - time0) / 1.0e+6);

    ps.format("%n%nVolume Store Triangle Count: %d%n", vStore.getTriangleCount());

    PrintStream ts = ps;
    File tableFile = properties.getTableFile();
    FileOutputStream tableOutputStream = null;
    if (tableFile != null) {
      tableOutputStream = new FileOutputStream(tableFile);
      BufferedOutputStream bos = new BufferedOutputStream(tableOutputStream);
      ts = new PrintStream(bos);
    }

    ts.println("Elevation, Area, Volume");
    for (AreaVolumeResult result : resultList) {
      ts.format("%12.3f, %12.3f, %12.3f%n",
              result.level,
              result.area / areaFactor,
              result.volume / volumeFactor);
    }

    ts.flush();
    if (tableOutputStream != null) {
      tableOutputStream.close();
    }

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

//  private void processGrid(PrintStream ps,
//          IIncrementalTin tin,
//          double cellSize,
//          File outputFolder ) {
//    Rectangle2D r2d = tin.getBounds();
//    double xMin = r2d.getMinX();
//    double xMax = r2d.getMaxX();
//    double yMin = r2d.getMinY();
//    double yMax = r2d.getMaxY();
//    GridSpecification grid = new GridSpecification(
//            GridSpecification.CellPosition.CenterOfCell,
//            cellSize,
//            xMin, xMax, yMin, yMax);
//
//    int nCells = grid.getCellCount();
//    int nRows = grid.getRowCount();
//    int nCols = grid.getColumnCount();
//    ps.println("\nProcessing grid, size " + nRows + " rows, " + nCols + " columns\n");
//
//    INeighborEdgeLocator locator = tin.getNeighborEdgeLocator();
//    NaturalNeighborInterpolator interpolator = new NaturalNeighborInterpolator(tin);
//
//    long time0 = System.nanoTime();
//    KahanSummation vSum = new KahanSummation();
//    float results[][] = new float[nRows][nCols];
//    double s2 = cellSize * cellSize;
//    Point2D p2d = new Point2D.Double();
//    for (int iRow = 0; iRow < nRows; iRow++) {
//      Arrays.fill(results[iRow], Float.NaN);
//      for (int iCol = 0; iCol < nCols; iCol++) {
//        grid.mapRowColumnToXy(iRow, iCol, p2d);
//        double x = p2d.getX();
//        double y = p2d.getY();
//        IQuadEdge a = locator.getNeigborEdge(x, y);
//        IConstraint constraint = getContainingRegion(tin, a);
//        if (constraint != null) {
//          Object appData = constraint.getApplicationData();
//          if (appData instanceof Boolean && (Boolean) appData) {
//            // the point is inside a water constraint
//            double zInterp = interpolator.interpolate(x, y, null);
//            if (!Double.isNaN(zInterp)) {
//              results[iRow][iCol] = (float) zInterp;
//              vSum.add(zInterp * s2);
//            }
//          }
//        }
//      } // iCol
//    } // iRow
//    long time1 = System.nanoTime();
//    double timeToProcess = (time1 - time0) / 1000000.0;
//    int nValues = vSum.getSummandCount();
//    double volume = vSum.getSum();
//    double area = nValues * s2;
//    double meanDepth = volume / area;
//    ps.format("N Cells:               %10d%n", nCells);
//    ps.format("N Depth Values:        %10d%n", nValues);
//    ps.format("Area:        %,20.0f m2%n", area);
//    ps.format("Volume:      %,20.0f m3%n", volume);
//    ps.format("Mean Depth:             %9.3f m%n", meanDepth);
//    ps.format("Time to process grid:   %9.3f ms%n", timeToProcess);
//    ps.format("%n");
//
//    if (outputFolder != null) {
//      String name = String.format("Grid_%04dx%04d.asc", nCols, nRows);
//      File file = new File(outputFolder, name);
//      ps.format("Writing output to %s%n", file.getPath());
//      try {
//        grid.writeAsciiFile(file, results, "%4.2f", "-99");
//      } catch (IOException ioex) {
//        ps.println("Write operation failed " + ioex.getMessage());
//      }
// 
//    }
//  }
}
