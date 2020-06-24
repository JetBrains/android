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
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.android.build.attribution.ui.warningsCountString
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
import com.intellij.openapi.util.text.StringUtil
import javax.swing.tree.DefaultMutableTreeNode

interface WarningsDataPageModel {
  val reportData: BuildAttributionReportUiData

  /** Text of the header visible above the tree. */
  val treeHeaderText: String

  /** The root of the tree that should be shown now. View is supposed to set this root in the Tree on update. */
  val treeRoot: DefaultMutableTreeNode

  /** Currently selected node. Can be null in case of an empty tree. */
  val selectedNode: WarningsTreeNode?

  /** True if there are no warnings to show. */
  val isEmpty: Boolean

  /**
   * Selects node in a tree to provided.
   * Provided node object should be the one created in this model and exist in the trees it holds.
   * Notifies listener if model state changes.
   */
  fun selectNode(warningsTreeNode: WarningsTreeNode)

  /** Looks for the tree node by it's pageId and selects it as described in [selectNode] if found. */
  fun selectPageById(warningsPageId: WarningsPageId)

  /** Install the listener that will be called on model state changes. */
  fun setModelUpdatedListener(listener: () -> Unit)
}

class WarningsDataPageModelImpl(
  override val reportData: BuildAttributionReportUiData
) : WarningsDataPageModel {
  @VisibleForTesting
  var modelUpdatedListener: (() -> Unit)? = null
    private set

  override val treeHeaderText: String =
    "${reportData.totalIssuesCount} ${StringUtil.pluralize("Warning", reportData.totalIssuesCount)}"

  private val treeStructure = WarningsTreeStructure(reportData)

  override val treeRoot: DefaultMutableTreeNode
    get() = treeStructure.groupedByTypeNodes

  // True when there are changes since last listener call.
  private var modelChanged = false

  override var selectedNode: WarningsTreeNode? = treeStructure.defaultNode
    private set(value) {
      if (value != null && value != field) {
        field = value
        modelChanged = true
      }
    }

  override val isEmpty: Boolean
    get() = reportData.totalIssuesCount == 0

  override fun selectNode(warningsTreeNode: WarningsTreeNode) {
    selectedNode = warningsTreeNode
    notifyModelChanges()
  }

  override fun selectPageById(warningsPageId: WarningsPageId) {
    treeStructure.pageIdToNode[warningsPageId]?.let { selectNode(it) }
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

private class WarningsTreeStructure(
  val reportData: BuildAttributionReportUiData
) {

  val pageIdToNode: MutableMap<WarningsPageId, WarningsTreeNode> = mutableMapOf()

  private fun treeNode(descriptor: WarningsTreePresentableNodeDescriptor) = WarningsTreeNode(descriptor).apply {
    pageIdToNode[descriptor.pageId] = this
  }

  val groupedByTypeNodes = DefaultMutableTreeNode().apply {
    reportData.issues.forEach { warningsGroup ->
      add(treeNode(TaskWarningTypeNodeDescriptor(warningsGroup)).apply {
        warningsGroup.issues.forEach { taskWarning ->
          add(treeNode(TaskWarningDetailsNodeDescriptor(taskWarning)))
        }
      })
    }
    if (reportData.annotationProcessors.issueCount > 0) {
      add(treeNode(AnnotationProcessorsRootNodeDescriptor(reportData.annotationProcessors)).apply {
        reportData.annotationProcessors.nonIncrementalProcessors.forEach {
          add(treeNode(AnnotationProcessorDetailsNodeDescriptor(it)))
        }
      })
    }
  }

  val defaultNode: WarningsTreeNode? = groupedByTypeNodes.nextNode as? WarningsTreeNode
}

class WarningsTreeNode(
  val descriptor: WarningsTreePresentableNodeDescriptor
) : DefaultMutableTreeNode(descriptor)

enum class WarningsPageType {
  TASK_WARNING_DETAILS,
  TASK_WARNING_TYPE_GROUP,
  ANNOTATION_PROCESSOR_DETAILS,
  ANNOTATION_PROCESSOR_GROUP
}

data class WarningsPageId(
  val pageType: WarningsPageType,
  val id: String
) {
  companion object {
    fun warning(warning: TaskIssueUiData) = WarningsPageId(WarningsPageType.TASK_WARNING_DETAILS,
                                                           "${warning.type}-${warning.task.taskPath}")

    fun warningType(warningType: TaskIssueType) = WarningsPageId(WarningsPageType.TASK_WARNING_TYPE_GROUP, warningType.name)
    fun annotationProcessor(annotationProcessorData: AnnotationProcessorUiData) = WarningsPageId(
      WarningsPageType.ANNOTATION_PROCESSOR_DETAILS, annotationProcessorData.className)

    val annotationProcessorRoot = WarningsPageId(WarningsPageType.ANNOTATION_PROCESSOR_GROUP, "ANNOTATION_PROCESSORS")
  }
}

sealed class WarningsTreePresentableNodeDescriptor {
  abstract val pageId: WarningsPageId
  abstract val analyticsPageType: PageType
  abstract val presentation: BuildAnalyzerTreeNodePresentation
  override fun toString(): String = presentation.mainText
}

/** Descriptor for the task warning type group node. */
class TaskWarningTypeNodeDescriptor(
  val warningTypeData: TaskIssuesGroup
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warningType(warningTypeData.type)
  override val analyticsPageType = when (warningTypeData.type) {
    TaskIssueType.ALWAYS_RUN_TASKS -> PageType.ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> PageType.TASK_SETUP_ISSUE_ROOT
  }

  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = warningTypeData.type.uiName,
      suffix = warningsCountString(warningTypeData.warningCount),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(warningTypeData.timeContribution.timeMs)
    )
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
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(issueData.task.executionTime.timeMs)
    )
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
      suffix = warningsCountString(annotationProcessorsReport.issueCount)
    )
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
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(annotationProcessorData.compilationTimeMs)
    )
}

private fun rightAlignedNodeDurationTextFromMs(timeMs: Long) = "%.1fs".format(timeMs.toDouble() / 1000)