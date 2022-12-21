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
package com.android.tools.componenttree.api

import com.android.tools.componenttree.impl.ComponentTreeModelImpl
import com.android.tools.componenttree.impl.ComponentTreeSelectionModelImpl
import com.android.tools.componenttree.impl.TreeImpl
import com.android.tools.componenttree.treetable.TreeTableImpl
import com.android.tools.componenttree.treetable.TreeTableModelImpl
import com.android.tools.componenttree.treetable.UpperRightCorner
import com.android.tools.idea.flags.StudioFlags
import com.intellij.designer.componentTree.ComponentTreeBuilder
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

/**
 * A Handler which will display a context popup menu.
 */
typealias ContextPopupHandler = (item: Any, component: JComponent, x: Int, y: Int) -> Unit
typealias DoubleClickHandler = (Any) -> Unit

/**
 * How to merge [Transferable]s when multiple items can be used for drag and drop.
 */
typealias DnDMerger = (Transferable, Transferable) -> Transferable

/**
 * A component tree builder creates a tree that can hold multiple types of nodes.
 *
 * Each [NodeType] must be specified. If a node type represent an Android View
 * consider using [ViewNodeType] which defines a standard node renderer.
 */
class ComponentTreeBuilder {
  private val nodeTypeMap = mutableMapOf<Class<*>, NodeType<*>>()
  private var headerRenderer: TableCellRenderer? = null
  private var contextPopup: ContextPopupHandler = { _, _, _, _ -> }
  private var doubleClick: DoubleClickHandler = { }
  private val badges = mutableListOf<BadgeItem>()
  private val columns = mutableListOf<ColumnInfo>()
  private var selectionMode = SINGLE_TREE_SELECTION
  private var invokeLater: (Runnable) -> Unit = SwingUtilities::invokeLater
  private var installTreeSearch = true
  private var isRootVisible = true
  private var showRootHandles = false
  private var horizontalScrollbar = false
  private var autoScroll = false
  private var dataProvider: DataProvider? = null
  private var dndSupport = false
  private var dndMerger: DnDMerger? = null
  private var dndDeleteOriginOfInternalMove = true
  private var componentName =  "componentTree"
  private var painter: (() -> Control.Painter?)? = null
  private var installKeyboardActions: (JComponent) -> Unit = {}
  private var toggleClickCount = 2
  private var expandAllOnRootChange = false

  /**
   * Register a [NodeType].
   */
  fun <T> withNodeType(type: NodeType<T>) = apply { nodeTypeMap[type.clazz] = type }

  /**
   * Header renderer for the tree column.
   */
  fun withHeaderRenderer(renderer: TableCellRenderer) = apply { headerRenderer = renderer }

  /**
   * Allow multiple nodes to be selected in the tree (default is a single selection).
   */
  fun withMultipleSelection() = apply { selectionMode = DISCONTIGUOUS_TREE_SELECTION }

  /**
   * Add a context popup menu on the tree node item.
   */
  fun withContextMenu(treeContextMenu: ContextPopupHandler) = apply { contextPopup = treeContextMenu }

  /**
   * Add a double click handler on the tree node item.
   */
  fun withDoubleClick(doubleClickHandler: DoubleClickHandler) = apply {
    doubleClick = doubleClickHandler
  }

  /**
   * Set the toggle click count (default is 2).
   */
  fun withToggleClickCount(clickCount: Int) = apply {
    toggleClickCount = clickCount
  }

  /**
   * Specify specific invokeLater implementation to use.
   */
  fun withInvokeLaterOption(invokeLaterImpl: (Runnable) -> Unit) = apply { invokeLater = invokeLaterImpl }

  /**
   * Do not install tree search. Can be omitted for tests.
   */
  fun withoutTreeSearch() = apply { installTreeSearch = false }

  /**
   * Add a column to the right of the tree node item.
   *
   * Note: This is only supported by the TreeTable implementation.
   */
  fun withColumn(columnInfo: ColumnInfo) = apply { columns.add(columnInfo) }

  /**
   * Add a badge column to the right of the tree node item.
   */
  fun withBadgeSupport(badge: BadgeItem) =
    if (StudioFlags.USE_COMPONENT_TREE_TABLE.get()) apply { columns.add(badge) } else apply { badges.add(badge) }

  /**
   * Add Copy, Cut, Paste & Delete support (works of the current selection)
   */
  fun withDataProvider(dataProvider: DataProvider) = apply { this.dataProvider = dataProvider }

  /**
   * Add Drag and Drop support.
   *
   * Optionally specify a merge operator for support of dragging multiple items.
   * Without a merge operator, only the 1st item will be dragged.
   * When dragging items from the component tree itself [deleteOriginOfInternalMove] controls whether the origin items should be deleted.
   */
  fun withDnD(merger: DnDMerger? = null, deleteOriginOfInternalMove: Boolean = true) = apply {
    dndSupport = true
    dndMerger = merger
    dndDeleteOriginOfInternalMove = deleteOriginOfInternalMove
  }

  /**
   * Don't show the root node.
   */
  fun withHiddenRoot() = apply { isRootVisible = false }

  /**
   * Show the expansion icon for the root.
   */
  fun withExpandableRoot() = apply { showRootHandles = true }

  /**
   * Show an horizontal scrollbar if necessary.
   */
  fun withHorizontalScrollBar() = apply { horizontalScrollbar = true }

  /**
   * Set the component name for UI tests.
   */
  fun withComponentName(name: String) = apply { componentName = name }

  /**
   * Auto scroll to make a newly selected item scroll into view.
   */
  fun withAutoScroll() = apply { autoScroll = true }

  /**
   * Sets a custom tree painter (e.g. [Control.Painter.COMPACT]) for this tree to use, which may change during runtime.
   */
  fun withPainter(painter: () -> Control.Painter?) = apply { this.painter = painter }

  /**
   * Allows custom keyboard actions to be installed.
   */
  fun withKeyboardActions(installer: (JComponent) -> Unit) = apply { this.installKeyboardActions = installer }

  fun withExpandAllOnRootChange() = apply {
    expandAllOnRootChange = true
  }

  /**
   * Build the tree component and return it with the tree model.
   */
  fun build(): ComponentTreeBuildResult =
    if (StudioFlags.USE_COMPONENT_TREE_TABLE.get()) buildTreeTable() else buildTree()

  private fun buildTree(): ComponentTreeBuildResult {
    if (columns.isNotEmpty()) {
      Logger.getInstance(ComponentTreeBuilder::class.java).warn("Columns are not supported with the Tree implementations")
    }
    val model = ComponentTreeModelImpl(nodeTypeMap, invokeLater)
    val selectionModel = ComponentTreeSelectionModelImpl(model, selectionMode)
    val tree = TreeImpl(model, contextPopup, doubleClick, badges, componentName, painter, installKeyboardActions, selectionModel,
                        autoScroll, installTreeSearch)
    tree.toggleClickCount = toggleClickCount
    tree.isRootVisible = isRootVisible
    tree.showsRootHandles = !isRootVisible || showRootHandles
    dataProvider?.let { DataManager.registerDataProvider(tree, it) }
    val horizontalPolicy = if (horizontalScrollbar) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER
    val scrollPane = ScrollPaneFactory.createScrollPane(tree, VERTICAL_SCROLLBAR_AS_NEEDED, horizontalPolicy)
    scrollPane.border = JBUI.Borders.empty()
    return ComponentTreeBuildResult(scrollPane, tree, tree, model, selectionModel, NoOpTableVisibility())
  }

  private fun buildTreeTable(): ComponentTreeBuildResult {
    val model = TreeTableModelImpl(columns, nodeTypeMap, invokeLater)
    val table = TreeTableImpl(model, contextPopup, doubleClick, painter, installKeyboardActions, selectionMode, autoScroll,
                              installTreeSearch, expandAllOnRootChange, headerRenderer)
    table.name = componentName // For UI tests
    if (dndSupport) {
      table.enableDnD(dndMerger, dndDeleteOriginOfInternalMove)
    }
    dataProvider?.let { DataManager.registerDataProvider(table, it) }
    val tree = table.tree
    tree.toggleClickCount = toggleClickCount
    tree.isRootVisible = isRootVisible
    tree.showsRootHandles = !isRootVisible || showRootHandles

    val horizontalPolicy = if (horizontalScrollbar) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER
    val scrollPane = ScrollPaneFactory.createScrollPane(table, VERTICAL_SCROLLBAR_AS_NEEDED, horizontalPolicy)
    scrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, UpperRightCorner())
    scrollPane.border = JBUI.Borders.empty()
    return ComponentTreeBuildResult(scrollPane, table, tree, model, table.treeTableSelectionModel, table)
  }
}

/**
 * The resulting component tree.
 */
class ComponentTreeBuildResult(
  /**
   * The top component which is JScrollPane.
   */
  val component: JComponent,

  /**
   * The component that has focus in the component tree.
   *
   * Note: This will be:
   * - the [tree] component if [StudioFlags.USE_COMPONENT_TREE_TABLE] is false
   * - the TreeTable component if [StudioFlags.USE_COMPONENT_TREE_TABLE] is true
   */
  val focusComponent: JComponent,

  /**
   * The Tree component of the component tree.
   *
   * Note: the Tree instance may be just be a renderer instance, and may not have a parent component.
   */
  val tree: Tree,

  /**
   * The component tree model.
   */
  val model: ComponentTreeModel,

  /**
   * The component tree selection model.
   */
  val selectionModel: ComponentTreeSelectionModel,

  /**
   * An object that can be used to modify the tree.
   *
   * The visibility of the header and the columns are supported.
   */
  val interactions: TableVisibility
)
