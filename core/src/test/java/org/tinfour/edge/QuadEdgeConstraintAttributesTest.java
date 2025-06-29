
/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2025  Gary W. Lucas.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * -------   ---------    -------------------------------------------------
 * 06/2025   G. Lucas     Created for Issue 119
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.edge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import static org.tinfour.edge.QuadEdgeConstants.CONSTRAINT_INDEX_VALUE_MAX;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

 /**
  * Tests to verify that edges can accept and return attributes
  * related to constraint flag and index values.
 */

public class QuadEdgeConstraintAttributesTest {
 

EdgePool edgePool;
Vertex A;
Vertex B;
  
  public QuadEdgeConstraintAttributesTest() {
  }

 @BeforeEach
  public void setUp() {
	edgePool = new EdgePool();
	A = new Vertex(0, 0, 0, 0);
	B = new Vertex(1, 1, 1, 1);
  }
  

  @Test
  public void testBorderConstraint() {
	IQuadEdge e = edgePool.allocateEdge(A, B);
	IQuadEdge d = e.getDual();

	assertFalse(e.isConstraintRegionBorder());
	assertFalse(e.isConstraintRegionMember());
	assertFalse(e.isConstrained());
	assertFalse(e.isConstraintLineMember());
	assertEquals(-1, e.getConstraintBorderIndex());
	assertEquals(-1, d.getConstraintBorderIndex());
	
    // test population of border indices:
    //   do the indices get placed in the correct sides of edge?
    //   are there potential overflows of constraint values?
    //   when we change one side of the border, is the dual's value retained?
    e.setConstraintBorderIndex(7);
    assertEquals(7, e.getConstraintBorderIndex());
    assertEquals(-1, d.getConstraintBorderIndex());
    d.setConstraintBorderIndex(CONSTRAINT_INDEX_VALUE_MAX);
    assertEquals(7, e.getConstraintBorderIndex());
    assertEquals(CONSTRAINT_INDEX_VALUE_MAX, d.getConstraintBorderIndex());
    assertEquals(7, e.getConstraintIndex());
    assertEquals(CONSTRAINT_INDEX_VALUE_MAX, d.getConstraintIndex());
  }
 
	@Test
	public void testSetBorderConstraintFlag() {
		IQuadEdge e = edgePool.allocateEdge(A, B);
		e.setConstraintRegionBorderFlag();
		assertTrue(e.isConstrained());
		assertTrue(e.isConstraintRegionMember());
		assertTrue(e.isConstraintRegionBorder());
		assertFalse(e.isConstraintRegionInterior());
		assertFalse(e.isConstraintLineMember());
	}

	@Test
	public void testLineIndices() {
		IQuadEdge e = edgePool.allocateEdge(A, B);
		IQuadEdge d = e.getDual();
		e.setConstraintLineIndex(1066);
		assertTrue(e.isConstrained());
		assertFalse(e.isConstraintRegionMember());
		assertFalse(e.isConstraintRegionBorder());
		assertFalse(e.isConstraintRegionInterior());
		assertTrue(e.isConstraintLineMember());
		assertEquals(1066, e.getConstraintLineIndex());
		assertEquals(1066, d.getConstraintLineIndex());
	}

	@Test
	public void testLineAndInteriorIndices() {
		IQuadEdge e = edgePool.allocateEdge(A, B);
		IQuadEdge d = e.getDual();
		e.setConstraintLineIndex(1066);
		e.setConstraintRegionInteriorIndex(1776);
		assertEquals(1066, e.getConstraintLineIndex());
		assertEquals(1776, e.getConstraintRegionInteriorIndex());
		assertEquals(1066, d.getConstraintLineIndex());
		assertEquals(1776, d.getConstraintRegionInteriorIndex());

		// now see what happens if we reconfigure e to be a border constraint
		e.setConstraintBorderIndex(1);
		assertEquals(-1, e.getConstraintLineIndex());
		assertFalse(e.isConstraintRegionInterior());
		assertEquals(-1, e.getConstraintRegionInteriorIndex());
		assertEquals(1, e.getConstraintBorderIndex());
		assertEquals(-1, d.getConstraintBorderIndex());
	}

	public void testSetConstraintInterior() {
		IQuadEdge e = edgePool.allocateEdge(A, B);
		e.setConstraintRegionInteriorIndex(1976);
		assertFalse(e.isConstrained());
		assertFalse(e.isConstraintRegionMember());
		assertFalse(e.isConstraintRegionBorder());
		assertTrue(e.isConstraintRegionInterior());
		assertFalse(e.isConstraintLineMember());
	}
}
