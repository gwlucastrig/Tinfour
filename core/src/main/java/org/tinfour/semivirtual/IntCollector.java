/*
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
 */


/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.semivirtual;

/**
 * A simple class for collecting an array of integers.  We use this custom
 * implementation rather than Java's ArrayList because the Java collection
 * class maintains a list of objects. This class maintains a list of
 * data primitives.  The equivalent use of ArrayList would involve storing
 * instances of java.lang. Integer, at the cost of 16 bytes each. This
 * class uses roughly 1/4th of that.
 */
class IntCollector {
  int []buffer = new int[0];
  int n;


  /**
   * Add an integer value to the collection
   * @param ix the integer to be added
   */
  void add(int ix){
    if(n==buffer.length){
      int []temp = new int[buffer.length+256];
      System.arraycopy(buffer, 0, temp, 0, buffer.length);
      buffer = temp;
    }
    buffer[n++] = ix;
  }

  /**
   * Trim the collection to the size of the number of entries.
   */
   void trimToSize(){
     if(buffer.length>n){
       int []temp = new int[n];
       System.arraycopy(buffer, 0, temp, 0, n);
       buffer = temp;

     }
   }
}
