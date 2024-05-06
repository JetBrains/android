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
package com.android.tools.idea.device.explorer.common

import com.android.tools.idea.io.IdeFileService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent Device Explorer settings
 */
@State(name = "DeviceExplorer", storages = [Storage("deviceExplorer.xml")])
class DeviceExplorerSettings : PersistentStateComponent<DeviceExplorerSettings> {
  var downloadLocation: String = getDefaultDownloadLocation()
  var isPackageFilterActive: Boolean = false

  companion object {
    @JvmStatic
    fun getInstance(): DeviceExplorerSettings {
      return ApplicationManager.getApplication().getService(DeviceExplorerSettings::class.java)
    }
  }

  override fun getState(): DeviceExplorerSettings {
    return this
  }

  override fun loadState(state: DeviceExplorerSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  /** Get default path for Device File Explorer downloaded files, originally in DeviceExplorerFileManagerImpl */
  private fun getDefaultDownloadLocation(): String = IdeFileService("device-explorer").cacheRoot.toString()
}
