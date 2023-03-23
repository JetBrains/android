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
package com.android.tools.idea.gradle.structure.configurables.dependencies.module

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.JarDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.ModuleDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.SingleDeclaredLibraryDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.DependencySelection
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener
import com.android.tools.idea.gradle.structure.configurables.ui.createMergingUpdateQueue
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDependenciesPanel
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.DeclaredDependenciesTableView
import com.android.tools.idea.gradle.structure.configurables.ui.enqueueTagged
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.google.common.collect.Lists
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.navigation.Place
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent

const val MODULE_DEPENDENCIES_PLACE_NAME = "module.dependencies.place"

/**
 * Panel that displays the table of "editable" dependencies.
 */
internal class DeclaredDependenciesPanel(
  val module: PsModule, context: PsContext
) : AbstractDependenciesPanel("Declared Dependencies", context, module), DependencySelection {
  private val updateQueue = createMergingUpdateQueue("declaredDependenciesUpdates", context, this)
  private val dependenciesTableModel: DeclaredDependenciesTableModel
  private val dependenciesTable: DeclaredDependenciesTableView<PsBaseDependency>
  private val placeName: String

  private val eventDispatcher = SelectionChangeEventDispatcher<PsBaseDependency>()

  init {
    context.analyzerDaemon.onIssuesChange(this) {
      updateQueue.enqueueTagged(DeclaredDependenciesPanel::class.java) {
        updateIssues(selection)
      }
    }

    placeName = MODULE_DEPENDENCIES_PLACE_NAME

    contentsPanel.add(createActionsPanel(), BorderLayout.NORTH)
    initializeDependencyDetails()

    setIssuesViewer(IssuesViewer(context, SingleModuleIssuesRenderer(context)))

    dependenciesTableModel = DeclaredDependenciesTableModel(
      module, context)
    dependenciesTable = DeclaredDependenciesTableView(dependenciesTableModel, context)

    module.addDependencyChangedListener(this) { event ->
      when (event) {
        is PsModule.DependencyAddedEvent -> {
          dependenciesTableModel.reset()
          dependenciesTable.clearSelection()
          dependenciesTable.selection = listOf(event.dependency.value)
        }
        is PsModule.DependencyModifiedEvent -> {
          updateDetails(event.dependency.value)
          dependenciesTableModel.reset(event.dependency.value)
        }
        else -> {
          val selectedKeys = dependenciesTable.selection.map { it.toText() to it.joinedConfigurationNames }.toSet()
          dependenciesTableModel.reset()
          val newSelection = dependenciesTableModel.items.filter { selectedKeys.contains(it.toText() to it.joinedConfigurationNames) }
          if (newSelection != dependenciesTable.selection) dependenciesTable.selection = newSelection
        }
      }
    }

    dependenciesTable.selectionModel.addListSelectionListener {
      if (!it.valueIsAdjusting) {
        updateDetailsAndIssues()
        notifySelectionChanged()
      }
    }
    dependenciesTable.selectFirstRow()

    val scrollPane = createScrollPane(dependenciesTable)
    scrollPane.border = JBUI.Borders.empty()
    contentsPanel.add(scrollPane, BorderLayout.CENTER)

    updateTableColumnSizes()
  }

  private fun initializeDependencyDetails() {
    addDetails(SingleDeclaredLibraryDependencyDetails(context))
    addDetails(JarDependencyDetails(context, true))
    addDetails(ModuleDependencyDetails(context, true))
  }

  override fun getPreferredFocusedComponent(): JComponent = dependenciesTable

  public override fun getPlaceName(): String = placeName

  override fun getExtraToolbarActions(focusComponent: JComponent): List<AnAction> {
    val actions = Lists.newArrayList<AnAction>()
    actions.add(
        RemoveDependencyAction()
            .apply {
              registerCustomShortcutSet(CommonShortcuts.getDelete(), focusComponent)
            })
    return actions
  }

  fun updateTableColumnSizes() {
    dependenciesTable.updateColumnSizes()
  }

  override fun dispose() {
    Disposer.dispose(dependenciesTable)
  }

  fun add(listener: SelectionChangeListener<PsBaseDependency>) {
    eventDispatcher.addListener(listener, this)
  }

  override fun getSelection(): PsBaseDependency? = dependenciesTable.selectionIfSingle

  override fun setSelection(selection: Collection<PsBaseDependency>?): ActionCallback {
    if (selection.isNullOrEmpty()) {
      dependenciesTable.clearSelection()
    }
    else {
      dependenciesTable.setSelection(selection.toSet())
    }
    updateDetailsAndIssues()
    history?.pushQueryPlace()
    return ActionCallback.DONE
  }

  private fun updateDetailsAndIssues() {
    val selected = selection
    super.updateDetails(selected)
    updateIssues(selected)
  }

  private fun notifySelectionChanged() {
    val selected = selection
    if (selected != null) {
      eventDispatcher.selectionChanged(selected)
    }
  }

  private fun updateIssues(selected: PsBaseDependency?) {
    displayIssues(selected?.let { context.analyzerDaemon.issues.findIssues(selected.path, null) }.orEmpty(), selected?.path)
  }

  fun selectDependency(dependency: String?) {
    if (isEmpty(dependency)) {
      dependenciesTable.clearSelection()
      return
    }
    doSelectDependency(dependency!!)
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    if (place != null) {
      val path = place.getPath(placeName)
      if (path is String) {
        val pathText = path as String?
        if (!pathText!!.isEmpty()) {
          dependenciesTable.requestFocusInWindow()
          doSelectDependency(pathText)
        }
      }
    }
    return ActionCallback.DONE
  }

  private fun doSelectDependency(toSelect: String) {
    dependenciesTable.selectDependency(toSelect)
  }

  private inner class RemoveDependencyAction internal constructor() :
      DumbAwareAction("Remove Dependency...", "", IconUtil.getRemoveIcon()) {

    override fun update(e: AnActionEvent) {
      val details = currentDependencyDetails
      e.presentation.isEnabled = details != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      val dependency = selection
      if (dependency != null) {
        if (Messages.showYesNoDialog(
            e.project,
            "Remove dependency '${dependency.joinedConfigurationNames} ${dependency.name}'?",
            "Remove Dependency",
            Messages.getQuestionIcon()
          ) == Messages.YES) {
          module.removeDependency(dependency as PsDeclaredDependency)
          dependenciesTable.selectFirstRow()
        }
      }
    }
  }
}
