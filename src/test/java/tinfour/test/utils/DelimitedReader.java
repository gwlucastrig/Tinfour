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
 * 02/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.test.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DelimitedReader {

  final InputStream fins;
  final BufferedInputStream bins;
  final int delimiter;

  public DelimitedReader(File file, char delimiter) throws IOException {
      fins = new FileInputStream(file);
      bins = new BufferedInputStream(fins);
      this.delimiter = delimiter;
  }

   public DelimitedReader(InputStream ins, char delimiter) throws IOException {
      fins = ins;
      bins = new BufferedInputStream(fins);
      this.delimiter = delimiter;
  }


  List<String> readStrings() throws IOException {
    int c;
    final StringBuilder sb= new StringBuilder();
    final List<String>sList = new ArrayList<>();
    while(true){
      c = bins.read();
      if (c < 0) { // End of File
        if(sb.length()>0){
          sList.add(sb.toString());
          sb.setLength(0);
        }
         break; // end of file
      }else if(c==0){
        continue;
      }else if(c==' ' && sb.length()==0){
        // skip leading spaces
        continue;
      }else if(c=='\n'){
        if(sb.length()>0){
          sList.add(sb.toString());
          sb.setLength(0);
        }
        // skip blank lines
        if(!sList.isEmpty()){
          break;
        }
      }else if(c==delimiter){
        if(delimiter==' ' && sb.length()==0){
           continue; // effectively skipping multiple blanks
        }
        sList.add(sb.toString());
        sb.setLength(0);
      }else if(!Character.isWhitespace(c)){
        sb.append((char)c);
      }
    }

    return sList;
  }

}
