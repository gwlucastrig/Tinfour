/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 07/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.utils.rendering;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;

/**
 * Provides an aid for establishing a Cartesian coordinate system for rendering
 * 2D drawings as well as utilities for initializing a graphics surface.
 */
public class RenderingTransformAid {

  private final double unitPerPixel;
  /**
   * Cartesian to pixel transform
   */
  private final AffineTransform c2p;

  /**
   * Pixel to Cartesian transform.
   */
  private final AffineTransform p2c;

  /**
   * The pixel bounds for the transformed rectangle.
   */
  private final Rectangle2D domainRectangle;

  /**
   * Constructs resources for a coordinate system appropriate for the
   * presentation of a rectangular region defined by the specified coordinates.
   * The transforms created by this class will be appropriate for drawing graphs
   * within the coordinate domain as large as possible within the bounds of the
   * specified size of the graphics surface based on width, height, and padding
   * allowance.
   *
   * @param width the width of the drawing surface, in pixels
   * @param height the height of the drawing surface, in pixels
   * @param pad an arbitrary padding to be added to each side of the rectangle,
   * in pixels
   * @param x0 Cartesian x coordinate for the lower-left corner of the domain
   * @param y0 Cartesian y coordinate for the lower-left corner of the domain
   * @param x1 Cartesian x coordinate for the upper-right corner of the domain
   * @param y1 Cartesian y coordinate for the upper-right corner of the domain
   */
  public RenderingTransformAid(
          int width, int height, int pad,
          double x0, double y0, double x1, double y1) {
    if (pad < 0) {
      throw new IllegalArgumentException("Negative pad value not allowed");
    }
    if (width - 2 * pad < 1 || height - 2 * pad < 1) {
      throw new IllegalArgumentException(
              "Width and height (minus padding) too small");
    }

    if (Double.isNaN(x0) || Double.isNaN(x1) || x1 == x0) {
      throw new IllegalArgumentException(
              "Improper domain for x coordinates [" + x0 + ", " + x1 + "]");
    }

    if (Double.isNaN(y0) || Double.isNaN(y1) || y1 == y0) {
      throw new IllegalArgumentException(
              "Improper domain for x coordinates [" + y0 + ", " + y1 + "]");
    }

    // The goal is to create an AffineTransform that will map coordinates
    // from the source data to image (pixel) coordinates).  We wish to
    // render the rectangle defined by the source coordinates on an
    // image with as large a scale as possible.  However, the shapes of
    // the image and the data rectangles may be different (one may be
    // wide and one may be narrow).  So we need to use the aspect ratios
    // of the two rectangles to determine whether to fit it to the
    // vertical axis or to fit it to the horizontal.
    double rImage = (double) (width - pad) / (double) (height - pad);
    double rData = (x1 - x0) / (y1 - y0);
    double rAspect = rImage / rData;

    if (rAspect >= 1) {
      // the shape of the image is fatter than that of the
      // data, the limiting factor is the vertical extent
      unitPerPixel = (y1 - y0) / (height - pad);
    } else { // r<1
      // the shape of the image is skinnier than that of the
      // data, the limiting factor is the horizontal extent
      unitPerPixel = (x1 - x0) / (width - pad);
    }
    double scale = 1.0 / unitPerPixel;

    double xCenter = (x0 + x1) / 2.0;
    double yCenter = (y0 + y1) / 2.0;
    double xOffset = (width - pad) / 2 - scale * xCenter + pad / 2;
    double yOffset = (height - pad) / 2 + scale * yCenter + pad / 2;

    c2p = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);
    try {
      p2c = c2p.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(
              "Input elements result in a degenerate transform: "
              + ex.getMessage(),
              ex);
    }

    double[] c = new double[8];
    c[0] = x0;
    c[1] = y0;
    c[2] = x1;
    c[3] = y1;
    c2p.transform(c, 0, c, 4, 2);

    domainRectangle = new Rectangle2D.Double(
            c[4], c[7], c[6] - c[4], c[5] - c[7]);

  }

  /**
   * Gets the affine transform for mapping Cartesian coordinates to pixel
   * coordinates. The transform was established by the constructor for this
   * class.
   *
   * @return a valid instance of an affine transform
   */
  public AffineTransform getCartesianToPixelTransform() {
    return c2p;
  }

  /**
   * Gets the affine transform for mapping pixel coordinates to Cartesian
   * coordinates. The transform was established by the constructor for this
   * class.
   *
   * @return a valid instance of an affine transform
   */
  public AffineTransform getPixelToCartesianTransform() {
    return p2c;
  }

  /**
   * Gets the rectangle for the drawing area corresponding to the coordinate
   * domain that was specified for the constructor. This rectangle is suitable
   * for use as a clipping rectangle when creating images.
   *
   * @return a valid instance of a rectangle, in pixel coordinates.
   */
  public Rectangle2D getDomainRectangle() {
    return domainRectangle;
  }

  /**
   * Gets the distance across a pixel in the corresponding Cartesian
   * coordinate system.
   *
   * @return a non-zero floating point value
   */
  public double getUnitsPerPixel() {
    return unitPerPixel;
  }
  
  
  /**
   * Gets the distance in pixels across one unit of distance in
   * the Cartesian coordinate system.
   * @return  a non-zero floating point value
   */
  public double getPixelsPerUnit(){
    return 1.0/unitPerPixel;
  }
}
