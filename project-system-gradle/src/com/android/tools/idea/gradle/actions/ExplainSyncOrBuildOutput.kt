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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEventResult
import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter.consoleLinkUnderlinedText
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.StudioBotBundle
import com.android.tools.idea.studiobot.StudioBotExternalFlags
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.intellij.build.ExecutionNode
import com.intellij.build.FileNavigatable
import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.MessageEventResult
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.util.projectStructure.module
import javax.swing.tree.TreePath

// These are used to trim an extra error description and links from the user message.
private val ASK_STUDIO_BOT_LINK_TEXT = "<a href=\"explain.issue\">${consoleLinkUnderlinedText}</a>"
private val ASK_STUDIO_BOT_UNTIL_EOL = Regex("${consoleLinkUnderlinedText}[^\n]*")

class ExplainSyncOrBuildOutput : DumbAwareAction(
  StudioBotBundle.message("studiobot.ask.text"), StudioBotBundle.message("studiobot.ask.description"),
  StudioIcons.StudioBot.ASK
) {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

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
    handleExecutionNode(selectedNodes[0], project)
  }

  private fun handleExecutionNode(node: ExecutionNode, project: Project) {
    val errorName = node.name

    val studioBot = StudioBot.getInstance()
    if (!studioBot.isContextAllowed(project)) {
      // If context isn't enabled, all we do is paste a simple query into the query bar
      // using the error message/details. We trim the length so that the chat bar
      // isn't filled up by an excessively long trace.
      val truncatedDetails = getErrorDetails(node.result)?.trimMessagesWithLongStacktrace() ?: errorName
      val query = "Explain build error: ${truncatedDetails.trim()}"
      studioBot.chat(project).stageChatQuery(query, StudioBot.RequestSource.BUILD)
    } else {
      val result = node.result
      val isSyncIssue = (result is AndroidSyncIssueEventResult)

      val fileLocation = node.navigatables.filterIsInstance<FileNavigatable>().firstOrNull()
      val file = fileLocation?.fileDescriptor?.file
      val fileType = file?.fileType
      val isCompilerIssue = fileType is JavaFileType || fileType is KotlinFileType

      val compilerErrorContextEnabled = StudioBotExternalFlags.isCompilerErrorContextEnabled()
      val gradleErrorContextEnabled = StudioFlags.STUDIOBOT_GRADLE_ERROR_CONTEXT_ENABLED.get()

      // If context is enabled, we can build a richer query by looking at the error details and location
      val aiExcludeService = studioBot.aiExcludeService(project)
      val filesUsedAsContext = mutableListOf<VirtualFile>()
      val context = buildString {
        // Get the full details of the error from the log and add it to the query.
        val details = getErrorDetails(result)

        // Cut the details off at 500 characters e.g. to avoid extremely long traces
        if (details != null) {
          append("\n\nFull error details/trace:\n${details.take(500)}\n")
        }

        // Check if the flags allow adding more context for this error
        val isContextEnabled = (isCompilerIssue && compilerErrorContextEnabled || !isCompilerIssue && gradleErrorContextEnabled)

        // Look at the error location and add context from that file, if applicable.
        if (file != null && isContextEnabled) {
          getErrorFileLocationContext(fileLocation, aiExcludeService)?.let {
            append("\n${it.first}\n")
            filesUsedAsContext.add(it.second)
          }
        }

        // For sync-related issues, attach source code from Gradle files in the project
        val areGradleFilesRelevant = (isSyncIssue || result is FailureResult) && !isCompilerIssue
        if (areGradleFilesRelevant && gradleErrorContextEnabled) {
          getGradleFilesContext(project, aiExcludeService)?.let {
            append("\n${it.first}\n")
            filesUsedAsContext.addAll(it.second)
          }
        }
      }

      val verb = if (isSyncIssue) "sync" else "build"

      val query = buildString {
        val firstPart = "I'm getting an error trying to $verb my project. The error is \"$errorName\"."
        val lastPart = "Explain this error and how to fix it."
        append(firstPart)
        append("\n\n")
        // Trim down the context so that the entire query is below the maximum length
        val trimmedContext = context.take(studioBot.MAX_QUERY_CHARS - firstPart.length - lastPart.length).trim()
        append(trimmedContext)
        append("\n\n")
        append(lastPart)
      }

      val source = if (isSyncIssue) StudioBot.RequestSource.SYNC else StudioBot.RequestSource.BUILD

      // This is how the query will appear in the chat timeline
      val displayText = "Explain build error: $errorName"
      val prompt = buildPrompt(project) {
        userMessage {
          text(query, filesUsed = filesUsedAsContext)
        }
      }
      studioBot.chat(project).sendChatQuery(prompt, source, displayText = displayText)
    }
  }

  companion object {
    /**
     * Returns a string representing the file the given navigatable points to with the line number indicated if applicable,
     * and a reference to that file.
     */
    @VisibleForTesting
    fun getErrorFileLocationContext(navigatable: FileNavigatable, aiExcludeService: AiExcludeService): Pair<String, VirtualFile>? {
      val file = navigatable.fileDescriptor?.file ?: return null

      if (aiExcludeService.isFileExcluded(file)) {
        return null
      }

      val fileText = runReadAction { file.readText() }

      val fileTextWithErrorArrow =
        if (navigatable.filePosition.startLine == -1 || navigatable.filePosition.endLine == -1) {
          fileText
        }
        else {
          fileText.lines().mapIndexed { i, line ->
            if (i >= navigatable.filePosition.startLine && i <= navigatable.filePosition.endLine)
              "---> $line"
            else "     $line"
          }
            .joinToString("\n")
        }

      return Pair("""
The error is in this file:
${file.path}

The error is located at the line marked with --->:
```
$fileTextWithErrorArrow
```
      """.trimIndent(),
                  file
      )
    }

    /**
     * Returns a string of contextual information and source code extracted from Gradle files
     * throughout the project, and a list of files which were accessed.
     */
    @VisibleForTesting
    fun getGradleFilesContext(project: Project, aiExcludeService: AiExcludeService): Pair<String, List<VirtualFile>>? {
      val gradleFiles = runReadAction {
        project.modules.flatMap {
          listOfNotNull(it.getBuildScriptPsiFile(), it.getBuildScriptSettingsPsiFile())
        }.distinctBy { it.virtualFile.path }
      }.filterNot {
        aiExcludeService.isFileExcluded(it.virtualFile)
      }

      if (gradleFiles.isEmpty()) return null

      val gradleFilesText = "Project Gradle files, separated by -------: \n" + runReadAction {
        gradleFiles.map {
          """
  Module: ${it.module?.name}
  Path: ${it.virtualFile.path}
  Contents:
    ${it.text}
        """.trimIndent()
        }
      }.joinToString("\n-------\n")

      return Pair("$gradleFilesText\n------", gradleFiles.map { it.virtualFile })
    }

    @VisibleForTesting
    fun getErrorDetails(result: EventResult?): String? {
      return when (result) {
        is FailureResult -> {
          result.failures?.mapNotNull { it.message + "\n" + it.description }?.joinToString("\n")
        }

        is MessageEventResult -> {
          result.details
        }

        else -> null
      }
        ?.replace(ASK_STUDIO_BOT_LINK_TEXT, "")
        ?.replace(ASK_STUDIO_BOT_UNTIL_EOL, "")
    }

    private fun getSelectedNodes(myTree: Tree): List<ExecutionNode> {
      return TreeUtil.collectSelectedObjects<ExecutionNode?>(myTree) { path: TreePath? ->
        TreeUtil.getLastUserObject(ExecutionNode::class.java, path)
      }
    }

    private fun String.trimMessagesWithLongStacktrace(n: Int = 25) = split("\n").take(n).joinToString("\n")
  }
}
