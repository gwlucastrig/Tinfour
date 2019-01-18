/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
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
 * 11/2018  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */ 
package org.tinfour.utils;

/**
 * Provides methods and elements for Kahan's algorithm for 
 * summing a set of numerical values with extended precision arithmetic.
 * Often, when adding a large set of small values to a large value,
 * the limited precision of computer arithmetic results in the contribution
 * of the small values being lost.  This limitation may result in a loss
 * of valuable data if the total sum of the collected small values is
 * large enough enough to make a meaningful contribution to the 
 * large value. Kahan's algorithm extends the precision of the computation
 * so that the contribution of small values is preserved.
 * 
 */
public class KahanSummation {
 private double c;  // compensator for Kahan summation  
 private double s;  // summand
 private int    n;
 
 /**
  * Add the value to the summation
  * @param a a valid floating-point number
  */
 public void add(double a){
       double y, t;
    y = a - c;
    t = s + y;
    c = (t - s ) - y;
    s = t; 
    n++;
 }
 
 /**
  * The current value of the summation.
  * @return the standard-precision part of the sum, 
  * a valid floating-point number.
  */
 public double getSum(){
   return s;
 }
 
 /**
  * Gets the mean value of the summands.
  * @return a valid floating-point value.
  */
 public double getMean(){
   if(n==0){
     return 0;
   }
   return s/n;
 }
 
 /**
  * Gets the number of summands that were added to the summation.
  * @return a value of zero or greater.
  */
 public int getSummandCount(){
   return n;
 }
 
}
