/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.SingleLibraryDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDependenciesPanel
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.DeclaredDependenciesTableView
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.google.common.collect.Lists
import com.intellij.icons.AllIcons
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Panel that displays the table of "editable" dependencies.
 */
internal class DeclaredDependenciesPanel(
  val module: PsAndroidModule, context: PsContext
) : AbstractDependenciesPanel("Declared Dependencies", context, module), DependencySelection {

  private val dependenciesTableModel: DeclaredDependenciesTableModel
  private val dependenciesTable: DeclaredDependenciesTableView<PsAndroidDependency>
  private val placeName: String

  private val eventDispatcher = SelectionChangeEventDispatcher<PsAndroidDependency>()

  private var skipSelectionChangeNotification: Boolean = false

  init {
    context.analyzerDaemon.add(
      PsAnalyzerDaemon.IssuesUpdatedListener { model ->
        if (model === module) {
          invokeLaterIfNeeded { this.updateDetailsAndIssues() }
        }
      }, this)

    placeName = createPlaceName(module.name)

    contentsPanel.add(createActionsPanel(), BorderLayout.NORTH)
    initializeDependencyDetails()

    setIssuesViewer(IssuesViewer(context, SingleModuleIssuesRenderer(context)))

    dependenciesTableModel = DeclaredDependenciesTableModel(module, context)
    dependenciesTable = DeclaredDependenciesTableView(dependenciesTableModel, context)

    module.add(PsModule.DependenciesChangeListener { event ->
      dependenciesTableModel.reset()
      var toSelect: PsAndroidDependency? = null
      if (event is PsModule.LibraryDependencyAddedEvent) {
        dependenciesTable.clearSelection()
        toSelect = dependenciesTableModel.findDependency(event.spec)
      }
      else if (event is PsModule.DependencyModifiedEvent) {
        val dependency = event.dependency
        if (dependency is PsAndroidDependency) {
          toSelect = dependency
        }
      }
      if (toSelect != null) {
        dependenciesTable.selection = listOf(toSelect)
      }
    }, this)

    dependenciesTable.selectionModel.addListSelectionListener { updateDetailsAndIssues() }
    dependenciesTable.selectFirstRow()

    val scrollPane = createScrollPane(dependenciesTable)
    scrollPane.border = JBUI.Borders.empty()
    contentsPanel.add(scrollPane, BorderLayout.CENTER)

    updateTableColumnSizes()
  }

  private fun createPlaceName(moduleName: String): String = "dependencies.$moduleName.place"

  private fun initializeDependencyDetails() {
    addDetails(SingleLibraryDependencyDetails())
    addDetails(ModuleDependencyDetails(context, true))
  }

  override fun getPreferredFocusedComponent(): JComponent = dependenciesTable

  public override fun getPlaceName(): String = placeName

  override fun getExtraToolbarActions(): List<AnAction> {
    val actions = Lists.newArrayList<AnAction>()
    actions.add(RemoveDependencyAction())
    return actions
  }

  fun updateTableColumnSizes() {
    dependenciesTable.updateColumnSizes()
  }

  override fun dispose() {
    Disposer.dispose(dependenciesTable)
  }

  fun add(listener: SelectionChangeListener<PsAndroidDependency>) {
    eventDispatcher.addListener(listener, this)
    notifySelectionChanged()
  }

  override fun getSelection(): PsAndroidDependency? = dependenciesTable.selectionIfSingle

  override fun setSelection(selection: PsAndroidDependency?) {
    skipSelectionChangeNotification = true
    if (selection == null) {
      dependenciesTable.clearSelection()
    }
    else {
      dependenciesTable.setSelection(setOf(selection))
    }
    updateDetailsAndIssues()
    skipSelectionChangeNotification = false
  }

  private fun updateDetailsAndIssues() {
    if (!skipSelectionChangeNotification) {
      notifySelectionChanged()
    }

    val selected = selection
    super.updateDetails(selected)
    updateIssues(selected)

    val history = history
    history?.pushQueryPlace()
  }

  private fun notifySelectionChanged() {
    val selected = selection
    if (selected != null) {
      eventDispatcher.selectionChanged(selected)
    }
  }

  private fun updateIssues(selected: PsAndroidDependency?) {
    var issues = emptyList<PsIssue>()
    if (selected != null) {
      issues = context.analyzerDaemon.issues.findIssues(selected, null)
    }
    displayIssues(issues)
  }

  override fun selectDependency(dependency: String?) {
    if (isEmpty(dependency)) {
      dependenciesTable.requestFocusInWindow()
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
          doSelectDependency(pathText)
        }
      }
    }
    return ActionCallback.DONE
  }

  private fun doSelectDependency(toSelect: String) {
    dependenciesTable.selectDependency(toSelect)
  }

  private inner class RemoveDependencyAction internal constructor() : DumbAwareAction("Remove Dependency...", "", AllIcons.Actions.Delete) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), dependenciesTable)
    }

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
          module.removeDependency(dependency)
          dependenciesTable.selectFirstRow()
        }
      }
    }
  }
}
