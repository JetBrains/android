package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.google.wireless.android.sdk.stats.AppInspectionEvent.WorkManagerInspectorEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator

private const val BUTTON_SIZE = 24 // Icon is 16x16. This gives it some padding, so it doesn't touch the border.
private val BUTTON_DIMENS = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))

/**
 * View to display information details of a selected work grouped by categories.
 */
class WorkInfoDetailsView(private val tab: WorkManagerInspectorTab,
                          private val client: WorkManagerInspectorClient,
                          private val ideServices: AppInspectionIdeServices,
                          private val scope: CoroutineScope,
                          private val workSelectionModel: WorkSelectionModel,
                          private val contentView: WorksContentView) : JPanel() {

  // A configuration map to add extra paddings at the bottom of certain components.
  private val extraBottomPaddingMap = mutableMapOf<Component, Int>()
  private val scrollPane = JBScrollPane()

  init {
    layout = TabularLayout("*", "Fit,Fit,*")
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Work Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 5, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    val closeButton = CloseButton { tab.isDetailsViewVisible = false }
    headingPanel.add(closeButton, BorderLayout.EAST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    add(JSeparator(), TabularLayout.Constraint(1, 0))
    scrollPane.border = BorderFactory.createEmptyBorder()
    add(scrollPane, TabularLayout.Constraint(2, 0))

    workSelectionModel.registerWorkSelectionListener { work, _ ->
      if (work != null && tab.isDetailsViewVisible) {
        updateSelectedWork(work)
      }
    }
  }

  private fun updateSelectedWork(work: WorkInfo) {
    val detailsPanel = ScrollablePanel(VerticalLayout(18))
    detailsPanel.border = BorderFactory.createEmptyBorder(6, 12, 20, 12)

    val idListProvider = IdListProvider(client, work) {
      client.tracker.trackWorkSelected(WorkManagerInspectorEvent.Context.DETAILS_CONTEXT)
      workSelectionModel.setSelectedWork(it, WorkSelectionModel.Context.DETAILS)
    }

    detailsPanel.add(buildCategoryPanel("Description", listOf(
      buildKeyValuePair("Class", work.workerClassName, ClassNameProvider(ideServices, client.tracker, scope)),
      buildKeyValuePair("Tags", work.tagsList, StringListProvider),
      buildKeyValuePair("UUID", work.id)
    )))

    detailsPanel.add(buildCategoryPanel("Execution", listOf(
      buildKeyValuePair("Enqueued by", work.callStack, EnqueuedAtProvider(ideServices, client.tracker, scope)),
      buildKeyValuePair("Constraints", work.constraints, ConstraintProvider),
      buildKeyValuePair("Frequency", if (work.isPeriodic) "Periodic" else "One Time"),
      buildKeyValuePair("State", work.state, StateProvider)
    )))

    val switchContentModeLabel = if (contentView.contentMode == WorksContentView.Mode.TABLE) {
      HyperlinkLabel("Show in graph").apply {
        addHyperlinkListener {
          contentView.contentMode = WorksContentView.Mode.GRAPH
          workSelectionModel.setSelectedWork(work, WorkSelectionModel.Context.DETAILS)
        }
      }
    }
    else {
      HyperlinkLabel("Show in table").apply {
        addHyperlinkListener {
          contentView.contentMode = WorksContentView.Mode.TABLE
          workSelectionModel.setSelectedWork(work, WorkSelectionModel.Context.DETAILS)
        }
      }
    }

    detailsPanel.add(buildCategoryPanel("WorkContinuation", listOf(
      // Visually separate switchContentModeLabel and work chain labels.
      switchContentModeLabel.apply { extraBottomPaddingMap[this] = 10 },
      buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
      // Visually separate work chain or else UUIDs run together.
      buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider).apply { extraBottomPaddingMap[this] = 14 },
      buildKeyValuePair("Unique work chain", client.getOrderedWorkChain(work.id).map { it.id }, idListProvider)
    )))

    detailsPanel.add(buildCategoryPanel("Results", listOf(
      buildKeyValuePair("Time Started", work.scheduleRequestedAt, TimeProvider),
      buildKeyValuePair("Retries", work.runAttemptCount),
      buildKeyValuePair("Output Data", work, OutputDataProvider)
    )))

    scrollPane.setViewportView(detailsPanel)
    revalidate()
    repaint()
  }

  private fun buildCategoryPanel(name: String, entryComponents: List<Component>): JPanel {
    val panel = JPanel(VerticalLayout(0))

    val headingPanel = TitledSeparator(name)
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
    val panel = JPanel(TabularLayout("180px,*")).apply {
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
