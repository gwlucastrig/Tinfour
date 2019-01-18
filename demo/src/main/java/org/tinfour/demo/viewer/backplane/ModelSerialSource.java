/*-----------------------------------------------------------------------
 *
 * Copyright (C) 2017 Sonalysts Inc. All Rights Reserved.
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 01/2017  G. Lucas     Created
 *
 * Notes:
 *
 *--------------------------------------------------------------------------
 */

package org.tinfour.demo.viewer.backplane;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a serial index to be used by all models as a diagnostic tool.
 * Each time a model is constructed, it receives an incremented integer
 * serial index value. This class is intended strictly as an aid to
 * debugging.
 */
final class ModelSerialSource {

  private ModelSerialSource() {
    // to deter application from explicityly constructing an instance.
  }

  private static final AtomicInteger serialIndexSource = new AtomicInteger(0);

  static int getSerialIndex() {
    return serialIndexSource.incrementAndGet();
  }

}
