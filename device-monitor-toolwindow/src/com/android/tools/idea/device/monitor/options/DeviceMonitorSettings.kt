/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.options

import com.android.tools.idea.io.IdeFileService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for 'Device Monitor'.
 */
@State(name = "DeviceMonitor", storages = [Storage("deviceMonitor.xml")])
class DeviceMonitorSettings : PersistentStateComponent<DeviceMonitorSettings> {
  var downloadLocation: String = getDefaultDownloadLocation()

  companion object {
    @JvmStatic
    fun getInstance(): DeviceMonitorSettings {
      return ApplicationManager.getApplication().getService(DeviceMonitorSettings::class.java)
    }
  }

  override fun getState(): DeviceMonitorSettings {
    return this
  }

  override fun loadState(state: DeviceMonitorSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  /** Get default path for Device Monitor downloaded files */
  private fun getDefaultDownloadLocation(): String = IdeFileService("device-monitor").cacheRoot.toString()
}
