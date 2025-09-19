/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 09/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.common;

/**
 * Defines an exception to be thrown in cases where the set of constraints
 * is insufficient to bootstrap a non-bootstrapped) instance of an Incremental TIN.
 * This situation arises when no vertices have been previously added to the
 * TIN or when the previously registered vertices were inadequate to
 * bootstrap the tin.  Typically, an incomplete geometry rises when all
 * vertices lie exactly on or very close to a single line or single point.
 * Because the vertices are insufficient to define a planar coordinate system,
 * they are insufficient to construct a Delaunay triangulation.
 *
 */
public class InsufficientConstraintGeometryException extends RuntimeException {
  /**
   * Creates an exception with the default message string
   */
    public InsufficientConstraintGeometryException(){
      super("Invalid or incomplete constraint geometry");
    }
}
