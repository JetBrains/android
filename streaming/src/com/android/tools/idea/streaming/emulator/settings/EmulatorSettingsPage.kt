/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.settings

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.streaming.DEFAULT_CAMERA_VELOCITY_CONTROLS
import com.android.tools.idea.streaming.DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.EmulatorSettings.CameraVelocityControls
import com.android.tools.idea.streaming.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.android.tools.idea.streaming.StreamingBundle.message
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/**
 * Implementation of the Settings > Tools > Emulator preference page.
 */
class EmulatorSettingsPage : BoundConfigurable(DISPLAY_NAME), SearchableConfigurable {

  private val state = EmulatorSettings.getInstance()

  override fun getId() = "emulator.options"

  override fun createPanel() = panel {
    row {
      checkBox("Launch in the Running Devices tool window")
        .bindSelected(state::launchInToolWindow)
        .comment("When this setting is enabled, virtual devices launched from Device Manager or when running an app will appear in" +
                 " the Running Devices tool window. Otherwise virtual devices will launch in a standalone Android Emulator application." +
                 " Virtual devices launched from the Running Devices window will always appear in that window regardless of this" +
                 " setting.")

    }
    indent {
      row {
        checkBox("Open the Running Devices tool window when launching an app")
          .bindSelected(state::activateOnAppLaunch)
      }.topGap(TopGap.SMALL)
      row {
        checkBox("Open the Running Devices tool window when launching a test")
          .bindSelected(state::activateOnTestLaunch)
      }
    }
    row {
      checkBox("Synchronize clipboard")
        .bindSelected(state::synchronizeClipboard)
    }.topGap(TopGap.SMALL)
    row {
      checkBox("Show camera control prompts")
        .bindSelected(state::showCameraControlPrompts)
    }.topGap(TopGap.SMALL)
    row {
      panel {
        row("Velocity control keys for virtual scene camera:") {}
        row {
          comboBox(EnumComboBoxModel(CameraVelocityControls::class.java),
                   renderer = SimpleListCellRenderer.create(DEFAULT_CAMERA_VELOCITY_CONTROLS.label) { it.label })
            .bindItem({ state.cameraVelocityControls }, { state.cameraVelocityControls = it!! })
        }
      }
    }
    row {
      panel {
        row("When encountering snapshots incompatible with the current configuration:") {}
        row {
          comboBox(EnumComboBoxModel(SnapshotAutoDeletionPolicy::class.java),
                   renderer = SimpleListCellRenderer.create(DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY.displayName) { it.displayName })
            .bindItem({ state.snapshotAutoDeletionPolicy }, { state.snapshotAutoDeletionPolicy = it!! })
        }
      }
    }.topGap(TopGap.SMALL)
  }
}

private val DISPLAY_NAME = when {
  IdeInfo.getInstance().isAndroidStudio -> message("android.configurable.EmulatorConfigurable.displayName")
  else -> "Android Emulator"
}
