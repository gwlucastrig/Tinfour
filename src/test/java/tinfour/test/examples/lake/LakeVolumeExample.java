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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;
import tinfour.common.GeometricOperations;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.PolygonConstraint;
import tinfour.common.SimpleTriangle;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;
import tinfour.semivirtual.SemiVirtualIncrementalTin;
import tinfour.standard.IncrementalTin;
import tinfour.utils.TriangleCollector;

/**
 *
 */
public class LakeVolumeExample {

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    File folder = new File("LakeVictoriaShapefiles");
    File inputSoundingsFile = new File(folder, "finbath.csv");
    File inputLakeFile = new File(folder, "LAKE.SHP");
    File inputIslandFile = new File(folder, "ISLANDS.SHP");
    try {
      BathymetryData data = new BathymetryData(
              inputSoundingsFile,
              inputLakeFile,
              inputIslandFile);
      data.printSummary(System.out);
      LakeVolumeExample lvCalc = new LakeVolumeExample();
      lvCalc.processVolume(System.out, data);
    } catch (IOException ioex) {
      System.out.println(
              "Exception processing lake volume files, "
              + ioex.getMessage());
    }
  }

  /**
   * A Java Consumer to collect the contribution from each water
   * triangle in the Constrained Delaunay Triangulation.
   */
  private static class LakeData implements Consumer<SimpleTriangle> {
    boolean []water;
    final GeometricOperations geoOp;
    int nTriangles;
    double totalVolume;
    double surfaceArea;
    KahanSummation volumeSum = new KahanSummation();
    KahanSummation areaSum = new KahanSummation();

    /**
     * Constructs an instance for processing and extracts the
     * water/land values based on the integer index assigned
     * to the constraints. 
     * @param tin A valid Constrained Delaunay Triangulation
     */
    LakeData(IIncrementalTin tin) {
      List<IConstraint> constraintsFromTin = tin.getConstraints();
      water = new boolean[constraintsFromTin.size()];
      for(IConstraint con: constraintsFromTin){
        water[con.getConstraintIndex()] = (Boolean)con.getApplicationData();
      }
      Thresholds thresholds = tin.getThresholds();
      geoOp = new GeometricOperations(thresholds);
    }

    @Override
    public void accept(SimpleTriangle t) {
      IQuadEdge a = t.getEdgeA();
      IQuadEdge b = t.getEdgeB();
      IQuadEdge c = t.getEdgeC();
      if (isWater(a) || isWater(b) || isWater(c)) {
        Vertex vA = a.getA();
        Vertex vB = b.getA();
        Vertex vC = c.getA();
        double zA = -vA.getZ();
        double zB = -vB.getZ();
        double zC = -vC.getZ();
        double zMean = (zA + zB + zC) / 3;
        double area = geoOp.area(vA, vB, vC);

        nTriangles++;
       // totalVolume += zMean * area;
        
        volumeSum.add(zMean*area);
        areaSum.add(area);
        surfaceArea = areaSum.getSum();
        totalVolume = volumeSum.getSum();

      }
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
  }

  /**
   * Performs the main process, printing the results to the specified
   * print stream.
   * @param ps
   * @param data 
   */
  void processVolume(PrintStream ps, BathymetryData data) {
    List<Vertex> soundings = data.getSoundings();
    List<IConstraint> lakeConstraints = data.getLakeConstraints();
    List<IConstraint> islandConstraints = data.getIslandConstraints();
    for (IConstraint con : lakeConstraints) {
      con.setApplicationData(true);
    }
    for (IConstraint con : islandConstraints) {
      con.setApplicationData(false);
    }

    List<IConstraint> allConstraints = new ArrayList<>();
    allConstraints.addAll(lakeConstraints);
    allConstraints.addAll(islandConstraints);

    long time0 = System.nanoTime();
    IIncrementalTin tin;
    if (soundings.size() > 500000) {
      tin = new IncrementalTin(1.0);
    } else {
      tin = new SemiVirtualIncrementalTin(1.0);
    }
    tin.add(soundings, null);
    tin.addConstraints(allConstraints, true);

    LakeData results = new LakeData(tin);
    TriangleCollector.visitSimpleTriangles(tin, results);
    long time1 = System.nanoTime();


    double lakeArea = getAreaSum(lakeConstraints);
    double islandArea = getAreaSum(islandConstraints);
    ps.format("%nData from Shapefiles%n");
    ps.format("  Lake area       %8.6e%n", lakeArea);
    ps.format("  Island area     %8.6e%n", islandArea);
    ps.format("  Net area        %8.6e%n", (lakeArea - islandArea));
    ps.format("  N Islands       %d%n", islandConstraints.size());

    ps.format("%nComputations from Constrained Delaunay Triangulation%n");
    ps.format("  Volume          %8.6e%n", results.totalVolume);
    ps.format("  Surface Area    %8.6e%n", results.surfaceArea);
    ps.format("  Avg depth      %5.2f%n", results.totalVolume / results.surfaceArea);
    ps.format("  N Triangles    %d%n", results.nTriangles);
    ps.format("  Est. Sample Spacing %8.2f%n", estimateSampleSpacing(tin));
 
    ps.format("%n%nCompleted processing in %3.1f ms%n", (time1-time0)/1000000.0);
  }

  private double getAreaSum(List<IConstraint> constraints) {
    double areaSum = 0;
    for (IConstraint con : constraints) {
      if (con instanceof PolygonConstraint) {
        PolygonConstraint pc = (PolygonConstraint) con;
        areaSum += pc.getArea();
      }
    }
    return areaSum;
  }
  
  private double estimateSampleSpacing(IIncrementalTin tin){
    BitSet bitSet = new BitSet(tin.getMaximumEdgeAllocationIndex());
    for(IQuadEdge e: tin.getPerimeter()){
      bitSet.set(e.getIndex());
    }
    
    double sumLength=0;
    int n=0;
    for(IQuadEdge e: tin.edges()){
      if(!bitSet.get(e.getIndex()) && !e.isConstrainedRegionBorder()){
        n++;
        sumLength+=e.getLength();
      }
    }
    return sumLength/n;
  }
}
