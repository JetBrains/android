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
package com.android.gmdcodecompletion.ftl

import com.android.gmdcodecompletion.FTL_DEVICE_CATALOG_UPDATE_FREQUENCY
import com.android.gmdcodecompletion.GmdDeviceCatalogService
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.Calendar
import kotlin.concurrent.withLock

/**
 * This class updates FTL device catalog when local cache is outdated, empty or corrupted
 *
 * PersistentStateComponent must disable roaming type
 */
@State(
  name = "FtlDeviceCatalogService",
  storages = [Storage(value = StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)],
)
@Service(Service.Level.APP)
class FtlDeviceCatalogService : GmdDeviceCatalogService<FtlDeviceCatalogState>(FtlDeviceCatalogState(), "FtlDeviceCatalogService") {
  companion object {
    @JvmStatic
    fun getInstance(): FtlDeviceCatalogService = ApplicationManager.getApplication().getService(FtlDeviceCatalogService::class.java)!!
  }

  override var myDeviceCatalogState: FtlDeviceCatalogState = FtlDeviceCatalogState()

  // Interactions with PSI / UI elements should not run as part of background task
  override fun runBeforeUpdate(project: Project): Boolean {
    val ftlEnabled = ProjectBuildModel.get(project)?.projectBuildModel?.plugins()?.any { pluginModel ->
      pluginModel?.psiElement?.let { psiElement ->
        psiElement?.text?.contains("com.google.firebase.testlab") ?: false
      } ?: false
    } ?: return true
    return (!ftlEnabled)
  }

  override fun updateDeviceCatalogTaskAction(project: Project,
                                             indicator: ProgressIndicator) {
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    myLock.withLock {
      indicator.text = "Checking cache freshness"
      if (myDeviceCatalogState.isCacheFresh()) return
      indicator.text = "Fetching device catalog from FTL Server"
      val calendar = Calendar.getInstance() // Specify the number of days that device catalog should be updated
      calendar.add(Calendar.DATE, FTL_DEVICE_CATALOG_UPDATE_FREQUENCY)
      myDeviceCatalogState = FtlDeviceCatalogState(calendar.time, FtlDeviceCatalog().syncDeviceCatalog())
    }

    indicator.text = "FTL device catalog cache updated"
    indicator.fraction = 1.0
  }
}