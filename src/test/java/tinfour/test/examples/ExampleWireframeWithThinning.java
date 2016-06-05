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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SimpleTimeZone;
import javax.imageio.ImageIO;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.TestPalette;
import tinfour.test.utils.VertexLoader;
import tinfour.standard.IncrementalTin;
import tinfour.utils.HilbertSort;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
public class ExampleWireframeWithThinning implements IDevelopmentTest
{

    static String[] mandatoryOptions = {
        "-in"
    };

    AffineTransform initTransform(int width, int height, double x0, double x1, double y0, double y1)
    {
        // The goal is to create an AffineTransform that will map coordinates
        // from the source data to image (pixel) coordinates).  We wish to
        // render the rectangle defined by the source coordinates on an
        // image with as large a scale as possible.  However, the shapes of
        // the image and the data rectangles may be different (one may be
        // wide and one may be narrow).  So we need to use the aspect ratios
        // of the two rectangles to determine whether to fit it to the
        // vertical axis or to fit it to the horizontal.

        double rImage = (double) width / (double) height;
        double rData = (x1 - x0) / (y1 - y0);
        double rAspect = rImage / rData;

        double uPerPixel; // units of distance per pixel
        if (rAspect >= 1) {
            // the shape of the image is fatter than that of the
            // data, the limiting factor is the vertical extent
            uPerPixel = (y1 - y0) / height;
        } else { // r<1
            // the shape of the image is skinnier than that of the
            // data, the limiting factor is the horizontal extent
            uPerPixel = (x1 - x0) / width;
        }
        double scale = 1.0 / uPerPixel;

        double xCenter = (x0 + x1) / 2.0;
        double yCenter = (y0 + y1) / 2.0;
        double xOffset = width / 2 - scale * xCenter;
        double yOffset = height / 2 + scale * yCenter;

        AffineTransform af;
        af = new AffineTransform(scale, 0, 0, -scale, xOffset, yOffset);

        return af;
    }

    /**
     * Run the example code accepting an input LAS file and rendering
     * a wire-frame diagram of the resulting triangular mesh, thinning
     * the sample points so that a minimum pixel spacing is maintained.
     *
     * @param ps a valid print-stream for recording results of processing.
     * @param args a set of arguments for configuring the processing.
     * @throws IOException if unable to read input or write output files.
     */
    @Override
    public void runTest(PrintStream ps, String[] args) throws IOException
    {

        long time0, time1;
        Date date = new Date();
        SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
        sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
        ps.println("ExampleWireframeWithThinning\n");
        ps.format("Date/time of test: %s (UTC)\n", sdFormat.format(date));

        // Load Options ---------------------------------------------
        //   The TestOptions class is designed to provide a convenient way
        // of collecting the most commonly used options in the TinFour
        // test suite.

        TestOptions options = new TestOptions();

        boolean[] optionsMatched = options.argumentScan(args);
        options.checkForMandatoryOptions(args, mandatoryOptions);

        // process special options that are used by this example program:
        Integer width
                = options.scanIntOption(args, "-width", optionsMatched, 500);
        Integer height
                = options.scanIntOption(args, "-height", optionsMatched, 500);
        int separationInPixels = 20;

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
        String outputPath = "None";
        if(outputFile != null){
          outputPath = outputFile.getAbsolutePath();
        }
        ps.format("Input file:  %s\n", inputFile.getAbsolutePath());
        ps.format("Output file: %s\n", outputPath);
        VertexLoader loader = new VertexLoader();
        List<Vertex> vertexList = loader.readInputFile(options);
        int nVertices = vertexList.size();
        double xmin = loader.getXMin();
        double xmax = loader.getXMax();
        double ymin = loader.getYMin();
        double ymax = loader.getYMax();
        double zmin = loader.getZMin();
        double zmax = loader.getZMax();
        ps.format("Number of vertices: %8d\n", nVertices);
        ps.format("Range x values:     %11.3f, %11.3f, (%f)\n", xmin, xmax, xmax - xmin);
        ps.format("Range y values:     %11.3f, %11.3f, (%f)\n", ymin, ymax, ymax - ymin);
        ps.format("Range z values:     %11.3f, %11.3f, (%f)\n", zmin, zmax, zmax - zmin);
        ps.flush();

        double x0 = xmin;
        double x1 = xmax;
        double y0 = ymin;
        double y1 = ymax;

        if (isFrameSet) {
            // a frame is specified
            x0 = frame[0];
            x1 = frame[1];
            y0 = frame[2];
            y1 = frame[3];
            ps.format("Frame x values:     %11.3f, %11.3f, (%f)\n", x0, x1, (x1 - x0));
            ps.format("Frame y values:     %11.3f, %11.3f, (%f)\n", y0, y1, (y1 - y0));
        }

        AffineTransform af = initTransform(width, height, x0, x1, y0, y1);
        double scale = Math.sqrt(Math.abs(af.getDeterminant()));
        ps.format("Data to image scale: %11.3f\n", scale);



        // Give the available image area, estimate how many points can
        // it can contain based on specificated average point spacing.
        // Even if we are frameing on a sub-region of the data samples,
        // we will build the TIN for the whole sample set because we wish
        // the rendering to include any edges that may run from outside
        // the rendering area to the inside. So when we compute the point
        // spacing, we work off the extent of the complete data set.
        //   Because the target spacing is given in pixels, the estimated
        // number of points must be based on pixel coordinates, not
        // data coordinates.  So in the block that follows, use the
        // affine transform to map data coordinates to pixels.  We
        // map the corners of the rectangle covering the domain of the
        // raw data to pixels, then compute the area of the transformed
        // rectangle.  This area will be given in pixels squared.  If a
        // frame-in is being applied, it may actually be larger than the
        // size of the image that we are going to build.
        Point2D llCorner = new Point2D.Double(xmin, ymin); // total range
        Point2D urCorner = new Point2D.Double(xmax, ymax);
        af.transform(llCorner, llCorner);
        af.transform(urCorner, urCorner);
        double dWidth = urCorner.getX() - llCorner.getX();
        double dHeight = llCorner.getY() - urCorner.getY(); // reversed order
        double area = dWidth * dHeight;

        // To estimate the number of points that can fit into the image,
        // we make the simplifying assumption that the triangular mesh will
        // be organized into a regular tesselation of equilateral triangles.
        // The area of each triangle will be s^2 * sqrt(3)/4 where s is
        // the side of the triangle. For a Delaunay triangulation containing
        // n vertices, there are 2*n triangles.
        //   So solve for n where area = 2 * n * s^2 * sqrt(3)/4;
        double s = separationInPixels;
        double k = area / (s * s * 0.866);

        // only thin the vertex list if k is sufficiently smaller than
        // the number of vertices we wish to plot.
        if (k < nVertices * 0.9) {
            time0 = System.nanoTime();

            HilbertSort hilbertSort = new HilbertSort();
            hilbertSort.sort(vertexList);
            int n = (int) k;
            List<Vertex> thinList = new ArrayList<>();
            for (int i = 0; i <= n; i++) {
                int index = (int) ((i / k) * nVertices + 0.5);
                if (index >= nVertices) {
                    break;
                }
                thinList.add(vertexList.get(index));
            }
            vertexList = thinList;
            nVertices = vertexList.size();
            time1 = System.nanoTime();
            ps.format("Time to thin vertices (milliseconds):       %11.3f\n",
                    (time1 - time0) / 1000000.0);
            ps.format("Number of thinned vertices:                 %7d\n",
                    nVertices);
        }


        IIncrementalTin tin;
        if (options.isTinClassSet()) {
            tin = options.getNewInstanceOfTestTin();
        } else {
            tin = new IncrementalTin();
        }

        ps.format("\nBuilding TIN using: %s\n", tin.getClass().getName());
        time0 = System.nanoTime();
        tin.add(vertexList, null);
        time1 = System.nanoTime();
        ps.format("Time to process vertices (milliseconds):    %11.3f\n",
                (time1 - time0) / 1000000.0);

        // Resolve the optional output file options
        if (outputFile == null) {
            return; // all done
        }

        String name = outputFile.getName();
        String fmt = "PNG";
        int i = name.lastIndexOf('.');
        if (i > 0) {
            fmt = name.substring(i + 1, name.length());
            if ("PNG".equalsIgnoreCase(fmt)) {
                fmt = "PNG";
            } else if ("JPEG".equalsIgnoreCase(fmt) || "JPG".equalsIgnoreCase(fmt)) {
                fmt = "JPEG";
            } else if ("GIF".equalsIgnoreCase(fmt)) {
                fmt = "GIF";
            } else {
                throw new IllegalArgumentException(
                        "Output file must be one of PNG, JPEG, or GIF\n");
            }
        }

        BufferedImage bImage = render(
                ps,
                af,
                width, height,
                x0, x1, y0, y1,
                zmin, zmax, tin, vertexList);

        ImageIO.write(bImage, fmt, outputFile);
        ps.println("Example application processing complete.");
    }

    /**
     * Provides the main method for an example application that draws
     * a color-coded image of a triangulated irregular network.
     * <p>
     * Data is accepted from an LAS file. For best results, the file should be
     * in a projected coordinate system rather than a geographic coordinate
     * system. In general, geographic coordinate systems are a poor choice for
     * Lidar data processing since they are non-isotropic, however many data
     * sources provide them in this form.
     * <p>
     * Command line arguments include the following:
     * <pre>
     *   -in     &lt;file path&gt;    input LAS file
     *   -out    &lt;file path&gt;    optional output image file
     *   -width  &lt;width&gt;   optional image width, default 500
     *   -height &lt;height&gt;  optional image height, default 500
     *   -frame   &lt;xmin xmax ymin ymax&gt;  frame into sub-region of data
     *
     *    Other arguments used by Tinfour test programs are supported
     * </pre>
     *
     * @param args command line arguments indicating the input LAS file for
     * processing and various output options.
     */
    public static void main(String[] args)
    {
        ExampleWireframeWithThinning example = new ExampleWireframeWithThinning();

        try {
            example.runTest(System.out, args);
        } catch (IOException | IllegalArgumentException ex) {
            ex.printStackTrace(System.err);
        }
    }

    private boolean inBounds(Vertex p, double x0, double x1, double y0, double y1)
    {
        double x = p.getX();
        double y = p.getY();
        return x0 <= x && x <= x1 && y0 <= y && y <= y1;
    }

    BufferedImage render(
            PrintStream ps,
            AffineTransform af,
            int width, int height,
            double x0, double x1, double y0, double y1,
            double zMin, double zMax,
            IIncrementalTin tin, List<Vertex> vertexList)
            throws IOException
    {

        BufferedImage bImage =
             new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bImage.createGraphics();
        g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, width, height);

        Point2D llCorner = new Point2D.Double(x0, y0);
        Point2D urCorner = new Point2D.Double(x1, y1);
        af.transform(llCorner, llCorner);
        af.transform(urCorner, urCorner);

        Rectangle2D clipBounds = new Rectangle2D.Double(
                llCorner.getX(), urCorner.getY(),
                urCorner.getX() - llCorner.getX(),
                llCorner.getY() - urCorner.getY());
        g2d.setColor(Color.darkGray);
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.draw(clipBounds);
        g2d.setClip(clipBounds);

        TestPalette palette = TestPalette.getRedToYellowToWhitePalette();
        //TestPalette palette = TestPalette.getBlueToYellowPalette();
        Point2D p0 = new Point2D.Double();
        Point2D p1 = new Point2D.Double();
        Line2D l2d = new Line2D.Double();
        Ellipse2D e2d = new Ellipse2D.Double();

        g2d.setStroke(new BasicStroke(2.0f));

        int nEdgeLen = 0;
        double sumEdgeLen = 0;
        List<IQuadEdge> edges = tin.getEdges();
        for (IQuadEdge edge : edges) {
            Vertex v0 = edge.getA();
            Vertex v1 = edge.getB();
            if (v0 == null || v1 == null) {
                continue; // skip the ghost edges
            }

            if (inBounds(v0, x0, x1, y0, y1) || inBounds(v1, x0, x1, y0, y1)) {
                p0.setLocation(v0.getX(), v0.getY());
                p1.setLocation(v1.getX(), v1.getY());
                af.transform(p0, p0);
                af.transform(p1, p1);
                nEdgeLen++;
                sumEdgeLen += p0.distance(p1);
                l2d.setLine(p0, p1);
                double z0 = v0.getZ();
                double z1 = v1.getZ();
                Color c0 = palette.getColor(z0, zMin, zMax);
                Color c1 = palette.getColor(z1, zMin, zMax);
                GradientPaint paint = new GradientPaint(
                        (float) v0.getX(), (float) v0.getY(), c0,
                        (float) v1.getX(), (float) v1.getY(), c1);
                g2d.setPaint(paint);
                g2d.draw(l2d);
            }
        }

        double avgLen = 0;
        if (nEdgeLen > 0) {
            avgLen = sumEdgeLen / nEdgeLen;
        }
        ps.format("Number of edges rendered:                   %7d\n", nEdgeLen);
        ps.format("Average length of edges:                    %11.3f\n", avgLen);
        g2d.setStroke(new BasicStroke(1.0f));
        for (Vertex v : vertexList) {
            if (inBounds(v, x0, x1, y0, y1)) {
                p0.setLocation(v.getX(), v.getY());
                double z = v.getZ();
                int argb = palette.getARGB(z, zMin, zMax);
                Color c = new Color(argb);
                g2d.setColor(c);
                af.transform(p0, p1);
                e2d.setFrame(p1.getX() - 2, p1.getY() - 2, 6.0f, 6.0f);
                g2d.fill(e2d);
                g2d.draw(e2d);
            }
        }

        g2d.setColor(Color.darkGray);
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.setClip(null);
        g2d.draw(clipBounds);
        return bImage;
    }

}
