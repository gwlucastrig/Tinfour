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
 * 01/2024  G. Lucas     Extend output formats to use Java Locale for
 *                       European-style number formatting.  Improvements
 *                       to filtering for anomalous soundings using the
 *                       experimental SvmSinglePointAnomalyFilter
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
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Thresholds;
import org.tinfour.common.Vertex;
import org.tinfour.svm.properties.SvmProperties;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.svm.SvmTriangleVolumeTabulator.AreaVolumeSum;
import org.tinfour.utils.HilbertSort;
import org.tinfour.utils.TriangleCollector;

/**
 * Provide logic and elements for performing a volume computation for the
 * specified input data.
 */
public class SvmComputation {

  private final static int N_VERTICES_FOR_SEMI_VIRTUAL=100000;
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
    TriangleSurvey(IIncrementalTin tin, double shoreReferenceElevation ) {
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
      return Math.abs(a - b) < 1.0e-3;
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
          } else if (zA < shoreReferenceElevation || zB < shoreReferenceElevation || zC < shoreReferenceElevation) {
            depthAreaSum.add(area);
            depthAreaWeightedSum.add(area * (shoreReferenceElevation - (zA + zB + zC) / 3.0));
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

    double getMeanDepth() {
      return depthAreaWeightedSum.getSum() / this.depthAreaSum.getSum();
    }
  }

  private static class SampleSpacing {

    final int nSamples;
    final double sigma;
    final double mean;
    final double median;
    final double lenMin;
    final double lenMax;
    final double percentile25;
    final double percentile75;

    SampleSpacing(int nSamples, double mean, double sigma, double median, double lenMin, double lenMax, double percentile25, double percentile75) {
      this.nSamples = nSamples;
      this.mean = mean;
      this.sigma = sigma;
      this.median = median;
      this.lenMin = lenMin;
      this.lenMax = lenMax;
      this.percentile25 = percentile25;
      this.percentile75 = percentile75;
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

    SvmBathymetryModel bathymetryModel = properties.getBathymetryModel();

    // The nominal point spacing is based on the number of sample points
    // and the area of the lake data.  It is a rough estimate used to
    // select significant metrics for the incremental TIN implementations.
    double nominalPointSpacing = data.getNominalPointSpacing()<1?1:data.getNominalPointSpacing();

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
    List<LinearConstraint> interiorConstraints = data.getInteriorConstraints();

    List<IConstraint> allConstraints = new ArrayList<>();
    allConstraints.addAll(interiorConstraints);
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

//        SvmClothFilter clothFilter = new SvmClothFilter();
//    clothFilter.processVolume(ps, properties, data);
//    soundings = data.getSoundings();


    IIncrementalTin tin;

    long time0 = System.nanoTime();

    if (soundings.size() < N_VERTICES_FOR_SEMI_VIRTUAL) {
      tin = new IncrementalTin(nominalPointSpacing);
    } else {
      tin = new SemiVirtualIncrementalTin(nominalPointSpacing);
    }
    //ps.println("TIN class: " + (tin.getClass().getName()));
    HilbertSort hilbert1 = new HilbertSort();
    hilbert1.sort(soundings);
    tin.add(soundings, null);
    long time1 = System.nanoTime();
    tin.addConstraints(allConstraints, true);
    long time2 = System.nanoTime();
    long timeToBuildTin = time1 - time0;
    long timeToAddConstraints = time2 - time1;

    long spTime0 = System.nanoTime();
    SampleSpacing spacing = this.evaluateSampleSpacing(tin);
    long spTime1 = System.nanoTime();
    long timeToFindSampleSpacing = spTime1 - spTime0;

    // The experimental filter is a non-advertised feature for removing
    // anomalous points.
    if (properties.isExperimentalFilterEnabled()) {
      ps.println("Processing experimental filter");
      spTime0 = System.nanoTime();
      SvmSinglePointAnomalyFilter filter = new SvmSinglePointAnomalyFilter(properties);
      int nFilter = filter.process(ps, tin);
      ps.format("  Slope of anomaly  %10.3f%n", filter.getSlopeOfAnomaly());
      ps.format("  Slope of support  %10.3f%n", filter.getSlopeOfSupport());
      ps.format("  Points removed    %10d%n", nFilter);
      if (nFilter > 0) {
        // some vertices were marked as withheld
        ArrayList<Vertex> filteredSamples = new ArrayList<>(soundings.size());
        for (Vertex v : soundings) {
          if (!v.isWithheld()) {
            filteredSamples.add(v);
          }
        }
        soundings = filteredSamples;
        data.replaceSoundings(filteredSamples);
        spTime1 = System.nanoTime();
        long timeToFilter = spTime1 - spTime0;
        ps.format("  Time for experimental filter   %9.1f ms%n", timeToFilter / 1.0e+6);
        File filterOutputFile = properties.getExperimentalFilterFile();
        if (filterOutputFile != null) {
          try (FileOutputStream tableOutputStream = new FileOutputStream(filterOutputFile);
            BufferedOutputStream bos = new BufferedOutputStream(tableOutputStream);
            PrintStream fs = new PrintStream(bos, true, "UTF-8");) {
            fs.println("x\ty\tz\tindex");
            for (Vertex v : soundings) {
              fs.format("%12.6f\t%12.6f\t%5.4f\t%d%n",
                v.getX(), v.getY(), v.getZ(), v.getIndex());
            }
            fs.flush();
          } catch (IOException ioex) {
            ps.println("IOException writing filter output " + ioex.getMessage());
          }
        }
      }
    }

    // the two bathymetry models, elevation and depth, result in the
    // source vertices being assigned different treatments of the z
    // value.  For the elevation model, the z values at the shore naturally
    // will be the shoreReferenceElevation.  But, for the depth model,
    // shorelines vertices will be assigned a z value of zero.
    // Flat-fixing logic depends on this criteria being specified.
    double zFlatShore = shoreReferenceElevation;
    if (bathymetryModel == SvmBathymetryModel.Depth) {
      zFlatShore = 0;
    }


    TriangleSurvey trigSurvey = new TriangleSurvey(tin, zFlatShore);
    TriangleCollector.visitSimpleTriangles(tin, trigSurvey);

    long timeToFixFlats = 0;
    if (properties.isFlatFixerEnabled()) {
      // During the flat-fixer loop, the total count of triangles
      // may bounce around a bit.  In the case of coves and similar
      // features, fixing one layer of flats may expose yet more
      // flats to be fixed. Also, note that the counts/areas are
      // not the total count/area of flat triangles, but the total
      // of those triangles that were subject to remediation.  Counting
      // the actual flat area would add too much processing for too
      // little added value.
      long timeF0 = System.nanoTime();
      int nRemediationVertices = 0;
      ps.println("");
      ps.println("Remediating flat triangles");

      for (int iFlat = 0; iFlat < 500; iFlat++) {
        // construct a new flat-fixer each time
        // so we can gather counts
        SvmFlatFixer flatFixer = new SvmFlatFixer(
          tin,
          zFlatShore);
        List<Vertex> fixList = flatFixer.fixFlats(ps);
        if (fixList.isEmpty()) {
          if (iFlat == 0) {
            if (flatFixer.getFlatCount() == 0) {
              ps.println("No flat triangles were detected");
              System.out.println("No flat triangles were detected");
            } else {
              ps.println("Insufficient data to fix flat triangles for "
                + flatFixer.getFlatCount() + " found");
              System.out.println("Insufficient data to fix flat triangles for "
                + flatFixer.getFlatCount() + " found");
            }
          }
          break;
        }
        if(iFlat==0){
          System.out.println("Pass   Remediated        Area     Volume Added    avg. depth");
        }
        if (iFlat % 10 == 0) {
          double fixArea = flatFixer.getRemediatedArea();
          double fixVolume = flatFixer.getRemediatedVolume();
          System.out.format("%4d  %8d  %14.3f  %14.3f  %7.3f%n",
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
      if (soundings.size() < N_VERTICES_FOR_SEMI_VIRTUAL) {
        tin = new IncrementalTin(nominalPointSpacing);
      } else {
        tin = new SemiVirtualIncrementalTin(nominalPointSpacing);
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
    double tableInterval = properties.getTableInterval();
    double zShore = data.shoreReferenceElevation;
    double zMin = data.getMinZ();
    //  Diagnostic to support future development
    //  double zMax = data.getMaxZ();
    //  int i0 = (int)Math.floor(zMin/tableInterval);
    //  int i1 = (int)Math.floor(zShore/tableInterval+1.0e-6);
    //  double z0 = (double)i0*tableInterval;
    //  double z1 = (double)i1*tableInterval;
    //  int nI = i1-i0+1;
    int nStep = (int) (Math.ceil(zShore - zMin) / tableInterval) + 1;
    if (nStep > 10000) {
      nStep = 10000;
    }

    if(nStep<0){
      String gripe = "Error in elevation or data extraction model and attribute settings";
      ps.println(gripe);
      throw new IllegalArgumentException(gripe);
    }
    double[] zArray = new double[nStep];
    for (int i = 0; i < nStep; i++) {
      zArray[i] = zShore - i * tableInterval;
    }

    SvmTriangleVolumeTabulator volumeTabulator
      = new SvmTriangleVolumeTabulator(tin, zShore, zArray);
    volumeTabulator.process(tin);

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

    ps.format("%nData from Shapefiles and Source Data --------------------------------------------%n");
    if (properties.doesLocaleUseCommaForDecimal()) {
      ps.format("  Lake area           %18.2f %s%n", lakeArea, areaUnits);
      ps.format("  Island area         %18.2f %s%n", islandArea, areaUnits);
      ps.format("  Net area (water)    %18.2f %s%n", netArea, areaUnits);
      ps.format("  Lake shoreline      %18.2f %s%n", lakePerimeter, lengthUnits);
      ps.format("  Island shoreline    %18.2f %s%n", islandPerimeter, lengthUnits);
      ps.format("  Total shoreline     %18.2f %s%n", totalShore, lengthUnits);
    } else {
      ps.format("  Lake area           %,18.2f %s%n", lakeArea, areaUnits);
      ps.format("  Island area         %,18.2f %s%n", islandArea, areaUnits);
      ps.format("  Net area (water)    %,18.2f %s%n", netArea, areaUnits);
      ps.format("  Lake shoreline      %,18.2f %s%n", lakePerimeter, lengthUnits);
      ps.format("  Island shoreline    %,18.2f %s%n", islandPerimeter, lengthUnits);
      ps.format("  Total shoreline     %,18.2f %s%n", totalShore, lengthUnits);
    }
    ps.format("  N Islands           %18d%n", islandConstraints.size());
    ps.format("  N Soundings         %18d%n", data.getSoundings().size());
    ps.format("  N Supplements       %18d%n", data.getSupplements().size());
    ps.format("  Bounds%n");
    ps.format("     x:    %12.3f to %12.3f, (%5.3f)%n",
      bounds.getMinX() / lengthFactor,
      bounds.getMaxX() / lengthFactor,
      bounds.getWidth() / lengthFactor);
    ps.format("     y:    %12.3f to %12.3f, (%5.3f)%n",
      bounds.getMinY() / lengthFactor,
      bounds.getMaxY() / lengthFactor,
      bounds.getHeight() / lengthFactor);
    ps.format("     z:    %12.3f to %12.3f, (%5.3f)%n",
      data.getMinZ() / lengthFactor,
      data.getMaxZ() / lengthFactor,
      (data.getMaxZ() - data.getMinZ()) / lengthFactor);

    ps.format("  Sounding spacing%n");
    ps.format("     mean            %12.3f %s%n", spacing.mean / lengthFactor, lengthUnits);
    ps.format("     std dev         %12.3f %s%n", spacing.sigma / lengthFactor, lengthUnits);
    ps.format("     25th percentile %12.3f %s%n", spacing.percentile25/lengthFactor, lengthUnits);
    ps.format("     median          %12.3f %s%n", spacing.median / lengthFactor, lengthUnits);
    ps.format("     75th percentile %12.3f %s%n", spacing.percentile75/lengthFactor, lengthUnits);
    ps.format("     maximum         %12.3f %s%n", spacing.lenMax / lengthFactor, lengthUnits);
    ps.format("     minimum         %14.5f %s%n", spacing.lenMin / lengthFactor, lengthUnits);
    ps.format("%n");

    double rawVolume = volumeTabulator.getVolume();
    double rawSurfArea = volumeTabulator.getSurfaceArea();

    double rawAdjMeanDepth = volumeTabulator.getAdjustedMeanDepth();
    double totalVolume = volumeTabulator.getVolume();
    double volume = volumeTabulator.getVolume() / volumeFactor;
    double surfArea = volumeTabulator.getSurfaceArea() / areaFactor;
    double totalArea = volumeTabulator.getSurfaceArea();
    double avgDepth = (rawVolume / rawSurfArea) / lengthFactor;
    double adjMeanDepth = rawAdjMeanDepth / lengthFactor;
    double rawFlatArea = volumeTabulator.getFlatArea();
    double flatArea = volumeTabulator.getFlatArea() / areaFactor;
    List<AreaVolumeSum> resultList = volumeTabulator.getResults();

    ps.format("%nComputations from Constrained Delaunay Triangulation -----------------------------%n");
    if (properties.doesLocaleUseCommaForDecimal()) {
      ps.format("  Volume              %18.2f %-12s     %24.1f %s^3%n",
        volume, volumeUnits, rawVolume, lengthUnits);
      ps.format("  Surface Area        %18.2f %-12s     %24.1f %s^2%n",
        surfArea, areaUnits, rawSurfArea, lengthUnits);
      ps.format("  Flat Area           %18.2f %-12s     %24.1f %s^2%n",
        flatArea, areaUnits, rawFlatArea, lengthUnits);
      ps.format("  Avg depth           %18.2f %s%n", avgDepth, lengthUnits);
      ps.format("  Adj mean depth      %18.2f %s%n", adjMeanDepth, lengthUnits);
      ps.format("  Mean Vertex Spacing %18.2f %s%n", spacing.mean, lengthUnits);
    } else {
      ps.format("  Volume              %,18.2f %-12s     %,24.1f %s^3%n",
        volume, volumeUnits, rawVolume, lengthUnits);
      ps.format("  Surface Area        %,18.2f %-12s     %,24.1f %s^2%n",
        surfArea, areaUnits, rawSurfArea, lengthUnits);
      ps.format("  Flat Area           %,18.2f %-12s     %,24.1f %s^2%n",
        flatArea, areaUnits, rawFlatArea, lengthUnits);
      ps.format("  Avg depth           %,18.2f %s%n", avgDepth, lengthUnits);
      ps.format("  Adj mean depth      %,18.2f %s%n", adjMeanDepth, lengthUnits);
      ps.format("  Mean Vertex Spacing %,18.2f %s%n", spacing.mean, lengthUnits);
    }
    ps.format("  N Triangles         %15d%n", volumeTabulator.nTriangles);
    ps.format("  N Flat Triangles    %15d%n", volumeTabulator.nFlatTriangles);

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
    ps.format("Time to find sample spacing    %9.1f ms%n", timeToFindSampleSpacing / 1.0e+6);
    ps.format("Time to remedy flat triangles  %9.1f ms%n", timeToFixFlats / 1.0e+6);
    ps.format("Time to compute lake volume    %9.1f ms%n", (time2 - time1) / 1.0e+6);
    ps.format("Time for all analysis          %9.1f ms%n", (time2 - time0) / 1.0e+6);
    ps.format("Time for all operations        %9.1f ms%n",
      (data.getTimeToLoadData() + time2 - time0) / 1.0e+6);

    ps.format("%n%nVolume Store Triangle Count: %d%n", volumeTabulator.nTriangles);

    ps.flush();

    File tableFile = properties.getTableFile();
    if (tableFile != null) {
      String tableFileName = tableFile.getName();
      boolean csvFlag = tableFileName.toLowerCase().endsWith(".csv");
      if (csvFlag && properties.doesLocaleUseCommaForDecimal()) {
        System.out.println("\nNote: Using CSV file for table output may conflict with formatting specified by Locale\n");
      }

      try (FileOutputStream tableOutputStream = new FileOutputStream(tableFile);
        BufferedOutputStream bos = new BufferedOutputStream(tableOutputStream);
        PrintStream ts = new PrintStream(bos, true, "UTF-8");) {
        String lineFormat;
        if (csvFlag) {
          ts.println("Elevation, Drawdown, Area, Percent_area, Volume, Volume_loss, Percent_volume");
          lineFormat = "%12.3f, %12.3f, %12.3f, %6.2f, %12.3f, %12.3f, %6.2f%n";
        } else {
          ts.println("Elevation\tDrawdown\tArea\tPercent_area\tVolume\tVolume_loss\tPercent_volume");
          lineFormat = "%12.3f\t%12.3f\t%12.3f\t%6.2f\t%12.3f\t%12.3f\t%6.2f%n";
        }

        for(AreaVolumeSum avSum: resultList){
          double elevation;
          double drawdown;
          if(bathymetryModel == SvmBathymetryModel.Elevation){
            elevation = avSum.level;
            drawdown = elevation-shoreReferenceElevation;
          }else{
            elevation = avSum.level+shoreReferenceElevation;
            drawdown = avSum.level;
          }
          double areaAtLevel = avSum.areaSum.getSum();
          double volumeAtLevel = avSum.volumeSum.getSum();
          ts.format(lineFormat,
            elevation,
            drawdown,
            areaAtLevel / areaFactor,
            100*areaAtLevel/totalArea,
            volumeAtLevel / volumeFactor,
            (totalVolume-volumeAtLevel)/volumeFactor,
            100 * volumeAtLevel / totalVolume);
          if (volumeAtLevel == 0) {
            break;
          }
        }
      } catch (IOException ioex) {
        ps.println("Serious error writing elevation/volume table "
          + ioex.getMessage());
      }
    }

    SvmRasterGeoTiff rTiff = new SvmRasterGeoTiff();
    rTiff.buildAndWriteRaster(properties, data, ps, tin, volumeTabulator.water, shoreReferenceElevation);

    File gridFile = properties.getGridFile();
    double s = properties.getGridCellSize();
    if (gridFile != null && !Double.isNaN(s)) {
      SvmRaster grid = new SvmRaster();
      grid.buildAndWriteRaster(properties, data, ps, tin, volumeTabulator.water, shoreReferenceElevation);
    } else {
      // if the user specified an image file, the grid file is mandatory
      File gridImageFile = properties.getGridImageFile();
      if (gridImageFile != null) {
        ps.println("\nNote: The properties specify a grid-image file "
          + gridImageFile.getPath());
        ps.println("but not a grid file. An image file cannot be produced");
        ps.println("without a valid grid file.\n");
      }
    }

    if (properties.isCapacityGraphEnabled()) {
      SvmCapacityGraph capacityGraph = new SvmCapacityGraph(
        properties,
        resultList,
        totalVolume);
      boolean wroteGraph = capacityGraph.writeOutput();
      if (wroteGraph) {
        ps.println("Capacity graph written to " + properties.getCapacityGraphFile());
      }
    }

    if (properties.isDrawdownGraphEnabled()) {
      SvmDrawdownGraph drawdownGraph = new SvmDrawdownGraph(
        properties,
        resultList,
        totalVolume);
      boolean wroteGraph = drawdownGraph.writeOutput();
      if (wroteGraph) {
        ps.println("Drawdown graph written to " + properties.getDrawdownGraphFile());
      }
    }

    File contourOutput = properties.getContourGraphFile();
    if (contourOutput != null) {
      long subtime0 = System.currentTimeMillis();
      System.out.println("\nIn preparation for contouring, subdividing large triangles");
      ps.println("\nIn preparation for contouring, subdividing large triangles");
      SvmRefinement refinement = new SvmRefinement();
      List<Vertex> vList = refinement.subdivideLargeTriangles(ps, tin, 0.95);
      long subtime1 = System.currentTimeMillis();
      System.out.println("\nCompleted subdividing large triangles in "+(subtime1-subtime0+" ms"));
      if (!vList.isEmpty()) {
        tin.dispose();
        soundings.addAll(vList);
        HilbertSort hilbert = new HilbertSort();
        hilbert.sort(soundings);
        if (soundings.size() < N_VERTICES_FOR_SEMI_VIRTUAL) {
          tin = new IncrementalTin(nominalPointSpacing);
        } else {
          tin = new SemiVirtualIncrementalTin(nominalPointSpacing);
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
   * Finds the spacing between samples in the source data, selecting
   * neighbors based on the Delaunay triangulation. This method
   * applies logic to exclude constraint edges and edges that
   * attach samples to constraint vertices. The rationale
   * for this exclusion is that the constraints have a different
   * level of data collection than the samples and would have
   * different statistics. The logic below also excludes
   * perimeter edges
   * <p>
   * This logic assumes that the TIN was prepared using the SVM conventions
   * of populating the auxiliary index of the sample vertices with
   * a flag indicating that they are depth samples.
   *
   * @param tin a valid instance
   * @return a valid instance
   */
  private SampleSpacing evaluateSampleSpacing(IIncrementalTin tin) {

    List<IConstraint> constraintsFromTin = tin.getConstraints();
    boolean[] water = new boolean[constraintsFromTin.size()];
    for (IConstraint con : constraintsFromTin) {
      water[con.getConstraintIndex()] = (Boolean) con.getApplicationData();
    }
    KahanSummation sumLen = new KahanSummation();
    KahanSummation sumLen2 = new KahanSummation();
    int nEdgeMax = tin.getMaximumEdgeAllocationIndex();
    float[] lenArray = new float[nEdgeMax];
    int nLen = 0;
    double lenMin = Double.POSITIVE_INFINITY;
    double lenMax = Double.NEGATIVE_INFINITY;

    for (IQuadEdge edge : tin.edges()) {
      if (!edge.isConstraintRegionInterior()) {
        continue;
      }

      int conIndex = edge.getConstraintRegionInteriorIndex();
      if (water[conIndex]) {
        // the edge lies in a water area, but we also need
        // to exclude any edges that connect a sounding to
        // a constraint border.
        Vertex a = edge.getA();
        Vertex b = edge.getB();
        int aAux = a.getAuxiliaryIndex();
        int bAux = b.getAuxiliaryIndex();
        if (aAux == SvmBathymetryData.BATHYMETRY_SOURCE
          && bAux == SvmBathymetryData.BATHYMETRY_SOURCE) {
          double len = edge.getLength();
          sumLen.add(len);
          sumLen2.add(len * len);
          lenArray[nLen++] = (float) len;
          if (len < lenMin) {
            lenMin = len;
          }
          if (len > lenMax) {
            lenMax = len;
          }
        }
      }
    }

    Arrays.sort(lenArray, 0, nLen);
    double sLen = sumLen.getSum();
    double sLen2 = sumLen2.getSum();

    double sigma = Double.NaN;
    double mean = sumLen.getMean();
    double median = lenArray[nLen / 2];
    if (nLen > 2) {
      sigma = Math.sqrt((sLen2 - (sLen / nLen) * sLen) / (nLen - 1));
    }
    double percentile25 = lenArray[(int)(nLen*0.25+0.5)];
     double percentile75 = lenArray[(int)(nLen*0.75+0.5)];

    return new SampleSpacing(nLen, mean, sigma, median, lenMin, lenMax, percentile25, percentile75);

  }
}
