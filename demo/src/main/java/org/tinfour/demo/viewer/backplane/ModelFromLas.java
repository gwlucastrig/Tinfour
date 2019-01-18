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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.gis.las.ILasRecordFilter;
import org.tinfour.gis.las.LasFileReader;
import org.tinfour.gis.las.LasGpsTimeType;
import org.tinfour.gis.las.LasPoint;
import org.tinfour.gis.las.LasRecordFilterByClass;
import org.tinfour.gis.las.LasRecordFilterByFirstReturn;
import org.tinfour.gis.las.LasRecordFilterByLastReturn;
import org.tinfour.gis.utils.VertexReaderLas;
import org.tinfour.utils.LinearUnits;

/**
 * A model for managing data taken from a Lidar (LAS) file.
 */
public class ModelFromLas extends ModelAdapter implements IModel {

  /**
   * The classification for ground-points as given in the
   * ASPRS LAS file-format specification.
   */
  public static final int GROUND_POINT = 2;

  /**
   * Lidar classification descriptions as given in LAS Specification
   * Version 1.4-R13 15 July 2013. Table 9 "ASPRS Standard LIDAR Point CLasses"
   */
  private static final String[] classificationDescription = {
    "Created, never classified",
    "Unclassified",
    "Ground",
    "Low Vegetation",
    "Medium Vegetation",
    "High Vegetation",
    "Building",
    "Low Point (noise)",
    "Model Key-point (mass point)",
    "Water",
    "Reserved for ASPRS definition",
    "Reserved for ASPRS definition",
    "Overlap Point"
  };

  LidarPointSelection lidarPointSelection;

  int nonGroundPoints;
  int groundPoints;

  LinearUnits linearUnits = LinearUnits.UNKNOWN;

  /**
   * Construct a model tied to the specified file with
   * filtering based on classification (only points of specified class
   * are accepted).
   *
   * @param file a valid LAS file
   * @param lidarPointSelection a specification for selecting lidar points
   * based on their classification or other elements.
   *
   *
   */
  public ModelFromLas(File file, LidarPointSelection lidarPointSelection) {
    super(file);
    this.lidarPointSelection = lidarPointSelection;
  }

  /**
   * Read the standard header of the LAS file associated with this
   * model and all internal records to create a collection of
   * vertices.
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

    long time0 = System.currentTimeMillis();
    VertexReaderLas reader = new VertexReaderLas(file);

    ILasRecordFilter vFilter = null;
    switch (lidarPointSelection) {
      case GroundPoints:
        vFilter = new LasRecordFilterByClass(2);
        break;
      case FirstReturn:
        vFilter = new LasRecordFilterByFirstReturn();
        break;
      case LastReturn:
        vFilter = new LasRecordFilterByLastReturn();
        break;
      default:
    }

    reader.setFilter(vFilter);
    List<Vertex> list = reader.read( monitor);
    if (list.isEmpty()) {
      monitor.reportDone(); // remove the progress bar
      if (lidarPointSelection == LidarPointSelection.AllPoints) {
        throw new IOException("Unable to read points from file");
      } else {
        // the source data contained no ground points. this can
        // happen when a LAS file is not classified or in the case of
        // bathymetric lidar (which may contain all water points)
        throw new IOException(
          "Source Lidar file does not contain samples for " + lidarPointSelection);
      }
    }

    this.linearUnits = reader.getLinearUnits();
    xMin = reader.getXMin();
    yMin = reader.getYMin();
    xMax = reader.getXMax();
    yMax = reader.getYMax();
    zMin = reader.getZMin();
    zMax = reader.getZMax();

    geographicCoordinates = reader.isSourceInGeographicCoordinates();
    if (geographicCoordinates) {
      coordinateTransform = reader.getCoordinateTransform();
    }

    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + list.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(list, monitor);

  }

  @Override
  public String getDescription() {
    return "Lidar (" + lidarPointSelection + ")";
  }

  void formatLidarFields(Formatter fmt, int vertexId) {
    if (vertexId < 0) {
      return;
    }

    // Opening the LAS file over and over again is
    // may be a performance issue
    LasFileReader reader = null;
    try {
      reader = new LasFileReader(file);
      LasPoint record = new LasPoint();
      reader.readRecord(vertexId, record);
      String description;
      if (record.classification < classificationDescription.length) {
        description = classificationDescription[record.classification];
      } else {
        description = "Reserved (" + record.classification + ")";
      }
      fmt.format("   Classification: %s%n", description);
      fmt.format("   Return:    %d of %d%n",
        record.returnNumber, record.numberOfReturns);
      fmt.format("   Intensity: %d%n", record.intensity);
      LasGpsTimeType gpsType = reader.getLasGpsTimeType();
      Date date = gpsType.transformGpsTimeToDate(record.gpsTime);
      SimpleDateFormat sdf;
      if (gpsType == LasGpsTimeType.WeekTime) {
        sdf = new SimpleDateFormat("EEE hh:MM:ss.S", Locale.getDefault());
      } else {
        sdf = new SimpleDateFormat("EEE YYYY-MM-dd HH:mm:ss.S", Locale.getDefault());
      }
      sdf.setTimeZone(new SimpleTimeZone(0, "UTC"));
      fmt.format("   Time/Date: %s UTC%n", sdf.format(date));

    } catch (IOException ioex) {
      System.err.println(
        "IOException reading " + file.getName() + " " + ioex.getMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException dontCare) {
          // don't care about this one
        }
      }
    }
  }

  /**
   * Gets the point selection option used to load the model.
   *
   * @return a valid enumeration instance
   */
  public LidarPointSelection getLidarPointSelection() {
    return lidarPointSelection;
  }

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return a valid enumeration instance
   */
  @Override
  public LinearUnits getLinearUnits() {
    return linearUnits;
  }
 
 
  @Override
  public String toString() {
    String conType = hasConstraints() ? " CDT" : "";
    String loaded = isLoaded() ? "Loaded" : "Unloaded";
    return String.format("Model From LAS %d %s%s", serialIndex, loaded, conType);
  }

}
