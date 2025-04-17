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

import com.android.tools.componenttree.treetable.ColumnTreeScrollPanel
import com.android.tools.componenttree.treetable.ColumnTreeUI
import com.android.tools.componenttree.treetable.TreeTableImpl
import com.android.tools.componenttree.treetable.TreeTableModelImpl
import com.android.tools.componenttree.treetable.UpperRightCorner
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

/** A Handler which will display a context popup menu. */
typealias ContextPopupHandler = (item: Any, component: JComponent, x: Int, y: Int) -> Unit

typealias DoubleClickHandler = (Any) -> Unit

/** How to merge [Transferable]s when multiple items can be used for drag and drop. */
typealias DnDMerger = (Transferable, Transferable) -> Transferable

/**
 * A component tree builder creates a tree that can hold multiple types of nodes.
 *
 * Each [NodeType] must be specified. If a node type represent an Android View consider using
 * [ViewNodeType] which defines a standard node renderer.
 */
class ComponentTreeBuilder {
  private val nodeTypeMap = mutableMapOf<Class<*>, NodeType<*>>()
  private var headerRenderer: TableCellRenderer? = null
  private var contextPopup: ContextPopupHandler = { _, _, _, _ -> }
  private var doubleClick: DoubleClickHandler = {}
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
  private var componentName = "componentTree"
  private var installKeyboardActions: (JComponent) -> Unit = {}
  private var toggleClickCount = 2
  private var expandAllOnRootChange = false
  private var showSupportLines: () -> Boolean = { true }
  private var isCallStackNode: (TreePath) -> Boolean = { path -> false }

  /** Register a [NodeType]. */
  fun <T> withNodeType(type: NodeType<T>) = apply { nodeTypeMap[type.clazz] = type }

  /** Header renderer for the tree column. */
  fun withHeaderRenderer(renderer: TableCellRenderer) = apply { headerRenderer = renderer }

  /** Allow multiple nodes to be selected in the tree (default is a single selection). */
  fun withMultipleSelection() = apply { selectionMode = DISCONTIGUOUS_TREE_SELECTION }

  /** Add a context popup menu on the tree node item. */
  fun withContextMenu(treeContextMenu: ContextPopupHandler) = apply {
    contextPopup = treeContextMenu
  }

  /** Add a double click handler on the tree node item. */
  fun withDoubleClick(doubleClickHandler: DoubleClickHandler) = apply {
    doubleClick = doubleClickHandler
  }

  /** Set the toggle click count (default is 2). */
  fun withToggleClickCount(clickCount: Int) = apply { toggleClickCount = clickCount }

  /** Specify specific invokeLater implementation to use. */
  fun withInvokeLaterOption(invokeLaterImpl: (Runnable) -> Unit) = apply {
    invokeLater = invokeLaterImpl
  }

  /** Do not install tree search. Can be omitted for tests. */
  fun withoutTreeSearch() = apply { installTreeSearch = false }

  /**
   * Add a column to the right of the tree node item.
   *
   * Note: This is only supported by the TreeTable implementation.
   */
  fun withColumn(columnInfo: ColumnInfo) = apply { columns.add(columnInfo) }

  /** Add a badge column to the right of the tree node item. */
  fun withBadgeSupport(badge: BadgeItem) = apply { columns.add(badge) }

  /** Add Copy, Cut, Paste & Delete support (works of the current selection) */
  fun withDataProvider(dataProvider: DataProvider) = apply { this.dataProvider = dataProvider }

  /**
   * Add Drag and Drop support.
   *
   * Optionally specify a merge operator for support of dragging multiple items. Without a merge
   * operator, only the 1st item will be dragged. When dragging items from the component tree itself
   * [deleteOriginOfInternalMove] controls whether the origin items should be deleted.
   */
  fun withDnD(merger: DnDMerger? = null, deleteOriginOfInternalMove: Boolean = true) = apply {
    dndSupport = true
    dndMerger = merger
    dndDeleteOriginOfInternalMove = deleteOriginOfInternalMove
  }

  /** Don't show the root node. */
  fun withHiddenRoot() = apply { isRootVisible = false }

  /** Show the expansion icon for the root. */
  fun withExpandableRoot() = apply { showRootHandles = true }

  /** Show the support lines for the component tree. */
  fun withShowSupportLines(showSupportLines: () -> Boolean) = apply {
    this.showSupportLines = showSupportLines
  }

  /** Checks if the node in the tree has a callstack relation. */
  fun withIsCallStackNodeCheck(isCallStackNode: (TreePath) -> Boolean) = apply {
    this.isCallStackNode = isCallStackNode
  }

  /** Show an horizontal scrollbar if necessary. */
  fun withHorizontalScrollBar() = apply { horizontalScrollbar = true }

  /** Set the component name for UI tests. */
  fun withComponentName(name: String) = apply { componentName = name }

  /** Auto scroll to make a newly selected item scroll into view. */
  fun withAutoScroll() = apply { autoScroll = true }

  /** Allows custom keyboard actions to be installed. */
  fun withKeyboardActions(installer: (JComponent) -> Unit) = apply {
    this.installKeyboardActions = installer
  }

  fun withExpandAllOnRootChange() = apply { expandAllOnRootChange = true }

  /** Build the tree component and return it with the tree model. */
  fun build(): ComponentTreeBuildResult {
    val model = TreeTableModelImpl(columns, nodeTypeMap, invokeLater)
    val table =
      TreeTableImpl(
        model,
        contextPopup,
        doubleClick,
        installKeyboardActions,
        selectionMode,
        autoScroll,
        installTreeSearch,
        expandAllOnRootChange,
        headerRenderer,
      )
    table.name = componentName // For UI tests
    if (dndSupport) {
      table.enableDnD(dndMerger, dndDeleteOriginOfInternalMove)
    }
    dataProvider?.let { DataManager.registerDataProvider(table, it) }
    val tree = table.tree
    tree.toggleClickCount = toggleClickCount
    tree.isRootVisible = isRootVisible
    tree.showsRootHandles = !isRootVisible || showRootHandles

    val horizontalPolicy =
      if (horizontalScrollbar) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER
    val verticalScrollPane =
      ScrollPaneFactory.createScrollPane(table, VERTICAL_SCROLLBAR_AS_NEEDED, horizontalPolicy)
    verticalScrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, UpperRightCorner())
    verticalScrollPane.border = JBUI.Borders.empty()

    if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_HORIZONTAL_SCROLLABLE_COMPONENT_TREE.get()) {
      val header = table.getTableHeader()
      header.setReorderingAllowed(false)
      val viewport = JViewport()
      viewport.setView(header)
      verticalScrollPane.columnHeader = viewport

      val horizontalScrollPane = ColumnTreeScrollPanel(tree, table)
      horizontalScrollPane.border = verticalScrollPane.border

      table.treeUI =
        ColumnTreeUI(
          table,
          horizontalScrollPane,
          verticalScrollPane,
          autoScroll,
          showSupportLines,
          isCallStackNode,
        )

      val outerPanel = JPanel(BorderLayout())
      // Add a vertical scroll pane wrapping the TreeTable content to the center, and add a JPanel
      // wrapping the horizontal scroll bar to the south.
      outerPanel.add(verticalScrollPane, BorderLayout.CENTER)
      outerPanel.add(horizontalScrollPane, BorderLayout.SOUTH)
      return ComponentTreeBuildResult(
        outerPanel,
        verticalScrollPane,
        horizontalScrollPane,
        table,
        tree,
        model,
        table.treeTableSelectionModel,
        table,
      )
    } else {
      return ComponentTreeBuildResult(
        verticalScrollPane,
        verticalScrollPane,
        null,
        table,
        tree,
        model,
        table.treeTableSelectionModel,
        table,
      )
    }
  }
}

/** The resulting component tree. */
class ComponentTreeBuildResult(
  /** The top component which wraps the vertical and horizontal scroll panels. */
  val component: JComponent,

  /** The vertical scroll pane that wraps the table. */
  val vScrollPane: JComponent,

  /** The horizontal scroll panel that wraps the horizontal scroll bar for the component tree. */
  val hScrollPanel: JComponent?,

  /**
   * The component that has focus in the component tree.
   *
   * Note: This will be:
   * - the TreeTable component
   */
  val focusComponent: JComponent,

  /**
   * The Tree component of the component tree.
   *
   * Note: the Tree instance may be just be a renderer instance, and may not have a parent
   * component.
   */
  val tree: Tree,

  /** The component tree model. */
  val model: ComponentTreeModel,

  /** The component tree selection model. */
  val selectionModel: ComponentTreeSelectionModel,

  /**
   * An object that can be used to modify the tree.
   *
   * The visibility of the header and the columns are supported.
   */
  val interactions: TableVisibility,
)
