/*-----------------------------------------------------------------------
 *
 * Copyright (C) 2016 Sonalysts Inc. All Rights Reserved.
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2016  G. Lucas     Created
 *
 * Notes:
 *
 *--------------------------------------------------------------------------
 */
package tinfour.test.development.cdt;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.SimpleTimeZone;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IIntegrityCheck;
import tinfour.common.LinearConstraint;
import tinfour.common.Vertex;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.TestVertices;

public class RandomConstraintTestSeries {

  /**
   * Provides a representation of geometry type for test constraints
   * based on a user argument string.
   */
  private enum ConstraintType {
    SingleSegment,
    ColinearSegments,
    RandomSegmentPair;

    static ConstraintType lenientValueOf(String s) {
      if (s == null || s.isEmpty()) {
        return RandomSegmentPair;
      }
      String test = s.toLowerCase();
      if (test.startsWith("single")) {
        return SingleSegment;
      } else if (test.startsWith("Colin")) {
        return ColinearSegments;
      } else {
        return RandomSegmentPair;
      }
    }

  }

  /**
   * Performs a series of tests over a randomly generated set of
   * sample points and constraint geometries.
   *
   * @param args command line arguments providing specifications for test
   */

  private static final String[] usage = {
    "RandomConstraintTestSeries",
    "  Tests the constraint addition by inserting random constraints",
    "  into sets of randomly generated vertices.  Constraint types",
    "  include:",
    "     SingleSegment      one randomly generated segment",
    "     ColinearSegments   a chain of two randomly generated colinear segments",
    "     RandomSegmentPair  a chain of two randomly generated segments (default)",
    "",
    "Options: ",
    "  -constraintType [SingleSegment, ColinearSegments, RandomSegmentPair]",
    "  -nTests <int>       number of vertex sets to generate for testing",
    "  -restoreConformity  restore conformity when adding constraints",
    "                          (by default, conformity is not restored)",
    "  -tinClass <class path>  selects the IIncrementalTin instance for testing",
    "                          supply full class path, such as ",
    "                          tinfour.standard.IncrementalTin",
    "  -vertexSeed <long>      initial random seed for vertex generation",
    "  -constraintSeed <long>  inital random seed for constraint generation",
    "",
    "      nTests*10 constraints will be tested for each vertex set",
    "      in the test series.  Random seeds will be incremented with each test,",
    "  "
  };

  public static void main(String[] args) {
    if (args.length == 0) {
      for (String a : usage) {
        System.out.println(a);
      }
    }
    RandomConstraintTestSeries test = new RandomConstraintTestSeries();
    test.process(System.out, args);
  }

  private long scanSeed(TestOptions options, String[] args, String target, boolean[] matched) {
    Long seedObj = options.scanLongOption(args, target, matched);
    if (seedObj == null) {
      return 0;
    } else {
      return seedObj;
    }
  }

  private void process(PrintStream ps, String[] args) {

    TestOptions options = new TestOptions();
    boolean[] matched = options.argumentScan(args);

    int testCount = options.getTestCount(100);
    int vertexCount = options.getVertexCount(20);
    long vertexSeed = scanSeed(options, args, "-vertexSeed", matched);
    long seed2 = scanSeed(options, args, "-constraintSeed", matched);

    boolean restoreConformity = options.scanBooleanOption(
      args, "-restoreConformity", matched, false);
    String cTypeStr = options.scanStringOption(args, "-constraintType", matched);
    ConstraintType constraintType = ConstraintType.lenientValueOf(cTypeStr);
    Class<?> tinClass = options.getTinClass();

    // -- Print test options ----------------------------
    SimpleDateFormat sdFormat
      = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    Date date = new Date();
    ps.println("");
    ps.println("Date of Test:                " + sdFormat.format(date) + " UTC");
    ps.println("TIN class:                   " + tinClass.getName());
    ps.println("Constraint type:             " + constraintType);
    ps.println("Restore delaunay conformity: " + restoreConformity);
    ps.format("Number of vertices to process:    %8d\n", vertexCount);
    ps.format("Seed (for vertex generation):     %8d\n", vertexSeed);
    ps.format("Seed (for constraint generation): %8d\n", seed2);

    // -- Run tests --------------------------------
    Random random = new Random(vertexSeed);
    int nTestsPerformed = 0;
    int nSyntheticPoints = 0;
    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    for (int i = 0; i < testCount; i++) {
      if ((i % 100) == 0) {
        ps.println("Testing vertex set " + i);
      }
      long iSeed = vertexSeed + i;
      List<Vertex> vertexList
        = TestVertices.makeRandomVertices(vertexCount, (int) iSeed);
      for (int j = 0; j < testCount * 10; j++) {
        tin.clear();
        tin.add(vertexList, null);
        long jSeed = seed2 + j;
        random.setSeed(jSeed);
        List<IConstraint> constraintList = makeConstraints(random, constraintType);

        try {
          tin.addConstraints(constraintList, restoreConformity);
          nTestsPerformed++;
          nSyntheticPoints += tin.getSyntheticVertexCount();
        } catch (Exception ex) {
          System.err.println("Unexpected exception in trial "
            + iSeed + ", " + jSeed);
          ex.printStackTrace(System.err);
          System.exit(-1);
        }
        IIntegrityCheck iCheck = tin.getIntegrityCheck();
        if (!iCheck.inspect()) {
          System.out.println("TIN failed inspection for vertex seed "
            + iSeed + ", constraint seed" + jSeed + " failed");
          iCheck.printSummary(System.out);
          System.exit(-1);
        } else if (restoreConformity && iCheck.getConstrainedViolationCount() > 0) {
          int n = iCheck.getConstrainedViolationCount();
          System.out.println("TIN failed to restore conformity for vertex seed "
            + iSeed + ", constraint seed" + jSeed
            + " constrained violation count " + n);
          System.exit(-1);
        }
      }

    }

    if (restoreConformity) {
      int edgesPerConstraint;
      if (constraintType == ConstraintType.SingleSegment) {
        edgesPerConstraint = 1;
      } else {
        edgesPerConstraint = 2;
      }
      ps.println("Average number of synthetic vertices added per constraint edge "
        + (double) nSyntheticPoints / (double) (nTestsPerformed * edgesPerConstraint));
    }
  }

  private List<IConstraint> makeConstraints(Random random, ConstraintType cType) {
    List<IConstraint> conList = new ArrayList<>();
    LinearConstraint linCon = new LinearConstraint();
    conList.add(linCon);

    double x0 = random.nextDouble();
    double y0 = random.nextDouble();
    double x1 = random.nextDouble();
    double y1 = random.nextDouble();
    double x2 = random.nextDouble();
    double y2 = random.nextDouble();

    Vertex v0, v1, v2, vm;

    switch (cType) {
      case SingleSegment:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1001);
        linCon.add(v0);
        linCon.add(v1);
        break;
      case ColinearSegments:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1002);
        double xm = (v0.getX() + v1.getX()) / 2.0;
        double ym = (v0.getY() + v1.getY()) / 2.0;
        vm = new Vertex(xm, ym, 1, 1001);
        linCon.add(v0);
        linCon.add(vm);
        linCon.add(v1);
        break;
      case RandomSegmentPair:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1001);
        v2 = new Vertex(x2, y2, 1, 1002);
        linCon.add(v0);
        linCon.add(v1);
        linCon.add(v2);
        break;
      default:
        return null; // never happens
    }

    return conList;
  }

}
