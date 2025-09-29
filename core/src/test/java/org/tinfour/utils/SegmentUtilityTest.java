package org.tinfour.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;


public class SegmentUtilityTest {

    private static final double DELTA = 1e-12;
    private static final double TOLERANCE = 1e-12;

    static class MockEdge implements IQuadEdge {
        final Vertex a, b;
        final double length;
        final int index, baseIndex;

        MockEdge(Vertex a, Vertex b, int index) {
            this.a = a;
            this.b = b;
            this.index = index;
            this.length = (a != null && b != null) ? Math.hypot(a.getX() - b.getX(), a.getY() - b.getY()) : 0.0;
            this.baseIndex = index & ~1;
        }

        @Override public Vertex getA() { return a; }
        @Override public Vertex getB() { return b; }
        @Override public double getLength() { return length; }
        @Override public double getLengthSq() { return length * length; }
        @Override public int getIndex() { return index; }
        @Override public int getBaseIndex() { return baseIndex; }
        @Override public IQuadEdge getBaseReference() { return this; }
        @Override public IQuadEdge getDual() { return null; }
        @Override public IQuadEdge getForwardFromDual() { return null; }
        @Override public IQuadEdge getReverseFromDual() { return null; }
        @Override public int getSide() { return index & 1; }
        @Override public IQuadEdge getForward() { return null; }
        @Override public IQuadEdge getReverse() { return null; }
        @Override public IQuadEdge getDualFromReverse() { return null; }
        @Override public int getConstraintIndex() { return 0; }
        @Override public void setConstraintIndex(int constraintIndex) { }
        @Override public boolean isConstrained() { return false; }
        @Override public boolean isConstraintRegionMember() { return false; }
        @Override public boolean isConstraintLineMember() { return false; }
        @Override public void setConstraintLineMemberFlag() { }
        @Override public boolean isConstraintRegionInterior() { return false; }
        @Override public boolean isConstraintRegionBorder() { return false; }
        @Override public void setConstraintRegionBorderFlag() { }
        @Override public void setSynthetic(boolean status) { }
        @Override public boolean isSynthetic() { return false; }
        @Override public Iterable<IQuadEdge> pinwheel() { return java.util.Collections.emptyList(); }
        @Override public void setLine2D(AffineTransform transform, Line2D l2d) { }
        @Override public void transcribeToLine2D(AffineTransform transform, Line2D l2d) { }
        @Override public void setConstraintBorderIndex(int constraintIndex) { }
        @Override public void setConstraintLineIndex(int constraintIndex) { }
        @Override public void setConstraintRegionInteriorIndex(int constraintIndex) { }
        @Override public int getConstraintBorderIndex() { return 0; }
        @Override public int getConstraintRegionInteriorIndex() { return 0; }
        @Override public int getConstraintLineIndex() { return 0; }
    }

    @Test
    void testCircleConstructor() {
        Circle c1 = new Circle(1.0, 2.0, 5.0);
        assertEquals(1.0, c1.x, DELTA);
        assertEquals(2.0, c1.y, DELTA);
        assertEquals(5.0, c1.radius, DELTA);
        assertEquals(25.0, c1.radiusSq, DELTA);

        Circle c2 = new Circle(0.0, 0.0, 0.0);
        assertEquals(0.0, c2.x, DELTA);
        assertEquals(0.0, c2.y, DELTA);
        assertEquals(0.0, c2.radius, DELTA);
        assertEquals(0.0, c2.radiusSq, DELTA);

        Circle c3 = new Circle(0.0, 0.0, -3.0);
        assertEquals(-3.0, c3.radius, DELTA);
        assertEquals(9.0, c3.radiusSq, DELTA);
    }

    @Test
    void testCircleIsStrictlyInside() {
        Circle circle = new Circle(0.0, 0.0, 5.0);
        assertTrue(circle.isStrictlyInside(1.0, 1.0, TOLERANCE));
        assertFalse(circle.isStrictlyInside(10.0, 10.0, TOLERANCE));
        assertFalse(circle.isStrictlyInside(5.0, 0.0, TOLERANCE));
        assertFalse(circle.isStrictlyInside(5.0, 0.0, 0.0));
        assertTrue(circle.isStrictlyInside(4.9, 0.0, TOLERANCE));
        assertFalse(circle.isStrictlyInside(5.000001, 0.0, TOLERANCE));

        Circle zero = new Circle(0.0, 0.0, 0.0);
        assertFalse(zero.isStrictlyInside(0.0, 0.0, TOLERANCE));
        assertFalse(zero.isStrictlyInside(1.0, 0.0, TOLERANCE));
    }

    @Test
    void testGetDiametralCircle() {
        Vertex vH1 = new Vertex(0, 0, 0, 0);
        Vertex vH2 = new Vertex(4, 0, 0, 1);
        MockEdge segH = new MockEdge(vH1, vH2, 0);
        Circle cH = SegmentUtility.getDiametralCircle(segH);
        assertEquals(2.0, cH.x, DELTA);
        assertEquals(0.0, cH.y, DELTA);
        assertEquals(2.0, cH.radius, DELTA);

        Vertex vV1 = new Vertex(1, 1, 0, 2);
        Vertex vV2 = new Vertex(1, 5, 0, 3);
        MockEdge segV = new MockEdge(vV1, vV2, 2);
        Circle cV = SegmentUtility.getDiametralCircle(segV);
        assertEquals(1.0, cV.x, DELTA);
        assertEquals(3.0, cV.y, DELTA);
        assertEquals(2.0, cV.radius, DELTA);

        Vertex vD1 = new Vertex(0, 0, 0, 4);
        Vertex vD2 = new Vertex(3, 4, 0, 5);
        MockEdge segD = new MockEdge(vD1, vD2, 4);
        Circle cD = SegmentUtility.getDiametralCircle(segD);
        assertEquals(1.5, cD.x, DELTA);
        assertEquals(2.0, cD.y, DELTA);
        assertEquals(2.5, cD.radius, DELTA);

        Vertex vZ1 = new Vertex(1, 1, 0, 6);
        Vertex vZ2 = new Vertex(1, 1, 0, 7);
        MockEdge segZ = new MockEdge(vZ1, vZ2, 6);
        assertEquals(0.0, segZ.getLength(), DELTA);
        Circle cZ = SegmentUtility.getDiametralCircle(segZ);
        assertEquals(1.0, cZ.x, DELTA);
        assertEquals(1.0, cZ.y, DELTA);
        assertEquals(0.0, cZ.radius, DELTA);

        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(null));

        Vertex validVertex = new Vertex(0, 0, 0, 8);
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(new MockEdge(null, validVertex, 8)));
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.getDiametralCircle(new MockEdge(validVertex, null, 10)));
    }

    @Test
    void testIsPointEncroachingSegment() {
        Vertex pA = new Vertex(0, 0, 0, 0);
        Vertex pB = new Vertex(4, 0, 0, 1);
        MockEdge segment = new MockEdge(pA, pB, 0);
        Circle dc = SegmentUtility.getDiametralCircle(segment);

        Vertex inside = new Vertex(2, 0.5, 0, 2);
        assertTrue(SegmentUtility.isPointEncroachingSegment(inside, segment, dc, TOLERANCE));

        Vertex outside = new Vertex(2, 3, 0, 3);
        assertFalse(SegmentUtility.isPointEncroachingSegment(outside, segment, dc, TOLERANCE));

        assertFalse(SegmentUtility.isPointEncroachingSegment(pA, segment, dc, TOLERANCE));
        assertFalse(SegmentUtility.isPointEncroachingSegment(pB, segment, dc, TOLERANCE));
        assertFalse(SegmentUtility.isPointEncroachingSegment(new Vertex(0, 0, 0, 0), segment, dc, TOLERANCE));

        Vertex onBoundary = new Vertex(2, 2, 0, 4);
        assertFalse(SegmentUtility.isPointEncroachingSegment(onBoundary, segment, dc, TOLERANCE));

        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(null, segment, dc, TOLERANCE));
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(inside, null, dc, TOLERANCE));
        assertThrows(IllegalArgumentException.class, () -> SegmentUtility.isPointEncroachingSegment(inside, segment, null, TOLERANCE));

        Vertex zP = new Vertex(1, 1, 0, 10);
        MockEdge zeroSeg = new MockEdge(zP, new Vertex(1, 1, 0, 11), 100);
        Circle zeroDC = SegmentUtility.getDiametralCircle(zeroSeg);
        Vertex other = new Vertex(2, 2, 0, 12);

        assertFalse(SegmentUtility.isPointEncroachingSegment(zP, zeroSeg, zeroDC, TOLERANCE));
        assertFalse(SegmentUtility.isPointEncroachingSegment(new Vertex(1, 1, 0, 99), zeroSeg, zeroDC, TOLERANCE));
        assertFalse(SegmentUtility.isPointEncroachingSegment(other, zeroSeg, zeroDC, TOLERANCE));
    }
}
