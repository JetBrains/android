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
import com.android.ddmlib.Client
import com.android.ddmlib.DebugViewDumpHandler
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.ComponentTreeData
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.findClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A [TreeLoader] that can handle pre-api 29 devices. Loads the view hierarchy and screenshot using DDM, and parses it into [ViewNode]s
 */
class LegacyTreeLoader(private val client: LegacyClient) : TreeLoader {
  private val LegacyClient.selectedDdmClient: Client?
    get() = ddmClientOverride ?: AdbUtils.getAdbFuture(client.model.project).get()?.findClient(process)

  @VisibleForTesting
  var ddmClientOverride: Client? = null

  override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup, process: ProcessDescriptor): ComponentTreeData? {
    val (windowName, updater, _) = data as? LegacyEvent ?: return null
    return capture(windowName, updater)?.let { ComponentTreeData(it, 0, emptySet()) }
  }

  override fun getAllWindowIds(data: Any?): List<String>? {
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_WINDOW_LIST_REQUESTED)
    val ddmClient = client.selectedDdmClient ?: return null
    val result = if (data is LegacyEvent) {
      data.allWindows
    }
    else {
      ListViewRootsHandler().getWindows(ddmClient, 5, TimeUnit.SECONDS)
    }
    client.latestScreenshots.keys.retainAll(result)
    client.latestData.keys.retainAll(result)
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_WINDOW_LIST_RECEIVED)
    return result
  }

  @Slow
  private fun capture(windowName: String, propertiesUpdater: LegacyPropertiesProvider.Updater): AndroidWindow? {
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_HIERARCHY_REQUESTED)
    val ddmClient = client.selectedDdmClient ?: return null
    val hierarchyHandler = CaptureByteArrayHandler()
    ddmClient.dumpViewHierarchy(windowName, false, true, false, hierarchyHandler)
    propertiesUpdater.lookup.resourceLookup.updateConfiguration(ddmClient.device.density)
    val hierarchyData = hierarchyHandler.getData() ?: return null
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_HIERARCHY_RECEIVED)
    client.latestData[windowName] = hierarchyData
    val (rootNode, hash) = LegacyTreeParser.parseLiveViewNode(hierarchyData, propertiesUpdater) ?: return null
    val imageHandler = CaptureByteArrayHandler()
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_SCREENSHOT_REQUESTED)
    ddmClient.captureView(windowName, hash, imageHandler)
    try {
      imageHandler.getData()?.let {
        client.latestScreenshots[windowName] = it
      }
    }
    catch (e: IOException) {
      // We didn't get an image, but still return the hierarchy and properties
    }
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_SCREENSHOT_RECEIVED)

    return LegacyAndroidWindow(client, rootNode, windowName)
  }

  private class CaptureByteArrayHandler : DebugViewDumpHandler() {

    private val mData = AtomicReference<ByteArray>()

    override fun handleViewDebugResult(data: ByteBuffer) {
      val b = ByteArray(data.remaining())
      data.get(b)
      mData.set(b)
    }

    fun getData(): ByteArray? {
      waitForResult(15, TimeUnit.SECONDS)
      return mData.get()
    }
  }

  private class ListViewRootsHandler : DebugViewDumpHandler() {

    private val viewRoots = Lists.newCopyOnWriteArrayList<String>()

    override fun handleViewDebugResult(data: ByteBuffer) {
      val nWindows = data.int

      for (i in 0 until nWindows) {
        val len = data.int
        viewRoots.add(getString(data, len))
      }
    }

    @Slow
    @Throws(IOException::class)
    fun getWindows(c: Client, timeout: Long, unit: TimeUnit): List<String> {
      c.listViewRoots(this)
      waitForResult(timeout, unit)
      return viewRoots
    }
  }
}
