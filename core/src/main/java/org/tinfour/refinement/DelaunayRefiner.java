package org.tinfour.refinement;

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
	 * Refines the associated triangulation to improve its quality according to the
	 * implementation's criteria (e.g., increasing minimum angles, removing skinny
	 * triangles, etc).
	 */
	void refine();

}