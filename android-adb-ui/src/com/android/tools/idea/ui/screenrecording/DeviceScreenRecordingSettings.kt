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
package com.android.tools.idea.ui.screenrecording

import com.android.tools.idea.ui.save.PostSaveAction
import com.android.tools.idea.ui.save.SaveConfiguration
import com.android.tools.idea.ui.save.SaveConfigurationResolver
import com.android.tools.idea.ui.screenshot.convertFilenameTemplateFromOldFormat
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.NON_ROAMABLE_FILE
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Dimension
import kotlin.math.roundToInt

private const val DEFAULT_BIT_RATE_MBPS = 4
private const val DEFAULT_RESOLUTION_PERCENT = 100

/** Settings for screen recordings of Android devices. */
@Service
@State(name = "DeviceScreenRecordingSettings", storages = [Storage(NON_ROAMABLE_FILE)])
internal class DeviceScreenRecordingSettings : PersistentStateComponent<DeviceScreenRecordingSettings> {

  var saveConfig: SaveConfiguration = SaveConfiguration().apply { filenameTemplate = "Screen_recording_<yyyy><MM><dd>_<HH><mm><ss>" }
  var scale: Double = DEFAULT_RESOLUTION_PERCENT / 100.0
  var bitRateMbps: Int = DEFAULT_BIT_RATE_MBPS
  var showTaps: Boolean = false
  var useEmulatorRecordingWhenAvailable: Boolean = true
  @Transient var emulatorRecordingAvailable: Boolean = false
  var recordingCount: Int = 0
  val fileExtension: String
    get() = if (useEmulatorRecordingWhenAvailable && emulatorRecordingAvailable) "webm" else "mp4"

  private var initialized = false

  override fun getState(): DeviceScreenRecordingSettings = this

  override fun noStateLoaded() {
    // Migrate from ScreenshotConfiguration.
    val screenRecordingOptions = service<ScreenRecorderPersistentOptions>()
    saveConfig.saveLocation = screenRecordingOptions.saveLocation
    saveConfig.filenameTemplate = screenRecordingOptions.filenameTemplate
    saveConfig.postSaveAction = screenRecordingOptions.postSaveAction
    scale = screenRecordingOptions.resolutionPercent / 100.0
    bitRateMbps = screenRecordingOptions.bitRateMbps
    showTaps = screenRecordingOptions.showTaps
    useEmulatorRecordingWhenAvailable = screenRecordingOptions.useEmulatorRecording
    recordingCount = screenRecordingOptions.recordingCount
    screenRecordingOptions.loadState(ScreenRecorderPersistentOptions()) // Reset ScreenshotConfiguration to default.
  }

  override fun loadState(state: DeviceScreenRecordingSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun initializeComponent() {
    initialized = true
  }


  fun toScreenRecorderOptions(displayId: Int, size: Dimension?, timeLimitSec: Int): ScreenRecorderOptions {
    val width: Int
    val height: Int
    if (size != null && scale != 1.0) {
      width = roundToMultipleOf16(size.width * scale)
      height = roundToMultipleOf16(size.height * scale)
    }
    else {
      width = 0
      height = 0
    }

    return ScreenRecorderOptions(displayId, width, height, bitRateMbps, showTaps, timeLimitSec)
  }

  companion object {
    @JvmStatic
    fun getInstance(): DeviceScreenRecordingSettings {
      return service<DeviceScreenRecordingSettings>()
    }
  }
}

// TODO: Remove after Narwhal.2 is released to stable.
@Service
@State(name = "ScreenRecorderOptions", storages = [Storage("screenRecorderOptions.xml")])
internal class ScreenRecorderPersistentOptions : PersistentStateComponent<ScreenRecorderPersistentOptions> {

  var bitRateMbps: Int = DEFAULT_BIT_RATE_MBPS
  var resolutionPercent: Int = DEFAULT_RESOLUTION_PERCENT
  var showTaps: Boolean = false
  var useEmulatorRecording: Boolean = true
  var saveLocation: String = SaveConfigurationResolver.DEFAULT_SAVE_LOCATION
  var filenameTemplate: String = "Screen_recording_<yyyy><MM><dd>_<HH><mm><ss>"
  var postSaveAction: PostSaveAction = PostSaveAction.OPEN
  var recordingCount: Int = 0

  override fun getState(): ScreenRecorderPersistentOptions = this

  override fun loadState(state: ScreenRecorderPersistentOptions) {
    XmlSerializerUtil.copyBean(state, this)
    filenameTemplate = convertFilenameTemplateFromOldFormat(filenameTemplate)
  }
}

private fun roundToMultipleOf16(n: Double): Int = ((n / 16).roundToInt() * 16)
