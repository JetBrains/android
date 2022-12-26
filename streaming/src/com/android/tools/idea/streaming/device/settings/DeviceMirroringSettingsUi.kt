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
package com.android.tools.idea.streaming.device.settings

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.and
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nls

/**
 * Implementation of Settings > Tools > Device Mirroring preference page.
 */
class DeviceMirroringSettingsUi : SearchableConfigurable, Configurable.NoScroll {

  private lateinit var deviceMirroringEnabledCheckBox: JBCheckBox
  private lateinit var synchronizeClipboardCheckBox: JBCheckBox
  private lateinit var activateOnConnectionCheckBox: JBCheckBox
  private lateinit var activateOnAppLaunchCheckBox: JBCheckBox
  private lateinit var activateOnTestLaunchCheckBox: JBCheckBox
  private lateinit var maxSyncedClipboardLengthTextField: JBTextField
  private lateinit var turnOffDisplayWhileMirroringCheckBox: JBCheckBox

  private val state = DeviceMirroringSettings.getInstance()

  override fun getId() = "device.mirroring.options"

  override fun createComponent() = panel {
    row {
      deviceMirroringEnabledCheckBox =
        checkBox("Enable mirroring of physical Android devices")
          .comment("Causes displays of connected Android devices to be mirrored in the&nbsp;Running&nbsp;Devices tool window. " +
                   "<a href='https://d.android.com/r/studio-ui/device-mirroring/help'>Learn&nbsp;more</a>")
          .bindSelected(state::deviceMirroringEnabled)
          .component
    }
    row {
      activateOnConnectionCheckBox =
        checkBox("Open the Running Devices tool window when a physical device is connected")
          .bindSelected(state::activateOnConnection)
          .component
    }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
    row {
      activateOnAppLaunchCheckBox =
        checkBox("Open the Running Devices tool window when launching an app")
          .bindSelected(state::activateOnAppLaunch)
          .component
    }.enabledIf(deviceMirroringEnabledCheckBox.selected)
    row {
      activateOnTestLaunchCheckBox =
        checkBox("Open the Running Devices tool window when launching a test")
          .bindSelected(state::activateOnTestLaunch)
          .component
    }.enabledIf(deviceMirroringEnabledCheckBox.selected)
    row {
      synchronizeClipboardCheckBox =
        checkBox("Enable clipboard sharing")
          .bindSelected(state::synchronizeClipboard)
          .component
    }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
    indent {
      row("Maximum length of synchronized clipboard text:") {
        maxSyncedClipboardLengthTextField =
          intTextField(range = 10..10_000_000, keyboardStep = 1000)
            .bindIntText(state::maxSyncedClipboardLength)
            .component
      }.enabledIf(deviceMirroringEnabledCheckBox.selected.and(synchronizeClipboardCheckBox.selected))
    }
    row {
      turnOffDisplayWhileMirroringCheckBox =
        checkBox("Turn off device display while mirroring")
          .bindSelected(state::turnOffDisplayWhileMirroring)
          .component
    }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
  }

  override fun isModified(): Boolean {
    return deviceMirroringEnabledCheckBox.isSelected != state.deviceMirroringEnabled ||
           activateOnConnectionCheckBox.isSelected != state.activateOnConnection ||
           activateOnAppLaunchCheckBox.isSelected != state.activateOnAppLaunch ||
           activateOnTestLaunchCheckBox.isSelected != state.activateOnTestLaunch ||
           synchronizeClipboardCheckBox.isSelected != state.synchronizeClipboard ||
           maxSyncedClipboardLengthTextField.text.trim() != state.maxSyncedClipboardLength.toString() ||
           turnOffDisplayWhileMirroringCheckBox.isSelected != state.turnOffDisplayWhileMirroring
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    maxSyncedClipboardLengthTextField.validate()
    state.deviceMirroringEnabled = deviceMirroringEnabledCheckBox.isSelected
    state.activateOnConnection = activateOnConnectionCheckBox.isSelected
    state.activateOnAppLaunch = activateOnAppLaunchCheckBox.isSelected
    state.activateOnTestLaunch = activateOnTestLaunchCheckBox.isSelected
    state.synchronizeClipboard = synchronizeClipboardCheckBox.isSelected
    state.maxSyncedClipboardLength = maxSyncedClipboardLengthTextField.text.trim().toInt()
    state.turnOffDisplayWhileMirroring = turnOffDisplayWhileMirroringCheckBox.isSelected
  }

  override fun reset() {
    deviceMirroringEnabledCheckBox.isSelected = state.deviceMirroringEnabled
    activateOnConnectionCheckBox.isSelected = state.activateOnConnection
    activateOnAppLaunchCheckBox.isSelected = state.activateOnAppLaunch
    activateOnTestLaunchCheckBox.isSelected = state.activateOnTestLaunch
    synchronizeClipboardCheckBox.isSelected = state.synchronizeClipboard
    maxSyncedClipboardLengthTextField.text = state.maxSyncedClipboardLength.toString()
    turnOffDisplayWhileMirroringCheckBox.isSelected = state.turnOffDisplayWhileMirroring
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Device Mirroring" else "Android Device Mirroring"
}
