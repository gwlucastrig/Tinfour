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
 *   Assembles a complete SVG document from Tinfour geometry. The document's
 *   viewBox is derived from a model-coordinate bounding rectangle, and a
 *   single group transform performs the Cartesian-to-SVG vertical flip so that
 *   the individual paths can be emitted in model coordinates. This class has no
 *   dependency on java.awt.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svg;

import java.io.IOException;
import org.tinfour.geom.GeoRectangle;
import org.tinfour.geom.WritableGeometry;

/**
 * Assembles a complete SVG document from Tinfour geometry, writing to any
 * {@link Appendable} (a file writer, a string builder, …). The document
 * {@code viewBox} is taken from a model-coordinate bounds rectangle and a
 * single group transform flips the vertical axis (Cartesian y-up to SVG
 * y-down), so paths added through {@link #addPath} may be supplied in model
 * coordinates.
 * <p>
 * Typical use: {@link #begin()}, one or more {@link #addPath}, then
 * {@link #finish()}.
 */
public class SvgDocumentWriter {

  private final Appendable out;
  private final GeoRectangle bounds;
  private final int width;
  private final int height;

  /**
   * Constructs a document writer for the specified output, model bounds, and
   * pixel dimensions.
   *
   * @param out the destination for the SVG text
   * @param modelBounds the bounds of the geometry, in model coordinates
   * @param width the nominal pixel width of the document
   * @param height the nominal pixel height of the document
   */
  public SvgDocumentWriter(Appendable out, GeoRectangle modelBounds,
          int width, int height) {
    this.out = out;
    this.bounds = modelBounds;
    this.width = width;
    this.height = height;
  }

  /**
   * Writes the document header, the opening {@code <svg>} element (with a
   * viewBox derived from the model bounds), and an enclosing group whose
   * transform flips the vertical axis.
   *
   * @throws IOException if an error occurs while writing
   */
  public void begin() throws IOException {
    double minX = bounds.getMinX();
    double maxY = bounds.getMaxY();
    double w = bounds.getWidth();
    double h = bounds.getHeight();
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
            .append("width=\"").append(Integer.toString(width)).append("\" ")
            .append("height=\"").append(Integer.toString(height)).append("\" ")
            .append("viewBox=\"")
            .append(num(minX)).append(' ').append(num(-maxY)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append("\">\n");
    // Cartesian (y-up) to SVG (y-down): reflect about the x-axis. Combined with
    // the viewBox above, model y=maxY maps to the top of the document.
    out.append("<g transform=\"scale(1,-1)\">\n");
  }

  /**
   * Adds a drawable geometry as an SVG {@code <path>} element with the
   * even-odd fill rule and the specified presentation attributes.
   *
   * @param g a drawable geometry (constraint, triangle, contour region, …)
   * @param styleAttributes SVG presentation attributes, e.g.
   * {@code fill="#cccccc" stroke="black" stroke-width="0.05"}
   * @throws IOException if an error occurs while writing
   */
  public void addPath(WritableGeometry g, String styleAttributes)
          throws IOException {
    SvgPathWriter w = new SvgPathWriter();
    g.writeTo(w);
    String d = w.getPathData();
    if (d.isEmpty()) {
      return;
    }
    out.append("<path d=\"").append(d).append("\" fill-rule=\"evenodd\"");
    if (styleAttributes != null && !styleAttributes.isEmpty()) {
      out.append(' ').append(styleAttributes);
    }
    out.append("/>\n");
  }

  /**
   * Closes the enclosing group and the {@code <svg>} element.
   *
   * @throws IOException if an error occurs while writing
   */
  public void finish() throws IOException {
    out.append("</g>\n</svg>\n");
  }

  private static String num(double v) {
    return Double.toString(v);
  }
}
