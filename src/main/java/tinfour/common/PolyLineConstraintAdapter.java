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
package tinfour.common;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of the IConstraint interface intended to store
 * constraints comprised of a chain of connected line segments.
 * Constraint chains must be non-self-intersecting (except at segment
 * endpoints). The chain must never "fold back" on itself. All segments
 * in the chain must be non-zero-length.
 * Do not use this class for closed polygons.
 */
public abstract class PolyLineConstraintAdapter implements IConstraint, Iterable<Vertex> {

  protected final List<Vertex> list;
  private final Rectangle2D bounds = new Rectangle2D.Double();
  private double x = Double.NaN;
  private double y = Double.NaN;
  protected Object applicationData;
  protected int constraintIndex;
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
  public void setConstraintIndex(int index) {
    constraintIndex = index;
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

}
