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
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.ExportAndroidTestResultsAction
import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.ImportTestGroup
import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.ImportTestsFromFileAction
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.isRootAggregationResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.export.AndroidTestResultsXmlFormatter
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkLinkListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.state.AndroidTestResultsUserPreferencesManager
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.TestStateStorage
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.largeFilesEditor.GuiUtils
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SystemNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.LayoutFocusTraversalPolicy
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult
import kotlin.math.max
import kotlin.math.min

private const val PASSED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.myPassedToggleButton"
private const val SKIPPED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySkippedToggleButton"

private const val MIN_FIRST_COMPONENT_PROPORTION: Float = 0.1f
private const val MAX_FIRST_COMPONENT_PROPORTION: Float = 0.9f

/**
 * A console view to display a test execution and result of Android instrumentation tests.
 *
 * @param parentDisposable a parent disposable which this view's lifespan is tied with.
 * @param project a project which this test suite view belongs to.
 * @param module a module which this test suite view belongs to. If null is given, some functions such as source code lookup
 * will be disabled in this view.
 * @param toolWindowId a tool window ID of which this view is to be displayed in.
 * @param runConfiguration a run configuration of a test. This is used for exporting test results into XML. Null is given
 * when this view displays an imported test result.
 */
class AndroidTestSuiteView @UiThread @JvmOverloads constructor(
  parentDisposable: Disposable,
  private val myProject: Project,
  module: Module?,
  private val toolWindowId: String? = null,
  private val runConfiguration: RunConfiguration? = null,
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

  private val myExportTestResultsAction = ExportAndroidTestResultsAction().apply {
    toolWindowId = this@AndroidTestSuiteView.toolWindowId
    runConfiguration = this@AndroidTestSuiteView.runConfiguration
  }

  private val myComponentsSplitter: OnePixelSplitter = object: OnePixelSplitter() {
    init {
      setHonorComponentsMinimumSize(false)
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
    }

    override fun doLayout() {
      // The internal proportion value can be greater than maxProportion and smaller
      // than minProportion when you change component's side (b/170234515).
      proportion = max(MIN_FIRST_COMPONENT_PROPORTION, min(proportion, MAX_FIRST_COMPONENT_PROPORTION))

      super.doLayout()
    }
  }

  @VisibleForTesting val myResultsTableView: AndroidTestResultsTableView
  @VisibleForTesting val myDetailsView: AndroidTestSuiteDetailsView

  @VisibleForTesting val myLogger: AndroidTestSuiteLogger = AndroidTestSuiteLogger()

  // Number of devices which we will run tests against.
  private val myScheduledDevices: MutableSet<AndroidDevice> = sortedSetOf(compareBy { it.id })
  private var myStartedDevices: MutableSet<AndroidDevice> = sortedSetOf(compareBy { it.id })
  private var myFinishedDevices: MutableSet<AndroidDevice> = sortedSetOf(compareBy { it.id })

  private var scheduledTestCases = 0
  private var passedTestCases = 0
  private var failedTestCases = 0
  private var skippedTestCases = 0

  // A timestamp when the test execution is scheduled.
  private var myTestStartTimeMillis: Long = 0
  private var myTestFinishedTimeMillis: Long = 0

  private val myIsImportedResult: Boolean = runConfiguration == null

  // If not null, this value is used as the test execution time instead of a measured
  // duration by myClock.
  var testExecutionDurationOverride: Duration? = null

  init {
    val androidTestResultsUserPreferencesManager: AndroidTestResultsUserPreferencesManager? = if (runConfiguration is AndroidTestRunConfiguration) {
      val scheduledDeviceIds = HashSet<String>()
      myScheduledDevices.forEach { scheduledDeviceIds.add(it.id) }
      AndroidTestResultsUserPreferencesManager(runConfiguration, scheduledDeviceIds) }
    else {
      null
    }
    myResultsTableView = AndroidTestResultsTableView(
      this,
      JavaPsiFacade.getInstance(myProject),
      module,
      module?.let { TestArtifactSearchScopes.getInstance(module) },
      myLogger,
      androidTestResultsUserPreferencesManager
    )
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
    myDeviceAndApiLevelFilterComboBoxAction.listener = object: DeviceAndApiLevelFilterComboBoxActionListener {
      override fun onFilterUpdated() {
        myResultsTableView.refreshTable()
      }
    }

    val testFilterActionGroup = DefaultActionGroup()
    testFilterActionGroup.addAll(
      myPassedToggleButton,
      mySkippedToggleButton,
      Separator.getInstance(),
      myDeviceAndApiLevelFilterComboBoxAction,
      Separator.getInstance(),
      myResultsTableView.createExpandAllAction(),
      myResultsTableView.createCollapseAllAction(),
      Separator.getInstance(),
      myResultsTableView.createNavigateToPreviousFailedTestAction(),
      myResultsTableView.createNavigateToNextFailedTestAction(),
      Separator.getInstance(),
      ImportTestGroup(),
      ImportTestsFromFileAction(),
      myExportTestResultsAction
    )

    val myFocusableActionToolbar: ActionToolbar = object: ActionToolbarImpl(ActionPlaces.ANDROID_TEST_SUITE_TABLE,
                                                                            testFilterActionGroup, true) {
      override fun createToolbarButton(action: AnAction,
                                       look: ActionButtonLook?,
                                       place: String,
                                       presentation: Presentation,
                                       minimumSize: Dimension): ActionButton {
        return super.createToolbarButton(action, look, place, presentation, minimumSize).apply {
          // Toolbar buttons are not accessible by tab key in IntelliJ's default implementation
          // when the screen reader is disabled. We override the behavior here and make it
          // always focusable so that you can navigate through buttons by tab key.
          isFocusable = true
        }
      }
    }
    myFocusableActionToolbar.setTargetComponent(myResultsTableView.getComponent())
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
          add(myFocusableActionToolbar.component)
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
      myProgressBar.maximum = scheduledTestCases * myScheduledDevices.size
      myProgressBar.value = completedTestCases * myStartedDevices.size
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
    myStatusText.minimumSize = myStatusText.preferredSize

    val statusBreakdownText = StringBuilder("${scheduledTestCases} tests")
    if (myScheduledDevices.size > 1) {
      statusBreakdownText.append(", ${myScheduledDevices.size} devices")
    }

    if (myTestFinishedTimeMillis != 0L) {
      val testDuration = testExecutionDurationOverride ?: Duration.ofMillis(myTestFinishedTimeMillis - myTestStartTimeMillis)
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
      if (myScheduledDevices.add(device)) {
        myDeviceAndApiLevelFilterComboBoxAction.addDevice(device)
        if (myScheduledDevices.size > 1) {
          myDetailsView.isDeviceSelectorListVisible = true
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
  }

  @AnyThread
  override fun onTestSuiteStarted(device: AndroidDevice, testSuite: AndroidTestSuite) {
    AppUIUtil.invokeOnEdt {
      scheduledTestCases += testSuite.testCaseCount
      myStartedDevices.add(device)
      updateProgress()
    }
  }

  @AnyThread
  override fun onTestCaseStarted(device: AndroidDevice,
                                 testSuite: AndroidTestSuite,
                                 testCase: AndroidTestCase) {
    AppUIUtil.invokeOnEdt {
      myResultsTableView.addTestCase(device, testCase)
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
      if (benchmarkOutput.isNotEmpty()) {
        val benchmark = BenchmarkOutput(benchmarkOutput)
        benchmark.print(myDetailsView.rawTestLogConsoleView, ConsoleViewContentType.NORMAL_OUTPUT, BenchmarkLinkListener(myProject))
      }
      when (Preconditions.checkNotNull(testCase.result)) {
        AndroidTestCaseResult.PASSED -> passedTestCases++
        AndroidTestCaseResult.FAILED -> {
          if (failedTestCases == 0) {
            myDetailsView.selectDevice(device)
            myResultsTableView.selectAndroidTestCase(testCase)
          }
          failedTestCases++
        }
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
      myFinishedDevices.add(device)
      if (myFinishedDevices.size == myScheduledDevices.size) {
        myTestFinishedTimeMillis = myClock.millis()
        showSystemNotification()
        showNotificationBalloonIfToolWindowIsNotActive()

        // Don't allow re-exporting the imported result.
        if (!myIsImportedResult) {
          myExportTestResultsAction.apply {
            devices = myScheduledDevices.toList()
            rootResultsNode = myResultsTableView.rootResultsNode
            executionDuration = Duration.ofMillis(myTestFinishedTimeMillis - myTestStartTimeMillis)
          }
          saveHistory()
        }
      }
      updateProgress()
      myResultsTableView.setTestSuiteResultForDevice(
        device, testSuite.result ?: AndroidTestSuiteResult.CANCELLED)
      myDetailsView.reloadAndroidTestResults()
    }
  }

  @AnyThread
  override fun onRerunScheduled(device: AndroidDevice) {
    AppUIUtil.invokeOnEdt {
      myFinishedDevices.remove(device)
      myStartedDevices.remove(device)
      myResultsTableView.setTestSuiteResultForDevice(device, null)
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
      val stats = myResultsTableView.rootResultsNode.results.getResultStats()
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
      val stats = myResultsTableView.rootResultsNode.results.getResultStats()
      return when {
        stats.failed > 0 -> NotificationType.ERROR
        else -> NotificationType.INFORMATION
      }
    }

  @UiThread
  private fun saveHistory() {
    val runConfiguration = runConfiguration ?: return
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(
        myProject,
        SmRunnerBundle.message("sm.test.runner.results.form.save.test.results.title"),
        false,
        PerformInBackgroundOption.ALWAYS_BACKGROUND) {

        private lateinit var myResultFile: File

        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          val historyFileName =
            PathUtil.suggestFileName(runConfiguration.name) + " - " +
            SimpleDateFormat(SMTestRunnerResultsForm.HISTORY_DATE_FORMAT, Locale.US).format(Date(myClock.millis())) + ".xml"
          val outputFile = File(TestStateStorage.getTestHistoryRoot(myProject!!), historyFileName)
          FileUtilRt.createParentDirs(outputFile)

          val transformerFactory = TransformerFactory.newInstance() as SAXTransformerFactory
          val transformerHandler = transformerFactory.newTransformerHandler().apply {
            transformer.apply {
              setOutputProperty(OutputKeys.INDENT, "yes")
              setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            }
            setResult(StreamResult(FileWriter(outputFile)))
          }
          AndroidTestResultsXmlFormatter(
            Duration.ofMillis(myTestFinishedTimeMillis - myTestStartTimeMillis),
            myResultsTableView.rootResultsNode,
            myScheduledDevices.toList(),
            runConfiguration,
            transformerHandler).execute()
          myResultFile = outputFile
        }

        override fun onSuccess() {
          if (::myResultFile.isInitialized && myResultFile.exists()) {
            AbstractImportTestsAction.adjustHistory(myProject)
            TestHistoryConfiguration.getInstance(myProject).registerHistoryItem(
              myResultFile.name, runConfiguration.name, runConfiguration.type.id)
          }
        }
      })
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