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
 * 02/2013  G. Lucas     Initial implementation using triangles as underlying
 *                          data structure for TIN.
 * 08/2013  G. Lucas     Introduced the Bowyer Watson logic to speed up
 *                          insertions
 * 03/2014  G. Lucas     Added vertex removal logic
 * 04/2014  G. Lucas     Consolidated all previous changes and performed
 *                          clean up, removing unused elements
 * 05/2014  G. Lucas     Implemented ghost triangles.
 * 05/2014  G. Lucas     Introduced IntegrityCheck class, performed extensive
 *                          and tedious correctness-of-code testing and fixes.
 * 05/2014  G. Lucas     Moved walk logic into separate class
 * 05/2014  G. Lucas     Corrected treatment of NNI when it includes triangles
 *                          on the boundary of the convex hull.
 * --------------------------------------------------------------------
 * 06/15 G. Lucas    Refactored original triangle-based implementation
 *                       and introduced edge-based representation with the
 *                       goal or reducing overall memory use and perhaps
 *                       gaining speed.
 * 07/2015 G. Lucas  Completed insertion, began vertex removal logic.
 * 08/2015 G. Lucas  Completed vertex removal, collected points in neighborhood,
 *                       implemented additional testing logic and diagnostics.
 * 11/2015 G. Lucas  Additional extraction of interfaces to support
 *                       interchangable use with virtual TIN implementation
 * 10/2016 G. Lucas  Implemented ability to add constraint geometries to
 *                       produce a Constrained Delaunay Triangulation (CDT).
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.standard;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tinfour.common.BootstrapUtility;
import tinfour.common.GeometricOperations;
import tinfour.common.IConstraint;
import tinfour.common.IIncrementalTin;
import tinfour.common.IIntegrityCheck;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.INeighborEdgeLocator;
import tinfour.common.INeighborhoodPointsCollector;
import tinfour.common.IQuadEdge;
import tinfour.common.QuadEdge;
import tinfour.common.Thresholds;
import tinfour.common.TriangleCount;
import tinfour.common.Vertex;
import tinfour.common.VertexMergerGroup;

/**
 * Provides methods and data elements for building and maintaining a
 * Triangulated Irregular Network (TIN) that is optimal with regard to the
 * Delaunay criterion.
 * <p>
 * The Delaunay Triangulation has several desirable properties and is well
 * documented on the Internet. The TIN produced by this class is meets the
 * Delaunay criterion except in cases where round-off errors due to the limits
 * of floating point calculations result in small deviations from the optimum.
 * <p>
 * There are three major classes of algorithms for creating a Delaunay
 * Triangulation: sweep-line algorithms, divide-and-conquer, and incremental
 * construction. In the incremental algorithm used for this implementation,
 * vertices are added to the TIN one-at-a time. If a vertex lies inside the
 * convex hull of an existing TIN, it is inserted. If the vertex lies to the
 * exterior, the bounds of the TIN is extended to include it. Delaunay
 * optimality is maintained at each step.
 * <h1>Memory use and performance</h1>
 * <p>
 * This class was designed to handle cases where the input set includes a large
 * number of vertices. In particular, terrain elevation data sets collected
 * using laser devices (lidar) that typically include multiple millions of data
 * points. With such large input sets, performance and memory-management are
 * critical issues.
 * <p>
 * Naturally, memory use and performance varies by hardware, operating system,
 * and Java Virtual Machine (HVM). In 2015, testing lidar data under Windows 7
 * on a computer with a 2.9 GHz Intel i7 processor, 8 gigabytes installed
 * memory, 512 kilobytes of L2 cache memory, and Hotspot JVM, this class
 * routinely delivered a processing rate of 1.1 million vertices per second.
 * Time-complexity for samples smaller than 10 million was nearly linear. Memory
 * use averaged 244 bytes per vertex.
 * <h2>Memory Use</h2>
 * <p>
 * About a third of the memory use by this class when running under Hotspot is
 * due to Java object-related overhead rather than actual data. Software
 * environments such as Java and C# provide automatic garbage collection and
 * memory management. Doing so adds a small amount of memory overhead to each
 * object created. Because the data-size of the objects used to build a TIN
 * (vertices and edges) is also small, this overhead is significant. In a
 * sufficiently large Delaunay Triangulation, the number of edges approaches
 * three per vertex. This implementation uses one object per vertex and two per
 * edge. Although the memory overhead for Java varies for different operating
 * systems and Java Virtual Machines (JVMs), the Hotspot JVM for Windows uses 12
 * bytes per object. Thus for each vertex, it requires (1+3*2)x12 = 84 bytes of
 * overhead.
 * <h2>Performance</h2>
 * <h3>Managing overhead due to object construction</h3>
 * Testing indicates that the most time-consuming part of the TIN construction
 * operation is the construction of Java objects. As noted above, this class
 * requires 6 edge-related objects per vertex. Although this overhead is
 * inescapable when processing a single data set, this class does permit a TIN
 * instance to be reused over-and-over again when processing multiple data sets.
 * A call to the clear() method resets the TIN to an empty state, but preserves
 * the edges already allocated so that they may be reused for the next data set.
 * By doing so, the cost of the up-front construction of edge objects can be
 * amortized over the entire data set, this reducing the processing time for a
 * group of multiple input sets. Applications that do so should be able to
 * improve on the run-time performance values quoted above.
 * <h3>Input geometry</h3>
 * The worst case vertex geometry for TIN construction is a data set in which a
 * large number of points are collinear and do not form triangles readily.
 * Unfortunately, that is exactly the geometry of one of the most obvious
 * classes of input: the regular grid. This class supports two different add()
 * methods for adding vertices to the TIN. When dealing with a regular grid or
 * similar geometries, it is advantageous to use the add() method that takes a
 * list as an input rather than the one that accepts single vertices. Having a
 * list of vertices gives this class more flexibility in constructing the TIN.
 * <p>
 * The process of inserting a vertex within a TIN requires fewer operations than
 * extending the convex hull of that TIN. If a list of vertices is supplied to
 * the initial add routine, the bootstrap process attempts pick the largest
 * starting triangle that it can without excessive processing. Doing so improves
 * performance and stability of the build process.
 * <h3>Storing the same vertex more than once</h3>
 * The add() methods detect when the same vertex object is inserted more than
 * once and ignore redundant inputs. For distinct vertex objects at the same or
 * nearly same coordinates, this class maintains a "merged group" of vertices.
 * Rules for disambiguating the values of a merged group my be specified using a
 * call to the setResolutionRuleForMergedVertices() method.
 * <h3>Sequential locality</h3>
 * <p>
 * Inserting a vertex into a TIN depends on identifying the triangle that
 * contains an insertion vertex (if any). This class uses the Stochastic
 * Lawson's Walk algorithm (SLW) that is most efficient when subsequent vertices
 * tend to be spaced close together. This condition is usually met by lidar data
 * because its measurements are derived from scanning lasers and stored in order
 * taken. Other data sources may not be compliant. Randomly generated data
 * points, in particular, may be problematic. For such data, there may be a
 * performance benefit in using the HilbertSort class to pre-order points before
 * insertion so that sequential locality is provided by the input data.
 * <p>
 * One way to judge the degree of sequential locality in an input set of
 * vertices is to view the output of the printDiagnostics() method after
 * building a TIN. Under the entry for the SLW statistics, the "average steps to
 * completion" indicates how many comparisons were needed to locate vertices. If
 * this number is larger than 7 or 8, it may be useful to try using the
 * HilbertSort and see if it improves processing times.
 * <h3>Cleaning up when finished</h3>
 * <p>
 * Because of the complex relationships between objects in a TIN, Java garbage
 * collection may require an above average number of passes to clean up memory
 * when an instance of this class goes out-of-scope. The dispose() method can be
 * used to expedite garbage collection. Once the dispose() method is called on a
 * TIN, it cannot be reused. Do not confuse dispose() with clear().
 * <h3>Running nude</h3>
 * <p>
 * Because of the unusually demanding performance considerations related to the
 * use of this class, object instances are frequently reused and, thus, are
 * subject to change. Thus the implementation provides little protection for
 * applications accessing its data. In particular, applications must never
 * modify an object (such as an edge) obtained from instances of this class.
 * Furthermore, they must assume that any addition or removal of vertices to the
 * TIN may change the internal state of any objects previously obtained.
 * <p>
 * To better understand the re-use strategy, consider that each time a vertex is
 * added to or removed from a TIN, the set of edges that link vertices changes.
 * Some edges may be removed, others added. Testing with lidar data sets
 * indicates that the present implementation re-uses each edge in the collection
 * a average about 7.5 times while the TIN is being constructed. If the
 * application were to treat edges as immutable, it would have to construct new
 * objects each time a vertex was inserted and many of those edge objects would
 * have to be discarded (and garbage collected) before the entire vertex set was
 * processed. Doing so would substantially degrade the performance of this
 * class.
 * <h1>Methods and References</h1>
 * <p>
 * A good review of point location using a stochastic Lawson's walk is provided
 * by <cite>Soukal, R.; Ma&#769;lkova&#769;, Kolingerova&#769; (2012) "Walking
 * algorithms for point location in TIN models", Computational Geoscience
 * 16:853-869</cite>.
 * <p>
 * The Bower-Watson algorithm for point insertion is discussed in
 * <cite>Cheng, Siu-Wing; Dey, T.; Shewchuk, J. (2013) "Delaunay mesh
 * generation", CRC Press, Boca Raton, FL</cite>. This is a challenging book
 * that provides an overview of both 2D and solid TIN models. Jonathan Shewchuk
 * is pretty much the expert on Delaunay Triangulations and his writings were a
 * valuable resource in the creation of this class. You can also read Bowyer's
 * and Watson's original papers both of which famously appeared in the same
 * issue of the same journal in 1981. See
 * <cite>Bowyer, A. (1981) "Computing Dirichlet tesselations", The Computer
 * Journal" Vol 24, No 2., p. 162-166</cite>. and
 * <cite>Watson, D. (1981) "Computing the N-dimensional tesselation with
 * application to Voronoi Diagrams", The Computer Journal" Vol 24, No 2., p.
 * 167-172</cite>.
 * <p>
 * The point-removal algorithm is due to Devillers. See
 * <cite>Devillers, O. (2002), "On deletion in delaunay triangulations",
 * International Journal of Computational Geometry &amp; Applications 12.3 p.
 * 123-2005</cite>.
 * <p>
 * The QuadEdge concept is based on the structure popularized by
 * <cite>Guibas, L. and Stolfi, J. (1985) "Primitives for the manipulation of
 * subdivisions and the computation of Voronoi diagrams", ACM Transactions on
 * Graphics, 4(2), 1985, p. 75-123.</cite>
 * <p>
 * The logic for adding constraints to the TIN was adapted from
 * <cite>Sloan, S.W. (1993) "A Fast Algorithm for Generating Constrained
 * Delaunay Triangulations", Computers &amp; Structures Vol 47. No 3, 1993,
 * p. 441-450.</cite>
 */
@SuppressWarnings("PMD.CompareObjectsWithEquals") // we do that on purpose!
public class IncrementalTin implements IIncrementalTin {

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
  private final EdgePool edgePool;
  /**
   * The edge used to preserve the end-position of the most recent search
   * results.
   */
  private QuadEdge searchEdge;

  /**
   * Indicates that the TIN is locked and that calls to add or remove vertices
   * are disabled. This can occur when the TIN is disposed, or when
   * constraints are added to the TIN.
   */
  private boolean isLocked;
  /**
   * Indicates that the TIN is disposed. All internal objects associated with
   * the current instance are put out-of-scope and the class is no longer
   * usable.
   */
  private boolean isDisposed;

  /**
   * The minimum x coordinate of all vertices that have been added to the TIN.
   */
  private double boundsMinX = Double.POSITIVE_INFINITY;

  /**
   * The maximum y coordinate of all vertices that have been added to the TIN.
   */
  private double boundsMinY = Double.POSITIVE_INFINITY;

  /**
   * The maximum x coordinate of all vertices that have been added to the TIN.
   */
  private double boundsMaxX = Double.NEGATIVE_INFINITY;

  /**
   * The maximum y coordinate of all vertices that have been added to the TIN.
   */
  private double boundsMaxY = Double.NEGATIVE_INFINITY;

  /**
   * The nominal spacing between points in sample data, used to specify
   * thresholds for geometry-based logic and comparisons.
   */
  private final double nominalPointSpacing;

  /**
   * The positive threshold used to determine if a higher-precision
   * calculation is required for performing calculations related to the
   * half-plane calculation. When a computed value is sufficiently close to
   * zero, there is a concern that numerical issues involved in the half-plane
   * calculations might result in incorrect determinations. This value helps
   * define "sufficiently close".
   */
  private final double halfPlaneThreshold;

  /**
   * The negative threshold used to determine if a higher-precision
   * calculation is required for performing calculations related to the
   * half-plane calculation. When a computed value is sufficiently close to
   * zero, there is a concern that numerical issues involved in the half-plane
   * calculations might result in incorrect determinations. This value helps
   * define "sufficiently close".
   */
  private final double halfPlaneThresholdNeg;

  /**
   * The positive threshold used to determine if a higher-precision
   * calculation is required for performing calculations related to the
   * inCircle calculation. When a computed value is sufficiently close to
   * zero, there is a concern that numerical issues involved in the half-plane
   * calculations might result in incorrect determinations. This value helps
   * define "sufficiently close".
   */
  private final double inCircleThreshold;

  /**
   * The negative threshold used to determine if a higher-precision
   * calculation is required for performing calculations related to the
   * half-plane calculation. When a computed value is sufficiently close to
   * zero, there is a concern that numerical issues involved in the half-plane
   * calculations might result in incorrect determinations. This value helps
   * define "sufficiently close".
   */
  private final double inCircleThresholdNeg;

  /**
   * The tolerance factor for treating closely spaced or identical vertices as
   * a single point.
   */
  private final double vertexTolerance;
  /**
   * The square of the vertex tolerance factor.
   */
  private final double vertexTolerance2;

  /**
   * Thresholds computed based on the nominal point spacing for the input
   * vertices.
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
   * Keeps count of the number of vertices inserted into the TIN. This value
   * may be larger than the number of vertices actually stored in the TIN if
   * vertices are added redundantly.
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
   * Tracks the number of synthetic vertices added while restoring
   * Delaunay conformity after adding constraints
   */
  private int nSyntheticVertices;
  /**
   * The rule used for disambiguating z values in a vertex merger group.
   */
  private VertexMergerGroup.ResolutionRule vertexMergeRule
    = VertexMergerGroup.ResolutionRule.MeanValue;

  /**
   * An instance of a SLW set with thresholds established in the constructor.
   */
  private final StochasticLawsonsWalk walker;

  /**
   * Constructs an incremental TIN using numerical thresholds appropriate for
   * the default nominal point spacing of 1 unit.
   */
  public IncrementalTin() {

    thresholds = new Thresholds(1.0);
    geoOp = new GeometricOperations(thresholds);
    nominalPointSpacing = thresholds.getNominalPointSpacing();

    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
    halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    inCircleThreshold = thresholds.getInCircleThreshold();
    inCircleThresholdNeg = -thresholds.getInCircleThreshold();

    vertexTolerance = thresholds.getVertexTolerance();
    vertexTolerance2 = thresholds.getVertexTolerance2();

    walker = new StochasticLawsonsWalk(thresholds);

    edgePool = new EdgePool();
  }

  /**
   * Constructs an incremental TIN using numerical thresholds appropriate for
   * the specified nominal point spacing. This value is an estimated spacing
   * used for determining numerical thresholds for various proximity and
   * inclusion tests. For best results, it should be within one to two orders
   * of magnitude of the actual value for the samples. In practice, this value
   * is usually chosen to be close to the mean point spacing for a sample. But
   * for samples with varying density, a mean value from the set of smaller
   * point spacings may be used.
   * <p>
   * Lidar applications sometimes refer to the point-spacing concept as
   * "nominal pulse spacing", a term that reflects the origin of the data in a
   * laser-based measuring system.
   *
   * @param estimatedPointSpacing the estimated nominal distance between
   * points.
   */
  public IncrementalTin(final double estimatedPointSpacing) {
    this.nominalPointSpacing = estimatedPointSpacing;
    thresholds = new Thresholds(this.nominalPointSpacing);
    geoOp = new GeometricOperations(thresholds);

    halfPlaneThreshold = thresholds.getHalfPlaneThreshold();
    halfPlaneThresholdNeg = -thresholds.getHalfPlaneThreshold();
    inCircleThreshold = thresholds.getInCircleThreshold();
    inCircleThresholdNeg = -thresholds.getInCircleThreshold();

    vertexTolerance = thresholds.getVertexTolerance();
    vertexTolerance2 = thresholds.getVertexTolerance2();

    walker = new StochasticLawsonsWalk(nominalPointSpacing);

    edgePool = new EdgePool();
  }

  /**
   * Insert a vertex into the collection of vertices managed by the TIN. If
   * the TIN is not yet bootstrapped, the vertex will be retained in a simple
   * list until enough vertices are received in order to bootstrap the TIN.
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
   * Thus, there is a small performance advantage in supplying the vertices
   * using a list that can be accessed efficiently in a random order (see the
   * discussion of the Java API for the List and java.util.RandomAccess
   * interfaces). Once the initial triangle is established, the list will be
   * traversed sequentially to build the TIN and random access considerations
   * will no longer apply.
   *
   * @param list a valid list of vertices to be added to the TIN.
   * @param monitor an optional instance of a monitoring implementation; null
   * if not used
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
    if (list == null || list.isEmpty()) {
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
   * Create the initial three-vertex mesh by selecting vertices from the input
   * list. Logic is provided to attempt to identify a initial triangle with a
   * non-trivial area (on the theory that this stipulation produces a more
   * robust initial mesh). In the event of an unsuccessful bootstrap attempt,
   * future attempts will be conducted as the calling application provides
   * additional vertices.
   *
   * @param list a valid list of input vertices.
   * @return if successful, true; otherwise, false.
   */
  private boolean bootstrap(final List<Vertex> list) {
    Vertex[] v = (new BootstrapUtility(thresholds)).bootstrap(list, geoOp);
    if (v == null) {
      return false;
    }

    // Allocate edges for initial TIN
    QuadEdge e1 = edgePool.allocateEdge(v[0], v[1]);
    QuadEdge e2 = edgePool.allocateEdge(v[1], v[2]);
    QuadEdge e3 = edgePool.allocateEdge(v[2], v[0]);
    QuadEdge e4 = edgePool.allocateEdge(v[0], null);
    QuadEdge e5 = edgePool.allocateEdge(v[1], null);
    QuadEdge e6 = edgePool.allocateEdge(v[2], null);

    QuadEdge ie1 = e1.getDual();
    QuadEdge ie2 = e2.getDual();
    QuadEdge ie3 = e3.getDual();
    QuadEdge ie4 = e4.getDual();
    QuadEdge ie5 = e5.getDual();
    QuadEdge ie6 = e6.getDual();

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
   * Given an perimeter edge AB defined by vertices a and b, compute the
   * equivalent of the in-circle h factor indicating if the the vertex v is on
   * the inside or outside of the edge (and, so, the TIN). The perimeter edge
   * is oriented so that the interior is on the side of its dual. Thus if the
   * test vertex is in the local direction of the TIN interior, h will be
   * negative and if it is in the local direction of the TIN exterior, h will
   * be positive. For the case where the vertex lies on the ray of the
   * segment, e.g. h is zero, special logic is applied. If the point lies
   * within the segment, h is artificially set to a value of positive 1. In
   * the insertion in-circle logic below, a value of h>0 indicates that an
   * edge is non-delaunay and thus AB needs to be removed (flipped). If the
   * point lies outside the segment, h is artifically set to +1, which
   * triggers the insertion logic to leave the edge AB in place.
   *
   * @param a a valid vertex
   * @param b a valid vertex
   * @param v a valid vertex to be tested for a psuedo in-circle condition
   * with vertices a and b.
   * @return A negative value if the vertex is in the direction of the TIN
   * interior, a positive value if it is in the direction of the exterior or a
   * zero if it lies directly on the edge; zero is not returned.
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
   * Performs processing for the public add() methods by adding the vertex to
   * a fully bootstrapped mesh. The vertex will be either inserted into the
   * mesh or the mesh will be extended to include the vertex.
   *
   * @param v a valid vertex.
   * @return true if the vertex was added successfully; otherwise false
   * (usually in response to redundant vertex specifications).
   */
  private boolean addWithInsertOrAppend(final Vertex v) {
    final double x = v.x;
    final double y = v.y;

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
    searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x, y);

    // the following is a debugging aid when trying to deal with vertex
    // insertion versus TIN extension.
    // boolean isVertexInside = (searchEdge.getForward().getB() != null);
    QuadEdge matchEdge
      = checkTriangleVerticesForMatch(searchEdge, x, y, vertexTolerance2);
    if (matchEdge != null) {
      mergeVertexOrIgnore(matchEdge, v);
      return false;
    }

    // The build buffer provides temporary tracking of edges that are
    // removed and replaced while building the TIN.  Because the
    // delete method of the EdgePool has to do a lot of bookkeeping,
    // we can gain speed by using the buffer.   The buffer is only large
    // enough to hold one edge. Were it larger, there would be times
    // when it would hold more than one edge. Tests reveal that the overhead
    // of maintaining an array rather than a single reference overwhelms
    // the potential saving. However, the times for the two approaches are quite
    // close and it is hard to remove the effect of measurement error.
    Vertex anchor = searchEdge.getA();

    QuadEdge buffer = null;
    QuadEdge c, n0, n1, n2;
    QuadEdge pStart = edgePool.allocateEdge(v, anchor);
    QuadEdge p = pStart;
    p.setForward(searchEdge);
    n1 = searchEdge.getForward();
    n2 = n1.getForward();
    n2.setForward(p.getDual());

    c = searchEdge;
    while (true) {
      n0 = c.getDual();
      n1 = n0.getForward();

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
        n2 = n1.getForward();
        n2.setForward(c.getForward());
        p.setForward(n1);
        c.clear();  // optional, done as a diagnostic
        // we need to get the base reference in order to ensure
        // that any ghost edges we create will start with a
        // non-null vertex and end with a null.
        c = c.getBaseReference();
        if (buffer == null) {
          c.clear();
          buffer = c;
        } else {
          edgePool.deallocateEdge(c);
        }

        c = n1;
        nReplacements++;
      } else {
        // check for completion
        if (c.getB() == anchor) {
          pStart.getDual().setForward(p);
          searchEdge = pStart;
          // TO DO: is buffer ever not null?
          //        i don't think so because it could only
          //        happen in a case where an insertion decreased
          //        the number of edge. so the following code
          //        is probably unnecessary
          if (buffer != null) {
            edgePool.deallocateEdge(buffer);
          }

          nEdgesReplacedDuringBuild += nReplacements;
          if (nReplacements > maxEdgesReplacedDuringBuild) {
            maxEdgesReplacedDuringBuild = nReplacements;
          }

          break;
        }

        n1 = c.getForward();
        QuadEdge e;
        if (buffer == null) {
          e = edgePool.allocateEdge(v, c.getB());
        } else {
          buffer.setVertices(v, c.getB());
          e = buffer;
          buffer = null;
        }
        e.setForward(n1);
        e.getDual().setForward(p);
        c.setForward(e.getDual());
        p = e;
        c = n1;
      }
    }
    return true;
  }

  /**
   * Tests the vertices of the triangle that includes the reference edge to
   * see if any of them are an exact match for the specified coordinates.
   * Typically, this method is employed after a search has obtained a
   * neighboring edge for the coordinates. If one of the vertices is an exact
   * match, within tolerance, for the specified coordinates, this method will
   * return the edge that starts with the vertex.
   *
   * @param x the x coordinate of interest
   * @param y the y coordinate of interest
   * @param baseEdge an edge from the triangle containing (x,y)
   * @param distanceTolerance2 the square of a tolerance specification for
   * accepting a vertex as a match for the coordinates
   * @return true if a match is found; otherwise, false
   */
  @Override
  public QuadEdge checkTriangleVerticesForMatch(
    final QuadEdge baseEdge,
    final double x,
    final double y,
    final double distanceTolerance2) {
    QuadEdge sEdge = baseEdge;
    if (sEdge.getA().getDistanceSq(x, y) < distanceTolerance2) {
      return sEdge;
    } else if (sEdge.getB().getDistanceSq(x, y) < distanceTolerance2) {
      return sEdge.getForward();
    } else {
      Vertex v2 = sEdge.getForward().getB();
      if (v2 != null && v2.getDistanceSq(x, y) < distanceTolerance2) {
        return sEdge.getReverse();
      }
    }
    return null;
  }

  /**
   * Tests the vertices of the triangle that includes the reference edge to
   * see if any of them are an exact (instance) match for the specified
   * vertex. If so, returns the QuadEdge that starts with the vertex. If the
   * vertex is a member of a VertexMergerGroup, the edge that starts with the
   * containing group is returned.
   *
   * @param v the vertex to test for match
   * @param sEdge an edge from the triangle containing the vertex, may be
   * adjusted by the search.
   * @return true if a match is found; otherwise, false
   */
  private QuadEdge checkTriangleVerticesForMatchingReference(
    final QuadEdge sEdge, final Vertex v) {
    Vertex a = sEdge.getA();
    Vertex b = sEdge.getB();
    Vertex c = sEdge.getForward().getB();

    // first try a direct comparison
    if (a == v) {
      return sEdge;
    } else if (b == v) {
      return sEdge.getForward();
    } else if (c == v) {
      return sEdge.getReverse();
    }

    if (a instanceof VertexMergerGroup && ((VertexMergerGroup) a).contains(v)) {
      return sEdge;
    } else if (b instanceof VertexMergerGroup && ((VertexMergerGroup) b).contains(v)) {
      return sEdge.getForward();
    } else if (c instanceof VertexMergerGroup && ((VertexMergerGroup) c).contains(v)) {
      return sEdge.getReverse();
    }

    return null;
  }

  /**
   * Allocates a number of vertices roughly sufficient to represent a TIN
   * containing the specified number of vertices. This method serves as a
   * diagnostic tool, allowing a test-application to separate the portion of
   * processing time consumed by Java object construction from that spent
   * processing the vertex data.
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
    return new Rectangle2D.Double(
      boundsMinX,
      boundsMinY,
      boundsMaxX - boundsMinX,
      boundsMaxY - boundsMinY);
  }

  /**
   * Given a vertex known to have coordinates very close or identical to a
   * previously inserted vertex, perform a merge. The first time a merge is
   * performed, the previously existing vertex is replaced with a
   * VertexMergerGroup object and the new vertex is added to the group.
   * <p>
   * This method also checks to see if the newly inserted vertex is the same
   * object as one previously inserted. In such a case, it is ignored.
   * Although this situation could happen due to a poorly implemented
   * application, the most common case is when the insertion was conducted
   * using a list of vertices rather than individual insertions. The bootstrap
   * logic creates an initial mesh from three randomly chosen vertices. When
   * the list is processed, these vertices will eventually be passed to the
   * processVertexInsertion routine. They will be identified as merge
   * candidates and ignored. For large input lists, this strategy is more
   * efficient than attempting to modify the input list.
   *
   * @param edge an edge selected so that the matching, previously inserted
   * vertex is assigned to vertex A.
   * @param v the newly inserted, matching vertex.
   */
  private void mergeVertexOrIgnore(final QuadEdge edge, final Vertex v) {
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
      QuadEdge start = edge;
      QuadEdge e = edge;

      ArrayList<QuadEdge> eList = new ArrayList<>();
      do {
        eList.add(e);
        e = e.getReverse();
        e = e.getDual();
      } while (e != start);

      for (QuadEdge qe : eList) {
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
  public void printEdges(final PrintStream ps) {
    List<IQuadEdge> list = edgePool.getEdges();
    for (IQuadEdge e : list) {
      ps.println(e.toString());
      ps.println(e.getDual().toString());
    }

  }

  /**
   * Number of bits in an integer.
   */
  private static final int INT_BITS = 32;  //NOPMD

  /**
   * Used to perform a modulus 32 operation on an integer through a bitwise
   * AND.
   */
  private static final int MOD_BY_32 = 0x1f; //NOPMD

  /**
   * Number of shifts to divide an integer by 32.
   */
  private static final int DIV_BY_32 = 5;  //NOPMD

  /**
   * Number of sides for an edge (2 of course).
   */
  private static final int N_SIDES = 2;  //NOPMD

  /**
   * Used to extract the low-order bit via a bitwise AND.
   */
  private static final int BIT1 = 0x01;   //NOPMD

  /**
   * Gets the edge mark bit.
   *
   * @param map an array at least as large as the largest edge index divided
   * by 32
   * @param edge a valid edge
   * @return if the edge is marked, a non-zero value; otherwise, a zero.
   */
  private int getMarkBit(final int[] map, final IQuadEdge edge) {
    int index = (edge.getIndex() * N_SIDES) | edge.getSide();
    return (map[index >> DIV_BY_32] >> (index & MOD_BY_32)) & BIT1;
  }

  /**
   * Set the mark bit for an edge to 1.
   *
   * @param map an array at least as large as the largest edge index divided
   * by 32
   * @param edge a valid edge
   */
  private void setMarkBit(final int[] map, final IQuadEdge edge) {
    int index = (edge.getIndex() * N_SIDES) | edge.getSide();
    map[index >> DIV_BY_32] |= (BIT1 << (index & MOD_BY_32));
  }

  /**
   * Process one side of an edge, develop a triangle if feasible.
   *
   * @param trigList a list to store triangles
   * @param map a bitmap for tracking which edges have been added to
   * triangles
   * @param e the edge to inspect
   */
  void countTriangleEdge(TriangleCount tCount, int[] map, IQuadEdge e) {
    if (getMarkBit(map, e) == 0) {
      setMarkBit(map, e);
      IQuadEdge f = e.getForward();
      // ghost triangle, not tabulated
      if (f.getB() != null) {
        IQuadEdge r = e.getReverse();
        // check to see that both neighbors are not marked.
        if ((getMarkBit(map, f) | getMarkBit(map, r)) == 0) {
          setMarkBit(map, f);
          setMarkBit(map, r);
          tCount.tabulateTriangle(e.getA(), f.getA(), r.getA());
        }
      }
    }
  }

  /**
   * Performs a survey of the TIN to gather statistics about the triangle
   * formed during its construction.
   *
   * @return A valid instance of the TriangleCount class.
   */
  @Override
  public TriangleCount countTriangles() {
    if (!isBootstrapped) {
      return new TriangleCount();
    }

    int maxIndex = edgePool.getMaximumAllocationIndex();
    int maxMapIndex = maxIndex * N_SIDES + 1;
    int mapSize = (maxMapIndex + INT_BITS - 1) / INT_BITS;
    int[] map = new int[mapSize];

    TriangleCount tCount = new TriangleCount();
    Iterator<QuadEdge> iEdge = edgePool.iterator();
    while (iEdge.hasNext()) {
      QuadEdge e = iEdge.next();
      if (e.getA() == null || e.getB() == null) {
        setMarkBit(map, e);
        setMarkBit(map, e.getDual());
        continue;
      }
      this.countTriangleEdge(tCount, map, e);
      this.countTriangleEdge(tCount, map, e.getDual());
    }
    return tCount;
  }

  /**
   * Gets a list of edges currently defining the perimeter of the TIN. The
   * list may be empty if the TIN is not initialized (bootstrapped).
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges return by
   * this routine are the same objects as those currently being used in the
   * instance. Any modification of the edge objects will damage the TIN.
   * Therefore, applications must not modify the edges returned by this
   * method.
   *
   * @return a valid, potentially empty list.
   */
  @Override
  public List<IQuadEdge> getPerimeter() {
    List<IQuadEdge> pList = new ArrayList<>();
    if (!isBootstrapped) {
      return pList;
    }

    QuadEdge g = edgePool.getStartingGhostEdge();
    QuadEdge s0 = g.getReverse();
    QuadEdge s = s0;
    do {
      pList.add(s.getDual());
      s = s.getForward();
      s = s.getForward();
      s = s.getDual();
      s = s.getReverse();
    } while (s != s0);
    return pList;
  }

  /**
   * Print statistics and diagnostic information collected during the TIN
   * construction process. This information will be removed and reset by a
   * call to the clear() method.
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
    double sumLength = 0;
    Iterator<QuadEdge> iEdge = edgePool.iterator();
    while (iEdge.hasNext()) {
      QuadEdge e = iEdge.next();
      if (e.getB() == null) {
        nGhost++;
      } else {
        nOrdinary++;
        sumLength += e.getLength();
      }
    }
    double avgPointSpacing = 0;
    if (nOrdinary > 0) {
      avgPointSpacing = sumLength / nOrdinary;
    }
    ps.format("Descriptive data\n");
    ps.format("Number Vertices Inserted:     %8d\n", nVerticesInserted);
    ps.format("Coincident Vertex Spacing:    %8f\n", vertexTolerance);
    ps.format("   Sets:                      %8d\n", coincidenceList.size());
    ps.format("   Total Count:               %8d\n", nCoincident);
    ps.format("Number Edges On Perimeter:    %8d\n", perimeter.size());
    ps.format("Number Ordinary Edges:        %8d\n", nOrdinary);
    ps.format("Number Ghost Edges:           %8d\n", nGhost);
    ps.format("Number Edge Replacements:     %8d    (avg: %3.1f)\n",
      nEdgesReplacedDuringBuild,
      (double) nEdgesReplacedDuringBuild / (double) (nVerticesInserted - nCoincident));
    ps.format("Max Edge Replaced by add op:  %8d\n", maxEdgesReplacedDuringBuild);
    ps.format("Average Point Spacing:        %11.2f\n", avgPointSpacing);
    ps.format("Application's Nominal Spacing:%11.2f\n", nominalPointSpacing);
    ps.format("Number Triangles:             %8d\n", trigCount.getCount());
    ps.format("Average area of triangles:    %12.3f\n", trigCount.getAreaMean());
    ps.format("Samp. std dev for area:       %12.3f\n", trigCount.getAreaStandardDeviation());
    if (trigCount.getAreaMin() < 1) {
      ps.format("Minimum area:                        %f\n", trigCount.getAreaMin());
    } else {
      ps.format("Minimum area:                 %12.3f\n", trigCount.getAreaMin());
    }
    ps.format("Maximum area:                 %12.3f\n", trigCount.getAreaMax());
    ps.format("Total area:                   %10.1f\n", trigCount.getAreaSum());

    ps.format("\nConstruction statistics\n");
    walker.printDiagnostics(ps);

    ps.format("InCircle calculations:        %8d\n", nInCircle);
    ps.format("   extended:                  %8d\n", nInCircleExtendedPrecision);
    ps.format("   conflicts:                 %8d\n", nInCircleExtendedPrecisionConflicts);

    ps.format("\nEdge resource diagnostics\n");
    edgePool.printDiagnostics(ps);
  }

  /**
   * Gets a list of edges currently allocated by an instance. The list may be
   * empty if the TIN is not initialized (bootstrapped).
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges return by
   * this routine are the same objects as those currently being used in the
   * instance. Any modification of the edge objects will damage the TIN.
   * Therefore, applications must not modify the edges returned by this
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

  /**
   * Gets the nominal point spacing used to determine numerical thresholds for
   * various proximity and inclusion tests. For best results, it should be
   * within one to two orders of magnitude of the actual value for the
   * samples. In practice, this value is usually chosen to be close to the
   * mean point spacing for a sample. But for samples with varying density, a
   * mean value from the set of smaller point spacings may be used.
   * <p>
   * Lidar applications sometimes refer to the point-spacing concept as
   * "nominal pulse spacing", a term that reflects the origin of the data in a
   * laser-based measuring system.
   *
   * @return a positive floating-point value greater than zero.
   */
  @Override
  public double getNominalPointSpacing() {
    return nominalPointSpacing;
  }

  @Override
  /**
   * Nullifies all internal data and references, preparing the instance for
   * garbage collection. Because of the complex relationships between objects
   * in a TIN, Java garbage collection may require an above average number of
   * passes to clean up memory when an instance of this class goes
   * out-of-scope. The dispose() method can be used to expedite garbage
   * collection. Do not confuse the dispose() method with the clear() method.
   * The clear() method prepares a TIN instance for reuse. The dispose()
   * method prepares a TIN instance for garbage collection. Once the dispose()
   * method is called on a TIN, it cannot be reused.
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
   * resources for re-use. When processing multiple sets of input data the
   * clear() method has an advantage in that it can be used to reduce the
   * overhead related to multiple edge object implementation.
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
    coincidenceList.clear();
    walker.reset();
    constraintList.clear();
    nSyntheticVertices = 0;
  }

  /**
   * Indicates whether the instance contains sufficient information to
   * represent a TIN. Bootstrapping requires the input of at least three
   * distinct, non-collinear vertices. If the TIN is not bootstrapped methods
   * that access its content may return empty or null results.
   *
   * @return true if the TIN is successfully initialized; otherwise, false.
   */
  @Override
  public boolean isBootstrapped() {
    return isBootstrapped;
  }

  /**
   * Set the search edge after a removal is completed. The logic for insertion
   * requires that the search edge cannot be a ghost edge, but the logic for
   * removal sometimes produces this result. Ensure that the search is set
   * with an interior-side edge.
   *
   * @param e the search edge identified by the removal process.
   */
  private void setSearchEdgeAfterRemoval(final QuadEdge e) {
    QuadEdge b = e.getBaseReference();
    if (b.getB() == null) {
      b = b.getReverse();
    }
    searchEdge = b;
  }

  /**
   * Removes the specified vertex from the TIN. If the vertex is part of a
   * merged-group, it is removed from the group by the structure of the TIN is
   * unchanged.
   * <p>
   * At this time, this method does not handle the case where all vertices are
   * removed from the TIN.
   *
   * @param vRemove the vertex to be removed
   * @return true if the vertex was found in the TIN and removed.
   */
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
    searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x, y);

    QuadEdge matchEdge
      = checkTriangleVerticesForMatchingReference(searchEdge, vRemove);

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
    QuadEdge n0 = matchEdge;
    searchEdge = null;

    // initialize edges needed for removal
    QuadEdge n1, n2, n3;

    // step 1: Cavitation
    //         remove vertex and create a polygonal cavity
    //         eliminating all connecting edges
    n1 = n0.getForward();
    while (true) {
      n2 = n1.getForward();
      n3 = n2.getDual().getForward();
      //n2 is to be deleted.  set the forward edge
      //of n2 to point to n3.
      n1.setForward(n3);
      n1 = n3;
      if (n2 == n0.getDual()) {
        //dispose of edge n2 and break
        edgePool.deallocateEdge(n2);
        break;
      } else {
        edgePool.deallocateEdge(n2);
      }
    }

    n0 = n1;

    // Step 2 -- Ear Creation
    //           Create a set of Devillers Ears around
    //           the polygonal cavity.
    int nEar = 0;
    n1 = n0.getForward();
    QuadEdge pStart = n0;
    DevillersEar firstEar = new DevillersEar(nEar, null, n1, n0);
    DevillersEar priorEar = firstEar;
    DevillersEar nextEar;
    firstEar.computeScore(geoOp, vRemove);

    nEar = 1;
    do {
      n0 = n1;
      n1 = n1.getForward();
      DevillersEar ear = new DevillersEar(nEar, priorEar, n1, n0); // NOPMD
      ear.computeScore(geoOp, vRemove);
      priorEar = ear;
      nEar++;
    } while (n1 != pStart);
    priorEar.next = firstEar;
    firstEar.prior = priorEar;

    if (nEar == 3) {
      // the removal of the vertex resulted in a single triangle
      // which is already Delaunay.  The cavitation process should
      // have reset the links.  So the removal operation is done.
      setSearchEdgeAfterRemoval(firstEar.c);
      return true;
    }

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
      DevillersEar earMin = null;
      double minScore = Double.POSITIVE_INFINITY;
      DevillersEar ear = firstEar;
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
      QuadEdge e = edgePool.allocateEdge(earMin.v2, earMin.v0);
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
   * Gets a walker that is compatible with the point-spacing specifications
   * for the TIN. Intended to support interpolator instances and related
   * applications.
   *
   * @return a valid walker.
   */
  StochasticLawsonsWalk getCompatibleWalker() {
    StochasticLawsonsWalk cw
      = new StochasticLawsonsWalk(nominalPointSpacing);
    return cw;
  }

  /**
   * Obtains an arbitrary edge to serve as the start of a search or traversal
   * operation.
   *
   * @return An ordinary (non-ghost) edge.
   */
  public QuadEdge getStartingEdge() {
    // because this method may be accessed simultaneously by multiple threads,
    // it must not modify the internal state of the instance.
    if (searchEdge == null) {
      return edgePool.getStartingEdge();
    } else {
      return searchEdge;
    }

  }

  /**
   * Gets a new instance of a neighbor edge locator implementation. Instances
   * observe the contract of the IProcessUsingTin interface in that they
   * access the TIN on a readonly basis and may be used in parallel threads
   * provided that the TIN is not modified.
   *
   * @return an edge locator tied to this TIN.
   */
  @Override
  public INeighborEdgeLocator getNeighborEdgeLocator() {
    return new NeighborEdgeLocator(this);
  }

  /**
   * Gets a new instance of a neighborhood points collector. Instances observe
   * the contract of the IProcessUsingTin interface in that they access the
   * TIN on a readonly basis and may be used in parallel threads provided that
   * the TIN is not modified.
   *
   * @return an points collector tied to this TIN.
   */
  @Override
  public INeighborhoodPointsCollector getNeighborhoodPointsCollector() {
    return new NeighborhoodPointsCollector(this, thresholds);
  }

  @Override
  public IIntegrityCheck getIntegrityCheck() {
    return new IntegrityCheck(this);
  }

  /**
   * Specifies a rule for interpreting the Z value of a group of vertices that
   * were merged due to being coincident, or nearly coincident.
   *
   * @param resolutionRule The rule to be used for interpreting merged
   * vertices.
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
   * is not necessarily equivalent to the set of objects that were input
   * because some vertices may have been incorporated into one or more
   * vertex-merger groups. Note that the list of vertices is not sorted and
   * will usually not be returned in the same order as the original input set.
   *
   * @return a valid list of vertices, potentially empty if the TIN has not
   * been initialized.
   */
  @Override
  public List<Vertex> getVertices() {
    // in the logic below, we use a bitmap to keep track
    // of which edges were already inspected.  We cannot use a
    // bitmap for tracking vertices, because the vertex indices are out
    // of our control.
    int maxIndex = edgePool.getMaximumAllocationIndex();
    int maxMapIndex = maxIndex * N_SIDES + 1;
    int mapSize = (maxMapIndex + INT_BITS - 1) / INT_BITS;
    int[] map = new int[mapSize];

    ArrayList<Vertex> vList = new ArrayList<>(this.nVerticesInserted);
    Iterator<QuadEdge> iEdge = edgePool.iterator();
    while (iEdge.hasNext()) {
      QuadEdge e = iEdge.next();
      Vertex v = e.getA();
      if (v != null && getMarkBit(map, e) == 0) {
        setMarkBit(map, e);
        vList.add(v);
        QuadEdge c = e;
        do {
          c = c.getForward().getForward().getDual();
          setMarkBit(map, c);
        } while (c != e);
      }
      QuadEdge d = e.getDual();
      v = d.getA();
      if (v != null && getMarkBit(map, d) == 0) {
        setMarkBit(map, d);
        vList.add(v);
        QuadEdge c = d;
        do {
          c = c.getForward().getForward().getDual();
          setMarkBit(map, c);
        } while (c != d);
      }

    }

    return vList;
  }

  // Code related to the addition of constraints ------------------------
  @Override
  public boolean isPointInsideTin(double x, double y) {
    if (this.isBootstrapped) {
      if (searchEdge == null) {
        searchEdge = edgePool.getStartingEdge();
      }
      searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x, y);

      Vertex v2 = searchEdge.getForward().getB();
      return v2 != null;
    }
    return false;
  }

  @Override
  public void addConstraints(
    List<IConstraint> constraints,
    boolean restoreConformity) {
    if (isLocked) {
      if (isDisposed) {
        throw new IllegalStateException(
          "Unable to add constraints after a call to dispose()");
      } else if (!constraintList.isEmpty()) {  //NOPMD
        throw new IllegalStateException(
          "Constrains have already been added to TIN and"
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
    if (constraints.size() > QuadEdge.CONSTRAINT_INDEX_MAX) {
      throw new IllegalArgumentException(
        "The maximum number of constraints is "
        + QuadEdge.CONSTRAINT_INDEX_MAX);
    }

    // Step 1 -- add all the vertices from the constraints to the TIN.
    for (IConstraint c : constraints) {
      c.complete();
      List<Vertex> rawList = c.getVertices();
      List<Vertex> vList = new ArrayList<>(rawList); // NOPMD -- safe copy

      double xPrior = Double.POSITIVE_INFINITY;
      double yPrior = Double.POSITIVE_INFINITY;
      for (int i = 0; i < vList.size(); i++) {
        Vertex v = vList.get(i);
        double x = v.getX();
        double y = v.getY();
        if (x == xPrior && y == yPrior) {
          // this should have been filtered out by logic that
          // ensured that the constraint was well-formed. But we
          // perform a test just in case
          vList.remove(i);
        }
        xPrior = x;
        yPrior = y;
      }

      if (vList.size() < 2) {
        throw new IllegalArgumentException(
          "Constaint contains fewer than 2 points");
      }
      constraintList.add(c);
      this.add(vList, null);

    }

    // Step 2 -- Construct new edges for constraint and mark any existing
    //           edges with the constraint index.
    isLocked = true;
    boolean foundDataAreaDefinition = false;
    int k = 0;
    for (IConstraint c : constraintList) {
      if (c.definesDataArea()) {
        foundDataAreaDefinition = true;
      }
      c.setConstraintIndex(k);
      processConstraint(c);
      k++;
    }

    // TO DO:  Use an iterator instead
    //         eliminate down-casting
    if (restoreConformity) {
      List<IQuadEdge> eList = edgePool.getEdges();
      for (IQuadEdge e : eList) {
        if (e.isConstrained()) {
          restoreConformity((QuadEdge) e);
        }
      }
    }

    if (foundDataAreaDefinition) {
      fillConstraintDataAreas();
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

  private void setConstrained(QuadEdge edge, IConstraint constraint) {
    edge.setConstrained(constraint.getConstraintIndex());
    if (constraint.definesDataArea()) {
      edge.setConstrainedAreaMemberFlag();
    }
  }

  private void processConstraint(IConstraint constraint) {
    List<Vertex> cvList = constraint.getVertices();
    int nSegments = cvList.size() - 1;

    double vTolerence = thresholds.getVertexTolerance();
    Vertex v0 = cvList.get(0);
    double x0 = v0.getX();
    double y0 = v0.getY();

    if (searchEdge == null) {
      searchEdge = edgePool.getStartingEdge();
    }
    searchEdge = walker.findAnEdgeFromEnclosingTriangle(searchEdge, x0, y0);
    QuadEdge e0 = null;
    if (isMatchingVertex(v0, searchEdge.getA())) {
      e0 = searchEdge;
    } else if (isMatchingVertex(v0, searchEdge.getB())) {
      e0 = searchEdge.getDual();
    } else { //if (isMatchingVertex(v0, searchEdge.getReverse().getA())) {
      e0 = searchEdge.getReverse();
    }
    Vertex a = e0.getA();
    if (a != v0 && a instanceof VertexMergerGroup) {
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
      QuadEdge e = e0;
      {
        boolean priorNull = false;
        QuadEdge reEntry = null;
        do {
          b = e.getB();
          if (b == null) {
            // ghost vertex
            priorNull = true;
          } else {
            if (b == v1) {
              setConstrained(e, constraint);
              e0 = e.getDual(); // set up e0 for next iteration of iSegment
              continue segmentLoop;
            } else if (b instanceof VertexMergerGroup) {
              VertexMergerGroup g = (VertexMergerGroup) b;
              if (g.contains(v1)) {
                cvList.set(iSegment + 1, g);
                setConstrained(e, constraint);
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
      QuadEdge h = null;
      QuadEdge right0 = null;
      QuadEdge left0 = null;
      QuadEdge right1 = null;
      QuadEdge left1 = null;

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
        setConstrained(e0, constraint);
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
        QuadEdge n = e.getForward(); //the edge opposite v0

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
            setConstrained(e, constraint);
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
      QuadEdge n = edgePool.allocateEdge(v0, c);
      setConstrained(n, constraint);
      QuadEdge d = n.getDual();
      n.setForward(left1);
      n.setReverse(left0);
      d.setForward(right0);
      d.setReverse(right1);
      e0 = d;

      fillCavity(n);
      fillCavity(d);
    }

  }

  private void removeEdge(QuadEdge e) {
    QuadEdge d = e.getDual();
    QuadEdge dr = d.getReverse();
    QuadEdge df = d.getForward();
    QuadEdge ef = e.getForward();
    QuadEdge er = e.getReverse();

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
  private void fillScore(DevillersEar ear) {
    ear.score = geoOp.area(ear.v0, ear.v1, ear.v2);

    if (ear.score > 0) {
      double x0 = ear.v0.getX();
      double y0 = ear.v0.getY();
      double x1 = ear.v1.getX();
      double y1 = ear.v1.getY();
      double x2 = ear.v2.getX();
      double y2 = ear.v2.getY();

      DevillersEar e = ear.next;
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
  private void fillCavity(QuadEdge cavityEdge) {
    // initialize edges needed for removal

    QuadEdge n0, n1;

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
    QuadEdge pStart = n0;
    DevillersEar firstEar = new DevillersEar(nEar, null, n1, n0);
    DevillersEar priorEar = firstEar;
    DevillersEar nextEar;

    nEar = 1;
    do {
      n0 = n1;
      n1 = n1.getForward();
      DevillersEar ear = new DevillersEar(nEar, priorEar, n1, n0); // NOPMD
      priorEar = ear;
      nEar++;
    } while (n1 != pStart);
    priorEar.next = firstEar;
    firstEar.prior = priorEar;

    if (nEar == 3) {
      return;
    }

    DevillersEar eC = firstEar.next;
    fillScore(firstEar);
    while (eC != firstEar) {
      fillScore(eC);
      eC = eC.next;
    }

    ArrayList<QuadEdge> list = new ArrayList<>();
    while (true) {
      DevillersEar earMin = null;
      double minScore = Double.POSITIVE_INFINITY;
      DevillersEar ear = firstEar;
      do {
        if (ear.score < minScore && ear.score > 0) {
          minScore = ear.score;
          earMin = ear;
        }
        ear = ear.next;
      } while (ear != firstEar);

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
      QuadEdge e = edgePool.allocateEdge(earMin.v2, earMin.v0);
      QuadEdge d = e.getDual();
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
    //  Loop through the nearly created edges and the non-constrained
    //  perimeter edges to flip and edges that violate the Delaunay criterion.]
    //  If the addition of the constraint did not involve the creation
    //  of synthetic points to restore Delaunay conformality, then
    //  the perimeter edges are still Delaunay and will not need to
    //  be flipped.  But if synthetic points were added, it is possible
    //  that they will fall within the circumcircle of a triangle adjacent
    //  to the cavity.  In which case, the flip operation will propagate
    //  to edges on the edge of the cavity and potentially beyond,
    for (QuadEdge n : list) {
      recursiveRestoreDelaunay(n);
    }
  }

  /**
   * Tests the edge to see if it is non-Delaunay and, if so,
   * flips it and recursively tests the neighboring edges.
   * It is assumed that n is an interior-facing edge of the
   * TIN. This method does not test constrained edges and
   * perimeter edges.
   *
   * @param n a valid, interior facing edge
   * @return true if an edge was flipped.
   */
  private boolean recursiveRestoreDelaunay(QuadEdge n) {
    if (n.isConstrained()) {
      return false;
    }
    QuadEdge nf = n.getForward();
    Vertex a = n.getA();
    Vertex b = n.getB();
    Vertex c = nf.getB();
    if (c == null) {
      return false;
    }
    QuadEdge d = n.getDual();
    QuadEdge df = d.getForward();
    Vertex t = df.getB();
    if (t == null) {
      return false;
    }

    double h = geoOp.inCircle(a, b, c, t);
    if (h > 0) {
      // flip n
      QuadEdge nr = n.getReverse();
      QuadEdge dr = d.getReverse();
      n.setVertices(t, c);
      n.setForward(nr);
      n.setReverse(df);
      d.setForward(dr);
      d.setReverse(nf);
      dr.setForward(nf);
      nr.setForward(df);
      recursiveRestoreDelaunay(nf);
      recursiveRestoreDelaunay(nr);
      recursiveRestoreDelaunay(df);
      recursiveRestoreDelaunay(dr);
      return true;
    }
    return false;
  }

  private void restoreConformity(QuadEdge ab) {

    QuadEdge ba = ab.getDual();
    QuadEdge bc = ab.getForward();
    QuadEdge ad = ba.getForward();
    Vertex a = ab.getA();
    Vertex b = ab.getB();
    Vertex c = bc.getB();
    Vertex d = ad.getB();
    if (a == null || b == null || c == null || d == null) {
      return;
    }
    double h = geoOp.inCircle(a, b, c, d);
    if (h <= 0) {
      return;
    }

    QuadEdge ca = ab.getReverse();
    QuadEdge db = ba.getReverse();

    if (ab.isConstrained()) {
      // subdivide the constraint edge to restore conformity
      double mx = (a.getX() + b.getX()) / 2.0;
      double my = (a.getY() + b.getY()) / 2.0;
      double mz = (a.getZ() + b.getZ()) / 2.0;
      Vertex m = new Vertex(mx, my, mz, nSyntheticVertices++);
      m.setStatus(Vertex.BIT_SYNTHETIC | Vertex.BIT_CONSTRAINT);

      // reuse edge ab, change name just to avoid confusion
      QuadEdge mb = ab;
      QuadEdge bm = ba;
      mb.setVertices(m, b);

      // create new edges
      QuadEdge am = edgePool.allocateEdge(a, m);
      QuadEdge cm = edgePool.allocateEdge(c, m);
      QuadEdge dm = edgePool.allocateEdge(d, m);
      QuadEdge ma = am.getDual();
      QuadEdge mc = cm.getDual();
      QuadEdge md = dm.getDual();

      am.setConstrained(mb.getConstraintIndex());

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
      restoreConformity(am);
      restoreConformity(mb);
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

    restoreConformity(bc.getDual());
    restoreConformity(ca.getDual());
    restoreConformity(ad.getDual());
    restoreConformity(db.getDual());
  }

  private void fillConstraintDataAreas() {
    for (QuadEdge e : this.edgePool) {
      if (e.isConstrainedAreaEdge()) {
        if (e.isConstraintAreaOnThisSide()) {
          fillConstraintDataAreaRecursion(e);
        } else {
          fillConstraintDataAreaRecursion(e.getDual());
        }
      }
    }
  }

  private void fillConstraintDataAreaRecursion(QuadEdge e) {
    int index = e.getConstraintIndex();
    QuadEdge f = e.getForward();
    if (!f.isConstrainedAreaMember()) {
      f.setConstrainedAreaMemberFlag();
      f.setConstraintIndex(index);
      fillConstraintDataAreaRecursion(f.getDual());
    }
    QuadEdge r = e.getReverse();
    if (!r.isConstrainedAreaMember()) {
      r.setConstrainedAreaMemberFlag();
      r.setConstraintIndex(index);
      fillConstraintDataAreaRecursion(r.getDual());
    }
  }

  @Override
  public List<IConstraint> getConstraints() {
    List<IConstraint> result = new ArrayList<>();
    result.addAll(constraintList);
    return result;
  }

  @Override
  public int getSyntheticVertexCount() {
    return nSyntheticVertices;
  }

}
