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

import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.StudioBotBundle
import com.intellij.build.ExecutionNode
import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.MessageEventResult
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.VisibleForTesting
import icons.StudioIcons
import javax.swing.tree.TreePath

private val ASK_STUDIO_BOT_UNTIL_EOL = Regex(">> Ask Studio Bot:[^\n]*")
private const val ASK_STUDIO_BOT_LINK_TEXT = "<a href=\"explain.issue\">>> Ask Studio Bot</a>"

class ExplainSyncOrBuildOutput : DumbAwareAction(
  StudioBotBundle.message("studiobot.ask.text"), StudioBotBundle.message("studiobot.ask.text"),
  StudioIcons.StudioBot.ASK
) {

  override fun update(e: AnActionEvent) {
    val studioBot = StudioBot.getInstance()
    if (!studioBot.isAvailable()) {
      e.presentation.isEnabled = false
      return
    }
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
    val project = e.project ?: return
    val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tree = component as? Tree ?: return
    val selectedNodes = getSelectedNodes(tree)
    if (selectedNodes.isEmpty()) return

    val node = selectedNodes[0]
    val errorName = node.name
    val shortDescription = getErrorShortDescription(node.result) ?: errorName
    val studioBot = StudioBot.getInstance()

    val query = "Explain build error: $shortDescription"

    // If context sharing is enabled, send the query immediately. Otherwise, stage
    // it in the query bar.
    if (studioBot.isContextAllowed()) {
      // A shorter version of the query. This is how it will appear in the chat UI.
      val displayText = "Explain build error: $errorName"
      val validatedQuery = studioBot.aiExcludeService()
        .validateQuery(project, query, emptyList())
        .getOrThrow()
      studioBot.chat(project).sendChatQuery(validatedQuery, StudioBot.RequestSource.BUILD, displayText = displayText)
    } else {
      studioBot.chat(project).stageChatQuery(query, StudioBot.RequestSource.BUILD)
    }
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
