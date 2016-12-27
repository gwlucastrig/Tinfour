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

package tinfour.common;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.List;

/**
 * Defines the primary methods used by an incremental TIN
 * implementation. The intended purpose of this interface is to support
 * automatic testing of different TIN implementations or to compare the
 * results of experimental changes.
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
   * the triangle formed during its construction.
   *
   * @return A valid instance of the TriangleCount class.
   */
  public TriangleCount countTriangles();


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
   * in that they access the TIN on a readonly basis and may be used
   * in parallel threads provided that the TIN is not modified.
   * @return an edge locator tied to this TIN.
   */
  INeighborEdgeLocator getNeighborEdgeLocator();


    /**
   * Gets a new instance of a neighborhood points collector.
   * Instances observe the contract of the IProcessUsingTin interface
   * in that they access the TIN on a readonly basis and may be used
   * in parallel threads provided that the TIN is not modified.
   * @return an points collector tied to this TIN.
   */
  public INeighborhoodPointsCollector getNeighborhoodPointsCollector();

  /**
   * Gets an implementation of the integrity check interface suitable for
   * the referenced TIN implementation.
   * @return a valid integrity check implementation.
   */
  public IIntegrityCheck getIntegrityCheck();

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
   * @return a valid list of vertices, potentially empty if the TIN has
   * not been initialized.
   */
  public List<Vertex> getVertices();

  /**
   * Tests the vertices of the triangle that includes the reference edge
   * to see if any of them are an exact match for the specified
   * coordinates. Typically, this method is employed after a search
   * has obtained a neighboring edge for the coordinates.
   * If one of the vertices is an exact match, within tolerance, for the
   * specified coordinates, this method will return the edge that
   * starts with the vertex.
   *
   * @param x the x coordinate of interest
   * @param y the y coordinate of interest
   * @param baseEdge an edge from the triangle containing (x,y)
   * @param vertexTolerance2 the square of a tolerance specification
   * for accepting a vertex as a match for the coordinates
   * @return true if a match is found; otherwise, false
   */
  QuadEdge checkTriangleVerticesForMatch(
    QuadEdge baseEdge,
    double x,
    double y,
    double vertexTolerance2);

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
   * Determines whether the point is inside the convex polygon boundary
   * of the TIN.  If the TIN is not bootstrapped, this method will
   * return a value of false.
   * @param x The x coordinate of interest
   * @param y THe y coordinate of interest
   * @return true if the coordinates identify a point inside the
   * boundary of the TIN; otherwise, false.
   */
  boolean isPointInsideTin(double x, double y);

    /**
   * Provides a diagnostic print out of the edges comprising the TIN.
   *
   * @param ps A valid print stream.
   */
  public void printEdges(final PrintStream ps) ;

  /**
   * Removes the specified vertex from the TIN. If the vertex is part of
   * a merged-group, it is removed from the group by the structure of the
   * TIN is unchanged.
   *
   * @param vRemove the vertex to be removed
   * @return true if the vertex was found in the TIN and removed.
   */
  public boolean remove(final Vertex vRemove);

  /**
   * Specifies a rule for interpreting the Z value of a group of vertices that
   * were merged due to being coincident, or nearly coincident.
   *
   * @param resolutionRule The rule to be used for interpreting merged vertices.
   */
  public void setResolutionRuleForMergedVertices(
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
   * it is not  directly enforced by the Tinfour implementations.
   * <p>
   * <strong>Restoring Conformity</strong>
   * <p>
   * When constraints are added to a Delaunay triangulation, they often
   * violate the Delaunay criterion and result in a non-conforming
   * mesh. The addConstraint method can optionally restore conformity
   * by inserting synthetic points into the the constraint edges.
   * The cost of this process is additional processing time and
   * an increase in the number of points in the TIN.
   * <p>When points are synthesized, it is necessary to interpolate
   * a value for the z-coordinate. At this time, the specific interpolation
   * process is undefined. The current Tinfour implementations
   * use linear interpolation between constraint points. While no
   * viable alternative approach is currently under consideration, the
   * choice of interpolation method is subject to change in the future.
   *
   * @param constraints a valid, potentially empty list.
   * @param restoreConformity restores conformity
   */
   public void addConstraints(
     List<IConstraint> constraints, boolean restoreConformity);


   /**
    * Gets a shallow copy of the list of constraints currently
    * stored in the TIN.
    * @return a valid, potentially empty list of constraint instances.
    */
   public List<IConstraint>getConstraints();

   /**
    * Gets the number of synthetic vertices added to the TIN.
    * Vertices can be synthesized as part of the Delaunay restoration
    * process when adding constraints. Future implementations of additional
    * functions (such as Delaunay refinement) may also add synthetic points.
    * @return a positive integer, potentially zero.
    */
   public int getSyntheticVertexCount();

}
