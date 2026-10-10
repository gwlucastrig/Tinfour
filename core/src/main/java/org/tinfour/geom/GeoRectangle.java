/*
 * Copyright 2025 Gary W. Lucas.
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
 * ------   ---------   -------------------------------------------------
 * 06/2026  M. Carleton Created as part of removing java.awt dependencies
 *
 * Notes:
 *
 *   This class provides a lightweight, double-precision replacement for
 *   java.awt.geom.Rectangle2D.Double so that the Tinfour core module does
 *   not depend on the AWT graphics package (see GitHub issue #136). The
 *   method semantics deliberately mirror those of Rectangle2D in order to
 *   preserve the numerical behavior of code that was written against it.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * A mutable, double-precision axis-aligned rectangle. This class is a
 * lightweight, dependency-free replacement for {@code java.awt.geom.Rectangle2D}
 * (specifically its {@code Double} variant). The semantics of its methods are
 * intended to match those of the AWT class.
 */
public class GeoRectangle {

  /**
   * The x coordinate of the upper-left corner of the rectangle.
   */
  public double x;

  /**
   * The y coordinate of the upper-left corner of the rectangle.
   */
  public double y;

  /**
   * The width of the rectangle.
   */
  public double width;

  /**
   * The height of the rectangle.
   */
  public double height;

  /**
   * Constructs a rectangle with all coordinates and dimensions set to zero.
   */
  public GeoRectangle() {
  }

  /**
   * Constructs a rectangle with the specified position and dimensions.
   *
   * @param x the x coordinate of the upper-left corner
   * @param y the y coordinate of the upper-left corner
   * @param width the width of the rectangle
   * @param height the height of the rectangle
   */
  public GeoRectangle(double x, double y, double width, double height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  /**
   * Gets the x coordinate of the upper-left corner of the rectangle.
   *
   * @return the x coordinate
   */
  public double getX() {
    return x;
  }

  /**
   * Gets the y coordinate of the upper-left corner of the rectangle.
   *
   * @return the y coordinate
   */
  public double getY() {
    return y;
  }

  /**
   * Gets the width of the rectangle.
   *
   * @return the width
   */
  public double getWidth() {
    return width;
  }

  /**
   * Gets the height of the rectangle.
   *
   * @return the height
   */
  public double getHeight() {
    return height;
  }

  /**
   * Gets the smaller x coordinate of the rectangle.
   *
   * @return the minimum x coordinate
   */
  public double getMinX() {
    return x;
  }

  /**
   * Gets the smaller y coordinate of the rectangle.
   *
   * @return the minimum y coordinate
   */
  public double getMinY() {
    return y;
  }

  /**
   * Gets the larger x coordinate of the rectangle.
   *
   * @return the maximum x coordinate
   */
  public double getMaxX() {
    return x + width;
  }

  /**
   * Gets the larger y coordinate of the rectangle.
   *
   * @return the maximum y coordinate
   */
  public double getMaxY() {
    return y + height;
  }

  /**
   * Gets the x coordinate of the center of the rectangle.
   *
   * @return the center x coordinate
   */
  public double getCenterX() {
    return x + width / 2.0;
  }

  /**
   * Gets the y coordinate of the center of the rectangle.
   *
   * @return the center y coordinate
   */
  public double getCenterY() {
    return y + height / 2.0;
  }

  /**
   * Indicates whether the rectangle is empty (has a non-positive width or
   * height).
   *
   * @return true if the rectangle is empty; otherwise, false
   */
  public boolean isEmpty() {
    return (width <= 0.0) || (height <= 0.0);
  }

  /**
   * Sets the position and dimensions of this rectangle.
   *
   * @param x the x coordinate of the upper-left corner
   * @param y the y coordinate of the upper-left corner
   * @param width the width of the rectangle
   * @param height the height of the rectangle
   */
  public void setRect(double x, double y, double width, double height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  /**
   * Adds a point to this rectangle, enlarging it as necessary so that it
   * encloses the point. The semantics match {@code Rectangle2D.add(double,double)},
   * which does not treat an empty rectangle specially.
   *
   * @param newx the x coordinate of the point
   * @param newy the y coordinate of the point
   */
  public void add(double newx, double newy) {
    double x1 = Math.min(getMinX(), newx);
    double x2 = Math.max(getMaxX(), newx);
    double y1 = Math.min(getMinY(), newy);
    double y2 = Math.max(getMaxY(), newy);
    setRect(x1, y1, x2 - x1, y2 - y1);
  }

  /**
   * Adds another rectangle to this rectangle, enlarging it as necessary so
   * that it encloses the union of the two rectangles. The semantics match
   * {@code Rectangle2D.add(Rectangle2D)}.
   *
   * @param r a valid rectangle
   */
  public void add(GeoRectangle r) {
    double x1 = Math.min(getMinX(), r.getMinX());
    double x2 = Math.max(getMaxX(), r.getMaxX());
    double y1 = Math.min(getMinY(), r.getMinY());
    double y2 = Math.max(getMaxY(), r.getMaxY());
    setRect(x1, y1, x2 - x1, y2 - y1);
  }

  /**
   * Indicates whether this rectangle intersects the specified rectangle. The
   * semantics match {@code Rectangle2D.intersects}.
   *
   * @param r a valid rectangle
   * @return true if the rectangles intersect; otherwise, false
   */
  public boolean intersects(GeoRectangle r) {
    if (isEmpty() || r.width <= 0 || r.height <= 0) {
      return false;
    }
    double x0 = getX();
    double y0 = getY();
    return (r.getX() + r.getWidth() > x0
            && r.getY() + r.getHeight() > y0
            && r.getX() < x0 + getWidth()
            && r.getY() < y0 + getHeight());
  }

  /**
   * Indicates whether this rectangle contains the specified point. The
   * semantics match {@code Rectangle2D.contains(double,double)}.
   *
   * @param px the x coordinate of the point
   * @param py the y coordinate of the point
   * @return true if the point lies within the rectangle; otherwise, false
   */
  public boolean contains(double px, double py) {
    double x0 = getX();
    double y0 = getY();
    return (px >= x0
            && py >= y0
            && px < x0 + getWidth()
            && py < y0 + getHeight());
  }

  /**
   * Indicates whether this rectangle entirely contains the specified
   * rectangle. The semantics match {@code Rectangle2D.contains(Rectangle2D)}.
   *
   * @param r a valid rectangle
   * @return true if the rectangle is entirely contained; otherwise, false
   */
  public boolean contains(GeoRectangle r) {
    if (isEmpty() || r.width <= 0 || r.height <= 0) {
      return false;
    }
    double x0 = getX();
    double y0 = getY();
    return (r.getX() >= x0
            && r.getY() >= y0
            && (r.getX() + r.getWidth()) <= x0 + getWidth()
            && (r.getY() + r.getHeight()) <= y0 + getHeight());
  }

  @Override
  public String toString() {
    return "GeoRectangle[x=" + x + ", y=" + y
            + ", w=" + width + ", h=" + height + "]";
  }
}
