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
 * 12/2020  G. Lucas     Add extended precision for area computation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.common;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import org.tinfour.vividsolutions.jts.math.DD;

/**
 * Provides methods and elements for a simple representation of a triangle based
 * on IQuadEdge edges.
 */
public class SimpleTriangle {

  private final IIncrementalTin tin;
  private final IQuadEdge edgeA;
  private final IQuadEdge edgeB;
  private final IQuadEdge edgeC;
  private final int index;
  private Circumcircle circumcircle;

  /**
   * Construct a simple triangle from the specified edges. For efficiency
   * purposes, this constructor is very lean and does not perform sanity
   * checking on the inputs. In particular, it is essential that the specified
   * edge be a member of the specified TIN and that the TIN must not be modified
   * while the SimpleTriangle instance is in use.
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
    index = computeIndex();
  }

  /**
   * Construct a simple triangle from the specified edges. For efficiency
   * purposes, this constructor is very lean and does not perform sanity
   * checking on the inputs. In particular, it is essential that the specified
   * edge be a member of the specified TIN and that the TIN must not be modified
   * while the SimpleTriangle instance is in use.
   *
   *
   * @param tin a reference to the TIN that was used to create this triangle.
   * @param a a valid edge which must be a member of the specified TIN.
   */
  public SimpleTriangle(
    IIncrementalTin tin,
    IQuadEdge a) {
    this.tin = tin;
    this.edgeA = a;
    this.edgeB = a.getForward();
    this.edgeC = a.getReverse();
    index = computeIndex();
  }

  private int computeIndex() {
    int aIndex = edgeA.getIndex();
    int bIndex = edgeB.getIndex();
    int cIndex = edgeC.getIndex();
    if (aIndex <= bIndex) {
      return aIndex < cIndex ? aIndex : cIndex;
    } else {
      return bIndex < cIndex ? bIndex : cIndex;
    }
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
   * Get the shortest edge of the triangle.
   *
   * @return the shortest edge (by squared length)
   */
  public IQuadEdge getShortestEdge() {
      double lenA = edgeA.getLengthSq();
      double lenB = edgeB.getLengthSq();
      double lenC = edgeC.getLengthSq();

      if (lenA <= lenB && lenA <= lenC) {
          return edgeA;
      } else if (lenB <= lenA && lenB <= lenC) {
          return edgeB;
      } else {
          return edgeC;
      }
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
	  Vertex a = getVertexA();
	  Vertex b = getVertexB();
	  Vertex c = getVertexC();
	  if (a == null || b == null || c == null) {
	    return 0.0;
	  }

	  double ax = a.getX(), ay = a.getY();
	  double bx = b.getX(), by = b.getY();
	  double cx = c.getX(), cy = c.getY();

	  // fast double‐only computation of twice the signed area
	  double abx = bx - ax;
	  double aby = by - ay;
	  double acx = cx - ax;
	  double acy = cy - ay;
	  double det  = acy * abx - acx * aby;    // = 2 * signed area
	  
	  double perm = Math.abs(abx) * Math.abs(acy) + Math.abs(aby) * Math.abs(acx);
	  double bound = 3.33066907387547e-16 * perm; // 3 * 2^-53
	  if (Math.abs(det) >= bound) return 0.5 * det;

	  // Fallback: The area computation is performed using extended precision
	  // to reduce the severify of numeric errors when processing
	  // triangles that are nearly degenerate (nearly collapsed to a single line).
	  DD t1 = new DD(acy).selfMultiply(abx); // (cy−ay)*(bx−ax)
	  DD t2 = new DD(acx).selfMultiply(aby); // (cx−ax)*(by−ay)
	  DD ddDet = t1.selfSubtract(t2); // exact (acy*abx − acx*aby)
	  return ddDet.doubleValue() * 0.5;
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
    // associated with a region
    IConstraint con = tin.getRegionConstraint(edgeA);
    if(con==null){
       con = tin.getRegionConstraint(edgeB);
       if(con==null){
          con = tin.getRegionConstraint(edgeC);
       }
    }
    return con;
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

  /**
   * Obtains the circumcircle for a simple triangle.
   * <p>
   * This method uses an ordinary-precision computation for circumcircles
   * that yields acceptable accuracy for well-formed triangles.  Applications
   * that need more accuracy or may need to deal with nearly degenerate
   * triangles (nearly flat triangles) may prefer to use Tinfour's
   * GeometricOperations class for that purpose.
   *
   * @return a valid instance
   */
  public Circumcircle getCircumcircle() {
    if (circumcircle == null) {
      Vertex a = edgeA.getA();
      Vertex b = edgeB.getA();
      Vertex c = edgeC.getA();
      circumcircle = new Circumcircle();
      if (a == null || b == null || c == null) {
        circumcircle.setCircumcenter(
          Double.POSITIVE_INFINITY,
          Double.POSITIVE_INFINITY,
          Double.POSITIVE_INFINITY);
      } else {
        circumcircle.compute(a, b, c);
      }
    }
    return circumcircle;
  }

  /**
   * Gets the centroid for the triangle. The centroid is computed as the
   * simple average of the x, y, and z coordinates for the vertices that
   * define the triangle.
   * @return a valid instance of a Vertex.
   */
  public Vertex getCentroid() {
    Vertex a = edgeA.getA();
    Vertex b = edgeB.getA();
    Vertex c = edgeC.getA();
    if (a == null || b == null || c == null) {
      return null;
    }
    double x = (a.getX() + b.getX() + c.getX()) / 3.0;
    double y = (a.getY() + b.getY() + c.getY()) / 3.0;
    double z = (a.getZ() + b.getZ() + c.getZ()) / 3.0;
    Vertex v = new Vertex(x, y, z, 0);
    v.setSynthetic(true);
    return v;
  }

  /**
   * Indicates whether the triangle is a ghost triangle. A ghost triangle
   * is one that lies outside the bounds of a Delaunay triangulation and
   * contains an undefined vertex.
   * <p>
   * The TriangleCollector class does not produce ghost triangles, but
   * those created from perimeter edges may be ghosts.
   *
   * @return true if the triangle is a ghost triangle; otherwise, false.
   */
  public boolean isGhost() {
    Vertex a = edgeA.getA();
    Vertex b = edgeB.getA();
    Vertex c = edgeC.getA();
    return a == null || b == null || c == null;
  }

  /**
   * Gets a unique index value associated with the triangle.
   * <p>
   * The index value for the triangle is taken from the lowest-value
   * index of the three edges that comprise the triangle. It will be
   * stable provided that the underlying Triangulated Irregular Network (TIN)
   * is not modified.
   * @return an arbitrary integer value.
   */
  public int getIndex(){
    return index;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("SimpleTriangle{");
    sb.append("index=").append(index);

    // Vertices A, B, C
    Vertex A = getVertexA();
    Vertex B = getVertexB();
    Vertex C = getVertexC();
    sb.append(", vertices=[")
      .append(A != null ? A : "null").append(", ")
      .append(B != null ? B : "null").append(", ")
      .append(C != null ? C : "null").append("]");

    // Signed area
    sb.append(", area=").append(String.format("%.4f", getArea()));

    // Ghost?
    if (isGhost()) {
      sb.append(", ghost");
    }

    sb.append("}");
    return sb.toString();
  }
}
