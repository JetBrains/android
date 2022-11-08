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
package com.android.tools.idea.common.error

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.UniversalProblemsPanelEvent
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

interface DesignerCommonIssuePanelUsageTracker {

  fun trackChangingCommonIssuePanelVisibility(visibility: Boolean, project: Project)

  fun trackNavigationFromIssue(target: UniversalProblemsPanelEvent.IssueNavigated, project: Project)

  fun trackSelectingTab(tab: UniversalProblemsPanelEvent.ActivatedTab, project: Project)

  fun trackSelectingIssue(project: Project)

  companion object {
    fun getInstance(): DesignerCommonIssuePanelUsageTracker {
      return if (AnalyticsSettings.optedIn)
        DesignerCommonIssuePanelUsageTrackerImpl
      else
        DesignerCommonIssuePanelNoOpUsageTracker
    }
  }
}

private object DesignerCommonIssuePanelUsageTrackerImpl : DesignerCommonIssuePanelUsageTracker {
  private val executorService = ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))

  override fun trackChangingCommonIssuePanelVisibility(visibility: Boolean, project: Project) {
    trackEvent(project) {
      UniversalProblemsPanelEvent.newBuilder()
        .setProblemsPanelVisibility(visibility).build()
    }
  }

  override fun trackNavigationFromIssue(target: UniversalProblemsPanelEvent.IssueNavigated, project: Project) {
    trackEvent(project) {
      UniversalProblemsPanelEvent.newBuilder()
        .setIssueNavigated(target).build()
    }
  }

  override fun trackSelectingTab(tab: UniversalProblemsPanelEvent.ActivatedTab, project: Project) {
    trackEvent(project) {
      UniversalProblemsPanelEvent.newBuilder()
        .setInteractionEvent(UniversalProblemsPanelEvent.InteractionEvent.TAB_ACTIVATED)
        .setActivatedTab(tab).build()
    }
  }

  override fun trackSelectingIssue(project: Project) {
    trackEvent(project) {
      UniversalProblemsPanelEvent.newBuilder()
        .setInteractionEvent(UniversalProblemsPanelEvent.InteractionEvent.ISSUE_SINGLE_CLICKED).build()
    }
  }

  private fun trackEvent(project: Project, eventProvider: () -> UniversalProblemsPanelEvent) {
    try {
      executorService.execute {
        val facet = project.getAndroidFacets().firstOrNull()
        val layoutEditorEventBuilder = LayoutEditorEvent.newBuilder()
          .setType(LayoutEditorEvent.LayoutEditorEventType.UNIVERSAL_PROBLEMS_PANEL)
          .setUniversalProblemsPanelEvent(eventProvider())
        val studioEvent = AndroidStudioEvent.newBuilder()
          .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
          .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
          .setLayoutEditorEvent(layoutEditorEventBuilder.build())

        facet?.let { studioEvent.setApplicationId(it) }
        UsageTracker.log(studioEvent)
      }
    }
    catch (ignore: RejectedExecutionException) { }
  }
}

private object DesignerCommonIssuePanelNoOpUsageTracker : DesignerCommonIssuePanelUsageTracker {
  override fun trackChangingCommonIssuePanelVisibility(visibility: Boolean, project: Project) = Unit
  override fun trackNavigationFromIssue(target: UniversalProblemsPanelEvent.IssueNavigated, project: Project) = Unit

  override fun trackSelectingTab(tab: UniversalProblemsPanelEvent.ActivatedTab, project: Project) = Unit

  override fun trackSelectingIssue(project: Project) = Unit
}
