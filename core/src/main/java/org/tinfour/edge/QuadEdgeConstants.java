/* --------------------------------------------------------------------
 * Copyright 2017 Gary W. Lucas.
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
 * 03/2017  G. Lucas     Created
 * 06/2025  G. Lucas     Refactored for better handling of constraint relationshipes
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.edge;

/**
 * Defines constants for use in QuadEdge related operations.
 * At this time, the following constants are defined
 * <pre><code>
 * CONSTRAINT_FLAG_MASK              0xf8000000
 * CONSTRAINT_EDGE_FLAG              0x80000000
 * CONSTRAINT_REGION_BORDER_FLAG     0x40000000
 * CONSTRAINT_REGION_INTERIOR_FLAG   0x20000000
 * CONSTRAINT_LINE_MEMBER_FLAG       0x10000000
 * SYNTHETIC_EDGE_FLAG               0x08000000
 * EDGE_FLAG_RESERVED_BIT            0x04000000
 * CONSTRAINT_REGION_MEMBER_FLAGS    0x60000000
 *
 * CONSTRAINT_INDEX_BIT_SIZE                 13
 * CONSTRAINT_INDEX_VALUE_MAX              8190
 *
 * CONSTRAINT_LOWER_INDEX_MASK       0x00001fff
 * CONSTRAINT_LOWER_INDEX_ZERO       0xffffe000
 * CONSTRAINT_UPPER_INDEX_MASK       0x03ffe000
 * CONSTRAINT_UPPER_INDEX_ZERO       0xfc001fff
 * </code></pre>
 */
public final class QuadEdgeConstants {

  private QuadEdgeConstants() {
    // a private constructor to deter applications from
    // constructing instances of this class.
  }

  /**
   * A mask for preserving the bits allocated for edge-related flags
   * At this time, there are definitions for 5 flags with one bit reserved
   * for future use.
   */
  public static final int CONSTRAINT_FLAG_MASK = 0xf8000000;

  /**
   * Defines the bit that is not yet committed for representing edge status.
   * This value is equivalent to bit 26.
   */
  public static final int EDGE_FLAG_RESERVED_BIT = 1 << 26;

  /**
   * The number of bits committed to the storage of a constraint index.
   * Tinfour reserves space to store the constraint index values for
   * the left and right side of a border constraint. Constraint indices
   * are stored in the "index" element of the QuadEdgePartner class.
   * The high order 5 bits are committed to various flags. So that
   * leaves 27 bits available for constraint information. Since storage is
   * required for two potential indices (left and right), thirteen bits
   * are available for each.
   */
  public static final int CONSTRAINT_INDEX_BIT_SIZE = 13;

  /**
   * The maximum value of a constraint index based on the 13 bits
   * allocated for its storage. This would be a value of 8191, or 2^13-1.
   * But QuadEdge reserves the value -1, bit state 0, to represent a null
   * specification. For valid constraint indices, the QuadEdge implementation
   * stores the constraint value plus one. That makes the maximum value 2^13-2
   */
  public static final int CONSTRAINT_INDEX_VALUE_MAX = (1 << CONSTRAINT_INDEX_BIT_SIZE) - 2;

  /**
   * A bit indicating that an edge is constrained. This bit just happens
   * to be the sign bit, a feature that is exploited by the isConstrained()
   * method.
   */
  public static final int CONSTRAINT_EDGE_FLAG = 1 << 31;

  /**
   * A bit indicating that the edge is the border of a constrained region
   */
  public static final int CONSTRAINT_REGION_BORDER_FLAG = 1 << 30;

  /**
   * A bit indicating that an edge is in the interior of a constrained region.
   */
  public static final int CONSTRAINT_REGION_INTERIOR_FLAG = 1 << 29;

  /**
   * A bit indicating that an edge is part of a non-region constraint line.
   * Edges are allowed to be both an interior and a line, so a separate flag bit
   * is required for both cases.
   */
  public static final int CONSTRAINT_LINE_MEMBER_FLAG = 1 << 28;

  /**
   * A set of bits combining the constraint region interior and border flags.
   */
  public static final int CONSTRAINT_REGION_MEMBER_FLAGS
    = CONSTRAINT_REGION_BORDER_FLAG | CONSTRAINT_REGION_INTERIOR_FLAG;

  /**
   * A bit indicating that an edge has been marked as synthetic.
   */
  public static final int SYNTHETIC_EDGE_FLAG = 1 << 27;

  /**
   * A specification for using an AND operation to zero out the lower field of
   * bits that contain a constraint index. Used in preparation for storing a
   * new value.
   */
  public static final int CONSTRAINT_LOWER_INDEX_ZERO = (0xffffffff << CONSTRAINT_INDEX_BIT_SIZE);

  /**
   * A specification for using an AND operation to extract the lower field of
   * bits  that contain a constraint index.
   */
  public static final int CONSTRAINT_LOWER_INDEX_MASK = ~CONSTRAINT_LOWER_INDEX_ZERO;

  /**
   * A specification for using an AND operation to extract the upper field of
   * bits that contain a constraint index.
   */
  public static final int CONSTRAINT_UPPER_INDEX_MASK = CONSTRAINT_LOWER_INDEX_MASK << CONSTRAINT_INDEX_BIT_SIZE;

  /**
   * A specification for using an AND operation to zero out the upper-field of
   * bits that contain a constraint index. Used in preparation for storing a
   * new value.
   */
  public static final int CONSTRAINT_UPPER_INDEX_ZERO = ~CONSTRAINT_UPPER_INDEX_MASK;

}
