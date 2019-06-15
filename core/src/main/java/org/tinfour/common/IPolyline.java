/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.common;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * An interface for defining a polyline feature (a polygon or chain of
 * connected line segments).
 */
public interface IPolyline extends Iterable<Vertex> {

  /**
   * Adds a vertex to the polyline feature.
   * Implementations of this interface are expected to ensure that all
   * vertices are unique and that no vertices are repeated.
   *
   * @param v a valid instance
   */
  void add(Vertex v);

  /**
   * Called to indicate that the feature is complete and that
   * no further vertices will be added to it. Some implementing classes
   * may perform lightweight sanity checking on the feature instance.
   * <p>
   * Multiple calls to complete are benign and will be ignored.
   * If vertices are added after complete is called, the behavior is undefined.
   */
  void complete();

  /**
   * Gets the bounds of the feature.
   * <p>
   * <strong>Caution:</strong> Implementations of this method expose
   * the Rectangle2D object used by the feature instance.
   * Although this approach supports efficiency
   * for the potentially intense processing conducted by the Tinfour classes,
   * it does not provide a safe implementation for careless developers.
   * Therefore, applications should <strong>not</strong> manipulate or modify
   * the rectangle instance returned by this routine at any time.
   *
   * @return a valid, potentially empty rectangle instance.
   */
  Rectangle2D getBounds();

  /**
   * Gets the total length of the feature. The length is the accumulated
   * sum of the lengths of the line segments that comprise the feature.
   * In the case of a closed polygon feature, the length is the
   * perimeter of the polygon.
   *
   * @return if the feature geometry is defined, a positive
   * floating point value, otherwise a zero.
   */
  double getLength();

  /**
   * Get the average distance between points for the feature.
   *
   * @return if the feature contains more than one point, a floating
   * point value greater than zero; otherwise a NaN.
   */
  double getNominalPointSpacing();

  /**
   * Gets the vertices for this feature. The vertices define a
   * non-self-intersecting chain of line segments (that is, no line segments
   * intersect except at their endpoints). The vertices are assumed to be
   * unique and far enough apart that they are stable in numeric operations.
   *
   * @return a valid list of two or more unique vertices.
   */
  List<Vertex> getVertices();


  /**
   * Indicates that sufficient information has been stored in
   * the polyline to establish a valid geometry.
   * @return true if the polyline has a valid geometry; otherwise, false.
   */
  boolean isValid();

  /**
   * Indicates whether the instance represents a polygon.
   * Some implementations may define a constant value for this method,
   * others may determine it dynamically.
   *
   * @return true if the instance is a polygon; otherwise, false.
   */
  boolean isPolygon();

  /**
   * Creates a new polyline feature with the specified geometry
   * and transfers any data elements defined by the implementing class
   * from the current object to the newly created one.
   * <p>
   * This method is intended to be used in cases where application
   * code performs some kind of transformation on the geometry of the
   * existing object and produces a new object. In doing so, the
   * application code treats the existing object on a read-only basis, but
   * is free to transfer any implementation-specific data from the
   * old object to the new.  Examples of possible transformations
   * include an implementation of a point-reduction technique such as
   * Visvalingam's algorithm or point-addition techniques such as curve
   * smoothing.
   * @param geometry a list or other iterable instance that
   * can be used as a source of vertices.
   * @return if successful, a new instance of the implementing class.
   */
  IPolyline refactor(Iterable<Vertex> geometry);

}
