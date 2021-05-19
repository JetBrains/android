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

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import java.util.Collections
import javax.swing.Icon
import javax.swing.JComponent

const val GOTO_DEFINITION_ACTION_KEY = "gotoDefinition"

class LayoutInspectorTreePanel : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel

  @VisibleForTesting
  val componentTreeSelectionModel: ComponentTreeSelectionModel

  init {
    val builder = ComponentTreeBuilder()
      .withHiddenRoot()
      .withNodeType(InspectorViewNodeType())
      .withContextMenu(::showPopup)
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withHorizontalScrollBar()
      .withComponentName("inspectorComponentTree")

    ActionManager.getInstance()?.getAction(IdeActions.ACTION_GOTO_DECLARATION)?.shortcutSet?.shortcuts
        ?.filterIsInstance<KeyboardShortcut>()
        ?.filter { it.secondKeyStroke == null }
        ?.forEach { builder.withKeyActionKey(GOTO_DEFINITION_ACTION_KEY, it.firstKeyStroke) { gotoDefinition() } }
    val (tree, model, selectionModel) = builder.build()
    componentTree = tree
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    selectionModel.addSelectionListener {
      layoutInspector?.layoutInspectorModel?.apply {
        selection = it.firstOrNull() as? ViewNode
        stats.selectionMadeFromComponentTree()
      }
    }
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add { _, _, _ -> componentTree.repaint() }
  }

  private fun showPopup(component: JComponent, x: Int, y: Int) {
    val node = componentTreeSelectionModel.currentSelection.singleOrNull() as ViewNode?
    if (node != null) {
      layoutInspector?.let { showViewContextMenu(listOf(node), it.layoutInspectorModel, component, x, y) }
    }
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.layoutInspectorModel?.modificationListeners?.remove(this::modelModified)
    layoutInspector = toolContext
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add(this::modelModified)
    componentTreeModel.treeRoot = layoutInspector?.layoutInspectorModel?.root
    toolContext?.layoutInspectorModel?.selectionListeners?.add(this::selectionChanged)
  }

  override fun getComponent() = componentTree

  override fun dispose() {
  }

  private fun gotoDefinition() {
    val model = layoutInspector?.layoutInspectorModel ?: return
    GotoDeclarationAction.findNavigatable(model)?.navigate(true)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelModified(old: AndroidWindow?, new: AndroidWindow?, structuralChange: Boolean) {
    if (structuralChange) {
      componentTreeModel.hierarchyChanged(new?.root)
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(oldView: ViewNode?, newView: ViewNode?) {
    if (newView == null) {
      componentTreeSelectionModel.currentSelection = emptyList()
    }
    else {
      componentTreeSelectionModel.currentSelection = Collections.singletonList(newView)
    }
  }

  private class InspectorViewNodeType : ViewNodeType<ViewNode>() {
    override val clazz = ViewNode::class.java

    override fun tagNameOf(node: ViewNode) = node.qualifiedName

    override fun idOf(node: ViewNode) = node.viewId?.name

    override fun textValueOf(node: ViewNode) = node.textValue

    override fun iconOf(node: ViewNode): Icon =
      AndroidDomElementDescriptorProvider.getIconForViewTag(node.unqualifiedName) ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun parentOf(node: ViewNode) = node.parent

    override fun childrenOf(node: ViewNode) = node.children

    override fun isEnabled(node: ViewNode) = node.visible
  }
}
