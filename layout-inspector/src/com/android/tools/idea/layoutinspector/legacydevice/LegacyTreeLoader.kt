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
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.ComponentTreeData
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.findClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import org.apache.log4j.Logger
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.util.Stack
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector
import javax.imageio.ImageIO

/**
 * A [TreeLoader] that can handle pre-api 29 devices. Loads the view hierarchy and screenshot using DDM, and parses it into [ViewNode]s
 */
class LegacyTreeLoader(private val adb: AndroidDebugBridge, private val client: LegacyClient) : TreeLoader {
  private val LegacyClient.selectedDdmClient: Client?
    get() = ddmClientOverride ?: adb.findClient(process)

  @VisibleForTesting
  var ddmClientOverride: Client? = null

  override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup): ComponentTreeData? {
    val (windowName, updater, _) = data as? LegacyEvent ?: return null
    return capture(windowName, updater)?.let { ComponentTreeData(it, 0, emptySet()) }
  }

  override fun getAllWindowIds(data: Any?): List<String>? {
    val ddmClient = client.selectedDdmClient ?: return null
    val result = if (data is LegacyEvent) {
      data.allWindows
    }
    else {
      ListViewRootsHandler().getWindows(ddmClient, 5, TimeUnit.SECONDS)
    }
    client.latestScreenshots.keys.retainAll(result)
    return result
  }

  @Slow
  private fun capture(windowName: String, propertiesUpdater: LegacyPropertiesProvider.Updater): AndroidWindow? {
    val ddmClient = client.selectedDdmClient ?: return null
    val hierarchyHandler = CaptureByteArrayHandler(DebugViewDumpHandler.CHUNK_VURT)
    ddmClient.dumpViewHierarchy(windowName, false, true, false, hierarchyHandler)
    propertiesUpdater.lookup.resourceLookup.dpi = ddmClient.device.density
    val hierarchyData = hierarchyHandler.getData() ?: return null
    val (rootNode, _) = parseLiveViewNode(hierarchyData, propertiesUpdater) ?: return null
    getScreenshotPngBytes(ddmClient)?.let { client.latestScreenshots[windowName] = it }

    return LegacyAndroidWindow(client, rootNode, windowName)
  }

  private fun getScreenshotPngBytes(ddmClient: Client): ByteArray? {
    // TODO(171901393): move back to using ddmclient so we can have windows fetched separately.
    val rawImage = try {
      ddmClient.device.getScreenshot(5, TimeUnit.SECONDS)
    }
    catch (ex: Exception) {
      Logger.getLogger(LegacyTreeLoader::class.java).warn("Couldn't get screenshot from device", ex)
      return null
    }
    val bufferedImage = rawImage.asBufferedImage()
    val baos = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "PNG", baos)
    return baos.toByteArray()
  }

  private class CaptureByteArrayHandler(type: Int) : DebugViewDumpHandler(type) {

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

  /** Parses the flat string representation of a view node and returns the root node.  */
  @VisibleForTesting
  fun parseLiveViewNode(bytes: ByteArray, propertyUpdater: LegacyPropertiesProvider.Updater): Pair<ViewNode, String>?  {
    var rootNodeAndHash: Pair<ViewNode, String>? = null
    var lastNodeAndHash: Pair<ViewNode, String>? = null
    var lastWhitespaceCount = Integer.MIN_VALUE
    val stack = Stack<ViewNode>()

    val input = BufferedReader(
      InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)
    )

    for (line in input.lines().collect(MergeNewLineCollector)) {
      if ("DONE.".equals(line, ignoreCase = true)) {
        break
      }
      // determine parent through the level of nesting by counting whitespaces
      var whitespaceCount = 0
      while (line[whitespaceCount] == ' ') {
        whitespaceCount++
      }

      if (lastWhitespaceCount < whitespaceCount) {
        stack.push(lastNodeAndHash?.first)
      }
      else if (!stack.isEmpty()) {
        val count = lastWhitespaceCount - whitespaceCount
        for (i in 0 until count) {
          stack.pop()
        }
      }

      lastWhitespaceCount = whitespaceCount
      var parent: ViewNode? = null
      if (!stack.isEmpty()) {
        parent = stack.peek()
      }
      lastNodeAndHash = createViewNode(parent, line.trim(), propertyUpdater)
      if (rootNodeAndHash == null) {
        rootNodeAndHash = lastNodeAndHash
      }
    }

    return rootNodeAndHash
  }

  private fun createViewNode(parent: ViewNode?, data: String, propertyLoader: LegacyPropertiesProvider.Updater): Pair<ViewNode, String> {
    val (name, dataWithoutName) = data.split('@', limit = 2)
    val (hash, properties) = dataWithoutName.split(' ', limit = 2)
    val hashId = hash.toLongOrNull(16) ?: 0
    val view = ViewNode(hashId, name, null, 0, 0, 0, 0, null, null, "", 0)
    view.parent = parent
    parent?.children?.add(view)
    propertyLoader.parseProperties(view, properties)
    return Pair(view, "$name@$hash")
  }

  /**
   * A custom collector that handles a special case see b/79183623
   * If a text field has text containing a new line it'll cause the view node output to be split
   * across multiple lines so the collector processes the file output and merges those back into a
   * single line so we can correctly create view nodes.
   */
  private object MergeNewLineCollector : Collector<String, MutableList<String>, List<String>> {
    override fun characteristics(): Set<Collector.Characteristics> {
      return setOf(Collector.Characteristics.CONCURRENT)
    }

    override fun supplier() = Supplier<MutableList<String>> { mutableListOf() }
    override fun finisher() = Function<MutableList<String>, List<String>> { it.toList() }
    override fun combiner() =
      BinaryOperator<MutableList<String>> { t, u -> t.apply { addAll(u) } }

    override fun accumulator() = BiConsumer<MutableList<String>, String> { stringGroup, line ->
      val newLine = line.trim()
      // add the original line because we need to keep the spacing to determine hierarchy
      if (newLine.startsWith("\\n")) {
        stringGroup[stringGroup.lastIndex] = stringGroup.last() + line
      }
      else {
        stringGroup.add(line)
      }
    }
  }

  private class ListViewRootsHandler : DebugViewDumpHandler(CHUNK_VULW) {

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
