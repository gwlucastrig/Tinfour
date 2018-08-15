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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

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
  private static final double MIN_AREA_FACTOR = Math.sqrt(3.0) / 4.0 / 64.0;
 
  /**
   * The threshold for determining whether the initial random-triangle selection
   * produced a sufficiently robust triangle to begin triangulation. The
   * assignment of this value is arbitrary. If a sufficiently large area
   * triangle is not found, the routine reverts to an exhaustive search.
   */
  final private double triangleMinAreaThreshold;

  public BootstrapUtility(Thresholds thresholds) {
    triangleMinAreaThreshold
            = thresholds.getNominalPointSpacing() * MIN_AREA_FACTOR;
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
   * @param geoOp a valid instance constructed with appropriate thresholds
   * @return if successful, a valid array of the initial three vertices.
   */
  @SuppressWarnings("PMD.ReturnEmptyArrayRatherThanNull")
  public Vertex[] bootstrap(final List<Vertex> list, GeometricOperations geoOp) {

    final Random random = new Random(0);
    if (list.size() < 3) {
      return null;  //NOPMD
    }
    Vertex[] v = new Vertex[3];
    Vertex[] vtest = new Vertex[3];
    int n = list.size();
    int nTrial = (int) Math.pow((double) n, TRIAL_FACTOR);
    if (nTrial < N_TRIAL_MIN) {
      nTrial = N_TRIAL_MIN;
    } else if (nTrial > N_TRIAL_MAX) {
      nTrial = N_TRIAL_MAX;
    }

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
}
