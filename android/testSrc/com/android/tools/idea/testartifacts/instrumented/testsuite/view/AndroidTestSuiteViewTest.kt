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
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessHandler
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.dualView.TreeTableView
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

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
    view.myFailedToggleButton.isSelected = true
    view.mySkippedToggleButton.isSelected = true
    view.myInProgressToggleButton.isSelected = true
  }

  @Test
  fun attachToProcess() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    view.attachToProcess(processHandler)

    verify(processHandler).putCopyableUserData(eq(ANDROID_TEST_RESULT_LISTENER_KEY), eq(view))
  }

  @Test
  fun detailsViewIsVisibleAndRawTestOutputIsDisplayedInitially() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    assertThat(view.detailsViewForTesting.rootPanel.isVisible).isTrue()
    assertThat(view.tableForTesting.getTableViewForTesting().selectedColumn).isEqualTo(0)
    assertThat(view.tableForTesting.getTableViewForTesting().selectedRow).isEqualTo(0)
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
    view.onAndroidTestResultsRowSelected(view.tableForTesting.getTableViewForTesting().getItem(4),
                                         /*selectedDevice=*/null)

    // Verifies the details view is visible now.
    assertThat(view.detailsViewForTesting.rootPanel.isVisible).isTrue()
    assertThat(view.detailsViewForTesting.titleTextViewForTesting.text).isEqualTo("package2.class2.method2")
    assertThat(view.detailsViewForTesting.selectedDeviceForTesting).isEqualTo(device1)

    // Click on the test case 1 results row in device2 column.
    view.onAndroidTestResultsRowSelected(view.tableForTesting.getTableViewForTesting().getItem(2),
                                         /*selectedDevice=*/device2)

    // Verifies the details view is visible now.
    assertThat(view.detailsViewForTesting.rootPanel.isVisible).isTrue()
    assertThat(view.detailsViewForTesting.titleTextViewForTesting.text).isEqualTo("package1.class1.method1")
    assertThat(view.detailsViewForTesting.selectedDeviceForTesting).isEqualTo(device2)

    // Finally, close the details view.
    view.onAndroidTestSuiteDetailsViewCloseButtonClicked()

    assertThat(view.detailsViewForTesting.rootPanel.isVisible).isFalse()
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

    val tableView = view.tableForTesting.getTableViewForTesting()

    // Initially, all tests are displayed.
    assertThat(tableView.rowCount).isEqualTo(7)
    assertThat(tableView.convertRowIndexToView(0)).isEqualTo(0)  // Root aggregation (failed)
    assertThat(tableView.convertRowIndexToView(1)).isEqualTo(1)  // Class A aggregation (failed)
    assertThat(tableView.convertRowIndexToView(2)).isEqualTo(2)  // method 1 (failed)
    assertThat(tableView.convertRowIndexToView(3)).isEqualTo(3)  // method 2 (passed)
    assertThat(tableView.convertRowIndexToView(4)).isEqualTo(4)  // Class B aggregation (in progress)
    assertThat(tableView.convertRowIndexToView(5)).isEqualTo(5)  // method 3 (skipped)
    assertThat(tableView.convertRowIndexToView(6)).isEqualTo(6)  // method 4 (in progress)


    // Remove "Skipped".
    view.mySkippedToggleButton.isSelected = false

    assertThat(tableView.rowCount).isEqualTo(6)
    assertThat(tableView.convertRowIndexToView(0)).isEqualTo(0)  // Root aggregation (failed)
    assertThat(tableView.convertRowIndexToView(1)).isEqualTo(1)  // Class A aggregation (failed)
    assertThat(tableView.convertRowIndexToView(2)).isEqualTo(2)  // method 1 (failed)
    assertThat(tableView.convertRowIndexToView(3)).isEqualTo(3)  // method 2 (passed)
    assertThat(tableView.convertRowIndexToView(4)).isEqualTo(4)  // Class B aggregation (in progress)
    assertThat(tableView.convertRowIndexToView(5)).isEqualTo(-1)  // method 3 (skipped)
    assertThat(tableView.convertRowIndexToView(6)).isEqualTo(5)  // method 4 (in progress)

    // Remove "Passed", "Failed" and "In progress". Then select "Skipped".
    view.myPassedToggleButton.isSelected = false
    view.myFailedToggleButton.isSelected = false
    view.mySkippedToggleButton.isSelected = true
    view.myInProgressToggleButton.isSelected = false

    assertThat(tableView.rowCount).isEqualTo(2)
    assertThat(tableView.convertRowIndexToView(0)).isEqualTo(0)  // Root aggregation should always be visible.
    assertThat(tableView.convertRowIndexToView(1)).isEqualTo(-1)  // Class A aggregation (failed)
    assertThat(tableView.convertRowIndexToView(2)).isEqualTo(-1)  // method 1 (failed)
    assertThat(tableView.convertRowIndexToView(3)).isEqualTo(-1)  // method 2 (passed)
    assertThat(tableView.convertRowIndexToView(4)).isEqualTo(-1)  // Class B aggregation (in progress)
    assertThat(tableView.convertRowIndexToView(5)).isEqualTo(1)  // method 3 (skipped)
    assertThat(tableView.convertRowIndexToView(6)).isEqualTo(-1)  // method 4 (in progress)

    // Remove "Skipped" and select "In Progress".
    view.mySkippedToggleButton.isSelected = false
    view.myInProgressToggleButton.isSelected = true

    assertThat(tableView.rowCount).isEqualTo(3)
    assertThat(tableView.convertRowIndexToView(0)).isEqualTo(0)  // Root aggregation should always be visible.
    assertThat(tableView.convertRowIndexToView(1)).isEqualTo(-1)  // Class A aggregation (failed)
    assertThat(tableView.convertRowIndexToView(2)).isEqualTo(-1)  // method 1 (failed)
    assertThat(tableView.convertRowIndexToView(3)).isEqualTo(-1)  // method 2 (passed)
    assertThat(tableView.convertRowIndexToView(4)).isEqualTo(1)  // Class B aggregation (in progress)
    assertThat(tableView.convertRowIndexToView(5)).isEqualTo(-1)  // method 3 (skipped)
    assertThat(tableView.convertRowIndexToView(6)).isEqualTo(2)  // method 4 (in progress)

    // Remove "In Progress". (Nothing is selected).
    view.myInProgressToggleButton.isSelected = false

    assertThat(tableView.rowCount).isEqualTo(1)
    assertThat(tableView.convertRowIndexToView(0)).isEqualTo(0)  // Root aggregation should always be visible.
  }

  @Test
  fun filterByTestStatusButtonStateShouldPersist() {
    val view = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    // All buttons are selected initially.
    assertThat(view.myFailedToggleButton.isSelected).isTrue()
    assertThat(view.myPassedToggleButton.isSelected).isTrue()
    assertThat(view.mySkippedToggleButton.isSelected).isTrue()
    assertThat(view.myInProgressToggleButton.isSelected).isTrue()

    // Update state to false.
    view.myFailedToggleButton.isSelected = false
    view.myPassedToggleButton.isSelected = false
    view.mySkippedToggleButton.isSelected = false
    view.myInProgressToggleButton.isSelected = false

    val view2 = AndroidTestSuiteView(disposableRule.disposable, projectRule.project, null)

    // The new state should persist even after recreation of the view.
    assertThat(view2.myFailedToggleButton.isSelected).isFalse()
    assertThat(view2.myPassedToggleButton.isSelected).isFalse()
    assertThat(view2.mySkippedToggleButton.isSelected).isFalse()
    assertThat(view2.myInProgressToggleButton.isSelected).isFalse()
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

    // Select "device1" in the device filter ComboBox.
    view.myDeviceFilterComboBoxModel.selectedItem =
      AndroidTestSuiteView.DeviceFilterComboBoxItem(device2)

    val tableView = view.tableForTesting.getTableViewForTesting()
    val tableViewModel = view.tableForTesting.getModelForTesting()
    assertThat(tableViewModel.columnInfos[2].getWidth(tableView)).isEqualTo(1)
    assertThat(tableViewModel.columnInfos[3].getWidth(tableView)).isEqualTo(120)
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
    view.myApiLevelFilterComboBoxModel.selectedItem =
      AndroidTestSuiteView.ApiLevelFilterComboBoxItem(AndroidVersion(29))

    val tableView = view.tableForTesting.getTableViewForTesting()
    val tableViewModel = view.tableForTesting.getModelForTesting()
    assertThat(tableViewModel.columnInfos[2].getWidth(tableView)).isEqualTo(120)
    assertThat(tableViewModel.columnInfos[3].getWidth(tableView)).isEqualTo(1)
  }

  private fun device(id: String, name: String, apiVersion: Int = 28): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(apiVersion))
  }

  private fun TreeTableView.getItem(index: Int): AndroidTestResults {
    return getValueAt(index, 0) as AndroidTestResults
  }
}