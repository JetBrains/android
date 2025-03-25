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
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

/**
 * Implementation of Settings > Tools > Device Mirroring preference page.
 */
class DeviceMirroringSettingsPage : BoundConfigurable(DISPLAY_NAME), SearchableConfigurable {

  private lateinit var synchronizeClipboardCheckBox: JBCheckBox

  private val state = DeviceMirroringSettings.getInstance()

  override fun getId() = "device.mirroring.options"

  override fun createPanel() = panel {
    row {
      checkBox(message("mirroring.settings.checkbox.activate.mirroring.when.device.connected"))
        .bindSelected(state::activateOnConnection)
        .applyToComponent {
          addActionListener {
            if (isSelected && !state.activateOnConnection) {
              onMirroringEnabled(this)
            }
          }
        }
    }
    row {
      checkBox(message("mirroring.settings.checkbox.open.tool.window.when.launching.app"))
        .bindSelected(state::activateOnAppLaunch)
        .applyToComponent {
          addActionListener {
            if (isSelected && !state.activateOnAppLaunch) {
              onMirroringEnabled(this)
            }
          }
        }
    }
    row {
      checkBox(message("mirroring.settings.checkbox.open.tool.window.when.launching.test"))
        .bindSelected(state::activateOnTestLaunch)
        .applyToComponent {
          addActionListener {
            if (isSelected && !state.activateOnTestLaunch) {
              onMirroringEnabled(this)
            }
          }
        }
    }
    row {
      checkBox(message("mirroring.settings.checkbox.redirect.audio"))
        .comment(message("mirroring.settings.checkbox.redirect.audio.comment"))
        .bindSelected(state::redirectAudio)
    }.topGap(TopGap.SMALL)
    row {
      synchronizeClipboardCheckBox =
        checkBox(message("mirroring.settings.checkbox.clipboard.sharing"))
          .bindSelected(state::synchronizeClipboard)
          .component
    }.topGap(TopGap.SMALL)
    indent {
      row(message("mirroring.settings.maximum.length.clipboard.text")) {
        intTextField(range = 10..10_000_000, keyboardStep = 1000)
          .bindIntText(state::maxSyncedClipboardLength)
      }.enabledIf(synchronizeClipboardCheckBox.selected)
    }
    row {
      checkBox(message("mirroring.settings.checkbox.turn.off.device.display.while.mirroring"))
        .bindSelected(state::turnOffDisplayWhileMirroring)
    }.topGap(TopGap.SMALL)
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

private val DISPLAY_NAME = when {
  IdeInfo.getInstance().isAndroidStudio -> message("android.configurable.DeviceMirroringConfigurable.displayName")
  else -> "Android Device Mirroring"
}