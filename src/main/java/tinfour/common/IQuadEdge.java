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

}
