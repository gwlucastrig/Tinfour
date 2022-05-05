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
 * 09/2015  G. Lucas     Created interface from original IncrementalTin class.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

/**
 * Defines the primary methods used by an incremental TIN
 * implementation.
 * <h1>Implementations</h1>
 * Currently, the Tinfour software library includes two implementations
 * of this interface: IncrementalTin in the package org.tinfour.standard,
 * and SemiVirtualIncrementalTin in the package org.tinfour.semivirtual.
 * The two classes produce nearly identical output data. The standard
 * IncrementalTin implementation is faster and simpler than its counterpart,
 * but the semi-virtual implementation reduces memory use by a factor
 * of two.  Under Java, IncrementalTin requires approximately 244 bytes per
 * vertex while the semi-virtual form requires about 120.
 * <p>
 * The main difference between the two implementations is that standard
 * incremental-TIN implementation represents each edge as an explicitly
 * constructed pair of objects (using the QuadEdge class) while the semi-virtual
 * implementation stores the raw data for vertices in memory and constructs
 * edge objects on-the-fly (using the SemiVirtualEdge class).
 * The semi-virtual approach has some processing overhead in that each
 * time an edge is required, it must be constructed and then allowed to
 * go out-of-scope. This approach results in about a 30 percent reduction
 * in the speed at which vertices can be added to the TIN. But by relying on
 * short-persistence edge objects, the semi-virtual class reduces the
 * number of objects kept in memory and the overall memory use.
 * The standard implementation classes uses about 7.005 objects per vertex
 * (including vertices, edges, and collections for management), while the
 * semi-virtual implementation uses about persistent 1.012 objects per vertex.
 * <h2>Usage Notes</h2>
 * <h3>Purpose of this Interface</h3>
 * The intended purpose of this interface is to allow application
 * code to use the two classes interchangeably. Applications may select
 * which implementation is constructed at run-time based on the size
 * of their input data sets.
 * <h3>Multi-Threading and Concurrency</h3>
 * The process of creating a Delaunay Triangulation (TIN) using an
 * incremental-insertion technique is inherently serial. Therefore, application
 * code that creates a TIN should not attempt to access the "add" methods
 * for this interface in parallel threads.  However, this API is designed so
 * that once a TIN is complete, it can be accessed by multiple threads
 * on a read-only basis.
 * Multi-threaded access is particularly useful when performing
 * surface-interpolation operations to construct raster (grid) representations
 * of data.
 */
public interface IIncrementalTin {

  /**
   * Clears all internal state data of the TIN, preparing any allocated
   * resources for re-use. When processing multiple sets of input data
   * the clear() method has an advantage in that it can be used to reduce
   * the overhead related to multiple edge object implementation.
   */
  void clear();

  /**
   * Performs a survey of the TIN to gather statistics about
   * the triangles formed during its construction.
   *
   * @return A valid instance of the TriangleCount class.
   */
  TriangleCount countTriangles();

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
  void dispose();

  /**
   * Gets the bounds of the TIN. If the TIN is not initialized (bootstrapped),
   * this method returns a null.
   *
   * @return if available, a valid rectangle giving the bounds of the TIN;
   * otherwise, a null
   */
  Rectangle2D getBounds();

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
  List<IQuadEdge> getEdges();

  /**
   * Gets an iterator for stepping through the collection of edges
   * currently stored in the TIN.
   * <p>
   * Note that this loop produces only the "base side" of each edge.  To access
   * the counterpart (the side of the edge in the other direction), an
   * application needs to access its dual using the edge's getDual() method.
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges
   * returned by this routine are the same objects as those currently being used
   * in the instance. Any modification of the edge objects will damage
   * the TIN. Therefore, applications must not modify the edges returned by this
   * method.
   * <strong>Caution:</strong> For reasons of efficiency, the iterator
   * does not offer any protection against concurrent modification.
   * Therefore applications using this iterator must never modify the
   * TIN during iteration.
   *
   * @return a valid iterator.
   */
  Iterator<IQuadEdge> getEdgeIterator();


  /**
   * Provides a convenience implementation
   * that can be used with a Java enhanced-loop statement to access the set
   * of edges that form the structure of the incremental TIN.
   * The edges produced by this Iterator are filtered so that the
   * fictitious edges (ghost edges) are not produced by the
   * iteration.
   * <p>
   * For example, this method could be used in the following manner:
   * <pre>
   *     IIncremntal tin = // some implementation
   *     for(IQuadEdge e: tin.edges(){
   *            // some processing logic
   *     }
   * </pre>
   *
   * <p>
   * Note that this loop produces only the "base side" of each edge.  To access
   * the counterpart (the side of the edge in the other direction), an
   * application needs to access its dual using the edge's getDual() method.
   * <p>
   * Please see the API documentation for getEdgeIterator() for
   * cautions regarding the use of this method.
   * @return a valid instance.
   */
  Iterable<IQuadEdge>edges();

  /**
   * Provides a convenience implementation
   * that can be used with a Java enhanced-loop statement to access the set
   * of SimpleTriangles implicit in the structure of the incremental TIN.
   * This iterable will produce all SimpleTriangles in the collection with
   * no repeats or omissions.
   * <p>
   * For example, this method could be used in the
   * following manner:
   * <pre>
   *     IIncremntal tin = // a valid instance
   *     for(SimpleTriangle t: tin.triangles(){
   *            // some processing logic
   *     }
   * </pre>
   *
   * <p>
   * Please see the API documentation for SimpleTriangleIterator for
   * cautions regarding the use of this method.
   * @return a valid instance.
   */
   Iterable<SimpleTriangle> triangles();

  /**
   * Gets the maximum index of the currently allocated edges. This
   * method can be used in support of applications that require the need
   * to survey the edge set and maintain a parallel array or
   * collection instance that tracks information about the edges.
   * In such cases, the maximum edge index provides a way of knowing how large
   * to size the array or collection.
   * <p>
   * Internally, Tinfour uses edge index values to manage edges in memory.
   * The while there can be small gaps in the indexing sequence, this
   * method provides a way of obtaining the absolute maximum value of
   * currently allocated edges.
   *
   * @return a positive value or zero if the TIN is not bootstrapped.
   */
  int getMaximumEdgeAllocationIndex();

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
  double getNominalPointSpacing();

  /**
   * Gets the Thresholds object that is associated with this instance.
   * Because all elements in Thresholds are declared final (immutable),
   * it can be shared safely between multiple threads or other classes.
   * @return a valid instance
   */
  Thresholds getThresholds();

  /**
   * Gets a list of edges currently defining the perimeter of the TIN.
   * The list may be empty if the TIN is not initialized (bootstrapped).
   * <p>
   * <strong>Warning:</strong> For efficiency purposes, the edges
   * return by this routine are the same objects as those currently being used
   * in the instance. Any modification of the edge objects will damage
   * the TIN. Therefore, applications must not modify the edges returned by
   * this method.
   *
   * @return a valid, potentially empty list.
   */
  List<IQuadEdge> getPerimeter();

  /**
   * Gets a new instance of the INeighborEdgeLocator interface.
   * Instances observe the contract of the IProcessUsingTin interface
   * in that they access the TIN on a read-only basis and may be used
   * in parallel threads provided that the TIN is not modified.
   * <p>
   * <strong>This method is obsolete. Use getNavigator instead.</strong>
   *
   * @return an edge locator tied to this TIN.
   */
  INeighborEdgeLocator getNeighborEdgeLocator();

    /**
   * Gets a new instance of the IIncrementalTinNavigator interface.
   * The navigator implementations provide utilities to perform
   * geometry-based queries on the TIN. These queries include tests to see if
   * a coordinate point lies within the TIN, tests to get neighboring edges,
   * etc.
   * <p>
   * Instances observe the contract of the IProcessUsingTin interface
   * in that they access the TIN on a read-only basis and may be used
   * in parallel threads provided that the TIN is not modified.
   *
   * @return an valid navigator instance or a null if the TIN is not
   * properly bootstrapped.
   */
  IIncrementalTinNavigator getNavigator();



  /**
   * Gets a new instance of a neighborhood points collector.
   * Instances observe the contract of the IProcessUsingTin interface
   * in that they access the TIN on a readonly basis and may be used
   * in parallel threads provided that the TIN is not modified.
   *
   * @return an points collector tied to this TIN.
   */
  INeighborhoodPointsCollector getNeighborhoodPointsCollector();

  /**
   * Gets an implementation of the integrity check interface suitable for
   * the referenced TIN implementation.
   *
   * @return a valid integrity check implementation.
   */
  IIntegrityCheck getIntegrityCheck();

  /**
   * Insert a vertex into the collection of vertices managed by
   * the TIN. If the TIN is not yet bootstrapped, the vertex will
   * be retained in a simple list until enough vertices are received
   * in order to bootstrap the TIN.
   *
   * @param v a valid vertex
   * @return true if the TIN is bootstrapped; otherwise false
   */
  boolean add(Vertex v);

  /**
   * Inserts a list of vertices into the collection of vertices managed by the
   * TIN. If the TIN is not yet bootstrapped, the vertices will be retained in
   * a simple list until enough vertices are received in order to bootstrap
   * the TIN.
   * <h1>Performance Consideration Related to List</h1>
   *
   * In the bootstrap phase, three points are chosen at random from the vertex
   * list to create the initial triangle for insertion. In the event that the
   * three points are not a suitable choice (as when they are collinear or
   * nearly collinear), the process will be repeated until a valid initial
   * triangle is selected. Thus, there is a small performance advantage in
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
   * @param monitor an optional monitoring implementation; null if not used.
   * @return true if the TIN is bootstrapped; otherwise false
   */
  boolean add(List<Vertex> list, IMonitorWithCancellation monitor);

  /**
   * Allocates a number of vertices roughly sufficient to represent a TIN
   * containing the specified number of vertices. This method also
   * serves as a diagnostic tool, allowing a test-application
   * to separate the portion of processing time consumed by
   * Java object construction from that spent on processing the
   * vertex data.
   *
   * @param nVertices the number of vertices expected to be added to the TIN.
   */
  void preAllocateEdges(int nVertices);

  /**
   * Print statistics and diagnostic information collected during the
   * TIN construction process. This information will be removed and
   * reset by a call to the clear() method.
   *
   * @param ps A valid instance of a PrintStream to receive the output.
   */
  void printDiagnostics(PrintStream ps);

  /**
   * Gets a list of vertices currently stored in the TIN. This list of objects
   * is not necessarily equivalent to the set of objects that were input because
   * some vertices may have been incorporated into one or more vertex-merger
   * groups. Note that the list of vertices is not sorted and will usually
   * not be returned in the same order as the original input set.
   * <p>
   * <strong>Note:</strong> For efficiency purposes, the vertices
   * return by this routine are the same objects as those currently being used
   * in the instance. The index and "reserved" elements of the Vertex
   * class are not used by the TIN and may be modified by application
   * code as required. However, the geometry related fields must not be
   * modified once a vertex is added to a TIN.
   *
   * @return a valid list of vertices, potentially empty if the TIN has
   * not been initialized.
   */
  List<Vertex> getVertices();

  /**
   * Indicates whether the instance contains sufficient information
   * to represent a TIN. Bootstrapping requires the input of at least
   * three distinct, non-collinear vertices. If the TIN is not bootstrapped
   * methods that access its content may return empty or null results.
   *
   * @return true if the TIN is successfully initialized; otherwise, false.
   */
  boolean isBootstrapped();

  /**
   * Provides a diagnostic print out of the edges comprising the TIN.
   *
   * @param ps A valid print stream.
   */
  void printEdges(final PrintStream ps);

  /**
   * Removes the specified vertex from the TIN. If the vertex is part of
   * a merged-group, it is removed from the group by the structure of the
   * TIN is unchanged.
   *
   * @param vRemove the vertex to be removed
   * @return true if the vertex was found in the TIN and removed.
   */
  boolean remove(final Vertex vRemove);

  /**
   * Specifies a rule for interpreting the Z value of a group of vertices that
   * were merged due to being coincident, or nearly coincident.
   *
   * @param resolutionRule The rule to be used for interpreting merged vertices.
   */
  void setResolutionRuleForMergedVertices(
    final VertexMergerGroup.ResolutionRule resolutionRule);

  /**
   * Adds constraints to the TIN.
   * <p>
   * <strong>Using Constraints</strong>
   * <p>
   * There are a number of important restrictions to the use of constraints.
   * Constraints must only be added to the TIN once, after all other vertices
   * have already been added. Furthermore, the addConstraint method can only
   * be called once. Logic is implemented as a safety measure to ensure that
   * these restrictions are not accidentally violated.
   * <p>
   * There are also important restrictions on the geometry of constraints.
   * Most importantly, constraints must never intersect each other except
   * at the endpoints of the segments that define them (i.e. segments
   * in constraints must never cross each other). Due to the high cost of
   * processing required to check that this restriction is observed,
   * it is not directly enforced by the Tinfour implementations.
   * <p>
   * <strong>Restoring Conformity</strong>
   * <p>
   * When constraints are added to a Delaunay triangulation, they often
   * violate the Delaunay criterion and result in a non-conforming
   * mesh. The addConstraint method can optionally restore conformity
   * by inserting synthetic points into the the constraint edges.
   * The cost of this process is additional processing time and
   * an increase in the number of points in the TIN.
   * <p>
   * When points are synthesized, it is necessary to interpolate
   * a value for the z-coordinate. At this time, the specific interpolation
   * process is undefined. The current Tinfour implementations
   * use linear interpolation between constraint points. While no
   * viable alternative approach is currently under consideration, the
   * choice of interpolation method is subject to change in the future.
   *
   * @param constraints a valid, potentially empty list.
   * @param restoreConformity restores conformity
   */
  void addConstraints(
    List<IConstraint> constraints, boolean restoreConformity);

  /**
   * Gets a shallow copy of the list of constraints currently
   * stored in the TIN.
   *
   * @return a valid, potentially empty list of constraint instances.
   */
  List<IConstraint> getConstraints();


  /**
   * Gets the constraint associated with the index, or a null if
   * no such constraint exists. Note that there is no out-of-bounds
   * range for the input index. An invalid index simply yields a null
   * reference.
   * @param index an arbitrary integer index
   * @return if found, a valid constraint; otherwise a null.
   */
  IConstraint getConstraint(int index);

  /**
   * Gets the number of synthetic vertices added to the TIN.
   * Vertices can be synthesized as part of the Delaunay restoration
   * process when adding constraints. Future implementations of additional
   * functions (such as Delaunay refinement) may also add synthetic points.
   *
   * @return a positive integer, potentially zero.
   */
  int getSyntheticVertexCount();


  /**
   * Split an existing edge into two at the midpoint, using the
   * specified zSplit value as the z coordinate for the edge.
   * <p>
   * <strong>WARNING</strong> The restoreDelaunay feature is
   * not yet implemented.
   * @param eInput a valid edge
   * @param zSplit the z coordinate for the new vertex
   * @param restoreConformity restore Delaunay conformance after
   * insertion <strong>NOT YET IMPLEMENTE</strong>
   * @return the insertion vertex
   */
   Vertex  splitEdge(
           IQuadEdge eInput,
           double zSplit,
           boolean restoreConformity);

  /**
   * Gets the region constraint associated with the edge, if any. If the edge is
   * on the border of a region, this method will return the constraint to its
   * immediate left side.
   *
   * @param edge a valid edge instance.
   * @return if a region constraint is associated with the edge, a valid
   * instance; otherwise, a null.
   */
  IConstraint getRegionConstraint(IQuadEdge edge);


    /**
   * Gets the linear constraint associated with the edge, if any.
   * In some cases, a linear constraint may lie within a constrained
   * region, but it will not lie on the border of a constrained
   * region.
   *
   * @param edge a valid edge instance.
   * @return if a linear constraint is associated with the edge, a valid
   * instance; otherwise, a null.
   */
  IConstraint getLinearConstraint(IQuadEdge edge);


   /**
   * Provides a convenience implementation
   * that can be used with a Java enhanced-loop statement to access the set
   * of vertices stored in an incremental TIN.
   * This iterable will produce all vertices in the collection with
   * no repeats or omissions.
   * <p>
   * For example, this method could be used in the
   * following manner:
   * <pre>
   *     IIncremntal tin = // a valid instance
   *     for(Vertex v: tin.verticess(){
   *            // some processing logic
   *     }
   * </pre>
   *
   * <p>
   * Please see the API documentation for VertexIterator for
   * cautions regarding the use of this method.
   * @return a valid instance.
   */
   Iterable<Vertex> vertices();


}
