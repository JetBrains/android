/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.builder.model.AaptOptions
import com.android.tools.idea.namespacing
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

/**
 * Project service that keeps track of whether the project uses any namespaced modules or not.
 *
 * When namespaces are not used at all, some project-wide functionality may be simplified, e.g. "find usages" doesn't have to look for
 * usages of fields from two R classes (namespaced and non-namespaced). This is mostly to simplify UI, not gain performance.
 */
class ProjectNamespacingStatusService(private val project: Project) {
  @Volatile
  var namespacesUsed = checkNamespacesUsed()
    private set


  init {
    project.messageBus.connect().subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object: ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
        namespacesUsed = checkNamespacesUsed()
      }
    })
  }

  private fun checkNamespacesUsed(): Boolean {
    return ModuleManager.getInstance(project).modules.any { it.androidFacet?.namespacing == AaptOptions.Namespacing.REQUIRED }
  }

  companion object {
    @JvmStatic fun getInstance(project: Project) = project.getService(ProjectNamespacingStatusService::class.java)!!
  }
}
