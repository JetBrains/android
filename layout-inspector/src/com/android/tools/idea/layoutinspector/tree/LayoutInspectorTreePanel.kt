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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.intellij.ui.treeStructure.Tree
import java.util.Enumeration
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreeSelectionModel

class LayoutInspectorTreePanel() : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private val tree = Tree()

  init {
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    tree.addTreeSelectionListener { e ->
      (e.newLeadSelectionPath.lastPathComponent as MyTreeNode?)?.let {
        layoutInspector?.layoutInspectorModel?.selection = it.root
      }
    }
  }

  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.modelChangeListeners?.remove(this::modelChanged)
    layoutInspector = toolContext
    layoutInspector?.modelChangeListeners?.add(this::modelChanged)
    if (toolContext != null) {
      modelChanged(null, toolContext.layoutInspectorModel)
    }
  }

  override fun getComponent(): JComponent {
    return tree
  }

  override fun dispose() {
  }

  fun modelChanged(old: InspectorModel?, new: InspectorModel) {
    tree.model = DefaultTreeModel(MyTreeNode(new.root, null))
  }

  private class MyTreeNode(val root: InspectorView, val _parent: MyTreeNode?) : TreeNode {
    val _children = root.children.map { MyTreeNode(it, this) }

    override fun children(): Enumeration<*> {
      return _children.toEnumeration()
    }

    override fun isLeaf() = root.children.isEmpty()

    override fun getChildCount() = root.children.size

    override fun getParent() = _parent

    override fun getChildAt(childIndex: Int) = _children[childIndex]

    override fun getIndex(node: TreeNode?) = _children.indexOf(node)

    override fun getAllowsChildren() = true

    override fun toString() = root.type
  }
}

private fun <T> List<T>.toEnumeration(): Enumeration<T> {
  return object : Enumeration<T> {
    var count = 0

    override fun hasMoreElements(): Boolean {
      return count < size
    }

    override fun nextElement(): T {
      if (count < size) {
        return get(count++)
      }
      throw IndexOutOfBoundsException("$count >= $size")
    }
  }
}
