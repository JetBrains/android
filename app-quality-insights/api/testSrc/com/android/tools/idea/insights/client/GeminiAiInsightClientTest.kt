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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.Content
import com.android.tools.idea.studiobot.GenerationConfig
import com.android.tools.idea.studiobot.Model
import com.android.tools.idea.studiobot.ModelConfig
import com.android.tools.idea.studiobot.ModelType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.testing.disposable
import com.google.android.studio.gemini.CodeSnippet
import com.google.android.studio.gemini.GeminiInsightsRequest
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeminiAiInsightClientTest {

  @get:Rule val projectRule = ProjectRule()

  private var expectedPromptText: String = ""
  private val fakeStudioBot =
    object : StudioBot {
      override val MAX_QUERY_CHARS = 1000

      override fun aiExcludeService(project: Project) = AiExcludeService.FakeAiExcludeService()

      override fun chat(project: Project) = ChatService.StubChatService()

      override fun model(project: Project, modelType: ModelType) =
        object : Model {
          override fun config() = ModelConfig(emptySet(), 1000, 1000, true)

          override fun generateContent(prompt: Prompt, config: GenerationConfig) = flow {
            assertThat(prompt.messages.size).isEqualTo(2)
            val preamble = prompt.messages[0]
            assertThat(preamble.chunks.size).isEqualTo(1)
            val systemChunk = preamble.chunks[0] as Prompt.TextChunk
            assertThat(systemChunk.text).isEqualTo(GEMINI_PREAMBLE)
            val message = prompt.messages[1]
            assertThat(message.chunks.size).isEqualTo(1)
            val chunk = message.chunks[0] as Prompt.TextChunk
            assertThat(chunk.filesUsed).isEmpty()
            assertThat(chunk.text).isEqualTo(expectedPromptText)
            emit(Content.TextContent("TextContent start"))
            emit(Content.FunctionCall("someFunctionName", emptyMap()))
            emit(Content.TextContent("This is added after FunctionCall"))
          }
        }
    }

  @Before
  fun setup() {
    application.replaceService(StudioBot::class.java, fakeStudioBot, projectRule.disposable)
  }

  @Test
  fun `test gemini client`() = runBlocking {
    val client = GeminiAiInsightClient.create(projectRule.project)

    val request =
      GeminiInsightsRequest.newBuilder()
        .apply {
          deviceName = "DeviceName"
          apiLevel = "ApiLevel"
          stackTrace = "stack\n  Trace"
        }
        .build()

    expectedPromptText =
      """
      Explain this exception from my app running on DeviceName with Android version ApiLevel:
      Exception:
      ```
      stack
        Trace
      ```
    """
        .trimIndent()
    val insight = client.fetchCrashInsight("", request)

    assertThat(insight.rawInsight).isEqualTo("TextContent start\nThis is added after FunctionCall")
  }

  @Test
  fun `test gemini client with code context`() = runBlocking {
    val client = GeminiAiInsightClient.create(projectRule.project)

    val request =
      GeminiInsightsRequest.newBuilder()
        .apply {
          deviceName = "DeviceName"
          apiLevel = "ApiLevel"
          stackTrace = "stack\n  Trace"
          addAllCodeSnippets(
            listOf(
              CodeSnippet.newBuilder()
                .apply {
                  codeSnippet =
                    """
                  package a.b.c
                  
                  fun helloWorld() {
                    println("Hello World")
                  }
                """
                      .trimIndent()
                  filePath = "a/b/c/HelloWorld1.kt"
                }
                .build(),
              CodeSnippet.newBuilder()
                .apply {
                  codeSnippet =
                    """
                  package a.b.c
                  
                  fun helloWorld2() {
                    println("Hello World 2")
                  }
                """
                      .trimIndent()
                  filePath = "a/b/c/HelloWorld2.kt"
                }
                .build(),
            )
          )
        }
        .build()

    expectedPromptText =
      """
      Explain this exception from my app running on DeviceName with Android version ApiLevel.
      Please reference the provided source code if they are helpful.
      Exception:
      ```
      stack
        Trace
      ```
      a/b/c/HelloWorld1.kt:
      ```
      package a.b.c
      
      fun helloWorld() {
        println("Hello World")
      }
      ```
      a/b/c/HelloWorld2.kt:
      ```
      package a.b.c
      
      fun helloWorld2() {
        println("Hello World 2")
      }
      ```
    """
        .trimIndent()
    val insight = client.fetchCrashInsight("", request)

    assertThat(insight.rawInsight).isEqualTo("TextContent start\nThis is added after FunctionCall")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }
}
