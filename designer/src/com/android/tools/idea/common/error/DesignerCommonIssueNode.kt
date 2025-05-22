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

import com.android.sdklib.devices.Device
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.uibuilder.surface.NlAtfIssue
import com.android.tools.idea.uibuilder.visual.ConfigurationSet
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.UniversalProblemsPanelEvent
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.CompoundIconProvider
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import com.intellij.util.ui.EmptyIcon
import java.util.Objects
import javax.swing.Icon

/**
 * The issue node in [DesignerCommonIssuePanel]. The Tree architecture will be:
 * - DesignerCommonIssueRoot | |-- IssuedFileNode 1 | | -- IssueNode | | -- IssueNode | ... | |--
 *   IssuedFileNode 2 | | -- IssueNode | | -- IssueNode | ... | ... | |-- NoFileNode | -- IssueNode
 *   | -- IssueNode ...
 */
abstract class DesignerCommonIssueNode(
  project: Project?,
  parentDescriptor: NodeDescriptor<DesignerCommonIssueNode>?,
) :
  PresentableNodeDescriptor<DesignerCommonIssueNode>(project, parentDescriptor),
  LeafState.Supplier {

  protected open val issueComparator: Comparator<DesignerCommonIssueNode>
    get() =
      (parentDescriptor?.element as? DesignerCommonIssueNode)?.issueComparator ?: compareBy { 0 }

  final override fun update(presentation: PresentationData) {
    if (myProject != null && myProject.isDisposed) {
      return
    }
    updatePresentation(presentation)
  }

  protected abstract fun updatePresentation(presentation: PresentationData)

  abstract override fun getName(): String

  override fun toString() = name

  abstract fun getChildren(): List<DesignerCommonIssueNode>

  /**
   * The associated [VirtualFile] of this node to provide [PlatformDataKeys.VIRTUAL_FILE] data. Can
   * be null.
   */
  open fun getVirtualFile(): VirtualFile? = null

  /**
   * The associated [Navigatable] of this node to provide the [PlatformDataKeys.NAVIGATABLE] data.
   * Can be null.
   */
  open fun getNavigatable(): Navigatable? = null

  final override fun getElement() = this

  /**
   * To provide the description of issue when copying the description by
   * [CopyIssueDescriptionAction] or shortcut key (Control-C/Command-C).
   */
  internal fun getDescription(): String {
    val data = PresentationData()
    updatePresentation(data)
    return data.coloredText.joinToString { it.text }.trim()
  }

  open fun getNodeProvider(): NodeProvider {
    return (parentDescriptor as? DesignerCommonIssueNode)?.getNodeProvider() ?: EmptyNodeProvider
  }
}

/** Response to create all the [DesignerCommonIssueNode]s when [updateIssues] is called. */
@VisibleForTesting
interface NodeProvider {
  /** Update the tree of [DesignerCommonIssueNode] */
  fun updateIssues(issueList: List<Issue>, nodeFactory: NodeFactory)

  /** Get the [DesignerCommonIssueNode] of all nodes of all files. */
  fun getFileNodes(): List<DesignerCommonIssueNode>

  /** Get the children [IssueNode]s of the given [fileNode]. */
  fun getIssueNodes(fileNode: DesignerCommonIssueNode): List<IssueNode>
}

object EmptyNodeProvider : NodeProvider {
  override fun updateIssues(issueList: List<Issue>, nodeFactory: NodeFactory) = Unit

  override fun getFileNodes(): List<DesignerCommonIssueNode> = emptyList()

  override fun getIssueNodes(fileNode: DesignerCommonIssueNode): List<IssueNode> = emptyList()
}

class NodeProviderImpl(private val rootNode: DesignerCommonIssueNode) : NodeProvider {
  /** Store the nodes of [VirtualFile]s and their associated [DesignerCommonIssueNode]s. */
  private var fileNodeMap = mapOf<VirtualFile?, DesignerCommonIssueNode>()

  /**
   * Store the nodes of [Issue]s and their associated parent [DesignerCommonIssueNode]s to
   * [IssueNode] pairs.
   */
  private var issueToNodeMap = mapOf<Issue, IssueNode>()

  @Suppress("UnstableApiUsage")
  override fun updateIssues(issueList: List<Issue>, nodeFactory: NodeFactory) {
    // Construct the nodes of the whole tree. The old node is reused if it exists.
    val fileIssuesMap: Map<VirtualFile?, List<Issue>> =
      issueList.groupBy { issue ->
        if (issue.source is VisualLintIssueProvider.VisualLintIssueSource) null
        else
          issue.source.files.let {
            BackedVirtualFile.getOriginFileIfBacked(it.firstOrNull() ?: return@let null)
          }
      }

    val oldFileNodeMap = fileNodeMap
    val oldFileIssueMap = issueToNodeMap

    // Update file nodes.
    val newFileNodeMap: MutableMap<VirtualFile?, DesignerCommonIssueNode> = mutableMapOf()
    for ((file, _) in fileIssuesMap) {
      val node =
        oldFileNodeMap[file]
          ?: let {
            if (file != null) nodeFactory.createFileNode(file, rootNode)
            else nodeFactory.createNoFileNode(rootNode)
          }
      newFileNodeMap[file] = node
    }

    // Update issue nodes.
    val newIssueToNodeMap = mutableMapOf<Issue, IssueNode>()
    for ((file, issues) in fileIssuesMap) {
      val parentNode: DesignerCommonIssueNode = newFileNodeMap[file]!!
      for (issue in issues) {
        val oldIssueNode: IssueNode? = oldFileIssueMap[issue]

        val nodeToAdd =
          if (oldIssueNode == null || oldIssueNode.parent != parentNode) {
            when (issue) {
              is VisualLintRenderIssue -> VisualLintIssueNode(issue, parentNode)
              else -> IssueNode(file, issue, parentNode)
            }
          } else {
            oldIssueNode
          }
        newIssueToNodeMap[issue] = nodeToAdd
      }
    }

    fileNodeMap = newFileNodeMap
    issueToNodeMap = newIssueToNodeMap
  }

  override fun getFileNodes(): List<DesignerCommonIssueNode> = fileNodeMap.values.toList()

  override fun getIssueNodes(fileNode: DesignerCommonIssueNode): List<IssueNode> {
    return issueToNodeMap.values.filter { it.parent == fileNode }.toList()
  }
}

/**
 * The root of common issue panel. This is an invisible root node for simulating the multi-root
 * tree.
 */
class DesignerCommonIssueRoot(
  project: Project?,
  val issueProvider: DesignerCommonIssueProvider<out Any?>,
  private val nodeFactoryProvider: () -> NodeFactory,
) : DesignerCommonIssueNode(project, null) {

  private var _comparator: Comparator<DesignerCommonIssueNode> = compareBy { 0 }
  override val issueComparator: Comparator<DesignerCommonIssueNode>
    get() = _comparator

  private val nodeProvider = NodeProviderImpl(this)

  init {
    issueProvider.registerUpdateListener {
      nodeProvider.updateIssues(issueProvider.getFilteredIssues(), nodeFactoryProvider())
    }
    nodeProvider.updateIssues(issueProvider.getFilteredIssues(), nodeFactoryProvider())
  }

  fun setComparator(comparator: Comparator<DesignerCommonIssueNode>) {
    _comparator = comparator
  }

  override fun getName(): String = "Current File And Qualifiers"

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getChildren(): List<DesignerCommonIssueNode> {
    return getNodeProvider().getFileNodes().sortedWith(FileNameComparator).toList()
  }

  override fun updatePresentation(presentation: PresentationData) {
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  override fun getNodeProvider(): NodeProvider {
    return nodeProvider
  }
}

/** The [DesignerCommonIssueNode] represents a file. */
class IssuedFileNode(val file: VirtualFile, parent: DesignerCommonIssueNode?) :
  DesignerCommonIssueNode(parent?.project, parent) {

  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = getVirtualFile().name

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile(): VirtualFile =
    file.let { BackedVirtualFile.getOriginFileIfBacked(file) }

  override fun getNavigatable(): Navigatable? = null

  override fun updatePresentation(presentation: PresentationData) {
    val virtualFile = file
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    presentation.setIcon(
      CompoundIconProvider.findIcon(PsiUtilCore.findFileSystemItem(project, virtualFile), 0)
        ?: when (virtualFile.isDirectory) {
          true -> AllIcons.Nodes.Folder
          else -> AllIcons.FileTypes.Any_type
        }
    )
    val url = virtualFile.parent?.presentableUrl ?: return
    presentation.addText(
      "  ${FileUtil.getLocationRelativeToUserHome(url)}",
      SimpleTextAttributes.GRAYED_ATTRIBUTES,
    )
    val count = getNodeProvider().getIssueNodes(this).size
    presentation.addText("  ${createIssueCountText(count)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element, file)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? IssuedFileNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element && that.file == file
  }

  override fun getChildren(): List<IssueNode> {
    return getNodeProvider()
      .getIssueNodes(this)
      .sortedWith(PreprocessNodeComparator.thenComparing(issueComparator))
  }
}

const val LAYOUT_VALIDATION_NODE_NAME = "Layout Validation"

const val UI_CHECK_NODE_NAME = "UI Check"

object LayoutValidationNodeFactory : NodeFactory {
  override fun createNoFileNode(parent: DesignerCommonIssueNode) =
    LayoutValidationNoFileNode(parent)
}

object UICheckNodeFactory : NodeFactory {
  override fun createNoFileNode(parent: DesignerCommonIssueNode) =
    NoFileNode(UI_CHECK_NODE_NAME, null, parent)
}

interface NodeFactory {
  fun createNoFileNode(parent: DesignerCommonIssueNode): DesignerCommonIssueNode

  fun createFileNode(
    virtualFile: VirtualFile,
    parent: DesignerCommonIssueNode,
  ): DesignerCommonIssueNode = IssuedFileNode(virtualFile, parent)
}

class LayoutValidationNoFileNode(parent: DesignerCommonIssueNode?) :
  NoFileNode(LAYOUT_VALIDATION_NODE_NAME, AllIcons.FileTypes.Xml, parent)

/**
 * A node which doesn't represent any file. This is used to show the issues which do not belong to
 * any particular file.
 */
open class NoFileNode(
  private val nodeName: String,
  private val nodeIcon: Icon?,
  parent: DesignerCommonIssueNode?,
) : DesignerCommonIssueNode(parent?.project, parent) {
  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = nodeName

  override fun getVirtualFile(): VirtualFile? = null

  override fun getNavigatable(): Navigatable? = null

  override fun updatePresentation(presentation: PresentationData) {
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    presentation.setIcon(nodeIcon)
    val count = getNodeProvider().getIssueNodes(this).size
    presentation.addText("  ${createIssueCountText(count)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? NoFileNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element
  }

  override fun getChildren(): List<IssueNode> {
    return getNodeProvider()
      .getIssueNodes(this)
      .sortedWith(PreprocessNodeComparator.thenComparing(issueComparator))
  }
}

val DESCEND_ORDER_DEFAULT_SEVERITIES: List<HighlightSeverity> =
  HighlightSeverity.DEFAULT_SEVERITIES.sortedByDescending { it.myVal }
    .toMutableList()
    .also { it.remove(HighlightSeverity.INFO) }

/** The node represents an [Issue] in the layout file. */
open class IssueNode(
  val file: VirtualFile?,
  val issue: Issue,
  val parent: DesignerCommonIssueNode?,
) : DesignerCommonIssueNode(parent?.project, parent) {

  override fun getLeafState() = LeafState.ALWAYS

  override fun getName(): String = createNodeDisplayText()

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile() = file?.let { BackedVirtualFile.getOriginFileIfBacked(file) }

  override fun getChildren(): List<DesignerCommonIssueNode> = emptyList()

  override fun getNavigatable(): Navigatable? {
    var navigatable = (issue.source as? NlComponentIssueSource)?.component?.navigatable
    if (navigatable == null) {
      val targetFile = getVirtualFile()
      if (project != null && targetFile != null) {
        navigatable = OpenFileDescriptor(project, targetFile, -1)
      }
    }
    if (navigatable is OpenFileDescriptor) {
      return MyOpenFileDescriptor(navigatable)
    }
    return navigatable
  }

  override fun updatePresentation(presentation: PresentationData) {
    val nodeDisplayText: String = createNodeDisplayText()
    val icon: Icon =
      HighlightDisplayLevel.find(issue.severity)?.icon
        ?: let {
          val issueSeverityLevel = issue.severity.myVal
          var icon: Icon? = null
          for (severity in DESCEND_ORDER_DEFAULT_SEVERITIES) {
            if (issueSeverityLevel < severity.myVal) {
              continue
            }
            icon = HighlightDisplayLevel.find(severity)?.icon
            break
          }
          icon ?: EmptyIcon.ICON_16
        }
    presentation.setIcon(icon)

    presentation.addText(nodeDisplayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  private fun createNodeDisplayText(): String {
    val source = issue.source
    return if (source is NlComponentIssueSource) source.displayText + ": " + issue.summary
    else issue.summary
  }

  override fun hashCode() = Objects.hash(parentDescriptor?.element, file, issue)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? IssueNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element && that.issue == issue
  }

  override fun toString(): String {
    val builder = StringBuilder()
    file?.canonicalPath?.let { builder.append(it).append(", ") }
    builder.append(issue.toString())
    return builder.toString()
  }
}

class VisualLintIssueNode(
  private val visualLintIssue: VisualLintRenderIssue,
  parent: DesignerCommonIssueNode?,
) : IssueNode(null, visualLintIssue, parent) {

  override fun getLeafState() = LeafState.DEFAULT

  override fun getChildren(): List<DesignerCommonIssueNode> = emptyList()

  override fun getNavigatable(): Navigatable? {
    if (project == null) {
      return null
    }
    val navigatable = visualLintIssue.navigatable as? OpenFileDescriptor
    if (navigatable != null) {
      return MyOpenFileDescriptor(navigatable)
    }
    val targetComponent = visualLintIssue.components.firstOrNull()
    val openLayoutValidationNavigatable =
      if (Device.isWear(visualLintIssue.models.firstOrNull()?.configuration?.device)) {
        SelectWearDevicesNavigatable(project)
      } else {
        SelectWindowSizeDevicesNavigatable(project)
      }
    if (targetComponent == null) {
      return openLayoutValidationNavigatable
    }

    return object : Navigatable {
      override fun navigate(requestFocus: Boolean) {
        navigateToComponent(targetComponent, true)
        openLayoutValidationNavigatable.navigate(requestFocus)
      }

      override fun canNavigate(): Boolean = project != null

      override fun canNavigateToSource(): Boolean = project != null
    }
  }

  override fun hashCode() =
    Objects.hash(parentDescriptor?.element, visualLintIssue.rangeBasedHashCode())

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? VisualLintIssueNode ?: return false
    return that.parentDescriptor?.element == parentDescriptor?.element &&
      that.visualLintIssue.rangeBasedHashCode() == visualLintIssue.rangeBasedHashCode()
  }
}

private class MyOpenFileDescriptor(openFileDescriptor: OpenFileDescriptor) :
  OpenFileDescriptor(
    openFileDescriptor.project,
    openFileDescriptor.file,
    openFileDescriptor.offset,
  ) {

  /**
   * [navigate], [navigateIn], and [navigateInEditor] may call each other depending on the
   * implementation of [com.intellij.openapi.fileEditor.FileNavigator]. Mark as tracked to avoid the
   * duplications.
   */
  private var hasTracked: Boolean = false

  override fun navigate(requestFocus: Boolean) {
    trackOpenFileEvent()
    super.navigate(requestFocus)
  }

  override fun navigateIn(e: Editor) {
    trackOpenFileEvent()
    super.navigateIn(e)
  }

  override fun navigateInEditor(project: Project, requestFocus: Boolean): Boolean {
    trackOpenFileEvent()
    return super.navigateInEditor(project, requestFocus)
  }

  private fun trackOpenFileEvent() {
    if (!hasTracked) {
      DesignerCommonIssuePanelUsageTracker.getInstance()
        .trackNavigationFromIssue(UniversalProblemsPanelEvent.IssueNavigated.OPEN_FILE, project)
      hasTracked = true
    }
  }
}

@VisibleForTesting
class SelectWindowSizeDevicesNavigatable(project: Project) :
  OpenLayoutValidationNavigatable(project, ConfigurationSet.WindowSizeDevices)

@VisibleForTesting
class SelectWearDevicesNavigatable(project: Project) :
  OpenLayoutValidationNavigatable(project, ConfigurationSet.WearDevices)

@VisibleForTesting
open class OpenLayoutValidationNavigatable(
  private val project: Project,
  configurationSetToSelect: ConfigurationSet,
) : Navigatable {
  private val task = {
    VisualizationToolWindowFactory.openAndSetConfigurationSet(project, configurationSetToSelect)
  }

  override fun navigate(requestFocus: Boolean) {
    DesignerCommonIssuePanelUsageTracker.getInstance()
      .trackNavigationFromIssue(
        UniversalProblemsPanelEvent.IssueNavigated.OPEN_VALIDATION_TOOL,
        project,
      )
    task()
  }

  override fun canNavigate(): Boolean = true

  override fun canNavigateToSource(): Boolean = true
}

private fun createIssueCountText(issueCount: Int): String {
  return when (issueCount) {
    0 -> "There is no problem"
    1 -> "1 problem"
    else -> "$issueCount problems"
  }
}

/**
 * Comparator to sort the [IssuedFileNode] and [NoFileNode]. The [IssuedFileNode] is sorted
 * alphabetically and [NoFileNode] is always the last.
 */
object FileNameComparator : Comparator<DesignerCommonIssueNode> {
  override fun compare(o1: DesignerCommonIssueNode?, o2: DesignerCommonIssueNode?): Int {
    if (o1 is NoFileNode) {
      return if (o2 is NoFileNode) 0 else 1
    }
    if (o2 is NoFileNode) {
      return -1
    }

    return if (o1 is IssuedFileNode && o2 is IssuedFileNode)
      o1.getVirtualFile().name.compareTo(o2.getVirtualFile().name)
    else 0
  }
}

/**
 * Define the default order for [DesignerCommonIssueNode]. All [DesignerCommonIssueNode]s must pass
 * to this comparator before sorting.
 */
object PreprocessNodeComparator : Comparator<DesignerCommonIssueNode> {
  override fun compare(o1: DesignerCommonIssueNode?, o2: DesignerCommonIssueNode?): Int {
    return when {
      o1 == o2 -> 0
      o1 == null -> -1
      o2 == null -> 1
      o1 is IssueNode && o2 !is IssueNode -> -1
      o1 !is IssueNode && o2 is IssueNode -> 1
      // Force sorted the ATF issue by name. This avoid the jumping order of ATF issue when there is
      // no sorting option is selected.
      o1 is IssueNode && o2 is IssueNode && o1.issue is NlAtfIssue && o2.issue is NlAtfIssue ->
        o1.issue.summary.compareTo(o2.issue.summary)
      // Provide consistent ordering for everything else
      else -> o1.name.compareTo(o2.name)
    }
  }
}
