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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.sdklib.AndroidVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.analytics.toDeviceInfo
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.android.tools.profiler.proto.Transport
import com.google.common.annotations.VisibleForTesting
import com.google.common.html.HtmlEscapers
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SESSION_DATA
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel

private const val IS_CAPTURING_KEY = "live.layout.inspector.capturing"

var isCapturingModeOn: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(IS_CAPTURING_KEY, true)
  set(value) = PropertiesComponent.getInstance().setValue(IS_CAPTURING_KEY, value, true)

class TransportInspectorClient(
  private val adb: AndroidDebugBridge,
  processes: ProcessesModel,
  model: InspectorModel,
  parentDisposable: Disposable,
  channelNameForTest: String = TransportService.CHANNEL_NAME,
  scheduler: ScheduledExecutorService = JobScheduler.getScheduler() // test only
) : InspectorClient, Disposable {
  private val project = model.project
  private val stats = model.stats
  private val client = TransportClient(channelNameForTest)

  private val streamManager = TransportStreamManager.createManager(client.transportStub, AndroidDispatchers.workerThread)

  @VisibleForTesting
  var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                          TimeUnit.MILLISECONDS.toNanos(100),
                                                          Comparator.comparing(Common.Event::getTimestamp).reversed(),
                                                          scheduler)

  override var selectedProcess: ProcessDescriptor? = null
    private set(value) {
      if (value != field) {
        loggedInitialRender = false
      }
      field = value
    }

  private val listeners: MutableList<TransportEventListener> = mutableListOf()

  override val provider = TransportPropertiesProvider(this, model)

  private var loggedInitialRender = false

  private val processChangedListeners: MutableList<(InspectorClient) -> Unit> = ContainerUtil.createConcurrentList()

  // Map of message group id to map of root view drawId to timestamp. "null" window id corresponds to messages with an empty window list.
  private val lastResponseTimePerWindow = mutableMapOf<Long, MutableMap<Long?, Long>>()

  private var attachListener: TransportEventListener? = null

  override var isCapturing: Boolean
    get() = isCapturingModeOn
    set(value) {
      isCapturingModeOn = value
      if (value) {
        execute(LayoutInspectorCommand.Type.START)
        stats.live.toggledToLive()
      }
      else {
        execute(LayoutInspectorCommand.Type.STOP)
        stats.live.toggledToRefresh()
      }
    }

  private var debugAttributesOverridden = false

  override val treeLoader = TransportTreeLoader(project, this)

  private val SELECTION_LOCK = Any()

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      model.root.children.clear()
      ViewNode.writeDrawChildren { drawChildren -> model.root.drawChildren().clear() }
      requestScreenshotMode()
      InspectorBannerService.getInstance(project).setNotification("Low Memory. Rotation disabled.")
    }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)

  init {
    processes.addSelectedProcessListeners {
      processes.selectedProcess.let { process ->
        if (process != null && process.isRunning && process.device.apiLevel >= AndroidVersion.VersionCodes.Q) {
          loggedInitialRender = false
          attach(process)
        }
        else {
          disconnect(sendStopCommand = false)
        }
      }
    }

    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    disconnectNow()
    listeners.clear()
    TransportEventPoller.stopPoller(transportPoller)
    TransportStreamManager.unregisterManager(streamManager)
    client.shutdown()
  }

  // TODO: detect when a connection is dropped
  // TODO: move all communication with the agent off the UI thread

  override fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {
    listeners.add(TransportEventListener(
      eventKind = Common.Event.Kind.LAYOUT_INSPECTOR,
      executor = MoreExecutors.directExecutor(),
      streamId = { selectedProcess?.streamId ?: 0 },
      groupId = { groupId.number.toLong() },
      processId = { selectedProcess?.pid ?: 0 }) {

      val groupLastResponseTimes = lastResponseTimePerWindow.getOrPut(it.groupId, ::mutableMapOf)
      // Get the timestamp of the most recent message we've received in this group.
      val latestMessageTimestamp = groupLastResponseTimes.values.max() ?: Long.MIN_VALUE

      val layoutInspectorEvent = it.layoutInspectorEvent
      // If this is the newest message in the group, update the map to contain only timestamps for current windows
      // (or for "null" if there are none).
      if (it.timestamp > latestMessageTimestamp) {
        layoutInspectorEvent?.tree?.allWindowIdsList?.ifEmpty { listOf(null) }?.let { allWindows ->
          allWindows.forEach { window -> groupLastResponseTimes.putIfAbsent(window, Long.MIN_VALUE) }
          groupLastResponseTimes.keys.retainAll(allWindows)
        }
      }

      val rootId = if (layoutInspectorEvent?.tree?.hasRoot() == true) layoutInspectorEvent.tree?.root?.drawId else null
      if (isConnected &&
          (it.groupId == EventGroupIds.PROPERTIES.number.toLong() ||
           // only continue if the rootId is in the map, or this is an empty event.
           it.timestamp > groupLastResponseTimes.getOrDefault(rootId, Long.MAX_VALUE))) {
        try {
          callback(layoutInspectorEvent)
        }
        catch (ex: Exception) {
          Logger.getInstance(TransportInspectorClient::class.java.name).warn(ex)
        }
        groupLastResponseTimes[rootId] = it.timestamp
      }
      false
    })
  }

  override fun registerProcessChanged(callback: (InspectorClient) -> Unit) {
    processChangedListeners.add(callback)
  }

  fun requestScreenshotMode() {
    val inspectorCommand = LayoutInspectorCommand.newBuilder()
      .setType(LayoutInspectorCommand.Type.USE_SCREENSHOT_MODE)
      .setScreenshotMode(true)
      .build()
    execute(inspectorCommand)
  }

  override fun execute(commandType: LayoutInspectorCommand.Type) {
    val command = LayoutInspectorCommand.newBuilder().setType(commandType)
    when (commandType) {
      LayoutInspectorCommand.Type.START,
      LayoutInspectorCommand.Type.REFRESH -> command.composeMode = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT.get()
      else -> {
      }
    }
    execute(command.build())
  }

  @Slow
  override fun execute(command: LayoutInspectorCommand) {
    val selectedProcess = selectedProcess ?: return
    val transportCommand = Command.newBuilder()
      .setType(Command.CommandType.LAYOUT_INSPECTOR)
      .setLayoutInspector(command)
      .setStreamId(selectedProcess.streamId)
      .setPid(selectedProcess.pid)
      .build()
    // TODO(b/150503095)
    val response = client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(transportCommand).build())
  }

  @Slow
  fun getPayload(id: Int): ByteArray {
    val selectedProcess = selectedProcess ?: return ByteArray(0)

    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(selectedProcess.streamId)
      .setId(id.toString())
      .build()

    return client.transportStub.getBytes(bytesRequest).contents.toByteArray()
  }

  private fun attach(process: ProcessDescriptor) {
    if (attachListener == null) {
      logEvent(DynamicLayoutInspectorEventType.ATTACH_REQUEST, process)
    }
    // Remove existing listener if we're retrying
    attachListener?.let { transportPoller.unregisterListener(it) }

    if (isConnected) {
      execute(LayoutInspectorCommand.Type.STOP)
      disconnectNow()
    }

    // The device daemon takes care of the case if and when the agent is previously attached already.
    val attachCommand = Command.newBuilder()
      .setStreamId(process.streamId)
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
      streamId = process::streamId,
      processId = process::pid,
      filter = { it.agentData.status == Common.AgentData.Status.ATTACHED }
    ) {
      synchronized(SELECTION_LOCK) {
        logEvent(DynamicLayoutInspectorEventType.ATTACH_SUCCESS, process)
        start(process)
      }
      // TODO: verify that capture started successfully
      attachListener = null
      true // Remove the listener after this callback
    }.also {
      transportPoller.registerListener(it)
    }

    // TODO(b/150503095)
    val response = client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  override fun refresh() {
    ApplicationManager.getApplication().executeOnPooledThread {
      execute(LayoutInspectorCommand.Type.REFRESH)
    }
  }

  override fun logEvent(type: DynamicLayoutInspectorEventType) {
    val selectedProcess = selectedProcess ?: return
    if (!isRenderEvent(type)) {
      logEvent(type, selectedProcess)
    }
    else if (!loggedInitialRender) {
      logEvent(type, selectedProcess)
      loggedInitialRender = true
    }
  }

  private fun isRenderEvent(type: DynamicLayoutInspectorEventType): Boolean =
    when (type) {
      INITIAL_RENDER,
      INITIAL_RENDER_NO_PICTURE,
      INITIAL_RENDER_BITMAPS -> true
      else -> false
    }

  private fun logEvent(eventType: DynamicLayoutInspectorEventType, process: ProcessDescriptor) {
    val inspectorEvent = DynamicLayoutInspectorEvent.newBuilder().setType(eventType)
    if (eventType == SESSION_DATA) {
      stats.save(inspectorEvent.sessionBuilder)
    }
    val builder = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
      .setDynamicLayoutInspectorEvent(inspectorEvent)
      .setDeviceInfo(process.device.toDeviceInfo())
      .withProjectId(project)

    UsageTracker.log(builder)
  }

  override fun disconnect(): Future<*> {
    return disconnect(sendStopCommand = true)
  }

  private fun disconnect(sendStopCommand: Boolean): Future<*> {
    return ApplicationManager.getApplication().executeOnPooledThread {
      if (sendStopCommand) {
        execute(LayoutInspectorCommand.Type.STOP)
      }
      disconnectNow()
    }
  }

  private fun disconnectNow() {
    val disconnectedProcess = synchronized(SELECTION_LOCK) {
      if (selectedProcess != null) {
        stop()
      }
      selectedProcess
    }
    if (disconnectedProcess != null) {
      logEvent(SESSION_DATA, disconnectedProcess)
      processChangedListeners.forEach { it(this) }
      SkiaParser.shutdownAll()
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

  private fun start(process: ProcessDescriptor) {
    enableDebugViewAttributes(process)
    startImpl(process)
  }

  private fun stop() {
    val oldProcess = selectedProcess ?: return
    selectedProcess = null
    listeners.forEach { transportPoller.unregisterListener(it) }
    lastResponseTimePerWindow.clear()
    processChangedListeners.forEach { it(this) }
    if (debugAttributesOverridden) {
      debugAttributesOverridden = false
      if (!disableDebugViewAttributes(oldProcess)) {
        reportUnableToResetGlobalSettings()
      }
    }
  }

  @Slow
  private fun startImpl(process: ProcessDescriptor) {
    selectedProcess = process
    processChangedListeners.forEach { it(this) }
    listeners.forEach { transportPoller.registerListener(it) }
    if (isCapturing) {
      execute(LayoutInspectorCommand.Type.START)
    }
    else {
      execute(LayoutInspectorCommand.Type.REFRESH)
    }
  }

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   */
  @Slow
  private fun enableDebugViewAttributes(process: ProcessDescriptor) {
    var errorMessage: String
    try {
      if (adb.executeShellCommand(process.device, "settings get global debug_view_attributes") != "null") {
        // A return value of "null" means: "debug_view_attributes" is not currently turned on for all processes on the device.
        return
      }
      val app = adb.executeShellCommand(process.device, "settings get global debug_view_attributes_application_package")
      if (app == process.name) {
        // A return value of process.name means: the debug_view_attributes are already turned on for this process.
        return
      }
      errorMessage =
        adb.executeShellCommand(process.device, "settings put global debug_view_attributes_application_package ${process.name}")
      if (errorMessage.isEmpty()) {
        // A return value of "" means: "debug_view_attributes_application_package" were successfully overridden.
        debugAttributesOverridden = true
      }
    }
    catch (ex: Exception) {
      Logger.getInstance(TransportInspectorClient::class.java).warn(ex)
      errorMessage = ex.message ?: ex.javaClass.simpleName
    }
    if (errorMessage.isNotEmpty()) {
      val encoder = HtmlEscapers.htmlEscaper()
      val text = encoder.escape("Unable to set the global setting:") + "<br/>" +
                 encoder.escape("\"debug_view_attributes_application_package\"") + "<br/>" +
                 encoder.escape("to: \"${process.name}\"") + "<br/><br/>" +
                 encoder.escape("Error: ${errorMessage}")
      AndroidNotification.getInstance(project).showBalloon("Could not enable resolution traces",
                                                           text, NotificationType.WARNING)
    }
  }

  /**
   * Disable debug view attributes for the current process that were set when we connected.
   *
   * Return true if the debug view attributes were successfully disabled.
   */
  @Slow
  private fun disableDebugViewAttributes(process: ProcessDescriptor): Boolean {
    try {
      adb.executeShellCommand(process.device, "settings delete global debug_view_attributes_application_package")
      return true
    }
    catch (ex: Exception) {
      Logger.getInstance(TransportInspectorClient::class.java).error(ex)
      return false
    }
  }

  private fun reportUnableToResetGlobalSettings() {
    ApplicationManager.getApplication().invokeLater {
      val message = """Could not reset the state on your device.

                       To fix this run the layout inspector again or manually run this command:
                       $ adb shell settings delete global debug_view_attributes_application_package
                       """.trimIndent()

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
}
