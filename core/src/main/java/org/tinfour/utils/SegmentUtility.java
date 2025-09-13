package org.tinfour.utils;

import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Provides utility methods for geometric operations on line segments,
 * represented by {@link IQuadEdge} instances. These operations are particularly
 * relevant in contexts like Delaunay triangulation and mesh refinement. This
 * class is not intended to be instantiated; all methods are static.
 */
public final class SegmentUtility {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private SegmentUtility() {
		// Utility class should not be instantiated.
	}

	/**
	 * Computes the diametral circle of a given line segment. The diametral circle
	 * is defined as the smallest circle that encloses the segment, where the
	 * segment itself forms a diameter of this circle. The center of the diametral
	 * circle is the midpoint of the segment, and its radius is half the length of
	 * the segment.
	 *
	 * @param segment the line segment (an {@link IQuadEdge}) for which to compute
	 *                the diametral circle; must not be null.
	 * @return a {@link Circle} object representing the diametral circle of the
	 *         segment.
	 * @throws IllegalArgumentException if the input segment is null, or if either
	 *                                  of its endpoint vertices (A or B) is null.
	 */
	public static Circle getDiametralCircle(IQuadEdge segment) {
		if (segment == null) {
			throw new IllegalArgumentException("Input segment cannot be null.");
		}
		Vertex vA = segment.getA();
		Vertex vB = segment.getB();

		if (vA == null || vB == null) {
			throw new IllegalArgumentException("Segment endpoints cannot be null. Segment: " + segment);
		}

		double midX = (vA.getX() + vB.getX()) / 2.0;
		double midY = (vA.getY() + vB.getY()) / 2.0;
		double length = segment.getLength();

		// IQuadEdge.getLength() should ideally always return a non-negative value.
		// If length is negative (which implies an issue with the IQuadEdge
		// implementation
		// or geometric consistency), this could lead to a negative radius.
		// The Circle constructor handles negative radius by squaring it for radiusSq,
		// but a negative radius itself is anomalous.
		// For robustness, ensure radius is non-negative.
		if (length < 0) {
			throw new IllegalArgumentException("Segment length cannot be negative.");
		}
		double radius = length / 2.0;

		return new Circle(midX, midY, radius);
	}

	/**
	 * Checks if a given point "encroaches" upon a specified line segment. A point
	 * is defined as encroaching a segment if it meets two conditions:
	 * <ol>
	 * <li>The point lies strictly inside the diametral circle of the segment. The
	 * "strictness" is determined by the provided {@code tolerance}.</li>
	 * <li>The point is not one of the segment's own endpoints (A or B). Vertex
	 * identity is checked using {@link Vertex#getIndex()}.</li>
	 * </ol>
	 * This check is fundamental in algorithms like Ruppert's algorithm for Delaunay
	 * refinement, where encroached segments must be split.
	 *
	 * @param point                    the {@link Vertex} to check for encroachment;
	 *                                 must not be null.
	 * @param segment                  the {@link IQuadEdge} segment to check
	 *                                 against; must not be null.
	 * @param diametralCircleOfSegment the pre-computed diametral circle of the
	 *                                 {@code segment}, as obtained from
	 *                                 {@link #getDiametralCircle(IQuadEdge)}; must
	 *                                 not be null.
	 * @param tolerance                a small positive value used in the
	 *                                 {@link Circle#isStrictlyInside(double, double, double)}
	 *                                 check to ensure the point is not too close to
	 *                                 the boundary of the diametral circle.
	 * @return {@code true} if the point encroaches upon the segment; {@code false}
	 *         otherwise.
	 * @throws IllegalArgumentException if {@code point}, {@code segment}, or
	 *                                  {@code diametralCircleOfSegment} is null, or
	 *                                  if the segment's endpoints are null.
	 */
	public static boolean isPointEncroachingSegment(Vertex point, IQuadEdge segment, Circle diametralCircleOfSegment, double tolerance) {
		if (point == null) {
			throw new IllegalArgumentException("Input point cannot be null.");
		}
		if (segment == null) {
			throw new IllegalArgumentException("Input segment cannot be null.");
		}
		if (diametralCircleOfSegment == null) {
			throw new IllegalArgumentException("Diametral circle of segment cannot be null.");
		}

		Vertex vA = segment.getA();
		Vertex vB = segment.getB();

		// Condition 2: The point is not one of the segment's endpoints.
		// Vertex identity is checked by index.
		if (point.getIndex() == vA.getIndex() || point.getIndex() == vB.getIndex()) {
			return false; // Points that are endpoints of the segment do not encroach.
		}

		// Condition 1: The point lies strictly inside the diametral circle.
		return diametralCircleOfSegment.isStrictlyInside(point.getX(), point.getY(), tolerance);
	}

	/**
	 * Returns the nearest encroaching apex vertex of the two triangles adjacent to
	 * the given edge, or null if neither apex lies strictly inside the edge’s
	 * diametral circle (Gabriel test). Endpoints of the edge are not considered
	 * encroachers. Handles boundary edges where one apex may be absent. Assumes the
	 * mesh is (constrained) Delaunay so checking only adjacent apices is
	 * sufficient.
	 *
	 * @param edge   edge to test (must be part of the current (C)DT)
	 * @return the closest encroaching apex vertex (C or D), or null if the edge is
	 *         not encroached
	 */
	public static Vertex closestEncroacherOrNull(IQuadEdge edge) {
		Vertex A = edge.getA(), B = edge.getB();
		double mx = 0.5 * (A.getX() + B.getX());
		double my = 0.5 * (A.getY() + B.getY());
		double r2 = edge.getLengthSq() / 4; // (diameter/2) squared

		Vertex best = null;
		double bestD2 = Double.POSITIVE_INFINITY;

		IQuadEdge f = edge.getForward();
		Vertex C = f.getB();
		if (C != null) {
			double d2 = C.getDistanceSq(mx, my);
			if (d2 < bestD2) {
				bestD2 = d2;
				best = C;
			}
		}
		IQuadEdge fd = edge.getForwardFromDual();
		Vertex D = fd.getB();
		if (D != null) {
			double d2 = D.getDistanceSq(mx, my);
			if (d2 < bestD2) {
				bestD2 = d2;
				best = D;
			}
		}

		return (best != null && bestD2 < r2) ? best : null;
	}
}
