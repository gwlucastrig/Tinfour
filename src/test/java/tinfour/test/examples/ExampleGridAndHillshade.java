/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 11/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.examples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.SimpleTimeZone;
import javax.imageio.ImageIO;
import tinfour.common.IIncrementalTin;
import tinfour.common.Vertex;
import tinfour.gwr.BandwidthSelectionMethod;
import tinfour.gwr.SurfaceModel;
import tinfour.test.utils.GridSpecification;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.InterpolationMethod;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.TestPalette;
import tinfour.test.utils.VertexLoader;
import tinfour.interpolation.GwrTinInterpolator;
import tinfour.interpolation.IInterpolatorOverTin;
import tinfour.utils.TinInstantiationUtility;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
public class ExampleGridAndHillshade implements IDevelopmentTest {

  static String[] mandatoryOptions = {
    "-in"
  };

  /**
   * Run the example code accepting an input LAS file and writing an
   * output grid in Esri's ASCII raster format.
   *
   * @param ps a valid print-stream for recording results of processing.
   * @param args a set of arguments for configuring the processing.
   * @throws IOException if unable to read input or write output files.
   */
  @Override
  public void runTest(PrintStream ps, String[] args) throws IOException {
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("ExampleGridAndHillshade\n");
    ps.format("Date/time of test: %s (UTC)\n", sdFormat.format(date));

    // Load Options ---------------------------------------------
    //   The TestOptions class is designed to provide a convenient way
    // of collecting the most commonly used options in the TinFour
    // test suite.
    //   Setting option values before performing an argumentScan
    // is a way of populating defaults.  The preSort option performs a
    // Hilbert sort on input samples. For lidar files, it is usually unnecessary
    // but for randomly generated samples or sets of files taken in a
    // highly random order, it can reduce construction time.
    // The lidar classification value of 2 restricts the input to samples
    // that have been classified as ground points
    TestOptions options = new TestOptions();
    options.setLidarClass(2);
    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    // process special options that are used by this example program:
    Double cellsizeArg
      = options.scanDoubleOption(args, "-cellSize", optionsMatched);
    double cellSize = Double.NaN; // indicates to use the default
    if (cellsizeArg != null) {
      cellSize = cellsizeArg;
      if (cellSize <= 0) {
        throw new IllegalArgumentException("Invalid cell size: " + cellSize);
      }
    }

    InterpolationMethod method = options.getInterpolationMethod();

    double[] frame = options.getFrame();
    boolean isFrameSet = options.isFrameSet();

    // if any non-recognized options were supplied, complain
    options.checkForUnrecognizedArgument(args, optionsMatched);

    // Load Vertices from LAS file ------------------------------------
    //   The vertex loader implements logic to use test options such as
    // those that indicate Lidar classification for processing
    // (ground points only, etc.) and sorting options.
    File inputFile = options.getInputFile();
    File outputFile = options.getOutputFile();
    ps.format("Input file: %s\n", inputFile.getAbsolutePath());
    VertexLoader loader = new VertexLoader();
    List<Vertex> vertexList = loader.readInputFile(options);
    int nVertices = vertexList.size();
    ps.format("Number of vertices: %8d\n", nVertices);
    double xmin = loader.getXMin();
    double xmax = loader.getXMax();
    double ymin = loader.getYMin();
    double ymax = loader.getYMax();
    double zmin = loader.getZMin();
    double zmax = loader.getZMax();
    // estimate the point spacing.  The estimate is based on the simplifying
    // assumption that the points are arranged in a uniformly spaced
    // triangulated mesh (consisting of equilateral triangles). There would
    // be 3*N triangules of area s^2*sqrt(3)/4.
    double area = (xmax - xmin) * (ymax - ymin);
    double sSpace = 0.87738 * Math.sqrt(area / nVertices);
    double nominalPointSpacing = sSpace; //used as an input into TIN class/

    double geoScaleX = 0;
    double geoScaleY = 0;
    double geoOffsetX = 0;
    double geoOffsetY = 0;
    if (loader.isSourceInGeographicCoordinates()) {
      geoScaleX = loader.getGeoScaleX();
      geoScaleY = loader.getGeoScaleY();
      geoOffsetX = loader.getGeoOffsetX();
      geoOffsetY = loader.getGeoOffsetY();
      double gx0 = geoOffsetX+xmin / geoScaleX;
      double gx1 = geoOffsetX+xmax / geoScaleX;
      double gy0 = geoOffsetY+ymin / geoScaleY;
      double gy1 = geoOffsetY+ymax / geoScaleY;
      double gArea = (gx1 - gx0) * (gy1 - gy0);
      double gsSpace = 0.87738 * Math.sqrt(gArea / nVertices);
      ps.format("Source data was in geographic coordinates\n");
      ps.format("Range x values:     %11.6f, %11.6f, (%f)\n", gx0, gx1, gx1 - gx0);
      ps.format("Range y values:     %11.6f, %11.6f, (%f)\n", gy0, gy1, gy1 - gy0);
      ps.format("Est. sample spacing:   %e degrees of arc\n", gsSpace);
      ps.format("Geographic coordinates are mapped to projected coordinates\n");
    }

    if(Double.isNaN(cellSize)){
      cellSize = nominalPointSpacing;
    }

    ps.format("Range x values:     %12.3f, %12.3f, (%f)\n", xmin, xmax, xmax - xmin);
    ps.format("Range y values:     %12.3f, %12.3f, (%f)\n", ymin, ymax, ymax - ymin);
    ps.format("Range z values:     %12.3f, %12.3f, (%f)\n", zmin, zmax, zmax - zmin);
    ps.format("Est. sample spacing:%12.3f\n", sSpace);
    ps.format("Grid cell size:     %12.3f\n", cellSize);
    ps.flush();

    double x0 = xmin;
    double y0 = ymin;
    double x1 = xmax;
    double y1 = ymax;
    if (isFrameSet) {
      // a frame is specified
      x0 = frame[0];
      x1 = frame[1];
      y0 = frame[2];
      y1 = frame[3];
      ps.format("Frame x values:     %12.3f, %12.3f, (%f)\n", x0, x1, (x1 - x0));
      ps.format("Frame y values:     %12.3f, %12.3f, (%f)\n", y0, y1, (y1 - y0));
    }

    GridSpecification grid = new GridSpecification(
      GridSpecification.CellPosition.CenterOfCell,
      cellSize,
      x0, x1, y0, y1,
      geoScaleX, geoScaleY, geoOffsetX, geoOffsetY);
    ps.format("Output grid\n");
    ps.format("   Rows:              %8d\n", grid.getRowCount());
    ps.format("   Columns:           %8d\n", grid.getColumnCount());
    ps.format("   Cells:             %8d\n", grid.getCellCount());
    ps.format("   Interpolation method: %s\n\n", method);

    // Determine which TIN class to use  --------------------------
    //  The IncrementalTin class runs 60 percent faster than the
    // VirtualIncrementalTin class, but requires roughly twice as much
    // memory.  The TestOptions permit the argument vector to specify
    // which class is used, but if a specific choice is not supplied,
    // the following logic will use the TinInstantiationUtility to pick
    // a TIN class based on the nuber of vertices and the amount of memory
    // that the application is willing to apportion to the TIN.  Since this
    // example doesn't do much except build a TIN, we assign it 50 percent
    // of the available memory. This value would vary by application.
    IIncrementalTin tin;
    Class<?> tinClass;
    TinInstantiationUtility tiu = new TinInstantiationUtility(0.5, nVertices);
    if (options.isTinClassSet()) {
      tinClass = options.getTinClass();
    } else {
      ps.println(
        "Performing automatic selection of TIN class"
        + " based on memory and number of vertices");
      tiu.printSummary(ps);
      tinClass = tiu.getTinClass();
    }

    tin = tiu.constructInstance(tinClass, nominalPointSpacing);

    // Add vertices to TIN  -------------------------------------------
    //   Vertices can be added one-at-a-time or via a list.  In general,
    // the use of a list has a small performance and robustness advantage.
    // The first time either TIN class is used, the performance is
    // affected by the overhead of class loaders and the Just-in-Time.
    // With repeated use, throughput improves substantially.
    ps.format("\nBuilding TIN using: %s\n", tin.getClass().getName());
    long time0 = System.nanoTime();
    tin.add(vertexList, null);
    long time1 = System.nanoTime();
    ps.format("Time to process vertices (milliseconds):    %12.3f\n",
      (time1 - time0) / 1000000.0);

    // ---------------------------------------------------------------
    // Write Output
    //   The output grid is build using an interpolation.
    // Make sure it has the "asc" extension
    if (outputFile == null) {
      ps.println("No output file specified");
      return;
    }

    int kVisible = 0;
    for (Vertex v : vertexList) {
      if (x0 <= v.getX() && v.getX() <= x1) {
        if (y0 <= v.getY() && v.getY() <= y1) {
          kVisible++;
        }
      }
    }
    ps.format("Estimated visible vertices: %11d\n", kVisible);
    // Build the elevation grid --------------------------------------
    // Interpolate the elevation grid using either the default method
    // or the method specified on the command-line. The results are
    // written to an Esri ASCII Raster formatted file and also
    // used to produce images.
    ps.println("");
    ps.println("Interpolating elevation data to build grid using method: " + method);
    time0 = System.nanoTime();
    float results[][] = buildElevationGrid(tin, method, grid);
    time1 = System.nanoTime();
    ps.format("Elevation grid processing completed in %3.2f ms\n",
      (time1 - time0) / 1000000.0);
    File file = prepFileNamedForSubject(outputFile, "_z", "asc");
    ps.println("Writing grid to file " + file.getAbsolutePath());
    ps.flush();
    grid.writeAsciiFile(file, results, "%4.3f", "-999");

    float eMin = Float.POSITIVE_INFINITY;
    float eMax = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < results.length; i++) {
      for (int j = 0; j < results[i].length; j++) {
        float eZ = results[i][j];
        if (eZ < eMin) {
          eMin = eZ;
        }
        if (eZ > eMax) {
          eMax = eZ;
        }
      }
    }

    // Write image files -----------------------------------------
    //  Just to show that you don't have to have access to a GIS program
    // to visualize surface data, this example creastes image files
    // showing elevation and hillshade effects.   For this purpose, a very
    // simple palette is used.
    int nRows = grid.getRowCount();
    int nCols = grid.getColumnCount();
    int nCells = nRows * nCols;
    int[] argb = new int[nCells];
    TestPalette palette;
    if (!options.isPaletteSet()) {
      ps.println("No palette specified, filling background image with white");
      Arrays.fill(argb, 0xffffffff);
    } else {
      palette = options.getPalette();
      ps.println("Color-coding elevation data using palette: " + palette.getName());

      int k = 0;
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          float z = results[iRow][iCol];
          if (Float.isNaN(z)) {
            argb[k++] = 0xff808080; // gray
          } else {
            argb[k++] = palette.getARGB(results[iRow][iCol], eMin, eMax);
          }
        }
      }
    }

    // Build hillshade data ---------------------------------
    //   Hillshade is computed using the surface normal obtained from a
    // Geographically Weighted Regression interpolation.  It would
    // have been feasible, and far more efficient, to combine the hillshade
    // and elevation calculations, but this example keeps them separate to
    // simplify the example code.
    ps.println("Building grid of hillshade data");
    time0 = System.nanoTime();
    float[][] hillshade = this.buildHillshadeGrid(tin, grid);
    time1 = System.nanoTime();
    ps.format("Hillshade grid processing completed in %3.2f ms\n",
      (time1 - time0) / 1000000.0);

    // Create the hillshade image ---------------------------------
    //   The values in the hillshade array giving lighting intensity
    // values in the range of 0 to 1.  These values are used to adjusted
    // the lightness/darkness of the pixels in the elevation image
    // that was developed above.
    int k = 0;
    for (int iRow = 0; iRow < nRows; iRow++) {
      for (int iCol = 0; iCol < nCols; iCol++) {
        int p = argb[k];
        if (Float.isNaN(results[iRow][iCol])) {
          argb[k++] = 0xff808080;
        } else {
          double s = hillshade[iRow][iCol];
          int red = (int) (((p >> 16) & 0xff) * s);
          int grn = (int) (((p >> 8) & 0xff) * s);
          int blu = (int) ((p & 0xff) * s);
          argb[k++] = ((((0xff00 | red) << 8) | grn) << 8) | blu;
        }
      }
    }
    BufferedImage bImage = new BufferedImage(
      nCols, nRows, BufferedImage.TYPE_INT_ARGB);
    File imageFile = this.prepFileNamedForSubject(outputFile, "_hillshade", "png");
    ps.println("Writing hillshade image to " + imageFile.getAbsolutePath());
    bImage.setRGB(0, 0, nCols, nRows, argb, 0, nCols);
    ImageIO.write(bImage, "PNG", imageFile);

    ps.println("Example application processing complete.");
  }

  /**
   * Builds a 2 dimensional array of elevation values using the
   * specified interpolation method at each (x,y)
   * coordinate specified by the supplied grid object
   *
   * @param tin a valid Triangulated Irregular Network populated with
   * vertices lying within the area specified by the grid object.
   * @param method an enumeration value indicating the method to use
   * for performing interpolation
   * @param grid a grid object derived from the bounds of the vertex set
   * and the cell-spacing method specified at the command-line.
   * @return if successful, a fully populated array of elevations.
   */
  float[][] buildElevationGrid(
    IIncrementalTin tin,
    InterpolationMethod method,
    GridSpecification grid
  ) {

    // for brevity, copy out values.
    int nRows = grid.getRowCount();
    int nCols = grid.getColumnCount();
    double xLL = grid.getLowerLeftX();
    double yUL = grid.getUpperRightY();
    double cellSize = grid.getCellSize();

    IInterpolatorOverTin interpolator = method.getInterpolator(tin);
    float results[][] = new float[nRows][nCols];

    for (int iRow = 0; iRow < nRows; iRow++) {
      float[] row = results[iRow];
      // the first row is at the top of the raster
      double yRow = yUL - iRow * cellSize;
      for (int iCol = 0; iCol < nCols; iCol++) {
        double xCol = iCol * cellSize + xLL;
        double z = interpolator.interpolate(xCol, yRow, null);
        if (Double.isNaN(z)) {
          row[iCol] = Float.NaN;
        } else {
          row[iCol] = (float) z;
        }
      }

    }
    return results;
  }

  /**
   * Builds a 2 dimensional array of illumination values in the
   * range 0 to 1 based on the normal vector at each (x,y)
   * coordinate specified by the supplied grid object.
   * The Geographically Weighted Regression method is used to derive
   * a model for the surface and the model's first derivatives are
   * used to compute the normal.
   *
   * @param tin a valid Triangulated Irregular Network populated with
   * vertices lying within the area specified by the grid object.
   * @param grid a grid object derived from the bounds of the vertex set
   * and the cell-spacing method specified at the command-line.
   * @return if successful, a fully populated array of illumination
   * values in the range 0 to 1.
   */
  float[][] buildHillshadeGrid(
    IIncrementalTin tin,
    GridSpecification grid
  ) {

    // for brevity, copy out values.
    int nRows = grid.getRowCount();
    int nCols = grid.getColumnCount();
    double xLL = grid.getLowerLeftX();
    double yUL = grid.getUpperRightY();
    double cellSize = grid.getCellSize();

    // The lightning model assumes an illumination point source at an
    // infinite distance and a non-directional component providing
    // ambient illumination.  Light intensity values from from 1.0
    // (fully illuminated) to 0.0 (fully dark). The azimuth and elevation settings
    // give the position of the illumination point. Azimuth is measured
    // counterclockwise from the x-axis.
    //   The fraction of direct lighting is computed by taking the dot
    // product of a unit vector pointing at the illumination source ("the sun")
    // and the unit surface normal vector.  This computation gives the cosine
    // of the angle between the two vectors. The direct lightning is multiplied
    // by the cosine to compute a directional shading value.
    //   The unit normal is obtained by using a geographically weighted
    // regression over a collection of points in the vicinity of the
    // query coordinates. This computation develops a polynomial function
    // z = f(x, y). The partial derivatives at point (x,y) are used to
    // obtain the surface normal vector at that position.
    double ambient = 0.25;
    double directLight = 1.0 - ambient;
    double sunAzimuth = Math.toRadians(135);
    double sunElevation = Math.toRadians(45);
    // create a unit vector pointing at illumination source
    double cosA = Math.cos(sunAzimuth);
    double sinA = Math.sin(sunAzimuth);
    double cosE = Math.cos(sunElevation);
    double sinE = Math.sin(sunElevation);
    double xSun = cosA * cosE;
    double ySun = sinA * cosE;
    double zSun = sinE;

    GwrTinInterpolator interpolator = new GwrTinInterpolator(tin);
    float results[][] = new float[nRows][nCols];

    for (int iRow = 0; iRow < nRows; iRow++) {
      float[] row = results[iRow];
      // the first row is at the top of the raster
      double yRow = yUL - iRow * cellSize;
      for (int iCol = 0; iCol < nCols; iCol++) {
        double xCol = iCol * cellSize + xLL;
        // for details of the regression, see the Javadoc for the
        // class implementation. The interpolate method performs a linear
        // regression which derives coefficients for a polynomial
        // model of the surface in the vicinity of the query point.
        // The results from the calculation are retained by the
        // interpolator instance and then used by the call to get the
        // unit normal to the surface at the interpolation point.
        // The regression class allows an application to pick different
        // models. After some experimentation, I chose a cubic model.
        // Neighboring points are weighted by their distance based on the
        // "bandwidth selection method".  I used a computed bandwidth
        // that is proportional to the mean distance of the sample points from
        // the query coordinates (the proportion parameter, 0.5, was chosen
        // through experimentation).
        double z = interpolator.interpolate(SurfaceModel.CubicWithCrossTerms,
          BandwidthSelectionMethod.FixedProportionalBandwidth, 1.0,
          xCol, yRow, null);
        if (Double.isNaN(z)) {
          row[iCol] = 0;
        } else {
          double[] n = interpolator.getSurfaceNormal();
          // n[0], n[1], n[2]  give x, y, and z values
          double cosTheta = n[0] * xSun + n[1] * ySun + n[2] * zSun;
          if (cosTheta < 0) {
            // the surface is facing more than 90 degrees away from the
            // illumination source.  The illumination source contributes
            // no light to the surface, but a negative value is undefined and
            // so it is constrained to zero.  The surface will be treated as
            // receiving only ambient light.
            cosTheta = 0;
          }
          double intensity = cosTheta * directLight + ambient;
          if (intensity > 1) {
            intensity = 1;
          } else if (intensity <= 0) {
            intensity = 0;
          }


          row[iCol] = (float) intensity;
        }
      }

    }
    return results;
  }

  /**
   * Prepare an output file with the specified subject string specified and
   * the extension. If an existing file extension of either ".txt" or ".asc"
   * is detected, it will be removed from the input file and replaced.
   * Other extensions or possible extensions will be left alone.
   *
   * @param file the file specification for the root name
   * @param subject the subject matter string
   * @param extension the extension for the output file name
   * @return a valid file object with an adjusted output file name
   */
  File prepFileNamedForSubject(File file, String subject, String extension) {
    String rootName = file.getAbsolutePath();

    int period = rootName.lastIndexOf('.');
    if (period > 0 && period < rootName.length() - 1) {
      String test = rootName.substring(period, rootName.length());
      if (".asc".equalsIgnoreCase(test) || ".txt".equalsIgnoreCase(test)) {
        rootName = rootName.substring(0, period);
      }
    }
    return new File(rootName + subject + "." + extension);
  }

  /**
   * Provides the main method for an example application
   * that develops raster elevation files in Esri's ASCII format
   * and image files in PNG format.
   * <p>
   * Data is accepted from an LAS file. For best results, the file
   * should be in a projected coordinate system rather than a geographic
   * coordinate system. In general, geographic coordinate systems are a
   * poor choice for Lidar data processing since they are non-isotropic,
   * however many data sources provide them in this form.
   * <p>
   * Command line arguments include the following:
   * <pre>
   *   -in &lt;file path&gt;    input LAS file
   *   -out &lt;file path&gt;   output file path to be used for processing.
   *   -cellSize &lt;value &gt; 0, default 1&gt;    the cell size for the output
   *                      grids. cell size should be consistent with
   *                      nominal pulse spacing of LAS file ground points
   *   -method [Linear, NaturalNeighbor, Regression] default NaturalNeighbor
   *                      Interpolation method used for modeling elevations.
   *
   *    Other arguments used by Tinfour test programs are supported
   * </pre>
   *
   * @param args command line arguments indicating the input LAS file
   * for processing and various output options.
   */
  public static void main(String[] args) {
    ExampleGridAndHillshade example = new ExampleGridAndHillshade();

    try {
      example.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }

}
