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

import com.android.build.attribution.analyzers.AGPUpdateRequired
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOff
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.IncompatiblePluginWarning
import com.android.build.attribution.analyzers.IncompatiblePluginsDetected
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoDataFromSavedResult
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.android.build.attribution.ui.warningsCountString
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.tree.DefaultMutableTreeNode

interface WarningsDataPageModel {
  /** Text of the header visible above the tree. */
  val treeHeaderText: String

  /** The root of the tree that should be shown now. View is supposed to set this root in the Tree on update. */
  val treeRoot: DefaultMutableTreeNode

  var groupByPlugin: Boolean

  /** Currently selected node. Can be null in case of an empty tree. */
  val selectedNode: WarningsTreeNode?

  /** True if there are no warnings to show. */
  val isEmpty: Boolean

  var filter: WarningsFilter

  /**
   * Selects node in a tree to provided.
   * Provided node object should be the one created in this model and exist in the trees it holds.
   * Null means changing to empty selection.
   * Notifies listener if model state changes.
   */
  fun selectNode(warningsTreeNode: WarningsTreeNode?)

  /** Looks for the tree node by it's pageId and selects it as described in [selectNode] if found. */
  fun selectPageById(warningsPageId: WarningsPageId)

  /** Install the listener that will be called on model state changes. */
  fun addModelUpdatedListener(disposable: Disposable, listener: (Boolean) -> Unit)

  /** Retrieve node descriptor by it's page id. Null if node does not exist in currently presented tree structure. */
  fun getNodeDescriptorById(pageId: WarningsPageId): WarningsTreePresentableNodeDescriptor?
}

class WarningsDataPageModelImpl(
  private val reportData: BuildAttributionReportUiData
) : WarningsDataPageModel {

  private val modelUpdatedListeners: MutableList<((treeStructureChanged: Boolean) -> Unit)> = CopyOnWriteArrayList()
  @VisibleForTesting
  val listenersCount: Int get() = modelUpdatedListeners.size

  override val treeHeaderText: String
    get() = treeStructure.treeStats.let { treeStats ->
      "Warnings - Total: ${treeStats.totalWarningsCount}, Filtered: ${treeStats.filteredWarningsCount}"
    }

  override var filter: WarningsFilter = WarningsFilter.DEFAULT
    set(value) {
      field = value
      treeStructure.updateStructure(groupByPlugin, value)
      dropSelectionIfMissing()
      treeStructureChanged = true
      modelChanged = true
      notifyModelChanges()
    }

  override var groupByPlugin: Boolean = false
    set(value) {
      field = value
      treeStructure.updateStructure(value, filter)
      dropSelectionIfMissing()
      treeStructureChanged = true
      modelChanged = true
      notifyModelChanges()
    }

  private val treeStructure = WarningsTreeStructure(reportData).apply {
    updateStructure(groupByPlugin, filter)
  }

  override val treeRoot: DefaultMutableTreeNode
    get() = treeStructure.treeRoot

  // True when there are changes since last listener call.
  private var modelChanged = false

  // TODO (mlazeba): this starts look wrong. Can we provide TreeModel instead of a root node? what are the pros and cons? what are the other options?
  //   idea 1) make listener have multiple methods: tree updated, selection updated, etc.
  //   idea 2) provide TreeModel instead of root. then that model updates tree itself on changes.
  // True when tree changed it's structure since last listener call.
  private var treeStructureChanged = false

  private var selectedPageId: WarningsPageId = when {
    reportData.jetifierData.checkJetifierBuild -> WarningsPageId.jetifierUsageWarningRoot
    else -> WarningsPageId.emptySelection
  }
    private set(value) {
      if (value != field) {
        field = value
        modelChanged = true
      }
    }

  override val selectedNode: WarningsTreeNode?
    get() = treeStructure.pageIdToNode[selectedPageId]

  override val isEmpty: Boolean
    get() = reportData.issues.sumOf { it.warningCount } +
      reportData.annotationProcessors.issueCount +
      reportData.confCachingData.warningsCount() +
      (reportData.criticalPathTaskCategories?.entries ?: emptyList()).sumOf { category ->
        category.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = true).size
      } == 0

  override fun selectNode(warningsTreeNode: WarningsTreeNode?) {
    selectedPageId = warningsTreeNode?.descriptor?.pageId ?: WarningsPageId.emptySelection
    notifyModelChanges()
  }

  override fun selectPageById(warningsPageId: WarningsPageId) {
    treeStructure.pageIdToNode[warningsPageId]?.let { selectNode(it) }
  }

  override fun addModelUpdatedListener(disposable: Disposable, listener: (Boolean) -> Unit) {
    modelUpdatedListeners.add(listener)
    Disposer.register(disposable) { modelUpdatedListeners.remove(listener) }
  }

  override fun getNodeDescriptorById(pageId: WarningsPageId): WarningsTreePresentableNodeDescriptor? =
    treeStructure.pageIdToNode[pageId]?.descriptor

  private fun dropSelectionIfMissing() {
    if (!treeStructure.pageIdToNode.containsKey(selectedPageId)) {
      selectedPageId = WarningsPageId.emptySelection
    }
  }

  private fun notifyModelChanges() {
    if (modelChanged) {
      modelUpdatedListeners.forEach { it.invoke(treeStructureChanged) }
      modelChanged = false
      treeStructureChanged = false
    }
  }
}

private class WarningsTreeStructure(
  val reportData: BuildAttributionReportUiData
) {

  val pageIdToNode: MutableMap<WarningsPageId, WarningsTreeNode> = mutableMapOf()

  val treeStats: TreeStats = TreeStats()

  private fun treeNode(descriptor: WarningsTreePresentableNodeDescriptor) = WarningsTreeNode(descriptor).apply {
    pageIdToNode[descriptor.pageId] = this
  }

  var treeRoot = DefaultMutableTreeNode()

  fun updateStructure(groupByPlugin: Boolean, filter: WarningsFilter) {
    pageIdToNode.clear()
    treeStats.clear()
    treeRoot.removeAllChildren()

    val warningsToAdd = mutableListOf<WarningsTreeNode>()

    val taskWarnings = reportData.issues.asSequence()
      .flatMap { it.issues.asSequence() }
      .filter { filter.acceptTaskIssue(it) }
      .toList()
    treeStats.filteredWarningsCount += taskWarnings.size

    if (groupByPlugin) {
      taskWarnings.groupBy { it.task.pluginName }.forEach { (pluginName, warnings) ->
        val warningsByTask = warnings.groupBy { it.task }
        val pluginTreeGroupingNode = treeNode(PluginGroupingWarningNodeDescriptor(pluginName, warningsByTask))
        warningsToAdd.add(pluginTreeGroupingNode)
        warningsByTask.forEach { (task, warnings) ->
          pluginTreeGroupingNode.add(treeNode(TaskUnderPluginDetailsNodeDescriptor(task, warnings)))
        }
      }
    }
    else {
      taskWarnings.groupBy { it.type }.forEach { (type, warnings) ->
        val warningTypeGroupingNodeDescriptor = TaskWarningTypeNodeDescriptor(type, warnings)
        val warningTypeGroupingNode = treeNode(warningTypeGroupingNodeDescriptor)
        warningsToAdd.add(warningTypeGroupingNode)
        warnings.map { TaskWarningDetailsNodeDescriptor(it) }.forEach { taskIssueNodeDescriptor ->
          warningTypeGroupingNode.add(treeNode(taskIssueNodeDescriptor))
        }
      }
    }

    if (filter.showAnnotationProcessorWarnings) {
      reportData.annotationProcessors.nonIncrementalProcessors.asSequence()
        .map { AnnotationProcessorDetailsNodeDescriptor(it) }
        .toList()
        .ifNotEmpty {
          val annotationProcessorsRootNode = treeNode(AnnotationProcessorsRootNodeDescriptor(reportData.annotationProcessors))
          warningsToAdd.add(annotationProcessorsRootNode)
          forEach {
            annotationProcessorsRootNode.add(treeNode(it))
          }
          treeStats.filteredWarningsCount += size
        }
    }

    // Add configuration caching issues
    if (filter.showConfigurationCacheWarnings && reportData.confCachingData.shouldShowWarning()) {
      val configurationDuration = reportData.buildSummary.configurationDuration
      val configurationCacheData = reportData.confCachingData
      warningsToAdd.add(treeNode(ConfigurationCachingRootNodeDescriptor(configurationCacheData, configurationDuration)).apply {
        if (configurationCacheData is IncompatiblePluginsDetected) {
          configurationCacheData.upgradePluginWarnings.forEach {
            add(treeNode(ConfigurationCachingWarningNodeDescriptor(it, configurationDuration)))
          }
          configurationCacheData.incompatiblePluginWarnings.forEach {
            add(treeNode(ConfigurationCachingWarningNodeDescriptor(it, configurationDuration)))
          }
        }
      })
      treeStats.filteredWarningsCount += configurationCacheData.warningsCount()
    }

    // Add Jetifier usage warning
    if (reportData.jetifierData.shouldShowWarning()) {
      if (filter.showJetifierWarnings) {
        warningsToAdd.add(treeNode(JetifierUsageWarningRootNodeDescriptor(reportData.jetifierData)))
        treeStats.filteredWarningsCount++
      }
    }

    reportData.criticalPathTaskCategories?.entries?.map { criticalPathTaskCategoryData ->
      val warnings = criticalPathTaskCategoryData.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = true)
      if (warnings.isNotEmpty()) {
        warningsToAdd.add(treeNode(TaskCategoryWarningNodeDescriptor(criticalPathTaskCategoryData)))
        treeStats.filteredWarningsCount += warnings.size
      }
    }

    warningsToAdd.sortedBy {
      it.descriptor
    }.forEach(treeRoot::add)

    treeStats.totalWarningsCount = reportData.countTotalWarnings()
  }

  class TreeStats {
    var totalWarningsCount: Int = 0
    var filteredWarningsCount: Int = 0

    fun clear() {
      totalWarningsCount = 0
      filteredWarningsCount = 0
    }
  }
}

class WarningsTreeNode(
  val descriptor: WarningsTreePresentableNodeDescriptor
) : DefaultMutableTreeNode(descriptor)

// TODO (mlazeba): consider removing this class as it is not really used
enum class WarningsPageType {
  EMPTY_SELECTION,
  TASK_WARNING_DETAILS,
  TASK_WARNING_TYPE_GROUP,
  TASK_UNDER_PLUGIN,
  TASK_WARNING_PLUGIN_GROUP,
  ANNOTATION_PROCESSOR_DETAILS,
  ANNOTATION_PROCESSOR_GROUP,
  CONFIGURATION_CACHING_ROOT,
  CONFIGURATION_CACHING_WARNING,
  JETIFIER_USAGE_WARNING,
  TASK_CATEGORY_WARNING
}

data class WarningsPageId(
  val pageType: WarningsPageType,
  val id: String
) {
  companion object {
    fun warning(warning: TaskIssueUiData) =
      WarningsPageId(WarningsPageType.TASK_WARNING_DETAILS, "${warning.type}-${warning.task.taskPath}")

    fun task(task: TaskUiData) = WarningsPageId(WarningsPageType.TASK_UNDER_PLUGIN, task.taskPath)

    fun warningType(warningType: TaskIssueType) = WarningsPageId(WarningsPageType.TASK_WARNING_TYPE_GROUP, warningType.name)
    fun warningPlugin(warningPluginName: String) = WarningsPageId(WarningsPageType.TASK_WARNING_PLUGIN_GROUP, warningPluginName)

    fun annotationProcessor(annotationProcessorData: AnnotationProcessorUiData) = WarningsPageId(
      WarningsPageType.ANNOTATION_PROCESSOR_DETAILS, annotationProcessorData.className)

    fun configurationCachingWarning(data: IncompatiblePluginWarning) =
      WarningsPageId(WarningsPageType.CONFIGURATION_CACHING_WARNING, data.plugin.toString())

    fun taskCategory(taskCategory: TaskCategory) = WarningsPageId(WarningsPageType.TASK_CATEGORY_WARNING, taskCategory.toString())

    val annotationProcessorRoot = WarningsPageId(WarningsPageType.ANNOTATION_PROCESSOR_GROUP, "ANNOTATION_PROCESSORS")
    val configurationCachingRoot = WarningsPageId(WarningsPageType.CONFIGURATION_CACHING_ROOT, "CONFIGURATION_CACHING")
    val jetifierUsageWarningRoot = WarningsPageId(WarningsPageType.JETIFIER_USAGE_WARNING, "JETIFIER_USAGE")
    val emptySelection = WarningsPageId(WarningsPageType.EMPTY_SELECTION, "EMPTY")
  }
}

sealed class WarningsTreePresentableNodeDescriptor: Comparable<WarningsTreePresentableNodeDescriptor> {
  abstract val pageId: WarningsPageId
  abstract val analyticsPageType: PageType
  abstract val presentation: BuildAnalyzerTreeNodePresentation
  abstract val executionTimeMs: Long?
  override fun toString(): String = presentation.mainText

  override fun compareTo(other: WarningsTreePresentableNodeDescriptor): Int {
    // sort by execution time descending, and then alphabetically for warnings without an execution time
    return if (this.executionTimeMs != null || other.executionTimeMs != null) {
      -1 * (this.executionTimeMs ?: -1).compareTo(other.executionTimeMs ?: -1)
    } else {
      this.toString().compareTo(other.toString())
    }
  }
}

/** Descriptor for the task warning type group node. */
class TaskWarningTypeNodeDescriptor(
  val warningType: TaskIssueType,
  val presentedWarnings: List<TaskIssueUiData>

) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warningType(warningType)
  override val analyticsPageType = when (warningType) {
    TaskIssueType.ALWAYS_RUN_TASKS -> PageType.ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> PageType.TASK_SETUP_ISSUE_ROOT
  }

  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = warningType.uiName,
      suffix = warningsCountString(presentedWarnings.size),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )

  override val executionTimeMs = presentedWarnings.sumByLong { it.task.executionTime.timeMs }
}

/** Descriptor for the task warning page node. */
class TaskWarningDetailsNodeDescriptor(
  val issueData: TaskIssueUiData
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warning(issueData)
  override val analyticsPageType = when (issueData) {
    is TaskIssueUiDataContainer.TaskSetupIssue -> PageType.TASK_SETUP_ISSUE_PAGE
    is TaskIssueUiDataContainer.AlwaysRunNoOutputIssue -> PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE
    is TaskIssueUiDataContainer.AlwaysRunUpToDateOverride -> PageType.ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
    else -> PageType.UNKNOWN_PAGE
  }
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = issueData.task.taskPath,
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )
  override val executionTimeMs = issueData.task.executionTime.timeMs
}

class PluginGroupingWarningNodeDescriptor(
  val pluginName: String,
  val presentedTasksWithWarnings: Map<TaskUiData, List<TaskIssueUiData>>

) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warningPlugin(pluginName)

  override val analyticsPageType = PageType.PLUGIN_WARNINGS_ROOT

  private val warningsCount = presentedTasksWithWarnings.values.sumOf { it.size }

  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = pluginName,
      suffix = warningsCountString(warningsCount),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )

  override val executionTimeMs = presentedTasksWithWarnings.keys.sumByLong { it.executionTime.timeMs }
}

class TaskCategoryWarningNodeDescriptor(
  val taskCategoryData: CriticalPathTaskCategoryUiData
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.taskCategory(taskCategoryData.taskCategory)
  override val analyticsPageType = PageType.TASK_CATEGORY_WARNING_ROOT
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = taskCategoryData.name,
      suffix = warningsCountString(taskCategoryData.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = true).size),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )
  override val executionTimeMs = taskCategoryData.criticalPathDuration.timeMs
}

/** Descriptor for the task warning page node. */
class TaskUnderPluginDetailsNodeDescriptor(
  val taskData: TaskUiData,
  val filteredWarnings: List<TaskIssueUiData>
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.task(taskData)
  override val analyticsPageType = PageType.PLUGIN_TASK_WARNINGS
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = taskData.taskPath,
      nodeIconState = NodeIconState.WARNING_ICON,
      suffix = warningsCountString(filteredWarnings.size),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )
  override val executionTimeMs = taskData.executionTime.timeMs
}

/** Descriptor for the non-incremental annotation processors group node. */
class AnnotationProcessorsRootNodeDescriptor(
  val annotationProcessorsReport: AnnotationProcessorsReport
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.annotationProcessorRoot
  override val analyticsPageType = PageType.ANNOTATION_PROCESSORS_ROOT
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = "Non-incremental Annotation Processors",
      suffix = warningsCountString(annotationProcessorsReport.issueCount),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)

    )
  override val executionTimeMs = annotationProcessorsReport.nonIncrementalProcessors.sumByLong { it.compilationTimeMs }
}

/** Descriptor for the non-incremental annotation processor page node. */
class AnnotationProcessorDetailsNodeDescriptor(
  val annotationProcessorData: AnnotationProcessorUiData
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.annotationProcessor(annotationProcessorData)
  override val analyticsPageType = PageType.ANNOTATION_PROCESSOR_PAGE
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = annotationProcessorData.className,
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )
  override val executionTimeMs = annotationProcessorData.compilationTimeMs
}

/** Descriptor for the configuration caching problems page node. */
class ConfigurationCachingRootNodeDescriptor(
  val data: ConfigurationCachingCompatibilityProjectResult,
  val projectConfigurationTime: TimeWithPercentage
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.configurationCachingRoot
  override val analyticsPageType = PageType.CONFIGURATION_CACHE_ROOT
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = "Configuration cache",
      suffix = when (data) {
        is AGPUpdateRequired -> "Android Gradle plugin update required"
        is IncompatiblePluginsDetected -> data.upgradePluginWarnings.size.let {
          when (it) {
            0 -> ""
            1 -> "1 plugin requires update"
            else -> "$it plugins require update"
          }
        }
        is NoIncompatiblePlugins -> ""
        ConfigurationCachingTurnedOn -> ""
        ConfigurationCacheCompatibilityTestFlow -> ""
        ConfigurationCachingTurnedOff -> ""
        NoDataFromSavedResult -> ""
      },
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs)
    )
  override val executionTimeMs = projectConfigurationTime.timeMs
}

class ConfigurationCachingWarningNodeDescriptor(
  val data: IncompatiblePluginWarning,
  val projectConfigurationTime: TimeWithPercentage
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.configurationCachingWarning(data)
  override val analyticsPageType = PageType.CONFIGURATION_CACHE_PLUGIN_WARNING
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = data.plugin.displayName,
      suffix = if (data.requiredVersion != null) "update required" else "not compatible",
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(executionTimeMs),
      nodeIconState = NodeIconState.WARNING_ICON
    )
  override val executionTimeMs = projectConfigurationTime.totalMs
}

class JetifierUsageWarningRootNodeDescriptor(
  val data: JetifierUsageAnalyzerResult
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.jetifierUsageWarningRoot
  override val analyticsPageType = PageType.JETIFIER_USAGE_WARNING
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = "Jetifier",
    )
  override val executionTimeMs = null
}

private fun ConfigurationCachingCompatibilityProjectResult.warningsCount() = when (this) {
  is AGPUpdateRequired -> 1
  is IncompatiblePluginsDetected -> incompatiblePluginWarnings.size + upgradePluginWarnings.size
  is NoIncompatiblePlugins -> 1
  ConfigurationCacheCompatibilityTestFlow -> 1
  ConfigurationCachingTurnedOn -> 0
  ConfigurationCachingTurnedOff -> 0
  NoDataFromSavedResult -> 0
}

fun ConfigurationCachingCompatibilityProjectResult.shouldShowWarning(): Boolean = warningsCount() != 0

fun JetifierUsageAnalyzerResult.shouldShowWarning(): Boolean = when (this.projectStatus) {
  AnalyzerNotRun -> false
  JetifierNotUsed -> false
  JetifierUsedCheckRequired -> true
  JetifierCanBeRemoved -> true
  is JetifierRequiredForLibraries -> true
}
private fun rightAlignedNodeDurationTextFromMs(timeMs: Long) =
  if (timeMs >= 100) "%.1fs".format(timeMs.toDouble() / 1000) else "<0.1s"

fun BuildAttributionReportUiData.countTotalWarnings(): Int =
  issues.sumOf { it.warningCount } +
  annotationProcessors.issueCount +
  confCachingData.warningsCount() +
  (criticalPathTaskCategories?.entries ?: emptyList()).sumOf { category ->
    category.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = true).size
  } +
  if (jetifierData.shouldShowWarning()) 1 else 0