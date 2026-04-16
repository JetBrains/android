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
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.stacktrace.StacktraceGroup
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.ui.FakeToolWindow
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginUsersRule
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiInsightToolkitTest {

  private val projectRule = ProjectRule()
  private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(FlagRule(StudioFlags.AQI_FIX_WITH_AGENT, false)).around(loginUsersRule)

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
    loginUsersRule.setActiveUser("test@google.com")

    projectRule.project.service<AppInsightsSettings>().enableAutoGenerate("test@google.com")
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

    setupAiInsightContributor()

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)
    assertThat(insight.valueOrNull()).isEqualTo(expectedInsight())
  }

  @Test
  fun `toolkit caches new insight`() = runBlocking {
    val cache = AiInsightCache()
    val toolkit = createToolkit(cache)

    setupAiInsightContributor()

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

    setupAiInsightContributor()

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
        )
      )

    var fakeInsightFetched = false
    setupAiInsightContributor { connection, event ->
      fakeInsightFetched = true
      AiInsight("insight for $connection and $event", event, insightSource = InsightSource.STUDIO_BOT)
    }

    val toolkit = createToolkit(cache)

    val loadingState = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)

    assertThat(fakeInsightFetched).isTrue()
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
    setupAiInsightContributor { _, event -> AiInsight("a different insight", event, insightSource = InsightSource.STUDIO_BOT) }

    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    val toolkit = createToolkit(cache)

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent, true)
    assertThat(insight.valueOrNull()).isNotNull()
    assertThat(insight.valueOrNull()).isNotEqualTo(DEFAULT_AI_INSIGHT)
  }

  @Test
  fun `getFirstAvailableContributor returns the first contributor that can contribute`() {
    val contributor1 = mock<AiInsightContributor>()
    whenever(contributor1.canContribute()).thenReturn(false)

    val contributor2 = mock<AiInsightContributor>()
    whenever(contributor2.canContribute()).thenReturn(true)

    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(contributor1, contributor2), projectRule.disposable)

    assertThat(AiInsightContributor.getFirstAvailableContributor()).isEqualTo(contributor2)
  }

  @Test
  fun `getFirstAvailableContributor returns null if no contributor can contribute`() {
    val contributor1 = mock<AiInsightContributor>()
    whenever(contributor1.canContribute()).thenReturn(false)

    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(contributor1), projectRule.disposable)

    assertThat(AiInsightContributor.getFirstAvailableContributor()).isNull()
  }

  @Test
  fun `isModelAvailable returns result from GeminiPluginApi when flag is disabled`() {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(false, projectRule.disposable)
    val toolkit = createToolkit()

    fakeGeminiPluginApi.available = true
    assertThat(toolkit.isModelAvailable()).isTrue()

    fakeGeminiPluginApi.available = false
    assertThat(toolkit.isModelAvailable()).isFalse()
  }

  @Test
  fun `isModelAvailable returns result from first available contributor when flag is enabled`() {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(true, projectRule.disposable)
    val contributor = mock<AiInsightContributor>()
    whenever(contributor.canContribute()).thenReturn(true)
    whenever(contributor.isModelAvailable()).thenReturn(true)
    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(contributor), projectRule.disposable)

    val toolkit = createToolkit()
    assertThat(toolkit.isModelAvailable()).isTrue()

    whenever(contributor.isModelAvailable()).thenReturn(false)
    assertThat(toolkit.isModelAvailable()).isFalse()
  }

  @Test
  fun `fetchInsight returns failure when auto-generation is disabled and not forced`() = runBlocking {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(true, projectRule.disposable)
    val toolkit = createToolkit()
    projectRule.project.service<AppInsightsSettings>().disableAutoGenerate("test@google.com")

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)
    assertThat(insight).isInstanceOf(LoadingState.InsightAutogenerateDisabled::class.java)
  }

  @Test
  fun `fetchInsight proceeds when auto-generation is disabled but forced`() = runBlocking {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(true, projectRule.disposable)
    val toolkit = createToolkit()
    projectRule.project.service<AppInsightsSettings>().disableAutoGenerate("test@google.com")
    setupAiInsightContributor()

    val insight =
      toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent, forceGenerateNewInsight = true)
    assertThat(insight).isInstanceOf(LoadingState.Ready::class.java)
  }

  @Test
  fun `fetchInsight proceeds when auto-generation is enabled`() = runBlocking {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(true, projectRule.disposable)
    val toolkit = createToolkit()
    projectRule.project.service<AppInsightsSettings>().enableAutoGenerate("test@google.com")
    setupAiInsightContributor()

    val insight = toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent)
    assertThat(insight).isInstanceOf(LoadingState.Ready::class.java)
  }

  @Test
  fun `setAutoGenerate and isAutoGenerateEnabled correctly update and read from AppInsightsSettings`() = runBlocking {
    StudioFlags.AQI_FIX_WITH_AGENT.overrideForTest(true, projectRule.disposable)
    val toolkit = createToolkit()

    toolkit.setAutoGenerate(true)
    assertThat(toolkit.isAutoGenerateEnabled()).isTrue()
    assertThat(projectRule.project.service<AppInsightsSettings>().isInsightAutoGenerateEnabled("test@google.com")).isTrue()

    toolkit.setAutoGenerate(false)
    assertThat(toolkit.isAutoGenerateEnabled()).isFalse()
    assertThat(projectRule.project.service<AppInsightsSettings>().isInsightAutoGenerateEnabled("test@google.com")).isFalse()
  }

  @Test
  fun `showOnboarding calls first available contributor showOnboarding`() {
    val contributor = mock<AiInsightContributor>()
    whenever(contributor.canContribute()).thenReturn(true)
    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(contributor), projectRule.disposable)

    val toolkit = createToolkit()
    toolkit.showOnboarding()

    verify(contributor).showOnboarding(projectRule.project)
  }

  @Test
  fun `fetchInsight calls callback before fetching the insight`() = runBlocking {
    val mutex = Mutex(locked = true)
    val toolkit = createToolkit()
    setupAiInsightContributor()

    toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent) { mutex.unlock() }

    assertThat(mutex.isLocked).isFalse()
  }

  @Test
  fun `fetchInsight does not call callback when insight is returned from cache`() = runBlocking {
    val mutex = Mutex(locked = true)
    val cache = AiInsightCache()
    cache.putAiInsight(CONNECTION1, ISSUE1.id, null, AI_INSIGHT_WITH_CODE_CONTEXT)
    val toolkit = createToolkit(cache)

    toolkit.fetchInsight(CONNECTION1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent) {
      fail("Should not call the callback")
    }

    assertThat(mutex.isLocked).isTrue()
  }

  private fun createToolkit(
    cache: AiInsightCache = AiInsightCache(),
    codeContextResolver: CodeContextResolver = FakeCodeContextResolver(emptyList()),
    fetchInsightCondition: (FailureType, Event) -> LoadingState.Done<AiInsight>? = { _, _ -> null },
  ) =
    object : AiInsightToolkit(projectRule.project, codeContextResolver, cache) {
      override suspend fun validateFetchInsightPrecondition(failureType: FailureType, event: Event) =
        fetchInsightCondition(failureType, event)
    }

  private fun expectedInsight(): AiInsight {
    return AiInsight("expected insight", ISSUE1.sampleEvent)
  }

  private fun setupAiInsightContributor(fetchInsight: suspend (Connection, Event) -> AiInsight = { _, _ -> expectedInsight() }) {
    val contributor =
      object : AiInsightContributor {
        override fun canContribute() = true

        override fun isModelAvailable() = true

        override fun showOnboarding(project: Project) = Unit

        override suspend fun fetchInsight(
          connection: Connection,
          event: Event,
          project: Project,
          codeContextResolver: CodeContextResolver,
        ): AiInsight {
          return fetchInsight(connection, event)
        }
      }
    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(contributor), projectRule.disposable)
  }
}
