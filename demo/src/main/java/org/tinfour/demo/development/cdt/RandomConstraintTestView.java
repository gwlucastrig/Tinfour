/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * Date     Name      Description
 * ------   --------- -------------------------------------------------
 * 12/2016  G. Lucas  Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.development.cdt;

import java.awt.Dimension;
import java.io.PrintStream;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.IInterpolatorOverTin;

/**
 * Provides a tool for visually inspecting the results from a random
 * constraint test. This class is designed for use with the
 * RandomConstraintTestSeries class.
 */
public class RandomConstraintTestView {

  IIncrementalTin tin;
  TestPanelForCdt testPanel;
  IInterpolatorOverTin interpolator;
  LinearConstraint linCon = new LinearConstraint();
  boolean cavitationPerformed;
  int nClicks;

  /**
   * Provides the main for running a visual inspection of constraint
   * geometries.
   *
   * @param args a valid, potentially zero-length set of command line arguments
   */
  public static void main(String[] args) {
    RandomConstraintTestView test = new RandomConstraintTestView();
    test.process(System.out, args);
  }

  private void process(final PrintStream ps, String[] args) {
    boolean[] argsRecognized = new boolean[args.length];
    RandomConstraintTestOptions options
      = new RandomConstraintTestOptions(args, argsRecognized);

    options.printSummary(ps);
    List<Vertex> vertices = options.makeRandomVertices(0);
    List<IConstraint> constraints = options.makeConstraints(0);

    tin = options.makeNewInstanceOfTestTin();
    tin.add(vertices, null);
    tin.addConstraints(constraints, options.restoreConformity);
    int vertexSeed = options.getVertexSeed(0);
    int constraintSeed = options.getConstraintSeed(0);
    testPanel = TestPanelForCdt.plot(tin,
      "View constraints from random vertexSeed " + vertexSeed
      + ", constraintSeed " + constraintSeed);
    testPanel.setSize(new Dimension(800, 800));
    testPanel.setValueLabelEnabled(false);
    testPanel.setValueLabelFormat("%1.0f");
    testPanel.setIndexLabelEnabled(true);

    for (IConstraint con : constraints) {
      List<Vertex> vList = con.getVertices();
      Vertex[] vArray = vList.toArray(new Vertex[vList.size()]); // NOPMD
      testPanel.addChainToSpecialList(vArray);
    }

    IIntegrityCheck iCheck = tin.getIntegrityCheck();
    boolean inspect = iCheck.inspect();
    System.out.println("Integrity check status=" + inspect);
    iCheck.printSummary(ps);

  }

}
