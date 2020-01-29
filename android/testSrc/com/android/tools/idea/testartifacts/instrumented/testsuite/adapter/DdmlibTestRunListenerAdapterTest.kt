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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [DdmlibTestRunListenerAdapter].
 */
class DdmlibTestRunListenerAdapterTest {

  @Mock lateinit var mockDevice: IDevice
  @Mock lateinit var mockListener: AndroidTestResultListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
    `when`(mockDevice.avdName).thenReturn("mockDeviceAvdName")
  }

  @Test
  fun runSuccess() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))

    verify(mockListener).onTestCaseStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1")))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                              AndroidTestCaseResult.PASSED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2"))

    verify(mockListener).onTestCaseStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2")))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                             eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.PASSED)))
  }

  @Test
  fun runPartiallyFail() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))

    verify(mockListener).onTestCaseStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1")))

    adapter.testFailed(TestIdentifier("exampleTestClass", "exampleTest1"), "")
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                               AndroidTestCaseResult.FAILED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2"))

    verify(mockListener).onTestCaseStarted(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2")))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(AndroidDevice("mockDeviceSerialNumber", "mockDeviceAvdName")),
                                             eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)))
  }

  @Test
  fun testResultIsUpdatedInPlace() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)

    val testSuite = ArgumentCaptor.forClass(AndroidTestSuite::class.java)
    verify(mockListener).onTestSuiteStarted(
      any(AndroidDevice::class.java),
      testSuite.capture() ?: AndroidTestSuite("", "", 0))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))

    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", ""))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"), mutableMapOf())
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    assertThat(testCase.value.result).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(testSuite.value.result).isEqualTo(AndroidTestSuiteResult.PASSED)
  }
}