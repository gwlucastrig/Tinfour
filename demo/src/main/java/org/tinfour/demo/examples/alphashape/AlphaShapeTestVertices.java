/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 08/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.demo.examples.alphashape;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;
import org.tinfour.common.Vertex;

/**
 * Utilities to make test vertices for example applications.
 */
class AlphaShapeTestVertices {

  /**
   * Make a list of vertices for test input using a letter form.
   * Although letter forms are not necessarily an ideal match for the
   * assumptions of an alpha shape, they provide a convenient way
   * to generate a test set.
   *
   * @param font the font to be used, should have a point size of at least 72.
   * @param text a non-empty text for input.
   * @param populateInterior populate the interior regions of the letter forms
   * with randomly generated vertices.
   * @return a valid test set.
   */
  static Shape makeVerticesFromText(List<Vertex> vertices, Font font, String text, boolean populateInterior) {
    AffineTransform flipY = new AffineTransform(1, 0, 0, -1, 0, 0);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    TextLayout layout = new TextLayout(text, font, frc);
    Shape shape = layout.getOutline(new AffineTransform());

    PathIterator path = shape.getPathIterator(flipY, 0.25);
    shape = flipY.createTransformedShape(shape);

    double[] d = new double[6];
    int vIndex = 0; // gives each vertex a unique index
    int n;
    double x0 = 0;
    double y0 = 0;
    boolean vPending = false;
    while (!path.isDone()) {
      int flag = path.currentSegment(d);
      switch (flag) {
        case PathIterator.SEG_MOVETO:
          vertices.add(new Vertex(d[0], d[1], 0, vIndex++)); // NOPMD
          x0 = d[0];
          y0 = d[1];
          vPending = true;
          break;
        case PathIterator.SEG_LINETO:
          if(vPending){
             vertices.add(new Vertex(x0, y0, 0, vIndex++));
             vPending = false;
          }
          double x1 = d[0];
          double y1 = d[1];
          double dx = x1 - x0;
          double dy = y1 - y0;
          double dS = Math.sqrt(dx * dx + dy * dy);
          n = (int) Math.ceil(dS / 4);
          for (int i = 1; i < n; i++) {
            double t = (double) i / (double) n;
            double x = t * (x1 - x0) + x0;
            double y = t * (y1 - y0) + y0;
            vertices.add(new Vertex(x, y, 0, vIndex++));
          }
          vertices.add(new Vertex(d[0], d[1], 0, vIndex++));
          x0 = x1;
          y0 = y1;
          break;
        case PathIterator.SEG_CLOSE:
          break;
        default:
          break;
      }
      path.next();
    }

    if (populateInterior) {
      int nD = 100;
      Rectangle2D r2d = shape.getBounds2D();
      Random random = new Random(0);
      double dx = r2d.getWidth() / nD;
      double dy = r2d.getHeight() / nD;
      for (int i = 0; i <= nD; i++) {
        for (int j = 0; j <= nD; j++) {
          double x = i * dx + r2d.getMinX();
          double y = j * dy + r2d.getMinY();
          if (shape.contains(x, y)) {
            if (random.nextDouble() < 0.125) {
              vertices.add(new Vertex(x, y, 0, vIndex++));
            }
          }
        }
      }
    }

    return shape;
  }

}
