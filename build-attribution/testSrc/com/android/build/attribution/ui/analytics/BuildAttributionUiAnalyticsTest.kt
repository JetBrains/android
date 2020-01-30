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
package com.android.build.attribution.ui.analytics

import com.android.build.attribution.ui.tree.AbstractBuildAttributionNode
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.CONTENT_REPLACED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.GENERATE_REPORT_LINK_CLICKED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.HELP_LINK_CLICKED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.REPORT_DIALOG_CLOSED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.REPORT_DIALOG_TEXT_COPY_CLICKED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.TAB_CLOSED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.TAB_CREATED
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.TAB_HIDDEN
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_ISSUE_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.ANNOTATION_PROCESSORS_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.ANNOTATION_PROCESSOR_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_PLUGIN
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_PROJECT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASK_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGINS_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_ISSUE_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_CRITICAL_PATH_TASKS_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_CRITICAL_PATH_TASK_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_TASK_SETUP_ISSUE_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.PLUGIN_TASK_SETUP_ISSUE_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.TASK_SETUP_ISSUE_PAGE
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.TASK_SETUP_ISSUE_ROOT
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.UUID

class BuildAttributionUiAnalyticsTest {
  @Mock
  private lateinit var project: Project

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private val buildSessionId = UUID.randomUUID().toString()

  private lateinit var uiAnalytics: BuildAttributionUiAnalytics

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    UsageTracker.setWriterForTest(tracker)

    Mockito.`when`(project.basePath).thenReturn("test")
    val moduleManager = Mockito.mock(ModuleManager::class.java)
    Mockito.`when`(project.getComponent(ModuleManager::class.java)).thenReturn(moduleManager)
    Mockito.`when`(moduleManager.modules).thenReturn(emptyArray<Module>())

    uiAnalytics = BuildAttributionUiAnalytics(project)
    uiAnalytics.newReportSessionId(buildSessionId)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
    Disposer.dispose(project)
  }

  @Test
  fun testTabCreate() {
    uiAnalytics.tabCreated()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    val buildAttributionUiEvent = buildAttributionEvents.first().studioEvent.buildAttributionUiEvent
    Truth.assertThat(buildAttributionUiEvent.buildAttributionReportSessionId).isEqualTo(buildSessionId)
    Truth.assertThat(buildAttributionUiEvent.eventType).isEqualTo(EventType.TAB_CREATED)
  }

  @Test
  fun testTabOpen() {
    uiAnalytics.initFirstPage("criticalPathTasks", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.tabOpened()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    val buildAttributionUiEvent = buildAttributionEvents.first().studioEvent.buildAttributionUiEvent
    Truth.assertThat(buildAttributionUiEvent.buildAttributionReportSessionId).isEqualTo(buildSessionId)
    Truth.assertThat(buildAttributionUiEvent.eventType).isEqualTo(EventType.TAB_OPENED_WITH_TAB_CLICK)
  }

  @Test
  fun testTabOpenFromBuildOutputLink() {
    uiAnalytics.initFirstPage("criticalPathTasks", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.registerBuildOutputLinkClick()
    uiAnalytics.tabOpened()
    //Check state was cleared and next open will be reported as TAB_OPENED_WITH_TAB_CLICK
    uiAnalytics.tabOpened()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(2)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK)
    }

    buildAttributionEvents.last().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.TAB_OPENED_WITH_TAB_CLICK)
    }
  }

  @Test
  fun testTabClosed() {
    uiAnalytics.initFirstPage("criticalPathTasks", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.tabClosed()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.TAB_CLOSED)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASKS_ROOT)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testTabHidden() {
    uiAnalytics.initFirstPage("criticalPathTasks", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.tabHidden()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.TAB_HIDDEN)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASKS_ROOT)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testContentReplaced() {
    uiAnalytics.tabCreated()
    uiAnalytics.initFirstPage("criticalPathTasks", CRITICAL_PATH_TASKS_ROOT)
    val newBuildSessionId = UUID.randomUUID().toString()
    uiAnalytics.newReportSessionId(newBuildSessionId)
    uiAnalytics.buildReportReplaced()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(3)

    buildAttributionEvents[0].studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.TAB_CREATED)
    }

    buildAttributionEvents[1].studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.USAGE_SESSION_OVER)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASKS_ROOT)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }

    buildAttributionEvents[2].studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(newBuildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.CONTENT_REPLACED)
    }
  }

  @Test
  fun testHelpLinkClicked() {
    uiAnalytics.initFirstPage("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.helpLinkClicked()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.HELP_LINK_CLICKED)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testGenerateReportLinkClicked() {
    uiAnalytics.initFirstPage("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.bugReportLinkClicked()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.GENERATE_REPORT_LINK_CLICKED)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testReportingWindowCopyClicked() {
    uiAnalytics.initFirstPage("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.reportingWindowCopyButtonClicked()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.REPORT_DIALOG_TEXT_COPY_CLICKED)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testReportingWindowClosed() {
    uiAnalytics.initFirstPage("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.reportingWindowClosed()

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.REPORT_DIALOG_CLOSED)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testPageChangeEvent() {
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(PAGE_CHANGE_TREE_CLICK)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASKS_ROOT)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
      Truth.assertThat(it.targetPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.targetPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testLinkPageChangeEvent() {
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.registerNodeLinkClick()
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).hasSize(1)

    buildAttributionEvents.first().studioEvent.buildAttributionUiEvent.let {
      Truth.assertThat(it.buildAttributionReportSessionId).isEqualTo(buildSessionId)
      Truth.assertThat(it.eventType).isEqualTo(EventType.PAGE_CHANGE_LINK_CLICK)
      Truth.assertThat(it.currentPage.pageType).isEqualTo(CRITICAL_PATH_TASKS_ROOT)
      Truth.assertThat(it.currentPage.pageEntryIndex).isEqualTo(1)
      Truth.assertThat(it.targetPage.pageType).isEqualTo(CRITICAL_PATH_TASK_PAGE)
      Truth.assertThat(it.targetPage.pageEntryIndex).isEqualTo(1)
    }
  }

  @Test
  fun testSamePageOpenTwice() {
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("task2", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("tasksRoot", CRITICAL_PATH_TASKS_ROOT)

    val buildAttributionEvents = convertToComparableString(
      tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    )

    val expected = expectedEventsFlow(
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(CRITICAL_PATH_TASK_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 1), Pair(CRITICAL_PATH_TASK_PAGE, 2)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 2), Pair(CRITICAL_PATH_TASK_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 1), Pair(CRITICAL_PATH_TASKS_ROOT, 1))
    )
    Truth.assertThat(buildAttributionEvents).isEqualTo(expected)
  }

  /**
   * This test verifies that a long session of calls to [BuildAttributionUiAnalytics] in a way it is expected from UI
   * results in an expected event chain.
   */
  @Test
  fun testFullFlow() {
    uiAnalytics.tabCreated()
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.tabOpened()
    uiAnalytics.pageChange("buildSummary", BUILD_SUMMARY)
    uiAnalytics.pageChange("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("pluginsRoot", PLUGINS_ROOT)
    uiAnalytics.pageChange("plugin1", PLUGIN_PAGE)
    uiAnalytics.pageChange("plugin1-tasks", PLUGIN_CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.pageChange("plugin1-task1", PLUGIN_CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("plugin1-task2", PLUGIN_CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("plugin2", PLUGIN_PAGE)
    uiAnalytics.pageChange("plugin2-task1", PLUGIN_CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.registerNodeLinkClick()
    uiAnalytics.pageChange("plugin1-taskSetupIssues", PLUGIN_TASK_SETUP_ISSUE_ROOT)
    uiAnalytics.registerNodeLinkClick()
    uiAnalytics.pageChange("plugin1-taskSetupIssue1", PLUGIN_TASK_SETUP_ISSUE_PAGE)
    uiAnalytics.pageChange("plugin1-alwaysRunIssues", PLUGIN_ALWAYS_RUN_ISSUE_ROOT)
    uiAnalytics.pageChange("plugin1-alwaysRunIssueOutputs1", PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE)
    uiAnalytics.pageChange("plugin1-alwaysRunIssueOverride1", PLUGIN_ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE)
    uiAnalytics.pageChange("pluginsRoot", PLUGINS_ROOT)
    uiAnalytics.pageChange("taskSetupIssues", TASK_SETUP_ISSUE_ROOT)
    uiAnalytics.pageChange("taskSetupIssue1", TASK_SETUP_ISSUE_PAGE)
    uiAnalytics.helpLinkClicked()
    uiAnalytics.bugReportLinkClicked()
    uiAnalytics.reportingWindowCopyButtonClicked()
    uiAnalytics.reportingWindowClosed()
    uiAnalytics.pageChange("alwaysRunIssues", ALWAYS_RUN_ISSUE_ROOT)
    uiAnalytics.pageChange("alwaysRunIssueOutputs1", ALWAYS_RUN_NO_OUTPUTS_PAGE)
    uiAnalytics.pageChange("alwaysRunIssueOverride1", ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE)
    uiAnalytics.pageChange("annotationProcessors", ANNOTATION_PROCESSORS_ROOT)
    uiAnalytics.pageChange("annotationProcessor1", ANNOTATION_PROCESSOR_PAGE)
    uiAnalytics.pageChange("annotationProcessor2", ANNOTATION_PROCESSOR_PAGE)
    uiAnalytics.pageChange("configurationTimeRoot", CONFIGURATION_TIME_ROOT)
    uiAnalytics.pageChange("configurationTimeProject", CONFIGURATION_TIME_PROJECT)
    uiAnalytics.pageChange("configurationTimePlugin", CONFIGURATION_TIME_PLUGIN)

    uiAnalytics.tabHidden()
    uiAnalytics.tabOpened()
    // Current page should still be "configurationTimePlugin". Next pages already have been opened and should have same numbers.
    uiAnalytics.pageChange("buildSummary", BUILD_SUMMARY)
    uiAnalytics.pageChange("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.pageChange("task1", CRITICAL_PATH_TASK_PAGE)
    // Not previously opened page, should have new number assigned.
    uiAnalytics.pageChange("task2", CRITICAL_PATH_TASK_PAGE)

    uiAnalytics.tabClosed()
    uiAnalytics.tabCreated()
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    uiAnalytics.registerBuildOutputLinkClick()
    uiAnalytics.tabOpened()
    // Current page should now be "tasksRoot" as view was reset.
    // "task2" was already opened and should have the same number 2.
    uiAnalytics.pageChange("task2", CRITICAL_PATH_TASK_PAGE)
    // This page is new and should have new number assigned.
    uiAnalytics.pageChange("task3", CRITICAL_PATH_TASK_PAGE)

    val newBuildSessionId = UUID.randomUUID().toString()
    uiAnalytics.newReportSessionId(newBuildSessionId)
    uiAnalytics.buildReportReplaced()
    uiAnalytics.initFirstPage("tasksRoot", CRITICAL_PATH_TASKS_ROOT)
    // All pages should get new numbers now.
    uiAnalytics.pageChange("task3", CRITICAL_PATH_TASK_PAGE)
    uiAnalytics.pageChange("task2", CRITICAL_PATH_TASK_PAGE)

    val expectedEvents = expectedEventsFlow(
      Event(buildSessionId, TAB_CREATED, Pair(UNKNOWN_PAGE, 0), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, TAB_OPENED_WITH_TAB_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(BUILD_SUMMARY, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(BUILD_SUMMARY, 1), Pair(CRITICAL_PATH_TASKS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(CRITICAL_PATH_TASK_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 1), Pair(PLUGINS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGINS_ROOT, 1), Pair(PLUGIN_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_PAGE, 1), Pair(PLUGIN_CRITICAL_PATH_TASKS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_CRITICAL_PATH_TASKS_ROOT, 1), Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 1), Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 2)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 2), Pair(PLUGIN_PAGE, 2)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_PAGE, 2), Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 3)),
      // Next to page changes were made using links.
      Event(buildSessionId, PAGE_CHANGE_LINK_CLICK, Pair(PLUGIN_CRITICAL_PATH_TASK_PAGE, 3), Pair(PLUGIN_TASK_SETUP_ISSUE_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_LINK_CLICK, Pair(PLUGIN_TASK_SETUP_ISSUE_ROOT, 1), Pair(PLUGIN_TASK_SETUP_ISSUE_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_TASK_SETUP_ISSUE_PAGE, 1), Pair(PLUGIN_ALWAYS_RUN_ISSUE_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_ALWAYS_RUN_ISSUE_ROOT, 1), Pair(PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE, 1),
            Pair(PLUGIN_ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGIN_ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE, 1), Pair(PLUGINS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(PLUGINS_ROOT, 1), Pair(TASK_SETUP_ISSUE_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(TASK_SETUP_ISSUE_ROOT, 1), Pair(TASK_SETUP_ISSUE_PAGE, 1)),
      // On last page there were interactions with help link and report dialog.
      Event(buildSessionId, HELP_LINK_CLICKED, Pair(TASK_SETUP_ISSUE_PAGE, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, GENERATE_REPORT_LINK_CLICKED, Pair(TASK_SETUP_ISSUE_PAGE, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, REPORT_DIALOG_TEXT_COPY_CLICKED, Pair(TASK_SETUP_ISSUE_PAGE, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, REPORT_DIALOG_CLOSED, Pair(TASK_SETUP_ISSUE_PAGE, 1), Pair(UNKNOWN_PAGE, 0)),

      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(TASK_SETUP_ISSUE_PAGE, 1), Pair(ALWAYS_RUN_ISSUE_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ALWAYS_RUN_ISSUE_ROOT, 1), Pair(ALWAYS_RUN_NO_OUTPUTS_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ALWAYS_RUN_NO_OUTPUTS_PAGE, 1), Pair(ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE, 1), Pair(ANNOTATION_PROCESSORS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ANNOTATION_PROCESSORS_ROOT, 1), Pair(ANNOTATION_PROCESSOR_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ANNOTATION_PROCESSOR_PAGE, 1), Pair(ANNOTATION_PROCESSOR_PAGE, 2)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(ANNOTATION_PROCESSOR_PAGE, 2), Pair(CONFIGURATION_TIME_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CONFIGURATION_TIME_ROOT, 1), Pair(CONFIGURATION_TIME_PROJECT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CONFIGURATION_TIME_PROJECT, 1), Pair(CONFIGURATION_TIME_PLUGIN, 1)),

      Event(buildSessionId, TAB_HIDDEN, Pair(CONFIGURATION_TIME_PLUGIN, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, TAB_OPENED_WITH_TAB_CLICK, Pair(CONFIGURATION_TIME_PLUGIN, 1), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CONFIGURATION_TIME_PLUGIN, 1), Pair(BUILD_SUMMARY, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(BUILD_SUMMARY, 1), Pair(CRITICAL_PATH_TASKS_ROOT, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(CRITICAL_PATH_TASK_PAGE, 1)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 1), Pair(CRITICAL_PATH_TASK_PAGE, 2)),

      Event(buildSessionId, TAB_CLOSED, Pair(CRITICAL_PATH_TASK_PAGE, 2), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, TAB_CREATED, Pair(UNKNOWN_PAGE, 0), Pair(UNKNOWN_PAGE, 0)),
      Event(buildSessionId, TAB_OPENED_WITH_BUILD_OUTPUT_LINK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(UNKNOWN_PAGE, 0)),
      // Event after tab re-creation page assigned numbers should stay the same for same build.
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(CRITICAL_PATH_TASK_PAGE, 2)),
      Event(buildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 2), Pair(CRITICAL_PATH_TASK_PAGE, 3)),
      // New build happened, we get new buildSessionId.
      // First of all event closing prev session should be sent
      Event(buildSessionId, USAGE_SESSION_OVER, Pair(CRITICAL_PATH_TASK_PAGE, 3), Pair(UNKNOWN_PAGE, 0)),
      // Next events should have new session id
      Event(newBuildSessionId, CONTENT_REPLACED, Pair(UNKNOWN_PAGE, 0), Pair(UNKNOWN_PAGE, 0)),
      // Pages should start new enumeration from 1 for the new build,
      // check it on example of previously visited "task3" and "task2"
      Event(newBuildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASKS_ROOT, 1), Pair(CRITICAL_PATH_TASK_PAGE, 1)),
      Event(newBuildSessionId, PAGE_CHANGE_TREE_CLICK, Pair(CRITICAL_PATH_TASK_PAGE, 1), Pair(CRITICAL_PATH_TASK_PAGE, 2))
    )

    val buildAttributionEvents = convertToComparableString(
      tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    )

    Truth.assertThat(buildAttributionEvents).isEqualTo(expectedEvents)

  }

  data class Event(
    val session: String,
    val type: EventType,
    val currentPage: Pair<PageType, Int>,
    val targetPage: Pair<PageType, Int>
  )

  private fun expectedEventsFlow(vararg events: Event): String = events.joinToString(separator = "\n")

  private fun convertToComparableString(loggedUsages: List<LoggedUsage>): String =
    loggedUsages.map { it.toTestEvent() }.joinToString(separator = "\n")

  private fun LoggedUsage.toTestEvent(): Event = Event(
    studioEvent.buildAttributionUiEvent.buildAttributionReportSessionId,
    studioEvent.buildAttributionUiEvent.eventType,
    Pair(studioEvent.buildAttributionUiEvent.currentPage.pageType, studioEvent.buildAttributionUiEvent.currentPage.pageEntryIndex),
    Pair(studioEvent.buildAttributionUiEvent.targetPage.pageType, studioEvent.buildAttributionUiEvent.targetPage.pageEntryIndex)
  )

  private fun mockPageNode(id: String, type: PageType): AbstractBuildAttributionNode {
    val pageMock = Mockito.mock(AbstractBuildAttributionNode::class.java)
    Mockito.`when`(pageMock.nodeId).thenReturn(id)
    Mockito.`when`(pageMock.pageType).thenReturn(type)
    return pageMock
  }
}