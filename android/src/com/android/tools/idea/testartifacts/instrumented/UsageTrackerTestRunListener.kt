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
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestLibraries
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

    findTestLibrariesVersions(artifact)?.let { testLibraries = it }

    testExecution = when (artifact?.testOptions?.execution) {
      TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestRun.TestExecution.ANDROID_TEST_ORCHESTRATOR
      TestOptions.Execution.HOST, null -> TestRun.TestExecution.HOST
      else -> TestRun.TestExecution.UNKNOWN_TEST_EXECUTION
    }
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

  private fun findTestLibrariesVersions(artifact: IdeAndroidArtifact?): TestLibraries? {
    val deps = artifact?.level2Dependencies ?: return null
    val builder = TestLibraries.newBuilder()

    for (lib in (Iterables.concat(deps.androidLibraries, deps.javaLibraries))) {
      val coordinate = GradleCoordinate.parseCoordinateString(lib.artifactAddress) ?: continue
      val version = coordinate.version?.toString() ?: continue

      when (coordinate.groupId) {
        "com.android.support.test", "androidx.test" -> {
          when (coordinate.artifactId) {
            "orchestrator" -> builder.testOrchestratorVersion = version
            "rules" -> builder.testRulesVersion = version
            "runner" -> builder.testSupportLibraryVersion = version
          }
        }
        "com.android.support.test.espresso", "androidx.test.espresso" -> {
          when (coordinate.artifactId) {
            "espresso-accessibility" -> builder.espressoAccessibilityVersion = version
            "espresso-contrib" -> builder.espressoContribVersion = version
            "espresso-core" -> builder.espressoVersion = version
            "espresso-idling-resource" -> builder.espressoIdlingResourceVersion = version
            "espresso-intents" -> builder.espressoIntentsVersion = version
            "espresso-web" -> builder.espressoWebVersion = version
          }
        }
        "org.robolectric" -> {
          when (coordinate.artifactId) { "robolectric" -> builder.robolectricVersion = version }
        }
        "org.mockito" -> {
          when (coordinate.artifactId) { "mockito-core" -> builder.mockitoVersion = version }
        }
      }
    }

    return builder.build()
  }
}
