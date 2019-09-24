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

import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ContextPopupHandler
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Window
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import javax.swing.JComponent
import javax.swing.SwingUtilities
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
  private val contextPopup: ContextPopupHandler,
  private val badges: List<BadgeItem>
) : Tree(componentTreeModel) {

  private var initialized = false
  private var hasApplicationFocus = { withinApplicationFocus() }

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
    addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        this@TreeImpl.mouseClicked(event)
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
      setCellRenderer(TreeCellRendererImpl(this, badges, model!!))
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
    return expandableItemsHandler.expandedItems.contains(row) && hasApplicationFocus()
  }

  /**
   * Hook for overriding withinApplicationFocus in tests.
   */
  var overrideHasApplicationFocus: () -> Boolean
    @TestOnly
    get() = hasApplicationFocus
    @TestOnly
    set(value) { hasApplicationFocus = value }

  /**
   * Return true if the current application currently has focus.
   *
   * We don't expand the tree items on hover if a different application has focus.
   */
  // Hack: copied with the exception of myTipComponent from the private method: AbstractExpandableItemsHandler.noIntersections
  private fun withinApplicationFocus(): Boolean {
    val owner = SwingUtilities.getWindowAncestor(this)
    var focus: Window? = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
    if (focus === owner.owner) {
      focus = null // do not check intersection with parent
    }
    var focused = SystemInfo.isWindows || isFocused(owner)
    for (other in owner.ownedWindows) {
      if (!focused) {
        focused = other.isFocused
      }
      if (focus === other) {
        focus = null // already checked
      }
    }
    return focused && (focus === owner || focus == null || !owner.bounds.intersects(focus.bounds))
  }

  // Hack: copied from the private method: AbstractExpandableItemsHandler.isFocused
  private fun isFocused(window: Window?): Boolean {
    return window != null && (window.isFocused || isFocused(window.owner))
  }

  private fun mouseClicked(event: MouseEvent) {
    if (!event.isPopupTrigger &&
        event.button == BUTTON1 &&
        event.clickCount == 1 &&
        (event.modifiers.and(InputEvent.SHIFT_MASK.or(InputEvent.CTRL_MASK))) == 0) {
      val (component, item) = lookupRenderComponentAt(event.x, event.y) ?: return
      val badge = component.getClientProperty(BADGE_ITEM) as? BadgeItem
      badge?.performAction(item)
    }
  }

  private fun invokePopup(x: Int, y: Int) {
    val (component, item) = lookupRenderComponentAt(x, y) ?: return
    val badge = component.getClientProperty(BADGE_ITEM) as? BadgeItem
    if (badge != null) {
      badge.showPopup(item, this, x, y)
    }
    else {
      contextPopup.invoke(this, x, y)
    }
  }

  // region Support for Badges

  override fun getToolTipText(event: MouseEvent): String? {
    val (component, _) = lookupRenderComponentAt(event.x, event.y) ?: return toolTipText
    return component.toolTipText ?: toolTipText
  }

  /**
   * Find the render component at a position in the tree.
   *
   * The components that make up the content of the tree only exists when painting.
   * There are other cases where it would be nice to have access to these components.
   * Examples: tooltips, context menus, mouse clicks etc.
   */
  private fun lookupRenderComponentAt(x: Int, y: Int): Pair<JComponent, Any>? {
    val row = getRowForLocation(x, y)
    val renderer = getCellRenderer() ?: return null
    val path = getPathForRow(row) ?: return null
    val item = path.lastPathComponent
    val renderComponent = renderer.getTreeCellRendererComponent(
      this, item, isRowSelected(row), isExpanded(row), model!!.isLeaf(item), row, true) as? JComponent ?: return null
    val bounds = getPathBounds(path) ?: return null
    renderComponent.bounds = bounds
    renderComponent.doLayout()
    val component = SwingUtilities.getDeepestComponentAt(renderComponent, x - bounds.x, y - bounds.y) as JComponent? ?: return null
    return Pair(component, item)
  }

  // endregion
}
