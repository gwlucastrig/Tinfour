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
public interface DelaunayRefiner {

	/**
	 * Performs a single refinement operation on the associated triangulation.
	 * <p>
	 * Implementations should perform one atomic refinement step: identify an
	 * element that violates the configured quality criteria (for example a "bad"
	 * triangle) and repair it (for example by inserting a Steiner vertex,
	 * performing local retriangulation or edge flips). This method mutates the
	 * underlying triangulation in-place.
	 * </p>
	 *
	 * @return the {@code Vertex} that was inserted as part of this refinement step,
	 *         or {@code null} if no refinement was necessary (the triangulation
	 *         already meets the quality criteria or no applicable operation was
	 *         available).
	 *
	 * @implNote Implementations are encouraged to make this method behave
	 *           predictably when invoked repeatedly: calling it repeatedly on an
	 *           unchanged triangulation should either return the same inserted
	 *           vertex until the local repair completes, or return {@code null}
	 *           once no further single-step refinements are required.
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