/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 12/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.development.cdt;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IIntegrityCheck;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;

/**
 * Provides a development tool for automated testing of the Tinfour
 * Constrained Delaunay Triangulation (CDT) code. This class
 * generates vertices and constraints at random, processes the
 * data through the specified incremental-TIN class, and
 * inspects the results using an integrity check. The intent for this
 * class is to test thousands, or millions, of randomly generated geometries
 * in order to exercise the code and look for unanticipated errors
 * in implementation.
 * <p>
 * When an error result is detected, the application prints the
 * random seeds that were used to create the problematic input data.
 * This developer may then debug the process by re-run this test, specifying the
 * vertex-seed and constraint-seed values. If desired, the
 * seeds may also be passed into the RandomConstraintTestView class which
 * provides a utility for displaying the results.
 */
public class RandomConstraintTestSeries {

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
    "     RandomCross        a pair of two perpendicular chains intersecting at a vertex",
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

  private void process(PrintStream ps, String[] args) {

    RandomConstraintTestOptions options = new RandomConstraintTestOptions(args, null);

    // -- Print test options ----------------------------
    SimpleDateFormat sdFormat
      = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    Date date = new Date();
    ps.println("");
    ps.println("Random Constraint Test Series");
    ps.println("Date of Test:                " + sdFormat.format(date) + " UTC");
    options.printSummary(ps);

    // -- Run tests --------------------------------
    int nTestsPerformed = 0;
    int nSyntheticPoints = 0;
    int testCount = options.getTestCount();
    boolean restoreConformity = options.isConformityRestoreSet();
    RandomConstraintTestOptions.ConstraintType constraintType
      = options.getConstraintType();
    IIncrementalTin tin = options.makeNewInstanceOfTestTin();
    Thresholds thresholds = new Thresholds(1.0);
    for (int i = 0; i < testCount; i++) {
      if ((i % 100) == 0) {
        ps.println("Testing vertex set " + i);
      }

      int vertexSeed = options.getVertexSeed(i);
      List<Vertex> vertexList = options.makeRandomVertices(i);
      for (int j = 0; j < testCount * 10; j++) {
        tin.clear();
        tin.add(vertexList, null);

        int constraintSeed = options.getConstraintSeed(j);
        List<IConstraint> constraintList = options.makeConstraints(j);

        try {
          tin.addConstraints(constraintList, restoreConformity);
          nTestsPerformed++;
          nSyntheticPoints += tin.getSyntheticVertexCount();
        } catch (Exception ex) {
          System.err.println("Unexpected exception in trial "
            + vertexSeed + ", " + constraintSeed);
          ex.printStackTrace(System.err);
          System.exit(-1);
        }
        IIntegrityCheck iCheck = tin.getIntegrityCheck();
        if (iCheck.inspect()) {
          // check was successful. The integrity check reports success
          // when there are non-Delaunay edges, provided that those edges
          // are constraints.  So if restore-conformity is set,
          // also check to see that there were no constraint-edge violations.
          if (restoreConformity && iCheck.getConstrainedViolationCount() > 0) {
            int n = iCheck.getConstrainedViolationCount();
            double hMax = iCheck.getConstrainedViolationMaximum();
            double hLimit = thresholds.getDelaunayThreshold();
            if (hMax > hLimit) {
              System.out.println("TIN failed to restore conformity for vertex seed "
                + vertexSeed + ", constraint seed" + constraintSeed
                + " constrained violation count " + n);
              System.exit(-1);
            }
          }
        } else {
          System.out.println("TIN failed inspection for vertex seed "
            + vertexSeed + ", constraint seed" + constraintSeed + " failed");
          iCheck.printSummary(System.out);
          System.exit(-1);
        }
      }

    }

    if (restoreConformity) {
      int edgesPerConstraint;
      if (constraintType == RandomConstraintTestOptions.ConstraintType.SingleSegment) {
        edgesPerConstraint = 1;
      } else {
        edgesPerConstraint = 2;
      }
      ps.println("Average number of synthetic vertices added per constraint edge "
        + (double) nSyntheticPoints / (double) (nTestsPerformed * edgesPerConstraint));
    }
  }
}
