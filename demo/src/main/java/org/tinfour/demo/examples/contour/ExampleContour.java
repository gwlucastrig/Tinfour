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
 * 08/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.examples.contour;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.common.Vertex;
import org.tinfour.contour.Contour;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.rendering.RenderingSurfaceAid;

/**
 * Provides an example implementation showing how to use the Tinfour Contour
 * classes. Tinfour provides contours graphs from a Delaunay
 * Triangulation based on the following assumptions:
 * <ol>
 * <li>The vertices can be treated as describing a continuous surface.<li>
 * <li>The surface does not include interior null-data values and none of the
 * vertex Z coordinates give NaN (not-a-number) values.</li>
 * </ol>
 * The output from this class includes a output file showing the
 * results.
 */
public class ExampleContour {

  private static final Color defaultColors[] = {
    Color.YELLOW,
    Color.MAGENTA,
    Color.ORANGE,
    Color.LIGHT_GRAY,
    Color.PINK,
    Color.GREEN.brighter(),
    Color.RED,
    Color.BLUE,};

  // The input data shown below is intended to exercise
  // most of the different ways contours can be constructed
  // by the contour-builder operation.
  //    One feature to note when inspecting the results is that the
  // area of 3's in the upper-right corner does not produce a contour.
  // This is consistent with the Tinfour definiton of a contour: A contour
  // is the boundary between a region of points with lower-values and a region
  // of points with values greater than or equal to the contour value.
  // This the contour for zContour[1] == 3 does not pass between 
  // vertices with values 4 and 3, and thus there is no contour or separate
  // region in the upper-left corner of the output.
  // 
  static final String[] input = {
    "4 4 4 4 4 4 3 3 3",
    "4 4 4 4 4 4 3 3 3",
    "4 4 4 0 4 4 4 4 4",
    "4 4 0 0 4 4 4 4 4",
    "4 4 4 4 4 4 4 4 4",
    "2 2 2 2 2 2 2 2 2",
    "2 2 2 2 2 2 2 2 2",
    "2 2 2 2 2 2 2 2 2",
    "2 2 2 2 2 2 2 2 2",
    "0 0 0 0 0 0 0 0 0",
    "2 2 0 0 0 2 0 0 0",
    "2 2 0 0 0 0 0 0 0"};

  static final double[] zContour = {1, 3};

  static public void main(String[] args) {
    // to obtain a sample input TIN for the example
    // build a regular grid of vertices from the strings given above.
    // Note that the y-coordinates are inverted to provide
    // a visual output that matches the appearance of the array
    // definitions above.
    int nRows = input.length;
    int nCols = input[0].length() / 2;
    List<Vertex> vList = new ArrayList<>(nRows * nCols);
    for (int iRow = 0; iRow < nRows; iRow++) {
      double y = ((nRows - 1) - iRow);
      for (int iCol = 0; iCol < nCols; iCol++) {
        double x = iCol;
        int iZ = (int) (input[iRow].charAt(iCol * 2)) - 48;
        Vertex v = new Vertex(x, y, iZ, vList.size());
        vList.add(v);
      }
    }

    IncrementalTin tin = new IncrementalTin(1.0);
    tin.add(vList, null);

    // Create a set of contours and regions based on the input tin.
    //    The example here uses a null valuator.  A null valuator tells the
    // builder to just use the z values from the vertices rather than applying
    // any adjustments to their values. 
    //    The example also supplies a value of "true" instructing the
    // builder to create regions as well as contours.
    ContourBuilderForTin builder
            = new ContourBuilderForTin(tin, null, zContour, true);

    List<Contour> contours = builder.getContours();
    List<ContourRegion> regions = builder.getRegions();

    // perform a basic sanity test.  The sum of the absolute areas of the
    // regions should equal that of the overall grid.  However, we do have
    // to correct for enclosed areas because they would otherwise be counted
    // twice. We accomplish that adjustment by using the getAdjustedArea()
    // method (which subtracted enclosed areas form the overall area 
    // for the region.
    double gridArea = (nRows - 1) * (nCols - 1);
    double sumArea = 0;
    for (ContourRegion region : regions) {
      sumArea += region.getAdjustedArea();
    }
    System.out.format("Total area of contour regions: %12.3f%n", sumArea);
    System.out.format("Overall area of input grid:    %12.3f%n", gridArea);
    double deltaArea = sumArea-gridArea;
    System.out.format("Difference in computed areas:  %f%n",
            Math.abs(deltaArea));


    System.out.format("%n");
    System.out.format("Reg     Intvl   Area   Encl   N-Enclosed%n");
    for (int i = 0; i < regions.size(); i++) {
      System.out.format("%3d: %s%n", i, regions.get(i).toString());
    }

    System.out.format("%nContours%n");
    for (Contour contour : contours) {
      System.out.println("  " + contour.toString());
    }
    // Now draw the output to a file called ExampleContour.png
    // File is written to the present working directory (current folder).
    // Cartesian coordinates of the input data domain.
    double x0 = 0;
    double y0 = 0;
    double x1 = nCols - 1;
    double y1 = nRows - 1;
    // image output size and padding around edges
    int width = 650;
    int height = 650;
    int padding = 5;  // in pixels
    RenderingSurfaceAid rsa = new RenderingSurfaceAid(
            width,
            height,
            padding,
            x0, y0, x1, y1);
    rsa.fillBackground(Color.white);

    BufferedImage bImage = rsa.getBufferedImage();
    Graphics2D g2d = rsa.getGraphics2D();
    AffineTransform af = rsa.getCartesianToPixelTransform();

    // color-fill the regions
    for (ContourRegion region : regions) {
      // Get the path for the region, allowing holes for nested (enclosed)
      // regions
      Color color = defaultColors[region.getRegionIndex()];
      g2d.setColor(color);
      Path2D path = region.getPath2D(af);
      g2d.fill(path);
      g2d.draw(path);
    }

    // Overlay the regions with the contours, in gray
    g2d.setColor(Color.gray);
    for (Contour contour : contours) {
      Path2D path = contour.getPath2D(af);
      g2d.draw(path);
    }

   
    File output = new File("ExampleContour.png");
    System.out.println("\nWriting output image file to "+output.getAbsolutePath());
    try {
      ImageIO.write(bImage, "PNG", output);
    } catch (IOException ioex) {
      System.out.println("Demonstration failed, I/O exception writeing image: "
              + ioex.getMessage());
    }

  }

}
