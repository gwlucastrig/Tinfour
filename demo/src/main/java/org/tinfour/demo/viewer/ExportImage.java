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
 * 03/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

 

package org.tinfour.demo.viewer;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Provides elements for exporting an image
 */
 class ExportImage {
  final BufferedImage bImage;
  final AffineTransform i2m; // image to model
  
  /**
   * Constructor for valid result
   * @param bImage a valid buffered image
   * @param i2m the affine transform from the image to the model 
   * coordinate system.
   */
   ExportImage(BufferedImage bImage, AffineTransform i2m) {
     this.bImage = bImage;
     if (i2m == null) {
       this.i2m = new AffineTransform();
     } else {
       this.i2m = new AffineTransform(i2m);
     }
   }
  
}
