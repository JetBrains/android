/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.base.Charsets
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Collections
import java.util.Stack
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

object ViewNodeParser {
    /** Parses the flat string representation of a view node and returns the root node.  */
    fun parse(bytes: ByteArray) = parseLiveViewNode(bytes)

    private fun parseLiveViewNode(bytes: ByteArray): ViewNode? {
        var root: ViewNode? = null
        var lastNode: ViewNode? = null
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
                stack.push(lastNode)
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
            lastNode = createViewNode(parent, line.trim())
            if (root == null) {
                root = lastNode
            }
        }

        return root
    }

    private fun createViewNode(parent: ViewNode?, data: String): ViewNode {
        var data = data
        var delimIndex = data.indexOf('@')
        if (delimIndex < 0) {
            throw IllegalArgumentException("Invalid format for ViewNode, missing @: $data")
        }
        val name = data.substring(0, delimIndex)
        data = data.substring(delimIndex + 1)
        delimIndex = data.indexOf(' ')

        val hash = data.substring(0, delimIndex)
        val properties = parseProperties(data.substring(delimIndex + 1))
        val node = ViewNode(0, name, null, (properties["mLeft"]?.toInt() ?: 0) + (parent?.x ?: 0),
                            (properties["mTop"]?.toInt() ?: 0) + (parent?.y ?: 0),
                            properties["mScrollX"]?.toInt() ?: 0, properties["mScrollY"]?.toInt() ?: 0,
                            properties["getWidth()"]?.toInt() ?: 0, properties["getHeight()"]?.toInt() ?: 0, null,
                            properties["text:mText"] ?: "")

        node.hash = "$name@$hash"

        parent?.children?.add(node)
        return node
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
        } while (!stop)

        return result
    }

    private fun parseProperty(propertyFullName: String, value: String) : Pair<String, String> {
        val colonIndex = propertyFullName.indexOf(':')
        val name: String?
        if (colonIndex != -1) {
            name = propertyFullName.substring(colonIndex + 1)
        } else {
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

        override fun supplier() = Supplier<MutableList<String>> { ArrayList() }
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
