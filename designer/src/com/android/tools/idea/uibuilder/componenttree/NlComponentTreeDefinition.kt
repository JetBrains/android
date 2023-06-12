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
package com.android.tools.idea.uibuilder.componenttree

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.IconColumn
import com.android.tools.componenttree.api.NodeType
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.editor.showPopup
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.DnDTransferComponent
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentReference
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.model.ensureLiveId
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.isGroup
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import com.android.tools.idea.uibuilder.model.viewHandler
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.structure.BackNavigationComponent
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.android.tools.idea.uibuilder.structure.findComponent
import com.android.tools.idea.uibuilder.structure.getVisibilityFromParents
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.lint.detector.api.stripIdPrefix
import com.google.common.collect.ImmutableList
import com.google.common.html.HtmlEscapers
import com.intellij.ide.DeleteProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import com.intellij.util.text.nullize
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.Image
import java.awt.Rectangle
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.tree.TreeCellRenderer

/**
 * The delay used to minimize updates
 */
private const val UPDATE_DELAY_MILLISECONDS = 250

/**
 * When dragging an item do not display a drag image
 */
private val EMPTY_IMAGE = ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB)

/**
 * [ToolWindowDefinition] for the Nele component tree using the ComponentTreeBuilder.
 */
class NlComponentTreeDefinition(
  project: Project,
  side: Side,
  split: Split,
  autoHide: AutoHide,
  isPassThroughQueue: Boolean = false
) : ToolWindowDefinition<DesignSurface<*>>(
  "Component Tree",
  StudioIcons.Shell.ToolWindows.COMPONENT_TREE,
  "COMPONENT_TREE", side, split,
  autoHide,
  { disposable -> ComponentTreePanel(project, isPassThroughQueue, disposable) }
)

/**
 * A panel holding the component tree.
 *
 * The tree is implemented as a Java TreeTable with 3 columns.
 * - 1st column holds the tree of components and component references from [NlModel].
 * - 2nd column will show an issue icon.
 * - 3rd column shows current visibility setting for the component.
 */
private class ComponentTreePanel(
  val project: Project,
  isPassThroughQueue: Boolean,
  parentDisposable: Disposable
) : AdtSecondaryPanel(BorderLayout()), ToolContent<DesignSurface<*>> {
  private var surface: NlDesignSurface? = null
  private var model: NlModel? = null
  private var facet: AndroidFacet? = null
  private val backNavigation = BackNavigationComponent()
  private val componentTree: ComponentTreeBuildResult
  private val modelSelectionListener: SelectionListener
  private val modelChangeListener: ModelListener
  private val selectionIsUpdating = AtomicBoolean(false)
  private var wasDisposed = false
  private val updateQueue =
    MergingUpdateQueue("android.layout.structure-pane", UPDATE_DELAY_MILLISECONDS, true, null, this, null, SWING_THREAD)

  init {
    componentTree = ComponentTreeBuilder()
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withNodeType(NlComponentNodeType())
      .withNodeType(NlComponentReferenceNodeType())
      .withAutoScroll()
      .withDataProvider { dataId -> getData(dataId) }
      .withDnD(::mergeItems, deleteOriginOfInternalMove = false)
      .withBadgeSupport(IssueBadgeColumn())
      .withBadgeSupport(VisibilityBadgeColumn { updateBadges() })
      .withDoubleClick { activateComponent(it) }
      .withContextMenu { item, _, x, y -> showContextMenu(item, x, y) }
      .withMultipleSelection()
      .withExpandAllOnRootChange()
      .build()
    componentTree.selectionModel.addSelectionListener {
      selectionIsUpdating.setWhile { setSurfaceSelection(it) }
    }
    modelSelectionListener = SelectionListener { _, selection ->
      if (!wasDisposed && !selectionIsUpdating.get()) {
        componentTree.selectionModel.currentSelection = selection
      }
    }
    modelChangeListener = object : ModelListener {
      override fun modelChanged(model: NlModel) {
        update()
      }

      override fun modelDerivedDataChanged(model: NlModel) {
        update()
      }

      private fun update() {
        updateQueue.queue(
          Update.create("updateComponentStructure") { fireHierarchyChanged(model) }
        )
      }
    }
    add(backNavigation, BorderLayout.NORTH)
    add(componentTree.component, BorderLayout.CENTER)
    Disposer.register(parentDisposable, this)
    updateQueue.isPassThrough = isPassThroughQueue
  }

  override fun getComponent(): JComponent = this

  override fun getFocusedComponent(): JComponent = componentTree.focusComponent

  override fun dispose() {
    wasDisposed = true
  }

  override fun setToolContext(context: DesignSurface<*>?) {
    surface?.selectionModel?.removeListener(modelSelectionListener)
    surface = context as? NlDesignSurface
    surface?.selectionModel?.addListener(modelSelectionListener)
    backNavigation.designSurface = surface
    model?.removeListener(modelChangeListener)
    model = surface?.model
    model?.addListener(modelChangeListener)
    facet = model?.facet
    componentTree.model.treeRoot = model?.components?.firstOrNull()
    invokeLater { TreeUtil.expandAll(componentTree.tree) }
  }

  private fun setSurfaceSelection(selection: List<Any>) {
    val references = selection.filterIsInstance(NlComponentReference::class.java)
    val highlighted = references.mapNotNull { model?.find(it.id) }
    val selected = selection.filterIsInstance(NlComponent::class.java)
    surface?.selectionModel?.setHighlightSelection(highlighted, selected)
    surface?.repaint()
  }

  private fun fireHierarchyChanged(model: NlModel?) {
    componentTree.model.treeRoot = model?.components?.firstOrNull()
  }

  private fun activateComponent(component: Any) = when (component) {
    is NlComponent -> component.viewHandler?.onActivateInComponentTree(component)
    is NlComponentReference -> findComponent(component.id, model)?.let { surface?.selectionModel?.setSelection(listOf(it)) }
    else -> error("unexpected node type: ${component.javaClass.name}")
  }

  private fun showContextMenu(component: Any, x: Int, y: Int) = when (component) {
    is NlComponent -> showContextMenuForComponent(component, x, y)
    is NlComponentReference -> showContextMenuForReference(x, y)
    else -> error("unexpected node type: ${component.javaClass.name}")
  }

  private fun showContextMenuForComponent(component: NlComponent, x: Int, y: Int) {
    surface?.actionManager?.getPopupMenuActions(component)?.let {
      showPopup(componentTree.focusComponent, x, y, it, ActionPlaces.EDITOR_POPUP)
    }
  }

  private fun showContextMenuForReference(x: Int, y: Int) {
    // Offer an delete action of the selected component references:
    ActionManager.getInstance().createActionPopupMenu(
      ActionPlaces.EDITOR_POPUP,
      DefaultActionGroup(ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE))
    ).component.show(componentTree.focusComponent, x, y)
  }

  private fun updateBadges() {
    componentTree.model.columnDataChanged()
  }

  private fun mergeItems(item1: Transferable, item2: Transferable): Transferable {
    val transferable1 = item1 as? ItemTransferable ?: return item1
    val transferable2 = item2 as? ItemTransferable ?: return item1
    return transferable1.merge(transferable2)
  }

  /**
   * A mechanism for avoiding infinite recursion while updating the selection between the tree and the selection model of the surface.
   */
  private fun AtomicBoolean.setWhile(operation: () -> Unit) {
    set(true)
    try {
      operation()
    }
    finally {
      set(false)
    }
  }

  /**
   * A [NlComponentReference] delete provider used when only references are selected in the tree.
   */
  private val referenceDeleteProvider = object : DeleteProvider {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun canDeleteElement(dataContext: DataContext): Boolean = true

    override fun deleteElement(dataContext: DataContext) {
      val references  = componentTree.selectionModel.currentSelection.filterIsInstance(NlComponentReference::class.java)
      references.forEach {
        it.parent.viewGroupHandler?.removeReference(it.parent, it.id)
      }
    }
  }

  /**
   * Provide a `DELETE_ELEMENT_PROVIDER` when only [NlComponentReference]s are selected.
   *
   * Otherwise simply delegate to whatever the surface is offering.
   */
  private fun getData(dataId: String): Any? {
    val referencesOnly  = componentTree.selectionModel.currentSelection.all { it is NlComponentReference }
    if (referencesOnly && PlatformDataKeys.DELETE_ELEMENT_PROVIDER.`is`(dataId)) {
      // Provide a way to delete a reference from a helper
      return referenceDeleteProvider
    }
    return surface?.getData(dataId)
  }

  /**
   * The [NodeType] used for [NlComponent]s in the [NlModel] of the design surface.
   */
  private inner class NlComponentNodeType : ViewNodeType<NlComponent>() {
    override val clazz: Class<NlComponent> = NlComponent::class.java

    override fun idOf(node: NlComponent): String? = stripIdPrefix(node.id).ifEmpty { null }

    override fun tagNameOf(node: NlComponent): String = node.viewHandler?.getTitle(node)?.nullize() ?: node.tagName

    override fun textValueOf(node: NlComponent): String? = node.viewHandler?.getTitleAttributes(node)

    override fun iconOf(node: NlComponent): Icon = node.viewHandler?.getIcon(node) ?: loadBuiltinIcon(getSimpleTagName(node))

    override fun parentOf(node: NlComponent): NlComponent? = node.parent

    override fun childrenOf(node: NlComponent): List<*> = node.viewGroupHandler?.getComponentTreeChildren(node) ?: node.children

    override fun toSearchString(node: NlComponent): String = "${idOf(node)} - ${tagNameOf(node)} - ${textValueOf(node)}"

    /** Display items with a strikeout if the effective visibility is [Visibility.GONE] */
    override fun isEnabled(node: NlComponent): Boolean =
      getVisibilityFromParents(node) != Visibility.GONE

    /** Display items with a weaker font color if the effective visibility is [Visibility.GONE] or [Visibility.INVISIBLE] */
    override fun isDeEmphasized(node: NlComponent): Boolean = when(getVisibilityFromParents(node)) {
      Visibility.GONE,
      Visibility.INVISIBLE -> true
      else -> false
    }

    override fun canInsert(node: NlComponent, data: Transferable): Boolean {
      val model = model ?: return false
      if (!data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) return false
      val item = DnDTransferItem.getTransferItem(data, true) ?: return false
      val components = model.createComponents(item, InsertType.COPY)
      val refs = item.references
      if (refs.isEmpty() && components.isEmpty()) {
        // Do not allow both components and references to be dropped.
        return false
      }
      // Allow:
      // - components to be dragged into a group component
      // - references or components to be dragged into a reference holder component (components will be saved as references)
      return (components.isNotEmpty() && node.isGroup() && model.canAddComponents(components, node, null)) ||
             node.viewGroupHandler?.holdsReferences() == true
    }

    override fun insert(node: NlComponent, data: Transferable, before: Any?, isMove: Boolean, draggedFromTree: List<Any>): Boolean {
      val model = model ?: return false
      if (!data.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) return false
      val item = DnDTransferItem.getTransferItem(data, true) ?: return false
      val insertType = if (isMove && draggedFromTree.isNotEmpty()) InsertType.MOVE else InsertType.COPY
      val components =
        if (insertType == InsertType.MOVE) draggedFromTree.filterIsInstance<NlComponent>()
        else model.createComponents(item, insertType)
      val refs = item.references
      when {
        node.isGroup() && refs.isEmpty() && model.canAddComponents(components, node, before as? NlComponent) ->
          model.addComponents(components, node, before as? NlComponent, insertType, null)
        node.viewGroupHandler?.holdsReferences() == true ->
          updateReferences(node, components, refs, before as? NlComponentReference, insertType)
        else -> return false
      }
      // Update immediately:
      fireHierarchyChanged(model)
      return true
    }

    private fun updateReferences(
      node: NlComponent,
      components: List<NlComponent>,
      references: List<String>,
      before: NlComponentReference?,
      insertType: InsertType
    ) {
      // First add the reference to the constraint helpers reference list:
      val ids = references + components.map { it.ensureLiveId() }
      node.viewGroupHandler?.addReferences(node, ids, before?.id)

      // Then add/move the referenced component to the corresponding constraint layout:
      val layout = node.parent ?: return
      val beforeComponent = before?.let { model?.find(it.id) }
      model?.addComponents(components, layout, beforeComponent, insertType, null)
    }

    override fun delete(node: NlComponent) {
      model?.delete(listOf(node))
    }

    override fun createTransferable(node: NlComponent): Transferable? {
      val text = node.tag?.text ?: return null
      val component = DnDTransferComponent(node.tagName, text, node.w, node.h)
      return ItemTransferable(DnDTransferItem(model?.id ?: 0, ImmutableList.of(component)))
    }

    override fun createDragImage(node: NlComponent): Image = EMPTY_IMAGE

    private fun getSimpleTagName(node: NlComponent): String =
      node.tagName.substringAfterLast('.')

    private fun loadBuiltinIcon(simpleTagName: String): Icon =
      AndroidDomElementDescriptorProvider.getIconForViewTag(simpleTagName) ?: StudioIcons.LayoutEditor.Palette.VIEW
  }

  /**
   * A [NodeType] for [NlComponentReference]s from [NlComponent]s in the [NlModel] of the design surface.
   */
  private inner class NlComponentReferenceNodeType : NodeType<NlComponentReference> {
    override val clazz: Class<NlComponentReference> = NlComponentReference::class.java

    override fun parentOf(node: NlComponentReference): NlComponent = node.parent

    override fun childrenOf(node: NlComponentReference): List<*> = emptyList<Nothing>()

    override fun toSearchString(node: NlComponentReference): String = node.id

    override fun createTransferable(node: NlComponentReference): Transferable =
      ItemTransferable(DnDTransferItem(model?.id ?: 0, ImmutableList.of(), ImmutableList.of(node.id)))

    private val label = JBLabel()
    private val renderer = TreeCellRenderer { _, value, selected, _, _, _, hasFocus ->
      val reference = value as? NlComponentReference
      label.text = reference?.id
      label.foreground = UIUtil.getTreeForeground(selected, hasFocus)
      label.background = UIUtil.getTreeBackground(selected, hasFocus)
      label
    }

    override fun createRenderer(): TreeCellRenderer = renderer
  }

  /**
   * A BadgeItem for displaying issue icons in the 2nd column of the component TreeTable.
   */
  private inner class IssueBadgeColumn : IconColumn("Issues") {

    override fun getIcon(item: Any): Icon? = issueOf(item)?.let { IssueModel.getIssueIcon(it.severity, false) }

    override fun getTooltipText(item: Any): String {
      val issue = issueOf(item) ?: return ""
      return "<html>" + HtmlEscapers.htmlEscaper().escape(issue.summary) + "<br>Click the badge for detail.</html>"
    }

    override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {
      if (item !is NlComponent) return
      val currentSurface = surface ?: return
      IssuePanelService.getInstance(project).showIssueForComponent(currentSurface, true, item, true)
    }

    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {}

    private fun issueOf(item: Any?): Issue? {
      val component = item as? NlComponent ?: return null
      return surface?.issueModel?.getHighestSeverityIssue(component)
    }
  }
}
