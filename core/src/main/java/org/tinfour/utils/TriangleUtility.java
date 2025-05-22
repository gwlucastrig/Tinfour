package org.tinfour.utils;

import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;

import static java.lang.Math.acos;
import static java.lang.Math.toDegrees;

/**
 * Provides utility methods for performing computations and checks on
 * {@link SimpleTriangle} instances. This class is not intended to be instantiated;
 * all methods are static.
 */
public final class TriangleUtility {

    private static final double EPSILON = 1e-9; // Used for small floating point comparisons

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TriangleUtility() {
        // Utility class should not be instantiated.
    }

    /**
     * Calculates the three interior angles of a given triangle using the Law of Cosines.
     * The angles are returned in degrees.
     * <p>
     * If the triangle is degenerate (e.g., its vertices are collinear, or it has
     * an edge of near-zero length according to internal epsilon comparisons),
     * this method returns an array {@code {0.0, 0.0, 180.0}} or similar,
     * depending on the nature of the degeneracy.
     *
     * @param triangle the input triangle; must not be null.
     * @return an array of 3 doubles representing the angles of the triangle in degrees.
     *         The order of angles in the array corresponds to vertices A, B, and C
     *         of the {@link SimpleTriangle#getVertexA()}, {@link SimpleTriangle#getVertexB()},
     *         and {@link SimpleTriangle#getVertexC()} respectively.
     *         Returns {@code {0.0, 0.0, 180.0}} if the triangle is considered degenerate.
     * @throws IllegalArgumentException if the input triangle is null.
     */
    public static double[] getAngles(SimpleTriangle triangle) {
        if (triangle == null) {
            throw new IllegalArgumentException("Input triangle cannot be null");
        }

        Vertex vA = triangle.getVertexA();
        Vertex vB = triangle.getVertexB();
        Vertex vC = triangle.getVertexC();

        // Although SimpleTriangle should ensure its vertices are non-null,
        // an explicit check here or reliance on IQuadEdge.getLength() to handle issues
        // from potentially malformed (e.g. ghost) triangles is important.
        if (vA == null || vB == null || vC == null) {
            // This condition implies a malformed triangle or a logic error upstream.
            // SimpleTriangle itself should prevent this for non-ghost triangles.
            // For robustness, treat as degenerate.
            return new double[]{0.0, 0.0, 180.0};
        }

        // Edge 'a' is opposite vertex A (formed by vertices B and C)
        IQuadEdge edgeOppositeA = triangle.getEdgeA(); // Corresponds to edge BC
        // Edge 'b' is opposite vertex B (formed by vertices A and C)
        IQuadEdge edgeOppositeB = triangle.getEdgeB(); // Corresponds to edge AC
        // Edge 'c' is opposite vertex C (formed by vertices A and B)
        IQuadEdge edgeOppositeC = triangle.getEdgeC(); // Corresponds to edge AB

        if (edgeOppositeA == null || edgeOppositeB == null || edgeOppositeC == null) {
             // Should not happen for a valid SimpleTriangle constructed by the TIN.
             // Treat as degenerate if edges are missing.
            return new double[]{0.0, 0.0, 180.0};
        }

        double len_a = edgeOppositeA.getLength();
        double len_b = edgeOppositeB.getLength();
        double len_c = edgeOppositeC.getLength();

        if (len_a <= EPSILON || len_b <= EPSILON || len_c <= EPSILON ||
            Double.isNaN(len_a) || Double.isNaN(len_b) || Double.isNaN(len_c) ||
            !Double.isFinite(len_a) || !Double.isFinite(len_b) || !Double.isFinite(len_c)) {
            // Handles cases where edges are zero, very small, or have invalid lengths.
            return new double[]{0.0, 0.0, 180.0};
        }
        
        // Numerators for Law of Cosines:
        double numA = len_b * len_b + len_c * len_c - len_a * len_a;
        double numB = len_a * len_a + len_c * len_c - len_b * len_b;
        double numC = len_a * len_a + len_b * len_b - len_c * len_c;

        // Denominators for Law of Cosines:
        double denA = 2 * len_b * len_c;
        double denB = 2 * len_a * len_c;
        double denC = 2 * len_a * len_b;

        // Check for division by zero or very small denominators (covered by len checks above)
        // and ensure argument to acos is within [-1, 1]
        double ratioA = Math.max(-1.0, Math.min(1.0, numA / denA));
        double ratioB = Math.max(-1.0, Math.min(1.0, numB / denB));
        double ratioC = Math.max(-1.0, Math.min(1.0, numC / denC));


        double angleA_rad = acos(ratioA);
        double angleB_rad = acos(ratioB);
        // The third angle can be derived to ensure sum is PI, which can be more robust
        // against floating point inaccuracies than direct calculation if previous angles are precise.
        // However, calculating all three and then potentially normalizing or simply returning them
        // is also common. The original code used PI - A - B.
        double angleC_rad = Math.PI - angleA_rad - angleB_rad;
        // Smallest angle might become negative if sum of A+B > PI due to precision errors.
        // Ensure C is not negative if A+B is slightly > PI.
        if (angleC_rad < 0) angleC_rad = 0;


        return new double[]{
                toDegrees(angleA_rad),
                toDegrees(angleB_rad),
                toDegrees(angleC_rad)
        };
    }

    /**
     * Gets the shortest of the three edges forming the specified triangle.
     *
     * @param triangle the input triangle; must not be null.
     * @return the {@link IQuadEdge} representing the shortest edge of the triangle.
     *         Returns one of the edges if multiple edges have the same minimal length.
     * @throws IllegalArgumentException if the input triangle is null or its edges are malformed.
     */
    public static IQuadEdge getShortestEdge(SimpleTriangle triangle) {
        if (triangle == null) {
            throw new IllegalArgumentException("Input triangle cannot be null.");
        }

        IQuadEdge edgeA = triangle.getEdgeA(); // Opposite vertex A
        IQuadEdge edgeB = triangle.getEdgeB(); // Opposite vertex B
        IQuadEdge edgeC = triangle.getEdgeC(); // Opposite vertex C

        if (edgeA == null || edgeB == null || edgeC == null) {
            throw new IllegalArgumentException("Triangle contains null edges, cannot determine shortest edge.");
        }

        double lengthA = edgeA.getLength();
        double lengthB = edgeB.getLength();
        double lengthC = edgeC.getLength();

        if (Double.isNaN(lengthA) || Double.isNaN(lengthB) || Double.isNaN(lengthC) ||
            !Double.isFinite(lengthA) || !Double.isFinite(lengthB) || !Double.isFinite(lengthC)) {
            // This indicates a problem with edge length calculation, possibly from degenerate geometry.
            // Depending on strictness, could throw or return one edge.
            // For now, stick to original behavior implicitly by comparison.
        }


        IQuadEdge shortestEdge = edgeA;
        double minLength = lengthA;

        if (lengthB < minLength) {
            shortestEdge = edgeB;
            minLength = lengthB;
        }

        if (lengthC < minLength) {
            shortestEdge = edgeC;
            // minLength = lengthC; // Not strictly needed as only shortestEdge is returned
        }

        return shortestEdge;
    }

    /**
     * Calculates the ratio of the triangle's circumradius to the length of its shortest edge.
     * This ratio is a common quality measure for triangles in mesh generation, where smaller
     * values (e.g., close to 0.5 for equilateral) are preferred, and very large values
     * indicate "skinny" or poorly shaped triangles.
     *
     * @param triangle the input triangle; may be null.
     * @return the ratio of the circumradius to the shortest edge length.
     *         Returns {@link Double#POSITIVE_INFINITY} if:
     *         <ul>
     *           <li>the triangle is null or a ghost triangle.</li>
     *           <li>the circumradius is not finite (e.g., for degenerate triangles).</li>
     *           <li>the shortest edge length is zero or very close to zero (<= EPSILON).</li>
     *         </ul>
     */
    public static double getCircumradiusToShortestEdgeRatio(SimpleTriangle triangle) {
        if (triangle == null || triangle.isGhost()) {
            return Double.POSITIVE_INFINITY;
        }

        Circle circumcircle = triangle.getCircumcircle();
        if (circumcircle == null) { // Should not happen for non-ghost SimpleTriangles from TIN
            return Double.POSITIVE_INFINITY;
        }
        double radius = circumcircle.getRadius();

        if (!Double.isFinite(radius) || radius < 0) { // Radius should be non-negative
            return Double.POSITIVE_INFINITY;
        }

        IQuadEdge shortestEdge = getShortestEdge(triangle); // Handles null triangle internally
        if (shortestEdge == null) { // Should be caught by getShortestEdge's null triangle check
            return Double.POSITIVE_INFINITY;
        }
        double shortestEdgeLength = shortestEdge.getLength();

        if (shortestEdgeLength <= EPSILON || !Double.isFinite(shortestEdgeLength)) {
            return Double.POSITIVE_INFINITY;
        }

        return radius / shortestEdgeLength;
    }

    /**
     * Determines if a triangle is of "poor quality" based on specified thresholds
     * for minimum angle and maximum circumradius-to-shortest-edge ratio.
     * <p>
     * A triangle is considered poor quality if:
     * <ul>
     *   <li>Any of its interior angles are less than {@code minAngleThresholdDegrees}, AND
     *       {@code minAngleThresholdDegrees} is a positive finite number.</li>
     *   <li>OR, its circumradius-to-shortest-edge ratio is greater than {@code maxRatioThreshold},
     *       AND {@code maxRatioThreshold} is a positive finite number.</li>
     * </ul>
     * If a threshold is {@link Double#NaN}, zero, or negative, the corresponding check is skipped.
     * Null or ghost triangles are not considered poor quality by this method (return false).
     * Degenerate triangles (e.g., with zero area or angles {0,0,180}) will likely
     * fail the minimum angle check if {@code minAngleThresholdDegrees} is positive,
     * or the ratio check if {@code maxRatioThreshold} is positive and finite, due to
     * extreme angles or an infinite ratio.
     *
     * @param triangle the triangle to check; may be null.
     * @param minAngleThresholdDegrees the minimum acceptable interior angle in degrees.
     *                                 If NaN, zero, or negative, the angle check is skipped.
     * @param maxRatioThreshold the maximum acceptable ratio of circumradius to shortest edge length.
     *                          If NaN, zero, or negative, the ratio check is skipped.
     * @return {@code true} if the triangle meets the criteria for being poor quality;
     *         {@code false} otherwise (including if the triangle is null or a ghost).
     */
    public static boolean isTrianglePoorQuality(SimpleTriangle triangle,
                                                double minAngleThresholdDegrees,
                                                double maxRatioThreshold) {
        if (triangle == null || triangle.isGhost()) {
            return false; // Null or Ghost triangles are not considered "poor quality" by this method.
        }

        // Angle Check
        boolean angleCheckEnabled = !Double.isNaN(minAngleThresholdDegrees) && minAngleThresholdDegrees > 0;
        if (angleCheckEnabled) {
            double[] angles = getAngles(triangle); // Handles degenerate cases internally
            // If getAngles returns the specific {0,0,180} for degenerate, and minAngleThreshold > 0,
            // then 0 < minAngleThresholdDegrees will be true, marking it poor.
            for (double angle : angles) {
                if (angle < minAngleThresholdDegrees) {
                    return true; // Fails minimum angle criterion
                }
            }
        }

        // Ratio Check
        boolean ratioCheckEnabled = !Double.isNaN(maxRatioThreshold) && maxRatioThreshold > 0;
        if (ratioCheckEnabled) {
            double ratio = getCircumradiusToShortestEdgeRatio(triangle);
            // POSITIVE_INFINITY (from degenerate/bad triangles) > maxRatioThreshold (if finite positive) will be true.
            if (ratio > maxRatioThreshold) {
                return true; // Fails maximum ratio criterion
            }
        }

        return false; // Passes all enabled quality checks
    }
}
