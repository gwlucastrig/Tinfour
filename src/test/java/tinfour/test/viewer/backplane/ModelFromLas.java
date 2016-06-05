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
import java.util.Formatter;
import java.util.List;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.las.ILasRecordFilter;
import tinfour.las.LasFileReader;
import tinfour.las.LasPoint;
import tinfour.las.LasRecordFilterByClass;
import tinfour.test.utils.VertexLoader;

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
  private static final String [] classificationDescription = {
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


  final boolean groundPointFilter;

  double geoScaleX;
  double geoScaleY;
  double geoOffsetX;
  double geoOffsetY;
  boolean geographicCoordinates;

  int nonGroundPoints;
  int groundPoints;



  /**
   * Construct a model tied to the specified file with
   * filtering based on classification (only points of specified class
   * are accepted).
   *
   * @param file a valid LAS file
   * @param groundPointFilter indicates that the model should load ground
   * points only (rather than all samples).
   *
   *
   */
  public ModelFromLas(File file, boolean groundPointFilter) {
    super(file);
    this.groundPointFilter = groundPointFilter;
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

    if (loaded) {
      System.out.println("Internal error, nultiple calls to load model");
      return;
    }

    long time0 = System.currentTimeMillis();
    VertexLoader loader = new VertexLoader();

    ILasRecordFilter vFilter = null;
    if (this.groundPointFilter) {
      vFilter = new LasRecordFilterByClass(2);
    }

    List<Vertex> list = loader.readLasFile(file, vFilter, monitor);

    xMin = loader.getXMin();
    yMin = loader.getYMin();
    xMax = loader.getXMax();
    yMax = loader.getYMax();
    zMin = loader.getZMin();
    zMax = loader.getZMax();

    geographicCoordinates = loader.isSourceInGeographicCoordinates();
    if (geographicCoordinates) {
      geoScaleX = loader.getGeoScaleX();
      geoScaleY = loader.getGeoScaleY();
      geoOffsetX = loader.getGeoOffsetX();
      geoOffsetY = loader.getGeoOffsetY();
    }

    long time1 = System.currentTimeMillis();
    timeToLoad = time1 - time0;
    System.out.println("Loaded " + list.size() + " vertices in " + timeToLoad + " ms");

    this.prepareModelForRendering(list, monitor);

  }


  @Override
  public String getDescription() {
    if (this.hasGroundPointFilter()) {
      return "Lidar (Ground Points)";
    } else {
      return "Lidar (All Samples)";
    }
  }


  /**
   * Indicates if the model was instantiated with the ground-point filter
   * activated.
   *
   * @return true if the model filters out non-ground-points.
   */
  public boolean hasGroundPointFilter() {
    return this.groundPointFilter;
  }

  @Override
  public String getFormattedCoordinates(double x, double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / geoScaleY + geoOffsetY, true);
      sb.append(" / ");
      fmtGeo(fmt, x / geoScaleX + geoOffsetX, false);
      return sb.toString();
    }
    return String.format("%4.2f,%4.2f", x, y);
  }

  @Override
  public String getFormattedX(double x) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, x / geoScaleX + geoOffsetX, false);
      return sb.toString();
    }
    return String.format("%11.2f", x);
  }

  @Override
  public String getFormattedY(double y) {
    if (geographicCoordinates) {
      StringBuilder sb = new StringBuilder();
      sb.append(' '); // to provide vertical alignment with longitudes
      Formatter fmt = new Formatter(sb);
      fmtGeo(fmt, y / geoScaleY + geoOffsetY, true);
      return sb.toString();
    }
    return String.format("%11.2f", y);
  }

  void fmtGeo(Formatter fmt, double coord, boolean latFlag) {
    double c = coord;
    if (c < -180) {
      c += 360;
    } else if (c >= 180) {
      c -= 360;
    }
    int x = (int) (Math.abs(c) * 360000 + 0.5);
    int deg = x / 360000;
    int min = (x - deg * 360000) / 6000;
    int sec = x % 6000;
    char q;
    if (latFlag) {
      if (c < 0) {
        q = 'S';
      } else {
        q = 'N';
      }
      fmt.format("%02d\u00b0 %02d' %05.2f\" %c", deg, min, sec/100.0, q);
    } else {
      if (c < 0) {
        q = 'W';
      } else {
        q = 'E';
      }
      fmt.format("%03d\u00b0 %02d' %05.2f\" %c", deg, min, sec/100.0, q);
    }
  }

  /**
   * Indicates whether the coordinates used by this instance are
   * geographic in nature.
   *
   * @return true if coordinates are geographic; otherwise, false.
   */
  public boolean isCoordinateSystemGeographic() {
    return this.geographicCoordinates;
  }



  void formatLidarFields(Formatter fmt, int vertexId) {
    if (vertexId < 0) {
      return;
    }

    LasFileReader reader = null;
    try {
      reader = new LasFileReader(file);
      LasPoint record = new LasPoint();
      reader.readRecord(vertexId, record);
      String description;
      if(record.classification<classificationDescription.length){
        description = classificationDescription[record.classification];
      }else{
        description = "Reserved ("+record.classification+")";
      }
      fmt.format("   Classification: %s\n", description);
      fmt.format("   Return:         %d of %d\n",
        record.returnNumber, record.numberOfReturns);
      fmt.format("   Intensity:      %d\n", record.intensity);
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

}
