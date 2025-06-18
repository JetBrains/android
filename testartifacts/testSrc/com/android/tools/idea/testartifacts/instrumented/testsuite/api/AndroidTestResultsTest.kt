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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration

/**
 * Unit tests for [AndroidTestResults].
 */
@RunWith(JUnit4::class)
class AndroidTestResultsTest {

  @Mock lateinit var mockDevice: AndroidDevice

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun roundedDuration() {
    assertThat(createAndroidTestResults().getRoundedDuration(mockDevice)).isNull()
    assertThat(createAndroidTestResults(Duration.ofMillis(500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(500))
    assertThat(createAndroidTestResults(Duration.ofMillis(999)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(999))
    assertThat(createAndroidTestResults(Duration.ofMillis(1000)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(1001)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(1500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(2500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(2000))
  }

  @Test
  fun testNames() {
    val testResults = TestAndroidTestResults("myMethod", "myClass", "myPackage")
    assertThat(testResults.getFullTestClassName()).isEqualTo("myPackage.myClass")
    assertThat(testResults.getFullTestCaseName()).isEqualTo("myPackage.myClass.myMethod")
  }

  @Test
  fun testNames_emptyPackageName() {
    val testResults = TestAndroidTestResults("myMethod", "myClass", "")
    assertThat(testResults.getFullTestClassName()).isEqualTo("myClass")
    assertThat(testResults.getFullTestCaseName()).isEqualTo("myClass.myMethod")
  }

  @Test
  fun testNames_emptyClassName() {
    val testResults = TestAndroidTestResults("myMethod", "", "myPackage")
    assertThat(testResults.getFullTestClassName()).isEqualTo("")
    assertThat(testResults.getFullTestCaseName()).isEqualTo("myMethod")
  }

  @Test
  fun testNames_emptyPackageNameAndClassName() {
    val testResults = TestAndroidTestResults("myMethod", "", "")
    assertThat(testResults.getFullTestClassName()).isEqualTo("")
    assertThat(testResults.getFullTestCaseName()).isEqualTo("myMethod")
  }

  private fun createAndroidTestResults(duration: Duration? = null): AndroidTestResults {
    val results = mock<AndroidTestResults>()
    whenever(results.getDuration(any())).thenReturn(duration)
    return results
  }
}

class TestAndroidTestResults(override val methodName: String,
                             override val className: String,
                             override val packageName: String) : AndroidTestResults {
  override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = null

  override fun getTestResultSummary(): AndroidTestCaseResult {
    return AndroidTestCaseResult.PASSED
  }

  override fun getTestResultSummary(devices: List<AndroidDevice>): AndroidTestCaseResult {
    return AndroidTestCaseResult.PASSED
  }

  override fun getTestResultSummaryText(devices: List<AndroidDevice>) = "PASSED"

  override fun getResultStats(): AndroidTestResultStats = AndroidTestResultStats(passed = 1)

  override fun getResultStats(device: AndroidDevice) = AndroidTestResultStats(passed = 1)

  override fun getResultStats(devices: List<AndroidDevice>) = AndroidTestResultStats(passed = 1)

  override fun getLogcat(device: AndroidDevice) = ""

  override fun getStartTime(device: AndroidDevice): Long? = null

  override fun getDuration(device: AndroidDevice): Duration? = null

  override fun getTotalDuration(): Duration = Duration.ZERO

  override fun getErrorStackTrace(device: AndroidDevice) = ""

  override fun getBenchmark(device: AndroidDevice) = BenchmarkOutput("")

  override fun getRetentionInfo(device: AndroidDevice) = null

  override fun getRetentionSnapshot(device: AndroidDevice) = null

  override fun getAdditionalTestArtifacts(device: AndroidDevice) = emptyMap<String, String>()

}