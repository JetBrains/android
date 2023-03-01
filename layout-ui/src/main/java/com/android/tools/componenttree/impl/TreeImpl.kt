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

import com.android.tools.adtui.TreeWalker
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Graphics
import java.awt.Window
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.event.TreeModelEvent
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
  private val doubleClick: DoubleClickHandler,
  private val badges: List<BadgeItem>,
  componentName: String,
  private val painter: (() -> Control.Painter?)?,
  private val installKeyboardActions: (JComponent) -> Unit,
  treeSelectionModel: ComponentTreeSelectionModelImpl,
  autoScroll: Boolean,
  installTreeSearch: Boolean
) : Tree(componentTreeModel) {

  private var initialized = false
  private var hasApplicationFocus = { withinApplicationFocus() }
  @VisibleForTesting
  var expandableTreeItemsHandler: AbstractExpandableItemsHandler<Int, JTree> = TreeImplExpandableItemsHandler(this)

  init {
    name = componentName // For UI tests
    isRootVisible = true
    showsRootHandles = false
    toggleClickCount = 2
    super.setExpandableItemsEnabled(false) // Disable the expandableItemsHandler from Tree.java
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
    componentTreeModel.addTreeModelListener(object : ComponentTreeModelAdapter() {
      override fun treeChanged(event: TreeModelEvent) {
        updateTree(event)
      }
    })
    selectionModel = treeSelectionModel
    if (autoScroll) {
      treeSelectionModel.addAutoScrollListener {
        selectionRows?.singleOrNull()?.let { scrollRowToVisible(it) }
      }
    }
    if (installTreeSearch) {
      TreeSpeedSearch.installOn(this, false) { componentTreeModel.toSearchString(it.lastPathComponent) }
    }
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

      installKeyboardActions(this)
    }
  }

  override fun paintComponent(g: Graphics?) {
    putClientProperty(Control.Painter.KEY, painter?.invoke())
    super.paintComponent(g)
  }

  override fun getModel() = super.getModel() as? ComponentTreeModelImpl

  override fun setExpandedState(path: TreePath?, state: Boolean) {
    if (path != null && !alwaysExpanded(path)) {
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

  override fun getExpandableItemsHandler(): ExpandableItemsHandler<Int> =
    expandableTreeItemsHandler

  /**
   * Return true if the specified row is currently being expanded by the expandableItemsHandler of the tree.
   *
   * Note: no nodes are left in an expanded state when the tree doesn't have focus.
   */
  fun isRowCurrentlyExpanded(row: Int): Boolean =
    expandableTreeItemsHandler.expandedItems.contains(row) && hasApplicationFocus()

  /**
   * Hook for overriding withinApplicationFocus in tests.
   */
  var overrideHasApplicationFocus: () -> Boolean
    @TestOnly
    get() = hasApplicationFocus
    @TestOnly
    set(value) { hasApplicationFocus = value }

  /**
   * Compute the max render width which is the visible width of the view port minus indents and badges.
   */
  fun computeMaxRenderWidth(nodeDepth: Int): Int =
    computeBadgePosition() - computeLeftOffset(nodeDepth)

  /**
   * Compute the x position of the badges relative to this tree.
   */
  fun computeBadgePosition(): Int {
    val viewPort = parent as? JViewport
    return (viewPort?.let { it.width + it.viewPosition.x } ?: width) - computeBadgesWidth() - insets.right
  }

  /**
   * Return the width of all the badges.
   */
  fun computeBadgesWidth(): Int =
    badges.size * EmptyIcon.ICON_16.iconWidth

  /**
   * Repaint the specified [row].
   */
  fun repaintRow(row: Int) =
    getRowBounds(row)?.let { repaint(it) }

  /**
   * Expand a repaint request to the entire row.
   *
   * This ensures repaint of badges during various events like selection changes & focus changes.
   */
  override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) =
    super.repaint(tm, 0, y, this.width, height)

  /**
   * Compute the left offset of a row with the specified [nodeDepth] in the tree.
   *
   * Note: This code is based on the internals of the UI for the tree e.g. the method [BasicTreeUI.getRowX].
   */
  private fun computeLeftOffset(nodeDepth: Int): Int {
    val ourUi = ui as BasicTreeUI
    return insets.left + (ourUi.leftChildIndent + ourUi.rightChildIndent) * (nodeDepth - 1)
  }

  /**
   * Return true if the current application currently has focus.
   *
   * We don't expand the tree items on hover if a different application has focus.
   */
  // Hack: copied with the exception of myTipComponent from the private method: AbstractExpandableItemsHandler.noIntersections
  private fun withinApplicationFocus(): Boolean {
    val owner = SwingUtilities.getWindowAncestor(this) ?: return false // NPE safeguard: b/174129669
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
        (event.modifiers.and(InputEvent.SHIFT_MASK.or(InputEvent.CTRL_MASK))) == 0) {
      if (event.clickCount == 1) {
        val (component, item, row) = lookupRenderComponentAt(event.x, event.y) ?: return
        val badge = component.getClientProperty(BADGE_ITEM) as? BadgeItem
        if (badge != null) {
          val bounds = getRowBounds(row)
          bounds.x = width - EmptyIcon.ICON_16.iconWidth
          bounds.width = EmptyIcon.ICON_16.iconWidth
          badge.performAction(item, this, bounds)
        }
      }
      else if (event.clickCount == 2) {
        doubleClick()
      }
    }
  }

  private fun invokePopup(x: Int, y: Int) {
    val (component, item, _) = lookupRenderComponentAt(x, y) ?: return
    val badge = component.getClientProperty(BADGE_ITEM) as? BadgeItem
    if (badge != null) {
      badge.showPopup(item, this, x, y)
    }
    else {
      contextPopup.invoke(this, x, y)
    }
  }

  /**
   * Update the tree after a change in the tree.
   *
   * Attempt to keep the same tree nodes expanded and the same selection if this is a partial update.
   */
  private fun updateTree(event: TreeModelEvent) {
    val selectionModel = getSelectionModel() as ComponentTreeSelectionModelImpl
    selectionModel.keepSelectionDuring {
      val expanded = TreeUtil.collectExpandedPaths(this)
      model?.fireTreeStructureChange(event)
      TreeUtil.restoreExpandedPaths(this, expanded)
    }
    if (!isRootVisible || !showsRootHandles) {
      val currentModel = model
      currentModel?.root?.let { root ->
        val paths = mutableListOf(TreePath(root))
        currentModel.children(root).mapTo(paths, { TreePath(arrayOf(root, it)) })
        paths.filter { alwaysExpanded(it) }.forEach { super.setExpandedState(it, true) }
      }
    }
  }

  private fun alwaysExpanded(path: TreePath): Boolean {
    // An invisible root or a root without root handles should always be expanded
    val parentPath = path.parentPath ?: return !isRootVisible || !showsRootHandles

    // The children of an invisible root that are shown without root handles should always be expanded
    return parentPath.parentPath == null && !isRootVisible && !showsRootHandles
  }

  // region Support for Badges

  override fun getToolTipText(event: MouseEvent): String? {
    val (component, _, _) = lookupRenderComponentAt(event.x, event.y) ?: return toolTipText
    return component.toolTipText ?: toolTipText
  }

  /**
   * Find the render component at a position in the tree.
   *
   * The components that make up the content of the tree only exists when painting.
   * There are other cases where it would be nice to have access to these components.
   * Examples: tooltips, context menus, mouse clicks etc.
   */
  private fun lookupRenderComponentAt(x: Int, y: Int): Triple<JComponent, Any, Int>? {
    val renderer = getCellRenderer() ?: return null
    val path = getClosestPathForLocation(x, y) ?: return null
    val row = getRowForPath(path)
    val item = path.lastPathComponent
    val depth = model!!.computeDepth(item)
    val renderComponent = renderer.getTreeCellRendererComponent(
      this, item, isRowSelected(row), isExpanded(row), model!!.isLeaf(item), row, true) as? JComponent ?: return null
    val bounds = getPathBounds(path) ?: return null
    bounds.width = computeMaxRenderWidth(depth) + badges.size * EmptyIcon.ICON_16.iconWidth
    renderComponent.bounds = bounds
    // TODO(b/171255033): It is possible that with the presence of a peer this can be replaced by: renderComponent.revalidate()
    TreeWalker(renderComponent).descendantStream().forEach { component: Component -> component.doLayout() }
    val component = SwingUtilities.getDeepestComponentAt(renderComponent, x - bounds.x, y - bounds.y) as JComponent? ?: return null
    return Triple(component, item, row)
  }
  // endregion
}
