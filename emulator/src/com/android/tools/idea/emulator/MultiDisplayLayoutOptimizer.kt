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
package com.android.tools.idea.emulator

import java.awt.Dimension
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
fun computeBestLayout(availableSpace: Dimension, rectangleSizes: List<Dimension>): LayoutNode {
  check(rectangleSizes.size <= MAX_LEAFS)
  val optimizer = LayoutOptimizer(rectangleSizes)
  return optimizer.optimize(availableSpace)
}

/**
 * Represents a panel.
 *
 * @param size dimensions of the panel contents before scaling
 */
sealed class LayoutNode(val size: Dimension)

/**
 * Represents a content panel.
 *
 * @param rectangleIndex the index in the `rectangleSizes` array corresponding to the content of the panel
 * @param size dimensions of the panel contents before scaling
 */
class LeafNode(val rectangleIndex: Int, size: Dimension) : LayoutNode(size) {
  override fun toString(): String {
    return "#$rectangleIndex"
  }
}

/**
 * Represents a split panel.
 *
 * @param splitType the direction of the split
 * @param firstChild the node representing the first subpanel
 * @param secondChild the node representing the second subpanel
 */
class SplitNode(
  val splitType: SplitType,
  val firstChild: LayoutNode,
  val secondChild: LayoutNode)
: LayoutNode(computeSize(splitType, firstChild, secondChild)) {

  /** The ratio of the size of the first child to the total size along the split direction. */
  val splitRatio: Double = when (splitType) {
    SplitType.HORIZONTAL -> firstChild.size.width.toDouble() / (firstChild.size.width + secondChild.size.width)
    SplitType.VERTICAL -> firstChild.size.height.toDouble() / (firstChild.size.height + secondChild.size.height)
  }

  override fun toString(): String {
    val splitChar = when (splitType) {
      SplitType.HORIZONTAL -> '|'
      SplitType.VERTICAL -> EM_DASH
    }
    val ratio = String.format("%.2g", splitRatio)
    return "[$firstChild] $splitChar [$secondChild] $ratio"
  }
}

enum class SplitType {
  /** Panels are side by side. */
  HORIZONTAL,
  /** One panel is above the other. */
  VERTICAL
}

private fun computeSize(splitType: SplitType, firstChild: LayoutNode, secondChild: LayoutNode): Dimension {
  return when (splitType) {
    SplitType.HORIZONTAL -> {
      Dimension(firstChild.size.width + secondChild.size.width, max(firstChild.size.height, secondChild.size.height))
    }
    SplitType.VERTICAL -> {
      Dimension(max(firstChild.size.width, secondChild.size.width), firstChild.size.height + secondChild.size.height)
    }
  }
}

private const val EM_DASH = '\u2014'

private class LayoutOptimizer(private val rectangleSizes: List<Dimension>) {

  /** Offset of the first child node indexed by the offset of the parent node. */
  private val childrenOffsetByParentOffset = IntArray(LEVEL3_OFFSET) { offset ->
    when {
      offset < LEVEL1_OFFSET -> LEVEL1_OFFSET + offset
      offset < LEVEL2_OFFSET -> LEVEL2_OFFSET + (offset - LEVEL1_OFFSET) * LEVEL1_MAX_CHILDREN
      else -> LEVEL3_OFFSET + (offset - LEVEL2_OFFSET) * LEVEL2_MAX_CHILDREN
    }
  }

  /** Maximum allowed number of children indexed by the offset of the parent node. */
  private val maxChildrenByOffset = IntArray(NODES_ARRAY_SIZE) { offset ->
    when {
      offset < LEVEL1_OFFSET -> LEVEL0_MAX_CHILDREN
      offset < LEVEL2_OFFSET -> LEVEL1_MAX_CHILDREN
      offset < LEVEL3_OFFSET -> LEVEL2_MAX_CHILDREN
      else -> LEVEL3_MAX_CHILDREN
    }
  }

  fun optimize(availableSpace: Dimension): LayoutNode {
    // The layout tree used by this method is stored in an array in a way similar to the heap data structure shown
    // at https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/Heap-as-array.svg/260px-Heap-as-array.svg.png.
    // In our case the tree is not binary. The root node may have up to 4 (LEVEL0_MAX_CHILDREN) children.
    // The children of the root may have up to 3 (LEVEL1_MAX_CHILDREN) children each. The grandchildren of the root
    // may have 2 (LEVEL2_MAX_CHILDREN) or no children. Only leaf nodes may be great-grandchildren of the root.
    // The node objects don't contain any references to make changing parts of the layout tree and copying of
    // the whole tree easier.
    val indices = IntArray(rectangleSizes.size) { i -> i }
    val currentLayout = Array<Node?>(NODES_ARRAY_SIZE) { null }
    val bestLayout = Array<Node?>(NODES_ARRAY_SIZE) { null }
    var bestScale = 0.0
    val layoutGenerator = LayoutGenerator(indices, null, 0, currentLayout)
    while (layoutGenerator.next()) {
      val scale = calculateScale(availableSpace, currentLayout)
      if (bestScale < scale) {
        bestScale = scale
        currentLayout.copyInto(bestLayout)
      }
    }
    return buildLayoutTree(bestLayout[0]!!, bestLayout)
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

  fun calculateScale(availableSpace: Dimension, layoutNodes: Array<Node?>): Double {
    val size = calculateSize(layoutNodes[0]!!, layoutNodes)
    return min(availableSpace.getWidth() / size.width, availableSpace.getHeight() / size.height)
  }

  fun calculateSize(node: Node, layoutNodes: Array<Node?>): Dimension {
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
          for (index in partition.indices) {
            storage[childOffset] = Node.Leaf(index, childOffset)
            childOffset++
          }
        }
        childGenerators.fill(null)
      }
      else {
        storage[offset] = Node.Split(splitType, 2, offset)
        val childOffset = childrenOffsetByParentOffset[offset]
        val partitionsAsList = partitions.toList()
        for (i in 0 until 2) {
          childGenerators[i] = LayoutGenerator(partitionsAsList[i], splitType, childOffset + i, storage).apply { next() }
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
      val values = ArrayList<SplitType>(SplitType.values().asList())
      values.remove(splitTypeToAvoid)
      return values.iterator()
    }
  }

  private sealed class Node(val offset: Int) {
    class Leaf(val rectangleIndex: Int, offset: Int) : Node(offset)

    class Split(val splitType: SplitType, val childrenCount: Int, offset: Int) : Node(offset)
  }
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
  val indices = IntArray(array.size) { 0 }

  override fun hasNext(): Boolean {
    return size > 1 || (size > 0 && array.size < 2)
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
    val second = IntArray(array.size - size) { 0 }
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

private const val MAX_LEAFS = 4
private const val LEVEL0_OFFSET = 0
private const val LEVEL0_SIZE = 1
private const val LEVEL0_MAX_CHILDREN = MAX_LEAFS
private const val LEVEL1_OFFSET = LEVEL0_OFFSET + LEVEL0_SIZE
private const val LEVEL1_SIZE = LEVEL0_MAX_CHILDREN
private const val LEVEL1_MAX_CHILDREN = LEVEL0_MAX_CHILDREN - 1
private const val LEVEL2_OFFSET = LEVEL1_OFFSET + LEVEL1_SIZE
private const val LEVEL2_SIZE = LEVEL1_SIZE * LEVEL1_MAX_CHILDREN
private const val LEVEL2_MAX_CHILDREN = LEVEL1_MAX_CHILDREN - 1
private const val LEVEL3_OFFSET = LEVEL2_OFFSET + LEVEL2_SIZE
private const val LEVEL3_SIZE = LEVEL2_SIZE * LEVEL2_MAX_CHILDREN
private const val LEVEL3_MAX_CHILDREN = 0
private const val NODES_ARRAY_SIZE = LEVEL3_OFFSET + LEVEL3_SIZE