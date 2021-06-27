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
import org.tinfour.utils.HilbertSort;
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
    KahanSummation depthAreaSum = new KahanSummation();
    KahanSummation depthAreaWeightedSum = new KahanSummation();

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
          }else if(zA<shoreReferenceElevation
            || zB<shoreReferenceElevation
            || zC<shoreReferenceElevation){
            depthAreaSum.add(area);
            depthAreaWeightedSum.add(area*(shoreReferenceElevation-(zA+zB+zC)/3.0));
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

    double getAdjustedMeanDepth(){
      return depthAreaWeightedSum.getSum()/this.depthAreaSum.getSum();
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
    KahanSummation depthAreaSum = new KahanSummation();
    KahanSummation depthAreaWeightedSum = new KahanSummation();

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
          }else if(zA<shoreReferenceElevation || zB<shoreReferenceElevation || zC<shoreReferenceElevation){
            depthAreaSum.add(area);
            depthAreaWeightedSum.add(area*(shoreReferenceElevation-(zA+zB+zC)/3.0));
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

    int getFlatTriangleCount() {
      return nFlatTriangles;
    }

    double getMeanDepth(){
      return depthAreaWeightedSum.getSum()/this.depthAreaSum.getSum();
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
    //ps.println("TIN class: " + (tin.getClass().getName()));
    tin.add(soundings, null);
    long time1=System.nanoTime();
    tin.addConstraints(allConstraints, true);
    long time2 = System.nanoTime();
    long timeToBuildTin = time1 - time0;
    long timeToAddConstraints = time2-time1;

    TriangleSurvey trigSurvey = new TriangleSurvey(tin, shoreReferenceElevation);
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
      ps.println("Pass   Remediated        Area     Volume Added    avg. depth");
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
          ps.format("%4d  %8d  %14.3f  %14.3f  %7.3f%n",
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
      ps.println("N remediation vertices added: " + nRemediationVertices);
      // the vertex insertion logic in the flat fixer results in
      // a triangulation that is not properly Delaunay.  Additionally,
      // there is evidence that the insertion is flawed and that the
      // resulting triangulation may have crossing edges.  This is a bug,
      // and will need to be addressed.  But, for now, rebuild the triangulation.
      tin.dispose();
      if (soundings.size() < 500000) {
        tin = new IncrementalTin(1.0);
      } else {
        tin = new SemiVirtualIncrementalTin(1.0);
      }
      //ps.println("TIN class: " + (tin.getClass().getName()));
      HilbertSort hilbert = new HilbertSort();
      hilbert.sort(soundings);
      tin.add(soundings, null);
      tin.addConstraints(allConstraints, true);
            long timeF1 = System.nanoTime();
      timeToFixFlats = timeF1 - timeF0;
    }
    ps.println("");
    ps.println("Processing data from Delaunay Triangulation");
     time1 = System.nanoTime();
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

     time2 = System.nanoTime();

    List<PolygonConstraint> lakeConstraints = data.getLakeConstraints();
    List<PolygonConstraint> islandConstraints = data.getIslandConstraints();

    double lakeArea = getAreaSum(lakeConstraints) / areaFactor;
    double islandArea = Math.abs(getAreaSum(islandConstraints) / areaFactor);
    double lakePerimeter = getPerimeterSum(lakeConstraints) / lengthFactor;
    double islandPerimeter = getPerimeterSum(islandConstraints) / lengthFactor;
    double netArea = lakeArea - islandArea;
    double totalShore = lakePerimeter + islandPerimeter;
    Rectangle2D bounds = data.getBounds();

    ps.format("%nData from Shapefiles --------------------------------------------------------------%n");
    ps.format("  Lake area           %,18.2f %s%n", lakeArea, areaUnits);
    ps.format("  Island area         %,18.2f %s%n", islandArea, areaUnits);
    ps.format("  Net area (water)    %,18.2f %s%n", netArea, areaUnits);
    ps.format("  Lake shoreline      %,18.2f %s%n", lakePerimeter, lengthUnits);
    ps.format("  Island shoreline    %,18.2f %s%n", islandPerimeter, lengthUnits);
    ps.format("  Total shoreline     %,18.2f %s%n", totalShore, lengthUnits);
    ps.format("  N Islands           %18d%n", islandConstraints.size());
    ps.format("  N Soundings         %18d%n", data.getSoundings().size());
    ps.format("  N Supplements       %18d%n", data.getSupplements().size());
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
      Arrays.sort(lenArray, 0, nLen);
      double meanLen = sumLen / nLen;
      double medianLen = lenArray[nLen / 2];

      ps.format("  Mean sounding spacing:   %12.3f %s%n",
        meanLen / lengthFactor, lengthUnits);
      ps.format("  Median sounding spacing: %12.3f %s%n",
        medianLen, lengthUnits);
      ps.format("%n");
    }

    double rawVolume = lakeConsumer.getVolume();
    double rawSurfArea = lakeConsumer.getSurfaceArea();

    double rawAdjMeanDepth = lakeConsumer.getAdjustedMeanDepth();
    double totalVolume = lakeConsumer.getVolume();
    double volume = lakeConsumer.getVolume() / volumeFactor;
    double surfArea = lakeConsumer.getSurfaceArea() / areaFactor;
    double avgDepth = (rawVolume / rawSurfArea) / lengthFactor;
    double adjMeanDepth = rawAdjMeanDepth/lengthFactor;
    double vertexSpacing = estimateInteriorVertexSpacing(tin, lakeConsumer);
    double rawFlatArea = lakeConsumer.getFlatArea();
    double flatArea = lakeConsumer.getFlatArea() / areaFactor;

    ps.format("%nComputations from Constrained Delaunay Triangulation -----------------------------%n");
    ps.format("  Volume              %,18.2f %s     %,28.1f %s^3%n",
      volume, volumeUnits, rawVolume, lengthUnits);
    ps.format("  Surface Area        %,18.2f %s     %,28.1f %s^2%n",
      surfArea, areaUnits, rawSurfArea, lengthUnits);
    ps.format("  Flat Area           %,18.2f %s     %,28.1f %s^2%n",
      flatArea, areaUnits, rawFlatArea, lengthUnits);
    ps.format("  Avg depth           %,18.2f %s%n", avgDepth, lengthUnits);
    ps.format("  Adj mean depth      %,18.2f %s%n", adjMeanDepth, lengthUnits);
    ps.format("  Mean Vertex Spacing %,18.2f %s%n", vertexSpacing, lengthUnits);
    ps.format("  N Triangles         %15d%n", lakeConsumer.nTriangles);
    ps.format("  N Flat Triangles    %15d%n", lakeConsumer.nFlatTriangles);

    if (properties.isFlatFixerEnabled()) {
      int originalTrigCount = trigSurvey.nTriangles;
      int originalFlatCount = trigSurvey.getFlatTriangleCount();
      double originalFlatArea = trigSurvey.getFlatArea() / areaFactor;
      ps.format("%nPre-Remediation statistics%n");
      ps.format("  Original Flat Area  %14.10e %,20.2f %s%n",
        originalFlatArea, originalFlatArea, areaUnits);
      ps.format("  Original N Triangle %d%n", originalTrigCount);
      ps.format("  Original N Flat     %d%n", originalFlatCount);
    }

    ps.format("%n%n%n");
    ps.format("Time to load data              %9.1f ms%n", data.getTimeToLoadData() / 1.0e+6);
    ps.format("Time to build TIN              %9.1f ms%n", timeToBuildTin / 1.0e+6);
    ps.format("Time to add shore constraint   %9.1f ms%n", timeToAddConstraints / 1.0e+6);
    ps.format("Time to remedy flat triangles  %9.1f ms%n", timeToFixFlats / 1.0e+6);
    ps.format("Time to compute lake volume    %9.1f ms%n", (time2 - time1) / 1.0e+6);
    ps.format("Time for all analysis          %9.1f ms%n", (time2 - time0) / 1.0e+6);
    ps.format("Time for all operations        %9.1f ms%n",
      (data.getTimeToLoadData() + time2 - time0) / 1.0e+6);

    ps.format("%n%nVolume Store Triangle Count: %d%n", vStore.getTriangleCount());

    PrintStream ts = ps;
    File tableFile = properties.getTableFile();
    FileOutputStream tableOutputStream = null;
    if (tableFile != null) {
      tableOutputStream = new FileOutputStream(tableFile);
      BufferedOutputStream bos = new BufferedOutputStream(tableOutputStream);
      ts = new PrintStream(bos, true, "UTF-8");
    }

    ts.println("Elevation, Area, Volume, Percent_Capacity");
    for (AreaVolumeResult result : resultList) {
      ts.format("%12.3f, %12.3f, %12.3f, %6.2f%n",
        result.level,
        result.area / areaFactor,
        result.volume / volumeFactor,
        100 * result.volume / totalVolume);
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
      totalVolume);
    boolean wroteGraph = capacityGraph.writeOutput();
    if (wroteGraph) {
      ps.println("Capacity graph written to " + properties.getCapacityGraphFile());
    }

    File contourOutput = properties.getContourGraphFile();
    if (contourOutput != null) {
         ps.println("\nIn preparation for contouring, subdividing large triangles");
      SvmRefinement refinement = new SvmRefinement();
      List<Vertex>vList = refinement.subdivideLargeTriangles(ps,  tin, 0.95);
      if(!vList.isEmpty()){
        tin.dispose();
        soundings.addAll(vList);
        HilbertSort hilbert = new HilbertSort();
        hilbert.sort(soundings);
         if (soundings.size() < 500000) {
      tin = new IncrementalTin(1.0);
    } else {
      tin = new SemiVirtualIncrementalTin(1.0);
    }
    tin.add(soundings, null);
    tin.addConstraints(allConstraints, true);
      }
      SvmContourGraph.write(
        ps,
        properties,
        data,
        shoreReferenceElevation,
        tin);
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
        int aAux = a.getAuxiliaryIndex();
        int bAux = a.getAuxiliaryIndex();
        if(aAux==SvmBathymetryData.BATHYMETRY_SOURCE && bAux==SvmBathymetryData.BATHYMETRY_SOURCE){
          n++;
          sumLength.add(e.getLength());
        }
      }
    }
    if(n==0){
      return 0;
    }
    return sumLength.getSum() / n;
  }

}
