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
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.isDeviceMatch
import com.android.tools.idea.layoutinspector.model.ComponentTreeLoader
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.stats.AndroidStudioUsageTracker
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
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.JLabel

private const val MAX_RETRY_COUNT = 60

class DefaultInspectorClient(
  model: InspectorModel,
  parentDisposable: Disposable,
  channelNameForTest: String = TransportService.CHANNEL_NAME,
  private val scheduler: ScheduledExecutorService = JobScheduler.getScheduler() // test only
) : InspectorClient, Disposable {
  private val project = model.project
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

  private val processChangedListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()

  // Map of message group id to map of root view drawId to timestamp
  private val lastResponseTimePerGroup = mutableMapOf<Long, MutableMap<Long?, Long>>()
  private var adb: ListenableFuture<AndroidDebugBridge>? = null
  private var adbBridge: AndroidDebugBridge? = null

  private var attachListener: TransportEventListener? = null

  override var isConnected = false
    private set

  override var isCapturing = false
    private set

  override val treeLoader = ComponentTreeLoader

  private val SELECTION_LOCK = Any()

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      model.root.children.clear()
      model.root.drawChildren.clear()
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
      val groupLastResponseTimes = lastResponseTimePerGroup.getOrPut(it.groupId, ::mutableMapOf)
      val rootId = it.layoutInspectorEvent?.tree?.root?.drawId
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && isConnected &&
          (it.groupId == EventGroupIds.PROPERTIES.number.toLong() ||
           it.timestamp > groupLastResponseTimes.getOrDefault(rootId, Long.MIN_VALUE))) {
        try {
          callback(it.layoutInspectorEvent)
        }
        catch (ex: Exception) {
          Logger.getInstance(DefaultInspectorClient::class.java.name).warn(ex)
        }
        groupLastResponseTimes[rootId] = it.timestamp
        if (rootId != null) {
          // Remove entries corresponding to windows we no longer know about (but keep any that we don't know about now but will in the
          // future, if we happen to process messages out of order).
          groupLastResponseTimes.entries.removeIf { (viewId, timestamp) ->
            viewId !in it.layoutInspectorEvent.tree.allWindowIdsList && timestamp < it.timestamp }
        }
      }
      false
    })
  }

  override fun registerProcessChanged(callback: () -> Unit) {
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
    if (commandType == LayoutInspectorCommand.Type.START) {
      execute(LayoutInspectorCommand.newBuilder().apply {
        type = commandType
        composeMode = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT.get()
      }.build())
    }
    else {
      super.execute(commandType)
    }
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

    when (command.type) {
      LayoutInspectorCommand.Type.STOP -> isCapturing = false
      LayoutInspectorCommand.Type.START -> isCapturing = true
      else -> {}
    }
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
        isConnected = true
        selectedStream = stream
        selectedProcess = process
        processChangedListeners.forEach { it() }
        setDebugViewAttributes(selectedStream, true)
        listeners.forEach { transportPoller.registerListener(it) }
        execute(LayoutInspectorCommand.Type.START)
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

  @Slow
  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    if (isConnected) {
      return
    }
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
    synchronized(SELECTION_LOCK) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance()) {
        didDisconnect = true
        setDebugViewAttributes(selectedStream, false)
        selectedStream = Common.Stream.getDefaultInstance()
        selectedProcess = Common.Process.getDefaultInstance()
        isConnected = false
        isCapturing = false
        listeners.forEach { transportPoller.unregisterListener(it) }
        lastResponseTimePerGroup.clear()
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

  @Slow
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
}
