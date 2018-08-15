/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * Date Name Description
 * ------   --------- -------------------------------------------------
 * 07/2018  G. Lucas  Initial implementation 
 *
 * Notes:
 *   At this time, the only reason that this class retains the defining
 *   Delaunay Triangulation after the structure is constructed is to 
 *   provide an efficient way for performing polygon lookups.  Given an
 *   efficient alternate, there would be no reason to retain the TIN.
 *
 * -----------------------------------------------------------------------
 */
package tinfour.voronoi;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import tinfour.common.Circumcircle;
import tinfour.common.GeometricOperations;
import tinfour.common.IIncrementalTin;
import tinfour.common.INeighborEdgeLocator;
import tinfour.common.IQuadEdge;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;
import tinfour.edge.EdgePool;
import tinfour.edge.QuadEdge;

/**
 * Constructs a Voronoi Diagram structure from a populated instance of an
 * IncrementalTin. The resulting structure is "limited" in the sense that it
 * covers only a finite domain on the coordinate plane (unlike a true Voronoi
 * Diagram, which covers an infinite domain).
 * <p>
 * <strong>This class is under development and is subject to changes in its API
 * and behavior.</strong>
 */
public class LimitedVoronoi {

  /**
   * The overall domain of the structure
   */
  final private Rectangle2D bounds;

  /**
   * The Delaunay Triangulation associated with the structure.
   */
  final private IIncrementalTin tin;

  final private EdgePool edgePool;

  final private List<Vertex> circleList = new ArrayList<>();

  final private List<ThiessenPolygon> polygons = new ArrayList<>();

  private double maxRadius = -1;

  private final int[] edgeToPolygon;

  private final INeighborEdgeLocator locator;
  
  private final  GeometricOperations geoOp;

  /**
   * Constructs an instance of a Voronoi Diagram that corresponds to the input
   * Delaunay Triangulation.
   *
   * @param delaunayTriangulation a valid instance of a Delaunay Triangulation
   * implementation.
   */
  public LimitedVoronoi(IIncrementalTin delaunayTriangulation) {
    if (delaunayTriangulation == null) {
      throw new IllegalArgumentException("Null input is not allowed for TIN");
    }
    if (!delaunayTriangulation.isBootstrapped()) {
      throw new IllegalArgumentException("Input TIN is not bootstrapped (populated)");
    }

    Rectangle2D r2d = delaunayTriangulation.getBounds();
    this.bounds = new Rectangle2D.Double(
            r2d.getX(),
            r2d.getY(),
            r2d.getWidth(),
            r2d.getHeight());

    this.tin = delaunayTriangulation;
    edgePool = new EdgePool();

    int maxEdgeIndex = tin.getMaximumEdgeAllocationIndex() + 1;
    edgeToPolygon = new int[maxEdgeIndex];
    Arrays.fill(edgeToPolygon, -1);
    locator = tin.getNeighborEdgeLocator();
    
    Thresholds thresholds = tin.getThresholds();
    geoOp = new GeometricOperations(thresholds);

    buildStructure();
  }

  private void buildPart(IQuadEdge e, Vertex[] center, IQuadEdge[] part) {
    // the parts are built so that the part associated with an edge index
    // is a segment from outside the triangle to its circumcircle center
    // in cases where the center is inside the triangle, this is 
    // essentially "outside to inside", though that is often not true.
    IQuadEdge d = e.getDual();
    int eIndex = e.getIndex();
    int dIndex = d.getIndex();
    Vertex v0 = center[dIndex];
    Vertex v1 = center[eIndex];
    if (v0 != null && v1 != null) {
      IQuadEdge n = edgePool.allocateEdge(v0, v1);
      part[eIndex] = n;
      part[dIndex] = n.getDual();
    }
  }

  /**
   * Build an edge based on the ray outward from the associated circumcenter and
   * perpendicular to the perimeter edge
   *
   * @param e a perimeter edge
   * @param center the array of circumcenters
   * @param part the array to store parts
   */
  private void buildPerimeterRay(IQuadEdge e, Vertex[] center, IQuadEdge[] part) {
    int index = e.getIndex();
    Vertex vCenter = center[index]; // vertex at the circumcenter
    Vertex A = e.getA();
    Vertex B = e.getB();
    double x0 = bounds.getMinX();
    double x1 = bounds.getMaxX();
    double y0 = bounds.getMinY();
    double y1 = bounds.getMaxY();

    // construct and edge from the outside to the inside.
    //   the edge we construct is based on an infinite ray outward
    // from the circumcircle and perpendicular to the perimeter edge.
    // because we will be constructing on the RIGHT side of the 
    // perimeter edge rather than the left, the perpendicular vector
    // for the ray will be (eY, -eX).   Because we cannot handle 
    // infinite rays, we need to clip the ray to form a segment 
    // running from the circumcircle center to the bounds.  it is 
    // possible that the ray could intersect two edges (a horizontal edge
    // and a vertical edge), so we want to find the first intersection
    // the one closest to the center.
    double eX = B.getX() - A.getX();
    double eY = B.getY() - A.getY();
    double u = Math.sqrt(eX * eX + eY * eY);
    double uX = eY / u;
    double uY = -eX / u;
    double cX = vCenter.getX();
    double cY = vCenter.getY();
    double tX = Double.POSITIVE_INFINITY;
    double tY = Double.POSITIVE_INFINITY;
    double x = Double.NaN;
    double y = Double.NaN;
    double z = Double.NaN;
    // in the following, we screen out the uX==0 and uY==0 cases
    // because the they only intersect one edge
    if (uX < 0) {
      // off to the left
      tX = (x0 - cX) / uX;
      x = x0;
    } else if (uX > 0) {
      tX = (x1 - cX) / uX;
      x = x1;
    }
    if (uY < 0) {
      tY = (y0 - cY) / uY;
      y = y0;
    } else if (tY > 0) {
      tY = (y1 - cY) / uY;
      y = y1;
    }
    if (tX < tY) {
      // find the y corresponding to x = tX*uX+cX;
      y = tX * uY + cY;
      double s = (y - y0) / (y1 - y0);
      if (uX < 0) {
        // the left side, descending
        z = 4 - s;
      } else {
        // the right edge, ascending
        z = 1 + s;
      }
    } else {
      // find the x correspoinding to y = tY*uY+cY
      x = tY * uX + cX;
      double s = (x - x0) / (x1 - x0);
      if (uY < 0) {
        z = s;
      } else {
        z = 3 - s;
      }
    }

    // the negative vertex index is just a diagnostic/debugging tool.
    Vertex vOut = new Vertex(x, y, z, -vCenter.getIndex());

    QuadEdge n = edgePool.allocateEdge(vOut, vCenter); // from out to in
    part[index] = n;
    part[index ^ 0x01] = n.getDual();

  }

  private int mindex(IQuadEdge e, IQuadEdge f, IQuadEdge r) {
    int index = e.getIndex();
    if (f.getIndex() < index) {
      index = f.getIndex();
    }
    if (r.getIndex() < index) {
      return r.getIndex();
    } else {
      return index;
    }
  }

  private void buildCenter(Circumcircle cCircle, IQuadEdge e, Vertex[] centers) {
    int index = e.getIndex();
    if (centers[index] == null) {
      Vertex A = e.getA();
      Vertex B = e.getB();
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();
      Vertex C = e.getForward().getB();
      if (C != null) {
        cCircle.compute(A, B, C);
        if (!cCircle.compute(A, B, C)) {
          throw new IllegalStateException(
                  "Internal error, triangle does not yield circumcircle");
        }
        double x = cCircle.getX();
        double y = cCircle.getY();
        double z = Double.NaN;
        Vertex v = new Vertex(x, y, z, mindex(e, f, r));
        centers[e.getIndex()] = v;
        centers[f.getIndex()] = v;
        centers[r.getIndex()] = v;
        circleList.add(v);
        double radius = cCircle.getRadius();
        if (radius > maxRadius) {
          maxRadius = radius;
        }
        bounds.add(x, y);
      }
    }
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private void buildStructure() {

    // The visited array tracks which of the TIN edges were 
    // visited for various processes.  It is used more than once.
    // There should be one part for each non-ghost edge.   The part
    // array is indexed using the tin edge index so that
    //    correspondingPart = part[edge.getIndex()]
    int maxEdgeIndex = tin.getMaximumEdgeAllocationIndex() + 1;
    boolean[] visited = new boolean[maxEdgeIndex];
    Vertex[] centers = new Vertex[maxEdgeIndex];
    QuadEdge[] parts = new QuadEdge[maxEdgeIndex];
    List<IQuadEdge> scratch = new ArrayList<>();

    // build the circumcircle-center vertices 
    // also collect some information about the overall
    // bounds and edge length of the input TIN.
    Circumcircle cCircle = new Circumcircle();
    Iterator<IQuadEdge> edgeIterator = tin.getEdgeIterator();
    double sumEdgeLength = 0;
    int nEdgeLength = 0;
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      if (e.getA() == null || e.getB() == null) {
        // ghost edge, do not process
        // mark both sides as visited to suppress future checks.
        int index = e.getIndex();
        visited[index] = true;
        visited[index ^ 0x01] = true;
        continue;
      }
      sumEdgeLength += e.getLength();
      nEdgeLength++;
      buildCenter(cCircle, e, centers);
      buildCenter(cCircle, e.getDual(), centers);
    }

    double avgLen = sumEdgeLength / nEdgeLength;
    double x0 = bounds.getMinX() - avgLen / 4;
    double x1 = bounds.getMaxX() + avgLen / 4;
    double y0 = bounds.getMinY() - avgLen / 4;
    double y1 = bounds.getMaxY() + avgLen / 4;
    bounds.setRect(x0, y0, x1 - x0, y1 - y0);

    // perimeter edges get special treatment because they give rise
    // to an infinite ray outward from circumcenter
    List<IQuadEdge> perimeter = tin.getPerimeter();
    for (IQuadEdge p : perimeter) {
      visited[p.getIndex()] = true;
      buildPerimeterRay(p, centers, parts);
    }

    edgeIterator = tin.getEdgeIterator();
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      IQuadEdge d = e.getDual();
      int eIndex = e.getIndex();
      int dIndex = d.getIndex();
      if (visited[eIndex]) {
        continue;
      }
      visited[eIndex] = true;
      visited[dIndex] = true;
      buildPart(e, centers, parts);
    }

    // reset the visited array, set all the ghost edges
    // to visited so that they are not processed below
    Arrays.fill(visited, false);
    for (IQuadEdge e : perimeter) {
      int index = e.getForward().getIndex();
      visited[index] = true;
      visited[index ^ 0x01] = true;
    }

    int polygonIndex = 0;

    // first build the open loops starting at perimeters edges
    for (IQuadEdge e : perimeter) {
      int index = e.getIndex();
      if (visited[index]) {
        continue;
      }
      scratch.clear();
      Vertex hub = e.getA();
      polygonIndex = polygons.size();
      QuadEdge prior = null;
      QuadEdge first = null;
      for (IQuadEdge p : e.pinwheel()) {
        index = p.getIndex();
        visited[index] = true;
        edgeToPolygon[index] = polygonIndex;
        QuadEdge q = parts[index];
        if (q == null) {
          // we've reached the exterior, the pinwheel would
          // continue out to ghost edges, but we don't want them.
          //System.out.println("");
          //for (IQuadEdge m : eList) {
          //    System.out.println("   " + m.toString());
          //}
          Vertex vLast = prior.getB();
          Vertex vFirst = first.getA();
          int iLast = (int) vLast.getZ();
          int iFirst = (int) vFirst.getZ();
          if (iFirst < iLast) {
            // it wraps around the lower-left corner
            iFirst += 4;
          }
          // construct edges as necessary to connect vLast to vFirst
          for (int i = iLast + 1; i <= iFirst; i++) {
            double x = 0;
            double y = 0;
            // anding with 0x03 is equivalent to modulus 4
            int iCorner = i & 0x03;
            if (iCorner == 0) {
              // lower-left corner
              x = x0;
              y = y0;
            } else if (iCorner == 1) {
              x = x1;
              y = y0;
            } else if (iCorner == 2) {
              x = x1;
              y = y1;
            } else {
              // iCorner == 3
              x = x0;
              y = y1;
            }

            Vertex v = new Vertex(x, y, Double.NaN, -1);
            v.setSynthetic(true);
            QuadEdge n = edgePool.allocateEdge(vLast, v);
            n.setSynthetic(true);
            n.setReverse(prior);
            vLast = v;
            prior = n;
            scratch.add(n);
          }
          QuadEdge n = edgePool.allocateEdge(vLast, vFirst);
          n.setSynthetic(true);
          n.setReverse(prior);
          first.setReverse(n);
          scratch.add(n);
          break;
        }
        scratch.add(q);
        if (prior == null) {
          first = q;
        } else {
          q.setReverse(prior);
        }
        prior = q;
      }
      polygons.add(new ThiessenPolygon(hub, scratch, true));
    }

    edgeIterator = tin.getEdgeIterator();
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      int index = e.getIndex();
      if (!visited[index] && parts[index] != null) {
        scratch.clear();
        Vertex hub = e.getA();
        polygonIndex = polygons.size();
        QuadEdge prior = null;
        QuadEdge first = null;
        for (IQuadEdge p : e.pinwheel()) {
          index = p.getIndex();
          visited[index] = true;
          edgeToPolygon[index] = polygonIndex;
          QuadEdge q = parts[p.getIndex()];
          scratch.add(q);
          if (prior == null) {
            first = q;
          } else {
            q.setReverse(prior);
          }
          prior = q;
        }
        if (first != null && prior != null) {
          first.setReverse(prior);
          polygons.add(new ThiessenPolygon(hub, scratch, false));
        }
      }

      IQuadEdge d = e.getDual();
      index = d.getIndex();
      if (!visited[index] && parts[index] != null) {
        scratch.clear();
        Vertex hub = d.getA();
        polygonIndex = polygons.size();
        QuadEdge prior = null;
        QuadEdge first = null;
        for (IQuadEdge p : d.pinwheel()) {
          index = p.getIndex();
          visited[index] = true;
          edgeToPolygon[index] = polygonIndex;
          QuadEdge q = parts[p.getIndex()];
          scratch.add(q);
          if (prior == null) {
            first = q;
          } else {
            q.setReverse(prior);
          }
          prior = q;
        }
        if (first != null && prior != null) {
          first.setReverse(prior);
          polygons.add(new ThiessenPolygon(hub, scratch, false));
        }
      }
    }
  }

  /**
   * Prints diagnostic statistics for the Voronoi Diagram object.
   *
   * @param ps a valid print stream instance.
   */
  public void printDiagnostics(PrintStream ps) {
    int nClosed = 0;
    double sumArea = 0;
    for (ThiessenPolygon p : polygons) {
      double a = p.getArea();
      if (!Double.isInfinite(a)) {
        sumArea += a;
        nClosed++;
      }
    }
    int nOpen = polygons.size() - nClosed;
    ps.format("Limited Voronoi Diagram%n");
    ps.format("   Polygons:   %8d%n", polygons.size());
    ps.format("     Open:     %8d%n", nOpen);
    ps.format("     Closed:   %8d%n", nClosed);
    ps.format("     Avg Area: %13.4f%n", sumArea / nClosed);
    ps.format("   Vertices:   %8d%n", circleList.size());
    ps.format("   Edges:      %8d%n", edgePool.size());
    ps.format("   Voronoi Bounds%n");
    ps.format("      x min:  %16.4f%n", bounds.getMinX());
    ps.format("      y min:  %16.4f%n", bounds.getMinY());
    ps.format("      x max:  %16.4f%n", bounds.getMaxX());
    ps.format("      y max:  %16.4f%n", bounds.getMaxY());
    Rectangle2D r2d = tin.getBounds();
    ps.format("   Input Triangulation Bounds%n");
    ps.format("      x min:  %16.4f%n", r2d.getMinX());
    ps.format("      y min:  %16.4f%n", r2d.getMinY());
    ps.format("      x max:  %16.4f%n", r2d.getMaxX());
    ps.format("      y max:  %16.4f%n", r2d.getMaxY());
    ps.format("   Max Circumcircle Radius:  %6.4f%n", maxRadius);
  }

  /**
   * Gets the bounds of the limited Voronoi Diagram. If there are "skinny"
   * triangles along the perimeter, the bounds may be substantially larger than
   * those of the original TIN.
   *
   * @return a valid rectangle
   */
  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(
            bounds.getX(),
            bounds.getY(),
            bounds.getWidth(),
            bounds.getHeight());
  }

  /**
   * Gets a list of the edges in the Voronoi Diagram
   *
   * @return a valid list
   */
  public List<IQuadEdge> getEdges() {
    return edgePool.getEdges();
  }

  /**
   * Gets the vertices that were created to produce the Voronoi Diagram. The
   * output does not include the original vertices from the TIN.
   *
   * @return a valid list of vertices
   */
  public List<Vertex> getVoronoiVertices() {
    List<Vertex> list = new ArrayList<>(circleList.size());
    list.addAll(circleList);
    return list;
  }

  /**
   * Gets a list of the polygons that comprise the Voronoi Diagram
   *
   * @return a valid list of polygons
   */
  public List<ThiessenPolygon> getPolygons() {
    List<ThiessenPolygon> list = new ArrayList<>(polygons.size());
    list.addAll(polygons);
    return list;
  }


  /**
   * Gets the polygon that contains the specified coordinate point (x,y).
   * <p>
   * <strong>Note: </strong>Although a true Voronoi Diagram covers the entire
   * plane, the Limited Voronoi class is has a finite domain. If the specified
   * coordinates are outside the bounds of this instance, no polygon will be
   * found and a null result will be returned.
   *
   * @param x a valid floating point value
   * @param y a valid floating point value
   * @return the containing polygon or a null if none is found.
   */
  public ThiessenPolygon getContainingPolygon(double x, double y) {
    if(!bounds.contains(x, y)){
      return null;
    }
    IQuadEdge edge = locateEdge(x, y);
    if (edge == null) {
      return null;
    }

    int polygonIndex = edgeToPolygon[edge.getIndex()];
    if (polygonIndex < 0) {
      return null;
    }
    return polygons.get(polygonIndex);
  }

  /**
   * Locate an edge which begins with the specified vertex.
   *
   * @param locator the edge locator associated with the tin
   * @param v a valid vertex
   * @return a valid edge
   */
  private IQuadEdge locateEdge(double x, double y) {
    // We have in memory an edge-to-polygon table which 
    // maps the index of an edge from the Delaunay Triangulation
    // to a polygon.  So, to find the polygon that contains (x,y),
    // we need to find an edge that starts with the vertex that 
    // 
    // Tinfour provides a utility called INeighborEdgeLocator that
    // efficiently traverses the Delaunay Triangulation and finds
    // the triangle that contains (x,y).  Since Tinfour doesn't
    // implement a class or data element that represents a triangle,
    // the output from the locator is an edge. That edge belongs to
    // the triangle that contains (x,y).   Now, which of the three edges
    // that it picks is arbitrary.   So the edge it produces 
    // may not be the edge that is closes to (x,y).
    // In fact, the triangle itself may not necessary include
    // the vertex that is closest to (x,y).  If (x,y) is close
    // to an edge, the opposite vertex from the adjacent triangle may be closer
    // than the one in the triangle.
    //    Thus the logic below has to test both the vertices of the
    // containing triangle -- A, B, C -- and those from the immediately
    // adjacent triangles to see which one is closest.  Vertex A and B
    // are guaranteed to not be null, but many of the others could be
    // null.  So the tests below do perform null checks.

    IQuadEdge e = locator.getNeigborEdge(x, y);
    if (e == null) {
      return null;  // not expected to happen
    }
    IQuadEdge f = e.getForward();
    IQuadEdge r = e.getReverse();


    Vertex A = e.getA();
    Vertex B = f.getA();
    Vertex C = r.getA();
    if (C == null) {
      // the query point is outside the convex hull of the Delaunay Triangulation
      // the locator produced the exterior-side of the perimeter edge.
      // we need to take its dual so that we pick up the inside vertex C.
      e = e.getDual();
      f = e.getForward();
      r = e.getReverse();
      A = e.getA();
      B = f.getA();
      C = r.getA();
    }
    
    IQuadEdge d = e.getReverseFromDual();
    IQuadEdge g = f.getReverseFromDual();
    IQuadEdge h = r.getReverseFromDual();
    Vertex D = d.getA();
    Vertex G = g.getA();
    Vertex H = h.getA();
     
    double dA = A.getDistanceSq(x, y);
    double dB = B.getDistanceSq(x, y);
    
    double minD2;
    IQuadEdge minEdge;
    
    // we're guaranteed that A,B are not null. But after that,
    // all bets are off.
    if (dA < dB) {
      minD2 = dA;
      minEdge = e;
    } else {
      minD2 = dB;
      minEdge = f;
    }

    if (C != null) {
      double dC = C.getDistanceSq(x, y);
      if (dC < minD2) {
        minD2 = dC;
        minEdge = r;
      }
    }
    
    if (D != null) {
      double dD = D.getDistanceSq(x, y);
      if (dD < minD2) {
        minD2 = dD;
        minEdge = d;
      }
    }
    
    if (G != null) {
      double dG = G.getDistanceSq(x, y);
      if (dG < minD2) {
        minD2 = dG;
        minEdge = g;
      }
    }
    
    if (H != null) {
      double dH = H.getDistanceSq(x, y);
      if (dH < minD2) {
        minD2 = dH;
        minEdge = h;
      }
    }

    return minEdge;
            
     
    // There is a flaw in the edge locator.getNeighborEdge() logic. 
    // When (x,y) is outside the convex hull for the Delaunay Triangulation,
    // it does not necessarily locate the best edge (the edge that most
    // nearly subtends the coordinate point).   So we have to do some 
    // extra work.  This is a problem that should be improved later.
    // 
    // Compare distances for edge e with those from the prior and next
    // perimeter edges.
//    IQuadEdge p = r.getReverseFromDual();   // prior
//    IQuadEdge n = f.getForwardFromDual(); // next
//    TestResult eTest = testPerimeterEdge(e, x, y);
//    TestResult pTest = testPerimeterEdge(p, x, y);
//    TestResult nTest = testPerimeterEdge(n, x, y);
//
//    if(eTest.d2<pTest.d2){
//      // compare eTest with nTest
//      if(eTest.d2<nTest.d2){
//        return eTest.edge;
//      }else{
//        return nTest.edge;
//      }
//    }else{
//      if(pTest.d2<nTest.d2){
//        return pTest.edge;
//      }else{
//        return nTest.edge;
//      }
//    }
 
  }
  
  private static class TestResult {
    IQuadEdge edge;
    double d2;
    TestResult(IQuadEdge edge, double d2){
      this.edge = edge;
      this.d2 = d2;
    }
  }
  
  private TestResult testPerimeterEdge(IQuadEdge edge, double x, double y){
    IQuadEdge e = edge.getDual();
    IQuadEdge f = e.getForward();
    IQuadEdge r = e.getReverse();

    double dA = e.getA().getDistanceSq(x, y);
    double dB = f.getA().getDistanceSq(x, y);
    double dC = r.getA().getDistanceSq(x, y);

    if (dA < dB) {
      if (dA < dC) {
        return new TestResult(e, dA); // vertex A is closest to (x,y)
      } else {
        return new TestResult(r, dC); // vertex C is closest to (x,y)
      }
    } else if (dB < dC) {
      return new TestResult(f, dB); // vertex B is closes to (x,y)
    } else {
      return new TestResult(r, dC); // vertex C is closest to (x,y)
    }
  }
   
}
