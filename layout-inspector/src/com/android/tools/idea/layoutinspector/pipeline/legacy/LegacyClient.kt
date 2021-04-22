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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.application.invokeLater

/**
 * [InspectorClient] that supports pre-api 29 devices.
 * Since it doesn't use [com.android.tools.idea.transport.TransportService], some relevant event listeners are manually fired.
 */
class LegacyClient(
  adb: AndroidDebugBridge,
  process: ProcessDescriptor,
  model: InspectorModel,
  stats: SessionStatistics,
  treeLoaderForTest: LegacyTreeLoader? = null
) : AbstractInspectorClient(process) {

  private val lookup: ViewNodeAndResourceLookup = model

  override val isCapturing = false

  override val provider = LegacyPropertiesProvider()

  private var loggedInitialAttach = false
  private var loggedInitialRender = false

  private val metrics = LayoutInspectorMetrics.create(model.project, process, stats)
  private val composeWarning = ComposeWarning(model.project)

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
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER,
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE -> true
      else -> false
    }

  override val treeLoader = treeLoaderForTest ?: LegacyTreeLoader(adb, this)

  val latestScreenshots = mutableMapOf<String, ByteArray>()

  init {
    loggedInitialRender = false
  }

  override fun doConnect(): ListenableFuture<Nothing> {
    attach()
    return Futures.immediateFuture(null)
  }

  private fun attach() {
    loggedInitialAttach = false
    if (!doAttach()) {
      // TODO: create a different event for when there are no windows
      metrics.logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE)
    }
  }

  /**
   * Attach to the current [process].
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  private fun doAttach(): Boolean {
    if (!loggedInitialAttach) {
      metrics.logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST)
      loggedInitialAttach = true
    }

    if (!reloadAllWindows()) {
      return false
    }
    logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS)
    invokeLater {
      composeWarning.performCheck(this)
    }
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
      fireTreeEvent(LegacyEvent(windowId, propertiesUpdater, windowIds))
    }
    propertiesUpdater.apply(provider)
    return true
  }

  override fun doDisconnect(): ListenableFuture<Nothing> {
    logEvent(DynamicLayoutInspectorEventType.SESSION_DATA)
    latestScreenshots.clear()
    return Futures.immediateFuture(null)
  }

  class LegacyFetchingUnsupportedOperationException : UnsupportedOperationException("Fetching is not supported by legacy clients")

  override fun startFetching() {
    throw LegacyFetchingUnsupportedOperationException()
  }

  override fun stopFetching() {
    throw LegacyFetchingUnsupportedOperationException()
  }
}

data class LegacyEvent(val windowId: String, val propertyUpdater: LegacyPropertiesProvider.Updater, val allWindows: List<String>)