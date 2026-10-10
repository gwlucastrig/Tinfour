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
 *   The Tinfour core module exposes geometry through the dependency-free
 *   org.tinfour.geom types (see GitHub issue #136). This utility bridges
 *   those types to the java.awt.geom equivalents required for rendering on
 *   the desktop JVM. It is intended for use by the rendering module and by
 *   downstream graphics code (the demo and svm modules).
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.rendering;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.tinfour.geom.GeoAffineTransform;
import org.tinfour.geom.GeoLine;
import org.tinfour.geom.GeoPoint;
import org.tinfour.geom.GeoRectangle;

/**
 * Provides static methods for converting between the dependency-free
 * {@code org.tinfour.geom} types produced by the Tinfour core module and the
 * {@code java.awt.geom} types used for rendering.
 */
public final class AwtGeometryAdapter {

  private AwtGeometryAdapter() {
    // a utility class; no instances permitted
  }

  /**
   * Converts a Tinfour rectangle to a {@code java.awt.geom.Rectangle2D}.
   *
   * @param r a valid rectangle, or null
   * @return an equivalent AWT rectangle, or null if the input was null
   */
  public static Rectangle2D toRectangle2D(GeoRectangle r) {
    if (r == null) {
      return null;
    }
    return new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight());
  }

  /**
   * Converts a Tinfour affine transform to a {@code java.awt.geom.AffineTransform}.
   *
   * @param a a valid transform, or null
   * @return an equivalent AWT transform, or null if the input was null
   */
  public static AffineTransform toAffineTransform(GeoAffineTransform a) {
    if (a == null) {
      return null;
    }
    return new AffineTransform(
            a.getScaleX(), a.getShearY(), a.getShearX(),
            a.getScaleY(), a.getTranslateX(), a.getTranslateY());
  }

  /**
   * Converts a {@code java.awt.geom.AffineTransform} to a Tinfour affine
   * transform.
   *
   * @param a a valid transform, or null
   * @return an equivalent Tinfour transform, or null if the input was null
   */
  public static GeoAffineTransform toGeoAffineTransform(AffineTransform a) {
    if (a == null) {
      return null;
    }
    return new GeoAffineTransform(
            a.getScaleX(), a.getShearY(), a.getShearX(),
            a.getScaleY(), a.getTranslateX(), a.getTranslateY());
  }

  /**
   * Converts a Tinfour line segment to a {@code java.awt.geom.Line2D}.
   *
   * @param line a valid line, or null
   * @return an equivalent AWT line, or null if the input was null
   */
  public static Line2D toLine2D(GeoLine line) {
    if (line == null) {
      return null;
    }
    return new Line2D.Double(
            line.getX1(), line.getY1(), line.getX2(), line.getY2());
  }

  /**
   * Converts a Tinfour point to a {@code java.awt.geom.Point2D}.
   *
   * @param p a valid point, or null
   * @return an equivalent AWT point, or null if the input was null
   */
  public static Point2D toPoint2D(GeoPoint p) {
    if (p == null) {
      return null;
    }
    return new Point2D.Double(p.getX(), p.getY());
  }
}
