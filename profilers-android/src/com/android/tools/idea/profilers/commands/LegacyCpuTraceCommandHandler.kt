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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.IDevice
import com.android.tools.idea.profilers.LegacyCpuProfilingHandler
import com.android.tools.idea.profilers.LegacyCpuTraceRecord
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.diagnostic.Logger
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.profiler.proto.Trace
import java.util.concurrent.BlockingDeque
import java.util.concurrent.TimeUnit

class LegacyCpuTraceCommandHandler(val device: IDevice,
                                   private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub,
                                   private val eventQueue: BlockingDeque<Common.Event>,
                                   byteCache: MutableMap<String, ByteString>)
  : TransportProxy.ProxyCommandHandler {

  private fun getLogger(): Logger {
    return Logger.getInstance(LegacyCpuTraceCommandHandler::class.java)
  }

  /**
   * Map from process id to the record of the profiling.
   * Existence in the map means there is an active ongoing profiling for that given app.
   *
   * Note - in StudioLegacyCpuTraceProfiler, this map is protected as it can be accessed from multiple threads (e.g. via the legacy
   * {@link LegacyCpuTraceProfiler#getTraceInfo} API. We don't need synchronization in the new pipeline because events are streamed
   * immediately within the command handlers.
   */
  private val legacyProfilingRecord = HashMap<Int, LegacyCpuTraceRecord>()

  init {
    // Register this command handler's device and associated metadata on the singleton ddmlib profiling handler.
    LegacyCpuProfilingHandler.registerDevice(device, legacyProfilingRecord, byteCache)
  }

  override fun shouldHandle(command: Commands.Command): Boolean {
    // We only handle ART traces in the proxy layer. ATraces are handled via the device daemon and all other trace options are unsupported
    // in pre-O devices.
    return when (command.type) {
      Commands.Command.CommandType.START_CPU_TRACE -> {
        command.startCpuTrace.configuration.userOptions.traceType == Trace.TraceType.ART
      }
      Commands.Command.CommandType.STOP_CPU_TRACE -> {
        command.stopCpuTrace.configuration.userOptions.traceType == Trace.TraceType.ART
      }
      else -> false
    }
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    when (command.type) {
      Commands.Command.CommandType.START_CPU_TRACE -> startTrace(command)
      Commands.Command.CommandType.STOP_CPU_TRACE -> stopTrace(command)
    }

    return Transport.ExecuteResponse.getDefaultInstance()
  }

  private fun startTrace(command: Commands.Command) {
    val traceConfiguration = command.startCpuTrace.configuration
    val userOptions = traceConfiguration.userOptions
    assert(userOptions.traceType == Trace.TraceType.ART)

    val pid = command.pid
    val appPkgName = device.getClientName(pid)
    val client = if (appPkgName != null) device.getClient(appPkgName) else null

    if (client == null) {
      val status = Trace.TraceStartStatus.newBuilder().apply {
        status = Trace.TraceStartStatus.Status.FAILURE
        errorMessage = "App is not running"
      }.build()
      sendStartStatusEvent(command, status)
    }
    else {
      if (!LegacyCpuTraceRecord.isMethodProfilingStatusOff(legacyProfilingRecord.get(pid), client)) {
        val status = Trace.TraceStartStatus.newBuilder().apply {
          status = Trace.TraceStartStatus.Status.FAILURE
          errorMessage = "Start request ignored. The app has an on-going profiling session."
        }.build()
        sendStartStatusEvent(command, status)
      }
      else {
        // com.android.ddmlib.HandleProfiling.sendSPSS(..) has buffer size as a parameter, but we cannot call it
        // because the class is not public. To set buffer size, we modify DdmPreferences which will be read by
        // client.startSamplingProfiler(..) and client.startMethodTracer().
        DdmPreferences.setProfilerBufferSizeMb(userOptions.bufferSizeInMb)

        val requestTimeNs = transportStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance()).timestampNs
        val record = LegacyCpuTraceRecord()
        legacyProfilingRecord.put(pid, record)
        try {
          if (userOptions.traceMode == Trace.TraceMode.SAMPLED) {
            client.startSamplingProfiler(userOptions.samplingIntervalUs, TimeUnit.MICROSECONDS)
          }
          else {
            client.startMethodTracer()
          }

          // startSamplingProfiler() and startMethodTracer() calls above always return immediately.
          // In case there is an error, ClientData.IMethodProfilingHandler.onEndFailure(..) will be called and the
          // responseBuilder has been populated there. Because IMethodProfilingHandler has no callback at success,
          // we limit the waiting to 0.1 second which is usually sufficient for the error handling to complete.
          record.startLatch.await(100, TimeUnit.MILLISECONDS)
          // The start latch is used to determine whether onEndFailure is called for start or stop tracing,
          // so we countDown here to indicate that we are done processing the start callback.
          record.startLatch.countDown()
          if (record.isStartFailed) {
            val status = Trace.TraceStartStatus.newBuilder().apply {
              status = Trace.TraceStartStatus.Status.FAILURE
              errorMessage = "Failed to start profiling: " + record.startFailureMessage
            }.build()
            legacyProfilingRecord.remove(pid)
            sendStartStatusEvent(command, status)
          }
          else {
            val status = Trace.TraceStartStatus.newBuilder().apply {
              status = Trace.TraceStartStatus.Status.SUCCESS
            }.build()
            sendStartStatusEvent(command, status)

            // Create a corresponding CpuTraceInfo for the trace start event.
            val traceInfo = Cpu.CpuTraceInfo.newBuilder().apply {
              traceId = requestTimeNs
              configuration = traceConfiguration
              fromTimestamp = requestTimeNs
              toTimestamp = -1
              startStatus = status
            }
            record.traceInfo = traceInfo
            sendStartTraceEvent(command, traceInfo.build())
          }
        }
        catch (e: Exception) {
          val status = Trace.TraceStartStatus.newBuilder().apply {
            status = Trace.TraceStartStatus.Status.FAILURE
            errorMessage = "Failed: $e"
          }.build()
          legacyProfilingRecord.remove(pid)
          sendStartStatusEvent(command, status)
          getLogger().error("Exception while CpuServiceProxy startProfilingAppDdms: $e")
        }
      }
    }
  }

  private fun stopTrace(command: Commands.Command) {
    val traceConfiguration = command.stopCpuTrace.configuration
    val userOptions = traceConfiguration.userOptions
    assert(userOptions.traceType == Trace.TraceType.ART)

    val pid = command.pid
    val appPkgName = device.getClientName(pid)
    val client = if (appPkgName != null) device.getClient(appPkgName) else null
    if (client == null) {
      val status = Trace.TraceStopStatus.newBuilder().apply {
        status = Trace.TraceStopStatus.Status.APP_PROCESS_DIED
        errorMessage = "App is not running"
      }.build()
      sendStopStatusEvent(command, status)

      val record = legacyProfilingRecord.get(pid)
      if (record != null) {
        val endTimeNs = getDeviceTimestamp(record)
        sendStopTraceEvent(command, record.traceInfo!!.setToTimestamp(endTimeNs).build())
      }
    }
    else {
      val record = legacyProfilingRecord.get(pid)
      if (LegacyCpuTraceRecord.isMethodProfilingStatusOff(record, client)) {
        val status = Trace.TraceStopStatus.newBuilder().apply {
          status = Trace.TraceStopStatus.Status.NO_ONGOING_PROFILING
          errorMessage = "The app is not being profiled."
        }.build()
        sendStopStatusEvent(command, status)
      }
      else {
        try {
          if (userOptions.getTraceMode() == Trace.TraceMode.SAMPLED) {
            client.stopSamplingProfiler()
          }
          else {
            client.stopMethodTracer()
          }
          record!!.stopLatch.await()

          val endTimeNs = getDeviceTimestamp(record)
          sendStopStatusEvent(command, record.traceInfo!!.stopStatus)
          sendStopTraceEvent(command, record.traceInfo!!.setToTimestamp(endTimeNs).build())
        }
        catch (e: Exception) {
          val status = Trace.TraceStopStatus.newBuilder().apply {
            status = Trace.TraceStopStatus.Status.STOP_COMMAND_FAILED
            errorMessage = "Failed: $e"
          }.build()
          sendStopStatusEvent(command, status)
          getLogger().error("Exception while CpuServiceProxy stopProfilingApp: $e")
        }
      }
    }

    // Remove the entry if there exists one.
    legacyProfilingRecord.remove(pid)
  }

  private fun getDeviceTimestamp(record: LegacyCpuTraceRecord): Long {
    var endTimeNs: Long
    try {
      endTimeNs = transportStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance()).timestampNs
    }
    catch (exception: StatusRuntimeException) {
      endTimeNs = record.traceInfo!!.fromTimestamp + 1
    }

    return endTimeNs
  }

  private fun sendStartStatusEvent(command: Commands.Command, startStatus: Trace.TraceStartStatus) {
    val statusEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.TRACE_STATUS
      commandId = command.commandId
      traceStatus = Trace.TraceStatusData.newBuilder().setTraceStartStatus(startStatus).build()
    }.build()
    eventQueue.offer(statusEvent)
  }

  private fun sendStartTraceEvent(command: Commands.Command, traceInfo: Cpu.CpuTraceInfo) {
    val traceStartEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.CPU_TRACE
      timestamp = traceInfo.fromTimestamp
      groupId = traceInfo.traceId
      cpuTrace = Cpu.CpuTraceData.newBuilder()
        .setTraceStarted(Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(traceInfo)).build()
    }.build()
    eventQueue.offer(traceStartEvent)
  }

  private fun sendStopStatusEvent(command: Commands.Command, startStatus: Trace.TraceStopStatus) {
    val statusEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.TRACE_STATUS
      commandId = command.commandId
      traceStatus = Trace.TraceStatusData.newBuilder().setTraceStopStatus(startStatus).build()
    }.build()
    eventQueue.offer(statusEvent)
  }

  private fun sendStopTraceEvent(command: Commands.Command, traceInfo: Cpu.CpuTraceInfo) {
    val traceStartEvent = Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.CPU_TRACE
      timestamp = traceInfo.toTimestamp
      groupId = traceInfo.traceId
      cpuTrace = Cpu.CpuTraceData.newBuilder()
        .setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(traceInfo)).build()
    }.build()
    eventQueue.offer(traceStartEvent)
  }
}