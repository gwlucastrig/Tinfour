/*
 * Copyright 2014 Gary W. Lucas.
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
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 03/2014 G. Lucas Created
 * 07/2015 G. Lucas Refactored for edge-based TIN.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.semivirtual;

import tinfour.common.GeometricOperations;
import tinfour.common.Vertex;

/**
 * A representation of a "ear" from Devillers' algorithm for vertex removal
 */
class SemiVirtualDevillersEar {

    int index;
    SemiVirtualDevillersEar prior;
    SemiVirtualDevillersEar next;
    SemiVirtualEdge c;
    SemiVirtualEdge p;
    SemiVirtualEdge n;
    Vertex v0, v1, v2;
    boolean degenerate;

    double score;

    SemiVirtualDevillersEar(int index, SemiVirtualDevillersEar priorEar, SemiVirtualEdge current, SemiVirtualEdge prior) {
        this.index = index;
        this.prior = priorEar;
        if (priorEar != null) {
            priorEar.next = this;
        }
        c = current;
        n = c.getForward();
        p = prior;
        v0 = c.getA();
        v1 = c.getB();
        v2 = n.getB();
    }

    void setReferences(SemiVirtualDevillersEar priorEar, SemiVirtualEdge current, SemiVirtualEdge prior) {
        this.prior = priorEar;
        if (priorEar != null) {
            priorEar.next = this;
        }
        c = current;
        n = c.getForward();
        p = prior;
        v0 = c.getA();
        v1 = c.getB();
        v2 = n.getB();
    }

    void computeScore(GeometricOperations geoOp, Vertex vRemove) {
        degenerate = false;
        if (v0 == null || v1 == null || v2 == null) {
            // one of the vertices is the ghost
            score = Double.POSITIVE_INFINITY;
            return;
        }

        double ax = v0.x;
        double ay = v0.y;
        double bx = v1.x;
        double by = v1.y;
        double cx = v2.x;
        double cy = v2.y;
        double orientation = geoOp.orientation(ax, ay, bx, by, cx, cy);
        if (orientation <= 0) {
            degenerate = true;
            // 3 points are oriented clockwise, indicating a
            // concavity. we can short-circuit the calculation
            score = Double.POSITIVE_INFINITY;
            return;
        }

        double dx = vRemove.x;
        double dy = vRemove.y;
        double inCircle = geoOp.inCircle(ax, ay, bx, by, cx, cy, dx, dy);

        score = inCircle / orientation;
    }

    void dispose() {
        index = -1;
        v0 = null;
        v1 = null;
        v2 = null;
        c = null;
        p = null;
        n = null;
        prior = null;
        next = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(48);
        sb.append("ear v[]=");
        sb.append((v0 == null ? "null," : v0.getIndex() + ","));
        sb.append((v1 == null ? "null," : v1.getIndex() + ","));
        sb.append((v2 == null ? "null," : v2.getIndex() + " "));
        sb.append("     ");
        sb.append("index=").append(Integer.toString(index));
        sb.append(",  score=");
        sb.append(Double.toString(score));
        return sb.toString();
    }

  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  @Override
  public boolean equals(Object o) {
    if (o instanceof SemiVirtualDevillersEar) {
      return (o == this);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 89 * hash + this.index;
    return hash;
  }
}
