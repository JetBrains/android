/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils

/** A "Sum Segment Tree" where each node stores the sum of its children's heights. */
internal class HeightSegmentTree(initialItemCount: Int, defaultHeight: Int) {
  /** The number of items stored in this tree */
  private var _itemCount = initialItemCount

  /** The number of leaf nodes for which we have a measured height. */
  private var _measuredCount = 0

  /**
   * The leaf nodes of the tree start at this offset in [treeArray]. It is the smallest power of 2 that is greater than or equal to
   * [_itemCount].
   */
  private var n = requiredTreeCapacity(initialItemCount)

  /**
   * Internal array representation of the segment tree.
   *
   * The tree is stored in a flat array of size 2*N, where N is a power of 2 (stored in `n`).
   *
   * Structure:
   * - Index 0: unused
   * - Index 1: Root of the tree (stores the sum of all item heights).
   * - Indices (1, n -1): Non-left nodes (store the sum of children's height).
   * - Indices (n, n + size - 1): Leaf nodes (store individual item heights).
   * - Indices (n + size, 2n - 1): Unused padding leaves (height 0).
   * - For any node at index `i`:
   *     - Left child: `2 * i`
   *     - Right child: `2 * i + 1`
   *     - Parent: `i / 2`
   *
   * Example for size = 4 (n = 4):
   * ```
   *              [1] (Sum 0..3)
   *             /           \
   *       [2] (Sum 0..1)     [3] (Sum 2..3)
   *       /      \           /      \
   *    [4] H(0)  [5] H(1)  [6] H(2)  [7] H(3)
   * ```
   */
  private var treeArray = HeightSlotArray(2 * n)

  @JvmInline
  private value class HeightSlot(val raw: Int) {
    inline val isEstimated: Boolean
      get() = raw < 0

    inline val isMeasured: Boolean
      get() = raw >= 0

    inline val value: Int
      get() = if (raw < 0) -raw else raw

    operator fun plus(a: HeightSlot): HeightSlot {
      return when {
        isEstimated || a.isEstimated -> estimated(value + a.value)
        else -> measured(value + a.value)
      }
    }

    companion object {
      fun measured(height: Int) = HeightSlot(maxOf(0, height))

      fun estimated(height: Int) = HeightSlot(-maxOf(0, height))
    }
  }

  @JvmInline
  private value class HeightSlotArray(private val intArray: IntArray) {
    val size: Int
      get() = intArray.size

    operator fun get(index: Int): HeightSlot = HeightSlot(intArray[index])

    operator fun set(index: Int, slot: HeightSlot) {
      intArray[index] = slot.raw
    }
  }

  private fun HeightSlotArray(size: Int): HeightSlotArray = HeightSlotArray(IntArray(size))

  init {
    // Initialize leaves with the default height estimate
    val paddingSlot = HeightSlot.estimated(defaultHeight)
    for (i in 0 until initialItemCount) {
      treeArray[n + i] = paddingSlot
    }
    _measuredCount = 0
    // Build the tree from the bottom up
    buildFromLeaves()
  }

  /** The number of items that have their heights stored in this tree, i.e. the number of leaf nodes. */
  val itemCount: Int
    get() = _itemCount

  /** The number of leaf nodes for which we have a measured height. */
  val measuredCount: Int
    get() = _measuredCount

  /** The number of leaf nodes for which we have an estimated height. */
  val estimatedCount: Int
    get() = _itemCount - _measuredCount

  /** The size of the underlying array used to store the tree (for debugging purposes only) */
  val capacity: Int
    get() = treeArray.size

  /**
   * Return the sum of the height of all items in the tree.
   *
   * Note: this is an O(1) operation
   */
  val totalHeight: Int
    get() = treeArray[1].value

  /**
   * Returns the average height of the items in the tree.
   *
   * Note: this is an O(1) operation
   */
  val avgHeight: Int
    get() = if (_itemCount == 0) 0 else totalHeight / _itemCount

  /**
   * Return a multi-line string showing the cumulative height to each item stored in the tree
   *
   * Note: this is an O(n) operation
   */
  override fun toString(): String {
    val sb = StringBuilder()
    var cumulative = HeightSlot.measured(0)
    for (i in 0 until _itemCount) {
      cumulative += treeArray[n + i]
      sb.append("[$i] ${cumulative.value} (${if (cumulative.isEstimated) "estimated" else "measured"})\n")
    }
    return sb.toString()
  }

  /**
   * Update the height of item at [itemIndex], and returns the previous height value for this item.
   *
   * Note: this is an O(log n) operation
   */
  fun update(itemIndex: Int, height: Int): Int {
    require(itemIndex in 0..<_itemCount) { "Invalid index (index=$itemIndex, size=$_itemCount)" }

    var i = n + itemIndex
    val previousSlot = treeArray[i]
    val previousHeight = previousSlot.value

    // If the height is identical, and we already marked it as measured, we can skip the update
    if (height == previousHeight && previousSlot.isMeasured) return previousHeight

    treeArray[i] = HeightSlot.measured(height)
    if (previousSlot.isEstimated) {
      _measuredCount++
    }

    // Bubble the update up to the root
    while (i > 1) {
      i /= 2
      treeArray[i] = treeArray[2 * i] + treeArray[2 * i + 1]
    }
    return previousHeight
  }

  /**
   * Update the height of all items in the tree, then update the sum nodes.
   *
   * Note: this is an O(n) operation
   */
  fun updateAll(updateHeight: (height: Int) -> Int) {
    // Update height of all leaves
    for (i in 0 until _itemCount) {
      val previousSlot = treeArray[n + i]
      val newHeight = updateHeight(previousSlot.value)
      treeArray[n + i] = if (previousSlot.isEstimated) HeightSlot.estimated(newHeight) else HeightSlot.measured(newHeight)
    }
    // Rebuild the tree from the bottom up
    buildFromLeaves()
  }

  /**
   * Returns the sum of heights from index 0 up to (but not including) [endItemIndex]
   *
   * Note: this is an O(log n) operation
   */
  fun totalHeightBefore(endItemIndex: Int): Int {
    require(endItemIndex in 0..itemCount) { "Invalid item index ($endItemIndex, count=$itemCount)" }
    var l = n
    var r = n + endItemIndex
    var sum = 0
    while (l < r) {
      if (l % 2 == 1) sum += treeArray[l++].value
      if (r % 2 == 1) sum += treeArray[--r].value
      l /= 2
      r /= 2
    }
    return sum
  }

  /**
   * Finds the largest index such that the sum of heights of items before it is <= [targetOffset]. Returns a Pair of (Index,
   * RemainingOffsetWithinItem).
   *
   * Note: this is an O(log n) operation
   */
  fun findIndexAtOffset(targetOffset: Int): Pair<Int, Int> {
    var i = 1
    var remainder = targetOffset

    // While we are not at a leaf node
    while (i < n) {
      val leftChildSum = treeArray[2 * i].value
      if (remainder < leftChildSum) {
        i *= 2 // Move to left child
      } else {
        remainder -= leftChildSum
        i = 2 * i + 1 // Move to right child
      }
    }

    val index = i - n
    return Pair(index, remainder)
  }

  /**
   * Ensures the tree has at least [newSize] items. If [newSize] is greater than the current [_itemCount], the tree is expanded. New items
   * are initialized with the [avgHeight] of existing items.
   *
   * Note: this is an O(n) operation if a resize is required, O(1) otherwise
   *
   * @return whether the tree size has changed
   */
  fun growTo(newSize: Int): Boolean {
    if (newSize <= _itemCount) return false
    rebuild(newSize, if (_itemCount > 0) avgHeight else 0)
    return true
  }

  /**
   * Truncates the tree to at most [newSize] items. If [newSize] is less than the current [_itemCount], the tree is shrunk.
   *
   * Note: this is an O(n) operation.
   *
   * @return whether the tree size has changed
   */
  fun truncateTo(newSize: Int): Boolean {
    if (newSize >= _itemCount) return false
    rebuild(newSize, 0)
    return true
  }

  /**
   * Expand or shrinks the tree to [newSize] items, using [paddingValue] as the default value of new items if expanding. The existing
   * [treeArray] is reused if [newSize] does not cross of "power of 2" boundary, otherwise a new [treeArray] is allocated.
   *
   * Note: This is an O(n) operation
   */
  private fun rebuild(newSize: Int, paddingValue: Int) {
    require(newSize != _itemCount) { "Rebuild should not be called with newSize == size ($newSize)" }
    val newN = requiredTreeCapacity(newSize)
    val paddingSlot = HeightSlot.estimated(paddingValue)
    if (newN != n) {
      // If newSize requires a new "power of 2" boundary, we need a new array, etc.
      val newTree = HeightSlotArray(2 * newN)
      val copyLimit = minOf(_itemCount, newSize)
      var measuredCount = 0
      for (i in 0 until copyLimit) {
        val slot = treeArray[n + i]
        newTree[newN + i] = slot
        if (slot.isMeasured) measuredCount++
      }
      for (i in _itemCount until newSize) {
        val slot = paddingSlot
        newTree[newN + i] = slot
        if (slot.isMeasured) measuredCount++
      }
      treeArray = newTree
      n = newN
      _measuredCount = measuredCount
    } else if (newSize > _itemCount) {
      // Add padding value if tree grows
      for (i in _itemCount until newSize) {
        val slot = paddingSlot
        treeArray[n + i] = slot
        if (slot.isMeasured) _measuredCount++
      }
    } else if (newSize < _itemCount) {
      // Clear extra values if tree shrinks
      for (i in newSize until _itemCount) {
        val previousSlot = treeArray[n + i]
        if (previousSlot.isMeasured) _measuredCount--
        treeArray[n + i] = HeightSlot.measured(0)
      }
    }
    _itemCount = newSize
    buildFromLeaves()
  }

  /** Recompute all non-leaf nodes from leaf nodes value */
  private fun buildFromLeaves() {
    for (i in n - 1 downTo 1) {
      treeArray[i] = treeArray[2 * i] + treeArray[2 * i + 1]
    }
  }

  companion object {
    private fun requiredTreeCapacity(itemCount: Int): Int {
      return 1 shl (32 - Integer.numberOfLeadingZeros(maxOf(0, itemCount - 1)))
    }
  }
}
