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
package com.android.tools.idea.layoutinspector.transport

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.isDeviceMatch
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel

private const val MAX_RETRY_COUNT = 60

class DefaultInspectorClient(private val project: Project) : InspectorClient {
  private var client = TransportClient(TransportService.getInstance().channelName)
  private var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                                  TimeUnit.MILLISECONDS.toNanos(100),
                                                                  Comparator.comparing(Common.Event::getTimestamp).reversed())

  override var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()

  private val processChangedListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()
  private val lastResponseTimePerGroup = mutableMapOf<Long, Long>()
  private var adb: ListenableFuture<AndroidDebugBridge>? = null

  override var isConnected = false
    private set

  override var isCapturing = false
    private set

  init {
    registerProcessEnded()
    adb = AndroidSdkUtils.getAdb(project)?.let { AdbService.getInstance().getDebugBridge(it) }
  }

  // TODO: detect when a connection is dropped
  // TODO: move all communication with the agent off the UI thread

  override fun register(groupId: Common.Event.EventGroupIds, callback: (LayoutInspectorEvent) -> Unit) {
    // TODO: unregister listeners
    transportPoller.registerListener(TransportEventListener(
      eventKind = Common.Event.Kind.LAYOUT_INSPECTOR,
      executor = MoreExecutors.directExecutor(),
      streamId = selectedStream::getStreamId,
      groupId = { groupId.number.toLong() },
      processId = selectedProcess::getPid) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && isConnected &&
          it.timestamp > lastResponseTimePerGroup.getOrDefault(it.groupId, Long.MIN_VALUE)) {
        try {
          callback(it.layoutInspectorEvent)
        }
        catch (ex: Exception) {
          Logger.getInstance(DefaultInspectorClient::class.java.name).warn(ex)
        }
        lastResponseTimePerGroup[it.groupId] = it.timestamp
      }
      false
    })
  }

  override fun registerProcessChanged(callback: () -> Unit) {
    processChangedListeners.add(callback)
  }

  private fun registerProcessEnded() {
    // TODO: unregister listeners
    transportPoller.registerListener(TransportEventListener(
      eventKind = Common.Event.Kind.PROCESS,
      executor = MoreExecutors.directExecutor(),
      streamId = selectedStream::getStreamId,
      groupId = { selectedProcess.pid.toLong() },
      processId = selectedProcess::getPid) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && isConnected && it.isEnded) {
        setDebugViewAttributes(selectedStream, false)
        selectedStream = Common.Stream.getDefaultInstance()
        selectedProcess = Common.Process.getDefaultInstance()
        isConnected = false
        isCapturing = false
        processChangedListeners.forEach { it() }
      }
      false
    })
  }

  override fun execute(command: LayoutInspectorCommand) {
    if (selectedStream == Common.Stream.getDefaultInstance() ||
        selectedProcess == Common.Process.getDefaultInstance() ||
        !isConnected) {
      return
    }
    val transportCommand = Command.newBuilder()
      .setType(Command.CommandType.LAYOUT_INSPECTOR)
      .setLayoutInspector(command)
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .build()
    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(transportCommand).build())

    when (command.type) {
      LayoutInspectorCommand.Type.STOP -> isCapturing = false
      LayoutInspectorCommand.Type.START -> isCapturing = true
      else -> {}
    }
  }

  override fun getPayload(id: Int): ByteArray {
    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setId(id.toString())
      .build()

    return client.transportStub.getBytes(bytesRequest).contents.toByteArray()
  }

  override fun loadProcesses(): Map<Common.Stream, List<Common.Process>> {
    // Query for current devices and processes
    val processesMap = HashMap<Common.Stream, List<Common.Process>>()
    val streams = LinkedList<Common.Stream>()
    // Get all streams of all types.
    val request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
      .setKind(Common.Event.Kind.STREAM)
      .build()
    val response = client.transportStub.getEventGroups(request)
    for (group in response.groupsList) {
      val isStreamDead = group.getEvents(group.eventsCount - 1).isEnded
      if (isStreamDead) {
        // Ignore dead streams.
        continue
      }
      val connectedEvent = getLastMatchingEvent(group) { e -> e.hasStream() && e.stream.hasStreamConnected() }
                           ?: // Ignore stream event groups that do not have the connected event.
                           continue
      val stream = connectedEvent.stream.streamConnected.stream
      // We only want streams of type device to get process information.
      if (stream.type == Common.Stream.Type.DEVICE) {
        streams.add(stream)
      }
    }

    for (stream in streams) {
      val processRequest = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(stream.streamId)
        .setKind(Common.Event.Kind.PROCESS)
        .build()
      val processResponse = client.transportStub.getEventGroups(processRequest)
      val processList = ArrayList<Common.Process>()
      // A group is a collection of events that happened to a single process.
      for (groupProcess in processResponse.groupsList) {
        val isProcessDead = groupProcess.getEvents(groupProcess.eventsCount - 1).isEnded
        if (isProcessDead) {
          // Ignore dead processes.
          continue
        }
        val aliveEvent = getLastMatchingEvent(groupProcess) { e -> e.hasProcess() && e.process.hasProcessStarted() }
                         ?: // Ignore process event groups that do not have the started event.
                         continue
        val process = aliveEvent.process.processStarted.process
        processList.add(process)
      }
      processesMap[stream] = processList
    }
    return processesMap
  }

  override fun attach(stream: Common.Stream, process: Common.Process) {
    // TODO: Probably need to detach from an existing process here
    selectedStream = stream
    selectedProcess = process
    isConnected = false
    isCapturing = false
    processChangedListeners.forEach { it() }

    // The device daemon takes care of the case if and when the agent is previously attached already.
    val attachCommand = Command.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .setType(Command.CommandType.ATTACH_AGENT)
      .setAttachAgent(
        Commands.AttachAgent.newBuilder()
          .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch))
          .setAgentConfigPath(TransportFileManager.getAgentConfigFile()))
      .build()

    lateinit var listener: TransportEventListener
    listener = TransportEventListener(
      eventKind = Common.Event.Kind.AGENT,
      executor = MoreExecutors.directExecutor(),
      streamId = stream::getStreamId,
      processId = process::getPid,
      filter = { it.agentData.status == Common.AgentData.Status.ATTACHED }
    ) {
      isConnected = true
      processChangedListeners.forEach { it() }
      setDebugViewAttributes(selectedStream, true)
      execute(LayoutInspectorCommand.Type.START)

      // TODO: verify that capture started successfully
      transportPoller.unregisterListener(listener)
      false
    }
    transportPoller.registerListener(listener)

    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  /**
   * Attempt to connect to the specified [preferredProcess].
   *
   * The method called will retry itself up to MAX_RETRY_COUNT times.
   */
  override fun attach(preferredProcess: LayoutInspectorPreferredProcess) {
    ApplicationManager.getApplication().executeOnPooledThread { attachWithRetry(preferredProcess, 0) }
  }

  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    if (selectedStream != Common.Stream.getDefaultInstance() ||
        selectedProcess != Common.Process.getDefaultInstance()) {
      return
    }
    val processesMap = loadProcesses()
    for ((stream, processes) in processesMap) {
      if (preferredProcess.isDeviceMatch(stream.device)) {
        for (process in processes) {
          if (process.name == preferredProcess.packageName) {
            try {
              attach(stream, process)
              return
            }
            catch (ex: StatusRuntimeException) {
              // If the process is not found it may still be loading. Retry!
              if (ex.status.code != Status.Code.NOT_FOUND) {
                throw ex
              }
            }
          }
        }
      }
    }
    if (timesAttempted < MAX_RETRY_COUNT) {
      JobScheduler.getScheduler().schedule({ attachWithRetry(preferredProcess, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
  }

  override fun disconnect() {
    ApplicationManager.getApplication().executeOnPooledThread {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance()) {

        execute(LayoutInspectorCommand.Type.STOP)

        selectedStream = Common.Stream.getDefaultInstance()
        selectedProcess = Common.Process.getDefaultInstance()
        isConnected = false
        isCapturing = false
        processChangedListeners.forEach { it() }
      }
    }
  }

  private fun setDebugViewAttributes(stream: Common.Stream, enable: Boolean) {
    adb?.let { future ->
      Futures.addCallback(future, object : FutureCallback<AndroidDebugBridge> {
        override fun onSuccess(bridge: AndroidDebugBridge?) {
          var success = false
          try {
            success = bridge?.let { setDebugViewAttributes(it, stream, enable) } ?: false
          }
          catch (ex: Exception) {
          }
          if (!success && !enable) {
            reportUnableToResetGlobalSettings()
          }
        }

        override fun onFailure(ex: Throwable) {
          if (!enable) {
            reportUnableToResetGlobalSettings()
          }
        }
      }, EdtExecutorService.getInstance())
    }
  }

  private fun reportUnableToResetGlobalSettings() {
    ApplicationManager.getApplication().invokeLater {
      val commands = createAdbDebugViewCommand(false)
      var message = """Could not reset the state on your device.

                       To fix this run the layout inspector again or manually run these commands:
                       """.trimIndent()
      commands.forEach { message += "\n $ adb shell $it" }

      val dialog = dialog(
        title = "Unable to connect to your device",
        panel = panel {
          row(JLabel(UIUtil.getErrorIcon())) {}
          noteRow(message)
        },
        createActions = { listOf(OkButtonAction()) },
        project = project)
      dialog.show()
    }
  }

  private class OkButtonAction : AbstractAction("OK") {
    init {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }

    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.OK_EXIT_CODE)
    }
  }


  private fun setDebugViewAttributes(bridge: AndroidDebugBridge, stream: Common.Stream, enable: Boolean): Boolean {
    val device = bridge.devices.find { isDeviceMatch(stream.device, it) } ?: return false
    val receiver = CollectingOutputReceiver()
    val commands = createAdbDebugViewCommand(enable)
    if (!enable || selectedStream == stream) {
      commands.forEach { device.executeShellCommand(it, receiver) }
    }
    return true
  }

  private fun createAdbDebugViewCommand(enable: Boolean): List<String> {
    val packageName = findPackageName()
    return when {
      !enable -> listOf("settings delete global debug_view_attributes",
                        "settings delete global debug_view_attributes_application_package")
      packageName.isNotEmpty() -> listOf("settings put global debug_view_attributes_application_package $packageName")
      else -> listOf("settings put global debug_view_attributes 1")
    }
  }

  private fun findPackageName(): String {
    var packageName = ""
    for (module in ModuleManager.getInstance(project).modules) {
      val modulePackageName = (AndroidFacet.getInstance(module) ?: continue).let(::getPackageName) ?: continue
      if (packageName.isNotEmpty()) {
        // Multiple Android package names:
        return ""
      }
      packageName = modulePackageName
    }
    // Found a single Android package name:
    return packageName
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  private fun getLastMatchingEvent(group: Transport.EventGroup, predicate: (Common.Event) -> Boolean): Common.Event? {
    return group.eventsList.lastOrNull { predicate(it) }
  }
}
