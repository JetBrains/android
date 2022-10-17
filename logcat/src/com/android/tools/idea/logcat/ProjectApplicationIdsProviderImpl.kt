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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.intellij.openapi.project.Project

/**
 * Prod implementation of [ProjectApplicationIdsProvider]
 */
internal class ProjectApplicationIdsProviderImpl(private val project: Project) : ProjectApplicationIdsProvider {
  private var applicationIds = loadApplicationIds()

  init {
    project.messageBus.connect().subscribe(PROJECT_SYSTEM_SYNC_TOPIC, RefreshApplicationIds())
  }

  override fun getPackageNames(): Set<String> = applicationIds

  private fun loadApplicationIds(): Set<String> =
    project.getAndroidFacets().flatMapTo(mutableSetOf()) { AndroidModel.get(it)?.allApplicationIds ?: emptyList() }

  private inner class RefreshApplicationIds : ProjectSystemSyncManager.SyncResultListener {
    override fun syncEnded(result: SyncResult) {
      val newIds = loadApplicationIds()
      if (newIds != applicationIds) {
        applicationIds = newIds
        project.messageBus.syncPublisher(PROJECT_APPLICATION_IDS_CHANGED_TOPIC).applicationIdsChanged(newIds)
      }
    }
  }
}