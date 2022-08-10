/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.base.Charsets
import java.awt.Rectangle
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.Stack
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

object LegacyTreeParser {
  /** Parses the flat string representation of a view node and returns the root node.  */
  fun parseLiveViewNode(bytes: ByteArray, propertyUpdater: LegacyPropertiesProvider.Updater): Pair<ViewNode, String>? {
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
      } else if (!stack.isEmpty()) {
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
    val view = ViewNode(hashId, name, null, Rectangle(), Rectangle(), null, "", 0)
    ViewNode.writeAccess {
      view.parent = parent
      parent?.children?.add(view)
    }
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
      } else {
        stringGroup.add(line)
      }
    }
  }
}