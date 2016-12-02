/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 11/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.semivirtual;

import java.util.Iterator;
import tinfour.common.IQuadEdge;

/**
 * Provides an implementation of the pinwheel operation
 * via the Iterator and Iterable interfaces.
 */
class SemiVirtualPinwheel implements Iterable<IQuadEdge>, Iterator<IQuadEdge> {

  final IQuadEdge e0;
  IQuadEdge n;
  boolean hasNext;

  SemiVirtualPinwheel(IQuadEdge e0){
    this.e0 = e0;
    n = e0;
    hasNext = true;

  }

  @Override
  public Iterator<IQuadEdge> iterator() {
    return this;
  }

  @Override
  public boolean hasNext() {
     return hasNext;
  }

  @Override
  public IQuadEdge next() {
     IQuadEdge e = n;
     n = e.getDualFromReverse();
     hasNext = !n.equals(e0);
     return e;
  }

  @Override
  public void remove(){
    throw new UnsupportedOperationException(
      "Remove is not supported for IQuadEdge iterators");
  }

}
