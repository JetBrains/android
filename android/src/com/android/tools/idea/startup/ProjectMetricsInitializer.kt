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
import com.android.tools.idea.projectsystem.getProjectSystem
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioProjectChange
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.*
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.util.application
import java.util.Collections
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private const val DELAY = 60L
private const val INITIAL_DELAY = 10L

@Service
class ProjectMetricsService {
  val persistStatisticsSessionsMap: MutableMap<Project, Future<*>> = Collections.synchronizedMap(HashMap<Project, Future<*>>())
}

private class ProjectMetricsInitializer : ProjectCloseListener {
  class MyStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      // don't include current project to be consistent with projectClosed
      val projectsOpen = ProjectManager.getInstance().openProjects.size - 1
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_OPENED)
                         .setStudioProjectChange(StudioProjectChange.newBuilder().setProjectsOpen(projectsOpen)))

      // Once an hour, log all known application ids for the current project
      val scheduledFuture = JobScheduler.getScheduler().schedule(
        {
          //wait until initial indexing is finished
          DumbService.getInstance(project).runWhenSmart {
            val future = JobScheduler.getScheduler()
              .scheduleWithFixedDelay(
                {
                  val knownProjectIds = project.getProjectSystem().getKnownApplicationIds(project)
                  UsageTracker.log(AndroidStudioEvent.newBuilder()
                                     .setKind(AndroidStudioEvent.EventKind.PROJECT_IDS)
                                     .addAllRawProjectIds(knownProjectIds)
                  )
                },
                0, DELAY, TimeUnit.MINUTES)
            application.getService(ProjectMetricsService::class.java).persistStatisticsSessionsMap[project] = future
          }
        }, INITIAL_DELAY, TimeUnit.MINUTES)
      application.getService(ProjectMetricsService::class.java).persistStatisticsSessionsMap[project] = scheduledFuture
    }
  }

  override fun projectClosed(project: Project) {
    val projectsOpen = ProjectManager.getInstance().openProjects.size
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_CLOSED)
                       .setStudioProjectChange(StudioProjectChange.newBuilder().setProjectsOpen(projectsOpen)))

    val future = application.getService(ProjectMetricsService::class.java).persistStatisticsSessionsMap.remove(project)
    future?.cancel(true)
  }
}
