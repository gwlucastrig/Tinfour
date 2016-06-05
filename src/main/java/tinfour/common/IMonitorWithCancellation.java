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
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.common;


/**
 * Provides an interface that allows an process to report its progress to
 * a monitoring application and also permits the implementation of a
 * "voluntary cancellation" action.
 * <p>
 * This interface is designed with three goals in mind.
 * First, it is intended to support applications that run a graphical user
 * interface and have a need to monitor the progress of time-consuming
 * operations. Second, it should provide an application with a mechanism
 * for requesting that the time-consuming processes perform a voluntary
 * termination (in the event that a user action makes this necessary).
 * And finally, it explicitly avoids any dependency on a particular
 * user-interface environment such as Swing, JFace, or JavaFX.
 * <p>
 * Because time-consuming processes usually operate in background
 * threads rather than from within the Event Dispatch Thread (or equivalent),
 * implementations are expected to handle thread-safety issues
 * appropriately. For example, a class that extended the Java Swing JProgressBar
 * or BoundedRangeModel would invoke their setValue() methods using
 * the SwingUtility class to ensure the operation executed in the
 * Event Dispatch Thread.
 * <code>
 *     void reportProgress(int progressValueInPercent){
 *         final int value = progressValueInPercent;
 *         SwingUtilities.invokeLater(new Runnable(){
 *              setValue(value);
 *         });
 *     }
 * </code>
 *
 */
public interface IMonitorWithCancellation {

    /**
     * Gets the minimum interval for reporting progress, thus indicating
     * the frequency with which the reportProgress method should be
     * called. It is important to avoid reporting progress too often since
     * doing so could adversely affect the performance of the process.
     * Values smaller than 5 percent or greater than 25 percent are generally
     * not recommended.
     * @return a value between 1 and 100.
     */
    public int getReportingIntervalInPercent();

    /**
     * Report progress to monitoring implementation.  Note that in many
     * cases, this method may be used to adjust a user-interface
     * element such as a JProgressBar. Since this method is intended for
     * use by tasks that <strong>do not run in the Event Dispatching Thread</strong>
     * it is imperative that the implementation handle thread-safety
     * issues correctly.
     * @param progressValueInPercent the estimated degree of completion, in percent
     */
    public void reportProgress(int progressValueInPercent);

    /**
     * Called when the progress is done.  Within the Tinfour package
     * itself, this method is <strong>never</strong> called.
     * It is provided for use by applications that may invoke multiple
     * Tinfour methods reusing the progress monitor for each and reporting
     * done according to their own custom logic.
     */
    public void reportDone();


    /**
     * Permits the monitor to post a message for use by the calling
     * application. Both the handling and phrasing
     * of messages is left to the implementation class.
     * In general, it is recommended that messages be short strings suitable
     * for use in user interfaces and logs.
     * @param message a valid string
     */
    public void postMessage(String message);

    /**
     * Indicates whether the calling application would like this
     * process to terminate voluntarily.  Note that the processing code may elect
     * to not call this method or to ignore its result. So there is no
     * guarantee that having an implementation of this method return
     * "true" will ensure termination.
     * @return true if the process is canceled and should be voluntarily
     * interrupted; false if the process is not canceled.
     */
    public boolean isCanceled();
}
