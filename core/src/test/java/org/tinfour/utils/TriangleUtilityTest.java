package org.tinfour.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.SimpleTriangle;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TriangleUtilityTest {

    private static final double DELTA = 1e-5; // For floating point comparisons

    // Helper method to create a SimpleTriangle from three vertices
    private SimpleTriangle createTriangle(Vertex v0, Vertex v1, Vertex v2) {
        IIncrementalTin tin = new IncrementalTin(DELTA);
        tin.add(v0);
        tin.add(v1);
        tin.add(v2);

        List<SimpleTriangle> triangles = new ArrayList<>();
        for (SimpleTriangle t : tin.triangles()) {
            if (!t.isGhost()) { // Ensure we are not picking a ghost triangle if TIN forms one
                triangles.add(t);
            }
        }
        if (triangles.isEmpty()) {
            // This can happen if vertices are collinear and form a degenerate structure
            // that IncrementalTin might handle by not forming a SimpleTriangle.
            // For testing degenerate cases of getAngles, we might need a direct mock.
            // However, SimpleTriangle constructor itself might fail or produce a ghost.
            // Let's try to get the edges manually for such cases if needed.
            // For now, assume valid, non-degenerate triangles are formed by non-collinear points.
            // If vertices are collinear, tin.triangles() might be empty or only ghost.
            // In such case, we need to construct SimpleTriangle manually with appropriate edges.
            // This indicates that for degenerate triangles in getAngles, special handling is needed.
             System.err.println("Warning: No non-ghost triangle formed for vertices: " + v0 + ", " + v1 + ", " + v2);
             // Fallback for collinear points: create a "manual" SimpleTriangle if possible
             // This is tricky because SimpleTriangle expects real edges from a TIN.
             // The design of SimpleTriangle is tightly coupled with the TIN.
             // For testing getAngles with degenerate, let's allow it to pass through and see what SimpleTriangle does.
             // If IncrementalTin.triangles() is empty for collinear points, then this helper fails for degenerate cases.
        }
        // If more than one triangle, it's unexpected for just 3 vertices unless they are collinear
        // and the TIN is trying to do something complex.
        assertTrue(!triangles.isEmpty(), "Failed to create a triangle from the provided vertices. Check for collinearity or TIN behavior.");
        return triangles.get(0);
    }

    @Test
    void testGetAngles() {
        // 1. Equilateral triangle
        Vertex vA_eq = new Vertex(0, 0, 0);
        Vertex vB_eq = new Vertex(2, 0, 0);
        Vertex vC_eq = new Vertex(1, Math.sqrt(3), 0);
        SimpleTriangle eqTriangle = createTriangle(vA_eq, vB_eq, vC_eq);
        double[] angles_eq = TriangleUtility.getAngles(eqTriangle);
        Arrays.sort(angles_eq); // Sort for comparison ease
        assertArrayEquals(new double[]{60.0, 60.0, 60.0}, angles_eq, DELTA, "Equilateral triangle angles incorrect");

        // 2. Right-angle triangle (3-4-5)
        Vertex vA_rt = new Vertex(0, 0, 0);
        Vertex vB_rt = new Vertex(3, 0, 0);
        Vertex vC_rt = new Vertex(0, 4, 0);
        SimpleTriangle rtTriangle = createTriangle(vA_rt, vB_rt, vC_rt);
        double[] angles_rt = TriangleUtility.getAngles(rtTriangle);
        Arrays.sort(angles_rt);
        // Angles: 90, atan(4/3) ~ 53.1301, atan(3/4) ~ 36.8699
        assertArrayEquals(new double[]{Math.toDegrees(Math.atan(3.0/4.0)), Math.toDegrees(Math.atan(4.0/3.0)), 90.0}, angles_rt, DELTA, "Right-angle triangle angles incorrect");

        // 3. Obtuse triangle
        Vertex vA_ob = new Vertex(0, 0, 0);
        Vertex vB_ob = new Vertex(3, 0, 0);
        Vertex vC_ob = new Vertex(1, 0.5, 0); // Small y, c will be short, angle at C likely obtuse
                                             // Let's make it more clearly obtuse: (0,0), (5,0), (1,1)
        vA_ob = new Vertex(0,0,0);
        vB_ob = new Vertex(5,0,0);
        vC_ob = new Vertex(1,1,0);
        // Side lengths: a=BC=sqrt((5-1)^2 + (0-1)^2) = sqrt(16+1) = sqrt(17)
        // b=AC=sqrt((0-1)^2 + (0-1)^2) = sqrt(1+1) = sqrt(2)
        // c=AB=5
        // Angle C (opposite side c=5): acos((17+2-25)/(2*sqrt(17)*sqrt(2))) = acos(-6 / (2*sqrt(34))) = acos(-3/sqrt(34))
        // This is acos(-0.5145) ~ 120.96 degrees, which is obtuse.
        SimpleTriangle obTriangle = createTriangle(vA_ob, vB_ob, vC_ob);
        double[] angles_ob = TriangleUtility.getAngles(obTriangle);
        boolean hasObtuse = false;
        for(double angle : angles_ob) {
            if (angle > 90.0 + DELTA) hasObtuse = true;
        }
        assertTrue(hasObtuse, "Obtuse triangle did not have an angle > 90 degrees.");
        assertEquals(180.0, angles_ob[0]+angles_ob[1]+angles_ob[2], DELTA, "Obtuse triangle angles sum not 180");

        // 4. Degenerate triangle (collinear vertices)
        // (0,0), (1,0), (2,0)
        // The createTriangle helper might not work directly if IncrementalTin doesn't form a SimpleTriangle.
        // Let's test TriangleUtility.getAngles directly with a manually constructed SimpleTriangle if possible.
        // SimpleTriangle requires IQuadEdge instances.
        // This is where mocking edges is cleaner for degenerate cases.
        // Let's assume for now that TriangleUtility.getAngles handles it if a SimpleTriangle can be formed.
        // The implementation of getAngles returns {0,0,180} if any edge length is EPSILON or less.
        // If SimpleTriangle reports edge lengths as zero for collinear points, this should work.

        // Mock edges for degenerate triangle:
        Vertex vd1 = new Vertex(0,0,0,0);
        Vertex vd2 = new Vertex(1,0,0,1);
        Vertex vd3 = new Vertex(2,0,0,2);
        // Edges: (vd1,vd2), (vd2,vd3), (vd1,vd3)
        MockEdge edge_d12 = new MockEdge(vd1, vd2, 0); // length 1
        MockEdge edge_d23 = new MockEdge(vd2, vd3, 2); // length 1
        MockEdge edge_d13 = new MockEdge(vd1, vd3, 4); // length 2
        // Link them for SimpleTriangle(tin, edge) constructor if needed, but we use (tin, e1,e2,e3)
        IncrementalTin dummyTin = new IncrementalTin(); // For constructor, not actually used for geometry here
        SimpleTriangle degenerateTriangle = new SimpleTriangle(dummyTin, edge_d13, edge_d12, edge_d23);
        // The edges should be: edgeA (opposite vA), edgeB (opposite vB), edgeC (opposite vC)
        // If triangle is (vd1, vd2, vd3), then:
        // edgeA is (vd2, vd3) -> edge_d23
        // edgeB is (vd1, vd3) -> edge_d13
        // edgeC is (vd1, vd2) -> edge_d12
        // So, SimpleTriangle(tin, vd1, vd2, vd3) would need edges: edge_d23, edge_d13, edge_d12
        // The constructor SimpleTriangle(tin, eA, eB, eC) takes edges opposite A, B, C
        // Let's re-map the vertices of SimpleTriangle to these edges
        // SimpleTriangle(vA, vB, vC) means:
        // Edge A is BC, Edge B is AC, Edge C is AB
        // If our vertices are vd1, vd2, vd3 in order:
        // vA=vd1, vB=vd2, vC=vd3
        // edgeA (opposite vd1) is edge (vd2, vd3) -> length 1 (edge_d23)
        // edgeB (opposite vd2) is edge (vd1, vd3) -> length 2 (edge_d13)
        // edgeC (opposite vd3) is edge (vd1, vd2) -> length 1 (edge_d12)
        // The SimpleTriangle constructor needs these edges directly.
        degenerateTriangle = new SimpleTriangle(dummyTin, edge_d23, edge_d13, edge_d12);
        // Ensure vertices are assigned correctly
//        degenerateTriangle.setVertices(vd1, vd2, vd3);


        double[] angles_degen = TriangleUtility.getAngles(degenerateTriangle);
        Arrays.sort(angles_degen);
        // Expect {0, 0, 180} due to law of cosines with a+b=c
        // (len_a=1, len_b=2, len_c=1). This is not quite right.
        // For collinear (0,0)-(1,0)-(2,0):
        // A=(0,0), B=(1,0), C=(2,0).
        // a=BC=1, b=AC=2, c=AB=1.
        // Angle A: acos((b^2+c^2-a^2)/(2bc)) = acos((4+1-1)/(2*2*1)) = acos(4/4) = acos(1) = 0 rad
        // Angle B: acos((a^2+c^2-b^2)/(2ac)) = acos((1+1-4)/(2*1*1)) = acos(-2/2) = acos(-1) = PI rad (180 deg)
        // Angle C: acos((a^2+b^2-c^2)/(2ab)) = acos((1+4-1)/(2*1*2)) = acos(4/4) = acos(1) = 0 rad
        assertArrayEquals(new double[]{0.0, 0.0, 180.0}, angles_degen, DELTA, "Degenerate triangle angles incorrect");

        // 5. Triangle with a very short edge (almost degenerate)
        Vertex vA_short = new Vertex(0,0,0);
        Vertex vB_short = new Vertex(0.00001, 0, 0); // Very short edge AB
        Vertex vC_short = new Vertex(1,1,0);
        SimpleTriangle shortEdgeTriangle = createTriangle(vA_short, vB_short, vC_short);
        double[] angles_short = TriangleUtility.getAngles(shortEdgeTriangle);
        assertEquals(180.0, angles_short[0]+angles_short[1]+angles_short[2], DELTA, "Short-edge triangle angles sum not 180");
        // Expect one angle to be very small, and two angles close to those of the triangle (0,0)-(1,1)-(0,0.00001)
        // which is almost (0,0)-(1,1)-(0,0). This would be angles near 0, 45, 135.
        // The angle opposite the short edge should be close to 0.
        Arrays.sort(angles_short);
        assertTrue(angles_short[0] < 0.1, "Shortest angle in short-edge triangle not close to 0"); // Check if smallest angle is small
    }

    @Test
    void testGetShortestEdge() {
        Vertex v0 = new Vertex(0,0,0);
        Vertex v1 = new Vertex(3,0,0); // Edge v0-v1 length 3
        Vertex v2 = new Vertex(0,4,0); // Edge v0-v2 length 4
                                      // Edge v1-v2 length 5 (3-4-5 triangle)
        SimpleTriangle triangle = createTriangle(v0, v1, v2);
        IQuadEdge edgeAB = findEdge(triangle, v0, v1); // Length 3
        IQuadEdge edgeBC = findEdge(triangle, v1, v2); // Length 5
        IQuadEdge edgeCA = findEdge(triangle, v2, v0); // Length 4

        assertNotNull(edgeAB);
        assertNotNull(edgeBC);
        assertNotNull(edgeCA);

        // Re-order edges to match SimpleTriangle internal representation if needed
        // SimpleTriangle.getEdgeA() is opposite VertexA, etc.
        // If triangle vertices are v0, v1, v2 (A, B, C)
        // EdgeA is v1-v2 (BC) length 5
        // EdgeB is v0-v2 (AC) length 4
        // EdgeC is v0-v1 (AB) length 3
        // So, EdgeC (edgeAB) should be the shortest.
        assertEquals(edgeAB.getIndex(), TriangleUtility.getShortestEdge(triangle).getIndex(), "Shortest edge is incorrect for 3-4-5 triangle");

        // Triangle with two edges same minimum length
        Vertex v_iso1 = new Vertex(0,0,0);
        Vertex v_iso2 = new Vertex(4,0,0); // Base, length 4
        Vertex v_iso3 = new Vertex(2,3,0); // Sides are sqrt(2^2+3^2) = sqrt(4+9) = sqrt(13)
                                           // Let's make it simpler: (0,0), (2,0), (1,1)
                                           // Sides: 2, sqrt(1^2+1^2)=sqrt(2), sqrt(1^2+1^2)=sqrt(2)
        v_iso1 = new Vertex(0,0,0);
        v_iso2 = new Vertex(2,0,0); // length 2
        v_iso3 = new Vertex(1,1,0); // length sqrt(2), length sqrt(2)
        SimpleTriangle isoTriangle = createTriangle(v_iso1, v_iso2, v_iso3);
        IQuadEdge shortestIso = TriangleUtility.getShortestEdge(isoTriangle);
        assertEquals(Math.sqrt(2), shortestIso.getLength(), DELTA, "Shortest edge length for isosceles incorrect");

        // Test with null triangle
        assertThrows(IllegalArgumentException.class, () -> TriangleUtility.getShortestEdge(null), "getShortestEdge(null) should throw IllegalArgumentException");
    }

    // Helper to find a specific edge in a triangle
    private IQuadEdge findEdge(SimpleTriangle t, Vertex u, Vertex v) {
        if (matches(t.getEdgeA(), u, v)) return t.getEdgeA();
        if (matches(t.getEdgeB(), u, v)) return t.getEdgeB();
        if (matches(t.getEdgeC(), u, v)) return t.getEdgeC();
        return null;
    }

    private boolean matches(IQuadEdge edge, Vertex u, Vertex v) {
        return (edge.getA().getIndex() == u.getIndex() && edge.getB().getIndex() == v.getIndex()) ||
               (edge.getA().getIndex() == v.getIndex() && edge.getB().getIndex() == u.getIndex());
    }


    @Test
    void testGetCircumradiusToShortestEdgeRatio() {
        // 1. Equilateral triangle: (0,0), (2,0), (1, sqrt(3))
        // Side length = 2. Shortest edge = 2.
        // Circumradius R = side / sqrt(3) = 2 / sqrt(3).
        // Ratio = (2/sqrt(3)) / 2 = 1/sqrt(3) ~= 0.57735
        Vertex vA_eq = new Vertex(0, 0, 0);
        Vertex vB_eq = new Vertex(2, 0, 0);
        Vertex vC_eq = new Vertex(1, Math.sqrt(3), 0);
        SimpleTriangle eqTriangle = createTriangle(vA_eq, vB_eq, vC_eq);
        assertEquals(1.0 / Math.sqrt(3), TriangleUtility.getCircumradiusToShortestEdgeRatio(eqTriangle), DELTA, "Ratio for equilateral triangle incorrect");

        // 2. Right isosceles triangle: (0,0), (2,0), (0,2)
        // Edges: 2, 2, 2*sqrt(2). Shortest edge = 2.
        // Hypotenuse = 2*sqrt(2). Circumcenter is midpoint of hypotenuse (1,1).
        // Circumradius = hypotenuse / 2 = sqrt(2).
        // Ratio = sqrt(2) / 2 ~= 0.7071
        Vertex vA_rt_iso = new Vertex(0,0,0);
        Vertex vB_rt_iso = new Vertex(2,0,0);
        Vertex vC_rt_iso = new Vertex(0,2,0);
        SimpleTriangle rtIsoTriangle = createTriangle(vA_rt_iso, vB_rt_iso, vC_rt_iso);
        assertEquals(Math.sqrt(2)/2.0, TriangleUtility.getCircumradiusToShortestEdgeRatio(rtIsoTriangle), DELTA, "Ratio for right isosceles triangle incorrect");

        // 3. "Bad" skinny triangle
        // (0,0), (10,0), (5, 0.1). Shortest edge will be small if (5, 0.1) is close to one of the others, or if one side is small.
        // Let vertices be (0,0), (0.01, 0.01), (10, 0).
        // Edge1 ((0,0)-(0.01,0.01)): length sqrt(0.01^2+0.01^2) = sqrt(2*0.0001) = 0.01*sqrt(2) ~= 0.01414 (Shortest)
        // Edge2 ((0.01,0.01)-(10,0)): length sqrt(9.99^2+0.01^2) = sqrt(99.8001+0.0001) = sqrt(99.8002) ~= 9.99
        // Edge3 ((0,0)-(10,0)): length 10
        // This triangle is very thin. Area approx 0.5 * base * height = 0.5 * 10 * 0.01 = 0.05
        // Circumradius R = abc/(4*Area) = (0.01414 * 9.99 * 10) / (4 * 0.05) = (1.412586) / 0.2 = 7.06
        // Ratio = R / shortest = 7.06 / 0.01414 ~= 500. This should be large.
        Vertex vA_skinny = new Vertex(0,0,0);
        Vertex vB_skinny = new Vertex(0.01, 0.01, 0);
        Vertex vC_skinny = new Vertex(10,0,0);
        SimpleTriangle skinnyTriangle = createTriangle(vA_skinny, vB_skinny, vC_skinny);
        assertTrue(TriangleUtility.getCircumradiusToShortestEdgeRatio(skinnyTriangle) > 10, "Ratio for skinny triangle should be large");


        // 4. Degenerate triangle (using mock for simplicity, as SimpleTriangle might not form)
        // If shortest edge is effectively zero, expect POSITIVE_INFINITY
        Vertex vd1 = new Vertex(0,0,0,0);
        Vertex vd2 = new Vertex(1,0,0,1);
        Vertex vd3 = new Vertex(1,TriangleUtility.EPSILON / 2.0 ,0,2); // vd2 and vd3 are very close
        MockEdge edge_d12 = new MockEdge(vd1, vd2, 0); // len 1
        MockEdge edge_d23 = new MockEdge(vd2, vd3, 2); // len EPSILON/2 (very small)
        MockEdge edge_d13 = new MockEdge(vd1, vd3, 4); // len approx 1
        SimpleTriangle degenTriangle = new SimpleTriangle(new IncrementalTin(), edge_d13, edge_d12, edge_d23); // (A=vd1, B=vd2, C=vd3) -> edges opp A, B, C
//        degenTriangle.setVertices(vd1, vd2, vd3); // ensure vertices match edges
        // Shortest edge is edge_d23 with length EPSILON/2, which is <= TriangleUtility.EPSILON
        assertEquals(Double.POSITIVE_INFINITY, TriangleUtility.getCircumradiusToShortestEdgeRatio(degenTriangle), "Ratio for degenerate (tiny shortest edge) triangle should be +Inf");

        // 6. Null triangle (should be caught by getShortestEdge -> null pointer exception if not handled, or IAEx)
        // The method itself has null check: if (triangle == null || triangle.isGhost())
         assertEquals(Double.POSITIVE_INFINITY, TriangleUtility.getCircumradiusToShortestEdgeRatio(null), "Ratio for null triangle should be +Inf");
    }


    @Test
    void testIsTrianglePoorQuality() {
        // Setup a "good" triangle: equilateral
        Vertex vA_eq = new Vertex(0, 0, 0);
        Vertex vB_eq = new Vertex(2, 0, 0);
        Vertex vC_eq = new Vertex(1, Math.sqrt(3), 0);
        SimpleTriangle goodTriangle = createTriangle(vA_eq, vB_eq, vC_eq); // Angles 60,60,60. Ratio 1/sqrt(3) ~0.577

        // Thresholds for "good" quality
        double minAngleGood = 30;
        double maxRatioGood = 2.0;

        assertFalse(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, maxRatioGood), "Good equilateral triangle marked as poor");

        // 1. Fails only angle check
        // Create a triangle with a small angle but good ratio. e.g., an isosceles triangle with a very small top angle.
        // (0,0), (2,0), (1, 0.01). Angles will be ~178, ~1, ~1. Ratio might be okay.
        // Base = 2. Sides = sqrt(1^2+0.01^2) = sqrt(1.0001) ~ 1.00005. Shortest edge ~1.00005.
        // Circumradius R = abc/(4*Area). Area = 0.5 * 2 * 0.01 = 0.01.
        // R = (1.00005 * 1.00005 * 2) / (4*0.01) = (2.0002) / 0.04 = 50.005
        // Ratio = R / shortest_side = 50.005 / 1.00005 ~ 50. This fails ratio too.
        // Need a triangle that has a small angle but is not "thin".
        // Example: (0,0), (100,0), (50,1). Angle at (50,1) is almost 180. Angles at (0,0) and (100,0) are tiny.
        // Shortest edge could be long.
        // Let's try: (0,0), (10, 1), (1, 10).
        // Side lengths:
        // s1 ((0,0)-(10,1)): sqrt(100+1) = sqrt(101) ~ 10.05
        // s2 ((10,1)-(1,10)): sqrt(9^2 + (-9)^2) = sqrt(81+81) = sqrt(162) ~ 12.7
        // s3 ((1,10)-(0,0)): sqrt(1+100) = sqrt(101) ~ 10.05
        // Shortest edge sqrt(101).
        // Angles: cosA = (b^2+c^2-a^2)/(2bc) = (101+101-162)/(2*sqrt(101)*sqrt(101)) = (202-162)/202 = 40/202 ~ 0.198. A ~ 78.5 deg.
        // This is an isosceles triangle. Two angles are equal. Sum is 180.
        // (180 - 78.5) / 2 = 101.5 / 2 = 50.75. This is not a small angle triangle.

        // Let's use a triangle with known angles: 20, 80, 80. Ratio should be okay.
        // For an isosceles triangle with angles (alpha, beta, beta): side_a / sin(alpha) = side_b / sin(beta) = 2R
        // R = a / (2 sin(alpha)). Shortest edge is 'a' if alpha is smallest.
        // Ratio = (a/(2sin(alpha))) / a = 1/(2sin(alpha)).
        // If alpha = 20 deg (0.349 rad), sin(alpha)=0.342. Ratio = 1/(2*0.342) = 1/0.684 ~ 1.46. This is good.
        // So, a 20,80,80 triangle fails angle (if minAngle=30) but passes ratio (if maxRatio=2).
        // We need to construct such a triangle. (0,0), (1,0), (x,y)
        // Angle at (0,0) is 20 deg. Angle at (1,0) is 80 deg. Angle at (x,y) is 80 deg.
        // This means side opposite (0,0) is shortest.
        // This is hard to construct directly.
        // Alternative: use the (0,0), (3,0), (0,4) right triangle. Angles: 36.87, 53.13, 90. Ratio?
        // Edges 3,4,5. Shortest 3. R = Hyp/2 = 5/2 = 2.5. Ratio = 2.5/3 = 0.833. Good.
        // Make angle fail: minAngle = 40. Then 36.87 fails.
        SimpleTriangle rtTriangle = createTriangle(new Vertex(0,0,0), new Vertex(3,0,0), new Vertex(0,4,0));
        assertTrue(TriangleUtility.isTrianglePoorQuality(rtTriangle, 40, maxRatioGood), "Fails angle only: rtTriangle (36.87deg) should be poor with minAngle=40");
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, minAngleGood, maxRatioGood), "Fails angle only: rtTriangle should be good with minAngle=30");


        // 2. Fails only ratio check
        // Equilateral has ratio 0.577. Make it fail: maxRatio = 0.5
        assertTrue(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, 0.5), "Fails ratio only: goodTriangle (ratio 0.577) should be poor with maxRatio=0.5");
        assertFalse(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, maxRatioGood), "Fails ratio only: goodTriangle should be good with maxRatio=2.0");

        // 3. Fails both
        // Use rtTriangle (angles 36.87, 53.13, 90; ratio 0.833)
        // Thresholds: minAngle=40, maxRatio=0.5
        assertTrue(TriangleUtility.isTrianglePoorQuality(rtTriangle, 40, 0.5), "Fails both: rtTriangle should be poor with minAngle=40, maxRatio=0.5");

        // 4. Passes both (already tested with goodTriangle and good thresholds)
        assertFalse(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, maxRatioGood), "Passes both: goodTriangle should be good");

        // 5. Null or ghost triangle
        assertFalse(TriangleUtility.isTrianglePoorQuality(null, minAngleGood, maxRatioGood), "Null triangle should not be poor quality");

        // 6. NaN/non-positive thresholds
        // NaN minAngle, good maxRatio
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, Double.NaN, maxRatioGood), "NaN minAngle should skip angle check (rtTriangle pass ratio)");
        // Good minAngle, NaN maxRatio
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, minAngleGood, Double.NaN), "NaN maxRatio should skip ratio check (rtTriangle pass angle)");
        // Both NaN
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, Double.NaN, Double.NaN), "Both NaN thresholds should skip both checks");
        // Non-positive minAngle
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, 0, maxRatioGood), "Zero minAngle should effectively skip angle check");
        assertFalse(TriangleUtility.isTrianglePoorQuality(rtTriangle, -5, maxRatioGood), "Negative minAngle should effectively skip angle check");
        // Non-positive maxRatio
        assertFalse(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, 0), "Zero maxRatio should effectively skip ratio check");
        assertFalse(TriangleUtility.isTrianglePoorQuality(goodTriangle, minAngleGood, -1.0), "Negative maxRatio should effectively skip ratio check");

        // Test degenerate triangle from getAngles test (0,0,180)
        // This should be poor quality if minAngleThreshold is positive, due to 0 angles.
        Vertex vd1 = new Vertex(0,0,0,0);
        Vertex vd2 = new Vertex(1,0,0,1);
        Vertex vd3 = new Vertex(2,0,0,2);
        MockEdge edge_d23 = new MockEdge(vd2, vd3, 0); // length 1
        MockEdge edge_d13 = new MockEdge(vd1, vd3, 2); // length 2
        MockEdge edge_d12 = new MockEdge(vd1, vd2, 4); // length 1
        SimpleTriangle degenerateTriangle = new SimpleTriangle(new IncrementalTin(), edge_d23, edge_d13, edge_d12);
//        degenerateTriangle.setVertices(vd1, vd2, vd3);
        assertTrue(TriangleUtility.isTrianglePoorQuality(degenerateTriangle, 10, 100), "Degenerate triangle (0,0,180 angles) should be poor if minAngle > 0");
        // If minAngleThreshold is 0 or NaN, it should pass angle check. Then depends on ratio.
        // Ratio for degenerate is likely POSITIVE_INFINITY. So it would fail if maxRatio is finite.
        assertTrue(TriangleUtility.isTrianglePoorQuality(degenerateTriangle, Double.NaN, 100), "Degenerate triangle (Inf ratio) should be poor if maxRatio is finite");
        assertFalse(TriangleUtility.isTrianglePoorQuality(degenerateTriangle, Double.NaN, Double.NaN), "Degenerate triangle should pass if both checks skipped");

    }


    // MockEdge class as discussed, for specific cases like degenerate triangles for getAngles
    // Placed as a static inner class.
    static class MockEdge implements IQuadEdge {
        Vertex a, b;
        double length;
        int index;
        int baseIndex;
        IQuadEdge forward;
        IQuadEdge reverse;
        IQuadEdge dual; // Optional, can be null or set via setter

        MockEdge(Vertex a, Vertex b, int index) {
            this.a = a;
            this.b = b;
            this.length = (a != null && b != null) ? Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2)) : 0;
            this.index = index;
            this.baseIndex = (index / 2) * 2;
            // Initialize to prevent NPE in SimpleTriangle if not explicitly set by test
            this.forward = this; 
            this.reverse = this;
            this.dual = null; // Or new MockEdge(b, a, index +1) for a basic dual pair
        }

        public void setForward(IQuadEdge forward) { this.forward = forward; }
        public void setReverse(IQuadEdge reverse) { this.reverse = reverse; }
        public void setDual(IQuadEdge dual) { this.dual = dual; }

        @Override public Vertex getA() { return a; }
        @Override public Vertex getB() { return b; }
        @Override public double getLength() { return length; }
        @Override public int getIndex() { return index; }
        @Override public int getBaseIndex() { return baseIndex; }
        @Override public IQuadEdge getForward() { return forward; }
        @Override public IQuadEdge getReverse() { return reverse; }
        @Override public IQuadEdge getDual() { return dual; }

        @Override public IQuadEdge getBaseReference() { return this; }
        @Override public IQuadEdge getForwardFromDual() { return null; }
        @Override public IQuadEdge getReverseFromDual() { return null; }
        @Override public int getSide() { return index % 2; }
        @Override public IQuadEdge getDualFromReverse() { return null; }
        @Override public int getConstraintIndex() { return 0; }
        @Override public void setConstraintIndex(int constraintIndex) { /* no-op */ }
        @Override public boolean isConstrained() { return false; }
        @Override public void setConstrained(int constraintIndex) { /* no-op */ }
        @Override public boolean isConstrainedRegionMember() { return false; }
        @Override public boolean isConstraintLineMember() { return false; }
        @Override public void setConstraintLineMemberFlag() { /* no-op */ }
        @Override public boolean isConstrainedRegionInterior() { return false; }
        @Override public boolean isConstrainedRegionBorder() { return false; }
        @Override public void setConstrainedRegionBorderFlag() { /* no-op */ }
        @Override public void setConstrainedRegionInteriorFlag() { /* no-op */ }
        @Override public void setSynthetic(boolean status) { /* no-op */ }
        @Override public boolean isSynthetic() { return false; }
        @Override public Iterable<IQuadEdge> pinwheel() { return java.util.Collections.emptyList(); }
        @Override public void setLine2D(java.awt.geom.AffineTransform transform, java.awt.geom.Line2D l2d) { /* no-op */ }
        
        // toString is kept as it's useful for debugging and not part of the interface removal criteria
        @Override public String toString() {
            String aStr = a == null ? "null" : "V"+a.getIndex();
            String bStr = b == null ? "null" : "V"+b.getIndex();
            return "MockEdge " + index + " (base " + baseIndex + ", side " + (index%2) + "): " + aStr + " -> " + bStr;
        }
    }
}
