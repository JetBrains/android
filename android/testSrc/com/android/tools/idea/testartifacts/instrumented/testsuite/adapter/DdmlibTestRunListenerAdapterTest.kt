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
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter.Companion.BENCHMARK_PATH_TEST_METRICS_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter.Companion.BENCHMARK_TEST_METRICS_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter.Companion.BENCHMARK_V2_TEST_METRICS_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.argThat
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.io.File

/**
 * Unit tests for [DdmlibTestRunListenerAdapter].
 */
class DdmlibTestRunListenerAdapterTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock
  lateinit var mockDevice: IDevice
  @Mock
  lateinit var mockListener: AndroidTestResultListener

  @Before
  fun setup() {
    whenever(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
    whenever(mockDevice.avdName).thenReturn("mockDeviceAvdName")
    whenever(mockDevice.version).thenReturn(AndroidVersion(29))
    whenever(mockDevice.isEmulator).thenReturn(true)
  }

  private fun eq(arg: AndroidTestCase): AndroidTestCase {
    argThat<AndroidTestCase> {
      arg.copy(startTimestampMillis = 0, endTimestampMillis = 0) == it.copy(startTimestampMillis = 0, endTimestampMillis = 0)
    }
    return arg
  }

  private fun device(id: String = "mockDeviceSerialNumber",
                     name: String = "mockDeviceAvdName"): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29),
                         mutableMapOf("SerialNumber" to "mockDeviceSerialNumber"))
  }

  @Test
  fun runSuccess() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(device()))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                              "exampleTest1",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                               "exampleTest1",
                                                               "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2", 2))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2 - 0",
                                                              "exampleTest2",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2", 2), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2 - 0",
                                                               "exampleTest2",
                                                               "exampleTestClass",
                                                               "",
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

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                              "exampleTest1",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testFailed(TestIdentifier("exampleTestClass", "exampleTest1", 1), "")
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                               "exampleTest1",
                                                               "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.FAILED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest2", 2))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest2 - 0",
                                                              "exampleTest2",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest2", 2), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest2 - 0",
                                                               "exampleTest2",
                                                               "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(device()),
                                             eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.FAILED)))
  }

  @Test
  fun runAssumptionFailure() {
    val suiteId = "exampleTestSuite"
    val testId = TestIdentifier("exampleTestClass", "exampleTest1", 1)
    DdmlibTestRunListenerAdapter(mockDevice, mockListener).apply {
      testRunStarted(suiteId, /*testCount=*/1)
      testStarted(testId)
      testAssumptionFailure(testId, "test assumption failed")
      testEnded(testId, mutableMapOf())
      testRunEnded(/*elapsedTime=*/1000, mutableMapOf())
    }

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite(suiteId, suiteId, 1, AndroidTestSuiteResult.PASSED)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                               "exampleTest1", "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.SKIPPED,
                                                               errorStackTrace = "test assumption failed")))
    verify(mockListener).onTestSuiteFinished(eq(device()),
                                             eq(AndroidTestSuite(suiteId, suiteId, 1, AndroidTestSuiteResult.PASSED)))
  }

  @Test
  fun testResultIsUpdatedInPlace() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)

    val testSuite = ArgumentCaptor.forClass(AndroidTestSuite::class.java)
    verify(mockListener).onTestSuiteStarted(
      any(AndroidDevice::class.java),
      testSuite.capture() ?: AndroidTestSuite("", "", 0))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", "", "", ""))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1),
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
  fun testDualBenchmarkKeysUsesNewKey() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)

    val testSuite = ArgumentCaptor.forClass(AndroidTestSuite::class.java)
    verify(mockListener).onTestSuiteStarted(
      any(AndroidDevice::class.java),
      testSuite.capture() ?: AndroidTestSuite("", "", 0))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", "", "", ""))  // Workaround for https://github.com/mockito/mockito/issues/1255

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1),
                      mutableMapOf(
                        DDMLIB_LOGCAT to "test logcat message",
                        BENCHMARK_TEST_METRICS_KEY to "test benchmark output legacy message",
                        BENCHMARK_V2_TEST_METRICS_KEY to "new [linked](style/message) is used"))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    assertThat(testCase.value.result).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(testCase.value.logcat).isEqualTo("test logcat message")
    assertThat(testCase.value.benchmark).isEqualTo("new [linked](style/message) is used")
    assertThat(testSuite.value.result).isEqualTo(AndroidTestSuiteResult.PASSED)
  }

  @Test
  fun benchmarkPrefixIsStripped() {
    val benchmarkOutputFromAndroidX = """
      WARNING: Not using IsolationActivity via AndroidBenchmarkRunner
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
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))
    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", "", "", ""))
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1),
                      mutableMapOf(BENCHMARK_TEST_METRICS_KEY to benchmarkOutputFromAndroidX))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    assertThat(testCase.value.benchmark).isEqualTo(expectedBenchmarkText)
  }

  @Test
  fun benchmarkFileLinkIsCopied() {
    val validTracePath = "path/to/valid/my.trace"
    val benchmarkOutputFromAndroidX = """
      Benchmark test ran in [32 ns](file://$validTracePath)
      However there was a bug in [this trace path](path/to/invalid.trace)
    """.trimIndent()
    val deviceRoot = "/device/root/path"
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)
    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))
    val testCase = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(
      any(AndroidDevice::class.java),
      any(AndroidTestSuite::class.java),
      testCase.capture() ?: AndroidTestCase("", "", "", ""))
    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1),
                      mutableMapOf(BENCHMARK_TEST_METRICS_KEY to benchmarkOutputFromAndroidX,
                                   BENCHMARK_PATH_TEST_METRICS_KEY to deviceRoot))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())
    // Expect we attempt to copy the valid trace file, and we do not attempt to copy the invalid trace file.
    verify(mockDevice, times(1)).pullFile("${deviceRoot}/$validTracePath",
                                          "${FileUtil.getTempDirectory()}${File.separator}${validTracePath.replace("/", File.separator)}")
  }

  @Test
  fun testResultsShouldChangeToCancelledWhenTestProcessIsKilled() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))
    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    val testCaseCaptor = ArgumentCaptor.forClass(AndroidTestCase::class.java)
    verify(mockListener).onTestCaseStarted(any(), any(), testCaseCaptor.capture() ?: AndroidTestCase("", "", "", ""))
    assertThat(testCaseCaptor.value.result).isEqualTo(AndroidTestCaseResult.CANCELLED)
    assertThat(testCaseCaptor.value.endTimestampMillis).isNotNull()

    val testSuiteCaptor = ArgumentCaptor.forClass(AndroidTestSuite::class.java)
    verify(mockListener).onTestSuiteFinished(any(), testSuiteCaptor.capture() ?: AndroidTestSuite("", "", 0))
    assertThat(testSuiteCaptor.value.result).isEqualTo(AndroidTestSuiteResult.CANCELLED)
  }

  @Test
  fun methodNameAndClassNameAndPackageNameIsExtractedCorrectly() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("com.example.test.exampleTestClass", "exampleTest1", 1))

    verify(mockListener).onTestCaseStarted(any(), any(),
                                           eq(AndroidTestCase("com.example.test.exampleTestClass#exampleTest1 - 0",
                                                              "exampleTest1",
                                                              "exampleTestClass",
                                                              "com.example.test",
                                                              AndroidTestCaseResult.IN_PROGRESS)))
  }

  @Test
  fun methodNameAndClassNameAndPackageNameIsExtractedCorrectlyForNestedClass() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("com.example.test.exampleTestClass\$NestedClassName", "exampleTest1", 1))

    verify(mockListener).onTestCaseStarted(any(), any(),
                                           eq(AndroidTestCase("com.example.test.exampleTestClass\$NestedClassName#exampleTest1 - 0",
                                                              "exampleTest1",
                                                              "exampleTestClass\$NestedClassName",
                                                              "com.example.test",
                                                              AndroidTestCaseResult.IN_PROGRESS)))
  }

  @Test
  fun timestamp() {
    lateinit var result: AndroidTestCase
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, object : AndroidTestResultListener {
      override fun onTestSuiteScheduled(device: AndroidDevice) {}
      override fun onTestSuiteStarted(device: AndroidDevice, testSuite: AndroidTestSuite) {}
      override fun onTestCaseStarted(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {
        result = testCase
      }

      override fun onTestCaseFinished(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {}
      override fun onTestSuiteFinished(device: AndroidDevice, testSuite: AndroidTestSuite) {}
      override fun onRerunScheduled(device: AndroidDevice) {}
    })

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/1)
    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    assertThat(result.startTimestampMillis).isNotNull()
    assertThat(result.endTimestampMillis).isNull()

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1), mutableMapOf())

    assertThat(result.startTimestampMillis).isNotNull()
    assertThat(result.endTimestampMillis).isNotNull()
  }

  @Test
  fun rerunOfSameTestShouldGetDifferentId() {
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(device()))

    adapter.testRunStarted("exampleTestSuite", /*testCount=*/2)

    verify(mockListener).onTestSuiteStarted(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 1))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                              "exampleTest1",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 1), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1 - 0",
                                                               "exampleTest1",
                                                               "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testStarted(TestIdentifier("exampleTestClass", "exampleTest1", 2))

    verify(mockListener).onTestCaseStarted(eq(device()),
                                           eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                           eq(AndroidTestCase("exampleTestClass#exampleTest1 - 1",
                                                              "exampleTest1",
                                                              "exampleTestClass",
                                                              "",
                                                              AndroidTestCaseResult.IN_PROGRESS)))

    adapter.testEnded(TestIdentifier("exampleTestClass", "exampleTest1", 2), mutableMapOf())

    verify(mockListener).onTestCaseFinished(eq(device()),
                                            eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2)),
                                            eq(AndroidTestCase("exampleTestClass#exampleTest1 - 1",
                                                               "exampleTest1",
                                                               "exampleTestClass",
                                                               "",
                                                               AndroidTestCaseResult.PASSED)))

    adapter.testRunEnded(/*elapsedTime=*/1000, mutableMapOf())

    verify(mockListener).onTestSuiteFinished(eq(device()),
                                             eq(AndroidTestSuite("exampleTestSuite", "exampleTestSuite", 2, AndroidTestSuiteResult.PASSED)))
  }

  @Test
  fun runCancelledByProcessHandler() {
    val processHandler = mock<ProcessHandler>()
    val adapter = DdmlibTestRunListenerAdapter(mockDevice, mockListener).apply {
      processTerminated(ProcessEvent(processHandler))
    }

    inOrder(mockListener, processHandler).apply {
      verify(mockListener).onTestSuiteScheduled(eq(device()))
      verify(processHandler).removeProcessListener(eq(adapter))
      verify(mockListener).onTestSuiteFinished(
        eq(device()),
        eq(AndroidTestSuite("", "", 0, AndroidTestSuiteResult.CANCELLED)))
      verifyNoMoreInteractions()
    }
  }
}