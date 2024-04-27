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
import com.android.ddmlib.Client
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.configurations.getAppThemeName
import com.android.tools.idea.configurations.getThemeNameForActivity
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.ComponentTreeData
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.adb.findClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.resource.data.createReference
import com.android.tools.idea.projectsystem.isMainModule
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val CONFIGURATION_REGEX = Regex("config: (.*)")
private val ACTIVITY_REGEX = Regex("mFocusedActivity: ActivityRecord\\{[^ ]+ [^ ]+ ([^ ]+) [^ ]+}")

/**
 * A [TreeLoader] that can handle pre-api 29 devices. Loads the view hierarchy and screenshot using
 * DDM, and parses it into [ViewNode]s
 */
class LegacyTreeLoader(private val client: LegacyClient) : TreeLoader {
  private val LegacyClient.selectedDdmClient: Client?
    get() =
      ddmClientOverride ?: AdbUtils.getAdbFuture(client.model.project).get()?.findClient(process)

  @VisibleForTesting var ddmClientOverride: Client? = null

  override fun loadComponentTree(
    data: Any?,
    resourceLookup: ResourceLookup,
    process: ProcessDescriptor
  ): ComponentTreeData? {
    val (windowName, updater, _) = data as? LegacyEvent ?: return null
    return capture(windowName, updater)?.let { ComponentTreeData(it, 0, emptySet()) }
  }

  override fun getAllWindowIds(data: Any?): List<String>? {
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_WINDOW_LIST_REQUESTED)
    val ddmClient = client.selectedDdmClient ?: return null
    val result =
      if (data is LegacyEvent) {
        data.allWindows
      } else {
        ListViewRootsHandler().getWindows(ddmClient, 5, TimeUnit.SECONDS)
      }
    client.latestScreenshots.keys.retainAll(result)
    client.latestData.keys.retainAll(result)
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_WINDOW_LIST_RECEIVED)
    return result
  }

  @Slow
  private fun capture(
    windowName: String,
    propertiesUpdater: LegacyPropertiesProvider.Updater
  ): AndroidWindow? {
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_HIERARCHY_REQUESTED)
    val ddmClient = client.selectedDdmClient ?: return null
    val hierarchyHandler = CaptureByteArrayHandler()
    ddmClient.dumpViewHierarchy(windowName, false, true, false, hierarchyHandler)
    updateConfiguration(ddmClient)
    val hierarchyData = hierarchyHandler.getData() ?: return null
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_HIERARCHY_RECEIVED)
    client.latestData[windowName] = hierarchyData
    if (!client.isConnected) {
      // The hierarchy data may be cut short if the client was closed under us
      return null
    }
    val (rootNode, hash) =
      LegacyTreeParser.parseLiveViewNode(hierarchyData, propertiesUpdater) ?: return null
    val imageHandler = CaptureByteArrayHandler()
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_SCREENSHOT_REQUESTED)
    ddmClient.captureView(windowName, hash, imageHandler)
    try {
      imageHandler.getData()?.let { client.latestScreenshots[windowName] = it }
    } catch (e: IOException) {
      // We didn't get an image, but still return the hierarchy and properties
    }
    client.launchMonitor.updateProgress(AttachErrorState.LEGACY_SCREENSHOT_RECEIVED)

    return LegacyAndroidWindow(client, rootNode, windowName)
  }

  private fun updateConfiguration(ddmClient: Client) {
    val adb = AdbUtils.getAdbFuture(client.model.project).get()
    val folderConfiguration = adb?.let { findConfiguration(it) }
    val theme = adb?.let { findTheme(it) }
    if (folderConfiguration != null) {
      client.model.resourceLookup.updateConfiguration(
        folderConfiguration,
        theme,
        client.process,
        fontScaleFromConfig = 1f
      )
    } else {
      client.model.resourceLookup.updateConfiguration(ddmClient.device.density)
    }
  }

  /** Find the folder configuration for the current device. */
  private fun findConfiguration(adb: AndroidDebugBridge): FolderConfiguration? {
    client.latestConfig = ""
    val configurations = adb.executeShellCommand(client.process.device, "am get-config")
    val result = CONFIGURATION_REGEX.find(configurations) ?: return null
    if (result.groupValues.size < 2) {
      return null
    }
    val config = result.groupValues[1]
    client.latestConfig = config
    return FolderConfiguration.getConfigForQualifierString(config)
  }

  /**
   * Find the theme reference for the current activity. If this fails: fallback to the application
   * theme.
   */
  private fun findTheme(adb: AndroidDebugBridge): ResourceReference? {
    client.latestTheme = ""
    val activity = findCurrentActivity(adb)
    val module =
      client.model.project.modules.find { it.isAndroidModule() && it.isMainModule() } ?: return null
    val themeString =
      activity?.let { module.getThemeNameForActivity(it) }
        ?: module.getAppThemeName()
        ?: return null
    client.latestTheme = themeString
    return createReference(themeString, client.process.packageName)
  }

  /** Find the current activity. */
  private fun findCurrentActivity(adb: AndroidDebugBridge): String? {
    val activities = adb.executeShellCommand(client.process.device, "dumpsys activity activities")
    val result = ACTIVITY_REGEX.find(activities) ?: return null
    if (result.groupValues.size < 2) {
      return null
    }
    return result.groupValues[1].replace("/", "")
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
