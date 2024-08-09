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
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.Nls

/**
 * Implementation of Settings > Tools > Device Mirroring preference page.
 */
class DeviceMirroringSettingsPage : SearchableConfigurable, Configurable.NoScroll {

  private lateinit var disposable: Disposable
  private lateinit var activateOnConnectionCheckBox: JBCheckBox
  private lateinit var activateOnAppLaunchCheckBox: JBCheckBox
  private lateinit var activateOnTestLaunchCheckBox: JBCheckBox
  private lateinit var streamAudioCheckBox: JBCheckBox
  private lateinit var synchronizeClipboardCheckBox: JBCheckBox
  private lateinit var maxSyncedClipboardLengthTextField: JBTextField
  private lateinit var turnOffDisplayWhileMirroringCheckBox: JBCheckBox

  private val state = DeviceMirroringSettings.getInstance()

  override fun getId() = "device.mirroring.options"

  override fun createComponent() = panel {
    row {
      activateOnConnectionCheckBox =
        checkBox(message("mirroring.settings.checkbox.activate.mirroring.when.device.connected"))
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
        checkBox(message("mirroring.settings.checkbox.open.tool.window.when.launching.app"))
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
        checkBox(message("mirroring.settings.checkbox.open.tool.window.when.launching.test"))
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
      streamAudioCheckBox =
        checkBox(message("mirroring.settings.checkbox.redirect.audio"))
          .comment(message("mirroring.settings.checkbox.redirect.audio.comment"))
          .bindSelected(state::redirectAudio)
          .component
    }.topGap(TopGap.SMALL)
    row {
      synchronizeClipboardCheckBox =
        checkBox(message("mirroring.settings.checkbox.clipboard.sharing"))
          .bindSelected(state::synchronizeClipboard)
          .component
    }.topGap(TopGap.SMALL)
    indent {
      row(message("mirroring.settings.maximum.length.clipboard.text")) {
        maxSyncedClipboardLengthTextField =
          intTextField(range = 10..10_000_000, keyboardStep = 1000)
            .bindIntText(state::maxSyncedClipboardLength)
            .component
      }.enabledIf(synchronizeClipboardCheckBox.selected)
    }
    row {
      turnOffDisplayWhileMirroringCheckBox =
        checkBox(message("mirroring.settings.checkbox.turn.off.device.display.while.mirroring"))
          .bindSelected(state::turnOffDisplayWhileMirroring)
          .component
    }.topGap(TopGap.SMALL)
  }.apply {
    disposable = Disposer.newDisposable("${this@DeviceMirroringSettingsPage::class.simpleName} validators disposable")
    registerValidators(disposable)
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  override fun isModified(): Boolean {
    return activateOnConnectionCheckBox.isSelected != state.activateOnConnection ||
           activateOnAppLaunchCheckBox.isSelected != state.activateOnAppLaunch ||
           activateOnTestLaunchCheckBox.isSelected != state.activateOnTestLaunch ||
           streamAudioCheckBox.isSelected != state.redirectAudio ||
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
    state.redirectAudio = streamAudioCheckBox.isSelected
    state.synchronizeClipboard = synchronizeClipboardCheckBox.isSelected
    state.maxSyncedClipboardLength = maxSyncedClipboardLengthTextField.text.trim().toInt()
    state.turnOffDisplayWhileMirroring = turnOffDisplayWhileMirroringCheckBox.isSelected
  }

  override fun reset() {
    activateOnConnectionCheckBox.isSelected = state.activateOnConnection
    activateOnAppLaunchCheckBox.isSelected = state.activateOnAppLaunch
    activateOnTestLaunchCheckBox.isSelected = state.activateOnTestLaunch
    streamAudioCheckBox.isSelected = state.redirectAudio
    synchronizeClipboardCheckBox.isSelected = state.synchronizeClipboard
    maxSyncedClipboardLengthTextField.text = state.maxSyncedClipboardLength.toString()
    turnOffDisplayWhileMirroringCheckBox.isSelected = state.turnOffDisplayWhileMirroring
  }

  @Nls
  override fun getDisplayName() = when {
    IdeInfo.getInstance().isAndroidStudio -> message("android.configurable.DeviceMirroringConfigurable.displayName")
    else -> "Android Device Mirroring"
  }

  private fun onMirroringEnabled(checkBox: JBCheckBox) {
    if (!state.confirmationDialogShown) {
      val title = message("mirroring.privacy.notice.title")
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
