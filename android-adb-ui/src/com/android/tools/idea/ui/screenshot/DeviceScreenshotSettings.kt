/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import com.android.SdkConstants.EXT_PNG
import com.android.tools.idea.ui.save.PostSaveAction
import com.android.tools.idea.ui.save.SaveConfiguration
import com.android.tools.idea.ui.save.SaveConfigurationResolver
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.NON_ROAMABLE_FILE
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/** Settings for screenshots of Android devices. */
@Service
@State(name = "DeviceScreenshotSettings", storages = [Storage(NON_ROAMABLE_FILE)])
internal class DeviceScreenshotSettings : PersistentStateComponent<DeviceScreenshotSettings> {

  var saveConfig: SaveConfiguration = SaveConfiguration().apply { filenameTemplate = "Screenshot_<yyyy><MM><dd>_<HH><mm><ss>" }
  var scale: Double = 1.0
  var frameScreenshot: Boolean = false
  var screenshotCount: Int = 0
  val fileExtension: String = EXT_PNG

  private var initialized = false

  override fun getState(): DeviceScreenshotSettings = this

  override fun noStateLoaded() {
    // Migrate from ScreenshotConfiguration.
    val screenshotConfig = service<ScreenshotConfiguration>()
    saveConfig.saveLocation = screenshotConfig.saveLocation
    saveConfig.filenameTemplate = screenshotConfig.filenameTemplate
    saveConfig.postSaveAction = screenshotConfig.postSaveAction
    scale = screenshotConfig.scale
    frameScreenshot = screenshotConfig.frameScreenshot
    screenshotCount = screenshotConfig.screenshotCount
    screenshotConfig.loadState(ScreenshotConfiguration()) // Reset ScreenshotConfiguration to default.
  }

  override fun loadState(state: DeviceScreenshotSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun initializeComponent() {
    initialized = true
  }

  companion object {
    @JvmStatic
    fun getInstance(): DeviceScreenshotSettings {
      return service<DeviceScreenshotSettings>()
    }
  }
}

// TODO: Remove after Narwhal.2 is released to stable.
@Service
@State(name = "ScreenshotConfiguration", storages = [Storage(NON_ROAMABLE_FILE)])
internal class ScreenshotConfiguration : PersistentStateComponent<ScreenshotConfiguration> {

  var frameScreenshot: Boolean = false
  var saveLocation: String = SaveConfigurationResolver.DEFAULT_SAVE_LOCATION
  var scale: Double = 1.0
  var filenameTemplate: String = "Screenshot_<yyyy><MM><dd>_<HH><mm><ss>"
  var screenshotCount: Int = 0
  var postSaveAction: PostSaveAction = PostSaveAction.OPEN

  override fun getState(): ScreenshotConfiguration {
    return this
  }

  override fun loadState(state: ScreenshotConfiguration) {
    XmlSerializerUtil.copyBean<ScreenshotConfiguration>(state, this)
    filenameTemplate = convertFilenameTemplateFromOldFormat(filenameTemplate)
  }
}

/** Converts the given filename template from the format that was used in Narwhal preview to the new format. */
internal fun convertFilenameTemplateFromOldFormat(oldTemplate: String): String {
  if (oldTemplate.contains('<') && oldTemplate.contains('>')) {
    return oldTemplate // Already in the new format.
  }
  return oldTemplate
    .replace("%Y", "<yyyy>")
    .replace("%y", "<yy>")
    .replace("%M", "<MM>")
    .replace("%D", "<dd>")
    .replace("%H", "<HH>")
    .replace("%m", "<mm>")
    .replace("%S", "<ss>")
    .replace("%d", "<#>")
    .replace(Regex("%(\\d+)d")) { match -> "<${"#".repeat(match.groupValues[1].toInt())}>" }
    .replace("%p", "<project>")
}

