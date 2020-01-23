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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class BuildAttributionUiAnalytics(private val project: Project) {

  private val unknownPage: BuildAttributionUiEvent.Page = BuildAttributionUiEvent.Page.newBuilder()
    .setPageType(BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE)
    .build()
  private var currentPage: BuildAttributionUiEvent.Page = unknownPage

  private var nodeLinkClickRegistered = false
  private var buildOutputLinkClickRegistered = false

  private var buildAttributionReportSessionId: String? = null

  private val pagesVisited = mutableMapOf<String, BuildAttributionUiEvent.Page>()
  private val pagesCountByType = mutableMapOf<BuildAttributionUiEvent.Page.PageType, Int>()

  init {
    Disposer.register(project, Disposable { sendSessionOverIfExist() })
  }

  private fun sendSessionOverIfExist() {
    if (buildAttributionReportSessionId != null) {
      doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER))
    }
  }

  /**
   * Called when new "Build Analyzer" tab is created.
   */
  fun tabCreated() = doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.TAB_CREATED))

  /**
   * Called when "Build Analyzer" tab becomes selected in Build toolwindow.
   * If [registerBuildOutputLinkClick] was called just before this call then this event will be reported as opened from build output link.
   */
  fun tabOpened() {
    val eventType = if (buildOutputLinkClickRegistered)
      BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
    else
      BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK
    doLog(newUiEventBuilderWithPage().setEventType(eventType))
  }

  /**
   * Called when other tab becomes opened on Build toolwindow.
   */
  fun tabHidden() {
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.TAB_HIDDEN))
  }

  /**
   * Called when "Build Analyzer" tab is getting closed.
   */
  fun tabClosed() {
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.TAB_CLOSED))
  }

  /**
   * Called when report about new build replaces current one in the opened "Build Analyzer" tab.
   */
  fun buildReportReplaced() {
    doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.CONTENT_REPLACED))
  }

  /**
   * Registers that page is going to be changed as the result of link click so next [pageChange] call should report it properly.
   * This state will be cleared with any next event sent.
   */
  fun registerNodeLinkClick() {
    nodeLinkClickRegistered = true
  }

  /**
   * Registers that build output window was clicked so next [tabOpened] call should report it's event as opened with link.
   * This state will be cleared with any next event sent.
   */
  fun registerBuildOutputLinkClick() {
    buildOutputLinkClickRegistered = true
  }

  /**
   * Called when tree selection changes and new page is shown to the user.
   * If [registerNodeLinkClick] was called just before this call then this event will be reported as PAGE_CHANGE_LINK_CLICK.
   */
  fun pageChange(selectedNodeId: String, pageType: BuildAttributionUiEvent.Page.PageType) {
    val newPage = toPage(selectedNodeId, pageType)
    val eventType = if (nodeLinkClickRegistered) {
      BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK
    }
    else {
      // TODO mlazeba Find how to easily track what was used to update tree selection: mouse or keystrokes.
      // Report both cases as TREE_CLICK for now.
      BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK
    }
    val uiEvent = newUiEventBuilderWithPage().setEventType(eventType).setTargetPage(newPage)
    doLog(uiEvent)

    currentPage = newPage
  }

  fun bugReportLinkClicked() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.GENERATE_REPORT_LINK_CLICKED))

  fun reportingWindowCopyButtonClicked() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_TEXT_COPY_CLICKED))

  fun reportingWindowClosed() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_CLOSED))


  fun helpLinkClicked() = doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.HELP_LINK_CLICKED))

  private fun newUiEventBuilder(): BuildAttributionUiEvent.Builder {
    requireNotNull(buildAttributionReportSessionId)
    return BuildAttributionUiEvent.newBuilder().setBuildAttributionReportSessionId(buildAttributionReportSessionId)
  }

  private fun newUiEventBuilderWithPage() = newUiEventBuilder().setCurrentPage(currentPage)

  private fun registerPage(pageType: BuildAttributionUiEvent.Page.PageType): BuildAttributionUiEvent.Page {
    val newPageEntryIndex = pagesCountByType.compute(pageType) { _, count -> count?.inc() ?: 1 }!!
    return BuildAttributionUiEvent.Page.newBuilder().setPageType(pageType).setPageEntryIndex(newPageEntryIndex).build()
  }

  private fun toPage(selectedNodeId: String, pageType: BuildAttributionUiEvent.Page.PageType): BuildAttributionUiEvent.Page =
    pagesVisited.computeIfAbsent(selectedNodeId) { registerPage(pageType) }

  private fun doLog(uiEvent: BuildAttributionUiEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT)
        .withProjectId(project)
        .setBuildAttributionUiEvent(uiEvent)
    )
    //Clear up state booleans
    buildOutputLinkClickRegistered = false
    nodeLinkClickRegistered = false
  }

  /**
   * Called instead of [pageChange] when it is a first page opened by default.
   */
  fun initFirstPage(selectedNodeId: String, pageType: BuildAttributionUiEvent.Page.PageType) {
    currentPage = toPage(selectedNodeId, pageType)
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
}