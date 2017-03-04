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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.edge;

/**
 * Defines constants for use in QuadEdge related operations.
 */
public class QuadEdgeConstants {

  private QuadEdgeConstants(){
    // a private constructor to deter applications from
    // constructing instances of this class.
  }

  /**
   * The maximum value of a constraint index based on the three bytes
   * allocated for its storage. This is a value of 16777215, or 2^24-1.
   * In practice this value is larger than the available
   * memory on many contemporary computers would allow.
   */
  public static final int CONSTRAINT_INDEX_MAX = ((1 << 24) - 1); // 16777215

  /**
   * A mask that can be anded with the QuadEdgePartner's
   * index field to extract the constraint index,
   * equivalent to the 24 low-order bits.
   */
  public static final int CONSTRAINT_INDEX_MASK = 0x00ffffff;

  /**
   * A bit indicating that an edge is constrained. This bit just happens
   * to be the sign bit, a feature that is exploited by the isConstrained()
   * method.
   */
  public static final int CONSTRAINT_FLAG = (1 << 31);

  /**
   * A bit indicating that an edge is part of a constrained area.
   */
  public static final int CONSTRAINT_AREA_FLAG = (1 << 30);

  /**
   * A bit indicating that the constrained area is to the base side
   * of the edge. This bit is only meaningful when CONSTRAINT_AREA_FLAG is set.
   * If CONSTRAINT_AREA_FLAG is set, then this bit tells which side the
   * constraint area lies on: if the bit is set, it's on the base side
   * and if the bit is clear, it's on the dual side.
   */
  public static final int CONSTRAINT_AREA_BASE_FLAG = (1 << 29);
}
