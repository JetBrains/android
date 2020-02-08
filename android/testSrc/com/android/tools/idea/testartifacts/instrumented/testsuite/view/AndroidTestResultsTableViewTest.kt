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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidTestResultsTableView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestResultsTableViewTest {

  @get:Rule val edtRule = EdtRule()

  @Mock lateinit var mockListener: AndroidTestResultsTableListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun initialTable() {
    val table = AndroidTestResultsTableView(mockListener)

    // Assert columns.
    assertThat(table.getModelForTesting().columnInfos).hasLength(2)
    assertThat(table.getModelForTesting().columnInfos[0].name).isEqualTo("Tests")
    assertThat(table.getModelForTesting().columnInfos[1].name).isEqualTo("Status")

    // Assert rows.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(0)
  }

  @Test
  fun addDevice() {
    val table = AndroidTestResultsTableView(mockListener)

    table.addDevice(AndroidDevice("deviceId1", "deviceName1"))
    table.addDevice(AndroidDevice("deviceId2", "deviceName2"))

    assertThat(table.getModelForTesting().columnInfos).hasLength(4)
    assertThat(table.getModelForTesting().columnInfos[2].name).isEqualTo("deviceName1")
    assertThat(table.getModelForTesting().columnInfos[3].name).isEqualTo("deviceName2")

    // Row count should be still zero until any test results come in.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(0)
  }

  @Test
  fun addTestResults() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = AndroidDevice("deviceId1", "deviceName1")
    val device2 = AndroidDevice("deviceId2", "deviceName2")
    val testcase1OnDevice1 = AndroidTestCase("testid1", "testname1")
    val testcase2OnDevice1 = AndroidTestCase("testid2", "testname2")
    val testcase1OnDevice2 = AndroidTestCase("testid1", "testname1")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, testcase1OnDevice1)
    table.addTestCase(device1, testcase2OnDevice1)
    table.addTestCase(device2, testcase1OnDevice2)

    // No test cases are finished yet.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(2)
    assertThat(table.getModelForTesting().getItem(0).testCaseName).isEqualTo("testname1")
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isNull()
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isNull()
    assertThat(table.getModelForTesting().getItem(1).testCaseName).isEqualTo("testname2")
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isNull()
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()

    // Let test case 1 and 2 finish on the device 1.
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED

    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isNull()
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()

    // Let test case 1 finish on the device 2.
    testcase1OnDevice2.result = AndroidTestCaseResult.PASSED

    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()
  }

  @Test
  fun clickTestResultsRow() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = AndroidDevice("deviceId1", "deviceName1")
    val device2 = AndroidDevice("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, AndroidTestCase("testid1", "testname1", AndroidTestCaseResult.PASSED))
    table.addTestCase(device1, AndroidTestCase("testid2", "testname2", AndroidTestCaseResult.FAILED))
    table.addTestCase(device2, AndroidTestCase("testid1", "testname1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device2, AndroidTestCase("testid2", "testname2", AndroidTestCaseResult.SKIPPED))

    // Select the test case 1.
    table.getTableViewForTesting().addSelection(table.getTableViewForTesting().getRow(0))

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.testCaseName == "testname1" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.PASSED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    })

    // Select the test case 2.
    table.getTableViewForTesting().addSelection(table.getTableViewForTesting().getRow(1))

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.testCaseName == "testname2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    })

    // Select the test case 2 again should not trigger the callback.
    table.getTableViewForTesting().addSelection(table.getTableViewForTesting().getRow(1))

    verifyNoMoreInteractions(mockListener)
  }

  // Workaround for Kotlin nullability check.
  // ArgumentMatchers.argThat returns null for interface types.
  private fun argThat(matcher: (AndroidTestResults) -> Boolean): AndroidTestResults {
    ArgumentMatchers.argThat(matcher)
    return object:AndroidTestResults {
      override val testCaseName = ""
      override fun getTestCaseResult(device: AndroidDevice) = null
    }
  }
}