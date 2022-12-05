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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.snapshots.saveLegacySnapshot
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.LEGACY_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.Disposable
import java.nio.file.Path

/**
 * [InspectorClient] that supports pre-api 29 devices.
 * Since it doesn't use `com.android.tools.idea.transport.TransportService`, some relevant event listeners are manually fired.
 */
class LegacyClient(
  process: ProcessDescriptor,
  isInstantlyAutoConnected: Boolean,
  val model: InspectorModel,
  private val metrics: LayoutInspectorSessionMetrics,
  parentDisposable: Disposable,
  treeLoaderForTest: LegacyTreeLoader? = null
) : AbstractInspectorClient(LEGACY_CLIENT, model.project, process, isInstantlyAutoConnected, SessionStatisticsImpl(LEGACY_CLIENT),
                            parentDisposable) {

  private val lookup: ViewNodeAndResourceLookup = model

  override val isCapturing = false

  override val provider = LegacyPropertiesProvider()

  private var loggedInitialRender = false

  private val composeWarning = ComposeWarning(model.project)

  fun logEvent(type: DynamicLayoutInspectorEventType) {
    if (!isRenderEvent(type)) {
      metrics.logEvent(type, stats)
    }
    else if (!loggedInitialRender) {
      metrics.logEvent(type, stats)
      loggedInitialRender = true
    }
  }

  private fun isRenderEvent(type: DynamicLayoutInspectorEventType): Boolean =
    when (type) {
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER,
      DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE -> true
      else -> false
    }

  override val treeLoader = treeLoaderForTest ?: LegacyTreeLoader(this)

  val latestScreenshots = mutableMapOf<String, ByteArray>()
  var latestData = mutableMapOf<String, ByteArray>()


  init {
    loggedInitialRender = false
  }

  override suspend fun doConnect() {
    doAttach()
  }

  /**
   * Attach to the current [process].
   *
   * Return <code>true</code> if windows were found otherwise false.
   */
  private fun doAttach() {
    metrics.logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, stats)
    while (!reloadAllWindows()) {
      // We were killed by InspectorClientLaunchMonitor
      if (state == InspectorClient.State.DISCONNECTED) {
        return
      }
      // The windows may not be available yet, try again: b/185936377
      Thread.sleep(1000) // wait 1 second
    }
    logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS)
    composeWarning.performCheck(this)
  }

  override fun refresh() {
    reloadAllWindows()
  }

  @Slow
  override fun saveSnapshot(path: Path) {
    val startTime = System.currentTimeMillis()
    val snapshotMetadata = saveLegacySnapshot(path, latestData, latestScreenshots, process, model)
    snapshotMetadata.saveDuration = System.currentTimeMillis() - startTime
    // Use a separate metrics instance since we don't want the snapshot metadata to hang around
    val saveMetrics = LayoutInspectorSessionMetrics(model.project, process, snapshotMetadata = snapshotMetadata)
    saveMetrics.logEvent(DynamicLayoutInspectorEventType.SNAPSHOT_CAPTURED, stats)
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

  override suspend fun startFetching() = throw LegacyFetchingUnsupportedOperationException()

  override suspend fun stopFetching() = throw LegacyFetchingUnsupportedOperationException()
}

data class LegacyEvent(val windowId: String, val propertyUpdater: LegacyPropertiesProvider.Updater, val allWindows: List<String>)