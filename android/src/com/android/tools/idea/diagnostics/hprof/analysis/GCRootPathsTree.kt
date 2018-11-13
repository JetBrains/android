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

  fun printTree(headLimit: Int, tailCount: Int): String {
    return topNode.createHotPathReport(nav, headLimit, tailCount)
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

    fun createHotPathReport(nav: ObjectNavigator, limit: Int, tailCount: Int): String {
      val rootList = mutableListOf<Triple<Int, RegularNode, ClassDefinition>>()
      val result = StringBuilder()
      edges.forEachEntry { objectId, (node, classDef) ->
        rootList.add(Triple(objectId, node, classDef))
      }
      rootList.sortByDescending { it.second.pathsCount }
      val totalInstanceCount = calculateTotalInstanceCount()

      // Show paths from roots that have at least 20% or MINIMUM_OBJECT_COUNT_FOR_REPORT objects (whichever is more).
      // Always show at least one path.
      val minimumObjectsForReport = Math.max(MINIMUM_OBJECT_COUNT_FOR_REPORT, totalInstanceCount / 100 * MINIMUM_OBJECT_COUNT_PERCENT)
      rootList.filterIndexed { index, (_, node, _) ->
        index == 0 || node.pathsCount >= minimumObjectsForReport
      }.forEach { (rootObjectId, rootObjectNode, rootObjectClass) ->
        val printFunc = { s: String -> result.appendln(s); Unit }

        val rootReasonString = (nav.getRootReasonForObjectId(rootObjectId.toLong())?.description
                                ?: "<Couldn't find root description>")
        val rootPercent = (100.0 * rootObjectNode.pathsCount / totalInstanceCount).toInt()
        result.appendln("ROOT: $rootReasonString: ${rootObjectNode.pathsCount} objects ($rootPercent%)")

        TruncatingPrintBuffer(limit, tailCount, printFunc).use { buffer ->
          // Iterate over the hot path
          var currentNode = rootObjectNode
          var currentClassDefinition = rootObjectClass
          while (true) {
            val pathsCountString = currentNode.pathsCount.toString().padStart(10)
            val instanceCountString = currentNode.instances.size().toString().padStart(10)

            val percent = (100.0 * currentNode.pathsCount / totalInstanceCount).toInt().toString().padStart(3)
            buffer.println("$pathsCountString $percent% $instanceCountString ${currentClassDefinition.prettyName}")

            val currentNodeEdges = currentNode.edges ?: break
            val (classDefinition, node) = currentNodeEdges.entries.maxBy { it.value.pathsCount } ?: break
            currentNode = node
            currentClassDefinition = classDefinition
          }
        }
      }
      return result.toString()
    }

    companion object {
      private const val MINIMUM_OBJECT_COUNT_FOR_REPORT = 5_000
      private const val MINIMUM_OBJECT_COUNT_PERCENT = 20
    }
  }
}

