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
package org.tinfour.demo.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.VertexReaderText;

/**
 * A model for managing data taken from a text or comma-separated-value
 * file
 */
public class ModelFromText extends ModelAdapter implements IModel {
  final char delimiter;
  
  /**
   * Construct a model tied to the specified file.
   * <p>
   * <strong>Delimiters</strong>
   * Specifications in text files depend on the presence of a delimiter
   * character to separate data fields given on a single line.
   * Typically delimiters are specified using a space, tab, comma, 
   * or pipe (vertical bar character). Files with the extension 
   * ".csv" (for comma-separated-value) are assumed to be comma-delimited.
   * Otherwise, the constructor will attempt to inspect the content
   * of the specified file and determine the delimiter based on internal
   * clues.  If a file includes a mix of potential delimiters,
   * such as both spaces and commas, the delimiter will be determined
   * on the basis of precedence (in increasing order, space, tab, 
   * comma, and pipe).
   * <p>An application may also override the automatically determined
   * delimiter through the setDelimiter() method.
   * <strong>Geographic coordinates</strong>
   * If the values in the file are in geographic coordinates,
   * the input file must include a header row indicating which
   * columns are latitude and longitude. This feature serves two
   * purposes, it informs the model that the coordinates are geographic
   * and it dispels any ambiguity about which column is which.
   * Geographic coordinate values must be giving in standard decimal
   * numeric form using negative numbers for southern latitudes and
   * western longitudes (quadrants and degrees-minutes-seconds notion are
   * not supported at this time).
   * <p>
   * For example:
   * <pre><code>
   *    latitude, longitude, z
   *      42.5,     73.33,   2001
   * </code></pre>
   *
   * @param file a valid text or comma-separated value file
   *
   */
  public ModelFromText(File file) {
    super(file);
    
    // TO DO:  Right now, this constructur does not throw an IOException,
    // but it should.
    char test = 0;
    try (VertexReaderText reader = new VertexReaderText(file)) {
      test = reader.getDelimiter();
       geographicCoordinates = reader.isSourceInGeographicCoordinates();
       coordinateTransform = reader.getCoordinateTransform();
    } catch (IOException ioex) {

    }
    delimiter = test;
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

 
    if (areVerticesLoaded) {
      System.out.println("Internal error, multiple calls to load model");
      return;
    }
   VertexReaderText reader  = new VertexReaderText(file);
    long time0 = System.currentTimeMillis();
 
  
    List<Vertex>list =reader.read (monitor );
 
    if (list.isEmpty()) {
      monitor.reportDone(); // remove the progress bar
      throw new IOException("Unable to read points from file");
    }

    xMin = reader.getXMin();
    yMin = reader.getYMin();
    xMax = reader.getXMax();
    yMax = reader.getYMax();
    zMin = reader.getZMin();
    zMax = reader.getZMax();

    geographicCoordinates = reader.isSourceInGeographicCoordinates();
    if (geographicCoordinates) {
   
    }

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

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return at this time, the method always returns LinearUnits.UNDEFINED
   */
  @Override
  public LinearUnits getLinearUnits() {
    return LinearUnits.UNKNOWN;
  }

  @Override
  public String toString() {
    String conType = hasConstraints() ? " CDT" : "";
    String loaded = isLoaded() ? "Loaded" : "Unloaded";
    return String.format("Model From TXT %d %s%s", serialIndex, loaded, conType);
  }

}
