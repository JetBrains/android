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

import com.android.tools.adtui.stdui.CommonHyperLinkLabel
import com.android.tools.adtui.stdui.SmallTextLabel
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.componenttree.api.createIntColumn
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.IconProvider
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode.Companion.readAccess
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.ui.LINES
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableActions
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.table.TableCellRenderer
import kotlin.math.max

fun AnActionEvent.treePanel(): LayoutInspectorTreePanel? =
  ToolContent.getToolContent(this.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    as? LayoutInspectorTreePanel

fun AnActionEvent.tree(): Tree? = treePanel()?.tree

private const val ICON_VERTICAL_BORDER = 5
private const val ICON_HORIZONTAL_BORDER = 10
private const val TEXT_HORIZONTAL_BORDER = 5
const val COMPONENT_TREE_NAME = "COMPONENT_TREE"

class LayoutInspectorTreePanel(parentDisposable: Disposable) : ToolContent<LayoutInspector> {
  @VisibleForTesting val nodeType = InspectorViewNodeType()

  @VisibleForTesting val componentTreeBuildResult = buildComponentTree(nodeType)

  private var layoutInspector: LayoutInspector? = null
  private val inspectorModel
    get() = layoutInspector?.inspectorModel

  @VisibleForTesting val tree = componentTreeBuildResult.tree
  @VisibleForTesting val focusComponent = componentTreeBuildResult.focusComponent
  @VisibleForTesting val componentTreeModel = componentTreeBuildResult.model
  @VisibleForTesting val componentTreeSelectionModel = componentTreeBuildResult.selectionModel

  private val componentTreePanel = componentTreeBuildResult.component

  private val interactions = componentTreeBuildResult.interactions
  // synthetic node to hold the root of the tree.
  private var root: TreeViewNode = ViewNode("root").treeNode
  // synthetic node for computing new hierarchy.
  private val temp: TreeViewNode = ViewNode("temp").treeNode
  // a map from [AndroidWindow.id] to the root [TreeViewNode] of that window
  private val windowRoots = mutableMapOf<Any, MutableList<TreeViewNode>>()
  private val additionalActions: List<AnAction>
  private val comparator = SpeedSearchComparator(false)
  private var toolWindowCallback: ToolWindowCallback? = null
  private var filter = ""
  private val modelModifiedListener =
    InspectorModel.ModificationListener { oldWindow, newWindow, isStructuralChange ->
      modelModified(oldWindow, newWindow, isStructuralChange)
      componentTreePanel.repaint()
    }
  private val selectionChangedListener: (ViewNode?, ViewNode?, SelectionOrigin) -> Unit =
    { oldView, newView, origin ->
      selectionChanged(oldView, newView, origin)
    }
  private val connectionListener: (InspectorClient?) -> Unit = { inspectorClient ->
    handleConnectionChange(inspectorClient)
  }
  private val componentTreeSelectionListener: (List<Any>) -> Unit = {
    val view = (it.firstOrNull() as? TreeViewNode)?.view
    inspectorModel?.setSelection(view, SelectionOrigin.COMPONENT_TREE)
    layoutInspector?.currentClient?.stats?.selectionMadeFromComponentTree(view)
  }
  private var upAction: Action? = null
  private var downAction: Action? = null

  private val rootPanel = RootPanel(this, componentTreePanel)

  @VisibleForTesting
  val nodeViewType
    get() = nodeType

  init {
    Disposer.register(parentDisposable, this)

    val gotoDeclarationAction =
      ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION)
    if (gotoDeclarationAction != null) {
      GotoDeclarationAction.registerCustomShortcutSet(
        gotoDeclarationAction.shortcutSet,
        componentTreePanel,
        parentDisposable
      )
    }
    componentTreeSelectionModel.addSelectionListener(componentTreeSelectionListener)

    // For UI tests
    focusComponent.name = COMPONENT_TREE_NAME
    focusComponent.addKeyListener(
      object : KeyAdapter() {
        override fun keyTyped(event: KeyEvent) {
          if (Character.isAlphabetic(event.keyChar.code)) {
            toolWindowCallback?.startFiltering(event.keyChar.toString())
          }
        }
      }
    )

    val treeExpander =
      object : DefaultTreeExpander({ tree }) {
        override fun isEnabled(tree: JTree) = componentTreePanel.isShowing && tree.rowCount > 0
      }
    val commonActionManager = CommonActionsManager.getInstance()
    additionalActions =
      listOf(
        FilterGroupAction { layoutInspector?.renderModel },
        commonActionManager.createExpandAllAction(treeExpander, tree),
        commonActionManager.createCollapseAllAction(treeExpander, tree)
      )
  }

  private fun buildComponentTree(nodeType: InspectorViewNodeType): ComponentTreeBuildResult {
    val recompositionCountsColumn =
      createIntColumn<TreeViewNode>(
        "Counts",
        { (it.view as? ComposeViewNode)?.recompositions?.count },
        leftDivider = true,
        maxInt = { inspectorModel?.maxRecomposition?.count ?: 0 },
        minInt = { 0 },
        headerRenderer = createCountsHeader()
      )

    val recompositionSkipsColumn =
      createIntColumn<TreeViewNode>(
        "Skips",
        { (it.view as? ComposeViewNode)?.recompositions?.skips },
        foreground = JBColor(Gray._192, Gray._128),
        maxInt = { inspectorModel?.maxRecomposition?.skips ?: 0 },
        minInt = { 0 },
        headerRenderer = createSkipsHeader()
      )

    return ComponentTreeBuilder()
      .withHiddenRoot()
      .withAutoScroll()
      .withNodeType(nodeType)
      .withDoubleClick(::doubleClick)
      .withToggleClickCount(3)
      .withContextMenu(::showPopup)
      .withoutTreeSearch()
      .withHeaderRenderer(createTreeHeaderRenderer())
      .withColumn(recompositionCountsColumn)
      .withColumn(recompositionSkipsColumn)
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withHorizontalScrollBar()
      .withComponentName("inspectorComponentTree")
      .withPainter { if (layoutInspector?.treeSettings?.supportLines == true) LINES else null }
      .withKeyboardActions(::installKeyboardActions)
      .build()
  }

  private fun createTreeHeaderRenderer(): TableCellRenderer {
    val header =
      SmallTextLabel("Recomposition counts").apply {
        border = JBUI.Borders.empty(ICON_VERTICAL_BORDER, TEXT_HORIZONTAL_BORDER)
      }
    val reset =
      CommonHyperLinkLabel().apply {
        text = "Reset"
        border = JBUI.Borders.empty(ICON_VERTICAL_BORDER, ICON_HORIZONTAL_BORDER)
        hyperLinkListeners.add(::resetRecompositionCountsFromTableHeaderClick)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to reset recomposition counts"
      }
    val panel =
      JPanel(BorderLayout()).apply {
        background = UIUtil.TRANSPARENT_COLOR
        isOpaque = false
        add(header, BorderLayout.CENTER)
        add(reset, BorderLayout.EAST)
      }
    return TableCellRenderer { _, _, _, _, _, _ -> panel }
  }

  private fun createCountsHeader(): TableCellRenderer {
    return createIconHeader(
      StudioIcons.LayoutInspector.Toolbar.RECOMPOSITION_COUNT,
      toolTipText = "Number of times this composable has been recomposed"
    )
  }

  private fun createSkipsHeader(): TableCellRenderer {
    return createIconHeader(
      StudioIcons.LayoutInspector.Toolbar.RECOMPOSITION_SKIPPED,
      toolTipText = "Number of times recomposition for this component has been skipped"
    )
  }

  private fun createIconHeader(icon: Icon, toolTipText: String? = null): TableCellRenderer {
    val label = JBLabel(icon)
    label.border = JBUI.Borders.empty(ICON_VERTICAL_BORDER, ICON_HORIZONTAL_BORDER)
    label.toolTipText = toolTipText
    return TableCellRenderer { _, _, _, _, _, _ -> label }
  }

  private fun installKeyboardActions(focusedComponent: JComponent) {
    val down = TableActions.Down.ID
    val up = TableActions.Up.ID

    if (downAction == null) {
      // Save the default up and down actions:
      downAction = focusedComponent.actionMap.get(down)
      upAction = focusedComponent.actionMap.get(up)
    }
    focusedComponent.actionMap.put(down, TreeAction(::nextMatch))
    focusedComponent.actionMap.put(up, TreeAction(::previousMatch))
  }

  @Suppress("UNUSED_PARAMETER")
  private fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
    val node = componentTreeSelectionModel.currentSelection.singleOrNull() as TreeViewNode?
    if (node != null) {
      inspectorModel?.let { showViewContextMenu(listOf(node.view), it, component, x, y) }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun doubleClick(item: Any) {
    layoutInspector?.let {
      it.currentClient.stats.gotoSourceFromTreeDoubleClick()
      GotoDeclarationAction.navigateToSelectedView(
        it.coroutineScope,
        it.inspectorModel,
        it.notificationModel
      )
    }
  }

  fun updateRecompositionColumnVisibility() {
    invokeLater {
      val show =
        layoutInspector?.treeSettings?.showRecompositions ?: false &&
          layoutInspector?.currentClient?.isConnected ?: false
      interactions.setHeaderVisibility(show)
      interactions.setColumnVisibility(1, show)
      interactions.setColumnVisibility(2, show)
    }
  }

  private fun resetRecompositionCountsFromTableHeaderClick() {
    resetRecompositionCounts()
    layoutInspector?.currentClient?.stats?.resetRecompositionCountsClick()
  }

  fun resetRecompositionCounts() {
    val inspector = layoutInspector ?: return
    val client = inspector.currentClient as? AppInspectionInspectorClient
    client?.updateRecompositionCountSettings()
    inspector.inspectorModel.resetRecompositionCounts()
    componentTreeModel.columnDataChanged()
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    cleanUp()

    layoutInspector = toolContext

    // register listeners on new layout inspector
    nodeType.model = inspectorModel
    componentTreeModel.treeRoot = root
    inspectorModel?.addModificationListener(modelModifiedListener)
    inspectorModel?.addSelectionListener(selectionChangedListener)
    inspectorModel?.addConnectionListener(connectionListener)
    inspectorModel?.windows?.values?.forEach { modelModified(null, it, true) }
    rootPanel.layoutInspector = toolContext

    updateRecompositionColumnVisibility()
  }

  /** Remove all listeners registered on Layout Inspector */
  private fun cleanUp() {
    inspectorModel?.removeModificationListener(modelModifiedListener)
    inspectorModel?.removeSelectionListener(selectionChangedListener)
    inspectorModel?.removeConnectionListener(connectionListener)
  }

  override fun getAdditionalActions() = additionalActions

  override fun getComponent(): JComponent = rootPanel

  override fun registerCallbacks(callback: ToolWindowCallback) {
    toolWindowCallback = callback
  }

  override fun supportsFiltering(): Boolean = true

  override fun setFilter(newFilter: String) {
    filter = newFilter
    val selection = inspectorModel?.selection?.treeNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = max(0, nodes.indexOf(selection))
    for (i in 0 until nodeCount) {
      // Select the next matching node, wrapping at the last node, and starting with the current
      // selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex + i, nodeCount)])) {
        componentTreePanel.repaint()
        return
      }
    }
    componentTreePanel.repaint()
  }

  override fun isFilteringActive(): Boolean {
    return layoutInspector?.currentClient?.isConnected ?: false
  }

  fun updateSemanticsFiltering() {
    setFilter(filter)
  }

  private fun nextMatch(event: ActionEvent) {
    if (filter.isEmpty() && layoutInspector?.treeSettings?.highlightSemantics == false) {
      downAction?.actionPerformed(event)
      return
    }
    val selection = inspectorModel?.selection?.treeNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = nodes.indexOf(selection) // if selection is null start from index -1
    for (i in 1..nodeCount) {
      // Select the next matching node, wrapping at the last node, and starting with the node just
      // after the current selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex + i, nodeCount)])) {
        return
      }
    }
  }

  private fun previousMatch(event: ActionEvent) {
    if (filter.isEmpty() && layoutInspector?.treeSettings?.highlightSemantics == false) {
      upAction?.actionPerformed(event)
      return
    }
    val selection = inspectorModel?.selection?.treeNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = max(0, nodes.indexOf(selection)) // if selection is null start from index 0
    for (i in 1..nodeCount) {
      // Select the previous matching node, wrapping at the first node, and starting with the node
      // just prior to the current selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex - i, nodeCount)])) {
        return
      }
    }
  }

  private fun matchAndSelectNode(node: TreeViewNode): Boolean {
    val match = matchNode(node)
    if (match) {
      selectNode(node)
    }
    return match
  }

  private fun matchNode(node: TreeViewNode): Boolean {
    val inspector = layoutInspector ?: return true
    if (!inspector.inspectorModel.isVisible(node.view)) {
      return false
    }
    val treeSettings = inspector.treeSettings
    if (filter.isEmpty() && !treeSettings.highlightSemantics) {
      return true
    }
    if (
      treeSettings.highlightSemantics &&
        !node.view.hasMergedSemantics &&
        !node.view.hasUnmergedSemantics
    ) {
      return false
    }
    val name = node.view.qualifiedName
    val id = node.view.viewId?.name
    val text = node.view.textValue.ifEmpty { null }
    val searchString = listOfNotNull(name, id, text).joinToString(" - ")
    return comparator.matchingFragments(filter, searchString) != null
  }

  private fun selectNode(node: TreeViewNode) {
    inspectorModel?.apply {
      if (node.view !== selection) {
        setSelection(node.view, SelectionOrigin.COMPONENT_TREE)
        layoutInspector?.currentClient?.stats?.selectionMadeFromComponentTree(node.view)
      }
    }
  }

  private fun getNodes(): List<TreeViewNode> = root.flatten().minus(root).toList()

  override fun getFilterKeyListener() = filterKeyListener

  private val filterKeyListener =
    object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.modifiersEx != 0) {
          return
        }
        when (event.keyCode) {
          KeyEvent.VK_DOWN -> {
            nextMatch(ActionEvent(focusComponent, 0, ""))
            event.consume()
          }
          KeyEvent.VK_UP -> {
            previousMatch(ActionEvent(focusComponent, 0, ""))
            event.consume()
          }
          KeyEvent.VK_ENTER -> {
            focusComponent.requestFocusInWindow()
            invokeLater { toolWindowCallback?.stopFiltering() }
            event.consume()
          }
        }
      }
    }

  override fun dispose() {
    cleanUp()
  }

  private fun modelModified(old: AndroidWindow?, new: AndroidWindow?, structuralChange: Boolean) {
    if (structuralChange) {
      val added = new?.let { addToRoot(it) } ?: emptyList()
      var changedNode = added.singleOrNull() ?: root
      val toExpand = if (old == null) added else emptyList()
      if (windowRoots.keys.retainAll(inspectorModel?.windows?.keys ?: emptySet())) {
        // If a different window was removed then force an update of the root node
        changedNode = root
      }
      rebuildRoot()
      componentTreeModel.hierarchyChanged(changedNode, toExpand)
    } else {
      componentTreeModel.columnDataChanged()
    }
    updateRecompositionColumnVisibility()
    inspectorModel?.let { model ->
      layoutInspector
        ?.currentClient
        ?.stats
        ?.updateRecompositionStats(model.maxRecomposition, model.maxHighlight)
    }
    // Make an explicit update of the toolbar now (the tree expand actions may have been
    // enabled/disabled)
    invokeLater { toolWindowCallback?.updateActions() }
  }

  /**
   * Add the TreeViewNode(s) of [window] root to the corresponding [windowRoots] such that the
   * TreeViewNode tree reflect the current system filter setting. Since the [window] root may be
   * currently hidden there may be multiple roots. Return these roots.
   */
  private fun addToRoot(window: AndroidWindow): List<TreeViewNode> {
    temp.children.clear()
    readAccess { updateHierarchy(window, temp) }
    temp.children.forEach { it.parent = root }
    val windowNodes = windowRoots.getOrPut(window.id) { mutableListOf() }
    windowNodes.clear()
    windowNodes.addAll(temp.children)
    temp.children.clear()

    return windowNodes
  }

  private fun rebuildRoot() {
    root.children.clear()
    inspectorModel?.windows?.keys?.flatMapTo(root.children) { id -> windowRoots[id] ?: emptyList() }
  }

  fun refresh() {
    windowRoots.clear()
    inspectorModel?.windows?.values?.forEach { addToRoot(it) }
    rebuildRoot()
    componentTreeModel.hierarchyChanged(root)
  }

  /**
   * Update the [TreeViewNode] hierarchy of the [window] and add the window to the specified [root].
   *
   * Walk the [ViewNode] of the [window] in pre-order (NLR).
   * - Skip hidden nodes by:
   *     - Not adding the skipped node to the parent [TreeViewNode]
   *     - Set the [TreeViewNode] parent to the first non skipped parent
   *     - Children of skipped nodes will find the first non skipped parent by using the parent
   *       field of its parents [TreeViewNode]
   * - Collapsing compose nodes to a callstack is handled by:
   *     - Set the [TreeViewNode] parent to the first parent that has siblings.
   *
   * There is an exception to the above rules. If we are collapsing compose nodes and a node with
   * multiple children is a child in a single call chain and is also a hidden node, we should NOT
   * add it's children to the top node in the call tree, but rather find the first parent that is
   * not hidden.
   */
  private fun ViewNode.ReadAccess.updateHierarchy(window: AndroidWindow, root: TreeViewNode) {
    val treeSettings = layoutInspector?.treeSettings ?: return
    val start = window.root
    start.treeNode.parent = root
    start.treeNode.children.clear()
    if (start.isInComponentTree(treeSettings)) {
      root.children.add(start.treeNode)
    }
    val pending = mutableListOf<ViewNode>().apply { addAll(start.children.asReversed()) }
    while (pending.isNotEmpty()) {
      val node = pending.removeLast()
      var parent = node.parent ?: error("The node cannot be the root")
      val skipNodeParent =
        parent.isSingleCall(treeSettings) || !parent.isInComponentTree(treeSettings)
      var parentTreeNode = (if (skipNodeParent) parent.treeNode.parent else parent.treeNode) ?: root
      if (
        parent.isSingleCall(treeSettings) &&
          !node.isInComponentTree(treeSettings) &&
          node.children.size > 1
      ) {
        while (!parent.isInComponentTree(treeSettings) && parent !== start) {
          parent = parent.parent ?: error("The node cannot be the root")
        }
        parentTreeNode = parent.treeNode
      }
      node.treeNode.parent = parentTreeNode
      node.treeNode.children.clear()
      if (node.isInComponentTree(treeSettings)) {
        parentTreeNode.children.add(node.treeNode)
      }
      pending.addAll(node.children.asReversed())
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(oldView: ViewNode?, newView: ViewNode?, origin: SelectionOrigin) {
    componentTreeSelectionModel.currentSelection = listOfNotNull(newView?.treeNode)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleConnectionChange(client: InspectorClient?) {
    updateRecompositionColumnVisibility()
  }

  private class TreeAction(private val action: (ActionEvent) -> Unit) : AbstractAction() {
    override fun actionPerformed(event: ActionEvent) = action(event)
  }

  @VisibleForTesting
  inner class InspectorViewNodeType : ViewNodeType<TreeViewNode>() {
    var model: InspectorModel? = null
    override val clazz = TreeViewNode::class.java

    override fun tagNameOf(node: TreeViewNode) = node.view.qualifiedName

    override fun idOf(node: TreeViewNode) = node.view.viewId?.name

    override fun textValueOf(node: TreeViewNode) =
      if (node.view.isInlined) "(inline)" else node.view.textValue.nullize()?.let { "\"$it\"" }

    override fun iconOf(node: TreeViewNode): Icon =
      IconProvider.getIconForView(node.view.qualifiedName, node.view is ComposeViewNode)

    override fun parentOf(node: TreeViewNode): TreeViewNode? = node.parent

    override fun childrenOf(node: TreeViewNode): List<TreeViewNode> = node.children

    override fun isEnabled(node: TreeViewNode) = model?.isVisible(node.view) == true

    override fun isDeEmphasized(node: TreeViewNode): Boolean = !matchNode(node)
  }
}
