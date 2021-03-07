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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A base implementation of the styler interface provided as a convenience.
 */
public class BoundedVoronoiStylerAdapter implements IBoundedVoronoiStyler {

  @Override
  public boolean isFeatureTypeEnabled(BoundedVoronoiRenderingType type) {
    return false;
  }

  @Override
  public boolean isRenderingEnabled(ThiessenPolygon polygon, BoundedVoronoiRenderingType type) {
    return true;
  }


  @Override
  public void initializeRendering(Graphics2D g2d) {
            g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

  }

  @Override
  public void applyStylingForAreaFill(Graphics2D g2d, ThiessenPolygon polygon) {
    // no action implemented at this time
  }

  @Override
  public void applyStylingForLineDrawing(Graphics2D g2d, ThiessenPolygon polygon) {
    // no action implemented at this time
  }

  @Override
  public IBoundedVoronoiVertexSymbol getVertexSymbol(ThiessenPolygon polygon) {
    return null;
  }

  @Override
  public void setAreaFillEnabled(boolean enabled) {
    // no action implemented at this time
  }


}
