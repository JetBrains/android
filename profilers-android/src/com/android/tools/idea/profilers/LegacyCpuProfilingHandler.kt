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
import com.android.ddmlib.IDevice
import com.android.tools.idea.profilers.LegacyCpuProfilingHandler.registerDevice
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace

/**
 * A singleton handler that implements [ClientData.setMethodProfilingHandler] since ddmlib only supports one such handler at a time.
 *
 * This handler keeps track of all devices by [registerDevice] and handles callbacks depending on the device from the given [Client].
 */
internal object LegacyCpuProfilingHandler : ClientData.IMethodProfilingHandler {
  /**
   * A device-to-(traceRecords, byteCache) map to for routing callbacks based on [Client]'s device.
   */
  private val deviceToProfilingMetadata = mutableMapOf<IDevice, Pair<Map<Int, LegacyCpuTraceRecord>, MutableMap<String, ByteString>>>()

  init {
    // ddmlib only supports one method profiling handler at a time.
    ClientData.setMethodProfilingHandler(this)
  }

  /**
   * Register a device with its associated trace records and byte cache so the handler knows where to route the callbacks.
   */
  fun registerDevice(device: IDevice, profilingRecords: Map<Int, LegacyCpuTraceRecord>, byteCache: MutableMap<String, ByteString>) {
    deviceToProfilingMetadata[device] = Pair(profilingRecords, byteCache)
  }

  override fun onSuccess(remoteFilePath: String, client: Client) {
    deviceToProfilingMetadata[client.device]?.let { (traceRecords, _) ->
      traceRecords[client.clientData.pid]?.let { record ->
        // Devices older than API 10 don't return profile results via JDWP. Instead they save the results on the
        // sdcard. We don't support this.
        val status = Trace.TraceStopStatus.newBuilder()
          .setStatus(Trace.TraceStopStatus.Status.CANNOT_COPY_FILE)
          .setErrorMessage("Method profiling: Older devices (API level < 10) are not supported. Please use DDMS.")
          .build()
        record.traceInfo!!.stopStatus = status
        record.stopLatch.countDown()
      }
    }
  }

  override fun onSuccess(data: ByteArray, client: Client) {
    deviceToProfilingMetadata[client.device]?.let { (traceRecords, byteCache) ->
      traceRecords[client.clientData.pid]?.let { record ->
        val traceInfo = record.traceInfo!!
        traceInfo.stopStatus = Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.SUCCESS).build()
        byteCache[traceInfo.traceId.toString()] = ByteString.copyFrom(data)
        record.stopLatch.countDown()
      }
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
    deviceToProfilingMetadata[client.device]?.let { (traceRecords, _) ->
      traceRecords[client.clientData.pid]?.let { record ->
        if (record.startLatch.count > 0) {
          record.startFailureMessage = message
          record.startLatch.countDown()
        }
        else {
          val status = Trace.TraceStopStatus.newBuilder()
            .setStatus(Trace.TraceStopStatus.Status.STOP_COMMAND_FAILED)
            .setErrorMessage("Failed to stop profiling: $message")
            .build()
          record.traceInfo!!.stopStatus = status
          record.stopLatch.countDown()
        }
      }
    }
  }
}
