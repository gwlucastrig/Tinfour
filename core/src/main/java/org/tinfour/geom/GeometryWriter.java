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
 * 06/2026  M. Carleton Created
 *
 * Notes:
 *
 *   GeometryWriter is a toolkit-neutral sink for the outline geometry of
 *   Tinfour objects. Core objects describe themselves to a writer in their
 *   own (Cartesian, "model") coordinate system using the declarative
 *   primitives below; a presentation adapter implements the writer and
 *   decides what those coordinates become (a Java2D Path2D, an SVG path
 *   string, an Android Path, a GeoJSON geometry, and so on).
 *
 *   The vocabulary is deliberately that of geometry (polylines and polygons),
 *   not that of a particular graphics toolkit. There is no notion of a
 *   "current point", no moveTo/lineTo state machine, and no winding rule:
 *   polygon holes are passed explicitly so the adapter can render them
 *   however its target requires.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * A toolkit-neutral sink to which Tinfour objects write their outline
 * geometry, in model (Cartesian) coordinates. Implementations adapt the
 * geometry to a particular output, such as a Java2D path, an SVG document, or
 * a vector-data file.
 * <p>
 * The geometry is expressed with two declarative primitives — a polyline
 * (a connected run of points, optionally closed) and a polygon (an outer ring
 * with zero or more interior holes). Coordinates are supplied as flat arrays
 * of alternating x and y values.
 *
 * @see WritableGeometry
 */
public interface GeometryWriter {

  /**
   * Adds a connected sequence of points to the output. The coordinates are
   * supplied as alternating x and y values: {@code [x0, y0, x1, y1, ...]}.
   *
   * @param xy a flat array of coordinate pairs; an array of length less than
   * four (fewer than two points) is ignored
   * @param closed true if the last point should be joined back to the first
   * (a closed ring); false for an open polyline
   */
  void addPolyline(double[] xy, boolean closed);

  /**
   * Adds a polygon, defined by an outer ring and zero or more interior holes,
   * to the output. Each ring is supplied as a flat array of alternating x and
   * y values; the rings are implicitly closed.
   *
   * @param shell the outer ring as alternating x and y coordinate values
   * @param holes the interior rings (holes), or null/empty if the polygon has
   * none; each hole is a flat array of alternating x and y values
   */
  void addPolygon(double[] shell, double[][] holes);
}
