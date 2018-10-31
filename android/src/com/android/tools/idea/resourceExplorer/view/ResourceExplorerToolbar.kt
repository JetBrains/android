/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.viewmodel.ResourceExplorerToolbarViewModel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Toolbar displayed at the top of the resource explorer which allows users
 * to change the module and add resources.
 */
class ResourceExplorerToolbar(
  private val toolbarViewModel: ResourceExplorerToolbarViewModel)
  : JPanel(BorderLayout()), DataProvider by toolbarViewModel {

  private val moduleSelectionAction = ModuleSelectionAction(toolbarViewModel)

  init {
    add(createLeftToolbar().component, BorderLayout.WEST)
    border = JBUI.Borders.merge(JBUI.Borders.empty(4, 2), JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), true)
  }

  private fun createLeftToolbar() = ActionManager.getInstance().createActionToolbar(
    "resourceExplorer",
    DefaultActionGroup(AddAction(toolbarViewModel), Separator(), moduleSelectionAction),
    true)
}

/**
 * Dropdown to select the module from which resources are displayed.
 */
private class ModuleSelectionAction(val viewModel: ResourceExplorerToolbarViewModel) : ComboBoxAction(), DumbAware {

  init {
    this.templatePresentation.text = viewModel.currentModuleName
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = viewModel.currentModuleName
  }

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup(viewModel.getSelectModuleActions())
}

/**
 * Button to add new resources
 */
private class AddAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel) : AnAction(), DumbAware {
  init {
    val presentation = templatePresentation
    presentation.icon = StudioIcons.Common.ADD
  }

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

  private fun createAddPopupGroup() = object : DefaultActionGroup() {
    init {
      addAll(viewModel.addActions)
      val importersActions = viewModel.getImportersActions()
      if (importersActions.isNotEmpty()) {
        add(Separator())
        addAll(importersActions)
      }
    }

    override fun isDumbAware() = true
  }
}
