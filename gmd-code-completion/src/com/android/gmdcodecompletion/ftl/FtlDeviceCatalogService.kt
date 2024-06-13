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

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.FTL_DEVICE_CATALOG_UPDATE_FREQUENCY
import com.android.gmdcodecompletion.GmdDeviceCatalogService
import com.android.gmdcodecompletion.isFtlPluginEnabled
import com.google.gct.testing.launcher.CloudAuthenticator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.annotations.VisibleForTesting
import java.util.Calendar
import kotlin.concurrent.withLock

/**
 * This class updates FTL device catalog when local cache is outdated, empty or corrupted
 *
 * PersistentStateComponent must disable roaming type
 */
@State(
  name = "FtlDeviceCatalogService",
  storages = [Storage(value = "FtlDeviceCatalogService.xml", roamingType = RoamingType.DISABLED)],
)
@Service(Service.Level.APP)
class FtlDeviceCatalogService : GmdDeviceCatalogService<FtlDeviceCatalogState>(FtlDeviceCatalogState(), "FtlDeviceCatalogService") {
  companion object {
    @JvmStatic
    fun getInstance(): FtlDeviceCatalogService = ApplicationManager.getApplication().getService(FtlDeviceCatalogService::class.java)!!

    @VisibleForTesting
    fun syncDeviceCatalog(): FtlDeviceCatalog {
      val deviceCatalog = FtlDeviceCatalog()
      val remoteCatalog = service<CloudAuthenticator>().androidDeviceCatalog ?: return deviceCatalog
      if (remoteCatalog.isEmpty()) return deviceCatalog
      remoteCatalog.models?.forEach { androidModel ->
        val versionIds = androidModel.supportedVersionIds ?: emptyList()

        if (versionIds.isNotEmpty() && androidModel.id != null) {
          deviceCatalog.devices[androidModel.id] =
            AndroidDeviceInfo(
              deviceName = androidModel.name ?: "",
              supportedApis = versionIds.mapNotNull { it.toIntOrNull() },
              brand = androidModel.brand ?: "",
              formFactor = androidModel["formFactor"]?.toString() ?: "",
              deviceForm = androidModel.form ?: "",
            )
        }
      }
      deviceCatalog.apiLevels.addAll(remoteCatalog.versions?.mapNotNull { it.apiLevel } ?: emptyList())
      deviceCatalog.orientation.addAll(
        remoteCatalog.runtimeConfiguration?.orientations?.mapNotNull { it.id } ?: emptyList()
      )
      remoteCatalog.runtimeConfiguration?.locales?.mapNotNull { locale ->
        if (locale.id != null && locale.id != "") {
          deviceCatalog.locale[locale.id] =
            FtlDeviceCatalog.LocaleInfo(languageName = locale.name ?: "", region = locale.region ?: "")
        }
      }
      deviceCatalog.isEmptyCatalog = deviceCatalog.devices.isEmpty() &&
                                     deviceCatalog.apiLevels.isEmpty() &&
                                     deviceCatalog.orientation.isEmpty() &&
                                     deviceCatalog.locale.isEmpty()
      return deviceCatalog
    }
  }

  /**
   * Return false if FTL plugin is not enabled to NOT run updateDeviceCatalogTaskAction
   */
  override fun shouldUpdate(project: Project): Boolean = isFtlPluginEnabled(project, project.modules)

  override fun updateDeviceCatalogTaskAction(project: Project,
                                             indicator: ProgressIndicator) {
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    myLock.withLock {
      indicator.text = "Checking cache freshness"
      if (this.state.isCacheFresh()) return
      indicator.text = "Fetching device catalog from FTL Server"
      val calendar = Calendar.getInstance() // Specify the number of days that device catalog should be updated
      calendar.add(Calendar.DATE, FTL_DEVICE_CATALOG_UPDATE_FREQUENCY)
      this.state = FtlDeviceCatalogState(calendar.time, syncDeviceCatalog())
    }

    indicator.text = "FTL device catalog cache updated"
    indicator.fraction = 1.0
  }
}