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
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.isDeviceMatch
import com.android.tools.idea.layoutinspector.model.ComponentTreeLoader
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import io.grpc.ManagedChannel
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
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel
import kotlin.properties.Delegates

private const val MAX_RETRY_COUNT = 60

class DefaultInspectorClient(
  model: InspectorModel,
  parentDisposable: Disposable,
  channelNameForTest: String = TransportService.CHANNEL_NAME,
  private val scheduler: ScheduledExecutorService = JobScheduler.getScheduler() // test only
) : InspectorClient, Disposable {
  private val project = model.project
  private var client = TransportClient(channelNameForTest)

  @VisibleForTesting
  var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                          TimeUnit.MILLISECONDS.toNanos(100),
                                                          Comparator.comparing(Common.Event::getTimestamp).reversed(),
                                                          scheduler)

  override var selectedStream: Common.Stream by Delegates.observable(Common.Stream.getDefaultInstance()) { _, old, new ->
    if (old != new) {
      loggedInitialRender = false
    }
  }
  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()

  override val provider = DefaultPropertiesProvider(this, model.resourceLookup)

  private var loggedInitialRender = false

  private val processChangedListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()
  private val lastResponseTimePerGroup = mutableMapOf<Long, Long>()
  private var adb: ListenableFuture<AndroidDebugBridge>? = null
  private var adbBridge: AndroidDebugBridge? = null

  private var attachListener: TransportEventListener? = null

  override var isConnected = false
    private set

  override var isCapturing = false
    private set

  override val treeLoader = ComponentTreeLoader

  private val SELECTION_LOCK = Any()

  init {
    registerProcessEnded()
    registerProjectClosed(project)
    // TODO: retry getting adb if it fails the first time
    adb = AndroidSdkUtils.getAdb(project)?.let { AdbService.getInstance()?.getDebugBridge(it) } ?:
          Futures.immediateFuture(AndroidDebugBridge.createBridge())
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    disconnectNow()
    TransportEventPoller.stopPoller(transportPoller)
  }

  // TODO: detect when a connection is dropped
  // TODO: move all communication with the agent off the UI thread

  override fun register(groupId: Common.Event.EventGroupIds, callback: (Any) -> Unit) {
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
        disconnectNow()
      }
      false
    })
  }

  private fun registerProjectClosed(project: Project) {
    val projectManagerListener = object : ProjectManagerListener {
      override fun projectClosed(project: Project) {
        disconnectNow()
        ProjectManager.getInstance().removeProjectManagerListener(project, this)
      }
    }
    ProjectManager.getInstance().addProjectManagerListener(project, projectManagerListener)
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

  fun getPayload(id: Int): ByteArray {
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
      if (stream.type == Common.Stream.Type.DEVICE && stream.device.featureLevel >= 29) {
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
    if (attachListener == null) {
      logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST, stream)
    }
    // Remove existing listener if we're retrying
    attachListener?.let { transportPoller.unregisterListener(it) }

    // TODO: Probably need to detach from an existing process here
    isConnected = false
    isCapturing = false
    processChangedListeners.forEach { it() }

    // The device daemon takes care of the case if and when the agent is previously attached already.
    val attachCommand = Command.newBuilder()
      .setStreamId(stream.streamId)
      .setPid(process.pid)
      .setType(Command.CommandType.ATTACH_AGENT)
      .setAttachAgent(
        Commands.AttachAgent.newBuilder()
          .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch))
          .setAgentConfigPath(TransportFileManager.getAgentConfigFile()))
      .build()

    attachListener = TransportEventListener(
      eventKind = Common.Event.Kind.AGENT,
      executor = MoreExecutors.directExecutor(),
      streamId = stream::getStreamId,
      processId = process::getPid,
      filter = { it.agentData.status == Common.AgentData.Status.ATTACHED }
    ) {
      synchronized(SELECTION_LOCK) {
        logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_SUCCESS, stream)
        isConnected = true
        selectedStream = stream
        selectedProcess = process
        processChangedListeners.forEach { it() }
        setDebugViewAttributes(selectedStream, true)
        execute(LayoutInspectorCommand.Type.START)
      }
      // TODO: verify that capture started successfully
      attachListener = null
      true // Remove the listener after this callback
    }
    attachListener?.let { transportPoller.registerListener(it) }

    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  /**
   * Attempt to connect to the specified [preferredProcess].
   *
   * The method called will retry itself up to MAX_RETRY_COUNT times.
   */
  override fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Future<*>? {
    if (preferredProcess.api < 29) {
      return null
    }
    return ApplicationManager.getApplication().executeOnPooledThread { attachWithRetry(preferredProcess, 0) }
  }

  fun logInitialRender(containsPicture: Boolean) {
    if (!loggedInitialRender) {
      logEvent(if (containsPicture) INITIAL_RENDER else INITIAL_RENDER_NO_PICTURE, selectedStream)
      loggedInitialRender = true
    }
  }


  private fun logEvent(eventType: DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType, stream: Common.Stream) {
    adb?.let { future ->
      Futures.addCallback(future, object : FutureCallback<AndroidDebugBridge> {
        override fun onFailure(t: Throwable) {
          // Can't get device information, but we can still log the request
          onSuccess(null)
        }

        override fun onSuccess(bridge: AndroidDebugBridge?) {
          val builder = AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
            .setDynamicLayoutInspectorEvent(
              DynamicLayoutInspectorEvent.newBuilder().setType(eventType))
          if (bridge != null) {
            findDevice(bridge, stream)?.let {
              builder.setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(it))
            }
          }
          UsageTracker.log(builder)
        }
      }, MoreExecutors.directExecutor())
    }
  }

  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    if (isConnected) {
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
      scheduler.schedule({ attachWithRetry(preferredProcess, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
  }

  override fun disconnect() {
    ApplicationManager.getApplication().executeOnPooledThread {
      execute(LayoutInspectorCommand.Type.STOP)
      disconnectNow()
    }
  }

  private fun disconnectNow() {
    var didDisconnect = false
    synchronized(SELECTION_LOCK) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance()) {
        didDisconnect = true
        setDebugViewAttributes(selectedStream, false)
        selectedStream = Common.Stream.getDefaultInstance()
        selectedProcess = Common.Process.getDefaultInstance()
        isConnected = false
        isCapturing = false
      }
    }
    if (didDisconnect) {
      processChangedListeners.forEach { it() }
      SkiaParser.shutdownAll()
    }
  }

  private fun setDebugViewAttributes(stream: Common.Stream, enable: Boolean) {
    val currentSelectedStream = selectedStream
    adbBridge?.let {
      if (!setDebugViewAttributes(it, stream, enable) && !enable) {
        reportUnableToResetGlobalSettings()
      }
      return
    }

    adb?.let { future ->
      Futures.addCallback(future, object : FutureCallback<AndroidDebugBridge> {
        override fun onSuccess(bridge: AndroidDebugBridge?) {
          adbBridge = bridge
          if (stream != currentSelectedStream) {
            return
          }
          val success = bridge?.let { setDebugViewAttributes(it, stream, enable) } ?: false
          if (!success && !enable) {
            reportUnableToResetGlobalSettings()
          }
        }

        override fun onFailure(ex: Throwable) {
          if (!enable) {
            reportUnableToResetGlobalSettings()
          }
        }
      }, MoreExecutors.directExecutor())
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
    try {
      val device = findDevice(bridge, stream) ?: return false
      val receiver = CollectingOutputReceiver()
      val commands = createAdbDebugViewCommand(enable)
      if (!enable || selectedStream == stream) {
        commands.forEach { device.executeShellCommand(it, receiver) }
      }
      return true
    }
    catch (ex: Exception) {
      Logger.getInstance(DefaultInspectorClient::class.java).warn(ex)
      return false
    }
  }

  private fun findDevice(bridge: AndroidDebugBridge,
                         stream: Common.Stream) = bridge.devices.find { isDeviceMatch(stream.device, it) }

  private fun createAdbDebugViewCommand(enable: Boolean): List<String> {
    if (!enable) {
      return listOf("settings delete global debug_view_attributes",
                    "settings delete global debug_view_attributes_application_package")
    }
    val packageName = findPackageName()
    return if (packageName.isNotEmpty())
      listOf("settings put global debug_view_attributes_application_package $packageName")
    else
      listOf("settings put global debug_view_attributes 1")
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
