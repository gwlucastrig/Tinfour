/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * Date Name Description
 * ------   --------- -------------------------------------------------
 * 10/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;

/**
 * The default renderer for vertices in a drawing of a BoundedVoronoiDiagram
 * instance.
 */
public class BoundedVoronoiVertexSymbol implements IBoundedVoronoiVertexSymbol {

  String label;
  Font font;
  Color color;
  double vertexSymbolSize;

  public BoundedVoronoiVertexSymbol() {
    vertexSymbolSize = 7;
  }

  public BoundedVoronoiVertexSymbol(double vertexSymbolSize) {
    if (vertexSymbolSize <= 0) {
      throw new IllegalArgumentException("Negative and zero sizes not supported");
    }
    this.vertexSymbolSize = vertexSymbolSize;
  }

  /**
   * Sets the label for this symbol
   *
   * @param label a valid, non-empty string; or a null if no label is required.
   */
  public void setLabel(String label) {
    this.label = label;
  }

  /**
   * Sets the font to be used for labeling. If not set, the currently color for
   * the graphics surface will be used.
   *
   * @param font a valid font; or a null
   */
  public void setFont(Font font) {
    this.font = font;
  }

  /**
   * Set the color for this symbol. If not set, the currently color for the
   * graphics surface will be used.
   *
   * @param color a valid color; or a null.
   */
  public void setColor(Color color) {
    this.color = color;
  }

  /**
   * Draw the icon for a vertex positioned at the indicated coordinates
   *
   * @param g a valid Graphics surface
   * @param x the user (pixel) x coordinate of the vertex
   * @param y the user (pixel) y coordinate of the vertex
   */
  @Override
  public void draw(Graphics g, double x, double y) {
    Graphics2D g2d = (Graphics2D) g;
    if (color == null) {
      color = g2d.getColor();
    }
    if (font == null) {
      font = g2d.getFont();
    }
    Ellipse2D e2d = new Ellipse2D.Double(
      x - vertexSymbolSize / 2,
      y - vertexSymbolSize / 3,
      vertexSymbolSize,
      vertexSymbolSize);
    g2d.setStroke(new BasicStroke(1.0f));
    g2d.fill(e2d);
    g2d.draw(e2d);
    if (label != null && !label.isEmpty() && font != null) {
      FontRenderContext frc = new FontRenderContext(null, true, true);
      TextLayout layout = new TextLayout(label, font, frc);
      double s = (vertexSymbolSize + 1) / 2;
      layout.draw(g2d,
        (float) (x + s),
        (float) (y - s));
    }
  }
}
