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
 * 05/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.demo.viewer.backplane;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class RenderProduct {
    public RenderProductType layerType;
    public MvComposite composite;
    public AffineTransform compatibilityTransform;
    public BufferedImage image;

    RenderProduct(
      RenderProductType layerType,
      MvComposite composite, BufferedImage image){
      this.layerType = layerType;
      this.composite = composite;
      this.image = image;
    }
}
