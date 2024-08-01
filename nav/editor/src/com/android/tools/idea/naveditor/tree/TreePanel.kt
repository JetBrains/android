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
package com.android.tools.idea.naveditor.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.common.editor.showPopup
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.application.ApplicationManager
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent

class TreePanel : ToolContent<DesignSurface<*>> {
  private var designSurface: DesignSurface<*>? = null
  private val componentTree: JComponent
  @VisibleForTesting
  val componentTreeModel: ComponentTreeModel
  @VisibleForTesting
  val componentTreeSelectionModel: ComponentTreeSelectionModel
  private val contextSelectionListener = SelectionListener { _, _ -> contextSelectionChanged() }
  private val modelListener = NlModelListener()

  init {
    val builder = ComponentTreeBuilder()
      .withNodeType(NlComponentNodeType())
      .withMultipleSelection()
      .withContextMenu { _, _, x: Int, y: Int -> showContextMenu(x, y) }
      .withDoubleClick { activateComponent() }
      .withExpandableRoot()
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withComponentName("navComponentTree")

    val result = builder.build()
    componentTree = result.component
    componentTreeModel = result.model
    componentTreeSelectionModel = result.selectionModel
    componentTreeSelectionModel.addSelectionListener { updateSelection() }
  }

  @VisibleForTesting
  fun updateSelection() {
    val surface = designSurface ?: return
    val list = componentTreeSelectionModel.currentSelection.filterIsInstance<NlComponent>()
    val oldRootNavigation = (surface as? NavDesignSurface)?.currentNavigation

    surface.selectionModel.setSelection(list)

    // Don't scroll if we've either selected the root navigation, or changed the root navigation
    val newRootNavigation = (surface as? NavDesignSurface)?.currentNavigation
    if (oldRootNavigation == newRootNavigation && !list.contains(newRootNavigation)) {
      surface.scrollToCenter(list.filter { component -> component.isDestination })
    }

    surface.needsRepaint()
  }

  override fun setToolContext(toolContext: DesignSurface<*>?) {
    designSurface?.let {
      it.selectionModel?.removeListener(contextSelectionListener)
      it.models.firstOrNull()?.removeListener(modelListener)
      DataManager.removeDataProvider(componentTree)
    }

    designSurface = toolContext

    designSurface?.let {
      it.selectionModel?.addListener(contextSelectionListener)
      it.models.firstOrNull()?.let { model ->
        model.addListener(modelListener)
        update(model)
      }
      DataManager.registerDataProvider(componentTree, EdtNoGetDataProvider { sink -> DataSink.uiDataSnapshot(sink, it) })
    }
  }

  private fun contextSelectionChanged() {
    componentTreeSelectionModel.currentSelection = designSurface?.selectionModel?.selection ?: emptyList()
  }

  override fun getComponent() = componentTree

  private fun showContextMenu(x: Int, y: Int) {
    val node = componentTreeSelectionModel.currentSelection.singleOrNull() as NlComponent? ?: return
    val actions = designSurface?.actionManager?.getPopupMenuActions(node) ?: return
    // TODO (b/151315668): extract the hardcoded value "NavEditor".
    showPopup(designSurface, componentTree, x, y, actions, "NavEditor")
  }

  private fun activateComponent() {
    val node = componentTreeSelectionModel.currentSelection.singleOrNull() as NlComponent? ?: return
    designSurface?.notifyComponentActivate(node)
  }

  override fun dispose() {
    setToolContext(null)
  }

  private fun update(model: NlModel) {
    componentTreeModel.treeRoot = model.treeReader.components.firstOrNull()
  }

  class NlComponentNodeType : ViewNodeType<NlComponent>() {
    override val clazz = NlComponent::class.java

    override fun tagNameOf(node: NlComponent) = node.tagName

    override fun idOf(node: NlComponent) = node.id

    override fun textValueOf(node: NlComponent): String? = null

    override fun iconOf(node: NlComponent): Icon = node.mixin?.icon ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun isEnabled(node: NlComponent) = true

    override fun isDeEmphasized(node: NlComponent) = false

    override fun parentOf(node: NlComponent) = node.parent

    override fun childrenOf(node: NlComponent) = node.children.filter { it.isDestination || it.isAction }
  }

  private inner class NlModelListener : ModelListener {
    override fun modelDerivedDataChanged(model: NlModel) = updateLater(model)
    override fun modelLiveUpdate(model: NlModel, animate: Boolean) = updateLater(model)

    private fun updateLater(model: NlModel) = ApplicationManager.getApplication().invokeLater { update(model) }
  }
}