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
package com.android.tools.idea.insights.ai

import com.android.flags.junit.FlagRule
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.formatForTests
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.SUPPORTED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.android.tools.idea.insights.AI_INSIGHT_WITH_CODE_CONTEXT
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.client.AiInsightCache
import com.android.tools.idea.insights.client.AiInsightClient
import com.android.tools.idea.insights.client.FakeAiInsightClient
import com.android.tools.idea.insights.client.GeminiAiInsightClient
import com.android.tools.idea.insights.client.createGeminiInsightRequest
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.stacktrace.StacktraceGroup
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.ui.FakeToolWindow
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiInsightToolkitTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val flagRule = FlagRule(StudioFlags.AQI_FIX_WITH_AGENT, false)

  private val conn = mock<Connection>().apply { doReturn(true).whenever(this).isMatchingProject() }

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  private lateinit var scope: CoroutineScope
  private lateinit var geminiToolWindow: FakeToolWindow
  private lateinit var deprecationDataProvider: DevServicesDeprecationDataProvider

  @Before
  fun setUp() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    ExtensionTestUtil.maskExtensions(GeminiPluginApi.EP_NAME, listOf(fakeGeminiPluginApi), projectRule.disposable)

    scope = projectRule.disposable.createCoroutineScope()
    geminiToolWindow = createFakeToolWindow(projectRule.project, projectRule.disposable, "Gemini")
    deprecationDataProvider = mock<DevServicesDeprecationDataProvider>()
    whenever(deprecationDataProvider.getCurrentDeprecationData(any(), any()))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    application.replaceService(DevServicesDeprecationDataProvider::class.java, deprecationDataProvider, projectRule.disposable)
  }

  @Test
  fun `code context resolver returns empty result when context sharing is off`() = runBlocking {
    val toolKit = createToolkit(codeContextResolver = FakeCodeContextResolver(listOf(CodeContext("a/b/c", "blah"))))

    fakeGeminiPluginApi.contextAllowed = false
    assertThat(toolKit.getSource(conn, StacktraceGroup())).isEqualTo(CodeContextData.DISABLED)

    fakeGeminiPluginApi.contextAllowed = true
    assertThat(toolKit.getSource(conn, StacktraceGroup()).codeContext).isNotEmpty()
  }

  @Test
  fun `code context resolver returns empty result when connection does not match project`() = runBlocking {
    doReturn(false).whenever(conn).isMatchingProject()
    val toolKit = createToolkit(codeContextResolver = FakeCodeContextResolver(listOf(CodeContext("a/b/c", "blah"))))
    fakeGeminiPluginApi.contextAllowed = true

    assertThat(toolKit.getSource(conn, StacktraceGroup()).isEmpty()).isTrue()
  }

  @Test
  fun `insight deprecation data checks gemini first`() = runBlocking {
    whenever(deprecationDataProvider.getCurrentDeprecationData(eq("gemini/gemini"), any()))
      .thenReturn(DevServicesDeprecationData("Gemini", "desc", "url", true, UNSUPPORTED))
    whenever(deprecationDataProvider.getCurrentDeprecationData(eq("aqi/insights"), any()))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    val toolkit = createToolkit()

    val data = toolkit.insightDeprecationData
    assertThat(data.isUnsupported()).isTrue()
    assertThat(data.header).isEqualTo("Gemini")
    assertThat(data.description).isEqualTo("desc")
    assertThat(data.moreInfoUrl).isEqualTo("url")
    assertThat(data.showUpdateAction).isTrue()
  }

  @Test
  fun `insight deprecation data checks insights after checking gemini`() = runBlocking {
    whenever(deprecationDataProvider.getCurrentDeprecationData(eq("gemini/gemini"), any()))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    whenever(deprecationDataProvider.getCurrentDeprecationData(eq("aqi/insights"), any()))
      .thenReturn(DevServicesDeprecationData("AQI", "desc", "url", true, UNSUPPORTED))
    val toolkit = createToolkit()

    val data = toolkit.insightDeprecationData
    assertThat(data.isUnsupported()).isTrue()
    assertThat(data.header).isEqualTo("AQI")
    assertThat(data.description).isEqualTo("desc")
    assertThat(data.moreInfoUrl).isEqualTo("url")
    assertThat(data.showUpdateAction).isTrue()
  }

  @Test
  fun `toolkit reuses cached insights`() = runBlocking {
    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    val toolkit = createToolkit(cache)

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)
    assertThat(insight.valueOrNull()).isEqualTo(expectedInsight())
  }

  @Test
  fun `toolkit caches new insight`() = runBlocking {
    val cache = AiInsightCache()
    val toolkit = createToolkit(cache)

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)
    assertThat(insight.valueOrNull()).isEqualTo(expectedInsight())
    assertThat(cache.getAiInsight(CONNECTION1, ISSUE1.id, null, ContextSharingState.DISABLED))
      .isEqualTo(expectedInsight().copy(isCached = true))
  }

  @Test
  fun `toolkit prefers insight generated with code context regardless of context sharing setting`() = runBlocking {
    fakeGeminiPluginApi.contextAllowed = false
    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, AI_INSIGHT_WITH_CODE_CONTEXT)
    val toolkit = createToolkit(cache)
    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)

    assertThat(insight.valueOrNull()).isEqualTo(AI_INSIGHT_WITH_CODE_CONTEXT.copy(isCached = true))
  }

  @Test
  fun `when context sharing is enabled, toolkit does not serve cached insight generated without context`() = runBlocking {
    fakeGeminiPluginApi.contextAllowed = true
    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)

    val codeContextData =
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

    val toolkit =
      createToolkit(cache, aiInsightClient = GeminiAiInsightClient(projectRule.project, FakeCodeContextResolver(codeContextData)))

    val expectedPromptText =
      """
      |USER
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
      |```
      |a/b/c/HelloWorld2.kt:
      |```
      |package a.b.c
      |
      |fun helloWorld2() {
      |  println("Hello World 2")
      |}
      |```
      """
        .trimMargin()
    val loadingState = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)

    assertThat(fakeGeminiPluginApi.receivedPrompt?.formatForTests()).isEqualTo(expectedPromptText)

    val insight = loadingState.valueOrNull() ?: fail("LoadingState did not have an insight")
    assertThat(insight.insightSource).isEqualTo(InsightSource.STUDIO_BOT)
  }

  @Test
  fun `toolkit checks condition before fetching insight`() = runBlocking {
    val toolkit = createToolkit { _, _ -> LoadingState.UnsupportedOperation(null) }

    assertThat(toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent))
      .isInstanceOf(LoadingState.UnsupportedOperation::class.java)
  }

  @Test
  fun `toolkit returns new insight with force regenerate`() = runBlocking {
    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    val toolkit = createToolkit(cache)

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent, true)
    assertThat(insight.valueOrNull()).isNotNull()
    assertThat(insight.valueOrNull()).isNotEqualTo(DEFAULT_AI_INSIGHT)
  }

  private fun createToolkit(
    cache: AiInsightCache = AiInsightCache(),
    codeContextResolver: CodeContextResolver = FakeCodeContextResolver(emptyList()),
    aiInsightClient: AiInsightClient = FakeAiInsightClient,
    fetchInsightCondition: (FailureType, Event) -> LoadingState.Done<AiInsight>? = { _, _ -> null },
  ) =
    object : AiInsightToolkit(projectRule.project, codeContextResolver, aiInsightClient, cache) {
      override val aiInsightOnboardingProvider: InsightsOnboardingProvider
        get() = StubInsightsOnboardingProvider()

      override suspend fun validateFetchInsightPrecondition(failureType: FailureType, event: Event) =
        fetchInsightCondition(failureType, event)
    }

  private suspend fun expectedInsight() =
    FakeAiInsightClient.fetchCrashInsight(createGeminiInsightRequest(CONNECTION1, ISSUE1.id, null, ISSUE1.sampleEvent))
}
