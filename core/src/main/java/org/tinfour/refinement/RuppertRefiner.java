package org.tinfour.refinement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tinfour.common.Circumcircle;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.utils.Circle;
import org.tinfour.utils.SegmentUtility;
import org.tinfour.utils.TriangleUtility;

/**
 * Improves mesh quality via Ruppert’s Delaunay refinement algorithm.
 * <p>
 * Ruppert’s algorithm generates high-quality triangulations by iteratively
 * improving an initial constrained Delaunay triangulation. Its primary goal is
 * to eliminate poorly-shaped triangles (specifically, triangles with angles
 * below a user-specified minimum).
 */
public class RuppertRefiner implements DelaunayRefiner {

	// Iteration cap
	private static final int MAX_ITERATIONS = 1000;

	// Relative tolerances
	// Encroachment test tolerance is scaled by segment length.
	private static final double ENCROACH_REL_TOL = 1e-6;
	// Near-vertex/edge tolerances are scaled by a local length.
	private static final double NEAR_VERTEX_REL_TOL = 1e-6;
	private static final double NEAR_EDGE_REL_TOL = 1e-8;

	private final IIncrementalTin tin;
	private final IIncrementalTinNavigator nav;

	private final double minAngleRad; // θmin in radians
	private final double cosThetaSquared; // cos^2(θmin)
	private final double beta; // β = 1 / (2 sin θmin)

	private final double minTriangleArea;

	private Vertex lastInsertedVertex = null;

	/**
	 * Instantiates the refiner using <i>circumradius-to-shortest-edge</i> ratio
	 * instead of minimum angle.
	 */
	public static RuppertRefiner fromEdgeRatio(IIncrementalTin tin, double ratio) {
		if (ratio <= 0) {
			throw new IllegalArgumentException("ratio must be > 0");
		}
		double minAngleDeg = Math.toDegrees(Math.asin(1.0 / (2.0 * ratio)));
		return new RuppertRefiner(tin, minAngleDeg);
	}

	/**
	 * Constructs a Ruppert refiner.
	 *
	 * <p>
	 * Note: theoretical termination is guaranteed for minAngleDeg <= 20.7° with
	 * standard circumcenters; off-centers improve behavior for larger angles, but
	 * pathological inputs can still defeat termination.
	 */
	public RuppertRefiner(IIncrementalTin tin, double minAngleDeg) {
		if (tin == null) {
			throw new IllegalArgumentException("tin must not be null");
		}
		if (!(minAngleDeg > 0 && minAngleDeg < 60)) {
			throw new IllegalArgumentException("minAngle must be in (0,60)");
		}
		this.tin = tin;
		this.nav = tin.getNavigator();

		this.minAngleRad = Math.toRadians(minAngleDeg);
		double cosTheta = Math.cos(minAngleRad);
		this.cosThetaSquared = cosTheta * cosTheta;
		this.beta = 1.0 / (2.0 * Math.sin(minAngleRad));

		this.minTriangleArea = tin.getNominalPointSpacing() / 100;
	}

	/**
	 * Executes Ruppert refinement.
	 */
	@Override
	public void refine() {
		int iterations = 0;

		while (iterations++ < MAX_ITERATIONS) {

			// 1) Gather all constrained segments afresh (no length filter; do not skip line
			// members).
			List<IQuadEdge> segments = collectConstrainedSegments();

			// 2) Split any encroached constrained segment.
			IQuadEdge enc = findEncroachedSegment(segments);
			if (enc != null) {
				splitSegment(enc);
				continue;
			}

			// 3) Otherwise, pick the largest skinny triangle and refine with off-center.
			SimpleTriangle bad = findLargestPoorTriangle();
			if (bad != null) {
				insertOffcenterOrSplit(bad, segments);
				continue;
			}

			// 4) No encroached segments; no skinny triangles.
			return;
		}

		System.err.println("Ruppert refinement: reached max iterations without convergence (" + MAX_ITERATIONS + ").");
	}

	/**
	 * Collects all constrained subsegments of the current TIN (uses base edges).
	 */
	private List<IQuadEdge> collectConstrainedSegments() {
		List<IQuadEdge> result = new ArrayList<>();
		// Deduplicate by baseIndex (assumes DCEL-style half-edges share base index).
		Set<Integer> seenBase = new HashSet<>();
		for (IQuadEdge e : tin.getEdges()) {
			if (!e.isConstrained()) {
				continue;
			}
			if (seenBase.add(e.getBaseIndex())) {
				result.add(e);
			}
		}
		return result;
	}

	/**
	 *
	 * Scans the constrained subsegments and returns one whose diametral circle is
	 * encroached by an existing vertex. The encroachment test is scale-aware: a
	 * small relative tolerance is applied proportional to the segment length. A
	 * global nearest-vertex query to the segment midpoint is used as a certificate:
	 * if any vertex lies strictly inside the diametral circle, then the nearest
	 * vertex to the midpoint must also lie inside that circle.
	 *
	 * @param segments the constrained subsegments to test
	 * @return an encroached subsegment if found; null if none are encroached
	 */
	private IQuadEdge findEncroachedSegment(List<IQuadEdge> segments) {
		for (IQuadEdge seg : segments) {
			// Diametral circle center is the midpoint of the segment.
			Circle diam = SegmentUtility.getDiametralCircle(seg);

			Vertex a = seg.getA();
			Vertex b = seg.getB();
			double cx = 0.5 * (a.getX() + b.getX());
			double cy = 0.5 * (a.getY() + b.getY());

			Vertex nearest = nav.getNearestVertex(cx, cy);
			if (nearest == null) {
				continue;
			}

			double localTol = ENCROACH_REL_TOL * seg.getLength();
			if (SegmentUtility.isPointEncroachingSegment(nearest, seg, diam, localTol)) {
				return seg;
			}
		}
		return null;
	}

	/**
	 *
	 * Splits a constrained subsegment by inserting its midpoint into the TIN.
	 *
	 * @param seg the constrained subsegment to split
	 */
	private void splitSegment(IQuadEdge seg) {
		Vertex a = seg.getA();
		Vertex b = seg.getB();
		Vertex mid = new Vertex(0.5 * (a.getX() + b.getX()), 0.5 * (a.getY() + b.getY()), Double.NaN);

		tin.add(mid);
		lastInsertedVertex = mid;
	}

	/**
	 * 
	 * Finds the largest skinny triangle that violates the minimum-angle criterion.
	 * 
	 * @return the largest-area skinny triangle found, or {@code null} if none
	 *         violate the angle bound.
	 */
	private SimpleTriangle findLargestPoorTriangle() {
		SimpleTriangle largest = null;
		double largestArea = -1.0;

		boolean hasAnyConstraints = !tin.getConstraints().isEmpty();

		for (SimpleTriangle t : tin.triangles()) {
			if (t.isGhost()) {
				continue;
			}

			// Refine only inside constrained regions if there are any.
			if (hasAnyConstraints) {
				IConstraint rc = t.getContainingRegion();
				if (rc == null || !rc.definesConstrainedRegion()) {
					continue;
				}
			}

			if (!TriangleUtility.hasAngleSmallerThanTheta(t, cosThetaSquared)) {
				continue;
			}

			// Optional: do not retry endlessly the exact same triangle object
			// if we've already seen it. Comment out if too aggressive.

			double area = t.getArea();
			if (area > minTriangleArea && area > largestArea) {
				largestArea = area;
				largest = t;
			}
		}
		return largest;
	}

	/**
	 * 
	 * Inserts Shewchuk’s off-center point for a skinny triangle, or splits an
	 * encroached constrained segment instead.
	 * <p>
	 * Given a bad triangle:
	 * <ol>
	 * <li>Identify its shortest edge e = pq and the opposite vertex r.</li>
	 * <li>Compute the edge midpoint m and the unit normal n of the perpendicular
	 * bisector oriented toward r.</li>
	 * <li>Compute the triangle’s circumcenter C and the distance dCirc = |mC|.</li>
	 * <li>Compute the target distance d = min(dCirc, β|e|) where β = 1/(2 sin
	 * θmin).</li>
	 * <li>Place the off-center at m + d n.</li>
	 * <li>If the off-center encroaches any constrained subsegment, split that
	 * subsegment (Ruppert rule) instead of inserting the point.</li>
	 * <li>Otherwise, guard against placing the point too close to an existing
	 * vertex or nearly on the interior of a constrained edge using scale-aware
	 * tolerances, and insert it if safe.</li>
	 * <li>If the perpendicular bisector is undefined or the circumcircle is not
	 * finite, fall back to the circumcenter path.</li>
	 * </ol>
	 * </p>
	 * <p>
	 * Tolerances:
	 * <ul>
	 * <li>Encroachment tests use a threshold proportional to the tested segment
	 * length.</li>
	 * <li>Near-vertex and near-edge interior checks use thresholds proportional to
	 * the triangle’s shortest edge length.</li>
	 * </ul>
	 * </p>
	 * 
	 * @param tri      the skinny triangle to improve.
	 * @param segments the current snapshot of constrained subsegments (used for
	 *                 encroachment checks).
	 */
	private void insertOffcenterOrSplit(SimpleTriangle tri, List<IQuadEdge> segments) {
		// 1) Identify shortest edge e = pq with opposite r
		Vertex a = tri.getVertexA();
		Vertex b = tri.getVertexB();
		Vertex c = tri.getVertexC();

		Vertex p = a, q = b, r = c;
		double ab2 = a.getDistanceSq(b);
		double bc2 = b.getDistanceSq(c);
		double ca2 = c.getDistanceSq(a);

		if (bc2 < ab2 && bc2 <= ca2) {
			p = b;
			q = c;
			r = a;
		} else if (ca2 < ab2 && ca2 <= bc2) {
			p = c;
			q = a;
			r = b;
		}

		double len = Math.sqrt(p.getDistanceSq(q)); // |e|

		// 2) Midpoint m and unit normal n of the perpendicular bisector, pointing
		// toward r
		double mx = 0.5 * (p.getX() + q.getX());
		double my = 0.5 * (p.getY() + q.getY());

		double nx = q.getY() - p.getY();
		double ny = -(q.getX() - p.getX());
		double nlen = Math.hypot(nx, ny);
		if (nlen == 0) {
			// Degenerate edge; fallback to circumcenter.
			insertCircumcenterOrSplit(tri, segments);
			return;
		}
		nx /= nlen;
		ny /= nlen;

		// Orient n toward r (inside the triangle)
		if ((r.getX() - mx) * nx + (r.getY() - my) * ny < 0) {
			nx = -nx;
			ny = -ny;
		}

		// 3) Circumcenter C and distance from m to C
		Circumcircle cc = tri.getCircumcircle();
		if (cc == null || !cc.isFinite()) {
			// Fallback: split shortest constrained segment if any encroached; else try
			// circumcenter insert
			insertCircumcenterOrSplit(tri, segments);
			return;
		}
		double cx = cc.getX();
		double cy = cc.getY();
		double dCirc = Math.hypot(cx - mx, cy - my);

		// 4) Desired distance (Shewchuk): d = min(dCirc, β|e|)
		double d = Math.min(dCirc, beta * len);

		// 5) Off-center point
		double ox = mx + nx * d;
		double oy = my + ny * d;
		Vertex off = new Vertex(ox, oy, Double.NaN);

		// 6) If off-center encroaches any constrained segment, split that segment
		IQuadEdge enc = firstEncroachedByPoint(off, segments);
		if (enc != null) {
			splitSegment(enc);
			return;
		}

		// 7) Guard against near-duplicate insertion and near-edge placement
		double localScale = Math.max(1e-12, len); // use shortest edge length as scale
		double nearVertexTol = NEAR_VERTEX_REL_TOL * localScale;
		double nearEdgeTol = NEAR_EDGE_REL_TOL * localScale;

		if (lastInsertedVertex != null) {
			if (lastInsertedVertex.getDistance(off) <= nearVertexTol) {
				// Avoid immediate oscillation on the same point
				return;
			}
		}

		// Snap/skip if too close to existing vertex
		Vertex nearest = nav.getNearestVertex(ox, oy);
		if (nearest != null && nearest.getDistance(off) <= nearVertexTol) {
			// Too close to an existing vertex; skip this insertion.
			// We rely on other refinements to change local geometry.
			return;
		}

		// Avoid placing the point nearly on the interior of a constrained edge
		IQuadEdge nearEdge = firstNearConstrainedEdgeInterior(off, segments, nearEdgeTol);
		if (nearEdge != null) {
			// Treat as encroachment to be safe
			splitSegment(nearEdge);
			return;
		}

		// 8) Safe: insert the off-center
		tin.add(off);
		lastInsertedVertex = off;

	}

	/**
	 * 
	 * Inserts a triangle’s circumcenter unless it would encroach a constrained
	 * subsegment, in which case that subsegment is split instead.
	 * <p>
	 * This is a fallback path used when off-center construction is degenerate or
	 * not applicable. The circumcenter candidate is screened as follows:
	 * <ul>
	 * <li>If it encroaches any constrained subsegment, split that subsegment.</li>
	 * <li>If it lies too close to an existing vertex (scale-aware threshold), skip
	 * insertion to avoid creating degenerate elements.</li>
	 * <li>If it lies within a small tolerance of the interior of a constrained
	 * edge, treat as encroachment and split the edge.</li>
	 * <li>Otherwise, insert the point into the TIN.</li>
	 * </ul>
	 * </p>
	 * 
	 * @param tri      the skinny triangle whose circumcenter is considered.
	 * @param segments the current snapshot of constrained subsegments (used for
	 *                 encroachment checks).
	 */
	private void insertCircumcenterOrSplit(SimpleTriangle tri, List<IQuadEdge> segments) {
		Circumcircle cc = tri.getCircumcircle();
		if (cc == null || !cc.isFinite()) {
			return;
		}

		Vertex center = cc.getCircumcenter();

		// If encroaches any constrained segment, split that instead
		IQuadEdge enc = firstEncroachedByPoint(center, segments);
		if (enc != null) {
			splitSegment(enc);
			return;
		}

		// Near-duplicate guard
		double localScale = Math.max(1e-12, tri.getShortestEdge().getLength());
		double nearVertexTol = NEAR_VERTEX_REL_TOL * localScale;
		Vertex nearest = nav.getNearestVertex(center.getX(), center.getY());
		if ((nearest != null && nearest.getDistance(center) <= nearVertexTol)
				|| (lastInsertedVertex != null && lastInsertedVertex.getDistance(center) <= nearVertexTol)) {
			return;
		}

		// Near constrained edge interior guard
		double nearEdgeTol = NEAR_EDGE_REL_TOL * localScale;
		IQuadEdge nearEdge = firstNearConstrainedEdgeInterior(center, segments, nearEdgeTol);
		if (nearEdge != null) {
			splitSegment(nearEdge);
			return;
		}

		if (tin.add(center)) {
			lastInsertedVertex = center;
		} else {
			lastInsertedVertex = center;
		}
	}

	/**
	 * 
	 * Returns the first constrained subsegment encroached by a given point.
	 * <p>
	 * For each constrained subsegment, the method constructs its diametral circle
	 * and checks whether the point lies strictly inside it, using a tolerance
	 * proportional to the subsegment length. The first encroached subsegment is
	 * returned.
	 * </p>
	 * 
	 * @param p        the candidate point to test.
	 * @param segments the constrained subsegments to check.
	 * @return the first encroached subsegment, or {@code null} if none are
	 *         encroached.
	 */
	private IQuadEdge firstEncroachedByPoint(Vertex p, List<IQuadEdge> segments) {
		for (IQuadEdge seg : segments) {
			Circle diam = SegmentUtility.getDiametralCircle(seg);
			double localTol = ENCROACH_REL_TOL * seg.getLength();
			if (SegmentUtility.isPointEncroachingSegment(p, seg, diam, localTol)) {
				return seg;
			}
		}
		return null;
	}

	/**
	 * 
	 * Finds the first constrained subsegment whose open interior lies within a
	 * given distance of a point.
	 * <p>
	 * The method projects the point orthogonally onto each constrained subsegment
	 * and checks whether the projection parameter lies strictly between 0 and 1
	 * (i.e., within the open segment, excluding endpoints). If so, it computes the
	 * perpendicular distance from the point to the segment. If the distance is less
	 * than or equal to the supplied tolerance, the subsegment is returned as
	 * “near.” This is used as a safeguard to avoid inserting points nearly on
	 * constrained edges, which can produce degenerate elements.
	 * </p>
	 * <p>
	 * The tolerance should be scale-aware (e.g., proportional to a local length
	 * like the shortest edge of the triangle being refined).
	 * </p>
	 * 
	 * @param v        the point to test.
	 * @param segments the constrained subsegments to consider.
	 * @param tol      the maximum permitted perpendicular distance to a segment’s
	 *                 interior.
	 * @return the first subsegment whose interior is within {@code tol} of the
	 *         point; {@code null} if none.
	 */
	private IQuadEdge firstNearConstrainedEdgeInterior(Vertex v, List<IQuadEdge> segments, double tol) {
		double px = v.getX();
		double py = v.getY();
		for (IQuadEdge seg : segments) {
			Vertex a = seg.getA();
			Vertex b = seg.getB();
			double ax = a.getX(), ay = a.getY();
			double bx = b.getX(), by = b.getY();

			double vx = bx - ax;
			double vy = by - ay;
			double wx = px - ax;
			double wy = py - ay;

			double vv = vx * vx + vy * vy;
			if (vv == 0) {
				continue;
			}

			// Projection parameter t of P onto AB
			double t = (wx * vx + wy * vy) / vv;
			if (t <= 0 || t >= 1) {
				// Near an endpoint: we allow it (the add() guard will catch duplicates).
				continue;
			}

			// Distance from P to the line AB
			double projx = ax + t * vx;
			double projy = ay + t * vy;
			double dist = Math.hypot(px - projx, py - projy);
			if (dist <= tol) {
				return seg;
			}
		}
		return null;
	}
}
