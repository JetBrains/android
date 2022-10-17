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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkLinkListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.google.common.annotations.VisibleForTesting
import com.google.common.html.HtmlEscapers
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.largeFilesEditor.GuiUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.tabs.JBTabsFactory.createTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.util.Arrays
import java.util.Locale
import javax.swing.JPanel

/**
 * Shows detailed tests results for a selected device.
 */
class DetailsViewContentView(parentDisposable: Disposable, private val project: Project, logger: AndroidTestSuiteLogger) {

  /**
   * Returns the root panel.
   */
  val rootPanel: JPanel

  @VisibleForTesting val myTestResultLabel: JBLabel = JBLabel().apply { border = JBUI.Borders.empty(10) }
  @VisibleForTesting val myLogsView: ConsoleViewImpl
  @VisibleForTesting val myBenchmarkTab: TabInfo
  @VisibleForTesting val myBenchmarkView: ConsoleViewImpl
  @VisibleForTesting val myDeviceInfoTableView: AndroidDeviceInfoTableView
  @VisibleForTesting val myRetentionView: RetentionView
  @VisibleForTesting val myRetentionTab: TabInfo

  private var myAndroidDevice: AndroidDevice? = null
  private var myAndroidTestCaseResult: AndroidTestCaseResult? = null
  private var myLogcat = ""
  private var myErrorStackTrace = ""
  private var myRetentionSnapshot: File? = null
  private var needsRefreshLogsView: Boolean = true

  init {
    val tabs = createTabs(project, parentDisposable)

    // Create logcat tab.
    myLogsView = ConsoleViewImpl(project,  /*viewer=*/true)
    Disposer.register(parentDisposable, myLogsView)
    logger.addImpressionWhenDisplayed(
      myLogsView.component,
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_LOG_VIEW)
    val logsViewWithVerticalToolbar = NonOpaquePanel(BorderLayout())
    logsViewWithVerticalToolbar.add(myLogsView.component, BorderLayout.CENTER)
    val logViewToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_DETAILS_VIEW_LOG,
      DefaultActionGroup(*myLogsView.createConsoleActions()),
      false)
    logViewToolbar.setTargetComponent(myLogsView.component)
    logsViewWithVerticalToolbar.add(logViewToolbar.component, BorderLayout.EAST)
    val logsTab = TabInfo(logsViewWithVerticalToolbar)
    logsTab.text = "Logs"
    logsTab.tooltipText = "Show logcat output"
    tabs.addTab(logsTab)

    // Create benchmark tab.
    myBenchmarkView = ConsoleViewImpl(project,  /*viewer=*/true)
    Disposer.register(parentDisposable, myBenchmarkView)
    val benchmarkViewWithVerticalToolbar = NonOpaquePanel(BorderLayout())
    benchmarkViewWithVerticalToolbar.add(myBenchmarkView.component, BorderLayout.CENTER)
    val benchmarkViewToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_DETAILS_VIEW_BENCHMARK,
      DefaultActionGroup(*myBenchmarkView.createConsoleActions()),
      false)
    benchmarkViewWithVerticalToolbar.add(benchmarkViewToolbar.component, BorderLayout.EAST)
    myBenchmarkTab = TabInfo(benchmarkViewWithVerticalToolbar)
    myBenchmarkTab.text = "Benchmark"
    myBenchmarkTab.tooltipText = "Show benchmark results"
    myBenchmarkTab.isHidden = true
    tabs.addTab(myBenchmarkTab)

    // Device info tab.
    myDeviceInfoTableView = AndroidDeviceInfoTableView()
    logger.addImpressionWhenDisplayed(
      myDeviceInfoTableView.getComponent(),
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DEVICE_INFO_VIEW)
    val deviceInfoTab = TabInfo(myDeviceInfoTableView.getComponent())
    deviceInfoTab.text = "Device Info"
    deviceInfoTab.tooltipText = "Show device information"
    tabs.addTab(deviceInfoTab)

    // Android Test Retention tab.
    myRetentionView = RetentionView()
    logger.addImpressionWhenDisplayed(
      myRetentionView.component,
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_RETENTION_VIEW)
    myRetentionTab = TabInfo(myRetentionView.rootPanel)
    myRetentionTab.text = "Retention"
    myRetentionTab.tooltipText = "Show emulator snapshots of failed tests"
    tabs.addTab(myRetentionTab)
    myRetentionTab.isHidden = true

    rootPanel = JPanel(BorderLayout()).apply {
      add(JPanel(BorderLayout()).apply {
        add(myTestResultLabel, BorderLayout.CENTER)
        GuiUtils.setStandardLineBorderToPanel(this, 0, 0, 1, 0)
      }, BorderLayout.NORTH)
      add(tabs.component, BorderLayout.CENTER)
      minimumSize = Dimension()
    }
  }

  fun setPackageName(packageName: String) {
    myRetentionView.setPackageName(packageName)
  }

  fun setAndroidDevice(androidDevice: AndroidDevice) {
    myAndroidDevice = androidDevice
    refreshTestResultLabel()
    myDeviceInfoTableView.setAndroidDevice(androidDevice)
    myRetentionView.setAndroidDevice(androidDevice)
  }

  fun setAndroidTestCaseResult(result: AndroidTestCaseResult?) {
    myAndroidTestCaseResult = result
    refreshTestResultLabel()
  }

  fun setAndroidTestCaseStartTime(time: Long?) {
    myRetentionView.setStartTime(time)
  }

  fun setLogcat(logcat: String) {
    // force refresh myLogsView on first call to setLogcat
    needsRefreshLogsView = needsRefreshLogsView || (myLogcat != logcat)
    if (needsRefreshLogsView) {
      myLogcat = logcat
      refreshLogsView()
    }
  }

  fun setErrorStackTrace(errorStackTrace: String) {
    needsRefreshLogsView = myErrorStackTrace != errorStackTrace
    if (needsRefreshLogsView) {
      myErrorStackTrace = errorStackTrace
      refreshTestResultLabel()
      refreshLogsView()
    }
  }

  fun setBenchmarkText(benchmarkText: BenchmarkOutput) {
    myBenchmarkView.clear()
    for (line in benchmarkText.lines) {
      line.print(myBenchmarkView, ConsoleViewContentType.NORMAL_OUTPUT, BenchmarkLinkListener(project))
    }
    myBenchmarkTab.isHidden = benchmarkText.lines.isEmpty()
  }

  fun setRetentionInfo(retentionInfo: File?) {
    myRetentionView.setRetentionInfoFile(retentionInfo)
  }

  fun setRetentionSnapshot(rententionSnapshot: File?) {
    myRetentionSnapshot = rententionSnapshot
    refreshRetentionView()
  }

  private fun refreshRetentionView() {
    myRetentionTab.isHidden = myRetentionSnapshot == null
    myRetentionView.setSnapshotFile(myRetentionSnapshot)
  }

  private fun refreshTestResultLabel() {
    val device = myAndroidDevice
    if (device == null) {
      myTestResultLabel.text = "No test status available"
      return
    }
    val testCaseResult = myAndroidTestCaseResult
    if (testCaseResult == null) {
      myTestResultLabel.text = "No test status available on " + device.getName()
      return
    }
    if (testCaseResult.isTerminalState) {
      val statusColor = getColorFor(testCaseResult) ?: UIUtil.getActiveTextColor()
      when (testCaseResult) {
        AndroidTestCaseResult.PASSED -> myTestResultLabel.text = String.format(
          Locale.US,
          "<html><font color='%s'>Passed</font> on %s</html>",
          ColorUtil.toHtmlColor(statusColor),
          device.getName().htmlEscape())
        AndroidTestCaseResult.FAILED -> {
          val errorMessage =
            Arrays.stream(StringUtil.splitByLines(myErrorStackTrace))
              .findFirst()
              .orElse("")
          if (StringUtil.isEmptyOrSpaces(errorMessage)) {
            myTestResultLabel.text = String.format(
              Locale.US,
              "<html><font color='%s'>Failed</font> on %s</html>",
              ColorUtil.toHtmlColor(statusColor),
              device.getName().htmlEscape())
          }
          else {
            myTestResultLabel.text = String.format(
              Locale.US,
              "<html><font size='+1'>%s</font><br><font color='%s'>Failed</font> on %s</html>",
              errorMessage.htmlEscape(),
              ColorUtil.toHtmlColor(statusColor),
              device.getName().htmlEscape())
          }
        }
        AndroidTestCaseResult.SKIPPED -> myTestResultLabel.text = String.format(
          Locale.US,
          "<html><font color='%s'>Skipped</font> on %s</html>",
          ColorUtil.toHtmlColor(statusColor),
          device.getName().htmlEscape())
        AndroidTestCaseResult.CANCELLED -> myTestResultLabel.text = String.format(
          Locale.US,
          "<html><font color='%s'>Cancelled</font> on %s</html>",
          ColorUtil.toHtmlColor(statusColor),
          device.getName().htmlEscape())
        else -> {
          myTestResultLabel.text = ""
          Logger.getInstance(javaClass).warn(String.format(Locale.US, "Unexpected result type: %s", testCaseResult))
        }
      }
    }
    else {
      myTestResultLabel.text = String.format(Locale.US, "Running on %s", device.getName())
    }
  }

  @VisibleForTesting fun refreshLogsView() {
    needsRefreshLogsView = false
    myLogsView.clear()
    if (StringUtil.isEmptyOrSpaces(myLogcat) && StringUtil.isEmptyOrSpaces(myErrorStackTrace)) {
      myLogsView.print("No logcat output for this device.",
                       ConsoleViewContentType.NORMAL_OUTPUT)
      return
    }
    if (!StringUtil.isEmptyOrSpaces(myLogcat)) {
      myLogsView.print(myLogcat, ConsoleViewContentType.NORMAL_OUTPUT)
      myLogsView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
    myLogsView.print(myErrorStackTrace, ConsoleViewContentType.ERROR_OUTPUT)
  }
}

private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)
