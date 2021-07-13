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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntryUpdateEventType
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

private const val BUTTON_SIZE = 24 // Icon is 16x16. This gives it some padding, so it doesn't touch the border.
private val BUTTON_DIMENS = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))


class EntryDetailsView(private val tab: BackgroundTaskInspectorTab,
                       private val client: BackgroundTaskInspectorClient,
                       private val ideServices: AppInspectionIdeServices,
                       private val selectionModel: EntrySelectionModel) : JPanel() {

  // A configuration map to add extra paddings at the bottom of certain components.
  private val extraBottomPaddingMap = mutableMapOf<Component, Int>()
  private val scrollPane = JBScrollPane()

  init {
    layout = TabularLayout("*", "28px,*")
    border = BorderFactory.createEmptyBorder()
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Task Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    val closeButton = CloseButton { tab.isDetailsViewVisible = false }
    headingPanel.add(closeButton, BorderLayout.EAST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))

    selectionModel.registerWorkSelectionListener { entry ->
      if (entry != null) {
        tab.isDetailsViewVisible = true
        if (entry is WorkEntry) {
          updateSelectedWork(entry)
        }
      }
    }
    client.addEntryUpdateEventListener { type, _ ->
      if (tab.isDetailsViewVisible && type == EntryUpdateEventType.UPDATE) {
        (selectionModel.selectedEntry as? WorkEntry)?.let {
          updateSelectedWork(it)
        }
      }
    }
  }

  private fun updateSelectedWork(workEntry: WorkEntry) {
    val detailsPanel = object : ScrollablePanel(VerticalLayout(18)) {
      override fun getScrollableTracksViewportWidth() = false
    }
    detailsPanel.border = BorderFactory.createEmptyBorder(6, 12, 20, 12)

    val work = workEntry.getWorkInfo()

    val idListProvider = IdListProvider(client, work) {
      selectionModel.selectedEntry = it
    }

    detailsPanel.add(buildCategoryPanel("Description", listOf(
      buildKeyValuePair("Class", work.workerClassName, ClassNameProvider(ideServices, client.scope)),
      buildKeyValuePair("Tags", work.tagsList, StringListProvider),
      buildKeyValuePair("UUID", work.id)
    )))

    detailsPanel.add(buildCategoryPanel("Execution", listOf(
      buildKeyValuePair("Enqueued by", work.callStack, EnqueuedAtProvider(ideServices, client.scope)),
      buildKeyValuePair("Constraints", work.constraints, ConstraintProvider),
      buildKeyValuePair("Frequency", if (work.isPeriodic) "Periodic" else "One Time"),
      buildKeyValuePair("State", work.state, StateProvider)
    )))

    detailsPanel.add(buildCategoryPanel("WorkContinuation", listOf(
      buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
      // Visually separate work chain or else UUIDs run together.
      buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider).apply { extraBottomPaddingMap[this] = 14 },
      buildKeyValuePair("Unique work chain", client.getOrderedWorkChain(work.id).map { it.id }, idListProvider)
    )))

    detailsPanel.add(buildCategoryPanel("Results", listOf(
      buildKeyValuePair("Time started", work.scheduleRequestedAt, TimeProvider),
      buildKeyValuePair("Retries", work.runAttemptCount),
      buildKeyValuePair("Output data", work, OutputDataProvider)
    )))

    scrollPane.setViewportView(detailsPanel)
    revalidate()
    repaint()
  }

  private fun buildCategoryPanel(name: String, entryComponents: List<Component>): JPanel {
    val panel = JPanel(VerticalLayout(6))

    val headingPanel = TitledSeparator(name)
    headingPanel.minimumSize = Dimension(0, 34)
    panel.add(headingPanel)

    for (component in entryComponents) {
      val borderedPanel = JPanel(BorderLayout())
      borderedPanel.add(component, BorderLayout.WEST)
      borderedPanel.border =
        BorderFactory.createEmptyBorder(0, 18, extraBottomPaddingMap.getOrDefault(component, 0), 0)
      panel.add(borderedPanel)
    }
    return panel
  }

  private fun <T> buildKeyValuePair(key: String,
                                    value: T,
                                    componentProvider: ComponentProvider<T> = ToStringProvider()): JPanel {
    val panel = JPanel(TabularLayout("155px,*")).apply {
      // Add a 2px text offset to align this panel with a [HyperlinkLabel] properly.
      // See HyperlinkLabel.getTextOffset() for more details.
      border = BorderFactory.createEmptyBorder(0, 2, 0, 0)
    }
    val keyPanel = JPanel(BorderLayout())
    keyPanel.add(JBLabel(key), BorderLayout.NORTH) // If value is multi-line, key should stick to the top of its cell
    panel.add(keyPanel, TabularLayout.Constraint(0, 0))
    panel.add(componentProvider.convert(value), TabularLayout.Constraint(0, 1))
    return panel
  }
}

class CloseButton(actionListener: ActionListener?) : InplaceButton(
  IconButton("Close", AllIcons.Ide.Notification.Close,
             AllIcons.Ide.Notification.CloseHover), actionListener) {

  init {
    preferredSize = BUTTON_DIMENS
    minimumSize = preferredSize // Prevent layout phase from squishing this button
  }
}
