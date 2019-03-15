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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.IntList
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntObjectHashMap
import java.util.ArrayDeque
import java.util.HashMap

class GCRootPathsTree(
  private val parentMapping: IntList,
  private val nav: ObjectNavigator
) {
  private val topNode = RootNode()

  fun registerObject(objectId: Int) {
    val gcPath = TIntArrayList()
    var objectIterationId = objectId
    var parentId = parentMapping[objectIterationId]
    while (parentId != objectIterationId) {
      gcPath.add(objectIterationId)
      objectIterationId = parentId
      parentId = parentMapping[objectIterationId]
    }
    gcPath.add(objectIterationId)

    var currentNode: Node = topNode
    for (i in gcPath.size() - 1 downTo 0) {
      val id = gcPath[i]
      val classDefinition = nav.getClassForObjectId(id.toLong())
      currentNode = currentNode.addEdge(id, classDefinition)
    }
  }

  fun printTree(headLimit: Int, tailLimit: Int): String {
    return topNode.createHotPathReport(nav, headLimit, tailLimit)
  }

  interface Node {
    fun addEdge(objectId: Int, classDefinition: ClassDefinition): Node
  }

  class RegularNode : Node {
    // In regular nodes paths are grouped by class definition
    var edges: HashMap<ClassDefinition, RegularNode>? = null
    var pathsCount = 0
    val instances = TIntHashSet(1)

    override fun addEdge(objectId: Int, classDefinition: ClassDefinition): Node {
      var localEdges = edges
      if (localEdges == null) {
        localEdges = HashMap(1)
        edges = localEdges
      }
      val node = localEdges.getOrPut(classDefinition, GCRootPathsTree::RegularNode)
      node.pathsCount++
      node.instances.add(objectId)
      return node
    }
  }

  class RootNode : Node {
    // In root node each instance has a separate path
    private val edges = TIntObjectHashMap<Pair<RegularNode, ClassDefinition>>()

    override fun addEdge(objectId: Int, classDefinition: ClassDefinition): Node {
      val nullableNode = edges.get(objectId)?.first
      val node: RegularNode

      if (nullableNode != null) {
        node = nullableNode
      }
      else {
        val newNode = RegularNode()
        val pair = Pair(newNode, classDefinition)
        newNode.instances.add(objectId)
        edges.put(objectId, pair)
        node = newNode
      }
      node.pathsCount++
      return node
    }

    private fun calculateTotalInstanceCount(): Int {
      var result = 0
      edges.forEachValue { (node, _) ->
        result += node.pathsCount
        true
      }
      return result
    }

    data class StackEntry(
      val classDefinition: ClassDefinition,
      val node: RegularNode,
      val indent: String,
      val nextIndent: String
    )

    fun createHotPathReport(nav: ObjectNavigator, headLimit: Int, tailLimit: Int): String {
      val rootList = mutableListOf<Triple<Int, RegularNode, ClassDefinition>>()
      val result = StringBuilder()
      edges.forEachEntry { objectId, (node, classDef) ->
        rootList.add(Triple(objectId, node, classDef))
      }
      rootList.sortByDescending { it.second.pathsCount }
      val totalInstanceCount = calculateTotalInstanceCount()

      val minimumObjectsForReport = Math.min(
        MINIMUM_OBJECT_COUNT_FOR_REPORT,
        (Math.ceil(totalInstanceCount / 100.0) * MINIMUM_OBJECT_COUNT_PERCENT).toInt())

      // Show paths from roots that have at least MINIMUM_OBJECT_COUNT_PERCENT or MINIMUM_OBJECT_COUNT_FOR_REPORT objects.
      // Always show at least two paths.
      rootList.filterIndexed { index, (_, node, _) ->
        index <= 1 || node.pathsCount >= minimumObjectsForReport
      }.forEach { (rootObjectId, rootNode, rootObjectClass) ->
        val printFunc = { s: String -> result.appendln(s); Unit }

        val rootReasonString =
          (nav.getRootReasonForObjectId(rootObjectId.toLong())?.description
           ?: "<Couldn't find root description>")

        val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

        result.appendln("ROOT: $rootReasonString: ${rootNode.pathsCount} objects ($rootPercent%)")

        TruncatingPrintBuffer(headLimit, tailLimit, printFunc).use { buffer ->
          // Iterate over the hot path
          val stack = ArrayDeque<StackEntry>()
          stack.push(StackEntry(rootObjectClass, rootNode, "", ""))

          while (!stack.isEmpty()) {
            val (classDefinition, node, indent, nextIndent) = stack.pop()

            printReportLine(buffer::println,
                            node.pathsCount,
                            (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                            node.instances.size(),
                            node.edges == null,
                            indent,
                            classDefinition.prettyName)

            val currentNodeEdges = node.edges ?: continue
            val childrenToReport =
              currentNodeEdges
                .entries
                .sortedByDescending { it.value.pathsCount }
                .filterIndexed { index, e ->
                  index == 0 || e.value.pathsCount >= minimumObjectsForReport
                }
                .asReversed()

            if (childrenToReport.size == 1) {
              // No indentation for a single child
              stack.push(StackEntry(childrenToReport[0].key, childrenToReport[0].value, nextIndent, nextIndent))
            }
            else {
              // Don't report too deep paths
              if (nextIndent.length >= MAX_INDENT)
                printReportLine(buffer::println, null, null, null, true, nextIndent, "\\-[...]")
              else {
                // Add indentation only if there are 2+ children
                childrenToReport.forEachIndexed { index, e ->
                  if (index == 0) stack.push(StackEntry(e.key, e.value, "$nextIndent\\-", "$nextIndent  "))
                  else stack.push(StackEntry(e.key, e.value, "$nextIndent+-", "$nextIndent| "))
                }
              }
            }
          }
        }
      }
      return result.toString()
    }

    private fun printReportLine(printFunc: (String) -> Any,
                                pathsCount: Int?,
                                percent: Int?,
                                instanceCount: Int?,
                                lastInPath: Boolean,
                                indent: String,
                                text: String) {
      val pathsCountString = (pathsCount ?: "").toString().padStart(10)
      val percentString = (percent?.let { "$it%" } ?: "").padStart(4)
      val instanceCountString = (instanceCount ?: "").toString().padStart(10)
      val lastInPathString = if (lastInPath) "*" else " "

      printFunc("$pathsCountString $percentString $instanceCountString $lastInPathString $indent$text")
    }

    companion object {
      private const val MINIMUM_OBJECT_COUNT_FOR_REPORT = 10_000
      private const val MINIMUM_OBJECT_COUNT_PERCENT = 10
      private const val MAX_INDENT = 40
    }
  }
}
