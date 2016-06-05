/*
 * Copyright 2013 Gary W. Lucas.
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
 * ------ --------- -------------------------------------------------
 * 03/2014 G. Lucas Created as a method of IncrementalTIN
 * 05/2014 G. Lucas Broken into separate class
 * 08/2015 G. Lucas Refactored for QuadEdge class
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.interpolation;

import tinfour.common.IIncrementalTin;
import tinfour.common.INeighborEdgeLocator;
import tinfour.common.IQuadEdge;
import tinfour.common.Thresholds;
import tinfour.common.Vertex;

/**
 * Provides interpolation based on treating the surface as a collection
 * of planar triangular facets.
 */
public class TriangularFacetInterpolator implements IInterpolatorOverTin {
    // tolerance for identical vertices.
    // the tolerance factor for treating closely spaced or identical vertices
    // as a single point.
    final private double vertexTolerance2; // square of vertexTolerance;
    final private double precisionThreshold;

    final IIncrementalTin tin;
    final INeighborEdgeLocator locator;

    private final VertexValuatorDefault defaultValuator = new VertexValuatorDefault();

    private double nx, ny, nz;

    /**
     * Construct an interpolator that operates on the specified TIN.
     * Because the interpolator will access the TIN on a read-only basis,
     * it is possible to construct multiple instances of this class and
     * allow them to operate in parallel threads.
     * <h1>Important Synchronization Issue</h1>
     * To improve performance, the classes in this package
     * frequently maintain state-data about the TIN that can be reused
     * for query to query.  They also avoid run-time overhead by not
     * implementing any kind of Java synchronization or or even the
     * concurrent-modification testing provided by the
     * Java collection classes.   If an application modifies the TIN, instances
     * of this class will not be aware of the change. In such cases,
     * interpolation methods may fail by either throwing an exception or,
     * worse, returning an incorrect value. The onus is on the calling
     * application to manage the use of this class and to ensure that
     * no modifications are made to the TIN between interpolation operations.
     * If the TIN is modified, the internal state data for this class must
     * be reset using a call to resetForChangeToTIN().
     *
     * @param tin a valid instance of an incremental TIN.
     */
    public TriangularFacetInterpolator(IIncrementalTin tin) {
        double nominalPointSpacing = tin.getNominalPointSpacing();
        Thresholds thresholds = new Thresholds(nominalPointSpacing);

        vertexTolerance2 = thresholds.getVertexTolerance2();
        precisionThreshold = thresholds.getPrecisionThreshold();

        this.tin = tin;
        locator = tin.getNeighborEdgeLocator();
    }

    /**
     * Used by an application to reset the state data within the interpolator
     * when the content of the TIN may have changed. Reseting the state data
     * unnecessarily may result in a minor performance reduction when processing
     * a large number of interpolations, but is otherwise harmless.
     */
    @Override
    public void resetForChangeToTin() {
        locator.resetForChangeToTin();
    }

    /**
     * Perform linear interpolation treating the triangle that contains the
     * query point as a flat plane. This interpolation
     * develops a continuous surface, but does not provide first-derivative
     * continuity at the edges of triangles.
     * <p>
     * This interpolation is not defined beyond the convex hull of the TIN
     * and this method will produce a Double.NaN if the specified coordinates
     * are exterior to the TIN.
     *
     * @param x the x coordinate for the interpolation point
     * @param y the y coordinate for the interpolation point
     * @param valuator a valid valuator for interpreting the z value of each
     * vertex or a null value to use the default.
     * @return if the interpolation is successful, a valid floating point
     * value; otherwise, a NaN.
     */
    @Override
    public double interpolate(double x, double y, IVertexValuator valuator) {
        // in the logic below, we access the Vertex x and z coordinates directly
        // but we use the getZ() method to get the z value.  Some vertices
        // may actually be VertexMergerGroup instances

        IVertexValuator vq = valuator;
        if (vq == null) {
            vq = defaultValuator;
        }

        IQuadEdge e= locator.getNeigborEdge(x, y);

        if (e == null) {
          // this should happen only when TIN is not bootstrapped
            return Double.NaN;
        }


        Vertex v0 = e.getA();
        Vertex v1 = e.getB();
        Vertex v2 = e.getForward().getB();


        double z0 = vq.value(v0);
        double z1 = vq.value(v1);
        double sx = x - v0.x;
        double sy = y - v0.y;

        double ax = v1.x - v0.x;
        double ay = v1.y - v0.y;
        double az = z1 - z0;

        if (v2 == null) {
            // (x,y) is either on perimeter or outside the TIN.
            // if on perimeter, apply linear interpolation
            nx = 0;
            ny = 0;
            nz = 0;
            double px = -ay;  // the perpendicular
            double py = ax;
            double h = (sx * px + sy * py)/Math.sqrt(ax*ax+ay*ay);
            if (Math.abs(h) < precisionThreshold) {
                double t;
                if (Math.abs(ax) > Math.abs(ay)) {
                    t = sx / ax;
                } else {
                    t = sy / ay;
                }
                return t * (z1 - z0) + z0;
            }

            return Double.NaN;

        }

        double z2 = vq.value(v2);


        double bx = v2.x - v0.x;
        double by = v2.y - v0.y;
        double bz = z2 - z0;

        nx = ay * bz - az * by;
        ny = az * bx - ax * bz;
        nz = ax * by - ay * bx;


        if (v0.getDistanceSq(x, y) < vertexTolerance2) {
            return z0;
        }

        if (v1.getDistanceSq(x, y) < vertexTolerance2) {
            return z1;
        }

        if (v2.getDistanceSq(x, y) < vertexTolerance2) {
            return z2;
        }

        if (Math.abs(nz) < precisionThreshold) {
            return (z0 + z1 + z0) / 3.0;
        }
        // solve for pz
        return z0 - (nx * sx + ny * sy) / nz;
    }

    @Override
    public boolean isSurfaceNormalSupported(){
      return true;
    }

  /**
   * Gets the unit normal to the surface at the position of the most
   * recent interpolation. The unit normal is computed based on the
   * partial derivatives of the surface polynomial evaluated at the
   * coordinates of the query point. Note that this method
   * assumes that the vertical and horizontal coordinates of the
   * input sample points are isotropic.
   * @return if defined, a valid array of dimension 3 giving
   * the x, y, and z components of the normal, respectively; otherwise,
   * a zero-sized array.
   */
    @Override
    public double [] getSurfaceNormal(){
      double nS = Math.sqrt(nx*nx+ny*ny+nz*nz);
      if(nS<1.0e-20){
        return new double[0];
      }
      double []n = new double[3];
      n[0] = nx/nS;
      n[1] = ny/nS;
      n[2] = nz/nS;
      return n;
    }


    @Override
    public String getMethod() {
        return "Triangular Facet";
    }

}
