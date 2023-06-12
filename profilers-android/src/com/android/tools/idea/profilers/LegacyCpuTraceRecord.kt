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

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.tools.profiler.proto.Trace
import java.util.concurrent.CountDownLatch

/**
 * Metadata of an ongoing profiling session of an app.
 */
internal class LegacyCpuTraceRecord {
  // Set after trace start is successful.
  var traceInfo: Trace.TraceInfo.Builder? = null
  var startFailureMessage = ""

  /**
   * The latch that the profiler waits on when sending a start profiling request.
   * If the start fails, LegacyCpuProfilingHandler.onEndFailure(..) would be triggered which
   * counts down the latch. There is no known way to count down if the start succeeds.
   */
  val startLatch = CountDownLatch(1)
  /**
   * The latch that the profiler waits on when sending a stop profiling request.
   * If the stop succeeds, LegacyCpuProfilingHandler.onSuccess(..) would be triggered which
   * counts down the latch. If the stop fails, LegacyCpuProfilingHandler.onEndFailure(..)
   * would be triggered which counts down the latch.
   */
  val stopLatch = CountDownLatch(1)

  val isStartFailed: Boolean
    get() = !startFailureMessage.isEmpty()

  companion object {

    /**
     * This method returns true if the method profiling status is off for art traces only. For all other trace types (mainly systrace) we
     * return false because method profiling is not an available feature.
     */
    fun isMethodProfilingStatusOff(record: LegacyCpuTraceRecord?, client: Client): Boolean {
      return record == null || client.clientData.methodProfilingStatus == ClientData.MethodProfilingStatus.OFF
    }
  }
}
