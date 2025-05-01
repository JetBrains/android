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
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.FakeGeminiPluginApi
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GeminiAiInsightClientTest {

  @get:Rule val projectRule = ProjectRule()

  private var expectedPromptText: String = ""

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi
  private lateinit var codeContextResolver: CodeContextResolver

  @Before
  fun setup() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.generateResponse = "a/b/c/HelloWorld1.kt,a/b/c/HelloWorld2.kt"
    codeContextResolver =
      FakeCodeContextResolver(
        listOf(
          CodeContext(
            "a/b/c/HelloWorld1.kt",
            """
              |package a.b.c
              |
              |fun helloWorld() {
              |  println("Hello World")
              |}
              """
              .trimMargin(),
          ),
          CodeContext(
            "a/b/c/HelloWorld2.kt",
            """
              |package a.b.c
              |
              |fun helloWorld2() {
              |  println("Hello World 2")
              |}
              """
              .trimMargin(),
          ),
        )
      )

    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
  }

  @Test
  fun `test gemini client without code context`() = runBlocking {
    val client =
      GeminiAiInsightClient(projectRule.project, AppInsightsCacheImpl(), codeContextResolver)

    val request =
      GeminiCrashInsightRequest(
        connection = CONNECTION1,
        issueId = ISSUE1.id,
        variantId = null,
        deviceName = "DeviceName",
        apiLevel = "ApiLevel",
        event = ISSUE1.sampleEvent,
      )

    (codeContextResolver as FakeCodeContextResolver).codeContext = emptyList()

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
      |retrofit2.HttpException: HTTP 401 
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight(request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("a/b/c/HelloWorld1.kt,a/b/c/HelloWorld2.kt")
  }

  @Test
  fun `test gemini client with code context`() = runBlocking {
    val client =
      GeminiAiInsightClient(projectRule.project, AppInsightsCacheImpl(), codeContextResolver)

    val request =
      GeminiCrashInsightRequest(
        connection = CONNECTION1,
        issueId = ISSUE1.id,
        variantId = null,
        deviceName = "DeviceName",
        apiLevel = "ApiLevel",
        event = ISSUE1.sampleEvent,
      )

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
      |retrofit2.HttpException: HTTP 401 
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
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
    val insight = client.fetchCrashInsight(request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("a/b/c/HelloWorld1.kt,a/b/c/HelloWorld2.kt")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }

  @Test
  fun `client reuses cached insights`() = runBlocking {
    val cache = AppInsightsCacheImpl()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    val client = GeminiAiInsightClient(projectRule.project, cache, codeContextResolver)

    (codeContextResolver as FakeCodeContextResolver).codeContext = emptyList()

    val request =
      GeminiCrashInsightRequest(
        connection = CONNECTION1,
        issueId = ISSUE1.id,
        variantId = null,
        deviceName = "DeviceName",
        apiLevel = "ApiLevel",
        event = ISSUE1.sampleEvent,
      )

    assertThat(client.fetchCrashInsight(request))
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))
  }

  @Test
  fun `client caches new insight`() = runBlocking {
    val cache = AppInsightsCacheImpl()
    val client = GeminiAiInsightClient(projectRule.project, cache, codeContextResolver)

    (codeContextResolver as FakeCodeContextResolver).codeContext = emptyList()
    fakeGeminiPluginApi.generateResponse = ""

    val request =
      GeminiCrashInsightRequest(
        connection = CONNECTION1,
        issueId = ISSUE1.id,
        variantId = null,
        deviceName = "DeviceName",
        apiLevel = "ApiLevel",
        event = ISSUE1.sampleEvent,
      )

    assertThat(client.fetchCrashInsight(request))
      .isEqualTo(AiInsight("", insightSource = InsightSource.STUDIO_BOT))
    assertThat(cache.getAiInsight(CONNECTION1, ISSUE1.id, null, CodeContextData(emptyList())))
      .isEqualTo(AiInsight("", isCached = true, insightSource = InsightSource.STUDIO_BOT))
  }

  @Test
  fun `client omits code context when connection does not match project`() = runBlocking {
    val client =
      GeminiAiInsightClient(projectRule.project, AppInsightsCacheImpl(), codeContextResolver)
    val connection = mock<Connection>()
    `when`(connection.isMatchingProject()).thenReturn(false)

    val request =
      GeminiCrashInsightRequest(
        connection = connection,
        issueId = ISSUE1.id,
        variantId = null,
        deviceName = "DeviceName",
        apiLevel = "ApiLevel",
        event = ISSUE1.sampleEvent,
      )

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
      |retrofit2.HttpException: HTTP 401 
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
      |${'\t'}dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight(request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("a/b/c/HelloWorld1.kt,a/b/c/HelloWorld2.kt")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
    assertThat(insight.codeContextData)
      .isEqualTo(CodeContextData(emptyList(), contextSharingState = ContextSharingState.ALLOWED))
  }

  @Test
  fun `create gemini insight request truncates at the context limit`() = runBlocking {
    val client =
      GeminiAiInsightClient(projectRule.project, AppInsightsCacheImpl(), codeContextResolver)

    val event = ISSUE1.sampleEvent

    // This should truncate the second source code file.
    fakeGeminiPluginApi.MAX_QUERY_CHARS = 650
    val request = createGeminiInsightRequest(CONNECTION1, ISSUE1.id, null, event)

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
      |a/b/c/HelloWorld1.kt:
      |```
      |package a.b.c
      |
      |fun helloWorld() {
      |  println("Hello World")
      |}
      |```"""
        .trimMargin()
    val insight = client.fetchCrashInsight(request)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    assertThat(insight.rawInsight).isEqualTo("a/b/c/HelloWorld1.kt,a/b/c/HelloWorld2.kt")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }
}
