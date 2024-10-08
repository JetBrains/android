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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.GroupAware
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.WithCount
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.tree.TreeModelAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TreeDropDownPopup<T, U : GroupAware<U>>(
  internal var selection: MultiSelection<WithCount<T>>,
  private val scope: CoroutineScope,
  private val groupNameSupplier: (T) -> String,
  private val nameSupplier: (T) -> String,
  private val secondaryGroupSupplier: (T) -> Set<U>,
  private val secondaryTitleSupplier: () -> JComponent? = { null },
) : JPanel(BorderLayout()) {
  private val eventDispatcher = EventDispatcher.create(CheckboxTreeListener::class.java)

  @VisibleForTesting
  val helper = CheckboxTreeHelper(CheckboxTreeHelper.DEFAULT_POLICY, eventDispatcher)

  @VisibleForTesting val root = Node("All")

  @VisibleForTesting val searchTextField = SearchTextField(false)
  private val secondaryGrouping =
    JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      border = JBUI.Borders.empty(1)
    }
  private val primaryToSecondaryGroups =
    selection.items.associate { it.value to secondaryGroupSupplier(it.value) }

  private val secondaryToPrimaryGroups =
    selection.items
      .asSequence()
      .flatMap { value ->
        val groups = secondaryGroupSupplier(value.value)
        groups.map { it to value }
      }
      .groupBy(keySelector = { it.first }, valueTransform = { it.second })
      .mapValues { it.value.toSet() }
  private val availableSecondaryGroups = secondaryToPrimaryGroups.keys.sorted()
  private val namesToSecondaryGroups = availableSecondaryGroups.associateBy { it.groupName }

  @VisibleForTesting
  val treeTable: TreeTableView =
    createTree(
      arrayOf(
        object : TreeColumnInfo(null) {
          override fun getWidth(table: JTable) =
            root
              .children()
              .asSequence()
              .flatMap { it.children().asSequence().plus(it) }
              .maxOfOrNull {
                table.getFontMetrics(table.font).stringWidth(getNodeText(it)) +
                  JBUI.scale(80) // for 2 levels of indent + checkbox
              } ?: JBUI.scale(80)
        },
        object : ColumnInfo<CheckedTreeNode, Long>(null) {
          override fun valueOf(item: CheckedTreeNode) = getIssueCount(item)

          override fun getWidth(table: JTable) =
            table.getFontMetrics(table.font).stringWidth(getIssueCount(root).toString()) +
              JBUI.scale(20) // So there's space for the scrollbar on the right
        },
      )
    )

  init {
    treeTable.tree.isFocusable = false
    initSecondaryGroupings()

    val scrollPanel =
      object : ScrollablePanel(BorderLayout()) {
        override fun getPreferredSize() =
          Dimension(
            treeTable.preferredSize.width + JBUI.scale(4),
            treeTable.preferredSize.height + JBUI.scale(4),
          )

        override fun getPreferredScrollableViewportSize() =
          Dimension(preferredSize.width, preferredSize.height.coerceAtMost(500))
      }
    val scrollPane =
      object : JBScrollPane(scrollPanel) {
        override fun getPreferredSize() =
          Dimension(
            scrollPanel.preferredScrollableViewportSize.width,
            scrollPanel.preferredScrollableViewportSize.height + JBUI.scale(4),
          )
      }
    scrollPanel.add(treeTable, BorderLayout.CENTER)
    scrollPanel.border = JBUI.Borders.empty(3, 0)
    scrollPanel.background = treeTable.background

    searchTextField.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val searchText = searchTextField.text.lowercase()
          updateTreeNodes { it.lowercase().contains(searchText) }
          val toExpand = mutableListOf<TreePath>()
          root.children().asSequence().forEach { child ->
            if (
              child.children().asSequence().any { getNodeText(it).lowercase().contains(searchText) }
            ) {
              toExpand.add(TreePath(arrayOf(root, child)))
            }
          }
          (treeTable.tree.model as DefaultTreeModel).reload()
          toExpand.forEach { treeTable.tree.expandPath(it) }
          treeTable.repaint()
        }
      }
    )

    add(
      if (secondaryToPrimaryGroups.isEmpty()) searchTextField
      else
        JPanel().apply {
          layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
          secondaryTitleSupplier()?.let { title ->
            add(
              JPanel(BorderLayout()).apply {
                add(title, BorderLayout.WEST)
                border = JBUI.Borders.empty(3)
              }
            )
          }
          add(secondaryGrouping)
          add(searchTextField)
        },
      BorderLayout.NORTH,
    )
    add(scrollPane, BorderLayout.CENTER)
    border = JBUI.Borders.empty()

    eventDispatcher.addListener(
      object : CheckboxTreeListener {
        override fun nodeStateChanged(node: CheckedTreeNode) {
          val value = (node as? Leaf<T>)?.item ?: return
          selection = if (node.isChecked) selection.select(value) else selection.deselect(value)

          val selectedSecondaryToPrimary = selectedSecondaryToPrimary()
          for (box in secondaryGrouping.children<ThreeStateCheckBox>()) {
            val boxGroup = namesToSecondaryGroups[box.text] ?: throw IllegalStateException()
            val selected = selectedSecondaryToPrimary[boxGroup] ?: emptySet()
            val available = secondaryToPrimaryGroups[boxGroup] ?: emptySet()
            box.state =
              if (selected.isEmpty()) ThreeStateCheckBox.State.NOT_SELECTED
              else if (selected == available) ThreeStateCheckBox.State.SELECTED
              else ThreeStateCheckBox.State.DONT_CARE
          }
        }
      }
    )
  }

  private fun selectedSecondaryToPrimary(): Map<U, Set<WithCount<T>>> =
    selection.selected
      .asSequence()
      .flatMap { value ->
        val groups = secondaryGroupSupplier(value.value)
        groups.map { it to value }
      }
      .groupBy(keySelector = { it.first }, valueTransform = { it.second })
      .mapValues { it.value.toSet() }

  private fun initSecondaryGroupings() {
    val selectedSecondaryToPrimary = selectedSecondaryToPrimary()

    for (item in availableSecondaryGroups) {
      val selected = selectedSecondaryToPrimary[item] ?: emptySet()
      val available = secondaryToPrimaryGroups[item] ?: emptySet()
      val state =
        if (selected.isEmpty()) ThreeStateCheckBox.State.NOT_SELECTED
        else if (selected != available) ThreeStateCheckBox.State.DONT_CARE
        else ThreeStateCheckBox.State.SELECTED
      secondaryGrouping.add(
        ThreeStateCheckBox(item.groupName, state)
          .also { box ->
            box.isThirdStateEnabled = false

            box.addItemListener {
              if (box.state == ThreeStateCheckBox.State.DONT_CARE) return@addItemListener
              val newState = box.state == ThreeStateCheckBox.State.SELECTED
              val matchingVersions = secondaryToPrimaryGroups[item] ?: emptySet()
              selection =
                matchingVersions.fold(selection) { acc, value ->
                  if (newState) acc.select(value) else acc.deselect(value)
                }
              root.checkMatching<T>(newState) {
                val set = primaryToSecondaryGroups[it] ?: return@checkMatching false
                item in set
              }
            }
          }
          .apply { border = JBUI.Borders.emptyRight(2) }
      )
    }
  }

  private fun getIssueCount(node: CheckedTreeNode): Long =
    when (node) {
      is Leaf<*> -> node.item.count
      else ->
        node.children().asSequence().sumOf { child: TreeNode ->
          getIssueCount(child as CheckedTreeNode)
        }
    }

  private fun createTree(columns: Array<ColumnInfo<out Any, out Any>>): TreeTableView {
    updateTreeNodes { true }

    val treeTable = TreeTableView(ListTreeTableModelOnColumns(root, columns))
    helper.initTree(
      treeTable.tree,
      treeTable,
      object : CheckboxTree.CheckboxTreeCellRenderer(true) {
        override fun customizeRenderer(
          tree: JTree?,
          value: Any?,
          selected: Boolean,
          expanded: Boolean,
          leaf: Boolean,
          row: Int,
          hasFocus: Boolean,
        ) {
          myCheckbox.text = getNodeText(value as CheckedTreeNode)
        }
      },
    )

    treeTable.tree.apply {
      isRootVisible = true
      selectionModel = null
      if (root.childCount == 1) {
        expandPath(TreePath(arrayOf(root, root.firstChild)))
      }
    }
    treeTable.rowSelectionAllowed = false
    TreeSpeedSearch(treeTable.tree, true) { getNodeText(it.lastPathComponent as CheckedTreeNode) }
    return treeTable
  }

  private fun filterGroupAndSortItems(
    filter: (String) -> Boolean
  ): List<FilteredGroup<WithCount<T>>> =
    selection.items
      .groupBy { groupNameSupplier(it.value) }
      .mapNotNull { entry ->
        val filteredValues =
          if (filter(entry.key)) {
            entry.value
          } else {
            entry.value.filter { filter(nameSupplier(it.value)) }
          }
        val sum = filteredValues.sumOf { it.count }
        if (filteredValues.isEmpty()) {
          null
        } else {
          sum to FilteredGroup(entry.key, filteredValues, entry.value.size > 1)
        }
      }
      .sortedByDescending { it.first }
      .map { it.second }

  private fun updateTreeNodes(filter: (String) -> Boolean): Set<T> {
    root.removeAllChildren()

    val groupedValues = filterGroupAndSortItems(filter)
    groupedValues.forEach { (groupingKey, values, wasMultiple) ->
      val node =
        if (!wasMultiple) {
          values.single().let { withCount ->
            Leaf(withCount).apply { isChecked = withCount in selection.selected }
          }
        } else {
          val node =
            Node(groupingKey).apply {
              values.forEach { value ->
                add(Leaf(value).apply { isChecked = value in selection.selected })
              }
            }
          if (node.childCount > 0) node else null
        }
      if (node != null) {
        root.add(node)
      }
    }
    return selection.selected.asSequence().map { it.value }.toSet()
  }

  private fun getNodeText(node: TreeNode) =
    when (node) {
      is Leaf<*> ->
        (node.item.value as T).let { value ->
          val name = nameSupplier(value)
          val group = groupNameSupplier(value)
          if (node.parent?.parent != null) name else if (name == group) name else "$group ($name)"
        }
      is Node -> node.title
      else -> node.toString()
    }

  fun asPopup(): JBPopup {
    val popup =
      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(this, searchTextField)
        .setFocusable(true)
        .setRequestFocus(true)
        .createPopup()
    val updatePopupSize = {
      scope.launch(AndroidDispatchers.uiThread) {
        popup.size = Dimension(preferredSize.width, preferredSize.height + 3)
      }
      Unit
    }
    updatePopupSize()
    treeTable.tree.addTreeExpansionListener(
      object : TreeExpansionListener {
        override fun treeExpanded(event: TreeExpansionEvent?) = updatePopupSize()

        override fun treeCollapsed(event: TreeExpansionEvent?) = updatePopupSize()
      }
    )
    treeTable.tree.model.addTreeModelListener(
      object : TreeModelAdapter() {
        override fun treeStructureChanged(event: TreeModelEvent?) = updatePopupSize()
      }
    )
    return popup
  }

  private fun <T> Node.checkMatching(newState: Boolean, predicate: (T) -> Boolean) {
    for (node in children()) {
      when (node) {
        is Leaf<*> -> {
          if (predicate(node.item.value as T)) {
            helper.setNodeState(treeTable.tree, node, newState)
          }
          continue
        }
        is Node -> node.checkMatching(newState, predicate)
        else -> continue
      }
    }
  }

  private data class FilteredGroup<T>(
    val groupName: String,
    val values: List<T>,
    // Represents whether this filtered group originally had multiple items. This affects how the
    // filtered value will be shown in the dropdown. If it belonged to a group with multiple items,
    // then it will be shown as a collapsible node. Otherwise if it belonged to a group by itself,
    // then it will be shown as a leaf node.
    val wasMultiple: Boolean,
  )

  data class Leaf<T>(val item: WithCount<T>) : CheckedTreeNode(item)

  data class Node(val title: String) : CheckedTreeNode(title)
}

private inline fun <reified T> JPanel.children(): Sequence<T> = sequence {
  for (i in 0 until componentCount) {
    yield(getComponent(i) as T)
  }
}
