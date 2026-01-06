/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.util.scaled
import java.awt.Dimension
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * This function computes a layout consisting of split panels and up to 4 content panels. Each split
 * panel contains two subpanels, each of which may be either a content or split panel. The subpanels
 * of a split panel are either located side by side ([SplitType.HORIZONTAL]) or one above the other
 * ([SplitType.VERTICAL]). All content panels display their content using the same scale factor.
 * The optimal layout maximizes this scale factor given dimensions of the available screen area.
 *
 * @param availableSpace the dimensions of the available screen area
 * @param rectangleSizes dimensions of the rectangles representing contents of all content panels
 *     before scaling. The size of this array may not exceed 4.
 */
internal fun computeBestLayout(availableSpace: Dimension, rectangleSizes: List<Dimension>): LayoutNode {
  require(availableSpace.width > 0)
  require(availableSpace.height > 0)
  require(rectangleSizes.isNotEmpty())
  if (rectangleSizes.size < 2) {
    return LeafNode(0, availableSpace)
  }
  val optimizer = LayoutOptimizer(rectangleSizes)
  return optimizer.optimize(availableSpace)
}

/**
 * Represents a panel.
 *
 * @param contentSize dimensions of the panel contents before scaling
 */
internal sealed class LayoutNode(val contentSize: Dimension) {

  val allocatedSize: Dimension = Dimension()

  fun isSpaceAllocated(): Boolean =
      allocatedSize.width != 0 || allocatedSize.height != 0
}

/**
 * Represents a content panel.
 *
 * @param rectangleIndex the index in the `rectangleSizes` array corresponding to the content of the panel
 * @param contentSize dimensions of the panel contents before scaling
 */
internal class LeafNode(val rectangleIndex: Int, contentSize: Dimension) : LayoutNode(contentSize) {

  override fun toString(): String =
      "#$rectangleIndex"
}

/**
 * Represents a split panel.
 *
 * @param splitType the direction of the split
 * @param firstChild the node representing the first subpanel
 * @param secondChild the node representing the second subpanel
 */
internal class SplitNode(
  val splitType: SplitType,
  val firstChild: LayoutNode,
  val secondChild: LayoutNode)
: LayoutNode(computeContentSize(splitType, firstChild, secondChild)) {

  /** The ratio of the size of the first child to the total size along the split direction. */
  val splitRatio: Double
    get() = when (splitType) {
      SplitType.HORIZONTAL -> firstChild.allocatedSize.width.toDouble() / allocatedSize.width
      SplitType.VERTICAL -> firstChild.allocatedSize.height.toDouble() / allocatedSize.height
    }

  override fun toString(): String {
    val splitChar = when (splitType) {
      SplitType.HORIZONTAL -> '|'
      SplitType.VERTICAL -> EM_DASH
    }
    return when {
      isSpaceAllocated() -> "[$firstChild] $splitChar [$secondChild] ${String.format(Locale.ROOT, "%.2g", splitRatio)}"
      else -> "[$firstChild] $splitChar [$secondChild]"
    }
  }
}

private fun computeContentSize(splitType: SplitType, firstChild: LayoutNode, secondChild: LayoutNode): Dimension {
  return when (splitType) {
    SplitType.HORIZONTAL -> {
      Dimension(firstChild.contentSize.width + secondChild.contentSize.width,
                max(firstChild.contentSize.height, secondChild.contentSize.height))
    }
    SplitType.VERTICAL -> {
      Dimension(max(firstChild.contentSize.width, secondChild.contentSize.width),
                firstChild.contentSize.height + secondChild.contentSize.height)
    }
  }
}

private const val EM_DASH = '\u2014'

private class LayoutOptimizer(private val rectangleSizes: List<Dimension>) {

  private val levels = populateLevelParameters(rectangleSizes.size)
  private val nodesArraySize = levels.last().offset + levels.last().size

  /** Offset of the first child node indexed by the offset of the parent node. */
  private val childrenOffsetByParentOffset = IntArray(levels.last().offset) { offset ->
    var multiplier = 1
    for (i in 1 until levels.size) {
      val level = levels[i]
      if (offset < level.offset) {
        return@IntArray level.offset + (offset - levels[i - 1].offset) * multiplier
      }
      multiplier = level.maxChildren
    }
    throw AssertionError("Internal error")
  }

  /** Maximum allowed number of children indexed by the offset of the parent node. */
  private val maxChildrenByOffset = IntArray(nodesArraySize) { offset ->
    for (i in 1 until levels.size) {
      if (offset < levels[i].offset) {
        return@IntArray levels[i - 1].maxChildren
      }
    }
    return@IntArray levels.last().maxChildren
  }


  fun optimize(availableSpace: Dimension): LayoutNode {
    // The layout tree used by this method is stored in an array in a way similar to the heap data structure shown
    // at https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/Heap-as-array.svg/260px-Heap-as-array.svg.png.
    // In our case the tree is not binary. The root node may have up to [rectangleSizes.size] children.
    // The children of the root may have up to [rectangleSizes.size - 1] children each. The grandchildren of
    // the root may have up to [rectangleSizes.size - 2], etc. Only leaf nodes may be at the last level.
    // The node objects don't contain any references to make changing parts of the layout tree and copying of
    // the whole tree easier.
    val indices = IntArray(rectangleSizes.size) { i -> i }
    val currentLayout = Array<Node?>(nodesArraySize) { null }
    val bestLayout = Array<Node?>(nodesArraySize) { null }
    var bestScale = -1.0
    val layoutGenerator = LayoutGenerator(indices, null, 0, currentLayout)
    while (layoutGenerator.next()) {
      val scale = calculateScale(availableSpace, currentLayout)
      if (bestScale < scale) {
        bestScale = scale
        currentLayout.copyInto(bestLayout)
      }
    }
    val layout = buildLayoutTree(bestLayout[0]!!, bestLayout)
    allocateSpace(layout, availableSpace.width, availableSpace.height)
    return layout
  }

  private fun populateLevelParameters(numLevels: Int): Array<LevelParams> {
    var upperLevel: LevelParams? = null
    return Array(numLevels) {
      upperLevel?.let { level ->
        val maxChildren = if (level.maxChildren > 2) level.maxChildren - 1 else 0
        LevelParams(level.offset + level.size, level.size * level.maxChildren, maxChildren).also { upperLevel = it }
      } ?: LevelParams(0, 1, numLevels).also { upperLevel = it }
    }
  }

  private fun buildLayoutTree(optimizationNode: Node, optimizationNodes: Array<Node?>): LayoutNode {
    if (optimizationNode is Node.Leaf) {
      return LeafNode(optimizationNode.rectangleIndex, rectangleSizes[optimizationNode.rectangleIndex])
    }

    optimizationNode as Node.Split
    var childOffset = childrenOffsetByParentOffset[optimizationNode.offset]
    var node = buildLayoutTree(optimizationNodes[childOffset++]!!, optimizationNodes)
    for (i in 1 until optimizationNode.childrenCount) {
      val nextNode = buildLayoutTree(optimizationNodes[childOffset++]!!, optimizationNodes)
      node = SplitNode(optimizationNode.splitType, node, nextNode)
    }
    return node
  }

  private fun allocateSpace(node: LayoutNode, width: Int, height: Int) {
    val allocatedSize = node.allocatedSize
    allocatedSize.setSize(width, height)
    if (node !is SplitNode || node.firstChild.isSpaceAllocated() && node.secondChild.isSpaceAllocated()) {
      return
    }

    val splitType = node.splitType
    var available = allocatedSize.longitudinalComponent(splitType)
    val transverse = allocatedSize.transverseComponent(splitType)
    var contentSize = node.contentSize.longitudinalComponent(splitType)
    val splitParticipants = getSplitParticipants(node)
    var maxUsable = 0
    for (n in splitParticipants) {
      maxUsable += n.contentSize.maxUsableSize(splitType, allocatedSize)
    }
    if (maxUsable <= available) {
      val scale = available.toDouble() / maxUsable
      for (n in splitParticipants) {
        val size = if (n === splitParticipants.last()) available else n.contentSize.maxUsableSize(splitType, allocatedSize).scaled(scale)
        available -= size
        allocateSpace(n, splitType, size, transverse)
      }
    }
    else {
      var count = splitParticipants.size
      var previousProgress = true
      while (count > 0) {
        val scale = available.toDouble() / contentSize
        var progress = false
        for (n in splitParticipants) {
          if (!n.isSpaceAllocated()) {
            val fitScale = n.contentSize.fitScale(allocatedSize)
            val s = if (previousProgress) fitScale else scale
            if (s <= scale) {
              val size = if (--count == 0) available else n.contentSize.longitudinalComponent(splitType).scaled(s)
              available -= size
              contentSize -= n.contentSize.longitudinalComponent(splitType)
              allocateSpace(n, splitType, size, transverse)
              progress = true
            }
          }
        }
        previousProgress = progress
      }
    }

    if (splitParticipants.size > 2) {
      node.firstChild.allocateSpaceFromChildren()
      assert(node.secondChild.isSpaceAllocated())
    }
  }

  private fun allocateSpace(node: LayoutNode, splitType: SplitType, longitudinal: Int, transverse: Int) {
    when (splitType) {
      SplitType.HORIZONTAL -> allocateSpace(node, longitudinal, transverse)
      SplitType.VERTICAL -> allocateSpace(node, transverse, longitudinal)
    }
  }

  private fun LayoutNode.allocateSpaceFromChildren() {
    if (isSpaceAllocated() || this !is SplitNode) {
      return
    }
    firstChild.allocateSpaceFromChildren()
    secondChild.allocateSpaceFromChildren()
    val l = firstChild.allocatedSize.longitudinalComponent(splitType) + secondChild.allocatedSize.longitudinalComponent(splitType)
    val t = max(firstChild.allocatedSize.transverseComponent(splitType), secondChild.allocatedSize.transverseComponent(splitType))
    allocateSpace(this, splitType, l, t)
  }

  private fun getSplitParticipants(splitNode: SplitNode): MutableList<LayoutNode> {
    val participants = mutableListOf<LayoutNode>()
    var node: LayoutNode = splitNode
    while (node is SplitNode && node.splitType == splitNode.splitType) {
      participants.add(node.secondChild)
      node = node.firstChild
    }
    participants.add(node)
    participants.reverse()
    return participants
  }

  private fun calculateScale(availableSpace: Dimension, layoutNodes: Array<Node?>): Double {
    val size = calculateSize(layoutNodes[0]!!, layoutNodes)
    return min(availableSpace.width.toDouble() / size.width, availableSpace.height.toDouble() / size.height)
  }

  private fun calculateSize(node: Node, layoutNodes: Array<Node?>): Dimension {
    if (node is Node.Leaf) {
      return rectangleSizes[node.rectangleIndex]
    }

    node as Node.Split
    var width = 0
    var height = 0
    var childOffset = childrenOffsetByParentOffset[node.offset]
    for (i in 0 until node.childrenCount) {
      val childSize = calculateSize(layoutNodes[childOffset++]!!, layoutNodes)
      when (node.splitType) {
        SplitType.HORIZONTAL -> {
          width += childSize.width
          height = height.coerceAtLeast(childSize.height)
        }
        SplitType.VERTICAL -> {
          width = width.coerceAtLeast(childSize.width)
          height += childSize.height
        }
      }
    }
    return Dimension(width, height)
  }

  private inner class LayoutGenerator(val indices: IntArray, splitTypeToAvoid: SplitType?, val offset: Int, val storage: Array<Node?>) {
    val splitTypeIterator = createSplitTypeIterator(splitTypeToAvoid)
    var splitType = splitTypeIterator.next()
    var partitionGenerator = PartitionGenerator(indices)
    var childGenerators = Array<LayoutGenerator?>(2) { null }

    fun next(): Boolean {
      for (childGenerator in childGenerators) {
        val generator = childGenerator ?: break
        if (generator.next()) {
          return true
        }
      }

      if (!partitionGenerator.hasNext()) {
        if (!splitTypeIterator.hasNext()) {
          return false
        }
        splitType = splitTypeIterator.next()
        partitionGenerator = PartitionGenerator(indices)
      }

      val partitions = partitionGenerator.next()
      clearDescendants(offset) // Clearing descendants is not strictly necessary but makes debugging easier.

      if (partitions.second.isEmpty()) {
        val partition = partitions.first
        if (partition.size == 1) {
          storage[offset] = Node.Leaf(partition[0], offset)
        }
        else {
          storage[offset] = Node.Split(splitType, partition.size, offset)
          var childOffset = childrenOffsetByParentOffset[offset]
          for (index in partition) {
            storage[childOffset] = Node.Leaf(index, childOffset)
            childOffset++
          }
        }
        childGenerators.fill(null)
      }
      else {
        storage[offset] = Node.Split(splitType, 2, offset)
        val childOffset = childrenOffsetByParentOffset[offset]
        for (i in 0 until 2) {
          childGenerators[i] = LayoutGenerator(partitions[i], splitType, childOffset + i, storage).apply { next() }
        }
      }
      return true
    }

    private fun clearDescendants(offset: Int) {
      val numChildren = maxChildrenByOffset[offset]
      if (numChildren == 0) {
        return
      }
      var childOffset = childrenOffsetByParentOffset[offset]
      for (i in 0 until numChildren) {
        storage[childOffset] = null
        clearDescendants(childOffset)
        childOffset++
      }
    }

    private fun createSplitTypeIterator(splitTypeToAvoid: SplitType?): Iterator<SplitType> {
      val values = if (splitTypeToAvoid == null) SplitType.entries else SplitType.entries - splitTypeToAvoid
      return values.iterator()
    }

    private operator fun <T> Pair<T, T>.get(i: Int): T {
      return when (i) {
        0 -> first
        1 -> second
        else -> throw IllegalArgumentException()
      }
    }
  }

  private sealed class Node(val offset: Int) {
    class Leaf(val rectangleIndex: Int, offset: Int) : Node(offset)

    class Split(val splitType: SplitType, val childrenCount: Int, offset: Int) : Node(offset)
  }

  private class LevelParams(val offset: Int, val size: Int, val maxChildren: Int)
}

/**
 * Generates all partitions of the given array into two arrays satisfying the following conditions:
 * 1. The first array always contains the first element of the source array
 * 2. The second array may be empty
 * 3. If the source array contains more than one element, at least one of the generated arrays
 *    contains more than one element
 */
private class PartitionGenerator(val array: IntArray) : Iterator<Pair<IntArray, IntArray>> {
  var size = array.size
  val indices = IntArray(array.size)

  override fun hasNext(): Boolean {
    return size > 1 || (size > 0 && (array.size == 1 || array.size - size > 1))
  }

  override fun next(): Pair<IntArray, IntArray> {
    if (!hasNext()) {
      throw NoSuchElementException()
    }
    if (size == array.size) {
      decrementSize()
      return Pair(array, intArrayOf())
    }

    val first = IntArray(size) { i -> array[indices[i]] }
    val second = IntArray(array.size - size)
    var i = 0
    for (index in array.indices) {
      if (index !in indices) {
        second[i++] = array[index]
      }
    }

    i = size
    while (true) {
      if (--i <= 0) {
        decrementSize()
        break
      }
      if (indices[i] < indices[i + 1] - 1) {
        indices[i]++
        break
      }
    }

    return Pair(first, second)
  }

  private fun decrementSize() {
    size--
    indices[size] = array.size
    for (i in 0 until size) {
      indices[i] = i
    }
  }
}

private fun Dimension.longitudinalComponent(splitType: SplitType): Int {
  return when (splitType) {
    SplitType.HORIZONTAL -> width
    SplitType.VERTICAL -> height
  }
}

private fun Dimension.transverseComponent(splitType: SplitType): Int {
  return when (splitType) {
    SplitType.HORIZONTAL -> height
    SplitType.VERTICAL -> width
  }
}

private fun Dimension.maxUsableSize(splitType: SplitType, maxSize: Dimension): Int =
    longitudinalComponent(splitType).scaled(fitScale(maxSize))

/**
 * Returns the maximum scale factor such that a rectangle with [this] dimensions would fit in
 * a rectangle with [maxSize] dimensions.
 */
private fun Dimension.fitScale(maxSize: Dimension): Double =
    min(maxSize.width.toDouble() / width, maxSize.height.toDouble() / height)

