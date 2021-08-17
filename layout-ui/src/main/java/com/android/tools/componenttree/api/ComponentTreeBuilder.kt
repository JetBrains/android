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

import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.componenttree.impl.ComponentTreeModelImpl
import com.android.tools.componenttree.impl.ComponentTreeSelectionModelImpl
import com.android.tools.componenttree.impl.TreeImpl
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.tree.ui.Control
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import javax.swing.tree.TreeSelectionModel

/**
 * A Handler which will display a context popup menu.
 */
typealias ContextPopupHandler = (component: JComponent, x: Int, y: Int) -> Unit
typealias DoubleClickHandler = () -> Unit

/**
 * A component tree builder creates a tree that can hold multiple types of nodes.
 *
 * Each [NodeType] must be specified. If a node type represent an Android View
 * consider using [ViewNodeType] which defines a standard node renderer.
 */
class ComponentTreeBuilder {
  private val nodeTypeMap = mutableMapOf<Class<*>, NodeType<*>>()
  private var contextPopup: ContextPopupHandler = { _, _, _ -> }
  private var doubleClick: DoubleClickHandler = { }
  private val badges = mutableListOf<BadgeItem>()
  private var selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
  private var invokeLater: (Runnable) -> Unit = SwingUtilities::invokeLater
  private val keyStrokes = mutableMapOf<KeyStroke, Pair<String, () -> Unit>>()
  private var installTreeSearch = true
  private var isRootVisible = true
  private var showRootHandles = false
  private var horizontalScrollbar = false
  private var autoScroll = false
  private var componentName =  "componentTree"
  private var painter: (() -> Control.Painter?)? = null

  /**
   * Register a [NodeType].
   */
  fun <T> withNodeType(type: NodeType<T>) = apply { nodeTypeMap[type.clazz] = type }

  /**
   * Allow multiple nodes to be selected in the tree (default is a single selection).
   */
  fun withMultipleSelection() = apply { selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION }

  /**
   * Add a context popup menu on the tree node item.
   */
  fun withContextMenu(treeContextMenu: ContextPopupHandler) = apply { contextPopup = treeContextMenu }

  /**
   * Add a double click handler on the tree node item.
   */
  fun withDoubleClick(doubleClickHandler: DoubleClickHandler) = apply { doubleClick = doubleClickHandler }

  /**
   * Add key strokes on the tree.
   */
  fun withKeyActionKey(name: String, keyStroke: KeyStroke, action: () -> Unit) = apply { keyStrokes[keyStroke] = Pair(name, action) }

  /**
   * Specify specific invokeLater implementation to use.
   */
  fun withInvokeLaterOption(invokeLaterImpl: (Runnable) -> Unit) = apply { invokeLater = invokeLaterImpl }

  /**
   * Do not install tree search. Can be omitted for tests.
   */
  fun withoutTreeSearch() = apply { installTreeSearch = false }

  /**
   * Add a badge icon to go to the right of a tree node item.
   */
  fun withBadgeSupport(badge: BadgeItem) = apply { badges.add(badge) }

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
   * Build the tree component and return it with the tree model.
   */
  fun build(): Triple<JComponent, ComponentTreeModel, ComponentTreeSelectionModel> {
    val model = ComponentTreeModelImpl(nodeTypeMap, invokeLater)
    val selectionModel = ComponentTreeSelectionModelImpl(model)
    val tree = TreeImpl(model, contextPopup, doubleClick, badges, componentName, painter)
    tree.isRootVisible = isRootVisible
    tree.showsRootHandles = !isRootVisible || showRootHandles
    if (installTreeSearch) {
      TreeSpeedSearch(tree) { model.toSearchString(it.lastPathComponent) }
    }
    selectionModel.selectionMode = selectionMode
    if (autoScroll) {
      selectionModel.addAutoScrollListener {
        tree.selectionRows?.singleOrNull()?.let { tree.scrollRowToVisible(it) }
      }
    }
    tree.selectionModel = selectionModel
    keyStrokes.forEach {
      tree.registerActionKey(it.value.second, it.key, it.value.first, { true }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    }
    val horizontalPolicy = if (horizontalScrollbar) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER
    val scrollPane = ScrollPaneFactory.createScrollPane(tree, VERTICAL_SCROLLBAR_AS_NEEDED, horizontalPolicy)
    scrollPane.border = JBUI.Borders.empty()
    return Triple(scrollPane, model, selectionModel)
  }
}
