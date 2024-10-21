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

import com.android.build.attribution.BUILD_ANALYZER_NOTIFICATION_GROUP_ID
import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalyzerSettings
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.constructEmptyBuildResultsObject
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.migrateSetting
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.Projects
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildContentManagerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.content.impl.ContentImpl
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import org.mockito.kotlin.mock
import java.util.UUID
import javax.swing.JPanel

/**
 * This test copies setup of BuildAttributionUiManagerTest and also operates on BuildAttributionUiManager
 * but focuses solely on notification logic.
 */
class BuildAnalyzerNotificationManagerTest : AndroidTestCase() {

  private val tracker = TestUsageTracker(VirtualTimeScheduler())
  private var notificationCounter = 0

  private lateinit var buildAttributionUiManager: BuildAttributionUiManagerImpl
  private val buildAnalyzerStorageMock = mock<BuildAnalyzerStorageManager>()

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(tracker)
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == BUILD_ANALYZER_NOTIFICATION_GROUP_ID) {
          notificationCounter++
        }
      }
    })
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
      //TODO (b/243175483): should we start showing warning for stable?
      configurationCachingCompatibilityAnalyzerResult = NoIncompatiblePlugins(emptyList(), false)
    )
    setNewReportData(result)

    verifyNotificationShownForSessions(emptyList())
  }

  fun testBalloonNotShownOnConfigCacheTrialBuild() {
    val buildSessionId = UUID.randomUUID().toString()
    val result = constructEmptyBuildResultsObject(buildSessionId, Projects.getBaseDirPath(project)).copy(
      configurationCachingCompatibilityAnalyzerResult = ConfigurationCacheCompatibilityTestFlow(false)
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

  fun testBalloonShownForTaskCategoryWarnings() {
    /*
    Test that
    1) shown for just TaskCategoryIssue
    2) not shown for the same issue second time
    3) shown for other TaskCategoryIssue found
     */
    val buildSessionId1 = UUID.randomUUID().toString()
    val buildSessionId2 = UUID.randomUUID().toString()
    val buildSessionId3 = UUID.randomUUID().toString()
    val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "compiler.plugin")
    val task1 = TaskData("task1", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList()).apply {
      setTaskCategories(TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD.taskCategory, emptyList())
    }
    val task2 = TaskData("task2", ":app", plugin, 0, 2000, TaskData.TaskExecutionMode.FULL, emptyList()).apply {
      setTaskCategories(TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED.taskCategory, emptyList())
    }
    val criticalPathAnalyzerResult = CriticalPathAnalyzer.Result(
      listOf(task1, task2),
      emptyList(),
      0,
      0
    )
    val result1 = constructEmptyBuildResultsObject(buildSessionId1, Projects.getBaseDirPath(project)).copy(
      taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.IssuesResult(listOf(
        TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD
      )),
      criticalPathAnalyzerResult = criticalPathAnalyzerResult,
      taskMap = mapOf(task1.taskName to task1, task2.taskName to task2),
      pluginMap = mapOf(plugin.idName to plugin)
    )
    val result2 = result1.copy(
      buildSessionID = buildSessionId2
    )
    val result3 = result1.copy(
      buildSessionID = buildSessionId3,
      taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.IssuesResult(listOf(
        TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD,
        TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED
      )),
    )
    setNewReportData(result1)
    setNewReportData(result2)
    setNewReportData(result3)

    verifyNotificationShownForSessions(listOf(buildSessionId1, buildSessionId3))
  }

  fun testNotificationSettingDefault() {
    val group = NotificationGroup.findRegisteredGroup(BUILD_ANALYZER_NOTIFICATION_GROUP_ID)!!
    Truth.assertThat(group.displayType).isEqualTo(NotificationDisplayType.TOOL_WINDOW)
    Truth.assertThat(group.isHideFromSettings).isEqualTo(false)
    Truth.assertThat(group.toolWindowId).isEqualTo(BuildContentManager.TOOL_WINDOW_ID)
  }

  fun testSettingMigration() {
    Truth.assertThat(NotificationsConfigurationImpl.getSettings(BUILD_ANALYZER_NOTIFICATION_GROUP_ID).displayType)
      .isEqualTo(NotificationDisplayType.TOOL_WINDOW)
    fun testCombination(
      oldSetting: String,
      newSettingBefore: NotificationDisplayType,
      newSettingAfter: NotificationDisplayType
    ) {
      BuildAnalyzerSettings.getInstance(project).settingsState.notifyAboutWarnings = oldSetting
      NotificationsConfigurationImpl.getSettings(BUILD_ANALYZER_NOTIFICATION_GROUP_ID).displayType = newSettingBefore

      migrateSetting(project)

      Truth.assertThat(NotificationsConfigurationImpl.getSettings(BUILD_ANALYZER_NOTIFICATION_GROUP_ID).displayType)
        .isEqualTo(newSettingAfter)
      Truth.assertThat(BuildAnalyzerSettings.getInstance(project).settingsState.notifyAboutWarnings)
        .isEqualTo("deprecated")
    }

    // Previously set false flag should convert to NONE in Notification settings.
    testCombination("false", NotificationDisplayType.TOOL_WINDOW, NotificationDisplayType.NONE)
    testCombination("false", NotificationDisplayType.NONE, NotificationDisplayType.NONE)
    // If it runs on already converted value - nothing should change.
    testCombination("deprecated", NotificationDisplayType.TOOL_WINDOW, NotificationDisplayType.TOOL_WINDOW)
    testCombination("deprecated", NotificationDisplayType.NONE, NotificationDisplayType.NONE)
    // This is probably not needed as previous default value 'true' would not be saved in the xml and thus
    // auto-converted to the new default value. But let's still test just in case.
    testCombination("true", NotificationDisplayType.TOOL_WINDOW, NotificationDisplayType.TOOL_WINDOW)
    testCombination("true", NotificationDisplayType.NONE, NotificationDisplayType.NONE)
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