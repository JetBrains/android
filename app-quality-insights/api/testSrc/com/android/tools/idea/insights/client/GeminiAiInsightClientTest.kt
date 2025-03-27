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

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.formatForTests
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ai.FakeGeminiPluginApi
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.Language
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.testing.disposable
import com.google.android.studio.gemini.CodeSnippet
import com.google.android.studio.gemini.GeminiInsightsRequest
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeminiAiInsightClientTest {

  @get:Rule val projectRule = ProjectRule()

  private var expectedPromptText: String = ""

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setup() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.generateResponse = "TextContent start. This is added after FunctionCall"

    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
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
      |SYSTEM
      |Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
      |For headings, use H3 only. Initial explanation should not be under a heading.
      |Begin with the explanation directly. Do not add fillers at the start of response.
      |
      |USER
      |Explain this exception from my app running on DeviceName with Android version ApiLevel:
      |Exception:
      |```
      |stack
      |  Trace
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight("", request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("TextContent start. This is added after FunctionCall")
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
      |SYSTEM
      |Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
      |For headings, use H3 only. Initial explanation should not be under a heading.
      |Begin with the explanation directly. Do not add fillers at the start of response.
      |
      |USER
      |Explain this exception from my app running on DeviceName with Android version ApiLevel.
      |Please reference the provided source code if they are helpful.
      |Exception:
      |```
      |stack
      |  Trace
      |```
      |a/b/c/HelloWorld1.kt:
      |```
      |package a.b.c
      |
      |fun helloWorld() {
      |  println("Hello World")
      |}
      |```
      |a/b/c/HelloWorld2.kt:
      |```
      |package a.b.c
      |
      |fun helloWorld2() {
      |  println("Hello World 2")
      |}
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight("", request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("TextContent start. This is added after FunctionCall")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }

  @Test
  fun `create gemini insight request truncates at the context limit`() = runBlocking {
    val client = GeminiAiInsightClient.create(projectRule.project)

    val event = ISSUE1.sampleEvent
    val codeContextData =
      CodeContextData(
        listOf(
          CodeContext(
            "HelloWorld.kt",
            "a/b/c/HelloWorld.kt",
            """
            |package a.b.c
            |
            |fun helloWorld() {
            |  println("Hello World")
            |}"""
              .trimMargin(),
            Language.KOTLIN,
          ),
          CodeContext(
            "HelloWorld2.kt",
            "a/b/c/HelloWorld2.kt",
            """
            |package a.b.c
            |
            |fun helloWorld2() {
            |  println("Hello World 2")
            |}"""
              .trimMargin(),
            Language.KOTLIN,
          ),
        ),
        Experiment.TOP_THREE_SOURCES,
      )

    // This should truncate the second source code file.
    fakeGeminiPluginApi.MAX_QUERY_CHARS = 650
    val request = createGeminiInsightRequest(event, codeContextData)

    expectedPromptText =
      """
      |SYSTEM
      |Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
      |For headings, use H3 only. Initial explanation should not be under a heading.
      |Begin with the explanation directly. Do not add fillers at the start of response.
      |
      |USER
      |Explain this exception from my app running on Google Pixel 4a with Android version 12.
      |Please reference the provided source code if they are helpful.
      |Exception:
      |```
      |retrofit2.HttpException: HTTP 401 
	    |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
	    |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
      |```
      |a/b/c/HelloWorld.kt:
      |```
      |package a.b.c
      |
      |fun helloWorld() {
      |  println("Hello World")
      |}
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight("", request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("TextContent start. This is added after FunctionCall")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }
}
