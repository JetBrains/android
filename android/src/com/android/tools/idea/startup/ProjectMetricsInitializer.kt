/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioProjectChange
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

class ProjectMetricsInitializer : ProjectManagerListener {
  override fun projectOpened(project: Project) {
    // don't include current project to be consistent with projectClosed
    val projectsOpen = ProjectManager.getInstance().openProjects.size - 1
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_OPENED)
                       .setStudioProjectChange(StudioProjectChange.newBuilder().setProjectsOpen(projectsOpen)))

  }

  override fun projectClosed(project: Project) {
    val projectsOpen = ProjectManager.getInstance().openProjects.size
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_CLOSED)
                       .setStudioProjectChange(StudioProjectChange.newBuilder().setProjectsOpen(projectsOpen)))

  }
}