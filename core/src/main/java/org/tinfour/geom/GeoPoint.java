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
 *   Lightweight, double-precision replacement for java.awt.geom.Point2D.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * A mutable, double-precision point. This class is a lightweight,
 * dependency-free replacement for {@code java.awt.geom.Point2D}.
 */
public class GeoPoint {

  /**
   * The x coordinate of the point.
   */
  public double x;

  /**
   * The y coordinate of the point.
   */
  public double y;

  /**
   * Constructs a point at the origin.
   */
  public GeoPoint() {
  }

  /**
   * Constructs a point at the specified coordinates.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   */
  public GeoPoint(double x, double y) {
    this.x = x;
    this.y = y;
  }

  /**
   * Gets the x coordinate of the point.
   *
   * @return the x coordinate
   */
  public double getX() {
    return x;
  }

  /**
   * Gets the y coordinate of the point.
   *
   * @return the y coordinate
   */
  public double getY() {
    return y;
  }

  /**
   * Sets the location of the point.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   */
  public void setLocation(double x, double y) {
    this.x = x;
    this.y = y;
  }

  @Override
  public String toString() {
    return "GeoPoint[" + x + ", " + y + "]";
  }
}
