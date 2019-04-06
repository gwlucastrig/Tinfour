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

package org.tinfour.io;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility for reading a file consisting of strings separated
 * by a delimiter character, such as the classic comma-separated-value (CSV)
 * or tab-delimited file formats. Strings are extracted with leading and
 * trailing white space removed. Embedded white space characters are
 * preserved.
 */
public class DelimitedReader implements Closeable {

  final InputStream fins;
  final BufferedInputStream bins;
  final int delimiter;
  int lineIndex;

  /**
   * Open a file and prepare to read it using the specified
   * delimiter character.
   * @param file a valid file reference
   * @param delimiter a delimiter character
   * @throws IOException in the event of an unsuccessful I/O operation
   */
  public DelimitedReader(File file, char delimiter) throws IOException {
      fins = new FileInputStream(file);
      bins = new BufferedInputStream(fins);
      this.delimiter = delimiter;

  }

  /**
   * Prepares to read an input stream using the specified
   * delimiter character.
   * @param ins a valid input stream reference
   * @param delimiter a delimiter character
   * @throws IOException in the event of an unsuccessful I/O operation
   */
   public DelimitedReader(InputStream ins, char delimiter) throws IOException {
      fins = ins;
      bins = new BufferedInputStream(fins);
      this.delimiter = delimiter;
  }

  /**
   * Read a row of strings from the file
   * @return a list of strings (potentially empty)
   * @throws IOException in the event of an unsuccessful I/O operation.
   */
  public List<String> readStrings() throws IOException {
    final List<String>sList = new ArrayList<String>();
    readStrings(sList);
    return sList;
  }


   /**
   * Read a row of strings from the file, storing the results in 
   * a reusable list.  Each call to this routine clears any content
   * that may already be in the list before extracting it from the file.
   * @param sList a list in which the strings will be stored
   * @return the number of strings that were stored in the list;
   * zero at the end of the file.
   * @throws IOException in the event of an unsuccessful I/O operation.
   */
  public int readStrings(final List<String>sList) throws IOException {
    int c;
    final StringBuilder sb= new StringBuilder();
    sList.clear();
    boolean newLine = true;
    while(true){
      c = bins.read();
      if (c < 0) { // End of File
        if(sb.length()>0){
          sList.add(sb.toString());
          sb.setLength(0);
        }
         break; // end of file
      }
      if(c==0){
        continue;
      }
      if(newLine){
        newLine=false;
        lineIndex++;
      }
      if(c==' ' && sb.length()==0){
        // skip leading spaces
        continue;
      }else if(c=='\n'){
        newLine=true;
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
    return sList.size();
  }



  /**
   * CLoses all open IO channels.
   * @throws IOException in the event of an unexpected I/O condition.
   */
  @Override
  public void close() throws IOException {
    bins.close();
    fins.close();
  }


  /**
   * Gets the current position in the file as a line number.
   * @return a value from 1 to the number of lines in the file.
   */
  public int getLineNumber(){
    return lineIndex;
  }
}
