/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.ide.analytics.toDeviceInfo
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.stats.withProjectId
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * [InspectorClient] that supports pre-api 29 devices.
 * Since it doesn't use [com.android.tools.idea.transport.TransportService], some relevant event listeners are manually fired.
 */
class LegacyClient(
  adb: AndroidDebugBridge,
  process: ProcessDescriptor,
  model: InspectorModel,
  treeLoaderForTest: LegacyTreeLoader? = null
) : AbstractInspectorClient(process) {
  private val lookup: ViewNodeAndResourceLookup = model
  private val stats = model.stats

  override val isCapturing = false

  override val provider = LegacyPropertiesProvider()

  private var loggedInitialAttach = false
  private var loggedInitialRender = false

  private val project = model.project

  override fun logEvent(type: DynamicLayoutInspectorEventType) {
    if (!isRenderEvent(type)) {
      logEvent(type, process)
    }
    else if (!loggedInitialRender) {
      logEvent(type, process)
      loggedInitialRender = true
    }
  }

  private fun logEvent(type: DynamicLayoutInspectorEventType, process: ProcessDescriptor) {
    val inspectorEvent = DynamicLayoutInspectorEvent.newBuilder().setType(type)
    if (type == DynamicLayoutInspectorEventType.SESSION_DATA) {
      stats.save(inspectorEvent.sessionBuilder)
    }
    val builder = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
      .setDynamicLayoutInspectorEvent(inspectorEvent)
      .setDeviceInfo(process.device.toDeviceInfo())
      .withProjectId(project)

    UsageTracker.log(builder)
  }

  private fun isRenderEvent(type: DynamicLayoutInspectorEventType): Boolean =
    when (type) {
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER,
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE -> true
      else -> false
    }

  override val treeLoader = treeLoaderForTest ?: LegacyTreeLoader(adb, this)

  val latestScreenshots = mutableMapOf<String, ByteArray>()

  init {
    loggedInitialRender = false
  }

  override fun doConnect() {
    attach(process)
  }

  private fun attach(process: ProcessDescriptor) {
    loggedInitialAttach = false
    if (!doAttach(process)) {
      // TODO: create a different event for when there are no windows
      logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE)
    }
  }

  /**
   * Attach to the specified [process].
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  private fun doAttach(process: ProcessDescriptor): Boolean {
    if (!loggedInitialAttach) {
      logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, process)
      loggedInitialAttach = true
    }

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
    val windowIds = treeLoader.getAllWindowIds(null) ?: return false
    if (windowIds.isEmpty()) {
      return false
    }
    val propertiesUpdater = LegacyPropertiesProvider.Updater(lookup)
    for (windowId in windowIds) {
      fireEvent(Common.Event.EventGroupIds.COMPONENT_TREE, LegacyEvent(windowId, propertiesUpdater, windowIds))
    }
    propertiesUpdater.apply(provider)
    return true
  }

  override fun doDisconnect(): Future<Nothing> {
    logEvent(DynamicLayoutInspectorEventType.SESSION_DATA)
    latestScreenshots.clear()
    return CompletableFuture.completedFuture(null)
  }

  override fun execute(command: LayoutInspectorProto.LayoutInspectorCommand) {}
}

data class LegacyEvent(val windowId: String, val propertyUpdater: LegacyPropertiesProvider.Updater, val allWindows: List<String>)