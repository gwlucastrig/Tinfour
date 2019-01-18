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
 * 03/2016  G. Lucas     Created
 * 11/2018  G. Lucas     Added test for pathological input cases
 *                         with more robust handling for large sets of
 *                         nearly collinear input points.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A utility for performing the part of the bootstrap operation that is common
 * to both the standard and virtual incremental TIN implementations.
 */
public class BootstrapUtility {

  /**
   * An arbitrary maximum number of trials for selecting a bootstrap triangle.
   */
  private static final int N_TRIAL_MAX = 16;  //NOPMD

  /**
   * An arbitrary minimum number of trials for selecting a bootstrap triangle
   */
  private static final int N_TRIAL_MIN = 3;  //NOPMD

  /**
   * An arbitrary factor for estimating the number of trials for selecting a
   * bootstrap triangle.
   */
  private static final double TRIAL_FACTOR = (1.0 / 3.0);  //NOPMD

  /**
   * An arbitrary factor for computing the triangle min-area threshold. The
   * current specifies an area 1/64th that of a equilateral triangle with edges
   * of the length of the nominal point spacing.
   */
  private final static double MIN_AREA_FACTOR = Math.sqrt(3.0) / 4.0 / 64.0;

  /**
   * The threshold for determining whether the initial random-triangle selection
   * produced a sufficiently robust triangle to begin triangulation. The
   * assignment of this value is arbitrary. If a sufficiently large area
   * triangle is not found, the routine reverts to an exhaustive search.
   */
  final private double triangleMinAreaThreshold;

  /**
   * A set of geometric calculations tuned to the threshold settings for the
   * bootstrap utility.
   */
  final private GeometricOperations geoOp;

  final Random random = new Random(0);

  public BootstrapUtility(Thresholds thresholds) {
    triangleMinAreaThreshold
            = thresholds.getNominalPointSpacing() * MIN_AREA_FACTOR;

    geoOp = new GeometricOperations(thresholds);

  }

  private int computeNumberOfTrials(int nVertices) {
    int nTrial = (int) Math.pow((double) nVertices, TRIAL_FACTOR);
    if (nTrial < N_TRIAL_MIN) {
      nTrial = N_TRIAL_MIN;
    } else if (nTrial > N_TRIAL_MAX) {
      nTrial = N_TRIAL_MAX;
    }
    return nTrial;
  }

  /**
   * Obtain the initial three vertices for building the mesh by selecting from
   * the input list. Logic is provided to attempt to identify an initial
   * triangle with a non-trivial area (on the theory that this stipulation
   * produces a more robust initial mesh). In the event of an unsuccessful
   * bootstrap attempt, future attempts can be conducted as the calling
   * application provides additional vertices.
   *
   * @param list a valid list of input vertices.
   * @return if successful, a valid array of the initial three vertices.
   */
  @SuppressWarnings("PMD.ReturnEmptyArrayRatherThanNull")
  public Vertex[] bootstrap(final List<Vertex> list) {

    if (list.size() < 3) {
      return null;  //NOPMD
    }
    Vertex[] v = new Vertex[3];
    Vertex[] vtest = new Vertex[3];
    int n = list.size();
    int nTrial = computeNumberOfTrials(n);

    double bestScore = Double.NEGATIVE_INFINITY;
    for (int iTrial = 0; iTrial < nTrial; iTrial++) {
      if (n == 3) {
        vtest[0] = list.get(0);
        vtest[1] = list.get(1);
        vtest[2] = list.get(2);
      } else {
        // pick three unique vertices at random
        for (int i = 0; i < 3; i++) {
          while (true) {
            int index = random.nextInt(n); // (int) (n * random.nextDouble());
            vtest[i] = list.get(index);
            for (int j = 0; j < i; j++) {
              if (vtest[j] == vtest[i]) {
                vtest[i] = null;
                break;
              }
            }
            if (vtest[i] != null) {
              break;
            }
          }
        }
      }
      double a = geoOp.area(vtest[0], vtest[1], vtest[2]);
      if (a == 0) {
        continue;
      } else if (a < 0) {
        Vertex swap = vtest[0];
        vtest[0] = vtest[2];
        vtest[2] = swap;
        a = -a;
      }
      if (a > bestScore) {
        bestScore = a;
        v[0] = vtest[0];
        v[1] = vtest[1];
        v[2] = vtest[2];
      }
    }

    if (bestScore >= triangleMinAreaThreshold) {
      return v;
    }

    if (n == 3) {
      // the above trials already tested this case.
      // the set of vertices is not yet sufficient
      // to bootstrap the TIN
      return null; //NOPMD
    }

    // Most of the time, if the input set is well formed,
    // the random test will have found a valid vertex set.
    // Sometimes, though, we are just unlucky and the random
    // selection of vertices just happened to pick vertices that wouldn't
    // work.  Other times, the input is a pathological case
    // (all the vertices are the same, or all the vertices are collinear).
    // The testResult tries to detect the pathological cases and
    // also attempts to find a valid triangle without the potentially
    // massive processing required for an exhaustive search.
    List<Vertex> testList = new ArrayList<>(3);
    BootstrapTestResult testResult = this.testInput(list, testList);
    if (testResult == BootstrapTestResult.Valid) {
      v[0] = testList.get(0);
      v[1] = testList.get(1);
      v[2] = testList.get(2);
      return v;
    } else if (testResult != BootstrapTestResult.Unknown) {
      // the testInput method detected a pathological case.
      // there is no point attempting the exhaustive test
      return null;
    }

    // the testInput method could not figure out a good triangle
    // and could not decide whether the input data was pathological
    // or not.  So all it can do is an exhaustic test.
    exhaustiveLoop:
    for (int i = 0; i < n - 2; i++) {
      vtest[0] = list.get(i);
      for (int j = i + 1; j < n - 1; j++) {
        vtest[1] = list.get(j);
        for (int k = j + 1; k < n; k++) {
          vtest[2] = list.get(k);
          double a = geoOp.area(vtest[0], vtest[1], vtest[2]);
          double aAbs = Math.abs(a);
          if (aAbs > bestScore) {
            bestScore = aAbs;
            if (a < 0) {
              v[0] = vtest[2];
              v[1] = vtest[1];
              v[2] = vtest[0];
            } else {
              v[0] = vtest[0];
              v[1] = vtest[1];
              v[2] = vtest[2];
            }
            if (aAbs >= triangleMinAreaThreshold) {
              return v;
            }
          }
        }
      }
    }

    // the expensive loop above failed to discover a
    // useful initial triangle.  we'll just have
    // to wait for more vertices.
    return null; // NOPMD
  }

  /**
   * Indicates the results of the evaluation for a set of input points.
   */
  public enum BootstrapTestResult {
    /**
     * Indicates that the point set is insufficiently large for analysis
     */
    InsufficientPointSet,
    /**
     * Indicates that all input vertices are coincident or nearly coincident, so
     * that no meaningful computations are possible within the specified
     * thresholds.
     */
    TrivialPointSet,
    /**
     * Indicates that all input vertices are collinear or nearly collinear
     * within the specified thresholds.
     */
    CollinearPointSet,
    /**
     * Indicates that the routine was able to find a valid bootstrap triangle
     * from the input vertex set.
     */
    Valid,
    /**
     * Indicates that the routine was unable to find a valid bootstrap triangle
     * from the input vertex set. It is still possible that one might be found
     * through an exhaustive search.
     */
    Unknown;
  }

  /**
   * Given a set of input Vertices, test to see if their (x,y) coordinates are
   * sufficient to create a bootstrap triangle for processing. If they are, it
   * populates the result list with three vertices specifying a triangle. This
   * method attempts to detect pathological cases such as all-points-coincident
   * or all-points-collinear.
   * <p>
   * This method performs a linear regression to find a line that passes through
   * the mean (xBar,yBar) point of the collection in the direction (uX, uY) (a
   * unit vector). If all points are collinear, then (xBar, yBar) will lie on
   * the line. If the points are not all collinear, it detects the point that is
   * farthest from the line (in the perpendicular direction) and uses it as the
   * apex of the triangle. Two additional vertices are found by searching for
   * the vertex with the maximum distances in the direction of (uX, uY) and
   * -(uX, uY). If these three vertices form a triangle with sufficient area,
   * the method uses them to populate the output list and returns a result of
   * Valid.
   * <p>
   * This method will find a line through the samples in any case where the
   * distribution of samples is wider in one direction than in any other. It
   * will also work in the case where all samples are uniformly distributed,
   * though it is not as consistent as in cases where a line can be fit to a
   * data set.
   *
   * @param input a valid list
   * @param output a valid list; or a null if the calling application does not
   * need to know which vertices were selected.
   * @return a enumeration indicating success or the failure condition
   */
  public BootstrapTestResult testInput(
          List<Vertex> input,
          List<Vertex> output) {

    if (input == null || input.size() < 3) {
      return BootstrapTestResult.InsufficientPointSet;
    }
    if (output != null) {
      output.clear();
    }
    int n = input.size();
    double XY = 0;
    double X2 = 0;
    double Y2 = 0;
    double xBar = 0;
    double yBar = 0;
    for (Vertex v : input) {
      double x = v.getX();
      double y = v.getY();
      xBar += x;
      yBar += y;
    }
    xBar /= input.size();
    yBar /= input.size();

    for (Vertex v : input) {
      double x = v.getX() - xBar;
      double y = v.getY() - yBar;
      XY += x * y;
      X2 += x * x;
      Y2 += y * y;
    }
    Thresholds thresholds = geoOp.getThresholds();
    double samePoint2 = thresholds.getVertexTolerance2();
    if (X2 <= samePoint2 && Y2 <= samePoint2) {
      return BootstrapTestResult.TrivialPointSet;
    }

    double twoTheta = Math.atan2(2 * XY, X2 - Y2);
    double sin2T = Math.sin(twoTheta);
    double cos2T = Math.cos(twoTheta);

    // compute secondDrv, the second derivative.  If 2nd derivative is negative, 
    // then the theta we found would produce a local maximum for the 
    // distance sum.  So we simply advance it by 90 degrees.
    // If d2 is close to zero, then the distribution of points was
    // similar in all directions (e.g. it exhibited radial symmetry) and
    // no line is any better than any others...  so we just take the
    // value of theta we found and use it.
    double secondDrv = 2 * (X2 - Y2) * cos2T + 4 * XY * sin2T;
    double theta = twoTheta / 2;
    if (secondDrv < -thresholds.getHalfPlaneThreshold()) {
      // the calculation found us the local maximum
      theta += Math.PI / 2;
    }
    double uX = Math.cos(theta);
    double uY = Math.sin(theta);
    double pX = -uY;  // the perpendicular to uX, uY
    double pY = uX;

    double sMax = Double.NEGATIVE_INFINITY;
    int iMax = -1;

    Vertex a = null;
    for (int i = 0; i < n; i++) {
      Vertex v = input.get(i);
      double x = v.getX() - xBar;
      double y = v.getY() - yBar;
      double s = Math.abs(x * pX + y * pY);
      if (s > sMax) {
        sMax = s;
        iMax = i;
        a = v;
      }
    }
    if (sMax < thresholds.getHalfPlaneThreshold()) {
      // all vertices are co-linear
      return BootstrapTestResult.CollinearPointSet;
    }

    double tMin = Double.POSITIVE_INFINITY;
    double tMax = Double.NEGATIVE_INFINITY;
    Vertex b = null;
    Vertex c = null;
    for (int i = 0; i < n; i++) {
      if (i != iMax) {
        Vertex v = input.get(i);
        double x = v.getX() - xBar;
        double y = v.getY() - yBar;
        double t = Math.abs(x * pX + y * pY);
        if (t > tMax) {
          tMax = t;
          b = v;
        }
        if (t < tMin) {
          tMin = t;
          c = v;
        }
      }
    }

    if (a == null || b == null || c == null) {
      // not expected
      return BootstrapTestResult.InsufficientPointSet;
    }

    double area = Math.abs(geoOp.area(a, b, c));
    if (a.getDistance(c) > a.getDistance(b)) {
      // move the vertex with greater distance to "b" position
      Vertex swap = b;
      b = c;
      c = swap;
    }
    double areaMax = area;
    Vertex vMax = c;
    int nTrial = computeNumberOfTrials(n);
    for (int iTrial = 0; iTrial < nTrial; iTrial++) {
      int index = random.nextInt(n);
      Vertex v = input.get(index);
      area = Math.abs(geoOp.area(a, b, v));
      if (area > areaMax) {
        areaMax = area;
        vMax = v;
      }
    }
    c = vMax;

    if(areaMax < triangleMinAreaThreshold){
      // try a semi-exhaustive test
      for(Vertex v: input){
        area = Math.abs(geoOp.area(a, b, v));
        if(area>triangleMinAreaThreshold){
          c = v;
          break;
        }
      }
    }
    
    
    // orient triangle so result is positive
    area = geoOp.area(a, b, c);
    if (area < 0) {
      Vertex swap = b;
      b = c;
      c = swap;
      area = -area;
    }

    if (area > triangleMinAreaThreshold) {
      if (output != null) {
        output.add(a);
        output.add(b);
        output.add(c);
      }
      return BootstrapTestResult.Valid;
    }

    return BootstrapTestResult.Unknown;
  }

}
