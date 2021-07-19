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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Creates data elements suitable for rendering 2D graphics based on a Cartesian
 * coordinate system. This class establishes a BufferedImage and associated
 * Graphics2D object, as well as the appropriate coordinate transforms.
 * Applications that require only the transform data may use the companion
 * utility RenderingTransformAid.
 */
public class RenderingSurfaceAid {

  private final int width;
  private final int height;
  private final RenderingTransformAid rta;
  private final BufferedImage bImage;
  private final Graphics2D g2d;

  /**
   * Constructs a graphics surface (BufferedImage) and resources for a
   * coordinate system appropriate for the presentation of a rectangular region
   * defined by the specified coordinates. The transforms created by this class
   * will be appropriate for drawing graphs within the coordinate domain as
   * large as possible within the bounds of the specified size of the graphics
   * surface based on width, height, and padding allowance.
   * <p>
   * Some implementations of the of Java API's ImageIO do not support
   * creating JPEG image files from a BufferedImage with an alpha channel
   * When using this class with Java ImageIO to write JPEG images,
   * the associated BufferedImage should be created without an alpha channel.
   * An alternate constructor is provided for this purpose.
   *
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
  public RenderingSurfaceAid(
          int width, int height, int pad,
           double x0, double y0, double x1, double y1) {
    this.width = width;
    this.height = height;
    rta = new RenderingTransformAid(
            width, height, pad, x0,   y0, x1, y1);

    bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    g2d = bImage.createGraphics();
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }


  /**
   * Constructs a graphics surface (BufferedImage) and resources for a
   * coordinate system appropriate for the presentation of a rectangular region
   * defined by the specified coordinates. The transforms created by this class
   * will be appropriate for drawing graphs within the coordinate domain as
   * large as possible within the bounds of the specified size of the graphics
   * surface based on width, height, and padding allowance.
   * <p>
   * When using this class with Java ImageIO to write JPEG images,
   * the associated BufferedImage should be created without an alpha channel.
   * Some implementations of the of Java API's ImageIO do not support
   * creating JPEG image files from a BufferedImage with an alpha channel.
   *
   * @param width the width of the drawing surface, in pixels
   * @param height the height of the drawing surface, in pixels
   * @param pad an arbitrary padding to be added to each side of the rectangle,
   * in pixels
   * @param x0 Cartesian x coordinate for the lower-left corner of the domain
   * @param y0 Cartesian y coordinate for the lower-left corner of the domain
   * @param x1 Cartesian x coordinate for the upper-right corner of the domain
   * @param y1 Cartesian y coordinate for the upper-right corner of the domain
   * @param alpha indicates if the associated image should be created using
   * an alpha channel.
   */
  public RenderingSurfaceAid(
          int width, int height, int pad,
           double x0, double y0, double x1, double y1, boolean alpha) {
    this.width = width;
    this.height = height;
    rta = new RenderingTransformAid(
            width, height, pad, x0,   y0, x1, y1);

    if (alpha) {
      bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    } else {
      bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }
    g2d = bImage.createGraphics();
    g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }





  /**
   * Gets the affine transform for mapping Cartesian coordinates to pixel
   * coordinates. The transform was established by the constructor for this
   * class.
   *
   * @return a valid instance of an affine transform
   */
  public AffineTransform getCartesianToPixelTransform() {
    return rta.getCartesianToPixelTransform();
  }

  /**
   * Gets the affine transform for mapping pixel coordinates to Cartesian
   * coordinates. The transform was established by the constructor for this
   * class.
   *
   * @return a valid instance of an affine transform
   */
  public AffineTransform getPixelToCartesianTransform() {
    return rta.getPixelToCartesianTransform();
  }

  /**
   * Gets the rectangle for the drawing area corresponding to the coordinate
   * domain that was specified for the constructor. This rectangle is suitable
   * for use as a clipping rectangle when creating images.
   *
   * @return a valid instance of a rectangle, in pixel coordinates.
   */
  public Rectangle2D getDomainRectangle() {
    return rta.getDomainRectangle();
  }

  /**
   * Gets the buffered image associated with this drawing surface
   * @return a valid instance of a buffered image
   */
  public BufferedImage getBufferedImage() {
    return bImage;
  }

  /**
   * Get the Graphics2D object associated with this drawing surface.
   * @return a valid instance of a graphics object.
   */
  public Graphics2D getGraphics2D() {
    return g2d;
  }


   /**
   * Gets the distance across a pixel in the corresponding Cartesian
   * coordinate system.
   *
   * @return a non-zero floating point value
   */
  public double getUnitsPerPixel() {
    return rta.getUnitsPerPixel();
  }


  /**
   * Gets the distance in pixels across one unit of distance in
   * the Cartesian coordinate system.
   * @return  a non-zero floating point value
   */
  public double getPixelsPerUnit(){
    return rta.getPixelsPerUnit();
  }

  public void fillBackground(Color c){
    g2d.setColor(c);
    g2d.fillRect(0, 0, width, height);
  }


  public void drawFrame(Color c){
    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(c);
    g2d.drawRect(0, 0, width-1, height-1);
  }
}
