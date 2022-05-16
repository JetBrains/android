/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.idea.transport.poller.TransportEventPoller.Companion.createStartedPoller
import com.android.tools.idea.transport.poller.TransportEventPoller.Companion.stopPoller
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import java.util.concurrent.TimeUnit

/**
 * Listener used to set the feature flag to true or false in the Transport Daemon.
 */
class TransportDeviceManagerListenerImpl : TransportDeviceManager.TransportDeviceManagerListener, ProjectManagerListener {

  override fun projectOpened(project: Project) {
    ApplicationManager.getApplication().messageBus.connect().subscribe(TransportDeviceManager.TOPIC, this)
  }

  override fun onPreTransportDaemonStart(device: Common.Device) { }
  override fun onStartTransportDaemonFail(device: Common.Device, exception: Exception) { }
  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) { }
  override fun customizeProxyService(proxy: TransportProxy) { }
  override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }

  override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) {
    configBuilder
      .setLayoutInspectorConfig(
        configBuilder.layoutInspectorConfigBuilder.setAutoconnectEnabled(
          StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
        )
      )
  }
}

/**
 * This class contains information about a foreground process on an Android device.
 */
data class ForegroundProcess(val pid: Int, val processName: String)

interface ForegroundProcessListener {
  fun onNewProcess(foregroundProcess: ForegroundProcess)
}

// TODO(b/232094819) add support for selecting a device
/**
 * This class is responsible for establishing a connection to the transport, sending commands to it and receiving events from it.
 *
 * The code that tracks the foreground process on the device is a library added to the transport's daemon, which runs on Android.
 * When it receives the start command, the library starts tracking the foreground process, it stops when it receives the stop command.
 * Information about the foreground process is sent by the transport as an event.
 */
class ForegroundProcessDetection(
  private val transportClient: TransportClient,
  private val foregroundProcessListener: ForegroundProcessListener) {
  private var stream: Common.Stream? = null
  private var transportEventPoller: TransportEventPoller? = null

  /**
   * Starts foreground process detection.
   */
  fun start() {
    if (transportEventPoller != null) {
      return
    }

    // create event poller and start listening for new events
    transportEventPoller = startPollingForEvents(
      onStreamConnected = { newStream ->
        stream = newStream
        // we can send commands only after a stream has connected
        sendStartPollingCommand(newStream)
      },
      onForegroundProcess = { foregroundProcess ->
        foregroundProcessListener.onNewProcess(foregroundProcess)
      }
    )
  }

  /**
   * Stop foreground process detection.
   */
  fun stop() {
    stream?.let {
      sendCommand(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS, it.streamId)
    }

    transportEventPoller?.let { stopPoller(it) }
    transportEventPoller = null
    stream = null
  }

  private fun sendStartPollingCommand(stream: Common.Stream) {
    sendCommand(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS, stream.streamId)
  }

  private fun startPollingForEvents(
    onStreamConnected: (Common.Stream) -> Unit,
    onForegroundProcess: (ForegroundProcess) -> Unit
  ): TransportEventPoller {
    val transportEventPoller = createStartedPoller(transportClient.transportStub, TimeUnit.MILLISECONDS.toNanos(250))

    // Create listener for LAYOUT_INSPECTOR_FOREGROUND_PROCESS
    val foregroundProcessEventListener = TransportEventListener(
      eventKind = Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS,
      executor = MoreExecutors.directExecutor(),
      callback = { event: Common.Event ->
        if (event.hasLayoutInspectorForegroundProcess() && event.layoutInspectorForegroundProcess.pid != null && event.layoutInspectorForegroundProcess.processName != null) {
          val foregroundProcessEvent = event.layoutInspectorForegroundProcess
          val pid = foregroundProcessEvent.pid.toInt()
          val packageName = foregroundProcessEvent.processName
          val foregroundProcess = ForegroundProcess(pid, packageName)
          onForegroundProcess(foregroundProcess)
        }

        // return false to continue listening for events
        false
      }
    )

    // Create listener for STREAM
    val streamConnectedEventListener = TransportEventListener(
      eventKind = Common.Event.Kind.STREAM,
      executor = MoreExecutors.directExecutor(),
      callback = { event: Common.Event ->
        if (event.hasStream() && event.stream.hasStreamConnected() && event.stream.streamConnected.hasStream()) {
          onStreamConnected(event.stream.streamConnected.stream)
        }
        // return false to continue listening for events
        false
      }
    )

    transportEventPoller.registerListener(foregroundProcessEventListener)
    transportEventPoller.registerListener(streamConnectedEventListener)

    return transportEventPoller
  }

  private fun sendCommand(commandType: Commands.Command.CommandType, streamId: Long) {
    val command = Commands.Command
      .newBuilder()
      .setType(commandType)
      .setStreamId(streamId)
      .build()

    transportClient.transportStub.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(command).build()
    )
  }
}