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
package org.tinfour.geom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import org.junit.Test;

/**
 * Verifies that the dependency-free org.tinfour.geom types reproduce the
 * numerical behavior of their java.awt.geom counterparts. The AWT classes
 * serve as the reference implementation.
 */
public class GeoVsAwtFidelityTest {

  private static final double EPS = 1.0e-12;

  @Test
  public void affineTransformMatchesAwt() throws NoninvertibleTransformException,
          GeoNoninvertibleTransformException {
    // a non-trivial transform with scale, shear, and translation
    double m00 = 1.5, m10 = 0.25, m01 = -0.75, m11 = 2.0, m02 = 10.0, m12 = -4.0;
    AffineTransform awt = new AffineTransform(m00, m10, m01, m11, m02, m12);
    GeoAffineTransform geo = new GeoAffineTransform(m00, m10, m01, m11, m02, m12);

    double[] src = {0, 0, 1, 0, 0, 1, 3.5, -2.25, 100, 200};
    double[] awtDst = new double[src.length];
    double[] geoDst = new double[src.length];
    awt.transform(src, 0, awtDst, 0, src.length / 2);
    geo.transform(src, 0, geoDst, 0, src.length / 2);
    for (int i = 0; i < src.length; i++) {
      assertEquals("transform element " + i, awtDst[i], geoDst[i], EPS);
    }

    // in-place transform into a different offset of the same array (the
    // pattern used by the Tinfour core classes)
    double[] awtArr = new double[8];
    double[] geoArr = new double[8];
    System.arraycopy(src, 0, awtArr, 0, 4);
    System.arraycopy(src, 0, geoArr, 0, 4);
    awt.transform(awtArr, 0, awtArr, 4, 2);
    geo.transform(geoArr, 0, geoArr, 4, 2);
    for (int i = 0; i < 8; i++) {
      assertEquals("in-place element " + i, awtArr[i], geoArr[i], EPS);
    }

    // inverse transform
    AffineTransform awtInv = awt.createInverse();
    GeoAffineTransform geoInv = geo.createInverse();
    double[] awtIm = new double[src.length];
    double[] geoIm = new double[src.length];
    awtInv.transform(src, 0, awtIm, 0, src.length / 2);
    geoInv.transform(src, 0, geoIm, 0, src.length / 2);
    for (int i = 0; i < src.length; i++) {
      assertEquals("inverse element " + i, awtIm[i], geoIm[i], EPS);
    }
  }

  @Test(expected = GeoNoninvertibleTransformException.class)
  public void degenerateTransformThrows() throws GeoNoninvertibleTransformException {
    // a transform with a zero determinant (collapses to a line)
    new GeoAffineTransform(1, 1, 1, 1, 0, 0).createInverse();
  }

  @Test
  public void rectangleMatchesAwt() {
    // add a sequence of points and compare with Rectangle2D semantics,
    // mirroring the "setRect then add" pattern used by Tinfour
    double[][] pts = {{3, 4}, {-2, 9}, {7, 1}, {3, 4}, {0, 0}};
    Rectangle2D awt = new Rectangle2D.Double();
    GeoRectangle geo = new GeoRectangle();
    awt.setRect(pts[0][0], pts[0][1], 0, 0);
    geo.setRect(pts[0][0], pts[0][1], 0, 0);
    for (int i = 1; i < pts.length; i++) {
      awt.add(pts[i][0], pts[i][1]);
      geo.add(pts[i][0], pts[i][1]);
    }
    assertEquals(awt.getX(), geo.getX(), EPS);
    assertEquals(awt.getY(), geo.getY(), EPS);
    assertEquals(awt.getWidth(), geo.getWidth(), EPS);
    assertEquals(awt.getHeight(), geo.getHeight(), EPS);
    assertEquals(awt.getMinX(), geo.getMinX(), EPS);
    assertEquals(awt.getMaxX(), geo.getMaxX(), EPS);
    assertEquals(awt.getCenterX(), geo.getCenterX(), EPS);
    assertEquals(awt.getCenterY(), geo.getCenterY(), EPS);

    // add(rectangle)
    Rectangle2D awtB = new Rectangle2D.Double(-10, -10, 2, 2);
    GeoRectangle geoB = new GeoRectangle(-10, -10, 2, 2);
    awt.add(awtB);
    geo.add(geoB);
    assertEquals(awt.getMinX(), geo.getMinX(), EPS);
    assertEquals(awt.getMaxY(), geo.getMaxY(), EPS);

    // intersects / contains across a grid of probe rectangles
    Rectangle2D awtR = new Rectangle2D.Double(0, 0, 10, 10);
    GeoRectangle geoR = new GeoRectangle(0, 0, 10, 10);
    double[][] probes = {{5, 5, 2, 2}, {-5, -5, 3, 3}, {9, 9, 5, 5},
      {0, 0, 10, 10}, {2, 2, 4, 4}, {10, 10, 1, 1}};
    for (double[] p : probes) {
      Rectangle2D awtP = new Rectangle2D.Double(p[0], p[1], p[2], p[3]);
      GeoRectangle geoP = new GeoRectangle(p[0], p[1], p[2], p[3]);
      assertEquals("intersects " + p[0] + "," + p[1],
              awtR.intersects(awtP), geoR.intersects(geoP));
      assertEquals("contains rect " + p[0] + "," + p[1],
              awtR.contains(awtP), geoR.contains(geoP));
    }

    // contains(point) and isEmpty
    assertEquals(awtR.contains(5, 5), geoR.contains(5, 5));
    assertEquals(awtR.contains(10, 10), geoR.contains(10, 10));
    assertEquals(awtR.contains(-1, 5), geoR.contains(-1, 5));
    assertEquals(new Rectangle2D.Double(0, 0, 0, 5).isEmpty(),
            new GeoRectangle(0, 0, 0, 5).isEmpty());
    assertTrue(new GeoRectangle().isEmpty());
  }
}
