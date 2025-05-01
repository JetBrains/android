/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Syncs the project upon startup.  */
class BlazeSyncStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Blaze.isBlazeProject(project)) {
      return
    }
    val importSettings =
      BlazeImportSettingsManager.getInstance(project).importSettings
    if (importSettings == null) {
      return
    }
    BlazeImportSettingsManager.getInstance(project).initProjectView()

    if (Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC) {
      // When query sync is not enabled hasProjectData triggers the load
      QuerySyncManager.getInstance(project)
        .onStartup(QuerySyncActionStatsScope.create(javaClass, null))
      return
    }
    DumbService.getInstance(project).runWhenSmart {
      if (hasProjectData(project, importSettings)) {
        BlazeSyncManager.getInstance(project).requestProjectSync(startupSyncParams())
      }
      else {
        BlazeSyncManager.getInstance(project).incrementalProjectSync(SYNC_REASON)
      }
    }
  }

  companion object {
    const val SYNC_REASON: String = "BlazeSyncStartupActivity"

    private fun hasProjectData(project: Project, importSettings: BlazeImportSettings): Boolean {
      return BlazeProjectDataManager.getInstance(project).loadProject(importSettings) != null
    }

    private fun startupSyncParams(): BlazeSyncParams {
      return BlazeSyncParams.builder()
        .setTitle("Sync Project")
        .setSyncMode(SyncMode.STARTUP)
        .setSyncOrigin(SYNC_REASON)
        .setAddProjectViewTargets(true)
        .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
        .build()
    }
  }
}
