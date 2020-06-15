/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.impl

import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

/**
 * A [DefaultTreeSelectionModel] where a selection is treated as a list of nodes rather than tree paths.
 */
class ComponentTreeSelectionModelImpl(private val model: ComponentTreeModelImpl): ComponentTreeSelectionModel, DefaultTreeSelectionModel() {

  override var selection: List<Any>
    get() = selectionPaths.map { it.lastPathComponent }
    set(value) {
      val oldValue = selectionPaths.map { it.lastPathComponent }
      if (value != oldValue) {
        selectionPaths = value.map { createTreePath(it) }.toTypedArray()
      }
    }

  override fun addSelectionListener(listener: (List<Any>) -> Unit) {
    addTreeSelectionListener(ComponentTreeSelectionListener(listener))
  }

  override fun removeSelectionListener(listener: (List<Any>) -> Unit) {
    val list = getListeners(ComponentTreeSelectionListener::class.java)
    val treeListener = list.firstOrNull { it.callback == listener } ?: return
    removeTreeSelectionListener(treeListener)
  }

  private fun createTreePath(node: Any): TreePath {
    val path = generateSequence(node) { model.parent(it) }
    return TreePath(path.toList().asReversed().toTypedArray())
  }

  private class ComponentTreeSelectionListener(val callback: (List<Any>) -> Unit): TreeSelectionListener {
    override fun valueChanged(event: TreeSelectionEvent) {
      callback(event.paths.filter { event.isAddedPath(it) }.map { it.lastPathComponent })
    }
  }
}
