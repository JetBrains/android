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
package com.android.tools.idea.navigator

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.FeatureSurveys
import com.android.utils.DateProvider
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProjectViewSelectionChangeEvent
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.EdtExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val COOL_DOWN_PERIOD_MS = 10_000L
private const val BEFORE_INIT_STATE = ""
const val ANDROID_VIEW_ID = ID
private const val ANDROID_PROJECT_VIEW_UNSELECTED_SURVEY_NAME = "ANDROID_PROJECT_VIEW_UNSELECTED"

/**
 * When project tool window is registered this class sets up a listener to its content manager to track selections.
 * When it detects a selection change it remembers the current state and then schedules a task to run in 10 seconds.
 * If some more changes happen during this time, new task is scheduled to start in 10 seconds. On execution task first check
 * if more events happened since it was scheduled, skipping further execution if so. This way we are waiting for the moment
 * when no more selection changes happening for this time.
 * Once change is settled task reports the change from the initial state to the final current state if the states are different.
 */
class ProjectViewListener(
  val project: Project,
  private val featureSurveys: FeatureSurveys,
  private val scheduler: ScheduledExecutorService,
  private val dateProvider: DateProvider
  ) : ToolWindowManagerListener {

  @Suppress("unused")
  constructor(project: Project) : this(project, FeatureSurveys, EdtExecutorService.getScheduledExecutorInstance(), DateProvider.SYSTEM)

  @Volatile
  private var addEventTimestamp: Long = 0
  @Volatile
  private var firstRemovedId: String? = null

  private val contentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      val currentTime = dateProvider.now().time
      when (event.operation) {
        ContentManagerEvent.ContentOperation.remove -> {
          val shouldUpdateFirstId = addEventTimestamp + COOL_DOWN_PERIOD_MS < currentTime
          addEventTimestamp = currentTime
          if (shouldUpdateFirstId) {
            firstRemovedId = ProjectView.getInstance(project).currentViewId
          }
        }
        ContentManagerEvent.ContentOperation.add -> {
          addEventTimestamp = currentTime
          scheduler.schedule(ReportingRunnable(currentTime), COOL_DOWN_PERIOD_MS, TimeUnit.MILLISECONDS)
        }
        else -> Unit
      }
    }
  }
  private inner class ReportingRunnable(
    val addingTime: Long
  ) : Runnable {
    override fun run() {
      if (project.isDisposed) return
      // The order matters here. On 'remove' event we first update time and then save the id.
      // So here we first read the saved id and then check if there were updates. Otherwise, in an unlikely case of parallel
      // execution, saved id could be changed after we decided to continue and then we end up with an incorrect value.
      val localInitialViewId = firstRemovedId

      // Check if newer activity happened, skip if so.
      if (addingTime != addEventTimestamp) return

      val newViewId = ProjectView.getInstance(project).currentViewId

      if (localInitialViewId != null && newViewId != null && localInitialViewId != newViewId) {
        processDetectedChange(localInitialViewId, newViewId)
      }
    }
  }

  override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
    if (ids.contains(ToolWindowId.PROJECT_VIEW)) {
      val currentTime = dateProvider.now().time
      addEventTimestamp = currentTime
      firstRemovedId = BEFORE_INIT_STATE
      scheduler.schedule(ReportingRunnable(currentTime), COOL_DOWN_PERIOD_MS, TimeUnit.MILLISECONDS)

      toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)?.contentManager?.addContentManagerListener(contentManagerListener)
    }
  }

  override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
    if (id == ToolWindowId.PROJECT_VIEW) {
      toolWindow.contentManager.removeContentManagerListener(contentManagerListener)
    }
  }

  private fun processDetectedChange(fromViewId: String, toViewId: String) {
    val viewBeforeChangeValue = convertToViewEnum(fromViewId)
    val viewAfterChangeValue = convertToViewEnum(toViewId)

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.PROJECT_VIEW_SELECTION_CHANGE_EVENT
      projectViewSelectionChangeEvent = ProjectViewSelectionChangeEvent.newBuilder().apply {
        viewBeforeChange = viewBeforeChangeValue
        viewAfterChange = viewAfterChangeValue
      }.build()
    })

    if (
      viewBeforeChangeValue == ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID
      && ProjectView.getInstance(project).paneIds.contains(ANDROID_VIEW_ID)
    ) {
      featureSurveys.triggerSurveyByName(ANDROID_PROJECT_VIEW_UNSELECTED_SURVEY_NAME)
    }
  }

  private fun convertToViewEnum(viewId: String): ProjectViewSelectionChangeEvent.ProjectViewContent = when(viewId) {
    BEFORE_INIT_STATE -> ProjectViewSelectionChangeEvent.ProjectViewContent.UNKNOWN
    ANDROID_VIEW_ID -> ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID
    ProjectViewPane.ID -> ProjectViewSelectionChangeEvent.ProjectViewContent.PROJECT
    else -> ProjectViewSelectionChangeEvent.ProjectViewContent.OTHER
  }
}