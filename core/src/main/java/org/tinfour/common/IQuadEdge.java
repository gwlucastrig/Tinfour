/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
 * 12/2016  G. Lucas     Introduced support for constraints
 * 11/2017  G. Lucas     Refactored for constrained regions
 * 06/2025  G. Lucas     Refactored for better handling of constraint relationshipes
 *
 * Notes:
 * Special considerations for setForward() and setReverse()
 *   It is not an oversight that this interface does not specify
 * setForward() and setReverse() methods. Early on, I performed a
 * test implementation in which these methods were included in the
 * interface.  While this approach permitted a lot of code to be shared by
 * the standard and virtual TIN implementations, the overhead related to
 * Java type casting (downcasting) resulted in a surprising 27 percent
 * degradation in performance (as measured using the TwinBuildTest).
 * -----------------------------------------------------------------------
 */

package org.tinfour.common;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;

/**
 * Defines methods for accessing the data in a quad-edge
 * implementation.
 * <p>
 * Currently, Tinfour implements two kinds of quad-edge objects:
 * standard and semi-virtual. The standard implementation (the QuadEdge class)
 * is based on in-memory references and literal instances of objects.
 * The semi-virtual approach attempts to reduce the amount of memory used
 * by a Delaunay triangulation by maintaining some in-memory data in arrays
 * rather than objects. Edge-related objects are created as needed and
 * discarded when no long required.
 * <p>
 * <strong>Memory considerations</strong>
 * <p>
 * For a Delaunay triangulation with a sufficiently large number of vertices, N,
 * the number of edges in the triangular mesh approaches 3*N. Since many of
 * the data sets that Tinfour processes can include millions of vertices,
 * memory management becomes an important issue.  Both the standard and
 * semi-virtual edge instances are designed to be conservative in their
 * use of memory. This approach has a significant influence on the organization
 * of the methods in the IQuadEdge interface. It is worth noting that,
 * in Java, an object requires memory for its content (explicitly defined
 * data fields) and also for object-management overhead.
 * In many implementations of the Java Runtime Environment (JRE) running
 * with less than 32 gigabytes of memory, this overhead is about 12 bytes.
 * For JRE's configured with 32 gigabytes or more, that figure doubles.
 * <p>
 * <strong>Performance considerations</strong>
 * <p>
 * In general, get operations can be performed without
 * any degradation of performance.  However, set operations on quad-edges
 * often require down casting (narrow casting) of object references.
 * In ordinary applications, the performance cost of down casting is small.
 * But for applications that require very large data sets and repeated
 * modifications to the edge structure of the TIN, this cost can degrade
 * processing rates by as much as 25 percent. Thus this interface avoids
 * specifying any methods that set edge relationships (connections).
 * <p>
 * <strong>Constraints and constrained edges</strong>
 * <p>
 * Normally, Tinfour is free to choose the geometry of the edges in a triangular
 * mesh based on the Delaunay criterion. But some applications require that
 * certain edges be preserved as specified. Therefore, Tinfour supports
 * the specification of constraint objects to create a Constrained Delaunay
 * Triangulations (CDT). Background information on the CDT is provided at
 * the Tinfour project web article
 * <a href="https://gwlucastrig.github.io/TinfourDocs/DelaunayIntroCDT/index.html">
 * What is the Constrained Delaunay Triangulation and why would you care?</a>.
 * <p>
 * Tinfour supports two kinds of constraints: region (polygon) constraints,
 * and line constraints (chains of one or more connected edges not forming
 * a closed polygon).
 * <p>
 * <strong>Constraint assignment to edges</strong>
 * <p>
 * When constraint objects are added to an incremental TIN instance,
 * Tinfour assigns each object a unique integer index (currently, in the range
 * zero to 8190). IQuadEdge instances can store these indices for internal
 * or application use.
 * <p>
 * In a Delaunay triangulation, an edge is either constrained (fixed geometry)
 * or unconstrained (free to be modified to meet the Delaunay criterion).
 * In Tinfour, an can have some combination of the following constraint-related states:
 * <ol>
 * <li>Unconstrained</li>
 * <li>Border of a constrained region (polygon) or the common border
 * of two adjacent regions (constrained)</li>
 * <li>The interior of a region constraint (unconstrained)</li>
 * <li>A member of a line-based constraint (constrained)</li>
 * </ol>
 * <p>
 * It is possible for an edge to belong to both a region-based constraint
 * (either as its border or its interior) and a line-based constraint.
 * Unfortunately, memory considerations for incremental TIN construction
 * limit the number of references to two.  But an edge that is assigned both
 * as the border of two adjacent constraint regions and an independent
 * line constraint would require three. In such cases, the IQuadEdge instances
 * give priority to the region specifications.  Tinfour's incremental TIN classes
 * implement logic for maintaining supplemental information so that they can
 * track linear constraint assignments when necessary.
 */
public interface IQuadEdge {

  /**
   * Gets the initial vertex for this edge.
   * @return a valid reference.
   */
  Vertex getA();

  /**
   * Gets the second vertex for this edge.
   * @return a valid reference or a null for a ghost edge.
   */
  Vertex getB();

  /**
   * Gets the reference to the side-zero edge of the pair.
   * <p>
   * From the perspective of application code, the Tinfour implementations
   * of the two elements associated with a bi-directional edge are symmetrical.
   * Neither side of an edge is more significant than the other.
   * @return a reference to the side-zero edge of the pair.
   */
  IQuadEdge getBaseReference();

  /**
   * Gets the dual edge to this instance.
   * @return a valid edge.
   */
  IQuadEdge getDual();

  /**
   * Gets the forward reference of the dual.
   * @return a valid reference
   */
  IQuadEdge getForwardFromDual();

  /**
   * Gets the reverse link of the dual.
   * @return a valid reference
   */
  IQuadEdge getReverseFromDual();



  /**
   * Gets the index value for this edge. In general, the index value is
   * intended for memory management and edge pools.  So while application
   * code may read index values, it is not generally enabled to set them.
   * <p>
   * In Tinfour implementations, edges are bi-directional. In effect,
   * the edge is implemented as a pair of unidirectional elements.
   * Each element is assigned a separate index.
   * <p>
   * One common use for the index code by applications is to main a record
   * of processing performed using edge-traversal operations. For example,
   * some applications use the index to maintain a bitmap of visited edges
   * when performing surface analysis.
   * <p>
   * When an edge is allocated, it is set with an arbitrary index value.
   * This value will not change while the edge remains allocated by and
   * edge-pool instance. As soon as the edge is released, it is likely
   * to have its index value reassigned.
   * @return a positive integer value
   */
  int getIndex();

  /**
   * Gets the index of the "base" side of a bi-directional edge.
   * In Tinfour implementations, edges are bi-directional. In effect,
   * the edge is implemented as a pair of unidirectional elements.
   * Each element is assigned a separate index. The first element in the
   * pair is designated as the "base" and is assigned an even-valued index.
   * Its dual is assigned a value one greater than the base index.
   * This method always returns an even value.
   * <p>
   * This method can be useful in cases where an application needs to track
   * a complete edge without regard to which side of the edge is being
   * considered.
   *
   * @return a positive, even value.
   */
  int getBaseIndex();

    /**
     * Indicates which side of an edge a particular IQuadEdge instance is
     * attached to. The side value is a strictly arbitrary index used for
     * algorithms that need to be able to assign a unique index to both sides of
     * an edge.
     *
     * @return a value of 0 or 1.
     */
  int getSide();

  /**
   * Gets the length of the edge.
   * @return a positive floating point value
   */
  double getLength();

  /**
   * Gets the forward reference of the edge.
   * @return a valid reference.
   */
  IQuadEdge getForward();

  /**
   * Gets the reverse reference of the edge.
   * @return a valid reference.
   */
  IQuadEdge getReverse();

  /**
   * Gets the dual of the reverse reference of the edge.
   * @return a valid reference.
   */
  IQuadEdge getDualFromReverse();

  /**
   * Gets the index of the constraint associated with this edge.
   *
   * @return a positive value; may be zero if not specified.
   */
  int getConstraintIndex();


   /**
   * Sets the constraint index for this edge.  This method does not
   * necessarily set an edge to a constrained status.  In some implementations
   * the constraint index may be used as a way of associating ordinary edges
   * with a neighboring constraint.
   * Constraint index values must be positive integers. The
   * range of supported values will depend on the specific class that
   * implements this interface. Please refer to the class documentation
   * for specific values.
   *
   * @param constraintIndex a positive number indicating which constraint
   * a particular edge is associated with.
   */
  void setConstraintIndex(int constraintIndex);

  /**
   * Indicates whether an edge is constrained.
   *
   * @return true if the edge is constrained; otherwise, false.
   */
  boolean isConstrained();

  /**
   * Indicates whether the edge is a member of a constrained region
   * (is in the interior or serves as the border of a polygon-based constraint).
   * A constrained region member is not necessarily a constrained edge.
   * @return true if the edge is a member of an region; otherwise false.
   */
  boolean isConstraintRegionMember();

  /**
   * Indicates whether the edge is a member of a constraint line, In some
   * cases, a constraint line member edge may lie within a constrained region
   * but will not lie on one of its borders.
   *
   * @return true if the edge is a member of an region; otherwise false.
   */
  boolean isConstraintLineMember();

  /**
   * Sets the constraint-line member flag for the edge to true.
   */
  void setConstraintLineMemberFlag();

    /**
   * Indicates whether the edge is in the interior of a constrained region.
   * Both sides of the edge lie within the interior of the region.
   * All points along the edge will lie within the interior of the region
   * with the possible exception of the endpoints. The endpoints may
   * lie on the border of the region.  An interior edge for a constrained
   * region is not a constrained edge.  Interior edges are also classified
   * as "member" edges of a constrained region.
   * @return true if the edge is in the interior of an region; otherwise false.
   */
  boolean isConstraintRegionInterior();

  /**
   * Indicates whether an edge represents the border of a constrained
   * region. Border edges will always be constrained.  Border edges are also
   * classified as "member" edges of a constrained region.
   * @return true if the edge is the border of the constrained region;
   * otherwise, false.
   */
  boolean isConstraintRegionBorder();

  /**
   * Sets a flag indicating that the edge is an edge of a constrained region.
   */
  void setConstraintRegionBorderFlag();

  /**
   * Sets the synthetic flag for the edge. Synthetic edges are
   * those that do not arise naturally from the TIN-building logic but
   * are created by special operations.
   * @param status true if the edge is synthetic; otherwise, false.
   */
  void setSynthetic(boolean status);

  /**
   * Indicates whether the synthetic flag is set for the edge.
   * @return true if the edge is synthetic; otherwise, false.
   */
  boolean isSynthetic();

  /**
   * Gets an instance of an iterable that performs a pinwheel operation.
   * This instance may be used in a Java for statement
   * <pre>
   *    for(IQuadEdge e: startingEdge.pinwheel()){
   *    }
   * </pre>
   * <p>
   * <strong>About the pinwheel operation:</strong> In the Tinfour library,
   * a pinwheel operation interates over the set of edges that connect to the
   * initial vertex of the current edge. The initial vertex is the
   * one returned from a call to getA().  Connected vertices may be obtained
   * through a call to getB().
   * <p>
   * <strong>Null references for vertex:</strong>If vertex A lies on the
   * perimeter of the Delaunay mesh, one or more of the connected edges
   * may terminate on the "ghost vertex" which is used by Tinfour to
   * complete the triangulation.  The ghost vertex is represented by
   * a null reference.  So applications performing a pinwheel on an
   * arbitrary edge should include logic to handle a null return from the
   * getB() method.
   * <pre>
   *    for(IQuadEdge e: startingEdge.pinwheel()){
   *        Vertex B = e.getB();
   *        if(B == null){
   *             // skip processing
   *        }else {
   *             // perform processing using B
   *        }
   *    }
   * </pre>
   *
   * @return a valid Iterable.
   */
  Iterable<IQuadEdge>pinwheel();


  /**
   * A deprecated method replaced by the equivalent transcribeToLine2D().
   * @param transform a valid affine transform
   * @param l2d a valid Line2D instance to receive the geometry data from the edge.
   */
  @Deprecated
  void setLine2D(AffineTransform transform, Line2D l2d);


  /**
   * Provides a convenience method for rendering edges by setting the
   * Line2D argument with the transformed coordinates of the edge.
   * The affine transform is used to map vertex A and B of the edge
   * to the specified coordinate system. The transformed coordinates
   * are then stored in the application-supplied Line2D object.  If a null
   * reference is supplied for the transform, this method treats it as the
   * identity transform.
   * <p>
   * This method is intended to support rendering operations that may
   * render a large number of edges using Java's Line2D class. In such cases,
   * this method avoids the overhead involved in creating multiple Line2D
   * instances by allowing an application to reuse a single instance multiple
   * times.
   * @param transform a valid affine transform
   * @param l2d a valid Line2D instance to receive the geometry data from the edge.
   */
  void transcribeToLine2D(AffineTransform transform, Line2D l2d);

  /**
   * Sets a flag identifying the edge as the border of a region-based constraint
   * and stores the index for that constraint.
   * @param constraintIndex a positive integer in the range zero to 8190, or -1 for a null constraint.
   */
  void setConstraintBorderIndex(int constraintIndex);

  /**
   * Sets a flag identifying the edge as the border of a line-based constraint
   * and stores the index for that constraint.
   *
   * @param constraintIndex a positive integer in range zero to 8190
   */
  void setConstraintLineIndex(int constraintIndex);

  /**
   * Sets a flag identifying the edge as an interior member of a region-based
   * constraint
   * and stores the index for that constraint.
   *
   * @param constraintIndex a positive integer in the range 0 to 8190, or -1 for a null value
   */
  void setConstraintRegionInteriorIndex(int constraintIndex);


  /**
   * Gets the index of the region-based constraint associated with an edge
   * that serves as part of the polygon bounding that region.
   * @return a positive integer or -1 if no constraint is specified.
   */
  int getConstraintBorderIndex();

  /**
   * Gets the index of the region-based constraint associated with an
   * edge contained in the interior of a constraint polygon.  The edge itself
   * is not necessarily constrained and is not part of the definition
   * for the polygon.
   * @return a positive integer or -1 if no constraint is specified.
   */
  int getConstraintRegionInteriorIndex();

  /**
   * Gets the index of a line-based constraint associated with an edge.
   * The edge is constrained.  Due to limitations of memory, the Tinfour
   * implementation cannot support an index for an edge that happens to
   * be a member of multiple constraints (as in the case of an edge that
   * is also part of a border constraint).
   * @return a positive integer or -1 is no constraint index is available.
   */
  int getConstraintLineIndex();
}
