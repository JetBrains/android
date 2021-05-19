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

import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.ide.HelpIdProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.LightColors
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A banner which promotes Android Test Matrix view.
 */
class OptInBannerView(logger: AndroidTestSuiteLogger,
                      confirmationDurationSeconds: Long = 10L) {
  private val myBannerBackground = LightColors.YELLOW
  private val myConfig = AndroidTestConfiguration.getInstance()

  private val myLabel = JLabel().apply {
    text = "Enable the new Test Matrix optimized for both single and multi-device test results."
  }

  val hyperLinkLabelToEnable: HyperlinkLabel = HyperlinkLabel("Enable", myBannerBackground).apply {
    addHyperlinkListener {
      myConfig.ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX = true
      myConfig.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER = false

      logger.reportClickInteraction(
        ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_OPT_IN_BANNER,
        ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionResultType.ACCEPT)

      // Update message and dismiss it after the delay.
      myLabel.text = "Test Matrix is enabled for your next test run. Revert to the older UI in Settings."
      actionLinkContainer.isVisible = false
      AppExecutorUtil.getAppScheduledExecutorService().schedule(
        {
          runInEdt {
            rootPanel.isVisible = false
          }
        }, confirmationDurationSeconds, TimeUnit.SECONDS)
    }
  }

  val hyperLinkLabelToDismiss: HyperlinkLabel = HyperlinkLabel("Dismiss", myBannerBackground).apply {
    addHyperlinkListener {
      rootPanel.isVisible = false
      myConfig.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER = false

      logger.reportClickInteraction(
        ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_OPT_IN_BANNER,
        ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionResultType.DISMISS)
    }
  }

  val actionLinkContainer = NonOpaquePanel().apply {
    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
    add(hyperLinkLabelToEnable)
    add(Box.createRigidArea(Dimension(16, 0)))
    add(hyperLinkLabelToDismiss)
  }

  val rootPanel: JComponent = OpaquePanel(BorderLayout(), myBannerBackground).apply {
    isVisible = myConfig.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER
    border = JBUI.Borders.empty(5, 10)
    add(myLabel, BorderLayout.WEST)
    add(actionLinkContainer, BorderLayout.EAST)
  }

  init {
    logger.addImpressionWhenDisplayed(rootPanel,
                                      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_OPT_IN_BANNER)
  }
}

/**
 * Creates a new [ConsoleView] with a [OptInBannerView] at top.
 * A [ConsoleView] method calls are delegated to a given [baseConsoleView].
 */
@JvmOverloads
fun createConsoleViewWithOptInBanner(baseConsoleView: BaseTestsOutputConsoleView,
                                     logger: AndroidTestSuiteLogger = AndroidTestSuiteLogger()): ConsoleView {
  val config = AndroidTestConfiguration.getInstance()
  if(!config.SHOW_ANDROID_TEST_MATRIX_OPT_IN_BANNER || config.ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX) {
    return baseConsoleView
  }

  return object: ConsoleView by baseConsoleView,
                 ObservableConsoleView by baseConsoleView,
                 HelpIdProvider by baseConsoleView {
    init {
      Disposer.register(this, baseConsoleView)
    }

    private val myOptInBannerView = OptInBannerView(logger)
    private val myRootPanel = JPanel(BorderLayout()).apply {
      add(myOptInBannerView.rootPanel, BorderLayout.NORTH)
      add(baseConsoleView.component, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = myRootPanel
    override fun dispose() {
      logger.reportImpressions()
    }
  }
}