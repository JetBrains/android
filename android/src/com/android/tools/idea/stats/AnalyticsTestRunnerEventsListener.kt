/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * [ProjectComponent] that listens to all test runs, recognizes "Android unit tests" and records them as [AndroidStudioEvent] events.
 *
 * @see UsageTrackerTestRunListener for how we track instrumentation test runs
 */
class AnalyticsTestRunnerEventsListener(val project: Project) : SMTRunnerEventsAdapter(), ExecutionListener, ProjectComponent {
  companion object {
    /** [Key] used to store the [TestRun.Builder] in a [ProcessHandler]. */
    private val TEST_RUN_KEY = Key.create<TestRun.Builder>(AnalyticsTestRunnerEventsListener::class.qualifiedName!!)
  }

  override fun projectOpened() {
    val connection = project.messageBus.connect()
    connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, this)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, this)
  }

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val runConfiguration = env.runProfile as? AndroidJUnitConfiguration ?: return

    val testRunProtoBuilder = TestRun.newBuilder().apply {
      testInvocationType = TestRun.TestInvocationType.ANDROID_STUDIO_TEST
      testKind = TestRun.TestKind.UNIT_TEST

      runReadAction { runConfiguration.configurationModule.module }
        ?.androidFacet
        ?.let(AndroidModuleModel::get)
        ?.selectedVariant
        ?.unitTestArtifact
        ?.let(::findTestLibrariesVersions)
        ?.let { testLibraries = it }
    }

    handler.putUserData(TEST_RUN_KEY, testRunProtoBuilder)
  }

  override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
    val testRunBuilder = handler.getUserData(TEST_RUN_KEY) ?: return

    // See com.intellij.rt.execution.junit.IdeaTestRunner. Negative values end up as unsigned byte values by the time they reach here, but
    // we'll check for them just to be sure. See b/111187233 for an example of an "internal failure".
    testRunBuilder.crashed = when (exitCode) {
      0, -1, 255 -> false
      else -> true
    }
  }

  override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
    val testRunBuilder = testsRoot.handler.getUserData(TEST_RUN_KEY) ?: return
    testRunBuilder.numberOfTestsExecuted = testsRoot.allTests.count { it.isLeaf }

    val studioEvent = AndroidStudioEvent.newBuilder().apply {
      category = AndroidStudioEvent.EventCategory.TESTS
      kind = AndroidStudioEvent.EventKind.TEST_RUN
      productDetails = AndroidStudioUsageTracker.productDetails
      testRun = testRunBuilder.build()
    }

    UsageTracker.log(studioEvent)
  }
}
