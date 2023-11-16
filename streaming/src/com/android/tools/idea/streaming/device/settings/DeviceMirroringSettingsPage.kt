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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.StreamingBundle.message
import com.android.tools.idea.streaming.device.dialogs.MirroringConfirmationDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.and
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nls

private const val MIRRORING_HELP_LINK = "https://d.android.com/r/studio-ui/device-mirroring/help"

/**
 * Implementation of Settings > Tools > Device Mirroring preference page.
 */
class DeviceMirroringSettingsPage : SearchableConfigurable, Configurable.NoScroll {

  private lateinit var disposable: Disposable
  private lateinit var deviceMirroringEnabledCheckBox: JBCheckBox
  private lateinit var synchronizeClipboardCheckBox: JBCheckBox
  private lateinit var activateOnConnectionCheckBox: JBCheckBox
  private lateinit var activateOnAppLaunchCheckBox: JBCheckBox
  private lateinit var activateOnTestLaunchCheckBox: JBCheckBox
  private lateinit var maxSyncedClipboardLengthTextField: JBTextField
  private lateinit var turnOffDisplayWhileMirroringCheckBox: JBCheckBox

  private val state = DeviceMirroringSettings.getInstance()

  override fun getId() = "device.mirroring.options"

  private val myPanel = panel {
    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      deviceMirroringEnabledCheckBox = JBCheckBox().apply { isSelected = true }
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
    else {
      row {
        deviceMirroringEnabledCheckBox =
          checkBox(message("android.checkbox.enable.mirroring"))
            .comment(message("android.checkbox.enable.mirroring.comment", MIRRORING_HELP_LINK))
            .bindSelected(state::deviceMirroringEnabled)
            .component.apply {
              addActionListener {
                if (isSelected && !state.deviceMirroringEnabled) {
                  onMirroringEnabled(this)
                }
              }
            }
      }
      row {

          checkBox(message("android.checkbox.open.tool.window.when.device.connected"))
            .bindSelected(state::activateOnConnection)

      }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
      row {

          checkBox(message("android.checkbox.open.tool.window.when.launching.app"))
            .bindSelected(state::activateOnAppLaunch)

      }.enabledIf(deviceMirroringEnabledCheckBox.selected)
      row {

          checkBox(message("android.checkbox.open.tool.window.when.launching.test"))
            .bindSelected(state::activateOnTestLaunch)

      }.enabledIf(deviceMirroringEnabledCheckBox.selected)
      row {
        synchronizeClipboardCheckBox =
          checkBox(message("android.checkbox.clipboard.sharing"))
            .bindSelected(state::synchronizeClipboard)
            .component
      }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
      indent {
        row(message("android.maximum.length.clipboard")) {
            intTextField(range = 10..10_000_000, keyboardStep = 1000)
              .bindIntText(state::maxSyncedClipboardLength)

        }.enabledIf(deviceMirroringEnabledCheckBox.selected.and(synchronizeClipboardCheckBox.selected))
      }
      row {

          checkBox(message("android.checkbox.turn.off.device.display.while.mirroring"))
            .comment("(not supported on Android 14)")
            .bindSelected(state::turnOffDisplayWhileMirroring)

      }.topGap(TopGap.SMALL).enabledIf(deviceMirroringEnabledCheckBox.selected)
    }
  }

  override fun createComponent() = myPanel.apply {
    disposable = Disposer.newDisposable("${this@DeviceMirroringSettingsPage::class.simpleName} validators disposable")
    registerValidators(disposable)
  }

  override fun isModified(): Boolean {
    return myPanel.isModified()
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    myPanel.apply()
  }

  override fun reset() {
    myPanel.reset()
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Device Mirroring" else message("android.configurable.DeviceMirroringConfigurable.displayName")

  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  private fun onMirroringEnabled(checkBox: JBCheckBox) {
    if (!state.confirmationDialogShown) {
      val title = message("android.mirroring.dialog.privacy.notice.title")
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
