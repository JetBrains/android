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
import com.android.ddmlib.testrunner.IInstrumentationResultParser.StatusKeys.DDMLIB_LOGCAT
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter.Companion.BENCHMARK_TEST_METRICS_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
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

  @get:Rule val projectRule = ProjectRule()

  @Mock lateinit var mockDevice: IDevice
  @Mock lateinit var mockListener: AndroidTestResultListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
    `when`(mockDevice.avdName).thenReturn("mockDeviceAvdName")
    `when`(mockDevice.version).thenReturn(AndroidVersion(29))
    `when`(mockDevice.isEmulator).thenReturn(true)
  }

  @Test
  fun runSuccess() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(device()))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                              AndroidTestCaseResult.PASSED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2"))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(device()),
                                             eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.PASSED)))
  }

  @Test
  fun runPartiallyFail() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(device()))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testFailed(TestIdentifier("exampleTestClass", "exampleTest1"), "")
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1", "exampleTestClass#exampleTest1",
                                                               AndroidTestCaseResult.FAILED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2"))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2"), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2", "exampleTestClass#exampleTest2",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(device()),
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

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"),
                      mutableMapOf(
                        DDMLIB_LOGCAT to "test logcat message",
                        BENCHMARK_TEST_METRICS_KEY to "test benchmark output message"))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    assertThat(testCase.value.result).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(testCase.value.logcat).isEqualTo("test logcat message")
    assertThat(testCase.value.benchmark).isEqualTo("test benchmark output message")
    assertThat(testSuite.value.result).isEqualTo(AndroidTestSuiteResult.PASSED)
  }

  @Test
  fun benchmarkPrefixIsStripped() {
    val benchmarkOutputFromAndroidX = """
      benchmark: WARNING: Not using IsolationActivity via AndroidBenchmarkRunner
      benchmark:     AndroidBenchmarkRunner should be used to isolate benchmarks from interference
      benchmark:     from other visible apps. To fix this, add the following to your module-level
      benchmark:     build.gradle:
      benchmark:         android.defaultConfig.testInstrumentationRunner
      benchmark:             = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
      benchmark:
      benchmark:    30,233,969 ns DEBUGGABLE_EMULATOR_UNLOCKED_ACTIVITY-MISSING_MyBenchmarkTest.benchmarkSomeWork
    """.trimIndent()
    val expectedBenchmarkText = """
      WARNING: Not using IsolationActivity via AndroidBenchmarkRunner
          AndroidBenchmarkRunner should be used to isolate benchmarks from interference
          from other visible apps. To fix this, add the following to your module-level
          build.gradle:
              android.defaultConfig.testInstrumentationRunner
                  = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

         30,233,969 ns DEBUGGABLE_EMULATOR_UNLOCKED_ACTIVITY-MISSING_MyBenchmarkTest.benchmarkSomeWork
    """.trimIndent()

    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))
    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", ""))
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1"),
                      mutableMapOf(BENCHMARK_TEST_METRICS_KEY to benchmarkOutputFromAndroidX))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    assertThat(testCase.value.benchmark).isEqualTo(expectedBenchmarkText)
  }

  @Test
  fun testResultsShouldChangeToCancelledWhenTestProcessIsKilled() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1"))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    val testCaseCaptor = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(any(), any(), testCaseCaptor.capture() ?: AndroidTestCase("", ""))
    assertThat(testCaseCaptor.value.result).isEqualTo(AndroidTestCaseResult.CANCELLED)

    val testSuiteCaptor = ArgumentCaptor.forClass(AndroidTestSuite::class.java)
    verify(mockListener).onTestSuiteFinished(any(), testSuiteCaptor.capture() ?: AndroidTestSuite("", "", 0))
    assertThat(testSuiteCaptor.value.result).isEqualTo(AndroidTestSuiteResult.CANCELLED)
  }

  private fun device(id: String = "mockDeviceSerialNumber",
                     name: String = "mockDeviceAvdName"): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }
}