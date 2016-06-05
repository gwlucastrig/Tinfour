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
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.List;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.test.utils.VertexLoader;

/**
 * A model for managing data taken from a text or comma-separated-value
 * file
 */
public class ModelFromText extends ModelAdapter implements IModel {

  final char delimiter;

  /**
   * Construct a model tied to the specified file.
   *
   * @param file a valid text or comma-separated value file
   * @param delimiter the delimiter character used for accessing files.
   *
   */
  public ModelFromText(File file, char delimiter) {
    super(file);
    this.delimiter = delimiter;
  }

  /**
   * Read the specified file.
   *
   * @param monitor an optional monitor for tracking progress (null
   * if not used)
   * @throws IOException In the event of a non-recoverable error
   * related to I/O or file access.
   */
  @Override
  public void load(IMonitorWithCancellation monitor) throws IOException {

    if (loaded) {
      System.out.println("Internal error, nultiple calls to load model");
      return;
    }

    long time0 = System.currentTimeMillis();
    VertexLoader loader = new VertexLoader();

    List<Vertex> list = loader.readDelimitedFile(file, delimiter);

    xMin = loader.getXMin();
    yMin = loader.getYMin();
    xMax = loader.getXMax();
    yMax = loader.getYMax();
    zMin = loader.getZMin();
    zMax = loader.getZMax();

    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + list.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(list, monitor);

  }



  @Override
  public String getDescription() {
    if (Character.isWhitespace(delimiter)) {
      return "Space delimented text file";
    } else if (delimiter == ',') {
      return "Comma-separated values";
    } else {
      return "Text delimited by '" + delimiter + "'";
    }
  }

}
