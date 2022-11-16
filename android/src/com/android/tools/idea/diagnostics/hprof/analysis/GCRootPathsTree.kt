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
import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.STRING_PADDING_FOR_COUNT
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.STRING_PADDING_FOR_SIZE
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntObjectHashMap
import java.util.ArrayDeque
import java.util.HashMap

class GCRootPathsTree(
  private val context: AnalysisContext,
  private val treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
  allObjectsOfClass: ClassDefinition?
) {
  private val topNode = RootNode(context.classStore)
  private var countOfIgnoredObjects = 0

  private val objectSizeStrategy = ObjectSizeCalculationStrategy.getBestStrategyForClass(allObjectsOfClass)

  private enum class Status {
    None,
    Warning,
    LastInPath;

    companion object {
      fun getStatus(refIndex: Int, lastInPath: Boolean, disposed: Boolean): Status = when {
        lastInPath -> LastInPath
        disposed -> Warning
        refIndex == RefIndexUtil.SOFT_REFERENCE -> Warning
        refIndex == RefIndexUtil.WEAK_REFERENCE -> Warning
        else -> None
      }
    }

    override fun toString(): String = getStatusCharacter().toString()

    fun getStatusCharacter(): Char = when (this) {
      None -> ' '
      Warning -> '!'
      LastInPath -> '*'
    }
  }

  interface ObjectSizeCalculationStrategy {
    fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int

    companion object {
      fun getBestStrategyForClass(classDefinition: ClassDefinition?): ObjectSizeCalculationStrategy {
        if (classDefinition == null || classDefinition.isArray()) {
          return SizeFromObjectNavigatorStrategy()
        }
        else if (classDefinition.name == "java.nio.DirectByteBuffer") {
          // When focusing on DirectByteBuffers, add sizes of native arrays.
          return DirectByteBufferNativeSizeStrategy(classDefinition)
        }
        else {
          // If all objects are of the same class and not arrays then instance size can be computed only once.
          return AllObjectsSameSizeStrategy(classDefinition.instanceSize + ClassDefinition.OBJECT_PREAMBLE_SIZE)
        }
      }
    }
  }

  private class AllObjectsSameSizeStrategy(private val objectSize: Int) : ObjectSizeCalculationStrategy {
    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int = objectSize
  }

  private class SizeFromObjectNavigatorStrategy : ObjectSizeCalculationStrategy {
    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int {
      nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.NO_REFERENCES)
      return nav.getObjectSize()
    }
  }

  private class DirectByteBufferNativeSizeStrategy(private val classDefinition: ClassDefinition) : ObjectSizeCalculationStrategy {
    init {
      assert(classDefinition.name == "java.nio.DirectByteBuffer")
    }

    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int {
      nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
      assert(nav.getClass() == classDefinition)
      return nav.getExtraData() + nav.getObjectSize()
    }
  }

  fun registerObject(objectId: Int) {
    val nav = context.navigator
    val parentMapping = context.parentList
    val refIndexMapping = context.refIndexList
    val sizesMapping = context.sizesList
    val disposedObjectsIDsSet = context.disposedObjectsIDs

    val gcPath = TIntArrayList()
    val fieldsPath = TIntArrayList()
    val sizesPath = TIntArrayList()
    var objectIterationId = objectId
    var parentId = parentMapping[objectIterationId]
    var count = 0

    val maxTreeDepth = treeDisplayOptions.maximumTreeDepth
    while (count < maxTreeDepth && parentId != objectIterationId) {
      gcPath.add(objectIterationId)
      fieldsPath.add(refIndexMapping[objectIterationId])
      sizesPath.add(sizesMapping[objectIterationId])
      objectIterationId = parentId
      parentId = parentMapping[objectIterationId]
      count++
    }

    // No field for a root node
    fieldsPath.add(RefIndexUtil.ROOT)

    if (parentId != objectIterationId) {
      // Object ignored as its GC-root path is too long
      countOfIgnoredObjects++
      return
    }

    gcPath.add(objectIterationId)
    sizesPath.add(sizesMapping[objectIterationId])

    assert(gcPath.size() == fieldsPath.size())

    val size = objectSizeStrategy.calculateObjectSize(nav, objectId)
    var currentNode: Node = topNode
    for (i in gcPath.size() - 1 downTo 0) {
      val id = gcPath[i]
      val classDefinition = nav.getClassForObjectId(id.toLong())
      currentNode = currentNode.addEdge(id, size, sizesPath[i], classDefinition, fieldsPath[i].toByte(), disposedObjectsIDsSet.contains(id))
    }
  }

  fun printTree(): String = buildString {
    if (countOfIgnoredObjects > 0) {
      append("Ignored ${countOfIgnoredObjects} too-deep objects\n")
    }
    val rootReasonGetter = { id: Int ->
      (context.navigator.getRootReasonForObjectId(id.toLong())?.description ?: "<Couldn't find root description>")
    }
    append(topNode.createHotPathReport(treeDisplayOptions, rootReasonGetter))
  }

  fun getDisposedDominatorNodes(): Map<ClassDefinition, List<RegularNode>> {
    val result = HashMap<ClassDefinition, MutableList<RegularNode>>()
    topNode.collectDisposedDominatorNodes(result)
    return result
  }

  interface Node {
    fun addEdge(objectId: Int,
                objectSize: Int,
                subgraphSizeInDwords: Int,
                classDefinition: ClassDefinition,
                refIndex: Byte,
                disposed: Boolean): Node
  }

  data class Edge(val classDefinition: ClassDefinition, val refIndex: Byte, val disposed: Boolean)

  class RegularNode : Node {

    // In regular nodes paths are grouped by class definition
    var edges: HashMap<Edge, RegularNode>? = null
    var pathsCount = 0
    var pathsSize = 0
    var totalSizeInDwords = 0
    val instances = TIntHashSet(1)

    override fun addEdge(objectId: Int,
                         objectSize: Int,
                         subgraphSizeInDwords: Int,
                         classDefinition: ClassDefinition,
                         refIndex: Byte,
                         disposed: Boolean): Node {
      var localEdges = edges
      if (localEdges == null) {
        localEdges = HashMap(1)
        edges = localEdges
      }
      val node = localEdges.getOrPut(Edge(classDefinition, refIndex, disposed)) { RegularNode() }
      node.pathsCount++
      if (node.pathsSize + objectSize.toLong() > Int.MAX_VALUE) {
        node.pathsSize = Int.MAX_VALUE
      }
      else {
        node.pathsSize += objectSize
      }

      val added = node.instances.add(objectId)
      if (added) {
        if (node.totalSizeInDwords + subgraphSizeInDwords.toLong() > Int.MAX_VALUE) {
          node.totalSizeInDwords = Int.MAX_VALUE
        }
        else {
          node.totalSizeInDwords += subgraphSizeInDwords
        }
      }
      return node
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      val stack = ArrayDeque<RegularNode>()
      stack.push(this)
      while (stack.isNotEmpty()) {
        val currentNode = stack.pop()
        currentNode.edges?.forEach { (edge, childNode) ->
          if (edge.disposed) {
            result.getOrPut(edge.classDefinition) { mutableListOf() }.add(childNode)
          }
          else {
            stack.push(childNode)
          }
        }
      }
    }
  }

  class RootNode(private val classStore: ClassStore) : Node {

    // In root node each instance has a separate path
    val edges = TIntObjectHashMap<Pair<RegularNode, Edge>>()

    override fun addEdge(objectId: Int,
                         objectSize: Int,
                         subgraphSizeInDwords: Int,
                         classDefinition: ClassDefinition,
                         refIndex: Byte,
                         disposed: Boolean): Node {
      val nullableNode = edges.get(objectId)?.first
      val node: RegularNode

      if (nullableNode != null) {
        node = nullableNode
      }
      else {
        val newNode = RegularNode()
        val pair = Pair(newNode, Edge(classDefinition, refIndex, disposed))
        newNode.instances.add(objectId)
        edges.put(objectId, pair)
        node = newNode
        node.totalSizeInDwords = subgraphSizeInDwords
      }
      node.pathsCount++
      if (node.pathsSize + objectSize.toLong() > Int.MAX_VALUE) {
        node.pathsSize = Int.MAX_VALUE
      }
      else {
        node.pathsSize += objectSize
      }

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
      val parentClass: ClassDefinition?,
      val edge: Edge,
      val node: RegularNode,
      val indent: String,
      val nextIndent: String
    )

    fun createHotPathReport(treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                            rootReasonGetter: (Int) -> String): String = buildString {
      val rootList = mutableListOf<Triple<Int, RegularNode, Edge>>()
      edges.forEachEntry { objectId, (node, edge) ->
        rootList.add(Triple(objectId, node, edge))
      }
      val totalInstanceCount = calculateTotalInstanceCount()

      val minimumObjectsForReport = Math.min(
        treeDisplayOptions.minimumObjectCount,
        (Math.ceil(totalInstanceCount / 100.0) * treeDisplayOptions.minimumObjectCountPercent).toInt())

      // Show paths from roots that have at least minimumObjectCountPercent%, minimumObjectCount objects or size of all reported objects
      // in the subtree is more than minimumObjectSize.
      // Always show at least two paths.
      rootList
        .sortedByDescending { it.second.pathsSize }
        .filterIndexed { index, (_, node, _) ->
          index < treeDisplayOptions.minimumPaths ||
          node.pathsCount >= minimumObjectsForReport ||
          node.pathsSize >= treeDisplayOptions.minimumObjectSize
        }
        .forEachIndexed { index, (rootObjectId, rootNode, rootEdge) ->
          val rootReasonString = rootReasonGetter(rootObjectId)
          val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

          appendln("Root ${index + 1}:")
          printReportLine(this::appendln,
                          rootNode.pathsCount,
                          rootPercent,
                          rootNode.pathsSize,
                          rootNode.totalSizeInDwords.toLong() * 4,
                          1,
                          Status.getStatus(RefIndexUtil.ROOT, false, false),
                          false,
                          null,
                          "",
                          "ROOT: $rootReasonString")

          TruncatingPrintBuffer(treeDisplayOptions.headLimit, treeDisplayOptions.tailLimit, this::appendln).use { buffer ->
            // Iterate over the hot path
            val stack = ArrayDeque<StackEntry>()
            stack.push(StackEntry(null, rootEdge, rootNode, "", ""))

            while (!stack.isEmpty()) {
              val (parentClass, edge, node, indent, nextIndent) = stack.pop()
              val (classDefinition, refIndexByte, disposed) = edge
              val refIndex = java.lang.Byte.toUnsignedInt(refIndexByte)

              printReportLine(buffer::println,
                              node.pathsCount,
                              (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                              node.pathsSize,
                              node.totalSizeInDwords.toLong() * 4,
                              node.instances.size(),
                              Status.getStatus(refIndex, node.edges == null, disposed),
                              disposed,
                              RefIndexUtil.getFieldDescription(refIndex, parentClass, classStore),
                              indent,
                              classDefinition.prettyName)

              val currentNodeEdges = node.edges ?: continue
              val childrenToReport =
                currentNodeEdges
                  .entries
                  .sortedWith { a, b ->
                    if (a.value.pathsSize != b.value.pathsSize)
                      // Descending
                      b.value.pathsSize.compareTo(a.value.pathsSize)
                    else
                      // To have a deterministic report, sort by field# if the size is the same
                      a.key.refIndex.compareTo(b.key.refIndex)
                  }
                  .filterIndexed { index, e ->
                    index == 0 ||
                    e.value.pathsCount >= minimumObjectsForReport ||
                    e.value.pathsSize >= treeDisplayOptions.minimumObjectSize ||
                    e.value.totalSizeInDwords.toLong() * 4 >= treeDisplayOptions.minimumSubgraphSize
                  }
                  .asReversed()

              if (childrenToReport.size == 1 && treeDisplayOptions.smartIndent) {
                // No indentation for a single child
                stack.push(StackEntry(classDefinition, childrenToReport[0].key, childrenToReport[0].value, nextIndent, nextIndent))
              }
              else {
                // Don't report too deep paths
                if (nextIndent.length >= treeDisplayOptions.maximumIndent)
                  printReportLine(buffer::println,
                                  null, null, null, null,
                                  null, Status.LastInPath, null, null,
                                  nextIndent, "\\-[...]")
                else {
                  // Add indentation only if there are 2+ children
                  childrenToReport.forEachIndexed { index, e ->
                    if (index == 0) stack.push(StackEntry(classDefinition, e.key, e.value, "$nextIndent\\-", "$nextIndent  "))
                    else stack.push(StackEntry(classDefinition, e.key, e.value, "$nextIndent+-", "$nextIndent| "))
                  }
                }
              }
            }
          }
        }
    }

    private fun printReportLine(printFunc: (String) -> Any,
                                pathsCount: Int?,
                                percent: Int?,
                                instanceSize: Int?,
                                subgraphSize: Long?,
                                instanceCount: Int?,
                                status: Status,
                                disposed: Boolean?,
                                fieldName: String?,
                                indent: String,
                                text: String) {
      val pathsCountString = (pathsCount?.let { toShortStringAsCount(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_COUNT)
      val percentString = (percent?.let { "$it%" } ?: "").padStart(4)
      val instanceSizeString = (instanceSize?.let { toShortStringAsSize(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_SIZE)
      val instanceCountString = (instanceCount ?: "").toString().padStart(10)
      val fieldNameString = if (fieldName != null) "$fieldName: " else ""
      val disposedString = if (disposed == true) " (disposed)" else ""
      val subgraphSizeString = (subgraphSize?.let { toShortStringAsSize(it) } ?: "").padStart(STRING_PADDING_FOR_SIZE)

      printFunc(
        "[$pathsCountString/$percentString/$instanceSizeString] $subgraphSizeString $instanceCountString $status $indent$fieldNameString$text$disposedString")
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      edges.forEachValue { (node, edge) ->
        if (edge.disposed) {
            result.getOrPut(edge.classDefinition) { mutableListOf() }.add(node)
        } else {
          node.collectDisposedDominatorNodes(result)
        }
        true
      }
    }
  }
}
