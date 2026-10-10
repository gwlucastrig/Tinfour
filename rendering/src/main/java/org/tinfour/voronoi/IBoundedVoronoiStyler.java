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

import java.awt.Graphics2D;

/**
 * Defines methods that supply style information for rendering.
 * The choice of methods for this interface reflects a set of options
 * that are broadly used in rendering applications, but is not
 * exhaustive. In general, the design of this interface attempts to
 * keep the overall number of methods small. However, the rendering
 * of vertices does require a small number of special methods.
 * Methods are provided to allow the application to control
 * whether vertices are labeled, drawn with symbols, or both.
 * A getter method is also supplied for getting the label symbol size.
 * <p>
 * <strong>This interface is under development.</strong> It is
 * not yet ready for use and may be subject to changes.
 */
public interface IBoundedVoronoiStyler {

  /**
   * Indicates whether features of the specified type are enabled for rendering.
   * <p>
   * This method is useful in cases where an application is required to control
   * whether broad classes of feature types are rendered. For example, an
   * application might be configured to draw line features, while suppressing
   * area-fill features.
   *
   * @param type a valid instance of a feature type enumeration
   * @return true if the type is enabled for rendering; otherwise, false.
   */
  boolean isFeatureTypeEnabled(BoundedVoronoiRenderingType type);

  /**
   * Indicates whether the specified type of rendering is enabled for the
   * specified polygon object.
   * <p>
   * This method is useful in cases where rendering of one type to be enabled
   * for a specific polygon while rendering of another type is not. For example,
   * an application may be configured to draw line features for most polygons
   * while area-filling a specific polygon.
   *
   * @param polygon a valid polygon
   * @param type a valid instance of a feature type enumeration
   * @return true if the polygon is enabled for the specified rendering type;
   * otherwise, false.
   */
  boolean isRenderingEnabled(
    ThiessenPolygon polygon,
    BoundedVoronoiRenderingType type);

  /**
   * Tests to see if the polygon is enabled for rendering a symbol
   * at the vertex position and, if it is, returns a vertex symbol
   * that can be used for rendering
   *
   * @param polygon a valid polygon
   * @return if rendering is enabled, a valid symbol instance; otherwise,
   * a null.
   */
  IBoundedVoronoiVertexSymbol getVertexSymbol(ThiessenPolygon polygon);

  /**
   * Called once at the beginning of rendering to set up the Graphics2D surface
   * for rendering. One common use of this routine is to ensure that
   * anti-aliasing is activated, though other settings may also be applied.
   *
   * @param g2d the graphics surface for rendering
   */
  void initializeRendering(Graphics2D g2d);

  /**
   * Applies styling for area fill operations. Styling may include setting a
   * Java Color or Paint, a Composite, clipping, etc.
   *
   * @param g2d the graphics surface for rendering
   * @param polygon a valid polygon instance
   */
  void applyStylingForAreaFill(Graphics2D g2d, ThiessenPolygon polygon);

  /**
   * Applies styling for line drawing operations. Styling may include setting a
   * Java Color or Paint, a Stroke, a Composite, clipping, etc.
   *
   * @param g2d the graphics surface for rendering
   * @param polygon a valid polygon instance
   */
  void applyStylingForLineDrawing(Graphics2D g2d, ThiessenPolygon polygon);

  /**
   * Sets the option for enabling area fill operations
   *
   * @param enabled true if the Theissen polygons (Voronoi cells) are to
   * be area-filled; otherwise, false.
   */
  void setAreaFillEnabled(boolean enabled);

}
