/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintSettings
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.UniversalProblemsPanelEvent
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.WeakReferenceDisposableWrapper
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ColorUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.Content
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val TOOLBAR_ACTIONS_ID = "Android.Designer.IssuePanel.ToolbarActions"

/** The id of pop action group which is shown when right-clicking a tree node. */
private const val POPUP_HANDLER_ACTION_ID = "Android.Designer.IssuePanel.TreePopup"
private val KEY_DETAIL_VISIBLE = DesignerCommonIssuePanel::class.java.name + "_detail_visibility"

val DESIGNER_COMMON_ISSUE_PANEL =
  DataKey.create<DesignerCommonIssuePanel>("DesignerCommonIssuePanel")

/** The issue panel to load the issues from Layout Editor and Layout Validation Tool. */
class DesignerCommonIssuePanel(
  parentDisposable: Disposable,
  private val project: Project,
  vertical: Boolean,
  initialPanelName: String,
  private val tabId: String,
  nodeFactoryProvider: () -> NodeFactory,
  issueFilter: DesignerCommonIssueProvider.Filter,
  private val emptyMessageProvider: suspend () -> String,
  private val onContentPopulated: (Content) -> Unit = {},
) : SimpleToolWindowPanel(vertical), ProblemsViewTab, Disposable {

  private val coroutineScope = AndroidCoroutineScope(this)
  private val issueListeners = mutableListOf<IssueListener>()

  var sidePanelVisible =
    PropertiesComponent.getInstance(project).getBoolean(KEY_DETAIL_VISIBLE, true)
    set(value) {
      field = value
      updateSidePanel(getSelectedNode(), value)
      PropertiesComponent.getInstance(project).setValue(KEY_DETAIL_VISIBLE, value)
    }

  private val tree: Tree
  private val splitter: OnePixelSplitter

  private val sidePanel: DesignerCommonIssueSidePanel
  private val treeModel: DesignerCommonIssueModel

  val issueProvider: DesignerCommonIssueProvider<Any>

  init {
    Disposer.register(parentDisposable, this)
    name = initialPanelName
    issueProvider = DesignToolsIssueProvider(this, project, issueFilter, tabId)
    treeModel = DesignerCommonIssuePanelModelProvider.getInstance(project).createModel()
    treeModel.root = DesignerCommonIssueRoot(project, issueProvider, nodeFactoryProvider)
    updateIssueOrder()

    val asyncModel = AsyncTreeModel(treeModel, this)
    tree = Tree(asyncModel)
    tree.emptyText.text = "Loading..."
    updateEmptyMessageIfNeed()
    PopupHandler.installPopupMenu(
      tree,
      POPUP_HANDLER_ACTION_ID,
      "Android.Designer.IssuePanel.TreePopup",
    )

    tree.isRootVisible = false
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    TreeSpeedSearch.installOn(tree)
    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)

    val toolbarActionGroup =
      ActionManager.getInstance().getAction(TOOLBAR_ACTIONS_ID) as ActionGroup
    ActionManager.getInstance()
      .createActionToolbar(javaClass.name, toolbarActionGroup, false)
      .apply {
        targetComponent = this@DesignerCommonIssuePanel
        toolbar = component
      }

    sidePanel = DesignerCommonIssueSidePanel(project, this)

    splitter = OnePixelSplitter(vertical, 0.5f, 0.3f, 0.7f)
    splitter.proportion = 0.5f
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
    splitter.secondComponent = sidePanel
    splitter.setResizeEnabled(true)
    setContent(splitter)

    treeModel.addTreeModelListener(
      object : TreeModelAdapter() {
        override fun treeNodesInserted(event: TreeModelEvent) {
          // Make sure the new inserted node (e.g. Layout Validation node) is expanded.
          TreeUtil.promiseExpand(tree, event.treePath)
        }
      }
    )

    tree.addTreeSelectionListener(RestoreSelectionListener())
    tree.addTreeSelectionListener { event ->
      val newSelectedNode = event?.newLeadSelectionPath?.lastPathComponent
      if (newSelectedNode == null) {
        updateSidePanel(null, false) // force hide the side panel even the sidePanelVisible is true.
        issueListeners.forEach { it.onIssueSelected(null) }
        return@addTreeSelectionListener
      }
      val selectedNode = newSelectedNode as DesignerCommonIssueNode
      updateSidePanel(selectedNode, sidePanelVisible)
      (selectedNode as? IssueNode)?.issue.let { issue ->
        issueListeners.forEach { it.onIssueSelected(issue) }
      }
    }

    // Listener for metric
    tree.addTreeSelectionListener {
      val newSelection = it?.newLeadSelectionPath?.lastPathComponent
      val oldSelection = it?.oldLeadSelectionPath?.lastPathComponent
      if (newSelection is IssueNode && newSelection != oldSelection) {
        DesignerCommonIssuePanelUsageTracker.getInstance().trackSelectingIssue(project)
      }
    }

    updateSidePanel(getSelectedNode(), sidePanelVisible)

    issueProvider.registerUpdateListener {
      updateTree()
      updateEmptyMessageIfNeed()
    }
  }

  override fun getName(count: Int): String {
    return name?.let { createTabName(it, count) } ?: DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE
  }

  override fun getTabId() = tabId

  @Suppress("UnstableApiUsage")
  override fun customizeTabContent(content: Content) {
    content.tabName = tabId
    treeModel.addTreeModelListener(
      object : TreeModelAdapter() {
        override fun process(event: TreeModelEvent, type: EventType) {
          val count = issueProvider.getFilteredIssues().distinct().size
          // This change the ui text, run it in the UI thread.
          runInEdt {
            if (project.isDisposed) return@runInEdt
            content.displayName = getName(count)
          }
        }
      }
    )
    onContentPopulated(content)
  }

  override fun orientationChangedTo(vertical: Boolean) {
    isVertical = vertical
    splitter.orientation = vertical
  }

  override fun selectionChangedTo(selected: Boolean) {
    if (selected) {
      updateIssueOrder()
      updateIssueVisibility()
      val type =
        if (tabId == SHARED_ISSUE_PANEL_TAB_ID)
          UniversalProblemsPanelEvent.ActivatedTab.DESIGN_TOOLS
        else UniversalProblemsPanelEvent.ActivatedTab.UI_CHECK
      DesignerCommonIssuePanelUsageTracker.getInstance().trackSelectingTab(type, project)
    }
  }

  override fun visibilityChangedTo(visible: Boolean) {
    if (visible) {
      updateIssueOrder()
      updateIssueVisibility()
    }
  }

  private fun getDataInBackground(dataId: String, node: DesignerCommonIssueNode): Any? =
    when (dataId) {
      CommonDataKeys.NAVIGATABLE.name -> node.getNavigatable()
      else -> null
    }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[DESIGNER_COMMON_ISSUE_PANEL] = this
    val node = getSelectedNode() ?: return
    sink[PlatformDataKeys.SELECTED_ITEM] = node
    sink[PlatformDataKeys.VIRTUAL_FILE] = node.getVirtualFile()
    sink.lazy(CommonDataKeys.NAVIGATABLE) {
      node.getNavigatable()
    }
  }

  fun setSelectedNode(visitor: TreeVisitor) {
    TreeUtil.promiseSelect(tree, visitor)
  }

  private fun updateEmptyMessageIfNeed() {
    if (issueProvider.getFilteredIssues().isEmpty()) {
      coroutineScope.launch(workerThread) {
        val newEmptyString = emptyMessageProvider()
        if (newEmptyString != tree.emptyText.text) {
          withContext(uiThread) { tree.emptyText.text = newEmptyString }
        }
      }
    }
  }

  private fun updateTree() {
    treeModel.structureChanged(null)
    TreeUtil.promiseExpand(tree, 2)
  }

  fun setViewOptionFilter(filter: DesignerCommonIssueProvider.Filter) {
    issueProvider.viewOptionFilter = filter
  }

  fun updateIssueOrder() {
    val state = ProblemsViewState.getInstance(project)
    (treeModel.root as? DesignerCommonIssueRoot)?.setComparator(
      DesignerCommonIssueNodeComparator(state.sortBySeverity, state.sortByName)
    )
    treeModel.structureChanged(null)
  }

  fun updateIssueVisibility() {
    val hiddenSeverities = ProblemsViewState.getInstance(project).hideBySeverity
    val showVisualLint = VisualLintSettings.getInstance(project).isVisualLintFilterSelected

    val filter =
      DesignerCommonIssueProvider.Filter { issue ->
        when {
          issue is VisualLintRenderIssue -> showVisualLint
          hiddenSeverities.contains(issue.severity.myVal) -> false
          else -> true
        }
      }
    setViewOptionFilter(filter)
  }

  private fun getSelectedNode(): DesignerCommonIssueNode? {
    return TreeUtil.getLastUserObject(DesignerCommonIssueNode::class.java, tree.selectionPath)
  }

  private fun updateSidePanel(node: DesignerCommonIssueNode?, visible: Boolean) {
    if (visible && sidePanel.loadIssueNode(node)) {
      splitter.secondComponent = sidePanel
      sidePanel.invalidate()
    } else {
      splitter.secondComponent = null
    }
    splitter.revalidate()
  }

  fun addIssueSelectionListener(listener: IssueListener, parentDisposable: Disposable) {
    Disposer.register(
      parentDisposable,
      WeakReferenceDisposableWrapper { issueListeners.remove(listener) },
    )
    issueListeners.add(listener)
  }

  fun removeIssueSelectionListener(listener: IssueListener) {
    issueListeners.remove(listener)
  }

  override fun dispose() {
    issueListeners.clear()
  }
}

/**
 * Used to find the target [DesignerCommonIssueNode] in the [com.intellij.ui.treeStructure.Tree].
 */
class DesignerIssueNodeVisitor(private val node: DesignerCommonIssueNode) : TreeVisitor {

  override fun visit(path: TreePath): TreeVisitor.Action {
    return when (val visitedNode = path.lastPathComponent) {
      !is DesignerCommonIssueNode -> TreeVisitor.Action.CONTINUE
      else -> compareNode(visitedNode, node)
    }
  }

  private fun compareNode(
    node1: DesignerCommonIssueNode?,
    node2: DesignerCommonIssueNode?,
  ): TreeVisitor.Action {
    if (node1 == null || node2 == null) {
      return if (node1 == null && node2 == null) TreeVisitor.Action.INTERRUPT
      else TreeVisitor.Action.CONTINUE
    }
    if (node1::class != node2::class) {
      return TreeVisitor.Action.CONTINUE
    }
    return when (node1) {
      is IssuedFileNode -> visitIssuedFileNode(node1, node2 as IssuedFileNode)
      is NoFileNode -> visitNoFileNode(node1, node2 as NoFileNode)
      is IssueNode -> visitIssueNode(node1, node2 as IssueNode)
      is DesignerCommonIssueRoot ->
        if (node1 === node2) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
      else -> TreeVisitor.Action.CONTINUE
    }
  }

  private fun visitIssuedFileNode(
    node1: IssuedFileNode,
    node2: IssuedFileNode,
  ): TreeVisitor.Action {
    return if (node1.file != node2.file) TreeVisitor.Action.CONTINUE
    else {
      compareNode(
        node1.parentDescriptor?.element as? DesignerCommonIssueNode,
        node2.parentDescriptor?.element as? DesignerCommonIssueNode,
      )
    }
  }

  private fun visitNoFileNode(node1: NoFileNode, node2: NoFileNode): TreeVisitor.Action {
    return if (node1.name != node2.name) TreeVisitor.Action.CONTINUE
    else {
      compareNode(
        node1.parentDescriptor?.element as? DesignerCommonIssueNode,
        node2.parentDescriptor?.element as? DesignerCommonIssueNode,
      )
    }
  }

  private fun visitIssueNode(node1: IssueNode, node2: IssueNode): TreeVisitor.Action {
    val issue1 = node1.issue
    val issue2 = node2.issue

    // It would be complicated if the issues are VisualLintRenderIssue, compare the parents first.
    val actionAfterComparingParents =
      compareNode(
        node1.parentDescriptor?.element as? DesignerCommonIssueNode,
        node2.parentDescriptor?.element as? DesignerCommonIssueNode,
      )
    if (actionAfterComparingParents == TreeVisitor.Action.CONTINUE) {
      return TreeVisitor.Action.CONTINUE
    }

    if (issue1 is VisualLintRenderIssue || issue2 is VisualLintRenderIssue) {
      if (issue1 !is VisualLintRenderIssue || issue2 !is VisualLintRenderIssue) {
        return TreeVisitor.Action.CONTINUE
      }
      if (issue1.summary != issue2.summary) {
        return TreeVisitor.Action.CONTINUE
      }
      val files1 = issue1.models.toList()
      val files2 = issue2.models.toList()
      if (files1 != files2) {
        return TreeVisitor.Action.CONTINUE
      }
      val comparator = compareBy<NlComponent>({ it.id }, { it.tagName }, { createIndexString(it) })

      val visitedNodeIterator = issue1.components.sortedWith(comparator).iterator()
      val nodeIterator = issue2.components.sortedWith(comparator).iterator()

      while (visitedNodeIterator.hasNext() && nodeIterator.hasNext()) {
        if (comparator.compare(visitedNodeIterator.next(), nodeIterator.next()) != 0) {
          return TreeVisitor.Action.CONTINUE
        }
      }
      return if (visitedNodeIterator.hasNext() || nodeIterator.hasNext())
        TreeVisitor.Action.CONTINUE
      else TreeVisitor.Action.INTERRUPT
    }

    return if (issue1 == issue2) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
  }

  /** Sequence of index to serialize the relative position of a [NlComponent] in the XML file. */
  private fun createIndexString(component: NlComponent): String {
    val orders = mutableListOf<Int>()
    var current: NlComponent? = component
    while (true) {
      val parent = current?.parent ?: break
      val order = parent.children.indexOf(current)
      orders.add(order)
      current = parent
    }
    return orders.reversed().joinToString(",")
  }
}

/**
 * This should be same as [com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel.getName]
 * for consistency.
 */
@VisibleForTesting
@NlsContexts.TabTitle
fun createTabName(name: String, count: Int): String {
  val padding = (if (count <= 0) 0 else JBUI.scale(8)).toString()
  val fg = ColorUtil.toHtmlColor(NamedColorUtil.getInactiveTextColor())
  val number = if (count <= 0) "" else count.toString()

  return "<html><body>" +
    "<table cellpadding='0' cellspacing='0'><tr>" +
    "<td><nobr>$name</nobr></td>" +
    "<td width='$padding'></td>" +
    "<td><font color='$fg'>$number</font></td>" +
    "</tr></table></body></html>"
}
