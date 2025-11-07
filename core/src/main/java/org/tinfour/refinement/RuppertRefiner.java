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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.tinfour.common.Circumcircle;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IIncrementalTinNavigator;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.interpolation.TriangularFacetSpecialInterpolator;

/**
 * RuppertRefiner implements Ruppert’s Delaunay refinement for improving mesh
 * quality of an {@link IIncrementalTin}.
 *
 * <p>
 * The refiner iteratively refines poor-quality triangles by inserting Steiner
 * points (Shewchuk-style off-centers with circumcenter fallback) and by
 * splitting encroached constrained subsegments. Several practical safeguards
 * are included to avoid pathological infinite refinement:
 * <ul>
 * <li>radius-edge gating (configurable; optionally enforces ρ ≥ √2);</li>
 * <li>concentric-shell segment tagging to enable robust off-center / midpoint
 * handling;</li>
 * <li>identification of <em>seditious</em> edges (midpoints on the same shell
 * about a critical corner) and optional skipping/ignoring of these in
 * split/encroachment decisions to prevent ping-pong cascades;</li>
 * <li>scale-aware tolerances for encroachment, near-vertex and near-edge checks
 * to avoid degenerate insertions.</li>
 * </ul>
 *
 * <p>
 * Features and guarantees:
 * <ul>
 * <li>The refiner is driven by a minimum-angle (or equivalently a
 * circumradius-to-shortest-edge ratio) goal supplied at construction. When
 * configured to enforce the √2 guard, the implementation follows standard
 * termination theory (Ruppert/Shewchuk) and mitigates many non-adversarial
 * inputs.</li>
 * <li>Concentric-shell and seditious-edge logic extend robustness to small
 * corner cases commonly observed in practice.</li>
 * <li>By default the algorithm uses off-center insertion (Shewchuk) and splits
 * constrained segments when required by encroachment rules.</li>
 * <li>Terminated either when no encroached subsegments and no poor triangles
 * remain, or when an iteration cap is reached.</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>J. Ruppert, "A Delaunay Refinement Algorithm for Quality 2-Dimensional
 * Mesh Generation", J. Algorithms (1995).</li>
 * <li>J. R. Shewchuk, "Delaunay Refinement Mesh Generation", 1997.</li>
 * </ul>
 *
 * @author Michael Carleton
 */
public class RuppertRefiner implements IDelaunayRefiner {

	private static final double DEFAULT_MIN_TRIANGLE_AREA = 1e-3;

	// Relative tolerances
	private static final double NEAR_VERTEX_REL_TOL = 1e-9;
	private static final double NEAR_EDGE_REL_TOL = 1e-9;

	// Shells and corner handling
	private static final double SHELL_BASE = 2.0;
	private static final double SHELL_EPS = 1e-9;
	/**
	 * Threshold, in degrees, for classifying a constrained vertex as a "small
	 * corner".
	 * <p>
	 * At each constrained vertex, we consider the smaller of the two angles between
	 * its incident constrained segments. If that smaller angle is less than
	 * <code>SMALL_CORNER_DEG</code>, the corner is treated as “critical.”
	 * </p>
	 *
	 * <h3>Why this matters:</h3>
	 * <ul>
	 * <li>Very small angles can trigger endless "ping-pong" refinement: splitting
	 * an encroached subsegment creates another encroachment or skinny triangle,
	 * which causes another split, and so on, producing ever shorter edges.</li>
	 * <li>When a corner is critical, the refiner applies special safeguards:
	 * <ol>
	 * <li>Midpoint splits are organized on concentric “shells” around the corner.
	 * </li>
	 * <li>Edges joining midpoints on the same shell are marked “seditious.”</li>
	 * <li>The refiner may skip splitting skinny triangles whose shortest edge is
	 * seditious and may ignore encroachments that would recreate the ping-pong.
	 * </li>
	 * </ol>
	 * These rules stop the cascade while allowing refinement elsewhere.</li>
	 * </ul>
	 *
	 * <h3>Recommended value:</h3>
	 * <ul>
	 * <li>60° is a safe, conservative default (following Shewchuk). It reliably
	 * prevents cascades in practice.</li>
	 * <li>Larger values can over-suppress refinement near ordinary corners.</li>
	 * </ul>
	 */
	private static final double SMALL_CORNER_DEG = 60.0;
	private static final double SQRT2 = Math.sqrt(2.0);

	private final IIncrementalTin tin;

	private final double minAngleRad;
	private final double beta; // 1/(2 sin θmin)
	private final double rhoTarget; // 1/(2 sin θmin)
	private final double rhoMin; // possibly clamped to √2

	private final double minTriangleArea;

	private final boolean skipSeditiousTriangles;
	private final boolean ignoreSeditiousEncroachments;

	private Vertex lastInsertedVertex = null;

	private final Map<Vertex, VData> vdata = new IdentityHashMap<>();

	private Map<Vertex, CornerInfo> cornerInfo = new IdentityHashMap<>();

	private final IIncrementalTinNavigator navigator;
	private final TriangularFacetSpecialInterpolator interpolator;

	private int vertexIndexer;

	/**
	 * Creates a RuppertRefiner configured by a target circumradius-to-shortest-edge
	 * ratio {@code ratio}.
	 *
	 * <p>
	 * This factory computes the equivalent minimum angle from {@code ratio} (via
	 * {@code θ = arcsin(1/(2·ratio))}) and delegates to the primary constructor.
	 * The created refiner will attempt to eliminate triangles whose
	 * circumradius-to-shortest-edge ratio meets or exceeds the implied target.
	 *
	 * @param tin   the incremental TIN to refine; must be bootstrapped and non-null
	 * @param ratio the target circumradius-to-shortest-edge ratio (must be &gt; 0)
	 * @return a configured {@code RuppertRefiner}
	 * @throws IllegalArgumentException if {@code tin} is null or not bootstrapped,
	 *                                  or if {@code ratio <= 0}
	 */
	public static RuppertRefiner fromEdgeRatio(final IIncrementalTin tin, final double ratio) {
		if (ratio <= 0) {
			throw new IllegalArgumentException("ratio must be > 0");
		}
		final double minAngleDeg = Math.toDegrees(Math.asin(1.0 / (2.0 * ratio)));
		return new RuppertRefiner(tin, minAngleDeg);
	}

	/**
	 * Constructs a RuppertRefiner with the requested minimum internal triangle
	 * angle.
	 *
	 * <p>
	 * The refiner will try to remove triangles with angles smaller than
	 * {@code minAngleDeg}. The implementation uses off-center insertion with
	 * circumcenter fallback, encroachment checks for constrained subsegments, and
	 * several practical safeguards (concentric-shell tagging and seditious-edge
	 * handling) to prevent pathological infinite refinement loops.
	 * </p>
	 *
	 * <p>
	 * <strong>Parameters and preconditions</strong>:
	 * </p>
	 * <ul>
	 * <li>{@code tin} must be non-null and bootstrapped (already built).</li>
	 * <li>{@code minAngleDeg} must be in (0, 60) degrees; values near the
	 * theoretical limits may still fail to terminate.</li>
	 * </ul>
	 *
	 * @param tin         the incremental triangulation to refine (non-null,
	 *                    bootstrapped)
	 * @param minAngleDeg the requested minimum angle in degrees (0 &lt; θ &lt; 60)
	 * @throws IllegalArgumentException on invalid inputs
	 */
	public RuppertRefiner(final IIncrementalTin tin, final double minAngleDeg) {
		this(tin, minAngleDeg, DEFAULT_MIN_TRIANGLE_AREA);
	}

	/**
	 * Constructs a RuppertRefiner with the requested minimum internal triangle
	 * angle and a user-specified minimum triangle area threshold.
	 *
	 * <p>
	 * The {@code minTriangleArea} is used as a conservative safeguard to avoid
	 * attempting to refine triangles whose area is extremely small (in the
	 * coordinate units squared). A value of 0 disables the area-based skipping.
	 * </p>
	 *
	 * @param tin             the incremental triangulation to refine (non-null,
	 *                        bootstrapped)
	 * @param minAngleDeg     the requested minimum angle in degrees (0 &lt; θ &lt;
	 *                        60)
	 * @param minTriangleArea area threshold for skipping refinement of very small
	 *                        triangles (must be &ge; 0)
	 * @throws IllegalArgumentException on invalid inputs
	 */
	public RuppertRefiner(final IIncrementalTin tin, final double minAngleDeg, final double minTriangleArea) {
		this(tin, minAngleDeg, minTriangleArea, false, true, true);
	}

	/**
	 * Constructs a RuppertRefiner with explicit runtime policy options.
	 *
	 * <p>
	 * This overload exposes three boolean options useful for production tuning:
	 * </p>
	 * <ul>
	 * <li>{@code enforceSqrt2Guard} — when {@code true}, the internal radius-edge
	 * gating enforces {@code ρ ≥ √2} (Shewchuk/Ruppert termination guard).</li>
	 * <li>{@code skipSeditiousTriangles} — when {@code true}, triangles whose
	 * shortest edges have been marked seditious are not attempted for split.</li>
	 * <li>{@code ignoreSeditiousEncroachments} — when {@code true}, encroachments
	 * identified as seditious are ignored (prevents ping-pong splitting).</li>
	 * </ul>
	 *
	 * @param tin                          the incremental triangulation to refine
	 *                                     (non-null, bootstrapped)
	 * @param minAngleDeg                  the requested minimum angle in degrees (0
	 *                                     &lt; θ &lt; 60)
	 * @param minTriangleArea              area threshold for skipping very small
	 *                                     triangles (must be &ge; 0)
	 * @param enforceSqrt2Guard            whether to force the ρ &ge; √2 termination
	 *                                     guard
	 * @param skipSeditiousTriangles       whether to skip splitting triangles whose
	 *                                     shortest edges are seditious
	 * @param ignoreSeditiousEncroachments whether to ignore seditious encroachments
	 * @throws IllegalArgumentException on invalid inputs
	 */
	public RuppertRefiner(final IIncrementalTin tin, final double minAngleDeg, final double minTriangleArea, final boolean enforceSqrt2Guard,
			final boolean skipSeditiousTriangles, final boolean ignoreSeditiousEncroachments) {
		if (tin == null) {
			throw new IllegalArgumentException("tin must not be null");
		}
		if (!tin.isBootstrapped()) {
			throw new IllegalArgumentException("tin is not properly constructed");
		}
		if (!(minAngleDeg > 0 && minAngleDeg < 60)) {
			throw new IllegalArgumentException("minAngle must be in (0,60)");
		}
		if (!Double.isFinite(minTriangleArea) || minTriangleArea < 0.0) {
			throw new IllegalArgumentException("minTriangleArea must be finite and >= 0");
		}

		this.tin = tin;
        this.interpolator = new TriangularFacetSpecialInterpolator(tin);
		this.minAngleRad = Math.toRadians(minAngleDeg);
		final double sinT = Math.sin(minAngleRad);
		this.beta = 1.0 / (2.0 * sinT);
		this.rhoTarget = 1.0 / (2.0 * sinT);

		this.skipSeditiousTriangles = skipSeditiousTriangles;
		this.ignoreSeditiousEncroachments = ignoreSeditiousEncroachments;

		this.rhoMin = enforceSqrt2Guard ? Math.max(SQRT2, rhoTarget) : rhoTarget;

		this.minTriangleArea = minTriangleArea;

		for (final Vertex v : tin.vertices()) {
			vdata.put(v, new VData(VType.INPUT, null, 0));
		}

		navigator = tin.getNavigator();
		cornerInfo = buildCornerInfo();

		// The vertex index is strictly for diagnostic purposes.
		int maxIndex = 0;
		for(Vertex v: tin.vertices()){
			if(v.getIndex()>maxIndex){
				maxIndex = v.getIndex();
			}
		}
		this.vertexIndexer = maxIndex+1;
	}

	/**
	 * Perform refinement on the supplied {@link IIncrementalTin}.
	 *
	 * <p>
	 * This method runs Ruppert’s iterative loop:
	 * </p>
	 * <ol>
	 * <li>collect constrained segments;</li>
	 * <li>split an encroached constrained segment if any;</li>
	 * <li>otherwise find a largest bad triangle and attempt an off-center insert
	 * (circumcenter fallback) or split an encroached segment found while testing
	 * the candidate;</li>
	 * <li>repeat until no encroached segments and no bad triangles remain or an
	 * iteration cap is reached.</li>
	 * </ol>
	 *
	 * <p>
	 * The method is re-entrant in the sense it mutates the supplied {@code tin}; it
	 * returns true when refinement converges or false if the maximum iteration cap
	 * is hit.
	 * </p>
	 */
	@Override
	public boolean refine() {
		// ~100 refinements per input triangle
		// very generous cap -- not intended to be hit!
		final int maxIterations = vdata.size() * 2 * 100;
		int iterations = 0;

		while (iterations++ < maxIterations) {
			final var vLast = refineOnce();
			if (vLast == null) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Vertex refineOnce() {
		final List<IQuadEdge> segments = collectConstrainedSegments();

		final IQuadEdge enc = findEncroachedSegment(segments);
		if (enc != null) {
			return splitSegmentSmart(enc);

		}

		final SimpleTriangle bad = findLargestPoorTriangle(segments);
		if (bad != null) {
			return insertOffcenterOrSplit(bad, segments);

		}
		return null;
	}

	/**
	 * Collects constrained subsegments from the current TIN.
	 *
	 * <p>
	 * Returns a snapshot list of {@link IQuadEdge} whose {@code isConstrained()}
	 * predicate is true. The list is used by encroachment and near-edge searches.
	 * </p>
	 *
	 * @return modifiable list of constrained subsegments
	 */
	private List<IQuadEdge> collectConstrainedSegments() {
		final List<IQuadEdge> result = new ArrayList<>();
		for (final IQuadEdge e : tin.edges()) { // .edges() returns only base edges
			if (e.isConstrained()) {
				result.add(e);
			}
		}
		return result;
	}

	/**
	 * Scan segments and return one encroached segment, or {@code null}.
	 *
	 * <p>
	 * The test is scale-aware: the encroachment tolerance is proportional to the
	 * segment length. A nearest-neighbour query at the segment midpoint is used as
	 * a fast certificate of encroachment; the method validates the certificate with
	 * the precise diametral-circle test.
	 * </p>
	 *
	 * @param segments a snapshot list of constrained subsegments to inspect
	 * @return an encroached {@link IQuadEdge} or {@code null} if none found
	 */
	private IQuadEdge findEncroachedSegment(final List<IQuadEdge> segments) {
		for (final IQuadEdge seg : segments) {
			// NOTE validity of this (fast) test requires Delaunay integrity
			final Vertex enc = closestEncroacherOrNull(seg);
			if (enc != null) {
				if (ignoreSeditiousEncroachments && shouldIgnoreEncroachment(seg, enc)) {
					continue;
				}
				return seg;
			}
		}
		return null;
	}

	/**
	 * Find the largest-area triangle whose quality violates the requested bound.
	 *
	 * <p>
	 * The implementation uses a radius-to-shortest-edge ratio test (equivalently
	 * the minimum-angle bound). Triangles marked as ghosts or outside constrained
	 * regions (when any constraints exist) are ignored. Optionally skips triangles
	 * whose shortest edge is seditious.
	 * </p>
	 *
	 * @param segments snapshot of constrained subsegments (used by heuristics)
	 * @return a {@link SimpleTriangle} chosen for refinement, or {@code null}
	 */
	private SimpleTriangle findLargestPoorTriangle(final List<IQuadEdge> segments) {
		SimpleTriangle best = null;
		double bestCross2 = -1.0;

		final int constraints = tin.getConstraints().size();
		final double threshMul = 4.0 * rhoMin * rhoMin; // 4*rhoMin^2
		final double minCross2 = 4.0 * minTriangleArea * minTriangleArea; // (2*area)^2

		for (final SimpleTriangle t : tin.triangles()) {
			if (t.isGhost()) {
				continue;
			}

			if (constraints == 1) {
				// faster constraint check
				if (!t.getEdgeA().isConstraintRegionMember() || !t.getEdgeB().isConstraintRegionMember() || !t.getEdgeC().isConstraintRegionMember()) {
					continue;
				}
			} else if (constraints > 1) {
				final var rc = t.getContainingRegion();
				if (rc == null || !rc.definesConstrainedRegion()) {
					continue;
				}
			}

			// below, compute edge ratio and area inline
			final Vertex A = t.getVertexA(), B = t.getVertexB(), C = t.getVertexC();
			final double ax = A.getX(), ay = A.getY();
			final double bx = B.getX(), by = B.getY();
			final double cx = C.getX(), cy = C.getY();

			// AB, AC
			final double abx = bx - ax, aby = by - ay;
			final double acx = cx - ax, acy = cy - ay;

			// |AB|^2, |AC|^2, |BC|^2 via (AC-AB)^2 = la + lc - 2 dot(AB,AC)
			final double la = abx * abx + aby * aby;
			final double lc = acx * acx + acy * acy;
			final double dot = abx * acx + aby * acy;
			final double lb = la + lc - 2.0 * dot;

			// cross^2 (double-area squared)
			final double cross = abx * acy - aby * acx;
			final double cross2 = cross * cross;
			if (!(cross2 > 0.0)) {
				continue;
			}

			// shortest edge and product of the other two squared sides
			double pairProd;
			Vertex sA, sB;
			if (la <= lb && la <= lc) {
				pairProd = lb * lc;
				sA = A;
				sB = B;
			} else if (lb <= la && lb <= lc) {
				pairProd = la * lc;
				sA = B;
				sB = C;
			} else {
				pairProd = la * lb;
				sA = C;
				sB = A;
			}

			// bad if (R/s) >= rhoMin <=> pairProd >= 4*rhoMin^2 * cross^2
			if (pairProd < threshMul * cross2) {
				continue;
			}

			if (skipSeditiousTriangles && isSeditious(sA, sB)) {
				continue;
			}

			if (cross2 > minCross2 && cross2 > bestCross2) {
				bestCross2 = cross2;
				best = t;
			}
		}
		return best;
	}

	/**
	 * Try to insert an off-center for a given skinny triangle, or split an
	 * encroached segment.
	 *
	 * <p>
	 * Procedure:
	 * <ol>
	 * <li>Compute triangle shortest edge midpoint and unit bisector oriented into
	 * the triangle;</li>
	 * <li>compute circumcenter and choose target distance
	 * {@code d = min(dCircumcenter, beta * |edge|)};</li>
	 * <li>place off-center at {@code m + d·n};</li>
	 * <li>if the off-center encroaches a constrained segment, split that segment
	 * instead;</li>
	 * <li>guard against inserting points too close to existing vertices or too near
	 * a constrained edge interior; otherwise insert.</li>
	 * </ol>
	 * </p>
	 *
	 * @param tri      the skinny triangle to resolve
	 * @param segments current constrained-subsegment snapshot used for encroachment
	 *                 tests
	 * @return
	 */
	private Vertex insertOffcenterOrSplit(final SimpleTriangle tri, final List<IQuadEdge> segments) {
		final Vertex a = tri.getVertexA(), b = tri.getVertexB(), c = tri.getVertexC();

		Vertex p = a, q = b;
		final double ab2 = a.getDistanceSq(b), bc2 = b.getDistanceSq(c), ca2 = c.getDistanceSq(a);
		if (bc2 < ab2 && bc2 <= ca2) {
			p = b;
			q = c;
		} else if (ca2 < ab2 && ca2 <= bc2) {
			p = c;
			q = a;
		}

		final double len = Math.sqrt(p.getDistanceSq(q));
		if (!(len > 0)) {
			return insertCircumcenterOrSplit(tri, segments);
		}

		final double mx = 0.5 * (p.getX() + q.getX());
		final double my = 0.5 * (p.getY() + q.getY());

		/*
		 * Tinfour edges are always oriented counterclockwise around the interior of a
		 * triangle - find vector oriented 90 degrees counterclockwise from PQ.
		 */
		double nx = -(q.getY() - p.getY());
		double ny = q.getX() - p.getX();
		final double nlen = hypot(nx, ny);
		if (nlen == 0) {
			return insertCircumcenterOrSplit(tri, segments);
		}
		nx /= nlen;
		ny /= nlen;

		final Circumcircle cc = tri.getCircumcircle();
		if (cc == null) {
			return insertCircumcenterOrSplit(tri, segments);
		}
		final double cx = cc.getX(), cy = cc.getY();
		final double dCirc = hypot(cx - mx, cy - my);

		final double d = Math.min(dCirc, beta * len);

		final double ox = mx + nx * d;
		final double oy = my + ny * d;
		final double oz = interpolator.interpolate(ox, oy, null);
		final Vertex off = new Vertex(ox, oy, oz);
		off.setRefinementProduct(true);

		final IQuadEdge enc = firstEncroachedByPoint(off, segments);
		if (enc != null) {
			return splitSegmentSmart(enc);
		}

		final double localScale = Math.max(1e-12, len);
		final double nearVertexTol = NEAR_VERTEX_REL_TOL * localScale;
		final double nearEdgeTol = NEAR_EDGE_REL_TOL * localScale;

		if (lastInsertedVertex != null && lastInsertedVertex.getDistance(off) <= nearVertexTol) {
			return null;
		}

		final Vertex nearest = nearestNeighbor(ox, oy);
		if (nearest != null && nearest.getDistance(off) <= nearVertexTol) {
			return null;
		}

		final IQuadEdge nearEdge = firstNearConstrainedEdgeInterior(off, segments, nearEdgeTol);
		if (nearEdge != null) {
			return splitSegmentSmart(nearEdge);
		}

		off.setIndex(vertexIndexer++);
		addVertex(off, VType.OFFCENTER, null, 0);
		lastInsertedVertex = off;
		return off;
	}

	/**
	 * Fallback insertion path that considers the triangle circumcenter.
	 *
	 * <p>
	 * If the circumcenter encroaches a constrained subsegment, the encroached
	 * segment is split instead of inserting the point. The candidate is also
	 * screened against near-duplicate and near-edge conditions.
	 * </p>
	 *
	 * @param tri      the triangle whose circumcenter is considered
	 * @param segments snapshot of constrained subsegments used for screening
	 * @return
	 */
	private Vertex insertCircumcenterOrSplit(final SimpleTriangle tri, final List<IQuadEdge> segments) {
		final Circumcircle cc = tri.getCircumcircle();
		if (cc == null) {
			return null;
		}

		final Vertex center = cc.getCircumcenter();

		final IQuadEdge enc = firstEncroachedByPoint(center, segments);
		if (enc != null) {
			return splitSegmentSmart(enc);
		}

		final double localScale = Math.max(1e-12, tri.getShortestEdge().getLength());
		final double nearVertexTol = NEAR_VERTEX_REL_TOL * localScale;
		final double nearEdgeTol = NEAR_EDGE_REL_TOL * localScale;

		final Vertex nearest = nearestNeighbor(center.x, center.y);
		if ((nearest != null && nearest.getDistance(center) <= nearVertexTol)
				|| (lastInsertedVertex != null && lastInsertedVertex.getDistance(center) <= nearVertexTol)) {
			return null;
		}

		final IQuadEdge nearEdge = firstNearConstrainedEdgeInterior(center, segments, nearEdgeTol);
		if (nearEdge != null) {
			return splitSegmentSmart(nearEdge);
		}
		double cz = interpolator.interpolate(center.getX(), center.getY(), null);
		Vertex centerZ = new Vertex(center.getX(), center.getY(), cz, vertexIndexer++);
		centerZ.setRefinementProduct(true);
		tin.add(centerZ);
		vdata.put(centerZ, new VData(VType.CIRCUMCENTER, null, 0));
		lastInsertedVertex = centerZ;
		return centerZ;
	}

	private Vertex nearestNeighbor(final double x, final double y) {
		return navigator.getNearestVertex(x, y);
	}

	/**
	 * Split a constrained subsegment while tagging the new midpoint for shells.
	 *
	 * <p>
	 * This method uses the TIN's {@code splitEdge} operation to preserve topology.
	 * If one endpoint is a critical corner, the inserted midpoint is assigned a
	 * shell index computed from the corner center; the midpoint is recorded in the
	 * vertex metadata map.
	 * </p>
	 *
	 * @param seg the constrained subsegment to split (must be non-null)
	 * @return the newly inserted vertex
	 */
	private Vertex splitSegmentSmart(final IQuadEdge seg) {
		final Vertex a = seg.getA(), b = seg.getB();
		Vertex corner = null;
		if (isCornerCritical(a)) {
			corner = a;
		} else if (isCornerCritical(b)) {
			corner = b;
		}

		double z = (a.getZ()+b.getZ())*0.5;
		final Vertex v = tin.splitEdge(seg, 0.5, z);
		if (v != null) {
			v.setRefinementProduct(true);
			final int k = (corner != null) ? shellIndex(corner, v.x, v.y) : 0;
			vdata.put(v, new VData(VType.MIDPOINT, corner, k));
			lastInsertedVertex = v;
		}
		return v;
	}

	/**
	 * Find the first constrained subsegment encroached by the candidate point.
	 *
	 * @param p        the candidate point (may be off-center or circumcenter)
	 * @param segments the constrained subsegment snapshot to check
	 * @return the first encroached {@link IQuadEdge} or {@code null}
	 */
	private IQuadEdge firstEncroachedByPoint(final Vertex p, final List<IQuadEdge> segments) {
		for (final IQuadEdge seg : segments) {
			final Vertex vA = seg.getA();
			final Vertex vB = seg.getB();
			final double midX = (vA.getX() + vB.getX()) / 2.0;
			final double midY = (vA.getY() + vB.getY()) / 2.0;
			final double length = seg.getLength();
			final double r2 = length * length / 4;
			if (p.getDistanceSq(midX, midY) < r2) {
				return seg;
			}
		}
		return null;
	}

	/**
	 * Find a constrained subsegment whose open interior lies within {@code tol} of
	 * {@code v}.
	 *
	 * <p>
	 * Used to avoid inserting points nearly on the interior of constrained edges.
	 * Projection onto each segment is computed; only projections with parameter
	 * {@code t ∈ (0,1)} are considered interior.
	 * </p>
	 *
	 * @param v        point to test
	 * @param segments list of constrained subsegments
	 * @param tol      perpendicular-distance tolerance (scale-aware)
	 * @return first subsegment whose interior is within {@code tol}, or {@code null}
	 */
	private IQuadEdge firstNearConstrainedEdgeInterior(final Vertex v, final List<IQuadEdge> segments, final double tol) {
		final double px = v.getX(), py = v.getY();
		for (final IQuadEdge seg : segments) {
			final Vertex a = seg.getA(), b = seg.getB();
			final double ax = a.getX(), ay = a.getY();
			final double bx = b.getX(), by = b.getY();

			final double vx = bx - ax, vy = by - ay;
			final double wx = px - ax, wy = py - ay;

			final double vv = vx * vx + vy * vy;
			if (vv == 0) {
				continue;
			}

			final double t = (wx * vx + wy * vy) / vv;
			if (t <= 0 || t >= 1) {
				continue;
			}

			final double projx = ax + t * vx, projy = ay + t * vy;
			final double dist = hypot(px - projx, py - projy);
			if (dist <= tol) {
				return seg;
			}
		}
		return null;
	}

	/**
	 * Conservative check to decide whether to ignore an encroachment (seditious
	 * case).
	 *
	 * <p>
	 * The method predicts the midpoint shell that would be created by splitting
	 * {@code e} and compares it with the shell of the {@code witness}. If both lie
	 * on the same shell around a critical corner and the witness is a corner-tied
	 * midpoint, the encroachment is considered seditious and may be ignored to
	 * prevent ping-pong.
	 * </p>
	 *
	 * @param e       the candidate constrained subsegment
	 * @param witness the vertex found in the diametral circle
	 * @return {@code true} if the encroachment should be ignored, {@code false}
	 *         otherwise
	 */
	private boolean shouldIgnoreEncroachment(final IQuadEdge e, final Vertex witness) {
		final Vertex A = e.getA(), B = e.getB();
		Vertex corner = null;
		if (isCornerCritical(A)) {
			corner = A;
		} else if (isCornerCritical(B)) {
			corner = B;
		}
		if (corner == null) {
			return false;
		}

		final double mx = 0.5 * (A.x + B.x), my = 0.5 * (A.y + B.y);
		final int kMid = shellIndex(corner, mx, my);
		final int kW = shellIndex(corner, witness.x, witness.y);
		if (kMid != kW) {
			return false;
		}

		final VData mw = vdata.get(witness);
		return (mw != null && mw.t == VType.MIDPOINT && mw.corner == corner);
	}

	/**
	 * Build corner angle information for constrained-graph vertices.
	 *
	 * <p>
	 * This routine inspects the constrained segment adjacency around each vertex
	 * and records the minimum and maximum interior angles between incident
	 * constrained edges. The computed {@link CornerInfo} map is used to mark
	 * corners that are either very acute (considered "critical" for seditious
	 * logic).
	 * </p>
	 *
	 * @return a map from corner {@link Vertex} to {@link CornerInfo}
	 */
	private Map<Vertex, CornerInfo> buildCornerInfo() {
		final Map<Vertex, List<Vertex>> nbrs = new IdentityHashMap<>();
		for (final IQuadEdge e : collectConstrainedSegments()) {
			final Vertex A = e.getA(), B = e.getB();
			nbrs.computeIfAbsent(A, k -> new ArrayList<>()).add(B);
			nbrs.computeIfAbsent(B, k -> new ArrayList<>()).add(A);
		}
		final Map<Vertex, CornerInfo> info = new IdentityHashMap<>();
		for (final var ent : nbrs.entrySet()) {
			final Vertex z = ent.getKey();
			final List<Vertex> list = ent.getValue();
			if (list.size() < 2) {
				continue;
			}

			final CornerInfo ci = new CornerInfo();
			final List<Double> angs = new ArrayList<>(list.size());
			for (final Vertex w : list) {
				angs.add(Math.atan2(w.y - z.y, w.x - z.x));
			}
			for (int i = 0; i < angs.size(); i++) {
				for (int j = i + 1; j < angs.size(); j++) {
					final double a = angleSmallBetweenDeg(angs.get(i), angs.get(j));
					ci.minAngleDeg = Math.min(ci.minAngleDeg, a);
				}
			}
			info.put(z, ci);
		}
		return info;
	}

	/**
	 * Compute the smaller of the two oriented angles (in degrees) between the two
	 * given directions (radians).
	 *
	 * @param a first direction in radians
	 * @param b second direction in radians
	 * @return absolute smallest angle between directions, in degrees, ∈ [0,180]
	 */
	private double angleSmallBetweenDeg(final double a, final double b) {
		double d = Math.abs(a - b);
		d = Math.min(d, 2 * Math.PI - d);
		return Math.toDegrees(d);
	}

	/**
	 * Test whether a corner vertex is critical.
	 *
	 * <p>
	 * A corner is critical if the smallest incident constrained-edge angle is less
	 * than a configured small threshold.
	 * </p>
	 *
	 * @param z the corner vertex to test
	 * @return {@code true} if the corner is critical; {@code false} otherwise
	 */
	private boolean isCornerCritical(final Vertex z) {
		final CornerInfo ci = cornerInfo.get(z);
		return ci != null && ci.minAngleDeg < SMALL_CORNER_DEG;
	}

	/**
	 * Tests whether the edge (u,v) is seditious.
	 *
	 * <p>
	 * An edge is seditious when both endpoints are midpoints produced on
	 * constrained segments incident to the same critical corner, and both points
	 * lie on the same concentric shell around that corner. Seditious edges are
	 * treated specially (their presence can be excluded from triangle-splitting
	 * decisions to prevent cascades).
	 * </p>
	 *
	 * @param u one endpoint
	 * @param v the other endpoint
	 * @return {@code true} if the edge is seditious; {@code false} otherwise
	 */
	private boolean isSeditious(final Vertex u, final Vertex v) {
		final VData mu = vdata.get(u), mv = vdata.get(v);
		if (mu == null || mv == null || mu.t != VType.MIDPOINT || mv.t != VType.MIDPOINT) {
			return false;
		}
		if (mu.corner == null || mu.corner != mv.corner) {
			return false;
		}
		final Vertex z = mu.corner;
		if (!isCornerCritical(z)) {
			return false;
		}
		return sameShell(z, u, v);
	}

	/**
	 * Compute a concentric-shell index for a point about corner {@code z}.
	 *
	 * <p>
	 * Shells are integer exponents of {@code SHELL_BASE} (powers of two by
	 * default). The shell index is a convenient bucket used for comparing whether
	 * two midpoints lie on the same concentric radius.
	 * </p>
	 *
	 * @param z the corner center
	 * @param x candidate point x-coordinate
	 * @param y candidate point y-coordinate
	 * @return integer shell index (0 for extremely small radii)
	 */
	private int shellIndex(final Vertex z, final double x, final double y) {
		final double d = hypot(x - z.x, y - z.y);
		if (d <= SHELL_EPS) {
			return 0;
		}
		return (int) Math.round(Math.log(d) / Math.log(SHELL_BASE));
	}

	/**
	 * Convenience: test whether two vertices lie on the same shell about corner
	 * {@code z}.
	 *
	 * @param z corner center
	 * @param a first vertex
	 * @param b second vertex
	 * @return {@code true} if both vertices have equal shell index around {@code z}
	 */
	private boolean sameShell(final Vertex z, final Vertex a, final Vertex b) {
		return shellIndex(z, a.x, a.y) == shellIndex(z, b.x, b.y);
	}

	/**
	 * Add a new vertex to both the TIN and record metadata.
	 *
	 * @param v      the vertex to add (must be non-null)
	 * @param type   the creation type (see {@link VType})
	 * @param corner optional corner vertex used for shell tagging (may be
	 *               {@code null})
	 * @param shell  shell index assigned to the vertex (0 if not used)
	 */
	private void addVertex(final Vertex v, final VType type, final Vertex corner, final int shell) {
		tin.add(v);
		vdata.put(v, new VData(type, corner, shell));
	}

	/**
	 * Returns the nearest encroaching apex vertex of the two triangles adjacent to
	 * the given edge, or null if neither apex lies strictly inside the edge’s
	 * diametral circle (Gabriel test). Endpoints of the edge are not considered
	 * encroachers. Handles boundary edges where one apex may be absent. Assumes the
	 * mesh is (constrained) Delaunay so checking only adjacent apices is
	 * sufficient.
	 *
	 * @param edge edge to test (must be part of the current (C)DT)
	 * @return the closest encroaching apex vertex (C or D), or null if the edge is
	 *         not encroached
	 */
	private static Vertex closestEncroacherOrNull(IQuadEdge edge) {
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

	private static double hypot(double dx, double dy) {
		double sumOfSquares = dx * dx + dy * dy;
		return Math.sqrt(sumOfSquares);
	}

	/**
	 * Enumeration of vertex creation types tracked by {@link VData}.
	 *
	 * <ul>
	 * <li>{@code INPUT} — original TIN vertex;</li>
	 * <li>{@code MIDPOINT} — vertex created by segment bisection;</li>
	 * <li>{@code OFFCENTER} — Shewchuk off-center insertion;</li>
	 * <li>{@code CIRCUMCENTER} — triangle circumcenter insertion (fallback).</li>
	 * </ul>
	 */
	private enum VType {
		INPUT, MIDPOINT, OFFCENTER, CIRCUMCENTER
	}

	/**
	 * Metadata recorded for each vertex inserted or present in the TIN.
	 *
	 * <p>
	 * This internal helper bundles:
	 * <ul>
	 * <li>{@link VType} — how the vertex was created (input, midpoint, off-center,
	 * circumcenter);</li>
	 * <li>{@code corner} — an optional corner vertex used for concentric-shell
	 * tagging;</li>
	 * <li>{@code shell} — an integer shell index (power-of-two radius) used to
	 * detect seditious midpoints.</li>
	 * </ul>
	 * </p>
	 *
	 * <p>
	 * Instances are stored in an {@link IdentityHashMap} keyed by {@code Vertex}.
	 * This compact form enables inexpensive seditious/encroachment heuristics.
	 * </p>
	 */
	private static class VData {
		VType t;
		Vertex corner;
		int shell;

		VData(final VType t, final Vertex c, final int s) {
			this.t = t;
			this.corner = c;
			this.shell = s;
		}
	}

	/**
	 * Simple structural holder for corner angular information.
	 *
	 * <p>
	 * Fields:
	 * <ul>
	 * <li>{@code minAngleDeg} — the smallest angle (in degrees) between any two
	 * constrained incident edges at the corner.</li>
	 * </p>
	 *
	 * <p>
	 * The refiner uses this to mark corners that are “critical” either because they
	 * are very acute (small) which can provoke pathological encroachment behavior.
	 * </p>
	 */
	private static class CornerInfo {
		double minAngleDeg = 180.0;
	}
}