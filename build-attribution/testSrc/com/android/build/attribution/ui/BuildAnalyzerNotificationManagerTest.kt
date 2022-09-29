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
package com.android.build.attribution.ui

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalyzerSettings
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.testutils.MockitoKt
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.Projects
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildContentManagerImpl
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.impl.ContentImpl
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import java.util.UUID
import javax.swing.JPanel

/**
 * This test copies setup of BuildAttributionUiManagerTest and also operates on BuildAttributionUiManager
 * but focuses solely on notification logic.
 */
class BuildAnalyzerNotificationManagerTest : AndroidTestCase() {

  private lateinit var windowManager: ToolWindowManager

  private val tracker = TestUsageTracker(VirtualTimeScheduler())
  private var notificationCounter = 0

  private lateinit var buildAttributionUiManager: BuildAttributionUiManagerImpl
  private val buildAnalyzerStorageMock = MockitoKt.mock<BuildAnalyzerStorageManager>()

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(tracker)
    windowManager = object : ToolWindowHeadlessManagerImpl(project) {
      override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
        notificationCounter++
      }
    }
    registerProjectService(ToolWindowManager::class.java, windowManager)
    registerProjectService(BuildContentManager::class.java, BuildContentManagerImpl(project))
    registerProjectService(BuildAnalyzerStorageManager::class.java, buildAnalyzerStorageMock)

    // Add a fake build tab
    project.getService(BuildContentManager::class.java).addContent(
      ContentImpl(JPanel(), BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get(), true)
    )

    buildAttributionUiManager = BuildAttributionUiManagerImpl(project)
  }

  override fun tearDown() {
    UsageTracker.cleanAfterTesting()
    super.tearDown()
  }

  fun testBalloonShownOnReportWithWarning() {
    val buildSessionId = UUID.randomUUID().toString()
    val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "compiler.plugin")
    val task = TaskData("compile", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList())
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzer.Result(listOf(AlwaysRunTaskData(task, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)))
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(listOf(buildSessionId))
  }

  fun testBalloonNotShownOnReportWithWarningWhenSettingIsOff() {
    BuildAnalyzerSettings.getInstance(project).settingsState.notifyAboutWarnings = false

    val buildSessionId = UUID.randomUUID().toString()
    val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "compiler.plugin")
    val task = TaskData("compile", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList())
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzer.Result(listOf(AlwaysRunTaskData(task, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)))
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(emptyList())
  }

  fun testBalloonNotShownOnSecondReportWithSameWarning() {
    val buildSessionId1 = UUID.randomUUID().toString()
    val buildSessionId2 = UUID.randomUUID().toString()
    val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "compiler.plugin")
    val task = TaskData("compile", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList())

    val result1 = constructEmptyBuildResultsObject(buildSessionId1, Projects.getBaseDirPath(project)).copy(
      alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzer.Result(listOf(AlwaysRunTaskData(task, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)))
    )
    setNewReportData(result1)
    val result2 = constructEmptyBuildResultsObject(buildSessionId2, Projects.getBaseDirPath(project)).copy(
      alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzer.Result(listOf(AlwaysRunTaskData(task, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)))
    )
    setNewReportData(result2)

    verifyNotificationShownForSessions(listOf(buildSessionId1))
  }

  fun testBalloonShownOnSecondReportWithDifferentWarningType() {
    val buildSessionId1 = UUID.randomUUID().toString()
    val buildSessionId2 = UUID.randomUUID().toString()
    val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "compiler.plugin")
    val task = TaskData("compile", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList())

    val result1 = constructEmptyBuildResultsObject(buildSessionId1, Projects.getBaseDirPath(project)).copy(
      jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(JetifierUsedCheckRequired)
    )
    setNewReportData(result1)
    val result2 = constructEmptyBuildResultsObject(buildSessionId2, Projects.getBaseDirPath(project)).copy(
      alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzer.Result(listOf(AlwaysRunTaskData(task, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS))),
      jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(JetifierUsedCheckRequired)
    )
    setNewReportData(result2)

    verifyNotificationShownForSessions(listOf(buildSessionId1, buildSessionId2))
  }

  fun testBalloonNotShownOnReportWithConfigCacheWarningOnly() {
    val buildSessionId = UUID.randomUUID().toString()
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      configurationCachingCompatibilityAnalyzerResult = NoIncompatiblePlugins(emptyList())
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(emptyList())
  }

  fun testBalloonNotShownOnConfigCacheTrialBuild() {
    val buildSessionId = UUID.randomUUID().toString()
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      configurationCachingCompatibilityAnalyzerResult = ConfigurationCacheCompatibilityTestFlow
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(emptyList())
  }

  fun testBalloonNotShownOnJetifierCheckBuild() {
    val buildSessionId = UUID.randomUUID().toString()
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(JetifierCanBeRemoved, 123456789, checkJetifierBuild = true)
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(emptyList())
  }

  private fun setNewReportData(analysisResults: BuildAnalysisResults) {
    Mockito.`when`(buildAnalyzerStorageMock.getLatestBuildAnalysisResults()).thenReturn(analysisResults)
    Mockito.`when`(buildAnalyzerStorageMock.hasData()).thenReturn(true)
    buildAttributionUiManager.showNewReport()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun verifyNotificationShownForSessions(expectedSessionsWithNotification: List<String>) {
    Truth.assertThat(notificationCounter).isEqualTo(expectedSessionsWithNotification.size)
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .filter { it.studioEvent.buildAttributionUiEvent.eventType == BuildAttributionUiEvent.EventType.TOOL_WINDOW_BALLOON_SHOWN }
      .map { it.studioEvent.buildAttributionUiEvent.buildAttributionReportSessionId }

    Truth.assertThat(buildAttributionEvents).containsExactlyElementsIn(expectedSessionsWithNotification).inOrder()
  }
}