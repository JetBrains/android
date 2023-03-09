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
package com.android.gmdcodecompletion

import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogService
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.project.Project

/**
 * Project listener that is triggered after each gradle sync.
 * Update code completion device catalog cache if cache is outdated, empty or corrupted
 */
class GmdCodeCompletionGradleSyncListener(private val project: Project) : ProjectSystemSyncManager.SyncResultListener {
  override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
    if (!result.isSuccessful) return
    val deviceCatalogServices = listOf(ManagedVirtualDeviceCatalogService.getInstance(), FtlDeviceCatalogService.getInstance())
    deviceCatalogServices.forEach { it.updateDeviceCatalog(project) }
  }
}