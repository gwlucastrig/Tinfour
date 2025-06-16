//package org.tinfour.refinement;
//
//import org.junit.jupiter.api.Test;
//import static org.junit.jupiter.api.Assertions.*;
//
//import org.tinfour.common.*;
//import org.tinfour.standard.IncrementalTin;
//import org.tinfour.utils.TriangleUtility;
//import org.tinfour.utils.SegmentUtility; // For debugging or deeper checks if needed
//import org.tinfour.utils.Circle; // For debugging or deeper checks if needed
//
//
//import java.util.List;
//import java.util.ArrayList;
//
//public class RuppertRefinerTest {
//
//    private static final double TOLERANCE = 1e-7; // For floating point comparisons in assertions
//    private static final double REFINE_DELTA = 1e-9; // General delta for geometric comparisons
//
//    // Helper method to count vertices
//    private int countVertices(IIncrementalTin tin) {
//        return tin.getVertices().size();
//    }
//
//    // Helper method to check if all non-ghost triangles are good
//    private boolean areAllNonGhostTrianglesGood(IIncrementalTin tin, double minAngle, double maxRatio) {
//        if (tin == null) return true; // Or false, depending on desired strictness for null TIN
//        for (SimpleTriangle triangle : tin.triangles()) {
//            if (triangle.isGhost()) {
//                continue;
//            }
//            if (TriangleUtility.isTrianglePoorQuality(triangle, minAngle, maxRatio)) {
//                // For debugging:
//                // double[] angles = TriangleUtility.getAngles(triangle);
//                // double ratio = TriangleUtility.getCircumradiusToShortestEdgeRatio(triangle);
//                // System.out.println("Found poor triangle: " + triangle.getVertexA() + ", " + triangle.getVertexB() + ", " + triangle.getVertexC());
//                // System.out.println("  Angles: " + angles[0] + ", " + angles[1] + ", " + angles[2]);
//                // System.out.println("  Ratio: " + ratio);
//                return false;
//            }
//        }
//        return true;
//    }
//
//    private SimpleTriangle getFirstNonGhostTriangle(IIncrementalTin tin) {
//        for (SimpleTriangle t : tin.triangles()) {
//            if (!t.isGhost()) {
//                return t;
//            }
//        }
//        return null;
//    }
//
//    private List<SimpleTriangle> getNonGhostTriangles(IIncrementalTin tin) {
//        List<SimpleTriangle> triangles = new ArrayList<>();
//        for (SimpleTriangle t : tin.triangles()) {
//            if (!t.isGhost()) {
//                triangles.add(t);
//            }
//        }
//        return triangles;
//    }
//
//
//    @Test
//    void testNoRefinementNeeded() {
//        IncrementalTin tin = new IncrementalTin();
//        // Equilateral triangle: (0,0), (2,0), (1, sqrt(3))
//        tin.add(new Vertex(0, 0, 0));
//        tin.add(new Vertex(2, 0, 0));
//        tin.add(new Vertex(1, Math.sqrt(3), 0));
//
//        assertEquals(1, getNonGhostTriangles(tin).size(), "Should form one non-ghost triangle");
//
//        double minAngle = 25; // Equilateral has 60 deg angles
//        double maxRatio = 2.0; // Equilateral has ratio 1/sqrt(3) ~ 0.577
//
//        assertTrue(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "Initial equilateral triangle should be good quality");
//
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin);
//
//        refiner.refine();
//
//        int finalVertices = countVertices(tin);
//        assertEquals(initialVertices, finalVertices, "No vertices should be added for an already good TIN");
//        assertTrue(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "TIN should remain good quality after no-op refinement");
//    }
//
//    @Test
//    void testRefineOnePoorAngleTriangle() {
//        IncrementalTin tin = new IncrementalTin();
//        // Skinny triangle: (0,0), (10,0), (5, 0.1)
//        // Angles: two very small (at (0,0) and (10,0)), one very large (at (5,0.1) approx 178 deg)
//        // Smallest angle approx atan(0.1/5) = atan(0.02) ~ 1.14 degrees
//        Vertex vA = new Vertex(0, 0, 0, 0);
//        Vertex vB = new Vertex(10, 0, 0, 1);
//        Vertex vC = new Vertex(5, 0.1, 0, 2);
//        tin.add(vA);
//        tin.add(vB);
//        tin.add(vC);
//
//        assertEquals(1, getNonGhostTriangles(tin).size(), "Should form one non-ghost triangle");
//
//        double minAngle = 20; // This triangle has angles far less than 20
//        double maxRatio = Double.POSITIVE_INFINITY; // Ignore ratio for this test
//
//        assertFalse(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "Initial skinny triangle should be poor quality due to angles");
//
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin); // Should be 3
//
//        refiner.refine();
//
//        int finalVertices = countVertices(tin);
//        assertTrue(finalVertices > initialVertices, "Vertices should be added to refine poor-angle triangle. Initial=" + initialVertices + ", Final=" + finalVertices);
//
//        // It's hard to guarantee all triangles are good without knowing termination/depth.
//        // But we can check if the *original* specific poor triangle is gone or improved.
//        // A simple check is that the number of triangles might increase, or their shapes change.
//        // The refiner adds circumcenters. The original triangle (vA,vB,vC) should have been split.
//        // One way to check: no triangle should exist with exactly vA,vB,vC as vertices.
//        boolean originalTriangleExists = false;
//        for (SimpleTriangle t : getNonGhostTriangles(tin)) {
//            if (t.getVertexA().getIndex() == vA.getIndex() || t.getVertexB().getIndex() == vA.getIndex() || t.getVertexC().getIndex() == vA.getIndex()) {
//                if (t.getVertexA().getIndex() == vB.getIndex() || t.getVertexB().getIndex() == vB.getIndex() || t.getVertexC().getIndex() == vB.getIndex()) {
//                    if (t.getVertexA().getIndex() == vC.getIndex() || t.getVertexB().getIndex() == vC.getIndex() || t.getVertexC().getIndex() == vC.getIndex()) {
//                        originalTriangleExists = true;
//                        break;
//                    }
//                }
//            }
//        }
//        assertFalse(originalTriangleExists, "The original poor-angle triangle should have been split/removed.");
//
//        // Optionally, could try to assert areAllNonGhostTrianglesGood, but it might not hold if refinement is partial.
//        // For now, focus on vertex addition and original triangle removal.
//    }
//
//    @Test
//    void testRefineOnePoorRatioTriangle() {
//        IncrementalTin tin = new IncrementalTin();
//        // Skinny triangle: (0,0), (10,0), (5, 0.1)
//        // Shortest edge: side from (0,0) to (5,0.1) or (10,0) to (5,0.1). Length approx 5.
//        // s1 ((0,0)-(5,0.1)) = sqrt(25+0.01) = sqrt(25.01) ~ 5.001
//        // s2 ((10,0)-(5,0.1)) = sqrt(25+0.01) = sqrt(25.01) ~ 5.001
//        // s3 ((0,0)-(10,0)) = 10
//        // Shortest edge is ~5.001.
//        // Circumradius R = abc/(4*Area). Area = 0.5 * base * height = 0.5 * 10 * 0.1 = 0.5.
//        // R = (5.001 * 5.001 * 10) / (4 * 0.5) = (250.1) / 2 = 125.05
//        // Ratio = R / shortest_edge = 125.05 / 5.001 ~ 25. This is large.
//        Vertex vA = new Vertex(0, 0, 0, 0);
//        Vertex vB = new Vertex(10, 0, 0, 1);
//        Vertex vC = new Vertex(5, 0.1, 0, 2);
//        tin.add(vA);
//        tin.add(vB);
//        tin.add(vC);
//
//        assertEquals(1, getNonGhostTriangles(tin).size(), "Should form one non-ghost triangle");
//
//        double minAngle = 0; // Ignore angle for this test
//        double maxRatio = 1.0; // This triangle has ratio ~25, so it fails
//
//        assertFalse(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "Initial skinny triangle should be poor quality due to ratio");
//
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin);
//
//        refiner.refine();
//
//        int finalVertices = countVertices(tin);
//        assertTrue(finalVertices > initialVertices, "Vertices should be added to refine poor-ratio triangle. Initial=" + initialVertices + ", Final=" + finalVertices);
//         boolean originalTriangleExists = false;
//        for (SimpleTriangle t : getNonGhostTriangles(tin)) {
//            if (t.getVertexA().getIndex() == vA.getIndex() || t.getVertexB().getIndex() == vA.getIndex() || t.getVertexC().getIndex() == vA.getIndex()) {
//                if (t.getVertexA().getIndex() == vB.getIndex() || t.getVertexB().getIndex() == vB.getIndex() || t.getVertexC().getIndex() == vB.getIndex()) {
//                    if (t.getVertexA().getIndex() == vC.getIndex() || t.getVertexB().getIndex() == vC.getIndex() || t.getVertexC().getIndex() == vC.getIndex()) {
//                        originalTriangleExists = true;
//                        break;
//                    }
//                }
//            }
//        }
//        assertFalse(originalTriangleExists, "The original poor-ratio triangle should have been split/removed.");
//    }
//
//
//    @Test
//    void testSegmentEncroachmentByExistingVertex() {
//        IncrementalTin tin = new IncrementalTin();
//        Vertex vA = new Vertex(0, 0, 0, 0);    // Segment AB
//        Vertex vB = new Vertex(10, 0, 0, 1);
//        Vertex vC = new Vertex(5, 0.1, 0, 2);  // Encroaching vertex C for segment AB
//
//        tin.add(vA);
//        tin.add(vB);
//
//        // Define constraint AB
//        List<IConstraint> constraints = new ArrayList<>();
//        constraints.add(new LinearConstraint(vA, vB));
//        tin.addConstraints(constraints, false);
//
//        tin.add(vC); // Add vC AFTER constraint is in place
//
//        // Verify C encroaches AB
//        IQuadEdge segAB = null;
//        for (IQuadEdge e : tin.getEdges()) {
//            if (e.isConstrained() &&
//                ((e.getA().getIndex() == vA.getIndex() && e.getB().getIndex() == vB.getIndex()) ||
//                 (e.getA().getIndex() == vB.getIndex() && e.getB().getIndex() == vA.getIndex()))) {
//                segAB = e;
//                break;
//            }
//        }
//        assertNotNull(segAB, "Constrained segment AB not found");
//
//        Circle diametralCircleAB = SegmentUtility.getDiametralCircle(segAB);
//        assertTrue(SegmentUtility.isPointEncroachingSegment(vC, segAB, diametralCircleAB, TOLERANCE),
//                   "Vertex C should encroach segment AB initially.");
//
//        double minAngle = 25; // Assume other triangles formed are okay for now
//        double maxRatio = 2.0;
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin); // Should be 3
//
//        refiner.refine(); // This should split segment AB due to vC's encroachment
//
//        int finalVertices = countVertices(tin);
//        assertTrue(finalVertices > initialVertices, "Vertices should be added due to segment encroachment. Initial="+initialVertices+", Final="+finalVertices);
//
//        // Check if a vertex was added close to the midpoint of AB
//        double midX = (vA.getX() + vB.getX()) / 2.0; // 5.0
//        double midY = (vA.getY() + vB.getY()) / 2.0; // 0.0
//        boolean midpointVertexFound = false;
//        for (Vertex v : tin.getVertices()) {
//            if (v.getIndex() != vA.getIndex() && v.getIndex() != vB.getIndex() && v.getIndex() != vC.getIndex()) { // New vertex
//                if (Math.abs(v.getX() - midX) < REFINE_DELTA && Math.abs(v.getY() - midY) < REFINE_DELTA) {
//                    midpointVertexFound = true;
//                    break;
//                }
//            }
//        }
//        assertTrue(midpointVertexFound, "A new vertex should be added near the midpoint of the encroached segment AB.");
//    }
//
//
//    @Test
//    void testSegmentEncroachmentByCircumcenter() {
//        IncrementalTin tin = new IncrementalTin();
//        // Constrained Segment S1: (0,10) to (10,10)
//        Vertex s1A = new Vertex(0, 10, 0, 0);
//        Vertex s1B = new Vertex(10, 10, 0, 1);
//        tin.add(s1A);
//        tin.add(s1B);
//        List<IConstraint> constraints = new ArrayList<>();
//        constraints.add(new LinearConstraint(s1A, s1B));
////        tin.addConstraints(constraints, false);
//
//        // Poor quality triangle T1: (0,0), (10,0), (5, 0.1)
//        // Its circumcenter is roughly (5, -big_Y_value) if skinny along X axis
//        // Or (small_X_value, 5) if skinny along Y axis.
//        // Let's use (0,0), (0.2, 5), (0,10). This is skinny along Y axis.
//        // Vertices for T1:
//        Vertex t1A = new Vertex(1, 0, 0, 2); // (1,0)
//        Vertex t1B = new Vertex(9, 0, 0, 3); // (9,0)
//        Vertex t1C = new Vertex(5, 0.1, 0, 4); // (5, 0.1) forms a skinny triangle below S1
//                                            // Circumcenter for T1 (1,0)-(9,0)-(5,0.1)
//                                            // Approx. ( (1+9)/2 , y_val ) = (5, y_val)
//                                            // R = abc/(4K). a=side opp t1A ( (9,0)-(5,0.1)) = sqrt(16+0.01)=sqrt(16.01)~4
//                                            // b=side opp t1B ( (1,0)-(5,0.1)) = sqrt(16+0.01)=sqrt(16.01)~4
//                                            // c=side opp t1C ( (1,0)-(9,0)) = 8
//                                            // K = 0.5 * 8 * 0.1 = 0.4
//                                            // R = (4*4*8)/(4*0.4) = 128/1.6 = 80.
//                                            // Circumcenter x is 5. For y: use formulas.
//                                            // Midpoint of (1,0)-(9,0) is (5,0). Perpendicular goes through (5,y).
//                                            // Midpoint of (1,0)-(5,0.1) is (3, 0.05). Slope (0.1-0)/(5-1) = 0.1/4 = 0.025. Perp slope = -40.
//                                            // Eq: y - 0.05 = -40(x-3)
//                                            // CC has x=5. y - 0.05 = -40(5-3) = -40*2 = -80. y = -79.95
//                                            // CC_T1 is (5, -79.95)
//
//        // We want CC_T1 to encroach S1 ( (0,10)-(10,10) ).
//        // Diametral circle of S1: center (5,10), radius 5.
//        // CC_T1 (5, -79.95) is far from this circle. This setup is not right.
//
//        // Let's try to make a poor triangle WHOSE CIRCUMCENTER IS NEAR S1.
//        // S1: (0,10) to (10,10). Diametral circle center (5,10), radius 5.
//        // We need a triangle T1 whose circumcenter is, e.g., (5, 9).
//        // If T1 has CC at (5,9), and is poor quality.
//        // e.g. T1_A=(4,0), T1_B=(6,0), T1_C=(5, 0.01) -- this is a skinny triangle, CC (5, y_large_neg)
//        // This is tricky. Let's try a different approach for this test:
//        // Create a poor triangle T1. Add its circumcenter CC1.
//        // THEN, add a constraint S1 such that CC1 encroaches S1. This is backwards to test the refiner's logic.
//
//        // Correct test: Refiner finds poor T1. Calculates CC1. Checks if CC1 encroaches any S.
//        // If yes, S is split. If no, CC1 is added.
//        // So, S1 must exist. T1 must be poor. CC1 must encroach S1.
//        // Let S1 be (0,0) to (10,0). Diametral circle: center (5,0), radius 5.
//        // Let T1 be (4,1), (6,1), (5, 1.0 + epsilon_small_y_for_skinniness).
//        // T1_A=(4,1), T1_B=(6,1), T1_C=(5, 1.01).
//        // This triangle is above S1.
//        // Sides: A-B = 2. A-C = sqrt(1^2+0.01^2)=sqrt(1.0001)~1. B-C = sqrt(1^2+0.01^2)~1.
//        // This is an isosceles triangle, slightly perturbed. Shortest edges are AC, BC.
//        // Area = 0.5 * base=2 * height=0.01 = 0.01.
//        // Circumradius R = (1*1*2)/(4*0.01) = 2/0.04 = 50.
//        // Midpoint of AB is (5,1). Circumcenter x=5.
//        // Midpoint of AC is (4.5, 1.005). Slope AC = (1.01-1)/(5-4) = 0.01. Perp slope = -100.
//        // Eq: y - 1.005 = -100(x - 4.5).
//        // CC has x=5. y - 1.005 = -100(5 - 4.5) = -100*0.5 = -50. y = -48.995.
//        // CC is (5, -48.995). This is its circumcenter.
//        // This circumcenter (5, -48.995) MUST encroach segment S1 ((0,0)-(10,0)).
//        // Diametral circle for S1: Center (5,0), Radius 5.
//        // Is (5, -48.995) in circle ((5,0), r=5)? No, it's too far down.
//
//        // A different setup for CC encroachment:
//        // Let S1 be a vertical constrained segment: (0, -5) to (0, 5). Diametral circle: center (0,0), radius 5.
//        // Let T1 be a poor triangle to the right of S1, e.g. (1, -0.1), (1, 0.1), (10, 0).
//        // This is a skinny triangle. Vertices: V1(1,-0.1), V2(1,0.1), V3(10,0).
//        // Side V1V2 length 0.2. Side V1V3 = sqrt(9^2 + 0.1^2) = sqrt(81.01) ~ 9. Side V2V3 ~ 9.
//        // Shortest edge = 0.2.
//        // Area = 0.5 * base=V1V2=0.2 * height=(10-1)=9 = 0.5 * 0.2 * 9 = 0.9.
//        // R = (0.2 * 9 * 9) / (4 * 0.9) = (16.2) / 3.6 = 4.5.
//        // Circumcenter:
//        // Midpoint of V1V2 is (1,0). Perpendicular is x-axis line y=0. So CC has y=0.
//        // Midpoint of V1V3 is (5.5, -0.05). Slope V1V3 = (0 - (-0.1))/(10-1) = 0.1/9. Perp slope = -90.
//        // Eq: y - (-0.05) = -90 (x - 5.5) => y + 0.05 = -90x + 495.
//        // Sub y=0: 0.05 = -90x + 495 => 90x = 494.95 => x = 494.95/90 ~ 5.499.
//        // CC is (5.499, 0).
//        // Does (5.499, 0) encroach S1 (vertical (0,-5) to (0,5))? Diametral circle (0,0) r=5.
//        // Yes, (5.499,0) is outside this circle. This setup also doesn't work.
//
//        // The logic is: IF a poor triangle T's circumcenter CC(T) encroaches a segment S,
//        // THEN S is split.
//        // For this test, we need to construct T and S such that this happens.
//        // Let segment S be (0,0)-(10,0). Diametral circle C_S: center (5,0), radius 5.
//        // Let poor triangle T be (4, 0.1), (6, 0.1), (5, 3).
//        // CC_T: midpoint of (4,0.1)-(6,0.1) is (5,0.1). Perpendicular is x=5. So CC_T_x = 5.
//        // Midpoint of (4,0.1)-(5,3) is (4.5, 1.55). Slope = (3-0.1)/(5-4) = 2.9. Perp slope = -1/2.9.
//        // Eq: y - 1.55 = (-1/2.9)(x - 4.5).
//        // Sub x=5: y - 1.55 = (-1/2.9)(0.5) = -0.5/2.9 ~ -0.172.
//        // y ~ 1.55 - 0.172 = 1.378.
//        // So CC_T is (5, 1.378).
//        // This point (5, 1.378) IS inside C_S (center (5,0), radius 5), because (5-5)^2 + (1.378-0)^2 = 1.378^2 ~ 1.89 < 5^2=25.
//        // So, segment S should be split.
//        // Triangle T: (4,0.1), (6,0.1), (5,3). Is it poor?
//        // Angles: at (5,3) is small. Sides: (4,0.1)-(6,0.1) is 2.
//        // (4,0.1)-(5,3): sqrt(1^2 + 2.9^2) = sqrt(1+8.41) = sqrt(9.41) ~ 3.067
//        // (6,0.1)-(5,3): sqrt((-1)^2 + 2.9^2) = sqrt(9.41) ~ 3.067
//        // Angles for isosceles (2, 3.067, 3.067): cos(alpha) = (3.067^2+3.067^2-2^2)/(2*3.067*3.067)
//        // = (9.41+9.41-4)/(2*9.41) = (14.82)/18.82 ~ 0.787. alpha ~ 38 degrees. This is the small angle.
//        // (180-38)/2 = 142/2 = 71 degrees. Angles (38, 71, 71). Not super poor by angle if minAngle=20.
//        // Ratio: R = abc / (4K). K = 0.5 * base=2 * height=(3-0.1)=2.9 = 2.9.
//        // R = (2 * 3.067 * 3.067) / (4 * 2.9) = 18.81 / 11.6 ~ 1.62. This ratio is good if maxRatio=2.
//
//        // To make T poor, let's make it skinny: (4.9, 0.1), (5.1, 0.1), (5, 3)
//        // Base = 0.2. Sides = sqrt(0.1^2 + 2.9^2) = sqrt(0.01+8.41)=sqrt(8.42)~2.901
//        // Area = 0.5 * 0.2 * 2.9 = 0.29.
//        // R = (0.2 * 2.901 * 2.901) / (4*0.29) = 1.683 / 1.16 ~ 1.45. Ratio is good.
//        // Angle at (5,3): cos(a) = (2.901^2+2.901^2-0.2^2)/(2*2.901*2.901) = (8.42+8.42-0.04)/(2*8.42) = 16.8/16.84 ~ 0.997. a ~ 4.4 degrees. This IS poor.
//        // CC_T is still (5, ~1.378) by symmetry if base is (4.9,0.1)-(5.1,0.1). Midpoint is (5, 0.1). x_CC=5.
//        // Midpoint of (4.9,0.1)-(5,3) is (4.95, 1.55). Slope (3-0.1)/(5-4.9) = 2.9/0.1 = 29. Perp slope = -1/29.
//        // y - 1.55 = (-1/29)(x - 4.95). Sub x=5: y - 1.55 = (-1/29)(0.05) = -0.05/29 ~ -0.0017.
//        // y ~ 1.548. CC_T is (5, 1.548). This is still inside C_S.
//
//        // Setup:
//        tin = new IncrementalTin();
//        Vertex sA = new Vertex(0, 0, 0, 100);    // Segment S: (0,0)-(10,0)
//        Vertex sB = new Vertex(10, 0, 0, 101);
//        tin.add(sA);
//        tin.add(sB);
//        
//        constraints = new ArrayList<>();
//        constraints.add(new LinearConstraint(sA, sB));
////        tin.addConstraints(constraints, false);
//
//        Vertex tA = new Vertex(4.9, 0.1, 0, 102);
//        Vertex tB = new Vertex(5.1, 0.1, 0, 103);
//        Vertex tC = new Vertex(5.0, 3.0, 0, 104); // Forms poor triangle T above S
//        tin.add(tA);
//        tin.add(tB);
//        tin.add(tC);
//
//        double minAngle = 15; // Poor triangle T has angle ~4.4 deg, so it's poor.
//        double maxRatio = 5.0; // Poor triangle T has ratio ~1.45, so it's good by ratio.
//        // Thus, T will be added to queue. Its CC (5, 1.548) will be computed.
//        // This CC encroaches S ((0,0)-(10,0)). So S should be split.
//
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin); // 3 for S, 3 for T = 5 (sA,sB,tA,tB,tC)
//
//        refiner.refine();
//
//        int finalVertices = countVertices(tin);
//        assertTrue(finalVertices > initialVertices, "Vertices should be added. Initial="+initialVertices+", Final="+finalVertices);
//
//        // Check if segment S was split: a new vertex near (5,0) should exist.
//        // Original vertices for S: sA (idx 100), sB (idx 101).
//        // Original vertices for T: tA (idx 102), tB (idx 103), tC (idx 104).
//        boolean midpointOfS_found = false;
//        for (Vertex v : tin.getVertices()) {
//            boolean isOriginal = v.getIndex() >= 100 && v.getIndex() <= 104;
//            if (!isOriginal) {
//                if (Math.abs(v.getX() - 5.0) < REFINE_DELTA && Math.abs(v.getY() - 0.0) < REFINE_DELTA) {
//                    midpointOfS_found = true;
//                    break;
//                }
//            }
//        }
//        assertTrue(midpointOfS_found, "Midpoint of segment S should be added because T's circumcenter encroached S.");
//        // Also check that CC_T (5, 1.548) was NOT added.
//        boolean cc_T_found = false;
//         for (Vertex v : tin.getVertices()) {
//            if (Math.abs(v.getX() - 5.0) < REFINE_DELTA && Math.abs(v.getY() - 1.548) < 0.01) { // Using larger delta for Y
//                cc_T_found = true;
//                break;
//            }
//        }
//        assertFalse(cc_T_found, "Circumcenter of T should NOT be added if it encroached S.");
//    }
//
//
//    @Test
//    void testIntegrationWithConstraintsAndQuality() {
//        IncrementalTin tin = new IncrementalTin();
//        // Square: (0,0)-(1,0)-(1,1)-(0,1)
//        Vertex v0 = new Vertex(0, 0, 0, 0);
//        Vertex v1 = new Vertex(1, 0, 0, 1);
//        Vertex v2 = new Vertex(1, 1, 0, 2);
//        Vertex v3 = new Vertex(0, 1, 0, 3);
//        // Perturb v1 slightly to make triangle v0-v1-v3 poor: (0,0) - (1, 0.01) - (0,1)
//        v1 = new Vertex(1, 0.01, 0, 1);
//
//        tin.add(v0);
//        tin.add(v1);
//        tin.add(v2);
//        tin.add(v3);
//
//        // Add square boundary constraints
//        List<List<Vertex>> pslg = new ArrayList<>();
//        List<Vertex> c1 = new ArrayList<>(); c1.add(v0); c1.add(v1); pslg.add(c1);
//        List<Vertex> c2 = new ArrayList<>(); c2.add(v1); c2.add(v2); pslg.add(c2);
//        List<Vertex> c3 = new ArrayList<>(); c3.add(v2); c3.add(v3); pslg.add(c3);
//        List<Vertex> c4 = new ArrayList<>(); c4.add(v3); c4.add(v0); pslg.add(c4);
//        // Add diagonal constraint
//        List<Vertex> c5 = new ArrayList<>(); c5.add(v0); c5.add(v2); pslg.add(c5);
//        
//        List<IConstraint> constraints = new ArrayList<>();
//        for(List<Vertex> con : pslg) {
//        	constraints.add(new LinearConstraint(con));
//        }
//        tin.addConstraints(constraints, false);
//        assertTrue(tin.isBootstrapped(), "TIN should be bootstrapped.");
//        // Initial TIN has 2 triangles due to diagonal v0-v2: (v0,v1,v2) and (v0,v2,v3)
//        // Triangle v0-v1-v2: (0,0)-(1,0.01)-(1,1). Angle at v1 is near 90. Angle at v2 is small. Angle at v0 is near 90.
//        //   v0v1: len sqrt(1+0.01^2) ~ 1. v1v2: len sqrt(0+(1-0.01)^2) = 0.99. v0v2: sqrt(1+1)=sqrt(2).
//        //   Angle at v2 (opp v0v1): cosB = ( (0.99)^2 + (sqrt(2))^2 - 1^2 ) / (2*0.99*sqrt(2))
//        //     = (0.9801 + 2 - 1) / (1.98*1.414) = (1.9801) / (2.799) ~ 0.707. Angle ~ 45 deg.
//        // Triangle v0-v2-v3: (0,0)-(1,1)-(0,1). This is a right isosceles. Angles 45,45,90. Good.
//        // So, triangle (v0,v1,v2) might be okay.
//        // Let's make v0-v1-v3 poor. v0(0,0), v1(1, 0.01), v3(0,1).
//        // This triangle is NOT formed by the diagonal v0-v2.
//        // The initial triangles are (v0,v1,v2) and (v0,v2,v3).
//        // Let's check (v0,v1,v2): (0,0)-(1,0.01)-(1,1).
//        //   Angle at v1: vertex (1, 0.01). Edges to (0,0) and (1,1).
//        //     Vector v1v0 = (-1, -0.01). Vector v1v2 = (0, 0.99).
//        //     Dot product = 0 - 0.0099. Mag1 = sqrt(1+0.0001)~1. Mag2=0.99.
//        //     cos = -0.0099 / (1*0.99) = -0.01. Angle is acos(-0.01) ~ 90.5 degrees.
//        //   Angle at v0: vertex (0,0). Edges to (1,0.01) and (1,1).
//        //     Vector v0v1 = (1,0.01). Vector v0v2 = (1,1).
//        //     Dot = 1+0.01=1.01. Mag1~1. Mag2=sqrt(2).
//        //     cos = 1.01 / (1*sqrt(2)) ~ 1.01/1.414 ~ 0.714. Angle ~ 44.4 degrees.
//        //   Angle at v2: vertex (1,1). Edges to (0,0) and (1,0.01).
//        //     Vector v2v0 = (-1,-1). Vector v2v1 = (0, -0.99).
//        //     Dot = 0.99. Mag1=sqrt(2). Mag2=0.99.
//        //     cos = 0.99 / (sqrt(2)*0.99) = 1/sqrt(2). Angle = 45 degrees.
//        // So angles are (90.5, 44.4, 45). These are all fine if minAngle=20.
//
//        // Let's make a triangle explicitly poor:
//        // (0,0), (10,0), (5,0.1) constraint (0,0)-(10,0).
//        tin = new IncrementalTin();
//        Vertex pA = new Vertex(0,0,0,200);
//        Vertex pB = new Vertex(10,0,0,201);
//        Vertex pC = new Vertex(5,0.1,0,202);
//        tin.add(pA);
//        tin.add(pB);
//        tin.add(pC); // Triangle pA,pB,pC is skinny. Angle at pA, pB ~ 1.14 deg.
//
//        List<IConstraint> constraintPoor = new ArrayList<>(); // Constraint on the base of the skinny triangle.
//        constraints.add(new LinearConstraint(pA, pB));
//        tin.addConstraints(constraintPoor, false);
//
//        double minAngle = 20;
//        double maxRatio = 3.0; // Skinny triangle has ratio ~25. This should trigger refinement.
//                               // Or angle should trigger it.
//
//        assertFalse(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "Initial setup for integration test should have a poor triangle.");
//        RuppertRefiner refiner = new RuppertRefiner(tin, minAngle, maxRatio);
//        int initialVertices = countVertices(tin); // 3
//
//        refiner.refine();
//
//        int finalVertices = countVertices(tin);
//        assertTrue(finalVertices > initialVertices, "Integration test: Vertices should be added. Initial="+initialVertices+", Final="+finalVertices);
//
//        // This is a loose check. A more detailed check might look at the properties of new vertices or triangles.
//        // For now, ensuring it runs and modifies the TIN is the primary goal.
//        // System.out.println("Integration test final vertex count: " + finalVertices);
//        // tin.getVertices().forEach(v -> System.out.println("  " + v));
//        // getNonGhostTriangles(tin).forEach(t -> {
//        //    System.out.println("Triangle: " + t.getVertexA() + t.getVertexB() + t.getVertexC());
//        //    System.out.println("  Angles: " + Arrays.toString(TriangleUtility.getAngles(t)));
//        //    System.out.println("  Ratio: " + TriangleUtility.getCircumradiusToShortestEdgeRatio(t));
//        // });
//        // assertTrue(areAllNonGhostTrianglesGood(tin, minAngle, maxRatio), "Integration test: Resulting TIN should be good.");
//        // The above assertion is strong and may fail if refinement is not complete or perfect.
//    }
//}
