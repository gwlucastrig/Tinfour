package org.tinfour.utils;

import org.tinfour.common.Circumcircle;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;

/**
 * Provides utility methods for performing computations and checks on
 * {@link SimpleTriangle} instances. This class is not intended to be
 * instantiated; all methods are static.
 */
public final class TriangleUtility {

	static final double EPSILON = 1e-9; // Used for small floating point comparisons
	private static final double EPS2 = EPSILON * EPSILON;

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private TriangleUtility() {
		// Utility class should not be instantiated.
	}

	/**
	 * Calculates the ratio of the triangle's circumradius to the length of its
	 * shortest edge. This ratio is a common quality measure for triangles in mesh
	 * generation, where smaller values (e.g., close to 0.5 for equilateral) are
	 * preferred, and very large values indicate "skinny" or poorly shaped
	 * triangles.
	 *
	 * @param triangle the input triangle; may be null.
	 * @return the ratio of the circumradius to the shortest edge length. Returns
	 *         {@link Double#POSITIVE_INFINITY} if:
	 *         <ul>
	 *         <li>the triangle is null or a ghost triangle.</li>
	 *         <li>the circumradius is not finite (e.g., for degenerate
	 *         triangles).</li>
	 *         <li>the shortest edge length is zero or very close to zero (<=
	 *         EPSILON).</li>
	 *         </ul>
	 */
	public static double getCircumradiusToShortestEdgeRatio(SimpleTriangle triangle) {
		if (triangle == null || triangle.isGhost()) {
			return Double.POSITIVE_INFINITY;
		}

		Circumcircle circumcircle = triangle.getCircumcircle();
		if (circumcircle == null) { // Should not happen for non-ghost SimpleTriangles from TIN
			return Double.POSITIVE_INFINITY;
		}
		double radius = circumcircle.getRadius();

		if (!Double.isFinite(radius) || radius < 0) { // Radius should be non-negative
			return Double.POSITIVE_INFINITY;
		}

		IQuadEdge shortestEdge = triangle.getShortestEdge();
		if (shortestEdge == null) {
			return Double.POSITIVE_INFINITY;
		}
		double shortestEdgeLength = shortestEdge.getLength();

		if (shortestEdgeLength <= EPSILON || !Double.isFinite(shortestEdgeLength)) {
			return Double.POSITIVE_INFINITY;
		}

		return radius / shortestEdgeLength;
	}

	/**
	 * Determines if a triangle has any interior angle smaller than θ.
	 * <p>
	 * The angle is specified by its cosine squared value {@code cos2}. For a given
	 * θ in radians, set {@code cos2 = Math.cos(θ) * Math.cos(θ)}.
	 * <p>
	 * This method checks all three angles of the triangle defined by vertices
	 * {@code A}, {@code B}, and {@code C}, and returns {@code true} if any angle is
	 * acuter ("smaller") than θ.
	 *
	 * @param t    The {@link SimpleTriangle} whose angles are to be tested.
	 * @param cos2 The threshold cosine squared value of angle θ. Should be in the
	 *             range [0, 1].
	 * @return {@code true} if the triangle has an angle < θ, otherwise
	 *         {@code false}.
	 */
	public static boolean hasAngleSmallerThanTheta(SimpleTriangle t, double cos2) {
		Vertex A = t.getVertexA(), B = t.getVertexB(), C = t.getVertexC();
		return hasAngleSmallerThanTheta(A, B, C, cos2);
	}

	/**
	 * Determines if any interior angle of a triangle formed by three vertices is
	 * smaller than a given angle θ (by comparing squared cosine values).
	 * <p>
	 * The method computes dot products between edge vectors to obtain cosines of
	 * the angles, and avoids explicit trigonometric functions for performance.
	 */
	private static boolean hasAngleSmallerThanTheta(Vertex a, Vertex b, Vertex c, double cos2) {
		// edge vectors
		double abx = b.x - a.x, aby = b.y - a.y;
		double acx = c.x - a.x, acy = c.y - a.y;
		double bcx = c.x - b.x, bcy = c.y - b.y; // needed later

		// squared edge lengths
		double ab2 = abx * abx + aby * aby;
		double ac2 = acx * acx + acy * acy;
		double bc2 = bcx * bcx + bcy * bcy;

		// quick degeneracy check
		if (ab2 < EPS2 || ac2 < EPS2 || bc2 < EPS2)
		 {
			return false; // ignore or flag as error
		}

		// angle at A
		double dotA = abx * acx + aby * acy; // AB·AC
		if (dotA > 0 && // obtuse angles cannot be "small"
				dotA * dotA > cos2 * ab2 * ac2) {
			return true;
		}

		// angle at B
		double bax = -abx, bay = -aby; // BA = -AB (no new length)
		double dotB = bax * bcx + bay * bcy; // BA·BC
		if (dotB > 0 && dotB * dotB > cos2 * ab2 * bc2) {
			return true;
		}

		// angle at C
		double cax = -acx, cay = -acy; // CA = -AC
		double cbx = -bcx, cby = -bcy; // CB = -BC
		double dotC = cax * cbx + cay * cby; // CA·CB
		if (dotC > 0 && dotC * dotC > cos2 * ac2 * bc2) {
			return true;
		}

		return false; // no angle < θ
	}
}
