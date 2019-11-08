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
import com.android.ddmlib.HandleViewDebug
import com.android.tools.idea.layoutinspector.model.TreeLoader
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.google.common.base.Charsets
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
  override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup, client: InspectorClient): ViewNode? {
    val legacyClient = client as? LegacyClient ?: return null
    val window = legacyClient.selectedWindow ?: return null
    val ddmClient = legacyClient.selectedClient ?: return null
    return capture(ddmClient, window)
  }

  private fun capture(client: Client, window: String): ViewNode? {
    val hierarchyHandler = CaptureByteArrayHandler(HandleViewDebug.CHUNK_VURT)
    HandleViewDebug.dumpViewHierarchy(client, window, false, true, false, hierarchyHandler)
    val hierarchyData = hierarchyHandler.getData() ?: return null
    val (rootNode, hash) = parseLiveViewNode(hierarchyData) ?: return null

    val imageHandler = CaptureByteArrayHandler(HandleViewDebug.CHUNK_VUOP)
    HandleViewDebug.captureView(client, window, hash, imageHandler)
    try {
      val imageData = imageHandler.getData()
      rootNode.imageBottom = ImageIO.read(ByteArrayInputStream(imageData))
    }
    catch (e: IOException) {
      return null
    }
    return rootNode
  }

  private class CaptureByteArrayHandler(type: Int) : HandleViewDebug.ViewDumpHandler(type) {

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
  private fun parseLiveViewNode(bytes: ByteArray): Pair<ViewNode, String>?  {
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
      lastNodeAndHash = createViewNode(parent, line.trim())
      if (rootNodeAndHash == null) {
        rootNodeAndHash = lastNodeAndHash
      }
    }

    return rootNodeAndHash
  }

  private fun createViewNode(parent: ViewNode?, data: String): Pair<ViewNode, String> {
    var delimIndex = data.indexOf('@')
    if (delimIndex < 0) {
      throw IllegalArgumentException("Invalid format for ViewNode, missing @: $data")
    }
    val name = data.substring(0, delimIndex)
    val dataWithoutName = data.substring(delimIndex + 1)
    delimIndex = dataWithoutName.indexOf(' ')

    val hash = dataWithoutName.substring(0, delimIndex)
    val properties = parseProperties(dataWithoutName.substring(delimIndex + 1))
    val node = ViewNode(hash.hashCode().toLong(), name, null, (properties["mLeft"]?.toInt() ?: 0) + (parent?.x ?: 0),
                        (properties["mTop"]?.toInt() ?: 0) + (parent?.y ?: 0),
                        properties["mScrollX"]?.toInt() ?: 0, properties["mScrollY"]?.toInt() ?: 0,
                        properties["getWidth()"]?.toInt() ?: 0, properties["getHeight()"]?.toInt() ?: 0, null,
                        properties["text:mText"] ?: "")

    parent?.children?.add(node)
    return node to "$name@$hash"
  }

  private fun parseProperties(data: String): Map<String, String> {
    var start = 0
    var stop: Boolean
    val result = mutableMapOf<String, String>()
    do {
      val index = data.indexOf('=', start)
      val fullName = data.substring(start, index)

      val index2 = data.indexOf(',', index + 1)
      val length = Integer.parseInt(data.substring(index + 1, index2))
      start = index2 + 1 + length

      val fullValue = data.substring(index2 + 1, index2 + 1 + length)
      val (name, value) = parseProperty(fullName, fullValue)

      result[name] = value

      stop = start >= data.length
      if (!stop) {
        start += 1
      }
    }
    while (!stop)

    return result
  }

  private fun parseProperty(propertyFullName: String, value: String): Pair<String, String> {
    val colonIndex = propertyFullName.indexOf(':')
    val name: String?
    if (colonIndex != -1) {
      name = propertyFullName.substring(colonIndex + 1)
    }
    else {
      name = propertyFullName
    }
    return name to value
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

}