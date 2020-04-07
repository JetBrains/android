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

import com.android.ddmlib.Client
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private const val MAX_RETRY_COUNT = 60

/**
 * [InspectorClient] that supports pre-api 29 devices.
 * Since it doesn't use [com.android.tools.idea.transport.TransportService], some relevant event listeners are manually fired.
 */
class LegacyClient(parentDisposable: Disposable) : InspectorClient {

  var selectedClient: Client? = null

  override var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
    private set
  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()
    private set

  override val isConnected: Boolean
    get() = selectedClient?.isValid == true

  override val isCapturing = false

  override val provider = LegacyPropertiesProvider()

  private val processChangedListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()

  private val processManager = LegacyProcessManager(parentDisposable)

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

  override fun registerProcessChanged(callback: () -> Unit) {
    processChangedListeners.add(callback)
  }

  override fun getStreams(): Sequence<Common.Stream> = processManager.getStreams()

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> = processManager.getProcesses(stream)

  override fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Future<*>? {
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
    selectedClient = processManager.findClientFor(stream, process) ?: return
    selectedProcess = process
    selectedStream = stream

    reloadAllWindows()
  }

  /**
   * Attach to the specified [process].
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  private fun doAttach(stream: Common.Stream, process: Common.Process): Boolean {
    selectedClient = processManager.findClientFor(stream, process) ?: return false
    selectedProcess = process
    selectedStream = stream

    return reloadAllWindows()
  }

  /**
   * Load all windows.
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  fun reloadAllWindows(): Boolean {
    val windowIds = treeLoader.getAllWindowIds(null, this) ?: return false
    if (windowIds.isEmpty()) {
      return false
    }
    val propertiesUpdater = LegacyPropertiesProvider.Updater()
    for (windowId in windowIds) {
      eventListeners[Common.Event.EventGroupIds.COMPONENT_TREE]?.forEach { it(LegacyEvent(windowId, propertiesUpdater, windowIds)) }
    }
    propertiesUpdater.apply(provider)
    return true
  }

  override fun disconnect() {
    if (selectedClient != null) {
      selectedClient = null
      selectedProcess = Common.Process.getDefaultInstance()
      selectedStream = Common.Stream.getDefaultInstance()
      processChangedListeners.forEach { it() }
    }
  }

  override fun execute(command: LayoutInspectorProto.LayoutInspectorCommand) {}

  override fun register(groupId: Common.Event.EventGroupIds, callback: (Any) -> Unit) {
    eventListeners.getOrPut(groupId, { mutableListOf() }).add(callback)
  }
}

data class LegacyEvent(val windowId: String, val propertyUpdater: LegacyPropertiesProvider.Updater, val allWindows: List<String>)