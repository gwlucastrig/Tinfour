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
 * 09/2018  G. Lucas  Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.voronoi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.Arrays;

/**
 * A default implementation of the styler interface.
 */
public class BoundedVoronoiStylerDefault implements IBoundedVoronoiStyler {

  private static final Color defaultPalette[] = {
    Color.YELLOW,
    Color.MAGENTA,
    Color.ORANGE,
    Color.LIGHT_GRAY,
    Color.PINK,
    Color.GREEN.brighter(),
    Color.RED,
    Color.BLUE,};

  private static final Stroke thinStroke = new BasicStroke(1.0f);

  boolean[] typeEnabled
          = new boolean[BoundedVoronoiRenderingType.values().length];

  boolean vertexLabelingEnabled = true;
  boolean vertexSymbolEnabled = true;
  double vertexSymbolSize = 9.0;
  Font vertexLabelingFont = new Font("Dialog", Font.BOLD, 12);

  boolean areaFillEnabled = true;
  boolean lineDrawEnabled = true;
  Color[] palette = defaultPalette;
  Color lineColor = Color.black;
  Color vertexColor = Color.black;
  Stroke lineStroke = thinStroke;
  Stroke borderStroke = new BasicStroke(2.0f);

  /**
   * Standard constructor.
   */
  public BoundedVoronoiStylerDefault() {
    Arrays.fill(typeEnabled, true);
  }

  @Override
  public boolean isFeatureTypeEnabled(BoundedVoronoiRenderingType type) {
    if (type == null) {
      return false;
    }
    return typeEnabled[type.ordinal()];
  }

  @Override
  public boolean isRenderingEnabled(ThiessenPolygon polygon,
          BoundedVoronoiRenderingType type) {
    if (polygon == null || type == null) {
      return false;
    }
    return typeEnabled[type.ordinal()];
  }

  @Override
  public void initializeRendering(Graphics2D g) {
    g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(lineColor);
  }

  @Override
  public void applyStylingForAreaFill(Graphics2D g, ThiessenPolygon polygon) {
    int index = polygon.getVertex().getAuxiliaryIndex() % palette.length;
    g.setColor(palette[index]);
    g.setStroke(thinStroke);
  }

  @Override
  public void applyStylingForLineDrawing(Graphics2D g, ThiessenPolygon polygon) {
    g.setColor(lineColor);
    g.setStroke(lineStroke);
  }

  /**
   * Set the color for rendering lines
   *
   * @param color a valid color object
   */
  public void setLineColor(Color color) {
    if (color == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }

    this.lineColor = color;
  }

  /**
   * Set the stroke for rendering lines
   *
   * @param stroke a valid stroke object
   */
  public void setLineStroke(Stroke stroke) {
    if (stroke == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }

    this.lineStroke = stroke;

  }

  /**
   * Set the area fill rendering to use a single, uniform color or paint for all
   * polygons.
   *
   * @param color a valid Color or Paint object
   */
  public void setAreaFillColor(Color color) {
    if (color == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }

    palette = new Color[1];
    palette[0] = color;

  }

  /**
   * Set the area fill rendering to use a single, uniform color or paint for all
   * polygons.
   *
   * @param paletteSpecification a valid array of Color or Paint objects
   */
  public void setAreaFillPalette(Color[] paletteSpecification) {
    if (paletteSpecification == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }
    if (paletteSpecification.length == 0) {
      throw new IllegalArgumentException("Zero-length array not supported");
    }

    palette = new Color[paletteSpecification.length];
    System.arraycopy(paletteSpecification, 0,
            palette, 0,
            paletteSpecification.length);

  }

    /**
   * Set the color for rendering vertices
   *
   * @param color a valid color object
   */
  public void setVertexColor(Color color) {
    if (color == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }

    this.vertexColor = color;
  }

  /**
   * Sets the font for labeling vertices
   *
   * @param font a valid font
   */
  public void setVertexLabelingFont(Font font) {
    if (font == null) {
      throw new IllegalArgumentException("Null argument not supported");
    }
    vertexLabelingFont = font;
  }

  /**
   * Specifies whether vertex labeling is enabled.
   *
   * @param enabled true if vertices are to be labeled; otherwise, false
   */
  public void setVertexLabelingEnabled(boolean enabled) {
    vertexLabelingEnabled = enabled;
  }

  /**
   * Sets the size for rendering vertices. By default, vertices are rendered as
   * a filled circle using the line color specification. The size is the
   * diameter (width and height) of the circle. Applications using other
   * presentations for vertices are free to interpret this value as appropriate.
   *
   * @param vertexSymbolSize a value greater than zero
   */
  public void setVertexSymbolSize(double vertexSymbolSize) {
    if (vertexSymbolSize <= 0) {
      throw new IllegalArgumentException("Negative and zero sizes not supported");
    }
    this.vertexSymbolSize = vertexSymbolSize;
  }

  /**
   * Specifies whether vertex symbols are to be rendered.
   *
   * @param enabled true if vertex symbols are to be rendered; otherwise, false
   */
  public void setVertexSymbolEnabled(boolean enabled) {
    vertexSymbolEnabled = enabled;
      typeEnabled[BoundedVoronoiRenderingType.Vertex.ordinal()] = enabled;
  }

  /**
   * Tests to see if the polygon is enabled for rendering a symbol at the vertex
   * position and, if it is, returns a vertex symbol that can be used for
   * rendering.
   * <p>
   * @param polygon a valid polygon
   * @return if rendering is enabled, a valid symbol instance; otherwise, a
   * null.
   */
  @Override
  public IBoundedVoronoiVertexSymbol getVertexSymbol(ThiessenPolygon polygon) {
    if (isRenderingEnabled(polygon, BoundedVoronoiRenderingType.Vertex)) {
      BoundedVoronoiVertexSymbol symbol = new BoundedVoronoiVertexSymbol(vertexSymbolSize);
              symbol.setColor(vertexColor);
      if (this.vertexLabelingEnabled) {
        symbol.setFont(vertexLabelingFont);
        symbol.setLabel(Integer.toString(polygon.getVertex().getIndex()));
      }
      return symbol;
    }
    return null;
  }

  /**
   * Sets the option for enabling area fill operations
   * @param enabled true if the Theissen polygons (Voronoi cells) are to
   * be area-filled; otherwise, false.
   */
  @Override
  public void setAreaFillEnabled(boolean enabled){
    typeEnabled[BoundedVoronoiRenderingType.Area.ordinal()] = enabled;
  }

  /**
   * Indicates whether area-fill operations are enabled
   * @return true if the Theissen polygons (Voronoi cells) are to
   * be area-filled; otherwise, false.
   */
  public boolean isAreaFillEnabled(){
    return typeEnabled[BoundedVoronoiRenderingType.Area.ordinal()];
  }
}
