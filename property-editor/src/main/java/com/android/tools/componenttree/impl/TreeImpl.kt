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

import com.android.tools.componenttree.api.ContextPopupHandler
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.event.ComponentEvent
import javax.swing.ToolTipManager
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath

/**
 * A Tree implementation.
 *
 * TODO: Move all the functionality from NlComponentTree into this tree
 */
class TreeImpl(
  componentTreeModel: ComponentTreeModelImpl,
  private val contextPopup: ContextPopupHandler
) : Tree(componentTreeModel) {

  private var initialized = false

  init {
    name = "componentTree"  // For UI tests
    isRootVisible = true
    showsRootHandles = false
    toggleClickCount = 2
    ToolTipManager.sharedInstance().registerComponent(this)
    TreeUtil.installActions(this)
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        invokePopup(x, y)
      }
    })
    initialized = true

    // JTree.updateUI() is called before setModel in JTree, which causes some model listeners not to be installed.
    // Fix this by calling updateUI again here.
    updateUI()
  }

  override fun updateUI() {
    super.updateUI()
    // This is called during the constructor of the super class.
    // None of the members of this class is available at that point.
    if (initialized) {
      // Recreate all renderers since fonts and colors may be cached in the renderers.
      model!!.clearRendererCache()
      setCellRenderer(TreeCellRendererImpl(this, model!!))
    }
  }

  override fun getModel() = super.getModel() as? ComponentTreeModelImpl

  override fun getShowsRootHandles(): Boolean {
    // This is needed because the intelliJ Tree class ignore setShowsRootHandles()
    // Without this override the tree is not aligned to the left side of the component.
    return false
  }

  override fun setExpandedState(path: TreePath?, state: Boolean) {
    // We never want to collapse the root
    val isRoot = getRowForPath(path) == 0
    if (!isRoot) {
      super.setExpandedState(path, state)
    }
  }

  override fun processComponentEvent(event: ComponentEvent) {
    // Hack: We are adjusting the preferred size of the tree nodes to always include the tree width.
    // Since the TreeUI is caching the preferred node sizes we need to invalidate the cache after
    // the tree is resized. This happens as a side effect of setting the leftChildIndent of the UI.
    super.processComponentEvent(event)
    if (ComponentEvent.COMPONENT_RESIZED == event.id) {
      val basicUI = ui as? BasicTreeUI ?: return
      basicUI.leftChildIndent = basicUI.leftChildIndent
    }
  }

  /**
   * Return true if the specified row is currently being expanded by the expandableItemsHandler of the tree.
   *
   * Note: no nodes are left in an expanded state when the tree doesn't have focus.
   */
  fun isRowCurrentlyExpanded(row: Int): Boolean {
    val expandableItemsHandler = expandableItemsHandler as? AbstractExpandableItemsHandler<*, *> ?: return false
    return expandableItemsHandler.expandedItems.contains(row) && hasFocus()
  }

  private fun invokePopup(x: Int, y: Int) {
    // The selected items is supplied via DataContext.getData(TREE_SELECTION)
    contextPopup.invoke(this, x, y)
  }
}
