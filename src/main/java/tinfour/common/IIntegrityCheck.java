/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.common;

import java.io.PrintStream;
import java.util.List;

/**
 * Defines methods to be used for checking the correctness of code
 * for Incremental TIN implementations.
 */
public interface IIntegrityCheck {

  /**
   * Gets descriptive information about the cause of a test failure.
   *
   * @return if a failure occurred, descriptive information; otherwise
   * a string indicating that No Error Detected.
   */
  String getMessage();

  /**
   * Performs an inspection of the TIN checking for conditions that
   * violate the construction rules.
   * <h3>The Rules</h3>
   * <ul>
   * <li>Ensure that every edge links to two valid triangular circuits
   * (one on each side).
   * <li>Ensure that the set of ghost triangles forms a closed loop around the
   * convex hull (perimeter) of the TIN</li>
   * <li>Ensure that all ghost triangles are included in the perimeter
   * loop</li>
   * <li>Ensure that no triangles are degenerate (negative or zero area)</li>
   * <li>Ensure that all triangle pairs are Delaunay or
   * close-to-Delaunay optimal</li>
   * </ul>
   * @return true if the TIN passes inspection; otherwise, false.
   */
  boolean inspect();

 /**
  * Prints a summary of data collected during inspection of a TIN.
  * @param ps a print stream to receive the output.
  */
  public void printSummary(PrintStream ps);

  /**
   * Compares the list of vertices from the getVertices() method
   * to the original list of input vertices and determines whether they
   * are consistent. The getVertices method must return one, and only
   * one, instance of each vertex in the input list.
   * <p>This method temporarily changes the index of the vertices in
   * the input set to a sequential order. They are restored when the
   * routine returns.
   * <p><strong>Important: </strong>The test assumes that each vertex
   * in the input set is unique.  If a vertex occurs more than once,
   * the test will fail.
   * @param inputList the list of vertices input into the TIN.
   * @return true if the test passes; otherwise false
   */
  boolean testGetVerticesAgainstInputList(List<Vertex> inputList);

  /**
   * Gets the number of constrained edges that would violate the Delaunay
   * criterion
   * @return a positive integer.
   */
   public int getConstrainedViolationCount();

}
