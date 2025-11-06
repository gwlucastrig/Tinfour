/* --------------------------------------------------------------------
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
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2025  M. Carleton  Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.refinement;

import org.tinfour.common.Vertex;

/**
 * A {@code DelaunayRefiner} refines a given triangulation to improve its
 * quality, for example by increasing minimum angles or enforcing other
 * geometric properties.
 * <p>
 * Refinement is performed in-place and may mutate the underlying triangulation.
 * Implementations should provide constructors for initializing with a specific
 * triangulation and any quality criteria or thresholds.
 * </p>
 * <p>
 * The {@link #refine()} method applies the refinement logic, such as inserting
 * Steiner points or retriangulating, to meet the desired properties.
 * </p>
 */
public interface IDelaunayRefiner {

	/**
	 * Performs a single refinement operation on the associated triangulation.
	 * <p>
	 * Implementations should perform one atomic refinement step: identify an
	 * element that violates the configured quality criteria (for example a "bad"
	 * triangle) and repair it (for example by inserting a Steiner vertex,
	 * performing local retriangulation or edge flips). This method mutates the
	 * underlying triangulation in-place.
	 * </p>
	 * <p>
	 * <strong>Implementation Note:</strong>
	 * Implementations are encouraged to make this method behave
	 * predictably when invoked repeatedly: calling it repeatedly on an
	 * unchanged triangulation should either return the same inserted
	 * vertex until the local repair completes, or return {@code null}
	 * once no further single-step refinements are required.
	 * </p>
	 * @return the {@code Vertex} that was inserted as part of this refinement step,
	 *         or {@code null} if no refinement was necessary (the triangulation
	 *         already meets the quality criteria or no applicable operation was
	 *         available).
	 * @see #refine()
	 */
	Vertex refineOnce();

	/**
	 * Refines the associated triangulation to improve its quality according to the
	 * implementation's criteria (e.g., increasing minimum angles, removing skinny
	 * triangles, etc).
	 * <p>
	 * This method typically runs a loop that performs repeated single-step
	 * refinements (for example by calling {@link #refineOnce()}) until the
	 * triangulation meets the quality criteria or a termination condition is
	 * reached.
	 * </p>
	 *
	 * @return {@code true} if the refinement process terminated successfully
	 *         because the triangulation meets the configured quality criteria;
	 *         {@code false} if the process terminated early due to a hard iteration
	 *         cap or other imposed stopping condition before the criteria were
	 *         satisfied.
	 */
	boolean refine();

}