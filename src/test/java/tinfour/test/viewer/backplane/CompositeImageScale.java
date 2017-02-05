/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 01/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.awt.geom.AffineTransform;

/**
 * Provides the scale and dimension specifications for
 * mapping model coordinates onto an image plane.
 */
public class CompositeImageScale {

  private final int width;
  private final int height;
  private final AffineTransform m2c;
  private final AffineTransform c2m;

  /**
   * Construct a specification for producing an image of the indicated
   * dimensions.
   *
   * @param width the width of the image bounds
   * @param height the height of the image bounds
   * @param m2c the model to composite image transform
   * @param c2m the composite image to model transform
   */
  public CompositeImageScale(
    int width,
    int height,
    AffineTransform m2c,
    AffineTransform c2m
  ) {
    this.m2c = m2c;
    this.c2m = c2m;
    this.width = width;
    this.height = height;
  }

  /**
   * @return the width
   */
  public int getWidth() {
    return width;
  }

  /**
   * @return the height
   */
  public int getHeight() {
    return height;
  }

  /**
   * @return the model to composite transform
   */
  public AffineTransform getModelToCompositeTransform() {
    return m2c;
  }

  /**
   * @return the composite to model transform
   */
  public AffineTransform getCompositeToModelTransform() {
    return c2m;
  }

}
