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
 * 10/2016  G. Lucas     Created
 * 01/2016  G. Lucas     Fixed bounds bug reported by Martin Janda
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of the IConstraint interface intended to store constraints
 * comprised of a chain of connected line segments. Constraint chains must be
 * non-self-intersecting (except at segment endpoints). The chain must never
 * "fold back" on itself. All segments in the chain must be non-zero-length. Do
 * not use this class for closed polygons.
 */
public abstract class PolyLineConstraintAdapter
        implements IConstraint, Iterable<Vertex> {

  protected final List<Vertex> list;
  private final Rectangle2D bounds = new Rectangle2D.Double();
  private double x = Double.NaN;
  private double y = Double.NaN;
  protected Object applicationData;
  protected int constraintIndex;
  protected IQuadEdge constraintLinkingEdge;
  protected IIncrementalTin maintainingTin;
  protected boolean isComplete;
  protected double length;

  PolyLineConstraintAdapter() {
    list = new ArrayList<>();
  }

  PolyLineConstraintAdapter(List<Vertex> vList) {
    list = new ArrayList<>(vList.size() + 1);
    for (Vertex v : vList) {
      add(v);
    }
  }

  @Override
  public List<Vertex> getVertices() {
    return list;
  }

  @Override
  public final void add(Vertex v) {
    isComplete = false;

    double vx = v.getX();
    double vy = v.getY();
    if (list.isEmpty()) {
      bounds.setRect(vx, vy, 0, 0);
    } else if (vx == x && vy == y) {
      return;  // quietly ignore duplicate points
    } else {
      length += v.getDistance(x, y);
      bounds.add(vx, vy);
    }

    x = vx;
    y = vy;
    v.setConstraintMember(true);
    list.add(v);
  }

  @Override
  public Rectangle2D getBounds() {
    return bounds;

  }

  @Override
  public void setApplicationData(Object applicationData) {
    this.applicationData = applicationData;
  }

  @Override
  public Object getApplicationData() {
    return applicationData;
  }

  @Override
  public void setConstraintIndex(IIncrementalTin tin, int index) {
    constraintIndex = index;
    maintainingTin = tin;
  }

  @Override
  public int getConstraintIndex() {
    return constraintIndex;
  }

  @Override
  public double getLength() {
    return length;
  }

  @Override
  public Iterator<Vertex> iterator() {
    return list.iterator();
  }

  @Override
  public IQuadEdge getConstraintLinkingEdge() {
    return constraintLinkingEdge;
  }

  @Override
  public void setConstraintLinkingEdge(IQuadEdge edge) {
    constraintLinkingEdge = edge;
  }

  @Override
  public IIncrementalTin getManagingTin() {
    return maintainingTin;
  }

  @Override
  public boolean isPointInsideConstraint(double x, double y) {
    if (!this.isPolygon()) {
      return false;
    }
    if (!isComplete) {
      return false;
    }
    int rCross = 0;
    int lCross = 0;
    Vertex v0 = list.get(list.size() - 1);
    for (Vertex v1 : list) {

      double x0 = v0.getX();
      double y0 = v0.getY();
      double x1 = v1.getX();
      double y1 = v1.getY();
      v0 = v1;

      double yDelta = y0 - y1;
      if (y1 > y != y0 > y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest > x) {
          rCross++;
        }
      }
      if (y1 < y != y0 < y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest < x) {
          lCross++;
        }
      }

    }

    // (rCross%2) != (lCross%2)
    if (((rCross ^ lCross) & 0x01) == 1) {
      return false; // on border
    } else if ((rCross & 0x01) == 1) {
      return true; // unambiguously inside
    }
    return false; // unambiguously outside
  }

  static int nDense;
  @Override
  public void densify(double threshold) {
    List<Vertex> vList = new ArrayList<>();
    if (list.size() < 2) {
      return;
    }
    nDense++;
    vList.add(list.get(0));
    for (int i0 = 0; i0 < list.size() - 1; i0++) {
      Vertex v0 = list.get(i0);
      Vertex v1 = list.get(i0 + 1);
      double d = v0.getDistance(v1);
      int n = 0;
      if (d > threshold) {
        n = (int) Math.floor(d / threshold)+1;
        for (int i = 1; i < n; i++) {
          double t = (double) i / (double) n;
          double x0 = v0.getX();
          double y0 = v0.getY();
          double z0 = v0.getZ();
          double x1 = v1.getX();
          double y1 = v1.getY();
          double z1 = v1.getZ();
          double x = t * (x1 - x0) + x0;
          double y = t * (y1 - y0) + y0;
          double z = t * (z1 - z0) + z0;
          Vertex v = new Vertex(x, y, z, vList.size());
          v.setSynthetic(true);
          v.setConstraintMember(true);
          vList.add(v);
        }
      }
      vList.add(v1);
    }

    list.clear();
    list.addAll(vList);
  }
}
