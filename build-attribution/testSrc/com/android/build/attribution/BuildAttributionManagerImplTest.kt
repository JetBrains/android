/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution

import com.android.build.attribution.analyzers.createBinaryPluginIdentifierStub
import com.android.build.attribution.analyzers.createTaskFinishEventStub
import com.android.build.attribution.ui.controllers.createCheckJetifierTaskRequest
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionStats
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.tooling.events.task.TaskFinishEvent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class BuildAttributionManagerImplTest {
  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testSuccessfulBuild() {
    val buildAttributionManager = BuildAttributionManagerImpl(project = projectRule.project)
    val request = builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    val buildAttributionFile = createBuildAttributionFile(request)
    Truth.assertThat(buildAttributionFile.exists()).isTrue()

    buildAttributionManager.onBuildStart(request)
    buildAttributionManager.statusChanged(taskFinishEvent())
    buildAttributionManager.onBuildSuccess(request)

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    // Make sure file and containing folder deleted
    Truth.assertThat(buildAttributionFile.exists()).isFalse()
    Truth.assertThat(buildAttributionFile.parentFile.exists()).isFalse()

    // Check events
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    Truth.assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.REGULAR_BUILD to BuildAttributionStats.BuildAnalysisStatus.SUCCESS,
    ))
  }

  @Test
  fun testFailedBuild() {
    val buildAttributionManager = BuildAttributionManagerImpl(project = projectRule.project)
    val request = builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    val buildAttributionFile = createBuildAttributionFile(request)
    Truth.assertThat(buildAttributionFile.exists()).isTrue()

    buildAttributionManager.onBuildStart(request)
    buildAttributionManager.statusChanged(taskFinishEvent())
    buildAttributionManager.onBuildFailure(request)

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    // Make sure file and containing folder deleted
    Truth.assertThat(buildAttributionFile.exists()).isFalse()
    Truth.assertThat(buildAttributionFile.parentFile.exists()).isFalse()

    // Check events
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    Truth.assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.REGULAR_BUILD to BuildAttributionStats.BuildAnalysisStatus.BUILD_FAILURE,
    ))
  }

  @Test
  fun testFailureInEventProcessing() {
    // Create mock event that would throw a special exception on interaction.
    val brokenTaskFinishEvent = Mockito.mock(TaskFinishEvent::class.java, RuntimeExceptionAnswer())
    var exceptionWasLogged = false

    val buildAttributionManager = BuildAttributionManagerImpl(project = projectRule.project)
    val request = builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    val buildAttributionFile = createBuildAttributionFile(request)
    Truth.assertThat(buildAttributionFile.exists()).isTrue()

    // Expect exception to be caught and logged.
    LoggedErrorProcessor.executeWith<RuntimeExceptionAnswer.TestException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String?, t: Throwable?, details: Array<out String>): Boolean {
        if (t is RuntimeExceptionAnswer.TestException) {
          exceptionWasLogged = true
          return false
        }
        return super.processError(category, message, t, details)
      }
    }) {
      buildAttributionManager.onBuildStart(request)
      buildAttributionManager.statusChanged(brokenTaskFinishEvent)
      buildAttributionManager.onBuildSuccess(request)
    }

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    // Make sure file and containing folder deleted.
    Truth.assertThat(buildAttributionFile.exists()).isFalse()
    Truth.assertThat(buildAttributionFile.parentFile.exists()).isFalse()

    // Make sure exception was logged.
    Truth.assertThat(exceptionWasLogged).isTrue()

    // Check events.
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    Truth.assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.REGULAR_BUILD to BuildAttributionStats.BuildAnalysisStatus.ANALYSIS_FAILURE,
    ))
  }


  @Test
  fun testCheckJetifierBuild() {
    val buildAttributionManager = BuildAttributionManagerImpl(project = projectRule.project)
    val originalBuildRequest = builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    val request = createCheckJetifierTaskRequest(projectRule.project, originalBuildRequest.data)
    val buildAttributionFile = createBuildAttributionFile(request)
    Truth.assertThat(buildAttributionFile.exists()).isTrue()

    buildAttributionManager.onBuildStart(request)
    buildAttributionManager.statusChanged(taskFinishEvent())
    buildAttributionManager.onBuildSuccess(request)

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    // Make sure file and containing folder deleted
    Truth.assertThat(buildAttributionFile.exists()).isFalse()
    Truth.assertThat(buildAttributionFile.parentFile.exists()).isFalse()

    // Check events
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    Truth.assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.CHECK_JETIFIER_BUILD to BuildAttributionStats.BuildAnalysisStatus.SUCCESS,
    ))
  }

  private fun createBuildAttributionFile(request: GradleBuildInvoker.Request): File {
    val attributionData = AndroidGradlePluginAttributionData()
    val outputDir = getAgpAttributionFileDir(request.data)
    val file = AndroidGradlePluginAttributionData.getAttributionFile(outputDir)
    file.parentFile.mkdirs()
    BufferedWriter(FileWriter(file)).use {
      it.write(AndroidGradlePluginAttributionData.AttributionDataAdapter.toJson(
        attributionData
      ))
    }
    return file
  }

  private fun taskFinishEvent(): TaskFinishEvent {
    val pluginA = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    return createTaskFinishEventStub(":sampleTask", pluginA, emptyList(), 0, 0)
  }

  private class RuntimeExceptionAnswer : Answer<Any> {
    class TestException : RuntimeException()
    override fun answer(invocation: InvocationOnMock): Any {
      throw TestException()
    }
  }
}
