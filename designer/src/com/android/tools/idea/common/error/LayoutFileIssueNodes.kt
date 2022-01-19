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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.CompoundIconProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons
import java.util.Objects
import javax.swing.tree.TreePath

/**
 * The node represents the layout file, which contains the issue(s).
 */
class LayoutFileIssuedFileNode(val fileData: IssuedFileData, parent: DesignerCommonIssueNode)
  : DesignerCommonIssueNode(parent.project, parent) {

  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = getVirtualFile().presentableName

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile() = BackedVirtualFile.getOriginFileIfBacked(fileData.file)

  override fun getNavigatable() = project?.let { OpenFileDescriptor(it, fileData.file) }

  override fun update(project: Project, presentation: PresentationData) {
    val virtualFile = getVirtualFile()
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    presentation.setIcon(
      CompoundIconProvider.findIcon(PsiUtilCore.findFileSystemItem(project, virtualFile), 0) ?: when (virtualFile.isDirectory) {
        true -> AllIcons.Nodes.Folder
        else -> AllIcons.FileTypes.Any_type
      })
    if (parentDescriptor !is LayoutFileIssuedFileNode) {
      val url = virtualFile.parent?.presentableUrl ?: return
      presentation.addText("  ${FileUtil.getLocationRelativeToUserHome(url)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    val root = findAncestor<DesignerCommonIssueRoot>()
    val count = root?.issueProvider?.getIssues(fileData)?.size ?: 0
    if (count > 0) {
      val text = "Has $count problems"
      presentation.addText("  $text", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    else {
      presentation.addText("  There is no issues", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  override fun getChildren(): Collection<DesignerCommonIssueNode> {
    val root = findAncestor<DesignerCommonIssueRoot>()
    return root?.issueProvider?.getIssues(fileData)?.map {
      LayoutFileIssueNode(fileData, it, this@LayoutFileIssuedFileNode)
    } ?: emptyList()
  }

  override fun hashCode() = Objects.hash(project, fileData)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? LayoutFileIssuedFileNode ?: return false
    return that.project == project && that.fileData == fileData
  }
}

/**
 * The node represent an [Issue] in the layout file.
 */
class LayoutFileIssueNode(val fileData: IssuedFileData, val issue: Issue, parent: LayoutFileIssuedFileNode)
  : DesignerCommonIssueNode(parent.project, parent) {

  private var text: String = ""

  private var offset: Int = -1

  override fun getLeafState() = LeafState.DEFAULT

  override fun getName() = text

  @Suppress("UnstableApiUsage")
  override fun getVirtualFile() = BackedVirtualFile.getOriginFileIfBacked(fileData.file)

  override fun getChildren(): Collection<DesignerCommonIssueNode> = emptySet()

  override fun getNavigatable() = project?.let {
    // Note: This only happens when double-clicking LayoutFileIssueNode doesn't expand the tree.
    OpenFileDescriptor(it, fileData.file, offset)
  }

  override fun getDescription(): String {
    // Use summary instead because [issue.description] is a html text and very long.
    return issue.summary
  }

  override fun update(project: Project, presentation: PresentationData) {
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
    presentation.tooltip = getDescription()
  }

  override fun hashCode() = Objects.hash(project, fileData, issue)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? LayoutFileIssueNode ?: return false
    return that.project == project && that.fileData == fileData && that.issue == issue
  }
}

/**
 * For executing the [Issue.Fix] when opening the file.
 */
private class MyOpenFileDescriptor(project: Project, file: VirtualFile, private val callback: () -> Unit)
  : OpenFileDescriptor(project, file) {
  override fun navigate(requestFocus: Boolean) {
    FileNavigator.getInstance().navigate(this, requestFocus)
    callback()
  }

  override fun navigateInEditor(project: Project, requestFocus: Boolean): Boolean {
    val ret = FileNavigator.getInstance().navigateInEditor(this, requestFocus)
    callback()
    return ret
  }

  override fun navigateIn(e: Editor) {
    navigateInEditor(this, e)
    callback()
  }
}

/**
 * Used to find the target [LayoutFileIssueNode] in the [com.intellij.ui.treeStructure.Tree].
 */
class LayoutFileIssueFileFinder(private val fileData: IssuedFileData) : TreeVisitor {
  override fun visit(path: TreePath) = when (val node = TreeUtil.getLastUserObject(path)) {
    is DesignerCommonIssueRoot -> TreeVisitor.Action.CONTINUE
    is LayoutFileIssuedFileNode -> visitFile(node)
    is LayoutFileIssueNode -> TreeVisitor.Action.SKIP_CHILDREN
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  fun visitFile(node: LayoutFileIssuedFileNode) = when (node.fileData) {
    fileData -> TreeVisitor.Action.INTERRUPT
    else -> TreeVisitor.Action.CONTINUE
  }
}
