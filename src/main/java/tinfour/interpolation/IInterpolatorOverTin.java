/*
 * Copyright 2014 Gary W. Lucas.
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
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 08/2014  G. Lucas     Created
 * 08/2015  G. Lucas     Migrated to current package
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.interpolation;

import tinfour.common.IProcessUsingTin;

/**
 * Defines an interface for interpolating data over a Triangulated
 * Irregular Network implementation.
 */
public interface IInterpolatorOverTin extends IProcessUsingTin {

     /**
     * Perform interpolation using the specified valuator.
     * <h1>Important Synchronization Issue</h1>
     * To improve performance, classes that implement this interface
     * frequently maintain state data about the TIN that can be reused
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
     * be reset using a call to resetForChangeToTin() defined in the
     * IProcessUsingTin interface.
     * @param x the x coordinate for the interpolation point
     * @param y the y coordinate for the interpolation point
     * @param valuator a valid valuator for interpreting the z value of each
     * vertex or a null value to use the default.
     * @return if the interpolation is successful, a valid floating point
     * value; otherwise, a NaN.
     */
    public double interpolate(double x, double y, IVertexValuator valuator) ;


    /**
     * Indicates whether the interpolation class supports the computation
     * of surface normals through the getUnitNormal() method.
     * @return true if the class implements the ability to compute
     * surface normals; otherwise, false.
     */
    public boolean isSurfaceNormalSupported();


  /**
   * Computes the surface normal at the most recent interpolation point,
   * returning an array of three values giving the unit surface
   * normal as x, y, and z coordinates. If the recent interpolation was
   * unsuccessful (returned a Java Double.NaN), the results of this method
   * call are undefined. If the computation of surface normals is not
   * supported, the class may throw an UnsupportedOperationException.
   * @return if defined and successful, a valid array of dimension 3 giving
   * the x, y, and z components of the unit normal, respectively; otherwise,
   * a zero-sized array.
   */
  public double[] getSurfaceNormal();

    /**
     * Gets a string describing the interpolation method
     * that can be used for labeling graphs and printouts.
     * Because this string may be used as a column header in a table,
     * its length should be kept short.
     * @return A valid string
     */
    public String getMethod();
}
