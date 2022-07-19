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

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.AsyncUpdater
import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.intellij.openapi.application.ApplicationManager
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class CpuTreeModel<T: Aggregate<T>>(val clockType: ClockType,
                                    private val range: Range,
                                    base: T,
                                    runModelUpdate: (Runnable) -> Unit): TreeModel {
  private var treeRange = Range(range)
  private var order: Comparator<CpuTreeNode<T>> = compareBy({ it.base.isUnmatched }, { -it.total })
  val aspect = AspectModel<Aspect>()
  private val observer = AspectObserver()
  private val listeners = mutableListOf<TreeModelListener>()
  private var root = CpuTreeNode.of(base, clockType, treeRange, order)
    set(newRoot) {
      if (newRoot !== field) {
        field = newRoot
        reload()
      }
    }
  val isRootNodeIdValid = root.base.id.isNotEmpty()
  val isEmpty get() = root.total == 0.0

  private val rangeChanged =
    AsyncUpdater.by(ApplicationManager.getApplication()::invokeAndWait,
                    runModelUpdate,
                    { root },
                    { it.withRange(clockType, range, treeRange, order) },
                    { newRoot ->
                      root = newRoot
                      treeRange.set(range)
                      aspect.changed(Aspect.TREE_MODEL)
                    })

  init {
    onReattached()
  }

  fun sort(newOrder: Comparator<CpuTreeNode<T>>) {
    if (newOrder != order) { // catch trivial cases
      order = newOrder
      root = root.withOrder(newOrder, clockType, treeRange)
    }
  }

  fun onDestroyed() {
    range.removeDependencies(observer)
  }

  fun onReattached() {
    range.addDependency(observer).onChange(Range.Aspect.RANGE, rangeChanged)
    rangeChanged()
  }

  private fun reload() {
    val event = TreeModelEvent(this, arrayOf(root), null, null)
    listeners.asReversed().forEach { it.treeStructureChanged(event) }
  }

  override fun getRoot() = root
  override fun getChild(parent: Any, index: Int) = (parent as CpuTreeNode<T>).getChildAt(index)
  override fun getChildCount(parent: Any) = (parent as CpuTreeNode<T>).childCount
  override fun isLeaf(node: Any) = (node as CpuTreeNode<T>).isLeaf
  override fun valueForPathChanged(path: TreePath, newValue: Any) {}
  override fun getIndexOfChild(parent: Any, child: Any) = (parent as CpuTreeNode<T>).getIndex(child as CpuTreeNode<T>)
  override fun addTreeModelListener(l: TreeModelListener) { listeners.add(l) }
  override fun removeTreeModelListener(l: TreeModelListener) { listeners.remove(l) }

  enum class Aspect {
    TREE_MODEL // Tree Model changed
  }
}