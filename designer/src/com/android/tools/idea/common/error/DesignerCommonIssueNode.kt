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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState

/**
 * The issue node in [DesignerCommonIssuePanel].
 */
abstract class DesignerCommonIssueNode(project: Project?, parentDescriptor: NodeDescriptor<DesignerCommonIssueNode>?)
  : PresentableNodeDescriptor<DesignerCommonIssueNode>(project, parentDescriptor), LeafState.Supplier {

  protected abstract fun update(project: Project, presentation: PresentationData)

  abstract override fun getName(): String

  override fun toString() = name

  abstract fun getChildren(): Collection<DesignerCommonIssueNode>

  open fun getVirtualFile(): VirtualFile? = null

  open fun getNavigatable(): Navigatable? = null

  override fun getElement() = this

  final override fun update(presentation: PresentationData) {
    if (myProject == null || myProject.isDisposed) {
      return
    }
    update(myProject, presentation)
  }

  protected inline fun <reified T: DesignerCommonIssueNode> findAncestor(): T? {
    var parent = parentDescriptor
    while (parent != null) {
      if (parent is T) {
        return parent
      }
      parent = parent.parentDescriptor
    }
    return null
  }
}

/**
 * The root of common issue panel. This is an invisible root node for simulating the multi-root tree.
 */
class DesignerCommonIssueRoot(project: Project, var issueProvider: DesignerCommonIssueProvider<out Any?>? = null)
  : DesignerCommonIssueNode(project, null) {

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  override fun getName(): String = "Current File And Qualifiers"

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getChildren(): Collection<DesignerCommonIssueNode> {
    return issueProvider?.getIssuedFileDataList()?.map { LayoutFileIssuedFileNode(it, this@DesignerCommonIssueRoot) } ?: emptySet()
  }
}
