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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of the IConstraint interface intended to store
 * constraints comprised of a polygon. The polygon is allowed to be
 * non-convex, but the segments comprising the polygon must be not
 * intersect except at segment endpoints (e.g. the polygon must be a
 * simple, non intersecting closed loop). All segments
 * in the chain must be non-zero-length.
 * <p>
 * For polygons defining an area, the interior of the area is defined as
 * being bounded by a counter-clockwise polygon. Thus a clockwise polygon
 * would define a "hole" in the area. It is worth noting that this convention
 * is just the opposite of that taken by ESRI's Shapefile format, though
 * it is consistent with conventions used in general computational geometry,
 */
public class PolygonConstraint implements IConstraint {

  private final List<Vertex> list = new ArrayList<>();
  private final Rectangle2D bounds = new Rectangle2D.Double();
  private double x = Double.NaN;
  private double y = Double.NaN;
  private Object applicationData;
  private boolean dataAreaDefinition;
  private int constraintIndex;

  @Override
  public List<Vertex> getVertices() {
    return list;
  }

  @Override
  public void add(Vertex v) {
    if (v.getX() == x && v.getY() == y) {
      return;  // quiently ignore duplicate points
    }
    v.setConstraintMember(true);
    x = v.getX();
    y = v.getY();
    list.add(v);
    bounds.add(v.getX(), v.getY());
  }

  @Override
  public Rectangle2D getBounds() {
    return bounds;
  }

  @Override
  public void complete() {
    if (list.size() < 3) {
      throw new IllegalStateException("Polygon contains fewer than 3 points");
    }
    Vertex a = list.get(0);
    Vertex b = list.get(list.size() - 1);
    if (a.getX() != b.getX() || a.getY() != b.getY()) {
      list.add(a);
    }
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
  public boolean isPolygon() {
    return true;
  }

  @Override
  public void setDefinesDataArea(boolean definesDataArea) {
     dataAreaDefinition = definesDataArea;
  }

  @Override
  public boolean definesDataArea() {
     return this.dataAreaDefinition;
  }

   @Override
  public void setConstraintIndex(int index) {
     constraintIndex = index;
  }

  @Override
  public int getConstraintIndex() {
    return constraintIndex;
  }
}
