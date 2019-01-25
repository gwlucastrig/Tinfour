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
import org.tinfour.gis.utils.VertexReaderShapefile;

/**
 * A model for managing data taken from a Shapefile. At this time, only PointZ
 * format files are supported.
 */
public class ModelFromShapefile extends ModelAdapter implements IModel {

  static final double eRadius = 6378137; // WGS-84 equatorial radius
  static final double eFlattening = 1 / 298.257223560; // WGS-84

  String rootPath;
  String dbfFieldOption;

  /**
   * Construct a model tied to the specified file.
   *
   *
   * @param file a valid text or comma-separated value file
   * @param dbfFieldOption an optional string indicating that a field from the DBF
   * file is to be used as a source of z coordinates
   *
   */
  public ModelFromShapefile(File file, String dbfFieldOption) {
    super(file);
    this.dbfFieldOption = dbfFieldOption;
    geoScaleX = 1.0;
    geoScaleY = 1.0;
    geoOffsetX = 0.0;
    geoOffsetY = 0.0;

    try (VertexReaderShapefile reader = new VertexReaderShapefile(file)) {
      geographicCoordinates = reader.isSourceInGeographicCoordinates();
      coordinateTransform = reader.getCoordinateTransform();
      linearUnits = reader.getLinearUnits();
      
    } catch (IOException ioex) {
      throw new IllegalArgumentException("Unable to read "
              + file.getPath() + ", " + ioex.getMessage(), ioex);
    }
  }
 
  /**
   * Read the specified file.
   *
   * @param monitor an optional monitor for tracking progress (null if not used)
   * @throws IOException In the event of a non-recoverable error related to I/O
   * or file access.
   */
  @Override
  public void load(IMonitorWithCancellation monitor) throws IOException {

    if (areVerticesLoaded) {
      System.out.println("Internal error, multiple calls to load model");
      return;
    }

    long time0 = System.currentTimeMillis();

    List<Vertex> vList = null;
    try (VertexReaderShapefile reader = new VertexReaderShapefile(file)) {
      reader.setDbfFieldForZ(dbfFieldOption);
      vList = reader.read(monitor);
      xMin = reader.getXMin();
      yMin = reader.getYMin();
      xMax = reader.getXMax();
      yMax = reader.getYMax();
      zMin = reader.getZMin();
      zMax = reader.getZMax();
    }

 
    if (vList==null || vList.isEmpty()) {
      monitor.reportDone(); // remove the progress bar
      throw new IOException("Unable to read points from file");
    }
 
    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + vList.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(vList, monitor);

  }

  @Override
  public String getDescription() {
    return "Shapefile " + this.file.getName();
  }



  @Override
  public String toString() {
    String conType = hasConstraints() ? " CDT" : "";
    String loaded = isLoaded() ? "Loaded" : "Unloaded";
    return String.format("Model From TXT %d %s%s", serialIndex, loaded, conType);
  }
  
}

  
