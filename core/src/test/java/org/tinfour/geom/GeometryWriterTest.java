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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.SimpleTriangleIterator;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

/**
 * Verifies that the path-producing core types describe their geometry to a
 * {@link GeometryWriter} correctly (correct primitive, coordinates, and
 * closed/hole structure), in model coordinates.
 */
public class GeometryWriterTest {

  /** A recording GeometryWriter that captures every call for inspection. */
  private static class Recorder implements GeometryWriter {

    final List<double[]> polylines = new ArrayList<>();
    final List<Boolean> polylineClosed = new ArrayList<>();
    final List<double[]> polygonShells = new ArrayList<>();
    final List<double[][]> polygonHoles = new ArrayList<>();

    @Override
    public void addPolyline(double[] xy, boolean closed) {
      polylines.add(xy);
      polylineClosed.add(closed);
    }

    @Override
    public void addPolygon(double[] shell, double[][] holes) {
      polygonShells.add(shell);
      polygonHoles.add(holes);
    }
  }

  private static final double EPS = 1.0e-12;

  @Test
  public void polygonConstraintWritesClosedPolygonNoHoles() {
    PolygonConstraint p = new PolygonConstraint();
    p.add(new Vertex(0, 0, 0));
    p.add(new Vertex(4, 0, 0));
    p.add(new Vertex(4, 3, 0));
    p.complete();

    Recorder r = new Recorder();
    p.writeTo(r);

    assertTrue(r.polylines.isEmpty(), "no polylines expected");
    assertEquals(1, r.polygonShells.size(), "one polygon expected");
    assertArrayEquals(new double[]{0, 0, 4, 0, 4, 3}, r.polygonShells.get(0), EPS);
    assertTrue(r.polygonHoles.get(0) == null
            || r.polygonHoles.get(0).length == 0, "no holes expected");
  }

  @Test
  public void linearConstraintWritesOpenPolyline() {
    LinearConstraint c = new LinearConstraint();
    c.add(new Vertex(1, 2, 0));
    c.add(new Vertex(3, 5, 0));
    c.add(new Vertex(7, 1, 0));
    c.complete();

    Recorder r = new Recorder();
    c.writeTo(r);

    assertTrue(r.polygonShells.isEmpty(), "no polygons expected");
    assertEquals(1, r.polylines.size(), "one polyline expected");
    assertFalse(r.polylineClosed.get(0), "polyline must be open");
    assertArrayEquals(new double[]{1, 2, 3, 5, 7, 1}, r.polylines.get(0), EPS);
  }

  @Test
  public void simpleTriangleWritesClosedTriangleNoHoles() {
    // a TIN of exactly three vertices contains a single triangle
    IncrementalTin tin = new IncrementalTin(1.0);
    List<Vertex> v = new ArrayList<>();
    v.add(new Vertex(0, 0, 0, 0));
    v.add(new Vertex(1, 0, 0, 1));
    v.add(new Vertex(0, 1, 0, 2));
    tin.add(v, null);

    SimpleTriangleIterator it = new SimpleTriangleIterator(tin);
    assertTrue(it.hasNext(), "expected at least one triangle");
    SimpleTriangle t = it.next();

    Recorder r = new Recorder();
    t.writeTo(r);

    assertTrue(r.polylines.isEmpty(), "no polylines expected");
    assertEquals(1, r.polygonShells.size(), "one polygon expected");
    double[] shell = r.polygonShells.get(0);
    assertEquals(6, shell.length, "a triangle has three vertices");
    assertTrue(r.polygonHoles.get(0) == null
            || r.polygonHoles.get(0).length == 0, "no holes expected");

    // the three emitted vertices must be the three input vertices (the winding
    // order is determined by the triangulation, so compare as a set)
    Set<String> emitted = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      emitted.add(shell[i * 2] + "," + shell[i * 2 + 1]);
    }
    Set<String> expected = new HashSet<>();
    expected.add("0.0,0.0");
    expected.add("1.0,0.0");
    expected.add("0.0,1.0");
    assertEquals(expected, emitted);
  }
}
