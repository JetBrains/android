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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.android.tools.profiler.proto.Transport
import com.google.common.html.HtmlEscapers
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SESSION_DATA
import com.intellij.concurrency.JobScheduler
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.asCoroutineDispatcher
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.EnumSet
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel

class TransportInspectorClient(
  private val adb: AndroidDebugBridge,
  process: ProcessDescriptor,
  model: InspectorModel,
  private val transportComponents: TransportComponents
) : AbstractInspectorClient(process) {

  override val capabilities = EnumSet.of(Capability.SUPPORTS_CONTINUOUS_MODE,
                                         Capability.SUPPORTS_FILTERING_SYSTEM_NODES,
                                         Capability.SUPPORTS_SYSTEM_NODES,
                                         Capability.SUPPORTS_SKP)!!

  private val eventCallbacks = mutableMapOf<EventGroupIds, MutableList<(Any) -> Unit>>()

  /**
   * A collection of disposable components provided by the transport framework that may get reused
   * across multiple [TransportInspectorClient]s.
   */
  class TransportComponents(
    channelName: String = TransportService.CHANNEL_NAME,
    scheduler: ScheduledExecutorService = JobScheduler.getScheduler()
  ) : Disposable {

    val client = TransportClient(channelName)
    val streamManager = TransportStreamManager.createManager(client.transportStub, scheduler.asCoroutineDispatcher())
    val poller = TransportEventPoller.createPoller(client.transportStub,
                                                   TimeUnit.MILLISECONDS.toNanos(100),
                                                   Comparator.comparing(Common.Event::getTimestamp).reversed(),
                                                   scheduler)

    override fun dispose() {
      TransportEventPoller.stopPoller(poller)
      TransportStreamManager.unregisterManager(streamManager)
      client.shutdown()
    }
  }

  private val project = model.project
  private val stats = model.stats
  private val metrics = LayoutInspectorMetrics.create(project, process, stats)

  private val listeners: MutableList<TransportEventListener> = mutableListOf()

  override val provider = TransportPropertiesProvider(this, model)

  private var loggedInitialRender = false

  // Map of message group id to map of root view drawId to timestamp. "null" window id corresponds to messages with an empty window list.
  private val lastResponseTimePerWindow = mutableMapOf<Long, MutableMap<Long?, Long>>()

  override val isCapturing: Boolean
    get() = InspectorClientSettings.isCapturingModeOn

  private var debugAttributesOverridden = false

  private val skiaParser = SkiaParserImpl(
    {
      capabilities.remove(Capability.SUPPORTS_SKP)
      requestScreenshotMode()
    })

  override val treeLoader = TransportTreeLoader(project, this, skiaParser)

  private val composeDependencyChecker = ComposeDependencyChecker(model)

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      model.root.children.clear()
      ViewNode.writeDrawChildren { drawChildren -> model.root.drawChildren().clear() }
      requestScreenshotMode()
      InspectorBannerService.getInstance(project).setNotification("Low Memory. Rotation disabled.")
    }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)

  init {
    loggedInitialRender = false
    register(EventGroupIds.COMPONENT_TREE) { event ->
      fireTreeEvent(event)
    }
    register(EventGroupIds.LAYOUT_INSPECTOR_ERROR) { event ->
      val error = when (event) {
        is LayoutInspectorProto.LayoutInspectorEvent -> event.errorMessage
        is String -> event
        else -> "Unknown Error"
      }
      fireError(error)
    }
  }

  override fun doConnect(): ListenableFuture<Nothing> {
    attach(process)
    invokeLater {
      composeDependencyChecker.performCheck(this)
    }

    return Futures.immediateFuture(null)
  }

  // TODO: detect when a connection is dropped
  // TODO: move all communication with the agent off the UI thread

  /**
   * Register a callback that will be triggered whenever transport pipeline sends us an event
   * associated with the specified [groupId].
   */
  fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {
    eventCallbacks.getOrPut(groupId) { mutableListOf() }.add(callback)
    if (eventCallbacks.getValue(groupId).size == 1) {
      onRegistered(groupId)
    }
  }

  private fun onRegistered(groupId: EventGroupIds) {
    listeners.add(TransportEventListener(
      eventKind = Common.Event.Kind.LAYOUT_INSPECTOR,
      executor = MoreExecutors.directExecutor(),
      streamId = { process.streamId },
      groupId = { groupId.number.toLong() },
      processId = { process.pid }) {

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
      if ((it.groupId == EventGroupIds.PROPERTIES.number.toLong() ||
           // only continue if the rootId is in the map, or this is an empty event.
           it.timestamp > groupLastResponseTimes.getOrDefault(rootId, Long.MAX_VALUE))) {
        try {
          fireEvent(groupId, layoutInspectorEvent)
        }
        catch (ex: Exception) {
          Logger.getInstance(TransportInspectorClient::class.java.name).warn(ex)
        }
        groupLastResponseTimes[rootId] = it.timestamp
      }
      false
    })
  }

  fun requestScreenshotMode() {
    execute(LayoutInspectorCommand.Type.USE_SCREENSHOT_MODE.toCommand().apply {
      screenshotMode = true
    })
  }

  private fun LayoutInspectorCommand.Type.toCommand(): LayoutInspectorCommand.Builder {
    return LayoutInspectorCommand.newBuilder().setType(this)
  }

  @Slow
  fun execute(command: LayoutInspectorCommand.Builder) = execute(command.build())

  @Slow
  fun execute(command: LayoutInspectorCommand) {
    val transportCommand = Command.newBuilder()
      .setType(Command.CommandType.LAYOUT_INSPECTOR)
      .setLayoutInspector(command)
      .setStreamId(process.streamId)
      .setPid(process.pid)
      .build()
    // TODO(b/150503095)
    val response =
      transportComponents.client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(transportCommand).build())
  }

  @Slow
  fun getPayload(id: Int): ByteArray {
    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(process.streamId)
      .setId(id.toString())
      .build()

    return transportComponents.client.transportStub.getBytes(bytesRequest).contents.toByteArray()
  }

  private fun attach(process: ProcessDescriptor) {
    logEvent(DynamicLayoutInspectorEventType.ATTACH_REQUEST)

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

    transportComponents.poller.registerListener(TransportEventListener(
      eventKind = Common.Event.Kind.AGENT,
      executor = MoreExecutors.directExecutor(),
      streamId = process::streamId,
      processId = process::pid,
      filter = { it.agentData.status == Common.AgentData.Status.ATTACHED }
    ) {
      logEvent(DynamicLayoutInspectorEventType.ATTACH_SUCCESS)
      start(process)

      // TODO: verify that capture started successfully
      true // Remove the listener after this callback
    })

    // TODO(b/150503095)
    val response =
      transportComponents.client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  override fun refresh() {
    ApplicationManager.getApplication().executeOnPooledThread {
      execute(LayoutInspectorCommand.Type.REFRESH.toCommand().apply {
        composeMode = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT.get()
        hideSystemNodes = TreeSettings.skipSystemNodesInAgent
      })
    }
  }

  fun logEvent(type: DynamicLayoutInspectorEventType) {
    if (!isRenderEvent(type)) {
      metrics.logEvent(type)
    }
    else if (!loggedInitialRender) {
      metrics.logEvent(type)
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

  override fun doDisconnect(): ListenableFuture<Nothing> {
    val future = SettableFuture.create<Nothing>()
    ApplicationManager.getApplication().executeOnPooledThread {
      stopFetching()
      stop()

      logEvent(SESSION_DATA)
      skiaParser.shutdown()
      future.set(null)
    }
    return future
  }

  override fun startFetching() {
    stats.live.toggledToLive()
    execute(LayoutInspectorCommand.Type.START.toCommand().apply {
      composeMode = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT.get()
      hideSystemNodes = TreeSettings.skipSystemNodesInAgent
    })
  }

  override fun stopFetching() {
    stats.live.toggledToRefresh()
    execute(LayoutInspectorCommand.Type.STOP.toCommand())
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

  @Slow
  private fun start(process: ProcessDescriptor) {
    enableDebugViewAttributes(process)

    listeners.forEach { transportComponents.poller.registerListener(it) }
    if (InspectorClientSettings.isCapturingModeOn) {
      startFetching()
    }
    else {
      refresh()
    }
  }

  private fun stop() {
    listeners.forEach { transportComponents.poller.unregisterListener(it) }
    lastResponseTimePerWindow.clear()
    if (debugAttributesOverridden) {
      debugAttributesOverridden = false
      if (!disableDebugViewAttributes(process)) {
        reportUnableToResetGlobalSettings()
      }
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
      if (adb.executeShellCommand(process.device, "settings get global debug_view_attributes") !in listOf("null", "0")) {
        // A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on for all processes on the device.
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
                 encoder.escape("Error: $errorMessage")
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
    return try {
      adb.executeShellCommand(process.device, "settings delete global debug_view_attributes_application_package")
      true
    }
    catch (ex: Exception) {
      Logger.getInstance(TransportInspectorClient::class.java).error(ex)
      false
    }
  }

  private fun reportUnableToResetGlobalSettings() {
    ApplicationManager.getApplication().invokeLater {
      val message = """Could not reset the state on your device.

                       To fix this, manually run this command:
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

  /**
   * Fire relevant callbacks registered with [register], if present
   */
  private fun fireEvent(groupId: EventGroupIds, data: Any) {
    eventCallbacks[groupId]?.forEach { callback -> callback(data) }
  }
}
