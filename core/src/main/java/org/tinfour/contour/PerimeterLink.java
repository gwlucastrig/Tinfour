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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.tinfour.common.IQuadEdge;

/**
 * Provides elements for tracking when contours interset perimeter edges
 */
class PerimeterLink {

  /**
   * A diagnostic value used to record the position of the link within the
   * perimeter chain.
   */
  final int index;

  /**
   * The interior edge of the perimeter;
   */
  final IQuadEdge edge;

  /**
   * A reference to the next perimeter edge in a counter-clockwise (left)
   * direction.
   */
  PerimeterLink next;
  /**
   * A reference to the prior perimeter edge in a counter-clockwise (left)
   * direction.
   */
  PerimeterLink prior;

  /**
   * The tip closest to the start of the edge
   */
  TipLink tip0;

  /**
   * The tip closest to the termination of the edge
   */
  TipLink tip1;

  ArrayList<TipLink> tempList = new ArrayList<>();

  PerimeterLink(int index, IQuadEdge edge) {
    this.index = index;
    this.edge = edge;
  }

  void addContourTip(Contour contour, boolean contourStart, int sweepIndex) {
    TipLink tip = new TipLink(this, contour, contourStart, sweepIndex);

    if (contourStart) {
      contour.startTip = tip;
    } else {
      contour.terminalTip = tip;
    }

    if (sweepIndex != 0) {
      tempList.add(tip);
      return;
    }

    if (tip0 == null) {
      tip0 = tip;
      tip1 = tip;
    } else {
      if (contourStart) {
        // the tip is the start of a contour,
        // it should be on a descending edge (A.z > B.Z)
        // prepend the new tip to the linked-list of tips
        tip.next = tip0;
        tip0.prior = tip;
        tip0 = tip;
      } else {
        // the tip is the terminatation of a contour
        // it should be on an ascending edge (A.Z < B.Z)
        // append the new tip to the linked-list of teps
        tip.prior = tip1;
        tip1.next = tip;
        tip1 = tip;
      }
    }
  }

  void prependThroughVertexTips() {
    if (tempList.isEmpty()) {
      return;
    }

    Collections.sort(tempList, new Comparator<TipLink>() {
      @Override
      public int compare(TipLink o1, TipLink o2) {
        return Integer.compare(o1.sweepIndex, o2.sweepIndex);
      }
    });

    for (TipLink tip : tempList) {
      if (tip0 == null) {
        tip0 = tip;
        tip1 = tip;
      } else {
        tip.next = tip0;
        tip0.prior = tip;
        tip0 = tip;
      }
    }

    tempList.clear();
  }

  @Override
  public String toString() {
    if (prior == null || next == null) {
      return "Perimeter link " + index + ": " + edge.getIndex() + " (no links)";
    }
    return "Perimeter link " + index + ": "
      + prior.edge.getIndex()
      + " <- " + edge.getIndex()
      + " -> " + next.edge.getIndex();
  }
}
