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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.LinearConstraint;
import tinfour.common.Vertex;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.TestVertices;

class RandomConstraintTestOptions {

  final TestOptions options;
  final int testCount;
  final int vertexCount;
  final long vertexSeed;
  final long constraintSeed;
  final ConstraintType constraintType;
  final Class<?> tinClass;
  final boolean restoreConformity;
  final Random random;

  private long scanSeed(
    TestOptions options,
    String[] args,
    String target,
    boolean[] matched) {
    Long seedObj = options.scanLongOption(args, target, matched);
    if (seedObj == null) {
      return 0;
    } else {
      return seedObj;
    }
  }

  /**
   * Constructs an instance populated with test options supplied as
   * command-line arguments
   *
   * @param args a valid array of zero or more arguments.
   * @param argsRecognized an optional array to record which arguments
   * were recognized and used
   */
  RandomConstraintTestOptions(String[] args, boolean[] argsRecognized) {
    random = new Random();
    options = new TestOptions();
    boolean[] matched = options.argumentScan(args);

    testCount = options.getTestCount(100);
    vertexCount = options.getVertexCount(20);
    vertexSeed = scanSeed(options, args, "-vertexSeed", matched);
    constraintSeed = scanSeed(options, args, "-constraintSeed", matched);

    restoreConformity = options.scanBooleanOption(
      args, "-restoreConformity", matched, false);
    String cTypeStr = options.scanStringOption(args, "-constraintType", matched);
    constraintType = ConstraintType.lenientValueOf(cTypeStr);
    tinClass = options.getTinClass();
    if (argsRecognized != null) {
      System.arraycopy(matched, 0, argsRecognized, 0, args.length);
    }
  }

  /**
   * Gets the number of tests to be performed.
   *
   * @return a positive integer
   */
  int getTestCount() {
    return testCount;
  }

  /**
   * Indicates whether test is configured to restore conformity.
   *
   * @return true if conformity is to be restored; otherwise, false.
   */
  boolean isConformityRestoreSet() {
    return restoreConformity;
  }

  /**
   * Gets the random seed that is used to create a set of vertices
   * for the specified test index. The seed is the sum of the
   * vertex seed specified by the arguments passed to the constructor
   * and the test index
   *
   * @param testIndex a positive integer
   * @return an integer value
   */
  int getVertexSeed(int testIndex) {
    return (int) (vertexSeed + testIndex);
  }

  /**
   * Gets the random seed that is used to create a set of constraints
   * for the specified test index. The seed is the sum of the
   * constraint seed specified by the arguments passed to the constructor
   * and the constraint test index
   *
   * @param testIndex a positive integer
   * @return an integer value
   */
  int getConstraintSeed(int constraintTestIndex) {
    return (int) (vertexSeed + constraintTestIndex);
  }

  /**
   * Gets the constraint-type geometry used for testing
   *
   * @return a valid enumeration instance.
   */
  ConstraintType getConstraintType() {
    return constraintType;
  }

  /**
   * Makes a list of vertices based on the sum of the vertexSeed specification
   * and
   * the specified test index. The list will contain the specified vertexCount
   * number of vertices.
   *
   * @param testIndex a positive integer, zero to use the vertex
   * seed obtained from the arguments passed to the constructor.
   * @return a valid list of vertices
   */
  List<Vertex> makeRandomVertices(int testIndex) {
    long iSeed = vertexSeed + testIndex;
    List<Vertex> vertexList
      = TestVertices.makeRandomVertices(vertexCount, (int) iSeed);
    return vertexList;
  }

  /**
   * Constructs a new instance of the incremental TIN class to
   * be used for testing. The class is selected based on
   * the argument vector passed to the constructor.
   *
   * @return a valid instance of an IIncrementalTin class.
   */
  IIncrementalTin makeNewInstanceOfTestTin() {
    return options.getNewInstanceOfTestTin();
  }

  /**
   * Makes collection of constraints based on the sum of the specified
   * constraint
   * seed and the constraint test index, as well as the specified constraint
   * type.
   *
   * @param constraintTestIndex a positive integer, zero to use the constraint
   * seed obtained from the arguments passed to the constructor.
   * @return
   */
  List<IConstraint> makeConstraints(int constraintTestIndex) {
    random.setSeed(constraintSeed + constraintTestIndex);
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
    double xm, ym, px, py;

    switch (constraintType) {
      case SingleSegment:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1001);
        linCon.add(v0);
        linCon.add(v1);
        break;
      case ColinearSegments:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1002);
        xm = (v0.getX() + v1.getX()) / 2.0;
        ym = (v0.getY() + v1.getY()) / 2.0;
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
      case RandomCross:
        v0 = new Vertex(x0, y0, 1, 1000);
        v1 = new Vertex(x1, y1, 1, 1002);
        xm = (v0.getX() + v1.getX()) / 2.0;
        ym = (v0.getY() + v1.getY()) / 2.0;
        vm = new Vertex(xm, ym, 1, 1001);
        linCon.add(v0);
        linCon.add(vm);
        linCon.add(v1);
        px = v0.getY() - v1.getY();
        py = v1.getX() - v0.getX();
        LinearConstraint cross = new LinearConstraint();
        conList.add(cross);
        cross.add(new Vertex(xm - px, ym - py, 1, 1003));
        cross.add(new Vertex(xm, ym, 1, 1004));
        cross.add(new Vertex(xm + px, ym + py, 1, 1005));
        break;
      default:
        return null; // never happens
    }

    return conList;
  }

  /**
   * Prints a summary of the test set-up derived from the
   * command-line arguments passed to the constructor.
   *
   * @param ps a valid print stream
   */
  void printSummary(PrintStream ps) {
    ps.println("TIN class:                      " + tinClass.getName());
    ps.println("Constraint type:                " + constraintType);
    ps.println("Restore delaunay conformity:    " + restoreConformity);
    ps.format("Number of vertex test sets:          %8d%n", testCount);
    ps.format("Number of constraint tests per sets: %8d%n", testCount);
    ps.format("Number of vertices to process:       %8d%n", vertexCount);
    ps.format("Seed (for vertex generation):        %8d%n", vertexSeed);
    ps.format("Seed (for constraint generation):    %8d%n", constraintSeed);
  }

  /**
   * Provides a representation of geometry type for test constraints
   * based on a user argument string.
   */
  enum ConstraintType {
    SingleSegment,
    ColinearSegments,
    RandomSegmentPair,
    RandomCross;

    static ConstraintType lenientValueOf(String s) {
      if (s == null || s.isEmpty()) {
        return RandomSegmentPair;
      }
      String test = s.toLowerCase();
      if (test.startsWith("single")) {
        return SingleSegment;
      } else if (test.startsWith("co")) {
        return ColinearSegments;
      } else if (test.startsWith("randomcross") || test.startsWith("cross")) {
        return RandomCross;
      } else {
        return RandomSegmentPair;
      }
    }

  }

}
