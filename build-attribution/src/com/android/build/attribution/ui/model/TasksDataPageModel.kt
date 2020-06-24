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
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState.EMPTY_PLACEHOLDER
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState.WARNING_ICON
import com.android.build.attribution.ui.warningsCountString
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
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

  /** True if there are no tasks to show. */
  val isEmpty: Boolean

  /** Update selected grouping to provided. Notifies listener if model state changes. */
  fun selectGrouping(newSelectedGrouping: Grouping)

  /**
   * Selects node in a tree to provided.
   * In case provided node is from another grouping tree updates the selected grouping.
   * Provided node object should be the one created in this model and exist in the trees it holds.
   * Notifies listener if model state changes.
   */
  fun selectNode(tasksTreeNode: TasksTreeNode)

  /** Looks for the tree node by it's pageId and selects it as described in [selectNode] if found. */
  fun selectPageById(tasksPageId: TasksPageId)

  /** Install the listener that will be called on model state changes. */
  fun setModelUpdatedListener(listener: () -> Unit)

  enum class Grouping(
    val uiName: String
  ) {
    UNGROUPED("Ungrouped"),
    BY_PLUGIN("By Plugin")
  }
}

class TasksDataPageModelImpl(
  override val reportData: BuildAttributionReportUiData
) : TasksDataPageModel {

  @VisibleForTesting
  var modelUpdatedListener: (() -> Unit)? = null
    private set

  // True when there are changes since last listener call.
  private var modelChanged = false

  override var selectedGrouping: TasksDataPageModel.Grouping = TasksDataPageModel.Grouping.UNGROUPED
    set(value) {
      if (value != field) {
        field = value
        modelChanged = true
        updateSelectedNodeOnGroupingChange()
      }
    }

  override val treeHeaderText: String
    get() = when (selectedGrouping) {
      TasksDataPageModel.Grouping.UNGROUPED -> "Tasks determining build duration" +
                                               " - ${reportData.criticalPathTasks.criticalPathDuration.durationString()}" +
                                               warningsSuffixFromCount(reportData.criticalPathTasks.warningCount)
      TasksDataPageModel.Grouping.BY_PLUGIN -> "Plugins with tasks determining build duration" +
                                               " - ${reportData.criticalPathPlugins.criticalPathDuration.durationString()}" +
                                               warningsSuffixFromCount(reportData.criticalPathPlugins.warningCount)
    }

  private fun warningsSuffixFromCount(warningCount: Int): String = when (warningCount) {
    0 -> ""
    1 -> " - 1 Warning"
    else -> " - $warningCount Warnings"
  }

  private val treeStructure = TasksTreeStructure(reportData)

  override var selectedNode: TasksTreeNode? = treeStructure.defaultNode
    private set(value) {
      // We can not have have empty selection by design. The type is nullable to support empty tree state.
      if (value == null) throw IllegalArgumentException("Setting selected node to null is not supported.")
      if (value != field) {
        field = value
        modelChanged = true
        selectedGrouping = value.descriptor.pageId.grouping
      }
    }

  override val isEmpty: Boolean
    get() = reportData.criticalPathTasks.size == 0

  override val treeRoot: DefaultMutableTreeNode
    get() = when (selectedGrouping) {
      TasksDataPageModel.Grouping.UNGROUPED -> treeStructure.ungroupedTasksNodes
      TasksDataPageModel.Grouping.BY_PLUGIN -> treeStructure.groupedByPluginTasksNodes
    }

  private fun updateSelectedNodeOnGroupingChange() {
    val oldSelectedNode = selectedNode
    if (oldSelectedNode != null) {
      val currentPageId = oldSelectedNode.descriptor.pageId
      val newPageId = currentPageId.copy(grouping = selectedGrouping)
      selectedNode = treeStructure.pageIdToNode[newPageId] ?: treeStructure.defaultNode
    }
  }

  override fun selectGrouping(newSelectedGrouping: TasksDataPageModel.Grouping) {
    selectedGrouping = newSelectedGrouping
    notifyModelChanges()
  }

  override fun selectNode(tasksTreeNode: TasksTreeNode) {
    selectedNode = tasksTreeNode
    notifyModelChanges()
  }

  override fun selectPageById(tasksPageId: TasksPageId) {
    treeStructure.pageIdToNode[tasksPageId]?.let { selectNode(it) }
  }

  override fun setModelUpdatedListener(listener: () -> Unit) {
    modelUpdatedListener = listener
  }

  private fun notifyModelChanges() {
    if (modelChanged) {
      modelUpdatedListener?.invoke()
      modelChanged = false
    }
  }
}

private class TasksTreeStructure(
  val reportData: BuildAttributionReportUiData
) {

  val pageIdToNode: MutableMap<TasksPageId, TasksTreeNode> = mutableMapOf()

  val ungroupedTasksNodes = DefaultMutableTreeNode().apply {
    reportData.criticalPathTasks.tasks.forEach {
      add(treeNode(TaskDetailsNodeDescriptor(it, TasksDataPageModel.Grouping.UNGROUPED)))
    }
  }

  val groupedByPluginTasksNodes = DefaultMutableTreeNode().apply {
    reportData.criticalPathPlugins.plugins.forEach { pluginUiData ->
      add(treeNode(PluginDetailsNodeDescriptor(pluginUiData)).apply {
        pluginUiData.criticalPathTasks.tasks.forEach { taskUiData ->
          add(treeNode(TaskDetailsNodeDescriptor(taskUiData, TasksDataPageModel.Grouping.BY_PLUGIN)))
        }
      })
    }
  }

  val defaultNode: TasksTreeNode? = ungroupedTasksNodes.nextNode as? TasksTreeNode

  private fun treeNode(descriptor: TasksTreePresentableNodeDescriptor): TasksTreeNode = TasksTreeNode(descriptor).apply {
    pageIdToNode[descriptor.pageId] = this
  }
}

class TasksTreeNode(
  val descriptor: TasksTreePresentableNodeDescriptor
) : DefaultMutableTreeNode(descriptor)

enum class TaskDetailsPageType {
  TASK_DETAILS,
  PLUGIN_DETAILS
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
  }
}

sealed class TasksTreePresentableNodeDescriptor {
  abstract val pageId: TasksPageId
  abstract val analyticsPageType: PageType
  abstract val presentation: BuildAnalyzerTreeNodePresentation
  override fun toString(): String = presentation.mainText
}

/** Tasks tree node descriptor that holds task node data and presentation. */
class TaskDetailsNodeDescriptor(
  val taskData: TaskUiData,
  grouping: TasksDataPageModel.Grouping
) : TasksTreePresentableNodeDescriptor() {
  override val pageId = TasksPageId.task(taskData, grouping)
  override val analyticsPageType = when (grouping) {
    TasksDataPageModel.Grouping.UNGROUPED -> PageType.CRITICAL_PATH_TASK_PAGE
    TasksDataPageModel.Grouping.BY_PLUGIN -> PageType.PLUGIN_CRITICAL_PATH_TASK_PAGE
  }
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = taskData.taskPath,
      rightAlignedSuffix = taskData.executionTime.toRightAlignedNodeDurationText(),
      nodeIconState = if (taskData.hasWarning) WARNING_ICON else EMPTY_PLACEHOLDER,
      showChartKey = pageId.grouping == TasksDataPageModel.Grouping.UNGROUPED
    )
}

/** Tasks tree node descriptor that holds plugin node data and presentation. */
class PluginDetailsNodeDescriptor(
  val pluginData: CriticalPathPluginUiData
) : TasksTreePresentableNodeDescriptor() {
  override val pageId = TasksPageId.plugin(pluginData)
  override val analyticsPageType = PageType.PLUGIN_PAGE
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = pluginData.name,
      suffix = warningsCountString(pluginData.warningCount),
      rightAlignedSuffix = pluginData.criticalPathDuration.toRightAlignedNodeDurationText(),
      showChartKey = true
    )
}

private fun TimeWithPercentage.toRightAlignedNodeDurationText() = "%.1fs %4.1f%%".format(timeS, percentage)