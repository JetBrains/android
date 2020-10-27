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

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.isDeviceMatch
import com.android.tools.idea.layoutinspector.model.ComponentTreeLoader
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.stats.AndroidStudioUsageTracker
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
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel

private const val MAX_RETRY_COUNT = 60
private const val IS_CAPTURING_KEY = "live.layout.inspector.capturing"
private const val ADB_TIMEOUT_SECONDS = 2L

var isCapturingModeOn: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(IS_CAPTURING_KEY, true)
  set(value) = PropertiesComponent.getInstance().setValue(IS_CAPTURING_KEY, value, true)

class DefaultInspectorClient(
  model: InspectorModel,
  parentDisposable: Disposable,
  channelNameForTest: String = TransportService.CHANNEL_NAME,
  private val scheduler: ScheduledExecutorService = JobScheduler.getScheduler() // test only
) : InspectorClient, Disposable {
  private val project = model.project
  private val stats = model.stats
  private val client = TransportClient(channelNameForTest)
  private val streamManager = TransportStreamManager.createManager(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))

  @VisibleForTesting
  val processManager = DefaultProcessManager(client.transportStub, AppExecutorUtil.getAppScheduledExecutorService(), streamManager, this)

  @VisibleForTesting
  var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                          TimeUnit.MILLISECONDS.toNanos(100),
                                                          Comparator.comparing(Common.Event::getTimestamp).reversed(),
                                                          scheduler)

  override var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
    private set(value) {
      if (value != field) {
        loggedInitialRender = false
      }
      field = value
    }

  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()

  private val listeners: MutableList<TransportEventListener> = mutableListOf()

  override val provider = DefaultPropertiesProvider(this, model)

  private var loggedInitialRender = false

  private val processChangedListeners: MutableList<(InspectorClient) -> Unit> = ContainerUtil.createConcurrentList()

  // Map of message group id to map of root view drawId to timestamp. "null" window id corresponds to messages with an empty window list.
  private val lastResponseTimePerWindow = mutableMapOf<Long, MutableMap<Long?, Long>>()
  private var adb: ListenableFuture<AndroidDebugBridge>? = null
  private var adbBridge: AndroidDebugBridge? = null

  private var attachListener: TransportEventListener? = null

  override var isConnected = false
    private set

  override var isCapturing: Boolean
    get() = isCapturingModeOn
    set(value) {
      isCapturingModeOn = value
      if (value) {
        execute(LayoutInspectorCommand.Type.START)
        stats.live.toggledToLive()
      } else {
        execute(LayoutInspectorCommand.Type.STOP)
        stats.live.toggledToRefresh()
      }
    }

  private var debugAttributesOverridden = false

  override val treeLoader = ComponentTreeLoader

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
    registerProcessEnded()
    // TODO: this doesn't seem to be needed now that this is a Disposable
    registerProjectClosed(project)
    // TODO: retry getting adb if it fails the first time
    adb = AndroidSdkUtils.getAdb(project)?.let { AdbService.getInstance()?.getDebugBridge(it) } ?:
          Futures.immediateFuture(AndroidDebugBridge.createBridge())
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
      streamId = { selectedStream.streamId },
      groupId = { groupId.number.toLong() },
      processId = { selectedProcess.pid }) {

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
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && isConnected &&
          (it.groupId == EventGroupIds.PROPERTIES.number.toLong() ||
           // only continue if the rootId is in the map, or this is an empty event.
           it.timestamp > groupLastResponseTimes.getOrDefault(rootId, Long.MAX_VALUE))) {
        try {
          callback(layoutInspectorEvent)
        }
        catch (ex: Exception) {
          Logger.getInstance(DefaultInspectorClient::class.java.name).warn(ex)
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

  private fun registerProcessEnded() {
    processManager.processListeners.add {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() &&
          !processManager.isProcessActive(selectedStream, selectedProcess)) {
        disconnect(sendStopCommand = false)
      }
    }
  }

  private fun registerProjectClosed(project: Project) {
    val projectManagerListener = object : ProjectManagerListener {
      override fun projectClosed(project: Project) {
        disconnect(sendStopCommand = true)
        ProjectManager.getInstance().removeProjectManagerListener(project, this)
      }
    }
    ProjectManager.getInstance().addProjectManagerListener(project, projectManagerListener)
  }

  override fun execute(commandType: LayoutInspectorCommand.Type) {
    val command = LayoutInspectorCommand.newBuilder().setType(commandType)
    when (commandType) {
      LayoutInspectorCommand.Type.START,
      LayoutInspectorCommand.Type.REFRESH -> command.composeMode = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT.get()
      else -> {}
    }
    execute(command.build())
  }

  @Slow
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
    // TODO(b/150503095)
    val response = client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(transportCommand).build())
  }

  @Slow
  fun getPayload(id: Int): ByteArray {
    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setId(id.toString())
      .build()

    return client.transportStub.getBytes(bytesRequest).contents.toByteArray()
  }

  override fun getStreams(): Sequence<Common.Stream> = processManager.getStreams()

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> = processManager.getProcesses(stream)

  @Slow
  override fun attach(stream: Common.Stream, process: Common.Process) {
    if (attachListener == null) {
      logEvent(DynamicLayoutInspectorEventType.ATTACH_REQUEST, stream)
    }
    // Remove existing listener if we're retrying
    attachListener?.let { transportPoller.unregisterListener(it) }

    if (isConnected) {
      execute(LayoutInspectorCommand.Type.STOP)
      disconnectNow()
    }

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
        logEvent(DynamicLayoutInspectorEventType.ATTACH_SUCCESS, stream)
        start(stream, process)
      }
      // TODO: verify that capture started successfully
      attachListener = null
      true // Remove the listener after this callback
    }
    attachListener?.let { transportPoller.registerListener(it) }

    // TODO(b/150503095)
    val response = client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
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

  override fun refresh() {
    ApplicationManager.getApplication().executeOnPooledThread {
      execute(LayoutInspectorCommand.Type.REFRESH)
    }
  }

  override fun logEvent(type: DynamicLayoutInspectorEventType) {
    if (!isRenderEvent(type)) {
      logEvent(type, selectedStream)
    }
    else if (!loggedInitialRender) {
      logEvent(type, selectedStream)
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

  private fun logEvent(eventType: DynamicLayoutInspectorEventType, stream: Common.Stream) {
    adb?.let { future ->
      Futures.addCallback(future, object : FutureCallback<AndroidDebugBridge> {
        override fun onFailure(t: Throwable) {
          // Can't get device information, but we can still log the request
          onSuccess(null)
        }

        override fun onSuccess(bridge: AndroidDebugBridge?) {
          val inspectorEvent = DynamicLayoutInspectorEvent.newBuilder().setType(eventType)
          if (eventType == SESSION_DATA) {
            stats.save(inspectorEvent.sessionBuilder)
          }
          val builder = AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
            .setDynamicLayoutInspectorEvent(inspectorEvent)
            .withProjectId(project)
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

  @Slow
  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    // If the user is re-running the application, this attach attempt may arrive before the previous
    // connection was closed. Try again later...
    if (!isConnected) {
      for (stream in getStreams()) {
        if (preferredProcess.isDeviceMatch(stream.device)) {
          for (process in getProcesses(stream)) {
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
    }
    if (timesAttempted < MAX_RETRY_COUNT) {
      scheduler.schedule({ attachWithRetry(preferredProcess, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
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
    var didDisconnect = false
    val oldStream = selectedStream
    synchronized(SELECTION_LOCK) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance()) {
        didDisconnect = true
        stop(selectedStream)
      }
    }
    if (didDisconnect) {
      logEvent(SESSION_DATA, oldStream)
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

  private fun start(stream: Common.Stream, process: Common.Process) {
    adbExecute(whenSuccess = { enableDebugViewAttributes(it, stream, process); startImpl(stream, process) },
               whenFailure = { startImpl(stream, process) })  // Attempt to start even if we could not set/verify debug attrs
  }

  private fun stop(stream: Common.Stream) {
    selectedStream = Common.Stream.getDefaultInstance()
    selectedProcess = Common.Process.getDefaultInstance()
    isConnected = false
    listeners.forEach { transportPoller.unregisterListener(it) }
    lastResponseTimePerWindow.clear()
    processChangedListeners.forEach { it(this) }
    if (debugAttributesOverridden) {
      if (!disableDebugViewAttributes(adbBridge, stream)) {
        reportUnableToResetGlobalSettings()
      }
      debugAttributesOverridden = false
    }
  }

  @Slow
  private fun startImpl(stream: Common.Stream, process: Common.Process) {
    isConnected = true
    selectedStream = stream
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
  private fun enableDebugViewAttributes(bridge: AndroidDebugBridge, stream: Common.Stream, process: Common.Process) {
    val device = findDevice(bridge, stream) ?: return
    var errorMessage: String
    try {
      if (executeShellCommand(device, "settings get global debug_view_attributes") != "null") {
        // A return value of "null" means: "debug_view_attributes" is not currently turned on for all processes on the device.
        return
      }
      val app = executeShellCommand(device, "settings get global debug_view_attributes_application_package")
      if (app == process.name) {
        // A return value of process.name means: the debug_view_attributes are already turned on for this process.
        return
      }
      errorMessage = executeShellCommand(device, "settings put global debug_view_attributes_application_package ${process.name}")
      if (errorMessage.isEmpty()) {
        // A return value of "" means: "debug_view_attributes_application_package" were successfully overridden.
        debugAttributesOverridden = true
      }
    }
    catch (ex: Exception) {
      Logger.getInstance(DefaultInspectorClient::class.java).warn(ex)
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
  private fun disableDebugViewAttributes(bridge: AndroidDebugBridge?, stream: Common.Stream): Boolean {
    val device = bridge?.let { findDevice(it, stream) } ?: return false
    try {
      executeShellCommand(device, "settings delete global debug_view_attributes_application_package")
      return true
    }
    catch (ex: Exception) {
      Logger.getInstance(DefaultInspectorClient::class.java).error(ex)
      return false
    }
  }

  @Slow
  private fun adbExecute(whenSuccess: (AndroidDebugBridge) -> Unit, whenFailure: () -> Unit) {
    adbBridge?.let { return whenSuccess(it) }
    adb?.let { future ->
      Futures.addCallback(future, object : FutureCallback<AndroidDebugBridge> {
        override fun onSuccess(bridge: AndroidDebugBridge?) {
          adbBridge = bridge
          bridge?.let { whenSuccess(it) } ?: whenFailure()
        }

        override fun onFailure(ex: Throwable) {
          Logger.getInstance(DefaultInspectorClient::class.java).error(ex)
          whenFailure()
        }
      }, MoreExecutors.directExecutor())
    }
  }

  @Slow
  private fun executeShellCommand(device: IDevice, command: String): String {
    val latch = CountDownLatch(1)
    val receiver = CollectingOutputReceiver(latch)
    device.executeShellCommand(command, receiver, ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    latch.await(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    return receiver.output.trim()
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

  private fun findDevice(bridge: AndroidDebugBridge,
                         stream: Common.Stream) = bridge.devices.find { isDeviceMatch(stream.device, it) }
}
