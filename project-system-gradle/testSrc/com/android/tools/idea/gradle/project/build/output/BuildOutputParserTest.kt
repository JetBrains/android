/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

/**
 * This implementation tests output from one task, suitable for testing on partial gradle outputs.
 */
@RunWith(Parameterized::class)
abstract class BuildOutputParserTest {
  companion object {
    @JvmStatic
    @Parameters(name="isGeminiAvailable={0}")
    fun parameters() = listOf(
      arrayOf(true),
      arrayOf(false),
    )
  }

  @Parameter
  @JvmField
  var isGeminiAvailable: Boolean? = null

  @get:Rule
  val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  val parsers = arrayListOf<BuildOutputParser>()

  lateinit var taskId: ExternalSystemTaskId

  @Before
  fun setup() {
    taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)

    ExternalSystemOutputParserProvider.EP_NAME.extensions.forEach {
      parsers.addAll(it.getBuildOutputParsers(taskId))
    }

    val geminiPluginApi = object : GeminiPluginApi {
      override val MAX_QUERY_CHARS = Int.MAX_VALUE
      override fun isAvailable(): Boolean = isGeminiAvailable!!
      override fun sendChatQuery(project: Project, prompt: LlmPrompt, displayText: String?, requestSource: GeminiPluginApi.RequestSource) {
      }

      override fun stageChatQuery(project: Project, prompt: String, requestSource: GeminiPluginApi.RequestSource) {
      }
    }
    ApplicationManager.getApplication().registerExtension(GeminiPluginApi.EP_NAME, geminiPluginApi, projectRule.testRootDisposable)
  }

  private fun parseOutput(parentEventId: String, gradleOutput: String, expectedEvents: String) {
    val messageEvents = arrayListOf<MessageEvent>()
    val progressListener = object : BuildProgressListener {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is MessageEvent) {
          messageEvents.add(event)
        }
      }
    }

    val parser = BuildOutputInstantReaderImpl(taskId, parentEventId, progressListener, parsers)
    parser.disableActiveReading()
    gradleOutput.lineSequence().forEach { parser.appendLine(it) }

    parser.closeAndGetFuture().join()

    val eventsDump = messageEvents.joinToString(separator = "\n") {
      buildString {
        appendLine("message: \"${it.message}\"")
        appendLine("FileMessageEvent: " + (it is FileMessageEvent))
        appendLine("BuildIssueEvent: " + (it is BuildIssueEvent))
        appendLine("DuplicateMessageAware: " + (it is DuplicateMessageAware))
        appendLine("group: " + it.group)
        appendLine("kind: " + it.kind)
        appendLine("parentId: " + it.parentId)
        if (it is FileMessageEvent) {
          appendLine(with(it.filePosition) { "filePosition: $file:${startLine + 1}:${startColumn + 1}-${endLine + 1}:${endColumn + 1}" })
        }
        appendLine("description:")
        appendLine(it.description)
        append("---")
      }
    }
    Truth.assertThat(eventsDump).isEqualTo(expectedEvents)
  }

  fun parseOutput(parentEventId: String, gradleOutput: String, expectedEvents: List<ExpectedEvent>) {
    val expectedEventsDump: String = expectedEvents.joinToString(separator = "\n") {
      buildString {
        appendLine("message: \"${it.message}\"")
        appendLine("FileMessageEvent: " + it.isFileMessageEvent)
        appendLine("BuildIssueEvent: " + (it.isBuildIssueEvent || expectBotLink(it)))
        appendLine("DuplicateMessageAware: " + it.isDuplicateMessageAware)
        appendLine("group: " + it.group)
        appendLine("kind: " + it.kind)
        appendLine("parentId: " + it.parentId)
        if (it.filePosition != null) {
          appendLine("filePosition: " + it.filePosition)
        }
        appendLine("description:")
        appendLine(it.description)
        if (expectBotLink(it)) {
          appendLine("<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
        }
        append("---")
      }
    }
    parseOutput(parentEventId, gradleOutput, expectedEventsDump)
  }

  private fun expectBotLink(event: ExpectedEvent): Boolean = isGeminiAvailable == true && event.kind == MessageEvent.Kind.ERROR

  data class ExpectedEvent(
    val message: String,
    val isFileMessageEvent: Boolean,
    val isBuildIssueEvent: Boolean,
    val isDuplicateMessageAware: Boolean,
    val group: String,
    val kind: MessageEvent.Kind,
    val parentId: Any,
    val filePosition: String? = null,
    val description: String
  )
}
