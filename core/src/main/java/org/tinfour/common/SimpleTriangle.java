/* --------------------------------------------------------------------
 * Copyright (C) 2018  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2018  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * Provides methods and elements for a simple representation of a triangle based
 * on IQuadEdge edges.
 */
public class SimpleTriangle {

  private final IIncrementalTin tin;
  private final IQuadEdge edgeA;
  private final IQuadEdge edgeB;
  private final IQuadEdge edgeC;

  /**
   * Construct a simple triangle from the specified edges. For efficiency
   * purposes, this constructor is very lean and does not perform sanity
   * checking on the inputs.
   *
   * @param tin a reference to the TIN that was used to create this triangle.
   * @param a a valid edge
   * @param b a valid edge
   * @param c a valid edge
   */
  public SimpleTriangle(
          IIncrementalTin tin,
          IQuadEdge a,
          IQuadEdge b,
          IQuadEdge c) {
    this.tin = tin;
    this.edgeA = a;
    this.edgeB = b;
    this.edgeC = c;
  }

  /**
   * Get edge a from the triangle
   *
   * @return a valid edge
   */
  public IQuadEdge getEdgeA() {
    return edgeA;
  }

  /**
   * Get edge b from the triangle
   *
   * @return a valid edge
   */
  public IQuadEdge getEdgeB() {
    return edgeB;
  }

  /**
   * Get edge c from the triangle
   *
   * @return a valid edge
   */
  public IQuadEdge getEdgeC() {
    return edgeC;
  }

  /**
   * Gets vertex A of the triangle. The method names used in this class follow
   * the conventions of trigonometry. Vertices are labeled so that vertex A is
   * opposite edge a, vertex B is opposite edge b, etc. This approach is
   * slightly different than that used in other parts of the Tinfour API.
   *
   * @return a valid vertex
   */
  public Vertex getVertexA() {
    return edgeC.getA();
  }

  /**
   * Gets vertex B of the triangle. The method names used in this class follow
   * the conventions of trigonometry. Vertices are labeled so that vertex A is
   * opposite edge a, vertex B is opposite edge b, etc. This approach is
   * slightly different than that used in other parts of the Tinfour API.
   *
   * @return a valid vertex
   */
  public Vertex getVertexB() {
    return edgeA.getA();
  }

  /**
   * Gets vertex A of the triangle. The method names used in this class follow
   * the conventions of trigonometry. Vertices are labeled so that vertex A is
   * opposite edge a, vertex B is opposite edge b, etc. This approach is
   * slightly different than that used in other parts of the Tinfour API.
   *
   * @return a valid vertex
   */
  public Vertex getVertexC() {
    return edgeB.getA();
  }

  /**
   * Gets the area of the triangle. This value is positive if the triangle is
   * given in counterclockwise order and negative if it is given in clockwise
   * order. A value of zero indicates a degenerate triangle.
   *
   * @return a valid floating point number.
   */
  public double getArea() {
    Vertex a = edgeA.getA();
    Vertex b = edgeB.getA();
    Vertex c = edgeC.getA();
    double h = (c.y - a.y) * (b.x - a.x) - (c.x - a.x) * (b.y - a.y);
    return h / 2;
  }

  /**
   * Gets the polygon-based constraint that contains this triangle, if any.
   * <p>
   * <strong>Under Construction</strong>This method is not yet complete. Because
   * the Tinfour implementation does not yet record which side of an edge an
   * region-based constraint lies on, there are cases involving constraint
   * borders that will not be accurately detected. It can only reliably report
   * membership when a triangle has at least one edge that is entirely inside a
   * constraint area
   *
   * @return if the triangle is enclosed by a constraint, a valid instance;
   * otherwise, a null.
   */
  public IConstraint getContainingRegion() {
    // The triangle is an interior triangle if any one edge is
    // asspciated with a region
    return tin.getRegionConstraint(edgeA);
  }

  /**
   * Gets a Java Path2D based on the geometry of the triangle mapped through an
   * optional affine transform.
   *
   * @param transform a valid transform, or the null to use the identity
   * transform.
   * @return a valid instance of a Java Path2D
   */
  public Path2D getPath2D(AffineTransform transform) {
    AffineTransform af = transform;
    if (transform == null) {
      af = new AffineTransform();
    }
    double[] c = new double[12];
    
    Vertex A = edgeA.getA();
    Vertex B = edgeB.getA();
    Vertex C = edgeC.getA();
    c[0] = A.getX();
    c[1] = A.getY();
    c[2] = B.getX();
    c[3] = B.getY();
    c[4] = C.getX();
    c[5] = C.getY();
    af.transform(c, 0, c, 6, 3);

    Path2D path = new Path2D.Double();
    path.moveTo(c[6], c[7]);
    path.lineTo(c[8], c[9]);
    path.lineTo(c[10], c[11]);
    path.closePath();
    return path;
  }
}
