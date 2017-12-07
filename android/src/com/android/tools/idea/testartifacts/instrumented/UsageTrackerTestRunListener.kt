/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.builder.model.TestOptions
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestRun

/**
 * [ITestRunListener] that builds an [AndroidStudioEvent] and logs it once the run is finished.
 */
class UsageTrackerTestRunListener @JvmOverloads constructor(
    private val artifact: IdeAndroidArtifact?,
    private val device: IDevice,
    private val usageTracker: UsageTracker = UsageTracker.getInstance()
) : ITestRunListener {

  private val testRun: TestRun.Builder = TestRun.newBuilder().apply {
    testInvocationType = TestRun.TestInvocationType.ANDROID_STUDIO_TEST
    testKind = TestRun.TestKind.INSTRUMENTATION_TEST
    testExecution = when (artifact?.testOptions?.execution) {
      TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestRun.TestExecution.ANDROID_TEST_ORCHESTRATOR
      TestOptions.Execution.HOST, null -> TestRun.TestExecution.HOST
      else -> TestRun.TestExecution.UNKNOWN_TEST_EXECUTION
    }
    // TODO(b/67255458): testLibraries
  }

  override fun testRunStarted(runName: String?, testCount: Int) {
    testRun.numberOfTestsExecuted = testCount
  }

  override fun testRunFailed(errorMessage: String?) {
    testRun.crashed = true
  }

  override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>?) {
    val studioEvent = AndroidStudioEvent.newBuilder().apply {
      category = AndroidStudioEvent.EventCategory.TESTS
      kind = AndroidStudioEvent.EventKind.TEST_RUN
      deviceInfo = AndroidStudioUsageTracker.deviceToDeviceInfo(device)
      testRun = this@UsageTrackerTestRunListener.testRun.build()
    }

    usageTracker.log(studioEvent)
  }

  override fun testRunStopped(elapsedTime: Long) {}
  override fun testStarted(test: TestIdentifier?) {}
  override fun testFailed(test: TestIdentifier?, trace: String?) {}
  override fun testAssumptionFailure(test: TestIdentifier?, trace: String?) {}
  override fun testIgnored(test: TestIdentifier?) {}
  override fun testEnded(test: TestIdentifier?, testMetrics: MutableMap<String, String>?) {}

}
