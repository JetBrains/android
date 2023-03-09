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
package com.android.gmdcodecompletion.managedvirtual

import com.android.gmdcodecompletion.GmdDeviceCatalogService
import com.android.gmdcodecompletion.MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY
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
 * This class updates managed virtual devices catalog when local cache is outdated, empty or corrupted
 *
 * PersistentStateComponent must disable roaming type
 */
@State(
  name = "ManagedVirtualDeviceCatalogService",
  storages = [Storage(value = StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)],
)
@Service(Service.Level.APP)
class ManagedVirtualDeviceCatalogService
  : GmdDeviceCatalogService<ManagedVirtualDeviceCatalogState>(ManagedVirtualDeviceCatalogState(), "ManagedVirtualDeviceCatalogService") {
  companion object {
    @JvmStatic
    fun getInstance(): ManagedVirtualDeviceCatalogService =
      ApplicationManager.getApplication().getService(ManagedVirtualDeviceCatalogService::class.java)!!
  }

  override var myDeviceCatalogState: ManagedVirtualDeviceCatalogState = ManagedVirtualDeviceCatalogState()

  override fun updateDeviceCatalogTaskAction(project: Project, indicator: ProgressIndicator) {
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    myLock.withLock {
      indicator.text = "Checking cache freshness"
      if (myDeviceCatalogState.isCacheFresh()) return
      val calendar = Calendar.getInstance() // Specify the number of days that device catalog should be updated
      calendar.add(Calendar.DATE, MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY)
      myDeviceCatalogState = ManagedVirtualDeviceCatalogState(calendar.time, ManagedVirtualDeviceCatalog().syncDeviceCatalog())
    }
    indicator.text = "Cache updated"
    indicator.fraction = 1.0
  }
}