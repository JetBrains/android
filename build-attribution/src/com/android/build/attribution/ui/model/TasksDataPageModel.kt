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
package com.android.build.attribution.ui.model

import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.CriticalPathEntriesUiData
import com.android.build.attribution.ui.data.CriticalPathEntryUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.displayName
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState.EMPTY_PLACEHOLDER
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState.WARNING_ICON
import com.android.build.attribution.ui.view.chart.ChartValueProvider
import com.android.build.attribution.ui.warningsCountString
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Tasks data page view model.
 * Keeps track of the selected tasks grouping and page,
 * provides corresponding to current state values to be set in the view.
 * Notifies the view about the changes with the listener set.
 */
interface TasksDataPageModel {
  val reportData: BuildAttributionReportUiData

  val selectedGrouping: Grouping

  /** Text of the header visible above the tree. */
  val treeHeaderText: String

  /** The root of the tree that should be shown now. View is supposed to set this root in the Tree on update. */
  val treeRoot: DefaultMutableTreeNode

  /** Can be null in case of empty tree. */
  val selectedNode: TasksTreeNode?

  val filter: TasksFilter

  /** True if there are no tasks to show. */
  val isEmpty: Boolean

  /** Update selected grouping to provided. Notifies listener if model state changes. */
  fun selectGrouping(newSelectedGrouping: Grouping)

  /**
   * Takes [TasksPageId] from the provided node and selects it as described in [selectPageById].
   * Null means changing to empty selection.
   * Notifies listener if model state changes.
   */
  fun selectNode(tasksTreeNode: TasksTreeNode?)

  /**
   * Selects node in a tree to the one with provided id.
   * In case provided [tasksPageId] is from another grouping tree updates the selected grouping first.
   * Check if such node exists in current tree, fallbacks to empty selection otherwise.
   * Notifies listener if model state changes.
   */
  fun selectPageById(tasksPageId: TasksPageId)

  /** Install the listener that will be called on model state changes. */
  fun addModelUpdatedListener(disposable: Disposable, listener: (treeStructureChanged: Boolean) -> Unit)
  fun getNodeDescriptorById(pageId: TasksPageId): TasksTreePresentableNodeDescriptor?

  enum class Grouping(
    val uiName: String
  ) {
    UNGROUPED("No Grouping"),
    BY_PLUGIN("Plugin"),
    BY_TASK_CATEGORY("Task Category")
  }

  val availableGroupings: List<Grouping>
    get() = Grouping.values().asList()

  val defaultGrouping: Grouping
    get() = if (reportData.showTaskCategoryInfo) {
      Grouping.BY_TASK_CATEGORY
    } else {
      Grouping.UNGROUPED
    }

  fun applyFilter(newFilter: TasksFilter)
}

class TasksDataPageModelImpl(
  override val reportData: BuildAttributionReportUiData
) : TasksDataPageModel {

  private val modelUpdatedListeners: MutableList<((treeStructureChanged: Boolean) -> Unit)> = CopyOnWriteArrayList()
  @VisibleForTesting
  val listenersCount: Int get() = modelUpdatedListeners.size

  override val selectedGrouping: TasksDataPageModel.Grouping
    get() = selectedPageId.grouping

  override val treeHeaderText: String
    get() = if (treeStructure.treeStats.filtersAreApplied) {
      "Tasks duration - Total: $totalTimeString, Filtered: $filteredTimeString"
    } else {
      "Tasks duration: $totalTimeString"
    }

  private val totalTimeString: String
    get() = durationString(treeStructure.treeStats.totalTasksTimeMs)

  private val filteredTimeString: String
    get() = durationString(treeStructure.treeStats.filteredTasksTimeMs)

  override val treeRoot: DefaultMutableTreeNode
    get() = treeStructure.treeRoot

  override val selectedNode: TasksTreeNode?
    get() = treeStructure.pageIdToNode[selectedPageId]

  override var filter: TasksFilter = TasksFilter.DEFAULT
    private set(value) {
      field = value
      treeStructure.updateStructure(selectedGrouping, value)
      treeStructureChanged = true
      modelChanged = true
    }

  override val isEmpty: Boolean
    get() = reportData.criticalPathTasks.size == 0

  // True when there are changes since last listener call.
  private var modelChanged = false

  // True when tree changed it's structure since last listener call.
  private var treeStructureChanged = false

  private var selectedPageId: TasksPageId = TasksPageId.emptySelection(defaultGrouping)
    private set(value) {
      val newSelectedGrouping = value.grouping
      if (newSelectedGrouping != field.grouping) {
        treeStructure.updateStructure(newSelectedGrouping, filter)
        treeStructureChanged = true
        modelChanged = true
      }
      val newSelectedPageId = if (treeStructure.pageIdToNode.containsKey(value)) value else TasksPageId.emptySelection(newSelectedGrouping)
      if (newSelectedPageId != field) {
        field = newSelectedPageId
        modelChanged = true
      }
    }

  private val treeStructure = TasksTreeStructure(reportData).apply {
    updateStructure(selectedGrouping, filter)
  }

  override fun selectGrouping(newSelectedGrouping: TasksDataPageModel.Grouping) {
    val currentPageId = selectedPageId
    val newSelectedPageId = if (
      (currentPageId.pageType == TaskDetailsPageType.PLUGIN_DETAILS &&
      newSelectedGrouping == TasksDataPageModel.Grouping.BY_PLUGIN) ||
      (currentPageId.pageType == TaskDetailsPageType.TASK_CATEGORY_DETAILS &&
       newSelectedGrouping == TasksDataPageModel.Grouping.BY_TASK_CATEGORY)
    ) TasksPageId.emptySelection(newSelectedGrouping)
    else currentPageId.copy(grouping = newSelectedGrouping)

    selectedPageId = newSelectedPageId
    notifyModelChanges()
  }

  override fun selectNode(tasksTreeNode: TasksTreeNode?) {
    val currentGrouping = selectedGrouping
    selectedPageId = tasksTreeNode?.descriptor?.pageId ?: TasksPageId.emptySelection(currentGrouping)
    notifyModelChanges()
  }

  override fun selectPageById(tasksPageId: TasksPageId) {
    selectedPageId = tasksPageId
    notifyModelChanges()
  }

  override fun applyFilter(newFilter: TasksFilter) {
    filter = newFilter
    notifyModelChanges()
  }

  override fun addModelUpdatedListener(disposable: Disposable, listener: (Boolean) -> Unit) {
    modelUpdatedListeners.add(listener)
    Disposer.register(disposable) { modelUpdatedListeners.remove(listener) }
  }

  override fun getNodeDescriptorById(pageId: TasksPageId): TasksTreePresentableNodeDescriptor? =
    treeStructure.pageIdToNode[pageId]?.descriptor

  private fun notifyModelChanges() {
    if (modelChanged) {
      modelUpdatedListeners.forEach { it.invoke(treeStructureChanged) }
      treeStructureChanged = false
      modelChanged = false
    }
  }
}

private class TasksTreeStructure(
  val reportData: BuildAttributionReportUiData
) {

  val pageIdToNode: MutableMap<TasksPageId, TasksTreeNode> = mutableMapOf()

  val treeRoot = DefaultMutableTreeNode()

  var treeStats: TreeStats = TreeStats()

  private fun treeNode(descriptor: TasksTreePresentableNodeDescriptor): TasksTreeNode = TasksTreeNode(descriptor).apply {
    pageIdToNode[descriptor.pageId] = this
  }

  fun updateStructure(grouping: TasksDataPageModel.Grouping, filter: TasksFilter) {
    pageIdToNode.clear()
    treeStats = TreeStats()
    treeRoot.removeAllChildren()
    when (grouping) {
      TasksDataPageModel.Grouping.UNGROUPED -> createUngroupedNodes(filter, treeStats)
      TasksDataPageModel.Grouping.BY_PLUGIN -> createGroupedByEntryNodes(filter, treeStats, reportData.criticalPathPlugins, grouping)
      TasksDataPageModel.Grouping.BY_TASK_CATEGORY -> createGroupedByEntryNodes(filter, treeStats, reportData.criticalPathTaskCategories!!, grouping)
    }
    treeStats.filtersAreApplied = (filter != TasksFilter.DEFAULT)
    treeStats.filteredTaskTimesDistribution.seal()
    treeStats.totalTasksTimeMs = reportData.criticalPathTasks.tasks.sumOf { it.executionTime.timeMs }
  }

  private fun createUngroupedNodes(filter: TasksFilter, treeStats: TreeStats) {
    treeRoot.removeAllChildren()
    reportData.criticalPathTasks.tasks.asSequence()
      .filter { filter.acceptTask(it, TasksDataPageModel.Grouping.UNGROUPED) }
      .map { TaskDetailsNodeDescriptor(it, TasksDataPageModel.Grouping.UNGROUPED, treeStats.filteredTaskTimesDistribution) }
      .forEach {
        treeRoot.add(treeNode(it))
      }
  }

  private fun createGroupedByEntryNodes(filter: TasksFilter, treeStats: TreeStats, criticalPathEntry: CriticalPathEntriesUiData, grouping: TasksDataPageModel.Grouping) {
    treeRoot.removeAllChildren()
    val filteredEntryTimesDistribution = TimeDistributionBuilder()
    criticalPathEntry.entries.forEach { entryUiData ->
      val filteredTasksForEntry = entryUiData.criticalPathTasks.filter { filter.acceptTask(it, grouping) }
      if (filteredTasksForEntry.isNotEmpty()) {
        val entryNode = treeNode(EntryDetailsNodeDescriptor(entryUiData, filteredTasksForEntry, filteredEntryTimesDistribution))
        filteredTasksForEntry.forEach {
          entryNode.add(
            treeNode(TaskDetailsNodeDescriptor(it, entryUiData.modelGrouping, treeStats.filteredTaskTimesDistribution)))
        }
        treeRoot.add(entryNode)
      }
    }
    filteredEntryTimesDistribution.seal()
  }

  class TreeStats {
    var totalTasksTimeMs: Long = 0
    val filteredTaskTimesDistribution = TimeDistributionBuilder()
    var filtersAreApplied: Boolean = false
    val filteredTasksTimeMs: Long
      get() = filteredTaskTimesDistribution.totalTime
  }
}

class TasksTreeNode(
  val descriptor: TasksTreePresentableNodeDescriptor
) : DefaultMutableTreeNode(descriptor), ChartValueProvider {
  override val relativeWeight: Double
    get() = descriptor.relativeWeight
  override val itemColor: Color
    get() = descriptor.chartItemColor
}

enum class TaskDetailsPageType {
  EMPTY_SELECTION,
  TASK_DETAILS,
  PLUGIN_DETAILS,
  TASK_CATEGORY_DETAILS
}

data class TasksPageId(
  val grouping: TasksDataPageModel.Grouping,
  val pageType: TaskDetailsPageType,
  val id: String
) {
  companion object {
    fun task(task: TaskUiData, grouping: TasksDataPageModel.Grouping) =
      TasksPageId(grouping, TaskDetailsPageType.TASK_DETAILS, task.taskPath)

    fun plugin(plugin: CriticalPathPluginUiData) =
      TasksPageId(TasksDataPageModel.Grouping.BY_PLUGIN, TaskDetailsPageType.PLUGIN_DETAILS, plugin.name)

    fun taskCategory(taskCategory: TaskCategory) =
      TasksPageId(TasksDataPageModel.Grouping.BY_TASK_CATEGORY, TaskDetailsPageType.TASK_CATEGORY_DETAILS, taskCategory.displayName())

    fun emptySelection(grouping: TasksDataPageModel.Grouping) =
      TasksPageId(grouping, TaskDetailsPageType.EMPTY_SELECTION, "EMPTY")
  }
}

sealed class TasksTreePresentableNodeDescriptor {
  abstract val pageId: TasksPageId
  abstract val analyticsPageType: PageType
  abstract val presentation: BuildAnalyzerTreeNodePresentation

  /**
   * Represents the impact of this node compared to other nodes. Used by TimeDistributionTreeChart to build the distribution chart.
   */
  abstract val relativeWeight: Double

  /**
   * Color of the chart item corresponding to this node.
   */
  abstract val chartItemColor: Color
  override fun toString(): String = presentation.mainText
}

/** Tasks tree node descriptor that holds task node data and presentation. */
class TaskDetailsNodeDescriptor(
  val taskData: TaskUiData,
  grouping: TasksDataPageModel.Grouping,
  timeDistributionBuilder: TimeDistributionBuilder
) : TasksTreePresentableNodeDescriptor() {
  override val pageId = TasksPageId.task(taskData, grouping)
  override val analyticsPageType = when (grouping) {
    TasksDataPageModel.Grouping.UNGROUPED -> PageType.CRITICAL_PATH_TASK_PAGE
    TasksDataPageModel.Grouping.BY_PLUGIN -> PageType.PLUGIN_CRITICAL_PATH_TASK_PAGE
    TasksDataPageModel.Grouping.BY_TASK_CATEGORY -> PageType.TASK_CATEGORY_CRITICAL_PATH_TASK_PAGE
  }
  private val filteredTaskTime = timeDistributionBuilder.registerTimeEntry(taskData.executionTime.timeMs)
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = taskData.taskPath,
      rightAlignedSuffix = filteredTaskTime.toRightAlignedNodeDurationText(),
      nodeIconState = if (taskData.hasWarning) WARNING_ICON else EMPTY_PLACEHOLDER
    )

  override val relativeWeight: Double
    get() = filteredTaskTime.toTimeWithPercentage().percentage
  override val chartItemColor: Color
    get() = CriticalPathChartLegend.resolveTaskColor(taskData).baseColor
}

/** Tasks tree node descriptor that holds plugin and task category node data and presentation. */
class EntryDetailsNodeDescriptor(
  val entryData: CriticalPathEntryUiData,
  val filteredTaskNodes: List<TaskUiData>,
  timeDistributionBuilder: TimeDistributionBuilder
) : TasksTreePresentableNodeDescriptor() {
  val filteredWarningCount = filteredTaskNodes.count { it.hasWarning }
  val filteredEntryTime = timeDistributionBuilder.registerTimeEntry(filteredTaskNodes.sumOf { it.executionTime.timeMs })
  override val pageId = when (entryData) {
    is CriticalPathPluginUiData -> TasksPageId.plugin(entryData)
    is CriticalPathTaskCategoryUiData -> TasksPageId.taskCategory(entryData.taskCategory)
    else -> throw IllegalArgumentException("Unknown type ${entryData::class.java} of ${entryData.name}")
  }
  override val analyticsPageType = if (entryData is CriticalPathPluginUiData) PageType.PLUGIN_PAGE else PageType.TASK_CATEGORY_PAGE
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = entryData.name,
      suffix = if (entryData is CriticalPathTaskCategoryUiData) {
        warningsCountString(filteredWarningCount + entryData.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = false).size)
      }
      else {
        warningsCountString(filteredWarningCount)
      },
      rightAlignedSuffix = filteredEntryTime.toRightAlignedNodeDurationText()
    )
  override val relativeWeight: Double
    get() = filteredEntryTime.toTimeWithPercentage().percentage
  override val chartItemColor: Color
    get() = CriticalPathChartLegend.pluginColorPalette.getColor(entryData.name).baseColor
}

private fun TimeWithPercentage.toRightAlignedNodeDurationText(): String {
  val timeString = if (timeMs < 100) "<0.1s" else "%2.1fs".format(timeS)
  val percentageString = when {
    percentage < 0.1 -> "<0.1%"
    percentage > 99.9 -> ">99.9%"
    else -> "%4.1f%%".format(percentage)
  }
  return "%s %5s".format(timeString, percentageString)
}

// TODO (mlazeba): It might be a good idea to replace all current usage of TimeWithPercentage to such way of distribution building.
//  But it is quite a lot of places so will do later if this works fine.
class TimeDistributionBuilder {
  var totalTime: Long = 0
    private set
  private var sealed: Boolean = false

  fun registerTimeEntry(timeMs: Long): TimeEntry {
    if (sealed) {
      throw UnsupportedOperationException("This distribution is already sealed and cannot be changed.")
    }
    totalTime += timeMs
    return TimeEntry(timeMs)
  }

  fun seal() {
    sealed = true
  }

  inner class TimeEntry(val timeMs: Long) {
    fun toTimeWithPercentage() = if (sealed)
      TimeWithPercentage(timeMs, totalTime)
    else
      throw UnsupportedOperationException("Shouldn't be called before distribution is sealed.")

    fun toRightAlignedNodeDurationText() = toTimeWithPercentage().toRightAlignedNodeDurationText()
  }
}