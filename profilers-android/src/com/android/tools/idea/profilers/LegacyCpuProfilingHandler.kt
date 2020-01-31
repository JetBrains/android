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
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Cpu

internal class LegacyCpuProfilingHandler(private val profilingRecords: Map<Int, LegacyCpuTraceRecord>,
                                         private val byteCache: MutableMap<String, ByteString>) : ClientData.IMethodProfilingHandler {

  override fun onSuccess(remoteFilePath: String, client: Client) {
    val record = profilingRecords[client.clientData.pid]
    if (record != null) {
      // Devices older than API 10 don't return profile results via JDWP. Instead they save the results on the
      // sdcard. We don't support this.
      val status = Cpu.TraceStopStatus.newBuilder()
      .setStatus(Cpu.TraceStopStatus.Status.CANNOT_COPY_FILE)
        .setErrorMessage("Method profiling: Older devices (API level < 10) are not supported. Please use DDMS.")
        .build()
      record.traceInfo!!.stopStatus = status
      record.stopLatch.countDown()
    }
  }

  override fun onSuccess(data: ByteArray, client: Client) {
    val record = profilingRecords[client.clientData.pid]
    if (record != null) {
      record.traceInfo!!.stopStatus = Cpu.TraceStopStatus.newBuilder().setStatus(Cpu.TraceStopStatus.Status.SUCCESS).build()
      byteCache[record.traceInfo!!.traceId.toString()] = ByteString.copyFrom(data)
      record.stopLatch.countDown()
    }
  }

  /**
   * The interface says this callback is "called when method tracing failed to start". It may be true
   * for API < 10 (not having FEATURE_PROFILING_STREAMING), but there appears no executing path trigger it
   * for API >= 10.
   *
   * @param client  the client that was profiled.
   * @param message an optional (`null` ok) error message to be displayed.
   */
  override fun onStartFailure(client: Client, message: String) {}

  /**
   * The interface says "Called when method tracing failed to end on the VM side", but in reality
   * it is called when either start or end fails. Therefore, we rely on whether
   * (record.myStartLatch) is still valid to distinguish it is a start or stop failure,
   *
   * @param client  the client that was profiled.
   * @param message an optional (`null` ok) error message to be displayed.
   */
  override fun onEndFailure(client: Client, message: String) {
    val record = profilingRecords[client.clientData.pid]
    if (record != null) {
      if (record.startLatch.getCount() > 0) {
        record.startFailureMessage = message
        record.startLatch.countDown()
      }
      else {
        val status = Cpu.TraceStopStatus.newBuilder()
          .setStatus(Cpu.TraceStopStatus.Status.STOP_COMMAND_FAILED)
          .setErrorMessage("Failed to stop profiling: $message")
          .build()
        record.traceInfo!!.stopStatus = status
        record.stopLatch.countDown()
      }
    }
  }
}
