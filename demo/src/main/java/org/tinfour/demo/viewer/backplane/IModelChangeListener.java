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
 * 08/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.demo.viewer.backplane;


/**
 * Defines a listener to be invoked when the model changes. This interface is
 * intended to be used when the UI includes elements that depend on a model.
 * If the user takes action to change the model, those elements may require
 * modification.
 * <p><strong>Note:</strong> Implementations of this interface are discouraged
 * to not maintain a reference to the model as a member element. Because models
 * may contain a very large amount of memory, the application should be
 * designed so that only one model is loaded at a time. The recommended
 * approach is for the listener to maintain a reference to the
 * DataViewingPanel and use it to obtain a reference to the model as
 * a local variable within the scope of methods that need it.
 */
public interface IModelChangeListener {

  /**
   * Invoked whenever a model is removed.  When a model is removed
   * there will always be a period of time during which no model is
   * available.
   */
  void modelRemoved();


  /**
   * Invoked whenever a model is added.
   * @param model a valid model
   */
  void modelAdded(IModel model);
}
