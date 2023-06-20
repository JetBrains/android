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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntryUpdateEventType
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskCallStack
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.InplaceButton
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.text.SimpleDateFormat
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

private const val MINIMUM_DETAILS_VIEW_WIDTH = 400
private const val BUTTON_SIZE =
  24 // Icon is 16x16. This gives it some padding, so it doesn't touch the border.
private val BUTTON_DIMENS = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))

class EntryDetailsView(
  private val tab: BackgroundTaskInspectorTab,
  private val client: BackgroundTaskInspectorClient,
  private val ideServices: AppInspectionIdeServices,
  val selectionModel: EntrySelectionModel,
  private val entriesView: BackgroundTaskEntriesView,
  uiComponentsProvider: UiComponentsProvider,
  private val scope: CoroutineScope,
  private val uiDispatcher: CoroutineDispatcher
) : JPanel() {

  // A configuration map to add extra paddings at the bottom of certain components.
  private val extraBottomPaddingMap = mutableMapOf<Component, Int>()
  private val scrollPane = JBScrollPane()
  private val entryIdProvider: EntryIdProvider

  @VisibleForTesting
  val stackTraceViews =
    listOf(
      EntryDetailsStackTraceView(uiComponentsProvider),
      EntryDetailsStackTraceView(uiComponentsProvider)
    )

  init {
    layout = TabularLayout("*", "28px,*")
    border = BorderFactory.createEmptyBorder()
    minimumSize = Dimension(MINIMUM_DETAILS_VIEW_WIDTH, minimumSize.height)
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Task Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    val closeButton = CloseButton { tab.isDetailsViewVisible = false }
    headingPanel.add(closeButton, BorderLayout.EAST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))

    entryIdProvider = EntryIdProvider { entry ->
      selectionModel.selectedEntry = entry
      client.tracker.trackWorkSelected(
        AppInspectionEvent.BackgroundTaskInspectorEvent.Context.DETAILS_CONTEXT
      )
    }

    selectionModel.registerEntrySelectionListener { entry ->
      if (entry == null) {
        tab.isDetailsViewVisible = false
      } else {
        updateSelectedTask(true)
      }
    }
    client.addEntryUpdateEventListener { type, _ ->
      scope.launch(uiDispatcher) {
        if (type == EntryUpdateEventType.UPDATE || type == EntryUpdateEventType.REMOVE) {
          updateSelectedTask(false)
        }
      }
    }
    entriesView.addContentModeChangedListener {
      if (selectionModel.selectedEntry is WorkEntry) {
        updateSelectedTask(false)
      }
    }
  }

  private fun updateSelectedTask(isSelectionChanged: Boolean) {
    val detailsPanel =
      object : ScrollablePanel(VerticalLayout(18)) {
        override fun getScrollableTracksViewportWidth(): Boolean {
          val parent = SwingUtilities.getUnwrappedParent(this)
          return ((parent as? JViewport)?.width ?: 0) > preferredSize.width
        }
      }
    // Reserve 14px extra space for scroll bar on the right.
    detailsPanel.border = BorderFactory.createEmptyBorder(6, 16, 20, 30)

    when (val entry = selectionModel.selectedEntry) {
      is WorkEntry -> updateSelectedWork(detailsPanel, entry)
      is JobEntry -> updateSelectedJob(detailsPanel, entry)
      is AlarmEntry -> updateSelectedAlarm(detailsPanel, entry)
      is WakeLockEntry -> updateSelectedWakeLock(detailsPanel, entry)
    }

    TreeWalker(detailsPanel).descendantStream().forEach { it.background = null }
    detailsPanel.background = primaryContentBackground
    val scrollBarPosition = scrollPane.verticalScrollBar.value
    scrollPane.setViewportView(detailsPanel)
    if (!isSelectionChanged) {
      scrollPane.verticalScrollBar.value = scrollBarPosition
    }
    revalidate()
    repaint()
  }

  private fun updateSelectedAlarm(detailsPanel: ScrollablePanel, alarm: AlarmEntry) {
    val alarmSet = alarm.alarmSet ?: return

    val descriptions = mutableListOf(buildKeyValuePair("Type", alarmSet.type))
    if (alarmSet.intervalMs > 0) {
      descriptions.add(
        buildKeyValuePair("Interval time", StringUtil.formatDuration(alarmSet.intervalMs))
      )
    }

    if (alarmSet.triggerMs > 0) {
      descriptions.add(buildKeyValuePair("Trigger time", alarmSet.triggerMs, TimeProvider))
    }

    if (alarmSet.windowMs > 0) {
      descriptions.add(
        buildKeyValuePair("Window time", StringUtil.formatDuration(alarmSet.windowMs))
      )
    }
    if (alarmSet.hasListener()) {
      descriptions.add(buildKeyValuePair("Listener tag", alarmSet.listener.tag))
    }
    if (alarmSet.hasOperation()) {
      val operation = alarmSet.operation
      if (operation.creatorPackage.isNotEmpty()) {
        descriptions.add(
          buildKeyValuePair("Creator", "${operation.creatorPackage} (UID: ${operation.creatorUid})")
        )
      }
    }
    detailsPanel.add(buildCategoryPanel("Description", descriptions))

    val results = mutableListOf(buildKeyValuePair("Time started", alarm.startTimeMs, TimeProvider))
    alarm.latestEvent?.let { latestEvent ->
      if (
        latestEvent.backgroundTaskEvent.hasAlarmFired() ||
          latestEvent.backgroundTaskEvent.hasAlarmCancelled()
      ) {
        val completeTimeMs = latestEvent.timestamp
        alarm.alarmFiredTimestamps.forEachIndexed { index, timestamp ->
          results.add(
            if (alarmSet.intervalMs > 0)
              buildKeyValuePair("Time fired #${index + 1}", timestamp, TimeProvider)
            else buildKeyValuePair("Time fired", timestamp, TimeProvider)
          )
        }
        if (alarm.status == AlarmEntry.State.CANCELLED.name) {
          results.add(buildKeyValuePair("Time cancelled", completeTimeMs, TimeProvider))
        }
        results.add(
          buildKeyValuePair(
            "Elapsed time",
            StringUtil.formatDuration(completeTimeMs - alarm.startTimeMs)
          )
        )
      }
    }
    detailsPanel.add(buildCategoryPanel("Results", results))

    detailsPanel.addStackTraceViews(alarm.callstacks, listOf("Set", "Cancelled"))
  }

  private fun updateSelectedWakeLock(detailsPanel: ScrollablePanel, wakeLock: WakeLockEntry) {
    val acquired = wakeLock.acquired ?: return
    val wakeLockAcquired = acquired.backgroundTaskEvent.wakeLockAcquired
    detailsPanel.add(
      buildCategoryPanel(
        "Description",
        listOf(
          buildKeyValuePair("Tag", wakeLockAcquired.tag),
          buildKeyValuePair("Level", wakeLockAcquired.level),
        )
      )
    )

    val results =
      mutableListOf(buildKeyValuePair("Time started", wakeLock.startTimeMs, TimeProvider))
    wakeLock.released?.let { released ->
      val completeTimeMs = released.timestamp
      results.add(buildKeyValuePair("Time completed", completeTimeMs, TimeProvider))
      results.add(
        buildKeyValuePair(
          "Elapsed time",
          StringUtil.formatDuration(completeTimeMs - wakeLock.startTimeMs)
        )
      )
    }
    detailsPanel.add(buildCategoryPanel("Results", results))

    detailsPanel.addStackTraceViews(wakeLock.callstacks, listOf("Acquired", "Released"))
  }

  private fun updateSelectedJob(detailsPanel: ScrollablePanel, jobEntry: JobEntry) {
    val job = jobEntry.jobInfo ?: return

    detailsPanel.add(
      buildCategoryPanel(
        "Description",
        listOf(
          buildKeyValuePair(
            "Service",
            job.serviceName,
            ClassNameProvider(ideServices, client.scope, client.tracker)
          )
        )
      )
    )

    val executions =
      mutableListOf(
        buildKeyValuePair("Constraints", job, JobConstraintProvider),
        buildKeyValuePair("Frequency", if (job.isPeriodic) "Periodic" else "OneTime"),
        buildKeyValuePair("State", jobEntry, StateProvider)
      )
    jobEntry.targetWorkId
      ?.let { id -> client.getEntry(id) }
      ?.let { workEntry ->
        executions.add(buildKeyValuePair("Related Worker", workEntry, entryIdProvider))
      }
    detailsPanel.add(buildCategoryPanel("Execution", executions))

    val results =
      mutableListOf(buildKeyValuePair("Time started", jobEntry.startTimeMs, TimeProvider))
    jobEntry.latestEvent?.let { latestEvent ->
      if (
        latestEvent.backgroundTaskEvent.hasJobStopped() ||
          latestEvent.backgroundTaskEvent.hasJobFinished()
      ) {
        val completeTimeMs = latestEvent.timestamp
        results.add(buildKeyValuePair("Time completed", completeTimeMs, TimeProvider))
        results.add(
          buildKeyValuePair(
            "Elapsed time",
            StringUtil.formatDuration(completeTimeMs - jobEntry.startTimeMs)
          )
        )
        if (latestEvent.backgroundTaskEvent.hasJobFinished()) {
          results.add(
            buildKeyValuePair(
              "Needs reschedule",
              latestEvent.backgroundTaskEvent.jobFinished.needsReschedule
            )
          )
        }
        if (latestEvent.backgroundTaskEvent.hasJobStopped()) {
          results.add(
            buildKeyValuePair("Reschedule", latestEvent.backgroundTaskEvent.jobStopped.reschedule)
          )
        }
      }
    }

    detailsPanel.add(buildCategoryPanel("Results", results))

    detailsPanel.addStackTraceViews(jobEntry.callstacks, listOf("Scheduled", "Finished"))
  }

  private fun updateSelectedWork(detailsPanel: ScrollablePanel, workEntry: WorkEntry) {
    val work = workEntry.getWorkInfo()

    val idListProvider =
      IdListProvider(client, work) {
        selectionModel.selectedEntry = it
        client.tracker.trackWorkSelected(
          AppInspectionEvent.BackgroundTaskInspectorEvent.Context.DETAILS_CONTEXT
        )
      }

    detailsPanel.add(
      buildCategoryPanel(
        "Description",
        listOf(
          buildKeyValuePair(
            "Class",
            work.workerClassName,
            ClassNameProvider(ideServices, client.scope, client.tracker)
          ),
          buildKeyValuePair("Tags", work.tagsList.toList(), StringListProvider),
          buildKeyValuePair("UUID", work.id)
        )
      )
    )

    val executions =
      mutableListOf(
        buildKeyValuePair(
          "Enqueued by",
          work.callStack,
          EnqueuedAtProvider(ideServices, client.scope, client.tracker)
        ),
        buildKeyValuePair("Constraints", work.constraints, WorkConstraintProvider),
        buildKeyValuePair("Frequency", if (work.isPeriodic) "Periodic" else "OneTime"),
        buildKeyValuePair("State", workEntry, StateProvider)
      )

    entriesView.tableView.treeModel.getJobUnderWork(work.id)?.let { jobEntry ->
      executions.add(buildKeyValuePair("Related Job", jobEntry, entryIdProvider))
    }

    detailsPanel.add(buildCategoryPanel("Execution", executions))

    if (workEntry.isValid) {
      val switchContentModeLabel =
        if (entriesView.contentMode == BackgroundTaskEntriesView.Mode.TABLE) {
          ActionLink("Show in graph") {
            entriesView.contentMode = BackgroundTaskEntriesView.Mode.GRAPH
          }
        } else {
          ActionLink("Show in table") {
            entriesView.contentMode = BackgroundTaskEntriesView.Mode.TABLE
          }
        }
      detailsPanel.add(
        buildCategoryPanel(
          "WorkContinuation",
          listOf(
            // Visually separate switchContentModeLabel and work chain labels.
            switchContentModeLabel.apply { extraBottomPaddingMap[this] = 10 },
            buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
            // Visually separate work chain or else UUIDs run together.
            buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider).apply {
              extraBottomPaddingMap[this] = 14
            },
            buildKeyValuePair(
              "Unique work chain",
              client.getOrderedWorkChain(work.id).map { it.id },
              idListProvider
            )
          )
        )
      )
    }

    detailsPanel.add(
      buildCategoryPanel(
        "Results",
        listOf(
          buildKeyValuePair("Time started", work.scheduleRequestedAt, TimeProvider),
          buildKeyValuePair("Retries", work.runAttemptCount),
          buildKeyValuePair("Output data", work, OutputDataProvider)
        )
      )
    )
  }

  private fun buildCategoryPanel(name: String, entryComponents: List<JComponent>): JPanel {
    val panel = JPanel(VerticalLayout(6))

    val headingPanel = TitledSeparator(name)
    headingPanel.minimumSize = Dimension(0, 34)
    panel.add(headingPanel)

    for (component in entryComponents) {
      component.border =
        BorderFactory.createEmptyBorder(0, 18, extraBottomPaddingMap.getOrDefault(component, 0), 0)
      panel.add(component)
    }
    return panel
  }

  private fun <T> buildKeyValuePair(
    key: String,
    value: T,
    componentProvider: ComponentProvider<T> = ToStringProvider()
  ): JPanel {
    val panel =
      JPanel(TabularLayout("155px,*")).apply {
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      }
    val keyPanel = JPanel(BorderLayout())
    keyPanel.add(
      JBLabel(key),
      BorderLayout.NORTH
    ) // If value is multi-line, key should stick to the top of its cell
    panel.add(keyPanel, TabularLayout.Constraint(0, 0))
    panel.add(componentProvider.convert(value), TabularLayout.Constraint(0, 1))
    return panel
  }

  private fun ScrollablePanel.addStackTraceViews(
    callStacks: List<BackgroundTaskCallStack>,
    labels: List<String>
  ) {
    val labelsToStackTraces =
      (labels zip callStacks)
        .filter { it.second.stack.isNotEmpty() }
        .map {
          "${SimpleDateFormat("H:mm:ss.SSS", Locale.getDefault()).format(it.second.triggerTime)} ${it.first}" to
            it.second.stack
        }

    if (labelsToStackTraces.isNotEmpty()) {
      val stackTraceComponents =
        labelsToStackTraces.mapIndexedNotNull { i, pair ->
          when (i) {
            0,
            1 -> {
              stackTraceViews[i].updateTrace(pair.second)
              val hideablePanel =
                HideablePanel.Builder(pair.first, stackTraceViews[i].component)
                  .setContentBorder(JBEmptyBorder(5, 0, 0, 0))
                  .setPanelBorder(JBEmptyBorder(0, 0, 0, 0))
                  .setTitleRightPadding(0)
                  .build()
              hideablePanel
            }
            else -> null
          }
        }
      // Layout the stack trace views in a vertical layout so they can have the same width.
      val containerPanel = JPanel(VerticalLayout(6))
      stackTraceComponents.forEach { containerPanel.add(it) }
      add(buildCategoryPanel("Callstacks", listOf(containerPanel)))
    }
  }
}

class CloseButton(actionListener: ActionListener?) :
  InplaceButton(
    IconButton("Close", AllIcons.Ide.Notification.Close, AllIcons.Ide.Notification.CloseHover),
    actionListener
  ) {

  init {
    preferredSize = BUTTON_DIMENS
    minimumSize = preferredSize // Prevent layout phase from squishing this button
  }
}
