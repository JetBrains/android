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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.android.tools.journeys.proto.Artifact
import com.android.tools.journeys.proto.ArtifactType
import com.android.tools.journeys.proto.Step
import com.android.tools.journeys.proto.Turn
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import java.io.File
import java.util.Base64
import javax.swing.JPanel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/** Unit tests for [DetailsViewContentView]. */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val headerActions = emptyList<AnAction>()
  @Mock lateinit var mockLogger: AndroidTestSuiteLogger

  @Mock(answer = Answers.RETURNS_DEEP_STUBS) lateinit var mockTestResults: AndroidTestResults

  @get:Rule val rules: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule()).around(disposableRule)

  private val createdViews = mutableListOf<DetailsViewContentView>()

  private fun createView(): DetailsViewContentView {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger, headerActions)
    createdViews.add(view)
    return view
  }

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)
    createdViews.clear()
  }

  @After
  fun tearDown() {
    try {
      createdViews.forEach { view ->
        view.pathResolutionFuture?.let {
          try {
            PlatformTestUtil.waitForFuture(it)
          } catch (e: Exception) {
            // Ignore expected CancellationExceptions or other failures during cleanup.
          }
        }
        view.myScreenshotResultView.imageLoadFutures.forEach { future ->
          try {
            PlatformTestUtil.waitForFuture(future)
          } catch (e: Exception) {}
        }
      }
    } finally {
      createdViews.clear()
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testResultLabelOnPassing() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.PASSED)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("<html><font color='#6cad74'>Passed</font></html>")
  }

  @Test
  fun testResultLabelOnFailing() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("<html><font color='#b81708'>Failed</font></html>")
  }

  @Test
  fun testResultLabelOnFailingWithErrorStackTrace() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("ErrorStackTrace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("<html><font color='#b81708'>Failed</font> ErrorStackTrace</html>")
  }

  @Test
  fun testResultLabelHtmlEscaping() {
    val view = createView()
    val testDevice = device("device id", "<device name>")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("<ErrorStackTrace>")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>&lt;device name&gt;</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("<html><font color='#b81708'>Failed</font> &lt;ErrorStackTrace&gt;</html>")
  }

  @Test
  fun testResultLabelOnRunning() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.IN_PROGRESS)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("Running on device name")
  }

  @Test
  fun testResultLabelNoTestStatus() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(null)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("No test status available")
  }

  @Test
  fun logsView() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun logsViewWithErrorStackTrace() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\nerror stack trace")
  }

  @Test
  fun logsViewWithNoLogsAndErrorStackTrace() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("error stack trace")
  }

  @Test
  fun logsViewWithNoMessage() {
    val view = createView()
    val testDevice = device("device id", "device name")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("No logs available")
    assertThat(view.logsTab.isHidden).isFalse()
  }

  @Test
  fun logsViewShouldClearPreviousMessage() {
    val view = createView()
    val testDevice = device("device id", "device name")

    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")

    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message 2")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message 2\n")
  }

  @Test
  fun logsViewShouldShouldNotRefreshWhenMessageUnchanged() {
    var refreshCount = 0
    val view =
      object : DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger, headerActions) {
        override fun refreshLogsView() {
          super.refreshLogsView()
          refreshCount++
        }
      }
    createdViews.add(view)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()
    view.myLogsView.waitAllRequests()

    assertThat(refreshCount).isEqualTo(1)
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun benchmarkTab() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput("test benchmark message"))

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }

    view.myBenchmarkView.waitAllRequests()
    assertThat(view.myBenchmarkView.text).isEqualTo("test benchmark message\n")
    assertThat(view.myBenchmarkTab.isHidden).isFalse()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(view.tabs.selectedInfo).isEqualTo(view.myBenchmarkTab)
  }

  @Test
  fun benchmarkTabIsHiddenIfNoOutput() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myBenchmarkView.waitAllRequests()
    assertThat(view.myBenchmarkView.text).isEqualTo("")
    assertThat(view.myBenchmarkTab.isHidden).isTrue()
  }

  @Test
  fun logging() {
    val view = createView()
    verify(mockLogger).addImpressionWhenDisplayed(view.myLogsView.component, ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_LOG_VIEW)
    verify(mockLogger)
      .addImpressionWhenDisplayed(
        view.myDeviceInfoTableView.getComponent(),
        ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DEVICE_INFO_VIEW,
      )
  }

  @Test
  fun screenshotTabsHiddenByDefault() {
    val view = createView()

    assertThat(view.myScreenshotTab.isHidden).isTrue()
    assertThat(view.myScreenshotAttributesTab.isHidden).isTrue()
    assertThat(view.myDeviceInfoTab.isHidden).isFalse()
  }

  @Test
  fun screenshotTabsDisplayedForScreenshotTests() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myScreenshotTab.isHidden).isFalse()
    assertThat(view.myScreenshotAttributesTab.isHidden).isFalse()
    assertThat(view.myDeviceInfoTab.isHidden).isTrue()
  }

  @Test
  fun screenshotTabIsSelectedForScreenshotTests() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myScreenshotTab)
  }

  @Test
  fun screenshotTabIsSelectedWhenComingFromDeviceInfoTab() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    // Force select Device Info tab first
    view.tabs.select(view.myDeviceInfoTab, false)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myScreenshotTab)
  }

  @Test
  fun screenshotTabIsNotSelectedIfUserHasSelectedLogsTab() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))

    // Simulate user selecting logs tab
    view.lastTabSelectedByUser = view.logsTab
    view.tabs.select(view.logsTab, false)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.logsTab)
  }

  @Test
  fun screenshotLogsTabAlwaysDisplayedForScreenshotTests() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.myLogsView.waitAllRequests()

    assertThat(view.myScreenshotTab.isHidden).isFalse()
    assertThat(view.myScreenshotAttributesTab.isHidden).isFalse()
    assertThat(view.logsTab.isHidden).isFalse()
    assertThat(view.myLogsView.text).isEqualTo("No logs available")
    assertThat(view.myDeviceInfoTab.isHidden).isTrue()
  }

  @Test
  fun journeysResultsTabHiddenByDefault() {
    val view = createView()

    assertThat(view.myJourneyScreenshotsTab.isHidden).isTrue()
  }

  @Test
  fun journeysResultsTabDisplayedWhenJourneyArtifactsExist() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(mapOf("Journeys.Step" to createEncodedJourneyArtifact()))

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myJourneyScreenshotsTab.isHidden).isFalse()
  }

  @Test
  fun logsTabIsSelectedWhenErrorProvidedAndUserHasNotYetSelectedATab() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("This is a test\n")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(null)
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.logsTab)
  }

  @Test
  fun logsAreAutomaticallyScrolledToTheEnd() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("This is a test\n".repeat(100))
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()
    view.myLogsView.waitAllRequests()

    assertThat(view.myLogsView.editor?.caretModel?.logicalPosition?.line)
      .isEqualTo(view.myLogsView.editor?.document?.lineCount?.minus(1) ?: -1)
  }

  @Test
  fun logsTabIsNotSelectedWhenErrorProvidedAndUserHasAlreadySelectedADifferentTab() {
    val view = createView()
    view.tabs.select(view.myDeviceInfoTab, false)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.refreshLogsView()
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myDeviceInfoTab)
  }

  @Test
  fun journeysTabIsSelectedByDefaultWhenUserHasntSelectedATabYet() {
    val view = createView()
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(mapOf("Journeys.Step" to createEncodedJourneyArtifact()))
    // The Journeys tab should be selected even though there is an error stack trace
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }

    UIUtil.dispatchAllInvocationEvents()
    assertThat(view.tabs.selectedInfo).isEqualTo(view.myJourneyScreenshotsTab)
  }

  @Test
  fun testTabFallbackWhenCurrentTabHidden() {
    val view = createView()
    val testDevice = device("device id", "device name")

    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput("test benchmark message"))
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    view.myBenchmarkView.waitAllRequests()
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myBenchmarkTab)

    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)
    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    view.myBenchmarkView.waitAllRequests()
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.logsTab)
  }

  @Test
  fun testSwapBeforeHideWhenDeviceInfoHidden() {
    val view = createView()
    val testDevice = device("device id", "device name")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    view.tabs.select(view.myDeviceInfoTab, false)
    UIUtil.dispatchAllInvocationEvents()
    assertThat(view.tabs.selectedInfo).isEqualTo(view.myDeviceInfoTab)

    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice))
      .thenReturn(mapOf("PreviewScreenshot.newImagePath" to File(projectRule.project.basePath, "newImage.png").canonicalPath))

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myScreenshotTab)
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }

  @Test
  fun `minimal console view test`() {
    runInEdtAndWait {
      // 1. Create a plain console view
      val console = ConsoleViewImpl(projectRule.project, true)
      Disposer.register(disposableRule.disposable, console)

      // Force the lazy initialization of the internal editor.
      console.component

      // 2. Print directly to it
      console.clear()
      console.print("Hello, World!", ConsoleViewContentType.NORMAL_OUTPUT)

      console.waitAllRequests()

      // 4. Assert
      assertThat(console.editor!!.document.text).isEqualTo("Hello, World!")
    }
  }

  @Test
  fun `dispose cleans up resources`() {
    val parentDisposable = Disposer.newDisposable(disposableRule.disposable)
    val view = DetailsViewContentView(parentDisposable, projectRule.project, mockLogger, headerActions)
    createdViews.add(view)

    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.pathResolutionFuture?.let { PlatformTestUtil.waitForFuture(it) }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(view.myLogcat).isEqualTo("test logcat message")
    assertThat(view.myErrorStackTrace).isEqualTo("error stack trace")

    Disposer.dispose(parentDisposable)

    assertThat(view.myLogcat).isEmpty()
    assertThat(view.myErrorStackTrace).isEmpty()
  }

  /** Verifies that the "Logs" tab doesn't contain a redundant internal heading label. The tab title itself is sufficient. */
  @Test
  fun logsTabShouldNotHaveRedundantHeadingLabel() {
    val view = createView()
    val tabComponent = view.logsTab.component as JPanel

    val labels = UIUtil.findComponentsOfType(tabComponent, javax.swing.JLabel::class.java)
    assertThat(labels.map { it.text }).doesNotContain("Logs")
  }

  private fun createEncodedJourneyArtifact(): String {
    val step =
      Step.newBuilder()
        .addTurns(
          Turn.newBuilder()
            .setDescription("The action taken")
            .setReasoning("The reasoning behind the action")
            .addArtifactsBefore(Artifact.newBuilder().setType(ArtifactType.SCREENSHOT).setUri("/path/to/screenshot.png").build())
            .build()
        )
        .build()

    return Base64.getEncoder().encodeToString(step.toByteArray())
  }
}
