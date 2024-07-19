/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.vitals.ui.VitalsConfigurationService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class VitalsSyncResultListener(private val project: Project) :
  ProjectSystemSyncManager.SyncResultListener {
  override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
    project.service<VitalsConfigurationService>().manager.refreshConfiguration()
  }
}
