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
import com.android.tools.idea.streaming.device.dialogs.MirroringConfirmationDialog
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nls

/**
 * Implementation of Settings > Tools > Device Mirroring preference page.
 */
class DeviceMirroringSettingsPage : SearchableConfigurable, Configurable.NoScroll {

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
      activateOnConnectionCheckBox =
        checkBox("Activate mirroring when a new physical device is connected")
          .bindSelected(state::activateOnConnection)
          .component.apply {
            addActionListener {
              if (isSelected && !state.activateOnConnection) {
                onMirroringEnabled(this)
              }
            }
          }
    }
    row {
      activateOnAppLaunchCheckBox =
        checkBox("Activate mirroring when launching an app on a physical device")
          .bindSelected(state::activateOnAppLaunch)
          .component.apply {
            addActionListener {
              if (isSelected && !state.activateOnAppLaunch) {
                onMirroringEnabled(this)
              }
            }
          }
    }
    row {
      activateOnTestLaunchCheckBox =
        checkBox("Activate mirroring when launching a test on a physical device")
          .bindSelected(state::activateOnTestLaunch)
          .component.apply {
            addActionListener {
              if (isSelected && !state.activateOnTestLaunch) {
                onMirroringEnabled(this)
              }
            }
          }
    }
    row {
      synchronizeClipboardCheckBox =
        checkBox("Enable clipboard sharing")
          .bindSelected(state::synchronizeClipboard)
          .component
    }.topGap(TopGap.SMALL)
    indent {
      row("Maximum length of synchronized clipboard text:") {
        maxSyncedClipboardLengthTextField =
          intTextField(range = 10..10_000_000, keyboardStep = 1000)
            .bindIntText(state::maxSyncedClipboardLength)
            .component
      }.enabledIf(synchronizeClipboardCheckBox.selected)
    }
    row {
      turnOffDisplayWhileMirroringCheckBox =
        checkBox("Turn off device display while mirroring")
          .comment("(not supported on Android 14)")
          .bindSelected(state::turnOffDisplayWhileMirroring)
          .component
    }.topGap(TopGap.SMALL)
  }

  override fun isModified(): Boolean {
    return activateOnConnectionCheckBox.isSelected != state.activateOnConnection ||
           activateOnAppLaunchCheckBox.isSelected != state.activateOnAppLaunch ||
           activateOnTestLaunchCheckBox.isSelected != state.activateOnTestLaunch ||
           synchronizeClipboardCheckBox.isSelected != state.synchronizeClipboard ||
           maxSyncedClipboardLengthTextField.text.trim() != state.maxSyncedClipboardLength.toString() ||
           turnOffDisplayWhileMirroringCheckBox.isSelected != state.turnOffDisplayWhileMirroring
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    maxSyncedClipboardLengthTextField.validate()
    state.activateOnConnection = activateOnConnectionCheckBox.isSelected
    state.activateOnAppLaunch = activateOnAppLaunchCheckBox.isSelected
    state.activateOnTestLaunch = activateOnTestLaunchCheckBox.isSelected
    state.synchronizeClipboard = synchronizeClipboardCheckBox.isSelected
    state.maxSyncedClipboardLength = maxSyncedClipboardLengthTextField.text.trim().toInt()
    state.turnOffDisplayWhileMirroring = turnOffDisplayWhileMirroringCheckBox.isSelected
  }

  override fun reset() {
    activateOnConnectionCheckBox.isSelected = state.activateOnConnection
    activateOnAppLaunchCheckBox.isSelected = state.activateOnAppLaunch
    activateOnTestLaunchCheckBox.isSelected = state.activateOnTestLaunch
    synchronizeClipboardCheckBox.isSelected = state.synchronizeClipboard
    maxSyncedClipboardLengthTextField.text = state.maxSyncedClipboardLength.toString()
    turnOffDisplayWhileMirroringCheckBox.isSelected = state.turnOffDisplayWhileMirroring
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Device Mirroring" else "Android Device Mirroring"

  private fun onMirroringEnabled(checkBox: JBCheckBox) {
    if (!state.confirmationDialogShown) {
      val title = "Privacy Notice"
      val dialogWrapper = MirroringConfirmationDialog(title).createWrapper(parent = checkBox).apply { show() }
      if (dialogWrapper.exitCode == MirroringConfirmationDialog.ACCEPT_EXIT_CODE) {
        state.confirmationDialogShown = true
      }
      else {
        checkBox.isSelected = false // Revert mirroring enablement.
      }
    }
  }
}
