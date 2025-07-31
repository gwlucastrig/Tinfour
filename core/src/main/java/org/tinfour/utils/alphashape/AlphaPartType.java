/* --------------------------------------------------------------------
 * Copyright (C) 2025  Gary W. Lucas.
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
 * 07/2025  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.utils.alphashape;

/**
 * Specifies the geometry of an AlphaPart instance.
 */
public enum AlphaPartType {

  /**
   * Indicates that the part type is unknown; this state is only used
   * for improperly constructed parts.
   */
  Unspecified,

  /**
   * Indicates that a part is a closed polygon. For graphics applications,
   * instances with this part type may be used for area-fill operations.
   */
  Polygon,

  /**
   * Indicates that a part is a chain of one or more potentially disjoint
   * line segments that do not form a proper closed polygon. For graphics
   * applications, instances this part type may be used for draw operations
   * but should not be used for area-fill operations.
   */
  OpenLine,

  /**
   * Indicates that a part contains a set of unassociated vertices. Parts
   * with this type occur when small alpha radius values result in a set of
   * vertices that are not connected by edges to other vertices.
   */
  Vertices;

}
