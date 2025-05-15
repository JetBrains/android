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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.idea.ui.save.SaveConfigurationPanel
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import java.time.Instant

/** Implementation of Settings > Tools > Screenshots & Screen Recordings > Screen Recordings settings page. */
internal class DeviceScreenRecordingSettingsPage(private val project: Project) :
    BoundConfigurable(message("device.screenrecording.text")), SearchableConfigurable {

  private val state = DeviceScreenRecordingSettings.getInstance()

  override fun getId() = "device.screenrecording"

  override fun createPanel(): DialogPanel =
      SaveConfigurationPanel(state.saveConfig, state.fileExtension, Instant.now(), state.recordingCount + 1, project).createPanel()

  class Provider(private val project: Project) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable = DeviceScreenRecordingSettingsPage(project)

    override fun canCreateConfigurable(): Boolean = StudioFlags.SCREENSHOT_STREAMLINED_SAVING.get()
  }
}
