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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import java.util.Collections
import java.util.Enumeration
import java.util.IdentityHashMap
import java.util.Objects
import javax.swing.tree.TreeNode

/**
 * A view of the aggregation tree that restricts it to a range, sums up number, and presents children in order
 */
class CpuTreeNode<T: Aggregate<T>>(val base: T,
                                   val total: Double,
                                   val childrenTotal: Double,
                                   private val childrenDelegate: Lazy<List<CpuTreeNode<T>>>): TreeNode {
  val self get() = total - childrenTotal

  /**
   * Return a tree for `newRange`, assuming current range is `oldRange`.
   * New tree preserves original one's shape, i.e. uninitialized nodes remains uninitialized
   */
  internal fun withRange(clockType: ClockType, newRange: Range, oldRange: Range, order: Comparator<CpuTreeNode<T>>?): CpuTreeNode<T> =
    withRange(clockType, newRange, newRange.subtract(oldRange) + oldRange - newRange, order)

  private fun withRange(clockType: ClockType, newRange: Range, diffs: List<Range>, order: Comparator<CpuTreeNode<T>>?): CpuTreeNode<T> =
    base.totalOver(clockType, newRange).let { (total, totalChildren) ->
      when {
        childrenDelegate.isInitialized() -> {
          val oldNode = childrenDelegate.value.associateByTo(IdentityHashMap(), CpuTreeNode<T>::base)
          val children = base.children.asSequence()
            .filter { it.overlapsWith(newRange) }
            .map { child ->
              oldNode[child]?.let { childNode -> when {
                diffs.any(child::overlapsWith) -> childNode.withRange(clockType, newRange, diffs, order)
                else -> childNode
              } } ?: of(child, clockType, newRange, order)
            }
            .maybe(Sequence<CpuTreeNode<T>>::sortedWith, order)
            .toList()
          CpuTreeNode(base, total, totalChildren, children.asLazy())
        }
        else ->
          CpuTreeNode(base, total, totalChildren, fresh(clockType, newRange, order))
      }
    }

  /**
   * Assume this tree is restricted to `range`, return tree like this but sorted by given order
   */
  internal fun withOrder(order: Comparator<CpuTreeNode<T>>, clockType: ClockType, range: Range): CpuTreeNode<T> =
    CpuTreeNode(base, total, childrenTotal,
                when {
                  childrenDelegate.isInitialized() -> children
                    .map { it.withOrder(order, clockType, range) }
                    .sortedWith(order)
                    .asLazy()
                  else -> fresh(clockType, range, order)
                })

  private fun fresh(clockType: ClockType, range: Range, order: Comparator<CpuTreeNode<T>>?) =
    base.children.let { // copy `base.children` over, so the thunk won't retain this tree just for `base`
      baseChildren -> lazy(LazyThreadSafetyMode.NONE) { of(baseChildren, clockType, range, order)}
    }

  val children: List<CpuTreeNode<T>> get() = childrenDelegate.value
  override fun getChildAt(childIndex: Int) = childrenDelegate.value[childIndex]
  override fun getChildCount() = childrenDelegate.value.size
  override fun getParent() = null // we don't need this
  override fun getIndex(node: TreeNode) = childrenDelegate.value.indexOf(node)
  override fun getAllowsChildren() = true
  override fun isLeaf() = base.children.isEmpty()
  override fun children(): Enumeration<CpuTreeNode<T>> = Collections.enumeration(childrenDelegate.value)

  // Coarse equality based on the underlying node, for use in TreePath
  override fun equals(other: Any?) = other is CpuTreeNode<*> && base === other.base
  override fun hashCode() = Objects.hash(javaClass.name) * 31 + System.identityHashCode(base)

  companion object {
    /**
     * Create a new view restricted to given range
     */
    fun<T: Aggregate<T>> of(base: T, clockType: ClockType, range: Range, order: Comparator<CpuTreeNode<T>>?): CpuTreeNode<T> =
      base.totalOver(clockType, range).let { (total, totalChildren) ->
        CpuTreeNode(base, total, totalChildren, lazy(LazyThreadSafetyMode.NONE) { of(base.children, clockType, range, order) })
      }

    internal fun<T: Aggregate<T>> of(bases: List<T>, clockType: ClockType, range: Range, order: Comparator<CpuTreeNode<T>>?): List<CpuTreeNode<T>> =
      bases
        .mapNotNull { base -> base.takeIf { base.overlapsWith(range) }?.let { of(base, clockType, range, order) } }
        .maybe(List<CpuTreeNode<T>>::sortedWith, order)

    private fun<X> X.asLazy() = object: Lazy<X> {
      override val value get() = this@asLazy
      override fun isInitialized() = true
    }
  }
}

private inline fun<O, X: Any> O.maybe(f: O.(X) -> O, x: X?): O = if (x == null) this else f(this, x)