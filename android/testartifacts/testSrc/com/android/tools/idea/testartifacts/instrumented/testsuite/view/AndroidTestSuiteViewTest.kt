/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.ImportTestGroup
import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.ImportTestsFromHistoryAction
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.dualView.TreeTableView
import com.intellij.util.TimeoutUtil.sleep
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.isNull
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [AndroidTestSuiteView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestSuiteViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Mock lateinit var processHandler: ProcessHandler
  @Mock lateinit var mockClock: Clock

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    resetToDefaultState()
  }

  @After
  fun tearDown() {
    resetToDefaultState()
  }

  private fun resetToDefaultState() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    // These persisted properties need to be reset before and after tests.
    view.myPassedToggleButton.isSelected = true
    view.mySkippedToggleButton.isSelected = true
  }

  @Test
  fun attachToProcess() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    view.attachToProcess(processHandler)

    verify(processHandler).putCopyableUserData(eq(ANDROID_TEST_RESULT_LISTENER_KEY), eq(view))

    Disposer.dispose(view)

    verify(processHandler).putCopyableUserData(eq(ANDROID_TEST_RESULT_LISTENER_KEY), isNull())
  }

  @Test
  fun detailsViewIsVisibleAndRawTestOutputIsDisplayedInitially() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    assertThat(view.myDetailsView.rootPanel.isVisible).isTrue()
    assertThat(view.myResultsTableView.getTableViewForTesting().selectedColumn).isEqualTo(0)
    assertThat(view.myResultsTableView.getTableViewForTesting().selectedRow).isEqualTo(0)
  }

  @Test
  fun openAndCloseDetailsView() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")
    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice1 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testId2", "method2", "class2", "package2")
    val testsuiteOnDevice2 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice2 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice2 = AndroidTestCase("testId2", "method2", "class2", "package2")

    view.onTestSuiteScheduled(device1)
    view.onTestSuiteScheduled(device2)

    // Test execution on device 1.
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase1OnDevice1)
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase1OnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase2OnDevice1)
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase2OnDevice1)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    // Test execution on device 2.
    view.onTestSuiteStarted(device2, testsuiteOnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase1OnDevice2)
    testcase1OnDevice2.result = AndroidTestCaseResult.SKIPPED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase1OnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase2OnDevice2)
    testcase2OnDevice2.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase2OnDevice2)
    view.onTestSuiteFinished(device2, testsuiteOnDevice2)

    // Click on the test case 2 results row.
    view.onAndroidTestResultsRowSelected(view.myResultsTableView.getTableViewForTesting().getItem(4),
                                         /*selectedDevice=*/null)

    // Verifies the details view is visible now.
    assertThat(view.myDetailsView.rootPanel.isVisible).isTrue()
    assertThat(view.myDetailsView.titleTextView.text).isEqualTo("package2.class2.method2")
    assertThat(view.myDetailsView.selectedDevice).isEqualTo(device1)

    // Click on the test case 1 results row in device2 column.
    view.onAndroidTestResultsRowSelected(view.myResultsTableView.getTableViewForTesting().getItem(2),
                                         /*selectedDevice=*/device2)

    // Verifies the details view is visible now.
    assertThat(view.myDetailsView.rootPanel.isVisible).isTrue()
    assertThat(view.myDetailsView.titleTextView.text).isEqualTo("package1.class1.method1")
    assertThat(view.myDetailsView.selectedDevice).isEqualTo(device2)

    // Finally, close the details view.
    view.onAndroidTestSuiteDetailsViewCloseButtonClicked()

    assertThat(view.myDetailsView.rootPanel.isVisible).isFalse()

    assertThat(view.myLogger.getImpressionsForTesting()).containsExactly(
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW,
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW_TABLE_ROW,
      ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DETAILS_HORIZONTAL_VIEW)
  }

  @Test
  fun filterByTestStatus() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1")
    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 4)
    val testcase1OnDevice1 = AndroidTestCase("testId1", "method1", "classA", "packageA")
    val testcase2OnDevice1 = AndroidTestCase("testId2", "method2", "classA", "packageA")
    val testcase3OnDevice1 = AndroidTestCase("testId3", "method3", "classB", "packageB")
    val testcase4OnDevice1 = AndroidTestCase("testId4", "method4", "classB", "packageB")
    val testcase5OnDevice1 = AndroidTestCase("testId5", "method5", "classC", "packageC")

    view.onTestSuiteScheduled(device1)

    // Test execution on device 1.
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase1OnDevice1)
    testcase1OnDevice1.result = AndroidTestCaseResult.FAILED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase1OnDevice1)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase2OnDevice1)
    testcase2OnDevice1.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase2OnDevice1)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase3OnDevice1)
    testcase3OnDevice1.result = AndroidTestCaseResult.SKIPPED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase3OnDevice1)

    testcase4OnDevice1.result = AndroidTestCaseResult.IN_PROGRESS
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase4OnDevice1)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase5OnDevice1)
    testcase5OnDevice1.result = AndroidTestCaseResult.CANCELLED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase5OnDevice1)

    val tableView = view.myResultsTableView.getTableViewForTesting()

    // Initially, all tests are displayed.
    assertThat(tableView.rowCount).isEqualTo(9)
    assertThat(tableView.getItem(0).getFullTestCaseName()).isEqualTo(".")  // Root aggregation (failed)
    assertThat(tableView.getItem(1).getFullTestCaseName()).isEqualTo("packageA.classA.")  // Class A aggregation (failed)
    assertThat(tableView.getItem(2).getFullTestCaseName()).isEqualTo("packageA.classA.method1")  // method 1 (failed)
    assertThat(tableView.getItem(3).getFullTestCaseName()).isEqualTo("packageA.classA.method2")  // method 2 (passed)
    assertThat(tableView.getItem(4).getFullTestCaseName()).isEqualTo("packageB.classB.")  // Class B aggregation (in progress)
    assertThat(tableView.getItem(5).getFullTestCaseName()).isEqualTo("packageB.classB.method3")  // method 3 (skipped)
    assertThat(tableView.getItem(6).getFullTestCaseName()).isEqualTo("packageB.classB.method4")  // method 4 (in progress)
    assertThat(tableView.getItem(7).getFullTestCaseName()).isEqualTo("packageC.classC.")  // Class C aggregation (cancelled)
    assertThat(tableView.getItem(8).getFullTestCaseName()).isEqualTo("packageC.classC.method5")  // method 5 (cancelled)

    // Remove "Skipped".
    view.mySkippedToggleButton.isSelected = false

    assertThat(tableView.rowCount).isEqualTo(8)
    assertThat(tableView.getItem(0).getFullTestCaseName()).isEqualTo(".")  // Root aggregation (failed)
    assertThat(tableView.getItem(1).getFullTestCaseName()).isEqualTo("packageA.classA.")  // Class A aggregation (failed)
    assertThat(tableView.getItem(2).getFullTestCaseName()).isEqualTo("packageA.classA.method1")  // method 1 (failed)
    assertThat(tableView.getItem(3).getFullTestCaseName()).isEqualTo("packageA.classA.method2")  // method 2 (passed)
    assertThat(tableView.getItem(4).getFullTestCaseName()).isEqualTo("packageB.classB.")  // Class B aggregation (in progress)
    assertThat(tableView.getItem(5).getFullTestCaseName()).isEqualTo("packageB.classB.method4")  // method 4 (in progress)
    assertThat(tableView.getItem(6).getFullTestCaseName()).isEqualTo("packageC.classC.")  // Class C aggregation (cancelled)
    assertThat(tableView.getItem(7).getFullTestCaseName()).isEqualTo("packageC.classC.method5")  // method 5 (cancelled)

    // Remove "Passed" and select "Skipped".
    view.myPassedToggleButton.isSelected = false
    view.mySkippedToggleButton.isSelected = true

    assertThat(tableView.rowCount).isEqualTo(8)
    assertThat(tableView.getItem(0).getFullTestCaseName()).isEqualTo(".")  // Root aggregation (failed)
    assertThat(tableView.getItem(1).getFullTestCaseName()).isEqualTo("packageA.classA.")  // Class A aggregation (failed)
    assertThat(tableView.getItem(2).getFullTestCaseName()).isEqualTo("packageA.classA.method1")  // method 1 (failed)
    assertThat(tableView.getItem(3).getFullTestCaseName()).isEqualTo("packageB.classB.")  // Class B aggregation (in progress)
    assertThat(tableView.getItem(4).getFullTestCaseName()).isEqualTo("packageB.classB.method3")  // method 3 (skipped)
    assertThat(tableView.getItem(5).getFullTestCaseName()).isEqualTo("packageB.classB.method4")  // method 4 (in progress)
    assertThat(tableView.getItem(6).getFullTestCaseName()).isEqualTo("packageC.classC.")  // Class C aggregation (cancelled)
    assertThat(tableView.getItem(7).getFullTestCaseName()).isEqualTo("packageC.classC.method5")  // method 5 (cancelled)

    // Remove "Passed" and "Skipped". (Nothing is selected).
    view.myPassedToggleButton.isSelected = false
    view.mySkippedToggleButton.isSelected = false

    assertThat(tableView.rowCount).isEqualTo(7)
    assertThat(tableView.getItem(0).getFullTestCaseName()).isEqualTo(".")  // Root aggregation (failed)
    assertThat(tableView.getItem(1).getFullTestCaseName()).isEqualTo("packageA.classA.")  // Class A aggregation (failed)
    assertThat(tableView.getItem(2).getFullTestCaseName()).isEqualTo("packageA.classA.method1")  // method 1 (failed)
    assertThat(tableView.getItem(3).getFullTestCaseName()).isEqualTo("packageB.classB.")  // Class B aggregation (in progress)
    assertThat(tableView.getItem(4).getFullTestCaseName()).isEqualTo("packageB.classB.method4")  // method 4 (in progress)
    assertThat(tableView.getItem(5).getFullTestCaseName()).isEqualTo("packageC.classC.")  // Class C aggregation (cancelled)
    assertThat(tableView.getItem(6).getFullTestCaseName()).isEqualTo("packageC.classC.method5")  // method 5 (cancelled)
  }

  @Test
  fun filterByTestStatusButtonStateShouldPersist() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    // All buttons are selected initially.
    assertThat(view.myPassedToggleButton.isSelected).isTrue()
    assertThat(view.mySkippedToggleButton.isSelected).isTrue()

    // Update state to false.
    view.myPassedToggleButton.isSelected = false
    view.mySkippedToggleButton.isSelected = false

    val view2 = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    // The new state should persist even after recreation of the view.
    assertThat(view2.myPassedToggleButton.isSelected).isFalse()
    assertThat(view2.mySkippedToggleButton.isSelected).isFalse()
  }

  @Test
  fun filterByDevice() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")
    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice1 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testId2", "method2", "class2", "package2")
    val testsuiteOnDevice2 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice2 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice2 = AndroidTestCase("testId2", "method2", "class2", "package2")

    view.onTestSuiteScheduled(device1)
    view.onTestSuiteScheduled(device2)

    // Test execution on device 1.
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase1OnDevice1)
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase1OnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase2OnDevice1)
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase2OnDevice1)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    // Test execution on device 2.
    view.onTestSuiteStarted(device2, testsuiteOnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase1OnDevice2)
    testcase1OnDevice2.result = AndroidTestCaseResult.SKIPPED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase1OnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase2OnDevice2)
    testcase2OnDevice2.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase2OnDevice2)
    view.onTestSuiteFinished(device2, testsuiteOnDevice2)

    // Select "device2" in the device filter ComboBox.
    val selectDevice2Action = view.myDeviceAndApiLevelFilterComboBoxAction.createActionGroup().flattenedActions().find {
      it.templateText == "deviceName2"
    }
    requireNotNull(selectDevice2Action).actionPerformed(mock())

    val tableView = view.myResultsTableView.getTableViewForTesting()
    val tableViewModel = view.myResultsTableView.getModelForTesting()
    assertThat(tableView.columnCount).isEqualTo(3)
    assertThat(tableViewModel.columns[0].name).isEqualTo("Tests")
    assertThat(tableViewModel.columns[1].name).isEqualTo("Duration")
    assertThat(tableViewModel.columns[2].name).isEqualTo("deviceName2")
  }

  @Test
  fun filterByApiLevel() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1", 29)
    val device2 = device("deviceId2", "deviceName2", 28)
    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice1 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testId2", "method2", "class2", "package2")
    val testsuiteOnDevice2 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice2 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice2 = AndroidTestCase("testId2", "method2", "class2", "package2")

    view.onTestSuiteScheduled(device1)
    view.onTestSuiteScheduled(device2)

    // Test execution on device 1.
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase1OnDevice1)
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase1OnDevice1)
    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase2OnDevice1)
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase2OnDevice1)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    // Test execution on device 2.
    view.onTestSuiteStarted(device2, testsuiteOnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase1OnDevice2)
    testcase1OnDevice2.result = AndroidTestCaseResult.SKIPPED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase1OnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase2OnDevice2)
    testcase2OnDevice2.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase2OnDevice2)
    view.onTestSuiteFinished(device2, testsuiteOnDevice2)

    // Select "API 29" in the API level filter ComboBox.
    view.myDeviceAndApiLevelFilterComboBoxAction.createActionGroup().getChildren(null)
    val selectApi29Action = view.myDeviceAndApiLevelFilterComboBoxAction.createActionGroup().flattenedActions().find {
      it.templateText == "API 29"
    }
    requireNotNull(selectApi29Action).actionPerformed(mock())

    val tableView = view.myResultsTableView.getTableViewForTesting()
    val tableViewModel = view.myResultsTableView.getModelForTesting()
    assertThat(tableView.columnCount).isEqualTo(3)
    assertThat(tableViewModel.columns[0].name).isEqualTo("Tests")
    assertThat(tableViewModel.columns[1].name).isEqualTo("Duration")
    assertThat(tableViewModel.columns[2].name).isEqualTo("deviceName1")
  }

  private fun ActionGroup.flattenedActions(): Sequence<AnAction> = sequence {
    getChildren(null).forEach {
      if (it is ActionGroup) {
        yieldAll(it.flattenedActions())
      } else {
        yield(it)
      }
    }
  }

  @Test
  fun progressBar() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1", 29)
    val device2 = device("deviceId2", "deviceName2", 28)
    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice1 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testId2", "method2", "class2", "package2")
    val testsuiteOnDevice2 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    val testcase1OnDevice2 = AndroidTestCase("testId1", "method1", "class1", "package1")
    val testcase2OnDevice2 = AndroidTestCase("testId2", "method2", "class2", "package2")

    assertThat(view.myProgressBar.isIndeterminate).isFalse()
    assertThat(view.myProgressBar.value).isEqualTo(0)
    assertThat(view.myProgressBar.maximum).isEqualTo(100)

    // Add scheduled devices.
    view.onTestSuiteScheduled(device1)
    view.onTestSuiteScheduled(device2)

    assertThat(view.myProgressBar.value).isEqualTo(0)
    assertThat(view.myProgressBar.maximum).isEqualTo(100)

    // Test execution on device 1.
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)

    // Progress = (completed tests / scheduled tests) * (started devices / scheduled devices).
    assertThat(view.myProgressBar.value).isEqualTo(0 * 1)
    assertThat(view.myProgressBar.maximum).isEqualTo(2 * 2)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase1OnDevice1)
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase1OnDevice1)

    assertThat(view.myProgressBar.value).isEqualTo(1 * 1)
    assertThat(view.myProgressBar.maximum).isEqualTo(2 * 2)

    view.onTestCaseStarted(device1, testsuiteOnDevice1, testcase2OnDevice1)
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED
    view.onTestCaseFinished(device1, testsuiteOnDevice1, testcase2OnDevice1)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    assertThat(view.myProgressBar.value).isEqualTo(2 * 1)
    assertThat(view.myProgressBar.maximum).isEqualTo(2 * 2)

    // Test execution on device 2.
    view.onTestSuiteStarted(device2, testsuiteOnDevice2)
    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase1OnDevice2)
    testcase1OnDevice2.result = AndroidTestCaseResult.SKIPPED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase1OnDevice2)

    assertThat(view.myProgressBar.value).isEqualTo(3 * 2)
    assertThat(view.myProgressBar.maximum).isEqualTo(4 * 2)

    view.onTestCaseStarted(device2, testsuiteOnDevice2, testcase2OnDevice2)
    testcase2OnDevice2.result = AndroidTestCaseResult.PASSED
    view.onTestCaseFinished(device2, testsuiteOnDevice2, testcase2OnDevice2)
    view.onTestSuiteFinished(device2, testsuiteOnDevice2)

    assertThat(view.myProgressBar.value).isEqualTo(4 * 2)
    assertThat(view.myProgressBar.maximum).isEqualTo(4 * 2)
  }

  @Test
  fun initialStatusText() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    assertThat(view.myStatusText.text).isEqualTo("<html><nobr>0 passed</nobr></html>")
    assertThat(view.myStatusBreakdownText.text).isEqualTo("0 tests")
  }

  @Test
  fun singleDeviceStatusText() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, myClock=mockClock)
    val device1 = device("deviceId1", "deviceName1")

    view.onTestSuiteScheduled(device1)

    whenever(mockClock.millis()).thenReturn(Duration.ofHours(2).plusSeconds(1).plusMillis(123).toMillis())

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.FAILED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    assertThat(view.myStatusText.text).isEqualTo("<html><nobr><b><font color='#d67b76'>2 failed</font></b></nobr></html>")
    assertThat(view.myStatusBreakdownText.text).isEqualTo("2 tests, 2 h 0 m 1 s")
  }

  @Test
  fun multipleDevicesStatusText() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, myClock=mockClock)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    view.onTestSuiteScheduled(device1)
    view.onTestSuiteScheduled(device2)

    whenever(mockClock.millis()).thenReturn(12345)

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.PASSED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.SKIPPED)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    val testsuiteOnDevice2 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice2)
    runTestCase(view, device2, testsuiteOnDevice2,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.PASSED)
    runTestCase(view, device2, testsuiteOnDevice2,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)
    view.onTestSuiteFinished(device2, testsuiteOnDevice2)

    assertThat(view.myStatusText.text)
      .isEqualTo("<html><nobr><b><font color='#d67b76'>1 failed</font></b>, 2 passed, 1 skipped</nobr></html>")
    assertThat(view.myStatusBreakdownText.text).isEqualTo("4 tests, 2 devices, 12 s 345 ms")
  }

  @Test
  fun durationInStatusTextCanBeOverritten() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, myClock=mockClock)
    view.testExecutionDurationOverride = Duration.ofSeconds(10)

    val device1 = device("deviceId1", "deviceName1")

    view.onTestSuiteScheduled(device1)

    whenever(mockClock.millis()).thenReturn(Duration.ofHours(2).plusSeconds(1).plusMillis(123).toMillis())

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.FAILED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    assertThat(view.myStatusText.text).isEqualTo("<html><nobr><b><font color='#d67b76'>2 failed</font></b></nobr></html>")
    assertThat(view.myStatusBreakdownText.text).isEqualTo("2 tests, 10 s")
  }

  @Test
  fun deviceSelectorIsHiddenWhenSingleDevice() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, myClock=mockClock)

    view.onTestSuiteScheduled(device("deviceId1", "deviceName1"))

    assertThat(view.myDetailsView.isDeviceSelectorListVisible).isFalse()
  }

  @Test
  fun deviceSelectorIsVisibleWhenMultiDevices() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, myClock=mockClock)

    view.onTestSuiteScheduled(device("deviceId1", "deviceName1"))
    view.onTestSuiteScheduled(device("deviceId2", "deviceName2"))

    assertThat(view.myDetailsView.isDeviceSelectorListVisible).isTrue()
  }

  @Test
  fun testHistoryIsSavedAfterTestExecution() {
    val initialTestHistoryCount = getTestHistory().size

    val runConfig = mock<RunConfiguration>().apply {
      whenever(name).thenReturn("mockRunConfig")
      whenever(type).thenReturn(AndroidTestRunConfigurationType.getInstance())
    }
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, "run", runConfig)
    val device1 = device("deviceId1", "deviceName1")

    view.onTestSuiteScheduled(device1)

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.FAILED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)

    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    assertThat(ProgressIndicatorUtils.withTimeout(30000) {
      while(getTestHistory().size == initialTestHistoryCount) {
        ProgressManager.checkCanceled()
        sleep(100)
      }
      true
    }).isTrue()
  }

  @Test
  fun testHistoryIsSavedAfterRerunTestExecution() {
    val historySavedLatch = CountDownLatch(2)
    val testHistoryConfigurationMock = Mockito.mock(TestHistoryConfiguration::class.java)
    Mockito.`when`(testHistoryConfigurationMock.registerHistoryItem(any(), eq("mockRunConfig"), any())).then {
      historySavedLatch.countDown()
    }
    projectRule.project.replaceService(TestHistoryConfiguration::class.java, testHistoryConfigurationMock, disposableRule.disposable)

    val runConfig = mock<RunConfiguration>().apply {
      whenever(name).thenReturn("mockRunConfig")
      whenever(type).thenReturn(AndroidTestRunConfigurationType.getInstance())
    }
    val initialCurrentTimeMillis = System.currentTimeMillis()
    whenever(mockClock.millis()).thenReturn(initialCurrentTimeMillis)
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null, "run", runConfig, myClock=mockClock)
    val device1 = device("deviceId1", "deviceName1")

    // First test run fails by error.
    view.onTestSuiteScheduled(device1)
    val failedTestsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 0)
    view.onTestSuiteStarted(device1, failedTestsuiteOnDevice1)
    view.onTestSuiteFinished(device1, failedTestsuiteOnDevice1)

    // Rerun is scheduled.
    view.onRerunScheduled(device1)

    whenever(mockClock.millis()).thenReturn(initialCurrentTimeMillis + Duration.ofMinutes(1).toMillis())

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.FAILED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)

    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    assertThat(historySavedLatch.await(30, TimeUnit.SECONDS)).isTrue()
  }

  private fun getTestHistory(): List<ImportTestsFromHistoryAction> {
    val mockEvent = mock<AnActionEvent>()
    whenever(mockEvent.project).thenReturn(projectRule.project)
    return ImportTestGroup().getChildren(mockEvent).mapNotNull {
      it as? ImportTestsFromHistoryAction
    }
  }

  private fun runTestCase(view: AndroidTestSuiteView,
                          device: AndroidDevice,
                          suite: AndroidTestSuite,
                          testcase: AndroidTestCase,
                          result: AndroidTestCaseResult) {
    view.onTestCaseStarted(device, suite, testcase)
    testcase.result = result
    view.onTestCaseFinished(device, suite, testcase)
  }

  @Test
  fun actionButtonsAreFocusable() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    UIUtil.uiTraverser(view.component)
      .filter(ActionToolbarImpl::class.java)
      .forEach(ActionToolbarImpl::updateActionsImmediately)

    val actionButtons = UIUtil.uiTraverser(view.component)
      .filter(ActionButton::class.java)
      .filter(ActionButton::isFocusable)
      .map { it.action.templateText }
      .filterNotNull()
      .toList()

    assertThat(actionButtons).containsExactly(
      "Show passed tests",
      "Show skipped tests",
      "Expand All",
      "Collapse All",
      "Previous Occurrence",
      "Next Occurrence",
      "Test History",
      "Import Tests from File...",
      "Export Test Results..."
    ).inOrder()
  }

  // Regression tests for b/172088812 where the apply-code-changes action causes
  // onTestSuiteScheduled(device) callback method to be called more than once.
  @Test
  fun onTestSuiteScheduledMethodMayBeCalledMoreThanOnce() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    val device1 = device("deviceId1", "deviceName1")
    val sameDeviceId = device("deviceId1", "different name")

    view.onTestSuiteScheduled(device1)

    assertThat(view.myResultsTableView.getTableViewForTesting().columnCount).isEqualTo(3)

    view.onTestSuiteScheduled(sameDeviceId)

    assertThat(view.myResultsTableView.getTableViewForTesting().columnCount).isEqualTo(3)
  }

  @Test
  fun firstFailedTestCaseShouldBeSelectedAutomatically() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)
    val device1 = device("deviceId1", "deviceName1")

    view.onTestSuiteScheduled(device1)

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.PASSED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.FAILED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId3", "method3", "class3", "package3"), AndroidTestCaseResult.PASSED)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    // Verifies the details view is visible now.
    assertThat(view.myDetailsView.rootPanel.isVisible).isTrue()
    assertThat(view.myDetailsView.titleTextView.text).isEqualTo("package2.class2.method2")
    assertThat(view.myDetailsView.selectedDevice).isEqualTo(device1)
  }

  @Test
  fun rootTableItemIsSelectedWhenAllTestsPassed() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)
    val device1 = device("deviceId1", "deviceName1")

    view.onTestSuiteScheduled(device1)

    val testsuiteOnDevice1 = AndroidTestSuite("testsuiteId", "testsuiteName", testCaseCount = 2)
    view.onTestSuiteStarted(device1, testsuiteOnDevice1)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId1", "method1", "class1", "package1"), AndroidTestCaseResult.PASSED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId2", "method2", "class2", "package2"), AndroidTestCaseResult.PASSED)
    runTestCase(view, device1, testsuiteOnDevice1,
                AndroidTestCase("testId3", "method3", "class3", "package3"), AndroidTestCaseResult.PASSED)
    view.onTestSuiteFinished(device1, testsuiteOnDevice1)

    // Verifies the details view is visible now.
    assertThat(view.myDetailsView.rootPanel.isVisible).isTrue()
    assertThat(view.myDetailsView.titleTextView.text).isEqualTo("Test Results")
    assertThat(view.myDetailsView.selectedDevice).isEqualTo(device1)
  }

  private fun device(id: String, name: String, apiVersion: Int = 28): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(apiVersion))
  }

  private fun TreeTableView.getItem(index: Int): AndroidTestResults {
    return getValueAt(index, 0) as AndroidTestResults
  }
}