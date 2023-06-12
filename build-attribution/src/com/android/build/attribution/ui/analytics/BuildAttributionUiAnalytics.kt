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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksFilter
import com.android.build.attribution.ui.model.WarningsFilter
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import java.time.Duration

class BuildAttributionUiAnalytics(
  private val project: Project,
  private val uiSizeProvider: () -> Dimension?
) {

  enum class TabOpenEventSource {
    WNA_BUTTON,
    BUILD_OUTPUT_LINK,
    TAB_HEADER,
    AUTO_OPEN,
    BALLOON_LINK,
    BUILD_MENU_ACTION
  }

  private val unknownPage: BuildAttributionUiEvent.Page = BuildAttributionUiEvent.Page.newBuilder()
    .setPageType(BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE)
    .build()
  private var currentPage: BuildAttributionUiEvent.Page = unknownPage

  private var tabOpenEventSource: TabOpenEventSource = TabOpenEventSource.TAB_HEADER

  private var buildAttributionReportSessionId: String? = null

  private val pagesVisited = mutableMapOf<AnalyticsPageId, BuildAttributionUiEvent.Page>()
  private val pagesCountByType = mutableMapOf<BuildAttributionUiEvent.Page.PageType, Int>()

  init {
    Disposer.register(project, Disposable { sendSessionOverIfExist() })
  }

  private fun sendSessionOverIfExist() {
    if (buildAttributionReportSessionId != null) {
      doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER))
    }
  }

  /**
   * Called when new "Build Analyzer" tab is created.
   */
  fun tabCreated() = doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.TAB_CREATED))

  /**
   * Called when "Build Analyzer" tab becomes selected in Build toolwindow.
   * If [registerOpenEventSource] was called just before this call then this event will be reported using provided there value.
   */
  fun tabOpened() {
    val eventType = when (tabOpenEventSource) {
      TabOpenEventSource.WNA_BUTTON -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_WNA_BUTTON
      TabOpenEventSource.BUILD_OUTPUT_LINK -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
      TabOpenEventSource.BUILD_MENU_ACTION -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_ACTION
      TabOpenEventSource.TAB_HEADER -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK
      // Not opened by direct user action so don't report.
      TabOpenEventSource.AUTO_OPEN -> null
      TabOpenEventSource.BALLOON_LINK -> BuildAttributionUiEvent.EventType.TOOL_WINDOW_BALLOON_DETAILS_LINK_CLICKED
    }
    if (eventType != null) doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(eventType))
  }

  /**
   * Called when other tab becomes opened on Build toolwindow.
   */
  fun tabHidden() {
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.TAB_HIDDEN))
  }

  /**
   * Called when "Build Analyzer" tab is getting closed.
   */
  fun tabClosed() {
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.TAB_CLOSED))
  }

  /**
   * Called when report about new build replaces current one in the opened "Build Analyzer" tab.
   */
  fun buildReportReplaced() {
    doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.CONTENT_REPLACED))
  }

  /**
   * Registers what action was clicked to open Build Analyzer tab so next [tabOpened] call should report it's event as opened using that action.
   * This state will be cleared with any next event sent.
   */
  fun registerOpenEventSource(eventSource: TabOpenEventSource) {
    tabOpenEventSource = eventSource
  }

  /**
   * Called when page selection changes and new page is shown to the user.
   * Called from the action handler which should be aware of and provide in the parameters
   * what page user is navigation to and what method is used.
   */
  fun pageChange(
    currentPage: BuildAttributionUiEvent.Page,
    targetPage: BuildAttributionUiEvent.Page,
    eventType: BuildAttributionUiEvent.EventType,
    duration: Duration
  ) {
    doLog(
      newUiEventBuilder()
        .setEventType(eventType)
        .setCurrentPage(currentPage)
        .setTargetPage(targetPage)
        .setEventProcessingTimeMs(duration.toMillis())
    )
    this.currentPage = targetPage
  }

  fun bugReportLinkClicked(currentPage: BuildAttributionUiEvent.Page) {
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.GENERATE_REPORT_LINK_CLICKED))
  }

  fun reportingWindowCopyButtonClicked() =
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_TEXT_COPY_CLICKED))

  fun reportingWindowClosed() =
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_CLOSED))

  fun helpLinkClicked(
    currentPageId: BuildAttributionUiEvent.Page,
    target: BuildAnalyzerBrowserLinks
  ) = doLog(
    newUiEventBuilder()
      .setCurrentPage(currentPageId)
      .setLinkTarget(target.analyticsValue)
      .setEventType(BuildAttributionUiEvent.EventType.HELP_LINK_CLICKED))

  fun memorySettingsOpened() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.OPEN_MEMORY_SETTINGS_BUTTON_CLICKED)
  )

  fun noGCSettingWarningSuppressed() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.CONFIGURE_GC_WARNING_SUSPEND_CLICKED)
  )

  fun runAgpUpgradeClicked() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.UPGRADE_AGP_BUTTON_CLICKED)
  )

  fun rerunBuildWithConfCacheClicked() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.RERUN_BUILD_WITH_CONFIGURATION_CACHE_CLICKED)
  )

  fun turnConfigurationCacheOnInPropertiesClicked() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.TURN_ON_CONFIGURATION_CACHE_IN_PROPERTIES_LINK_CLICKED)
  )

  fun updatePluginButtonClicked(duration: Duration) = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.UPDATE_PLUGIN_BUTTON_CLICKED)
      .setEventProcessingTimeMs(duration.toMillis())
  )

  fun runCheckJetifierTaskClicked(duration: Duration) = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.RUN_CHECK_JETIFIER_TASK_CLICKED)
      .setEventProcessingTimeMs(duration.toMillis())
  )

  fun migrateToNonTransitiveRClassesClicked() = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.MIGRATE_NON_TRANSITIVE_R_CLASS_ACTION_CLICKED)
  )

  fun turnJetifierOffClicked(duration: Duration) = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.REMOVE_JETIFIER_PROPERTY_CLICKED)
      .setEventProcessingTimeMs(duration.toMillis())
  )

  fun findLibraryVersionDeclarationActionUsed(duration: Duration) = doLog(
  newUiEventBuilder()
  .setEventType(BuildAttributionUiEvent.EventType.FIND_LIBRARY_DECLARATION_CLICKED)
  .setEventProcessingTimeMs(duration.toMillis())
  )

  fun warningsFilterApplied(filter: WarningsFilter, duration: Duration) = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.FILTER_APPLIED)
      .addAllAppliedFilters(warningsFilterState(filter))
      .setEventProcessingTimeMs(duration.toMillis())
  )

  fun tasksFilterApplied(filter: TasksFilter, duration: Duration) = doLog(
    newUiEventBuilder()
      .setEventType(BuildAttributionUiEvent.EventType.FILTER_APPLIED)
      .addAllAppliedFilters(tasksFilterState(filter))
      .setEventProcessingTimeMs(duration.toMillis())
  )

  fun toolWindowBalloonShown() = doLog(
    newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.TOOL_WINDOW_BALLOON_SHOWN)
  )

  private fun newUiEventBuilder(): BuildAttributionUiEvent.Builder {
    requireNotNull(buildAttributionReportSessionId)
    return BuildAttributionUiEvent.newBuilder().also { builder ->
      builder.buildAttributionReportSessionId = buildAttributionReportSessionId
      uiSizeProvider()?.let {
        builder.width = it.width.toLong()
        builder.height = it.height.toLong()
      }
    }
  }

  private fun registerPage(pageId: AnalyticsPageId): BuildAttributionUiEvent.Page {
    val newPageEntryIndex = pagesCountByType.compute(pageId.pageType) { _, count -> count?.inc() ?: 1 }!!
    return BuildAttributionUiEvent.Page.newBuilder().setPageType(pageId.pageType).setPageEntryIndex(newPageEntryIndex).build()
  }

  private fun toPage(pageId: AnalyticsPageId): BuildAttributionUiEvent.Page =
    pagesVisited.computeIfAbsent(pageId) { registerPage(it) }

  private fun doLog(uiEvent: BuildAttributionUiEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT)
        .withProjectId(project)
        .setBuildAttributionUiEvent(uiEvent)
    )
    //Clear up state variables
    tabOpenEventSource = TabOpenEventSource.TAB_HEADER
  }

  fun initFirstPage(model: BuildAnalyzerViewModel) {
    currentPage = getStateFromModel(model)
  }

  /**
   * Set new build id to be sent with the events.
   * If previous session existed, send closing event for it.
   */
  fun newReportSessionId(buildSessionId: String) {
    sendSessionOverIfExist()
    pagesVisited.clear()
    pagesCountByType.clear()
    currentPage = unknownPage
    buildAttributionReportSessionId = buildSessionId
  }

  fun getStateFromModel(model: BuildAnalyzerViewModel): BuildAttributionUiEvent.Page {
    return toPage(
      when (model.selectedData) {
        BuildAnalyzerViewModel.DataSet.OVERVIEW ->
          AnalyticsPageId(BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY, BuildAnalyzerViewModel.DataSet.OVERVIEW.uiName)
        BuildAnalyzerViewModel.DataSet.TASKS ->
          model.tasksPageModel.selectedNode.let {
            if (it != null) AnalyticsPageId(it.descriptor.analyticsPageType, it.descriptor.pageId.id)
            else AnalyticsPageId(
              pageType = when (model.tasksPageModel.selectedGrouping) {
                TasksDataPageModel.Grouping.UNGROUPED -> BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT
                TasksDataPageModel.Grouping.BY_PLUGIN -> BuildAttributionUiEvent.Page.PageType.PLUGIN_CRITICAL_PATH_TASKS_ROOT
                TasksDataPageModel.Grouping.BY_TASK_CATEGORY -> BuildAttributionUiEvent.Page.PageType.TASK_CATEGORY_CRITICAL_PATH_TASKS_ROOT
              },
              pageId = ""
            )
          }
        BuildAnalyzerViewModel.DataSet.WARNINGS -> model.warningsPageModel.selectedNode.let {
          if (it != null) AnalyticsPageId(it.descriptor.analyticsPageType, it.descriptor.pageId.id)
          else AnalyticsPageId(BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT, pageId = "")
        }
        BuildAnalyzerViewModel.DataSet.DOWNLOADS ->
          AnalyticsPageId(BuildAttributionUiEvent.Page.PageType.DOWNLOADS_INFO, "")
      }
    )
  }

  fun reportUnregisteredEvent() {
    doLog(newUiEventBuilder().setCurrentPage(currentPage).setEventType(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE))
  }

  data class AnalyticsPageId(
    val pageType: BuildAttributionUiEvent.Page.PageType,
    val pageId: String
  )

  private fun warningsFilterState(filter: WarningsFilter): List<BuildAttributionUiEvent.FilterItem> {
    return mutableListOf<BuildAttributionUiEvent.FilterItem>().apply {
      filter.showTaskSourceTypes.forEach {
        add(when (it) {
              PluginSourceType.ANDROID_PLUGIN -> BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS
              PluginSourceType.BUILD_SCRIPT -> BuildAttributionUiEvent.FilterItem.SHOW_PROJECT_CUSTOMIZATION_TASKS
              PluginSourceType.THIRD_PARTY -> BuildAttributionUiEvent.FilterItem.SHOW_THIRD_PARTY_TASKS
            })
      }
      filter.showTaskWarningTypes.forEach {
        add(when (it) {
              TaskIssueType.ALWAYS_RUN_TASKS -> BuildAttributionUiEvent.FilterItem.SHOW_ALWAYS_RUN_TASK_WARNINGS
              TaskIssueType.TASK_SETUP_ISSUE -> BuildAttributionUiEvent.FilterItem.SHOW_TASK_SETUP_ISSUE_WARNINGS
            })
      }
      if (filter.showAnnotationProcessorWarnings) add(BuildAttributionUiEvent.FilterItem.SHOW_ANNOTATION_PROCESSOR_WARNINGS)
      if (filter.showNonCriticalPathTasks) add(BuildAttributionUiEvent.FilterItem.SHOW_WARNINGS_FOR_TASK_NOT_FROM_CRITICAL_PATH)
      if (filter.showConfigurationCacheWarnings) add(BuildAttributionUiEvent.FilterItem.SHOW_CONFIGURATION_CACHE_WARNINGS)
      if (filter.showJetifierWarnings) add(BuildAttributionUiEvent.FilterItem.SHOW_JETIFIER_USAGE_WARNINGS)
    }.sorted()
  }

  private fun tasksFilterState(filter: TasksFilter): List<BuildAttributionUiEvent.FilterItem> {
    return mutableListOf<BuildAttributionUiEvent.FilterItem>().apply {
      filter.showTaskSourceTypes.forEach {
        add(when (it) {
              PluginSourceType.ANDROID_PLUGIN -> BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS
              PluginSourceType.BUILD_SCRIPT -> BuildAttributionUiEvent.FilterItem.SHOW_PROJECT_CUSTOMIZATION_TASKS
              PluginSourceType.THIRD_PARTY -> BuildAttributionUiEvent.FilterItem.SHOW_THIRD_PARTY_TASKS
            })
      }
      if (filter.showTasksWithoutWarnings) add(BuildAttributionUiEvent.FilterItem.SHOW_TASKS_WITHOUT_WARNINGS)
    }.sorted()
  }
}