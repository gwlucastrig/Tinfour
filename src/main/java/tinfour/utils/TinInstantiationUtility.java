/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 02/2016  G. Lucas     Created
 * -----------------------------------------------------------------------
 */
package tinfour.utils;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import tinfour.common.IIncrementalTin;
import tinfour.standard.IncrementalTin;
import tinfour.virtual.VirtualIncrementalTin;

/**
 * Provides a utility for instantiating either the QuadEdge or virtual
 * versions of the TIN class based on the amount of memory available
 */
public class TinInstantiationUtility {

  /**
   * An arbitrary setting telling how much of the maximum
   * memory to commit for processing the TIN.
   */
  public static final double DEFAULT_MEMORY_FRACTION = 0.6;
  /**
   * Measured memory use by Hotspot JVM with a maximum
   * memory setting smaller than 32 gigabytes, using object
   * reference compression.
   */
  public static final long MEMORY_FOR_VIRTUAL = 120L;

  /**
   * Measured memory use by Hotspot JVM with a maximum
   * memory setting smaller than 32 gigabytes,
   * using object reference compression.
   */
  public static final long MEMORY_FOR_STANDARD = 240L;

  private final int nVertices;
  private final long maxMemoryBytes;
  private final long maxAllowedForUse;

  private final long nBytesNeededForStandard;
  private final long nBytesNeededForVirtual;

  private final Class<?> tinClass;


  /**
   * A private constructor to deter application code from
   * instantiating this class without proper arguments.
   */
  private TinInstantiationUtility(){
    this.nVertices = 0;
    this.maxAllowedForUse = 0;
    this.maxMemoryBytes = 0;
    this.nBytesNeededForStandard = 0;
    this.nBytesNeededForVirtual = 0;
    this.tinClass = null;
  }
  /**
   * Constructs an instance bases on a specification for how much
   * memory is available for use and the anticipated number of vertices that
   * will be added to the TIN.
   *
   * @param memoryUseFraction the fraction of the JVM total memory that
   * is allowed for memory use, a value in the range 0 to 1.
   * @param nVertices the number of vertices to be used.
   */
  public TinInstantiationUtility(double memoryUseFraction, int nVertices) {
    if (memoryUseFraction < 0 || memoryUseFraction > 1) {
      // the bad argument doesn't really matter to this class,
      // but throw an exception for the benefit of the calling application
      throw new IllegalArgumentException(
        "Memory use fraction " + memoryUseFraction + " is not in range 0 to 1");
    }
    if(nVertices<3){
      throw new IllegalArgumentException(
        "Number of vertices "+nVertices+" is less than minimum of 3");
    }
    this.nVertices = nVertices;
    maxMemoryBytes = Runtime.getRuntime().maxMemory();
    maxAllowedForUse = (long) (maxMemoryBytes * memoryUseFraction);

    nBytesNeededForStandard = nVertices * MEMORY_FOR_STANDARD;
    nBytesNeededForVirtual = nVertices * MEMORY_FOR_VIRTUAL;

    if (nBytesNeededForStandard < maxAllowedForUse) {
      tinClass = IncrementalTin.class;
    } else {
      tinClass = VirtualIncrementalTin.class;
    }

  }

  /**
   * Constructs an instance of the specified TIN class.
   * <p>
   * The nominal point spacing should be a rough estimate of the average
   * distance between vertices. This value is used for establishing
   * decision thresholds such as "when are two vertices so close together
   * that they should be treated as one?" and does not need to be
   * especially accurate. As long as it is within an order of magnitude of
   * the true value, the Tinfour algorithms should produce good results.
   *
   * @param tinClass a valid class reference for an implementation of
   * IIncrementalTin
   * @param nominalPointSpacing a rough estimate of the average distance
   * between vertices.
   * @return a valid instance of the specified incremental TIN class.
   */
  public IIncrementalTin constructInstance(
    Class<?> tinClass,
    double nominalPointSpacing) {

    if (tinClass == null) {
      throw new IllegalArgumentException("Null specification for TIN class");
    }
    boolean failedToImplement = true;
    Class<?>[] interfaces = tinClass.getInterfaces();
    for(Class<?>c : interfaces){
      if(c.equals(IIncrementalTin.class)){
        failedToImplement = false;
        break;
      }
    }
    if (failedToImplement) {
      throw new IllegalArgumentException(
        "Specified class does not implement IIncrementalTin: "+tinClass.getName());
    }
    Double pointSpacing;
    if (0 <= nominalPointSpacing && nominalPointSpacing < Double.POSITIVE_INFINITY) {
      pointSpacing = nominalPointSpacing;
    } else {
      // used the default and hope for the best
      pointSpacing = new Double(1);
    }

    Constructor<?> constructor = null;
    try {
      Constructor<?>[] allConstructors = tinClass.getDeclaredConstructors();
      for (Constructor<?> c : allConstructors) {
        Class<?>[] pTypes = c.getParameterTypes();
        if (pTypes.length == 1 && pTypes[0]==Double.TYPE) {
          constructor = c;
          break;
        }
      }

      if (constructor == null) {
        throw new IllegalArgumentException(
          "TIN class does not specify a constructor with one double argument"
          + " giving nominal point spacing: "
          + tinClass.getName());
      }
    } catch (SecurityException ex) {
      throw new IllegalArgumentException(
        "No-argument constructor not available for " + tinClass.getName(), ex);
    }

    try {
      return (IIncrementalTin) (constructor.newInstance(pointSpacing));
    } catch (InstantiationException
      | IllegalAccessException
      | IllegalArgumentException
      | InvocationTargetException ex)
    {
      throw new IllegalArgumentException(
        "Unable to instantiate class " + tinClass.getName(), ex);
    }
  }

  /**
   * Uses the information about available memory use that was passed into
   * the constructor to select a TIN class and construct and instance.
   * <p>
   * The nominal point spacing should be a rough estimate of the average
   * distance between vertices. This value is used for establishing
   * decision thresholds such as "when are two vertices so close together
   * that they should be treated as one?" and does not need to be
   * especially accurate. As long as it is within an order of magnitude of
   * the true value, the Tinfour algorithms should produce good results.
   *
   * @param nominalPointSpacing a rough estimate of the average distance
   * between vertices.
   * @return a valid instance of a class that implements IIncrementalTin.
   */
  public IIncrementalTin constructInstance(double nominalPointSpacing)
  {
    return this.constructInstance(tinClass, nominalPointSpacing);
  }

  /**
   * Prints a summary of the size computations and resulting IIncrementalTin
   * class decision based on information supplied to the constructor.
   *
   * @param ps the print stream.
   */
  public void printSummary(PrintStream ps) {
    ps.format("Number of vertices used for calculation:    %8d\n", nVertices);
    ps.format("Memory limit for JVM:                       %12.3f megabytes\n",
      maxMemoryBytes / 1024.0 / 1024.0);
    ps.format("Rule of thumb threshold for method choice:  %12.3f megabytes\n",
      maxAllowedForUse / 1024.0 / 1024.0);
    ps.format("Memory required for standard edge class:    %12.3f megabytes\n",
      nBytesNeededForStandard / 1024.0 / 1024.0);
    ps.format("Memory required for virtual edge class:     %12.3f megabytes\n",
      nBytesNeededForVirtual / 1024.0 / 1024.0);
    ps.format("Selected class:                             %s\n",
      tinClass.getName());
  }

  /**
   * Get the class that was selected as most appropriate for the available
   * memory and number of vertices.
   * @return  a valid class reference for an implementation of IIncrementalTin
   */
  public Class<?>getTinClass(){
    return tinClass;
  }

}
