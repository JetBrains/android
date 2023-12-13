/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.explainer.IssueExplainer
import com.intellij.build.ExecutionNode
import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.MessageEventResult
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.tree.TreePath

private val ASK_STUDIO_BOT_UNTIL_EOL = Regex(">> Ask Studio Bot:[^\n]*")
private const val ASK_STUDIO_BOT_LINK_TEXT = "<a href=\"explain.issue\">>> Ask Studio Bot</a>"

class ExplainSyncOrBuildOutput : DumbAwareAction(
  service<IssueExplainer>().getShortLabel(), service<IssueExplainer>().getShortLabel(),
  service<IssueExplainer>().getIcon()
) {

  override fun update(e: AnActionEvent) {
    // we don't want to ask question about intermediate nodes which are just file names
    val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tree = component as? Tree
    val rowNumber = tree?.selectionRows?.singleOrNull()
    if (rowNumber == null) {
      e.presentation.isEnabled = false
    }
    else {
      // treePath could be null when running on BGT
      val treePath = tree.getPathForRow(rowNumber) ?: return
      // skip "Download info" node with rowNumber == 1
      e.presentation.isEnabled = rowNumber > 1 && tree.model.isLeaf(treePath.lastPathComponent)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tree = component as? Tree ?: return
    val selectedNodes = getSelectedNodes(tree)
    if (selectedNodes.isEmpty()) return
    val node = selectedNodes[0]
    val shortDescription = getErrorShortDescription(node.result) ?: node.name
    service<IssueExplainer>().explain(e.project!!, shortDescription, IssueExplainer.RequestKind.BUILD_ISSUE)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

}

private fun getSelectedNodes(myTree: Tree): List<ExecutionNode> {
  return TreeUtil.collectSelectedObjects<ExecutionNode?>(myTree) { path: TreePath? ->
    TreeUtil.getLastUserObject(ExecutionNode::class.java, path)
  }
}

private fun String.trimMessagesWithLongStacktrace(n: Int = 25) = split("\n").take(n).joinToString("\n")

@VisibleForTesting
fun getErrorShortDescription(result: EventResult?): String? {
  return when (result) {
           is FailureResult -> {
             result.failures?.mapNotNull { it.error }?.joinToString("\n")
           }

           is MessageEventResult -> {
             result.details
           }

           else -> null
         }
           ?.trimMessagesWithLongStacktrace()
           ?.replace(ASK_STUDIO_BOT_UNTIL_EOL, "")
           ?.replace(ASK_STUDIO_BOT_LINK_TEXT, "")
}
