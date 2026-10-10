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
 *   An implementation of the toolkit-neutral GeometryWriter SPI that builds an
 *   SVG path-data string ("d" attribute) from the geometry it receives. It has
 *   no dependency on java.awt; coordinates are emitted in their supplied
 *   (model) coordinate system, and any required projection is handled at the
 *   document level (see SvgDocumentWriter).
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svg;

import org.tinfour.geom.GeometryWriter;

/**
 * A {@link GeometryWriter} that accumulates the geometry it receives into an
 * SVG path-data string suitable for the {@code d} attribute of an SVG
 * {@code <path>} element. Coordinates are written verbatim (in model
 * coordinates); the holes of a polygon are emitted as additional subpaths so
 * that an even-odd fill rule renders them correctly.
 */
public class SvgPathWriter implements GeometryWriter {

  private final StringBuilder d = new StringBuilder();

  @Override
  public void addPolyline(double[] xy, boolean closed) {
    if (xy == null || xy.length < 4) {
      return;
    }
    appendRing(xy, closed);
  }

  @Override
  public void addPolygon(double[] shell, double[][] holes) {
    if (shell != null && shell.length >= 4) {
      appendRing(shell, true);
    }
    if (holes != null) {
      for (double[] hole : holes) {
        if (hole != null && hole.length >= 4) {
          appendRing(hole, true);
        }
      }
    }
  }

  private void appendRing(double[] xy, boolean closed) {
    if (d.length() > 0) {
      d.append(' ');
    }
    d.append('M').append(num(xy[0])).append(' ').append(num(xy[1]));
    for (int i = 1; i < xy.length / 2; i++) {
      d.append(" L").append(num(xy[i * 2])).append(' ').append(num(xy[i * 2 + 1]));
    }
    if (closed) {
      d.append(" Z");
    }
  }

  private static String num(double v) {
    // Double.toString uses '.' regardless of locale and avoids grouping
    // separators, which is what SVG requires.
    return Double.toString(v);
  }

  /**
   * Gets the accumulated SVG path data.
   *
   * @return a valid, possibly empty, string suitable for an SVG {@code d}
   * attribute
   */
  public String getPathData() {
    return d.toString();
  }
}
