package org.tinfour.refinement;

import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.common.IConstraint;
import org.tinfour.utils.TriangleUtility;
import org.tinfour.utils.SegmentUtility;
import org.tinfour.utils.Circle;

import java.util.Queue;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Implements Ruppert's algorithm for Delaunay refinement to improve mesh quality.
 * Ruppert's algorithm iteratively refines a Delaunay triangulation by inserting points
 * (triangle circumcenters or midpoints of encroached segments) until certain
 * quality criteria (minimum angle, maximum triangle aspect ratio) are met for all
 * triangles in the mesh, while respecting input segment constraints.
 * <p>
 * This implementation uses a simplified approach for queue management where the
 * entire queue of potentially problematic elements (poor-quality triangles or
 * encroached segments) is rebuilt after each modification to the TIN.
 */
public class RuppertsRefiner {

    private final IIncrementalTin tin;
    private final double minAngleThresholdDegrees;
    private final double maxCircumradiusToShortestEdgeRatio;
    private final double tolerance; // For floating point comparisons in encroachment checks
    private final List<IConstraint> constraints; // Local cache of constraints

    /**
     * Constructs a new RuppertsRefiner instance.
     *
     * @param tin The {@link IIncrementalTin} instance to be refined.
     *            This TIN will be modified in place by the {@link #refine()} method.
     *            Must not be null.
     * @param minAngleThresholdDegrees The minimum acceptable interior angle for any triangle
     *                                 in the mesh, specified in degrees. If a triangle
     *                                 contains an angle smaller than this value, it is
     *                                 considered "poor quality" and its circumcenter may be
     *                                 inserted (unless it encroaches a segment).
     *                                 To disable this criterion, pass a non-positive value or {@link Double#NaN}.
     * @param maxCircumradiusToShortestEdgeRatio The maximum acceptable ratio of a triangle's
     *                                           circumradius to its shortest edge length. If a
     *                                           triangle's ratio exceeds this value, it is
     *                                           considered "poor quality" and its circumcenter
     *                                           may be inserted.
     *                                           To disable this criterion, pass a non-positive value or {@link Double#NaN}.
     * @throws IllegalArgumentException if the {@code tin} is null.
     */
    public RuppertsRefiner(IIncrementalTin tin, double minAngleThresholdDegrees, double maxCircumradiusToShortestEdgeRatio) {
        if (tin == null) {
            throw new IllegalArgumentException("IIncrementalTin instance cannot be null.");
        }
        this.tin = tin;
        this.minAngleThresholdDegrees = minAngleThresholdDegrees;
        this.maxCircumradiusToShortestEdgeRatio = maxCircumradiusToShortestEdgeRatio;
        this.tolerance = 1e-9; // Default internal tolerance for geometric checks

        // Cache constraints locally. Ensure this.constraints is never null.
        List<IConstraint> currentConstraints = tin.getConstraints();
        if (currentConstraints == null) {
            this.constraints = Collections.emptyList();
        } else {
            // Create a defensive copy if the list from tin.getConstraints() might change
            // or is not guaranteed to be modifiable/persistent.
            this.constraints = new ArrayList<>(currentConstraints);
        }
    }

    /**
     * Retrieves all unique constrained segments currently present in the TIN.
     * This method iterates through all edges in the TIN and collects those
     * marked as constrained, ensuring each segment (by its base index) is added only once.
     *
     * @return A list of {@link IQuadEdge} objects representing the constrained segments.
     *         Returns an empty list if there are no constrained segments or if the
     *         TIN does not provide edges.
     */
    private List<IQuadEdge> getAllConstrainedSegmentsFromTin() {
        List<IQuadEdge> segments = new ArrayList<>();
        Set<Integer> addedBaseIndices = new HashSet<>(); // Tracks base indices to ensure uniqueness
        Iterable<IQuadEdge> allEdgesInTin = tin.getEdges();

        if (allEdgesInTin == null) {
            return Collections.emptyList(); // Or handle as an error condition
        }

        for (IQuadEdge edge : allEdgesInTin) {
            if (edge.isConstrained()) {
                // Add edge if its base index hasn't been processed, ensuring each segment is unique
                if (addedBaseIndices.add(edge.getBaseIndex())) {
                    segments.add(edge);
                }
            }
        }
        return segments;
    }

    /**
     * Executes the Delaunay refinement process on the {@link IIncrementalTin}
     * provided during construction. This method iteratively modifies the TIN
     * by adding new vertices until all non-ghost triangles meet the specified
     * quality criteria (minimum angle and maximum circumradius-to-shortest-edge ratio)
     * and no constrained segments are encroached by existing vertices.
     * <p>
     * The core operations are:
     * <ol>
     *   <li><strong>Splitting Encroached Segments:</strong> If a constrained segment is
     *       encroached by any vertex in the TIN (either an existing vertex or the
     *       circumcenter of a poor-quality triangle), the segment is split by
     *       inserting its midpoint.</li>
     *   <li><strong>Inserting Circumcenters:</strong> If a triangle is deemed "poor quality"
     *       (based on angle or ratio criteria) and its circumcenter does not encroach
     *       upon any constrained segment, the circumcenter is inserted into the TIN.</li>
     * </ol>
     * This process is repeated until no further points need to be added.
     * The refinement is performed in place, directly modifying the input TIN.
     * <p>
     * Note: Depending on the complexity of the input TIN and the stringency of
     * the quality criteria, this operation can be computationally intensive.
     * The current implementation rebuilds its internal processing queue after each
     * modification, which is a simplified but potentially less optimal approach for
     * very large meshes.
     */
    public void refine() {
        Queue<Object> q = new ArrayDeque<>();
        // Sets to keep track of what's already been added to the queue in the current pass of populateQueue,
        // to avoid redundant processing if an item meets multiple criteria.
        Set<Integer> trianglesInQueue = new HashSet<>();
        Set<Integer> segmentsInQueue = new HashSet<>();

        // Initial population of the queue with items that need processing.
        populateQueue(q, trianglesInQueue, segmentsInQueue);

        while (!q.isEmpty()) {
            Object itemToProcess = q.poll();

            if (itemToProcess instanceof SimpleTriangle) {
                SimpleTriangle poorTriangle = (SimpleTriangle) itemToProcess;

                // A triangle might be processed if its circumcenter is added, then new bad triangles form.
                // The populateQueue method handles not re-adding already processed items from *previous* TIN states.
                // Here, we are processing an item pulled from the queue.

                Circle circumcircle = poorTriangle.getCircumcircle();
                if (circumcircle == null) { // Should not happen for non-ghost triangles
                    continue;
                }
                // Create a vertex for the circumcenter. Z-coordinate is typically an average or 0.
                // For 2D refinement, Z might be ignored or set to a default (e.g., 0 or interpolated).
                // Assuming Z=0 or interpolated if available from triangle vertices.
                // For simplicity, let's use Z=0 as it's a common 2D refinement approach.
                Vertex ccVertex = new Vertex(circumcircle.getX(), circumcircle.getY(), 0.0);

                boolean encroaches = false;
                List<IQuadEdge> currentConstrainedSegments = getAllConstrainedSegmentsFromTin();
                for (IQuadEdge segment : currentConstrainedSegments) {
                    Circle diametralCircle = SegmentUtility.getDiametralCircle(segment);
                    if (SegmentUtility.isPointEncroachingSegment(ccVertex, segment, diametralCircle, this.tolerance)) {
                        // If ccVertex encroaches segment S, S must be split. Add S to queue.
                        // (The populateQueue method will handle adding it if not already queued/processed).
                        // Here, we directly add to queue if not already present in this iteration's *segmentsInQueue*.
                        // This ensures if multiple CCs encroach the same segment, it's only added once per main loop.
                        // However, the simplified model is to clear and repopulate, so this direct add might be less critical.
                        if (segmentsInQueue.add(segment.getBaseIndex())) { // If not already added in this populate pass
                           q.offer(segment);
                        }
                        encroaches = true;
                        break; // ccVertex encroaches one segment, that's enough.
                    }
                }

                if (!encroaches) {
                    tin.add(ccVertex); // Add circumcenter to the TIN
                    // TIN has changed. Re-evaluate everything.
                    q.clear();
                    trianglesInQueue.clear();
                    segmentsInQueue.clear();
                    populateQueue(q, trianglesInQueue, segmentsInQueue);
                }
                // If it encroaches, the segment was (or will be) added to the queue.
                // The main loop will eventually process the segment.

            } else if (itemToProcess instanceof IQuadEdge) {
                IQuadEdge encroachedSegment = (IQuadEdge) itemToProcess;

                Vertex vA = encroachedSegment.getA();
                Vertex vB = encroachedSegment.getB();
                if (vA == null || vB == null) { // Should not happen for valid segments
                    continue;
                }

                // Calculate midpoint of the encroached segment.
                // Z-coordinate can be interpolated.
                double midX = (vA.getX() + vB.getX()) / 2.0;
                double midY = (vA.getY() + vB.getY()) / 2.0;
                double midZ = (vA.getZ() + vB.getZ()) / 2.0;
                Vertex midpointVertex = new Vertex(midX, midY, midZ);

                tin.add(midpointVertex); // Add midpoint, splitting the segment.
                // TIN has changed. Re-evaluate everything.
                q.clear();
                trianglesInQueue.clear();
                segmentsInQueue.clear();
                populateQueue(q, trianglesInQueue, segmentsInQueue);
            }
        } // End while loop
    }

    /**
     * Populates the processing queue {@code q} with poor-quality triangles and
     * encroached segments from the current state of the TIN.
     * This method is called initially and after each modification to the TIN.
     *
     * @param q The queue to populate with {@link SimpleTriangle} or {@link IQuadEdge} objects.
     * @param trianglesInQueue A set used to track triangle indices already added to the queue
     *                         in the current pass, to avoid duplicates.
     * @param segmentsInQueue A set used to track segment base indices already added to the queue
     *                        in the current pass, to avoid duplicates.
     */
    private void populateQueue(Queue<Object> q, Set<Integer> trianglesInQueue, Set<Integer> segmentsInQueue) {
        // Identify and queue poor-quality triangles
        Iterable<SimpleTriangle> allTriangles = tin.triangles();
        if (allTriangles != null) {
            for (SimpleTriangle triangle : allTriangles) {
                if (triangle.isGhost()) {
                    continue;
                }
                // Add to queue if it's poor quality and not already added in this pass
                if (TriangleUtility.isTrianglePoorQuality(triangle, minAngleThresholdDegrees, maxCircumradiusToShortestEdgeRatio)) {
                    if (trianglesInQueue.add(triangle.getIndex())) { // getIndex() for unique ID
                        q.offer(triangle);
                    }
                }
            }
        }

        // Identify and queue encroached segments
        List<IQuadEdge> currentConstrainedSegments = getAllConstrainedSegmentsFromTin();
        List<Vertex> allVertices = tin.getVertices(); // Get all vertices currently in the TIN
        if (allVertices == null) allVertices = Collections.emptyList();


        for (IQuadEdge segment : currentConstrainedSegments) {
            Circle diametralCircle = SegmentUtility.getDiametralCircle(segment);
            for (Vertex vertex : allVertices) {
                if (vertex == null) continue;

                // Check if this vertex encroaches the current segment
                if (SegmentUtility.isPointEncroachingSegment(vertex, segment, diametralCircle, this.tolerance)) {
                    // If segment is encroached and not already added in this pass, add to queue
                    if (segmentsInQueue.add(segment.getBaseIndex())) { // getBaseIndex() for unique ID
                        q.offer(segment);
                    }
                    break; // Segment is encroached, no need to check other vertices against this segment
                }
            }
        }
    }
}
