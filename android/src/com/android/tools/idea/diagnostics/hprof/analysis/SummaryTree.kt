/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsSize
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.ArrayDeque

class SummaryTree(val sizeThreshold: Long, val depthLimit: Int) {
  private val roots = Int2ObjectOpenHashMap<Node>()

  fun merge(node: GCRootPathsTree.RootNode) {
    node.edges.forEach { id ->
      val oldNode = node.edges[id].first
      if (oldNode.totalSizeInBytes >= sizeThreshold) {
        roots.getOrPut(id) { Node(node.edges[id].second, oldNode.totalSizeInBytes, oldNode.instances.size()) }.merge(oldNode, 0)
      }
      true
    }
  }

  private fun convertToSummary(node: GCRootPathsTree.RegularNode, edge: GCRootPathsTree.Edge, depth: Int): Node {
    val n = Node(edge, node.totalSizeInBytes, node.instances.size())
    if (node.edges == null || depth >= depthLimit) return n
    for (e in node.edges!!) {
      if (e.value.totalSizeInBytes > sizeThreshold) {
        n.edges[e.key] = convertToSummary(e.value, e.key, depth+1)
      }
    }
    return n
  }

  fun printTree(context: AnalysisContext, out: StringBuilder) {
    roots.toList().sortedByDescending { pair -> pair.second.subtreeSize }.forEachIndexed { i, (id, node) ->
      out.appendLine("Root ${i+1}:")
      val rootReason = context.navigator.getRootReasonForObjectId(id.toLong())?.description ?: "<Couldn't find root description>"
      out.appendLine("${sizeAndCountString(node.subtreeSize, node.instanceCount)}   ROOT: $rootReason")
      node.print(context, out)
    }
  }

  private fun sizeAndCountString(size: Long, count: Int): String {
    val subgraphSizeString = toShortStringAsSize(size).padStart(HeapReportUtils.STRING_PADDING_FOR_SIZE)
    val instanceCountString = count.toString().padStart(10)
    return "$subgraphSizeString $instanceCountString"
  }

  private data class StackEntry(val node: Node, val classDefinition: ClassDefinition?, val indent: String, val nextIndent: String)

  inner class Node(val incomingEdge: GCRootPathsTree.Edge, var subtreeSize: Long, var instanceCount: Int) {
    val edges = HashMap<GCRootPathsTree.Edge, Node>()

    fun merge(node: GCRootPathsTree.RegularNode, depth: Int) {
      if (node.totalSizeInBytes < sizeThreshold) return
      subtreeSize = subtreeSize.coerceAtLeast(node.totalSizeInBytes)
      instanceCount = instanceCount.coerceAtLeast(node.instances.size())
      if (depth >= depthLimit) return
      val och = node.edges?.entries?.toMutableList()
      if (och == null) return
      for (e in edges) {
        for (oe in och) {
          if (e.key == oe.key) {
            e.value.merge(oe.value, depth+1)
            och.remove(oe)
            break
          }
        }
      }
      for (oe in och) {
        if (oe.value.totalSizeInBytes > sizeThreshold) {
          edges[oe.key] = convertToSummary(oe.value, oe.key, depth+1)
        }
      }
    }

    fun print(context: AnalysisContext, out: StringBuilder) {
      val stack = ArrayDeque<StackEntry>()
      stack.add(StackEntry(this, null, "", ""))
      while (stack.isNotEmpty()) {
        val (node, parentClass, indent, nextIndent) = stack.pop()
        val edge = node.incomingEdge
        val fieldNameString = RefIndexUtil.getFieldDescription(java.lang.Byte.toUnsignedInt(edge.refIndex), parentClass, context.classStore)
        val text = edge.classDefinition.prettyName
        val disposedString = if (edge.disposed) " (disposed)" else ""
        out.appendLine("${sizeAndCountString(node.subtreeSize, node.instanceCount)}   $indent$fieldNameString: $text$disposedString")
        if (node.edges.size == 1) {
          stack.push(StackEntry(node.edges.values.first(), edge.classDefinition, nextIndent, nextIndent))
        } else {
          node.edges.entries.sortedBy { e -> e.value.subtreeSize }.forEachIndexed { index, (_, childNode) ->
            if (index == 0) {
              stack.push(StackEntry(childNode, edge.classDefinition, "$nextIndent\\-", "$nextIndent  "))
            } else {
              stack.push(StackEntry(childNode, edge.classDefinition, "$nextIndent+-", "$nextIndent| "))
            }
          }
        }
      }
    }
  }
}