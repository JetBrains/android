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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_IN_NON_EXPERIMENTAL_UI
import com.android.tools.idea.gradle.project.SYNC_DUE_DIALOG_SHOWN
import com.android.tools.idea.gradle.project.SYNC_DUE_SNOOZED_SETTING
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.gradle.project.sync.AutoSyncSettingStore
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JList

/**
 * This is a replacement for [Settings | Build, Execution, Deployment | Build Tools] area, which offers IDEA sync project control
 * that we don't use. This configurable exposes control over [AutoSyncBehavior] instead.
 *
 * Hiding of the standard IDEA build tools setting happens via [com.android.tools.idea.gradle.util.AndroidStudioPreferences.cleanUpPreferences]
 * that currently happens in [com.android.tools.idea.gradle.project.AndroidStudioProjectActivity] (for settings once a project is open) and in
 * [com.android.tools.idea.projectsystem.gradle.AndroidStudioSettingsInitializer] (for settings before any project is opened).
 */
class AndroidStudioBuildToolsConfigurable : BoundSearchableConfigurable(
  message("settings.build.tools.display.name"),
  "Settings_Build_Tools",
  "build.tools"
) {

  private lateinit var autoSyncBehaviorComboBox: ComboBox<AutoSyncBehavior>
  private var autoSyncBehaviorAwaitingSetting: AutoSyncBehavior? = null
  private val showAutoSyncControl = SHOW_GRADLE_AUTO_SYNC_SETTING_IN_NON_EXPERIMENTAL_UI.get()

  override fun createPanel(): DialogPanel = panel {
    if (showAutoSyncControl) {
      addAutoSyncControl(this)
    }
  }

  override fun apply() {
    if (showAutoSyncControl) {
      autoSyncBehaviorAwaitingSetting?.let {
        AutoSyncSettingStore.autoSyncBehavior = it
        clearAutoSyncVariables()
        trackAutoSyncSettingChanged()
      }
      autoSyncBehaviorAwaitingSetting = null
    }
    super.apply()
  }

  override fun isModified(): Boolean {
    if (showAutoSyncControl && autoSyncBehaviorAwaitingSetting != null) return true
    return super.isModified()
  }

  override fun reset() {
    if (showAutoSyncControl) {
      autoSyncBehaviorComboBox.selectedItem = AutoSyncSettingStore.autoSyncBehavior
      autoSyncBehaviorAwaitingSetting = null
    }
    super.reset()
  }


  private fun addAutoSyncControl(panel: Panel) {
    with(panel) {
      row {
        label(AndroidBundle.message("gradle.settings.autoSync.settings.label"))
        autoSyncBehaviorComboBox = comboBox(EnumComboBoxModel<AutoSyncBehavior>(AutoSyncBehavior::class.java), object : SimpleListCellRenderer<AutoSyncBehavior>() {
          override fun customize(list: JList<out AutoSyncBehavior?>,
                                 value: AutoSyncBehavior?,
                                 index: Int,
                                 selected: Boolean,
                                 hasFocus: Boolean) {
            text = value?.labelBundleKey?.let { AndroidBundle.message(it) }
          }
        }).component
      }
    }
    autoSyncBehaviorComboBox.whenItemSelected { newSelection ->
      autoSyncBehaviorAwaitingSetting = newSelection
    }
  }

  private fun trackAutoSyncSettingChanged() {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.AUTO_SYNC_SETTING_CHANGE)
        .setAutoSyncSettingChangeEvent(
          AutoSyncSettingChangeEvent.newBuilder()
            .setState(autoSyncBehaviorComboBox.selectedItem == AutoSyncBehavior.Default)
            .setChangeSource(AutoSyncSettingChangeEvent.ChangeSource.SETTINGS)
            .build()
        )
    );
  }

  /**
   * Clears snooze and first dialog flags that are used by Optional Auto Sync feature.
   */
  private fun clearAutoSyncVariables() {
    PropertiesComponent.getInstance().unsetValue(SYNC_DUE_SNOOZED_SETTING)
    PropertiesComponent.getInstance().unsetValue(SYNC_DUE_DIALOG_SHOWN)
  }
}