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

import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import org.tinfour.utils.KahanSummation;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
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

    double shoreReferenceElevation;
    boolean[] water;
    final GeometricOperations geoOp;
    int nTriangles;
    int nFlatTriangles;

    KahanSummation volumeSum = new KahanSummation();
    KahanSummation areaSum = new KahanSummation();
    KahanSummation flatAreaSum = new KahanSummation();

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
      }
      Thresholds thresholds = tin.getThresholds();
      geoOp = new GeometricOperations(thresholds);
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

    double getFlatArea() {
      return flatAreaSum.getSum();
    }
  }

  
  
  /**
   * A Java Consumer to collect the contribution from each water triangle in the
   * Constrained Delaunay Triangulation.
   */
  private static class TriangleSurvey implements Consumer<SimpleTriangle> {

    double shoreReferenceElevation;
    boolean[] water;

    int nTriangles;
    int nFlatTriangles;
    KahanSummation areaSum = new KahanSummation();
    KahanSummation flatAreaSum = new KahanSummation();
        final GeometricOperations geoOp;

 
    /**
     * Constructs an instance for processing and extracts the water/land values
     * based on the integer index assigned to the constraints.
     *
     * @param tin A valid Constrained Delaunay Triangulation
     */
    TriangleSurvey(IIncrementalTin tin, double shoreReferenceElevation) {
      this.shoreReferenceElevation = shoreReferenceElevation;
      List<IConstraint> constraintsFromTin = tin.getConstraints();
      water = new boolean[constraintsFromTin.size()];
      for (IConstraint con : constraintsFromTin) {
        water[con.getConstraintIndex()] = (Boolean) con.getApplicationData();
      }
         Thresholds thresholds = tin.getThresholds();
      geoOp = new GeometricOperations(thresholds);
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
 
          double area = geoOp.area(vA, vB, vC);

          if (nEqual(zA, shoreReferenceElevation)
                  && nEqual(zB, shoreReferenceElevation)
                  && nEqual(zC, shoreReferenceElevation)) {
            nFlatTriangles++;
            flatAreaSum.add(area);
          }

          nTriangles++;
          areaSum.add(area);
        }
      }
    }

  
    double getSurfaceArea() {
      return areaSum.getSum();
    }

    double getFlatArea() {
      return flatAreaSum.getSum();
    }
    
    int getFlatTriangleCount(){
        return nFlatTriangles;
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
   * @return the instance of the final TIN object created for the processing.
   * @throws java.io.IOException in the event of an unrecoverable I/O exception.
   */
  public IIncrementalTin processVolume(
          PrintStream ps,
          SvmProperties properties,
          SvmBathymetryData data) throws IOException {

    String lengthUnits = properties.getUnitOfDistance().getLabel();
    double lengthFactor = properties.getUnitOfDistance().getScaleFactor();
    String areaUnits = properties.getUnitOfArea().getLabel();
    double areaFactor = properties.getUnitOfArea().getScaleFactor();
    String volumeUnits = properties.getUnitOfVolume().getLabel();
    double volumeFactor = properties.getUnitOfVolume().getScaleFactor();

    // shoreline reference elevation should come from the properties, but
    // if it is not provided, use the value extractd from the data.
    double shoreReferenceElevation
            = properties.getShorelineReferenceElevation();
    if (Double.isNaN(shoreReferenceElevation)) {
      shoreReferenceElevation = data.getShoreReferenceElevation();
    }

    List<Vertex> soundings = data.getSoundingsAndSupplements();
    List<PolygonConstraint> boundaryConstraints = data.getBoundaryConstraints();

    List<IConstraint> allConstraints = new ArrayList<>();
    allConstraints.addAll(boundaryConstraints);
 
    if (soundings.isEmpty()) {
      ps.print("Unable to proceed, no soundings are available");
      ps.flush();
      throw new IOException("No soungings availble");
    }

    if (boundaryConstraints.isEmpty()) {
      ps.print("Unable to proceed, no boundary constraints are available");
      ps.flush();
      throw new IOException("No boundary constraints availble");
    }
    
    IIncrementalTin tin;

    long time0 = System.nanoTime();

    if (soundings.size() < 500000) {
      tin = new IncrementalTin(1.0);
    } else {
      tin = new SemiVirtualIncrementalTin(1.0);
    }
    // ps.println("TIN class: " + (tin.getClass().getName()));
    tin.add(soundings, null);
    tin.addConstraints(allConstraints, true);
    long timeToBuildTin = System.nanoTime() - time0;

    TriangleSurvey  trigSurvey = new TriangleSurvey(tin, shoreReferenceElevation);
    TriangleCollector.visitSimpleTriangles(tin, trigSurvey);

    
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
      ps.println("");
      ps.println("Remediating flat triangles");
      ps.println("Pass   Remediated     Area     Volume Added  avg. depth");
      for (int iFlat = 0; iFlat < 500; iFlat++) {
        // construct a new flat-fixer each time
        // so we can gather counts
        SvmFlatFixer flatFixer = new SvmFlatFixer(
                tin,
                shoreReferenceElevation);
        List<Vertex> fixList = flatFixer.fixFlats(ps);
        if (fixList.isEmpty()) {
          break;
        }
        if (iFlat % 10 == 0) {
          double fixArea = flatFixer.getRemediatedArea();
          double fixVolume = flatFixer.getRemediatedVolume();
          ps.format("%4d  %8d  %12.3f  %12.3f  %7.3f%n",
                  iFlat,
                  flatFixer.getRemediationCount(),
                  fixArea / areaFactor,
                  fixVolume / volumeFactor,
                  (fixVolume / fixArea) / lengthFactor
          );
        }
        nRemediationVertices += fixList.size();
        soundings.addAll(fixList);

      }
      long timeF1 = System.nanoTime();
      timeToFixFlats = timeF1 - timeF0;
      ps.println("N remediation vertices added " + nRemediationVertices);
    }
    ps.println("");
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
    Rectangle2D bounds = data.getBounds();
    
    ps.format("%nData from Shapefiles%n");
    ps.format("  Lake area        %10.8e %,20.0f %s%n", lakeArea, lakeArea, areaUnits);
    ps.format("  Island area      %10.8e %,20.0f %s%n", islandArea, islandArea, areaUnits);
    ps.format("  Net area (water) %10.8e %,20.0f %s%n", netArea, netArea, areaUnits);
    ps.format("  Lake shoreline   %10.8e %,20.0f %s%n", lakePerimeter, lakePerimeter, lengthUnits);
    ps.format("  Island shoreline %10.8e %,20.0f %s%n", islandPerimeter, islandPerimeter, lengthUnits);
    ps.format("  Total shoreline  %10.8e %,20.0f %s%n", totalShore, totalShore, lengthUnits);
    ps.format("  N Islands        %d%n", islandConstraints.size());
    ps.format("  N Soundings      %d%n", data.getSoundings().size());
    ps.format("  N Supplements    %d%n", data.getSupplements().size());
    ps.format("  Bounds%n");
    ps.format("     x:    %12.3f, %12.3f, (%5.3f)%n",
            bounds.getMinX() / lengthFactor,
            bounds.getMaxX() / lengthFactor,
            bounds.getWidth() / lengthFactor);
    ps.format("     y:    %12.3f, %12.3f, (%5.3f)%n",
            bounds.getMinY() / lengthFactor,
            bounds.getMaxY() / lengthFactor,
            bounds.getHeight() / lengthFactor);
    ps.format("     z:    %12.3f, %12.3f, (%5.3f)%n",
            data.getMinZ() / lengthFactor,
            data.getMaxZ() / lengthFactor,
            (data.getMaxZ() - data.getMinZ()) / lengthFactor);

    if (properties.isSoundingSpacingEnabled()) {
      List<Vertex> originalSoundings = data.getSoundings();
      double[] lenArray = new double[originalSoundings.size()];
      double sumLen = 0;
      int nLen = 0;
      Vertex prior = null;
      for (Vertex v : originalSoundings) {
        if (prior != null) {
          double len = v.getDistance(prior);
          sumLen += len;
          lenArray[nLen++] = len;
        }
        prior = v;
      }

      double meanLen = sumLen / nLen;
      double medianLen = lenArray[nLen / 2];
      Arrays.sort(lenArray, 0, nLen);
      ps.format("  Mean sounding spacing:   %12.3f %s%n",
              meanLen / lengthFactor, lengthUnits);
      ps.format("  Median sounding spacing: %12.3f %s%n",
              medianLen, lengthUnits);
      ps.format("%n");
    }

    double totalVolume = lakeConsumer.getVolume();
    double volume = lakeConsumer.getVolume() / volumeFactor;
    double surfArea = lakeConsumer.getSurfaceArea() / areaFactor;
    double avgDepth = volume / surfArea;
    double vertexSpacing = estimateInteriorVertexSpacing(tin, lakeConsumer);
    double flatArea = lakeConsumer.getFlatArea() / areaFactor;

    ps.format("%nComputations from Constrained Delaunay Triangulation%n");
    ps.format("  Volume              %10.8e %,20.0f %s%n", volume, volume, volumeUnits);
    ps.format("  Surface Area        %10.8e %,20.0f %s%n", surfArea, surfArea, areaUnits);
    ps.format("  Flat Area           %10.8e %,20.0f %s%n", flatArea, flatArea, areaUnits);
    ps.format("  Avg depth           %5.2f %s%n", avgDepth, lengthUnits);
    ps.format("  N Triangles         %d%n", lakeConsumer.nTriangles);
    ps.format("  N Flat Triangles    %d%n", lakeConsumer.nFlatTriangles);
    ps.format("  Mean Vertex Spacing %8.2f%n", vertexSpacing);
    
    if (properties.isFlatFixerEnabled()) {
          int originalTrigCount = trigSurvey.nTriangles;
          int originalFlatCount = trigSurvey.getFlatTriangleCount();
          double originalFlatArea = trigSurvey.getFlatArea() / areaFactor;
          ps.format("%nPre-Remediation statistics%n");
          ps.format("  Original Flat Area  %10.8e %,20.0f %s%n", 
                  originalFlatArea, originalFlatArea, areaUnits);
          ps.format("  Original N Triangle %d%n", originalTrigCount);
          ps.format("  Original N Flat     %d%n", originalFlatCount);
    }

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

    ts.println("Elevation, Area, Volume, Percent_Capacity");
    for (AreaVolumeResult result : resultList) {
      ts.format("%12.3f, %12.3f, %12.3f, %6.2f%n",
              result.level,
              result.area / areaFactor,
              result.volume / volumeFactor,
              100*result.volume/totalVolume);
    }

    ts.flush();
    if (tableOutputStream != null) {
      tableOutputStream.close();
    }

    File gridFile = properties.getGridFile();
    double s = properties.getGridCellSize();
    if (gridFile != null && !Double.isNaN(s)) {
      SvmRaster grid = new SvmRaster();
      grid.buildAndWriteRaster(properties, ps, tin, lakeConsumer.water, shoreReferenceElevation);
    }
    
    SvmCapacityGraph capacityGraph = new SvmCapacityGraph(
            properties,
            resultList,
            shoreReferenceElevation,
            totalVolume);
    boolean wroteGraph = capacityGraph.writeOutput();
    if(wroteGraph){
      ps.println("Capacity graph written to "+properties.getCapacityGraphFile());
    }
    
    return tin;
    // testGrid(ps, tin, lakeConsumer.water, 2.0, areaFactor, shoreReferenceElevation);
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

  /**
   * Estimates vertex spacing for TIN based exclusively on interior edges.
   * Constraint edges and perimeter edges are not included.
   *
   * @param tin a valid TIN.
   * @param lakeData a valid instance of input data
   * @return an estimated vertex spacing
   */
  private double estimateInteriorVertexSpacing(IIncrementalTin tin, LakeData lakeData) {
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
