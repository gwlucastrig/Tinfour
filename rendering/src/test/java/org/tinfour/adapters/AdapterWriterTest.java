/*
 * Copyright 2025 Gary W. Lucas.
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
 */
package org.tinfour.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.tinfour.geom.GeoRectangle;
import org.tinfour.svg.SvgDocumentWriter;
import org.tinfour.svg.SvgPathWriter;
import org.tinfour.utils.rendering.AwtPathWriter;
import org.w3c.dom.Document;

/**
 * Verifies the AWT and SVG implementations of GeometryWriter reproduce the
 * geometry they are given, including polygon holes and an applied transform.
 */
public class AdapterWriterTest {

  private static final double EPS = 1.0e-9;

  @Test
  public void awtPathWriterBuildsShellAndHoleWithTransform() {
    // translate model coordinates by (+10, +20)
    AffineTransform t = AffineTransform.getTranslateInstance(10, 20);
    AwtPathWriter w = new AwtPathWriter(t);
    w.addPolygon(
            new double[]{0, 0, 4, 0, 4, 4, 0, 4},
            new double[][]{{1, 1, 2, 1, 2, 2, 1, 2}});
    Path2D path = w.getPath2D();
    assertEquals(Path2D.WIND_EVEN_ODD, path.getWindingRule());

    int[] expectedTypes = {
      PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO, PathIterator.SEG_LINETO,
      PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE,
      PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO, PathIterator.SEG_LINETO,
      PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE};
    // first vertices of shell and hole, transformed
    double[] firstShell = {10, 20};
    double[] firstHole = {11, 21};

    PathIterator it = path.getPathIterator(null);
    double[] c = new double[6];
    int i = 0;
    boolean checkedShellStart = false;
    boolean checkedHoleStart = false;
    while (!it.isDone()) {
      int type = it.currentSegment(c);
      assertEquals("segment " + i, expectedTypes[i], type);
      if (type == PathIterator.SEG_MOVETO && !checkedShellStart) {
        assertEquals(firstShell[0], c[0], EPS);
        assertEquals(firstShell[1], c[1], EPS);
        checkedShellStart = true;
      } else if (type == PathIterator.SEG_MOVETO && !checkedHoleStart) {
        assertEquals(firstHole[0], c[0], EPS);
        assertEquals(firstHole[1], c[1], EPS);
        checkedHoleStart = true;
      }
      i++;
      it.next();
    }
    assertEquals(expectedTypes.length, i);
    assertTrue(checkedShellStart && checkedHoleStart);
  }

  @Test
  public void svgPathWriterEmitsExpectedData() {
    SvgPathWriter open = new SvgPathWriter();
    open.addPolyline(new double[]{0, 0, 1, 0, 1, 1}, false);
    assertEquals("M0.0 0.0 L1.0 0.0 L1.0 1.0", open.getPathData());

    SvgPathWriter poly = new SvgPathWriter();
    poly.addPolygon(new double[]{0, 0, 2, 0, 2, 2},
            new double[][]{{0.5, 0.5, 1.0, 0.5, 1.0, 1.0}});
    assertEquals(
            "M0.0 0.0 L2.0 0.0 L2.0 2.0 Z M0.5 0.5 L1.0 0.5 L1.0 1.0 Z",
            poly.getPathData());
  }

  @Test
  public void svgDocumentIsWellFormedWithExpectedPaths() throws Exception {
    StringWriter sw = new StringWriter();
    SvgDocumentWriter doc = new SvgDocumentWriter(
            sw, new GeoRectangle(0, 0, 10, 10), 200, 200);
    doc.begin();
    // a triangle and a square, written directly through the writer
    doc.addPath(w -> w.addPolygon(new double[]{0, 0, 10, 0, 0, 10}, null),
            "fill=\"#cccccc\" stroke=\"black\" stroke-width=\"0.1\"");
    doc.addPath(w -> w.addPolygon(new double[]{2, 2, 6, 2, 6, 6, 2, 6}, null),
            "fill=\"none\" stroke=\"red\" stroke-width=\"0.1\"");
    doc.finish();

    String svg = sw.toString();
    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document parsed = db.parse(new ByteArrayInputStream(
            svg.getBytes(StandardCharsets.UTF_8)));
    assertEquals("svg", parsed.getDocumentElement().getNodeName());
    assertEquals(2, parsed.getElementsByTagName("path").getLength());
  }
}
