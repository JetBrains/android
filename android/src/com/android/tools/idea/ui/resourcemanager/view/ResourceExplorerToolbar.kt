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
package com.android.tools.idea.ui.resourcemanager.view

import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.viewmodel.ResourceExplorerToolbarViewModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

private const val SEARCH_FIELD_LABEL = "Search resources by name"
private const val ADD_BUTTON_LABEL = "Add resources to the module"
private const val FILTERS_BUTTON_LABEL = "Filter displayed resources"

/**
 * Toolbar displayed at the top of the resource explorer which allows users
 * to change the module and add resources.
 */
class ResourceExplorerToolbar(
  private val toolbarViewModel: ResourceExplorerToolbarViewModel)
  : JPanel(BorderLayout()), DataProvider by toolbarViewModel {

  private val moduleSelectionAction = ModuleSelectionAction(toolbarViewModel)

  init {
    add(createLeftToolbar().component)
    add(createRightToolbar().component, BorderLayout.EAST)
    border = JBUI.Borders.merge(JBUI.Borders.empty(4, 2), JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), true)
  }

  private fun createLeftToolbar() = ActionManager.getInstance().createActionToolbar(
    "resourceExplorer",
    DefaultActionGroup(
      AddAction(toolbarViewModel),
      Separator(),
      moduleSelectionAction,
      SearchAction(toolbarViewModel)
    ), true).apply { layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY }


  private fun createRightToolbar() = ActionManager.getInstance().createActionToolbar(
    "resourceExplorer", DefaultActionGroup(FilterAction(toolbarViewModel)), true)
}

/**
 * Dropdown to select the module from which resources are displayed.
 */
private class ModuleSelectionAction(val viewModel: ResourceExplorerToolbarViewModel) : ComboBoxAction(), DumbAware {

  init {
    this.templatePresentation.text = viewModel.getSelectedModuleText()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = viewModel.getSelectedModuleText()
  }

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup(viewModel.getSelectModuleActions())
}

/**
 * Button to add new resources
 */
abstract class PopupAction internal constructor(val icon: Icon?, description: String)
  : AnAction(description, description, icon), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    var x = 0
    var y = 0
    val inputEvent = e.inputEvent
    if (inputEvent is MouseEvent) {
      x = 0
      y = inputEvent.component.height
    }

    showAddPopup(inputEvent.component, x, y)
  }

  private fun showAddPopup(component: Component, x: Int, y: Int) {
    ActionManager.getInstance()
      .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, createAddPopupGroup())
      .component.show(component, x, y)
  }

  protected abstract fun createAddPopupGroup(): ActionGroup
}

private class AddAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : PopupAction(AllIcons.General.Add, ADD_BUTTON_LABEL) {
  override fun createAddPopupGroup() = DefaultActionGroup().apply {
    addAll(viewModel.addActions)
    val importersActions = viewModel.getImportersActions()
    if (importersActions.isNotEmpty()) {
      add(Separator())
      addAll(importersActions)
    }
  }

}

class FilterAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : PopupAction(AllIcons.General.Filter, FILTERS_BUTTON_LABEL) {
  override fun createAddPopupGroup() = DefaultActionGroup().apply {
    add(ShowDependenciesAction(viewModel))
  }
}

private class ShowDependenciesAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : ToggleAction("Show libraries") {
  override fun isSelected(e: AnActionEvent) = viewModel.isShowDependencies
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    viewModel.isShowDependencies = state
    ResourceManagerTracking.logShowLibrariesToggle(state)
  }
}

private class SearchAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : DumbAwareAction(), CustomComponentAction {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.isFromActionToolbar
  }

  val searchField = SearchTextField(true).apply {
    isFocusable = true
    toolTipText = SEARCH_FIELD_LABEL
    accessibleContext.accessibleName = SEARCH_FIELD_LABEL
    textEditor.columns = 10
    textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        viewModel.searchString = e.document.getText(0, e.document.length)
      }
    })
  }

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun createCustomComponent(presentation: Presentation): JComponent = searchField
}
