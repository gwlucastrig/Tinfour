package org.tinfour.refinement;

import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.common.Circumcircle;
import org.tinfour.common.IConstraint;
import org.tinfour.utils.TriangleUtility;
import org.tinfour.utils.SegmentUtility;
import org.tinfour.utils.Circle;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Improves mesh quality via Ruppert’s Delaunay refinement algorithm.
 * <p>
 * Ruppert’s algorithm generates high-quality triangulations by iteratively
 * improving an initial constrained Delaunay triangulation. Its primary goal is
 * to eliminate poorly-shaped triangles—specifically, triangles with angles
 * below a user-specified minimum—while also preserving boundary constraints.
 * <p>
 * The refinement process operates as follows: 1. It examines all constrained
 * segments in the mesh. If a segment is 'encroached' (meaning there is a point
 * in the mesh whose presence threatens the integrity or conformity of that
 * segment, typically when a triangle’s circumcenter lies inside or too close to
 * the segment), it splits the segment by inserting a new vertex at its
 * midpoint. 2. If no encroached segments are found, the algorithm inspects all
 * triangles in the mesh. For any triangle whose smallest angle is smaller than
 * the prescribed threshold, a new vertex is inserted at the triangle’s
 * circumcenter. This step improves the shape and angle quality of the
 * triangles. 3. After each insertion, the triangulation is updated and the
 * process repeats: the lists of problematic (encroached or poorly-shaped)
 * segments and triangles are rebuilt from scratch. 4. The refinement continues
 * until all segments are protected from encroachment, and all triangles meet
 * the minimum angle requirement.
 * <p>
 * This implementation uses a straightforward, brute-force approach by globally
 * reconstructing the lists of problematic elements after every insertion. For
 * very large or performance-critical applications, a more efficient
 * strategy—such as a priority queue or localized updates—may be preferable.
 */
public class RuppertRefiner implements DelaunayRefiner {

	static final int MAX_ITERATIONS = 5000;
	static final double DEFAULT_TOLERANCE = 1e-8;

	private final IIncrementalTin tin;
	private final double minAngleRad; // θmin in radians
	private final double beta; // β = 1 /(2 sin θmin)
	private final double cosThetaSquared;
	private final double tolerance;
	private final IIncrementalTinNavigator nav;

	private Vertex lastInsertedVertex = null;

	private double minTriangleArea = 1; // TODO
	private double minEdgeLength = 1e-3; // TODO

	Set<SimpleTriangle> badTriangles = new HashSet<>();

	/**
	 * Instantiate using circumradius-to-shortest edge ratio instead of minimum
	 * angle.
	 */
	public static RuppertRefiner fromEdgeRatio(IIncrementalTin tin, double ratio) {
		return new RuppertRefiner(tin, Math.toDegrees(Math.asin(1 / (2 * ratio))));
	}

	/**
	 * Constructs a Ruppert Delaunay mesh refiner for a given triangulation.
	 * <p>
	 * This refiner applies iterative quality improvement on a constrained Delaunay
	 * triangulation by inserting Steiner points so that all triangles meet a
	 * minimum angle constraint. The process continues until no segment is
	 * encroached and all triangles have angles greater than or equal to the
	 * specified minimum.
	 * </p>
	 *
	 * <p>
	 * <b>Termination Guarantee:</b> The refinement is provably guaranteed to
	 * terminate only if {@code minAngleDeg} ≤ 20.7°. For values above this
	 * threshold, there exist pathological inputs for which termination is not
	 * guaranteed. The minimum angle must be between 0 and 60 degrees (exclusive) to
	 * ensure meaningful refinement and to respect geometric constraints of
	 * triangulation.
	 * </p>
	 *
	 * @param tin         The incremental constrained Delaunay triangulation to
	 *                    refine. Must not be {@code null}.
	 * @param minAngleDeg The minimum permitted angle (in degrees) for any triangle
	 *                    in the mesh. Must be in the interval (0, 60). A larger
	 *                    value produces "fatter" triangles, but may endanger
	 *                    termination if the value exceeds 20.7°.
	 * @throws IllegalArgumentException if {@code tin} is {@code null}, or if
	 *                                  {@code minAngleDeg} is not in (0, 60).
	 */
	public RuppertRefiner(IIncrementalTin tin, double minAngleDeg) {
		if (tin == null) {
			throw new IllegalArgumentException("tin must not be null");
		}
		if (minAngleDeg < 0 || minAngleDeg >= 60) {
			throw new IllegalArgumentException("minAngle must be in (0,60) to ensure termination of Ruppert’s algorithm");
		}
		this.tin = tin;
		this.tolerance = DEFAULT_TOLERANCE;
		this.minAngleRad = Math.toRadians(minAngleDeg);
		this.beta = 1.0 / (2.0 * Math.sin(minAngleRad));

		double cosTheta = Math.cos(Math.toRadians(minAngleDeg));
		this.cosThetaSquared = cosTheta * cosTheta;
		nav = tin.getNavigator();
	}

	/**
	 * Executes Ruppert’s Delaunay refinement process on the current triangulation.
	 * <p>
	 * The algorithm iteratively enforces quality constraints by:
	 * <ol>
	 * <li>Identifying and splitting encroached segments at their midpoints,</li>
	 * <li>Inserting Steiner points at the circumcenters of "skinny" (low-quality)
	 * triangles,</li>
	 * <li>Repeating until all segments and triangles satisfy the minimum quality
	 * threshold,</li>
	 * <li>Or until the maximum iteration count ({@value #MAX_ITERATIONS}) is
	 * reached.</li>
	 * </ol>
	 * If convergence is not achieved within the iteration limit, a warning is
	 * printed to {@code System.err}. (An unchecked exception may optionally be
	 * thrown instead.)
	 */
	public void refine() {
		int iterations = 0;
		while (iterations++ < MAX_ITERATIONS) {
			// 1) Get all constrained segments afresh.
			List<IQuadEdge> segments = collectConstrainedSegments();

			// 2) Split any encroached segment
			final IQuadEdge enc = findEncroachedSegment(segments);
			if (enc != null) {
				splitSegment(enc);
				continue;
			}

			// 3) Otherwise, find a poor ("skinny") triangle
			final SimpleTriangle badTri = findPoorTriangle();
			if (badTri != null) {
				insertCircumcenter(badTri, segments);
				continue;
			}

			// 4) Everything is fine
			return;
		}

		String msg = "Ruppert refinement failed to converge in " + MAX_ITERATIONS + " steps.";
		throw new IllegalStateException(msg);
	}

	/** Collects all unique constrained edges in the mesh. */
	private List<IQuadEdge> collectConstrainedSegments() {
		List<IQuadEdge> result = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (IQuadEdge e : tin.getEdges()) {
			if (e.isConstrained() && e.getLength() > minEdgeLength && seen.add(e.getBaseIndex())) {
				result.add(e);
			}
		}
		return result;
	}

	/**
	 * Returns the first segment found for which any existing vertex encroaches its
	 * diametral circle.
	 */
	private IQuadEdge findEncroachedSegment(List<IQuadEdge> segments) {
		nav.resetForChangeToTin();
		for (IQuadEdge seg : segments) {
			if (seg.getLength() <= minEdgeLength) {
				continue;
			}
			Circle diam = SegmentUtility.getDiametralCircle(seg);
			var vA = seg.getA();
			var vB = seg.getB();
			double cx = (vA.getX() + vB.getX()) / 2.0;
			double cy = (vA.getY() + vB.getY()) / 2.0;

			Vertex v = nav.getNearestVertex(cx, cy);
			if (v == null) {
				// outside the TIN entirely – no chance of an encroacher
				continue;
			}

			// if the nearest vertex is inside the diametral circle, we have an
			// encroacher. Otherwise no vertex is closer, so cannot encroach.
			if (SegmentUtility.isPointEncroachingSegment(v, seg, diam, tolerance)) {
				return seg;
			}
		}
		return null;
	}

	/**
	 * Splits the given constrained segment at its midpoint and re‐inserts it into
	 * the TIN.
	 */
	private void splitSegment(IQuadEdge seg) {
		Vertex a = seg.getA();
		Vertex b = seg.getB();
		Vertex mid = new Vertex(0.5 * (a.getX() + b.getX()), 0.5 * (a.getY() + b.getY()), Double.NaN);

		// Add the midpoint to the mesh. We assume the tin impl
		// either splits the original edge or you must re-constrain.
		if (!tin.add(mid)) {
		}
		System.out.println("1. " + mid.toString());
		lastInsertedVertex = mid;

		var e = tin.getNeighborEdgeLocator().getNeigborEdge(mid.x, mid.y);
		if (!e.isConstrained()) {
			System.err.println("ERR");
		}
		SimpleTriangle t = new SimpleTriangle(tin, e);
		if (!t.getEdgeA().isConstrained() || !t.getEdgeB().isConstrained() || !t.getEdgeC().isConstrained()) {
			System.err.println("ERR");
		}

		// If your TIN doesn't auto‐reconstrain, uncomment:
		// tin.constrainEdge(a, mid);
		// tin.constrainEdge(mid, b);
	}

	/**
	 * Scans all triangles and returns one violating *either* test
	 */
	private SimpleTriangle findPoorTriangle() {
		for (SimpleTriangle t : tin.triangles()) {
			if (t.isGhost()) {
				continue;
			}
			if (!badTriangles.add(t)) {
				// seen before
				continue;
			}

			// only refine inside constrained regions (or everywhere if none)
			IConstraint rc = t.getContainingRegion();
			if (!tin.getConstraints().isEmpty() && (rc == null || !rc.definesConstrainedRegion())) {
				continue;
			}

			// Skip likely non-termination of Ruppert refinement
//			if (t.getShortestEdge().getLength() < 1e-3) {
//				continue;
//			}

			final boolean badAngle = TriangleUtility.hasAngleSmallerThanTheta(t, cosThetaSquared);

			if (lastInsertedVertex != null) {
//				System.out.println(lastInsertedCircumcenter.getDistance(t.getCircumcircle().getCircumcenter()));

			}

			if (badAngle) {
//				if (t.getArea() < minTriangleArea) {
//					continue;
//				}
				var cc = t.getCircumcircle().getCircumcenter();
				if (lastInsertedVertex != null && lastInsertedVertex.getDistance(cc) < 1e-8) {
//					System.out.println("d " + lastInsertedCircumcenter.getDistance(cc));
					// skip obvious cases causing non-termination of Ruppert refinement
					// NOTE not thorough -- insertion can oscillate between > 1 non-terminating
					// cases
					continue; // bad angle, but seen before
				}
				return t;
			}
		}
		return null;
	}

	/**
	 * Insert a triangle’s circumcenter unless it encroaches a segment, in which
	 * case we split that segment instead.
	 */
	private void insertCircumcenter(SimpleTriangle tri, List<IQuadEdge> segments) {
		tri.getEdgeA();
		Circumcircle cc = tri.getCircumcircle();
		if (cc == null) {
			return;
		}

		Vertex center = cc.getCircumcenter();

		// If this point encroaches any segment, split that instead
		for (IQuadEdge seg : segments) {
			Circle diam = SegmentUtility.getDiametralCircle(seg);
			if (SegmentUtility.isPointEncroachingSegment(center, seg, diam, tolerance)) {
				splitSegment(seg);
				return;
			}
		}

		// Safe to insert the circumcenter
		System.out.println(center.toString());
//		System.out.println(tri.getShortestEdge().getLength());
//		System.out.println(tri.getShortestEdge().getLength() < 1e-3);
		if (!tin.add(center)) {
//			System.out.println(tri.hashCode());
//			System.out.println(tri.getIndex());
//			System.out.println("2. " + center.toString());
			// vertex HAS been seen before!
			// TODO need to add this triangle to a "bad" pool
		} else {
			lastInsertedVertex = center;
			if (tri.getArea() == 0) {
				System.out.println(tri.toString()); // WHY?
			}
		}
	}

	/**
	 * 
	 * Insert Shewchuk's “off-centre” point for a bad triangle.
	 * 
	 * The point is the circum-centre C unless the distance from the
	 * 
	 * midpoint of the shortest edge to C exceeds β·|e|, in which case
	 * 
	 * it is moved towards the edge until that distance is β·|e|.
	 * 
	 * (β = 1 /(2 sin θmin).)
	 */
	private void insertOffcentre(SimpleTriangle tri, List<IQuadEdge> segments) {
		// ------------------------------------------------------------
		// 1. Identify the shortest edge e = pq and the opposite
//	     vertex  r.  Also keep its length |e|.
		// ------------------------------------------------------------
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

		// ------------------------------------------------------------
		// 2. Mid-point m and unit vector n of the perpendicular
//	     bisector, oriented toward  r  (i.e. inside the triangle).
		// ------------------------------------------------------------
		double mx = 0.5 * (p.getX() + q.getX());
		double my = 0.5 * (p.getY() + q.getY());

		double nx = q.getY() - p.getY(); // rotate (q-p) by +90°
		double ny = -q.getX() + p.getX();
		double nlen = Math.hypot(nx, ny);
		nx /= nlen;
		ny /= nlen;

		// make n point toward r
		if ((r.getX() - mx) * nx + (r.getY() - my) * ny < 0) {
			nx = -nx;
			ny = -ny;
		}

		// ------------------------------------------------------------
		// 3. Distance from m to the circum-centre of the triangle.
		// ------------------------------------------------------------
		Circumcircle cc = tri.getCircumcircle();
		double cx = cc.getX();
		double cy = cc.getY();
		double dCirc = Math.hypot(cx - mx, cy - my);

		// ------------------------------------------------------------
		// 4. Desired distance d guaranteed not to encroach e.
//	     d = min(dCirc, β · |e|)       (Shewchuk 2002)
		// ------------------------------------------------------------
		double d = Math.min(dCirc, beta * len);

		// ------------------------------------------------------------
		// 5. The off-centre point.
		// ------------------------------------------------------------
		double ox = mx + nx * d;
		double oy = my + ny * d;
		Vertex off = new Vertex(ox, oy, 0.0);

		// ------------------------------------------------------------
		// 6. If the point encroaches a constrained segment, split that
//	     segment instead (standard Ruppert rule).
		// ------------------------------------------------------------
		for (IQuadEdge seg : segments) {
			Circle diam = SegmentUtility.getDiametralCircle(seg);
			if (SegmentUtility.isPointEncroachingSegment(off, seg, diam, tolerance)) {
				splitSegment(seg);
				return;
			}
		}

		// ------------------------------------------------------------
		// 7. Safe: insert the off-centre.
		// ------------------------------------------------------------
		if (lastInsertedVertex != null) {
			System.out.println(lastInsertedVertex.getDistance(off) < 1e-5);

		}
		System.out.println(off.toString());
		tin.add(off);
		lastInsertedVertex = off;
	}
}
