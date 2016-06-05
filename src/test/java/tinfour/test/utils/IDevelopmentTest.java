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
 * 11/2015  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Defines an interface for running integration or development tests
 * used by the Tinfour library.
 */
public interface IDevelopmentTest {

  /**
   * Run the main test for the implementation, sending any output
   * to the specified print stream.
   * <p>
   * Many, but not all, implementations of this interface will
   * perform file I/O and may throw IOExceptions in the event of
   * an unrecoverable I/O exception.
   * <p>
   * The argument specification is often supplied from the command line.
   * Most implementations will throw an IllegalArgumentException
   * when supplied with an invalid or unrecognized argument.
   *
   * @param ps a valid print-stream for recording results of processing.
   * @param args a set of arguments for configuring the processing,
   * often accepted from the command line.
   * @throws java.io.IOException in the event of an unrecoverable
   * I/O error.
   */
  public void runTest(PrintStream ps, String[] args) throws IOException;

}
