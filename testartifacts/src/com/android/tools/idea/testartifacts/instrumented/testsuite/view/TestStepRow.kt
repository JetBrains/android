/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getSummaryResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestStep
import java.time.Duration
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.math.max

class TestStepRow(val testStep: AndroidTestStep) : AndroidTestResults, DefaultMutableTreeNode() {

  private val myTestSteps = mutableMapOf<String, AndroidTestStep>()

  override val methodName: String
    get() = testStep.name

  override val className = ""

  override val packageName = ""

  override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? {
    return myTestSteps[device.id]?.result
  }

  override fun getLogcat(device: AndroidDevice): String {
    return myTestSteps[device.id]?.logcat ?: ""
  }

  override fun getStartTime(device: AndroidDevice): Long? {
    return myTestSteps[device.id]?.startTimestampMillis
  }

  override fun getDuration(device: AndroidDevice): Duration? {
    val start = myTestSteps[device.id]?.startTimestampMillis ?: return null
    val end = myTestSteps[device.id]?.endTimestampMillis ?: System.currentTimeMillis()
    return Duration.ofMillis(max(end - start, 0))
  }

  override fun getTotalDuration(): Duration {
    return Duration.ofMillis(myTestSteps.values.asSequence().map {
      val start = it.startTimestampMillis ?: return@map 0L
      val end = it.endTimestampMillis ?: System.currentTimeMillis()
      max(end - start, 0L)
    }.sum())
  }

  /**
   * Returns an error stack for a given [device].
   */
  override fun getErrorStackTrace(device: AndroidDevice): String = myTestSteps[device.id]?.errorStackTrace ?: ""

  /**
   * Returns the additional test artifacts.
   */
  override fun getAdditionalTestArtifacts(device: AndroidDevice): Map<String, String> = myTestSteps[device.id]?.additionalTestArtifacts
                                                                                        ?: mapOf()

  /**
   * Returns an aggregated test result.
   */
  override fun getTestResultSummary(): AndroidTestCaseResult = getResultStats().getSummaryResult()

  /**
   * Returns an aggregated test result for the given devices.
   */
  override fun getTestResultSummary(devices: List<AndroidDevice>): AndroidTestCaseResult = getResultStats(devices).getSummaryResult()

  /**
   * Returns a one liner test result summary string for the given devices.
   */
  override fun getTestResultSummaryText(devices: List<AndroidDevice>): String {
    val stats = getResultStats(devices)
    return when {
      stats.failed == 1 -> "Fail"
      stats.failed > 0 -> "Fail (${stats.failed})"
      stats.cancelled > 0 -> "Cancelled"
      stats.running > 0 -> "Running"
      stats.passed > 0 -> "Pass"
      stats.skipped > 0 -> "Skip"
      else -> ""
    }
  }

  override fun getResultStats(): AndroidTestResultStats {
    return myTestSteps.values.fold(AndroidTestResultStats()) { acc, androidTestCase ->
      acc.addTestCaseResult(androidTestCase.result)
    }
  }

  override fun getResultStats(device: AndroidDevice): AndroidTestResultStats {
    val stats = AndroidTestResultStats()
    return stats.addTestCaseResult(getTestCaseResult(device))
  }

  override fun getResultStats(devices: List<AndroidDevice>): AndroidTestResultStats {
    return devices.fold(AndroidTestResultStats()) { acc, device ->
      acc.addTestCaseResult(getTestCaseResult(device))
    }
  }

  fun addTestStep(testStep: AndroidTestStep, device: AndroidDevice) {
    myTestSteps[device.id] = testStep
  }
}

