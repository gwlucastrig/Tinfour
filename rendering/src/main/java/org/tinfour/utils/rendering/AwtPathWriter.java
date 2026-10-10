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
 *   An implementation of the toolkit-neutral GeometryWriter SPI that builds a
 *   java.awt.geom.Path2D for rendering with Java2D. An optional affine
 *   transform maps the model (Cartesian) coordinates supplied by the core
 *   geometry to device (pixel) coordinates.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.rendering;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import org.tinfour.geom.GeometryWriter;
import org.tinfour.geom.WritableGeometry;

/**
 * A {@link GeometryWriter} that accumulates the geometry it receives into a
 * {@link java.awt.geom.Path2D} for rendering with Java2D. An optional affine
 * transform maps incoming model (Cartesian) coordinates to device (pixel)
 * coordinates as they are written.
 * <p>
 * The path uses the even-odd winding rule, so polygons supplied with interior
 * holes (via {@link #addPolygon(double[], double[][])}) render with the holes
 * removed.
 */
public class AwtPathWriter implements GeometryWriter {

  private final Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
  private final AffineTransform transform;

  /**
   * Constructs a writer that accumulates geometry in its supplied (model)
   * coordinates, applying no transform.
   */
  public AwtPathWriter() {
    this.transform = null;
  }

  /**
   * Constructs a writer that maps incoming model coordinates to device
   * coordinates with the specified transform.
   *
   * @param transform an affine transform, or null to apply none
   */
  public AwtPathWriter(AffineTransform transform) {
    this.transform = transform;
  }

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
    double[] c = xy;
    if (transform != null) {
      c = new double[xy.length];
      transform.transform(xy, 0, c, 0, xy.length / 2);
    }
    path.moveTo(c[0], c[1]);
    for (int i = 1; i < c.length / 2; i++) {
      path.lineTo(c[i * 2], c[i * 2 + 1]);
    }
    if (closed) {
      path.closePath();
    }
  }

  /**
   * Gets the path accumulated from the geometry written so far.
   *
   * @return a valid, possibly empty, path
   */
  public Path2D getPath2D() {
    return path;
  }

  /**
   * Convenience method: builds a {@link Path2D} from a drawable Tinfour object,
   * mapping its model coordinates to device coordinates with the specified
   * transform.
   *
   * @param g a drawable geometry (constraint, triangle, contour, …)
   * @param transform an affine transform, or null to apply none
   * @return a valid path
   */
  public static Path2D toPath2D(WritableGeometry g, AffineTransform transform) {
    AwtPathWriter w = new AwtPathWriter(transform);
    g.writeTo(w);
    return w.getPath2D();
  }
}
