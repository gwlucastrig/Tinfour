/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 02/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.las;

/**
 * An example implementation of a vertex filter, accepts all
 * records have the specified classification and which
 * are not marked as "withheld".
 */
public class LasRecordFilterByClass implements ILasRecordFilter {
    int classification;

    /**
     * Construction a filter that accepts only records with the
     * specified classification.
     * @param classification a value in the range 0 to 255 (values in
     * the range 0 to 16 are the most common).
     */
    public LasRecordFilterByClass(int classification){
        this.classification = classification;
    }
    @Override
    public boolean accept(LasPoint record) {
       // on the theory that withheld records are relatively uncommon
        // test on classification first
       if(record.classification == classification){
           return record.withheld^=true;
       }
       return false;
    }
}
