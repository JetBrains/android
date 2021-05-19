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
import com.android.ddmlib.DebugViewDumpHandler
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.TreeLoader
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.ByteArrayInputStream
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
object LegacyTreeLoader : TreeLoader {

  override fun loadComponentTree(
    data: Any?, resourceLookup: ResourceLookup, client: InspectorClient, project: Project
  ): Pair<AndroidWindow, Int>? {
    val (windowName, updater, _) = data as? LegacyEvent ?: return null
    return capture(client, windowName, updater)?.let { Pair(it, 0) }
  }

  override fun getAllWindowIds(data: Any?, client: InspectorClient): List<String>? {
    if (data is LegacyEvent) {
      return data.allWindows
    }
    val legacyClient = client as? LegacyClient ?: return null
    val ddmClient = legacyClient.selectedClient ?: return null
    return ListViewRootsHandler().getWindows(ddmClient, 5, TimeUnit.SECONDS)
  }

  @Slow
  private fun capture(client: InspectorClient, windowName: String, propertiesUpdater: LegacyPropertiesProvider.Updater): AndroidWindow? {
    val legacyClient = client as? LegacyClient ?: return null
    val ddmClient = legacyClient.selectedClient ?: return null
    val hierarchyHandler = CaptureByteArrayHandler(DebugViewDumpHandler.CHUNK_VURT)
    ddmClient.dumpViewHierarchy(windowName, false, true, false, hierarchyHandler)
    propertiesUpdater.lookup.resourceLookup.dpi = ddmClient.device.density
    val hierarchyData = hierarchyHandler.getData() ?: return null
    val (rootNode, hash) = parseLiveViewNode(hierarchyData, propertiesUpdater) ?: return null
    val imageHandler = CaptureByteArrayHandler(DebugViewDumpHandler.CHUNK_VUOP)
    ddmClient.captureView(windowName, hash, imageHandler)
    ViewNode.writeDrawChildren { drawChildren ->
      try {
        val imageData = imageHandler.getData()
        if (imageData != null) {
          rootNode.drawChildren().add(DrawViewImage(ImageIO.read(ByteArrayInputStream(imageData)), rootNode))
        }
      }
      catch (e: IOException) {
        // We didn't get an image, but still return the hierarchy and properties
      }
      rootNode.flatten().forEach { it.children.mapTo(it.drawChildren()) { child -> DrawViewChild(child) } }
      if (rootNode.drawChildren().size != rootNode.children.size) {
        client.logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER)
      }
      else {
        client.logEvent(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE)
      }
    }

    return AndroidWindow(rootNode, windowName, LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_AS_REQUESTED)
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

  private class ListViewRootsHandler :
    DebugViewDumpHandler(CHUNK_VULW) {

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
