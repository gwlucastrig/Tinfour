/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
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

package tinfour.common;

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
   * @return a link to the side-zero edge of the pair.
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
   * Gets the index value for this edge.
   * @return an integer value
   */
  int getIndex();

    /**
   * Indicates which side of an edge a particular IQuadEdge instance is
   * attached to. The side value is a strictly arbitrary index used for
   * algorithms that need to be able to assign a unique index to
   * both sides of an edge.
   *
   * @return a value of 0 or 1.
   */
  public int getSide();

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
  public int getConstraintIndex();


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
  public void setConstraintIndex(int constraintIndex);

  /**
   * Indicates whether an edge is constrained.
   *
   * @return true if the edge is constrained; otherwise, false.
   */
  public boolean isConstrained();


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
  public void setConstrained(int constraintIndex);

  /**
   * Indicates whether the edge is a member of a constrained area.
   * @return true if the constraint is a member of an area; otherwise false.
   */
  public boolean isConstrainedAreaMember();

  /**
   * Indicates whether an edge is constrained and a member of a
   * constraint which defines the data area.
   * @return true if the edge is the boundary of the data area;
   * otherwise, false.
   */
  public boolean isConstrainedAreaEdge();


  /**
   * Sets the constrained area membership flag for the edge to true.
   */
  public void setConstrainedAreaMemberFlag( );

  /**
   * Gets an instance of an iterable that performs a pinwheel operation.
   * This instance may be used in a Java for statement
   * <code>
   *    for(IQuadEdge e: startingEdge.pinwheel()){
   *    }
   * </code>
   * @return a valid Iterable.
   */
  public Iterable<IQuadEdge>pinwheel();
}
