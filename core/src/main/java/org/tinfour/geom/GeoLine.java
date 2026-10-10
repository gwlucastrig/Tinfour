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
 *   Lightweight, double-precision replacement for java.awt.geom.Line2D.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * A mutable, double-precision line segment defined by two endpoints. This
 * class is a lightweight, dependency-free replacement for
 * {@code java.awt.geom.Line2D}.
 */
public class GeoLine {

  private double x1;
  private double y1;
  private double x2;
  private double y2;

  /**
   * Constructs a line segment with both endpoints at the origin.
   */
  public GeoLine() {
  }

  /**
   * Constructs a line segment with the specified endpoints.
   *
   * @param x1 the x coordinate of the first endpoint
   * @param y1 the y coordinate of the first endpoint
   * @param x2 the x coordinate of the second endpoint
   * @param y2 the y coordinate of the second endpoint
   */
  public GeoLine(double x1, double y1, double x2, double y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  /**
   * Sets the endpoints of the line segment.
   *
   * @param x1 the x coordinate of the first endpoint
   * @param y1 the y coordinate of the first endpoint
   * @param x2 the x coordinate of the second endpoint
   * @param y2 the y coordinate of the second endpoint
   */
  public void setLine(double x1, double y1, double x2, double y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  /**
   * Gets the x coordinate of the first endpoint.
   *
   * @return the x coordinate
   */
  public double getX1() {
    return x1;
  }

  /**
   * Gets the y coordinate of the first endpoint.
   *
   * @return the y coordinate
   */
  public double getY1() {
    return y1;
  }

  /**
   * Gets the x coordinate of the second endpoint.
   *
   * @return the x coordinate
   */
  public double getX2() {
    return x2;
  }

  /**
   * Gets the y coordinate of the second endpoint.
   *
   * @return the y coordinate
   */
  public double getY2() {
    return y2;
  }

  @Override
  public String toString() {
    return "GeoLine[(" + x1 + ", " + y1 + ")-(" + x2 + ", " + y2 + ")]";
  }
}
