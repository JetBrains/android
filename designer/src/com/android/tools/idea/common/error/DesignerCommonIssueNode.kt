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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.isVisualLintErrorSuppressed
import com.google.common.collect.Ordering
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.CompoundIconProvider
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import icons.StudioIcons
import java.util.Objects

/**
 * The issue node in [DesignerCommonIssuePanel].
 * The Tree architecture will be:
 * - DesignerCommonIssueRoot
 *    |
 *    |-- IssuedFileNode 1
 *    |    | -- IssueNode
 *    |    | -- IssueNode
 *    |    ...
 *    |
 *    |-- IssuedFileNode 2
 *    |    | -- IssueNode
 *    |    | -- IssueNode
 *    |    ...
 *    | ...
 *    |
 *    |-- NoFileNode
 *        | -- IssueNode
 *        | -- IssueNode
 *        ...
 *
 */
abstract class DesignerCommonIssueNode(project: Project?, parentDescriptor: NodeDescriptor<DesignerCommonIssueNode>?)
  : PresentableNodeDescriptor<DesignerCommonIssueNode>(project, parentDescriptor), LeafState.Supplier {

  final override fun update(presentation: PresentationData) {
    if (myProject != null && myProject.isDisposed) {
      return
    }
    updatePresentation(presentation)
  }

  protected abstract fun updatePresentation(presentation: PresentationData)

  abstract override fun getName(): String

  override fun toString() = name

  abstract fun getChildren(): Collection<DesignerCommonIssueNode>

  /**
   * The associated [VirtualFile] of this node to provide [PlatformDataKeys.VIRTUAL_FILE] data. Can be null.
   */
  open fun getVirtualFile(): VirtualFile? = null

  /**
   * The associated [Navigatable] of this node to provide the [PlatformDataKeys.NAVIGATABLE] data. Can be null.
   */
  open fun getNavigatable(): Navigatable? = null

  final override fun getElement() = this

  /**
   * To provide the description of issue when copying the description by [CopyIssueDescriptionAction] or shortcut key (Control-C/Command-C).
   */
  internal fun getDescription(): String {
    val data = PresentationData()
    updatePresentation(data)
    return data.coloredText.joinToString { it.text }.trim()
  }
}

/**
 * The root of common issue panel. This is an invisible root node for simulating the multi-root tree.
 */
class DesignerCommonIssueRoot(project: Project?, var issueProvider: DesignerCommonIssueProvider<out Any?>)
  : DesignerCommonIssueNode(project, null) {

  override fun getName(): String = "Current File And Qualifiers"

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getChildren(): Collection<DesignerCommonIssueNode> {
    val fileIssuesMap: MutableMap<VirtualFile?, List<Issue>> = issueProvider.getFilteredIssues().groupBy { it.source.file }.toMutableMap()
    val otherIssues = fileIssuesMap.remove(null)
    val fileNodes = fileIssuesMap.toSortedMap(Ordering.usingToString())
      .map { (file, issues) -> IssuedFileNode(file!!, issues, this@DesignerCommonIssueRoot) }
      .toList()

    return if (otherIssues != null) fileNodes + NoFileNode(otherIssues, this) else fileNodes
  }

  override fun updatePresentation(presentation: PresentationData) {
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

/**
 * The node represents a file, which contains the issue(s).
 *
 */
class IssuedFileNode(val file: VirtualFile, val issues: List<Issue>, parent: DesignerCommonIssueNode?)
  : DesignerCommonIssueNode(parent?.project, parent) {

  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = getVirtualFile().name

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile() = file.let { BackedVirtualFile.getOriginFileIfBacked(file) }

  override fun getNavigatable(): Navigatable? = null

  override fun updatePresentation(presentation: PresentationData) {
    val virtualFile = file
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    presentation.setIcon(
      CompoundIconProvider.findIcon(PsiUtilCore.findFileSystemItem(project, virtualFile), 0) ?: when (virtualFile.isDirectory) {
        true -> AllIcons.Nodes.Folder
        else -> AllIcons.FileTypes.Any_type
      }
    )
    val url = virtualFile.parent?.presentableUrl ?: return
    presentation.addText("  ${FileUtil.getLocationRelativeToUserHome(url)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    val count = issues.size
    presentation.addText("  ${createIssueCountText(count)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun getChildren(): Collection<DesignerCommonIssueNode> {
    return issues.map { IssueNode(file, it, this@IssuedFileNode) }
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element, file, *(issues.toTypedArray()))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? IssuedFileNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element && that.file == file && that.issues == issues
  }
}

/**
 * TODO(b/222110455): So far Layout Validation is the only use case. Needs refactor when having other use cases.
 *                    It can use the type of [IssueSource] to detect the source.
 */
const val NO_FILE_NODE_NAME = "Layout Validation"

/**
 * A node for the issues which do not belong to any particular file.
 */
class NoFileNode(val issues: List<Issue>, parent: DesignerCommonIssueNode?) : DesignerCommonIssueNode(parent?.project, parent) {
  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = NO_FILE_NODE_NAME

  override fun getVirtualFile(): VirtualFile? = null

  override fun getNavigatable(): Navigatable? = null

  override fun updatePresentation(presentation: PresentationData) {
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    presentation.setIcon(AllIcons.FileTypes.Xml)
    val count = issues.size
    presentation.addText("  ${createIssueCountText(count)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun getChildren(): Collection<DesignerCommonIssueNode> {
    return issues.map {
      when (it) {
        is VisualLintRenderIssue -> VisualLintIssueNode(it, this@NoFileNode)
        else -> IssueNode(null, it, this@NoFileNode)
      }
    }
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element, *(issues.toTypedArray()))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? NoFileNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element && that.issues == issues
  }
}

/**
 * The node represents an [Issue] in the layout file.
 */
open class IssueNode(val file: VirtualFile?, val issue: Issue, parent: DesignerCommonIssueNode?)
  : DesignerCommonIssueNode(parent?.project, parent) {

  private var text: String = ""
  private var offset: Int = -1

  override fun getLeafState() = LeafState.ALWAYS

  override fun getName() = text

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile() = file?.let { BackedVirtualFile.getOriginFileIfBacked(file) }

  override fun getChildren(): Collection<DesignerCommonIssueNode> = emptySet()

  override fun getNavigatable(): Navigatable? {
    val targetFile = getVirtualFile()
    return if (project != null && targetFile != null) OpenFileDescriptor(project, targetFile, offset) else null
  }

  override fun updatePresentation(presentation: PresentationData) {
    val source = issue.source
    val nodeDisplayText: String
    if (source is NlComponentIssueSource) {
      nodeDisplayText = source.displayText + ": " + issue.summary
      offset = source.component.tag?.textRange?.startOffset ?: -1
    }
    else {
      nodeDisplayText = issue.summary
    }
    text = nodeDisplayText

    val severity = issue.severity
    val icon = if (severity.myVal >= HighlightSeverity.ERROR.myVal) StudioIcons.Common.ERROR else StudioIcons.Common.WARNING
    presentation.setIcon(icon)

    presentation.addText(nodeDisplayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element, file, issue)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? IssueNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element && that.issue == issue
  }
}

class VisualLintIssueNode(private val visualLintIssue: VisualLintRenderIssue, parent: DesignerCommonIssueNode?)
  : IssueNode(null, visualLintIssue, parent) {
  override fun getNavigatable(): Navigatable? {
    if (project == null) {
      return null
    }
    val targetComponent = visualLintIssue.components
                            .filterNot { it.isVisualLintErrorSuppressed(visualLintIssue.type) }
                            .firstOrNull() ?: return null

    return object : Navigatable {
      override fun navigate(requestFocus: Boolean) {
        navigateToComponent(targetComponent, true)
      }
      override fun canNavigate(): Boolean = project != null
      override fun canNavigateToSource(): Boolean = project != null
    }
  }
}

private fun createIssueCountText(issueCount: Int): String {
  return when (issueCount) {
    0 -> "There is no problem"
    1 -> "Has 1 problem"
    else -> "Has $issueCount problems"
  }
}
