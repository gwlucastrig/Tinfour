/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 * 07/2019  G. Lucas     Created
 *
 * Notes:
 *
 */

package org.tinfour.demo.examples;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.tinfour.common.Vertex;
import org.tinfour.demo.utils.TestVertices;
import org.tinfour.voronoi.BoundedVoronoiBuildOptions;
import org.tinfour.voronoi.BoundedVoronoiDiagram;
import org.tinfour.voronoi.BoundedVoronoiDrawingUtility;
import org.tinfour.voronoi.BoundedVoronoiStylerDefault;

/**
 * Provides an example main method showing how to plot a graphic
 * of a Voronoi Diagram from a set of randomly generated vertices
 */
public class ExampleVoronoi {

    /**
     * Write an example Voronoi Diagram to the current working directory.
     *
     * @param args the command line arguments (not used)
     * @throws java.io.IOException if unable to write an output file
     */
    public static void main(String[] args) throws IOException {
        
        // Make a set of specified number of random vertices.
        int nVertices = 50;
        List<Vertex> vList = TestVertices.makeRandomVertices(nVertices, 0);

        // Specify options to automatically assign colors to the 
        // cells in the Voronoi Diagram. Colors are selected so that
        // all adjacent cells have different colors. Six colors are used.
        // Internally, color-assignments are stored using the 
        // "auxiliary index" element for each vertex. This is an numerical
        // value from 0 to 5.  Actual color values are applied by the
        // render logic which maps these index values to colors.
        BoundedVoronoiBuildOptions options = new BoundedVoronoiBuildOptions();
        options.enableAutomaticColorAssignment(true);

        // Cerate the diagram.
        BoundedVoronoiDiagram diagram = new BoundedVoronoiDiagram(vList, options);

        // Create a 1000-by-1000 pixel image. A margin of 5 pixels is
        // reserved along all outer edges.
        // 
        int width = 1000;
        int height = 1000;
        int padding = 5;  // specifies margin size
        BufferedImage bImage = new BufferedImage(
                width, 
                height, 
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bImage.createGraphics();

        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, width, height);

        // Construct a "styler" which provides options for rendering
        // the Voronoi Diagram. In practice, the code for drawing a
        // Voronoi Diagram (or a Delaunay Triangulation) is not difficult
        // to write. The rendering utility is used here to keep this
        // examaple brief.
        BoundedVoronoiStylerDefault defaultStyler
                = new BoundedVoronoiStylerDefault();
        
        BoundedVoronoiDrawingUtility drawUtility
                = new BoundedVoronoiDrawingUtility(
                        diagram,
                        width,
                        height,
                        padding,
                        null // optional bounds, not used in this example
                );

        drawUtility.draw(g2d, defaultStyler);
        g2d.dispose();
        
        
        // Write the output file to the current directory
        File outputFile = new File("ExampleVoronoi.png");
        ImageIO.write(bImage, "PNG", outputFile);
    }

}
