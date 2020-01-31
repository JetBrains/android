/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.profilers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.function.Consumer

class FakeLegacyAllocationTracker() : LegacyAllocationTracker {
  /**
   * Auxiliary static test data used for verifying legacy allocation tracking workflow
   */
  companion object {
    val RAW_DATA = byteArrayOf('a'.toByte())
  }

  val parsingWaitLatch = CountDownLatch(1)
  val parsingDoneLatch = CountDownLatch(1)
  var trackingState = false
  var returnNullTrackingData = false

  override fun trackAllocations(enabled: Boolean, executor: Executor?, allocationConsumer: Consumer<ByteArray?>?): Boolean {
    trackingState = enabled
    if (!enabled) {
      Thread {
        try {
          parsingWaitLatch.await()
        }
        catch (ignored: InterruptedException) {
        }

        allocationConsumer!!.accept(if (returnNullTrackingData) null else RAW_DATA)
        parsingDoneLatch.countDown()
      }.start()
    }

    return true
  }
}