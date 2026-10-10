/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 00/2025  G. Lucas     Created
 *
 * Notes:
 *
 *   Demonstrates the toolkit-neutral GeometryWriter SPI: a single set of
 *   Tinfour geometry objects (contour regions and contour lines) is rendered
 *   to two completely different outputs -- a Java2D raster image (PNG) and a
 *   vector SVG document -- using two interchangeable GeometryWriter adapters.
 *   The core objects describe themselves the same way to both; only the
 *   adapter differs.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.demo.examples;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.common.Vertex;
import org.tinfour.contour.Contour;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.geom.GeoRectangle;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.svg.SvgDocumentWriter;
import org.tinfour.utils.rendering.AwtPathWriter;
import org.tinfour.utils.rendering.RenderingSurfaceAid;

/**
 * Renders the same Tinfour geometry to both a PNG image (via the AWT adapter)
 * and an SVG document (via the SVG adapter), illustrating that the core
 * geometry is independent of any particular rendering toolkit.
 */
public class ExampleRenderToImageAndSvg {

  private static final String[] FILL_COLORS = {
    "#fff2b3", "#ffb3e6", "#ffd9b3", "#dddddd",
    "#ffc6d9", "#9be89b", "#ff9999", "#99b3ff"};

  // a small surface, with a couple of basins, used to generate contours
  private static final String[] INPUT = {
    "4 4 4 4 4 4 3 3 3",
    "4 4 4 0 4 4 4 4 4",
    "4 4 0 0 4 4 4 4 4",
    "2 2 2 2 2 2 2 2 2",
    "2 2 2 2 2 2 2 2 2",
    "0 0 0 0 0 0 0 0 0",
    "2 2 0 0 0 2 0 0 0"};

  private static final double[] Z_CONTOUR = {1, 3};

  public static void main(String[] args) throws IOException {
    // build a TIN from a small regular grid (y inverted so the picture
    // matches the text layout above)
    int nRows = INPUT.length;
    int nCols = INPUT[0].length() / 2;
    List<Vertex> vList = new ArrayList<>(nRows * nCols);
    for (int iRow = 0; iRow < nRows; iRow++) {
      double y = (nRows - 1) - iRow;
      for (int iCol = 0; iCol < nCols; iCol++) {
        int z = INPUT[iRow].charAt(iCol * 2) - '0';
        vList.add(new Vertex(iCol, y, z, vList.size()));
      }
    }
    IncrementalTin tin = new IncrementalTin(1.0);
    tin.add(vList, null);

    ContourBuilderForTin builder
            = new ContourBuilderForTin(tin, null, Z_CONTOUR, true);
    List<ContourRegion> regions = builder.getRegions();
    List<Contour> contours = builder.getContours();

    renderToPng(tin, regions, contours, new File("ExampleRender.png"));
    renderToSvg(tin, regions, contours, new File("ExampleRender.svg"));
    System.out.println("Wrote ExampleRender.png and ExampleRender.svg");
  }

  /**
   * Renders the geometry to a raster image using the AWT GeometryWriter adapter.
   */
  private static void renderToPng(
          IncrementalTin tin, List<ContourRegion> regions,
          List<Contour> contours, File output) throws IOException {
    GeoRectangle b = tin.getBounds();
    int width = 650;
    int height = 650;
    RenderingSurfaceAid rsa = new RenderingSurfaceAid(
            width, height, 10,
            b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY());
    rsa.fillBackground(Color.white);
    BufferedImage image = rsa.getBufferedImage();
    Graphics2D g2d = rsa.getGraphics2D();
    AffineTransform af = rsa.getCartesianToPixelTransform();

    for (ContourRegion region : regions) {
      // the AWT adapter turns the region (outer ring + holes) into a Path2D
      Path2D path = AwtPathWriter.toPath2D(region, af);
      g2d.setColor(colorForRegion(region.getRegionIndex()));
      g2d.fill(path);
    }
    g2d.setColor(Color.gray);
    for (Contour contour : contours) {
      g2d.draw(AwtPathWriter.toPath2D(contour, af));
    }
    ImageIO.write(image, "PNG", output);
  }

  /**
   * Renders the same geometry to an SVG document using the SVG GeometryWriter
   * adapter. The geometry objects are handed to the document writer directly;
   * the vertical flip and view box are derived from the model bounds.
   */
  private static void renderToSvg(
          IncrementalTin tin, List<ContourRegion> regions,
          List<Contour> contours, File output) throws IOException {
    GeoRectangle b = tin.getBounds();
    try (Writer w = new FileWriter(output)) {
      SvgDocumentWriter svg = new SvgDocumentWriter(w, b, 650, 650);
      svg.begin();
      for (ContourRegion region : regions) {
        String color = FILL_COLORS[region.getRegionIndex() % FILL_COLORS.length];
        svg.addPath(region, "fill=\"" + color + "\" stroke=\"none\"");
      }
      for (Contour contour : contours) {
        svg.addPath(contour,
                "fill=\"none\" stroke=\"gray\" stroke-width=\"0.03\"");
      }
      svg.finish();
    }
  }

  private static Color colorForRegion(int regionIndex) {
    Color[] colors = {
      Color.YELLOW, Color.MAGENTA, Color.ORANGE, Color.LIGHT_GRAY,
      Color.PINK, Color.GREEN.brighter(), Color.RED, Color.BLUE};
    return colors[regionIndex % colors.length];
  }
}
