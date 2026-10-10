/*
 * Copyright 2025 Gary W. Lucas.
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------   ---------   -------------------------------------------------
 * 06/2026  M. Carleton Created as part of removing java.awt dependencies
 *
 * Notes:
 *
 *   This class provides a lightweight, double-precision replacement for
 *   java.awt.geom.AffineTransform so that the Tinfour core module does not
 *   depend on the AWT graphics package (see GitHub issue #136). The matrix
 *   conventions and method semantics deliberately mirror those of the AWT
 *   class so that the numerical behavior of existing code is preserved.
 *
 *   The transform is represented by the 3-by-3 matrix
 *
 *       [ m00  m01  m02 ]     [ x ]     [ m00*x + m01*y + m02 ]
 *       [ m10  m11  m12 ]  *  [ y ]  =  [ m10*x + m11*y + m12 ]
 *       [   0    0    1 ]     [ 1 ]     [          1          ]
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * A double-precision affine transform for two-dimensional coordinates. This
 * class is a lightweight, dependency-free replacement for
 * {@code java.awt.geom.AffineTransform}. Its matrix conventions and method
 * semantics match those of the AWT class.
 */
public class GeoAffineTransform {

  private final double m00;
  private final double m10;
  private final double m01;
  private final double m11;
  private final double m02;
  private final double m12;

  /**
   * Constructs an identity transform.
   */
  public GeoAffineTransform() {
    m00 = 1.0;
    m11 = 1.0;
    m10 = 0.0;
    m01 = 0.0;
    m02 = 0.0;
    m12 = 0.0;
  }

  /**
   * Constructs a transform from the six specified matrix elements. The
   * argument order matches that of {@code java.awt.geom.AffineTransform}.
   *
   * @param m00 the X coordinate scaling element
   * @param m10 the Y coordinate shearing element
   * @param m01 the X coordinate shearing element
   * @param m11 the Y coordinate scaling element
   * @param m02 the X coordinate translation element
   * @param m12 the Y coordinate translation element
   */
  public GeoAffineTransform(double m00, double m10, double m01,
          double m11, double m02, double m12) {
    this.m00 = m00;
    this.m10 = m10;
    this.m01 = m01;
    this.m11 = m11;
    this.m02 = m02;
    this.m12 = m12;
  }

  /**
   * Transforms an array of coordinate pairs. The source and destination arrays
   * may be the same; overlapping ranges are handled in the same manner as
   * {@code java.awt.geom.AffineTransform}.
   *
   * @param srcPts the source coordinates, stored as (x,y) pairs
   * @param srcOff the offset into the source array
   * @param dstPts the destination array for the transformed (x,y) pairs
   * @param dstOff the offset into the destination array
   * @param numPts the number of points (coordinate pairs) to transform
   */
  public void transform(double[] srcPts, int srcOff,
          double[] dstPts, int dstOff, int numPts) {
    int sOff = srcOff;
    int dOff = dstOff;
    if (srcPts == dstPts && dOff > sOff && dOff < sOff + numPts * 2) {
      // The source and destination ranges overlap. Copy the source data into
      // the destination range first and read from there, matching AWT.
      System.arraycopy(srcPts, sOff, dstPts, dOff, numPts * 2);
      sOff = dOff;
    }
    int n = numPts;
    while (--n >= 0) {
      double x = srcPts[sOff++];
      double y = srcPts[sOff++];
      dstPts[dOff++] = m00 * x + m01 * y + m02;
      dstPts[dOff++] = m10 * x + m11 * y + m12;
    }
  }

  /**
   * Creates a transform that is the inverse of this transform.
   *
   * @return a valid transform
   * @throws GeoNoninvertibleTransformException if the matrix is degenerate and
   * cannot be inverted
   */
  public GeoAffineTransform createInverse()
          throws GeoNoninvertibleTransformException {
    double det = m00 * m11 - m01 * m10;
    if (Math.abs(det) <= Double.MIN_VALUE) {
      throw new GeoNoninvertibleTransformException("Determinant is " + det);
    }
    return new GeoAffineTransform(
            m11 / det,
            -m10 / det,
            -m01 / det,
            m00 / det,
            (m01 * m12 - m11 * m02) / det,
            (m10 * m02 - m00 * m12) / det);
  }

  /**
   * Gets the X coordinate scaling element (m00) of the matrix.
   *
   * @return the m00 element
   */
  public double getScaleX() {
    return m00;
  }

  /**
   * Gets the Y coordinate scaling element (m11) of the matrix.
   *
   * @return the m11 element
   */
  public double getScaleY() {
    return m11;
  }

  /**
   * Gets the X coordinate shearing element (m01) of the matrix.
   *
   * @return the m01 element
   */
  public double getShearX() {
    return m01;
  }

  /**
   * Gets the Y coordinate shearing element (m10) of the matrix.
   *
   * @return the m10 element
   */
  public double getShearY() {
    return m10;
  }

  /**
   * Gets the X coordinate translation element (m02) of the matrix.
   *
   * @return the m02 element
   */
  public double getTranslateX() {
    return m02;
  }

  /**
   * Gets the Y coordinate translation element (m12) of the matrix.
   *
   * @return the m12 element
   */
  public double getTranslateY() {
    return m12;
  }

  /**
   * Gets the six matrix elements in the order m00, m10, m01, m11, m02, m12.
   * This order matches {@code java.awt.geom.AffineTransform.getMatrix}.
   *
   * @param flatmatrix an array of length at least six to receive the elements
   */
  public void getMatrix(double[] flatmatrix) {
    flatmatrix[0] = m00;
    flatmatrix[1] = m10;
    flatmatrix[2] = m01;
    flatmatrix[3] = m11;
    if (flatmatrix.length > 5) {
      flatmatrix[4] = m02;
      flatmatrix[5] = m12;
    }
  }
}
