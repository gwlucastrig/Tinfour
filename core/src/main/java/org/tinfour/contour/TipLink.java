/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 08/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

/**
 * Provides node definitions for a linked list of the tips for a single
 * perimeter edge.
 */
class TipLink {

  /**
   * True if the contour starts on the tip; otherwise false.
   */
  final boolean start;

  /**
   * True if the contour terminates on the tip; otherwise, false.
   */
  final boolean termination;

  /**
   * The contour
   */
  final Contour contour;

  final PerimeterLink pLink;

  final int sweepIndex;

  TipLink next;
  TipLink prior;

  TipLink(PerimeterLink pLink, Contour contour, boolean start, int sweepIndex) {
    this.contour = contour;
    this.pLink = pLink;
    this.start = start;
    this.termination = !start;
    this.sweepIndex = sweepIndex;
  }

}
