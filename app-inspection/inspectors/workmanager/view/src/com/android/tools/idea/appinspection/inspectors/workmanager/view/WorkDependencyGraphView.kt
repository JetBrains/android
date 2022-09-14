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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorColors.DEFAULT_WORK_BORDER_COLOR
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorColors.GRAPH_LABEL_BACKGROUND_COLOR
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorColors.SELECTED_WORK_BORDER_COLOR
import com.google.wireless.android.sdk.stats.AppInspectionEvent.WorkManagerInspectorEvent
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import kotlin.math.max

const val MIN_GAP_BETWEEN_LABELS = 50

/**
 * Graph Panel which shows dependencies among selected chaining works.
 *
 * @param onSelectedWorkCleared function called when the selected work is cleared.
 */
class WorkDependencyGraphView(private val tab: WorkManagerInspectorTab,
                              private val client: WorkManagerInspectorClient,
                              private val workSelectionModel: WorkSelectionModel,
                              private val onSelectedWorkCleared: () -> Unit) : JPanel() {

  private var works = listOf<WorkInfo>()
  private var labelMap = mapOf<String, JLabel>()

  init {
    border = EmptyBorder(JBUI.scale(50), JBUI.scale(100), JBUI.scale(50), 0)
    workSelectionModel.registerWorkSelectionListener { work, context ->
      refreshView(work, context)
    }

    registerDirectionKeyStroke(KeyEvent.VK_UP, "Up", -1, 0)
    registerDirectionKeyStroke(KeyEvent.VK_DOWN, "Down", 1, 0)
    registerDirectionKeyStroke(KeyEvent.VK_LEFT, "Left", 0, -1)
    registerDirectionKeyStroke(KeyEvent.VK_RIGHT, "Right", 0, 1)
  }

  private fun registerDirectionKeyStroke(keyCode: Int, name: String, deltaRow: Int, deltaCol: Int) {
    val inputMap = getInputMap(WHEN_FOCUSED)
    inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), name)
    actionMap.put(name, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        // Find the selected label in the graph.
        val selectedWork = workSelectionModel.selectedWork ?: return
        val selectedLabel = labelMap[selectedWork.id] ?: return
        var col = -1
        var row = components.indexOfFirst { rowPanel ->
          col = (rowPanel as JPanel).components.indexOfFirst {
            it == selectedLabel
          }
          col != -1
        }
        if (row == -1) {
          return
        }
        row += deltaRow
        col += deltaCol

        // Select the next work.
        if (row in 0 until componentCount) {
          val rowPanel = getComponent(row) as JPanel
          if (col in 0 until rowPanel.componentCount) {
            val label = rowPanel.getComponent(col)
            val id = labelMap.entries.firstOrNull {
              it.value == label
            }?.key ?: return
            val work = works.firstOrNull { it.id == id } ?: return
            workSelectionModel.setSelectedWork(work, WorkSelectionModel.Context.GRAPH)
            return
          }
        }
      }
    })
  }

  private fun refreshView(work: WorkInfo?, context: WorkSelectionModel.Context) {
    if (work == null) {
      onSelectedWorkCleared()
    }
    else {
      updateWorks(work, context)
    }
  }

  private fun updateWorks(selectedWork: WorkInfo, context: WorkSelectionModel.Context) {
    removeAll()
    works = client.getOrderedWorkChain(selectedWork.id)

    if (works.isEmpty()) {
      onSelectedWorkCleared()
      return
    }

    labelMap = works.map { it.id to createLabel(it) }.toMap()
    val selectedLabel = labelMap[selectedWork.id]
    val depthMap = mutableMapOf<String, Int>()
    for (work in works) {
      depthMap[work.id] = (work.prerequisitesList.mapNotNull { depthMap[it] }.maxOrNull() ?: -1) + 1
    }
    val maxDepth = depthMap.values.maxOrNull() ?: -1

    // Find the maximum estimated width for all depths.
    var maxWidth = 0
    for (depth in 0..maxDepth) {
      val worksWithDepth = works.filter { depthMap[it.id] == depth }
      maxWidth = max(maxWidth, estimateWidth(worksWithDepth))
    }

    // Leave a proper gap between works with different depths for dependency arrows.
    layout = VerticalLayout(JBUI.scale(50))
    for (depth in 0..maxDepth) {
      val worksWithDepth = works.filter { depthMap[it.id] == depth }

      // Set a proper gap between labels so that works with different depths have the same total width.
      val gap = (maxWidth - worksWithDepth.sumOf { labelMap[it.id]!!.preferredSize.width }) / ((worksWithDepth.size + 1))
      val panelWithDepth = JPanel(TabularLayout("${gap}px" + ",Fit,${gap}px".repeat(worksWithDepth.size)))
      for (index in worksWithDepth.indices) {
        panelWithDepth.add(labelMap[worksWithDepth[index].id]!!, TabularLayout.Constraint(0, 1 + index * 2))
      }
      add(panelWithDepth)
    }
    validate()
    if (context == WorkSelectionModel.Context.DETAILS) {
      selectedLabel?.scrollToCenter()
    }
    repaint()
  }

  private fun estimateWidth(works: List<WorkInfo>): Int {
    return MIN_GAP_BETWEEN_LABELS * (works.size + 1) + works.sumOf { labelMap[it.id]!!.preferredSize.width }
  }

  private fun createLabel(work: WorkInfo): JLabel {
    val label = JBLabel(work.workerClassName.substringAfterLast('.'), SwingConstants.CENTER)
    label.icon = work.state.icon()
    label.background = GRAPH_LABEL_BACKGROUND_COLOR
    label.isOpaque = true

    val defaultBorder = EmptyBorder(JBUI.scale(6), JBUI.scale(10), JBUI.scale(6), JBUI.scale(10))
    label.border = if (work == workSelectionModel.selectedWork) {
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(JBUI.scale(2), JBUI.scale(2), JBUI.scale(2), JBUI.scale(2), SELECTED_WORK_BORDER_COLOR),
        defaultBorder
      )
    }
    else {
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(JBUI.scale(1), JBUI.scale(1), JBUI.scale(1), JBUI.scale(1), DEFAULT_WORK_BORDER_COLOR),
        BorderFactory.createCompoundBorder(
          EmptyBorder(JBUI.scale(1), JBUI.scale(1), JBUI.scale(1), JBUI.scale(1)),
          defaultBorder
        ),
      )
    }

    label.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        client.tracker.trackWorkSelected(WorkManagerInspectorEvent.Context.GRAPH_CONTEXT)
        workSelectionModel.setSelectedWork(work, WorkSelectionModel.Context.GRAPH)
        tab.isDetailsViewVisible = true
      }
    })
    return label
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    val g2d = g as Graphics2D
    // Draw lines for dependencies between works.
    for (work in works) {
      val fromLabel = labelMap[work.id] ?: continue
      g2d.color = if (work.state.isFinished()) Color.LIGHT_GRAY else Color.WHITE
      val fx = fromLabel.parent.x + fromLabel.bounds.x + fromLabel.bounds.width / 2
      val fy = fromLabel.parent.y + fromLabel.bounds.y + fromLabel.bounds.height
      for (toLabel in work.dependentsList.mapNotNull { labelMap[it] }) {
        val tx = toLabel.parent.x + toLabel.bounds.x + toLabel.bounds.width / 2
        val ty = toLabel.parent.y + toLabel.bounds.y
        g2d.drawLine(fx, fy, tx, ty)
      }
    }
  }
}
