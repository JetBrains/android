/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.controllers.BuildAnalyzerPropertiesAction
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.LayoutFocusTraversalPolicy

/**
 * Main view of Build Analyzer report that is based on ComboBoxes navigation on the top level.
 */
class BuildAnalyzerComboBoxView(
  private val model: BuildAnalyzerViewModel,
  private val actionHandlers: ViewActionHandlers,
): Disposable {

  // Flag to prevent triggering calls to action handler on pulled from the model updates.
  private var fireActionHandlerEvents = true

  val dataSetCombo = ComboBox(CollectionComboBoxModel(model.availableDataSets)).apply {
    name = "dataSetCombo"
    renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value.uiName }
    selectedItem = this@BuildAnalyzerComboBoxView.model.selectedData
    addItemListener { event ->
      if (fireActionHandlerEvents && event.stateChange == ItemEvent.SELECTED) {
        actionHandlers.dataSetComboBoxSelectionUpdated(event.item as BuildAnalyzerViewModel.DataSet)
      }
    }
  }

  private val pageViewByDataSetMap: MutableMap<BuildAnalyzerViewModel.DataSet, BuildAnalyzerDataPageView> = mutableMapOf()

  private fun pageViewByDataSet(dataSet: BuildAnalyzerViewModel.DataSet): BuildAnalyzerDataPageView =
    pageViewByDataSetMap.computeIfAbsent(dataSet) {
      when (it) {
        BuildAnalyzerViewModel.DataSet.OVERVIEW -> BuildOverviewPageView(model.overviewPageModel, actionHandlers)
        BuildAnalyzerViewModel.DataSet.TASKS -> TasksPageView(model.tasksPageModel, actionHandlers, this)
        BuildAnalyzerViewModel.DataSet.WARNINGS -> WarningsPageView(model.warningsPageModel, actionHandlers, this)
        BuildAnalyzerViewModel.DataSet.DOWNLOADS -> DownloadsInfoPageView(model.downloadsInfoPageModel, actionHandlers, this)
      }
  }

  private val pagesPanel = object : CardLayoutPanel<BuildAnalyzerViewModel.DataSet, BuildAnalyzerViewModel.DataSet, JComponent>() {
    override fun prepare(key: BuildAnalyzerViewModel.DataSet): BuildAnalyzerViewModel.DataSet = key

    override fun create(dataSet: BuildAnalyzerViewModel.DataSet): JComponent = pageViewByDataSet(dataSet).component
  }

  private val additionalControlsPanel = object : CardLayoutPanel<BuildAnalyzerViewModel.DataSet, BuildAnalyzerViewModel.DataSet, JComponent>() {
    override fun prepare(key: BuildAnalyzerViewModel.DataSet): BuildAnalyzerViewModel.DataSet = key

    override fun create(dataSet: BuildAnalyzerViewModel.DataSet): JComponent = pageViewByDataSet(dataSet).additionalControls
  }

  /**
   * Main panel that contains all the UI.
   */
  val wholePanel = JBPanel<JBPanel<*>>(BorderLayout(0, 1)).apply {
    background = JBUI.CurrentTheme.ToolWindow.headerBorderBackground()
    val controlsPanel = JBPanel<JBPanel<*>>(HorizontalLayout(10)).apply {
      border = JBUI.Borders.emptyLeft(4)
      withPreferredHeight(35)
    }
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun accept(aComponent: Component?): Boolean {
        return aComponent !is JEditorPane && super.accept(aComponent)
      }
    }

    controlsPanel.add(dataSetCombo)
    controlsPanel.add(additionalControlsPanel)
    add(controlsPanel, BorderLayout.NORTH)
    add(pagesPanel, BorderLayout.CENTER)
  }

  init {
    selectPage(model.selectedData)

    model.dataSetSelectionListener = {
      fireActionHandlerEvents = false
      model.selectedData.let {
        selectPage(it)
        dataSetCombo.selectedItem = it
      }
      fireActionHandlerEvents = true
    }
  }

  private fun selectPage(page: BuildAnalyzerViewModel.DataSet) {
    pagesPanel.select(page, true)
    additionalControlsPanel.select(page, true)
  }

  override fun dispose() {
    model.dataSetSelectionListener = null
  }

  private fun createToolbar(targetComponent: JComponent): JComponent {
    val group = DefaultActionGroup()
    group.add(BuildAnalyzerPropertiesAction())
    val actionManager = ActionManager.getInstance()
    val toolbar = actionManager.createActionToolbar("BuildAnalyzerToolbar", group, true)
    toolbar.targetComponent = targetComponent
    return JBUI.Panels.simplePanel(toolbar.component)
  }
}

