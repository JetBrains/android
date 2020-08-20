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

import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

data class WarningsFilter(
  val showTaskSourceTypes: Set<PluginSourceType>,
  val showTaskWarningTypes: Set<TaskIssueType>,
  val showAnnotationProcessorWarnings: Boolean,
  val showNonCriticalPathTasks: Boolean
) {

  fun acceptTaskIssue(issueData: TaskIssueUiData): Boolean =
    showTaskWarningTypes.contains(issueData.type) &&
    showTaskSourceTypes.contains(issueData.task.sourceType) &&
    (showNonCriticalPathTasks || issueData.task.onExtendedCriticalPath)

  fun acceptAnnotationProcessorIssue(annotationProcessorData: AnnotationProcessorUiData): Boolean = showAnnotationProcessorWarnings

  companion object {
    fun default() = WarningsFilter(
      showTaskSourceTypes = setOf(PluginSourceType.ANDROID_PLUGIN, PluginSourceType.THIRD_PARTY, PluginSourceType.BUILD_SRC),
      showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS, TaskIssueType.TASK_SETUP_ISSUE),
      showAnnotationProcessorWarnings = true,
      showNonCriticalPathTasks = false
    )
  }
}

private abstract class WarningsFilterToggleAction(
  uiName: String,
  val warningsModel: WarningsDataPageModel,
  val actionHandlers: ViewActionHandlers
) : ToggleAction(uiName), DumbAware {

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val updatedFilter = if (state) onAdd(warningsModel.filter)
    else onRemove(warningsModel.filter)
    actionHandlers.applyWarningsFilter(updatedFilter)
  }

  override fun isSelected(e: AnActionEvent): Boolean = isSelected(warningsModel.filter)

  abstract fun onAdd(filter: WarningsFilter): WarningsFilter
  abstract fun onRemove(filter: WarningsFilter): WarningsFilter
  abstract fun isSelected(filter: WarningsFilter): Boolean
}

private class WarningTypeFilterToggleAction(
  uiName: String,
  val warningType: TaskIssueType,
  warningsModel: WarningsDataPageModel,
  actionHandlers: ViewActionHandlers
) : WarningsFilterToggleAction(uiName, warningsModel, actionHandlers) {
  override fun onAdd(filter: WarningsFilter): WarningsFilter =
    filter.copy(showTaskWarningTypes = filter.showTaskWarningTypes.plus(warningType))

  override fun onRemove(filter: WarningsFilter): WarningsFilter =
    filter.copy(showTaskWarningTypes = filter.showTaskWarningTypes.minus(warningType))

  override fun isSelected(filter: WarningsFilter): Boolean = filter.showTaskWarningTypes.contains(warningType)
}

private class TaskSourceTypeWarningFilterToggleAction(
  uiName: String,
  val sourceType: PluginSourceType,
  warningsModel: WarningsDataPageModel,
  actionHandlers: ViewActionHandlers
) : WarningsFilterToggleAction(uiName, warningsModel, actionHandlers) {
  override fun onAdd(filter: WarningsFilter): WarningsFilter =
    filter.copy(showTaskSourceTypes = filter.showTaskSourceTypes.plus(sourceType))

  override fun onRemove(filter: WarningsFilter): WarningsFilter =
    filter.copy(showTaskSourceTypes = filter.showTaskSourceTypes.minus(sourceType))

  override fun isSelected(filter: WarningsFilter): Boolean = filter.showTaskSourceTypes.contains(sourceType)
}

private class AnnotationProcessorWarningsFilterToggleAction(
  uiName: String,
  warningsModel: WarningsDataPageModel,
  actionHandlers: ViewActionHandlers
) : WarningsFilterToggleAction(uiName, warningsModel, actionHandlers) {
  override fun onAdd(filter: WarningsFilter): WarningsFilter = filter.copy(showAnnotationProcessorWarnings = true)

  override fun onRemove(filter: WarningsFilter): WarningsFilter = filter.copy(showAnnotationProcessorWarnings = false)

  override fun isSelected(filter: WarningsFilter): Boolean = filter.showAnnotationProcessorWarnings
}

private class NonCriticalPathTaskWarningsFilterToggleAction(
  uiName: String,
  warningsModel: WarningsDataPageModel,
  actionHandlers: ViewActionHandlers
) : WarningsFilterToggleAction(uiName, warningsModel, actionHandlers) {
  override fun onAdd(filter: WarningsFilter): WarningsFilter = filter.copy(showNonCriticalPathTasks = true)

  override fun onRemove(filter: WarningsFilter): WarningsFilter = filter.copy(showNonCriticalPathTasks = false)

  override fun isSelected(filter: WarningsFilter): Boolean = filter.showNonCriticalPathTasks
}

fun warningsFilterActions(model: WarningsDataPageModel, actionHandlers: ViewActionHandlers): ActionGroup {
  val actionGroup = DefaultActionGroup("Filters", true).apply {
    templatePresentation.icon = AllIcons.Actions.Show
    add(WarningTypeFilterToggleAction("Show Always-run tasks", TaskIssueType.ALWAYS_RUN_TASKS, model, actionHandlers))
    add(WarningTypeFilterToggleAction("Show Task Setup issues", TaskIssueType.TASK_SETUP_ISSUE, model, actionHandlers))
    addSeparator()
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for Android/Java/Kotlin plugins", PluginSourceType.ANDROID_PLUGIN, model, actionHandlers
    ))
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for other plugins", PluginSourceType.THIRD_PARTY, model, actionHandlers
    ))
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for project customization", PluginSourceType.BUILD_SRC, model, actionHandlers
    ))
    addSeparator()
    add(AnnotationProcessorWarningsFilterToggleAction("Show annotation processors issues", model, actionHandlers))
    add(NonCriticalPathTaskWarningsFilterToggleAction(
      "Include issues for tasks non determining this build duration", model, actionHandlers
    ))
  }
  return actionGroup
}

data class TasksFilter(
  val showTaskSourceTypes: Set<PluginSourceType>,
  val showTasksWithoutWarnings: Boolean
) {

  fun acceptTask(taskData: TaskUiData): Boolean =
    (showTasksWithoutWarnings || taskData.hasWarning) &&
    showTaskSourceTypes.contains(taskData.sourceType)

  companion object {
    fun default() = TasksFilter(
      showTaskSourceTypes = setOf(PluginSourceType.ANDROID_PLUGIN, PluginSourceType.THIRD_PARTY, PluginSourceType.BUILD_SRC),
      showTasksWithoutWarnings = true
    )
  }
}

private abstract class TasksFilterToggleAction(
  uiName: String,
  val tasksModel: TasksDataPageModel,
  val actionHandlers: ViewActionHandlers
) : ToggleAction(uiName), DumbAware {

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val updatedFilter = if (state) onAdd(tasksModel.filter) else onRemove(tasksModel.filter)
    actionHandlers.applyTasksFilter(updatedFilter)
  }

  override fun isSelected(e: AnActionEvent): Boolean = isSelected(tasksModel.filter)

  abstract fun onAdd(filter: TasksFilter): TasksFilter
  abstract fun onRemove(filter: TasksFilter): TasksFilter
  abstract fun isSelected(filter: TasksFilter): Boolean
}

private class TaskSourceTypeTasksFilterToggleAction(
  uiName: String,
  val sourceType: PluginSourceType,
  tasksModel: TasksDataPageModel,
  actionHandlers: ViewActionHandlers
) : TasksFilterToggleAction(uiName, tasksModel, actionHandlers) {
  override fun onAdd(filter: TasksFilter): TasksFilter =
    filter.copy(showTaskSourceTypes = filter.showTaskSourceTypes + sourceType)

  override fun onRemove(filter: TasksFilter): TasksFilter =
    filter.copy(showTaskSourceTypes = filter.showTaskSourceTypes - sourceType)

  override fun isSelected(filter: TasksFilter): Boolean = filter.showTaskSourceTypes.contains(sourceType)
}

private class TasksWithoutWarningsFilterToggleAction(
  uiName: String,
  tasksModel: TasksDataPageModel,
  actionHandlers: ViewActionHandlers
) : TasksFilterToggleAction(uiName, tasksModel, actionHandlers) {
  override fun isSelected(filter: TasksFilter): Boolean = filter.showTasksWithoutWarnings

  override fun onAdd(filter: TasksFilter): TasksFilter = filter.copy(showTasksWithoutWarnings = true)

  override fun onRemove(filter: TasksFilter): TasksFilter = filter.copy(showTasksWithoutWarnings = false)

}

fun tasksFilterActions(model: TasksDataPageModel, actionHandlers: ViewActionHandlers): ActionGroup =
  DefaultActionGroup("Filters", true).apply {
    templatePresentation.icon = AllIcons.Actions.Show
    add(TaskSourceTypeTasksFilterToggleAction(
      "Show tasks for Android/Java/Kotlin plugins", PluginSourceType.ANDROID_PLUGIN, model, actionHandlers
    ))
    add(TaskSourceTypeTasksFilterToggleAction("Show tasks for other plugins", PluginSourceType.THIRD_PARTY, model, actionHandlers))
    add(TaskSourceTypeTasksFilterToggleAction(
      "Show tasks for project customization", PluginSourceType.BUILD_SRC, model, actionHandlers
    ))
    addSeparator()
    add(TasksWithoutWarningsFilterToggleAction("Show tasks without warnings", model, actionHandlers))
  }