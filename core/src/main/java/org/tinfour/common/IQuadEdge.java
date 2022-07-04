/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
 * 12/2016  G. Lucas     Introduced support for constraints
 * 11/2017  G. Lucas     Refactored for constrained regions
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
 * In general, get operations can be performed without
 * any degradation of performance.  However, set operations on quad-edges
 * often require down casting (narrow casting) of object references.
 * In ordinary applications, the performance cost of down casting is small.
 * But for TIN applications require very large data sets with repeated
 * modifications to the edge structure of the TIN, this cost can degrade
 * processing rates by as much as 25 percent. Thus this interface avoids
 * specifying any methods that set edge relationships (connections).
 * <p>
 * See the definition of IConstraint for a discussion of constrained regions.
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
   * Sets an edge as constrained and sets its constraint index. Note that
   * once an edge is constrained, it cannot be set to a non-constrained
   * status.  Constraint index values must be positive integers. The
   * range of supported values will depend on the specific class that
   * implements this interface. Please refer to the class documentation
   * for specific values.
   * @param  constraintIndex positive number indicating which constraint
   * a particular edge is associated with.
   */
  void setConstrained(int constraintIndex);

  /**
   * Indicates whether the edge is a member of a constrained region
   * (is in the interior or serves as the border of a polygon-based constraint).
   * A constrained region member is not necessarily a constrained edge.
   * @return true if the edge is a member of an region; otherwise false.
   */
  boolean isConstrainedRegionMember();

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
  boolean isConstrainedRegionInterior();

  /**
   * Indicates whether an edge represents the border of a constrained
   * region. Border edges will always be constrained.  Border edges are also
   * classified as "member" edges of a constrained region.
   * @return true if the edge is the border of the constrained region;
   * otherwise, false.
   */
  boolean isConstrainedRegionBorder();

  /**
   * Sets a flag indicating that the edge is an edge of a constrained region.
   */
  void setConstrainedRegionBorderFlag();

  /**
   * Sets the constrained region membership flag for the edge to true.
   */
  void setConstrainedRegionInteriorFlag( );

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
   * Provides a convenience method for rendering edges by setting the
   * Line2D argument with the transformed coordinates of the edge.
   * The affine transform is used to map vertex A and B of the edge
   * to the specified coordinate system. The transformed coordinates
   * are then stored in the application-supplied Line2D object.
   * <p>
   * This method is intended to support rendering operations that may
   * render a large number of edges using Java's Line2D class. In such cases,
   * this method avoids the overhead involved in creating multiple Line2D
   * instances by allowing an application to reuse a single instance multiple
   * times.
   * @param transform a valid affine transform
   * @param l2d a valid Line2D instance
   */
  void setLine2D(AffineTransform transform, Line2D l2d);
}
