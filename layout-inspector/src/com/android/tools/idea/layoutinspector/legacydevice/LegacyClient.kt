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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.Client
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.android.tools.idea.stats.withProjectId
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private const val MAX_RETRY_COUNT = 60

/**
 * [InspectorClient] that supports pre-api 29 devices.
 * Since it doesn't use [com.android.tools.idea.transport.TransportService], some relevant event listeners are manually fired.
 */
class LegacyClient(model: InspectorModel, parentDisposable: Disposable) : InspectorClient {
  var selectedClient: Client? = null
  private val lookup: ViewNodeAndResourceLookup = model
  private val stats = model.stats

  override var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
    private set(value) {
      if (value != field) {
        loggedInitialRender = false
      }
      field = value
    }

  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()
    private set

  override val isConnected: Boolean
    get() = selectedClient?.isValid == true

  override val isCapturing = false

  override val provider = LegacyPropertiesProvider()

  private var loggedInitialAttach = false
  private var loggedInitialRender = false

  private val processChangedListeners: MutableList<(InspectorClient) -> Unit> = ContainerUtil.createConcurrentList()

  private val processManager = LegacyProcessManager(parentDisposable)

  private val project = model.project

  override fun logEvent(type: DynamicLayoutInspectorEventType) {
    if (!isRenderEvent(type)) {
      logEvent(type, selectedStream)
    }
    else if (!loggedInitialRender) {
      logEvent(type, selectedStream)
      loggedInitialRender = true
    }
  }

  private fun logEvent(type: DynamicLayoutInspectorEventType, stream: Common.Stream) {
    val inspectorEvent = DynamicLayoutInspectorEvent.newBuilder().setType(type)
    if (type == DynamicLayoutInspectorEventType.SESSION_DATA) {
      stats.save(inspectorEvent.sessionBuilder)
    }
    val builder = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
      .setDynamicLayoutInspectorEvent(inspectorEvent)
      .withProjectId(project)
    processManager.findIDeviceFor(stream)?.let { builder.setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(it)) }
    UsageTracker.log(builder)
  }

  private fun isRenderEvent(type: DynamicLayoutInspectorEventType): Boolean =
    when (type) {
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER,
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE -> true
      else -> false
    }

  override var treeLoader = LegacyTreeLoader
    @VisibleForTesting set

  private val eventListeners: MutableMap<Common.Event.EventGroupIds, MutableList<(Any) -> Unit>> = mutableMapOf()

  init {
    processManager.processListeners.add {
      if (selectedClient?.isValid != true) {
        disconnect()
      }
    }
  }

  override fun registerProcessChanged(callback: (InspectorClient) -> Unit) {
    processChangedListeners.add(callback)
  }

  override fun getStreams(): Sequence<Common.Stream> = processManager.getStreams()

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> = processManager.getProcesses(stream)

  override fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Future<*>? {
    loggedInitialAttach = false
    return ApplicationManager.getApplication().executeOnPooledThread { attachWithRetry(preferredProcess, 0) }
  }

  // TODO: It might be possible for attach() to be successful here before the process is actually ready to be inspected, causing the later
  // call to LegacyTreeLoader.capture to fail. If this is the case, this method should be changed to ensure the capture will work before
  // declaring success.
  // If it's not the case, this code is duplicated from DefaultClient and so should be factored out somewhere.
  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    for (stream in getStreams()) {
      if (preferredProcess.isDeviceMatch(stream.device)) {
        for (process in getProcesses(stream)) {
          if (process.name == preferredProcess.packageName) {
            if (doAttach(stream, process)) {
              return
            }
          }
        }
      }
    }
    if (timesAttempted < MAX_RETRY_COUNT) {
      JobScheduler.getScheduler().schedule({ attachWithRetry(preferredProcess, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
    return
  }

  override fun attach(stream: Common.Stream, process: Common.Process) {
    loggedInitialAttach = false
    if (!doAttach(stream, process)) {
      // TODO: create a different event for when there are no windows
      logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE)
    }
  }

  /**
   * Attach to the specified [process].
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  private fun doAttach(stream: Common.Stream, process: Common.Process): Boolean {
    if (!loggedInitialAttach) {
      logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, stream)
      loggedInitialAttach = true
    }
    selectedClient = processManager.findClientFor(stream, process) ?: return false
    selectedProcess = process
    selectedStream = stream
    processChangedListeners.forEach { it(this) }

    if (!reloadAllWindows()) {
      return false
    }
    logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS)
    return true
  }

  override fun refresh() {
    reloadAllWindows()
  }

  /**
   * Load all windows.
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  @Slow
  fun reloadAllWindows(): Boolean {
    val windowIds = treeLoader.getAllWindowIds(null, this) ?: return false
    if (windowIds.isEmpty()) {
      return false
    }
    val propertiesUpdater = LegacyPropertiesProvider.Updater(lookup)
    for (windowId in windowIds) {
      eventListeners[Common.Event.EventGroupIds.COMPONENT_TREE]?.forEach { it(LegacyEvent(windowId, propertiesUpdater, windowIds)) }
    }
    propertiesUpdater.apply(provider)
    return true
  }

  override fun disconnect(): Future<Nothing> {
    if (selectedClient != null) {
      logEvent(DynamicLayoutInspectorEventType.SESSION_DATA)
      selectedClient = null
      selectedProcess = Common.Process.getDefaultInstance()
      selectedStream = Common.Stream.getDefaultInstance()
      processChangedListeners.forEach { it(this) }
    }
    return CompletableFuture.completedFuture(null)
  }

  override fun execute(command: LayoutInspectorProto.LayoutInspectorCommand) {}

  override fun register(groupId: Common.Event.EventGroupIds, callback: (Any) -> Unit) {
    eventListeners.getOrPut(groupId, { mutableListOf() }).add(callback)
  }
}

data class LegacyEvent(val windowId: String, val propertyUpdater: LegacyPropertiesProvider.Updater, val allWindows: List<String>)