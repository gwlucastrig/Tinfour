package org.tinfour.standard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

/**
 * Test various edge iterators for correctness
 */
public class EdgeIteratorTest {

    public EdgeIteratorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {

    }

    private static IncrementalTin setupTin() {
        IncrementalTin tin = new IncrementalTin(1.0);
        List<Vertex> vList = new ArrayList<>();
        Random r = new Random(0);
        for (int i = 0; i < 23; i++) {
            double x = r.nextDouble();
            double y = r.nextDouble();
            double z = x * x + y * y - (x + y) - 0.5;  // NOPMD (x-0.5)^2 + (y-0.5)^2
            vList.add(new Vertex(x, y, z, i)); //NOPMD
        }
        tin.add(vList, null);
        return tin;
    }

    private static boolean[] initFlags(IIncrementalTin tin, List<IQuadEdge> masterList) {
        // Internally, the incremental TIN classes getEdges() method
        // use a simple loop to collect a master list of all edges.  For these
        // tests, this list is used as a baseline against which we test
        // the edge iterators for correctness.
        int n = tin.getMaximumEdgeAllocationIndex();
        boolean[] flags = new boolean[n];

        for (IQuadEdge e : masterList) {
            Vertex A = e.getA();
            Vertex B = e.getB();
            // The edge iterators do not produce "ghost" edges
            // so those are screened out when populating the flags.
            if (A != null && B != null) {
                flags[e.getIndex()] = true;
                flags[e.getIndex() ^ 1] = true;
            }
        }
        return flags;
    }

    @Test
    public void testEdgesAndDualsCoverage() {

        IncrementalTin tin = setupTin();
        List<IQuadEdge> masterList = tin.getEdges();

        // Test the edges and duals iterator --------------------
        boolean[] flags = initFlags(tin, masterList);

        for (IQuadEdge e : tin.edgesAndDuals()) {
            int index = e.getIndex();
            assertTrue(flags[index], "edgesAndDuals() produced unpopulated index: " + index);
            flags[index] = false;
        }

        // all flags should have been clears by the above loop
        for (int i = 0; i < flags.length; i++) {
            assertFalse(flags[i], "edgesAndDuals() did not produce populated index: " + i);
        }
    }

    @Test
    public void testEdgesCoverage() {
        IncrementalTin tin = setupTin();
        List<IQuadEdge> masterList = tin.getEdges();

        // Test the edges iterator --------------------
        // by design,  edges() produces only base edges (even-numbered edges)
        boolean[] flags = initFlags(tin, masterList);

        for (IQuadEdge e : tin.edges()) {
            int index = e.getIndex();
            assertTrue(flags[index], "edges() produced unpopulated index: " + index);
            flags[index] = false;
            index = index ^ 1;
            assertTrue(flags[index], "edges() produced unpopulated index: " + index);
            flags[index] = false;
        }

        // all flags should have been clears by the above loop
        for (int i = 0; i < flags.length; i++) {
            assertFalse(flags[i], "edges() did not produce populated index: " + i);
        }
    }

}
