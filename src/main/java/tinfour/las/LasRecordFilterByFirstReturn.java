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
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.las;

/**
 * An example implementation of a vertex filter, accepts all
 * records give first-return data and which
 * are not marked as "withheld".
 */
public class LasRecordFilterByFirstReturn implements ILasRecordFilter {

    /**
     * Constructs a filter that accepts only records with the
     * return number set to 1.
     */
    public LasRecordFilterByFirstReturn( ){
      // empty constructor
    }
    @Override
    public boolean accept(LasPoint record) {
       // on the theory that withheld records are relatively uncommon
        // test on the return number first
       if(record.returnNumber == 1){
           return record.withheld^=true;
       }
       return false;
    }
}
