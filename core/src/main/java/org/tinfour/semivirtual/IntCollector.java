/* --------------------------------------------------------------------
 * Copyright (C) 2017 Gary W. Lucas.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ---------------------------------------------------------------------
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

package tinfour.semivirtual;

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
