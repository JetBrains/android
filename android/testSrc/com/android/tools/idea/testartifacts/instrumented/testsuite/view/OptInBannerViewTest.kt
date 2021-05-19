/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UiElement
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionResultType
import com.intellij.execution.Executor
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.TimeoutUtil
import com.intellij.util.config.Storage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_MOCKS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings
import org.mockito.MockitoAnnotations
import javax.swing.JPanel

/**
 * Unit tests for [OptInBannerView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class OptInBannerViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Mock lateinit var mockStorage: Storage
  @Mock lateinit var mockExecutor: Executor
  @Mock lateinit var mockLogger: AndroidTestSuiteLogger

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    resetToDefaultSettings()
    AndroidTestConfiguration.getInstance().ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX = false
  }

  @After
  fun tearDown() {
    resetToDefaultSettings()
  }

  private fun resetToDefaultSettings() {
    AndroidTestConfiguration.getInstance().loadState(AndroidTestConfiguration())
  }

  @Test
  fun attachTheBannerToBaseConsole() {
    val baseConsole = createBaseConsole()
    val consoleWithBanner = createConsoleViewWithOptInBanner(baseConsole)
    Disposer.register(disposableRule.disposable, consoleWithBanner)

    assertThat(consoleWithBanner).isNotSameAs(baseConsole)
  }

  @Test
  fun shouldNotAttachTheBannerWhenDisabled() {
    AndroidTestConfiguration.getInstance().SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER = false
    val baseConsole = createBaseConsole()
    val consoleWithBanner = createConsoleViewWithOptInBanner(baseConsole)
    Disposer.register(disposableRule.disposable, consoleWithBanner)

    assertThat(consoleWithBanner).isSameAs(baseConsole)
  }

  @Test
  fun shouldNotAttachTheBannerWhenUserHasOptedInAlready() {
    AndroidTestConfiguration.getInstance().ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX = true
    val baseConsole = createBaseConsole()
    val consoleWithBanner = createConsoleViewWithOptInBanner(baseConsole)
    Disposer.register(disposableRule.disposable, consoleWithBanner)

    assertThat(consoleWithBanner).isSameAs(baseConsole)
  }

  @Test
  fun clickOnEnableButtonInBanner() {
    val banner = OptInBannerView(mockLogger, confirmationDurationSeconds = 0)

    banner.hyperLinkLabelToEnable.doClick()

    val config = AndroidTestConfiguration.getInstance()
    assertThat(config.ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX).isTrue()
    assertThat(config.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER).isFalse()
    assertThat(banner.actionLinkContainer.isVisible).isFalse()

    assertThat(ProgressIndicatorUtils.withTimeout(5000) {
      while (banner.rootPanel.isVisible) {
        dispatchAllEventsInIdeEventQueue()
        ProgressManager.checkCanceled()
        TimeoutUtil.sleep(100)
      }
      true
    }).isTrue()

    verify(mockLogger).reportClickInteraction(eq(UiElement.TEST_SUITE_OPT_IN_BANNER),
                                              eq(UserInteractionResultType.ACCEPT))
  }

  @Test
  fun clickOnDismissButtonInBanner() {
    val banner = OptInBannerView(mockLogger)

    banner.hyperLinkLabelToDismiss.doClick()

    val config = AndroidTestConfiguration.getInstance()
    assertThat(config.ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX).isFalse()
    assertThat(config.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER).isFalse()
    assertThat(banner.rootPanel.isVisible).isFalse()

    verify(mockLogger).reportClickInteraction(eq(UiElement.TEST_SUITE_OPT_IN_BANNER),
                                              eq(UserInteractionResultType.DISMISS))
  }

  @Test
  fun impressionLogging() {
    val consoleWithBanner = createConsoleViewWithOptInBanner(createBaseConsole(), mockLogger)

    // Impressions are reported at the time of disposal.
    Disposer.dispose(consoleWithBanner)

    verify(mockLogger).addImpressionWhenDisplayed(any(), eq(UiElement.TEST_SUITE_OPT_IN_BANNER))
    verify(mockLogger).reportImpressions()
  }

  private fun createBaseConsole(): BaseTestsOutputConsoleView {
    val properties = mock(TestConsoleProperties::class.java,
                          withSettings().apply {
                            useConstructor(mockStorage, projectRule.project, mockExecutor)
                            defaultAnswer(RETURNS_MOCKS)
                          })
    val testProxy = mock(AbstractTestProxy::class.java)
    val baseConsole = mock(BaseTestsOutputConsoleView::class.java,
                           withSettings().apply {
                             useConstructor(properties, testProxy)
                             defaultAnswer(RETURNS_MOCKS)
                           })
    `when`(baseConsole.component).thenReturn(JPanel())
    return baseConsole
  }
}