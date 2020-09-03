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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes.Companion.getInstance
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.isRootAggregationResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.largeFilesEditor.GuiUtils
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.SystemNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.time.Clock
import java.time.Duration
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import kotlin.math.min

private const val PASSED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.myPassedToggleButton"
private const val SKIPPED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySkippedToggleButton"
private const val SORT_BY_NAME_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySortByNameToggleButton"
private const val SORT_BY_DURATION_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySortByDurationToggleButton"

/**
 * A console view to display a test execution and result of Android instrumentation tests.
 *
 * @param parentDisposable a parent disposable which this view's lifespan is tied with.
 * @param project a project which this test suite view belongs to.
 * @param module a module which this test suite view belongs to. If null is given, some functions such as source code lookup
 * will be disabled in this view.
 * @param toolWindowId a tool window ID of which this view is to be displayed in.
 */
class AndroidTestSuiteView @UiThread @JvmOverloads constructor(
  parentDisposable: Disposable,
  private val myProject: Project,
  module: Module?,
  private val toolWindowId: String? = null,
  private val myClock: Clock = Clock.systemDefaultZone()
) : ConsoleView,
    AndroidTestResultListener,
    AndroidTestResultsTableListener,
    AndroidTestSuiteDetailsViewListener,
    AndroidTestSuiteViewController {
  @VisibleForTesting val myProgressBar: JProgressBar = JProgressBar().apply {
    preferredSize = Dimension(170, preferredSize.height)
    maximumSize = preferredSize
  }
  @VisibleForTesting val myStatusText: JBLabel = JBLabel()
  @VisibleForTesting val myStatusBreakdownText: JBLabel = JBLabel()

  @VisibleForTesting val myDeviceAndApiLevelFilterComboBoxAction = DeviceAndApiLevelFilterComboBoxAction()

  @VisibleForTesting val myPassedToggleButton = MyToggleAction(
    "Show passed tests", getIconFor(AndroidTestCaseResult.PASSED, false),
    PASSED_TOGGLE_BUTTON_STATE_KEY, true)
  @VisibleForTesting val mySkippedToggleButton = MyToggleAction(
    "Show skipped tests", getIconFor(AndroidTestCaseResult.SKIPPED, false),
    SKIPPED_TOGGLE_BUTTON_STATE_KEY, true)

  @VisibleForTesting val mySortByNameToggleButton = MyToggleAction(
    PlatformEditorBundle.message("action.sort.alphabetically"),
    AllIcons.ObjectBrowser.Sorted,
    SORT_BY_NAME_TOGGLE_BUTTON_STATE_KEY,
    false)
  @VisibleForTesting val mySortByDurationToggleButton = MyToggleAction(
    ExecutionBundle.message("junit.runing.info.sort.by.statistics.action.name"),
    AllIcons.RunConfigurations.SortbyDuration,
    SORT_BY_DURATION_TOGGLE_BUTTON_STATE_KEY,
    false)

  private val myComponentsSplitter: JBSplitter = JBSplitter().apply {
    setHonorComponentsMinimumSize(false)
    dividerWidth = 1
    divider.background = UIUtil.CONTRAST_BORDER_COLOR
  }

  @VisibleForTesting val myResultsTableView: AndroidTestResultsTableView
  @VisibleForTesting val myDetailsView: AndroidTestSuiteDetailsView

  private val myInsertionOrderMap: MutableMap<AndroidTestResults, Int> = mutableMapOf()
  @VisibleForTesting val myLogger: AndroidTestSuiteLogger = AndroidTestSuiteLogger()

  // Number of devices which we will run tests against.
  private var myScheduledDevices = 0
  private var myStartedDevices = 0
  private var myFinishedDevices = 0

  private var scheduledTestCases = 0
  private var passedTestCases = 0
  private var failedTestCases = 0
  private var skippedTestCases = 0

  // A timestamp when the test execution is scheduled.
  private var myTestStartTimeMillis: Long = 0
  private var myTestFinishedTimeMillis: Long = 0

  init {
    val testArtifactSearchScopes = module?.let { getInstance(module) }
    myResultsTableView = AndroidTestResultsTableView(
      this, JavaPsiFacade.getInstance(myProject), testArtifactSearchScopes, myLogger)
    myResultsTableView.setRowFilter { testResults: AndroidTestResults ->
      if (testResults.isRootAggregationResult()) {
        return@setRowFilter true
      }
      when (testResults.getTestResultSummary()) {
        AndroidTestCaseResult.PASSED -> myPassedToggleButton.isSelected
        AndroidTestCaseResult.SKIPPED -> mySkippedToggleButton.isSelected
        else -> true
      }
    }
    myResultsTableView.setColumnFilter(myDeviceAndApiLevelFilterComboBoxAction.filter)
    myResultsTableView.setRowComparator(java.util.Comparator { o1, o2 ->
      if (mySortByNameToggleButton.isSelected) {
        val result = TEST_NAME_COMPARATOR.compare(o1, o2)
        if (result != 0) {
          return@Comparator result
        }
      }
      if (mySortByDurationToggleButton.isSelected) {
        val result = TEST_DURATION_COMPARATOR.compare(o1, o2) * -1
        if (result != 0) {
          return@Comparator result
        }
      }
      myInsertionOrderMap.getOrDefault(o1, Int.MAX_VALUE).compareTo(
        myInsertionOrderMap.getOrDefault(o2, Int.MAX_VALUE))
    })
    myDeviceAndApiLevelFilterComboBoxAction.listener = object: DeviceAndApiLevelFilterComboBoxActionListener {
      override fun onFilterUpdated() {
        myResultsTableView.refreshTable()
      }
    }

    val testFilterAndSorterActionGroup = DefaultActionGroup()
    testFilterAndSorterActionGroup.addAll(
      myPassedToggleButton,
      mySkippedToggleButton,
      Separator.getInstance(),
      myDeviceAndApiLevelFilterComboBoxAction,
      Separator.getInstance(),
      mySortByNameToggleButton,
      mySortByDurationToggleButton,
      Separator.getInstance(),
      myResultsTableView.createExpandAllAction(),
      myResultsTableView.createCollapseAllAction(),
      Separator.getInstance(),
      myResultsTableView.createNavigateToPreviousFailedTestAction(),
      myResultsTableView.createNavigateToNextFailedTestAction())

    val contentPanel = JPanel(BorderLayout()).apply {
      add(JPanel().apply {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        add(JPanel().apply {
          layout = BoxLayout(this, BoxLayout.LINE_AXIS)
          GuiUtils.setStandardLineBorderToPanel(this, 0, 0, 1, 0)
          add(Box.createRigidArea(Dimension(10, 0)))
          add(JBLabel("Status"))
          add(Box.createRigidArea(Dimension(10, 0)))
          add(myProgressBar)
          add(MyItemSeparator())
          add(myStatusText)
          add(MyItemSeparator())
          add(myStatusBreakdownText)
          add(Box.createHorizontalGlue())
        })
        add(JPanel().apply {
          layout = BoxLayout(this, BoxLayout.LINE_AXIS)
          GuiUtils.setStandardLineBorderToPanel(this, 0, 0, 1, 0)
          add(Box.createRigidArea(Dimension(10, 0)))
          add(JBLabel("Filter tests:"))
          add(ActionManager.getInstance().createActionToolbar(
            ActionPlaces.ANDROID_TEST_SUITE_TABLE,
            testFilterAndSorterActionGroup, true).component)
        })
      }, BorderLayout.NORTH)
      add(myResultsTableView.getComponent(), BorderLayout.CENTER)
      minimumSize = Dimension()
    }

    myComponentsSplitter.firstComponent = contentPanel
    myDetailsView = AndroidTestSuiteDetailsView(parentDisposable, this, this, myProject, myLogger).apply {
      isDeviceSelectorListVisible = false
      rootPanel.isVisible = false
      rootPanel.minimumSize = Dimension()
    }
    myComponentsSplitter.secondComponent = myDetailsView.rootPanel
    myResultsTableView.selectRootItem()
    updateProgress()
    myLogger.addImpressions(ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW,
                            ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW_TABLE_ROW)
    Disposer.register(parentDisposable, this)
  }

  @UiThread
  private fun updateProgress() {
    val completedTestCases = passedTestCases + failedTestCases + skippedTestCases
    if (scheduledTestCases == 0) {
      myProgressBar.value = 0
      myProgressBar.isIndeterminate = false
      myProgressBar.foreground = ColorProgressBar.BLUE
    }
    else {
      myProgressBar.maximum = scheduledTestCases * myScheduledDevices
      myProgressBar.value = completedTestCases * myStartedDevices
      myProgressBar.isIndeterminate = false
      if (failedTestCases > 0) {
        myProgressBar.foreground = ColorProgressBar.RED
      }
      else if (completedTestCases == scheduledTestCases) {
        myProgressBar.foreground = ColorProgressBar.GREEN
      }
    }
    updateStatusText()
  }

  @UiThread
  private fun updateStatusText() {
    val statusText = StringBuilder()
    if (failedTestCases > 0) {
      statusText.append(
        "<b><font color='#${ColorUtil.toHex(ColorProgressBar.RED)}'>${failedTestCases} failed</font></b>")
    }
    if (passedTestCases > 0) {
      if (statusText.isNotEmpty()) {
        statusText.append(", ")
      }
      statusText.append("${passedTestCases} passed")
    }
    if (skippedTestCases > 0) {
      if (statusText.isNotEmpty()) {
        statusText.append(", ")
      }
      statusText.append("${skippedTestCases} skipped")
    }
    if (statusText.isEmpty()) {
      statusText.append("0 passed")
    }
    myStatusText.text = "<html><nobr>${statusText}</nobr></html>"
    myStatusText.maximumSize = myStatusText.preferredSize

    val statusBreakdownText = StringBuilder("${scheduledTestCases} tests")
    if (myScheduledDevices > 1) {
      statusBreakdownText.append(", ${myScheduledDevices} devices")
    }

    if (myTestFinishedTimeMillis != 0L) {
      val testDuration = Duration.ofMillis(myTestFinishedTimeMillis - myTestStartTimeMillis)
      val roundedTestDuration = if (testDuration < Duration.ofHours(1)) {
        testDuration
      } else {
        Duration.ofSeconds(testDuration.seconds)
      }
      statusBreakdownText.append(", ${StringUtil.formatDuration(roundedTestDuration.toMillis(), "\u2009")}")
    }

    myStatusBreakdownText.text = statusBreakdownText.toString()
  }

  @AnyThread
  override fun onTestSuiteScheduled(device: AndroidDevice) {
    AppUIUtil.invokeOnEdt {
      if (myTestStartTimeMillis == 0L) {
        myTestStartTimeMillis = myClock.millis()
      }
      myDeviceAndApiLevelFilterComboBoxAction.addDevice(device)
      myScheduledDevices++
      if (myScheduledDevices == 1) {
        myResultsTableView.showTestDuration(device)
        myResultsTableView.showTestStatusColumn = false
      }
      else {
        myResultsTableView.showTestDuration(null)
        myDetailsView.isDeviceSelectorListVisible = true
        myResultsTableView.showTestStatusColumn = true
      }
      myResultsTableView.addDevice(device)
      myDetailsView.addDevice(device)
      updateProgress()

      if (myComponentsSplitter.width > 0) {
        myComponentsSplitter.proportion =
          min(0.6f, myResultsTableView.preferredTableWidth.toFloat() / myComponentsSplitter.width)
      }
    }
  }

  @AnyThread
  override fun onTestSuiteStarted(device: AndroidDevice, testSuite: AndroidTestSuite) {
    AppUIUtil.invokeOnEdt {
      scheduledTestCases += testSuite.testCaseCount
      myStartedDevices++
      updateProgress()
    }
  }

  @AnyThread
  override fun onTestCaseStarted(device: AndroidDevice,
                                 testSuite: AndroidTestSuite,
                                 testCase: AndroidTestCase) {
    AppUIUtil.invokeOnEdt {
      myResultsTableView.addTestCase(device, testCase)
        .iterator()
        .forEachRemaining { results: AndroidTestResults ->
          myInsertionOrderMap.computeIfAbsent(results) { myInsertionOrderMap.size }
        }
      myDetailsView.reloadAndroidTestResults()
    }
  }

  @AnyThread
  override fun onTestCaseFinished(device: AndroidDevice,
                                  testSuite: AndroidTestSuite,
                                  testCase: AndroidTestCase) {
    AppUIUtil.invokeOnEdt {
      // Include a benchmark output to a raw output console for backward compatibility.
      val benchmarkOutput = testCase.benchmark
      if (!benchmarkOutput.isBlank()) {
        for (line in benchmarkOutput.lines()) {
          print("benchmark: $line\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
      }
      when (Preconditions.checkNotNull(testCase.result)) {
        AndroidTestCaseResult.PASSED -> passedTestCases++
        AndroidTestCaseResult.FAILED -> failedTestCases++
        AndroidTestCaseResult.SKIPPED -> skippedTestCases++
      }
      updateProgress()
      myResultsTableView.refreshTable()
      myDetailsView.reloadAndroidTestResults()
    }
  }

  @AnyThread
  override fun onTestSuiteFinished(device: AndroidDevice, testSuite: AndroidTestSuite) {
    AppUIUtil.invokeOnEdt {
      myFinishedDevices++
      if (myFinishedDevices == myScheduledDevices) {
        myTestFinishedTimeMillis = myClock.millis()
        showSystemNotification()
        showNotificationBalloonIfToolWindowIsNotActive()
      }
      updateProgress()
      myResultsTableView.refreshTable()
      myDetailsView.reloadAndroidTestResults()
    }
  }

  @UiThread
  private fun showSystemNotification() {
    SystemNotifications.getInstance().notify("TestRunner", notificationTitle, notificationContent)
  }

  @UiThread
  private fun showNotificationBalloonIfToolWindowIsNotActive() {
    if (toolWindowId.isNullOrBlank()) {
      return
    }
    val toolWindowManager = ToolWindowManager.getInstance(myProject)
    if (toolWindowId == toolWindowManager.activeToolWindowId) {
      return
    }
    val displayId = "Test Results: ${toolWindowId}"
    val group = NotificationGroup.findRegisteredGroup(displayId) ?: NotificationGroup.toolWindowGroup(displayId, toolWindowId)
    group.createNotification(notificationTitle, notificationContent, notificationType).notify(myProject)
  }

  @get:UiThread
  private val notificationTitle: String
    get() {
      val stats = myResultsTableView.aggregatedTestResults.getResultStats()
      return when {
        stats.failed > 0 -> "Tests Failed"
        stats.cancelled > 0 -> "Tests Cancelled"
        else -> "Tests Passed"
      }
    }

  @get:UiThread
  private val notificationContent: String
    get() {
      val content = StringBuilder()
      if (failedTestCases > 0) {
        content.append("${failedTestCases} failed")
      }
      if (passedTestCases > 0) {
        if (content.isNotEmpty()) {
          content.append(", ")
        }
        content.append("${passedTestCases} passed")
      }
      if (skippedTestCases > 0) {
        if (content.isNotEmpty()) {
          content.append(", ")
        }
        content.append("${skippedTestCases} skipped")
      }
      if (content.isEmpty()) {
        content.append("0 passed")
      }
      return content.toString()
    }

  @get:UiThread
  private val notificationType: NotificationType
    get() {
      val stats = myResultsTableView.aggregatedTestResults.getResultStats()
      return when {
        stats.failed > 0 -> NotificationType.ERROR
        else -> NotificationType.INFORMATION
      }
    }

  @UiThread
  override fun onAndroidTestResultsRowSelected(selectedResults: AndroidTestResults,
                                               selectedDevice: AndroidDevice?) {
    openAndroidTestSuiteDetailsView(selectedResults, selectedDevice)
  }

  @UiThread
  override fun onAndroidTestSuiteDetailsViewCloseButtonClicked() {
    closeAndroidTestSuiteDetailsView()
  }

  @UiThread
  private fun openAndroidTestSuiteDetailsView(results: AndroidTestResults,
                                              selectedDevice: AndroidDevice?) {
    myLogger.addImpression(
      if (orientation === AndroidTestSuiteViewController.Orientation.HORIZONTAL) {
        ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DETAILS_HORIZONTAL_VIEW
      }
      else {
        ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DETAILS_VERTICAL_VIEW
      }
    )
    myDetailsView.setAndroidTestResults(results)
    if (selectedDevice != null) {
      myDetailsView.selectDevice(selectedDevice)
    }
    else if (results.isRootAggregationResult()) {
      myDetailsView.selectRawOutput()
    }
    if (!myDetailsView.rootPanel.isVisible) {
      myDetailsView.rootPanel.isVisible = true
    }
  }

  @UiThread
  private fun closeAndroidTestSuiteDetailsView() {
    myDetailsView.rootPanel.isVisible = false
    myResultsTableView.clearSelection()
  }

  @get:UiThread
  @set:UiThread
  override var orientation: AndroidTestSuiteViewController.Orientation
    get() {
      val isVertical = myComponentsSplitter.orientation
      return if (isVertical) AndroidTestSuiteViewController.Orientation.VERTICAL else AndroidTestSuiteViewController.Orientation.HORIZONTAL
    }
    set(orientation) {
      myComponentsSplitter.orientation =
        orientation === AndroidTestSuiteViewController.Orientation.VERTICAL
    }

  override fun print(text: String, contentType: ConsoleViewContentType) {
    myDetailsView.rawTestLogConsoleView.print(text, contentType)
  }

  override fun clear() {
    myDetailsView.rawTestLogConsoleView.clear()
  }

  override fun scrollTo(offset: Int) {
    myDetailsView.rawTestLogConsoleView.scrollTo(offset)
  }

  override fun attachToProcess(processHandler: ProcessHandler) {
    // Put this test suite view to the process handler as AndroidTestResultListener so the view
    // is notified the test results and to be updated.
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, this)
    myDetailsView.rawTestLogConsoleView.attachToProcess(processHandler)
  }

  override fun setOutputPaused(value: Boolean) {
    myDetailsView.rawTestLogConsoleView.isOutputPaused = value
  }

  override fun isOutputPaused(): Boolean {
    return myDetailsView.rawTestLogConsoleView.isOutputPaused
  }

  override fun hasDeferredOutput(): Boolean {
    return myDetailsView.rawTestLogConsoleView.hasDeferredOutput()
  }

  override fun performWhenNoDeferredOutput(runnable: Runnable) {
    myDetailsView.rawTestLogConsoleView.performWhenNoDeferredOutput(runnable)
  }

  override fun setHelpId(helpId: String) {
    myDetailsView.rawTestLogConsoleView.setHelpId(helpId)
  }

  override fun addMessageFilter(filter: Filter) {
    myDetailsView.rawTestLogConsoleView.addMessageFilter(filter)
  }

  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {
    myDetailsView.rawTestLogConsoleView.printHyperlink(hyperlinkText, info)
  }

  override fun getContentSize(): Int {
    return myDetailsView.rawTestLogConsoleView.contentSize
  }

  override fun canPause(): Boolean {
    return myDetailsView.rawTestLogConsoleView.canPause()
  }

  override fun createConsoleActions(): Array<AnAction> {
    return AnAction.EMPTY_ARRAY
  }

  override fun allowHeavyFilters() {
    myDetailsView.rawTestLogConsoleView.allowHeavyFilters()
  }

  override fun getComponent(): JComponent {
    return myComponentsSplitter
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return myResultsTableView.getPreferredFocusableComponent()
  }

  override fun dispose() {
    myLogger.reportImpressions()
  }

  private class MyItemSeparator : JComponent() {
    init {
      val mySize = JBDimension(JBUIScale.scale(20), JBUIScale.scale(24), /*preScaled=*/true)
      minimumSize = mySize
      maximumSize = mySize
      preferredSize = mySize
      size = mySize
    }

    override fun paintComponent(g: Graphics) {
      if (parent == null) return
      val center = width.toDouble() / 2
      val gap = JBUIScale.scale(2)
      val y2 = parent.height - gap * 2
      g.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
      LinePainter2D.paint((g as Graphics2D),
                          center, gap.toDouble(),
                          center, y2.toDouble())
    }
  }

  @VisibleForTesting
  inner class MyToggleAction(actionText: String,
                             actionIcon: Icon?,
                             private val propertiesComponentKey: String,
                             private val defaultState: Boolean) : ToggleAction(Supplier { actionText },
                                                                               actionIcon) {

    var isSelected: Boolean = PropertiesComponent.getInstance(myProject).getBoolean(
      propertiesComponentKey, defaultState)
      set(value) {
        field = value
        PropertiesComponent.getInstance(myProject).setValue(
          propertiesComponentKey, isSelected, defaultState)
        myResultsTableView.refreshTable()
      }

    override fun isSelected(e: AnActionEvent): Boolean {
      return isSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      isSelected = state
      PropertiesComponent.getInstance(myProject)
        .setValue(propertiesComponentKey, isSelected, defaultState)
      myResultsTableView.refreshTable()
    }
  }
}