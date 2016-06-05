/*
 * Copyright 2014 Gary W. Lucas.
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
 * 09/2014  G. Lucas     Created
 * 08/2015  G. Lucas     Migrated to main tin package
 * 11/2015  G. Lucas     Migrated to common package
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.common;

/**
 * An interface for classes that perform processing on a TIN.
 * <h1>Support for Parallel Processing</h1>
 * The design of use of this interface is intended to support cases
 * where multiple instances of a processing class may run in parallel
 * (in order to expedite completion).  For performance reasons very few
 * of the methods in the TIN-processing collection use synchronization.
 * Therefore, parallel processing requires that the TIN be accessed on
 * a read-only basis and not be modified by any other thread while the
 * processing methods are running.
 * <p>A class implementing this interface <strong>must not modify the
 * TIN in any way</strong>.
 */
public interface IProcessUsingTin {
    /**
     * Reset the processor due to a change in the TIN.  For processors that
     * maintain state data about the TIN in order to expedite processing,
     * this method provides a way to clear the state data.
     * <p>Reseting the state data unnecessarily may result in a
     * performance reduction when processing a large number of operations,
     * but is otherwise harmless.  Implementations are expected to be able
     * to run properly after a reset is called.
     */
    public void resetForChangeToTin();
}
