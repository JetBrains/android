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
package com.android.tools.idea.emulator.settings

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.emulator.EmulatorSettings
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox

/**
 * Implementation of Settings > Tools > Emulator preference page.
 */
internal class EmulatorSettingsUi : SearchableConfigurable, Configurable.NoScroll {

  private lateinit var launchInToolWindowCheckBox: JCheckBox

  private val state = EmulatorSettings.getInstance()

  override fun getId() = "emulator.options"

  override fun createComponent() = panel {
    row {
      launchInToolWindowCheckBox = checkBox("Launch in a tool window",
                                            comment = "Enabling this setting will cause Android Emulator to launch in a tool window. " +
                                                      "Otherwise the Android Emulator will launch as a standalone application. Some AVDs " +
                                                      "will launch as standalone applications regardless of this setting due to their " +
                                                      "hardware profiles or system images. The Emulator's extended controls are not " +
                                                      "available when launched in a tool window.").component
    }
  }

  override fun isModified() =
    launchInToolWindowCheckBox.isSelected != state.launchInToolWindow

  @Throws(ConfigurationException::class)
  override fun apply() {
    state.launchInToolWindow = launchInToolWindowCheckBox.isSelected
  }

  override fun reset() {
    launchInToolWindowCheckBox.isSelected = state.launchInToolWindow
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Emulator" else "Android Emulator"
}