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
class WorkInfoDetailsView(client: WorkManagerInspectorClient,
                          work: WorkInfo,
                          ideServices: AppInspectionIdeServices,
                          scope: CoroutineScope,
                          workSelectionModel: WorkSelectionModel,
                          tab: WorkManagerInspectorTab) : JPanel() {


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

    val detailsPanel = ScrollablePanel(VerticalLayout(18))
    detailsPanel.border = BorderFactory.createEmptyBorder(6, 12, 20, 12)

    val idListProvider = IdListProvider(client, work) {
      client.tracker.trackWorkSelected(WorkManagerInspectorEvent.Context.DETAILS_CONTEXT)
      workSelectionModel.setSelectedWork(it, WorkSelectionModel.Context.DETAILS)
    }
    detailsPanel.preferredScrollableViewportSize
    val scrollPane = JBScrollPane(detailsPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
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

    detailsPanel.add(buildCategoryPanel("WorkContinuation", listOf(
      buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
      buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider),
      buildKeyValuePair(" ", ""), // Visually separate work chain or else UUIDs run together
      buildKeyValuePair("Unique work chain", client.getOrderedWorkChain(work.id).map { it.id }, idListProvider)
    )))

    detailsPanel.add(buildCategoryPanel("Results", listOf(
      buildKeyValuePair("Time Started", work.scheduleRequestedAt, TimeProvider),
      buildKeyValuePair("Retries", work.runAttemptCount),
      buildKeyValuePair("Output Data", work, OutputDataProvider)
    )))

    add(scrollPane, TabularLayout.Constraint(2, 0))
  }


  private fun buildCategoryPanel(name: String, subPanels: List<Component>): JPanel {
    val panel = JPanel(VerticalLayout(0))

    val headingPanel = TitledSeparator(name)
    panel.add(headingPanel)

    for (subPanel in subPanels) {
      val borderedPanel = JPanel(BorderLayout())
      borderedPanel.add(subPanel, BorderLayout.WEST)
      borderedPanel.border = BorderFactory.createEmptyBorder(0, 20, 0, 0)
      panel.add(borderedPanel)
    }
    return panel
  }

  private fun <T> buildKeyValuePair(key: String,
                                    value: T,
                                    componentProvider: ComponentProvider<T> = ToStringProvider()): JPanel {
    val panel = JPanel(TabularLayout("180px,*"))
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
