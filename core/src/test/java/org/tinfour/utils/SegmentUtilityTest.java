package org.tinfour.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.Collections;


public class SegmentUtilityTest {

    private static final double DELTA = 1e-9; // For floating point comparisons
    private static final double TOLERANCE = 1e-7; // For isStrictlyInside and encroachment checks

    // MockEdge static inner class
    static class MockEdge implements IQuadEdge {
        Vertex a, b;
        double length;
        int index;
        int baseIndex;
        // boolean constrained; // Example if we were to store state
        // int constraintIdx;   // Example if we were to store state
        // boolean synthetic;   // Example if we were to store state


        MockEdge(Vertex a, Vertex b, int index) {
            this.a = a;
            this.b = b;
            // Ensure length is calculated only if vertices are non-null
            if (a != null && b != null) {
                this.length = Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
            } else {
                this.length = 0;
            }
            this.index = index;
            // Ensure baseIndex is unique and typically even for one side of an edge pair
            this.baseIndex = (index / 2) * 2;
        }

        @Override public Vertex getA() { return a; }
        @Override public Vertex getB() { return b; }
        @Override public double getLength() { return length; }
        @Override public double getLengthSq() { return length*length; }
        @Override public int getIndex() { return index; }
        @Override public int getBaseIndex() { return baseIndex; }

        // Default implementations for other IQuadEdge methods
        @Override public IQuadEdge getBaseReference() { return this; }
        @Override public IQuadEdge getDual() { return null; }
        @Override public IQuadEdge getForwardFromDual() { return null; }
        @Override public IQuadEdge getReverseFromDual() { return null; }
        @Override public int getSide() { return index % 2; } // Differentiate sides for an edge pair
        @Override public IQuadEdge getForward() { return null; }
        @Override public IQuadEdge getReverse() { return null; }
        @Override public IQuadEdge getDualFromReverse() { return null; }
        @Override public int getConstraintIndex() { return 0; }
        @Override public void setConstraintIndex(int constraintIndex) { /* no-op */ }
        @Override public boolean isConstrained() { return false; }
        @Override public void setConstrained(int constraintIndex) { /* no-op */ } // Corrected signature
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

        @Override public String toString() {
            String aStr = a == null ? "null" : "V"+a.getIndex();
            String bStr = b == null ? "null" : "V"+b.getIndex();
            return "MockEdge " + index + " (base " + baseIndex + ", side " + (index%2) + "): " + aStr + " -> " + bStr;
        }
    }


    // --- Tests for org.tinfour.utils.Circle ---

    @Test
    void testCircleConstructor() {
        // Positive radius
        Circle c1 = new Circle(1.0, 2.0, 5.0);
        assertEquals(1.0, c1.x, DELTA, "Circle x coordinate incorrect");
        assertEquals(2.0, c1.y, DELTA, "Circle y coordinate incorrect");
        assertEquals(5.0, c1.radius, DELTA, "Circle radius incorrect");
        assertEquals(25.0, c1.radiusSq, DELTA, "Circle radiusSq incorrect");

        // Zero radius
        Circle c2 = new Circle(0.0, 0.0, 0.0);
        assertEquals(0.0, c2.x, DELTA, "Circle zero-radius x incorrect");
        assertEquals(0.0, c2.y, DELTA, "Circle zero-radius y incorrect");
        assertEquals(0.0, c2.radius, DELTA, "Circle zero-radius radius incorrect");
        assertEquals(0.0, c2.radiusSq, DELTA, "Circle zero-radius radiusSq incorrect");

        // Negative radius (constructor allows, radiusSq still positive)
        // Behavior of Circle class with negative radius is not specified to throw,
        // so we test its effect on radiusSq.
        Circle c3 = new Circle(0.0, 0.0, -3.0);
        assertEquals(-3.0, c3.radius, DELTA, "Circle negative-radius radius incorrect");
        assertEquals(9.0, c3.radiusSq, DELTA, "Circle negative-radius radiusSq incorrect");
    }

    @Test
    void testCircleIsStrictlyInside() {
        Circle circle = new Circle(0.0, 0.0, 5.0); // Radius 5, center (0,0)

        // Point clearly inside
        assertTrue(circle.isStrictlyInside(1.0, 1.0, TOLERANCE), "Point (1,1) should be strictly inside circle r=5");

        // Point clearly outside
        assertFalse(circle.isStrictlyInside(10.0, 10.0, TOLERANCE), "Point (10,10) should be outside circle r=5");

        // Point exactly on the boundary (distSq == radiusSq)
        // (px - cx)^2 + (py - cy)^2 < radiusSq - tolerance
        // 5^2 + 0^2 = 25. radiusSq = 25.
        // 25 < 25 - tolerance. If tolerance > 0, this is false.
        assertFalse(circle.isStrictlyInside(5.0, 0.0, TOLERANCE), "Point (5,0) on boundary should not be strictly inside");
        assertFalse(circle.isStrictlyInside(5.0, 0.0, 0.0), "Point (5,0) on boundary, zero tolerance, should not be strictly inside (uses '<')");


        // Point very close to boundary (inside)
        // (4.9, 0). distSq = 4.9^2 = 24.01. radiusSq = 25.
        // 24.01 < 25 - TOLERANCE (e.g., 25 - 1e-7 = 24.9999999) -> true
        assertTrue(circle.isStrictlyInside(4.9, 0.0, TOLERANCE), "Point (4.9,0) very close inside should be strictly inside");

        // Point very close to boundary (outside)
        // (5.000001, 0). distSq = (5.000001)^2 ~ 25.00001. radiusSq = 25.
        // 25.00001 < 25 - TOLERANCE -> false
        assertFalse(circle.isStrictlyInside(5.000001, 0.0, TOLERANCE), "Point (5.000001,0) very close outside should not be strictly inside");
        // Test edge case where point is between radiusSq - tolerance and radiusSq
        // distSq = radiusSq - tolerance/2.  Should be true.
        // Example: radius=5, tol=0.1. radiusSq=25. radiusSq-tol=24.9
        // Point at sqrt(24.95),0.  distSq=24.95.  24.95 < 24.9 is false. This is fine.
        // My condition: dx*dx+dy*dy < radiusSq - tolerance
        // If distSq = radiusSq - (tolerance/2), then: radiusSq - (tolerance/2) < radiusSq - tolerance. This is false.
        // So a point whose distance squared is radiusSq - tolerance/2 is NOT strictly inside.
        // This means the "tolerance" effectively shrinks the circle for the "strictly inside" check.
        // This interpretation aligns with the problem description: "distSq < radiusSq - tolerance"

        // Test with zero radius circle
        Circle zeroRadiusCircle = new Circle(0.0, 0.0, 0.0);
        // Point at its center (0,0). distSq = 0. radiusSq = 0.
        // 0 < 0 - TOLERANCE. If TOLERANCE > 0, this is false.
        assertFalse(zeroRadiusCircle.isStrictlyInside(0.0, 0.0, TOLERANCE), "Point at center of zero-radius circle should not be strictly inside");
        // Point not at its center
        assertFalse(zeroRadiusCircle.isStrictlyInside(1.0, 0.0, TOLERANCE), "Point not at center of zero-radius circle should not be strictly inside");
    }


    // --- Tests for org.tinfour.utils.SegmentUtility ---

    @Test
    void testGetDiametralCircle() {
        // Horizontal segment
        Vertex vH1 = new Vertex(0, 0, 0, 0);
        Vertex vH2 = new Vertex(4, 0, 0, 1);
        MockEdge segH = new MockEdge(vH1, vH2, 0);
        Circle cH = SegmentUtility.getDiametralCircle(segH);
        assertEquals(2.0, cH.x, DELTA, "Horizontal segment: Circle center x incorrect");
        assertEquals(0.0, cH.y, DELTA, "Horizontal segment: Circle center y incorrect");
        assertEquals(2.0, cH.radius, DELTA, "Horizontal segment: Circle radius incorrect");

        // Vertical segment
        Vertex vV1 = new Vertex(1, 1, 0, 2);
        Vertex vV2 = new Vertex(1, 5, 0, 3);
        MockEdge segV = new MockEdge(vV1, vV2, 2);
        Circle cV = SegmentUtility.getDiametralCircle(segV);
        assertEquals(1.0, cV.x, DELTA, "Vertical segment: Circle center x incorrect");
        assertEquals(3.0, cV.y, DELTA, "Vertical segment: Circle center y incorrect");
        assertEquals(2.0, cV.radius, DELTA, "Vertical segment: Circle radius incorrect");

        // Diagonal segment (3-4-5 triangle dimensions)
        Vertex vD1 = new Vertex(0, 0, 0, 4);
        Vertex vD2 = new Vertex(3, 4, 0, 5); // Length 5
        MockEdge segD = new MockEdge(vD1, vD2, 4);
        Circle cD = SegmentUtility.getDiametralCircle(segD);
        assertEquals(1.5, cD.x, DELTA, "Diagonal segment: Circle center x incorrect");
        assertEquals(2.0, cD.y, DELTA, "Diagonal segment: Circle center y incorrect");
        assertEquals(2.5, cD.radius, DELTA, "Diagonal segment: Circle radius incorrect");

        // Zero-length segment
        Vertex vZ1 = new Vertex(1, 1, 0, 6);
        Vertex vZ2 = new Vertex(1, 1, 0, 7); // Same as vZ1
        MockEdge segZ = new MockEdge(vZ1, vZ2, 6);
        assertEquals(0.0, segZ.getLength(), DELTA, "Zero-length segment should have length 0");
        Circle cZ = SegmentUtility.getDiametralCircle(segZ);
        assertEquals(1.0, cZ.x, DELTA, "Zero-length segment: Circle center x incorrect");
        assertEquals(1.0, cZ.y, DELTA, "Zero-length segment: Circle center y incorrect");
        assertEquals(0.0, cZ.radius, DELTA, "Zero-length segment: Circle radius should be 0");

        // Null segment
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(null),
            "getDiametralCircle(null) should throw IllegalArgumentException");

        // Segment with null vertex A
        Vertex validVertex = new Vertex(0,0,0,8);
        MockEdge segNullA = new MockEdge(null, validVertex, 8);
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(segNullA),
            "Segment with null vertex A should throw IllegalArgumentException");

        // Segment with null vertex B
        MockEdge segNullB = new MockEdge(validVertex, null, 10);
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(segNullB),
            "Segment with null vertex B should throw IllegalArgumentException");
    }


    @Test
    void testIsPointEncroachingSegment() {
        Vertex pA = new Vertex(0, 0, 0, 0); // Segment endpoint A
        Vertex pB = new Vertex(4, 0, 0, 1); // Segment endpoint B
        MockEdge segment = new MockEdge(pA, pB, 0); // Horizontal segment (0,0)-(4,0)
        Circle diametralCircle = SegmentUtility.getDiametralCircle(segment); // Center (2,0), radius 2

        // Point encroaches (inside diametral circle, not an endpoint)
        Vertex encroachingPoint = new Vertex(2, 0.5, 0, 2); // (2, 0.5) is inside circle (2,0) r=2
        assertTrue(SegmentUtility.isPointEncroachingSegment(encroachingPoint, segment, diametralCircle, TOLERANCE),
            "Point (2,0.5) should encroach segment (0,0)-(4,0)");

        // Point does not encroach (outside diametral circle)
        Vertex outsidePoint = new Vertex(2, 3, 0, 3); // (2,3) is outside
        assertFalse(SegmentUtility.isPointEncroachingSegment(outsidePoint, segment, diametralCircle, TOLERANCE),
            "Point (2,3) should not encroach segment (0,0)-(4,0)");

        // Point is one of the segment's endpoints
        assertFalse(SegmentUtility.isPointEncroachingSegment(pA, segment, diametralCircle, TOLERANCE),
            "Endpoint A should not encroach its own segment");
        assertFalse(SegmentUtility.isPointEncroachingSegment(pB, segment, diametralCircle, TOLERANCE),
            "Endpoint B should not encroach its own segment");
        // Test with a different vertex instance but same coordinates and index as an endpoint
        Vertex pA_copy = new Vertex(0,0,0, 0); // Same index as pA
        assertFalse(SegmentUtility.isPointEncroachingSegment(pA_copy, segment, diametralCircle, TOLERANCE),
            "Copy of Endpoint A (same index) should not encroach");


        // Point on the boundary of the diametral circle (should return false due to "strictly inside")
        Vertex onBoundaryPoint = new Vertex(2, 2, 0, 4); // (2,2) is on boundary of circle (2,0) r=2
                                                         // distSq = (2-2)^2 + (2-0)^2 = 4. radiusSq = 4.
                                                         // isStrictlyInside checks: 4 < 4 - TOLERANCE (false if TOLERANCE > 0)
        assertFalse(SegmentUtility.isPointEncroachingSegment(onBoundaryPoint, segment, diametralCircle, TOLERANCE),
            "Point on diametral circle boundary should not encroach");

        // Null inputs
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(null, segment, diametralCircle, TOLERANCE));
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(encroachingPoint, null, diametralCircle, TOLERANCE));
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(encroachingPoint, segment, null, TOLERANCE));

        // Zero-length segment
        Vertex zP = new Vertex(1,1,0, 10);
        MockEdge zeroSeg = new MockEdge(zP, new Vertex(1,1,0,11) /* different index for B */, 100); // Segment from (1,1) to (1,1)
        Circle zeroDiamCircle = SegmentUtility.getDiametralCircle(zeroSeg); // Center (1,1), radius 0
        Vertex otherPoint = new Vertex(2,2,0, 12);

        // Point is the segment itself (an endpoint). Endpoint check should dominate.
        // Note: zeroSeg.getA() and zeroSeg.getB() might be distinct objects but same coordinates.
        // For isPointEncroachingSegment, if point.getIndex() matches either endpoint's index, it's false.
        // Let's use zP, which is segment.A
        assertFalse(SegmentUtility.isPointEncroachingSegment(zP, zeroSeg, zeroDiamCircle, TOLERANCE),
             "Point that is an endpoint of zero-length segment should not encroach");
        // If we use a vertex that is identical to an endpoint but has a different index,
        // it could be considered encroaching if it's "strictly inside" the zero-radius circle.
        // Circle.isStrictlyInside(px,py,tol) for (0,0,0) circle:
        // (px-0)^2 + (py-0)^2 < 0 - tol. This is only true if tol is large and negative.
        // With positive tolerance, px^2+py^2 < -tol is never true. So points are never inside.
        Vertex pointAtZeroSegCenterDiffIndex = new Vertex(1,1,0, 99); // Same coords as zP, different index
        assertFalse(SegmentUtility.isPointEncroachingSegment(pointAtZeroSegCenterDiffIndex, zeroSeg, zeroDiamCircle, TOLERANCE),
             "Point at center of zero-length segment (diff index) should not encroach (not strictly inside zero-radius circle)");

        assertFalse(SegmentUtility.isPointEncroachingSegment(otherPoint, zeroSeg, zeroDiamCircle, TOLERANCE),
             "Point away from zero-length segment should not encroach");
    }
}
