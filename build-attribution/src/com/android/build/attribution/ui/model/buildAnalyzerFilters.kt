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

import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.ClickListener
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.popup.KeepingPopupOpenAction
import com.intellij.ui.popup.util.PopupState
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.LafIconLookup
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

data class WarningsFilter(
  val showTaskSourceTypes: Set<PluginSourceType>,
  val showTaskWarningTypes: Set<TaskIssueType>,
  val showAnnotationProcessorWarnings: Boolean,
  val showNonCriticalPathTasks: Boolean,
  val showConfigurationCacheWarnings: Boolean,
  val showJetifierWarnings: Boolean
) {

  fun acceptTaskIssue(issueData: TaskIssueUiData): Boolean =
    showTaskWarningTypes.contains(issueData.type) &&
    showTaskSourceTypes.contains(issueData.task.sourceType) &&
    (showNonCriticalPathTasks || issueData.task.onExtendedCriticalPath)

  fun toUiText(): String {
    val taskWarningsPart = when {
      showTaskSourceTypes.isEmpty() || showTaskWarningTypes.isEmpty() -> ""
      showTaskSourceTypes.containsAll(PluginSourceType.values().asList())
      && showTaskWarningTypes.containsAll(TaskIssueType.values().asList()) -> "All task warnings"
      else -> "Selected types of task warnings"
    }

    val annotationProcessorsPart = if (showAnnotationProcessorWarnings) "Annotation processors" else ""
    val configurationCachePart = if (showConfigurationCacheWarnings) "Configuration cache" else ""
    val jetifierPart = if (showJetifierWarnings) "Jetifier" else ""

    return sequenceOf(taskWarningsPart, annotationProcessorsPart, configurationCachePart, jetifierPart)
             .filter { it.isNotBlank() }
             .joinToString(separator = ", ")
             .takeIf { it.isNotBlank() } ?: "Nothing selected"
  }

  companion object {
    val DEFAULT = WarningsFilter(
      showTaskSourceTypes = setOf(PluginSourceType.ANDROID_PLUGIN, PluginSourceType.THIRD_PARTY, PluginSourceType.BUILD_SRC),
      showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS, TaskIssueType.TASK_SETUP_ISSUE),
      showAnnotationProcessorWarnings = true,
      showNonCriticalPathTasks = false,
      showConfigurationCacheWarnings = true,
      showJetifierWarnings = true
    )
  }
}

abstract class WarningsFilterToggleAction(
  uiName: String,
  private val warningsModel: WarningsDataPageModel,
  val actionHandlers: ViewActionHandlers
) : AnAction(uiName), DumbAware, KeepingPopupOpenAction {

  private val toggleableIcon = LayeredIcon(EmptyIcon.ICON_16, LafIconLookup.getIcon("checkmark"))
  private val toggleableSelectedIcon = LayeredIcon(EmptyIcon.ICON_16, LafIconLookup.getSelectedIcon("checkmark"))

  init {
    templatePresentation.icon = toggleableIcon
    templatePresentation.selectedIcon = toggleableSelectedIcon
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    // Popup list used in action menu does not refresh action presentation (e.g. icon) while popup stays open.
    // Use LayeredIcon instead in order to show checked items, switch checkmark layer on and off
    // depending on the actual action state.
    // Note that setSelected()/isSelected() methods of this class and *Selected icon is an unfortunate name clashing,
    // they correspond to different notions:
    // - setSelected()/isSelected() correspond to the action state, if is should be marked as being 'on'.
    // - 'selected' icon is used when list row is under selection and rendered in selection color (e.g. dark blue).
    val selected = isSelected(warningsModel.filter)
    toggleableIcon.setLayerEnabled(1, selected)
    toggleableSelectedIcon.setLayerEnabled(1, selected)
  }

  private fun setSelected(state: Boolean) {
    toggleableIcon.setLayerEnabled(1, state)
    toggleableSelectedIcon.setLayerEnabled(1, state)
    val updatedFilter = if (state) onAdd(warningsModel.filter)
    else onRemove(warningsModel.filter)
    actionHandlers.applyWarningsFilter(updatedFilter)
  }

  override fun actionPerformed(e: AnActionEvent) = setSelected(!isSelected(warningsModel.filter))

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


private class BoolValueWarningsFilterToggleAction(
  uiName: String,
  warningsModel: WarningsDataPageModel,
  actionHandlers: ViewActionHandlers,
  val valueGetter: (WarningsFilter) -> Boolean,
  val valueSetter: (WarningsFilter, Boolean) -> WarningsFilter
) : WarningsFilterToggleAction(uiName, warningsModel, actionHandlers) {
  override fun onAdd(filter: WarningsFilter): WarningsFilter = valueSetter(filter, true)
  override fun onRemove(filter: WarningsFilter): WarningsFilter = valueSetter(filter, false)
  override fun isSelected(filter: WarningsFilter): Boolean = valueGetter(filter)
}

fun warningsFilterActions(model: WarningsDataPageModel, actionHandlers: ViewActionHandlers): ActionGroup {
  return FilterComponentAction(
    subscribeToModelUpdates = { r: Runnable -> model.addModelUpdatedListener { r.run() } },
    getModelUIText = { model.filter.toUiText() }
  ).apply {
    addAction(object : AnAction("Reset filters to default") {
      override fun actionPerformed(unused: AnActionEvent) = actionHandlers.applyWarningsFilter(WarningsFilter.DEFAULT)
    })
    addSeparator("By task warning type")
    add(WarningTypeFilterToggleAction("Show Always-run tasks", TaskIssueType.ALWAYS_RUN_TASKS, model, actionHandlers))
    add(WarningTypeFilterToggleAction("Show Task Setup issues", TaskIssueType.TASK_SETUP_ISSUE, model, actionHandlers))
    addSeparator("By task type")
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for Android/Java/Kotlin plugins", PluginSourceType.ANDROID_PLUGIN, model, actionHandlers
    ))
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for other plugins", PluginSourceType.THIRD_PARTY, model, actionHandlers
    ))
    add(TaskSourceTypeWarningFilterToggleAction(
      "Show issues for project customization", PluginSourceType.BUILD_SRC, model, actionHandlers
    ))
    add(BoolValueWarningsFilterToggleAction(
      "Include issues for tasks non determining this build duration", model, actionHandlers,
      valueGetter = { filter -> filter.showNonCriticalPathTasks },
      valueSetter = { filter, value -> filter.copy(showNonCriticalPathTasks = value) }
    ))
    addSeparator("Other warnings")
    add(BoolValueWarningsFilterToggleAction("Show annotation processors issues", model, actionHandlers,
                                            valueGetter = { filter -> filter.showAnnotationProcessorWarnings },
                                            valueSetter = { filter, value -> filter.copy(showAnnotationProcessorWarnings = value) }))
    add(BoolValueWarningsFilterToggleAction("Show configuration cache issues", model, actionHandlers,
                                            valueGetter = { filter -> filter.showConfigurationCacheWarnings },
                                            valueSetter = { filter, value -> filter.copy(showConfigurationCacheWarnings = value) }))
    add(BoolValueWarningsFilterToggleAction("Show Jetifier usage warning", model, actionHandlers,
                                            valueGetter = { filter -> filter.showJetifierWarnings },
                                            valueSetter = { filter, value -> filter.copy(showJetifierWarnings = value) }))
  }
}

fun warningsFilterComponent(model: WarningsDataPageModel, actionHandlers: ViewActionHandlers): Component {
  return JPanel().apply {
    add(FilterCustomComponent(
      warningsFilterActions(model, actionHandlers),
      subscribeToModelUpdates = { r: Runnable -> model.addModelUpdatedListener { r.run() } },
      getModelUIText = { model.filter.toUiText() }
    ))
  }
}

data class TasksFilter(
  val showTaskSourceTypes: Set<PluginSourceType>,
  val showTasksWithoutWarnings: Boolean
) {

  fun acceptTask(taskData: TaskUiData): Boolean =
    (showTasksWithoutWarnings || taskData.hasWarning) &&
    showTaskSourceTypes.contains(taskData.sourceType)

  fun toUiText(): String {
    if (showTaskSourceTypes.isEmpty()) return "No task types selected"
    val taskTypesPart = if (showTaskSourceTypes.containsAll(PluginSourceType.values().asList()))
      "All tasks"
    else
      PluginSourceType.values()
        .filter { it in showTaskSourceTypes }
        .joinToString(
          separator = ", ",
          postfix = " tasks"
        ) { it.toFilterUiShortName() }
    return if (showTasksWithoutWarnings) taskTypesPart else "$taskTypesPart with warnings"
  }

  companion object {
    val DEFAULT = TasksFilter(
      showTaskSourceTypes = setOf(PluginSourceType.ANDROID_PLUGIN, PluginSourceType.THIRD_PARTY, PluginSourceType.BUILD_SRC),
      showTasksWithoutWarnings = true
    )
  }
}

private fun PluginSourceType.toFilterUiShortName(): String = when (this) {
  PluginSourceType.BUILD_SRC -> "Project customization"
  PluginSourceType.ANDROID_PLUGIN -> "Android/Java/Kotlin"
  PluginSourceType.THIRD_PARTY -> "Other"
}

abstract class TasksFilterToggleAction(
  uiName: String,
  private val tasksModel: TasksDataPageModel,
  val actionHandlers: ViewActionHandlers
) : AnAction(uiName), DumbAware, KeepingPopupOpenAction {

  private val toggleableIcon = LayeredIcon(EmptyIcon.ICON_16, LafIconLookup.getIcon("checkmark"))
  private val toggleableSelectedIcon = LayeredIcon(EmptyIcon.ICON_16, LafIconLookup.getSelectedIcon("checkmark"))

  init {
    templatePresentation.icon = toggleableIcon
    templatePresentation.selectedIcon = toggleableSelectedIcon
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    // Popup list used in action menu does not refresh action presentation (e.g. icon) while popup stays open.
    // Use LayeredIcon instead in order to show checked items, switch checkmark layer on and off
    // depending on the actual action state.
    // Note that setSelected()/isSelected() methods of this class and *Selected icon is an unfortunate name clashing,
    // they correspond to different notions:
    // - setSelected()/isSelected() correspond to the action state, if is should be marked as being 'on'.
    // - 'selected' icon is used when list row is under selection and rendered in selection color (e.g. dark blue).
    val selected = isSelected(tasksModel.filter)
    toggleableIcon.setLayerEnabled(1, selected)
    toggleableSelectedIcon.setLayerEnabled(1, selected)
  }

  private fun setSelected(state: Boolean) {
    toggleableIcon.setLayerEnabled(1, state)
    toggleableSelectedIcon.setLayerEnabled(1, state)
    val updatedFilter = if (state) onAdd(tasksModel.filter) else onRemove(tasksModel.filter)
    actionHandlers.applyTasksFilter(updatedFilter)
  }

  override fun actionPerformed(e: AnActionEvent) = setSelected(!isSelected(tasksModel.filter))

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
    addAction(object : AnAction("Reset filters to default") {
      override fun actionPerformed(unused: AnActionEvent) = actionHandlers.applyTasksFilter(TasksFilter.DEFAULT)
    })
    addSeparator()
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

fun tasksFilterComponent(model: TasksDataPageModel, actionHandlers: ViewActionHandlers): Component =
  JPanel().apply {
    add(FilterCustomComponent(
      tasksFilterActions(model, actionHandlers),
      subscribeToModelUpdates = { r: Runnable -> model.addModelUpdatedListener { r.run() } },
      getModelUIText = { model.filter.toUiText() }
    ))
  }

class FilterComponentAction(
  val subscribeToModelUpdates: (Runnable) -> Unit,
  val getModelUIText: () -> String
) : DefaultActionGroup("Filters", true), CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    FilterCustomComponent(this, subscribeToModelUpdates, getModelUIText)
}

private class FilterCustomComponent(
  val filterActions: ActionGroup,
  val subscribeToModelUpdates: (Runnable) -> Unit,
  val getModelUIText: () -> String
) : JPanel() {
  private val popupState = PopupState()
  private val nameLabel = JLabel("Filters: ")
  private val valueLabel = JLabel(getModelUIText())

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isFocusable = true

    add(nameLabel)
    add(valueLabel)
    add(Box.createHorizontalStrut(3))
    add(JLabel(AllIcons.Ide.Statusbar_arrows))
    subscribeToModelUpdates {
      valueLabel.text = getModelUIText()
    }
    installShowPopupMenuOnClick()
    installShowPopupMenuFromKeyboard()
    installFocusIndication()
  }

  private fun installFocusIndication() {
    fun setFocusedBorder() {
      border = BorderFactory.createCompoundBorder(
        RoundedLineBorder(UIUtil.getFocusedBorderColor(), 10, 2),
        JBUI.Borders.empty(2)
      )
    }

    fun setUnFocusedBorder() {
      border = JBUI.Borders.empty(4)
    }
    setUnFocusedBorder()
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) = setFocusedBorder()
      override fun focusLost(e: FocusEvent) = setUnFocusedBorder()
    })
  }

  private fun installShowPopupMenuOnClick() {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        showPopupMenu()
        return true
      }
    }.installOn(this)
  }

  private fun installShowPopupMenuFromKeyboard() {
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_DOWN || e.keyCode == KeyEvent.VK_SPACE) {
          showPopupMenu()
        }
      }
    })
  }

  private fun showPopupMenu() {
    if (popupState.isRecentlyHidden) return  // Do not show new popup.

    val popup: ListPopup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, filterActions,
      DataManager.getInstance().getDataContext(this),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true
    )
    popup.addListener(popupState)

    AutoPopupSupportingListener.installOn(popup)
    popup.showUnderneathOf(this)
  }
}
