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
 * 08/2018  G. Lucas  Added vertex based constructor and build options
 *
 * Notes:
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
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import tinfour.edge.EdgePool;
import tinfour.edge.QuadEdge;
import tinfour.semivirtual.SemiVirtualIncrementalTin;
import tinfour.standard.IncrementalTin;
import tinfour.utils.TinInstantiationUtility;

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
   * The overall bounds of the sample points
   */
  final private Rectangle2D sampleBounds;

  final private EdgePool edgePool;

  final private List<Vertex> circleList = new ArrayList<>();

  final private List<ThiessenPolygon> polygons = new ArrayList<>();

  private double maxRadius = -1;

  private LimitedVoronoi() {
    // a private constructor to deter applications from
    // invoking the default constructor
    sampleBounds = null;
    bounds = null;
    edgePool = null;
  }

  /**
   * Construct a Voronoi Diagram structure based on the input vertex set.
   *
   * @param vertexList a valid list of vertices
   * @param options optional specification for setting build parameters or a
   * null to use defaults.
   *
   */
  public LimitedVoronoi(List<Vertex> vertexList, LimitedVoronoiBuildOptions options) {
    if (vertexList == null) {
      throw new IllegalArgumentException(
              "Null input not allowed for constructor");
    }

    int nVertices = vertexList.size();
    if (nVertices < 3) {
      throw new IllegalArgumentException(
              "Insufficent input size, at least 3 vertices are required");
    }

    sampleBounds = new Rectangle2D.Double(
            vertexList.get(0).getX(),
            vertexList.get(0).getY(),
            0, 0);

    for (Vertex v : vertexList) {
      sampleBounds.add(v.getX(), v.getY());
    }

    // estimate a nominal point spacing based on the domain of the
    // input data set and assuming a rougly uniform density.
    // the value 0.866 is based on the parameters of a regular
    // hexagonal tesselation of a plane
    double area = sampleBounds.getWidth() * sampleBounds.getHeight();
    double nominalPointSpacing = Math.sqrt(area / nVertices / 0.866);
    TinInstantiationUtility maker
            = new TinInstantiationUtility(0.25, vertexList.size());
    IIncrementalTin tin = maker.constructInstance(nominalPointSpacing);
    tin.add(vertexList, null);
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Input vertex geometry is insufficient "
              + "to establish a Voronoi Diagram");
    }

    this.bounds = new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());

    edgePool = new EdgePool();

    LimitedVoronoiBuildOptions pOptions = options;
    if (options == null) {
      pOptions = new LimitedVoronoiBuildOptions();
    }
    buildStructure(tin, pOptions);
  }

  /**
   * Constructs an instance of a Voronoi Diagram that corresponds to the input
   * Delaunay Triangulation.
   *
   * @param delaunayTriangulation a valid instance of a Delaunay Triangulation
   * implementation.
   */
  public LimitedVoronoi(IIncrementalTin delaunayTriangulation) {
    if (delaunayTriangulation == null) {
      throw new IllegalArgumentException(
              "Null input is not allowed for TIN");
    }
    if (!delaunayTriangulation.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Input TIN is not bootstrapped (populated)");
    }

    sampleBounds = delaunayTriangulation.getBounds();
    this.bounds = new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());

    edgePool = new EdgePool();
    LimitedVoronoiBuildOptions pOptions = new LimitedVoronoiBuildOptions();
    buildStructure(delaunayTriangulation, pOptions);
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
    double z;
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
  private void buildStructure(
          IIncrementalTin tin,
          LimitedVoronoiBuildOptions pOptions) 
  {
    List<IQuadEdge> perimeter = tin.getPerimeter();
    Circumcircle cCircle = new Circumcircle();
    if (pOptions.enableAdjustments) {
      double w = sampleBounds.getWidth();
      double h = sampleBounds.getHeight();
      double diagonal = Math.sqrt(w * w + h * h);
      double threshold = pOptions.adjustmentThreshold * diagonal;
      int iPerimeter = 0;
      while (iPerimeter < perimeter.size()) {
        IQuadEdge p = perimeter.get(iPerimeter);
        Vertex A = p.getA();
        Vertex B = p.getB();
        Vertex C = p.getForward().getB();
        cCircle.compute(A, B, C);
        if (cCircle.getRadius() > threshold) {
          adjustPerimeterEdge(tin, perimeter, iPerimeter);
          iPerimeter += 2;
        } else {
          iPerimeter++;
        }
      }
    }

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
      IQuadEdge f = e.getForwardFromDual();
      int index = f.getIndex();
      //int index = e.getForwardFromDual().getIndex();
      visited[index] = true;
      visited[index ^ 0x01] = true;
    }

    // first build the open loops starting at perimeters edges
    for (IQuadEdge e : perimeter) {
      int index = e.getIndex();
      if (visited[index]) {
        continue;
      }
      scratch.clear();
      Vertex hub = e.getA();
      QuadEdge prior = null;
      QuadEdge first = null;
      for (IQuadEdge p : e.pinwheel()) {
        index = p.getIndex();
        visited[index] = true;
        QuadEdge q = parts[index];
        if (q == null) {
          // we've reached the exterior, the pinwheel would
          // continue out to ghost edges, but we don't want them.
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
        QuadEdge prior = null;
        QuadEdge first = null;
        for (IQuadEdge p : e.pinwheel()) {
          index = p.getIndex();
          visited[index] = true;
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
        QuadEdge prior = null;
        QuadEdge first = null;
        for (IQuadEdge p : d.pinwheel()) {
          index = p.getIndex();
          visited[index] = true;
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
    ps.format("   Max Circumcircle Radius:  %6.4f%n", maxRadius);
    ps.format("   Data Sample Bounds%n");
    ps.format("      x min:  %16.4f%n", sampleBounds.getMinX());
    ps.format("      y min:  %16.4f%n", sampleBounds.getMinY());
    ps.format("      x max:  %16.4f%n", sampleBounds.getMaxX());
    ps.format("      y max:  %16.4f%n", sampleBounds.getMaxY());
  }

  /**
   * Gets the bounds of the limited Voronoi Diagram. If the associated Delaunay
   * Triangulation included "skinny" triangles along its perimeter, the Voronoi
   * Diagram's bounds may be substantially larger than those of the original
   * input data set
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
   * Gets the bounds of the sample data set. These will usually be smaller than
   * the bounds of the overall structure.
   *
   * @return a valid rectangle
   */
  public Rectangle2D getSampleBounds() {
    return new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());
  }

  /**
   * Gets a list of the edges in the Voronoi Diagram. Applications are
   * <strong>strongly cautioned against modifying these edges.</strong>
   *
   * @return a valid list of edges
   */
  public List<IQuadEdge> getEdges() {
    return edgePool.getEdges();
  }

  /**
   * Gets a list of the vertices that define the Voronoi Diagram. This list is
   * based on the input set, though in some cases coincident or nearly
   * coincident vertices will be combined into a single vertex of type
   * VertexMergerGroup.
   *
   * @return a valid list
   */
  public List<Vertex> getVertices() {
    List<Vertex> vList = new ArrayList<>(polygons.size());
    for (ThiessenPolygon p : polygons) {
      vList.add(p.getVertex());
    }
    return vList;
  }

  /**
   * Gets the vertices that were created to produce the Voronoi Diagram. The
   * output does not include the original vertices from the input source
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
    // The containing polygon is simply the one with the vertex
    // closest to the specified coordinates (x,y).
    ThiessenPolygon minP = null;
    if (bounds.contains(x, y)) {
      double minD = Double.POSITIVE_INFINITY;

      for (ThiessenPolygon p : polygons) {
        Vertex v = p.getVertex();
        double d = v.getDistanceSq(x, y);
        if (d < minD) {
          minD = d;
          minP = p;
        }
      }
    }
    return minP;
  }
 
  void adjustPerimeterEdge(
          IIncrementalTin tin,
          List<IQuadEdge> perimeter,
          int index) 
  {
    IQuadEdge e = perimeter.get(index);
    IQuadEdge f = e.getForward();
    IQuadEdge r = e.getReverse();
    IQuadEdge fd = f.getDual();
    IQuadEdge rd = r.getDual();
    if (tin instanceof IncrementalTin) {
      ((IncrementalTin) tin).collapsePerimeterEdge(e);
    } else if (tin instanceof SemiVirtualIncrementalTin) {
      ((SemiVirtualIncrementalTin) tin).collapsePerimeterEdge(e);
    }
    perimeter.set(index, rd);
    perimeter.add(index + 1, fd);
  }
}
