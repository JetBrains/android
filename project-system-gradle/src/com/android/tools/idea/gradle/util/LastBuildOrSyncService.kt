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
package com.android.tools.idea.gradle.util

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly


class LastBuildOrSyncService {
  // Do not set outside of tests or this class!!
  @Volatile
  var lastBuildOrSyncTimeStamp = -1L
    @VisibleForTesting set
}

internal class LastBuildOrSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
  override fun onEnd(id: ExternalSystemTaskId) {
    id.findProject()?.also { project ->
      project.getService(LastBuildOrSyncService::class.java).lastBuildOrSyncTimeStamp = System.currentTimeMillis()
    }
  }
}

/**
 * This should not really be used, but we currently do not use the intellij build infra and therefore do not get
 * events for build. If we move to using this and the events from running tasks trigger the GenericBuiltArtifactsCacheCleaner then
 * this should be removed.
 */
internal class LastBuildOrSyncStartupActivity : AndroidStartupActivity {
  @UiThread
  override fun runActivity(project: Project, disposable: Disposable) {
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildFinished(status: BuildStatus, context: BuildContext) {
        val service = context.project.getService(LastBuildOrSyncService::class.java)
        service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
      }
    })

    val service = project.getService(LastBuildOrSyncService::class.java)
    service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
  }
}

@TestOnly
fun emulateStartupActivityForTest(project: Project) = AndroidStartupActivity.STARTUP_ACTIVITY.findExtension(
  LastBuildOrSyncStartupActivity::class.java)?.runActivity(project, project)

