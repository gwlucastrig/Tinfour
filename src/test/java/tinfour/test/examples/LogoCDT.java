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
 * 11/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.examples;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.PolygonConstraint;
import tinfour.common.Vertex;
import tinfour.test.utils.TestOptions;

/**
 * A simple demonstrator class to plot text characters in the form of a
 * Constrained Delaunay Triangulation.
 */
final public class LogoCDT {

  /**
   * A private constructor to indicate that instances of this simple
   * demonstration class are not desirable.
   */
  private LogoCDT() {

  }

  /**
   * Run the demonstrator. Default options are:
   * <ul>
   * <li> -text &lt;String&gt; The text to display, default: "CDT" </li>
   * <li> -tinClass &lt;class name&gt; The Incremental tin class to use,
   * default: standard </li>
   * </ul>
   *
   * @param args a valid, potentially zero-length array
   */
  public static void main(String[] args) {
    TestOptions options = new TestOptions();
    boolean[] optionsMatched = options.argumentScan(args);

    // set up text, using "CDT" as the default
    String text = "CDT";
    String test = options.scanStringOption(args, "-text", optionsMatched);
    if (test != null) {
      text = test;
    }

    Class<?> tinClass = options.getTinClass();
    String title = "Constrained Delaunay Triangulation -- "
            + tinClass.getSimpleName();
    List<IConstraint> outlineList = getOutlineConstraints(text);
    IIncrementalTin tin = options.getNewInstanceOfTestTin();
    tin.addConstraints(outlineList, true);
    LogoPanel.plot(tin, title);
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private static List<IConstraint> getOutlineConstraints(String text) {
    // We wish to create a series of Tinfour constraints
    // based on the outlines of the characters in the specified
    // text.  The Java API has a nice way to do this by using the
    // TextLayout class.  TextLayout will produce a Java shape and
    // we can use a "flattening path iterator" to reduce it to a set
    // of line segments.  We can then extract the vertices from the
    // segments and feed them to Tinfour.
    //   A couple of nuances here:
    //     a. Java uses graphic coordinates, which give
    //         the origin as the upper-left corner of the display
    //         with the y-axis increasing DOWNWARD>
    //         Tinfour wants Cartesian coordinates which treats the
    //         y-axis as increasing upward.  So we need to flip
    //         the coordinates using an AffineTransform.
    //     b. The flattening path iterator wants a "flattening" parameter
    //         indicating the degree of flattening in a curve.  Here we
    //         picked 0.25 as an arbitrary value (it means about 1/4 pixel).
    //     c. Some characters include multiple polygons.  The main
    //         polygons will be given in counter-clockwise order. For those
    //         with holes (such as the letter 'o'), the inner polygon will
    //         be ordered in clockwise order.
    //
    //  While it would have been simpler to create the entire text
    //  as a single shape, we wish to be able to assign individual colors
    //  to each character.  So we loop through one-character-at-a-time.
    //  The Java TextLayout does have provision for computing the spacing
    //  for blank characters, so we use the spacing for a period as an 
    //  alternative to the getVisibleAdvance().

    List<IConstraint> pList = new ArrayList<>();
    FontRenderContext frc = new FontRenderContext(null, true, true);
    Font font = new Font("Arial Black", Font.BOLD, 72);

    // compute a color adjustment factor based on the number of characters.
    double deltaHue;
    if (text.length() > 1) {
      deltaHue = 0.75 / (text.length() - 1);
    } else {
      deltaHue = 0;
    }

    // compute spacing for blanks.
    TextLayout layout = new TextLayout(".", font, frc);
    double spaceAdvance = layout.getVisibleAdvance();

    // loop on characters and generate constraints.
    int xOffset = 0;
    for (int iText = 0; iText < text.length(); iText++) {
      int iclx = Color.HSBtoRGB((float) (iText * deltaHue), 1.0f, 1.0f);
      Color clx = new Color(iclx | 0xff000000, true);
      AffineTransform flipY = new AffineTransform(1, 0, 0, -1, xOffset, 0);
      String subtext = text.substring(iText, iText + 1);
      if (Character.isWhitespace(text.charAt(iText))) {
        xOffset += spaceAdvance;
        continue;
      }
      layout = new TextLayout(subtext, font, frc);
      xOffset += layout.getVisibleAdvance();

      Shape shape = layout.getOutline(new AffineTransform());
      PathIterator path = shape.getPathIterator(flipY, 0.25);

      double[] d = new double[6];
      int k = 0;
      List<Vertex> vList = new ArrayList<>();
      while (!path.isDone()) {
        k++;
        int flag = path.currentSegment(d);
        switch (flag) {
          case PathIterator.SEG_MOVETO:
            vList.clear();
            vList.add(new Vertex(d[0], d[1], 0, k)); // NOPMD
            break;
          case PathIterator.SEG_LINETO:
            vList.add(new Vertex(d[0], d[1], 0, k));  // NOPMD
            break;
          case PathIterator.SEG_CLOSE:
            // the list of vertices for the character outline polygon
            // has been completed.  Java produces these polygons in
            // counterclockwise order for filled polygons and clockwise
            // order for interior "hole" polygons. This convention is the
            // same as used by Tinfour.  But because Java uses
            // graphic coordinates and Tinfour wants Cartesian coordinates,
            // we flipped the coordinate system across the y axis.
            // This action reversed the orientation of the polygons.
            // So we need to reverse their order to restore the orientation
            // to the proper form for Tinfour.
            PolygonConstraint poly = new PolygonConstraint(); // NOPMD
            poly.setApplicationData(clx);
            int n = vList.size();
            for (int i = n - 1; i >= 0; i--) {
              Vertex v = vList.get(i);
              poly.add(v);
            } poly.complete();
            pList.add(poly);
            break;
          default:
            break;
        }
        path.next();
      }
    }
    return pList;
  }
}
