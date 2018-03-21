/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.projectsystem

import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

/**
 * Listens for all sync results for an associated project and broadcasts them over the project's message bus
 * on [PROJECT_SYSTEM_SYNC_TOPIC]. This is a separate project service instead of just a local field in GradleProjectSystemSyncManager
 * because no AndroidProjectSystem structs are initialized until one of the project system APIs is used. We want to make
 * sure that sync results are broadcast on [PROJECT_SYSTEM_SYNC_TOPIC] and the latest sync result is recorded
 * regardless of whether or not a project system method has been called.
 */
class GradleSyncResultPublisher(val project: Project) : SyncWithSourceGenerationListener() {
  var lastSyncResult: ProjectSystemSyncManager.SyncResult = ProjectSystemSyncManager.SyncResult.UNKNOWN
    private set

  init {
    GradleSyncState.subscribe(project, this)
    GradleBuildState.subscribe(project, this)
  }

  override fun syncFinished(sourceGenerationRequested: Boolean, result: ProjectSystemSyncManager.SyncResult) {
    lastSyncResult = result
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleSyncResultPublisher =
        ServiceManager.getService(project, GradleSyncResultPublisher::class.java)
  }
}