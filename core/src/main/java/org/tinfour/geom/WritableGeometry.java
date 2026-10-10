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
 * 06/2026  M. Carleton Created
 *
 * Notes:
 *
 *   The dual of GeometryWriter: an object that can describe its own outline
 *   geometry to a writer. Implemented by constraints, triangles, contours,
 *   alpha-shape parts, etc., so that rendering adapters can treat any
 *   drawable Tinfour object uniformly.
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.geom;

/**
 * Implemented by Tinfour objects that can describe their outline geometry to
 * a {@link GeometryWriter}. This lets presentation adapters convert any
 * drawable object (a constraint, a triangle, a contour, an alpha-shape, …) to
 * their target format through a single, uniform call.
 */
public interface WritableGeometry {

  /**
   * Writes the outline geometry of this object, in model (Cartesian)
   * coordinates, to the specified writer.
   *
   * @param writer a valid geometry writer
   */
  void writeTo(GeometryWriter writer);
}
