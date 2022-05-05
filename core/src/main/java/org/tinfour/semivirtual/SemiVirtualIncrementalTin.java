/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * ------ --------- -------------------------------------------------
 * 09/2015  G. Lucas     Started implementation adapting IncrementalTIN
 *                          to use virtual edges.
 * 11/2015  G. Lucas     Completed implementation using virtual edges.
 * 12/2016  G. Lucas     Implemented ability to add constraint geometries to
 *                         produce a Constrained Delaunay Triangulation (CDT).
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.semivirtual;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import org.tinfour.common.BootstrapUtility;
import org.tinfour.common.GeometricOperations;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IIntegrityCheck;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.INeighborEdgeLocator;
import org.tinfour.common.INeighborhoodPointsCollector;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.SimpleTriangleIterator;
import org.tinfour.common.Thresholds;
import org.tinfour.common.TriangleCount;
import org.tinfour.common.Vertex;
import org.tinfour.common.VertexIterator;
import org.tinfour.common.VertexMergerGroup;
import org.tinfour.edge.QuadEdgeConstants;

/**
 * Provides a memory-conserving variation on the IncrementalTin class
 * for building and maintaining a Triangulated Irregular Network (TIN)
 * that is optimal with regard to the Delaunay criterion. For background
 * information and tactics for using this class,
 * see the documentation for the IncrementalTin class.
 * <p>
 * The counterpart to this class, IncrementalTin, uses a direct implementation
 * of the quad-edge structure popularized by Guibas and Stolfi. While that
 * approach leads to elegant code, the nature of the Java language
 * (and object-oriented languages in general) results in relatively expensive
 * memory requirements, approximately 244 bytes per vertex inserted into the TIN
 * (counting the storage for both edges and vertices). Since it is common for
 * many geophysical data sets to include multiple-millions of points,
 * that memory use adds up fast.
 * <p>
 * This class reduces memory requirements by representing
 * the edge-link relationships with internal integer arrays
 * and numerical indexing rather than object references. By keeping the edge
 * relationship data in "virtual form", the implementation reduces the
 * memory use for the edges to about 120 bytes per vertex.
 * The implementation is "semi-virtual" in that all data is still kept
 * in core, but the amount of memory is reduced by a virtual representation
 * of the edge structures.
 * <p>
 * Unfortunately, this reduction comes with a cost. In testing, the
 * virtual implementation requires approximately 30 percent more runtime
 * to process vertices than the direct implementation. Both
 * implementations experience a degradation of throughput when the
 * memory use approaches the maximum allowed by the JVM (specified
 * on the command line using the -Xmx#### option). But since the
 * virtual implementation uses half the memory of the direct implementation,
 * the onset of degraded conditions when working with a fixed memory size
 * can be pushed back considerably using the virtual implementation.
 * <p>
 * This class also dramatically reduces the number of objects that
 * are used to represent the TIN. The IncrementalTIN class uses about 7.005
 * objects per vertex (including vertices and edges) while the virtual
 * implementation uses only about 1.012. This reduction can be
 * valuable in reducing the impact of garbage collection when processing
 * large data sets..
 * <h1>Methods and References</h1>
 * A good review of point location using a stochastic Lawson's walk
 * is provided by <cite>Soukal, R.; Ma&#769;lkova&#769;, Kolingerova&#769;
 * (2012)
 * "Walking algorithms for point location in TIN models", Computational
 * Geoscience 16:853-869</cite>.
 * <p>
 * The Bower-Watson algorithm for point insertion is discussed in
 * <cite>Cheng, Siu-Wing; Dey, T.; Shewchuk, J. (2013) "Delaunay mesh
 * generation",
 * CRC Press, Boca Raton, FL</cite>. This is a challenging book that provides
 * an overview of both 2D and solid TIN models. Jonathan Shewchuk is pretty much
 * the expert on Delaunay Triangulations and his writings were a valuable
 * resource in the creation of this class.
 * You can also read Bowyer's and Watson's original papers both of which
 * famously appeared in the same issue of the same journal in 1981. See
 * <cite>Bowyer, A. (1981) "Computing Dirichlet tesselations",
 * The Computer Journal" Vol 24, No 2., p. 162-166</cite>. and
 * <cite>Watson, D. (1981) "Computing the N-dimensional tesselation
 * with application to Voronoi Diagrams", The Computer Journal"
 * Vol 24, No 2., p. 167-172</cite>.
 * <p>
 * The point-removal algorithm is due to Devillers. See
 * <cite>Devillers, O. (2002), "On deletion in delaunay triangulations",
 * International Journal of Computational Geometry &amp; Applications
 * 12.3 p. 123-2005</cite>.
 * <p>
 * The QuadEdge concept is based on the structure popularized by
 * <cite>Guibas, L. and Stolfi, J. (1985) "Primitives for the
 * manipulation of subdivisions and the computation of Voronoi diagrams"
 * ACM Transactions on Graphics, 4(2), 1985, p. 75-123.</cite>
 */
public class SemiVirtualIncrementalTin implements IIncrementalTin {

  /**
   * Number of sides for an edge (2 of course).
   */
  private static final int N_SIDES = 2;  //NOPMD

  /**
   * A temporary list of vertices maintained until the TIN is successfully
   * bootstrapped, and then discarded.
   */
  private List<Vertex> vertexList;

  /**
   * A list of the vertex merger groups created when identical or nearly
   * identical vertices are inserted.
   */
  private final List<VertexMergerGroup> coincidenceList = new ArrayList<>();

  private final List<IConstraint> constraintList = new ArrayList<>();
  /**
   * The collection of edges using the classic object-pool concept.
   */
  private final SemiVirtualEdgePool edgePool;
  /**
   * The edge used to preserve the end-position of the
   * most recent search results.
   */
  private SemiVirtualEdge searchEdge;

  /**
   * Indicates that the TIN is locked and that calls to
   * add or remove vertices are disabled. This can occur when the
   * TIN is disposed, or when constraints are added to the TIN.
   */
  private boolean isLocked;

  /**
   * Indicates that the TIN is disposed. All internal objects
   * associated with the current instance are put out-of-scope
   * and the class is no longer usable.
   */
  private boolean isDisposed;

  /**
   * The minimum x coordinate of all vertices that have been added to
   * the TIN.
   */
  private double boundsMinX = Double.POSITIVE_INFINITY;

  /**
   * The maximum y coordinate of all vertices that have been added to
   * the TIN.
   */
  private double boundsMinY = Double.POSITIVE_INFINITY;

  /**
   * The maximum x coordinate of all vertices that have been added to
   * the TIN.
   */
  private double boundsMaxX = Double.NEGATIVE_INFINITY;

  /**
   * The maximum y coordinate of all vertices that have been added to
   * the TIN.
   */
  private double boundsMaxY = Double.NEGATIVE_INFINITY;

  /**
   * The nominal spacing between points in sample data, used
   * to specify thresholds for geometry-based logic and comparisons.
   */
  private final double nominalPointSpacing;

  /**
   * The positive threshold used to determine if a higher-precision
   * calculation
   * is required for performing calculations related to the half-plane
   * calculation. When a computed value is sufficiently close to zero,
   * there is a concern that numerical issues involved in the
   * half-plane calculations might result in incorrect
   * determinations. This value helps define "sufficiently close".
   */
  private final double halfPlaneThreshold;

  /**
   * The negative threshold used to determine if a higher-precision
   * calculation
   * is required for performing calculations related to the half-plane
   * calculation. When a computed value is sufficiently close to zero,
   * there is a concern that numerical issues involved in the
   * half-plane calculations might result in incorrect
   * determinations. This value helps define "sufficiently close".
   */
  private final double halfPlaneThresholdNeg;

  /**
   * The positive threshold used to determine if a higher-precision
   * calculation
   * is required for performing calculations related to the inCircle
   * calculation. When a computed value is sufficiently close to zero,
   * there is a concern that numerical issues involved in the
   * half-plane calculations might result in incorrect
   * determinations. This value helps define "sufficiently close".
   */
  private final double inCircleThreshold;

  /**
   * The negative threshold used to determine if a higher-precision
   * calculation
   * is required for performing calculations related to the half-plane
   * calculation. When a computed value is sufficiently close to zero,
   * there is a concern that numerical issues involved in the
   * half-plane calculations might result in incorrect
   * determinations. This value helps define "sufficiently close".
   */
  private final double inCircleThresholdNeg;

  /**
   * The tolerance factor for treating closely spaced or identical vertices
   * as a single point.
   */
  private final double vertexTolerance;
  /**
   * The square of the vertex tolerance factor.
   */
  private final double vertexTolerance2;

  /**
   * Thresholds computed based on the nominal point spacing for
   * the input vertices.
   */
  private final Thresholds thresholds;
  /**
   * A set of geometric utilities used for various computations.
   */
  private final GeometricOperations geoOp;

  /**
   * Indicates whether the TIN is bootstrapped (initialized).
   */
  private boolean isBootstrapped;
  /**
   * Keeps count of the number of vertices inserted into the TIN.
   * This value may be larger than the number of vertices actually stored
   * in the TIN if vertices are added redundantly.
   */
  private int nVerticesInserted;
  /**
   * Number of in-circle calculations performed (a diagnostic statistic).
   */
  private int nInCircle;
  /**
   * Number of times extended precision methods were needed for in-circle
   * calculations (a diagnostic statistic).
   */
  private int nInCircleExtendedPrecision;
  /**
   * Number of times the extended precision results were significantly
   * different than the low-precision calculation.
   */
  private int nInCircleExtendedPrecisionConflicts;

  /**
   * Counts the number of edges that are removed and replaced during build
   */
  private int nEdgesReplacedDuringBuild;

  /**
   * Tracks the maximum number of edges removed and replaced during build.
   */
  private int maxEdgesReplacedDuringBuild;

  /**
   * Tracks the number of synthetic vertices added during construction.
   */
  private int nSyntheticVertices;

  /**
   * Tracks the maximum depth of recursion when restoring Delaunay
   * conformance after the addition of constraints.
   */
  private int maxDepthOfRecursionInRestore;

  /**
   * Gets the maximum length of the queue in the flood fill operation.
   */
  private int maxLengthOfQueueInFloodFill;

  /**
   * The rule used for disambiguating z values in a vertex merger group.
   */
  private VertexMergerGroup.ResolutionRule vertexMergeRule
    = VertexMergerGroup.ResolutionRule.MeanValue;

  /**
   * An instance of a SLW set with thresholds established in the constructor.
   */
  private final SemiVirtualStochasticLawsonsWalk walker;

  /**
   * Constructs an incremental TIN using numerical thresholds appropriate
   * for the default nominal point spacing of 1 unit.
   */
  public SemiVirtualIncrementalTin() {

    thresholds = new Thresholds(1.0);
    geoOp = new GeometricOperations(thresholds);
    nominalPointSpacing = thresholds.getNominalPointSpacing();

    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
    halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    inCircleThreshold = thresholds.getInCircleThreshold();
    inCircleThresholdNeg = -thresholds.getInCircleThreshold();

    vertexTolerance = thresholds.getVertexTolerance();
    vertexTolerance2 = thresholds.getVertexTolerance2();

    walker = new SemiVirtualStochasticLawsonsWalk(thresholds);

    edgePool = new SemiVirtualEdgePool();
  }

  /**
   * Constructs an incremental TIN using numerical thresholds appropriate
   * for the specified nominal point spacing. This value is an
   * estimated spacing used for determining numerical thresholds for
   * various proximity and inclusion tests. For best results, it should be
   * within one to two orders of magnitude of the actual value for the
   * samples. In practice, this value is usually chosen to be close
   * to the mean point spacing for a sample. But for samples with varying
   * density, a mean value from the set of smaller point spacings may be used.
   * <p>
   * Lidar applications sometimes refer to the point-spacing concept as
   * "nominal pulse spacing", a term that reflects the origin of the
   * data in a laser-based measuring system.
   *
   * @param nominalPointSpacing the nominal distance between points.
   */
  public SemiVirtualIncrementalTin(final double nominalPointSpacing) {
    this.nominalPointSpacing = nominalPointSpacing;
    thresholds = new Thresholds(this.nominalPointSpacing);
    geoOp = new GeometricOperations(thresholds);

    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
    halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    inCircleThreshold = thresholds.getInCircleThreshold();
    inCircleThresholdNeg = -thresholds.getInCircleThreshold();

    vertexTolerance = thresholds.getVertexTolerance();
    vertexTolerance2 = thresholds.getVertexTolerance2();

    walker = new SemiVirtualStochasticLawsonsWalk(thresholds);

    edgePool = new SemiVirtualEdgePool();
  }

  /**
   * Insert a vertex into the collection of vertices managed by
   * the TIN. If the TIN is not yet bootstrapped, the vertex will
   * be retained in a simple list until enough vertices are received
   * in order to bootstrap the TIN.
   *
   * @param v a valid vertex
   * @return true if the TIN is bootstrapped; otherwise false
   */
  @Override
  public boolean add(final Vertex v) {
    if (isLocked) {
      if (isDisposed) {
        throw new IllegalStateException(
          "Unable to add vertex after a call to dispose()");
      } else {
        throw new IllegalStateException(
          "Unable to add vertex, TIN is locked");
      }
    }
    nVerticesInserted++;
    if (isBootstrapped) {
      return addWithInsertOrAppend(v);
    } else {
      if (vertexList == null) {
        vertexList = new ArrayList<>();
        vertexList.add(v);
        return false;
      }
      vertexList.add(v);
      boolean status = bootstrap(vertexList);
      if (status) {
        // the bootstrap process uses 3 vertices from
        // the vertex list but does not remove them from
        // the list.   The processVertexInsertion method has the ability
        // to ignore multiple insert actions for the same vertex.
        if (vertexList.size() > 3) {
          for (Vertex vertex : vertexList) {
            addWithInsertOrAppend(vertex);
          }
        }
        vertexList.clear();
        vertexList = null;
        return true;
      }
      return false;
    }
  }

  /**
   * Inserts a list of vertices into the collection of vertices managed by the
   * TIN. If the TIN is not yet bootstrapped, the vertices will be retained in
   * a simple list until enough vertices are received in order to bootstrap
   * the TIN.
   * <h1>Performance Consideration Related to List</h1>
   * <p>
   * In the bootstrap phase, three points are chosen at random from the vertex
   * list to create the initial triangle for insertion. The initialization
   * will make a small number of selection attempts and select the triangle
   * with the largest number. In the event that this process does not find
   * three points that are not a suitable choice (as when they are collinear
   * or nearly collinear), the process will be repeated until a valid initial
   * triangle is selected.
   * <p>
   * Thus, there is a small performance advantage in
   * supplying the vertices using a list that can be accessed efficiently in a
   * random order (see the discussion of the Java API for the List and
   * java.util.RandomAccess interfaces). Once the initial triangle is
   * established, the list will be traversed sequentially to build the TIN and
   * random access considerations will no longer apply.
   *
   * <h1>Performance Consideration Related to Location of Vertices</h1>
   *
   * The performance of the insertion process is sensitive to the
   * relative location of vertices.  An input data set based on
   * <strong>purely random</strong> vertex positions represents one of the
   * worst-case input sets in terms of processing time.
   * <p>
   * Ordinarily, the most computationally expensive operation for inserting
   * a vertex into the Delaunay triangulation is locating the triangle
   * that contains its coordinates. But Tinfour implements logic to
   * expedite this search operation by taking advantage of a characteristic
   * that occurs in many data sets:  the location of one vertex in a sequence
   * is usually close to the location of the vertex that preceded it.
   * By starting each search at the position in the triangulation where a vertex
   * was most recently inserted, the time-to-search can be reduced dramatically.
   * Unfortunately, in vertices generated by a random process, this assumption
   * of sequential proximity (i.e. "spatial autocorrelation") is not true.
   * <p>
   * To assist in the case of random or poorly correlated vertex geometries,
   * application can take advantage of the HilbertSort class which is supplied
   * as part of the Core Tinfour module. In the example shown below, the
   * use of the HilbertSort yields a <strong>factor of 100</strong>
   * improvement in the time to perform the .add() method.
   * <pre>
   *      int nVertices = 1_000_000;
   *      List&lt;Vertex&gt; vertices = new ArrayList&lt;&gt;();
   *      for (int i = 0; i &lt; nVertices; i++) {
   *        double x = Math.random() * 1000;
   *        double y = Math.random() * 1000;
   *        vertices.add(new Vertex(x, y, 0));
   *      }
   *
   *      HilbertSort hs = new HilbertSort();
   *      hs.sort(vertices);
   *      IIncrementalTin tin = new IncrementalTin();
   *      tin.add(vertices, null);
   * </pre>
   *
   * @param list a valid list of vertices to be added to the TIN.
   * @param monitor an optional instance of a monitoring implementation;
   * null if not used
   * @return true if the TIN is bootstrapped; otherwise false
   */
  @Override
  public boolean add(final List<Vertex> list, IMonitorWithCancellation monitor) {
    if (isLocked) {
      if (isDisposed) {
        throw new IllegalStateException(
          "Unable to add vertex after a call to dispose()");
      } else {
        throw new IllegalStateException(
          "Unable to add vertex, TIN is locked");
      }
    }
    if (list.isEmpty()) {
      return false;
    }

    int nVertices = list.size();
    int iProgressThreshold = Integer.MAX_VALUE;
    int pProgressThreshold = 0;
    if (monitor != null) {
      monitor.reportProgress(0);
      int iPercent = monitor.getReportingIntervalInPercent();
      int iTemp = (int) (nVertices * (iPercent / 100.0) + 0.5);
      if (iTemp > 1) {
        if (iTemp < 10000) {
          iTemp = 10000;
        }
        iProgressThreshold = iTemp;
      }
    }

    nVerticesInserted += list.size();
    List<Vertex> aList = list;
    if (!isBootstrapped) {
      // the bootstrap is conducted using
      // the newly provided vertices as well as
      // any previously input objects.  if some vertices were
      // already added, the vertexList will be defined.
      if (vertexList != null) {
        vertexList.addAll(list);
        aList = vertexList;
      }
      boolean status = bootstrap(aList);
      if (!status) {
        // The bootstrap was unsuccessful.  We need to
        // maintain the vertices for future operations.
        // However, we cannot depend on the list that the
        // calling application passed in as being inmutable.
        // So we make a private copy.
        if (vertexList == null) {
          vertexList = new ArrayList<>();
        }
        vertexList.addAll(list);
        return false;
      }
      // if the bootstrap succeeded, just fall through
      // and process the remainder of the list.
    }

    this.preAllocateEdges(aList.size());
    int nVertexAdded = 0;
    for (Vertex v : aList) {
      addWithInsertOrAppend(v);
      nVertexAdded++;
      pProgressThreshold++;
      if (pProgressThreshold == iProgressThreshold) {
        pProgressThreshold = 0;
        monitor.reportProgress((int) (0.1 + (100.0 * (nVertexAdded + 1)) / nVertices));
      }
    }

    if (vertexList != null) {
      vertexList.clear();
      vertexList = null;
    }
    return true;
  }

  /**
   * Create the initial three-vertex mesh by selecting vertices from
   * the input list. Logic is provided to attempt to identify a
   * initial triangle with a non-trivial area (on the theory that this
   * stipulation produces a more robust initial mesh). In the event
   * of an unsuccessful bootstrap attempt, future attempts will be conducted
   * as the calling application provides additional vertices.
   *
   * @param list a valid list of input vertices.
   * @return if successful, true; otherwise, false.
   */
  private boolean bootstrap(final List<Vertex> list) {
    Vertex[] v = new BootstrapUtility(thresholds).bootstrap(list);
    if (v == null) {
      return false;
    }

    // Allocate edges for initial TIN
    SemiVirtualEdge e1 = edgePool.allocateEdge(v[0], v[1]);
    SemiVirtualEdge e2 = edgePool.allocateEdge(v[1], v[2]);
    SemiVirtualEdge e3 = edgePool.allocateEdge(v[2], v[0]);
    SemiVirtualEdge e4 = edgePool.allocateEdge(v[0], null);
    SemiVirtualEdge e5 = edgePool.allocateEdge(v[1], null);
    SemiVirtualEdge e6 = edgePool.allocateEdge(v[2], null);

    SemiVirtualEdge ie1 = e1.getDual();
    SemiVirtualEdge ie2 = e2.getDual();
    SemiVirtualEdge ie3 = e3.getDual();
    SemiVirtualEdge ie4 = e4.getDual();
    SemiVirtualEdge ie5 = e5.getDual();
    SemiVirtualEdge ie6 = e6.getDual();

    // establish linkages for initial TIN
    e1.setForward(e2);
    e2.setForward(e3);
    e3.setForward(e1);
    e4.setForward(ie5);
    e5.setForward(ie6);
    e6.setForward(ie4);

    ie1.setForward(e4);
    ie2.setForward(e5);
    ie3.setForward(e6);
    ie4.setForward(ie3);
    ie5.setForward(ie1);
    ie6.setForward(ie2);

    isBootstrapped = true;

    // The x,y bounds tests will be performed for vertices when they
    // are inserted using the processVertexInsertion method.  But since
    // these three are already part of the TIN, test for their bounds
    // explicitly.
    boundsMinX = v[0].x;
    boundsMaxX = boundsMinX;
    boundsMinY = v[0].y;
    boundsMaxY = boundsMinY;
    for (int i = 1; i < 3; i++) {
      if (v[i].x < boundsMinX) {
        boundsMinX = v[i].x;
      } else if (v[i].x > boundsMaxX) {
        boundsMaxX = v[i].x;
      }
      if (v[i].y < boundsMinY) {
        boundsMinY = v[i].y;
      } else if (v[i].y > boundsMaxY) {
        boundsMaxY = v[i].y;
      }
    }

    return true;
  }

  /**
   * Given an perimeter edge AB defined by vertices a and b,
   * compute the equivalent of the in-circle h factor indicating if the the
   * vertex v is on the inside or outside of the edge (and, so, the TIN).
   * The perimeter edge is oriented so that the interior is on the side
   * of its dual. Thus if the test vertex is in the local direction of the TIN
   * interior, h will be negative and if it is in the local direction of the
   * TIN
   * exterior, h will be positive. For the case where the vertex lies on
   * the ray of the segment, e.g. h is zero, special logic is applied.
   * If the point lies within the segment, h is artificially set
   * to a value of positive 1. In the insertion in-circle logic below,
   * a value of h>0 indicates that an edge is non-delaunay and thus AB needs
   * to be removed (flipped). If the point lies outside the segment, h is
   * artifically set to +1, which triggers the insertion logic to leave
   * the edge AB in place.
   *
   * @param a a valid vertex
   * @param b a valid vertex
   * @param v a valid vertex to be tested for a psuedo in-circle condition
   * with vertices a and b.
   * @return A negative value if the vertex is in the direction of the TIN
   * interior, a positive value if it is in the direction of the exterior
   * or a zero if it lies directly on the edge; zero is not returned.
   */
  private double inCircleWithGhosts(final Vertex a, final Vertex b, final Vertex v) {
    double h = (v.x - a.x) * (a.y - b.y) + (v.y - a.y) * (b.x - a.x);
    if (halfPlaneThresholdNeg < h && h < halfPlaneThreshold) {
      h = geoOp.halfPlane(a.x, a.y, b.x, b.y, v.x, v.y);
      if (h == 0) {
        double ax = v.getX() - a.getX();
        double ay = v.getY() - a.getY();
        double nx = b.getX() - a.getX();
        double ny = b.getY() - a.getY();
        double can = ax * nx + ay * ny;
        if (can < 0) {
          h = -1;
        } else if (ax * ax + ay * ay > nx * nx + ny * ny) {
          h = -1;
        } else {
          h = 1;
        }
      }
    }
    return h;
  }

  /**
   * Performs processing for the public add() methods by adding the vertex
   * to a fully bootstrapped mesh. The vertex will be either inserted
   * into the mesh or the mesh will be extended to include the vertex.
   *
   * @param v a valid vertex.
   * @return true if the vertex was added successfully; otherwise false
   * (usually in response to redundant vertex specifications).
   */
  private boolean addWithInsertOrAppend(final Vertex v) {
    final double x = v.x;
    final double y = v.y;

    // The build buffer provides temporary tracking of edges that are
    // removed and replaced while building the TIN.  Because the
    // delete method of the EdgePool has to do a lot of bookkeeping,
    // we can gain speed by using the buffer.   The buffer is only large
    // enough to hold one edge. Were it larger, there would be times
    // when it would hold more than one edge. Tests reveal that the overhead
    // of maintaining an array rather than a single integer overwhelms
    // the potential saving. However, the times for the two approaches are quite
    // close and it is hard to remove the effect of measurement error.
    int buffer = -1;
    int nReplacements = 0;

    if (x < boundsMinX) {
      boundsMinX = x;
    } else if (x > boundsMaxX) {
      boundsMaxX = x;
    }
    if (y < boundsMinY) {
      boundsMinY = y;
    } else if (y > boundsMaxY) {
      boundsMaxY = y;
    }

    if (searchEdge == null) {
      searchEdge = edgePool.getStartingEdge();
    }
    walker.findAnEdgeFromEnclosingTriangleInternal(searchEdge, x, y);

    if (checkTriangleVerticesForMatchInternal(searchEdge, x, y, vertexTolerance2)) {
      mergeVertexOrIgnore(searchEdge, v);
      return false;
    }

    Vertex anchor = searchEdge.getA();

    final SemiVirtualEdge e = edgePool.allocateUnassignedEdge();
    final SemiVirtualEdge pStart = edgePool.allocateEdge(v, anchor);
    final SemiVirtualEdge p = pStart.copy();
    p.setForward(searchEdge);
    final SemiVirtualEdge n0 = p.getDual();
    final SemiVirtualEdge n1 = searchEdge.getForward();
    final SemiVirtualEdge n2 = n1.getForward();
    n2.setForward(n0);

    final SemiVirtualEdge c = searchEdge.copy();
    while (true) {
      n0.loadDualFromEdge(c);   //n0 = c.getDual();
      n1.loadForwardFromEdge(n0); // = n0.getForward();

      // check for the Delaunay in-circle criterion.  In the original
      // implementation, this was accomplished through a call to
      // a method in another class (GeometricOperations), but testing
      // revealed that we could gain nearly 10 percent throughput
      // by embedding the logic in this loop.
      // the three vertices of the neighboring triangle are, in order,
      //    n0.getA(), n1.getA(), n1.getB()
      double h;
      Vertex vA = n0.getA();
      Vertex vB = n1.getA();
      Vertex vC = n1.getB();
      if (vC == null) {
        h = inCircleWithGhosts(vA, vB, v);
      } else if (vA == null) {
        h = inCircleWithGhosts(vB, vC, v);
      } else if (vB == null) {
        h = inCircleWithGhosts(vC, vA, v);
      } else {
        nInCircle++;
        double a11 = vA.x - x;
        double a21 = vB.x - x;
        double a31 = vC.x - x;

        // column 2
        double a12 = vA.y - y;
        double a22 = vB.y - y;
        double a32 = vC.y - y;

        h = (a11 * a11 + a12 * a12) * (a21 * a32 - a31 * a22)
          + (a21 * a21 + a22 * a22) * (a31 * a12 - a11 * a32)
          + (a31 * a31 + a32 * a32) * (a11 * a22 - a21 * a12);
        if (inCircleThresholdNeg < h && h < inCircleThreshold) {
          nInCircleExtendedPrecision++;
          double h2 = h;
          h = geoOp.inCircleQuadPrecision(
            vA.x, vA.y,
            vB.x, vB.y,
            vC.x, vC.y,
            x, y);
          if (h == 0) {
            if (h2 != 0) {
              nInCircleExtendedPrecisionConflicts++;
            }
          } else if (h * h2 <= 0) {
            nInCircleExtendedPrecisionConflicts++;
          }
        }
      }

      if (h >= 0) {
        n2.loadForwardFromEdge(n1); //  = n1.getForward();
        n0.loadForwardFromEdge(c); // just use n0 as a temp
        n2.setForward(n0);  // n2.setForward(c.getForward());
        p.setForward(n1);
        // we need to get the base reference in order to ensure
        // that any ghost edges we create will start with a
        // non-null vertex and end with a null.
        nReplacements++;
        if (buffer == -1) {
          buffer = c.getBaseIndex();
        } else {
          edgePool.deallocateEdge(c);
        }

        c.loadFromEdge(n1);  // c = n1;
      } else {
        // check for completion
        if (c.getB() == anchor) {
          n0.loadDualFromEdge(pStart);
          n0.setForward(p);
          searchEdge.loadFromEdge(pStart);
          // TO DO: is buffer ever not empty?
          //        i don't think so because it could only
          //        happen in a case where an insertion decreased
          //        the number of edge. so the following code
          //        is probably unnecessary
          if (buffer != -1) {
            edgePool.deallocateEdge(buffer);
          }

          nEdgesReplacedDuringBuild += nReplacements;
          if (nReplacements > maxEdgesReplacedDuringBuild) {
            maxEdgesReplacedDuringBuild = nReplacements;
          }
          break;
        }

        n1.loadForwardFromEdge(c); // n1 = c.getForward()
        if (buffer == -1) {
          edgePool.allocateEdgeWithReceiver(e, v, c.getB());
        } else {
          edgePool.getEdgeForIndexWithReceiver(e, buffer, v, c.getB());
          buffer = -1;
        }
        e.setForward(n1);
        n0.loadDualFromEdge(e); //  e.getDual().setForward(p);
        n0.setForward(p);
        c.setForward(n0);  //  c.setForward(e.getDual());
        p.loadFromEdge(e);  // p = e;
        c.loadFromEdge(n1); // c = n1;
      }
    }
    return true;
  }

  /**
   * Tests the vertices of the triangle that includes the reference edge
   * to see if any of them are an exact (instance) match for the specified
   * vertex. If so, the VirtualEdge is loaded with the edge that starts
   * with the specified vertex. If the vertex is a member of a
   * VertexMergerGroup, the edge that starts with the containing group
   * is returned.
   *
   * @param v the vertex to test for match
   * @param sEdge an edge from the triangle containing the vertex,
   * may be adjusted by the search.
   * @return true if a match is found; otherwise, false
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  private boolean checkTriangleVerticesForMatchingReference(
    final SemiVirtualEdge sEdge, Vertex v) {
    Vertex a = sEdge.getA();
    Vertex b = sEdge.getB();
    Vertex c = sEdge.getTriangleApex();

    // first try a direct comparison
    if (a == v) {
      return true;
    } else if (b == v) {
      sEdge.loadDualFromEdge(sEdge);
      return true;
    } else if (c == v) {
      sEdge.loadReverseFromEdge(sEdge);
      return true;
    }

    if (a instanceof VertexMergerGroup && ((VertexMergerGroup) a).contains(v)) {
      return true;
    } else if (b instanceof VertexMergerGroup && ((VertexMergerGroup) b).contains(v)) {
      sEdge.loadDualFromEdge(sEdge);
      return true;
    } else if (c instanceof VertexMergerGroup && ((VertexMergerGroup) c).contains(v)) {
      sEdge.loadReverseFromEdge(sEdge);
      return true;
    }

    return false;
  }

  /**
   * Tests the vertices of the triangle that includes the reference edge
   * to see if any of them are an exact match for the specified
   * coordinates. Typically, this method is employed after a search
   * has obtained a neighboring edge for the coordinates.
   * If one of the vertices is an exact match, within tolerance, for the
   * specified coordinates, this method will return the edge that
   * starts with the vertex.
   *
   * @param x the x coordinate for the point of interest
   * @param y the y coordinate for the point of interest
   * @param sEdge an edge from the triangle containing (x,y)
   * @param distanceTolerance2 the square of a tolerance specification
   * for accepting a vertex as a match for the coordinates
   * @return true if a match is found; otherwise, false
   */
  private boolean checkTriangleVerticesForMatchInternal(
    final SemiVirtualEdge sEdge,
    double x,
    double y,
    final double distanceTolerance2) {

    if (sEdge.getA().getDistanceSq(x, y) < distanceTolerance2) {
      return true;
    } else if (sEdge.getB().getDistanceSq(x, y) < distanceTolerance2) {
      sEdge.loadDualFromEdge(sEdge);
      return true;
    } else {
      Vertex v2 = sEdge.getTriangleApex();
      if (v2 != null && v2.getDistanceSq(x, y) < distanceTolerance2) {
        sEdge.loadReverseFromEdge(sEdge);
        return true;
      }
    }
    return false;
  }

  /**
   * Allocates a number of vertices roughly sufficient to represent a TIN
   * containing the specified number of vertices. This method
   * serves as a diagnostic tool, allowing a test-application
   * to separate the portion of processing time consumed by
   * Java object construction from that spent processing the
   * vertex data.
   *
   * @param nVertices the number of vertices expected to be added to the TIN.
   */
  @Override
  public void preAllocateEdges(final int nVertices) {
    edgePool.preAllocateEdges(nVertices * 3);
  }

  /**
   * Gets the bounds of the TIN. If the TIN is not initialized (bootstrapped),
   * this method returns a null.
   *
   * @return if available, a valid rectangle giving the bounds of the TIN;
   * otherwise, a null
   */
  @Override
  public Rectangle2D getBounds() {
    if (Double.isInfinite(boundsMinX)) {
      return null;
    }
    return new Rectangle2D.Double(boundsMinX, boundsMinY, boundsMaxX - boundsMinX, boundsMaxY - boundsMinY);
  }

  /**
   * Given a vertex known to have coordinates very close or identical
   * to a previously inserted vertex, perform a merge. The first time
   * a merge is performed, the previously existing vertex is replaced with
   * a VertexMergerGroup object and the new vertex is added to the
   * group.
   * <p>
   * This method also checks to see if the newly inserted vertex is the
   * same object as one previously inserted. In such a case, it is ignored.
   * Although this situation could happen due to a poorly implemented
   * application, the most common case is when the insertion was conducted
   * using a list of vertices rather than individual insertions. The
   * bootstrap logic creates an initial mesh from three randomly chosen
   * vertices. When the list is processed, these vertices will eventually
   * be passed to the processVertexInsertion routine. They will be identified
   * as merge candidates and ignored. For large input lists, this strategy
   * is more efficient than attempting to modify the input list.
   *
   * @param edge an edge selected so that the matching, previously inserted
   * vertex is assigned to vertex A.
   * @param v the newly inserted, matching vertex.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  private void mergeVertexOrIgnore(final SemiVirtualEdge edge, final Vertex v) {
    Vertex a = edge.getA();
    if (a == v) {
      // this vertex was already inserted.  usually this is
      // because the vertex was used in the bootstrap process
      // but it could happen if the list gave the same vertex more
      // than once.
      return;
    }
    VertexMergerGroup group;
    if (a instanceof VertexMergerGroup) {
      group = (VertexMergerGroup) a;
    } else {
      // Replace the vertex that already exists in the TIN
      // with a VertexMergerGroup.
      group = new VertexMergerGroup(edge.getA());
      group.setResolutionRule(vertexMergeRule);
      coincidenceList.add(group);
      // build a list of edges that contain the target vertex.
      // for each of these, replace the previously existing
      // vertex (a) with the new group.
      SemiVirtualEdge start = edge;
      SemiVirtualEdge e = edge;

      ArrayList<SemiVirtualEdge> eList = new ArrayList<>();
      int startIndex = start.getIndex();
      do {
        eList.add(e);
        e = e.getReverse();
        e = e.getDual();
      } while (e.getIndex() != startIndex);

      for (SemiVirtualEdge qe : eList) {
        qe.setA(group);
      }
    }
    group.addVertex(v);
  }

  /**
   * Provides a diagnostic print out of the edges comprising the TIN.
   *
   * @param ps A valid print stream.
   */
  @Override
  public void printEdges(final PrintStream ps) {
    List<IQuadEdge> list = edgePool.getEdges();
    for (IQuadEdge e : list) {
      ps.println(e.toString());
      ps.println(e.getDual().toString());
    }
    ps.flush();

  }

  /**
   * Set the mark bit for an edge to 1.
   *
   * @param map an array at least as large as the largest edge index divided
   * by 32
   * @param edge a valid edge
   */
  private void setMarkBit(BitSet bitset, final IQuadEdge edge) {
    int index = (edge.getIndex() * N_SIDES) | edge.getSide();
    bitset.set(index);
  }

  /**
   * Gets the edge mark bit.
   *
   * @param map an array at least as large as the largest edge index divided
   * by 32
   * @param edge a valid edge
   * @return if the edge is marked, a non-zero value; otherwise, a zero.
   */
  private boolean getMarkBit(BitSet bitset, final IQuadEdge edge) {
    int index = (edge.getIndex() * N_SIDES) | edge.getSide();
    return bitset.get(index);
  }

  /**
   * Performs a survey of the TIN to gather statistics about the triangle
   * formed during its construction.
   *
   * @return A valid instance of the TriangleCount class.
   */
  @Override
  public TriangleCount countTriangles() {
    return new TriangleCount(this);

  }

  /**
   * Gets a list of edges currently defining the perimeter of the TIN.
   * The list may be empty if the TIN is not initialized (bootstrapped).
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges
   * return by this routine are the same objects as those currently being used
   * in the instance. Any modification of the edge objects will damage
   * the TIN. Therefore, applications must not modify the edges returned by this
   * method.
   *
   * @return a valid, potentially empty list.
   */
  @Override
  public List<IQuadEdge> getPerimeter() {
    List<IQuadEdge> pList = new ArrayList<>();
    IQuadEdge g = edgePool.getStartingGhostEdge();
    if (isBootstrapped && g != null) {
      IQuadEdge s0 = g.getReverse();
      IQuadEdge s = s0;
      do {
        pList.add(s.getDual());
        s = s.getForward();
        s = s.getForward();
        s = s.getDual();
        s = s.getReverse();
      } while (!s.equals(s0));
    }

    return pList;
  }

  /**
   * Print statistics and diagnostic information collected during the
   * TIN construction process. This information will be removed and
   * reset by a call to the clear() method.
   *
   * @param ps A valid instance of a PrintStream to receive the output.
   */
  @Override
  public void printDiagnostics(final PrintStream ps) {
    if (!isBootstrapped) {
      ps.println("Insufficient information to create a TIN");
      return;
    }

    List<IQuadEdge> perimeter = getPerimeter();

    TriangleCount trigCount = this.countTriangles();

    int nCoincident = 0;
    for (VertexMergerGroup c : coincidenceList) {
      nCoincident += c.getSize();
    }

    int nOrdinary = 0;
    int nGhost = 0;
    int nConstrained = 0;
    double sumLength = 0;
    Iterator<IQuadEdge> iEdge = edgePool.iterator();
    while (iEdge.hasNext()) {
      IQuadEdge e = iEdge.next();
      if (e.getB() == null) {
        nGhost++;
      } else {
        nOrdinary++;
        sumLength += e.getLength();
        if(e.isConstrained()){
          nConstrained++;
        }
      }
    }
    double avgPointSpacing = 0;
    if (nOrdinary > 0) {
      avgPointSpacing = sumLength / nOrdinary;
    }
    ps.format("Descriptive data%n");
    ps.format("Number Vertices Inserted:     %8d%n", nVerticesInserted);
    ps.format("Coincident Vertex Spacing:    %8f%n", vertexTolerance);
    ps.format("   Sets:                      %8d%n", coincidenceList.size());
    ps.format("   Total Count:               %8d%n", nCoincident);
    ps.format("Number Ordinary Edges:        %8d%n", nOrdinary);
    ps.format("Number Edges On Perimeter:    %8d%n", perimeter.size());
    ps.format("Number Ghost Edges:           %8d%n", nGhost);
    ps.format("Number Constrained Edges:     %8d%n", nConstrained);
    ps.format("Number Edge Replacements:     %8d    (avg: %3.1f)%n",
      nEdgesReplacedDuringBuild,
      (double) nEdgesReplacedDuringBuild / (double) (nVerticesInserted - nCoincident));
    ps.format("Max Edge Replaced by add op:  %8d%n", maxEdgesReplacedDuringBuild);
    ps.format("Average Point Spacing:        %11.2f%n", avgPointSpacing);
    ps.format("Application's Nominal Spacing:%11.2f%n", nominalPointSpacing);
    ps.format("Number Triangles:             %8d%n", trigCount.getCount());
    ps.format("Average area of triangles:    %12.3f%n", trigCount.getAreaMean());
    ps.format("Samp. std dev for area:       %12.3f%n", trigCount.getAreaStandardDeviation());
    if (trigCount.getAreaMin() < 1) {
      ps.format("Minimum area:                        %f%n", trigCount.getAreaMin());
    } else {
      ps.format("Minimum area:                 %12.3f%n", trigCount.getAreaMin());
    }
    ps.format("Maximum area:                 %12.3f%n", trigCount.getAreaMax());
    ps.format("Total area:                   %10.1f%n", trigCount.getAreaSum());

    ps.format("%nConstruction statistics%n");
    walker.printDiagnostics(ps);

    ps.format("InCircle calculations:        %8d%n", nInCircle);
    ps.format("   extended:                  %8d%n", nInCircleExtendedPrecision);
    ps.format("   conflicts:                 %8d%n", nInCircleExtendedPrecisionConflicts);

    ps.format("%n");
    edgePool.printDiagnostics(ps);
    ps.format("%n");
    ps.format("Number of constraints:        %8d%n", constraintList.size());
    ps.format("Max recursion during restore: %8d%n", maxDepthOfRecursionInRestore);
    ps.format("Number of synthetic vertices: %8d%n", nSyntheticVertices);
    ps.format("Max queue size in flood fill: %8d%n", this.maxLengthOfQueueInFloodFill);
  }

  /**
   * Gets a list of edges currently allocated by an instance. The list may
   * be empty if the TIN is not initialized (bootstrapped).
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges
   * return by this routine are the same objects as those currently being used
   * in the instance. Any modification of the edge objects will damage
   * the TIN. Therefore, applications must not modify the edges returned by this
   * method.
   *
   * @return a valid, potentially empty list.
   */
  @Override
  public List<IQuadEdge> getEdges() {
    if (!isBootstrapped) {
      return new ArrayList<>();
    }
    return edgePool.getEdges();
  }

  @Override
  public Iterator<IQuadEdge> getEdgeIterator() {
    return edgePool.getIterator(true);
  }

  @Override
  public Iterable<IQuadEdge> edges() {
    return  new Iterable<IQuadEdge>(){
      @Override
      public Iterator<IQuadEdge> iterator() {
         return edgePool.getIterator(false);
      }
    };
  }

  @Override
  public int getMaximumEdgeAllocationIndex() {
    return edgePool.getMaximumAllocationIndex();
  }

  List<SemiVirtualEdge> getVirtualEdges() {
    if (!isBootstrapped) {
      return new ArrayList<SemiVirtualEdge>();
    }
    return edgePool.getVirtualEdges();
  }

  /**
   * Gets the nominal point spacing used to determine numerical thresholds
   * for various proximity and inclusion tests. For best results, it should be
   * within one to two orders of magnitude of the actual value for the
   * samples. In practice, this value is usually chosen to be close
   * to the mean point spacing for a sample. But for samples with varying
   * density, a mean value from the set of smaller point spacings may be used.
   * <p>
   * Lidar applications sometimes refer to the point-spacing concept as
   * "nominal pulse spacing", a term that reflects the origin of the
   * data in a laser-based measuring system.
   *
   * @return a positive floating-point value greater than zero.
   */
  @Override
  public double getNominalPointSpacing() {
    return nominalPointSpacing;
  }

  @Override
  public Thresholds getThresholds() {
    return thresholds;
  }

  @Override
  /**
   * Nullifies all internal data and references, preparing the
   * instance for garbage collection.
   * Because of the complex relationships between objects in a TIN,
   * Java garbage collection may require an above average number of passes
   * to clean up memory when an instance of this class goes out-of-scope.
   * The dispose() method can be used to expedite garbage collection.
   * Do not confuse the dispose() method with the clear() method.
   * The clear() method prepares a TIN instance for reuse.
   * The dispose() method prepares a TIN instance for garbage collection.
   * Once the dispose() method is called on a TIN, it cannot be reused.
   */
  public void dispose() {
    if (!isDisposed) {
      isLocked = true;
      isDisposed = true;
      edgePool.dispose();
      searchEdge = null;
      if (vertexList != null) {
        vertexList.clear();
        vertexList = null;
      }
      if (coincidenceList != null) {
        coincidenceList.clear();
      }
    }
  }

  @Override
  /**
   * Clears all internal state data of the TIN, preparing any allocated
   * resources for re-use. When processing multiple sets of input data
   * the clear() method has an advantage in that it can be used to reduce
   * the overhead related to multiple edge object implementation.
   */
  public void clear() {
    if (isDisposed) {
      return;
    }
    isLocked = false;
    isBootstrapped = false;
    edgePool.clear();
    searchEdge = null;
    if (vertexList != null) {
      vertexList.clear();
    }
    if (coincidenceList != null) {
      coincidenceList.clear();
    }
    constraintList.clear();
    walker.reset();
    nSyntheticVertices = 0;
  }

  /**
   * Indicates whether the instance contains sufficient information
   * to represent a TIN. Bootstrapping requires the input of at least
   * three distinct, non-collinear vertices. If the TIN is not bootstrapped
   * methods that access its content may return empty or null results.
   *
   * @return true if the TIN is successfully initialized; otherwise, false.
   */
  @Override
  public boolean isBootstrapped() {
    return isBootstrapped;
  }

  /**
   * Set the search edge after a removal is completed.
   * The logic for insertion requires that the search edge
   * cannot be a ghost edge, but the logic for removal sometimes
   * produces this result. Ensure that the search is set with
   * an interior-side edge.
   *
   * @param e the search edge identified by the removal process.
   */
  private void setSearchEdgeAfterRemoval(SemiVirtualEdge e) {
    SemiVirtualEdge b = e.getBaseReference();
    if (b.getB() == null) {
      b = b.getReverse();
    }
    searchEdge = b;
  }

  /**
   * Removes the specified vertex from the TIN. If the vertex is part of
   * a merged-group, it is removed from the group by the structure of the
   * TIN is unchanged.
   * <p>
   * At this time, this method does not handle the case where all
   * vertices are removed from the TIN.
   *
   * @param vRemove the vertex to be removed
   * @return true if the vertex was found in the TIN and removed.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  @Override
  public boolean remove(final Vertex vRemove) {
    if (isLocked) {
      if (isDisposed) {
        throw new IllegalStateException(
          "Unable to add vertex after a call to dispose()");
      } else {
        throw new IllegalStateException(
          "Unable to add vertex, TIN is locked");
      }
    }
    if (vRemove == null) {
      return false;
    }
    if (!isBootstrapped) {
      if (vertexList != null) {
        return vertexList.remove(vRemove);
      }
      return false;
    }

    final double x = vRemove.x;
    final double y = vRemove.y;

    if (searchEdge == null) {
      searchEdge = edgePool.getStartingEdge();
    }
    SemiVirtualEdge matchEdge
      = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x, y);

    checkTriangleVerticesForMatchingReference(matchEdge, vRemove);
    if (matchEdge == null) {
      return false;
    }

    // target vertex is now located at matchEdge.a
    // perform special handling for a merger group
    final Vertex matchA = matchEdge.getA();
    if (matchA instanceof VertexMergerGroup && vRemove != matchA) {
      // when vRemove==the A vertex of the matched edge, we have the special
      // case where the calling application is trying to remove the
      // whole group and the logic will just fall through and
      // perform a normal removal.  When they are not equal,
      // we perform a removal on the group's internal list and
      // only remove the group from the TIN if it is empty.
      VertexMergerGroup group = (VertexMergerGroup) matchA;
      if (!group.removeVertex(vRemove)) {
        return false;
      }
      if (group.getSize() > 0) {
        return true;
      }
      // if the group is empty, it must now be removed from the TIN
      // just like any other vertex.
    }

    // because we are going to delete a point, the state data in
    // the matchedEdge will become obsolete.
    SemiVirtualEdge n0 = matchEdge;
    searchEdge = null;

    // initialize edges needed for removal
    SemiVirtualEdge n1, n2, n3;

    // step 1: Cavitation
    //         remove vertex and create a polygonal cavity
    //         eliminating all connecting edges
    int initialSize = edgePool.size();
    n1 = n0.getForward();
    while (true) {
      n2 = n1.getForward();
      n3 = n2.getForwardFromDual();
      //n2 is to be deleted.  set the forward edge
      //of n2 to point to n3.
      n1.setForward(n3);
      n1 = n3;
      //if (n2.getB() == null) {
      //   vertexIsOnPerimeter = true;
      //}
      if (n2.equals(n0.getDual())) {
        //dispose of edge n2 and break
        edgePool.deallocateEdge(n2);
        break;
      } else {
        edgePool.deallocateEdge(n2);
      }
    }
    n0 = n1;

    if(initialSize-edgePool.size() == 3){
      // Three edges deleted, which indicates that
      // the removal of the vertex resulted in a single triangle
      // that is already Delaunay.  The cavitation process should
      // have reset the links.  So the removal operation is done.
      setSearchEdgeAfterRemoval(n0);
      return true;
    }

    // Step 2 -- Ear Creation
    //           Create a set of Devillers Ears around
    //           the polygonal cavity.
    int nEar = 0;
    n1 = n0.getForward();
    SemiVirtualEdge pStart = n0;
    SemiVirtualDevillersEar firstEar = new SemiVirtualDevillersEar(nEar, null, n1, n0);
    SemiVirtualDevillersEar priorEar = firstEar;
    SemiVirtualDevillersEar nextEar;
    firstEar.computeScore(geoOp, vRemove);

    nEar = 1;
    do {
      n0 = n1;
      n1 = n1.getForward();
      SemiVirtualDevillersEar ear = new SemiVirtualDevillersEar(nEar, priorEar, n1, n0); //NOPMD
      ear.computeScore(geoOp, vRemove);
      priorEar = ear;
      nEar++;
    } while (!n1.equals(pStart));
    priorEar.next = firstEar;
    firstEar.prior = priorEar;


    // Step 3: Ear Closing
    // loop through the set of ears, finding the one
    // with the highest score.  The high score will ensure that
    // the resulting triangle will be Delaunay.
    // Create an edge from the high-scoring ear and
    // link it into the TIN closing one piece of the cavity.
    // Remove the ear from the collection linking its predecessor
    // and successor ears and set their newgeometry and score.
    // Repeat until only 3 ears remain.
    // Then reset the search edge based on the new TIN topology.
    //
    // I do not believe that Devillers' original paper
    // adequately covered the case where the removal vertex is
    // on the permimeter. If the deletion point was
    // on the perimeter, it is possible that the process reduced the ears
    // to the exterior edges of the network.
    //   When that happens we will be left with ears that generate
    // two kinds of triangles: degenerates and ghosts.  We do not
    // select the ears that will produce degenerate triangles.
    // We add logic to assign the "best score" to the ear that
    // has v0 as a null.  This will ensure that the newly constructed
    // edge, which starts with v2 and ends with v0, will end on a
    // null vertex, which is consistent with our special rules for
    // the TIN exterior region.
    //    With this approach, as we generate new ghost triangles,
    // the degenerates will eventually be removed from the linked list
    // of ears and finally we will be reduced to three ears.
    while (true) {
      SemiVirtualDevillersEar earMin = null;
      double minScore = Double.POSITIVE_INFINITY;
      SemiVirtualDevillersEar ear = firstEar;
      do {
        if (ear.score < minScore) {
          minScore = ear.score;
          earMin = ear;
        } else if (Double.isInfinite(minScore) && ear.v0 == null) {
          earMin = ear;
        }
        ear = ear.next;
      } while (ear != firstEar);

      if (earMin == null) {
        throw new UnsupportedOperationException(
          "Implementation failure: "
          + "Unable to identify correct geometry for vertex removal");
      }

      // close off the ear forming a triangle and
      // populate the linking references on all edges.
      // the forward reference of the new edge loops into
      // the new triangle, the reverse reference is populated so
      // that the cavity polygon is properly maintained.
      priorEar = earMin.prior;
      nextEar = earMin.next;
      SemiVirtualEdge e = edgePool.allocateEdge(earMin.v2, earMin.v0);
      e.setForward(earMin.c);  // part of final triangulation
      earMin.n.setForward(e);

      // set up references for cavity.  in most cases, these
      // are temporary until the cavity is filled.  when the
      // last ear is removed (nEar==4), these will complete
      // the circuit.
      e.setDualForward(nextEar.n); // temporary, until cavity is filled.
      priorEar.c.setForward(e.getDual());

      if (nEar == 4) {
        break;
      }

      // link the prior and next ears together
      // and adjust their edges and Devillers scores
      // to match the new geometry
      priorEar.v2 = earMin.v2;
      priorEar.n = e.getDual();
      nextEar.setReferences(priorEar, priorEar.n, priorEar.c);

      priorEar.computeScore(geoOp, vRemove);
      nextEar.computeScore(geoOp, vRemove);

      firstEar = priorEar;
      nEar--;
    }

    setSearchEdgeAfterRemoval(firstEar.c);
    return true;
  }

  /**
   * Gets a walker that is compatible with the point-spacing
   * specifications for the TIN. Intended to support interpolator
   * instances and related applications.
   *
   * @return a valid walker.
   */
  SemiVirtualStochasticLawsonsWalk getCompatibleWalker() {
    SemiVirtualStochasticLawsonsWalk cw
      = new SemiVirtualStochasticLawsonsWalk(nominalPointSpacing);
    return cw;
  }

  /**
   * Obtains an arbitrary edge to serve as the
   * start of a search or traversal operation.
   *
   * @return An ordinary (non-ghost) edge.
   */
  public SemiVirtualEdge getStartingEdge() {
    // because this method may be accessed simultaneously by multiple threads,
    // it must not modify the internal state of the instance.
    if (searchEdge == null) {
      return edgePool.getStartingEdge();
    } else {
      return searchEdge;
    }

  }

  /**
   * Specifies a rule for interpreting the Z value of a group of vertices that
   * were merged due to being coincident, or nearly coincident.
   *
   * @param resolutionRule The rule to be used for interpreting merged vertices.
   */
  @Override
  public void setResolutionRuleForMergedVertices(
    final VertexMergerGroup.ResolutionRule resolutionRule) {
    this.vertexMergeRule = resolutionRule;
    for (VertexMergerGroup c : coincidenceList) {
      c.setResolutionRule(resolutionRule);
    }
  }

  /**
   * Gets a list of vertices currently stored in the TIN. This list of objects
   * is not necessarily equivalent to the set of objects that were input because
   * some vertices may have been incorporated into one or more vertex-merger
   * groups. Note that the list of vertices is not sorted and will usually
   * not be returned in the same order as the original input set.
   *
   * @return a valid list of vertices, potentially empty if the TIN has
   * not been initialized.
   */
  @Override
  public List<Vertex> getVertices() {
    // in the logic below, we use a bitmap to keep track
    // of which edges were already inspected.  We cannot use a
    // bitmap for tracking vertices, because the vertex indices are out
    // of our control.
    int maxIndex = edgePool.getMaximumAllocationIndex();
    int maxMapIndex = maxIndex * N_SIDES + 1;
    BitSet bitset = new BitSet(maxMapIndex);

    ArrayList<Vertex> vList = new ArrayList<>(this.nVerticesInserted);
    Iterator<IQuadEdge> iEdge = edgePool.iterator();
    while (iEdge.hasNext()) {
      IQuadEdge e = iEdge.next();
      Vertex v = e.getA();
      if (v != null && !getMarkBit(bitset, e)) {
        setMarkBit(bitset, e);
        vList.add(v);
        IQuadEdge c = e;
        do {
          c = c.getForward().getForward().getDual();
          setMarkBit(bitset, c);
        } while (!c.equals(e));
      }
      IQuadEdge d = e.getDual();
      v = d.getA();
      if (v != null && !getMarkBit(bitset, d)) {
        setMarkBit(bitset, d);
        vList.add(v);
        IQuadEdge c = d;
        do {
          c = c.getForward().getForward().getDual();
          setMarkBit(bitset, c);
        } while (!c.equals(d));
      }
    }

    return vList;
  }

  @Override
  public INeighborEdgeLocator getNeighborEdgeLocator() {
    return new SemiVirtualNeighborEdgeLocator(this);
  }

  @Override
  public IIncrementalTinNavigator getNavigator() {
    return new SemiVirtualIncrementalTinNavigator(this);
  }

  @Override
  public INeighborhoodPointsCollector getNeighborhoodPointsCollector() {
    return new SemiVirtualNeighborhoodPointsCollector(this, thresholds);
  }

  @Override
  public IIntegrityCheck getIntegrityCheck() {
    return new SemiVirtualIntegrityCheck(this);
  }


  @Override
  public void addConstraints(List<IConstraint> constraints, boolean restoreConformity) {
    if (isLocked) {
      if (isDisposed) {
        throw new IllegalStateException(
          "Unable to add constraints after a call to dispose()");
      } else if (!constraintList.isEmpty()) {  //NOPMD
        throw new IllegalStateException(
          "Constraints have already been added to TIN and"
          + " no further additions are supported");
      } else {
        throw new IllegalStateException(
          "Unable to add vertex, TIN is locked");
      }
    }
    if (constraints == null || constraints.isEmpty()) {
      return;
    }

    // the max number of constraints is (2^20)-1
    if (constraints.size() > QuadEdgeConstants.CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "The maximum number of constraints is "
        + QuadEdgeConstants.CONSTRAINT_INDEX_MAX);
    }

    // Step 1 -- add all the vertices from the constraints to the TIN.
    boolean redundantVertex = false;
    for (IConstraint c : constraints) {
      c.complete();
      IConstraint reference = c;
      for (Vertex v : c) {
        boolean status = add(v);
        if (!status) {
          redundantVertex = true;
        }
      }

      if (redundantVertex) {
        Vertex prior = null;
        ArrayList<Vertex> replacementList = new ArrayList<Vertex>(); //NOPMD
        for (Vertex v : c) {
          Vertex m = this.getMatchingVertex(v);
          if (m == v) { // NOPMD
            replacementList.add(v);
            prior = v;
          } else {
            // m should never be null, but should be a vertex merger group
            if (m == prior) { // NOPMD
              continue;
            }
            replacementList.add(m);
            prior = m;
          }
        }
        reference = c.getConstraintWithNewGeometry(replacementList);
      }
      constraintList.add(reference);
    }

    // Step 2 -- Construct new edges for constraint and mark any existing
    //           edges with the constraint index.
    isLocked = true;
    IntCollector []icArray = new IntCollector[constraintList.size()];
    int k = 0;
    for (IConstraint c : constraintList) {
      c.setConstraintIndex(this, k);
      icArray[k] = new IntCollector(); // NOPMD
      processConstraint(c, icArray[k]);
      icArray[k].trimToSize();
      k++;
    }

    // TO DO:  Use an iterator instead
    //         eliminate down-casting
    if (restoreConformity) {
      List<IQuadEdge> eList = edgePool.getEdges();
      for (IQuadEdge e : eList) {
        if (e.isConstrained()) {
          SemiVirtualEdge sEdge = (SemiVirtualEdge) e;
          restoreConformity(sEdge, 1);
        }
      }
    }

    int maxIndex = getMaximumEdgeAllocationIndex();
    BitSet visited = new BitSet(maxIndex + 1);
    for (int i = 0; i < constraintList.size(); i++) {
      IConstraint c = constraintList.get(i);
      if (c.definesConstrainedRegion()) {
        floodFillConstrainedRegions(c, icArray[i], visited );
        IQuadEdge e = this.edgePool.getEdgeForIndex(icArray[i].buffer[0]);
        c.setConstraintLinkingEdge(e);
      }
    }

  }

  private boolean isMatchingVertex(Vertex v, Vertex vertexFromTin) {
    if (v.equals(vertexFromTin)) {
      return true;
    } else if (vertexFromTin instanceof VertexMergerGroup) {
      VertexMergerGroup g = (VertexMergerGroup) vertexFromTin;
      return g.contains(v);
    }
    return false;
  }

   /**
   * Will mark the edge as a constrained edge and will set the
   * constrained-region flags as necessary.  The constraint index
   * is set, but is meaningful only for region-interior edges.
   * In other cases, you may view it as a diagnostic, though its value
   * is essentially undefined.
   * @param edge a valid edge
   * @param constraint a valid constraint
   * @param edgesForConstraint the edges making up a constraint, only set
   * for region-defining constraints.
   */
  private void setConstrained(
          SemiVirtualEdge edge,
          IConstraint constraint,
          IntCollector edgesForConstraint) {
    edge.setConstrained(constraint.getConstraintIndex());
    if (constraint.definesConstrainedRegion()) {
      edgesForConstraint.add(edge.getIndex());
      edge.setConstrainedRegionBorderFlag();
      edgePool.addBorderConstraintToMap(edge, constraint);
    }else{
      edgePool.addLinearConstraintToMap(edge, constraint);
      edge.setConstraintLineMemberFlag();
    }
  }


  private void processConstraint(
    IConstraint constraint,
    IntCollector intCollector)
  {
    List<Vertex> cvList = new ArrayList<>();
    cvList.addAll(constraint.getVertices());
    if (constraint.isPolygon()) {
      // close the loop
      cvList.add(cvList.get(0));
    }
    int nSegments = cvList.size() - 1;

    double vTolerence = thresholds.getVertexTolerance();
    Vertex v0 = cvList.get(0);
    double x0 = v0.getX();
    double y0 = v0.getY();

    if (searchEdge == null) {
      searchEdge = edgePool.getStartingEdge();
    }
    searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x0, y0);
    SemiVirtualEdge e0 = null;
    if (isMatchingVertex(v0, searchEdge.getA())) {
      e0 = searchEdge;
    } else if (isMatchingVertex(v0, searchEdge.getB())) {
      e0 = searchEdge.getDual();
    } else { //if (isMatchingVertex(v0, searchEdge.getReverse().getA())) {
      e0 = searchEdge.getReverse();
    }
    Vertex a = e0.getA();
    if (a instanceof VertexMergerGroup && a != v0) {  // NOPMD
      VertexMergerGroup g = (VertexMergerGroup) a;
      if (g.contains(v0)) {
        cvList.set(0, a);
      }
    }

    // because this method may change the TIN, we cannot assume
    // that the current search edge will remain valid.
    searchEdge = null;

    double x1, y1, ux, uy, u, px, py;
    double ax, ay, ah, bx, by, bh;
    Vertex v1, b;
    segmentLoop:
    for (int iSegment = 0; iSegment < nSegments; iSegment++) {
      // e0 is now an edge which has v0 as it's initial vertex.
      // the special case where one of the edges connecting to e0
      // is the edge (v0,v1) benefits from special handling to avoid
      // potential numerical issues... especially in the case where
      // the constraint includes 3 nearly colinear edges in a row.
      // So the code below performs a pinwheel operation to test for that case.
      //   The code also checks to see if the pinwheel will move out
      // of the boundaries of the TIN (when e.getB() returns a null).
      // In that case, one of the edges in the pinwheel is the re-entry edge.
      // we assign e0 to be the re-entry edge.  This only happens when the
      // constraint edge(v0,v1) is not located within the boundary of the TIN,
      // so often the variable reEntry will stay set to null.
      v0 = cvList.get(iSegment);
      v1 = cvList.get(iSegment + 1);
      SemiVirtualEdge e = e0;
      {
        boolean priorNull = false;
        SemiVirtualEdge reEntry = null;
        do {
          b = e.getB();
          if (b == null) {
            // ghost vertex
            priorNull = true;
          } else {
            if (b == v1) { // NOPMD
              setConstrained(e, constraint, intCollector);
              e0 = e.getDual(); // set up e0 for next iteration of iSegment
              continue segmentLoop;
            } else if (b instanceof VertexMergerGroup) {
              VertexMergerGroup g = (VertexMergerGroup) b;
              if (g.contains(v1)) {
                cvList.set(iSegment + 1, g);
                setConstrained(e, constraint, intCollector);
                e0 = e.getDual(); // set up e0 for next iteration of iSegment
                continue segmentLoop;
              }
            }
            if (priorNull) {
              reEntry = e;
            }
            priorNull = false;
          }
          e = e.getDualFromReverse();
        } while (!e.equals(e0));

        if (reEntry != null) {
          e0 = reEntry;
        }
        // if reEntry is null and priorNull is true, then
        // the last edge we tested the B value for was null.
        // this would have been the edge right before e0, which
        // means that e0 is the reEntry edge.
      }

      // pinwheel to find the right-side edge of a triangle
      // which overlaps the constraint segment.  The segment may be entirely
      // contained in this triangle, or may intersect the edge opposite v0.
      x0 = v0.getX();
      y0 = v0.getY();
      x1 = v1.getX();
      y1 = v1.getY();
      ux = x1 - x0;
      uy = y1 - y0;
      u = Math.sqrt(ux * ux + uy * uy);
      // TO DO: test for vector too small
      ux /= u; // unit vector
      uy /= u;
      px = -uy;  // perpendicular
      py = ux;

      // The search should now be positioned on v0.  We've already verified
      // that v0 does not connect directly to v1, so we need to find
      // the next vertex affected by the constraint.
      //    There is also the case where the one of the connecting edges is colinear
      // (or nearly colinear) with the constraint segment. If we find a
      // vertext that is sufficiently close to the constraint segment,
      // we insert the vertex into the constraint (making a new segment)
      // and continue on to the newly formed segment.
      SemiVirtualEdge h = null;
      SemiVirtualEdge right0 = null;
      SemiVirtualEdge left0 = null;
      SemiVirtualEdge right1 = null;
      SemiVirtualEdge left1 = null;

      // begin the pre-loop initialization.  The search below performs a pinwheel
      // through the edge that start with v0, looking for a case where the
      // edge opposite v0 straddles the constraint segment.  We call the
      // candidate edges n where n=edge(a,b).  As we loop, the b from one
      // test is the same as the a for the next test. So we copy values
      // from b into a at the beginning of the loop.  To support that, we
      // pre-initialize b before enterring the loop.  This pre-initialization
      // must also include the side-of-edge calculation, bh, which is the
      // coordinate of (bx,by) in the direction of the perpendicular.
      //    The pre-test must also test for the case where the first edge
      // in the pinwheel lies on or very close to the ray(v0, v1).
      // The logic is similar to that inside the loop, except that a
      // simple dot product is sufficient to determine if the vertex is
      // in front of, or behind, the ray (see the comments in the loop for
      // more explanation.
      b = e0.getB();
      bx = b.getX() - x0;
      by = b.getY() - y0;
      bh = bx * px + by * py;
      if (Math.abs(bh) <= vTolerence && bx * ux + by * uy > 0) {
        // edge e0 is either colinear or nearly colinear with
        // ray(v0,v1). insert it into the constraint, set up e0 for the
        // next segment, and advance to the next segment in the constraint.
        cvList.add(iSegment + 1, b);
        nSegments++;
        setConstrained(e0, constraint, intCollector);
        e0 = e0.getDual(); // set up e0 for next iteration of iSegment
        continue; // continue segmentLoop;
      }

      // perform a pinwheel, testing each sector to see if
      // it contains the constraint segment.
      e = e0;
      do {
        // copy calculated values from b to a.
        ax = bx;
        ay = by;
        ah = bh;
        SemiVirtualEdge n = e.getForward(); //the edge opposite v0

        // TO DO: the following code is commented out because it should
        // no longer be necessary.  The test for the reEntry edge above
        // should have positioned e0 so that the pinwheel will find the
        // straddle point before it reaches the ghost edge.  The only case
        // where this code would fail (and b would be null) would be when
        // something we haven't anticipated happens and the straddle isn't found.
        //   // be wary of the ghost vertex case
        //   b = n.getB();
        //   if (b == null) {
        //      // TO DO: does this actually happen anymore now that
        //      // the reEntry logic was added above?
        //      bh = Double.NaN;
        //      e = e.getDualFromReverse();
        //      continue;
        //   }
        b = n.getB();
        bx = b.getX() - x0;
        by = b.getY() - y0;
        bh = bx * px + by * py;
        if (Math.abs(bh) <= vTolerence) {
          // the edge e is either colinear or nearly colinear with the
          // line through vertices v0 and v1.  We need to see if the
          // straddle point lies on or near the ray(v0,v1).
          // this is complicated slightly by the fact that some points
          // on the edge n could be in front of v0 (a positive direction
          // on the ray) while others could be behind it.  So there's
          // no way around it, we have to compute the intersection.
          // Of course, we don't need to compute the actual points (x,y)
          // of the intersection, just the parameter t from the parametric
          // equation of a line. If t is negative, the intersection is
          // behind the ray. If t is positive, the intersection is in front
          // of the ray.  If t is zero, the TIN insertion algorithm failed and
          // we have an implementation problem elsewhere in the code.
          double dx = bx - ax;
          double dy = by - ay;
          double t = (ax * dy - ay * dx) / (ux * dy - uy * dx);
          if (t > 0) {
            // edge e is either colinear or nearly colinear with
            // ray(v0,v1). insert it into the constraint, set up e0 for
            // the next loop, and then advance to the next constraint segment.
            cvList.add(iSegment + 1, b);
            nSegments++;
            e0 = e.getReverse(); // will be (b, v0), set up for next iSegment
            setConstrained(e0.getDual(), constraint, intCollector);
            continue segmentLoop;
          }
        }

        // test to see if the segment (a,b) crosses the line (v0,v1).
        // if it does, the intersection will either be behind the
        // segment (v0,v1) or on it.  The t variable is from the
        // parametric form of the line equation for the intersection
        // point (x,y) such that
        //   (x,y) = t*(ux, uy) + (v0.x, v0.y)
        double hab = ah * bh;
        if (hab <= 0) {
          double dx = bx - ax;
          double dy = by - ay;
          double t = (ax * dy - ay * dx) / (ux * dy - uy * dx);
          if (t > 0) {
            right0 = e;
            left0 = e.getReverse();
            h = n.getDual();
            break;
          }
        }
        e = e.getDualFromReverse();
      } while (!e.equals(e0));

      // step 2 ------------------------------------------
      // h should now be non-null and straddles the
      // constraint, vertex a is to its right
      // and vertex b is to its left.  we have already
      // tested for the cases where either a or b lies on (v0,v1)
      // begin digging the cavities to the left and right of h.
      if (h == null) {
        throw new IllegalStateException("Internal failure, constraint not added");
      }
      Vertex c = null;
      while (true) {
        right1 = h.getForward();
        left1 = h.getReverse();
        c = right1.getB();
        if (c == null) {
          throw new IllegalStateException("Internal failure, constraint not added");
        }
        removeEdge(h);
        double cx = c.getX() - x0;
        double cy = c.getY() - y0;
        double ch = cx * px + cy * py;
        if (Math.abs(ch) < vTolerence && cx * ux + cy * uy > 0) {
          // Vertex c is on the edge.  We will break the loop and
          // then construct a new segment from v0 to c.
          //   We need to ensure that c shows up in the constraint
          // vertex list.  But it is possible that c is actually a
          // vertex merger group that contains v1 (this could happen
          // if there were sample points in the original tin that
          // we coincident with v1 and also some that appeared between
          // v0 and v1, so that the above tests didn't catch an edge.

          if (!c.equals(v1)) {
            if (c instanceof VertexMergerGroup && ((VertexMergerGroup) c).contains(v1)) {
              cvList.set(iSegment + 1, c);
            } else {
              cvList.add(iSegment + 1, c);
              nSegments++;
            }
          }

          break;
        }

        double hac = ah * ch;
        double hbc = bh * ch;
        if (hac == 0 || hbc == 0) {
          throw new IllegalStateException("Internal failure, constraint not added");
        }

        if (hac < 0) {
          // branch right
          h = right1.getDual();
          bx = cx;
          by = cy;
          bh = bx * px + by * py;
        } else {
          // branch left (could hbc be zero?)
          h = left1.getDual();
          ax = cx;
          ay = cy;
          ah = ax * px + ay * py;
        }
      }

      // insert the constraint edge
      SemiVirtualEdge n = edgePool.allocateEdge(v0, c);
      setConstrained(n, constraint, intCollector);
      SemiVirtualEdge d = n.getDual();
      n.setForward(left1);
      n.setReverse(left0);
      d.setForward(right0);
      d.setReverse(right1);
      e0 = d;

      fillCavity(n);
      fillCavity(d);
    }

    searchEdge = e0;

  }

  private void restoreConformity(SemiVirtualEdge ab, int depthOfRecursion) {
    if (depthOfRecursion > maxDepthOfRecursionInRestore) {
      maxDepthOfRecursionInRestore = depthOfRecursion;
    }

    SemiVirtualEdge ba = ab.getDual();
    SemiVirtualEdge bc = ab.getForward();
    SemiVirtualEdge ad = ba.getForward();
    Vertex a = ab.getA();
    Vertex b = ab.getB();
    Vertex c = bc.getB();
    Vertex d = ad.getB();
    if (a == null || b == null || c == null || d == null) {
      return;
    }

    // If the edge passes the inCircle test, treat it as Delaunay.
    // Here the test uses a small threshold value because this the numeric
    // calculation is limited by floating-point precision issues.  We have
    // seen cases where no number of recursive subdivisions is sufficient
    // to produce a calculated result of a zero or less.
    double h = geoOp.inCircle(a, b, c, d);
    if (h <= thresholds.getDelaunayThreshold()) {
      return;
    }

    SemiVirtualEdge ca = ab.getReverse();
    SemiVirtualEdge db = ba.getReverse();

    if (ab.isConstrained()) {
      // subdivide the constraint edge to restore conformity
      double mx = (a.getX() + b.getX()) / 2.0;
      double my = (a.getY() + b.getY()) / 2.0;
      double mz = (a.getZ() + b.getZ()) / 2.0;
      Vertex m = new Vertex(mx, my, mz, nSyntheticVertices++);
      m.setStatus(Vertex.BIT_SYNTHETIC | Vertex.BIT_CONSTRAINT);

      // split ab by inserting midpoint m.  ab will become the second segment
      // the newly allocated point will become the first.
      // we assign variables to local references with descriptive names
      // such as am, mb, etc. just to avoid confusion.
      SemiVirtualEdge am = edgePool.splitEdge(ab, m);
      SemiVirtualEdge mb = ab;
      SemiVirtualEdge bm = ba;

      // create new edges
      SemiVirtualEdge cm = edgePool.allocateEdge(c, m);
      SemiVirtualEdge dm = edgePool.allocateEdge(d, m);
      SemiVirtualEdge ma = am.getDual();
      SemiVirtualEdge mc = cm.getDual();
      SemiVirtualEdge md = dm.getDual();

      ma.setForward(ad);  // should already be set
      ad.setForward(dm);
      dm.setForward(ma);

      mb.setForward(bc);
      bc.setForward(cm);
      cm.setForward(mb);

      mc.setForward(ca);
      ca.setForward(am); // should already be set
      am.setForward(mc);

      md.setForward(db);
      db.setForward(bm);
      bm.setForward(md);
      restoreConformity(am, depthOfRecursion+1);
      restoreConformity(mb, depthOfRecursion+1);
    } else {
      // the edge is not constrained, so perform a flip to restore Delaunay
      ab.setVertices(d, c);
      ab.setReverse(ad);
      ab.setForward(ca);
      ba.setReverse(bc);
      ba.setForward(db);
      ca.setForward(ad);
      db.setForward(bc);
    }

    restoreConformity(bc.getDual(), depthOfRecursion+1);
    restoreConformity(ca.getDual(), depthOfRecursion+1);
    restoreConformity(ad.getDual(), depthOfRecursion+1);
    restoreConformity(db.getDual(), depthOfRecursion+1);
  }

  private void removeEdge(SemiVirtualEdge e) {
    SemiVirtualEdge d = e.getDual();
    SemiVirtualEdge dr = d.getReverse();
    SemiVirtualEdge df = d.getForward();
    SemiVirtualEdge ef = e.getForward();
    SemiVirtualEdge er = e.getReverse();

    dr.setForward(ef);
    df.setReverse(er);
    edgePool.deallocateEdge(e);
  }

  // A fill score based on the inCircle function will also work here
  // and would have the advantage of removing the flip-test in the
  // second half of the fillCavity routine.
  //   In testing, it appeared slower, but there was some uncertaintly
  // about the correctness of the implementation. So further testing
  // would be worthwhile.
  private void fillScore(SemiVirtualDevillersEar ear) {
    ear.score = geoOp.area(ear.v0, ear.v1, ear.v2);

    if (ear.score > 0) {
      double x0 = ear.v0.getX();
      double y0 = ear.v0.getY();
      double x1 = ear.v1.getX();
      double y1 = ear.v1.getY();
      double x2 = ear.v2.getX();
      double y2 = ear.v2.getY();

      SemiVirtualDevillersEar e = ear.next;
      while (e != ear.prior) {

        if (e.v2 != ear.v0 && e.v2 != ear.v1 && e.v2 != ear.v2) {
          double x = e.v2.getX();
          double y = e.v2.getY();
          if (geoOp.halfPlane(x0, y0, x1, y1, x, y) >= 0
            && geoOp.halfPlane(x1, y1, x2, y2, x, y) >= 0
            && geoOp.halfPlane(x2, y2, x0, y0, x, y) >= 0) {
            ear.score = Double.POSITIVE_INFINITY;
            break;
          }
        }
        e = e.next;
      }

    }
  }

  /**
   * Fills a cavity that was created by removing edges from the
   * TIN. It is assumed that all the edges of the cavity are either
   * Delaunay or are constrained edge.
   *
   * @param cavityEdge a valid edge.
   */
  private void fillCavity(SemiVirtualEdge cavityEdge) {
    // initialize edges needed for removal

    SemiVirtualEdge n0, n1;

    // The cavity will often be just a triangle.
    // If so, it doesn't need to be filled. However, a
    // multipoint cavity may include a triangle or a dangling edge
    // as part of its geometry. This fact means that there are cases
    // where simply comparing the forward reference with the reverse reference
    // will fail.  Instead, we need to survey the entire cavity and
    // count up the number of vertices.
    //   TO DO: if cases where there are only three edges involved
    //          occur often enough, there might be efficiency in counting up
    //          the edges before creating ears.  If it is not often enough,
    //          then we might be better served by just leaving it as is.
    // Step 1 -- Ear Creation
    //    Create a set of Devillers Ears around
    //    the polygonal cavity.
    int nEar = 0;
    n0 = cavityEdge;
    n1 = n0.getForward();
    SemiVirtualEdge pStart = n0;
    SemiVirtualDevillersEar firstEar = new SemiVirtualDevillersEar(nEar, null, n1, n0);
    SemiVirtualDevillersEar priorEar = firstEar;
    SemiVirtualDevillersEar nextEar;

    nEar = 1;
    do {
      n0 = n1;
      n1 = n1.getForward();
      SemiVirtualDevillersEar ear = new SemiVirtualDevillersEar(nEar, priorEar, n1, n0); // NOPMD
      priorEar = ear;
      nEar++;
    } while (!n1.equals(pStart));
    priorEar.next = firstEar;
    firstEar.prior = priorEar;

    if (nEar == 3) {
      return;
    }

    SemiVirtualDevillersEar eC = firstEar.next;
    fillScore(firstEar);
    while (eC != firstEar) {  //NOPMD
      fillScore(eC);
      eC = eC.next;
    }

    ArrayList<SemiVirtualEdge> list = new ArrayList<>();
    while (true) {
      SemiVirtualDevillersEar earMin = null;
      double minScore = Double.POSITIVE_INFINITY;
      SemiVirtualDevillersEar ear = firstEar;
      do {
        if (ear.score < minScore && ear.score > 0) {
          minScore = ear.score;
          earMin = ear;
        }
        ear = ear.next;
      } while (ear != firstEar);  //NOPMD

      if (earMin == null) {
        throw new IllegalStateException(
          "Implementation failure: "
          + "Unable to identify correct geometry for cavity fill");
      }

      // close off the ear forming a triangle and
      // populate the linking references on all edges.
      // the forward reference of the new edge loops into
      // the new triangle, the reverse reference is populated so
      // that the cavity polygon is properly maintained.
      priorEar = earMin.prior;
      nextEar = earMin.next;
      SemiVirtualEdge e = edgePool.allocateEdge(earMin.v2, earMin.v0);
      SemiVirtualEdge d = e.getDual();
      e.setForward(earMin.c);
      e.setReverse(earMin.n);
      d.setForward(nextEar.n);
      d.setReverse(priorEar.c);

      list.add(e);

      // if there are 4 ears left, the edge that was just added will
      // have closed the 4-point polygon, resulting in a filled cavity
      if (nEar == 4) {
        break;
      }

      // link the prior and next ears together
      // and adjust their edges and area scores
      // to match the new geometry
      priorEar.next = nextEar;
      nextEar.prior = priorEar;
      priorEar.v2 = earMin.v2;
      priorEar.n = d;
      nextEar.c = d;
      nextEar.p = priorEar.c;
      nextEar.v0 = earMin.v0;
      fillScore(priorEar);
      fillScore(nextEar);

      firstEar = priorEar;
      nEar--;
    }

    // Step 2 -- Edge correction
    //  Loop through the nearly created edges and
    //  flip any edges that violate the Delaunay criterion.
    //  Flipping one edge may change the Delaynay correctness of its
    //  neighbors.
    int k = list.size();
    int k2 = k * k;
    for (int i = 0; i < k2; i++) {
      int flipped = 0;
      for (SemiVirtualEdge n : list) {
        SemiVirtualEdge d = n.getDual();
        SemiVirtualEdge nf = n.getForward();
        SemiVirtualEdge df = d.getForward();
        Vertex a = n.getA();
        Vertex b = n.getB();
        Vertex c = nf.getB();
        Vertex t = df.getB();
        double h = geoOp.inCircle(a, b, c, t);
        if (h > 0) {
          flipped++;
          // flip n
          SemiVirtualEdge nr = n.getReverse();
          SemiVirtualEdge dr = d.getReverse();
          n.setVertices(t, c);
          n.setForward(nr);
          n.setReverse(df);
          d.setForward(dr);
          d.setReverse(nf);
          dr.setForward(nf);
          nr.setForward(df);
        }
      }
      if (flipped == 0) {
        break;
      }
    }
  }

  /**
   * Marks all edges inside a constrained region as being members of
   * that region (transferring the index value of the constraint to
   * the member edges). The name of this method is based on the idea
   * that the operation resembles a flood-fill algorithm from computer graphics.
   * @param c a constraint defining a region to be flood filled
   * @param intCollector a collection of the integer index values for
   * the edges that correspond to the boundary of the constrained region
   */
  private void floodFillConstrainedRegions(
    IConstraint c,
    IntCollector intCollector,
    BitSet visited) {
    int constraintIndex = c.getConstraintIndex();
    for (int i = 0; i < intCollector.n; i++) {
      IQuadEdge e = this.edgePool.getEdgeForIndex(intCollector.buffer[i]);
      if (e.isConstrainedRegionBorder()) {
        floodFillConstrainedRegionsQueue( constraintIndex, visited, e);
      }
    }
  }


  private void floodFillConstrainedRegionsQueue(
          final int constraintIndex,
          final BitSet visited,
          final IQuadEdge firstEdge) {
    // While the following logic could be more elegantly coded
    // using recursion, the depth of the recursion could get so deep that
    // it would overflow any reasonably sized stack.  So we use as
    // explicitly coded stack instead.
    //    There is special logic here for the case where an alternate constraint
    // occurs inside the flood-fill area. For example, a linear constraint
    // might occur inside a polygon (a road might pass through a town).
    // The logic needs to preserve the constraint index of thecontained
    // edge from the alternate constraint. In that case, the flood fill
    // passes over the embedded edge, but does not modify it.
    ArrayDeque<IQuadEdge> deque = new ArrayDeque<>();
    deque.push(firstEdge);
    while (!deque.isEmpty()) {
      if (deque.size() > maxLengthOfQueueInFloodFill) {
        maxLengthOfQueueInFloodFill = deque.size();
      }
      IQuadEdge e = deque.peek();
      IQuadEdge f = e.getForward();
      int fIndex = f.getIndex();
      if (!f.isConstrainedRegionBorder() && !visited.get(fIndex)) {
        visited.set(fIndex);
        f.setConstrainedRegionInteriorFlag();
        f.setConstraintIndex(constraintIndex);
        deque.push(f.getDual());
        continue;
      }
      IQuadEdge r = e.getReverse();
      int rIndex = r.getIndex();
      if (!r.isConstrainedRegionBorder() && !visited.get(rIndex)) {
        visited.set(rIndex);
        r.setConstrainedRegionInteriorFlag();
        r.setConstraintIndex(constraintIndex);
        deque.push(r.getDual());
        continue;
      }
      deque.pop();
    }
  }


  @Override
  public List<IConstraint> getConstraints() {
    List<IConstraint> result = new ArrayList<>();
    result.addAll(constraintList);
    return result;
  }


  @Override
  public IConstraint getConstraint(int index){
    if(index<0 || index>=constraintList.size()){
      return null;
    }
    return constraintList.get(index);
  }


  @Override
  public IConstraint getRegionConstraint(IQuadEdge edge) {
    if (edge.isConstrainedRegionInterior()) {
      int index = edge.getConstraintIndex();
      // the test for constraintList.size() should be completely
      // unnecessary, but we do it just in case.
      if (index < constraintList.size()) {
        return constraintList.get(index);
      }
    } else if (edge.isConstrainedRegionBorder()) {
      return edgePool.getBorderConstraint(edge);
    }
    return null;
  }

  @Override
  public IConstraint getLinearConstraint(IQuadEdge edge) {
    return edgePool.getLinearConstraint(edge);
  }


  @Override
  public int getSyntheticVertexCount() {
    return nSyntheticVertices;
  }

  private Vertex getMatchingVertex(Vertex v) {
    if (v == null) {
      return null;
    }

    final double x = v.x;
    final double y = v.y;

    if (searchEdge == null) {
      searchEdge = edgePool.getStartingEdge();
    }
    walker.findAnEdgeFromEnclosingTriangleInternal(searchEdge, x, y);

    if (checkTriangleVerticesForMatchInternal(searchEdge, x, y, vertexTolerance2)) {
      Vertex a = searchEdge.getA();
      if (a == v) { // NOPMD
        // this vertex was already inserted.
        return v;
      }
      VertexMergerGroup group;
      if (a instanceof VertexMergerGroup) {
        group = (VertexMergerGroup) a;
        if (group.contains(v)) {
          return group;
        }
      }
    }
    return null;
  }

  @Override
  public Vertex splitEdge(
          IQuadEdge eInput,
          double zSplit,
          boolean restoreConformity)
  {
    if (restoreConformity) {
      throw new UnsupportedOperationException(
              "The restoreConformity option is not yet implemented");
    }

    int index = eInput.getIndex();
    SemiVirtualEdge eTest = edgePool.getEdgeForIndex(index);
    if (eTest == null
            || eTest.getA() != eInput.getA()
            || eTest.getB() != eInput.getB()) {
      throw new IllegalArgumentException(
              "Specified edge does not belong to this instance for edge index "
              + index);
    }


   SemiVirtualEdge ab = (SemiVirtualEdge) eInput;
    // TO DO: implement a check to make sure that eInput
    //        is a valid edge for this TIN instance.

    SemiVirtualEdge ba = ab.getDual();
    SemiVirtualEdge bc = ab.getForward();
    SemiVirtualEdge ad = ba.getForward();
    Vertex a = ab.getA();
    Vertex b = ab.getB();
    Vertex c = bc.getB();
    Vertex d = ad.getB();

    if (a == null || b == null) {
      return null;
    }

    SemiVirtualEdge ca = ab.getReverse();
    SemiVirtualEdge db = ba.getReverse();
    // subdivide the constraint edge to restore conformity
    double mx = (a.getX() + b.getX()) / 2.0;
    double my = (a.getY() + b.getY()) / 2.0;
    double mz = zSplit;

    Vertex m = new Vertex(mx, my, mz, nSyntheticVertices++);
    if (ab.isConstrained()) {
      m.setStatus(Vertex.BIT_SYNTHETIC | Vertex.BIT_CONSTRAINT);
    } else {
      m.setStatus(Vertex.BIT_SYNTHETIC);
    }

    // split ab by inserting midpoint m.  ab will become the second segment
    // the newly allocated point will become the first.
    // we assign variables to local references with descriptive names
    // such as am, mb, etc. just to avoid confusion.
    SemiVirtualEdge am = edgePool.splitEdge(ab, m);
    SemiVirtualEdge mb = ab;
    SemiVirtualEdge bm = ba;

    // create new edges
    SemiVirtualEdge cm = edgePool.allocateEdge(c, m);
    SemiVirtualEdge dm = edgePool.allocateEdge(d, m);
    SemiVirtualEdge ma = am.getDual();
    SemiVirtualEdge mc = cm.getDual();
    SemiVirtualEdge md = dm.getDual();
    ma.setForward(ad);  // should already be set
    ad.setForward(dm);
    dm.setForward(ma);
    mb.setForward(bc);
    bc.setForward(cm);
    cm.setForward(mb);
    mc.setForward(ca);
    ca.setForward(am); // should already be set
    am.setForward(mc);
    md.setForward(db);
    db.setForward(bm);
    bm.setForward(md);

    return m;

  }

  @Override
  public Iterable<SimpleTriangle> triangles() {
    final SimpleTriangleIterator sti = new SimpleTriangleIterator(this);
    return new Iterable<SimpleTriangle>() {
      @Override
      public Iterator<SimpleTriangle> iterator() {
        return sti;
      }
    };
  }

  @Override
  public Iterable<Vertex> vertices() {
    final VertexIterator vertexIterator = new VertexIterator(this);
    return new Iterable<Vertex>() {
      @Override
      public Iterator<Vertex> iterator() {
        return vertexIterator;
      }
    };
  }
}
